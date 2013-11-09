# migrations

Generic database migrations for Clojure.

**WARNING:** Project not yet ready for use.

## Usage

### Intallation

For use **migrations** you should configure **datasource** for database connection
parameters and define migrations modules of your project:

```clojure
(defproject yourproject "0.1.0"
  ;; [...]
  :dependencies [[datasource "0.1.0"]
                 [be.niwi/migrations "0.1.0"]
                 [org.clojure/java.jdbc "0.3.0-beta1"]
                 [postgresql "9.1-901.jdbc4"]]
  :migrations {:modules ["yourapp/migrations"]})
```

### Define migrations

For define your migrations you should use `defmigration` macro. This some examples of
defmigration syntax:

```clojure
;; src/yourapp/migrations.clj
(ns yourapp.migrations
  (:require [migrations.core :refer [defmigration]]
            [clojure.java.jdbc :as j]))

(defmigration :name "migration-1" :parent nil
  :up   (fn [db]
            (j/execute! db ["alter table migrations add column foo varchar(255);"]))
  :down (fn [db]
          (j/execute! db ["alter table migrations drop column foo;"])))

(defmigration :name "migration-2" :parent "migration-1"
  :up   (fn [db]
            (j/execute! db ["alter table migrations add column bar varchar(255);"]))
  :down (fn [db]
          (j/execute! db ["alter table migrations drop column bar;"])))
```

All migrations are executed in one transaction.

## License

Copyright © 2013 Andrey Antukh <niwi@niwi.be>

Distributed under the Apache 2.0 License.
