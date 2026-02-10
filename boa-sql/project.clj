(defproject org.clojars.jj/boa-sql "1.0.4-SNAPSHOT"
  :description "A library for frictionless SQL"
  :url "https://github.com/ruroru/boa-sql"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url  "https://www.eclipse.org/legal/epl-2.0/"}

  :profiles {:test {
                    :dependencies   [[ch.qos.logback/logback-classic "1.5.28"]
                                     [org.clojars.jj/embedded-mariadb-clj "1.2.1"]
                                     [org.mariadb.jdbc/mariadb-java-client "3.5.7"]
                                     [org.xerial/sqlite-jdbc "3.51.1.0"]
                                     [org.clojars.bigsy/pg-embedded-clj "1.0.2"]
                                     [com.h2database/h2 "2.4.240"]
                                     [org.clojars.jj/ring-http-exchange "1.3.0"]
                                     [hato "1.0.0"]
                                     [org.postgresql/postgresql "42.7.9"]
                                     [com.github.seancorfield/next.jdbc "1.3.1093"]]
                    :resource-paths ["test/resources"]}}


  :deploy-repositories [["clojars" {:url      "https://repo.clojars.org"
                                    :username :env/clojars_user
                                    :password :env/clojars_pass}]]


  :dependencies [[org.clojure/clojure "1.12.4"]
                 [org.clojars.jj/boa-query "1.0.4-SNAPSHOT"]
                 [org.clojars.jj/next-jdbc-adapter "1.0.4-SNAPSHOT"]
                 [org.clojars.jj/boa-sql "1.0.4-SNAPSHOT"]
                 [org.clojure/tools.logging "1.3.1"]]

  :plugins [[org.clojars.jj/bump "1.0.4"]
            [org.clojars.jj/strict-check "1.1.0"]
            [org.clojars.jj/lein-git-tag "1.0.0"]
            [org.clojars.jj/bump-md "1.1.0"]]
  )
