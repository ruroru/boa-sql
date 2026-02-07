(ns jj.sql.boa.mariadb_test
  (:require [clojure.test :refer [are deftest is use-fixtures]]
            [clojure.tools.logging :as logger]
            [embedded.mariadb :as mariadb]
            [jj.sql.boa :as boa]
            [jj.sql.boa.query.next-jdbc :refer [->NextJdbcAdapter]]
            [next.jdbc :as jdbc]))

(def ds-without-database (jdbc/get-datasource
                           {:dbtype "mariadb"
                            :host   "localhost"
                            :port   4306}))

(def ds {:dbtype            "mariadb"
         :dbname            "test_db"
         :host              "localhost"
         :port              4306
         :useUnicode        "true"
         :characterEncoding "utf8"})

(use-fixtures :each
              (fn [f]

                (mariadb/with-db
                  (fn []
                    (logger/info "Creating database test_db")
                    (jdbc/execute! ds-without-database ["CREATE DATABASE test_db CHARACTER SET utf8mb4
                    COLLATE utf8mb4_general_ci;"])
                    (jdbc/execute! ds ["CREATE TABLE  IF NOT EXISTS  test_db.users (
                                    id VARCHAR(255) PRIMARY KEY)
                                    ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"])
                    (jdbc/execute! ds ["CREATE TABLE customers (id INTEGER PRIMARY KEY AUTO_INCREMENT,
                    username VARCHAR(255) NOT NULL UNIQUE, email VARCHAR(255) NOT NULL UNIQUE,
                    name VARCHAR(255) NOT NULL\n);"])
                    (jdbc/execute! ds ["CREATE TABLE IF NOT EXISTS test_db.usersessions (
    id INTEGER PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(255) NOT NULL,
    session_id VARCHAR(255) NOT NULL UNIQUE,
    creation_date DATETIME NOT NULL
)"])
                    (f)
                    (jdbc/execute! ds ["DROP TABLE IF EXISTS test_db.customers"])
                    (jdbc/execute! ds ["DROP TABLE IF EXISTS test_db.users"]))

                  {:port     4306
                   :on-error (fn [e]
                               (.printStackTrace ^Exception e))})))

(defn- verify-users-exists [ds]
  (jdbc/execute! ds ["SELECT * FROM users"]))

(defn- verify-customers-exists [ds] (jdbc/execute! ds ["SELECT * FROM customers"]))
(defrecord Id [id])
(deftest verify-insert
  (let [query-fn (boa/build-query (->NextJdbcAdapter) "mariadb/insert")]
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
  (let [query-fn (boa/build-query (->NextJdbcAdapter) "mariadb/multi-insert")
        select-fn (boa/build-query (->NextJdbcAdapter) "mariadb/select-customer")]
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
  (let [query-fn (boa/build-query (->NextJdbcAdapter) "mariadb/multi-insert")
        select-fn (boa/build-query (->NextJdbcAdapter) "mariadb/select-customer")]
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
  (let [query-fn (boa/build-query (->NextJdbcAdapter) "mariadb/insert-user-session")
        select-fn (boa/build-query (->NextJdbcAdapter) "mariadb/select-user-session")
        select-all-fn (boa/build-query (->NextJdbcAdapter) "mariadb/select-all-user-sessions")]

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

    (is (= [{:creation-date "2025-10-13 14:30:00"
             :session-id    "sess123"
             :username      "john_doe"}
            {:creation-date "2025-10-13 15:45:00"
             :session-id    "sess456"
             :username      "jane_smith"}]
           (select-all-fn ds)))

    (is (= []
           (select-fn ds {:session "not-existing"})))))
