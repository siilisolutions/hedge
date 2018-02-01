(ns boot-hedge.core
  (:require [boot.core          :as c]))

(def SUPPORTED_CLOUDS [:aws :azure])
(def SUPPORTED_HANDLERS [:api :timer])
(def AZURE_FUNCTION {:api 'azure-api-function
                     :timer 'azure-timer-function})

(c/deftask hedge-help
  "WARNING: run this task to get more info" []
  (println "Check example projects to find out how to import Hedge tasks")
  identity)

(c/deftask help
  "Displays some help"
  []
  (println "Tasks marked with ** are main tasks for end-users"))

(defn init! [& {:keys [clouds]
                :or {clouds SUPPORTED_CLOUDS}}]
  (ns-unmap 'boot-hedge.core 'hedge-help)
  (doseq [cloud clouds]
    (case cloud
      :aws (require '[boot-hedge.aws.core :as aws])
      :azure (require '[boot-hedge.azure.core :as azure]))))
