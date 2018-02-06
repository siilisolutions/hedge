(ns boot-hedge.common.core
  (:require 
   [clojure.pprint]
   [clojure.string :as str]
   [cheshire.core :refer [generate-stream]]))

(def SUPPORTED_HANDLERS [:api :timer])
(def AZURE_FUNCTION {:api 'azure-api-function
                     :timer 'azure-timer-function})
(def AWS_FUNCTIONS {:api 'lambda-apigw-function
                    :timer 'lambda-timer-function})

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

(defn ^:private item->handler-name
  "helper to clarify expression, extracting the handler name during calling map function."
  [item]
  (first item))

(defn one-handler-configs
  "Returns a sequence of one-handler-configs"
  [edn-config]
  (let [configs (select-keys edn-config SUPPORTED_HANDLERS)]
    (->
      (for [config-type (keys configs)]
       (map 
         (fn [item] (one-handler-config (item->handler-name item) edn-config)) 
         (get configs config-type))) 
      flatten)))

(defn ensure-valid-cron
  [{timer :timer :as all}]
  (doseq [timer (vals timer)]
    (let [[minutes hours dom month dow :as splitted] (str/split (:cron timer) #" ")]
      (when (not= 5 (count splitted)) (throw (Exception. "Bad amount of parameters in cron expression")))
      (when (and (not= "*" dom) (not= "*" dow)) (throw (Exception. "Bad cron expression")))))
      ; TODO: use spec and add checks for L, W and #
  all)
