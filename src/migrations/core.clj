(ns migrations.core
  (:require [clojure.pprint :refer (pprint)]
            [slingshot.slingshot :refer [throw+ try+]]
            [suricatta.core :as sc])
  (:import org.mapdb.DBMaker))

;; (def ^:dynamic *migrations* (atom {}))
;; (def ^:dynamic *db*)

;; (defn bootstrap
;;   [dbspec]
;;   (try
;;     (jdbc/db-do-commands dbspec
;;       (ddl/create-table :migrations
;;         [:module "varchar(255)"]
;;         [:name "varchar(255)"]))
;;     (catch java.sql.BatchUpdateException e)))

;; (defn load-migration-modules
;;   [migrations-cfg]
;;   (when-let [opts-modules (:modules migrations-cfg)]
;;     (doall (map load opts-modules))))

;; (defn attach-migration
;;   "Function used by defmigration macro
;;   to register migration."
;;   [module function]
;;   (let [mod-data (module @*migrations*)]
;;     (when (nil? mod-data)
;;       (swap! *migrations* assoc module []))
;;     (swap! *migrations* update-in [module] conj function)))

;; (defmacro defmigration
;;   [& {:keys [name parent up down]}]
;;   `(do
;;      (def ~(symbol (format "migration-%s" name))
;;        (with-meta {:up ~up :down ~down}
;;                   {:migration-name ~name
;;                    :migration-parent ~parent
;;                    :migration-module (str (.-name *ns*))}))
;;      (let [updater# (ns-resolve 'migrations.core 'attach-migration)]
;;        (updater# (keyword (.-name *ns*)) (var ~(symbol (format "migration-%s" name)))))))

;; (defn applied-migration?
;;   [module name]
;;   (let [rows (jdbc/query *db*
;;                ["SELECT module, name FROM migrations WHERE module = ? AND name = ?" module name])]
;;     (if (= (count rows) 0) false true)))

;; (defn apply-migration
;;   [module name]
;;   (jdbc/insert! *db* :migrations
;;              {:module module :name name}))

;; (defn rollback-migration
;;   [module name]
;;   (jdbc/delete! *db* :migrations (sql/where {:name name :module module})))


(defn get-localdb
  "Get an instance of local db."
  []
  (let [file (java.io.File. "./migrations.db")
        db (doto (DBMaker/newFileDB file)
             (.closeOnJvmShutdown))]
    (.make db)))


(defn migrate
  "Main entry point for apply migrations."
  ([dbspec migration] (migration dbspec migration {}))
  ([dbspec migration {:keys [verbose until fake]
                      :or {verbose true fake false}}]
   (with-open [ctx (sc/context dbspec)]
     (let [name (keyword (:name migration))]
       (bootstrap-if-neeed ctx name)
