(ns yield.app
  (:require [reagent.core :as r]
            [reagent.dom.client :as rdc]
            ["@xyflow/react" :refer [ReactFlow Background Controls Panel Handle
                                     applyNodeChanges applyEdgeChanges addEdge]]
            ["@xyflow/react/dist/style.css"]))

;; ── State ───────────────────────────────────────────────────────

(defonce nodes (r/atom #js []))
(defonce edges (r/atom #js []))
(defonce node-id-counter (atom 0))
(defonce panel-open? (r/atom false))
(defonce graphs (r/atom []))
(defonce current-graph-id (r/atom nil))
(defonce creating-graph? (r/atom false))
(defonce new-graph-name (r/atom ""))
(defonce save-timer (atom nil))
(defonce loading? (atom false))

;; ── Helpers ─────────────────────────────────────────────────────

(defn- get-root-data [attr]
  (some-> (.getElementById js/document "react-flow-root")
          (.getAttribute attr)))

(defn- csrf-token []
  (get-root-data "data-csrf-token"))

(defn- api-headers []
  {"Content-Type"  "application/json"
   "X-CSRF-Token"  (csrf-token)})

;; ── Node helpers ────────────────────────────────────────────────

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

;; ── API calls ───────────────────────────────────────────────────

(defn- nodes->clj [nodes-array]
  (mapv (fn [n]
          {:node-id    (.-id n)
           :type       (or (.-type n) "editable")
           :position-x (.. n -position -x)
           :position-y (.. n -position -y)
           :label      (.. n -data -label)})
        (array-seq nodes-array)))

(defn- edges->clj [edges-array]
  (mapv (fn [e]
          {:edge-id        (.-id e)
           :source-node-id (.-source e)
           :target-node-id (.-target e)})
        (array-seq edges-array)))

(defn- clj-nodes->js [nodes-clj]
  (clj->js (mapv (fn [{:keys [node-id type position-x position-y label]}]
                   {:id node-id
                    :type (or type "editable")
                    :position {:x position-x :y position-y}
                    :data {:label (or label "")}})
                 nodes-clj)))

(defn- clj-edges->js [edges-clj]
  (clj->js (mapv (fn [{:keys [edge-id source-node-id target-node-id]}]
                   {:id edge-id
                    :source source-node-id
                    :target target-node-id})
                 edges-clj)))

(defn save-current-graph! []
  (when-let [gid @current-graph-id]
    (-> (js/fetch (str "/api/graphs/" gid "/save")
                  #js {:method  "PUT"
                       :headers (clj->js (api-headers))
                       :body    (js/JSON.stringify
                                 (clj->js {:nodes (nodes->clj @nodes)
                                           :edges (edges->clj @edges)}))})
        (.catch (fn [err] (js/console.error "Save failed:" err))))))

(defn schedule-save! []
  (when-not @loading?
    (when-let [t @save-timer]
      (js/clearTimeout t))
    (reset! save-timer
            (js/setTimeout (fn [] (save-current-graph!)) 2000))))

(defn load-graph! [graph-id]
  (reset! loading? true)
  (-> (js/fetch (str "/api/graphs/" graph-id)
                #js {:method "GET" :headers (clj->js (api-headers))})
      (.then (fn [res] (.json res)))
      (.then (fn [data]
               (let [d (js->clj data :keywordize-keys true)]
                 (reset! nodes (clj-nodes->js (:nodes d)))
                 (reset! edges (clj-edges->js (:edges d)))
                 (let [max-id (->> (:nodes d)
                                   (map :node-id)
                                   (keep #(second (re-matches #"node-(\d+)" %)))
                                   (map parse-long)
                                   (reduce max 0))]
                   (reset! node-id-counter max-id))
                 (reset! current-graph-id graph-id)
                 (reset! loading? false))))
      (.catch (fn [err]
                (reset! loading? false)
                (js/console.error "Load failed:" err)))))

(defn fetch-graphs! []
  (-> (js/fetch "/api/graphs"
                #js {:method "GET" :headers (clj->js (api-headers))})
      (.then (fn [res] (.json res)))
      (.then (fn [data]
               (let [d (js->clj data :keywordize-keys true)]
                 (reset! graphs (:graphs d))
                 ;; Load the first graph if exists and none selected
                 (when (and (seq (:graphs d)) (nil? @current-graph-id))
                   (load-graph! (:id (first (:graphs d))))))))
      (.catch (fn [err] (js/console.error "Fetch graphs failed:" err)))))

(defn switch-graph! [graph-id]
  (when (not= graph-id @current-graph-id)
    (-> (save-current-graph!)
        (.then (fn [_] (load-graph! graph-id))))))

(defn create-graph! [graph-name]
  (-> (js/fetch "/api/graphs"
                #js {:method  "POST"
                     :headers (clj->js (api-headers))
                     :body    (js/JSON.stringify (clj->js {:name graph-name}))})
      (.then (fn [res] (.json res)))
      (.then (fn [data]
               (let [d (js->clj data :keywordize-keys true)
                     graph (:graph d)]
                 (swap! graphs conj graph)
                 ;; Save current graph first, then switch
                 (if @current-graph-id
                   (-> (save-current-graph!)
                       (.then (fn [_]
                                (reset! nodes #js [])
                                (reset! edges #js [])
                                (reset! node-id-counter 0)
                                (reset! current-graph-id (:id graph)))))
                   (do
                     (reset! nodes #js [])
                     (reset! edges #js [])
                     (reset! node-id-counter 0)
                     (reset! current-graph-id (:id graph)))))))
      (.catch (fn [err] (js/console.error "Create graph failed:" err)))))

(defn delete-graph! [graph-id]
  (-> (js/fetch (str "/api/graphs/" graph-id)
                #js {:method  "DELETE"
                     :headers (clj->js (api-headers))})
      (.then (fn [_]
               (swap! graphs (fn [gs] (vec (remove #(= (:id %) graph-id) gs))))
               (when (= graph-id @current-graph-id)
                 (reset! current-graph-id nil)
                 (reset! nodes #js [])
                 (reset! edges #js [])
                 (reset! node-id-counter 0)
                 ;; Load first remaining graph if available
                 (when-let [first-graph (first @graphs)]
                   (load-graph! (:id first-graph))))))
      (.catch (fn [err] (js/console.error "Delete graph failed:" err)))))

;; ── UI Components ───────────────────────────────────────────────

(defn- new-graph-form []
  [:div {:class "px-4 pb-3"}
   [:input {:type "text"
            :placeholder "Graph name"
            :value @new-graph-name
            :auto-focus true
            :on-change #(reset! new-graph-name (.. % -target -value))
            :on-key-down (fn [e]
                           (when (and (= (.-key e) "Enter")
                                      (seq (.-value (.-target e))))
                             (create-graph! @new-graph-name)
                             (reset! new-graph-name "")
                             (reset! creating-graph? false))
                           (when (= (.-key e) "Escape")
                             (reset! new-graph-name "")
                             (reset! creating-graph? false)))
            :class "w-full px-2 py-1 text-sm border border-gray-300 rounded focus:outline-none focus:ring-1 focus:ring-indigo-500"}]
   [:div {:class "flex gap-1 mt-1"}
    [:button {:on-click (fn []
                          (when (seq @new-graph-name)
                            (create-graph! @new-graph-name)
                            (reset! new-graph-name "")
                            (reset! creating-graph? false)))
              :class "flex-1 px-2 py-1 text-xs bg-indigo-600 text-white rounded hover:bg-indigo-700"}
     "Create"]
    [:button {:on-click (fn []
                          (reset! new-graph-name "")
                          (reset! creating-graph? false))
              :class "flex-1 px-2 py-1 text-xs border border-gray-300 rounded hover:bg-gray-50"}
     "Cancel"]]])

(defn- graph-list-item [graph]
  (let [selected? (= (:id graph) @current-graph-id)]
    [:div {:class (str "flex items-center justify-between px-3 py-2 cursor-pointer rounded-md text-sm "
                       (if selected?
                         "bg-indigo-50 text-indigo-700 font-medium"
                         "text-gray-700 hover:bg-gray-100"))
           :on-click #(switch-graph! (:id graph))}
     [:div {:class "flex items-center gap-2 min-w-0"}
      (when selected?
        [:div {:class "w-1.5 h-1.5 rounded-full bg-indigo-600 flex-shrink-0"}])
      [:span {:class "truncate"} (:name graph)]]
     [:button {:on-click (fn [e]
                           (.stopPropagation e)
                           (when (js/confirm (str "Delete \"" (:name graph) "\"?"))
                             (delete-graph! (:id graph))))
               :class "text-gray-400 hover:text-red-500 flex-shrink-0 ml-1"}
      "\u2715"]]))

(defn- sidebar []
  (let [user-email (get-root-data "data-user-email")
        csrf-token-val (csrf-token)]
    [:div {:class "flex flex-col h-full bg-white shadow-lg"
           :style {:width "260px"}}
     ;; Header
     [:div {:class "flex items-center justify-between p-4 border-b border-gray-200"}
      [:span {:class "text-sm font-semibold text-gray-700"} "Menu"]
      [:button {:class "text-gray-400 hover:text-gray-600"
                :on-click #(reset! panel-open? false)}
       "\u2715"]]
     ;; Graphs section
     [:div {:class "flex-1 overflow-y-auto"}
      [:div {:class "px-4 pt-3 pb-2 flex items-center justify-between"}
       [:span {:class "text-xs font-semibold text-gray-500 uppercase tracking-wide"} "Graphs"]
       [:button {:on-click #(reset! creating-graph? true)
                 :class "text-xs text-indigo-600 hover:text-indigo-800 font-medium"}
        "+ New"]]
      (when @creating-graph?
        [new-graph-form])
      [:div {:class "px-2 pb-2 space-y-0.5"}
       (if (seq @graphs)
         (for [graph @graphs]
           ^{:key (:id graph)}
           [graph-list-item graph])
         (when-not @creating-graph?
           [:div {:class "px-2 py-4 text-xs text-gray-400 text-center"}
            "No graphs yet"]))]]
     ;; User info & Logout
     [:div {:class "border-t border-gray-200"}
      [:div {:class "px-4 pt-3 pb-1"}
       [:div {:class "text-xs text-gray-500 mb-1"} "Logged in as"]
       [:div {:class "text-sm font-medium text-gray-800 truncate"} user-email]]
      [:div {:class "p-4 pt-2"}
       [:form {:method "POST" :action "/logout"}
        [:input {:type "hidden" :name "__anti-forgery-token" :value csrf-token-val}]
        [:button {:type "submit"
                  :class "w-full px-4 py-2 text-sm text-red-600 border border-red-300 rounded-md hover:bg-red-50 font-medium"}
         "Logout"]]]]]))

(defn app []
  [:<>
   (when @panel-open?
     [:div {:class "absolute top-0 left-0 h-full z-50"}
      [sidebar]])
   [:> ReactFlow
    {:nodes @nodes
     :edges @edges
     :onNodesChange (fn [changes]
                      (swap! nodes #(applyNodeChanges changes %))
                      (schedule-save!))
     :onEdgesChange (fn [changes]
                      (swap! edges #(applyEdgeChanges changes %))
                      (schedule-save!))
     :onConnect (fn [params]
                  (swap! edges #(addEdge params %))
                  (schedule-save!))
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
                   (swap! nodes #(.concat % #js [(make-node)]))
                   (schedule-save!))
       :style {:padding "8px 16px"
               :background "#4f46e5"
               :color "white"
               :border "none"
               :border-radius "6px"
               :cursor "pointer"
               :font-size "14px"}}
      "Add Node"]]]])

;; ── Init ────────────────────────────────────────────────────────

(defonce root (atom nil))

(defn ^:after-load init []
  (when-let [el (.getElementById js/document "react-flow-root")]
    (when-not @root
      (reset! root (rdc/create-root el)))
    (rdc/render @root [app])
    (fetch-graphs!)))
