(ns boot-hedge.function-app-test
    (:require [clojure.test :refer :all]
              [boot-hedge.azure.function-app :refer :all]))
  
(def api-handler-config1
  {:type :api
  :function {:handler 'my_cool_function.core/crunch-my-data :authorization :anonymous}
  :path "api1"})

(def api-handler-config2
  {:type :api
  :function {:handler 'my_cool_function.core/crunch-my-data :authorization :function}
  :path "api2"})

(def api-handler-config3
  {:type :api
  :function {:handler 'my_cool_function.core/crunch-my-data}
  :path "api3"})

(def timer-handler-config
 {:type :timer
  :function {:handler 'my_cool_function.core/timer-handler-broken :cron "*/10 * * * *"}
  :path "timer2"})

(deftest function-json-test
  (testing "symbol representing the function is normalized to URL compatible form"
    (testing "allows changing path"      
      (is (= (generate-cloud-name (symbol "my-app.core" "fn-name"))
             "my-app_core__fn-name")))))

(deftest function-type->function-json-test
  (testing "Testing function.json generation for Azure"
    (testing "Makes an app possible to hook up with Azure serverless runtime"
      (let [json-api1   (function-type->function-json api-handler-config1)
            json-api2   (function-type->function-json api-handler-config2)
            json-api3   (function-type->function-json api-handler-config3)
            json-timer (function-type->function-json timer-handler-config)]
        
        (is (= (-> json-api1 :bindings first :type) "httpTrigger"))
        (is (= (-> json-api1 :bindings first :route) (-> "api1")))
        (is (= (-> json-api1 :bindings first :authLevel) "anonymous"))

        (is (= (-> json-api2 :bindings first :route) (-> "api2")))
        (is (= (-> json-api2 :bindings first :authLevel) "function"))
     
        ; if authentication was omitted
        (is (= (-> json-api3 :bindings first :route) (-> "api3")))
        (is (= (-> json-api3 :bindings first :authLevel) "anonymous"))

        (is (= (-> json-timer :bindings first :type) "timerTrigger"))
        (is (= (-> json-timer :bindings first :schedule) "0 */10 * * * *"))))))