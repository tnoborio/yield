(ns yield.handler.auth
  (:require [integrant.core :as ig]
            [ring.util.response :as response]
            [ring.middleware.anti-forgery :refer [*anti-forgery-token*]]
            [yield.auth :as auth]
            [yield.boundary.user :as user-db]
            [yield.handler :refer [page-layout]]))

;; ── Hiccup Views ────────────────────────────────────────────────

(defn- csrf-field []
  [:input {:type "hidden" :name "__anti-forgery-token"
           :value *anti-forgery-token*}])

(defn- login-form [& {:keys [error]}]
  (page-layout {:title "Login - Yield"}
    [:div {:class "flex items-center justify-center min-h-screen"}
     [:div {:class "w-full max-w-md p-8 bg-white rounded-lg shadow-md"}
      [:h1 {:class "text-2xl font-bold text-center mb-6"} "Sign In"]
      (when error
        [:div {:class "mb-4 p-3 bg-red-100 text-red-700 rounded"} error])
      [:form {:method "POST" :action "/login"}
       (csrf-field)
       [:div {:class "mb-4"}
        [:label {:class "block text-sm font-medium text-gray-700 mb-1" :for "email"} "Email"]
        [:input {:type "email" :name "email" :id "email" :required true
                 :class "w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-indigo-500"}]]
       [:div {:class "mb-6"}
        [:label {:class "block text-sm font-medium text-gray-700 mb-1" :for "password"} "Password"]
        [:input {:type "password" :name "password" :id "password" :required true
                 :class "w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-indigo-500"}]]
       [:button {:type "submit"
                 :class "w-full bg-indigo-600 text-white py-2 px-4 rounded-md hover:bg-indigo-700 font-medium"}
        "Sign In"]]
      [:div {:class "mt-4 text-center text-sm text-gray-600"}
       [:a {:href "/register" :class "text-indigo-600 hover:underline"} "Create an account"]
       " | "
       [:a {:href "/reset-password" :class "text-indigo-600 hover:underline"} "Forgot password?"]]]]))

(defn- register-form [& {:keys [error]}]
  (page-layout {:title "Register - Yield"}
    [:div {:class "flex items-center justify-center min-h-screen"}
     [:div {:class "w-full max-w-md p-8 bg-white rounded-lg shadow-md"}
      [:h1 {:class "text-2xl font-bold text-center mb-6"} "Create Account"]
      (when error
        [:div {:class "mb-4 p-3 bg-red-100 text-red-700 rounded"} error])
      [:form {:method "POST" :action "/register"}
       (csrf-field)
       [:div {:class "mb-4"}
        [:label {:class "block text-sm font-medium text-gray-700 mb-1" :for "email"} "Email"]
        [:input {:type "email" :name "email" :id "email" :required true
                 :class "w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-indigo-500"}]]
       [:div {:class "mb-4"}
        [:label {:class "block text-sm font-medium text-gray-700 mb-1" :for "password"} "Password"]
        [:input {:type "password" :name "password" :id "password" :required true
                 :class "w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-indigo-500"}]]
       [:div {:class "mb-6"}
        [:label {:class "block text-sm font-medium text-gray-700 mb-1" :for "password-confirm"} "Confirm Password"]
        [:input {:type "password" :name "password-confirm" :id "password-confirm" :required true
                 :class "w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-indigo-500"}]]
       [:button {:type "submit"
                 :class "w-full bg-indigo-600 text-white py-2 px-4 rounded-md hover:bg-indigo-700 font-medium"}
        "Create Account"]]
      [:div {:class "mt-4 text-center text-sm text-gray-600"}
       "Already have an account? "
       [:a {:href "/login" :class "text-indigo-600 hover:underline"} "Sign in"]]]]))

(defn- reset-request-form [& {:keys [error success]}]
  (page-layout {:title "Reset Password - Yield"}
    [:div {:class "flex items-center justify-center min-h-screen"}
     [:div {:class "w-full max-w-md p-8 bg-white rounded-lg shadow-md"}
      [:h1 {:class "text-2xl font-bold text-center mb-6"} "Reset Password"]
      (when error
        [:div {:class "mb-4 p-3 bg-red-100 text-red-700 rounded"} error])
      (when success
        [:div {:class "mb-4 p-3 bg-green-100 text-green-700 rounded"} success])
      [:form {:method "POST" :action "/reset-password"}
       (csrf-field)
       [:div {:class "mb-6"}
        [:label {:class "block text-sm font-medium text-gray-700 mb-1" :for "email"} "Email"]
        [:input {:type "email" :name "email" :id "email" :required true
                 :class "w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-indigo-500"}]]
       [:button {:type "submit"
                 :class "w-full bg-indigo-600 text-white py-2 px-4 rounded-md hover:bg-indigo-700 font-medium"}
        "Send Reset Link"]]
      [:div {:class "mt-4 text-center text-sm text-gray-600"}
       [:a {:href "/login" :class "text-indigo-600 hover:underline"} "Back to sign in"]]]]))

(defn- reset-password-form [token & {:keys [error]}]
  (page-layout {:title "Set New Password - Yield"}
    [:div {:class "flex items-center justify-center min-h-screen"}
     [:div {:class "w-full max-w-md p-8 bg-white rounded-lg shadow-md"}
      [:h1 {:class "text-2xl font-bold text-center mb-6"} "Set New Password"]
      (when error
        [:div {:class "mb-4 p-3 bg-red-100 text-red-700 rounded"} error])
      [:form {:method "POST" :action "/reset-password-confirm"}
       (csrf-field)
       [:input {:type "hidden" :name "token" :value token}]
       [:div {:class "mb-4"}
        [:label {:class "block text-sm font-medium text-gray-700 mb-1" :for "password"} "New Password"]
        [:input {:type "password" :name "password" :id "password" :required true
                 :class "w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-indigo-500"}]]
       [:div {:class "mb-6"}
        [:label {:class "block text-sm font-medium text-gray-700 mb-1" :for "password-confirm"} "Confirm New Password"]
        [:input {:type "password" :name "password-confirm" :id "password-confirm" :required true
                 :class "w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-indigo-500"}]]
       [:button {:type "submit"
                 :class "w-full bg-indigo-600 text-white py-2 px-4 rounded-md hover:bg-indigo-700 font-medium"}
        "Update Password"]]]]))

;; ── Handlers ────────────────────────────────────────────────────

(defn login-page [_request]
  (login-form))

(defn login-submit [db request]
  (let [{:keys [email password]} (:params request)
        user (user-db/find-user-by-email db email)]
    (if (and user (auth/check-password password (:password-hash user)))
      (-> (response/redirect "/")
          (assoc :session {:user-id (str (:id user))
                           :email   (:email user)}))
      (login-form :error "Invalid email or password."))))

(defn register-page [_request]
  (register-form))

(defn register-submit [db request]
  (let [{:keys [email password password-confirm]} (:params request)]
    (cond
      (or (empty? email) (empty? password))
      (register-form :error "Email and password are required.")

      (not= password password-confirm)
      (register-form :error "Passwords do not match.")

      (< (count password) 8)
      (register-form :error "Password must be at least 8 characters.")

      (user-db/find-user-by-email db email)
      (register-form :error "An account with this email already exists.")

      :else
      (let [hash (auth/hash-password password)
            user (user-db/create-user! db {:email email :password-hash hash})]
        (-> (response/redirect "/")
            (assoc :session {:user-id (str (:id user))
                             :email   (:email user)}))))))

(defn reset-password-page [_request]
  (reset-request-form))

(defn reset-password-submit [db request]
  (let [email (get-in request [:params :email])
        user  (user-db/find-user-by-email db email)]
    (when user
      (let [token   (auth/generate-reset-token)
            expires (auth/reset-token-expiry)]
        (user-db/create-reset-token! db (:id user) token expires)
        (println (str "\n===== PASSWORD RESET LINK =====\n"
                      "http://localhost:3000/reset-password/" token
                      "\n===============================\n"))))
    (reset-request-form :success "If an account exists with that email, a reset link has been generated. Check the server console.")))

(defn reset-password-token-page [request]
  (let [token (get-in request [:path-params :token])]
    (reset-password-form token)))

(defn reset-password-confirm [db request]
  (let [{:keys [token password password-confirm]} (:params request)
        token-record (user-db/find-reset-token db token)]
    (cond
      (nil? token-record)
      (reset-request-form :error "Invalid or expired reset link. Please request a new one.")

      (not= password password-confirm)
      (reset-password-form token :error "Passwords do not match.")

      (< (count password) 8)
      (reset-password-form token :error "Password must be at least 8 characters.")

      :else
      (do
        (user-db/update-user-password! db (:user-id token-record) (auth/hash-password password))
        (user-db/mark-token-used! db token)
        (-> (response/redirect "/login")
            (assoc :flash "Password updated successfully. Please sign in."))))))

(defn logout [_request]
  (-> (response/redirect "/login")
      (assoc :session nil)))

;; ── Integrant Keys ──────────────────────────────────────────────

(defmethod ig/init-key :yield.handler.auth/login-page [_ _]
  #'login-page)

(defmethod ig/init-key :yield.handler.auth/login-submit [_ {:keys [db]}]
  (fn [request] (login-submit db request)))

(defmethod ig/init-key :yield.handler.auth/register-page [_ _]
  #'register-page)

(defmethod ig/init-key :yield.handler.auth/register-submit [_ {:keys [db]}]
  (fn [request] (register-submit db request)))

(defmethod ig/init-key :yield.handler.auth/reset-password-page [_ _]
  #'reset-password-page)

(defmethod ig/init-key :yield.handler.auth/reset-password-submit [_ {:keys [db]}]
  (fn [request] (reset-password-submit db request)))

(defmethod ig/init-key :yield.handler.auth/reset-password-token-page [_ _]
  #'reset-password-token-page)

(defmethod ig/init-key :yield.handler.auth/reset-password-confirm [_ {:keys [db]}]
  (fn [request] (reset-password-confirm db request)))

(defmethod ig/init-key :yield.handler.auth/logout [_ _]
  #'logout)
