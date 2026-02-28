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
(defonce panel-open? (r/atom false))

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

(defn- get-root-data [attr]
  (some-> (.getElementById js/document "react-flow-root")
          (.getAttribute attr)))

(defn- sidebar []
  (let [user-email (get-root-data "data-user-email")
        csrf-token (get-root-data "data-csrf-token")]
    [:div {:class "flex flex-col h-full bg-white shadow-lg"
           :style {:width "260px"}}
     [:div {:class "flex items-center justify-between p-4 border-b border-gray-200"}
      [:span {:class "text-sm font-semibold text-gray-700"} "Menu"]
      [:button {:class "text-gray-400 hover:text-gray-600"
                :on-click #(reset! panel-open? false)}
       "\u2715"]]
     [:div {:class "flex-1 p-4"}
      [:div {:class "text-xs text-gray-500 mb-1"} "Logged in as"]
      [:div {:class "text-sm font-medium text-gray-800 truncate"} user-email]]
     [:div {:class "p-4 border-t border-gray-200"}
      [:form {:method "POST" :action "/logout"}
       [:input {:type "hidden" :name "__anti-forgery-token" :value csrf-token}]
       [:button {:type "submit"
                 :class "w-full px-4 py-2 text-sm text-red-600 border border-red-300 rounded-md hover:bg-red-50 font-medium"}
        "Logout"]]]]))

(defn app []
  [:<>
   (when @panel-open?
     [:div {:class "absolute top-0 left-0 h-full z-50"}
      [sidebar]])
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
    [:> Panel {:position "top-left"}
     (when-not @panel-open?
       [:button
        {:on-click #(reset! panel-open? true)
         :class "bg-white shadow-md rounded-md px-3 py-2 text-gray-600 hover:text-gray-800 hover:bg-gray-50"
         :style {:font-size "18px" :line-height "1"}}
        "\u2630"])]
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
      "Add Node"]]]])

(defonce root (atom nil))

(defn ^:after-load init []
  (when-let [el (.getElementById js/document "react-flow-root")]
    (when-not @root
      (reset! root (rdc/create-root el)))
    (rdc/render @root [app])))
