(ns jj.sql.boa.query)

(defprotocol BoaQuery
  (parameterless-query [this ds sql])
  (query [this ds sql params]))