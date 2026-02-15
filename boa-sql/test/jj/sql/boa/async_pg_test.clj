(ns jj.sql.boa.async-pg-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [clojure.tools.logging :as logger]
            [jj.sql.boa :as boa]
            [jj.sql.boa.query.next-jdbc :refer [->VertxPgAdapter ]]
            [next.jdbc :as jdbc]
            [pg-embedded-clj.core :as pg])
  (:import [io.vertx.pgclient PgBuilder PgConnectOptions]
           [io.vertx.sqlclient PoolOptions Row Tuple]
           [io.vertx.core Vertx Handler]
           (java.util Arrays)
           [java.util.concurrent CountDownLatch TimeUnit]))

(defonce ds (atom nil))
(def ^:const test-port 54321)

(defn- row->map
  "Convert a Vert.x Row to a Clojure map"
  [^Row row]
  (let [column-names (.columnsNames row)]
    (reduce (fn [m col]
              (assoc m (keyword col) (.getValue row col)))
            {}
            column-names)))

(defn- rows->maps
  "Convert Vert.x RowSet to a sequence of maps"
  [rows]
  (mapv row->map rows))

(defn create-test-pool []
  (let [connect-opts (doto (PgConnectOptions.)
                       (.setHost "localhost")
                       (.setPort test-port)
                       (.setDatabase "test_db")
                       (.setUser "postgres")
                       (.setPassword "postgres"))
        pool-opts (doto (PoolOptions.)
                    (.setMaxSize 10))]
    (-> (PgBuilder/pool)
        (.connectingTo connect-opts)
        (.with pool-opts)
        (.using (Vertx/vertx))
        (.build))))

(defn execute-sync
  "Execute an async query synchronously - for test setup/teardown"
  [pool sql]
  (let [latch (CountDownLatch. 1)
        error (atom nil)]
    (-> pool
        (.query sql)
        (.execute (reify Handler
                    (handle [_ ar]
                      (when-not (.succeeded ar)
                        (reset! error (.cause ar)))
                      (.countDown latch)))))
    (.await latch 5 TimeUnit/SECONDS)
    (when @error (throw @error))))

(defn- prepare-sql [sql]
  (let [counter (atom 0)]
    (clojure.string/replace sql #"\?" (fn [_] (str "$" (swap! counter inc))))))

(defn query-sync
  "Execute a parameterized query synchronously - for verification"
  [pool sql params]
  (let [latch (CountDownLatch. 1)
        result (atom nil)
        error (atom nil)
        ;; Use Tuple/from with the params vector directly
        tuple (Tuple/from params)]
    (-> pool
        (.preparedQuery sql)
        (.execute tuple)
        (.onComplete
          (reify Handler
            (handle [_ ar]
              (if (.succeeded ar)
                (reset! result
                        (mapv (fn [row]
                                (let [cols (.columnNames row)]
                                  (reduce (fn [m col]
                                            (assoc m (keyword col) (.getValue row col)))
                                          {}
                                          cols)))
                              (.result ar)))
                (reset! error (.cause ar)))
              (.countDown latch)))))
    (.await latch 5 TimeUnit/SECONDS)
    (when @error
      (throw @error))
    @result))

(use-fixtures :each
              (fn [f]
                (logger/info "Creating database test_db")
                (pg/with-pg-fn {:port test-port}
                               (fn []
                                 (jdbc/execute! {:dbtype "postgresql" :host "localhost" :port test-port :user "postgres" :password "postgres"}
                                                ["CREATE DATABASE test_db"])
                                 (reset! ds (create-test-pool))
                                 (try
                                   (execute-sync @ds "CREATE TABLE IF NOT EXISTS users (id TEXT PRIMARY KEY)")
                                   (f)
                                   (finally
                                     (when @ds (.close @ds))))))))

(defn- verify-exists [pool]
  (query-sync pool "SELECT * FROM users ORDER BY id" []))

(defrecord Id [id])

(deftest verify-insert
  (let [set-value (atom (list))
        error-value (atom (list))
        latch (CountDownLatch. 5)
        adapter (->VertxPgAdapter)
        query-fn (boa/build-async-query adapter "pg/insert")]

    (doseq [input [{:id "id1"}
                   {:id "id2"}
                   {:id "id3"}
                   {:id "id4"}
                   (Id. "id5")]]
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
  (let [adapter (->VertxPgAdapter)]
    (execute-sync @ds "INSERT INTO users (id) VALUES ('test-id')")
    (let [latch (CountDownLatch. 1)
          result (atom nil)
          query-fn (boa/build-async-query adapter "pg/find-user")]
      (query-fn @ds {:id "test-id"}
                (fn [value]
                  (reset! result value)
                  (.countDown latch))
                (fn [_]
                  (.countDown latch)))
      (.await latch 5 TimeUnit/SECONDS)
      (is (= [{:id "test-id"}] @result)))))