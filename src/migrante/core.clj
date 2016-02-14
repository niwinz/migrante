(ns migrante.core
  (:require [suricatta.core :as sc]
            [suricatta.proto :as sp]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Private Api: Helpers
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def ^:dynamic *verbose* false)

(defmacro log
  "A simple sugar syntax helper for log information
  into the standard output."
  [& args]
  `(when *verbose*
     (println ~@args)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Private Api: Implementation
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- migration-registered?
  "Check if concrete migration is already registred."
  ([conn module step]
   {:pre [(keyword? module) (keyword? step)]}
   (let [sql (str "select * from migrations"
                  " where module=? and step=?")
         res (sc/fetch conn [sql (name module) (name step)])]
     (pos? (count res)))))

(defn- register-migration!
  "Register a concrete migration into local migrations database."
  ([conn module step]
   {:pre [(keyword? module) (keyword? step)]}
   (let [sql "insert into migrations (module, step) values (?, ?)"]
     (sc/execute conn [sql (name module) (name step)]))))

(defn- unregister-migration!
  "Unregister a concrete migration from local migrations database."
  ([conn module step]
   {:pre [(keyword? module) (keyword? step)]}
   (let [sql "delete from migrations where module=? and step=?;"]
     (sc/execute conn [sql (name module) (name step)]))))

(defn- setup!
  "Initialize the database if it is not initialized."
  [conn]
  (let [sql (str "create table if not exists migrations ("
                 " module varchar(255),"
                 " step varchar(255),"
                 " created_at timestamp,"
                 " unique(module, step)"
                 ");")]
    (sc/execute conn sql)))

(defprotocol IMigration
  "Define a migration step behavior on up and down
  migration actons."
  (-run-up [_ _] "Run function in migrate process.")
  (-run-down [_ _] "Run function in rollback process."))

(extend-protocol IMigration
  clojure.lang.IPersistentMap
  (-run-up [step conn]
    (let [upfn (:up step)]
      (upfn conn)))
  (-run-down [step conn]
    (if-let [downfn (:down step)]
      (downfn conn)))

  clojure.lang.IFn
  (-run-up [step conn]
    (step conn))
  (-run-down [step conn]
    nil))

(defn- do-migrate
  [conn migration {:keys [until fake] :or {fake false}}]
  (let [mid (:name migration)
        steps (:steps migration)]
    (sc/atomic conn
      (run! (fn [[sid sdata]]
              (when-not (migration-registered? conn mid sid)
                (log (format "- Applying migration %s/%s" mid sid))
                (sc/atomic conn
                  (when (not fake)
                    (-run-up sdata conn))
                  (register-migration! conn mid sid)))
              (when (= until sid)
                (reduced nil)))
            steps))))

(defn- do-rollback
  [conn migration {:keys [until fake] :or {fake false}}]
  (let [mid (:name migration)
        steps (reverse (:steps migration))]
    (sc/atomic conn
      (run! (fn [[sid sdata]]
              (when (migration-registered? conn mid sid)
                (log (format "- Rollback migration %s/%s" mid sid))
                (sc/atomic conn
                  (when (not fake)
                    (-run-down sdata conn))
                  (unregister-migration! conn mid sid)))
              (when (= until sid)
                (reduced nil)))
            steps))))

(defn- normalize-to-connection
  [dbspec]
  (if (satisfies? sp/IContextHolder dbspec)
    dbspec
    (sc/context dbspec)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Public Api
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn execute
  "Execute a query and return a number of rows affected."
  ([q]
   (sc/execute q))
  ([ctx q]
   (sc/execute ctx q)))

(defn fetch
  "Fetch eagerly results executing a query.

  This function returns a vector of records (default) or
  rows (depending on specified opts). Resources are relased
  inmediatelly without specific explicit action for it."
  ([ctx q]
   (sc/fetch ctx q))
  ([ctx q opts]
   (sc/fetch ctx q opts)))

(defprotocol IMigrationContext
  (-migrate [_ migration options])
  (-rollback [_ migration options])
  (-registered? [_ module step]))

(defn context
  "Create new instance of migration context."
  ([conn] (context conn nil))
  ([conn {:keys [verbose] :or {verbose true}}]
   (let [conn (normalize-to-connection conn)]
     (setup! conn)
     (reify
       java.lang.AutoCloseable
       (close [_] (.close conn))

       IMigrationContext
       (-migrate [_ migration options]
         (sc/atomic conn
           (binding [*verbose* verbose]
             (do-migrate conn migration options))))
       (-rollback [_ migration options]
         (sc/atomic conn
           (binding [*verbose* verbose]
             (do-rollback conn migration options))))
       (-registered? [_ module step]
         (migration-registered? conn module step))))))

(defn migrate
  "Main entry point for apply a migration."
  ([mctx migration]
   (migrate mctx migration nil))
  ([mctx migration options]
   (-migrate mctx migration options)))

(defn rollback
  "Main entry point for apply a migration."
  ([mctx migration]
   (rollback mctx migration nil))
  ([mctx migration options]
   (-rollback mctx migration options)))

(defn registered?
  [mctx module step]
  (-registered? mctx module step))
