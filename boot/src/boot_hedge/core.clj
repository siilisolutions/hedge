(ns boot-hedge.core
  (:require [boot.core          :as c]))

(def SUPPORTED_CLOUDS [:aws :azure])

(c/deftask hedge-help
  "WARNING: run this task to get more info" []
  (println "Check example projects to find out how to import Hedge tasks")
  identity)

(c/deftask help
  "Hedge Help"
  []
  (println "\n\nTasks marked with ** are main tasks for end-users. You can find out more of a function by example:")
  (println "$> boot azure/deploy --help")
  (println ".. Would display help of function"))

(defn init! [& {:keys [clouds]
                :or {clouds SUPPORTED_CLOUDS}}]
  (ns-unmap 'boot-hedge.core 'hedge-help)
  (doseq [cloud clouds]
    (case cloud
      :aws (require '[boot-hedge.aws.core :as aws])
      :azure (require '[boot-hedge.azure.core :as azure]))))
