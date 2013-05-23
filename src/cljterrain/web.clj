(ns cljterrain.web
  (:use 
    [ring.adapter.jetty :only [run-jetty]]
    [ring.middleware.reload]
    [compojure.core])
  (:require
    [clojure.data.json :as json]
    [compojure.route :as route]
    [monger.core :as mg]
    [monger.collection :as mc])
  (:import
    [com.mongodb MongoOptions ServerAddress]))


(defn write-str [o]
  (json/write-str o :escape-slash false))

(mg/connect-via-uri! (or (System/getenv "MONGOLAB_URI") "mongodb://localhost/cljterrain"))

;; Endpoint resource
(defn get-entrypoint [version]
  (if (= version "1.0")
    (write-str
      {:links [{
         :rel "/rel/simulations"
         :href (format "/metasim/%s/simulations" version)
         :method "GET"}]})
    {:status 404}))

;; Simulations resource
(defn get-simulations [version]
  (if (= version "1.0")
    (write-str
      {:simulations (mc/find-maps "simulations")
       :links [{
                :rel "/rel/add"
                :href (format "/metasim/%s/simulations" version)
                :method "POST"}]})
    {:status 404}))

(defroutes metasim-routes
  (GET "/metasim/:version" [version] (get-entrypoint version))
  (GET "/metasim/:version/simulations" [version] (get-simulations version))
  (route/not-found "Could not find resource"))

(def app
  (wrap-reload #'metasim-routes '(cljterrain.web)))

(defn -main [port]
  (run-jetty app {:port (Integer. port)}))


