(ns jj.sql.boa.async-query)

(defprotocol AsyncBoaQuery
  (parameterless-query [this ds sql respond reject])
  (query [this ds sql params respond reject]))