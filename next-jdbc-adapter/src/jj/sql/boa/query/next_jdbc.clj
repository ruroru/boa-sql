(ns jj.sql.boa.query.next-jdbc
  (:require [clojure.tools.logging :as logger]
            [next.jdbc :as jdbc]
            [jj.sql.boa.query :as boa-query]
            [next.jdbc.result-set :as rs]))

(defrecord NextJdbcAdapter [additional-info]
  boa-query/BoaQuery
  (parameterless-query [this ds sql]
    (when (logger/enabled? :debug)
      (logger/debugf "Query is: %s" [sql]))
    (jdbc/execute! ds [sql] additional-info))
  (query [this ds sql params]
    (when (logger/enabled? :debug)
      (logger/debugf "Query is: %s" (into [sql] params)))
    (jdbc/execute! ds (into [sql] params) additional-info)))

(defn ->NextJdbcAdapter
  ([] (->NextJdbcAdapter {:builder-fn rs/as-unqualified-lower-maps}))
  ([additional-info]
   (NextJdbcAdapter. additional-info)))