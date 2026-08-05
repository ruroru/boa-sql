(ns jj.sql.boa.query.next-jdbc
  (:require [jj.sql.boa.query :as boa-query]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]))

(defrecord NextJdbcAdapter [additional-info]
  boa-query/BoaQuery
  (create-query [_]
    (fn [ds query-args]
      (jdbc/execute! ds query-args additional-info)))
  (create-query-args [_ param-names]
    (boa-query/positional-args-fn param-names)))

(defn ->NextJdbcAdapter
  ([] (->NextJdbcAdapter {:builder-fn rs/as-unqualified-lower-maps}))
  ([additional-info] (NextJdbcAdapter. additional-info)))
