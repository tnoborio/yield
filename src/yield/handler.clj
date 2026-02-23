(ns yield.handler
  (:require [integrant.core :as ig]))

(defn index [_request]
  {:status 200
   :headers {"content-type" "application/json"}
   :body "{\"message\":\"okaa\"}"})

(defmethod ig/init-key :yield.handler/index [_ _]
  #'index)
