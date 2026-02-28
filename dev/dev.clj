(ns dev
  (:require [clojure.java.io :as io]
            [duct.main.config :as config]
            [integrant.core :as ig]
            [integrant.repl]
            [integrant.repl.state]))

(defn- read-config []
  (let [cfg (ig/read-string {:readers {'duct/include (fn [f] (ig/read-string (slurp (io/file f))))
                                       'duct/resource io/resource}}
                            (slurp (io/file "duct.edn")))]
    (update cfg :vars #(merge (->> (keys (:system cfg))
                                   (map (comp :duct/vars ig/describe))
                                   (apply merge))
                              %))))

(ig/load-hierarchy)
(ig/load-annotations)

(integrant.repl/set-prep!
 (fn []
   (let [cfg (read-config)
         prepped (config/prep cfg {:main true})]
     (ig/load-namespaces prepped)
     prepped)))

(defn go [] (integrant.repl/go))
(defn halt [] (integrant.repl/halt))
(defn reset [] (integrant.repl/reset))
(defn system [] integrant.repl.state/system)
(defn config [] integrant.repl.state/config)
