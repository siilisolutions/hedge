(ns boot-hedge.aws.cloudformation
  (:require
   [boot.core          :as c]
   [boot-hedge.common.core :as common]
   [camel-snake-kebab.core :refer [->camelCaseString]]))

(defn output
  []
  {:HedgeAPIEndpoint {:Value {"Fn::Join" ["" ["https://"
                                              {:Ref "ServerlessRestApi"}
                                              ".execute-api."
                                              {:Ref "AWS::Region"}
                                              ".amazonaws.com/Prod"]]}
                      :Description "API endpoint base URL"}})

(defn parameters
  []
  {:FunctionDeploymentBucket
   {:Type "String"
    :Description "S3 bucket for functions-*.zip"}
   :FunctionDeploymentKey
   {:Type "String"
    :Description "S3 key for for functions-*.zip"}})

(defn codeuri
  []
  {:Bucket {:Ref "FunctionDeploymentBucket"}
   :Key {:Ref "FunctionDeploymentKey"}})

(defn api-function
  [config  name]
  {:Type "AWS::Serverless::Function"
   :Properties {:Handler (str (common/generate-cloud-name (:handler config)) "/index.handler")
                ;:Role {":Fn::GetAtt" ["LambdaExecutionRole" "Arn"]}
                :Runtime "nodejs6.10"
                :CodeUri (codeuri)
                :Events {(->camelCaseString name) {:Type "Api"
                                                   :Properties {:Path (str "/" name)
                                                                :Method "Any"}}}}})

(defn api-functions
  [config]
  (into {} (map 
            (fn [[key val]] [(->camelCaseString (str "hedge-" key)) (api-function val key)])
            config)))
; test button in API gateway is currently broken with SAM
; ref: https://github.com/luebken/hello-sam/blob/master/template.yaml#L18-L27

(defn functions
  [config]
  (merge (api-functions (or (:api config) {}))))

(defn base
  [config]
  {:AWSTemplateFormatVersion "2010-09-09"
   :Transform "AWS::Serverless-2016-10-31"
   :Description (str "Hedge generated template for ????")
   :Parameters (parameters)
   :Resources (merge (functions config))
   :Outputs (output)})

(defn write-template-file
  [config file]
  (common/serialize-json file (base config)))

(defn create-template
  [config fs]
  (let [tmp (c/tmp-dir!)
        tmp-file (clojure.java.io/file tmp "cloudformation.json")]
    (write-template-file config tmp-file)
    (-> fs (c/add-resource tmp) c/commit!)))
