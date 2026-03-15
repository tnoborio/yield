(ns yield.boundary.todo-list
  (:require [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]))

(defprotocol TodoListDatabase
  (list-todo-lists [db user-id])
  (list-all-todo-lists [db])
  (create-todo-list! [db user-id list-name])
  (find-todo-list [db list-id user-id])
  (find-todo-list-by-id [db list-id])
  (delete-todo-list! [db list-id]))

(extend-protocol TodoListDatabase
  javax.sql.DataSource
  (list-todo-lists [db user-id]
    (jdbc/execute! db
      ["SELECT id, name, created_at, updated_at FROM todo_lists WHERE user_id = ? ORDER BY created_at"
       (parse-uuid user-id)]
      {:builder-fn rs/as-unqualified-kebab-maps}))

  (list-all-todo-lists [db]
    (jdbc/execute! db
      ["SELECT tl.id, tl.name, tl.created_at, tl.updated_at, u.email AS user_email
        FROM todo_lists tl JOIN users u ON u.id = tl.user_id
        ORDER BY tl.created_at"]
      {:builder-fn rs/as-unqualified-kebab-maps}))

  (create-todo-list! [db user-id list-name]
    (jdbc/execute-one! db
      ["INSERT INTO todo_lists (user_id, name) VALUES (?, ?) RETURNING id, name, created_at, updated_at"
       (parse-uuid user-id) list-name]
      {:builder-fn rs/as-unqualified-kebab-maps}))

  (find-todo-list [db list-id user-id]
    (jdbc/execute-one! db
      ["SELECT id, name, user_id, created_at, updated_at FROM todo_lists WHERE id = ? AND user_id = ?"
       (parse-uuid list-id) (parse-uuid user-id)]
      {:builder-fn rs/as-unqualified-kebab-maps}))

  (find-todo-list-by-id [db list-id]
    (jdbc/execute-one! db
      ["SELECT id, name, user_id, created_at, updated_at FROM todo_lists WHERE id = ?"
       (parse-uuid list-id)]
      {:builder-fn rs/as-unqualified-kebab-maps}))

  (delete-todo-list! [db list-id]
    (jdbc/execute-one! db
      ["DELETE FROM todo_lists WHERE id = ?" (parse-uuid list-id)])))
