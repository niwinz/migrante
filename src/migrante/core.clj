(ns migrante.core
  (:require [clojure.pprint :refer (pprint)]
            [schema.core :as s]
            [taoensso.timbre :as timbre]
            [slingshot.slingshot :refer [throw+ try+]]
            [suricatta.core :as sc]
            [suricatta.proto :as scproto]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Private Api: Helpers
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def ^:dynamic *verbose* false)

(defmacro log
  "A simple sugar syntax helper for log information
  into the standard output."
  [& args]
  `(when *verbose*
     (timbre/info ~@args)))

(def ^:private
  bootstrap-sql
  (str "create table if not exists migrations ("
       " module varchar(255),"
       " step varchar(255),"
       " created_at timestamp,"
       " unique(module, step)"
       ");"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Private Api: Validators
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def ^{:private true
       :doc "A schema representaion of the migration module data structure."}
  migration-validator
  (s/checker {:name s/Keyword
              :steps [[(s/one s/Keyword "stepname")
                       (s/either (s/pred fn?)
                                 {:up (s/pred fn?)
                                  (s/optional-key :down) (s/pred fn?)})]]}))

(defn- migration?
  [m]
  (nil? (migration-validator m)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Private Api: Implementation
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn migration-registred?
  "Check if concrete migration is already registred."
  ([ctx module step]
   {:pre [(keyword? module) (keyword? step)]}
   (let [sql (str "select * from migrations"
                  " where module=? and step=?")
         res (sc/fetch ctx [sql (name module) (name step)])]
     (pos? (count res)))))

(defn- register-migration!
  "Register a concrete migration into local migrations database."
  ([ctx module step]
   {:pre [(keyword? module) (keyword? step)]}
   (let [sql "insert into migrations (module, step) values (?, ?)"]
     (sc/execute ctx [sql (name module) (name step)]))))

(defn- unregister-migration!
  "Unregister a concrete migration from local migrations database."
  ([ctx module step]
   {:pre [(keyword? module) (keyword? step)]}
   (let [sql "delete from migrations where module=? and step=?;"]
     (sc/execute ctx [sql (name module) (name step)]))))

(defn local-context
  [{:keys [localdb] :or {localdb "_migrations.h2"}}]
  (let [dbspec {:subprotocol "h2" :subname localdb}
        ctx    (sc/context dbspec)]
    (sc/execute ctx bootstrap-sql)
    ctx))

(defprotocol IMigration
  "Define a migration step behavior on up and down
  migration actons."
  (^:private run-up [_ _] "Run function in migrate process.")
  (^:private run-down [_ _] "Run function in rollback process."))

(extend-protocol IMigration
  clojure.lang.IPersistentMap
  (run-up [step ctx]
    (let [upfn (:up step)]
      (upfn ctx)))
  (run-down [step ctx]
    (if-let [downfn (:down step)]
      (downfn ctx)))

  clojure.lang.IFn
  (run-up [step ctx]
    (step ctx))
  (run-down [step ctx]
    nil))

(defn- do-migrate
  [mctx lctx migration {:keys [until fake] :or {fake false}}]
  (let [mid (:name migration)
        steps (:steps migration)]
    (sc/atomic mctx
      (reduce (fn [_ [sid sdata]]
                (when-not (migration-registred? lctx mid sid)
                  (log (format "- Applying migration %s/%s." mid sid))
                  (sc/atomic mctx
                    (when (not fake)
                      (run-up sdata mctx))
                    (register-migration! lctx mid sid)))
                (when (= until sid)
                  (reduced nil)))
              nil
              steps))))

(defn- do-rollback
  [mctx lctx migration {:keys [until fake] :or {fake false}}]
  (let [mid (:name migration)
        steps (reverse (:steps migration))]
    (sc/atomic mctx
      (reduce (fn [_ [sid sdata]]
                (when (migration-registred? lctx mid sid)
                  (log (format "- Rollback migration %s/%s." mid sid))
                  (sc/atomic mctx
                    (when (not fake)
                      (run-down sdata mctx))
                    (unregister-migration! lctx mid sid)))
                (when (= until sid)
                  (reduced nil)))
              nil
              steps))))

(defn- normalize-to-context
  [dbspec]
  (if (satisfies? scproto/IContext dbspec)
    dbspec
    (sc/context dbspec)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Public Api
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

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

(defn migrate
  "Main entry point for apply a migration."
  ([dbspec migration] (migrate dbspec migration {}))
  ([dbspec migration {:keys [verbose] :or {verbose true} :as options}]
   {:pre [(migration? migration)]}
   (with-open [mctx (normalize-to-context dbspec)
               lctx (local-context options)]
     (sc/atomic lctx
       (binding [*verbose* verbose]
         (do-migrate mctx lctx migration options))))))

(defn rollback
  "Main entry point for rollback migrations."
  ([dbspec migration] (rollback dbspec migration {}))
  ([dbspec migration {:keys [verbose] :or {verbose true} :as options}]
   {:pre [(migration? migration)]}
   (with-open [mctx (normalize-to-context dbspec)
               lctx (local-context options)]
     (sc/atomic lctx
       (binding [*verbose* verbose]
         (do-rollback mctx lctx migration options))))))
