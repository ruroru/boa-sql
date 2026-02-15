(ns jj.sql.boa.async-pg-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [clojure.tools.logging :as logger]
            [jj.sql.boa :as boa]
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

(defn- verify-exists [ds]
  (jdbc/execute! ds ["SELECT * FROM users ORDER BY id"]
                 {:builder-fn rs/as-unqualified-lower-maps}))

(defrecord Id [id])

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
                                   (f)
                                   (finally
                                     (reset! ds nil)))))))

(deftest verify-insert
  (let [set-value (atom (list))
        error-value (atom (list))
        latch    (CountDownLatch. 5)
        adapter  (->NextJdbcAdapter executor)
        query-fn (boa/build-async-query adapter "pg/insert")]
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
           (verify-exists @ds)))))

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