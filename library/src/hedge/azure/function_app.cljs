(ns hedge.azure.function-app
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [camel-snake-kebab.core :refer [->camelCaseString ->kebab-case-keyword]]
            [camel-snake-kebab.extras :refer [transform-keys]]
            [goog.object :as gobj]
            [cljs.core.async :refer [<!]]
            [cljs.core.async.impl.protocols :refer [ReadPort]]
            [clojure.string :as str]
            [clojure.walk :as w]))


(defprotocol Codec
  (serialize [this data])
  (deserialize [this message]))

(extend-protocol Codec
  nil
    (serialize [this data] (->> data
                                (transform-keys ->camelCaseString)
                                clj->js))
    (deserialize [this message] (->> message
                                     js->clj
                                     (transform-keys ->kebab-case-keyword))))

(defn serialize-response 
  [codec resp]
  (let [g (serialize codec resp)]
    (cond-> g
        (and (map? resp) (:headers resp)) (gobj/set "headers" (clj->js (:headers resp))))
    g))

(defn dig [headers header-name clean-fn]
  (let [header (get headers header-name)]
    (if (some? header)
      (clean-fn header))))

(defn azure->ring 
  [req]
  (let [r       (js->clj req)
        headers (get r "headers")]
    {:server-port    -1
     :server-name     (get headers "Host")
     :remote-addr     (dig headers "x-forwarded-for" #(-> % (str/split #"," 2) first))
     :uri             (get headers "x-original-url")
     :query-string    (-> (get r "originalUrl") (str/split #"\?" 2) second)
     :scheme          (-> (get r "originalUrl") (str/split #":" 2) first keyword)
     :request-method  (-> (get r "method") str/lower-case keyword)
     :protocol        "HTTP/1.1"      ; TODO: figure out if this can ever be anything else
     :ssl-client-cert nil             ; TODO: we have the client cert string but not as Java type...
     :headers         headers
     :body            (get r "body")  ; TODO: should use codec or smth probably to handle request body type
  }))

(defn azure->timer
  "Converts incoming timer trigger to Hedge timer handler"
  [timer]
  (let [timer (js->clj timer)]
    {:trigger-time (str (get timer "next") \Z)}))   ; Azure times are UTC but timestamps miss TimeZone

(defn ring->azure [context codec]
  (fn [raw-resp]
    #_(.log context (str "result: " raw-resp))
    (if (string? raw-resp)
      (.done context nil (clj->js {:body raw-resp}))
      (.done context nil (clj->js raw-resp)))))

(defn azure-api-function-wrapper
  "wrapper used for http in / http out api function"
  ([handler]
   (azure-api-function-wrapper handler nil))
  ([handler codec]
   (fn [context req]
     (try
       (let [ok     (ring->azure context codec)
             logfn (.-log context)
             result (handler (into (azure->ring req) {:log logfn}))]

          #_(.log context (str "request: " (js->clj req)))
          (cond
            (satisfies? ReadPort result) (do (.log context "Result is channel, content pending...")
                                           (go (ok (<! result))))
            (string? result)             (ok {:body result})
            :else                        (ok result)))
       (catch :default e (.done context e nil))))))

(defn azure-timer-function-wrapper
  "wrapper used for timer-triggered function"
  ([handler]
    (azure-timer-function-wrapper handler nil))
  ([handler codec]
    (fn [context timer]
      (try 
        (let [ok    #(.done context nil)
              logfn (.-log context)
              result (handler (into (azure->timer timer) {:log logfn}))]
          #_(.log context (str "timer: " (js->clj timer)))
          (ok))
        (catch :default e (.done context e nil))))))