(ns jj.sql.boa
  (:require [jj.sql.boa.builder :as builder]
            [jj.sql.boa.parser :as parser]
            [jj.sql.boa.protocol.resolver :as resolver]
            [jj.sql.boa.query :as boa-query]
            [jj.sql.boa.resource-resolver :as resource-resolver]))

(defn- resolve-and-tokenize [query-file]
  (let [resource-resolver (resource-resolver/->ResourceResolver)
        string-value (when
                       (resolver/can-open? resource-resolver query-file)
                       (resolver/open resource-resolver query-file))]
    (parser/tokenize string-value)))

(defn build-query [adapter query-file]
  (let [tokens (resolve-and-tokenize query-file)
        transform-fn (builder/build-query tokens)
        run-query (boa-query/create-query adapter)
        args-from-ctx (boa-query/create-query-args adapter (builder/param-names tokens))
        args-from-seq (boa-query/create-query-args adapter nil)]
    (fn query-fn
      ([ds] (query-fn ds nil))
      ([ds ctx]
       (let [built (transform-fn ctx)]
         (if (string? built)
           (run-query ds (args-from-ctx built ctx))
           (run-query ds (args-from-seq (nth built 0) (nth built 1)))))))))
