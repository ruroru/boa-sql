(ns jj.sql.boa.resource-resolver
  (:require [clojure.java.io :as io]
            [jj.sql.boa.protocol.resolver :as resolver]))


(defrecord ResourceResolver []
  resolver/Resolver
  (can-open? [_ path]
    (boolean (io/resource path)))

  (open [_ path]
    (if-let [res (io/resource path)]
      (slurp (io/input-stream res))
      (throw (ex-info "Resource not found on classpath"
                      {:path path :type :resource-not-found})))))