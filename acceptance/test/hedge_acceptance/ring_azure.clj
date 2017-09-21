(ns hedge-acceptance.ring-azure
  (:require [hedge-acceptance.util.concordion :refer [concordion-fixture]]
            [hedge-acceptance.util.sample-codes :refer :all]))


(concordion-fixture basic-ring-handler-azure
                    (handlerNsName [] (str handler-ns-name))
                    (simpleBoot [] (code-print simple-boot-conf))
                    (basicHandlerNS [] (code-print basic-handler))
                    (basicHelloConf [] (code-print hello-conf))
                    (deploy [bc] (println bc))
                    (getresource [url]))
