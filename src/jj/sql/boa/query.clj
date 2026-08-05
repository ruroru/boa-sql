(ns jj.sql.boa.query)

(defprotocol BoaQuery
  (create-query [this])
  (create-query-args [this param-names]))

(defn positional-args-fn
  [param-names]
  (if (nil? param-names)
    (fn [sql params] (into [sql] params))
    (let [[a b c d] param-names]
      (case (count param-names)
        0 (fn [sql _] [sql])
        1 (fn [sql ctx] [sql (get ctx a)])
        2 (fn [sql ctx] [sql (get ctx a) (get ctx b)])
        3 (fn [sql ctx] [sql (get ctx a) (get ctx b) (get ctx c)])
        4 (fn [sql ctx] [sql (get ctx a) (get ctx b) (get ctx c) (get ctx d)])
        (fn [sql ctx] (into [sql] (map #(get ctx %)) param-names))))))
