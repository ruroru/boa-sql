(ns jj.sql.boa
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.tools.logging :as logger]
            [jj.sql.boa.parser :as parser]
            [jj.sql.boa.query :as boa-query]))

(defn build-query [adapter query-file]
  (let [resource (or (io/resource query-file)
                     (throw (ex-info "Query not found" {:file query-file})))
        tokens (parser/tokenize (str/trim (slurp resource)))
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

      :else
      (fn [ds context]
        (let [{:keys [sql params]} (parser/parse context tokens)]
          (when (logger/enabled? :debug)
            (logger/debug "Query is: " sql))
          (boa-query/query adapter ds sql params))))))
