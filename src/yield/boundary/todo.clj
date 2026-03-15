(ns yield.boundary.todo
  (:require [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]
            [clojure.string :as str]))

(defprotocol TodoDatabase
  (list-todos [db user-id opts])
  (list-all-todos [db])
  (create-todo! [db user-id todo-data])
  (find-todo [db todo-id user-id])
  (find-todo-by-id [db todo-id])
  (update-todo! [db todo-id changes])
  (delete-todo! [db todo-id])
  (reorder-todos! [db user-id todo-ids]))

(defn- build-filter-clause [{:keys [status category]}]
  (let [clauses (cond-> []
                  status   (conj ["status = ?" status])
                  category (conj ["category = ?" category]))]
    (when (seq clauses)
      [(str " AND " (str/join " AND " (map first clauses)))
       (mapv second clauses)])))

(extend-protocol TodoDatabase
  javax.sql.DataSource
  (list-todos [db user-id opts]
    (let [[filter-sql filter-params] (build-filter-clause opts)
          list-id (:list-id opts)
          base-sql "SELECT id, title, description, status, category, due_date, position, created_at, updated_at FROM todos WHERE user_id = ?"
          [list-sql list-params] (if list-id
                                   [" AND list_id = ?" [(parse-uuid list-id)]]
                                   ["" []])
          sql (str base-sql list-sql filter-sql " ORDER BY position, created_at")
          params (into [(parse-uuid user-id)] (concat list-params filter-params))]
      (jdbc/execute! db (into [sql] params)
        {:builder-fn rs/as-unqualified-kebab-maps})))

  (list-all-todos [db]
    (jdbc/execute! db
      ["SELECT t.id, t.title, t.description, t.status, t.category, t.due_date, t.position, t.created_at, t.updated_at, u.email AS user_email
        FROM todos t JOIN users u ON u.id = t.user_id
        ORDER BY t.position, t.created_at"]
      {:builder-fn rs/as-unqualified-kebab-maps}))

  (create-todo! [db user-id todo-data]
    (let [uid (parse-uuid user-id)
          {:keys [title description status category due-date list-id]} todo-data
          lid (parse-uuid list-id)]
      (jdbc/execute-one! db
        [(str "INSERT INTO todos (user_id, list_id, title, description, status, category, due_date, position) "
              "VALUES (?, ?, ?, ?, ?, ?, ?, (SELECT COALESCE(MAX(position), 0) + 1 FROM todos WHERE list_id = ?)) "
              "RETURNING id, title, description, status, category, due_date, position, created_at, updated_at")
         uid
         lid
         title
         (or description "")
         (or status "ready")
         (or category "private")
         due-date
         lid]
        {:builder-fn rs/as-unqualified-kebab-maps})))

  (find-todo [db todo-id user-id]
    (jdbc/execute-one! db
      ["SELECT id, title, description, status, category, due_date, position, user_id, created_at, updated_at FROM todos WHERE id = ? AND user_id = ?"
       (parse-uuid todo-id) (parse-uuid user-id)]
      {:builder-fn rs/as-unqualified-kebab-maps}))

  (find-todo-by-id [db todo-id]
    (jdbc/execute-one! db
      ["SELECT id, title, description, status, category, due_date, position, user_id, created_at, updated_at FROM todos WHERE id = ?"
       (parse-uuid todo-id)]
      {:builder-fn rs/as-unqualified-kebab-maps}))

  (update-todo! [db todo-id changes]
    (let [field-map {:title "title" :description "description" :status "status"
                     :category "category" :due-date "due_date"}
          updates (keep (fn [[k v]]
                          (when-let [col (field-map k)]
                            [(str col " = ?") v]))
                        changes)]
      (when (seq updates)
        (let [set-clause (str (str/join ", " (map first updates)) ", updated_at = now()")
              params (mapv second updates)
              sql (str "UPDATE todos SET " set-clause " WHERE id = ? RETURNING id, title, description, status, category, due_date, position, created_at, updated_at")]
          (jdbc/execute-one! db
            (into [sql] (conj params (parse-uuid todo-id)))
            {:builder-fn rs/as-unqualified-kebab-maps})))))

  (delete-todo! [db todo-id]
    (jdbc/execute-one! db
      ["DELETE FROM todos WHERE id = ?" (parse-uuid todo-id)]))

  (reorder-todos! [db user-id todo-ids]
    (let [uid (parse-uuid user-id)]
      (jdbc/with-transaction [tx db]
        (doseq [[idx tid] (map-indexed vector todo-ids)]
          (jdbc/execute-one! tx
            ["UPDATE todos SET position = ?, updated_at = now() WHERE id = ? AND user_id = ?"
             idx (parse-uuid tid) uid]))))))
