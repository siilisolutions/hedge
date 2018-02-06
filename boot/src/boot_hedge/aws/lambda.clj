(ns boot-hedge.aws.lambda
  (:require
   [boot.core          :as c]
   [boot.util          :as util]
   [clojure.string :as str]
   [cheshire.core :refer [generate-stream]]
   [boot.filesystem :as fs]
   [boot-hedge.common.core :as common]))

(defn read-conf [fileset]
  (->> fileset
       c/input-files
       (c/by-name #{"hedge.edn"})
       first
       c/tmp-file
       slurp
       clojure.edn/read-string))

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

(defn generate-source [fs {:keys [handler]} type]
  "Generates multiple source files with rules read from hedge.edn"
  (let [handler-ns (symbol (namespace handler))
        handler-func (symbol (name handler))
        func-ns (hedge-ns fs handler-ns)
        tgt (c/tmp-dir!)
        ff (clojure.java.io/file tgt (ns-file func-ns))
        wrapper (type common/AWS_FUNCTIONS)]
    (doto ff
      clojure.java.io/make-parents
      (spit `(~'ns ~func-ns (:require [hedge.aws.function-app :refer-macros [~wrapper]]
                                      [~handler-ns :as ~'handler])))
      (spit `(~'enable-console-print!) :append true)
      (spit `(~wrapper ~(symbol (str 'handler "/" handler-func))) :append true))
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

(defn generate-function [type]
  (fn
    [fs [path func]]
    (->
      (generate-source fs func type)
      generate-js-func-compile)))

(defn generate-build-files
  "Generates wrapped source codes and edn files for build"
  [fs conf type]
  (if (c/get-env :function-to-build)
    (reduce (generate-function type) fs (select-keys (type conf) [(c/get-env :function-to-build)]))
    (reduce (generate-function type) fs (type conf))))

(defn generate-files [conf fs]
  "Generates files for build and deploy"
  (-> fs
    (generate-build-files conf :api)
    (generate-build-files conf :timer)))
