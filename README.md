# twitter-oauth

[Ring](https://github.com/ring-clojure/ring) middleware for the [Twitter OAuth sign-in flow](https://dev.twitter.com/web/sign-in/implementing). Use with [twitter-api](https://github.com/adamwynne/twitter-api) for best results.

## Installation

This project isn't on Clojars yet, so you'll have to download it and `lein install` the repo locally. Then add it to your `project.clj`'s `:dependencies`:

```clojure
[twitter-oauth.middleware "0.0-SNAPSHOT"]
```

## Usage

You probably don't want to use this yet.

First, if you haven't already, create an application at [apps.twitter.com](https://apps.twitter.com/) and grab the consumer key and consumer secret from there. Then:

```clojure
(ns whatever
  (:require [compojure.handler]
            [ring.middleware.session]
            [twitter-oauth.middleware]))

...

(-> my-ring-handler ;; this can be a Compojure `defroutes` or whatever else you want
    (compojure.handler/site) ;; or any other middleware that provides a :params map with keyword keys
    (ring.middleware.session/wrap-session)
    (twitter-oauth.middleware/wrap-twitter-oauth
      {:consumer-key      "consumer key"
       :consumer-secret   "consumer secret"
       :request-token-uri "/sign-in"
       :access-token-uri  "/oauth-callback"
       :after-auth-uri    "/"}))
```
