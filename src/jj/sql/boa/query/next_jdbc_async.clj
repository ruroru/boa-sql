(ns jj.sql.boa.query.next-jdbc-async
  (:require [jj.sql.boa.query :as boa-query]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs])
  (:import (java.util.concurrent CompletableFuture ExecutorService)))

(defrecord NextJdbcAsyncAdapter [^ExecutorService executor additional-info]
  boa-query/BoaQuery
  (create-query [_]
    (fn [ds query-args]
      (if executor
        (CompletableFuture/supplyAsync
          (fn [] (jdbc/execute! ds query-args additional-info))
          executor)
        (CompletableFuture/supplyAsync
          (fn [] (jdbc/execute! ds query-args additional-info))))))
  (create-query-args [_ param-names]
    (boa-query/positional-args-fn param-names)))

(defn ->NextJdbcAsyncAdapter
  ([] (->NextJdbcAsyncAdapter {:builder-fn rs/as-unqualified-lower-maps} nil))
  ([additional-info] (->NextJdbcAsyncAdapter additional-info nil))
  ([additional-info executor] (NextJdbcAsyncAdapter. executor additional-info)))
