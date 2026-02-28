(ns yield.handler.graph
  (:require [integrant.core :as ig]
            [ring.util.response :as response]
            [clojure.data.json :as json]
            [yield.boundary.graph :as graph-db]))

(defn- json-response [body]
  (-> (response/response (json/write-str body))
      (response/content-type "application/json")))

(defn- parse-body [request]
  (some-> request :body slurp (json/read-str :key-fn keyword)))

(defn- require-auth [request]
  (get-in request [:session :user-id]))

;; ── Handlers ────────────────────────────────────────────────────

(defn list-graphs [db request]
  (if-let [user-id (require-auth request)]
    (let [graphs (graph-db/list-graphs db user-id)]
      (json-response {:graphs (mapv #(update-vals % str) graphs)}))
    (-> (json-response {:error "Unauthorized"})
        (response/status 401))))

(defn create-graph [db request]
  (if-let [user-id (require-auth request)]
    (let [{:keys [name]} (parse-body request)]
      (if (or (nil? name) (empty? name))
        (-> (json-response {:error "Name is required"})
            (response/status 400))
        (let [graph (graph-db/create-graph! db user-id name)]
          (-> (json-response {:graph (update-vals graph str)})
              (response/status 201)))))
    (-> (json-response {:error "Unauthorized"})
        (response/status 401))))

(defn get-data [db request]
  (if-let [user-id (require-auth request)]
    (let [graph-id (get-in request [:path-params :id])
          graph (graph-db/find-graph db graph-id user-id)]
      (if graph
        (let [nodes (graph-db/get-nodes db graph-id)
              edges (graph-db/get-edges db graph-id)]
          (json-response {:nodes nodes :edges edges}))
        (-> (json-response {:error "Not found"})
            (response/status 404))))
    (-> (json-response {:error "Unauthorized"})
        (response/status 401))))

(defn delete-graph [db request]
  (if-let [user-id (require-auth request)]
    (let [graph-id (get-in request [:path-params :id])
          graph (graph-db/find-graph db graph-id user-id)]
      (if graph
        (do (graph-db/delete-graph! db graph-id)
            (json-response {:ok true}))
        (-> (json-response {:error "Not found"})
            (response/status 404))))
    (-> (json-response {:error "Unauthorized"})
        (response/status 401))))

(defn save-data [db request]
  (if-let [user-id (require-auth request)]
    (let [graph-id (get-in request [:path-params :id])
          graph (graph-db/find-graph db graph-id user-id)]
      (if graph
        (let [{:keys [nodes edges]} (parse-body request)]
          (graph-db/save-nodes! db graph-id nodes)
          (graph-db/save-edges! db graph-id edges)
          (json-response {:ok true}))
        (-> (json-response {:error "Not found"})
            (response/status 404))))
    (-> (json-response {:error "Unauthorized"})
        (response/status 401))))

;; ── Integrant Keys ──────────────────────────────────────────────

(defmethod ig/init-key :yield.handler.graph/list [_ {:keys [db]}]
  (fn [request] (list-graphs db request)))

(defmethod ig/init-key :yield.handler.graph/create [_ {:keys [db]}]
  (fn [request] (create-graph db request)))

(defmethod ig/init-key :yield.handler.graph/get-data [_ {:keys [db]}]
  (fn [request] (get-data db request)))

(defmethod ig/init-key :yield.handler.graph/delete [_ {:keys [db]}]
  (fn [request] (delete-graph db request)))

(defmethod ig/init-key :yield.handler.graph/save-data [_ {:keys [db]}]
  (fn [request] (save-data db request)))
