(ns cljterrain.web
  (:use 
    [ring.adapter.jetty :only [run-jetty]]
    [ring.middleware.reload]
    [ring.middleware.json]
    [ring.util.response]
    [compojure.core])
  (:require
    [clojure.data.json :as json]
    [compojure.route :as route]
    [monger.core :as mg]
    [monger.collection :as mc]
    [clj-http.client :as client])
  (:import
    [com.mongodb MongoOptions ServerAddress]
    [org.bson.types ObjectId]))

;; ObjectId serializer
(defn- write-json-mongodb-objectid [x out]
  (.print out (str x)))
(extend org.bson.types.ObjectId json/JSONWriter
  {:-write write-json-mongodb-objectid})

;; Write non-escaped JSON
(defn write-str [o]
  (json/write-str o :escape-slash false))

;; Http server port
(def server-port (atom nil))

;; Connect to MongoDB
(mg/connect-via-uri! (or (System/getenv "MONGOLAB_URI") "mongodb://localhost/cljterrain"))

;; Begin MetaSim Resources

;; Endpoint resource
(defn get-entrypoint [version]
  (if (= version "1.0")
    {:status 200
     :headers {"Content-Type" "application/json"}
     :body (write-str
       {:links [{
          :rel "/rel/simulations"
          :href (format "/metasim/%s/simulations" version)
          :method "GET"}]})}
    {:status 404}))

;; Simulations resource
(defn get-simulations [version]
  (if (= version "1.0")
    {:status 200
     :headers {"Content-Type" "application/json"}
     :body (write-str
       {:simulations (mc/find-maps "simulations")
        :links [{
                 :rel "/rel/add"
                 :href (format "/metasim/%s/simulations" version)
                 :method "POST"}]})}
    {:status 404}))

;; Simulation resource
(defn add-simulation [version simulationReq requestHost]
  (if (= version "1.0")
    (let [simulationId  (ObjectId. (:simulation_id simulationReq))
          simulationUrl (:simulation_href simulationReq)
          simulationRes (client/get simulationUrl)]
      (when (= (:status simulationRes) 200)
        (let [simulation (json/read-str (:body simulationRes))
              worldTextureUri (format "/metasim/%s/simulations/%s/bodies/%s/terrain/worldtexture.jpg"
                          version (:simulation_id simulationReq) (:id simulationReq))
              worldTextureNightUri (format "/metasim/%s/simulations/%s/bodies/%s/terrain/worldtexturenight.jpg"
                          version (:simulation_id simulationReq) (:id simulationReq))
              add-terrain-links  (fn [body] (merge body
                {:links [{:rel "/rel/world_texture"
                 :href worldTextureUri
                 :method "GET"}
                {:rel "/rel/world_texture_night"
                 :href worldTextureNightUri
                 :method "GET"}]}))
              mergedSimulation (merge simulation
                                      {:bodies (map add-terrain-links (:bodies simulation))
                                       :forwardedPaths [worldTextureUri worldTextureNightUri]})]
          (mc/insert "simulations" (merge mergedSimulation { :_id simulationId}))
          {:status 201
           :headers {"Location" (format "http://%s:%d/metasim/%s/simulations/%s"
                                        requestHost @server-port version simulationId)}
           :body (write-str
             {:simulations (mc/find-maps "simulations")
              :links [{
                       :rel "/rel/add"
                       :href (format "/metasim/%s/simulations" version)
                       :method "POST"}]})})))
    {:status 404}))

(defn delete-simulation [version simulationIdStr]
  (if (= version "1.0")
    (let [simulationId (ObjectId. simulationIdStr)]
      (mc/remove "simulations" { :_id simulationId})
      {:status 203})
    {:status 404}))


;; Simulation data
(defn get-simulation-world-texture [version simulationIdStr bodyid resource]
  (if (= version "1.0")
    (let [simulationId (ObjectId. simulationIdStr)
          simulations (mc/find-maps "simulations" { :_id simulationId})]
      (if (> (count simulations) 0)
        (let [simulation (first simulations)]
          nil)
        {:status 404})
      {:status 203})
    {:status 404}))


(defroutes metasim-routes
  (GET "/metasim/:version" [version] (get-entrypoint version))
  (GET "/metasim/:version/simulations" [version] (get-simulations version))
  (POST "/metasim/:version/simulations" {{version :version} :params
                                         body :body
                                         serverName :serverName}
        (add-simulation version (json/read-str (slurp body)) serverName))
  (DELETE "/metasim/:version/simulations/:id" [version id] (delete-simulation version id))
  (GET "/metasim/:version/simulations/:id/bodies/:bodyid/terrain/:resource" [version id bodyid resource]
       (get-simulation-world-texture version id bodyid resource))
  (route/not-found "Could not find resource"))

(def app
  (wrap-reload #'metasim-routes '(cljterrain.web)))

(defn -main [port]
  (do
    (swap! server-port (fn [_] port)
    (run-jetty app {:port (Integer. port)}))))


