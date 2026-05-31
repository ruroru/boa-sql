(ns jj.sql.boa.query.next-jdbc
  (:require [jj.sql.boa.protocol.query-builder :as query-builder]
            [jj.sql.boa.query :as boa-query]
            [jj.sql.boa.strategy.jdbc :as jdbc-strategy]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]))

(defrecord NextJdbcAdapter [additional-info strategy]
  boa-query/BoaQuery
  (parameterless-query [this ds sql]
    (jdbc/execute! ds [sql] additional-info))
  (query [this ds sql params]
    (jdbc/execute! ds (into [sql] params) additional-info))
  query-builder/QueryBuilder
  (build-query [_ tokens]
    (query-builder/build-query strategy tokens)))

(defn ->NextJdbcAdapter
  ([] (->NextJdbcAdapter {:builder-fn rs/as-unqualified-lower-maps}))
  ([additional-info] (NextJdbcAdapter. additional-info (jdbc-strategy/->JDBCStrategy))))
