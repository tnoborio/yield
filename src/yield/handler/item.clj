(ns yield.handler.item
  (:require [integrant.core :as ig]
            [ring.util.response :as response]
            [clojure.data.json :as json]
            [yield.boundary.item :as item-db]))

(defn- json-response [body]
  (-> (response/response (json/write-str body))
      (response/content-type "application/json")))

(defn- parse-body [request]
  (some-> request :body slurp (json/read-str :key-fn keyword)))

(defn- require-auth [request]
  (get-in request [:session :user-id]))

(defn- format-item [item]
  (-> item
      (update :id str)
      (update :created-at str)
      (update :updated-at str)
      (update :due-date #(some-> % str))))

;; ── Handlers ────────────────────────────────────────────────────

(defn list-items [db request]
  (if-let [user-id (require-auth request)]
    (let [params (:query-params request)
          opts {:status   (get params "status")
                :category (get params "category")
                :list-id  (get params "list_id")}]
      (json-response {:items (mapv format-item (item-db/list-items db user-id opts))}))
    (-> (json-response {:error "Unauthorized"})
        (response/status 401))))

(defn create-item [db request]
  (if-let [user-id (require-auth request)]
    (let [{:keys [title description status category due_date list_id]} (parse-body request)]
      (if (or (nil? title) (empty? title))
        (-> (json-response {:error "Title is required"})
            (response/status 400))
        (if (nil? list_id)
          (-> (json-response {:error "list_id is required"})
              (response/status 400))
          (let [item (item-db/create-item! db user-id
                       {:title       title
                        :description description
                        :status      status
                        :category    category
                        :due-date    due_date
                        :list-id     list_id})]
            (-> (json-response {:item (format-item item)})
                (response/status 201))))))
    (-> (json-response {:error "Unauthorized"})
        (response/status 401))))

(defn update-item [db request]
  (if-let [user-id (require-auth request)]
    (let [item-id (get-in request [:path-params :id])
          item (item-db/find-item db item-id user-id)]
      (if item
        (let [body (parse-body request)
              {:keys [title description status category due_date]} body
              changes (cond-> {}
                        title       (assoc :title title)
                        description (assoc :description description)
                        status      (assoc :status status)
                        category    (assoc :category category)
                        (contains? body :due_date) (assoc :due-date due_date))
              updated (item-db/update-item! db item-id changes)]
          (json-response {:item (format-item (or updated item))}))
        (-> (json-response {:error "Not found"})
            (response/status 404))))
    (-> (json-response {:error "Unauthorized"})
        (response/status 401))))

(defn delete-item [db request]
  (if-let [user-id (require-auth request)]
    (let [item-id (get-in request [:path-params :id])
          item (item-db/find-item db item-id user-id)]
      (if item
        (do (item-db/delete-item! db item-id)
            (json-response {:ok true}))
        (-> (json-response {:error "Not found"})
            (response/status 404))))
    (-> (json-response {:error "Unauthorized"})
        (response/status 401))))

(defn reorder-items [db request]
  (if-let [user-id (require-auth request)]
    (let [{:keys [item_ids]} (parse-body request)]
      (if (seq item_ids)
        (do (item-db/reorder-items! db user-id item_ids)
            (json-response {:ok true}))
        (-> (json-response {:error "item_ids is required"})
            (response/status 400))))
    (-> (json-response {:error "Unauthorized"})
        (response/status 401))))

;; ── Integrant Keys ──────────────────────────────────────────────

(defmethod ig/init-key :yield.handler.item/list [_ {:keys [db]}]
  (fn [request] (list-items db request)))

(defmethod ig/init-key :yield.handler.item/create [_ {:keys [db]}]
  (fn [request] (create-item db request)))

(defmethod ig/init-key :yield.handler.item/update [_ {:keys [db]}]
  (fn [request] (update-item db request)))

(defmethod ig/init-key :yield.handler.item/delete [_ {:keys [db]}]
  (fn [request] (delete-item db request)))

(defmethod ig/init-key :yield.handler.item/reorder [_ {:keys [db]}]
  (fn [request] (reorder-items db request)))
