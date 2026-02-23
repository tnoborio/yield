(ns yield.handler
  (:require [integrant.core :as ig]))

(defmethod ig/init-key :yield.handler/index [_ _]
  (fn [_request]
    {:status 200
     :headers {"content-type" "application/json"}
     :body "{\"message\":\"ok\"}"}))
