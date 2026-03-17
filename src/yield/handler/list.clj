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

(defn- pgobj->clj [v]
  (if (instance? org.postgresql.util.PGobject v)
    (json/read-str (.getValue v) :key-fn keyword)
    v))

(defn- serialize-list [l]
  (-> (update-vals l str)
      (assoc :settings (pgobj->clj (:settings l)))))

;; ── Handlers ────────────────────────────────────────────────────

(defn list-lists [db request]
  (if-let [user-id (require-auth request)]
    (let [lists (list-db/list-lists db user-id)]
      (json-response {:lists (mapv serialize-list lists)}))
    (-> (json-response {:error "Unauthorized"})
        (response/status 401))))

(defn create-list [db request]
  (if-let [user-id (require-auth request)]
    (let [{:keys [name]} (parse-body request)]
      (if (or (nil? name) (empty? name))
        (-> (json-response {:error "Name is required"})
            (response/status 400))
        (let [l (list-db/create-list! db user-id name)]
          (-> (json-response {:list (serialize-list l)})
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

(defn update-settings [db request]
  (if-let [user-id (require-auth request)]
    (let [list-id (get-in request [:path-params :id])
          {:keys [settings]} (parse-body request)]
      (if (nil? settings)
        (-> (json-response {:error "Settings is required"})
            (response/status 400))
        (let [settings-str (if (string? settings) settings (json/write-str settings))]
          ;; Validate JSON by parsing it
          (try
            (json/read-str settings-str)
            (if-let [l (list-db/update-list-settings! db list-id user-id settings-str)]
              (json-response {:list (serialize-list l)})
              (-> (json-response {:error "Not found"})
                  (response/status 404)))
            (catch Exception _
              (-> (json-response {:error "Invalid JSON"})
                  (response/status 400)))))))
    (-> (json-response {:error "Unauthorized"})
        (response/status 401))))

;; ── Integrant Keys ──────────────────────────────────────────────

(defmethod ig/init-key :yield.handler.list/list [_ {:keys [db]}]
  (fn [request] (list-lists db request)))

(defmethod ig/init-key :yield.handler.list/create [_ {:keys [db]}]
  (fn [request] (create-list db request)))

(defmethod ig/init-key :yield.handler.list/delete [_ {:keys [db]}]
  (fn [request] (delete-list db request)))

(defmethod ig/init-key :yield.handler.list/update-settings [_ {:keys [db]}]
  (fn [request] (update-settings db request)))
