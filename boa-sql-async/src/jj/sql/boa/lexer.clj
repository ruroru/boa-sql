(ns jj.sql.boa.lexer)

(def ^:private terminators #{\space \, \) \( \newline \tab \; \return})

(defn- skip-comment [my-sequence]
  (let [rest-after-comment (drop-while #(not= % \newline) my-sequence)]
    (if (= (first rest-after-comment) \newline)
      (rest rest-after-comment)
      rest-after-comment)))

(defn- collect-variable-name [my-sequence]
  (loop [name-chars []
         remaining my-sequence]
    (if (or (empty? remaining)
            (terminators (first remaining))
            (and (= (first remaining) \:)
                 (= (first (next remaining)) \:)))
      [(apply str name-chars) remaining]
      (recur (conj name-chars (first remaining))
             (rest remaining)))))

(defn- tokenize-recursively [my-sequence current-string vector in-quote]
  (if (empty? my-sequence)
    (if (empty? current-string)
      vector
      (conj vector [:text current-string]))
    (let [current-char (first my-sequence)
          next-char (first (next my-sequence))]
      (cond
        (and (or (= current-char \`) (= current-char \'))
             (or (nil? in-quote) (= current-char in-quote)))
        (recur (rest my-sequence)
               (str current-string current-char)
               vector
               (if in-quote nil current-char))

        in-quote
        (recur (rest my-sequence) (str current-string current-char) vector in-quote)

        (and (= current-char \-) (= next-char \-))
        (let [remaining (skip-comment (rest my-sequence))]
          (recur remaining current-string vector nil))

        (and (= current-char \:) (= next-char \:))
        (recur (rest (rest my-sequence)) (str current-string "::") vector nil)

        (and (= current-char \:) (not= next-char \:))
        (let [vector-with-text (if (empty? current-string)
                                 vector
                                 (conj vector [:text current-string]))
              [var-name remaining] (collect-variable-name (rest my-sequence))]
          (recur remaining "" (conj vector-with-text [:variable (keyword var-name)]) nil))

        :else
        (recur (rest my-sequence) (str current-string current-char) vector in-quote)))))

(defn tokenize [string]
  (tokenize-recursively (seq string) "" [] nil))
