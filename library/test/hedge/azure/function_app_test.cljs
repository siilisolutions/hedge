  (ns hedge.azure.function-app-test
  (:require [cljs.test :refer-macros [deftest is testing async]]
            [cljs.core.async :refer [chan put!]]
            [hedge.azure.function-app :refer [azure-function-wrapper] :refer-macros [azure-function]]))


(deftest azure-function-wrapper-test
  (testing "azure-function-wrapper"
    (testing "should call context done with the result given by the handler"
      (async done
        ((azure-function-wrapper (constantly "result")) #js {:done #(do (is (= "async-result" %2)) (done))})))
    (testing "should serialize the object returned by handler to camel case js object"
      (async done
             ((azure-function-wrapper (constantly {:test-data "Data"}))
              #js {:done #(do
                            (is
                             (= (.-testData %2) "Data"))
                            (done))})))
    (testing "should not camel case header fields"
      (async done
             ((azure-function-wrapper (constantly {:headers {:Content-Type "application/json+transit"}}))
              #js {:done #(do
                            (is
                             (= (aget (.-headers %2) "Content-Type") "application/json+transit"))
                            (done))})))
    (testing "should deserialize the arguments to maps with dashed keywords"
      (async done
             ((azure-function-wrapper (fn [& args] (is (= [{:done done} {:test-data {:some-field "Data"}}] args))))
              #js {:done done}
              #js {"testData" #js {"someField" "Data"}})))
    (testing "handler returning a core async ReadPort"
      (async done
        (let [results (chan)
              azure-fn (azure-function-wrapper (constantly results))]
          (testing "should complete on receival of a message"
            (azure-fn #js {:done #(do (is (= "result" %2)) (done))})
            (put! results "result")))))))


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



