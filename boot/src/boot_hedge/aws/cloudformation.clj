(ns boot-hedge.aws.cloudformation
  (:require
   [boot-hedge.common.core :as common]
   [camel-snake-kebab.core :refer [->camelCaseString ->PascalCase]]
   [clojure.string :as str]))

; TODO: test button in API gateway is currently broken with SAM
; ref: https://github.com/luebken/hello-sam/blob/master/template.yaml#L18-L27

(declare sns-topic-name)

(defn fnsub
  "Creates data structure for Fn::Sub
   s input string
   values map for replacements

   e.g. (fnsub \"www.${Domain}\" { :Domain \"asdf\" } ]}
  "
  [s values]
  {"Fn::Sub" [s values]})

(defn output
  [config]
  (if (:api config)
    {:HedgeAPIEndpoint {:Value {"Fn::Join" ["" ["https://"
                                                {:Ref "ServerlessRestApi"}
                                                ".execute-api."
                                                {:Ref "AWS::Region"}
                                                ".amazonaws.com/Prod"]]}
                        :Description "API endpoint base URL"}}
    {}))

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
    :Description "S3 key for for functions-*.zip"}
   :PrettyDeploymentName
   {:Type "String"
    :Description "Name of the deployment"}
   :DeploymentName  ; "hedgeStackName"
   {:Type "String"
    :Description "Name of the deployment (compatible with logical names)"}})

(defn api-event
  [name]
  {:Type "Api"
   :Properties {:Path (str "/" name)
                :Method "Any"}})

(defn hedge-timer->aws 
  [expression]
  (let [[minutes hours dom month dow :as splitted] (str/split expression #" ")]
    (case [dom dow]
      ["*" "*"] (str "cron(" minutes " " hours " " dom " " month " " "?" " *)") ; AWS has weird rules
      :default (str "cron(" expression " *)"))))

(defn timer-event
  [config]
  {:Type "Schedule"
   :Properties {:Schedule (hedge-timer->aws (:cron config))}})

(defn queue-event-sns
  [name config]
  {:Type "SNS"
   :Properties {:Topic {:Ref (sns-topic-name name)}}})

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

(defn queue-function-sns
  [config  name]
  {:Type "AWS::Serverless::Function"
   :Properties {:Handler (str (common/generate-cloud-name (:handler config)) "/index.handler")
                :Events {(->camelCaseString name) (queue-event-sns name config)}
                :DeadLetterQueue {:Type "SNS"
                                  :TargetArn {:Ref (sns-topic-name name)}}}})

(defn queue-sns-topic
  [config  name]
  {:Type "AWS::SNS::Topic"
   :Properties {:DisplayName (fnsub "${v1} topic for ${v2}" {:v1 name
                                                             :v2 {:Ref "PrettyDeploymentName"}})
                :TopicName (fnsub "${v1}-${v2}" {:v1 {:Ref "PrettyDeploymentName"}
                                                 :v2 name})}})

(defn api-functions
  [config]
  (into {} (map 
            (fn [[key val]] [(->PascalCase (str "hedge-" key)) (api-function val key)])
            config)))

(defn timer-functions
  [config]
  (into {} (map 
            (fn [[key val]] [(->PascalCase (str "hedge-" key)) (timer-function val key)])
            config)))

(defn queue-functions-sns
  [config]
  (into {} (map 
            (fn [[key val]] [(->PascalCase (str "hedge-" key)) (queue-function-sns val key)])
            config)))

(defn sns-topic-name
  [name]
  (->PascalCase name))

(defn sns-topics
  [config]
  (into {} (map 
            (fn [[key val]] [(sns-topic-name key) (queue-sns-topic val (sns-topic-name key))])
            config)))

(defn dlq-topic
  [config]
  (if (or (nil? config) (empty? config))
    {}
    {:DLQ {:Type "AWS::SNS::Topic"
           :Properties {:DisplayName (fnsub "DLQ topic for ${v}" {:v {:Ref "PrettyDeploymentName"}})
                        :TopicName (fnsub "${v}-DLQ" {:v {:Ref "PrettyDeploymentName"}})}}}))

(defn resources
  [config]
  (merge (api-functions (:api config))
         (timer-functions (:timer config))
         (dlq-topic (:queue config))
         (sns-topics (:queue config))
         (queue-functions-sns (:queue config))))

(defn base
  [config]
  {:AWSTemplateFormatVersion "2010-09-09"
   :Transform "AWS::Serverless-2016-10-31"
   :Description (str "Hedge generated template")
   :Globals (globals)
   :Parameters (parameters)
   :Resources (merge (resources config))
   :Outputs (output config)})

(defn write-template-file
  "Writes Cloudformation temptate to file using config as input"
  [config file]
  (common/serialize-json file (base config)))
