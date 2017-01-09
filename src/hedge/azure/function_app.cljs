(ns hedge.azure.function-app
  (:require [camel-snake-kebab.core :refer [->camelCaseString ->kebab-case-keyword]]
            [camel-snake-kebab.extras :refer [transform-keys]]))


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

(defn azure-function-wrapper
  ([handler]
   (azure-function-wrapper handler nil))
  ([handler codec]
   (fn [&[context :as params]]
     (try
       (.done context (serialize codec
                                 (apply handler (map (partial deserialize codec) params))))
       (catch js/Object e (.done context e nil))))))
