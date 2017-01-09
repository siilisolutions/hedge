(ns hedge.azure.function-app-test
  (:require [cljs.test :refer-macros [deftest is testing async]]
            [hedge.azure.function-app :refer [azure-function-wrapper] :refer-macros [azure-function]]))


(deftest azure-function-wrapper-test
  (testing "azure-function-wrapper"
    (testing "should call context done with the result given by the handler"
      (async done
        ((azure-function-wrapper (constantly "result")) #js {:done #(do (is (= "result" %1)) (done))})))
    (testing "should serialize the object returned by handler to camel case js object"
      (async done
             ((azure-function-wrapper (constantly {:test-data "Data"}))
              #js {:done #(do
                            (is
                             (= (.-testData %1) "Data"))
                            (done))})))
    (testing "should deserialize the arguments to maps with dashed keywords"
      (async done
             ((azure-function-wrapper (fn [& args] (is (= [{:done done} {:test-data {:some-field "Data"}}] args))))
              #js {:done done}
              #js {"testData" #js {"someField" "Data"}})))))


(deftest azure-function-test
  (testing "azure function"

    (testing "should have a cli function that returns nil"
      (azure-function (constantly :result))

      (is (= (*main-cli-fn*)
             nil)))
    (testing "should export the a wrapped handler"
      (async done
        (azure-function (fn [& args] (is (= [{:done done} {:test-data {:some-field "Data"}}] args))))
        ((aget js/module "exports")
         #js {:done done}
              #js {"testData" #js {"someField" "Data"}})))))



