(ns jj.sql.boa.resource-resolver
  (:require [jj.sql.boa.protocol.resolver :as resolver]))


(defrecord ResourceResolver []
  resolver/Resolver
  (can-open? [_ path]
    (boolean (clojure.java.io/resource path)))

  (open [_ path]
    (if-let [res (clojure.java.io/resource path)]
      (slurp (clojure.java.io/input-stream res))
      (throw (ex-info "Resource not found on classpath"
                      {:path path :type :resource-not-found})))))