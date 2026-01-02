(ns jj.sql.boa.h2-test
  (:require [clojure.test :refer [are deftest is use-fixtures]]
            [jj.sql.boa :as boa]
            [next.jdbc :as jdbc])
  (:import (java.nio.file Files Paths)))

(defn delete-db
  [db-path]
  (Files/deleteIfExists (Paths/get (str db-path ".mv.db") (make-array String 0)))
  (Files/deleteIfExists (Paths/get (str db-path ".trace.db") (make-array String 0))))

(def file "file")
(def ds (jdbc/get-datasource {:dbtype "h2" :dbname file}))

(use-fixtures :each
              (fn [f]
                (delete-db file)
                (jdbc/execute! ds ["DROP TABLE IF EXISTS users"])
                (jdbc/execute! ds ["DROP TABLE IF EXISTS usersessions"])
                (jdbc/execute! ds ["DROP TABLE IF EXISTS customers"])
                (jdbc/execute! ds ["CREATE TABLE IF NOT EXISTS users (id VARCHAR(255) PRIMARY KEY)"])
                (jdbc/execute! ds ["CREATE TABLE IF NOT EXISTS usersessions (
    id INTEGER PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(255) NOT NULL,
    session_id VARCHAR(255) NOT NULL UNIQUE,
    creation_date VARCHAR(255) NOT NULL)"])
                (jdbc/execute! ds ["CREATE TABLE customers (id INTEGER PRIMARY KEY AUTO_INCREMENT,
                username VARCHAR(255) NOT NULL UNIQUE, email VARCHAR(255) NOT NULL UNIQUE, name VARCHAR(255) NOT NULL)"])
                (f)))

(defn- verify-exists [ds] (jdbc/execute! ds ["SELECT * FROM users"]))
(defn- verify-customers-exists [ds] (jdbc/execute! ds ["SELECT * FROM customers"]))

(defrecord Id [id])

(deftest verify-insert
  (let [query-fn (boa/build-query (boa/->NextJdbcAdapter) "h2/insert")]
    (are [input expected] (= expected (query-fn ds input))
                          {:id "id1"} [#:next.jdbc{:update-count 1}]
                          {:id "id2"} [#:next.jdbc{:update-count 1}]
                          {:id "id3"} [#:next.jdbc{:update-count 1}]
                          {:id "id4"} [#:next.jdbc{:update-count 1}]
                          (Id. "id5") [#:next.jdbc{:update-count 1}]
                          ))
  (is (= [#:USERS{:ID "id1"}
          #:USERS{:ID "id2"}
          #:USERS{:ID "id3"}
          #:USERS{:ID "id4"}
          #:USERS{:ID "id5"}]
         (verify-exists ds))))


(deftest insert-tuple
  (let [query-fn (boa/build-query (boa/->NextJdbcAdapter) "h2/multi-insert")
        select-fn (boa/build-query (boa/->NextJdbcAdapter) "h2/select-customer")]
    (are [input expected] (= expected (query-fn ds input))
                          {:customer ["username" "email" "name"]} [#:next.jdbc{:update-count 1}]
                          {:customer ["username2" "email2" "name2"]} [#:next.jdbc{:update-count 1}]
                          {:customer ["username3" "email3" "name3"]} [#:next.jdbc{:update-count 1}]
                          {:customer ["usernam4" "email4" "name4"]} [#:next.jdbc{:update-count 1}])

    (is (= [#:CUSTOMERS{:EMAIL    "email"
                        :ID       1
                        :NAME     "name"
                        :USERNAME "username"}
            #:CUSTOMERS{:EMAIL    "email2"
                        :ID       2
                        :NAME     "name2"
                        :USERNAME "username2"}
            #:CUSTOMERS{:EMAIL    "email3"
                        :ID       3
                        :NAME     "name3"
                        :USERNAME "username3"}
            #:CUSTOMERS{:EMAIL    "email4"
                        :ID       4
                        :NAME     "name4"
                        :USERNAME "usernam4"}]
           (verify-customers-exists ds)))

    (is (= [{:email    "email4"
             :id       4
             :name     "name4"
             :username "usernam4"}]
           (select-fn ds {:email "email4"})))))

(defrecord Customer [user-name email name])

(deftest insert-multiple-tuples
  (let [query-fn (boa/build-query (boa/->NextJdbcAdapter) "h2/multi-insert")
        select-fn (boa/build-query (boa/->NextJdbcAdapter) "h2/select-customer")]
    (are [input expected] (= expected (query-fn ds input))
                          {:customer [["username" "email" "name"]
                                      ["username2" "email2" "name2"]
                                      ["username3" "email3" "name3"]
                                      ["usernam4" "email4" "name4"]]}
                          [#:next.jdbc{:update-count 4}])

    (is (= [#:CUSTOMERS{:EMAIL    "email"
                        :ID       1
                        :NAME     "name"
                        :USERNAME "username"}
            #:CUSTOMERS{:EMAIL    "email2"
                        :ID       2
                        :NAME     "name2"
                        :USERNAME "username2"}
            #:CUSTOMERS{:EMAIL    "email3"
                        :ID       3
                        :NAME     "name3"
                        :USERNAME "username3"}
            #:CUSTOMERS{:EMAIL    "email4"
                        :ID       4
                        :NAME     "name4"
                        :USERNAME "usernam4"}]
           (verify-customers-exists ds)))

    (is (= [{:email    "email4"
             :id       4
             :name     "name4"
             :username "usernam4"}]
           (select-fn ds {:email "email4"})))))


(deftest select-user-session
  (let [query-fn (boa/build-query (boa/->NextJdbcAdapter) "h2/insert-user-session")
        select-fn (boa/build-query (boa/->NextJdbcAdapter) "h2/select-user-session")]

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