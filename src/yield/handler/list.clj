(ns yield.handler.list
  (:require [integrant.core :as ig]
            [ring.util.response :as response]
            [clojure.data.json :as json]
            [yield.boundary.list :as list-db]))

(defn- json-response [body]
  (-> (response/response (json/write-str body))
      (response/content-type "application/json")))

(defn- parse-body [request]
  (some-> request :body slurp (json/read-str :key-fn keyword)))

(defn- require-auth [request]
  (get-in request [:session :user-id]))

;; ── Handlers ────────────────────────────────────────────────────

(defn list-lists [db request]
  (if-let [user-id (require-auth request)]
    (let [lists (list-db/list-lists db user-id)]
      (json-response {:lists (mapv #(update-vals % str) lists)}))
    (-> (json-response {:error "Unauthorized"})
        (response/status 401))))

(defn create-list [db request]
  (if-let [user-id (require-auth request)]
    (let [{:keys [name]} (parse-body request)]
      (if (or (nil? name) (empty? name))
        (-> (json-response {:error "Name is required"})
            (response/status 400))
        (let [l (list-db/create-list! db user-id name)]
          (-> (json-response {:list (update-vals l str)})
              (response/status 201)))))
    (-> (json-response {:error "Unauthorized"})
        (response/status 401))))

(defn delete-list [db request]
  (if-let [user-id (require-auth request)]
    (let [list-id (get-in request [:path-params :id])
          l (list-db/find-list db list-id user-id)]
      (if l
        (do (list-db/delete-list! db list-id)
            (json-response {:ok true}))
        (-> (json-response {:error "Not found"})
            (response/status 404))))
    (-> (json-response {:error "Unauthorized"})
        (response/status 401))))

;; ── Integrant Keys ──────────────────────────────────────────────

(defmethod ig/init-key :yield.handler.list/list [_ {:keys [db]}]
  (fn [request] (list-lists db request)))

(defmethod ig/init-key :yield.handler.list/create [_ {:keys [db]}]
  (fn [request] (create-list db request)))

(defmethod ig/init-key :yield.handler.list/delete [_ {:keys [db]}]
  (fn [request] (delete-list db request)))
