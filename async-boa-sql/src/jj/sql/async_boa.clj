(ns jj.sql.async-boa
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.tools.logging :as logger]
            [jj.sql.boa.async-query :as async-boa-query]
            [jj.sql.boa.parser :as parser]
            ))

(def ^:private ^:const comma ",")
(def ^:private ^:const question-mark "?")
(def ^:private ^:const op-paren "(")
(def ^:private ^:const cl-paren ")")

(defn- build-prepared-statement [context parsed-tokens sb parameters]
  (if-let [[token-type token-value] (first parsed-tokens)]
    (let [remaining (rest parsed-tokens)]
      (case token-type
        :text
        (recur context remaining (str sb token-value) parameters)

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
        tokens (parser/tokenize (str/trim (slurp resource)))
        var-count (count (filter (fn [[type _]] (= type :variable)) tokens))]
    (cond
      (zero? var-count)
      (let [{:keys [sql]} (build-prepared-statement {} tokens "" [])]
        (fn
          ([ds respond raise]
           (when (logger/enabled? :debug)
             (logger/debug "Query is: " sql))
           (async-boa-query/parameterless-query adapter ds sql respond raise))
          ([ds _ respond raise]
           (when (logger/enabled? :debug)
             (logger/debug "Query is: " sql))
           (async-boa-query/parameterless-query adapter ds sql respond raise))))

      (= 1 var-count)
      (let [var-name (second (first (filter (fn [[type _]] (= type :variable)) tokens)))
            {:keys [sql]} (build-prepared-statement {var-name ::single-placeholder} tokens "" [])
            single-arg-fn (fn [ds arg respond raise]
                            (when (logger/enabled? :debug)
                              (logger/debug "Query is: " sql))
                            (let [param-value (get arg var-name)]
                              (async-boa-query/query adapter ds sql [param-value] respond raise)))
            array-arg-fn (fn [ds arg-map respond raise]
                           (let [{:keys [sql params]} (build-prepared-statement arg-map tokens "" [])]
                             (when (logger/enabled? :debug)
                               (logger/debug "Query is: " sql))
                             (async-boa-query/query adapter ds sql params respond raise)))]
        (fn [ds arg respond raise]
          (if (vector? (get arg var-name))
            (array-arg-fn ds arg respond raise)
            (single-arg-fn ds arg respond raise))))

      (= 2 var-count)
      (let [vars (filterv (fn [[type _]] (= type :variable)) tokens)
            var-name-1 (second (nth vars 0))
            var-name-2 (second (nth vars 1))
            {:keys [sql]} (build-prepared-statement {var-name-1 ::single-placeholder
                                                     var-name-2 ::single-placeholder} tokens "" [])

            simple-fn (fn [ds arg respond raise]
                        (when (logger/enabled? :debug)
                          (logger/debug "Query is: " sql))
                        (async-boa-query/query adapter ds sql [(get arg var-name-1) (get arg var-name-2)] respond raise))

            complex-fn (fn [ds arg respond raise]
                         (let [{:keys [sql params]} (build-prepared-statement arg tokens "" [])]
                           (when (logger/enabled? :debug)
                             (logger/debug "Query is: " sql))
                           (async-boa-query/query adapter ds sql params respond raise)))]
        (fn [ds arg respond raise]
          (if (or (vector? (get arg var-name-1))
                  (vector? (get arg var-name-2)))
            (complex-fn ds arg respond raise)
            (simple-fn ds arg respond raise))))

      (= 3 var-count)
      (let [vars (filterv (fn [[type _]] (= type :variable)) tokens)
            var-name-1 (second (nth vars 0))
            var-name-2 (second (nth vars 1))
            var-name-3 (second (nth vars 2))
            {:keys [sql]} (build-prepared-statement {var-name-1 ::single-placeholder
                                                     var-name-2 ::single-placeholder
                                                     var-name-3 ::single-placeholder} tokens "" [])

            simple-fn (fn [ds arg respond raise]
                        (when (logger/enabled? :debug)
                          (logger/debug "Query is: " sql))
                        (async-boa-query/query adapter ds sql 
                                               [(get arg var-name-1) 
                                                (get arg var-name-2) 
                                                (get arg var-name-3)] 
                                               respond raise))

            complex-fn (fn [ds arg respond raise]
                         (let [{:keys [sql params]} (build-prepared-statement arg tokens "" [])]
                           (when (logger/enabled? :debug)
                             (logger/debug "Query is: " sql))
                           (async-boa-query/query adapter ds sql params respond raise)))]
        (fn [ds arg respond raise]
          (if (or (vector? (get arg var-name-1))
                  (vector? (get arg var-name-2))
                  (vector? (get arg var-name-3)))
            (complex-fn ds arg respond raise)
            (simple-fn ds arg respond raise))))

      :else
      (fn [ds context respond raise]
        (let [{:keys [sql params]} (build-prepared-statement context tokens "" [])]
          (when (logger/enabled? :debug)
            (logger/debug "Query is: " sql))
          (async-boa-query/query adapter ds sql params respond raise))))))
