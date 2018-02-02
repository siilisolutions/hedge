(ns boot-hedge.common.core
  (:require 
   [clojure.pprint]
   [clojure.string :as str]
   [cheshire.core :refer [generate-stream]]))

(def SUPPORTED_HANDLERS [:api :timer])
(def AZURE_FUNCTION {:api 'azure-api-function
                    :timer 'azure-timer-function})

(defn print-and-return [s]
  (clojure.pprint/pprint s)
  s)

(defn now
  []
  (java.util.Date.))

(defn date->unixts
  [date]
  (-> date
      (.getTime)
      (/ 1000)
      (int)))

(defn serialize-json [f d]
  (generate-stream d (clojure.java.io/writer f)))

(defn dashed-alphanumeric [s]
  (str/replace s #"[^A-Za-z0-9\-]" "_"))

(defn generate-cloud-name [handler]
  ; hedge-test.core/hello => hedge-test_core__hello
  (str
    (dashed-alphanumeric (namespace handler))
    "__"
    (dashed-alphanumeric (name handler))))

(defn ^:private ->handler
  "Helper to create handler variables"
  [key handler value]
  {key {handler value}})

(defn ^:private handler-config 
  "Gets handler (given as string) config from hedge.edn, returns a map of type one-handler-config"
  [handler edn-config]
  (into {} 
    (map 
      (fn [key] (when-let [value (get (-> edn-config key) handler)] 
                  (->handler key handler value))) 
      (keys edn-config))))

(defn one-handler-config 
  "Returns a one-handler-cfg map"
  [handler edn-config]
  (let [cfg (handler-config handler edn-config)]
  {:type (-> cfg keys first)
   :path handler
   :function (get (first (vals cfg)) handler)}))
