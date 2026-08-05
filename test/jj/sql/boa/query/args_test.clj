(ns jj.sql.boa.query.args-test
  (:require [clojure.test :refer [are deftest is]]
            [jj.sql.boa.query :as boa-query]))

(def ctx {:a 1 :b 2 :c 3 :d 4 :e 5 :f 6 :g 7})

(defn- names [n]
  (vec (take n [:a :b :c :d :e :f :g])))

(defn- values [n]
  (vec (take n [1 2 3 4 5 6 7])))

;; Both adapters unroll the common parameter counts by hand, so every branch
;; gets checked here rather than only the counts the query files happen to use.

(deftest positional-args-covers-every-branch
  (are [n] (= (into ["sql"] (values n))
              ((boa-query/positional-args-fn (names n)) "sql" ctx))
           0 1 2 3 4 5 6 7))

(deftest positional-args-reads-params-in-placeholder-order
  (is (= ["sql" 3 1 2]
         ((boa-query/positional-args-fn [:c :a :b]) "sql" ctx))))

;; A variable used more than once in a query gets one placeholder - and so one
;; entry in param-names - per occurrence, and every one of them has to be filled
;; in with that variable's value. Repeats are checked at each unrolled count as
;; well as past it, since each count is a separately written branch.

(deftest positional-args-repeats-a-value-per-occurrence
  (are [param-names expected]
    (= expected ((boa-query/positional-args-fn param-names) "sql" ctx))

    [:a :a] ["sql" 1 1]
    [:a :b :a] ["sql" 1 2 1]
    [:a :a :a] ["sql" 1 1 1]
    [:a :a :b :a] ["sql" 1 1 2 1]
    [:a :a :a :a] ["sql" 1 1 1 1]
    [:a :b :a :b :a] ["sql" 1 2 1 2 1]
    [:a :a :a :a :a :a] ["sql" 1 1 1 1 1 1]
    [:a :b :a :b :a :b :a] ["sql" 1 2 1 2 1 2 1]))

(deftest positional-args-repeats-a-missing-variable-as-nil
  (is (= ["sql" nil 1 nil]
         ((boa-query/positional-args-fn [:missing :a :missing]) "sql" ctx))))

(deftest positional-args-yields-nil-for-missing-variables
  (is (= ["sql" 1 nil]
         ((boa-query/positional-args-fn [:a :missing]) "sql" ctx))))

(deftest positional-args-passes-through-expanded-params
  (is (= ["sql" 1 2 3]
         ((boa-query/positional-args-fn nil) "sql" [1 2 3])))
  (is (= ["sql"]
         ((boa-query/positional-args-fn nil) "sql" [])))
  ;; A repeated variable holding a vector arrives already expanded, values and
  ;; all, once per occurrence.
  (is (= ["sql" 1 2 1 2]
         ((boa-query/positional-args-fn nil) "sql" [1 2 1 2]))))
