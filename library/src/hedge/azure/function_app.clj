(ns hedge.azure.function-app
  (:require [hedge.node :refer [node-module]]))



(defmacro azure-function [f]
  `(node-module (hedge.azure.function-app/azure-function-wrapper ~f)))
