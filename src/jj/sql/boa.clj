(ns jj.sql.boa
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.tools.logging :as logger]
            [jj.sql.boa.parser :as parser]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]))

(def ^:private ^:const comma ",")
(def ^:private ^:const question-mark "?")
(def ^:private ^:const op-paren "(")
(def ^:private ^:const cl-paren ")")

(defn build-prepared-statement [context parsed-tokens ^StringBuilder sb parameters]
  (if-let [[token-type token-value] (first parsed-tokens)]
    (let [remaining (rest parsed-tokens)]
      (case token-type
        :text
        (do
          (.append sb token-value)
          (recur context remaining sb parameters))

        :variable
        (let [value (get context token-value)]
          (if (coll? value)
            (if (coll? (first value))
              (do
                (let [number-of-items-per-tuple (count (first value))
                      number-of-tuples (count value)]
                  (dotimes [tuple-idx number-of-tuples]
                    (when (pos? tuple-idx)
                      (.append sb comma))
                    (.append sb op-paren)
                    (dotimes [i number-of-items-per-tuple]
                      (when (pos? i)
                        (.append sb comma))
                      (.append sb question-mark))
                    (.append sb cl-paren))
                  (recur context remaining sb (into parameters (flatten value)))))
              (do
                (.append sb op-paren)
                (let [elements-in-value (count value)]
                  (dotimes [i elements-in-value]
                    (when (pos? i)
                      (.append sb comma))
                    (.append sb question-mark)))
                (.append sb cl-paren)
                (recur context remaining sb (into parameters value))))
            (do
              (.append sb question-mark)
              (recur context remaining sb (conj parameters (get context token-value))))))
        (recur context remaining sb parameters)))
    parameters))

(defprotocol BoaQuery
  (build-query [this query-file]))

(defrecord NextJdbcAdapter []
  BoaQuery
  (build-query [this query-file]
    (let [resource (or (io/resource query-file)
                       (throw (ex-info "Query not found" {:file query-file})))
          tokens (parser/tokenize (str/trim (slurp resource)))
          var-count (count (filter (fn [[type _]] (= type :variable)) tokens))]

      (cond
        (zero? var-count)
        (let [static-sb (StringBuilder.)
              static-params (build-prepared-statement {} tokens static-sb [])
              static-query (vec (cons (.toString static-sb) static-params))]
          (fn
            ([ds] (jdbc/execute! ds static-query {:builder-fn rs/as-unqualified-lower-maps}))
            ([ds _] (jdbc/execute! ds static-query {:builder-fn rs/as-unqualified-lower-maps}))))

        :else
        (fn
          ([ds context]
           (let [sb (StringBuilder.)
                 params (build-prepared-statement context tokens sb [])]
             (when (logger/enabled? :debug)
               (logger/debugf "Running multi-variable query: %s with params: %s" query-file params))
             (jdbc/execute! ds (into [(.toString sb)] params)
                            {:builder-fn rs/as-unqualified-lower-maps}))))))))
