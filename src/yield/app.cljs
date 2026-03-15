(ns yield.app
  (:require [reagent.core :as r]
            [reagent.dom.client :as rdc]
            ["@xyflow/react" :refer [ReactFlow Background Controls Panel Handle
                                     applyNodeChanges applyEdgeChanges addEdge]]
            ["@xyflow/react/dist/style.css"]
))

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

;; Item state
(defonce current-view (r/atom :graph))
(defonce items (r/atom []))
(defonce new-item-title (r/atom ""))
(defonce drag-item-id (r/atom nil))
(defonce drop-target (r/atom nil)) ;; {:status s :index i}

;; List state
(defonce lists (r/atom []))
(defonce current-list-id (r/atom nil))
(defonce creating-list? (r/atom false))
(defonce new-list-name (r/atom ""))

;; ── URL routing ───────────────────────────────────────────────

(defn- push-url! [path]
  (.pushState js/history nil "" path))

(defn- parse-path []
  (let [path (.-pathname js/location)]
    (condp re-matches path
      #"/graphs/([0-9a-f-]+)" :>> (fn [[_ id]] {:view :graph :id id})
      #"/lists/([0-9a-f-]+)"  :>> (fn [[_ id]] {:view :list :id id})
      {:view :graph :id nil})))

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

;; ── Graph API calls ───────────────────────────────────────────

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
                 (when (and (seq (:graphs d)) (nil? @current-graph-id))
                   (load-graph! (:id (first (:graphs d))))))))
      (.catch (fn [err] (js/console.error "Fetch graphs failed:" err)))))

(defn switch-graph! [graph-id]
  (when (not= graph-id @current-graph-id)
    (reset! current-view :graph)
    (push-url! (str "/graphs/" graph-id))
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
                 (reset! current-view :graph)
                 (push-url! (str "/graphs/" (:id graph)))
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
                 (when-let [first-graph (first @graphs)]
                   (load-graph! (:id first-graph))))))
      (.catch (fn [err] (js/console.error "Delete graph failed:" err)))))

;; ── List API calls ───────────────────────────────────────────

(declare fetch-items!)

(defn fetch-lists! []
  (-> (js/fetch "/api/lists"
                #js {:method "GET" :headers (clj->js (api-headers))})
      (.then (fn [res] (.json res)))
      (.then (fn [data]
               (let [d (js->clj data :keywordize-keys true)]
                 (reset! lists (:lists d))
                 (when (and (seq (:lists d)) (nil? @current-list-id))
                   (reset! current-list-id (:id (first (:lists d))))
                   (fetch-items!)))))
      (.catch (fn [err] (js/console.error "Fetch lists failed:" err)))))

(defn switch-list! [list-id]
  (when (not= list-id @current-list-id)
    (reset! current-list-id list-id)
    (reset! current-view :list)
    (push-url! (str "/lists/" list-id))
    (fetch-items!)))

(defn create-list! [list-name]
  (-> (js/fetch "/api/lists"
                #js {:method  "POST"
                     :headers (clj->js (api-headers))
                     :body    (js/JSON.stringify (clj->js {:name list-name}))})
      (.then (fn [res] (.json res)))
      (.then (fn [data]
               (let [d (js->clj data :keywordize-keys true)
                     l (:list d)]
                 (swap! lists conj l)
                 (reset! current-list-id (:id l))
                 (reset! current-view :list)
                 (push-url! (str "/lists/" (:id l)))
                 (reset! items []))))
      (.catch (fn [err] (js/console.error "Create list failed:" err)))))

(defn delete-list! [list-id]
  (-> (js/fetch (str "/api/lists/" list-id)
                #js {:method  "DELETE"
                     :headers (clj->js (api-headers))})
      (.then (fn [_]
               (swap! lists (fn [ls] (vec (remove #(= (:id %) list-id) ls))))
               (when (= list-id @current-list-id)
                 (reset! current-list-id nil)
                 (reset! items [])
                 (when-let [first-list (first @lists)]
                   (reset! current-list-id (:id first-list))
                   (fetch-items!)))))
      (.catch (fn [err] (js/console.error "Delete list failed:" err)))))

;; ── Item API calls ────────────────────────────────────────────

(defn fetch-items! []
  (when-let [lid @current-list-id]
    (-> (js/fetch (str "/api/items?list_id=" lid)
                  #js {:method "GET" :headers (clj->js (api-headers))})
        (.then (fn [res] (.json res)))
        (.then (fn [data]
                 (let [d (js->clj data :keywordize-keys true)]
                   (reset! items (:items d)))))
        (.catch (fn [err] (js/console.error "Fetch items failed:" err))))))

(defn create-item! [title]
  (when-let [lid @current-list-id]
    (-> (js/fetch "/api/items"
                  #js {:method  "POST"
                       :headers (clj->js (api-headers))
                       :body    (js/JSON.stringify (clj->js {:title title :list_id lid}))})
        (.then (fn [res] (.json res)))
        (.then (fn [data]
                 (let [d (js->clj data :keywordize-keys true)]
                   (swap! items conj (:item d)))))
        (.catch (fn [err] (js/console.error "Create item failed:" err))))))

(defn update-item! [item-id changes]
  (-> (js/fetch (str "/api/items/" item-id)
                #js {:method  "PUT"
                     :headers (clj->js (api-headers))
                     :body    (js/JSON.stringify (clj->js changes))})
      (.then (fn [res] (.json res)))
      (.then (fn [data]
               (let [d (js->clj data :keywordize-keys true)
                     updated (:item d)]
                 (swap! items (fn [is]
                                (mapv #(if (= (:id %) item-id) updated %) is))))))
      (.catch (fn [err] (js/console.error "Update item failed:" err)))))

(defn delete-item! [item-id]
  (-> (js/fetch (str "/api/items/" item-id)
                #js {:method  "DELETE"
                     :headers (clj->js (api-headers))})
      (.then (fn [_]
               (swap! items (fn [is] (vec (remove #(= (:id %) item-id) is))))))
      (.catch (fn [err] (js/console.error "Delete item failed:" err)))))

;; ── Item UI helpers ───────────────────────────────────────────

(def status-config
  {"done"        {:label "Done"        :bg "bg-green-100"  :text "text-green-800"  :dot "bg-green-500"}
   "ready"       {:label "Ready"       :bg "bg-blue-100"   :text "text-blue-800"   :dot "bg-blue-500"}
   "in_progress" {:label "In Progress" :bg "bg-amber-100"  :text "text-amber-800"  :dot "bg-amber-500"}
   "wait"        {:label "Wait"        :bg "bg-gray-100"   :text "text-gray-600"   :dot "bg-gray-400"}
   "reject"      {:label "Reject"      :bg "bg-red-100"    :text "text-red-800"    :dot "bg-red-500"}
   "arts"        {:label "Arts"        :bg "bg-purple-100" :text "text-purple-800" :dot "bg-purple-500"}})

(def status-order ["ready" "in_progress" "wait" "arts" "done" "reject"])

;; ── Item DnD helpers ─────────────────────────────────────────

(defn- all-item-ids-in-order
  [item-list]
  (let [grouped (group-by :status item-list)]
    (->> status-order
         (mapcat #(map :id (get grouped % [])))
         vec)))

(defn reorder-items! [item-ids]
  (-> (js/fetch "/api/item-reorder"
                #js {:method  "PUT"
                     :headers (clj->js (api-headers))
                     :body    (js/JSON.stringify (clj->js {:item_ids item-ids}))})
      (.catch (fn [err]
                (js/console.error "Reorder failed:" err)
                (fetch-items!)))))

(defn move-item-to-status!
  [item-id new-status target-index]
  (let [item (first (filter #(= (:id %) item-id) @items))
        old-status (:status item)]
    (swap! items
      (fn [is]
        (let [without (vec (remove #(= (:id %) item-id) is))
              moved (assoc item :status new-status)
              grouped (group-by :status without)
              target-group (get grouped new-status [])
              idx (min target-index (count target-group))
              new-group (vec (concat (take idx target-group)
                                    [moved]
                                    (drop idx target-group)))
              new-grouped (assoc grouped new-status new-group)]
          (->> status-order
               (mapcat #(get new-grouped % []))
               vec))))
    (when (not= old-status new-status)
      (update-item! item-id {:status new-status}))
    (reorder-items! (all-item-ids-in-order @items))))

(def category-config
  {"private"  {:label "Private"  :bg "bg-slate-100"  :text "text-slate-700"}
   "keystone" {:label "Keystone" :bg "bg-orange-100" :text "text-orange-700"}
   "sasara"   {:label "Sasara"   :bg "bg-teal-100"   :text "text-teal-700"}
   "contract" {:label "Contract" :bg "bg-cyan-100"   :text "text-cyan-700"}
   "toeic"    {:label "TOEIC"    :bg "bg-pink-100"   :text "text-pink-700"}})


;; ── Kanban Board Components ──────────────────────────────────

(defn- board-card [_item]
  (let [editing? (r/atom false)]
    (fn [item]
      (let [{:keys [id title status category due-date]} item
            cc (category-config category)
            dragging? (= id @drag-item-id)]
        [:div {:class (str "bg-white rounded-md border border-gray-200 px-3 py-2 mb-1.5 cursor-grab group shadow-sm hover:shadow-md transition-shadow "
                           (when dragging? "opacity-30"))
               :draggable true
               :on-drag-start (fn [e]
                                (reset! drag-item-id id)
                                (.setData (.-dataTransfer e) "text/plain" id)
                                (set! (.-effectAllowed (.-dataTransfer e)) "move"))
               :on-drag-end (fn [_]
                              (reset! drag-item-id nil)
                              (reset! drop-target nil))}
         ;; Title
         [:div {:class "min-w-0 mb-1"}
          (if @editing?
            [:input {:default-value title
                     :auto-focus true
                     :on-blur (fn [e]
                                (let [v (.. e -target -value)]
                                  (when (and (seq v) (not= v title))
                                    (update-item! id {:title v})))
                                (reset! editing? false))
                     :on-key-down (fn [e]
                                    (when (= (.-key e) "Enter")
                                      (let [v (.. e -target -value)]
                                        (when (and (seq v) (not= v title))
                                          (update-item! id {:title v})))
                                      (reset! editing? false))
                                    (when (= (.-key e) "Escape")
                                      (reset! editing? false)))
                     :class "w-full px-1 py-0.5 text-xs border border-indigo-400 rounded focus:outline-none focus:ring-1 focus:ring-indigo-500"}]
            [:span {:on-click #(reset! editing? true)
                    :class (str "text-xs cursor-text block break-words "
                                (when (= status "done") "line-through text-gray-400"))}
             title])]
         ;; Bottom row: category + due date + delete
         [:div {:class "flex items-center gap-1.5"}
          (when cc
            [:span {:class (str "px-1.5 py-0.5 text-[10px] rounded-full " (:bg cc) " " (:text cc))}
             (:label cc)])
          (when due-date
            [:span {:class "text-[10px] text-gray-400"} due-date])
          [:div {:class "flex-1"}]
          [:button {:on-click (fn [e]
                                (.stopPropagation e)
                                (delete-item! id))
                    :class "text-gray-300 hover:text-red-500 opacity-0 group-hover:opacity-100 transition-opacity text-xs"}
           "\u2715"]]]))))

(defn- status-column [status col-items]
  (let [sc (status-config status)
        is-over? (and @drop-target (= status (:status @drop-target)))]
    [:div {:class (str "flex flex-col bg-gray-100 rounded-lg min-w-0 flex-1 "
                       (when is-over? "ring-2 ring-indigo-300"))
           :style {:min-width "160px" :max-width "240px"}
           :on-drag-over (fn [e]
                           (.preventDefault e)
                           (set! (.-dropEffect (.-dataTransfer e)) "move")
                           (when @drag-item-id
                             (reset! drop-target {:status status :index (count col-items)})))
           :on-drag-leave (fn [e]
                            (when-not (.contains (.-currentTarget e) (.-relatedTarget e))
                              (when (= status (:status @drop-target))
                                (reset! drop-target nil))))
           :on-drop (fn [e]
                      (.preventDefault e)
                      (let [item-id (.getData (.-dataTransfer e) "text/plain")
                            target-idx (or (:index @drop-target) (count col-items))]
                        (when (seq item-id)
                          (move-item-to-status! item-id status target-idx)))
                      (reset! drop-target nil)
                      (reset! drag-item-id nil))}
     ;; Column header
     [:div {:class "flex items-center gap-1.5 px-3 py-2 flex-shrink-0"}
      [:div {:class (str "w-2 h-2 rounded-full " (:dot sc))}]
      [:span {:class "text-xs font-semibold text-gray-700"} (:label sc)]
      [:span {:class "text-[10px] text-gray-400 ml-auto"} (count col-items)]]
     ;; Cards
     [:div {:class "flex-1 overflow-y-auto px-2 pb-2 space-y-0"}
      (if (seq col-items)
        (doall
          (map-indexed
            (fn [idx item]
              (let [show-indicator? (and is-over?
                                         @drop-target
                                         (= idx (:index @drop-target)))]
                ^{:key (:id item)}
                [:div {:on-drag-over (fn [e]
                                       (.preventDefault e)
                                       (.stopPropagation e)
                                       (let [rect (.getBoundingClientRect (.-currentTarget e))
                                             mid-y (+ (.-top rect) (/ (.-height rect) 2))
                                             insert-idx (if (< (.-clientY e) mid-y) idx (inc idx))]
                                         (reset! drop-target {:status status :index insert-idx})))}
                 (when show-indicator?
                   [:div {:class "h-0.5 bg-indigo-500 rounded-full mx-1 mb-1"}])
                 [board-card item]]))
            col-items))
        ;; Empty column drop area
        [:div {:class (str "flex items-center justify-center h-16 border-2 border-dashed rounded-md text-[10px] text-gray-400 mx-1 "
                           (if is-over? "border-indigo-300 bg-indigo-50" "border-gray-300"))}
         "Drop here"])
      ;; Show indicator at end if dropping after last item
      (when (and is-over? (= (count col-items) (:index @drop-target)))
        [:div {:class "h-0.5 bg-indigo-500 rounded-full mx-1 mt-0.5"}])]]))

(defn- new-item-input []
  [:div {:class "flex gap-2 px-4 py-3 bg-white border-b border-gray-200"}
   [:input {:type "text"
            :placeholder "Add a new item..."
            :value @new-item-title
            :on-change #(reset! new-item-title (.. % -target -value))
            :on-key-down (fn [e]
                           (when (and (= (.-key e) "Enter") (seq @new-item-title))
                             (create-item! @new-item-title)
                             (reset! new-item-title "")))
            :class "flex-1 px-3 py-1.5 text-sm border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-transparent"}]
   [:button {:on-click (fn []
                         (when (seq @new-item-title)
                           (create-item! @new-item-title)
                           (reset! new-item-title "")))
             :class "px-4 py-1.5 text-sm bg-indigo-600 text-white rounded-lg hover:bg-indigo-700"}
    "Add"]])

(defn- list-view []
  (if-not @current-list-id
    [:div {:class "h-full flex items-center justify-center bg-gray-50"}
     [:div {:class "text-center text-gray-400"}
      [:p {:class "text-lg mb-2"} "No list selected"]
      [:p {:class "text-sm"} "Create a list from the menu to get started"]]]
    (let [grouped (group-by :status @items)]
      [:div {:class "h-full flex flex-col bg-gray-50"}
       [new-item-input]
       [:div {:class "flex-1 overflow-x-auto overflow-y-hidden p-4"}
        [:div {:class "flex gap-3 h-full"}
         (for [s status-order]
           ^{:key s}
           [status-column s (get grouped s [])])]]])))

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

(defn- new-list-form []
  [:div {:class "px-4 pb-3"}
   [:input {:type "text"
            :placeholder "List name"
            :value @new-list-name
            :auto-focus true
            :on-change #(reset! new-list-name (.. % -target -value))
            :on-key-down (fn [e]
                           (when (and (= (.-key e) "Enter")
                                      (seq (.-value (.-target e))))
                             (create-list! @new-list-name)
                             (reset! new-list-name "")
                             (reset! creating-list? false))
                           (when (= (.-key e) "Escape")
                             (reset! new-list-name "")
                             (reset! creating-list? false)))
            :class "w-full px-2 py-1 text-sm border border-gray-300 rounded focus:outline-none focus:ring-1 focus:ring-indigo-500"}]
   [:div {:class "flex gap-1 mt-1"}
    [:button {:on-click (fn []
                          (when (seq @new-list-name)
                            (create-list! @new-list-name)
                            (reset! new-list-name "")
                            (reset! creating-list? false)))
              :class "flex-1 px-2 py-1 text-xs bg-indigo-600 text-white rounded hover:bg-indigo-700"}
     "Create"]
    [:button {:on-click (fn []
                          (reset! new-list-name "")
                          (reset! creating-list? false))
              :class "flex-1 px-2 py-1 text-xs border border-gray-300 rounded hover:bg-gray-50"}
     "Cancel"]]])

(defn- list-list-item [l]
  (let [selected? (= (:id l) @current-list-id)]
    [:div {:class (str "flex items-center justify-between px-3 py-2 cursor-pointer rounded-md text-sm "
                       (if selected?
                         "bg-indigo-50 text-indigo-700 font-medium"
                         "text-gray-700 hover:bg-gray-100"))
           :on-click #(switch-list! (:id l))}
     [:div {:class "flex items-center gap-2 min-w-0"}
      (when selected?
        [:div {:class "w-1.5 h-1.5 rounded-full bg-indigo-600 flex-shrink-0"}])
      [:span {:class "truncate"} (:name l)]]
     [:button {:on-click (fn [e]
                           (.stopPropagation e)
                           (when (js/confirm (str "Delete \"" (:name l) "\"?"))
                             (delete-list! (:id l))))
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
     ;; Graphs & Lists sections
     [:div {:class "flex-1 overflow-y-auto"}
      ;; Graphs section
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
            "No graphs yet"]))]
      ;; Lists section
      [:div {:class "px-4 pt-3 pb-2 flex items-center justify-between border-t border-gray-200"}
       [:span {:class "text-xs font-semibold text-gray-500 uppercase tracking-wide"} "Lists"]
       [:button {:on-click #(reset! creating-list? true)
                 :class "text-xs text-indigo-600 hover:text-indigo-800 font-medium"}
        "+ New"]]
      (when @creating-list?
        [new-list-form])
      [:div {:class "px-2 pb-2 space-y-0.5"}
       (if (seq @lists)
         (for [l @lists]
           ^{:key (:id l)}
           [list-list-item l])
         (when-not @creating-list?
           [:div {:class "px-2 py-4 text-xs text-gray-400 text-center"}
            "No lists yet"]))]]
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

(defn- graph-view []
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
     "Add Node"]]])

(defn app []
  [:<>
   (when @panel-open?
     [:div {:class "absolute top-0 left-0 h-full z-50"}
      [sidebar]])
   (case @current-view
     :list
     [:div {:class "h-full flex flex-col"}
      [:div {:class "flex items-center gap-2 p-2 bg-white border-b border-gray-200"}
       (when-not @panel-open?
         [:button
          {:on-click #(reset! panel-open? true)
           :class "bg-white shadow-md rounded-md px-3 py-2 text-gray-600 hover:text-gray-800 hover:bg-gray-50"
           :style {:font-size "18px" :line-height "1"}}
          "\u2630"])]
      [:div {:class "flex-1 overflow-hidden"}
       [list-view]]]
     ;; default: graph view
     [graph-view])])

;; ── Init ────────────────────────────────────────────────────────

(defonce root (atom nil))

(defn- apply-route! [{:keys [view id]}]
  (case view
    :list (do (reset! current-view :list)
              (when id
                (reset! current-list-id id)
                (fetch-items!)))
    ;; :graph (default)
    (do (reset! current-view :graph)
        (when id
          (load-graph! id)))))

(defn- init-routing! []
  (let [route (parse-path)]
    (.addEventListener js/window "popstate"
      (fn [_] (apply-route! (parse-path))))
    (-> (js/fetch "/api/graphs"
                  #js {:method "GET" :headers (clj->js (api-headers))})
        (.then (fn [res] (.json res)))
        (.then (fn [data]
                 (let [d (js->clj data :keywordize-keys true)]
                   (reset! graphs (:graphs d))
                   (when (= :graph (:view route))
                     (let [gid (or (:id route) (:id (first (:graphs d))))]
                       (when gid
                         (load-graph! gid)
                         (when-not (:id route)
                           (push-url! (str "/graphs/" gid)))))))))
        (.catch (fn [err] (js/console.error "Fetch graphs failed:" err))))
    (-> (js/fetch "/api/lists"
                  #js {:method "GET" :headers (clj->js (api-headers))})
        (.then (fn [res] (.json res)))
        (.then (fn [data]
                 (let [d (js->clj data :keywordize-keys true)]
                   (reset! lists (:lists d))
                   (when (= :list (:view route))
                     (let [lid (or (:id route) (:id (first (:lists d))))]
                       (when lid
                         (reset! current-view :list)
                         (reset! current-list-id lid)
                         (fetch-items!)
                         (when-not (:id route)
                           (push-url! (str "/lists/" lid))))))
                   (when (and (nil? @current-list-id) (seq (:lists d)))
                     (reset! current-list-id (:id (first (:lists d))))))))
        (.catch (fn [err] (js/console.error "Fetch lists failed:" err))))))

(defn ^:after-load init []
  (when-let [el (.getElementById js/document "react-flow-root")]
    (when-not @root
      (reset! root (rdc/create-root el)))
    (rdc/render @root [app])
    (init-routing!)))
