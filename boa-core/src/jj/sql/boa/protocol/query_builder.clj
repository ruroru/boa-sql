(ns jj.sql.boa.protocol.query-builder)

(defprotocol QueryBuilder
  (build-query [this tokens]))
