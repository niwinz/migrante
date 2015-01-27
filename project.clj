(defproject migrante "0.1.0-SNAPSHOT"
  :description "Flexible database migration library for Clojure."
  :url "https://github.com/niwibe/migrante"
  :license {:name "BSD (2-Clause)"
            :url "http://opensource.org/licenses/BSD-2-Clause"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [cuerdas "0.3.0"]
                 [com.taoensso/timbre "3.3.1"]
                 [slingshot "0.12.1"]
                 [suricatta "0.2.0"]
                 [com.h2database/h2 "1.3.176"]]
  :profiles {:dev {:dependencies [[postgresql "9.3-1102.jdbc41"]
                                  [com.h2database/h2 "1.3.176"]]
                   :main ^:skip-aot migrations.test}}
  :eval-in-leiningen true)
