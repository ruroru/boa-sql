(ns jj.sql.boa.sequential-strategy-test
  (:require [clojure.test :refer [deftest is]]
            [jj.sql.boa.parser :as parser]
            [jj.sql.boa.protocol.query-builder :as query-builder]
            [jj.sql.boa.strategy.sequential :refer [->SequentialStrategy]]))

(deftest parameterless-query-generates-plain-sql
  (let [tokens (parser/tokenize "SELECT * FROM users")
        transform-fn (query-builder/build-query (->SequentialStrategy) tokens)]
    (is (= {:sql "SELECT * FROM users" :params nil}
           (transform-fn nil)))))

(deftest single-variable-generates-dollar-1
  (let [tokens (parser/tokenize "SELECT * FROM users WHERE id = :id")
        transform-fn (query-builder/build-query (->SequentialStrategy) tokens)]
    (is (= {:sql "SELECT * FROM users WHERE id = $1" :params ["user-1"]}
           (transform-fn ["user-1"])))))

(deftest multiple-variables-generate-sequential-placeholders
  (let [tokens (parser/tokenize "INSERT INTO users (name, email, age) VALUES (:name, :email, :age)")
        transform-fn (query-builder/build-query (->SequentialStrategy) tokens)]
    (is (= {:sql "INSERT INTO users (name, email, age) VALUES ($1, $2, $3)"
            :params ["John" "john@test.com" 30]}
           (transform-fn ["John" "john@test.com" 30])))))

(deftest two-variables-generate-dollar-1-and-2
  (let [tokens (parser/tokenize "SELECT * FROM users WHERE name = :name AND age = :age")
        transform-fn (query-builder/build-query (->SequentialStrategy) tokens)]
    (is (= {:sql "SELECT * FROM users WHERE name = $1 AND age = $2"
            :params ["John" 20]}
           (transform-fn ["John" 20])))))

(deftest comments-are-stripped-from-sql
  (let [tokens (parser/tokenize "-- find user by id\nSELECT * FROM users WHERE id = :id")
        transform-fn (query-builder/build-query (->SequentialStrategy) tokens)]
    (is (= {:sql "SELECT * FROM users WHERE id = $1" :params [42]}
           (transform-fn [42])))))
