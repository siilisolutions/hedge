(ns hedge.aws.function-app
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [camel-snake-kebab.core :refer [->camelCaseString ->kebab-case-keyword]]
            [camel-snake-kebab.extras :refer [transform-keys]]
            [goog.object :as gobj]
            [cljs.core.async :refer [<!]]
            [cljs.core.async.impl.protocols :refer [ReadPort]]
            [clojure.string :as str]
            [clojure.walk :as w]
            [taoensso.timbre :as timbre
                       :refer (log  trace  debug  info  warn  error  fatal  report
                               logf tracef debugf infof warnf errorf fatalf reportf
                               spy get-env log-env)]
            [oops.core :refer [oget oset! ocall oapply ocall! oapply!
                               oget+ oset!+ ocall+ oapply+ ocall!+ oapply!+]]))

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

(defn lambda->ring
  [req]
  (let [r       (js->clj req)
        query-map (goog.object/get req "queryStringParameters")
        headers (get r "headers")
        context (get r "requestContext")]
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

; FIXME: finish, use protocols, support stream and buffer?
(defn- ringbody->awsbody
  "Convert ring body to AWS body"
  [body]
  [false (.stringify js/JSON (clj->js body))])

(defn ring->lambda [callback codec]
  (fn [raw-resp]
    (trace (str "result: " raw-resp))
    (let [response
          (if (string? raw-resp)
            {:statusCode 200 :body raw-resp}
            (let [[base64 body] (ringbody->awsbody (get raw-resp :body))
                  headers (get raw-resp :headers {})
                  status (get raw-resp :status 200)]
              {:statusCode status
               :headers headers
               :body body
               :isBase64Encoded base64}))]
      (callback nil (clj->js response)))))

(defn lambda-apigw-function-wrapper
  ([handler]
   (lambda-apigw-function-wrapper handler nil))
  ([handler codec]
   (fn [event context callback]
     (try
       (trace (str "request: " (js->clj event)))
       (let [ok     (ring->lambda callback codec)
             result (handler (lambda->ring event))]
         (cond
           (satisfies? ReadPort result) (do (info "Result is channel, content pending...")
                                            (go (ok (<! result))))
            :else                       (ok result)))
       (catch :default e (callback e nil))))))

(defn ->hedge-timer
  "converts AWS specific timer payload to Hedge handler unified format"
  [event]
  {:trigger-time (oget event "time")})

(defn ->aws-timer
  [callback]
  (fn [response]
    (when (not (nil? response)) (warn (str "Response " + response + " not being sent anywhere")))
    (callback)))

(defn lambda-timer-function-wrapper
  "wrapper for AWS timer events"
  ([handler]
   (fn [event context callback]
     (try
       (trace (str "request: " (js->clj event)))
       (let [ok     (->aws-timer callback)
             result (handler (->hedge-timer event))]
         (cond
           (satisfies? ReadPort result) (do (info "Result is channel, content pending...")
                                            (go (ok (<! result))))
            :else                       (ok result)))
       (catch :default e (callback e nil))))))
