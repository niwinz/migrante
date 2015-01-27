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

(defmacro log
  [& args]
  `(when *verbose*
     (timbre/info ~@args)))

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
  {:pre [(keyword? module) (keyword? step)]}
  (let [sql (str "select * from migrations"
                 " where module=? and step=?")
        res (sc/fetch *localdb* [sql (name module) (name step)])]
    (pos? (count res))))

(defn- register-migration!
  "Register a concrete migration into local migrations database."
  [module step]
  {:pre [(keyword? module) (keyword? step)]}
  (let [sql "insert into migrations (module, step) values (?, ?)"]
    (sc/execute *localdb* [sql (name module) (name step)])))

(defn- unregister-migration!
  "Unregister a concrete migration from local migrations database."
  [module step]
  {:pre [(keyword? module) (keyword? step)]}
  (let [sql "delete from migrations where module=? and step=?;"]
    (sc/execute *localdb* [sql (name module) (name step)])))

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
  [ctx migrations {:keys [until fake] :or {fake false}}]
  (let [migrationsid (:name migrations)
        migrationsname (name migrationsid)
        steps (:steps migrations)]
    (sc/atomic ctx
      (reduce (fn [_ [stepid stepdata]]
                (when-not (migration-registred? migrationsid stepid)
                  (log (format "- Applying migration %s/%s." migrationsid stepid))
                  (sc/atomic ctx
                    (when (not fake)
                      (run-up stepdata ctx))
                    (register-migration! migrationsid stepid)))
                (when (= until stepid)
                  (reduced nil)))
              nil
              steps))))

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
  "Main entry point for apply migrations."
  ([dbspec migration] (migrate dbspec migration {}))
  ([dbspec migration {:keys [verbose fake] :or {verbose true} :as options}]
   (bootstrap-if-needed options)
   (let [context (if (satisfies? scproto/IContext dbspec)
                   dbspec
                   (sc/context dbspec))]
     (with-open [ctx context
                 lctx (localdb options)]
       (sc/atomic-apply lctx (fn [lctx]
                               (binding [*localdb* lctx
                                         *verbose* verbose]
                                 (do-migrate ctx migration options))))))))