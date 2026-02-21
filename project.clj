(defproject org.clojars.jj/boa-sql-parent "1.0.4-SNAPSHOT"
  :description "A library for frictionless SQL"
  :url "https://github.com/ruroru/boa-sql"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url  "https://www.eclipse.org/legal/epl-2.0/"}

  :deploy-repositories [["clojars" {:url      "https://repo.clojars.org"
                                    :username :env/clojars_user
                                    :password :env/boa_parent_clojars_pass}]]
  :dependencies [[org.clojure/clojure "1.12.4"]]

  :sub [
        "boa-query"
        "boa-async-query"
        "next-jdbc-adapter"
        "next-jdbc-async-adapter"
        "boa-sql"
        "async-boa-sql"
        ]

  :repositories [["local" {:url       "file:///home/user/.m2/repository"
                           :snapshots true}]
                 ["central" {:url "https://repo1.maven.org/maven2/" :snapshots false}]
                 ["clojars" {:url "https://repo.clojars.org/"}]]

  :plugins [[org.clojars.jj/bump "1.0.4"]
            [org.clojars.jj/strict-check "1.1.0"]
            [lein-sub "0.3.0"]
            [org.clojars.jj/lein-git-tag "1.0.0"]
            [org.clojars.jj/bump-md "1.1.0"]]
  )
