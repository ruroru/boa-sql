(ns jj.sql.boa.query.next-jdbc-async
  (:require [jj.sql.boa.async-query :as boa-query]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs])
  (:import (java.util.concurrent CompletableFuture)
           (java.util.function Consumer Function)))

(defn- as-consumer [f]
       (reify Consumer
              (accept [_ v] (f v))))

(defrecord NextJdbcAdapter [executor additional-info]
           boa-query/AsyncBoaQuery
           (parameterless-query [this ds sql respond raise]
                                (let [^CompletableFuture cf (CompletableFuture/supplyAsync
                                                              (fn [] (jdbc/execute! ds [sql] additional-info))
                                                              executor)]
                                     (-> cf
                                         (.thenAccept ^Consumer (as-consumer respond))
                                         (.exceptionally ^Function (fn [throwable]
                                                                       (raise throwable)
                                                                       nil)))))

           (query [this ds sql params respond raise]
                  (let [^CompletableFuture cf (CompletableFuture/supplyAsync
                                                (fn [] (jdbc/execute! ds (into [sql] params) additional-info))
                                                executor)]
                       (-> cf
                           (.thenAccept ^Consumer (as-consumer respond))
                           (.exceptionally ^Function (fn [throwable]
                                                         (raise throwable)
                                                         nil))))))

(defn ->NextJdbcAdapter
      ([executor] (->NextJdbcAdapter executor {:builder-fn rs/as-unqualified-lower-maps}))
      ([executor additional-info] (NextJdbcAdapter. executor additional-info)))