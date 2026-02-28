(ns yield.handler
  (:require [integrant.core :as ig]
            [ring.util.response :as response]
            [ring.middleware.anti-forgery :refer [*anti-forgery-token*]]))

(defn page-layout [{:keys [title]} & body]
  [:html {:lang "en"}
   [:head
    [:meta {:charset "UTF-8"}]
    [:meta {:name "viewport" :content "width=device-width, initial-scale=1.0"}]
    [:title (or title "Yield")]
    [:link {:rel "stylesheet" :href "/css/output.css"}]
    [:script {:src "/js/htmx.min.js"}]
    [:script {:src "/cljs/app.js"}]]
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
    [:link {:rel "stylesheet" :href "/css/reactflow.css"}]
    [:script {:src "/js/htmx.min.js"}]]
   (into [:body {:class "m-0 overflow-hidden"
                 :hx-headers "{\"X-Ring-Anti-Forgery\": \"true\"}"}]
         body)])

(defn index [request]
  (let [email (get-in request [:session :email])]
    (editor-layout {:title "Yield"}
      [:div {:id "react-flow-root"
             :class "w-screen h-screen"
             :data-user-email email
             :data-csrf-token *anti-forgery-token*}]
      [:script {:src "/cljs/app.js"}])))

(defn greeting [_request]
  {:status 200
   :headers {"Content-Type" "text/html; charset=utf-8"}
   :body "<p class=\"text-green-600 font-semibold\">Hello from the server via htmx!</p>"})

(defmethod ig/init-key :yield.handler/index [_ _]
  (fn [request]
    (if (get-in request [:session :user-id])
      (index request)
      (response/redirect "/login"))))
(defmethod ig/init-key :yield.handler/greeting [_ _] #'greeting)
