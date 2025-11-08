(defproject org.clojars.jj/boa-sql "1.0.1-SNAPSHOT"
  :description "A library for frictionless SQL"
  :url "https://github.com/ruroru/boa-sql"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url  "https://www.eclipse.org/legal/epl-2.0/"}

  :profiles {:test {
                    :dependencies   [[ch.qos.logback/logback-classic "1.5.19"]
                                     [org.clojars.jj/embedded-mariadb-clj "1.1.1"]
                                     [org.mariadb.jdbc/mariadb-java-client "3.5.6"]
                                     [org.xerial/sqlite-jdbc "3.51.0.0"]
                                     [org.clojars.bigsy/pg-embedded-clj "1.0.2"]
                                     [com.h2database/h2 "2.4.240"]
                                     [org.postgresql/postgresql "42.7.8"]
                                     [com.github.seancorfield/next.jdbc "1.3.1070"]]
                    :resource-paths ["test/resources"]}}


  :deploy-repositories [["clojars" {:url      "https://repo.clojars.org"
                                    :username :env/clojars_user
                                    :password :env/clojars_pass}]]


  :dependencies [[org.clojure/clojure "1.12.3"]
                 [org.clojure/tools.logging "1.3.0"]]

  :plugins [[org.clojars.jj/bump "1.0.4"]
            [org.clojars.jj/strict-check "1.1.0"]
            [org.clojars.jj/bump-md "1.1.0"]]
  )
