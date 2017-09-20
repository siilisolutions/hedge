(ns ring-azure
  (:require [concordion :refer [concordion-fixture]]
            [zprint.core :as zp]
            [backtick :refer [quote-fn]]))


(defmacro code-block [n & body]
  `(def ~n ~(quote-fn identity body)))


(code-block simple-boot-conf
            (set-env!
             :source-paths #{"src"}
             :resource-paths  #{"resources"}
             :dependencies '[[adzerk/boot-cljs "1.7.228-2" :scope "test"]
                             [siili/boot-hedge "0.0.1-SNAPSHOT" :scope "test"]
                             [siili/hedge "0.0.1-SNAPSHOT"]]))

(def handler-ns-name 'hedge-handler)

(def handler-func-name 'hello)

(code-block  basic-handler
             (ns ~handler-ns-name)
             (defn ~handler-func-name [req]
               {:status  200
                :headers {"Content-Type" "text/html"}
                :body    "hello HTTP!"}))

(code-block hello-conf
            {:api {"hello" {:handler ~(symbol (str handler-ns-name) (str handler-func-name))}}})


(defn code-print [ss]
  (with-out-str
    (println)
    (doseq [s ss]
      (zp/zprint s)
      (println))))

(concordion-fixture basic-ring-handler-azure
                    (testi [koodii]
                           (println koodii)
                           "HIIOHOI")
                    (handlerNsName [] (str handler-ns-name))
                    (simpleBoot [] (code-print simple-boot-conf))
                    (basicHandlerNS [] (code-print basic-handler))
                    (basicHelloConf [] (code-print hello-conf))
                    (deploy [bc] (println bc))
                    (getresource [url]))
