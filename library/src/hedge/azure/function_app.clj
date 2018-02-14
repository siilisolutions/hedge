(ns hedge.azure.function-app
  (:require [hedge.node :refer [node-module1]]))



(defmacro azure-api-function [f & {:keys [inputs outputs]}]
  `(node-module1 (hedge.azure.function-app/azure-api-function-wrapper ~f :inputs ~inputs :outputs ~outputs)))

(defmacro azure-timer-function [f & {:keys [inputs outputs]}]
  `(node-module1 (hedge.azure.function-app/azure-timer-function-wrapper ~f :inputs ~inputs :outputs ~outputs)))

(defmacro azure-queue-function [f & {:keys [inputs outputs]}]
  `(node-module1 (hedge.azure.function-app/azure-queue-function-wrapper ~f :inputs ~inputs :outputs ~outputs)))
