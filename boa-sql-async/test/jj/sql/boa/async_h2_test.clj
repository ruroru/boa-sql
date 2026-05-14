(ns jj.sql.boa.async-h2-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [jj.sql.async-boa :as boa]
            [jj.sql.boa.query.next-jdbc-async :refer [->NextJdbcAdapter]]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs])
  (:import (java.nio.file Files Paths)
           (java.util.concurrent CountDownLatch Executors TimeUnit)))

(defn- delete-db [db-path]
  (Files/deleteIfExists (Paths/get (str db-path ".mv.db") (make-array String 0)))
  (Files/deleteIfExists (Paths/get (str db-path ".trace.db") (make-array String 0))))

(def file "async-file")
(def ds (jdbc/get-datasource {:dbtype "h2" :dbname file}))
(def ^:private executor (Executors/newFixedThreadPool 10))

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

(defn- verify-users [ds]
  (jdbc/execute! ds ["SELECT * FROM users"] {:builder-fn rs/as-unqualified-lower-maps}))

(defn- verify-customers [ds]
  (jdbc/execute! ds ["SELECT * FROM customers"] {:builder-fn rs/as-unqualified-lower-maps}))

(defn- invoke-async [query-fn & args]
  (let [latch  (CountDownLatch. 1)
        result (atom nil)
        error  (atom nil)]
    (apply query-fn (concat args
                            [(fn [v] (reset! result v) (.countDown latch))
                             (fn [e] (reset! error e) (.countDown latch))]))
    (.await latch 5 TimeUnit/SECONDS)
    {:result @result :error @error}))

(defrecord Id [id])

(deftest verify-insert
  (let [adapter  (->NextJdbcAdapter executor)
        query-fn (boa/build-async-query adapter "h2/insert")]
    (doseq [input [{:id "id1"} {:id "id2"} {:id "id3"} {:id "id4"} (Id. "id5")]]
      (let [{:keys [error]} (invoke-async query-fn ds input)]
        (is (nil? error))))
    (is (= [{:id "id1"} {:id "id2"} {:id "id3"} {:id "id4"} {:id "id5"}]
           (verify-users ds)))))

(deftest insert-tuple
  (let [adapter   (->NextJdbcAdapter executor)
        query-fn  (boa/build-async-query adapter "h2/multi-insert")
        select-fn (boa/build-async-query adapter "h2/select-customer")]
    (doseq [input [{:customer ["username" "email" "name"]}
                   {:customer ["username2" "email2" "name2"]}
                   {:customer ["username3" "email3" "name3"]}
                   {:customer ["usernam4" "email4" "name4"]}]]
      (let [{:keys [error]} (invoke-async query-fn ds input)]
        (is (nil? error))))

    (is (= [{:email "email" :id 1 :name "name" :username "username"}
            {:email "email2" :id 2 :name "name2" :username "username2"}
            {:email "email3" :id 3 :name "name3" :username "username3"}
            {:email "email4" :id 4 :name "name4" :username "usernam4"}]
           (verify-customers ds)))

    (let [{:keys [result error]} (invoke-async select-fn ds {:email "email4"})]
      (is (nil? error))
      (is (= [{:email "email4" :id 4 :name "name4" :username "usernam4"}] result)))))

(defrecord Customer [user-name email name])

(deftest insert-multiple-tuples
  (let [adapter   (->NextJdbcAdapter executor)
        query-fn  (boa/build-async-query adapter "h2/multi-insert")
        select-fn (boa/build-async-query adapter "h2/select-customer")]
    (let [{:keys [error]} (invoke-async query-fn ds {:customer [["username" "email" "name"]
                                                                 ["username2" "email2" "name2"]
                                                                 ["username3" "email3" "name3"]
                                                                 ["usernam4" "email4" "name4"]]})]
      (is (nil? error)))

    (is (= [{:email "email" :id 1 :name "name" :username "username"}
            {:email "email2" :id 2 :name "name2" :username "username2"}
            {:email "email3" :id 3 :name "name3" :username "username3"}
            {:email "email4" :id 4 :name "name4" :username "usernam4"}]
           (verify-customers ds)))

    (let [{:keys [result error]} (invoke-async select-fn ds {:email "email4"})]
      (is (nil? error))
      (is (= [{:email "email4" :id 4 :name "name4" :username "usernam4"}] result)))))

(deftest select-user-session
  (let [adapter   (->NextJdbcAdapter executor)
        insert-fn (boa/build-async-query adapter "h2/insert-user-session")
        select-fn (boa/build-async-query adapter "h2/select-user-session")]

    (invoke-async insert-fn ds {:username "john_doe" :session-id "sess123" :creation-date "2025-10-13 14:30:00"})
    (invoke-async insert-fn ds {:username "jane_smith" :session-id "sess456" :creation-date "2025-10-13 15:45:00"})

    (let [{:keys [result error]} (invoke-async select-fn ds {:session "sess123"})]
      (is (nil? error))
      (is (= [{:creation-date "2025-10-13 14:30:00" :session-id "sess123" :username "john_doe"}] result)))

    (let [{:keys [result error]} (invoke-async select-fn ds {:session "sess456"})]
      (is (nil? error))
      (is (= [{:creation-date "2025-10-13 15:45:00" :session-id "sess456" :username "jane_smith"}] result)))

    (let [{:keys [result error]} (invoke-async select-fn ds {:session "not-existing"})]
      (is (nil? error))
      (is (= [] result)))))
