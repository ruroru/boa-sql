(ns jj.sql.async-boa
  (:require
    [clojure.tools.logging :as logger]
    [jj.sql.boa.async-query :as async-boa-query]
    [jj.sql.boa.parser :as parser]
    [jj.sql.boa.protocol.resolver :as resolver]
    [jj.sql.boa.resource-resolver :as resource-resolver]
    ))

(defn- build-variadic-fn
  [adapter tokens]
  (let [var-names (mapv second (filterv (fn [[t _]] (= t :variable)) tokens))
        placeholder-ctx (zipmap var-names (repeat ::single-placeholder))
        {:keys [sql]} (parser/parse placeholder-ctx tokens)
        n (count var-names)]
    (fn [ds arg respond raise]
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
            (async-boa-query/query adapter ds sql params respond raise))
          (let [params (loop [i 0
                              acc (transient [])]
                         (if (< i n)
                           (recur (inc i) (conj! acc (get arg (nth var-names i))))
                           (persistent! acc)))]
            (when (logger/enabled? :debug)
              (logger/debug "Query is: " sql))
            (async-boa-query/query adapter ds sql params respond raise)))))))


(defn build-async-query [adapter query-file-path]
  (let [resource-resolver (resource-resolver/->ResourceResolver)
        string-value (when
                       (resolver/can-open? resource-resolver query-file-path)
                       (resolver/open resource-resolver query-file-path))
        tokens (parser/tokenize string-value)
        var-count (count (filter (fn [[type _]] (= type :variable)) tokens))]
    (cond
      (zero? var-count)
      (let [{:keys [sql]} (parser/parse {} tokens)]
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
            {:keys [sql]} (parser/parse {var-name ::single-placeholder} tokens)
            single-arg-fn (fn [ds arg respond raise]
                            (when (logger/enabled? :debug)
                              (logger/debug "Query is: " sql))
                            (let [param-value (get arg var-name)]
                              (async-boa-query/query adapter ds sql [param-value] respond raise)))
            array-arg-fn (fn [ds arg-map respond raise]
                           (let [{:keys [sql params]} (parser/parse arg-map tokens)]
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
            {:keys [sql]} (parser/parse {var-name-1 ::single-placeholder
                                         var-name-2 ::single-placeholder} tokens)

            simple-fn (fn [ds arg respond raise]
                        (when (logger/enabled? :debug)
                          (logger/debug "Query is: " sql))
                        (async-boa-query/query adapter ds sql [(get arg var-name-1) (get arg var-name-2)] respond raise))

            complex-fn (fn [ds arg respond raise]
                         (let [{:keys [sql params]} (parser/parse arg tokens)]
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
            {:keys [sql]} (parser/parse {var-name-1 ::single-placeholder
                                         var-name-2 ::single-placeholder
                                         var-name-3 ::single-placeholder} tokens)

            simple-fn (fn [ds arg respond raise]
                        (when (logger/enabled? :debug)
                          (logger/debug "Query is: " sql))
                        (async-boa-query/query adapter ds sql
                                               [(get arg var-name-1)
                                                (get arg var-name-2)
                                                (get arg var-name-3)]
                                               respond raise))

            complex-fn (fn [ds arg respond raise]
                         (let [{:keys [sql params]} (parser/parse arg tokens)]
                           (when (logger/enabled? :debug)
                             (logger/debug "Query is: " sql))
                           (async-boa-query/query adapter ds sql params respond raise)))]
        (fn [ds arg respond raise]
          (if (or (vector? (get arg var-name-1))
                  (vector? (get arg var-name-2))
                  (vector? (get arg var-name-3)))
            (complex-fn ds arg respond raise)
            (simple-fn ds arg respond raise))))

      (= 4 var-count)
      (let [vars (filterv (fn [[type _]] (= type :variable)) tokens)
            var-name-1 (second (nth vars 0))
            var-name-2 (second (nth vars 1))
            var-name-3 (second (nth vars 2))
            var-name-4 (second (nth vars 3))
            {:keys [sql]} (parser/parse {var-name-1 ::single-placeholder
                                         var-name-2 ::single-placeholder
                                         var-name-3 ::single-placeholder
                                         var-name-4 ::single-placeholder} tokens)

            simple-fn (fn [ds arg respond raise]
                        (when (logger/enabled? :debug)
                          (logger/debug "Query is: " sql))
                        (async-boa-query/query adapter ds sql
                                               [(get arg var-name-1)
                                                (get arg var-name-2)
                                                (get arg var-name-3)
                                                (get arg var-name-4)]
                                               respond raise))

            complex-fn (fn [ds arg respond raise]
                         (let [{:keys [sql params]} (parser/parse arg tokens)]
                           (when (logger/enabled? :debug)
                             (logger/debug "Query is: " sql))
                           (async-boa-query/query adapter ds sql params respond raise)))]
        (fn [ds arg respond raise]
          (if (or (vector? (get arg var-name-1))
                  (vector? (get arg var-name-2))
                  (vector? (get arg var-name-3))
                  (vector? (get arg var-name-4)))
            (complex-fn ds arg respond raise)
            (simple-fn ds arg respond raise))))

      :else
      (build-variadic-fn adapter tokens))))