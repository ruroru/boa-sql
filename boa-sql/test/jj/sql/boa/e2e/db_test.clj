(ns jj.sql.boa.e2e.db-test
  (:require [clojure.test :refer :all]
            [hato.client :as http]
            [jj.sql.boa :as boa]
            [jj.sql.boa.query.next-jdbc :refer [->NextJdbcAdapter]]
            [next.jdbc :as jdbc]
            [ring-http-exchange.core :as server])
  (:import (java.io File)
           (java.nio.file Files)))

(def query (boa/build-query (->NextJdbcAdapter) "e2e/select-users.sql"))

(def db-spec {:dbtype "sqlite" :dbname "./target/test.db"})

(defn get-users-handler [request]
  (let [users (query db-spec)]
    {:status  200
     :headers {"Content-Type" "application/json"}
     :body    (str users)}))

(defn app [request]
  (if (= (:uri request) "/users")
    (get-users-handler request)
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


