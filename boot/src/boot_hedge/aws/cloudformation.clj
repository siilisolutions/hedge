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

(defn api-function
  [config s3-zip name]
  {:Type "AWS::Serverless::Function"
   :Properties {:Handler (str (common/generate-cloud-name (:handler config)) "/index.handler")
                ;:Role {":Fn::GetAtt" ["LambdaExecutionRole" "Arn"]}
                :Runtime "nodejs6.10"
                :CodeUri s3-zip
                :Events {(->camelCaseString name) {:Type "Api"
                                                   :Properties {:Path (str "/" name)
                                                                :Method "Any"}}}}})

(defn api-functions
  [config s3-zip]
  (into {} (map 
            (fn [[key val]] [(->camelCaseString (str "hedge-" key)) (api-function val s3-zip key)])
            config)))
; test button in API gateway is currently broken with SAM
; ref: https://github.com/luebken/hello-sam/blob/master/template.yaml#L18-L27

(defn functions
  [config s3-zip]
  (merge (api-functions (or (:api config) {}) s3-zip)))

(defn base
  [config stack-name]
  {:AWSTemplateFormatVersion "2010-09-09"
   :Transform "AWS::Serverless-2016-10-31"
   :Description (str "Hedge generated template for " stack-name)
   :Resources (merge (functions config (str "s3://hedge-" stack-name "-deploy/functions.zip")))
   :Outputs (output)})

(defn write-template-file
  [config stack-name file]
  (common/serialize-json file (base config stack-name)))

(defn create-template
  [config fs stack-name]
  (let [tmp (c/tmp-dir!)
        tmp-file (clojure.java.io/file tmp "cloudformation.json")]
    (write-template-file config stack-name tmp-file)
    (-> fs (c/add-resource tmp) c/commit!)))
