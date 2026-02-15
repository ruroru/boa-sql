(ns jj.sql.boa.query.next-jdbc
  (:require [jj.sql.boa.query :as boa-query]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]))

(defrecord NextJdbcAdapter [additional-info]
  boa-query/BoaQuery
  (parameterless-query [this ds sql]
    (jdbc/execute! ds [sql] additional-info))
  (query [this ds sql params]
    (jdbc/execute! ds (into [sql] params) additional-info)))

(defn ->NextJdbcAdapter
  ([] (->NextJdbcAdapter {:builder-fn rs/as-unqualified-lower-maps}))
  ([additional-info] (NextJdbcAdapter. additional-info)))



