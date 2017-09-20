(ns ring-azure
  (:require [concordion :refer [concordion-fixture]]
            [sample-codes :refer :all]))


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
