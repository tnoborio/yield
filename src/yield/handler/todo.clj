(ns yield.handler.todo
  (:require [integrant.core :as ig]
            [ring.util.response :as response]
            [clojure.data.json :as json]
            [yield.boundary.todo :as todo-db]))

(defn- json-response [body]
  (-> (response/response (json/write-str body))
      (response/content-type "application/json")))

(defn- parse-body [request]
  (some-> request :body slurp (json/read-str :key-fn keyword)))

(defn- require-auth [request]
  (get-in request [:session :user-id]))

(defn- format-todo [todo]
  (-> todo
      (update :id str)
      (update :created-at str)
      (update :updated-at str)
      (update :due-date #(some-> % str))))

;; ── Handlers ────────────────────────────────────────────────────

(defn list-todos [db request]
  (if-let [user-id (require-auth request)]
    (let [params (:query-params request)
          opts {:status   (get params "status")
                :category (get params "category")}]
      (json-response {:todos (mapv format-todo (todo-db/list-todos db user-id opts))}))
    (-> (json-response {:error "Unauthorized"})
        (response/status 401))))

(defn create-todo [db request]
  (if-let [user-id (require-auth request)]
    (let [{:keys [title description status category due_date]} (parse-body request)]
      (if (or (nil? title) (empty? title))
        (-> (json-response {:error "Title is required"})
            (response/status 400))
        (let [todo (todo-db/create-todo! db user-id
                     {:title       title
                      :description description
                      :status      status
                      :category    category
                      :due-date    due_date})]
          (-> (json-response {:todo (format-todo todo)})
              (response/status 201)))))
    (-> (json-response {:error "Unauthorized"})
        (response/status 401))))

(defn update-todo [db request]
  (if-let [user-id (require-auth request)]
    (let [todo-id (get-in request [:path-params :id])
          todo (todo-db/find-todo db todo-id user-id)]
      (if todo
        (let [body (parse-body request)
              {:keys [title description status category due_date]} body
              changes (cond-> {}
                        title       (assoc :title title)
                        description (assoc :description description)
                        status      (assoc :status status)
                        category    (assoc :category category)
                        (contains? body :due_date) (assoc :due-date due_date))
              updated (todo-db/update-todo! db todo-id changes)]
          (json-response {:todo (format-todo (or updated todo))}))
        (-> (json-response {:error "Not found"})
            (response/status 404))))
    (-> (json-response {:error "Unauthorized"})
        (response/status 401))))

(defn delete-todo [db request]
  (if-let [user-id (require-auth request)]
    (let [todo-id (get-in request [:path-params :id])
          todo (todo-db/find-todo db todo-id user-id)]
      (if todo
        (do (todo-db/delete-todo! db todo-id)
            (json-response {:ok true}))
        (-> (json-response {:error "Not found"})
            (response/status 404))))
    (-> (json-response {:error "Unauthorized"})
        (response/status 401))))

(defn reorder-todos [db request]
  (if-let [user-id (require-auth request)]
    (let [{:keys [todo_ids]} (parse-body request)]
      (if (seq todo_ids)
        (do (todo-db/reorder-todos! db user-id todo_ids)
            (json-response {:ok true}))
        (-> (json-response {:error "todo_ids is required"})
            (response/status 400))))
    (-> (json-response {:error "Unauthorized"})
        (response/status 401))))

;; ── Integrant Keys ──────────────────────────────────────────────

(defmethod ig/init-key :yield.handler.todo/list [_ {:keys [db]}]
  (fn [request] (list-todos db request)))

(defmethod ig/init-key :yield.handler.todo/create [_ {:keys [db]}]
  (fn [request] (create-todo db request)))

(defmethod ig/init-key :yield.handler.todo/update [_ {:keys [db]}]
  (fn [request] (update-todo db request)))

(defmethod ig/init-key :yield.handler.todo/delete [_ {:keys [db]}]
  (fn [request] (delete-todo db request)))

(defmethod ig/init-key :yield.handler.todo/reorder [_ {:keys [db]}]
  (fn [request] (reorder-todos db request)))
