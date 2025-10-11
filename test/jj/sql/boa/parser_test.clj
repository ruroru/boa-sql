(ns jj.sql.boa.parser-test
  (:require [clojure.test :refer [deftest testing is are]]
            [jj.sql.boa.parser :refer [tokenize]]))

(deftest test-empty-string
  (is (= [] (tokenize ""))))

(deftest test-plain-text
  (is (= [[:text "SELECT * FROM users"]]
         (tokenize "SELECT * FROM users"))))

(deftest test-ignore-comments
  (are [expected input] (= expected (tokenize input))
                        [[:text "SELECT * FROM users"]] "-- somethingsomething\nSELECT * FROM users"
                        [[:text "SELECT * FROM users"]] "-- somethingsomething\r\nSELECT * FROM users"
                        [[:text "SELECT * FROM users"]] "SELECT * -- this is a comment\nFROM users"
                        [[:text "SELECT * FROM users"]] "SELECT * FROM users-- comment at the end"))

(deftest lexer-ignores-values-inside-colon
  (is (= [[:text
           "SELECT\n    username,\n    session_id AS `session-id`,\n    DATE_FORMAT(creation_date, '%Y-%m-%d %H:%i:%s') AS `creation-date`\nFROM\n    moviedb.usersessions\nWHERE session_id = "]
          [:variable :session]
          [:text ";"]]
         (tokenize "SELECT\n    username,\n    session_id AS `session-id`,\n    DATE_FORMAT(creation_date, '%Y-%m-%d %H:%i:%s') AS `creation-date`\nFROM\n    moviedb.usersessions\nWHERE session_id = :session;"))))

(deftest with-semicolon-at-the-end
  (is (= [[:text "INSERT INTO users (id) VALUES "]
          [:variable :id]
          [:text ";"]]
         (tokenize "INSERT INTO users (id) VALUES :id;"))))

(deftest insert-with-comma-at-the-end1
  (is (= [[:text
           "INSERT INTO moviedb.usersessions (username, session_id, creation_date)\nVALUES ("]
          [:variable
           :user]
          [:text
           ", "]
          [:variable
           :session]
          [:text
           ", "]
          [:variable
           :creation-date]
          [:text
           ");"]]
         (tokenize "INSERT INTO moviedb.usersessions (username, session_id, creation_date)\nVALUES (:user, :session, :creation-date);"))))

(deftest test-single-variable
  (is (= [[:text "SELECT * FROM users WHERE id = "]
          [:variable :id]]
         (tokenize "SELECT * FROM users WHERE id = :id"))))

(deftest test-variable-at-start
  (is (= [[:variable :name]
          [:text " is the name"]]
         (tokenize ":name is the name"))))

(deftest test-variable-at-end
  (is (= [[:text "WHERE id = "]
          [:variable :id]]
         (tokenize "WHERE id = :id"))))

(deftest test-multiple-variables
  (is (= [[:text "SELECT * FROM users WHERE name = "]
          [:variable :name]
          [:text " AND age = "]
          [:variable :age]]
         (tokenize "SELECT * FROM users WHERE name = :name AND age = :age"))))

(deftest test-variable-followed-by-space
  (is (= [[:variable :id]
          [:text " "]]
         (tokenize ":id "))))

(deftest test-variable-followed-by-comma
  (is (= [[:variable :id]
          [:text ","]]
         (tokenize ":id,"))))

(deftest test-variable-followed-by-parenthesis
  (testing "Variable followed by closing parenthesis"
    (is (= [[:variable :id]
            [:text ")"]]
           (tokenize ":id)"))))
  (testing "Variable followed by opening parenthesis"
    (is (= [[:variable :func]
            [:text "("]]
           (tokenize ":func(")))))

(deftest test-variable-followed-by-newline
  (testing "Variable followed by newline"
    (is (= [[:variable :id]
            [:text "\n"]]
           (tokenize ":id\n")))))

(deftest test-variable-followed-by-tab
  (testing "Variable followed by tab"
    (is (= [[:variable :id]
            [:text "\t"]]
           (tokenize ":id\t")))))

(deftest test-variable-names-with-underscores
  (testing "Variable names containing underscores"
    (is (= [[:variable :user_id]]
           (tokenize ":user_id")))))

(deftest test-variable-names-with-numbers
  (testing "Variable names containing numbers"
    (is (= [[:variable :id123]]
           (tokenize ":id123")))))

(deftest test-only-colon
  (testing "Single colon without variable name"
    (is (= [[:variable (keyword "")]]
           (tokenize ":")))))

(deftest test-newlines-and-tabs
  (is (= [[:text "SELECT *\nFROM users\nWHERE id = "]
          [:variable :id]
          [:text "\n"]]
         (tokenize "SELECT *\nFROM users\nWHERE id = :id\n"))))


(deftest double-colon-are-ignored
  (is (= [[:text "SELECT '123'::INTEGER; SELECT "]
          [:variable :id]
          [:text " "]]
         (tokenize "SELECT '123'::INTEGER; SELECT :id "))))

(deftest double-colon-are-ignored-after-variable
  (is (= [[:text "INSERT INTO users_with_cast (id, numeric_id) VALUES ("]
          [:variable :id]
          [:text ", "]
          [:variable :numeric-id]
          [:text "::INTEGER)"]]
         (tokenize "INSERT INTO users_with_cast (id, numeric_id) VALUES (:id, :numeric-id::INTEGER)"))))