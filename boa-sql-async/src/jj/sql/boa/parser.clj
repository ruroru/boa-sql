(ns jj.sql.boa.parser
  (:require [jj.sql.boa.lexer :as lexer]))

(def ^:private ^:const comma ",")
(def ^:private ^:const question-mark "?")
(def ^:private ^:const op-paren "(")
(def ^:private ^:const cl-paren ")")

(defn- skip-to-endif [lexed-list]
  (let [after (drop-while #(not= (first %) :endif) lexed-list)]
    (if (seq after) (rest after) after)))

(defn- skip-to-else-or-endif [lexed-list]
  (drop-while #(not (#{:else :endif} (first %))) lexed-list))

(defn- parse-ast [context lexed-list sb parameters]
  (if-let [[token-type token-value] (first lexed-list)]
    (let [remaining (rest lexed-list)]
      (case token-type
        :text
        (recur context remaining (str sb token-value) parameters)

        :variable
        (let [value (get context token-value)]
          (if (coll? value)
            (if (coll? (first value))
              (let [number-of-items-per-tuple (count (first value))
                    number-of-tuples (count value)
                    placeholders (apply str
                                       (interpose comma
                                                  (repeat number-of-tuples
                                                          (str op-paren
                                                               (apply str (interpose comma (repeat number-of-items-per-tuple question-mark)))
                                                               cl-paren))))]
                (recur context remaining (str sb placeholders) (into parameters (flatten value))))
              (let [placeholders (str op-paren
                                     (apply str (interpose comma (repeat (count value) question-mark)))
                                     cl-paren)]
                (recur context remaining (str sb placeholders) (into parameters value))))
            (recur context remaining (str sb question-mark) (conj parameters (get context token-value)))))

        :if
        (if (get context token-value)
          (recur context remaining sb parameters)
          (let [after (skip-to-else-or-endif remaining)]
            (if (= (first (first after)) :else)
              (recur context (rest after) sb parameters)
              (recur context after sb parameters))))

        :else
        (recur context (skip-to-endif remaining) sb parameters)

        :endif
        (recur context remaining sb parameters)

        (recur context remaining sb parameters)))
    {:sql sb :params parameters}))

(defn tokenize [string]
  (lexer/tokenize string))

(defn parse [context tokens]
  (parse-ast context tokens "" []))
