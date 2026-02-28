(ns yield.boundary.graph
  (:require [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]))

(defprotocol GraphDatabase
  (list-graphs [db user-id])
  (create-graph! [db user-id graph-name])
  (find-graph [db graph-id user-id])
  (delete-graph! [db graph-id])
  (get-nodes [db graph-id])
  (get-edges [db graph-id])
  (save-nodes! [db graph-id nodes])
  (save-edges! [db graph-id edges]))

(extend-protocol GraphDatabase
  javax.sql.DataSource
  (list-graphs [db user-id]
    (jdbc/execute! db
      ["SELECT id, name, created_at, updated_at FROM graphs WHERE user_id = ? ORDER BY created_at"
       (parse-uuid user-id)]
      {:builder-fn rs/as-unqualified-kebab-maps}))

  (create-graph! [db user-id graph-name]
    (jdbc/execute-one! db
      ["INSERT INTO graphs (user_id, name) VALUES (?, ?) RETURNING id, name, created_at, updated_at"
       (parse-uuid user-id) graph-name]
      {:builder-fn rs/as-unqualified-kebab-maps}))

  (find-graph [db graph-id user-id]
    (jdbc/execute-one! db
      ["SELECT id, name, user_id, created_at, updated_at FROM graphs WHERE id = ? AND user_id = ?"
       (parse-uuid graph-id) (parse-uuid user-id)]
      {:builder-fn rs/as-unqualified-kebab-maps}))

  (delete-graph! [db graph-id]
    (jdbc/execute-one! db
      ["DELETE FROM graphs WHERE id = ?" (parse-uuid graph-id)]))

  (get-nodes [db graph-id]
    (jdbc/execute! db
      ["SELECT node_id, type, position_x, position_y, label FROM nodes WHERE graph_id = ?"
       (parse-uuid graph-id)]
      {:builder-fn rs/as-unqualified-kebab-maps}))

  (get-edges [db graph-id]
    (jdbc/execute! db
      ["SELECT edge_id, source_node_id, target_node_id FROM edges WHERE graph_id = ?"
       (parse-uuid graph-id)]
      {:builder-fn rs/as-unqualified-kebab-maps}))

  (save-nodes! [db graph-id nodes]
    (let [gid (parse-uuid graph-id)]
      (jdbc/with-transaction [tx db]
        (jdbc/execute-one! tx ["DELETE FROM nodes WHERE graph_id = ?" gid])
        (doseq [{:keys [node-id type position-x position-y label]} nodes]
          (jdbc/execute-one! tx
            ["INSERT INTO nodes (graph_id, node_id, type, position_x, position_y, label) VALUES (?, ?, ?, ?, ?, ?)"
             gid node-id type position-x position-y label])))))

  (save-edges! [db graph-id edges]
    (let [gid (parse-uuid graph-id)]
      (jdbc/with-transaction [tx db]
        (jdbc/execute-one! tx ["DELETE FROM edges WHERE graph_id = ?" gid])
        (doseq [{:keys [edge-id source-node-id target-node-id]} edges]
          (jdbc/execute-one! tx
            ["INSERT INTO edges (graph_id, edge_id, source_node_id, target_node_id) VALUES (?, ?, ?, ?)"
             gid edge-id source-node-id target-node-id]))))))
