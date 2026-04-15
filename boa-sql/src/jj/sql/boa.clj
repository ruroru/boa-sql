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

      :else
      (fn [ds context]
        (let [{:keys [sql params]} (parser/parse context tokens)]
          (when (logger/enabled? :debug)
            (logger/debug "Query is: " sql))
          (boa-query/query adapter ds sql params))))))
