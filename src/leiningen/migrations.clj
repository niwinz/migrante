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
  "Simply manage sql migrations with clojure/jdbc

Commands:
create name      Create migration (eg: migrations/20130712101745082-<name>.clj)
migrate          Run all pending migrations
rollback n       Rollback last n migrations (n defaults to 1)"

  ([project] (println (:doc (meta #'migrations))))
  ([project command & args]
   (let [spec (get-dbspec)
         opts (:migrations project)]
     (when (nil? opts)
       (throw+ "migrations config is missing:"))
     (migrations-cli/run-cli project spec command))))
