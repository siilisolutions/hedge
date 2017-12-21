(ns hedge-acceptance.ring-azure
  (:require [hedge-acceptance.util.concordion :refer [concordion-fixture]]
            [hedge-acceptance.util.sample-codes :refer :all]
            [me.raynes.conch :refer [run-command] :as sh]
            [clojure.java.io :as io]
            [clojure.string :as s]
            [org.httpkit.client :as http])
  (:import [java.nio.file Paths Files LinkOption]
           [java.nio.file.attribute FileAttribute]))

; java varargs trickery
(def no-file-attrs (into-array FileAttribute []))
(def no-link-opts (into-array LinkOption []))
(def no-strs (into-array java.lang.String []))

(defn- create-file
  [dir filename content]
  (let [target     (.resolve dir filename)
        parent-dir (.getParent target)]
    (when (not (Files/exists parent-dir no-link-opts))
      (Files/createDirectories parent-dir no-file-attrs))
    (spit (.toFile target) content)))

; the suite is called twice (init+exec?) by Concordion so not doing 
; memoization would produce an unused empty directory
(def tmp-dir (memoize (fn [] (Files/createTempDirectory (Paths/get "/tmp" no-strs) "hedge-acceptance" no-file-attrs))))

(defn delete-recursively 
  [fname]
  (let [walk-dir (fn [func f]
                (when (.isDirectory f)
                  (doseq [f2 (.listFiles f)]
                    (func func f2)))
                (io/delete-file f))]
    (walk-dir walk-dir (io/file fname))))

(defmacro with-files [files & body]
  `(do
     (doseq [[path# contents#] ~files] (create-file (tmp-dir) path# contents#))
     (let [result# (do ~@body)]
       ;(when (Files/exists tmp-dir no-link-opts)
       ;  (delete-recursively (.toFile tmp-dir)))
       result#)))

(defn cmd 
  [command]
  (let [[command & args] (s/split (s/trim command)  #"\s+")
        result (run-command command args {:dir          (str (tmp-dir)) 
                                          :redirect-err true
                                          :seq          true
                                          :throw        false
                                          :verbose      true})]
    (when (not= 0 @(:exit-code result))
      (do
        (clojure.pprint/pprint result)
        (println "Command failed with return value: " @(:exit-code result))
        (println "Output of failed command:")
        (doseq [x (:stdout result)] (println x))
        (throw (Exception. "failure with return value"))))))

(defn handler-file-name
  []
  (str 
    "src/"
    (-> handler-ns-name (s/replace "-" "_") (s/replace "." "/"))
    ".cljs"))

(defn deploy-project
  [bc]
  (with-files [["build.boot"           (code-print simple-boot-conf)]
               ["boot.properties"      boot-props]
               [(handler-file-name)    (code-print basic-handler)]
               ["resources/hedge.edn"  (code-print hello-conf)]]
    (cmd bc)))

(defn fetch
  [method url]
  (let [{:keys [status headers body error] :as resp} @(method url)]
    (if error
      (do (println resp) (str "HTTP request failed, see logs for more information. " error))
      body)))

(concordion-fixture basic-ring-handler-azure
                    (handlerNsName [] (str handler-ns-name))
                    (simpleBoot [] (code-print simple-boot-conf))
                    (bootProps [] (str boot-props))
                    (basicHandlerNS [] (code-print basic-handler))
                    (basicHelloConf [] (code-print hello-conf))
                    (prepare [bc] (cmd bc))
                    (deploy [bc] (deploy-project bc))
                    (getresource [url] (fetch http/get url)))
