(ns jj.sql.boa.protocol.resolver)

(defprotocol Resolver
  (can-open? [this path])
  (open [this path]))