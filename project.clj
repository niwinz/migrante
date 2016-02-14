(defproject migrante "0.1.0-SNAPSHOT"
  :description "Flexible database migration library for Clojure."
  :url "https://github.com/niwibe/migrante"
  :license {:name "BSD (2-Clause)"
            :url "http://opensource.org/licenses/BSD-2-Clause"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [funcool/cuerdas "0.7.1"]
                 [funcool/suricatta "0.8.1"]
                 [org.postgresql/postgresql "9.4.1207"]
                 [com.h2database/h2 "1.4.191"]]
  :test-paths ["test"]
  :profiles
  {:dev {:main ^:skip-aot migrations.test
         :plugins [[lein-ancient "0.6.7"]]}

   :migrante {:dbspec {:subprotocol "postgresql"
                       :subname "//127.0.0.1/test"}
              :migrations [migrante.test:mymigrations]}}
   :eval-in-leiningen true)



