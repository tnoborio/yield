(ns yield.handler.todo-list
  (:require [integrant.core :as ig]
            [ring.util.response :as response]
            [clojure.data.json :as json]
            [yield.boundary.todo-list :as list-db]))

(defn- json-response [body]
  (-> (response/response (json/write-str body))
      (response/content-type "application/json")))

(defn- parse-body [request]
  (some-> request :body slurp (json/read-str :key-fn keyword)))

(defn- require-auth [request]
  (get-in request [:session :user-id]))

;; ── Handlers ────────────────────────────────────────────────────

(defn list-todo-lists [db request]
  (if-let [user-id (require-auth request)]
    (let [lists (list-db/list-todo-lists db user-id)]
      (json-response {:todo_lists (mapv #(update-vals % str) lists)}))
    (-> (json-response {:error "Unauthorized"})
        (response/status 401))))

(defn create-todo-list [db request]
  (if-let [user-id (require-auth request)]
    (let [{:keys [name]} (parse-body request)]
      (if (or (nil? name) (empty? name))
        (-> (json-response {:error "Name is required"})
            (response/status 400))
        (let [tl (list-db/create-todo-list! db user-id name)]
          (-> (json-response {:todo_list (update-vals tl str)})
              (response/status 201)))))
    (-> (json-response {:error "Unauthorized"})
        (response/status 401))))

(defn delete-todo-list [db request]
  (if-let [user-id (require-auth request)]
    (let [list-id (get-in request [:path-params :id])
          tl (list-db/find-todo-list db list-id user-id)]
      (if tl
        (do (list-db/delete-todo-list! db list-id)
            (json-response {:ok true}))
        (-> (json-response {:error "Not found"})
            (response/status 404))))
    (-> (json-response {:error "Unauthorized"})
        (response/status 401))))

;; ── Integrant Keys ──────────────────────────────────────────────

(defmethod ig/init-key :yield.handler.todo-list/list [_ {:keys [db]}]
  (fn [request] (list-todo-lists db request)))

(defmethod ig/init-key :yield.handler.todo-list/create [_ {:keys [db]}]
  (fn [request] (create-todo-list db request)))

(defmethod ig/init-key :yield.handler.todo-list/delete [_ {:keys [db]}]
  (fn [request] (delete-todo-list db request)))
