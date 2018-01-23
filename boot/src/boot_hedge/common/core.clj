(ns boot-hedge.common.core
  (:require 
   [boot.util]
   [clojure.pprint]
   [clojure.string :as str]
   [cheshire.core :refer [generate-stream]]))

(defn print-and-return [s]
  (clojure.pprint/pprint s)
  s)

(defn now
  []
  (java.util.Date.))

(defn date->unixts
  [date]
  (-> date
      (.getTime)
      (/ 1000)
      (int)))

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

(defn fail-if-false
  "Calls `boot.util/exit-error` if a map in the `values` collection includes `:val` whose value is nil.
  Will print the failure message defined in the map's `:msg` using `boot.util/fail`.
  `values` should follow the following format:
  [{:val value-to-check :msg \"Failure message\"} {:val another-value :msg \"Another failure message\"}]"
  [values]
  (some (fn [val]
          (when-not (:val val)
            (boot.util/fail "\n%s\n" (:msg val))
            (boot.util/exit-error)))
        values))
