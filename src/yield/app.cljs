(ns yield.app
  (:require [reagent.core :as r]
            [reagent.dom.client :as rdc]
            ["@xyflow/react" :refer [ReactFlow Background Controls Panel Handle
                                     applyNodeChanges applyEdgeChanges addEdge]]
            ["@xyflow/react/dist/style.css"]))

(def initial-nodes
  #js [#js {:id "node-1" :type "editable" :position #js {:x 100 :y 100} :data #js {:label "Start"}}
       #js {:id "node-2" :type "editable" :position #js {:x 100 :y 250} :data #js {:label "Process"}}
       #js {:id "node-3" :type "editable" :position #js {:x 300 :y 250} :data #js {:label "End"}}])

(def initial-edges
  #js [#js {:id "edge-1-2" :source "node-1" :target "node-2"}
       #js {:id "edge-2-3" :source "node-2" :target "node-3"}])

(defonce nodes (r/atom initial-nodes))
(defonce edges (r/atom initial-edges))
(defonce node-id-counter (atom 3))

(defn update-node-label [nodes-array node-id new-label]
  (.map nodes-array
    (fn [node]
      (if (= (.-id node) node-id)
        (js/Object.assign #js {} node
                          #js {:data (js/Object.assign #js {} (.-data node)
                                                       #js {:label new-label})})
        node))))

(defn editable-node [_props]
  (let [editing? (r/atom false)]
    (fn [props]
      (let [node-id (:id props)
            label (.-label (:data props))]
        [:div {:style {:padding "10px 20px"
                       :border "1px solid #ccc"
                       :border-radius "5px"
                       :background "white"
                       :font-size "12px"}}
         [:> Handle {:type "target" :position "top"}]
         (if @editing?
           [:input {:class "nodrag"
                    :default-value label
                    :auto-focus true
                    :on-blur (fn [e]
                               (swap! nodes update-node-label node-id (.. e -target -value))
                               (reset! editing? false))
                    :on-key-down (fn [e]
                                   (when (= (.-key e) "Enter")
                                     (swap! nodes update-node-label node-id (.. e -target -value))
                                     (reset! editing? false))
                                   (when (= (.-key e) "Escape")
                                     (reset! editing? false)))
                    :style {:border "1px solid #4f46e5"
                            :border-radius "3px"
                            :padding "2px 4px"
                            :outline "none"
                            :font-size "12px"
                            :width "100%"}}]
           [:div {:on-double-click (fn [_] (reset! editing? true))
                  :style {:cursor "text" :min-width "50px"}}
            label])
         [:> Handle {:type "source" :position "bottom"}]]))))

(def node-types
  #js {:editable (r/reactify-component editable-node)})

(defn make-node []
  (let [id (swap! node-id-counter inc)
        offset (mod id 10)]
    #js {:id (str "node-" id)
         :type "editable"
         :position #js {:x (+ 200 (* 30 offset))
                        :y (+ 100 (* 30 offset))}
         :data #js {:label (str "Node " id)}}))

(defn app []
  [:> ReactFlow
   {:nodes @nodes
    :edges @edges
    :onNodesChange (fn [changes] (swap! nodes #(applyNodeChanges changes %)))
    :onEdgesChange (fn [changes] (swap! edges #(applyEdgeChanges changes %)))
    :onConnect (fn [params] (swap! edges #(addEdge params %)))
    :deleteKeyCode #js ["Backspace" "Delete"]
    :nodeTypes node-types
    :fitView true}
   [:> Background]
   [:> Controls]
   [:> Panel {:position "top-right"}
    [:button
     {:on-click (fn [_e]
                  (swap! nodes #(.concat % #js [(make-node)])))
      :style {:padding "8px 16px"
              :background "#4f46e5"
              :color "white"
              :border "none"
              :border-radius "6px"
              :cursor "pointer"
              :font-size "14px"}}
     "Add Node"]]])

(defonce root (atom nil))

(defn ^:after-load init []
  (when-not @root
    (reset! root (rdc/create-root (.getElementById js/document "react-flow-root"))))
  (rdc/render @root [app]))
