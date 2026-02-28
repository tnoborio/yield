(ns yield.auth
  (:require [buddy.hashers :as hashers])
  (:import [java.util UUID]
           [java.time Instant]))

(defn hash-password [password]
  (hashers/derive password {:alg :bcrypt+sha512}))

(defn check-password [password hash]
  (hashers/check password hash))

(defn generate-reset-token []
  (str (UUID/randomUUID)))

(defn reset-token-expiry []
  (.plusSeconds (Instant/now) 3600))
