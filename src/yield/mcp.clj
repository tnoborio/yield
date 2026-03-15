(ns yield.mcp
  (:require [org.clojars.roklenarcic.mcp-server.server :as server]
            [org.clojars.roklenarcic.mcp-server.json.charred :as mcp-json]
            [org.clojars.roklenarcic.mcp-server.core :as core]
            [org.clojars.roklenarcic.mcp-server.handler.init :as h.init]
            [next.jdbc :as jdbc]
            [yield.boundary.graph :as graph-db]
            [yield.boundary.item :as item-db]
            [yield.boundary.list :as list-db]
            [yield.boundary.user :as user-db]
            [clojure.data.json :as json]
            [clojure.string :as str])
  (:gen-class))

;; Patch: mcp-server 0.2.14 does not support protocol version 2025-11-25
;; that Claude Desktop sends. Add it to the allowed set.
(alter-var-root #'h.init/allowed-protocol-versions conj "2025-11-25")

(defn- make-datasource []
  (jdbc/get-datasource {:jdbcUrl (System/getenv "JDBC_DATABASE_URL")}))

(defn- underscore->kebab [m]
  (into {} (map (fn [[k v]]
                  [(keyword (str/replace (name k) "_" "-")) v])
                m)))

(defn- normalize-node [node]
  (merge {:type "editable" :label ""} (underscore->kebab node)))

(defn- format-record [record]
  (update-vals record str))

;; ── Graph Tool Handlers ──────────────────────────────────────

(defn- handle-list-graphs [db _exchange _args]
  (let [graphs (graph-db/list-all-graphs db)]
    (json/write-str {:graphs (mapv format-record graphs)})))

(defn- handle-get-graph [db _exchange {:keys [graph_id]}]
  (if-let [graph (graph-db/find-graph-by-id db graph_id)]
    (let [nodes (graph-db/get-nodes db graph_id)
          edges (graph-db/get-edges db graph_id)]
      (json/write-str {:graph  (format-record graph)
                       :nodes  nodes
                       :edges  edges}))
    (core/tool-error (str "Graph not found: " graph_id))))

(defn- handle-create-graph [db _exchange {:keys [user_email name]}]
  (if-let [user (user-db/find-user-by-email db user_email)]
    (let [graph (graph-db/create-graph! db (str (:id user)) name)]
      (json/write-str {:graph (format-record graph)}))
    (core/tool-error (str "User not found: " user_email))))

(defn- handle-save-graph [db _exchange {:keys [graph_id nodes edges]}]
  (if-let [_graph (graph-db/find-graph-by-id db graph_id)]
    (do
      (graph-db/save-nodes! db graph_id (mapv normalize-node nodes))
      (graph-db/save-edges! db graph_id (mapv underscore->kebab edges))
      (json/write-str {:ok true}))
    (core/tool-error (str "Graph not found: " graph_id))))

(defn- handle-delete-graph [db _exchange {:keys [graph_id]}]
  (if-let [_graph (graph-db/find-graph-by-id db graph_id)]
    (do
      (graph-db/delete-graph! db graph_id)
      (json/write-str {:ok true :deleted graph_id}))
    (core/tool-error (str "Graph not found: " graph_id))))

;; ── List Tool Handlers ──────────────────────────────────────

(defn- handle-list-lists [db _exchange {:keys [user_email]}]
  (if user_email
    (if-let [user (user-db/find-user-by-email db user_email)]
      (let [lists (list-db/list-lists db (str (:id user)))]
        (json/write-str {:lists (mapv format-record lists)}))
      (core/tool-error (str "User not found: " user_email)))
    (let [lists (list-db/list-all-lists db)]
      (json/write-str {:lists (mapv format-record lists)}))))

(defn- handle-create-list [db _exchange {:keys [user_email name]}]
  (if-let [user (user-db/find-user-by-email db user_email)]
    (let [l (list-db/create-list! db (str (:id user)) name)]
      (json/write-str {:list (format-record l)}))
    (core/tool-error (str "User not found: " user_email))))

(defn- handle-delete-list [db _exchange {:keys [list_id]}]
  (if-let [_l (list-db/find-list-by-id db list_id)]
    (do
      (list-db/delete-list! db list_id)
      (json/write-str {:ok true :deleted list_id}))
    (core/tool-error (str "List not found: " list_id))))

;; ── Item Tool Handlers ───────────────────────────────────────

(defn- format-item [item]
  (update-vals item str))

(defn- handle-list-items [db _exchange {:keys [user_email list_id]}]
  (if user_email
    (if-let [user (user-db/find-user-by-email db user_email)]
      (let [opts (cond-> {}
                   list_id (assoc :list-id list_id))
            items (item-db/list-items db (str (:id user)) opts)]
        (json/write-str {:items (mapv format-item items)}))
      (core/tool-error (str "User not found: " user_email)))
    (let [items (item-db/list-all-items db)]
      (json/write-str {:items (mapv format-item items)}))))

(defn- handle-create-item [db _exchange {:keys [user_email list_id title description status category due_date]}]
  (if-let [user (user-db/find-user-by-email db user_email)]
    (if (nil? list_id)
      (core/tool-error "list_id is required")
      (let [item (item-db/create-item! db (str (:id user))
                  {:title       title
                   :description description
                   :status      status
                   :category    category
                   :due-date    due_date
                   :list-id     list_id})]
        (json/write-str {:item (format-item item)})))
    (core/tool-error (str "User not found: " user_email))))

(defn- handle-update-item [db _exchange {:keys [item_id title description status category due_date]}]
  (if-let [_item (item-db/find-item-by-id db item_id)]
    (let [changes (cond-> {}
                    title       (assoc :title title)
                    description (assoc :description description)
                    status      (assoc :status status)
                    category    (assoc :category category)
                    due_date    (assoc :due-date due_date))
          updated (item-db/update-item! db item_id changes)]
      (json/write-str {:item (format-item (or updated _item))}))
    (core/tool-error (str "Item not found: " item_id))))

(defn- handle-delete-item [db _exchange {:keys [item_id]}]
  (if-let [_item (item-db/find-item-by-id db item_id)]
    (do
      (item-db/delete-item! db item_id)
      (json/write-str {:ok true :deleted item_id}))
    (core/tool-error (str "Item not found: " item_id))))

;; ── Tool Definitions ───────────────────────────────────────────

(defn- make-tools [db]
  [(server/tool
    "list_graphs"
    "List all graphs with their names, IDs, and owner emails"
    (server/obj-schema nil {} [])
    (partial handle-list-graphs db))

   (server/tool
    "get_graph"
    "Get a graph's nodes and edges by graph ID"
    (server/obj-schema nil
      {:graph_id (server/str-schema "UUID of the graph" nil)}
      ["graph_id"])
    (partial handle-get-graph db))

   (server/tool
    "create_graph"
    "Create a new graph for a user"
    (server/obj-schema nil
      {:user_email (server/str-schema "Email of the user who owns the graph" nil)
       :name       (server/str-schema "Name of the graph" nil)}
      ["user_email" "name"])
    (partial handle-create-graph db))

   (server/tool
    "save_graph"
    "Save/update a graph's nodes and edges. Replaces all existing nodes and edges."
    {"type"       "object"
     "properties" {"graph_id" {"type" "string" "description" "UUID of the graph"}
                   "nodes"    {"type"        "array"
                               "description" "Array of node objects"
                               "items"       {"type"       "object"
                                              "properties" {"node_id"    {"type" "string" "description" "Unique node identifier (e.g. node-1)"}
                                                            "type"       {"type" "string" "description" "Node type (default: editable)"}
                                                            "position_x" {"type" "number" "description" "X coordinate on canvas"}
                                                            "position_y" {"type" "number" "description" "Y coordinate on canvas"}
                                                            "label"      {"type" "string" "description" "Node display label"}}
                                              "required"   ["node_id" "position_x" "position_y"]}}
                   "edges"    {"type"        "array"
                               "description" "Array of edge objects"
                               "items"       {"type"       "object"
                                              "properties" {"edge_id"        {"type" "string" "description" "Unique edge identifier (e.g. edge-1)"}
                                                            "source_node_id" {"type" "string" "description" "Source node ID"}
                                                            "target_node_id" {"type" "string" "description" "Target node ID"}}
                                              "required"   ["edge_id" "source_node_id" "target_node_id"]}}}
     "required"   ["graph_id" "nodes" "edges"]}
    (partial handle-save-graph db))

   (server/tool
    "delete_graph"
    "Delete a graph and all its nodes and edges"
    (server/obj-schema nil
      {:graph_id (server/str-schema "UUID of the graph to delete" nil)}
      ["graph_id"])
    (partial handle-delete-graph db))

   ;; List tools
   (server/tool
    "list_lists"
    "List all lists. Optionally filter by user email."
    (server/obj-schema nil
      {:user_email (server/str-schema "Email of the user (optional, lists all if omitted)" nil)}
      [])
    (partial handle-list-lists db))

   (server/tool
    "create_list"
    "Create a new list for a user"
    (server/obj-schema nil
      {:user_email (server/str-schema "Email of the user" nil)
       :name       (server/str-schema "Name of the list" nil)}
      ["user_email" "name"])
    (partial handle-create-list db))

   (server/tool
    "delete_list"
    "Delete a list and all its items"
    (server/obj-schema nil
      {:list_id (server/str-schema "UUID of the list to delete" nil)}
      ["list_id"])
    (partial handle-delete-list db))

   ;; Item tools
   (server/tool
    "list_items"
    "List all items. Optionally filter by user email and/or list ID."
    (server/obj-schema nil
      {:user_email (server/str-schema "Email of the user (optional, lists all if omitted)" nil)
       :list_id    (server/str-schema "UUID of the list to filter by (optional)" nil)}
      [])
    (partial handle-list-items db))

   (server/tool
    "create_item"
    "Create a new item in a specific list"
    (server/obj-schema nil
      {:user_email  (server/str-schema "Email of the user" nil)
       :list_id     (server/str-schema "UUID of the list to add the item to" nil)
       :title       (server/str-schema "Title of the item" nil)
       :description (server/str-schema "Description (optional)" nil)
       :status      (server/str-schema "Status: done, ready, in_progress, wait, reject, arts (default: ready)" nil)
       :category    (server/str-schema "Category: private, keystone, sasara, contract, toeic (default: private)" nil)
       :due_date    (server/str-schema "Due date in YYYY-MM-DD format (optional)" nil)}
      ["user_email" "list_id" "title"])
    (partial handle-create-item db))

   (server/tool
    "update_item"
    "Update an existing item. Only provided fields are updated."
    (server/obj-schema nil
      {:item_id     (server/str-schema "UUID of the item to update" nil)
       :title       (server/str-schema "New title (optional)" nil)
       :description (server/str-schema "New description (optional)" nil)
       :status      (server/str-schema "New status: done, ready, in_progress, wait, reject, arts (optional)" nil)
       :category    (server/str-schema "New category: private, keystone, sasara, contract, toeic (optional)" nil)
       :due_date    (server/str-schema "New due date in YYYY-MM-DD format (optional)" nil)}
      ["item_id"])
    (partial handle-update-item db))

   (server/tool
    "delete_item"
    "Delete an item"
    (server/obj-schema nil
      {:item_id (server/str-schema "UUID of the item to delete" nil)}
      ["item_id"])
    (partial handle-delete-item db))])

;; ── Main ───────────────────────────────────────────────────────

(defn -main [& _args]
  (let [db      (make-datasource)
        serde   (mcp-json/serde {})
        info    (server/server-info
                 "Yield" "1.2.0"
                 "MCP server for Yield. Manage graphs (nodes/edges) and lists with items (tasks with status, category, due dates).")
        session (reduce server/add-tool
                        (server/make-session info serde {})
                        (make-tools db))]
    (server/start-server-on-streams session System/in System/out {})))
