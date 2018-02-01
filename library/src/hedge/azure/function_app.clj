(ns hedge.azure.function-app
  (:require [hedge.node :refer [node-module1]]))



(defmacro azure-api-function [f]
  `(node-module1 (hedge.azure.function-app/azure-api-function-wrapper ~f)))

(defmacro azure-timer-function [f]
  `(node-module1 (hedge.azure.function-app/azure-timer-function-wrapper ~f)))