(ns suricatta.core-test
  (:require [clojure.test :refer :all]
            [migrations.core :as mg]))

(def ^{:dynamic true
       :private true}
  *ctx*)

(def ^:private dbspec {:subprotocol "postgresql"
                       :subname "//127.0.0.1/test"})

(defn- database-fixture
  [end]
  (with-open [ctx (sc/context pgdbspec)]
    (sc/atomic ctx
      (binding [*ctx* ctx]
        (end)
        (sc/set-rollback! ctx)))))

(use-fixtures :each database-fixture)

;; Declaration of migration steps

;; (def migration-0001
;;   {:up (fn [ctx] (println " (applying migration 0001)"))
;;    :down (fn [ctx] (println " (unapplying migration 0001)"))})

;; (def migration-0002
;;   {:up (fn [ctx] (println " (applying migration 0002)"))
;;    :down (fn [ctx] (println " (unapplying migration 0002)"))})

;; (def migration-0003
;;   {:up (fn [ctx] (println " (applying migration 0003)"))
;;    :down (fn [ctx] (println " (unapplying migration 0003)"))})

;; ;; Declaration of migration

;; (def migrations
;;   {:name "testapp"
;;    :steps [migration-0001
;;            migration-0002]})

;; ;; Usage example

;; (mg/migrate dbspec migrations)
;; (mg/migrate dbspec migrations {:verbose true})

;; (mg/migrate dbspec migrations {:verbose true
;;                                :until :migration-0002
;;                                :fake true})
