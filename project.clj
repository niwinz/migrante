(defproject be.niwi/migrations "0.1.0"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Apache 2.0"
            :url "http://www.apache.org/licenses/LICENSE-2.0.txt"}
  :dependencies [[datasource "0.1.0"]
                 [slingshot "0.10.3"]
                 [org.clojure/java.jdbc "0.3.0-beta1"]]
  :main ^:skip-aot migrations.test
  :target-path "target/%s"
  :resource-paths ["config"]
  :profiles {:uberjar {:aot :all}
             :dev {:dependencies [[org.clojure/clojure "1.5.1"]
                                  [postgresql "9.1-901.jdbc4"]]}}
  :migrations {:modules ["migrations/test"]}
  :eval-in-leiningen true)
