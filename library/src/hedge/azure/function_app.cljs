(ns hedge.azure.function-app
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [camel-snake-kebab.core :refer [->camelCaseString ->kebab-case-keyword]]
            [camel-snake-kebab.extras :refer [transform-keys]]
            [goog.object :as gobj]
            [cljs.core.async :refer [<!]]
            [cljs.core.async.impl.protocols :refer [ReadPort]]))


(defprotocol Codec
  (serialize [this data])
  (deserialize [this message]))

(extend-protocol Codec
  nil
    (serialize [this data] (->> data
                                (transform-keys ->camelCaseString)
                                clj->js))
    (deserialize [this message] (->> message
                                     js->clj
                                     (transform-keys ->kebab-case-keyword))))

(defn serialize-response [codec resp]
  (let [g (serialize codec resp)]
    (do
      (cond-> g
        (and (map? resp) (:headers resp)) (gobj/set "headers" (clj->js (:headers resp))))
      g)))

(defn azure-function-wrapper
  ([handler]
   (azure-function-wrapper handler nil))
  ([handler codec]
   (fn [&[context :as params]]
     (try
       (let [ok #(.done context nil (serialize-response codec %1))
             result (apply handler (map (partial deserialize codec) params))]
         (cond
          (satisfies? ReadPort result) (go (ok (<! result)))
          :else (ok result)))
       (catch js/Object e (.done context e nil))))))
