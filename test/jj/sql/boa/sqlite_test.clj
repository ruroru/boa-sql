(ns jj.sql.boa.sqlite-test
  (:require [clojure.test :refer [are deftest is use-fixtures]]
            [jj.sql.boa :as boa]
            [next.jdbc :as jdbc])
  (:import (java.nio.file Files Paths)))

(defn delete-db
  [db-path]
  (Files/deleteIfExists (Paths/get db-path (make-array String 0))))

(def file "file.db")
(def ds (jdbc/get-datasource {:dbtype "sqlite" :dbname file}))

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
  (let [query-fn (boa/build-query (boa/->NextJdbcAdapter) "sqlite/insert")]
    (are [input expected] (= expected (query-fn ds input))
                          {:id "id1"} [#:next.jdbc{:update-count 1}]
                          {:id "id2"} [#:next.jdbc{:update-count 1}]
                          {:id "id3"} [#:next.jdbc{:update-count 1}]
                          {:id "id4"} [#:next.jdbc{:update-count 1}]
                          (Id. "id5") [#:next.jdbc{:update-count 1}]
                          ))
  (is (= [#:users{:id "id1"}
          #:users{:id "id2"}
          #:users{:id "id3"}
          #:users{:id "id4"}
          #:users{:id "id5"}
          ]
         (verify-exists ds))))


(deftest insert-tuple
  (let [query-fn (boa/build-query (boa/->NextJdbcAdapter) "sqlite/multi-insert")
        select-fn (boa/build-query (boa/->NextJdbcAdapter) "sqlite/select-customer")]
    (are [input expected] (= expected (query-fn ds input))
                          {:customer ["username" "email" "name"]} [#:next.jdbc{:update-count 1}]
                          {:customer ["username2" "email2" "name2"]} [#:next.jdbc{:update-count 1}]
                          {:customer ["username3" "email3" "name3"]} [#:next.jdbc{:update-count 1}]
                          {:customer ["usernam4" "email4" "name4"]} [#:next.jdbc{:update-count 1}])

    (is (= [#:customers{:email    "email"
                        :id       1
                        :name     "name"
                        :username "username"}
            #:customers{:email    "email2"
                        :id       2
                        :name     "name2"
                        :username "username2"}
            #:customers{:email    "email3"
                        :id       3
                        :name     "name3"
                        :username "username3"}
            #:customers{:email    "email4"
                        :id       4
                        :name     "name4"
                        :username "usernam4"}
            ]
           (verify-customers-exists ds)))

    (is (= [{:email    "email4"
             :id       4
             :name     "name4"
             :username "usernam4"}]
           (select-fn ds {:email "email4"})))))

(defrecord Customer [user-name email name])
(deftest insert-multiple-tuples
  (let [query-fn (boa/build-query (boa/->NextJdbcAdapter) "sqlite/multi-insert")
        select-fn (boa/build-query (boa/->NextJdbcAdapter) "sqlite/select-customer")]
    (are [input expected] (= expected (query-fn ds input))
                          {:customer ["username" "email" "name"]} [#:next.jdbc{:update-count 1}]
                          {:customer ["username2" "email2" "name2"]} [#:next.jdbc{:update-count 1}]
                          {:customer ["username3" "email3" "name3"]} [#:next.jdbc{:update-count 1}]
                          {:customer ["usernam4" "email4" "name4"]} [#:next.jdbc{:update-count 1}])

    (is (= [#:customers{:email    "email"
                        :id       1
                        :name     "name"
                        :username "username"}
            #:customers{:email    "email2"
                        :id       2
                        :name     "name2"
                        :username "username2"}
            #:customers{:email    "email3"
                        :id       3
                        :name     "name3"
                        :username "username3"}
            #:customers{:email    "email4"
                        :id       4
                        :name     "name4"
                        :username "usernam4"}]
           (verify-customers-exists ds)))

    (is (= [{:email    "email4"
             :id       4
             :name     "name4"
             :username "usernam4"}]
           (select-fn ds {:email "email4"})))))



(deftest insert-multiple-tuples
  (let [query-fn (boa/build-query (boa/->NextJdbcAdapter) "sqlite/multi-insert")
        select-fn (boa/build-query (boa/->NextJdbcAdapter) "sqlite/select-customer")]
    (are [input expected] (= expected (query-fn ds input))
                          {:customer [["username" "email" "name"]
                                      ["username2" "email2" "name2"]
                                      ["username3" "email3" "name3"]
                                      ["usernam4" "email4" "name4"]]}
                          [#:next.jdbc{:update-count 4}])

    (is (= [#:customers{:email    "email"
                        :id       1
                        :name     "name"
                        :username "username"}
            #:customers{:email    "email2"
                        :id       2
                        :name     "name2"
                        :username "username2"}
            #:customers{:email    "email3"
                        :id       3
                        :name     "name3"
                        :username "username3"}
            #:customers{:email    "email4"
                        :id       4
                        :name     "name4"
                        :username "usernam4"}]
           (verify-customers-exists ds)))

    (is (= [{:email    "email4"
             :id       4
             :name     "name4"
             :username "usernam4"}]
           (select-fn ds {:email "email4"})))))


(deftest select-user-session
  (let [query-fn (boa/build-query (boa/->NextJdbcAdapter) "sqlite/insert-user-session")

        select-fn (boa/build-query (boa/->NextJdbcAdapter) "sqlite/select-user-session")]

    (query-fn ds {:username      "john_doe"
                  :session-id    "sess123"
                  :creation-date "2025-10-13 14:30:00"})
    (query-fn ds {:username      "jane_smith"
                  :session-id    "sess456"
                  :creation-date "2025-10-13 15:45:00"})

    (is (= [{:creation-date "2025-10-13 14:30:00"
             :session-id    "sess123"
             :username      "john_doe"}]
           (select-fn ds {:session "sess123"})))

    (is (= [{:creation-date "2025-10-13 15:45:00"
             :session-id    "sess456"
             :username      "jane_smith"}]
           (select-fn ds {:session "sess456"})))

    (is (= []
           (select-fn ds {:session "not-existing"})))))