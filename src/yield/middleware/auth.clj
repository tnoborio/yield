(ns yield.middleware.auth
  (:require [integrant.core :as ig]
            [ring.util.response :as response]))

(defn wrap-auth
  [handler]
  (fn [request]
    (if (get-in request [:session :user-id])
      (handler request)
      (response/redirect "/login"))))

(defmethod ig/init-key :yield.middleware/auth [_ _]
  wrap-auth)
