(ns yield.boundary.list
  (:require [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]))

(defprotocol ListDatabase
  (list-lists [db user-id])
  (list-all-lists [db])
  (create-list! [db user-id list-name])
  (find-list [db list-id user-id])
  (find-list-by-id [db list-id])
  (delete-list! [db list-id])
  (update-list-settings! [db list-id user-id settings-json]))

(extend-protocol ListDatabase
  javax.sql.DataSource
  (list-lists [db user-id]
    (jdbc/execute! db
      ["SELECT id, name, settings, created_at, updated_at FROM lists WHERE user_id = ? ORDER BY created_at"
       (parse-uuid user-id)]
      {:builder-fn rs/as-unqualified-kebab-maps}))

  (list-all-lists [db]
    (jdbc/execute! db
      ["SELECT l.id, l.name, l.settings, l.created_at, l.updated_at, u.email AS user_email
        FROM lists l JOIN users u ON u.id = l.user_id
        ORDER BY l.created_at"]
      {:builder-fn rs/as-unqualified-kebab-maps}))

  (create-list! [db user-id list-name]
    (jdbc/execute-one! db
      ["INSERT INTO lists (user_id, name) VALUES (?, ?) RETURNING id, name, settings, created_at, updated_at"
       (parse-uuid user-id) list-name]
      {:builder-fn rs/as-unqualified-kebab-maps}))

  (find-list [db list-id user-id]
    (jdbc/execute-one! db
      ["SELECT id, name, settings, user_id, created_at, updated_at FROM lists WHERE id = ? AND user_id = ?"
       (parse-uuid list-id) (parse-uuid user-id)]
      {:builder-fn rs/as-unqualified-kebab-maps}))

  (find-list-by-id [db list-id]
    (jdbc/execute-one! db
      ["SELECT id, name, settings, user_id, created_at, updated_at FROM lists WHERE id = ?"
       (parse-uuid list-id)]
      {:builder-fn rs/as-unqualified-kebab-maps}))

  (delete-list! [db list-id]
    (jdbc/execute-one! db
      ["DELETE FROM lists WHERE id = ?" (parse-uuid list-id)]))

  (update-list-settings! [db list-id user-id settings-json]
    (jdbc/execute-one! db
      ["UPDATE lists SET settings = ?::jsonb, updated_at = now() WHERE id = ? AND user_id = ? RETURNING id, name, settings, created_at, updated_at"
       settings-json (parse-uuid list-id) (parse-uuid user-id)]
      {:builder-fn rs/as-unqualified-kebab-maps})))
