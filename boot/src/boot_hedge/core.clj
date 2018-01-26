(ns boot-hedge.core
  (:require [boot.core          :as c]))

(def SUPPORTED_CLOUDS [:aws :azure])

(c/deftask hedge-help "Placeholder task, please run init! to import Hedge tasks" []
  (println "TODO: add some message here"))

(defn init! [& {:keys [clouds]
                :or {clouds SUPPORTED_CLOUDS}}]
  (ns-unmap 'boot-hedge.core 'hedge-help)
  (doseq [cloud clouds]
    (case cloud
      :aws (require '[boot-hedge.aws.core :as aws])
      :azure (require '[boot-hedge.azure.core :as azure]))))
