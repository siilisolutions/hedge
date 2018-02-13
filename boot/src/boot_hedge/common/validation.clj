(ns boot-hedge.common.validation
  (:require [clojure.spec.alpha :as spec]))

; no whitespace matcher
(spec/def ::no-ws (spec/and 
                    string? 
                    #(not (nil? (re-matches #"^\S*$" %)))))


; cron expression simple validation
(spec/def ::cron #(= 5 (-> (clojure.string/split % #" ") count)))

; authorization for azure
(spec/def ::authorization #{:anonymous :function :admin :system :user})

; the handler referenced
(spec/def ::handler symbol?)

; queue name referenced in trigger config
(spec/def ::queue ::no-ws)

; the azure function app environmental variable holding the connection string to referenced service in trigger, input or output
(spec/def ::connection ::no-ws)

; subscription name if trigger is azure servicebus topic
(spec/def ::subscription ::no-ws)

; accessRights (if used, defines servicebus instead of storage queue in azure)
(spec/def ::accessRights #{"Listen" "Manage"})

; name of azure cosmosdb collection
(spec/def ::collection ::no-ws)

; type of input / output
(spec/def ::type #{:queue :table :db})

; key is the reference in inputs and outputs the handler receives
(spec/def ::key ::no-ws)

; define topic true if servicebus is using topic instead of queue in output
(spec/def ::topic (spec/and
                    boolean?
                    true?))

(spec/def ::inputs (spec/coll-of (spec/keys :req-un [::name 
                                                     ::key
                                                     ::type]
                                            :opt-un [::connection
                                                     ::collection])))

(spec/def ::outputs (spec/coll-of (spec/keys :req-un [::name 
                                                      ::key
                                                      ::type]
                                             :opt-un [::connection
                                                      ::topic
                                                      ::accessRights
                                                      ::collection])))

;;;
; different trigger / handler types
;;;

(spec/def :handler/api (spec/map-of string? (spec/keys :req-un [::handler]
                                                       :opt-un [::authorization
                                                                ::inputs
                                                                ::outputs])))

(spec/def :handler/timer (spec/map-of string? (spec/keys :req-un [::handler
                                                                  ::cron]
                                                         :opt-un [::inputs
                                                                  ::outputs])))

(spec/def :handler/queue (spec/map-of string? (spec/keys :req-un [::handler
                                                                  ::queue 
                                                                  ::connection]
                                                         :opt-un [::inputs
                                                                  ::outputs
                                                                  ::subscription
                                                                  ::accessRights])))

;;;
; The main spec for hedge.edn
;;;

(spec/def ::hedge-edn (spec/and 
                        map?
                        (spec/keys :opt-un [:handler/api
                                            :handler/queue
                                            :handler/timer])))