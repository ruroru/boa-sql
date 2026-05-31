(ns jj.sql.boa.strategy.jdbc
  (:require [clojure.tools.logging :as logger]
            [jj.sql.boa.parser :as parser]
            [jj.sql.boa.protocol.query-builder :as query-builder]))

(defn- build-variadic-fn
  [tokens]
  (let [var-names (mapv second (filterv (fn [[t _]] (= t :variable)) tokens))
        placeholder-ctx (zipmap var-names (repeat ::single-placeholder))
        {:keys [sql]} (parser/parse placeholder-ctx tokens)
        n (count var-names)]
    (fn [arg]
      (let [has-vec? (loop [i 0]
                       (if (< i n)
                         (if (vector? (get arg (nth var-names i)))
                           true
                           (recur (inc i)))
                         false))]
        (if has-vec?
          (let [{:keys [sql params]} (parser/parse arg tokens)]
            (when (logger/enabled? :debug)
              (logger/debug "Query is: " sql))
            {:sql sql :params params})
          (let [params (loop [i 0
                              acc (transient [])]
                         (if (< i n)
                           (recur (inc i) (conj! acc (get arg (nth var-names i))))
                           (persistent! acc)))]
            (when (logger/enabled? :debug)
              (logger/debug "Query is: " sql))
            {:sql sql :params params}))))))

(defn- log-and-return [sql params]
  (when (logger/enabled? :debug)
    (logger/debug "Query is: " sql))
  {:sql sql :params params})

(defrecord JDBCStrategy []
  query-builder/QueryBuilder
  (build-query [_ tokens]
    (let [var-count (count (filter (fn [[type _]] (= type :variable)) tokens))
          conditional? (some #(= (first %) :if) tokens)]

      (if conditional?
        (fn [arg]
          (let [{:keys [sql params]} (parser/parse (or arg {}) tokens)]
            (log-and-return sql (when (seq params) params))))

        (cond
          (zero? var-count)
          (let [{:keys [sql]} (parser/parse {} tokens)]
            (fn [_]
              (log-and-return sql nil)))

          (= 1 var-count)
          (let [var-name (second (first (filter (fn [[type _]] (= type :variable)) tokens)))
                {:keys [sql]} (parser/parse {var-name ::single-placeholder} tokens)]
            (fn [arg]
              (if (vector? (get arg var-name))
                (let [{:keys [sql params]} (parser/parse arg tokens)]
                  (log-and-return sql params))
                (log-and-return sql [(get arg var-name)]))))

          (= 2 var-count)
          (let [vars (filterv (fn [[type _]] (= type :variable)) tokens)
                var-name-1 (second (nth vars 0))
                var-name-2 (second (nth vars 1))
                {:keys [sql]} (parser/parse {var-name-1 ::single-placeholder
                                             var-name-2 ::single-placeholder} tokens)]
            (fn [arg]
              (if (or (vector? (get arg var-name-1))
                      (vector? (get arg var-name-2)))
                (let [{:keys [sql params]} (parser/parse arg tokens)]
                  (log-and-return sql params))
                (log-and-return sql [(get arg var-name-1) (get arg var-name-2)]))))

          (= 3 var-count)
          (let [vars (filterv (fn [[type _]] (= type :variable)) tokens)
                var-name-1 (second (nth vars 0))
                var-name-2 (second (nth vars 1))
                var-name-3 (second (nth vars 2))
                {:keys [sql]} (parser/parse {var-name-1 ::single-placeholder
                                             var-name-2 ::single-placeholder
                                             var-name-3 ::single-placeholder} tokens)]
            (fn [arg]
              (if (or (vector? (get arg var-name-1))
                      (vector? (get arg var-name-2))
                      (vector? (get arg var-name-3)))
                (let [{:keys [sql params]} (parser/parse arg tokens)]
                  (log-and-return sql params))
                (log-and-return sql [(get arg var-name-1)
                                     (get arg var-name-2)
                                     (get arg var-name-3)]))))

          (= 4 var-count)
          (let [vars (filterv (fn [[type _]] (= type :variable)) tokens)
                var-name-1 (second (nth vars 0))
                var-name-2 (second (nth vars 1))
                var-name-3 (second (nth vars 2))
                var-name-4 (second (nth vars 3))
                {:keys [sql]} (parser/parse {var-name-1 ::single-placeholder
                                             var-name-2 ::single-placeholder
                                             var-name-3 ::single-placeholder
                                             var-name-4 ::single-placeholder} tokens)]
            (fn [arg]
              (if (or (vector? (get arg var-name-1))
                      (vector? (get arg var-name-2))
                      (vector? (get arg var-name-3))
                      (vector? (get arg var-name-4)))
                (let [{:keys [sql params]} (parser/parse arg tokens)]
                  (log-and-return sql params))
                (log-and-return sql [(get arg var-name-1)
                                     (get arg var-name-2)
                                     (get arg var-name-3)
                                     (get arg var-name-4)]))))

          :else
          (build-variadic-fn tokens))))))
