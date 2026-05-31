(ns jj.sql.boa
  (:require [jj.sql.boa.parser :as parser]
            [jj.sql.boa.protocol.query-builder :as query-builder]
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
        transform-fn (query-builder/build-query adapter tokens)]
    (fn
      ([ds]
       (let [{:keys [sql params]} (transform-fn nil)]
         (if params
           (boa-query/query adapter ds sql params)
           (boa-query/parameterless-query adapter ds sql))))
      ([ds arg]
       (let [{:keys [sql params]} (transform-fn arg)]
         (if params
           (boa-query/query adapter ds sql params)
           (boa-query/parameterless-query adapter ds sql)))))))
