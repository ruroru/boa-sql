(ns jj.sql.boa.async-sqlite-test
  (:require [clojure.test :refer [are deftest is use-fixtures]]
            [jj.sql.boa :as boa]
            [jj.sql.boa.query.next-jdbc :refer [->NextJdbcAdapter]]
            [next.jdbc :as jdbc])
  (:import (java.nio.file Files Paths)
           (java.util.concurrent CompletableFuture Executors)))

(defn delete-db
  [db-path]
  (Files/deleteIfExists (Paths/get db-path (make-array String 0))))

(def file "file.db")
(def ds (jdbc/get-datasource {:dbtype "sqlite" :dbname file}))
(def executor (Executors/newFixedThreadPool 128))

(use-fixtures :each
              (fn [f]
                (delete-db file)
                (jdbc/execute! ds ["CREATE TABLE IF NOT EXISTS users (id TEXT PRIMARY KEY)"])
                (jdbc/execute! ds ["CREATE TABLE IF NOT EXISTS usersessions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    username TEXT NOT NULL,
    session_id TEXT NOT NULL UNIQUE,
    creation_date TEXT NOT NULL)"])
                (jdbc/execute! ds ["CREATE TABLE customers (id INTEGER PRIMARY KEY AUTOINCREMENT,
                username TEXT NOT NULL UNIQUE, email TEXT NOT NULL UNIQUE, name TEXT NOT NULL);"])
                (f)))

(defn- verify-exists [ds] (jdbc/execute! ds ["SELECT * FROM users"]))
(defn- verify-customers-exists [ds] (jdbc/execute! ds ["SELECT * FROM customers"]))


(defrecord Id [id])

(deftest verify-insert
  (let [set-value (atom (list ))
        error-value (atom (list ))
        query-fn (boa/build-async-query executor (->NextJdbcAdapter) "sqlite/insert")]
    (are [input] (= nil (.get ^CompletableFuture (query-fn ds input (fn [value]
                                                                      (println "------------" value)
                                                                      (swap! set-value conj value))
                                                           (fn [value]
                                                             (println "------------" value)
                                                             (swap! error-value conj value)
                                                             ))))
                 {:id "id1"}
                 {:id "id2"}
                 {:id "id3"}
                 {:id "id4"}
                 (Id. "id5")
                 )
    (is (= (list [#:next.jdbc{:update-count 1}]
            [#:next.jdbc{:update-count 1}]
            [#:next.jdbc{:update-count 1}]
            [#:next.jdbc{:update-count 1}]
            [#:next.jdbc{:update-count 1}]) @set-value)))

  (is (= [#:users{:id "id1"}
          #:users{:id "id2"}
          #:users{:id "id3"}
          #:users{:id "id4"}
          #:users{:id "id5"}]
         (verify-exists ds))))

