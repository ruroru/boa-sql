(ns jj.sql.boa.async-pg-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [clojure.tools.logging :as logger]
            [jj.sql.async-boa :as boa]
            [jj.sql.boa.query.next-jdbc-async :refer [->NextJdbcAdapter]]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]
            [pg-embedded-clj.core :as pg])
  (:import (java.util.concurrent CountDownLatch Executors TimeUnit)))

(defonce ds (atom nil))

(def ^:const test-port 54321)

(def ^:private executor (Executors/newFixedThreadPool 10))

(defn- db-spec []
  {:dbtype   "postgresql"
   :host     "localhost"
   :port     test-port
   :user     "postgres"
   :password "postgres"
   :dbname   "test_db"})

(defn- verify-users [ds]
  (jdbc/execute! ds ["SELECT * FROM users ORDER BY id"]
                 {:builder-fn rs/as-unqualified-lower-maps}))

(defn- verify-customers [ds]
  (jdbc/execute! ds ["SELECT * FROM customers ORDER BY id"]
                 {:builder-fn rs/as-unqualified-lower-maps}))

(defrecord Id [id])
(defrecord NumericId [id numeric-id])

(use-fixtures :each
              (fn [f]
                (logger/info "Starting embedded Postgres on port" test-port)
                (pg/with-pg-fn {:port test-port}
                               (fn []
                                 (try
                                   (let [system-db {:dbtype   "postgresql"
                                                    :host     "localhost"
                                                    :port     test-port
                                                    :user     "postgres"
                                                    :password "postgres"
                                                    :dbname   "postgres"}]
                                     (jdbc/execute! system-db ["CREATE DATABASE test_db"]))
                                   (reset! ds (jdbc/get-datasource (db-spec)))
                                   (jdbc/execute! @ds ["CREATE TABLE IF NOT EXISTS users (id TEXT PRIMARY KEY)"])
                                   (jdbc/execute! @ds ["CREATE TABLE users_with_cast (id INTEGER, numeric_id INTEGER)"])
                                   (jdbc/execute! @ds ["CREATE TABLE customers (
                                   id SERIAL PRIMARY KEY, username VARCHAR(255) NOT NULL UNIQUE,
                                   email VARCHAR(255) NOT NULL UNIQUE, name VARCHAR(255) NOT NULL)"])
                                   (jdbc/execute! @ds ["CREATE TABLE IF NOT EXISTS usersessions
                                   (id SERIAL PRIMARY KEY, username VARCHAR(255) NOT NULL,
                                   session_id VARCHAR(255) NOT NULL UNIQUE, creation_date TIMESTAMP NOT NULL)"])
                                   (f)
                                   (finally
                                     (reset! ds nil)))))))

(defn- invoke-async [query-fn & args]
  (let [latch  (CountDownLatch. 1)
        result (atom nil)
        error  (atom nil)]
    (apply query-fn (concat args
                            [(fn [v] (reset! result v) (.countDown latch))
                             (fn [e] (reset! error e) (.countDown latch))]))
    (.await latch 5 TimeUnit/SECONDS)
    {:result @result :error @error}))

(deftest verify-insert
  (let [set-value  (atom (list))
        error-value (atom (list))
        latch      (CountDownLatch. 5)
        adapter    (->NextJdbcAdapter executor)
        query-fn   (boa/build-async-query adapter "pg/insert")]
    (doseq [input [{:id "id1"} {:id "id2"} {:id "id3"} {:id "id4"} (Id. "id5")]]
      (query-fn @ds input
                (fn [value]
                  (swap! set-value conj value)
                  (.countDown latch))
                (fn [error]
                  (swap! error-value conj error)
                  (.countDown latch))))
    (.await latch 10 TimeUnit/SECONDS)
    (is (= 5 (count @set-value)))
    (is (empty? @error-value))
    (is (= [{:id "id1"} {:id "id2"} {:id "id3"} {:id "id4"} {:id "id5"}]
           (verify-users @ds)))))

(deftest verify-query-with-params
  (let [adapter  (->NextJdbcAdapter executor)]
    (jdbc/execute! @ds ["INSERT INTO users (id) VALUES ('test-id')"])
    (let [latch    (CountDownLatch. 1)
          result   (atom nil)
          query-fn (boa/build-async-query adapter "pg/find-user")]
      (query-fn @ds {:id "test-id"}
                (fn [value]
                  (reset! result value)
                  (.countDown latch))
                (fn [_]
                  (.countDown latch)))
      (.await latch 5 TimeUnit/SECONDS)
      (is (= [{:id "test-id"}] @result)))))

(deftest insert-tuple
  (let [adapter   (->NextJdbcAdapter executor)
        query-fn  (boa/build-async-query adapter "pg/multi-insert")
        select-fn (boa/build-async-query adapter "pg/select-customer")]
    (doseq [input [{:customer ["username" "email" "name"]}
                   {:customer ["username2" "email2" "name2"]}
                   {:customer ["username3" "email3" "name3"]}
                   {:customer ["usernam4" "email4" "name4"]}]]
      (let [{:keys [error]} (invoke-async query-fn @ds input)]
        (is (nil? error))))

    (is (= [{:email "email" :id 1 :name "name" :username "username"}
            {:email "email2" :id 2 :name "name2" :username "username2"}
            {:email "email3" :id 3 :name "name3" :username "username3"}
            {:email "email4" :id 4 :name "name4" :username "usernam4"}]
           (verify-customers @ds)))

    (let [{:keys [result error]} (invoke-async select-fn @ds {:email "email4"})]
      (is (nil? error))
      (is (= [{:email "email4" :id 4 :name "name4" :username "usernam4"}] result)))))

(deftest insert-multiple-tuples
  (let [adapter   (->NextJdbcAdapter executor)
        query-fn  (boa/build-async-query adapter "pg/multi-insert")
        select-fn (boa/build-async-query adapter "pg/select-customer")]
    (let [{:keys [error]} (invoke-async query-fn @ds {:customer [["username" "email" "name"]
                                                                   ["username2" "email2" "name2"]
                                                                   ["username3" "email3" "name3"]
                                                                   ["usernam4" "email4" "name4"]]})]
      (is (nil? error)))

    (is (= [{:email "email" :id 1 :name "name" :username "username"}
            {:email "email2" :id 2 :name "name2" :username "username2"}
            {:email "email3" :id 3 :name "name3" :username "username3"}
            {:email "email4" :id 4 :name "name4" :username "usernam4"}]
           (verify-customers @ds)))

    (let [{:keys [result error]} (invoke-async select-fn @ds {:email "email4"})]
      (is (nil? error))
      (is (= [{:email "email4" :id 4 :name "name4" :username "usernam4"}] result)))))

(deftest select-user-session
  (let [adapter   (->NextJdbcAdapter executor)
        insert-fn (boa/build-async-query adapter "pg/insert-user-session")
        select-fn (boa/build-async-query adapter "pg/select-user-session")]

    (invoke-async insert-fn @ds {:username "john_doe" :session-id "sess123" :creation-date "2025-10-13 14:30:00"})
    (invoke-async insert-fn @ds {:username "jane_smith" :session-id "sess456" :creation-date "2025-10-13 15:45:00"})

    (let [{:keys [result error]} (invoke-async select-fn @ds {:session "sess123"})]
      (is (nil? error))
      (is (= [{:creation-date "2025-10-13 14:30:00" :session-id "sess123" :username "john_doe"}] result)))

    (let [{:keys [result error]} (invoke-async select-fn @ds {:session "sess456"})]
      (is (nil? error))
      (is (= [{:creation-date "2025-10-13 15:45:00" :session-id "sess456" :username "jane_smith"}] result)))

    (let [{:keys [result error]} (invoke-async select-fn @ds {:session "not-existing"})]
      (is (nil? error))
      (is (= [] result)))))

(deftest verify-cast-in-pg
  (let [adapter  (->NextJdbcAdapter executor)
        query-fn (boa/build-async-query adapter "pg/insert-with-cast")]
    (doseq [input [{:id 1 :numeric-id "2"}
                   {:id 2 :numeric-id "3"}
                   {:id 3 :numeric-id "4"}
                   {:id 4 :numeric-id "5"}
                   (NumericId. 5 "6")]]
      (let [{:keys [error]} (invoke-async query-fn @ds input)]
        (is (nil? error))))

    (is (= [{:id 1 :numeric_id 2}
            {:id 2 :numeric_id 3}
            {:id 3 :numeric_id 4}
            {:id 4 :numeric_id 5}
            {:id 5 :numeric_id 6}]
           (jdbc/execute! @ds ["SELECT * FROM users_with_cast"]
                          {:builder-fn rs/as-unqualified-lower-maps})))))
