(ns jj.sql.boa.query)

(defprotocol BoaQuery
  (build-parameterless-query [this ds sql])
  (build-query [this ds sql params]))