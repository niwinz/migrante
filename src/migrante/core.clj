(ns migrante.core
  (:require [clojure.pprint :refer (pprint)]
            [slingshot.slingshot :refer [throw+ try+]]
            [suricatta.core :as sc]))

(def ^:dynamic *localdb* nil)
(def ^:dynamic *verbose* false)

(def ^:private
  sql (str "create table if not exists migrations ("
           " name varchar(255),"
           " created_at datetime,"
           " migration varchar(255),"
           " unique(name, migration)"
           ");"))

(defn- localdb
  "Get a suricatta opened context to the local state database."
  [{:keys [localdb] :or {localdb "_migrations.h2"}}]
  (let [dbspec {:subprotocol "h2" :subname localdb}]
    (sc/context dbspec)))

(defn- migration-registred?
  "Check if concrete migration is already registred."
  [modname name]
  (let [sql (str "select name, migration from migrations"
                 " where name=? and migration=?")
        res (sc/fetch *localdb* [sql modname name])]
    (pos? (count res))))

(defn- register-migration!
  "Register a concrete migration into local migrations database."
  [modname name]
  (let [sql "insert into migrations (name, migration) values (?, ?)"]
    (sc/execute *localdb* [sql modname name])))

(defn- unregister-migration!
  "Unregister a concrete migration from local migrations database."
  [modname name]
  (let [sql "delete from migrations where name=? and migration=?;"]
    (sc/execute *localdb* [sql modname name])))

(defn- bootstrap-if-needed
  "Bootstrap the initial database for store migrations."
  [options]
  (with-open [ctx (localdb options)]
    (sc/execute ctx sql)))

(defn- do-migrate
  [ctx {:keys [name steps]} {:keys [fake until] :or [fake false]}]
  (sc/atomic ctx
    (doseq [step steps]
      (let [upfn (:up step)]
        (sc/atomic ctx (upfn ctx))))))

(defn migrate
  "Main entry point for apply migrations."
  ([dbspec migration] (migration dbspec migration {}))
  ([dbspec migration {:keys [verbose] :or {verbose true} :as options}]
   (bootstrap-if-neeed options)
   (binding [*localdb* (localdb options)
             *verbose* verbose]
     (with-open [ctx (sc/context dbspec)]
       (do-migrate ctx migration options)))))
