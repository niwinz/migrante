(ns migrations.core
  (:require [clojure.pprint :refer [pprint]])
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.java.jdbc.ddl :as ddl])
  (:gen-class))

(def ^:dynamic *migrations* (atom {}))

(defn attach-migration
  [module function]
  (let [mod-data (module @*migrations*)]
    (println "attach-migration" module function)
    (when (nil? mod-data)
      (swap! *migrations* assoc module []))
    (swap! *migrations* update-in [module] conj function)))

(defn bootstrap
  [dbspec]
  (println "Bootstraping...")
  (try
    (jdbc/db-do-commands dbspec
      (ddl/create-table :migrations
        [:module "varchar(255)"]
        [:name "varchar(255)"]))
    (catch java.sql.BatchUpdateException e
      (println "Tables exists..."))))

(defn applied-migration?
  [module name db-spec]
  (let [rows (jdbc/query db-spec
               ["SELECT status FROM migrations WHERE module = ? AND name = ?" module name])]
    (if (= (count rows) 0) false true)))

(defn scan-modules
  [opts]
  (let [opts-modules  (:modules opts)]
    (if (nil? opts-modules)
      []
      (do
        (doseq [nmod opts-modules]
          (println "LOADING:" nmod)
          (load nmod))
        @*migrations*))))

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

;; (defmigration "foo-1" nil
;;   (up [dbspec]
;;     (jdbc/db-do-commands dbspec
;;       (jdbc/create-table :foo
;;         [:name "varchar(10)"])))
;;   (down [dbspec]
;;     (jdbc/db-do-commands dbspec
;;       (ddl/drop-table :foo))))
