(ns jj.sql.boa.query.next-jdbc
  (:require [clojure.tools.logging :as logger]
            [clojure.string :as str]
            [next.jdbc :as jdbc]
            [jj.sql.boa.query :as boa-query]
            [jj.sql.boa.async-query :as async-boa-query]
            [next.jdbc.result-set :as rs])
  (:import (io.vertx.core Handler)
           (io.vertx.sqlclient Row Tuple)
           (java.util Arrays)))


;; --- Adapters ---

(defrecord NextJdbcAdapter [additional-info]
  boa-query/BoaQuery
  (parameterless-query [this ds sql]
    (jdbc/execute! ds [sql] additional-info))
  (query [this ds sql params]
    (jdbc/execute! ds (into [sql] params) additional-info)))

(defn ->NextJdbcAdapter
  ([] (->NextJdbcAdapter {:builder-fn rs/as-unqualified-lower-maps}))
  ([additional-info] (NextJdbcAdapter. additional-info)))

(defn row->map
  "Convert a Vert.x Row to a Clojure map"
  [^Row row]
  (let [column-names (.columnNames row)] ;; FIXED: was columnsNames
    (reduce (fn [m col]
              (assoc m (keyword col) (.getValue row col)))
            {}
            column-names)))

(defn rows->maps
  "Convert Vert.x RowSet to a sequence of maps"
  [rows]
  (mapv row->map rows))

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
        (let [rows (.result ar)
              results (rows->maps rows)]
          (when (logger/enabled? :debug)
            (logger/debugf "Query succeeded: %s, returned %d rows" sql-vec (count results)))
          (respond results))
        (let [error (.cause ar)]
          (logger/errorf error "Query failed: %s" sql-vec)
          (reject error))))))

;; --- Adapters ---

(defrecord NextJdbcAdapter [additional-info]
  boa-query/BoaQuery
  (parameterless-query [this ds sql]
    (jdbc/execute! ds [sql] additional-info))
  (query [this ds sql params]
    (jdbc/execute! ds (into [sql] params) additional-info)))

(defn ->NextJdbcAdapter
  ([] (->NextJdbcAdapter {:builder-fn rs/as-unqualified-lower-maps}))
  ([additional-info] (NextJdbcAdapter. additional-info)))

(defrecord VertxPgAdapter [additional-info]
  async-boa-query/AsyncBoaQuery

  (parameterless-query [this ds sql respond reject]
    (-> ds
        (.query sql)
        (.execute (execute-handler [sql] respond reject))))

  (query [this ds sql params respond reject]
    (let [pg-sql (prepare-pg-sql sql)
          tuple (params->tuple params)
          sql-vec (into [pg-sql] params)]
      (-> ds
          (.preparedQuery pg-sql)
          (.execute tuple (execute-handler sql-vec respond reject))))))

(defn ->VertxPgAdapter
  ([] (->VertxPgAdapter {}))
  ([additional-info] (VertxPgAdapter. additional-info)))