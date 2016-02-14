(ns migrante.core-tests
  (:require [clojure.test :refer :all]
            [migrante.core :as mg]
            [suricatta.core :as sc]
            [suricatta.proto :as sp]))

(def ^:dynamic *conn*)
(def ^:dynamic *mctx*)

(def ^:private dbspec {:subprotocol "postgresql"
                       :subname "//127.0.0.1/test"})

;; Declaration of migration steps

;; (def migration-0001
;;   {:up (fn [ctx]
;;          (println " (applying migration 0001)"))
;;    :down (fn [ctx]
;;            (println " (unapplying migration 0001)"))})

;; (def migration-0002
;;   {:up (fn [ctx]
;;          (println " (applying migration 0002)"))
;;    :down (fn [ctx]
;;            (println " (unapplying migration 0002)"))})

;; (def migration-0003
;;   {:up (fn [ctx]
;;          (println " (applying migration 0003)"))
;;    :down (fn [ctx]
;;            (println " (unapplying migration 0003)"))})

;; Declaration of migration
;; (def migrations
;;   {:name :testapp
;;    :steps [[:0001 migration-0001]
;;            [:0002 migration-0002]
;;            [:0003 migration-0003]]})

(defn- wrap-conn
  "Function that wraps context in something
  that test can control."
  ([conn]
   (wrap-conn conn false))
  ([conn allowclose]
   (reify
     sp/IContextHolder
     (-get-context [_] (sp/-get-context conn))
     (-get-config [_] (sp/-get-config conn))

     java.io.Closeable
     (close [_]
       (when allowclose
         (.close conn))))))

(defn- database-fixture
  [end]
  (with-open [conn (sc/context dbspec)]
    (sc/atomic conn
      (let [conn (wrap-conn conn)]
        (binding [*conn* conn
                  *mctx* (mg/context conn)]
          (end)
          (sc/set-rollback! *conn*))))))

(use-fixtures :each database-fixture)

(deftest migrations-run-twice-and-preserve
  (let [result (atom 0)
        step {:up (fn [_] (swap! result inc))}
        migrations {:name :foobar
                    :steps [[:0001 step]
                            [:0002 step]
                            [:0003 step]
                            [:0004 step]]}]
    (mg/migrate *mctx* migrations)
    (is (= @result 4))

    (mg/migrate *mctx* migrations)
    (is (= @result 4))))

(deftest migrations-with-step-as-fn
  (let [result (atom 0)
        step (fn [_] (swap! result inc))
        migrations {:name :foobar
                    :steps [[:0001 step]
                            [:0002 step]]}]

    (mg/migrate *mctx* migrations)
    (is (= @result 2))
    (is (mg/registered? *mctx* :foobar :0002))
    (is (mg/registered? *mctx* :foobar :0001))

    (mg/rollback *mctx* migrations)
    (is (not (mg/registered? *mctx* :foobar :0002)))
    (is (not (mg/registered? *mctx* :foobar :0001)))))

(deftest migrations-with-exceptions
  (let [result (atom 0)
        step (fn [n]
               (fn [ctx]
                 (swap! result inc)
                 (sc/execute ctx ["insert into foo (id) values (?)" n])))
        migrations {:name :foobar
                    :steps [[:0001 (step 1)]
                            [:0002 (step 2)]
                            [:0003 (fn [_] (throw (Exception. "test")))]]}]

    (sc/execute *conn* "create table foo (id integer);")
    (is (thrown? Exception (mg/migrate *mctx* migrations)))

    ;; Test if first two migrations are executed correctly.
    (is (= @result 2))

    ;; Test if all database changes are rollback.
    (let [res (sc/fetch *conn* "select * from foo;")]
      (is (= 0 (count res))))))

(deftest migrations-with-until-limitation
  (let [result (atom 0)
        step (fn [n]
               {:up (fn [ctx]
                      (swap! result inc)
                      (sc/execute ctx ["insert into foo (id) values (?)" n]))
                :down (fn [ctx]
                        (swap! result inc)
                        (sc/execute ctx ["delete from foo where id = ?" n]))})
        migrations {:name :foobar
                    :steps [[:0001 (step 1)]
                            [:0002 (step 2)]
                            [:0003 (fn [_] (throw (Exception. "test")))]]}]

    (sc/execute *conn* "create table foo (id integer);")
    (mg/migrate *mctx* migrations {:until :0002})

    ;; Test if first two migrations are executed correctly.
    (is (= @result 2))

    ;; Test if only two migrations are correctly
    ;; persisted in a database.
    (let [res (sc/fetch *conn* "select * from foo;")]
      (is (= 2 (count res))))

    (mg/rollback *mctx* migrations {:until :0002})

    ;; Test if only two migrations are correctly
    ;; removed
    (let [res (sc/fetch *conn* "select * from foo;")]
      (is (= 1 (count res))))

    (is (not (mg/registered? *mctx* :foobar :0002)))
    (is (mg/registered? *mctx* :foobar :0001))))

(deftest migrations-with-fake-parameter
  (let [result (atom 0)
        step (fn [n]
               (fn [ctx]
                 (swap! result inc)
                 (sc/execute ctx ["insert into foo (id) values (?)" n])))
        migrations {:name :foobar
                    :steps [[:0001 (step 1)]
                            [:0002 (step 2)]
                            [:0003 (fn [_] (throw (Exception. "test")))]]}]

    (sc/execute *conn* "create table foo (id integer);")
    (mg/migrate *mctx* migrations {:fake true})

    ;; Test if first two migrations are executed correctly.
    (is (= @result 0))

    ;; This now should not raise exception because all
    ;; migration are faked and registred.
    (mg/migrate *mctx*  migrations)))
