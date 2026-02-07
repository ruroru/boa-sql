(ns jj.sql.boa.e2e.db-test
  (:require [clojure.test :refer :all]
            [clojure.tools.logging :as logger]
            [hato.client :as http]
            [jj.sql.boa :as boa]
            [jj.sql.boa.query.next-jdbc :refer [->NextJdbcAdapter]]
            [next.jdbc :as jdbc]
            [ring-http-exchange.core :as server])
  (:import (java.nio.file Files)
           (java.io File)
           (java.util.concurrent Executors)))

(def executor (Executors/newFixedThreadPool 128))
(def query (boa/build-query (->NextJdbcAdapter) "e2e/select-users.sql"))
(def async-query (boa/build-async-query executor (->NextJdbcAdapter) "e2e/select-users.sql"))

(def db-spec {:dbtype "sqlite" :dbname "./target/test.db"})

(defn get-users-handler [request]
  (let [users (query db-spec)]
    {:status  200
     :headers {"Content-Type" "application/json"}
     :body    (str users)}))

(defn async-get-users-handler [request respond raise]
  (async-query db-spec {} (fn [results]
                            (println results)
                            (respond {:status  200
                                      :headers {"Content-Type" "application/json"}
                                      :body    (str results)})
                            )
               (fn [results]
                 (raise {:status  500
                         :headers {"Content-Type" "application/json"}
                         :body    (str results)}))))

(defn app [request]
  (if (= (:uri request) "/users")
    (get-users-handler request)
    {:status 404 :body "Not found"}))

(defn async-app [request respond raise]
  (if (= (:uri request) "/users")
    (async-get-users-handler request respond raise)
    {:status 404 :body "Not found"}))

(deftest test-http-select-and-return
  (.mkdirs (File. "./target"))
  (Files/deleteIfExists (.toPath (File. "./target/test.db")))

  (testing "Select data from SQLite and return to HTTP client using Hato"
    (jdbc/execute! db-spec ["CREATE TABLE users (id INTEGER PRIMARY KEY, name TEXT)"])
    (jdbc/execute! db-spec ["INSERT INTO users (name) VALUES ('Alice')"])
    (jdbc/execute! db-spec ["INSERT INTO users (name) VALUES ('Bob')"])

    (let [server (server/run-http-server app {:port 3000 :join? false})]
      (try
        (Thread/sleep 500)

        (let [response (http/get "http://localhost:3000/users")]
          (is (= 200 (:status response)))
          (is (= "application/json" (get-in response [:headers "content-type"])))
          (is (string? (:body response)))
          (is (re-find #"Alice" (:body response)))
          (is (re-find #"Bob" (:body response))))

        (finally
          (server/stop-http-server server)
          (Files/deleteIfExists (.toPath (File. "./target/test.db"))))))))

(deftest test-http-async-select-and-return
  (.mkdirs (File. "./target"))
  (Files/deleteIfExists (.toPath (File. "./target/test.db")))

  (testing "Select data from SQLite asynchronously and return to HTTP client using Hato"
    (jdbc/execute! db-spec ["CREATE TABLE users (id INTEGER PRIMARY KEY, name TEXT)"])
    (jdbc/execute! db-spec ["INSERT INTO users (name) VALUES ('Alice')"])
    (jdbc/execute! db-spec ["INSERT INTO users (name) VALUES ('Bob')"])

    (let [async-app (fn [request respond raise]
                      (when (= (:uri request) "/users")
                        (async-get-users-handler request respond raise)))
          server (server/run-http-server async-app {:async? true
                                                    :port   3001})]

      (try
        (Thread/sleep 500)

        (let [response (http/get "http://localhost:3001/users")]
          (is (= 200 (:status response)))
          (is (= "application/json" (get-in response [:headers "content-type"])))
          (is (string? (:body response)))
          (logger/error (:body response))
          (is (re-find #"Alice" (:body response)))
          (is (re-find #"Bob" (:body response))))

        (finally
          (server/stop-http-server server)
          (Files/deleteIfExists (.toPath (File. "./target/test.db"))))))))
