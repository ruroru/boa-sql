(ns jj.sql.boa
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.tools.logging :as logger]
            [jj.sql.boa.parser :as parser]
            [jj.sql.boa.query :as boa-query]
            [jj.sql.boa.async-query :as async-boa-query]
            )
  (:import (java.util.function Function)))

(def ^:private ^:const comma ",")
(def ^:private ^:const question-mark "?")
(def ^:private ^:const op-paren "(")
(def ^:private ^:const cl-paren ")")

(deftype ErrorHandler [raise]
  Function
  (apply [this throwable]
    (raise {:status  500
            :headers {}
            :body    "Internal server error|"})))

(defn- build-prepared-statement [context parsed-tokens sb parameters]
  (if-let [[token-type token-value] (first parsed-tokens)]
    (let [remaining (rest parsed-tokens)]
      (case token-type
        :text
        (do
          (str sb token-value)
          (recur context remaining (str sb token-value) parameters))

        :variable
        (let [value (get context token-value)]
          (if (coll? value)
            (if (coll? (first value))
              (let [number-of-items-per-tuple (count (first value))
                    number-of-tuples (count value)
                    placeholders (apply str
                                        (interpose comma
                                                   (repeat number-of-tuples
                                                           (str op-paren
                                                                (apply str (interpose comma (repeat number-of-items-per-tuple question-mark)))
                                                                cl-paren))))]
                (recur context remaining (str sb placeholders) (into parameters (flatten value))))
              (let [placeholders (str op-paren
                                      (apply str (interpose comma (repeat (count value) question-mark)))
                                      cl-paren)]
                (recur context remaining (str sb placeholders) (into parameters value))))
            (recur context remaining (str sb question-mark) (conj parameters (get context token-value)))))
        (recur context remaining sb parameters)))
    {:sql sb :params parameters}))




(defn build-async-query [adapter query-file]
  (let [resource (or (io/resource query-file)
                     (throw (ex-info "Query not found" {:file query-file})))
        tokens (parser/tokenize (str/trim (slurp resource)))]
    (fn
      ([ds context respond reject]
       (let [{:keys [sql params]} (build-prepared-statement context tokens "" [])]
         (when (logger/enabled? :debug)
           (logger/debug "Query is: " sql))
         (async-boa-query/query adapter ds sql params respond reject))))))
