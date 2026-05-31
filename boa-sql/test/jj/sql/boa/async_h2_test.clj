(ns jj.sql.boa.async-h2-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [jj.sql.boa :as boa]
            [jj.sql.boa.query.next-jdbc-async :refer [->NextJdbcAsyncAdapter]]
            [next.jdbc :as jdbc])
  (:import (java.nio.file Files Paths)
           (java.util.concurrent CompletableFuture)))

(defn delete-db
  [db-path]
  (Files/deleteIfExists (Paths/get (str db-path ".mv.db") (make-array String 0)))
  (Files/deleteIfExists (Paths/get (str db-path ".trace.db") (make-array String 0))))

(def file "file-async")
(def ds (jdbc/get-datasource {:dbtype "h2" :dbname file}))
(use-fixtures :each
              (fn [f]
                (delete-db file)
                (jdbc/execute! ds ["DROP TABLE IF EXISTS users"])
                (jdbc/execute! ds ["DROP TABLE IF EXISTS usersessions"])
                (jdbc/execute! ds ["CREATE TABLE IF NOT EXISTS users (id VARCHAR(255) PRIMARY KEY)"])
                (jdbc/execute! ds ["CREATE TABLE IF NOT EXISTS usersessions (
    id INTEGER PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(255) NOT NULL,
    session_id VARCHAR(255) NOT NULL UNIQUE,
    creation_date VARCHAR(255) NOT NULL)"])
                (f)))

(deftest returns-completable-future
  (let [adapter  (->NextJdbcAsyncAdapter)
        query-fn (boa/build-query adapter "h2/insert")
        result   (query-fn ds {:id "id1"})]
    (is (instance? CompletableFuture result))
    (is (= [#:next.jdbc{:update-count 1}] (.get ^CompletableFuture result)))))

(deftest insert-and-select
  (let [adapter   (->NextJdbcAsyncAdapter)
        insert-fn (boa/build-query adapter "h2/insert")
        select-fn (boa/build-query adapter "h2/select-user-session")
        insert-session-fn (boa/build-query adapter "h2/insert-user-session")]
    (.get ^CompletableFuture (insert-fn ds {:id "id1"}))
    (.get ^CompletableFuture (insert-fn ds {:id "id2"}))

    (.get ^CompletableFuture
      (insert-session-fn ds {:username      "john_doe"
                             :session-id    "sess123"
                             :creation-date "2025-10-13 14:30:00"}))

    (let [result (.get ^CompletableFuture (select-fn ds {:session "sess123"}))]
      (is (= [{:creation-date "2025-10-13 14:30:00"
               :session-id    "sess123"
               :username      "john_doe"}]
             result)))))

(deftest multiple-async-inserts
  (let [adapter   (->NextJdbcAsyncAdapter)
        insert-fn (boa/build-query adapter "h2/insert")]
    (let [futures (mapv (fn [id] (insert-fn ds {:id id})) ["a1" "a2" "a3"])]
      (doseq [^CompletableFuture f futures]
        (is (= [#:next.jdbc{:update-count 1}] (.get f)))))
    (let [rows (jdbc/execute! ds ["SELECT * FROM users ORDER BY id"])]
      (is (= 3 (count rows))))))
