(defproject org.clojars.jj/boa-resolver "1.0.11"
            :description "FIXME: write description"
            :url "http://example.com/FIXME"
            :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
                      :url  "https://www.eclipse.org/legal/epl-2.0/"}

            :deploy-repositories [["clojars" {:url      "https://repo.clojars.org"
                                              :username :env/clojars_user
                                              :password :env/boa_sql_clojars_pass}]]


            :dependencies [[org.clojure/clojure "1.12.4"]]
            :repl-options {:init-ns boa-resolver.core})
