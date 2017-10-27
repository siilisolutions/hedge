  (ns hedge.azure.function-app-test
  (:require [cljs.test :refer-macros [deftest is testing async]]
            [cljs.core.async :refer [chan put!]]
            [goog.object :as gobj]
            [hedge.azure.function-app :refer [azure-function-wrapper azure->ring] :refer-macros [azure-function]]))
      

(defn azure-ctx [done-fn]
  "Generate monkeypatched Azure context.
  
   Automatically converts between JS and CLJS
   where applicable to allow writing assertions in more idiomatic manner"
  #js {:done (fn [err result] (done-fn (js->clj result)))
       :log  #(println (str "LOG :: " %))})

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
      ((azure-function-wrapper handler)
         (azure-ctx #(do (assertions %) (done)))
         azure-req))))
            
(deftest azure-function-wrapper-test-calls-done-after
  (testing-azure-wrapper "should call context done with the result given by the handler"
    (constantly "result")
    #(is (= {"body" "result"} %))))

(deftest azure-function-wrapper-test-serialization
  (testing-azure-wrapper "should serialize the object returned by handler to camel case js object"
    (constantly {:body {:test-data "Data"}})
    #(is (= (get-in % ["body" "test-data"])  ; TODO: not in camelcase yet
            "Data"))))

(deftest azure-function-wrapper-test-headers
  (testing-azure-wrapper "should not camel case header fields"
    (constantly {:headers {"Content-Type" "application/json+transit"}})
    #(is (= (get-in % ["headers" "Content-Type"])
            "application/json+transit"))))
            
(deftest azure-function-wrapper-test-deserialization
  (testing "should deserialize the arguments to maps with dashed keywords"
    (async done
      ((azure-function-wrapper (fn [req] (is (= {:test-data {:some-field "Data"}} req))))
         (azure-ctx #())
         #js {"testData" #js {"someField" "Data"}}))))

(deftest azure-function-wrapper-test-readport
    (testing "handler returning a core async ReadPort"
      (async done
        (let [results (chan)
              azure-fn (azure-function-wrapper (constantly results))]
          (testing "should complete on receival of a message"
            (azure-fn azure-ctx #js {:done #(do (is (= "result" %)) (done))})
            (put! results "result"))))))

(deftest azure-function-test
  (testing "azure function"

    (testing "should have a cli function that returns nil"
      (azure-function (constantly :result))

      (is (= (*main-cli-fn*)
             nil)))
    (testing "should export the a wrapped handler"
      (async done
        (azure-function (fn [req] (is (= {:test-data {:some-field "Data"}} req))))
        ((gobj/get js/module "exports")
          (azure-ctx #())
          #js {"testData" #js {"someField" "Data"}})))))
