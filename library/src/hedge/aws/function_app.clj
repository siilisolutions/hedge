(ns hedge.aws.function-app
  (:require [hedge.node :refer [node-module2]]))


; FIXME read parameter for module["exports"][<foo>] from configurations
(defmacro azure-function [f]
  `(node-module2 (hedge.aws.function-app/azure-function-wrapper ~f) "handler"))
