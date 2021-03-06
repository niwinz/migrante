(ns leiningen.migrante
  "Flexible migrations library for Clojure."
  (:refer-clojure :exclude [list])
  (:require [cuerdas.core :as str]
            [migrante.core :as mg]
            [clojure.edn :as edn]))

(defn- load-migrations
  [migrations]
  (reduce (fn [acc m]
            (let [[nsname varname] (str/split (name m) #":" 2)]
              (require (symbol nsname))
              (let [migration @(ns-resolve (symbol nsname) (symbol varname))
                    migrationid (:name migration)]
                (assoc acc migrationid migration))))
          {}
          migrations))

(defn- migrate
  "Execute migrations configured in `project.clj`.

  Usage examples:
    lein migrante migrate :myapp
    lein migrante migrate :myapp :0002

  The `:myapp` represents a name given to the
  migrations data structure. And it's only detects
  migrations referencied in `:migrations` option on
  `:migrante` profile on your `project.clj`.

  If you run migrate subcommand without passing it
  an migration module name, all migrations will
  be applied."
  ([project]
   (println "Missing migration name (try lein migrante migrate :myapp)."))
  ([project name]
   (migrate project name nil))
  ([project name until]
   (let [name (edn/read-string name)
         until (edn/read-string until)
         profile (:migrante (:profiles project))
         dbspec (:dbspec profile)
         options (:options profile)
         migrations (load-migrations (:migrations profile []))
         migration (get migrations name)]
     (if (nil? migration)
       (println "Migration not found.")
       (with-open [mctx (mg/context dbspec options)]
         (mg/migrate mctx migration {:until until}))))))

(defn- list
  "Show all registred migration modules and all migration
  steps of each migration module.

  Usage examples:
    lein migrante list
    lein migrante list :myapp

  The first command list all registred migration modules
  and the second shows the migration steps with corresponding
  state (applied or not) of selected migration module."
  [project]
  (let [profile (:migrante (:profiles project))
        dbspec (:dbspec profile)
        options (:options profile)
        migrations (load-migrations (:migrations profile []))]
    (with-open [mctx (mg/context dbspec options)]
      (println "Registred migration modules:")
      (doseq [[mid migration] migrations]
        (let [description (or (:desc migration) "(without description)")]
          (println (format "* %s %s" mid description))
          (doseq [[sid _] (:steps migration)]
            (if (mg/registered? mctx mid sid)
              (println (format " - [x] %s" sid))
              (println (format " - [ ] %s" sid)))))))))

(defn- help
  "Show the help string."
  ([] nil)
  ([project command params]
   (let [command (keyword command)]
     (condp = (keyword command)
       :help (println "Help")
       (let [message (format "Command %s not found (try lein migrante help)."
                             command)]
         (println message))))))

(defn ^{:subtasks [#'migrate #'help #'list]}
  migrante
  ([project] (help project :help))
  ([project command & args]
   (condp = (keyword command)
     :migrate (apply migrate project args)
     :list (apply list project args)
     (help project command args))))
