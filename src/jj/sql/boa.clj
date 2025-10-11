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
  (execute-one [this query-file])
  (execute [this query-file]))

(defrecord NextJdbcAdapter []
  BoaQuery
  (execute-one [this query-file]
    (let [resource (io/resource query-file)]
      (when-not resource
        (throw (ex-info (str "Query resource not found: " query-file)
                        {:resource query-file})))
      (let [file-content (str/trim (slurp resource))
            parsed (parser/tokenize file-content)]
        (fn ([data-source]
             (let [sb (StringBuilder.)
                   statement-params (build-prepared-statement {} parsed sb [])]
               (when (logger/enabled? :debug)
                 (logger/debugf "Running query \n'%s'\nwith params: %s" (.toString sb) statement-params))
               (jdbc/execute! data-source
                              (into [(.toString sb)] statement-params)
                              {:builder-fn rs/as-unqualified-lower-maps})))
          ([data-source context]
           (let [sb (StringBuilder.)
                 statement-params (build-prepared-statement context parsed sb [])]
             (when (logger/enabled? :debug)
               (logger/debugf "Running query \n'%s'\nwith params: %s" (.toString sb) statement-params))
             (first (jdbc/execute! data-source
                                   (into [(.toString sb)] statement-params)
                                   {:builder-fn rs/as-unqualified-lower-maps}))))))))
  (execute [this query-file]
    (let [resource (io/resource query-file)]
      (when-not resource
        (throw (ex-info (str "Query resource not found: " query-file)
                        {:resource query-file})))
      (let [file-content (str/trim (slurp resource))
            parsed (parser/tokenize file-content)]
        (fn
          ([data-source]
           (let [sb (StringBuilder.)
                 statement-params (build-prepared-statement {} parsed sb [])]
             (when (logger/enabled? :debug)
               (logger/debugf "Running query \n'%s'\nwith params: %s" (.toString sb) statement-params))
             (jdbc/execute! data-source
                            (into [(.toString sb)] statement-params)
                            {:builder-fn rs/as-unqualified-lower-maps})))
          ([data-source context]
           (let [sb (StringBuilder.)
                 statement-params (build-prepared-statement context parsed sb [])]
             (when (logger/enabled? :debug)
               (logger/debugf "Running query \n'%s'\nwith params: %s" (.toString sb) statement-params))
             (jdbc/execute! data-source
                            (into [(.toString sb)] statement-params)
                            {:builder-fn rs/as-unqualified-lower-maps}))))))))



