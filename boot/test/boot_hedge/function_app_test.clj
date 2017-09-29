(ns boot-hedge.function-app-test
    (:require [clojure.test :refer :all]
              [boot-hedge.function-app :refer :all]))
  
(deftest function-json-test
  (testing "symbol representing the function is normalized to URL compatible form"
    (testing "allows changing path"      
      (is (= (generate-cloud-name (symbol "my-app.core" "fn-name"))
             "my-app_core__fn-name")))))