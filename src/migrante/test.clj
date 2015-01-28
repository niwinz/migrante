(ns migrante.test)

(def ^:private
  migration-0001
  {:up (fn [ctx]
         (println " (applying migration 0001)"))
   :down (fn [ctx]
           (println " (unapplying migration 0001)"))})

(def ^:private
  migration-0002
  {:up (fn [ctx]
         (println " (applying migration 0002)"))
   :down (fn [ctx]
           (println " (unapplying migration 0002)"))})

(def ^:private
  migration-0003
  {:up (fn [ctx]
         (println " (applying migration 0003)"))
   :down (fn [ctx]
           (println " (unapplying migration 0003)"))})

(def mymigrations
  {:name :testapp
   :steps [[:0001 migration-0001]
           [:0002 migration-0002]
           [:0003 migration-0003]]})
