(defproject org.clojars.jj/next-jdbc-async-adapter "1.0.13-SNAPSHOT"
  :description "Boa SQL async adapter for Next-JDBC"
  :url "https://github.com/ruroru/boa-sql"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url  "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojars.jj/boa-core "1.0.13-SNAPSHOT"]
                 [org.clojars.jj/jdbc-strategy "1.0.13-SNAPSHOT"]
                 [com.github.seancorfield/next.jdbc "1.3.1118"]
                 [org.clojure/tools.logging "1.3.1"]]

  :repositories [
                 ["central" {:url "https://repo1.maven.org/maven2/" :snapshots false}]
                 ["clojars" {:url "https://repo.clojars.org/"}]]

  :deploy-repositories [["clojars" {:url      "https://repo.clojars.org"
                                    :username :env/clojars_user
                                    :password :env/clojars_pass}]]

  :repl-options {:init-ns next-jdbc-adapter.core})
