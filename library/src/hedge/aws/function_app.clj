(ns hedge.aws.function-app
  (:require [hedge.node :refer [node-module2]]))


; FIXME read parameter for module["exports"][<foo>] from configurations
(defmacro lambda-apigw-function [f]
  `(node-module2 (hedge.aws.function-app/lambda-apigw-function-wrapper ~f) "handler"))

(defmacro lambda-timer-function [f]
  `(node-module2 (hedge.aws.function-app/lambda-timer-function-wrapper ~f) "handler"))
