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

(defn generate-source [fs {:keys [handler]}]
  (let [handler-ns (symbol (namespace handler))
        handler-func (symbol (name handler))
        func-ns (hedge-ns fs handler-ns)
        tgt (c/tmp-dir!)
        ff (clojure.java.io/file tgt (ns-file func-ns))]
    (doto ff
      clojure.java.io/make-parents
      (spit `(~'ns ~func-ns (:require [hedge.aws.function-app :refer-macros [~'azure-function]]
                                     [~handler-ns :as ~'handler])))
      (spit `(~'azure-function ~(symbol (str 'handler "/" handler-func))) :append true))
    (clojure.pprint/pprint (slurp ff))
    {:fs (-> fs (c/add-source tgt) c/commit!)
     :func func-ns
     :cloud-name (generate-cloud-name handler)}))

(defn generate-cljs-edn [dir fns]
  (doto (clojure.java.io/file dir "index.cljs.edn")
    clojure.java.io/make-parents
    (spit  {:require [fns]}))
  dir)

(defn function-json [path authorization]
  {:bindings
   [{:authLevel (name authorization),
     :type "httpTrigger",
     :direction "in",
     :name "req"
     :route path}
    {:type "http", :direction "out", :name "$return"}],
   :disabled false})

(defn serialize-json [f d]
  (generate-stream d (clojure.java.io/writer f)))

(defn generate-function-json [{:keys [fs cloud-name]} path {:keys [authorization] :or {authorization :anonymous}}]
  (let [tgt (c/tmp-dir!)
        func-dir (clojure.java.io/file tgt cloud-name)]
  (doto (clojure.java.io/file func-dir "function.json")
    clojure.java.io/make-parents
    (serialize-json (function-json path authorization)))
  (-> fs (c/add-resource tgt) c/commit!)))


(defn generate-js-func-compile [{:keys [fs func cloud-name] :as conf}]
  (let [tgt (c/tmp-dir!)
        func-dir (clojure.java.io/file tgt cloud-name)]
    (-> func-dir
        (generate-cljs-edn func))
    (assoc conf :fs (-> fs (c/add-source tgt) c/commit!))))

(defn generate-function [fs [path func]]
  (->
    (generate-source fs func)
    generate-js-func-compile
    (generate-function-json path func)))

(defn generate-files [{:keys [api]} fs]
  (reduce generate-function fs api)

  )
