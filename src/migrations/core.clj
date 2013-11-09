(ns migrations.core
  (:require [clojure.pprint :refer [pprint]])
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.java.jdbc.ddl :as ddl]
            [clojure.java.jdbc.sql :as sql])
  (:gen-class))

(def ^:dynamic *migrations* (atom {}))
(def ^:dynamic *dbspec*)
(def ^:dynamic *db*)

(defn attach-migration
  [module function]
  (let [mod-data (module @*migrations*)]
    (println "attach-migration" module function)
    (when (nil? mod-data)
      (swap! *migrations* assoc module []))
    (swap! *migrations* update-in [module] conj function)))

(defn bootstrap
  []
  (try
    (jdbc/db-do-commands *dbspec*
      (ddl/create-table :migrations
        [:module "varchar(255)"]
        [:name "varchar(255)"]))
    (catch java.sql.BatchUpdateException e
      (println "Tables exists..."))))

(defn get-migration-modules
  [opts]
  (if-let [opts-modules (:modules opts)]
    (do
      (doseq [nmod opts-modules]
        (println "LOADING:" nmod)
        (load nmod))
      @*migrations*)
    {}))

(defmacro defmigration
  [& {:keys [name parent up down]}]
  `(do
     (def ~(symbol (format "migration-%s" name))
       (with-meta {:up ~up :down ~down}
                  {:migration-name ~name
                   :migration-parent ~parent
                   :migration-module (str (.-name *ns*))}))
     (let [updater# (ns-resolve 'migrations.core 'attach-migration)]
       (updater# (keyword (.-name *ns*)) (var ~(symbol (format "migration-%s" name)))))))

(defn applied-migration?
  [module name]
  (let [rows (jdbc/query *db*
               ["SELECT module, name FROM migrations WHERE module = ? AND name = ?" module name])]
    (if (= (count rows) 0) false true)))

(defn apply-migration
  [module name]
  (jdbc/insert! *db* :migrations
             {:module module :name name}))

(defn rollback-migration
  [module name]
  (jdbc/delete! *db* :migrations (sql/where {:name name :module module})))
