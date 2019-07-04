(ns hedge.common
  (:require [clojure.java.io]
            [clojure.string]))

(defn find-version [version line]
  (if (clojure.string/starts-with? line "version=")
    (last (clojure.string/split line #"="))
    version))

(defn get-hedge-version
  "Return the version of hedge."
  []
  (let [props (clojure.java.io/resource "META-INF/maven/siili/hedge/pom.properties")
        all-props (slurp props)]
    (reduce find-version (clojure.string/split-lines all-props))))

(defn print-hedge-version
  "Print the version of hedge to stdout"
  []
  (println "hedge version:" (get-hedge-version)))
