(ns jj.sql.boa.query.vertx-pg-client
  (:require [clojure.string :as str]
            [clojure.tools.logging :as logger]
            [jj.sql.boa.async-query :as async-boa-query])
  (:import (io.vertx.core Handler)
           (io.vertx.sqlclient RowSet Tuple)
           (java.util Arrays)))




(defn rows->maps
  "Convert Vert.x RowSet to a sequence of maps"
  [rows]
  (let [cols (.columnsNames  ^RowSet rows)]
    (mapv (fn [row]
            (reduce (fn [m col]
                      (assoc m (keyword col) (.getValue row col)))
                    {}
                    cols))
          rows)))


(defn- params->tuple
  "Create a Vert.x Tuple from params"
  [params]
  (if (empty? params)
    (Tuple/tuple)
    (Tuple/from (Arrays/asList (object-array params)))))

(defn- prepare-pg-sql
  "Converts JDBC '?' placeholders to Postgres '$n' placeholders"
  [sql]
  (if (str/blank? sql)
    sql
    (let [counter (atom 0)]
      (str/replace sql #"\?" (fn [_] (str "$" (swap! counter inc)))))))

(defn- execute-handler
  "Create a handler that processes query results"
  [sql-vec respond reject]
  (reify Handler
    (handle [_ ar]
      (if (.succeeded ar)
        (let [rows ^RowSet (.result ar)
              results (rows->maps rows)]
          (when (logger/enabled? :debug)
            (logger/debugf "Query succeeded: %s, returned %d rows" sql-vec (count results)))
          (respond results))
        (let [error (.cause ar)]
          (logger/errorf error "Query failed: %s" sql-vec)
          (reject error))))))



(defrecord VertxPgAdapter [additional-info]
  async-boa-query/AsyncBoaQuery

  (parameterless-query [this ds sql respond reject]
    (-> ds
        (.query sql)
        (.execute)
        (.onComplete (execute-handler [sql] respond reject))))

  (query [this ds sql params respond reject]
    (let [pg-sql (prepare-pg-sql sql)
          tuple (params->tuple params)
          sql-vec (into [pg-sql] params)]
      (-> ds
          (.preparedQuery pg-sql)
          (.execute tuple)
          (.onComplete (execute-handler sql-vec respond reject))))))

(defn ->VertxPgAdapter
  ([] (->VertxPgAdapter {}))
  ([additional-info] (VertxPgAdapter. additional-info)))