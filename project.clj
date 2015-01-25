(defproject be.niwi/migrations "0.1.0"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "BSD (2-Clause)"
            :url "http://opensource.org/licenses/BSD-2-Clause"}
  :dependencies [[cuerdas "0.3.0"]
                 [slingshot "0.12.1"]
                 [slingshot "0.10.3"]
                 [suricatta "0.2.0"]]

  :profiles {:uberjar {:aot :all}
             :dev {:dependencies [[org.clojure/clojure "1.6.0"]
                                  [postgresql "9.3-1102.jdbc41"]
                                  [com.h2database/h2 "1.3.176"]]
                   :main ^:skip-aot migrations.test}}
  :eval-in-leiningen true)
