(ns hedge.node-test
  (:require [cljs.test :refer-macros [deftest is testing run-tests]]
            [goog.object :as gobj]
            [hedge.node :refer-macros [export cli-main node-module]]))


(deftest export-module-test
  (testing "module export"
    (testing "should set module.export"
      (export :exports)
      (is (= (gobj/get js/module "exports")
             :exports)))))


(deftest cli-main-test
  (testing "cli main macro"
    (testing "should bind cli main to a function that executes the given body"
      (cli-main :main)
      (is (= (*main-cli-fn*)
             :main)))))

(deftest node-module-test
  (testing "node module"
    (node-module :module-exports)
    (testing "should have a cli function tha returns nil"
      (is (= (*main-cli-fn*)
             nil)))
    (testing "should export the given object"
      (is (= (gobj/get js/module "exports")
             :module-exports)))))
