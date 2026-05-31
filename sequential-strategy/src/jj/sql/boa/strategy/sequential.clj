(ns jj.sql.boa.strategy.sequential
  (:require [clojure.tools.logging :as logger]
            [jj.sql.boa.protocol.query-builder :as query-builder]))

(defn- build-sql [tokens]
  (loop [remaining tokens
         sb ""
         idx 1]
    (if-let [[token-type token-value] (first remaining)]
      (case token-type
        :text (recur (rest remaining) (str sb token-value) idx)
        :variable (recur (rest remaining) (str sb "$" idx) (inc idx))
        (recur (rest remaining) sb idx))
      sb)))

(defrecord SequentialStrategy []
  query-builder/QueryBuilder
  (build-query [_ tokens]
    (let [sql (build-sql tokens)
          var-count (count (filter (fn [[type _]] (= type :variable)) tokens))]
      (if (zero? var-count)
        (fn [_]
          (when (logger/enabled? :debug)
            (logger/debug "Query is: " sql))
          {:sql sql :params nil})
        (fn [params]
          (when (logger/enabled? :debug)
            (logger/debug "Query is: " sql))
          {:sql sql :params params})))))
