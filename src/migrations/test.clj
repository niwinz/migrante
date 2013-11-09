(ns migrations.test
  (:require [migrations.core :refer [defmigration]])
  (:require [datasource.core :as ds])
  (:gen-class))

(defn -main
  [& args]
  (println "Environment config:" (ds/get-config)))

(defmigration :name "test-1" :parent nil
  :up (fn [dbspec] (println "up1"))
  :down (fn [dbspec] (println "down1")))

(defmigration :name "test-2" :parent "test-1"
  :up (fn [dbspec] (println "up2"))
  :down (fn [dbspec] (println "down2")))
