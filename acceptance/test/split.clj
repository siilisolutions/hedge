(ns split
  (:require [concordion :refer [concordion-fixture]]))

(deftype Person [firstName lastName])



(concordion-fixture splitting-names
                      (split [s]
                        (let [[first-name last-name] (seq (.split s " "))]
                          (->Person first-name last-name))))

