(ns boot-hedge.aws.cloudformation
  (:require
   [boot-hedge.common.core :as common]
   [camel-snake-kebab.core :refer [->camelCaseString]]
   [clojure.string :as str]))

; TODO: test button in API gateway is currently broken with SAM
; ref: https://github.com/luebken/hello-sam/blob/master/template.yaml#L18-L27

(defn output
  [config]
  {:HedgeAPIEndpoint {:Value {"Fn::Join" ["" ["https://"
                                              {:Ref "ServerlessRestApi"}
                                              ".execute-api."
                                              {:Ref "AWS::Region"}
                                              ".amazonaws.com/Prod"]]}
                      :Description "API endpoint base URL"}})
(defn codeuri
  []
  {:Bucket {:Ref "FunctionDeploymentBucket"}
   :Key {:Ref "FunctionDeploymentKey"}})

(defn globals
  []
  {:Function {
              :Runtime "nodejs6.10"
              :CodeUri (codeuri)}})

(defn parameters
  []
  {:FunctionDeploymentBucket
   {:Type "String"
    :Description "S3 bucket for functions-*.zip"}
   :FunctionDeploymentKey
   {:Type "String"
    :Description "S3 key for for functions-*.zip"}})

(defn api-event
  [name]
  {:Type "Api"
   :Properties {:Path (str "/" name)
                :Method "Any"}})

(defn hedge-timer->aws 
  [expression]
  (let [[minutes hours dom month dow :as splitted] (str/split expression #" ")]
    (when (not= 5 (count splitted)) (throw (Exception. "Bad amount of parameters in cron expression")))
    (when (and (not= "*" dom) (not= "*" dow)) (throw (Exception. "Bad cron expression")))
    (case [dom dow]
      ["*" "*"] (str "cron(" minutes " " hours " " dom " " month " " "?" " *)") ; AWS has weird rules
      :default (str "cron(" expression " *)"))))

(defn timer-event
  [config]
  {:Type "Schedule"
   :Properties {:Schedule (hedge-timer->aws (:cron config))}})

(defn api-function
  [config  name]
  {:Type "AWS::Serverless::Function"
   :Properties {:Handler (str (common/generate-cloud-name (:handler config)) "/index.handler")
                :Events {(->camelCaseString name) (api-event name)}}})

(defn timer-function
  [config  name]
  {:Type "AWS::Serverless::Function"
   :Properties {:Handler (str (common/generate-cloud-name (:handler config)) "/index.handler")
                :Events {(->camelCaseString name) (timer-event config)}}})

(defn api-functions
  [config]
  (into {} (map 
            (fn [[key val]] [(->camelCaseString (str "hedge-" key)) (api-function val key)])
            config)))

(defn timer-functions
  [config]
  (into {} (map 
            (fn [[key val]] [(->camelCaseString (str "hedge-" key)) (timer-function val key)])
            config)))

(defn functions
  [config]
  (merge (api-functions (or (:api config) {}))
         (timer-functions (or (:timer config) {}))))

(defn base
  [config]
  {:AWSTemplateFormatVersion "2010-09-09"
   :Transform "AWS::Serverless-2016-10-31"
   :Description (str "Hedge generated template")
   :Globals (globals)
   :Parameters (parameters)
   :Resources (merge (functions config))
   :Outputs (output config)})

(defn write-template-file
  "Writes Cloudformation temptate to file using config as input"
  [config file]
  (common/serialize-json file (base config)))
