(ns boot-hedge.common.core)

(defn print-and-return [s]
  (clojure.pprint/pprint s)
  s)
