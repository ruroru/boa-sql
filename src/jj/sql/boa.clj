(ns jj.sql.boa
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.tools.logging :as logger]
            [jj.sql.boa.parser :as parser]
            [jj.sql.boa.query :as boa-query])
  (:import (java.util.concurrent CompletableFuture)
           (java.util.function Consumer Function)))

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

(defn build-query [adapter query-file]
  (let [resource (or (io/resource query-file)
                     (throw (ex-info "Query not found" {:file query-file})))
        tokens (parser/tokenize (str/trim (slurp resource)))
        var-count (count (filter (fn [[type _]] (= type :variable)) tokens))]

    (cond
      (zero? var-count)
      (let [{:keys [sql params]} (build-prepared-statement {} tokens "" [])]
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
            {:keys [sql]} (build-prepared-statement {var-name ::single-placeholder} tokens "" [])

            single-arg-fn (fn [ds arg]
                            (when (logger/enabled? :debug)
                              (logger/debug "Query is: " sql))
                            (let [param-value (get arg var-name)]
                              (boa-query/query adapter ds sql [param-value])))

            array-arg-fn (fn [ds arg-map]
                           (let [{:keys [sql params]} (build-prepared-statement arg-map tokens "" [])]
                             (when (logger/enabled? :debug)
                               (logger/debug "Query is: " sql))
                             (boa-query/query adapter ds sql params)))]
        (fn [ds arg]
          (if (vector? (get arg var-name))
            (array-arg-fn ds arg)
            (single-arg-fn ds arg))))

      :else
      (fn
        ([ds context]
         (let [{:keys [sql params]} (build-prepared-statement context tokens "" [])]
           (when (logger/enabled? :debug)
             (logger/debug "Query is: " sql))
           (boa-query/query adapter ds sql params)))))))

(defn build-async-query [executor adapter query-file]
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
           (-> (CompletableFuture/supplyAsync
                 (fn []
                   (boa-query/parameterless-query adapter ds sql))
                 executor)
               ^CompletableFuture (.thenAccept ^Consumer respond)
               ^CompletableFuture (.exceptionally ^Function (ErrorHandler. raise))))
          ([ds _ respond raise]
           (when (logger/enabled? :debug)
             (logger/debug "Query is: " sql))
           (-> (CompletableFuture/supplyAsync
                 (fn []
                   (boa-query/parameterless-query adapter ds sql))
                 executor)
               ^CompletableFuture (.thenAccept ^Consumer respond)
               ^CompletableFuture  (.exceptionally ^Function (ErrorHandler. raise))))))

      (= 1 var-count)
      (let [var-name (second (first (filter (fn [[type _]] (= type :variable)) tokens)))
            {:keys [sql]} (build-prepared-statement {var-name ::single-placeholder} tokens "" [])

            single-arg-fn (fn [ds arg respond raise]
                            (when (logger/enabled? :debug)
                              (logger/debug "Query is: " sql))
                            (let [param-value (get arg var-name)]
                              (-> (CompletableFuture/supplyAsync
                                    (fn []
                                      (boa-query/query adapter ds sql [param-value]))
                                    executor)
                                  ^CompletableFuture (.thenAccept ^Consumer respond)
                                  ^CompletableFuture (.exceptionally ^Function (ErrorHandler. raise)))))

            array-arg-fn (fn [ds arg-map respond raise]
                           (let [{:keys [sql params]} (build-prepared-statement arg-map tokens "" [])]
                             (when (logger/enabled? :debug)
                               (logger/debug "Query is: " sql))
                             (-> (CompletableFuture/supplyAsync
                                   (fn []
                                     (boa-query/query adapter ds sql params))
                                   executor)
                                 ^CompletableFuture (.thenAccept ^Consumer respond)
                                 ^CompletableFuture (.exceptionally ^Function (ErrorHandler. raise)))))]
        (fn [ds arg respond raise]
          (if (vector? (get arg var-name))
            (array-arg-fn ds arg respond raise)
            (single-arg-fn ds arg respond raise))))

      :else
      (fn [ds context respond raise]
        (let [{:keys [sql params]} (build-prepared-statement context tokens "" [])]
          (when (logger/enabled? :debug)
            (logger/debug "Query is: " sql))
          (-> (CompletableFuture/supplyAsync
                (fn []
                  (boa-query/query adapter ds sql params))
                executor)
              ^CompletableFuture (.thenAccept ^Consumer respond)
              ^CompletableFuture (.exceptionally ^Function (ErrorHandler. raise))))))))