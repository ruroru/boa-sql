(ns resource-resolver.core-test
  (:require [clojure.test :refer :all]
            [jj.sql.boa.resource-resolver :refer :all]
            [jj.sql.boa.protocol.resolver :as resolver]))

(deftest can-open-test
  (testing "can-open? returns false for missing resource"
    (is (false? (resolver/can-open? (->ResourceResolver) "nonexistent.sql")))))
