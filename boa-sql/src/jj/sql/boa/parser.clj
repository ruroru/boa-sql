(ns jj.sql.boa.parser)

(def ^:private terminators #{\space \, \) \( \newline \tab \; \return})

(defn- tokenize-recursively [chars current-string tokens in-quote]
  (if (empty? chars)
    (if (empty? current-string)
      tokens
      (conj tokens [:text current-string]))
    (let [char (first chars)
          remaining (rest chars)]
      (cond
        (and (or (= char \`) (= char \'))
             (or (nil? in-quote) (= char in-quote)))
        (recur remaining
               (str current-string char)
               tokens
               (if in-quote nil char))

        in-quote
        (recur remaining (str current-string char) tokens in-quote)

        (and (not in-quote) (= char \-) (= (first remaining) \-))
        (let [rest-after-comment (drop-while #(not= % \newline) remaining)
              rest-after-newline (if (= (first rest-after-comment) \newline)
                                   (rest rest-after-comment)
                                   rest-after-comment)]
          (recur rest-after-newline current-string tokens nil))

        (and (= char \:) (= (first remaining) \:))
        (recur (rest remaining) (str current-string "::") tokens nil)

        (and (= char \:) (not= (first remaining) \:))
        (let [tokens-with-text (if (empty? current-string)
                                 tokens
                                 (conj tokens [:text current-string]))
              [var-name rest-chars] (loop [name-chars []
                                           remaining-chars remaining]
                                      (if (or (empty? remaining-chars)
                                              (terminators (first remaining-chars))
                                              (and (= (first remaining-chars) \:)
                                                   (= (second remaining-chars) \:)))
                                        [(apply str name-chars) remaining-chars]
                                        (recur (conj name-chars (first remaining-chars))
                                               (rest remaining-chars))))
              var-token [:variable (keyword var-name)]
              new-tokens (conj tokens-with-text var-token)]
          (recur rest-chars "" new-tokens nil))

        :else
        (recur remaining (str current-string char) tokens in-quote)))))

(defn tokenize [string]
  (tokenize-recursively (seq string) "" [] nil))