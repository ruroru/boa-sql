(ns jj.sql.boa.pg-test
  (:require [clojure.test :refer [are deftest is use-fixtures]]
            [clojure.tools.logging :as logger]
            [jj.sql.boa :as boa]
            [jj.sql.boa.query.next-jdbc :refer [->NextJdbcAdapter]]
            [next.jdbc :as jdbc]
            [pg-embedded-clj.core :as pg]))

(def ds
  {:dbtype   "postgresql"
   :dbname   "test_db"
   :host     "localhost"
   :port     5432
   :user     "postgres"
   :password "postgres"
   })

(use-fixtures :each
              (fn [f]
                (logger/info "Creating database test_db")


                (pg/with-pg-fn {:port 5432}

                               (fn []
                                 (jdbc/execute! {:dbtype   "postgresql"
                                                 :host     "localhost"
                                                 :port     5432
                                                 :user     "postgres"
                                                 :password "postgres"
                                                 } ["CREATE DATABASE test_db"])

                                 (jdbc/execute! ds ["CREATE TABLE IF NOT EXISTS users (
                                 id VARCHAR(255) PRIMARY KEY\n);"])
                                 (jdbc/execute! ds ["CREATE TABLE users_with_cast (id INTEGER, numeric_id INTEGER)"])
                                 (jdbc/execute! ds ["CREATE TABLE customers (
                                 id SERIAL PRIMARY KEY, username VARCHAR(255) NOT NULL UNIQUE,
                                 email VARCHAR(255) NOT NULL UNIQUE, name VARCHAR(255) NOT NULL\n);"])
                                 (jdbc/execute! ds ["CREATE TABLE IF NOT EXISTS usersessions
                                 (id SERIAL PRIMARY KEY, username VARCHAR(255) NOT NULL,
                                 session_id VARCHAR(255) NOT NULL UNIQUE, creation_date TIMESTAMP NOT NULL\n);"])
                                 (f)
                                 ))))

(defn- verify-users-exists [ds]
  (jdbc/execute! ds ["SELECT * FROM users"]))

(defn- verify-customers-exists [ds] (jdbc/execute! ds ["SELECT * FROM customers"]))
(defrecord Id [id])
(defrecord NumericId [id numeric-id])
(deftest verify-insert
  (let [query-fn (boa/build-query (->NextJdbcAdapter) "pg/insert")]
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
         (verify-users-exists ds))))

(deftest insert-tuple
  (let [query-fn (boa/build-query (->NextJdbcAdapter) "pg/multi-insert")
        select-fn (boa/build-query (->NextJdbcAdapter) "pg/select-customer")]
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
  (let [query-fn (boa/build-query (->NextJdbcAdapter) "pg/multi-insert")
        select-fn (boa/build-query (->NextJdbcAdapter) "pg/select-customer")]
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
  (let [insert-fn (boa/build-query (->NextJdbcAdapter) "pg/insert-user-session")
        select-fn (boa/build-query (->NextJdbcAdapter) "pg/select-user-session")]

    (insert-fn ds {:username      "john_doe"
                   :session-id    "sess123"
                   :creation-date "2025-10-13 14:30:00"})
    (insert-fn ds {:username      "jane_smith"
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

(deftest verify-cast-in-pg
  (let [query-fn (boa/build-query (->NextJdbcAdapter) "pg/insert-with-cast")]
    (are [input expected] (= expected (query-fn ds input))
                          {:id 1 :numeric-id "2"} [#:next.jdbc{:update-count 1}]
                          {:id 2 :numeric-id "3"} [#:next.jdbc{:update-count 1}]
                          {:id 3 :numeric-id "4"} [#:next.jdbc{:update-count 1}]
                          {:id 4 :numeric-id "5"} [#:next.jdbc{:update-count 1}]
                          (NumericId. 5 "6") [#:next.jdbc{:update-count 1}]))

  (is (= [#:users_with_cast{:id         1
                            :numeric_id 2}
          #:users_with_cast{:id         2
                            :numeric_id 3}
          #:users_with_cast{:id         3
                            :numeric_id 4}
          #:users_with_cast{:id         4
                            :numeric_id 5}
          #:users_with_cast{:id         5
                            :numeric_id 6}]
         (jdbc/execute! ds ["SELECT * FROM users_with_cast; "])))

  )

