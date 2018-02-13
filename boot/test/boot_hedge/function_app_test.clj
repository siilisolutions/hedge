(ns boot-hedge.function-app-test
    (:require [clojure.test :refer :all]
              [boot-hedge.azure.function-app :refer :all]
              [boot-hedge.common-test :refer [hedge-edn-input inputs outputs]]))
  
(def api-handler-config1
  {:type :api
  :function {:handler `my_cool_function.core/crunch-my-data :authorization :anonymous}
  :path "api1"})

(def api-handler-config2
  {:type :api
  :function {:handler `my_cool_function.core/crunch-my-data :authorization :function}
  :path "api2"})

(def api-handler-config3
  {:type :api
  :function {:handler `my_cool_function.core/crunch-my-data}
  :path "api3"})

(def timer-handler-config
 {:type :timer
  :function {:handler `my_cool_function.core/timer-handler-broken :cron "*/10 * * * *"}
  :path "timer2"})

(def queue-handler-config 
  {:type :queue
  :function {:handler `my_cool_function.core/queuehandler
             :queue "myqueue"
             :connection "AzureWebJobsDashboard"}
  :path "queue1"})

(def topic-queue-handler-config 
  {:type :queue
  :function {:handler `my_cool_function.core/queuehandler
             :queue "mytopic"
             :subscription "subscription"
             :accessRights "Manage"
             :connection "AzureWebJobsDashboard"}
  :path "queue1"})

(def sb-queue-handler-config 
  {:type :queue
  :function {:handler `my_cool_function.core/queuehandler
              :queue "myqueue"
              :accessRights "Manage"
              :connection "AzureWebJobsDashboard"}
  :path "queue1"})

(def http-trigger-multiple-inputs-outputs-config
  {:type :api
  :function {:handler `my_cool_function.core/crunch-my-data
             :outputs outputs
             :inputs inputs}
  :path "api3"})

(def timer-trigger-multiple-inputs-outputs-config
  {:type :timer
   :function {:handler `my_cool_function.core/crunch-my-data
              :cron "* * * * *"
              :outputs outputs
              :inputs inputs}
  :path "api3"})

(def stqueue-trigger-multiple-inputs-outputs-config
  {:type :queue
   :function {:handler `my_cool_function.core/queuehandler
              :queue "myqueue"
              :connection "AzureWebJobsDashboard"
              :outputs outputs
              :inputs inputs}
   :path "api3"})

(def sbqueue-trigger-multiple-inputs-outputs-config
  {:type :queue
    :function {:handler `my_cool_function.core/queuehandler
               :queue "myqueue"
               :accessRights "Manage"
               :connection "AzureWebJobsDashboard"
               :outputs outputs
               :inputs inputs}
    :path "api3"})

(def sbtopic-trigger-multiple-inputs-outputs-config
  {:type :queue
    :function {:handler `my_cool_function.core/queuehandler
                :queue "myqueue"
                :accessRights "Manage"
                :subscription "subscription"
                :connection "AzureWebJobsDashboard"
                :outputs outputs
                :inputs inputs}
    :path "api3"})

(deftest function-json-test
  (testing "symbol representing the function is normalized to URL compatible form"
    (testing "allows changing path"      
      (is (= (generate-cloud-name (symbol "my-app.core" "fn-name"))
             "my-app_core__fn-name")))))

(deftest function-type->function-json-test
  (testing "Testing function.json generation for Azure"
    (testing "Makes an app possible to hook up with Azure serverless runtime"
      (let [json-api1    (function-type->function-json api-handler-config1)
            json-api2    (function-type->function-json api-handler-config2)
            json-api3    (function-type->function-json api-handler-config3)
            json-timer   (function-type->function-json timer-handler-config)
            json-queue   (function-type->function-json queue-handler-config)
            json-topic   (function-type->function-json topic-queue-handler-config)
            json-sbqueue (function-type->function-json sb-queue-handler-config)]
        
        (is (= (-> json-api1 :bindings first :type) "httpTrigger"))
        (is (= (-> json-api1 :bindings first :route) (-> "api1")))
        (is (= (-> json-api1 :bindings first :authLevel) "anonymous"))

        (is (= (-> json-api2 :bindings first :route) (-> "api2")))
        (is (= (-> json-api2 :bindings first :authLevel) "function"))
     
        ; if authentication was omitted
        (is (= (-> json-api3 :bindings first :route) (-> "api3")))
        (is (= (-> json-api3 :bindings first :authLevel) "anonymous"))

        (is (= (-> json-timer :bindings first :type) "timerTrigger"))
        (is (= (-> json-timer :bindings first :schedule) "0 */10 * * * *"))
        
        (is (= (-> json-queue :bindings first :type) "queueTrigger"))
        (is (= (-> json-queue :bindings first :queueName) "myqueue"))

        (is (= (-> json-topic :bindings first :type) "serviceBusTrigger"))
        (is (= (-> json-topic :bindings first :topicName) "mytopic"))
        (is (-> json-topic :bindings first :accessRights))
        (is (-> json-topic :bindings first :subscriptionName))

        (is (= (-> json-sbqueue :bindings first :type) "serviceBusTrigger"))
        (is (= (-> json-sbqueue :bindings first :queueName) "myqueue"))
        (is (-> json-sbqueue :bindings first :accessRights))
        (is (nil? (-> json-sbqueue :bindings first :subscriptionName)))))))

(deftest functions-with-inputs-outputs-test
  (testing "Function.json generation for Azure with function inputs and outputs"
    (let [api-cfg      (function-type->function-json http-trigger-multiple-inputs-outputs-config)
          timer-cfg    (function-type->function-json timer-trigger-multiple-inputs-outputs-config)
          st-queue-cfg (function-type->function-json stqueue-trigger-multiple-inputs-outputs-config)
          sb-queue-cfg (function-type->function-json sbqueue-trigger-multiple-inputs-outputs-config)
          sb-topic-cfg (function-type->function-json sbtopic-trigger-multiple-inputs-outputs-config)]

      (is (= 1 (-> (filter (fn [x] (= "httpTrigger" (-> x :type))) (-> api-cfg :bindings)) count)))
      (is (= 1 (-> (filter (fn [x] (= "timerTrigger" (-> x :type))) (-> timer-cfg :bindings)) count)))
      (is (= 1 (-> (filter (fn [x] (= "queueTrigger" (-> x :type))) (-> st-queue-cfg :bindings)) count)))
      (is (= 1 (-> (filter (fn [x] (= "serviceBusTrigger" (-> x :type))) (-> sb-queue-cfg :bindings)) count)))
      (is (= 1 (-> (filter (fn [x] (= "serviceBusTrigger" (-> x :type))) (-> sb-topic-cfg :bindings)) count)))

      ; 6 because api-cfg has http out output binding in excess to configured outputs
      (is (= 6 (-> (filter (fn [x] (= "out" (-> x :direction))) (-> api-cfg :bindings)) count)))
      (is (= 5 (-> (filter (fn [x] (= "out" (-> x :direction))) (-> timer-cfg :bindings)) count)))
      (is (= 5 (-> (filter (fn [x] (= "out" (-> x :direction))) (-> st-queue-cfg :bindings)) count)))
      (is (= 5 (-> (filter (fn [x] (= "out" (-> x :direction))) (-> sb-queue-cfg :bindings)) count)))
      (is (= 5 (-> (filter (fn [x] (= "out" (-> x :direction))) (-> sb-topic-cfg :bindings)) count)))
      
      ; 3 because trigger is also an input
      (is (= 3 (-> (filter (fn [x] (= "in" (-> x :direction))) (-> api-cfg :bindings)) count)))
      (is (= 3 (-> (filter (fn [x] (= "in" (-> x :direction))) (-> timer-cfg :bindings)) count)))
      (is (= 3 (-> (filter (fn [x] (= "in" (-> x :direction))) (-> st-queue-cfg :bindings)) count)))
      (is (= 3 (-> (filter (fn [x] (= "in" (-> x :direction))) (-> sb-queue-cfg :bindings)) count)))
      (is (= 3 (-> (filter (fn [x] (= "in" (-> x :direction))) (-> sb-topic-cfg :bindings)) count))))))

(deftest generate-files-precondition-fail-test
  (testing "generate-files precondition failure (invalid hedge.edn) should stop execution"
    (is (thrown? java.lang.AssertionError 
                 ;when
                 (generate-files {:api {:handler 123}} nil)))))

(deftest generate-files-precondition-success-test
  (testing "generate-files precondition success (valid hedge.edn) should not throw spec validation exception"
    ; because no fileset mock is passed processing will fail on missing protocol implementation
    (is (thrown? java.lang.IllegalArgumentException 
                 ;when
                 (generate-files hedge-edn-input nil)))))