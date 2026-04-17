(ns jj.sql.boa
  (:require [clojure.tools.logging :as logger]
            [jj.sql.boa.parser :as parser]
            [jj.sql.boa.protocol.resolver :as resolver]
            [jj.sql.boa.query :as boa-query]
            [jj.sql.boa.resource-resolver :as resource-resolver]
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

(defn build-query [adapter query-file]
  (let [
        resource-resolver (resource-resolver/->ResourceResolver)
        string-value (when
                               (resolver/can-open? resource-resolver query-file)
                               (resolver/open resource-resolver query-file))
        tokens (parser/tokenize string-value)
        var-count (count (filter (fn [[type _]] (= type :variable)) tokens))]

    (cond
      (zero? var-count)
      (let [{:keys [sql]} (parser/parse {} tokens)]
        (fn
          ([ds]
           (when (logger/enabled? :debug)
             (logger/debug "Query is: " sql))
           (boa-query/parameterless-query adapter ds sql))
          ([ds _]
           (when (logger/enabled? :debug)
             (logger/debug "Query is: " sql))
           (boa-query/parameterless-query adapter ds sql))))

      (= 1 var-count)
      (let [var-name (second (first (filter (fn [[type _]] (= type :variable)) tokens)))
            {:keys [sql]} (parser/parse {var-name ::single-placeholder} tokens)

            single-arg-fn (fn [ds arg]
                            (when (logger/enabled? :debug)
                              (logger/debug "Query is: " sql))
                            (let [param-value (get arg var-name)]
                              (boa-query/query adapter ds sql [param-value])))

            array-arg-fn (fn [ds arg-map]
                           (let [{:keys [sql params]} (parser/parse arg-map tokens)]
                             (when (logger/enabled? :debug)
                               (logger/debug "Query is: " sql))
                             (boa-query/query adapter ds sql params)))]
        (fn [ds arg]
          (if (vector? (get arg var-name))
            (array-arg-fn ds arg)
            (single-arg-fn ds arg))))

      (= 2 var-count)
      (let [vars (filterv (fn [[type _]] (= type :variable)) tokens)
            var-name-1 (second (nth vars 0))
            var-name-2 (second (nth vars 1))
            {:keys [sql]} (parser/parse {var-name-1 ::single-placeholder
                                         var-name-2 ::single-placeholder} tokens)

            simple-fn (fn [ds arg]
                        (when (logger/enabled? :debug)
                          (logger/debug "Query is: " sql))
                        (boa-query/query adapter ds sql [(get arg var-name-1) (get arg var-name-2)]))

            complex-fn (fn [ds arg]
                         (let [{:keys [sql params]} (parser/parse arg tokens)]
                           (when (logger/enabled? :debug)
                             (logger/debug "Query is: " sql))
                           (boa-query/query adapter ds sql params)))]
        (fn [ds arg]
          (if (or (vector? (get arg var-name-1))
                  (vector? (get arg var-name-2)))
            (complex-fn ds arg)
            (simple-fn ds arg))))

      (= 3 var-count)
      (let [vars (filterv (fn [[type _]] (= type :variable)) tokens)
            var-name-1 (second (nth vars 0))
            var-name-2 (second (nth vars 1))
            var-name-3 (second (nth vars 2))
            {:keys [sql]} (parser/parse {var-name-1 ::single-placeholder
                                         var-name-2 ::single-placeholder
                                         var-name-3 ::single-placeholder} tokens)

            simple-fn (fn [ds arg]
                        (when (logger/enabled? :debug)
                          (logger/debug "Query is: " sql))
                        (boa-query/query adapter ds sql [(get arg var-name-1)
                                                         (get arg var-name-2)
                                                         (get arg var-name-3)]))

            complex-fn (fn [ds arg]
                         (let [{:keys [sql params]} (parser/parse arg tokens)]
                           (when (logger/enabled? :debug)
                             (logger/debug "Query is: " sql))
                           (boa-query/query adapter ds sql params)))]
        (fn [ds arg]
          (if (or (vector? (get arg var-name-1))
                  (vector? (get arg var-name-2))
                  (vector? (get arg var-name-3)))
            (complex-fn ds arg)
            (simple-fn ds arg))))

      :else
      (fn [ds context]
        (let [{:keys [sql params]} (parser/parse context tokens)]
          (when (logger/enabled? :debug)
            (logger/debug "Query is: " sql))
          (boa-query/query adapter ds sql params))))))
