(ns yield.handler
  (:require [integrant.core :as ig]))

(defn page-layout [{:keys [title]} & body]
  [:html {:lang "en"}
   [:head
    [:meta {:charset "UTF-8"}]
    [:meta {:name "viewport" :content "width=device-width, initial-scale=1.0"}]
    [:title (or title "Yield")]
    [:link {:rel "stylesheet" :href "/css/output.css"}]
    [:script {:src "/js/htmx.min.js"}]]
   (into [:body {:class "min-h-screen bg-gray-50"
                 :hx-headers "{\"X-Ring-Anti-Forgery\": \"true\"}"}]
         body)])

(defn editor-layout [{:keys [title]} & body]
  [:html {:lang "en"}
   [:head
    [:meta {:charset "UTF-8"}]
    [:meta {:name "viewport" :content "width=device-width, initial-scale=1.0"}]
    [:title (or title "Yield")]
    [:link {:rel "stylesheet" :href "/css/output.css"}]
    [:link {:rel "stylesheet" :href "/js/app.css"}]
    [:script {:src "/js/htmx.min.js"}]]
   (into [:body {:class "m-0 overflow-hidden"
                 :hx-headers "{\"X-Ring-Anti-Forgery\": \"true\"}"}]
         body)])

(defn index [_request]
  (editor-layout {:title "Yield"}
    [:div {:id "react-flow-root"
           :class "w-screen h-screen"}]
    [:script {:src "/js/app.js"}]))

(defn greeting [_request]
  {:status 200
   :headers {"Content-Type" "text/html; charset=utf-8"}
   :body "<p class=\"text-green-600 font-semibold\">Hello from the server via htmx!</p>"})

(defmethod ig/init-key :yield.handler/index [_ _] #'index)
(defmethod ig/init-key :yield.handler/greeting [_ _] #'greeting)
