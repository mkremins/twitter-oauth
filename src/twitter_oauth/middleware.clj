(ns twitter-oauth.middleware
  (:require [oauth.client :as oauth]
            [ring.util.response :as response]
            [twitter.oauth]))

(defn- make-consumer [opts]
  (oauth/make-consumer
    (:consumer-key opts)
    (:consumer-secret opts)
    "https://api.twitter.com/oauth/request_token"
    "https://api.twitter.com/oauth/access_token"
    "https://api.twitter.com/oauth/authorize"
    :hmac-sha1))

(defn- make-oauth-creds [consumer access-token]
  (twitter.oauth/->OauthCredentials
    consumer
    (:oauth_token access-token)
    (:oauth_token_secret access-token)))

(defn- base-url
  "Given a Ring `request`, returns the base URL (everything before the path)."
  [{:keys [scheme headers] :as request}]
  (str (name scheme) "://" (get headers "host")))

(defn wrap-twitter-oauth
  "Wraps `handler` to provide support for the Twitter OAuth sign-in process.
  For this middleware to work correctly, the `compojure.handler/site` and
  `ring.middleware.session/wrap-session` middlewares must precede it in the
  middleware stack.

  The `opts` parameter must be a map containing the following keys:
  :consumer-key – The application's consumer key.
  :consumer-secret – The application's consumer secret.
  :request-token-uri – The URI that will prompt users to sign in with Twitter.
  :access-token-uri – The URI that the Twitter OAuth servers will access once
    the user has decided whether to sign in with Twitter.
  :after-auth-uri – The URI to which users will be redirected once they finish
    signing in with Twitter.

  Once a user has successfully signed in with Twitter, the `:twitter` key in
  their session data will point at a map containing the following keys:
  :oauth-creds – A `twitter.oauth.OauthCredentials` object representing the
    user's Twitter OAuth credentials.
  :screen-name – The screen name of the user's Twitter account.
  :user-id – The user ID of the user's Twitter account."
  [handler opts]
  (let [{:keys [request-token-uri
                access-token-uri
                after-auth-uri]} opts
        consumer (make-consumer opts)
        pending-request-tokens (atom {})]
    (fn [req]
      (condp = (:uri req)
        ;; Step 1: Acquire a request token for the user. Then redirect them to
        ;; the "Sign in with Twitter" page on Twitter's servers.
        request-token-uri
        (let [callback-uri  (str (base-url req) access-token-uri)
              request-token (oauth/request-token consumer callback-uri)
              oauth-token   (:oauth_token request-token)
              approval-uri  (oauth/user-approval-uri consumer oauth-token)]
          (swap! pending-request-tokens assoc oauth-token request-token)
          (response/redirect approval-uri))
        ;; Step 2: Convert the request token into an access token. Store the
        ;; access token and other Twitter data in the user's session data under
        ;; the :twitter key. Then redirect the user to the :after-auth-uri.
        access-token-uri
        (let [params (:params req)]
          (if-let [denied (:denied params)]
            ;; the user decided not to sign in to Twitter
            (do (swap! pending-request-tokens dissoc denied)
                (response/redirect after-auth-uri))
            ;; the user decided to sign in to Twitter
            (let [oauth-token (:oauth_token params)]
              (if-let [request-token (get @pending-request-tokens oauth-token)]
                ;; this request corresponds to a pending request token
                (let [verifier     (:oauth_verifier params)
                      access-token (oauth/access-token consumer request-token verifier)
                      oauth-creds  (make-oauth-creds consumer access-token)]
                  (swap! pending-request-tokens dissoc oauth-token)
                  (-> (response/redirect after-auth-uri)
                      (assoc-in [:session :twitter]
                        {:oauth-creds oauth-creds
                         :screen-name (:screen_name access-token)
                         :user-id     (:user_id access-token)})))
                ;; this request doesn't correspond to a pending request token
                (do (println "No such request token!")
                    (response/redirect after-auth-uri))))))
        ;else
        (handler req)))))
