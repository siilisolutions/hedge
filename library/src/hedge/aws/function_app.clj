(ns hedge.aws.function-app
  (:require [hedge.node :refer [node-module]]))



(defmacro azure-function [f]
  `(node-module (hedge.aws.function-app/azure-function-wrapper ~f)))
