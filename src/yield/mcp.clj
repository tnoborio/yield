(ns yield.mcp
  (:require [org.clojars.roklenarcic.mcp-server.server :as server]
            [org.clojars.roklenarcic.mcp-server.json.charred :as mcp-json]
            [org.clojars.roklenarcic.mcp-server.core :as core]
            [org.clojars.roklenarcic.mcp-server.handler.init :as h.init]
            [next.jdbc :as jdbc]
            [yield.boundary.graph :as graph-db]
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

(defn- format-graph [graph]
  (update-vals graph str))

;; ── Tool Handlers ──────────────────────────────────────────────

(defn- handle-list-graphs [db _exchange _args]
  (let [graphs (graph-db/list-all-graphs db)]
    (json/write-str {:graphs (mapv format-graph graphs)})))

(defn- handle-get-graph [db _exchange {:keys [graph_id]}]
  (if-let [graph (graph-db/find-graph-by-id db graph_id)]
    (let [nodes (graph-db/get-nodes db graph_id)
          edges (graph-db/get-edges db graph_id)]
      (json/write-str {:graph  (format-graph graph)
                       :nodes  nodes
                       :edges  edges}))
    (core/tool-error (str "Graph not found: " graph_id))))

(defn- handle-create-graph [db _exchange {:keys [user_email name]}]
  (if-let [user (user-db/find-user-by-email db user_email)]
    (let [graph (graph-db/create-graph! db (str (:id user)) name)]
      (json/write-str {:graph (format-graph graph)}))
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
    (partial handle-delete-graph db))])

;; ── Main ───────────────────────────────────────────────────────

(defn -main [& _args]
  (let [db      (make-datasource)
        serde   (mcp-json/serde {})
        info    (server/server-info
                 "Yield Graph Editor" "1.0.0"
                 "MCP server for the Yield graph editor. Read, create, and modify graphs with nodes and edges.")
        session (reduce server/add-tool
                        (server/make-session info serde {})
                        (make-tools db))]
    (server/start-server-on-streams session System/in System/out {})))
