(ns hedge.module
  (:require [hedge.node :refer-macros [node-module]]))



(node-module (js-obj "foo" "bar"))

