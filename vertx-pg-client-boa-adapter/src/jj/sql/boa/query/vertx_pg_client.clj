(ns jj.sql.boa.query.vertx-pg-client
  (:require [clojure.string :as str]
            [clojure.tools.logging :as logger]
            [jj.sql.boa.async-query :as async-boa-query])
  (:import (io.vertx.core Handler)
           (io.vertx.sqlclient RowSet Tuple)
           (java.util Arrays)))

(defn rows->maps
  [rows]
  (let [cols (.columnsNames  ^RowSet rows)]
    (mapv (fn [row]
            (reduce (fn [m col]
                      (assoc m (keyword col) (.getValue row col)))
                    {}
                    cols))
          rows)))


(defn- params->tuple
  [params]
  (if (empty? params)
    (Tuple/tuple)
    (Tuple/from (Arrays/asList (object-array params)))))

(defn- prepare-pg-sql
  [sql]
  (if (str/blank? sql)
    sql
    (let [parts (str/split sql #"\?" -1)]
      (loop [result (first parts)
             counter 1
             remaining (rest parts)]
        (if (empty? remaining)
          result
          (recur (str result "$" counter (first remaining))
                 (inc counter)
                 (rest remaining)))))))

(defn- execute-handler
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