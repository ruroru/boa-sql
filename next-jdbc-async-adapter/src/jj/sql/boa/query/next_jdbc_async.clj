(ns jj.sql.boa.query.next-jdbc-async
  (:require [jj.sql.boa.protocol.query-builder :as query-builder]
            [jj.sql.boa.query :as boa-query]
            [jj.sql.boa.strategy.jdbc :as jdbc-strategy]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs])
  (:import (java.util.concurrent CompletableFuture ExecutorService)))

(defrecord NextJdbcAsyncAdapter [^ExecutorService executor additional-info strategy]
  boa-query/BoaQuery
  (parameterless-query [_ ds sql]
    (if executor
      (CompletableFuture/supplyAsync
        (fn [] (jdbc/execute! ds [sql] additional-info))
        executor)
      (CompletableFuture/supplyAsync
        (fn [] (jdbc/execute! ds [sql] additional-info)))))
  (query [_ ds sql params]
    (if executor
      (CompletableFuture/supplyAsync
        (fn [] (jdbc/execute! ds (into [sql] params) additional-info))
        executor)
      (CompletableFuture/supplyAsync
        (fn [] (jdbc/execute! ds (into [sql] params) additional-info)))))
  query-builder/QueryBuilder
  (build-query [_ tokens]
    (query-builder/build-query strategy tokens)))

(defn ->NextJdbcAsyncAdapter
  ([] (->NextJdbcAsyncAdapter nil {:builder-fn rs/as-unqualified-lower-maps}))
  ([executor] (->NextJdbcAsyncAdapter executor {:builder-fn rs/as-unqualified-lower-maps}))
  ([executor additional-info] (NextJdbcAsyncAdapter. executor additional-info (jdbc-strategy/->JDBCStrategy))))
