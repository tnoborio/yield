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

(defn index [_request]
  (page-layout {:title "Yield"}
    [:div {:class "max-w-2xl mx-auto p-8"}
     [:h1 {:class "text-3xl font-bold text-gray-900 mb-6"} "Yield"]
     [:div {:class "bg-white rounded-lg shadow p-6"}
      [:p {:class "text-gray-600 mb-4"} "Welcome to Yield."]
      [:button {:class "px-4 py-2 bg-blue-600 text-white rounded hover:bg-blue-700 transition-colors"
                :hx-get "/greeting"
                :hx-target "#greeting-area"
                :hx-swap "innerHTML"}
       "Load Greeting"]
      [:div {:id "greeting-area"
             :class "mt-4 p-4 bg-gray-50 rounded min-h-[2rem]"}]]]))

(defn greeting [_request]
  {:status 200
   :headers {"Content-Type" "text/html; charset=utf-8"}
   :body "<p class=\"text-green-600 font-semibold\">Hello from the server via htmx!</p>"})

(defmethod ig/init-key :yield.handler/index [_ _] #'index)
(defmethod ig/init-key :yield.handler/greeting [_ _] #'greeting)
