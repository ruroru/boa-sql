(defproject org.clojars.jj/next-jdbc-adapter "1.0.4-SNAPSHOT"
  :description "Boa SQL adapter for Next-JDBC"
  :url "https://github.com/ruroru/boa-sql"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url  "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojars.jj/boa-query "1.0.4-SNAPSHOT"]
                 [com.github.seancorfield/next.jdbc "1.3.1093"]
                 [org.clojure/tools.logging "1.3.1"]]
  :repl-options {:init-ns next-jdbc-adapter.core})
