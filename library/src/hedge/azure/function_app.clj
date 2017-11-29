(ns hedge.azure.function-app
  (:require [hedge.node :refer [node-module1]]))



(defmacro azure-function [f]
  `(node-module1 (hedge.azure.function-app/azure-function-wrapper ~f)))
