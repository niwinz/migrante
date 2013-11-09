(ns migrations.cli
  (:require [migrations.core :as core])
  (:require [clojure.pprint :refer [pprint]])
  (:gen-class))

(defn- cmdlist-print-migrationstatus
  [fnvar]
  (let [metadata (meta @fnvar)]
    (println (format "[] - %s" (str fnvar)))
    (pprint metadata)))

(defn cli-command-list
  [project dbspec]
  (let [scanned (core/scan-modules (:migrations project))]
    (doseq [[nsname migrations] (seq scanned)]
      (loop [parent nil
             migs migrations]
        (if (seq migs)
          (do
            (let [m1 (filter (fn [x]
                               (let [metadata (meta @x)]
                                 (= (:migration-parent metadata) parent))) migs)
                  m1 (first m1)
                  m2 (filter (fn [x]
                               (let [metadata (meta @x)]
                                 (not= (:migration-parent metadata) parent))) migs)]
              (if (nil? m1) nil
                (do
                  (cmdlist-print-migrationstatus m1)
                  (recur (:migration-name (meta @m1)) m2))))))))))

(defn cli-command-default
  [project dbspec command]
  (println "run-cli:" dbspec))

(defn run-cli
  [project dbspec command]
  (core/bootstrap dbspec)
  (cond
    (= command "list") (cli-command-list project dbspec)
    :else (cli-command-default project dbspec command)))
