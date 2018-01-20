(ns boot-hedge.common.core
  (:require 
   [clojure.pprint]
   [clojure.string :as str]
   [cheshire.core :refer [generate-stream]]))

(defn print-and-return [s]
  (clojure.pprint/pprint s)
  s)

(defn serialize-json [f d]
  (generate-stream d (clojure.java.io/writer f)))

(defn dashed-alphanumeric [s]
  (str/replace s #"[^A-Za-z0-9\-]" "_"))

(defn generate-cloud-name [handler]
  ; hedge-test.core/hello => hedge-test_core__hello
  (str
    (dashed-alphanumeric (namespace handler))
    "__"
    (dashed-alphanumeric (name handler))))
