(ns migrante.core
  (:require [clojure.pprint :refer (pprint)]
            [taoensso.timbre :as timbre]
            [slingshot.slingshot :refer [throw+ try+]]
            [suricatta.core :as sc]
            [suricatta.proto :as scproto]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Private Api
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def ^:dynamic *localdb* nil)
(def ^:dynamic *verbose* false)
(def ^:dynamic *fake* false)

(def ^:private
  sql (str "create table if not exists migrations ("
           " module varchar(255),"
           " step varchar(255),"
           " created_at timestamp,"
           " unique(module, step)"
           ");"))

(defn- localdb
  "Get a suricatta opened context to the local state database."
  [{:keys [localdb] :or {localdb "_migrations.h2"}}]
  (let [dbspec {:subprotocol "h2" :subname localdb}]
    (sc/context dbspec)))

(defn- migration-registred?
  "Check if concrete migration is already registred."
  [module step]
  {:pre [(string? module) (string? step)]}
  (let [sql (str "select * from migrations"
                 " where module=? and step=?")
        res (sc/fetch *localdb* [sql module step])]
    (pos? (count res))))

(defn- register-migration!
  "Register a concrete migration into local migrations database."
  [module step]
  {:pre [(string? module) (string? step)]}
  (let [sql "insert into migrations (module, step) values (?, ?)"]
    (sc/execute *localdb* [sql module step])))

(defn- unregister-migration!
  "Unregister a concrete migration from local migrations database."
  [module step]
  {:pre [(string? module) (string? step)]}
  (let [sql "delete from migrations where module=? and step=?;"]
    (sc/execute *localdb* [sql module step])))

(defn- bootstrap-if-needed
  "Bootstrap the initial database for store migrations."
  [options]
  (with-open [ctx (localdb options)]
    (sc/execute ctx sql)))


(defprotocol IMigration
  (run-up [_ _] "Run function in migrate process.")
  (run-down [_ _] "Run function in rollback process."))

(extend-protocol IMigration
  clojure.lang.IPersistentMap
  (run-up [step ctx]
    (let [upfn (:up step)]
      (upfn ctx)))

  (run-down [step ctx]
    (let [downfn (:down step)]
      (downfn ctx)))

  clojure.lang.IFn
  (run-up [step ctx]
    (step ctx))
  (run-down [step ctx]
    nil))

(defn- do-migrate
  [ctx migration {:keys [until]}]
  (let [modname (name (:name migration))
        steps (:steps migration)]
    (sc/atomic ctx
      (reduce (fn [_ [stepname step]]
                (let [stepname (name stepname)]
                  (when-not (migration-registred? modname (name stepname))
                    (timbre/info (format "- Applying migration [%s] %s." name stepname))
                    (sc/atomic ctx
                      (run-up step ctx)
                      (register-migration! modname stepname)))))
              nil
              steps))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Public Api
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn execute
  "Execute a query and return a number of rows affected."
  ([q]
   (when (false? *fake*)
     (sc/execute q)))
  ([ctx q]
   (when (false? *fake*)
     (sc/execute q ctx))))

(defn fetch
  "Fetch eagerly results executing a query.

  This function returns a vector of records (default) or
  rows (depending on specified opts). Resources are relased
  inmediatelly without specific explicit action for it."
  ([q]
   (when (false? *fake*)
     (sc/fetch q)))
  ([ctx q]
   (when (false? *fake*)
     (sc/fetch q ctx)))
  ([ctx q opts]
   (when (false? *fake*)
     (sc/fetch q ctx opts))))

(defn migrate
  "Main entry point for apply migrations."
  ([dbspec migration] (migrate dbspec migration {}))
  ([dbspec migration {:keys [verbose fake] :or {verbose true fake false} :as options}]
   (bootstrap-if-needed options)
   (let [context (if (satisfies? scproto/IContext dbspec)
                   dbspec
                   (sc/context dbspec))]
     (with-open [ctx context
                 lctx (localdb options)]
       (sc/atomic-apply lctx (fn [lctx]
                               (binding [*localdb* lctx
                                         *verbose* verbose
                                         *fake* fake]
                                 (do-migrate ctx migration options))))))))
