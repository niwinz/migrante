(ns migrante.core-tests
  (:require [clojure.test :refer :all]
            [migrante.core :as mg]
            [suricatta.core :as sc]
            [schema.core :as s]
            [suricatta.proto :as scproto]))

(def ^{:dynamic true
       :private true}
  *ctx*)

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

(defn- wrap-ctx
  "Function that wraps context in something
  that test can control."
  ([ctx] (wrap-ctx ctx false))
  ([ctx allowclose]
   (reify
     scproto/IContext
     (get-context [_] (scproto/get-context ctx))
     (get-configuration [_] (scproto/get-configuration ctx))

     java.io.Closeable
     (close [_]
       (when allowclose
         (.close ctx))))))

(defn- database-fixture
  [end]
  (with-open [ctx (sc/context dbspec)]
    (sc/atomic ctx
      (let [ctx (wrap-ctx ctx)]
        (with-redefs [mg/local-context (fn [_]
                                         (sc/execute ctx (deref #'mg/bootstrap-sql))
                                         ctx)]
          (binding [*ctx* ctx]
            (end)
            (sc/set-rollback! *ctx*)))))))

(use-fixtures :each database-fixture)

(deftest migrations-run-twice-and-preserve
  (let [result (atom 0)
        step {:up (fn [_] (swap! result inc))}
        migrations {:name :foobar
                    :steps [[:0001 step]
                            [:0002 step]
                            [:0003 step]
                            [:0004 step]]}]
    (mg/migrate *ctx* migrations)
    (is (= @result 4))

    (mg/migrate *ctx* migrations)
    (is (= @result 4))))

(deftest migrations-with-step-as-fn
  (let [result (atom 0)
        step (fn [_] (swap! result inc))
        migrations {:name :foobar
                    :steps [[:0001 step]
                            [:0002 step]]}]
    (mg/migrate *ctx* migrations)
    (is (= @result 2))
    (is (mg/migration-registred? *ctx* :foobar :0002))
    (is (mg/migration-registred? *ctx* :foobar :0001))

    (mg/rollback *ctx* migrations)
    (is (not (mg/migration-registred? *ctx* :foobar :0002)))
    (is (not (mg/migration-registred? *ctx* :foobar :0001)))))

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

    (mg/execute *ctx* "create table foo (id integer);")
    (is (thrown? Exception (mg/migrate *ctx* migrations)))

    ;; Test if first two migrations are executed correctly.
    (is (= @result 2))

    ;; Test if all database changes are rollback.
    (let [res (mg/fetch *ctx* "select * from foo;")]
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

    (mg/execute *ctx* "create table foo (id integer);")
    (mg/migrate *ctx* migrations {:until :0002})

    ;; Test if first two migrations are executed correctly.
    (is (= @result 2))

    ;; Test if only two migrations are correctly
    ;; persisted in a database.
    (let [res (mg/fetch *ctx* "select * from foo;")]
      (is (= 2 (count res))))


    (mg/rollback *ctx* migrations {:until :0002})

    ;; Test if only two migrations are correctly
    ;; removed
    (let [res (mg/fetch *ctx* "select * from foo;")]
      (is (= 1 (count res))))

    (is (not (mg/migration-registred? *ctx* :foobar :0002)))
    (is (mg/migration-registred? *ctx* :foobar :0001))))

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

    (mg/execute *ctx* "create table foo (id integer);")
    (mg/migrate *ctx* migrations {:fake true})

    ;; Test if first two migrations are executed correctly.
    (is (= @result 0))

    ;; This now should not raise exception because all
    ;; migration are faked and registred.
    (mg/migrate *ctx* migrations)))


(deftest migrations-validation
  (let [mvalidator (deref #'mg/migration-validator)
        stepfn (fn [n] {:up (fn [ctx] nil)})
        migration {:name :foobar
                   :steps [[:0001 (stepfn 1)]
                           [:0002 (stepfn 2)]]}]
    (is (nil? (mvalidator migration))))
  (let [mvalidator (deref #'mg/migration-validator)
        stepfn (fn [n] (fn [ctx] nil))
        migration {:name :foobar
                   :steps [[:0001 (stepfn 1)]
                           [:0002 (stepfn 2)]]}]
    (is (nil? (mvalidator migration)))))
