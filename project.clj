(defproject cljterrain "0.1.0-SNAPSHOT"
  :description "A Clojure-based terrain engine"
  :url "https://github.com/rSimulate/cljterrain.git"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.1"],
                 [ring/ring-core "1.1.0"]
                 [ring/ring-devel"1.1.0"]
                 [ring/ring-jetty-adapter "1.1.0"]
                 [compojure "1.1.4"]
                 [org.clojure/data.json "0.2.2"]
                 [com.novemberain/monger "1.5.0"]]
  :min-lein-version "2.0.0"
  :main cljterrain.web
  :repl-init cljterrain.web)
