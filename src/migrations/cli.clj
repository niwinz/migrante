(ns migrations.cli
  (:require [clojure.java.jdbc :as jdbc])
  (:require [migrations.core :as core])
  (:require [clojure.pprint :refer [pprint]])
  (:gen-class))

(defn- ordered-migrations
  "Analyze all migrations and return dependency ordered
  vector with migration functions."
  [migrations]
  (let [result (atom [])]
    (doseq [[nsname migrations-list] (seq migrations)]
      (loop [parent nil
             migs migrations-list]
        (if (seq migs)
          (do
            (let [m1 (filter (fn [x]
                               (let [metadata (meta @x)]
                                 (= (:migration-parent metadata) parent))) migs)
                  m1 (first m1)
                  m2 (filter (fn [x]
                               (let [metadata (meta @x)]
                                 (not= (:migration-parent metadata) parent))) migs)]
              (if (nil? m1) nil
                (do
                  (swap! result conj m1)
                  (recur (:migration-name (meta @m1)) m2))))))))
    @result))

(defn- cmdlist-print-migrationstatus
  [fnvar]
  (let [metadata        (meta @fnvar)
        module-name     (:migration-module metadata)
        migration-name  (:migration-name metadata)]
    (if (core/applied-migration? module-name migration-name)
      (println (format "[x] - %s/%s" module-name migration-name))
      (println (format "[ ] - %s/%s" module-name migration-name)))))

(defn- cli-command-list
  [project]
  (let [migration-modules (core/get-migration-modules (:migrations project))]
    (doseq [m (ordered-migrations migration-modules)]
      (cmdlist-print-migrationstatus m))))

(defn- cli-command-migrate-all
  [project]
  (let [migration-modules (core/get-migration-modules (:migrations project))]
    (doseq [m (ordered-migrations migration-modules)]
      (let [metadata        (meta @m)
            module-name     (:migration-module metadata)
            migration-name  (:migration-name metadata)]
        (when-not (core/applied-migration? module-name migration-name)
          (apply (:up @m) [core/*db*])
          (core/apply-migration module-name migration-name)
          (println (format "Applying migration: %s/%s" module-name migration-name)))))))

(defn- cli-command-rollback
  [project module-name migration-name]
  (let [migration-modules (core/get-migration-modules (:migrations project))
        filtered          ((keyword module-name) migration-modules)
        rollback-pending  (atom [])]
    (loop [ms (reverse filtered)]
      (let [metadata  (meta @(first ms))
            modname   (:migration-module metadata)
            migname   (:migration-name metadata)]

        (swap! rollback-pending conj (first ms))
        (when-not (= migname migration-name)
          (recur (next ms)))))

    (doseq [m @rollback-pending]
      (let [metadata        (meta @m)
            module-name     (:migration-module metadata)
            migration-name  (:migration-name metadata)]
        (when (core/applied-migration? module-name migration-name)
          (apply (:down @m) [core/*db*])
          (core/rollback-migration module-name migration-name)
          (println (format "Rollback migration: %s/%s" module-name migration-name)))))))

(defn- cli-command-default
  [project command]
  (println "run-cli:" core/*dbspec*))

(defn run-cli
  [project dbspec command & args]
  (binding [core/*dbspec* dbspec]
    (core/bootstrap)
    (jdbc/db-transaction [db dbspec]
      (binding [core/*db* db]
        (cond
          (= command "list") (cli-command-list project)
          (= command "migrate-all") (cli-command-migrate-all project)
          (= command "rollback") (apply cli-command-rollback (cons project (vec args)))
          :else (cli-command-default project command))))))
