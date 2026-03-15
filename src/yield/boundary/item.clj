(ns yield.boundary.item
  (:require [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]
            [clojure.string :as str]))

(defprotocol ItemDatabase
  (list-items [db user-id opts])
  (list-all-items [db])
  (create-item! [db user-id item-data])
  (find-item [db item-id user-id])
  (find-item-by-id [db item-id])
  (update-item! [db item-id changes])
  (delete-item! [db item-id])
  (reorder-items! [db user-id item-ids]))

(defn- build-filter-clause [{:keys [status category]}]
  (let [clauses (cond-> []
                  status   (conj ["status = ?" status])
                  category (conj ["category = ?" category]))]
    (when (seq clauses)
      [(str " AND " (str/join " AND " (map first clauses)))
       (mapv second clauses)])))

(extend-protocol ItemDatabase
  javax.sql.DataSource
  (list-items [db user-id opts]
    (let [[filter-sql filter-params] (build-filter-clause opts)
          list-id (:list-id opts)
          base-sql "SELECT id, title, description, status, category, due_date, position, created_at, updated_at FROM items WHERE user_id = ?"
          [list-sql list-params] (if list-id
                                   [" AND list_id = ?" [(parse-uuid list-id)]]
                                   ["" []])
          sql (str base-sql list-sql filter-sql " ORDER BY position, created_at")
          params (into [(parse-uuid user-id)] (concat list-params filter-params))]
      (jdbc/execute! db (into [sql] params)
        {:builder-fn rs/as-unqualified-kebab-maps})))

  (list-all-items [db]
    (jdbc/execute! db
      ["SELECT i.id, i.title, i.description, i.status, i.category, i.due_date, i.position, i.created_at, i.updated_at, u.email AS user_email
        FROM items i JOIN users u ON u.id = i.user_id
        ORDER BY i.position, i.created_at"]
      {:builder-fn rs/as-unqualified-kebab-maps}))

  (create-item! [db user-id item-data]
    (let [uid (parse-uuid user-id)
          {:keys [title description status category due-date list-id]} item-data
          lid (parse-uuid list-id)]
      (jdbc/execute-one! db
        [(str "INSERT INTO items (user_id, list_id, title, description, status, category, due_date, position) "
              "VALUES (?, ?, ?, ?, ?, ?, ?, (SELECT COALESCE(MAX(position), 0) + 1 FROM items WHERE list_id = ?)) "
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

  (find-item [db item-id user-id]
    (jdbc/execute-one! db
      ["SELECT id, title, description, status, category, due_date, position, user_id, created_at, updated_at FROM items WHERE id = ? AND user_id = ?"
       (parse-uuid item-id) (parse-uuid user-id)]
      {:builder-fn rs/as-unqualified-kebab-maps}))

  (find-item-by-id [db item-id]
    (jdbc/execute-one! db
      ["SELECT id, title, description, status, category, due_date, position, user_id, created_at, updated_at FROM items WHERE id = ?"
       (parse-uuid item-id)]
      {:builder-fn rs/as-unqualified-kebab-maps}))

  (update-item! [db item-id changes]
    (let [field-map {:title "title" :description "description" :status "status"
                     :category "category" :due-date "due_date"}
          updates (keep (fn [[k v]]
                          (when-let [col (field-map k)]
                            [(str col " = ?") v]))
                        changes)]
      (when (seq updates)
        (let [set-clause (str (str/join ", " (map first updates)) ", updated_at = now()")
              params (mapv second updates)
              sql (str "UPDATE items SET " set-clause " WHERE id = ? RETURNING id, title, description, status, category, due_date, position, created_at, updated_at")]
          (jdbc/execute-one! db
            (into [sql] (conj params (parse-uuid item-id)))
            {:builder-fn rs/as-unqualified-kebab-maps})))))

  (delete-item! [db item-id]
    (jdbc/execute-one! db
      ["DELETE FROM items WHERE id = ?" (parse-uuid item-id)]))

  (reorder-items! [db user-id item-ids]
    (let [uid (parse-uuid user-id)]
      (jdbc/with-transaction [tx db]
        (doseq [[idx iid] (map-indexed vector item-ids)]
          (jdbc/execute-one! tx
            ["UPDATE items SET position = ?, updated_at = now() WHERE id = ? AND user_id = ?"
             idx (parse-uuid iid) uid]))))))
