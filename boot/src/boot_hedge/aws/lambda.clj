(ns boot-hedge.lambda
  (:require
   [boot.core          :as c]
   [boot.util          :as util]
   [clojure.string :as str]
   [cheshire.core :refer [generate-stream]]
   [boot.filesystem :as fs]))


(defn print-and-return [s]
  (clojure.pprint/pprint s)
  s)

(defn read-conf [fileset]
  (->> fileset
       c/input-files
       (c/by-name #{"hedge.edn"})
       first
       c/tmp-file
       slurp
       clojure.edn/read-string
       print-and-return))

(defn ns-file [ns]
  (-> (name ns)
      (str/replace "." "/")
      (str/replace "-" "_")
      (str ".cljs")))

(defn file-exists? [fs f]
  (< 0 (count (c/by-name #{f} (c/input-files fs)))))

(defn hedge-ns [fs orig]
  (loop []
    (let [func-ns (gensym orig)
          func-file (ns-file func-ns)]
      (if (file-exists? fs func-file)
        (recur)
        func-ns))))

(defn dashed-alphanumeric [s]
  (str/replace s #"[^A-Za-z0-9\-]" "_"))

(defn generate-cloud-name [handler]
  ; hedge-test.core/hello => hedge-test_core__hello
  (str
    (dashed-alphanumeric (namespace handler))
    "__"
    (dashed-alphanumeric (name handler))))

; TODO: parametrize output's lambda-apigw-function
(defn generate-source [fs {:keys [handler]}]
  "Generates multiple source files with rules read from hedge.edn"
  (let [handler-ns (symbol (namespace handler))
        handler-func (symbol (name handler))
        func-ns (hedge-ns fs handler-ns)
        tgt (c/tmp-dir!)
        ff (clojure.java.io/file tgt (ns-file func-ns))]
    (doto ff
      clojure.java.io/make-parents
      (spit `(~'ns ~func-ns (:require [hedge.aws.function-app :refer-macros [~'lambda-apigw-function]]
                                     [~handler-ns :as ~'handler])))
      (spit `(~'enable-console-print!) :append true)
      (spit `(~'lambda-apigw-function ~(symbol (str 'handler "/" handler-func))) :append true))
    (clojure.pprint/pprint (slurp ff))
    {:fs (-> fs (c/add-source tgt) c/commit!)
     :func func-ns
     :cloud-name (generate-cloud-name handler)}))

(defn generate-cljs-edn [dir fns]
  (doto (clojure.java.io/file dir "index.cljs.edn")
    clojure.java.io/make-parents
    (spit  {:require [fns]}))
  dir)

(defn serialize-json [f d]
  (generate-stream d (clojure.java.io/writer f)))

(defn generate-js-func-compile [{:keys [fs func cloud-name] :as conf}]
  (let [tgt (c/tmp-dir!)
        func-dir (clojure.java.io/file tgt cloud-name)]
    (-> func-dir
        (generate-cljs-edn func))
    (-> fs (c/add-source tgt) c/commit!)))

(defn generate-function [f]
  (fn [fs [path func]]
    (->
      (f fs func)
      generate-js-func-compile)))

(defn generate-build-files
  "Generates wrapped source codes and edn files for build"
  [fs conf key f]
  (if (= (c/get-env :function-to-build) :all)
    (reduce (generate-function f) fs (key conf))
    (reduce (generate-function f) fs (select-keys (key conf) [(c/get-env :function-to-build)]))))

(defn generate-files-cf-template
  "WIP: generate cloudformation template"
  [fs conf]
  fs)

; TODO: maybe this should be task?
(defn generate-files [conf fs]
  "Generates files for build and deploy"
  (-> fs
    (generate-build-files conf :api generate-source)
    #_(generate-build-files conf :timer generate-source-timer)
    (generate-files-cf-template conf)))