(defproject org.clojars.jj/boa-sql-parent "1.0.4-SNAPSHOT"
  :description "A library for frictionless SQL"
  :url "https://github.com/ruroru/boa-sql"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url  "https://www.eclipse.org/legal/epl-2.0/"}

  :dependencies [[org.clojars.jj/boa-sql "1.0.4-SNAPSHOT"]
                 [org.clojars.jj/boa-query "1.0.4-SNAPSHOT"]
                 [org.clojars.jj/next-jdbc-adapter "1.0.4-SNAPSHOT"]
                 ]


  :deploy-repositories [["clojars" {:url      "https://repo.clojars.org"
                                    :username :env/clojars_user
                                    :password :env/clojars_pass}]]

  :sub ["boa-query"
        "next-jdbc-adapter"
        "boa-sql"
        ]

  :plugins [[org.clojars.jj/bump "1.0.4"]
            [org.clojars.jj/strict-check "1.1.0"]
            [lein-sub "0.3.0"]
            [org.clojars.jj/lein-sub-bump "1.0.0-SNAPSHOT"]
            [org.clojars.jj/lein-git-tag "1.0.0"]
            [org.clojars.jj/bump-md "1.1.0"]]
  )
