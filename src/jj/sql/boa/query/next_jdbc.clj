(ns jj.sql.boa.query.next-jdbc
  (:require [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]
            [jj.sql.boa.query :refer [BoaQuery]]))

(defrecord NextJdbcAdapter [additional-info]
  BoaQuery
  (build-parameterless-query [this ds sql]
    (jdbc/execute! ds [sql] additional-info))
  (build-query [this ds sql params]
    (jdbc/execute! ds (into [sql] params) additional-info)))

(defn ->NextJdbcAdapter
  ([] (->NextJdbcAdapter {:builder-fn rs/as-unqualified-lower-maps}))
  ([additional-info]
   (NextJdbcAdapter. additional-info)))