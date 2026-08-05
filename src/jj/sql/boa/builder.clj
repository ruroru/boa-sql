(ns jj.sql.boa.builder
  (:require [clojure.tools.logging :as logger]
            [jj.sql.boa.parser :as parser]))

(defn param-names
  [tokens]
  (mapv second (filterv (fn [[type _]] (= type :variable)) tokens)))

(defn- log-sql
  [sql]
  (when (logger/enabled? :debug)
    (logger/debug "Query is: " sql))
  sql)

(defn- log-and-expand
  [sql params]
  (when (logger/enabled? :debug)
    (logger/debug "Query is: " sql))
  [sql params])

(defn- build-variadic-fn
  [tokens]
  (let [var-names (param-names tokens)
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
            (log-and-expand sql params))
          (log-sql sql))))))

(defn build-query
  [tokens]
  (let [var-count (count (filter (fn [[type _]] (= type :variable)) tokens))
        conditional? (some #(= (first %) :if) tokens)]

    (if conditional?
        (fn [arg]
          (let [{:keys [sql params]} (parser/parse (or arg {}) tokens)]
            (log-and-expand sql params)))

        (cond
          (zero? var-count)
          (let [{:keys [sql]} (parser/parse {} tokens)]
            (fn [_]
              (log-sql sql)))

          (= 1 var-count)
          (let [var-name (second (first (filter (fn [[type _]] (= type :variable)) tokens)))
                {:keys [sql]} (parser/parse {var-name ::single-placeholder} tokens)]
            (fn [arg]
              (if (vector? (get arg var-name))
                (let [{:keys [sql params]} (parser/parse arg tokens)]
                  (log-and-expand sql params))
                (log-sql sql))))

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
                  (log-and-expand sql params))
                (log-sql sql))))

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
                  (log-and-expand sql params))
                (log-sql sql))))

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
                  (log-and-expand sql params))
                (log-sql sql))))

          :else
          (build-variadic-fn tokens)))))
