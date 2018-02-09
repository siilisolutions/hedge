  (ns hedge.azure.function-app-test
  (:require [cljs.test :refer-macros [deftest is testing async use-fixtures]]
            [cljs.core.async :refer [chan put!]]
            [goog.object :as gobj]
            [taoensso.timbre :as timbre]
            [hedge.azure.function-app :refer [azure-api-function-wrapper 
                                              azure->ring
                                              bindings->inputs
                                              outputs->bindings] 
                                      :refer-macros [azure-api-function]]
            [hedge.azure.common :refer [azure-context-logger-mock]]
            [hedge.common :refer [outputs->atoms]]))

(def fixture-once
  {:before (fn [] (timbre/merge-config! {:level :trace}))
   :after (fn [] (timbre/merge-config! {:level :debug}))})

(use-fixtures :once fixture-once)

(defn azure-ctx [done-fn]
  "Generate monkeypatched Azure context.
  
   Automatically converts between JS and CLJS
   where applicable to allow writing assertions in more idiomatic manner"
  #js {:done (fn [err result] (done-fn (js->clj result)))
       :log  azure-context-logger-mock
       :bindings #js {:in1 "input1value"
                      :out1 nil}})

(def azure-req (clj->js {"headers" {"x-forwarded-for" "127.0.0.1:1234"
                                    "x-original-url"  "/api?query=params"
                                    "Hello"           "World"
                                    "Host"            "appname.azurewebsites.net"}
                          "originalUrl" "https://appname.azurewebsites.net/api?query=params"
                          "method" "POST"}))

(deftest azure->ring_mapping
  (testing "converts Azure context to Ring request - minimal values"
    (is (= {:server-port     -1
            :server-name     "appname.azurewebsites.net"
            :remote-addr     "127.0.0.1:1234"
            :uri             "/api?query=params"
            :query-string    "query=params"
            :scheme          :https
            :request-method  :post
            :protocol        "HTTP/1.1"
            :ssl-client-cert nil
            :headers         {"x-forwarded-for" "127.0.0.1:1234"
                              "x-original-url"  "/api?query=params"
                              "Hello"           "World"
                              "Host"            "appname.azurewebsites.net"}
            :body            nil}
            (azure->ring azure-req)))))

(defn testing-azure-wrapper
  [m handler assertions]
  (testing m
    (async done
      ((azure-api-function-wrapper handler 
                                   :inputs [{:type :table
                                   :key "in1"
                                   :name "discussionStateTable"
                                   :connection "AzureWebJobsStorage"}]
                                   :outputs [{:type :queue
                                   :key "out1"
                                   :name "hedge-output-queue1"
                                   :connection "AzureWebJobsStorage"}])
         (azure-ctx #(do (assertions %) (done)))
         azure-req))))


         
(deftest azure-api-function-wrapper-test-calls-done-after
  (testing-azure-wrapper "should call context done with the result given by the handler"
    (constantly "result")
    #(is (= {"body" "result"} %))))

    
(deftest azure-api-function-wrapper-test-serialization
  (testing-azure-wrapper "should serialize the object returned by handler to camel case js object"
    (constantly {:body {:test-data "Data"}})
    #(is (= (get-in % ["body" "test-data"])  ; TODO: not in camelcase yet
            "Data"))))

(deftest azure-api-function-wrapper-test-headers
  (testing-azure-wrapper "should not camel case header fields"
    (constantly {:headers {"Content-Type" "application/json+transit"}})
    #(is (= (get-in % ["headers" "Content-Type"])
            "application/json+transit"))))

(deftest bindings->inputs-test
  (testing "if bindings can be extracted to inputs"
    (is (= "input1value" (get (get (-> (js->clj (azure-ctx #()))) "bindings") "in1")))))

(deftest outputs->bindings-test
  (testing "if outputs can be bound to bindings"
    (let [opatoms (outputs->atoms [{:type :queue
                                    :key "out1"
                                    :name "queue2"
                                    :connection "AzureWebJobsStorage"}
                                  {:type :queue
                                    :key "out2"
                                    :name "queue2"
                                    :connection "AzureWebJobsStorage"}])]
      (def mock-context (clj->js {:bindings {:out1 nil
                                             :out2 nil}}))

      ; set a value
      (reset! (-> opatoms :out1 :value) "this value was set")
      ; bind outputs to bindings
      (outputs->bindings mock-context opatoms)
      ; assert
      (is (= "this value was set" (js->clj (get (get (js->clj mock-context) "bindings") "out1")))))))

; new tests placed after azure-api-function-wrapper-test-deserialization does not work correctly
; there is something wrong with the tests below:

(deftest azure-api-function-wrapper-test-deserialization
  (testing "should deserialize the arguments to maps with dashed keywords"
    (async done
      ((azure-api-function-wrapper (fn [req] (is (= {:test-data {:some-field "Data"}} req))))
         (azure-ctx #())
         #js {"testData" #js {"someField" "Data"}}))))

(deftest azure-api-function-wrapper-test-readport
    (testing "handler returning a core async ReadPort"
      (async done
        (let [results (chan)
              azure-fn (azure-api-function-wrapper (constantly results))]
          (testing "should complete on receival of a message"
            (azure-fn azure-ctx #js {:done #(do (is (= "result" %)) (done))})
            (put! results "result"))))))

(deftest azure-api-function-test
  (testing "azure function"

    (testing "should have a cli function that returns nil"
      (azure-api-function (constantly :result))

      (is (= (*main-cli-fn*)
             nil)))
    (testing "should export the a wrapped handler"
      (async done
        (azure-api-function (fn [req] (is (= {:test-data {:some-field "Data"}} req))))
        ((gobj/get js/module "exports")
          (azure-ctx #())
          #js {"testData" #js {"someField" "Data"}})))))
