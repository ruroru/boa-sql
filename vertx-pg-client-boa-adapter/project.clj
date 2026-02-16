(defproject org.clojars.jj/vertx-pg-client-boa-adapter "1.0.4-SNAPSHOT"
  :description "Boa SQL adapter for Next-JDBC"
  :url "https://github.com/ruroru/boa-sql"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url  "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojars.jj/boa-async-query "1.0.4-SNAPSHOT"]
                 [io.vertx/vertx-pg-client "5.0.7"]
                 [io.vertx/vertx-core "5.0.8"]
                 [org.clojure/tools.logging "1.3.1"]]

  :repositories [["local" {:url "file:///home/user/.m2/repository"
                           :snapshots true}]
                 ["central" {:url "https://repo1.maven.org/maven2/" :snapshots false}]
                 ["clojars" {:url "https://repo.clojars.org/"}]]

  :deploy-repositories [["clojars" {:url      "https://repo.clojars.org"
                                    :username :env/clojars_user
                                    :password :env/next_jdbc_adapter_clojars_pass}]]

  :repl-options {:init-ns next-jdbc-adapter.core})
