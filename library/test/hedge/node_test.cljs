(ns hedge.node-test
  (:require [cljs.test :refer-macros [deftest is testing run-tests]]
            [goog.object :as gobj]
            [hedge.node :refer-macros [export1 export2 cli-main node-module1 node-module2]]))


(deftest export-module-test
  (testing "module export1"
    (testing "should set module.export"
      (export1 :exports)
      (is (= (gobj/get js/module "exports")
             :exports)))))


(deftest cli-main-test
  (testing "cli main macro"
    (testing "should bind cli main to a function that executes the given body"
      (cli-main :main)
      (is (= (*main-cli-fn*)
             :main)))))

(deftest node-module1-test
  (testing "node module"
    (node-module1 :module-exports)
    (testing "should have a cli function tha returns nil"
      (is (= (*main-cli-fn*)
             nil)))
    (testing "should export the given object"
      (is (= (gobj/get js/module "exports")
             :module-exports)))))
