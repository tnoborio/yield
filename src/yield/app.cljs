(ns yield.app
  (:require [reagent.core :as r]
            [reagent.dom.client :as rdc]
            ["@xyflow/react" :refer [ReactFlow Background Controls
                                     applyNodeChanges applyEdgeChanges addEdge]]
            ["@xyflow/react/dist/style.css"]))

(def initial-nodes
  #js [#js {:id "node-1" :position #js {:x 100 :y 100} :data #js {:label "Start8"}}
       #js {:id "node-2" :position #js {:x 100 :y 250} :data #js {:label "Process"}}
       #js {:id "node-3" :position #js {:x 300 :y 250} :data #js {:label "End"}}])

(def initial-edges
  #js [#js {:id "edge-1-2" :source "node-1" :target "node-2"}
       #js {:id "edge-2-3" :source "node-2" :target "node-3"}])

(defn app []
  (let [nodes (r/atom initial-nodes)
        edges (r/atom initial-edges)]
    (fn []
      [:> ReactFlow
       {:nodes @nodes
        :edges @edges
        :onNodesChange (fn [changes] (swap! nodes #(applyNodeChanges changes %)))
        :onEdgesChange (fn [changes] (swap! edges #(applyEdgeChanges changes %)))
        :onConnect (fn [params] (swap! edges #(addEdge params %)))
        :fitView true}
       [:> Background]
       [:> Controls]])))

(defonce root (atom nil))

(defn ^:after-load init []
  (when-not @root
    (reset! root (rdc/create-root (.getElementById js/document "react-flow-root"))))
  (rdc/render @root [app]))
