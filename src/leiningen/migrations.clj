(ns leiningen.migrations
  (:use [slingshot.slingshot :only [throw+]])
  (:require [datasource.core :as ds]
            [migrations.cli :as migrations-cli]))

(defn get-dbspec
  "Get current dbspec from datasource."
  []
  (let [spec (:database (ds/get-config))]
    (when (nil? spec)
      (throw+ "database configuration not found."))
    spec))

(defn migrations
  ([project] (migrations-cli/cli-command-help project))
  ([project command & args]
   (let [spec (get-dbspec)
         opts (:migrations project)]
     (when (nil? opts)
       (throw+ "migrations config is missing:"))
     (apply migrations-cli/run-cli (concat [project spec command] args)))))
