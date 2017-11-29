(ns hedge.aws.function-app
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

(defn map->querystring [data]
  (let [querystring (cljs.nodejs/require "querystring")]
    (.stringify querystring data)))

(defn azure->ring 
  [req]
  (let [r       (js->clj req)
        query-map (goog.object/get req "queryStringParameters")
        headers (get r "headers")
        context (get r "requestContext")]
    (println "raw req" r)
    {:server-port     (-> (get headers "X-Forwarded-Port") js/parseInt)
     :server-name     (get headers "Host")
     :remote-addr     (dig headers "X-Forwarded-For" #(-> % (str/split #"," 2) first))
     :uri             (get context "path")
     :query-string    (map->querystring query-map)
     :scheme          (-> (get headers "X-Forwarded-Proto") keyword)
     :request-method  (-> (get context "httpMethod") str/lower-case keyword)
     :protocol        "HTTP/1.1"      ; TODO: figure out if this can ever be anything else
     :ssl-client-cert nil             ; TODO: we have the client cert string but not as Java type...
     :headers         headers
     :body            (get r "body")}))  ; TODO: should use codec or smth probably to handle request body type

; FIXME: change for AWS API lambda proxy
(defn ring->azure [callback codec]
  (fn [raw-resp]
    (println (str "result: " raw-resp))
    (if (string? raw-resp)
      (callback nil (clj->js {:body raw-resp}))
      (callback nil (clj->js raw-resp)))))

(defn azure-function-wrapper
  ([handler]
   (azure-function-wrapper handler nil))
  ([handler codec]
   (fn [event context callback]
     (try
       (let [ok     (ring->azure callback codec)
             logfn (.-log context)
             result (handler (azure->ring event))]

          (println (str "raw request: " (js->clj event)))
          (cond
            (satisfies? ReadPort result) (do (println "Result is channel, content pending...")
                                           (go (ok (<! result))))
            (string? result)             (ok {:body result})
            :else                        (ok result)))
       (catch :default e (callback e nil))))))
