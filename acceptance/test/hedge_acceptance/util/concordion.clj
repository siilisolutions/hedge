(ns hedge-acceptance.util.concordion
  (:require [camel-snake-kebab.core :refer [->PascalCaseSymbol]]))


(defn get-signature [[mname args]]
  (if (= args [])
    [mname [] Object]
    [mname [Object] Object]))

(defmacro gen-fixture-class [prefix n fs]
  `(gen-class :name ~(with-meta (->PascalCaseSymbol n) `{org.junit.runner.RunWith  org.concordion.integration.junit4.ConcordionRunner})
           :prefix ~prefix
           :methods [ ~@(map get-signature fs)]))

(defn gen-fixture-method [prefix]
  (fn [[n args & body]]
    (if (= args [])
     `(defn ~(symbol (str prefix n)) [~'_] ~@body)
     `(defn ~(symbol (str prefix n)) [~'_ ~(first args)] ~@body))))

(defn gen-fixture-funcs [prefix fs]
  (map (gen-fixture-method prefix) fs))


(defmacro concordion-fixture [n & fs]
  (let [prefix (str (gensym "concordion") "-")]
    `(do (hedge-acceptance.util.concordion/gen-fixture-class ~prefix ~n ~fs)
         ~@(hedge-acceptance.util.concordion/gen-fixture-funcs prefix fs))))
