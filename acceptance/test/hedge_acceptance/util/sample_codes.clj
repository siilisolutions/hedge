(ns hedge-acceptance.util.sample-codes
  (:require [zprint.core :as zp]
            [backtick :refer [quote-fn]]))

(defmacro code-block [n & body]
  `(def ~n ~(quote-fn identity body)))

(code-block simple-boot-conf
            (set-env!
             :source-paths #{"src"}
             :resource-paths  #{"resources"}
             :dependencies '[[siili/boot-hedge "0.1.3-SNAPSHOT" :scope "test"]
                             [siili/hedge "0.1.3-SNAPSHOT"]])

            (require
            '[boot-hedge.core :refer :all]))

(def handler-ns-name 'simple-hedge-handler.core)

(def handler-func-name 'hello)

(code-block  basic-handler
             (ns ~handler-ns-name)
             (defn ~handler-func-name [req]
               {:status  200
                :headers {"Content-Type" "text/html"}
                :body    "Hello!"}))

(code-block hello-conf
            {:api {"hello" {:handler ~(symbol (str handler-ns-name) (str handler-func-name))
                            :authorization :anonymous}}})

(defn code-print [ss]
  (with-out-str
    (println)
    (doseq [s ss]
      (zp/zprint s)
      (println))))

(def boot-props
"BOOT_CLOJURE_NAME=org.clojure/clojure
BOOT_CLOJURE_VERSION=1.9.0
BOOT_VERSION=2.7.2")
