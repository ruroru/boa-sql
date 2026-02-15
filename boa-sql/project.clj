(defproject org.clojars.jj/boa-sql "1.0.4-SNAPSHOT"
  :description "A library for frictionless SQL"
  :url "https://github.com/ruroru/boa-sql"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url  "https://www.eclipse.org/legal/epl-2.0/"}

  :profiles {:test {
                    :dependencies   [[ch.qos.logback/logback-classic "1.5.31"]
                                     [org.clojars.jj/embedded-mariadb-clj "1.2.1"]
                                     [org.mariadb.jdbc/mariadb-java-client "3.5.7"]
                                     [org.xerial/sqlite-jdbc "3.51.2.0"]
                                     [org.clojars.bigsy/pg-embedded-clj "1.0.2"]
                                     [com.h2database/h2 "2.4.240"]
                                     [org.clojars.jj/ring-http-exchange "1.4.1"]
                                     [hato "1.0.0"]

                                     [org.postgresql/postgresql "42.7.10"]
                                     [com.github.seancorfield/next.jdbc "1.3.1093"]]
                    :resource-paths ["test/resources"]}}


  :deploy-repositories [["clojars" {:url      "https://repo.clojars.org"
                                    :username :env/clojars_user
                                    :password :env/boa_sql_clojars_pass}]]


  :dependencies [[org.clojure/clojure "1.12.4"]
                 [org.clojars.jj/boa-query "1.0.4-SNAPSHOT"]
                 [org.clojars.jj/next-jdbc-adapter "1.0.8-SNAPSHOT"]
                 [io.vertx/vertx-core "5.0.7"]
                 [io.vertx/vertx-pg-client "4.5.24"]
                 [org.clojure/tools.logging "1.3.1"]]

  :plugins [[org.clojars.jj/bump "1.0.4"]
            [org.clojars.jj/strict-check "1.1.0"]
            [org.clojars.jj/lein-git-tag "1.0.0"]
            [org.clojars.jj/bump-md "1.1.0"]]
  )
