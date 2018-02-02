(ns boot-hedge.azure.function-app
  (:require
   [boot.core          :as c]
   [boot.util          :as util]
   [clojure.string :as str]
   [boot.filesystem :as fs]
   [boot-hedge.core :refer [SUPPORTED_HANDLERS AZURE_FUNCTION]]
   [boot-hedge.common.core :refer [serialize-json one-handler-config]]))

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

(defn generate-source [fs {:keys [function type]}]
  (let [handler (-> function :handler)
        handler-ns (symbol (namespace handler))
        handler-func (symbol (name handler))
        func-ns (hedge-ns fs handler-ns)
        tgt (c/tmp-dir!)
        ff (clojure.java.io/file tgt (ns-file func-ns))
        azure-function (get AZURE_FUNCTION type)]

    (doto ff
      clojure.java.io/make-parents
      (spit `(~'ns ~func-ns (:require [hedge.azure.function-app :refer-macros [~azure-function]]
                                     [~handler-ns :as ~'handler])))
      (spit `(~azure-function ~(symbol (str 'handler "/" handler-func))) :append true))
    (clojure.pprint/pprint (slurp ff))
    {:fs (-> fs (c/add-source tgt) c/commit!)
     :func func-ns
     :cloud-name (generate-cloud-name handler)}))

(defn generate-cljs-edn [dir fns]
  (doto (clojure.java.io/file dir "index.cljs.edn")
    clojure.java.io/make-parents
    (spit  {:require [fns]}))
  dir)

(defn function-json-for-api 
  "function.json constructor for function of type api (http in / http out)"
  [path authorization]
  {:bindings
   [{:authLevel (name authorization),
     :type "httpTrigger",
     :direction "in",
     :name "req"
     :route path}
    {:type "http", :direction "out", :name "$return"}],
   :disabled false})

(defn function-json-for-timer
  "function.json constructor for function of type timer (timer trigger in). Generates the seconds field as zero."
  [cron]
   {:bindings 
    [{:name "timer",
      :type "timerTrigger",
      :direction "in",
      :schedule (str "0 " cron)}],
     :disabled false})

(defn generate-function-json 
  [{:keys [fs cloud-name]} cfg]
  (let [tgt (c/tmp-dir!)
        func-dir (clojure.java.io/file tgt cloud-name)
        function-json (cond
                        (= (-> cfg :type) :api)
                          (function-json-for-api 
                            (-> cfg :path) 
                            (if-let [auth (-> cfg :function :authorization)] auth {:authorization :anonymous})) 
                        (= (-> cfg :type) :timer)
                          (function-json-for-timer 
                            (-> cfg :function :cron))
                        :else
                          (throw (Exception. (str "Cannot create function.json for unsupported handler type " type))))]
  (doto (clojure.java.io/file func-dir "function.json")
    clojure.java.io/make-parents
    (serialize-json function-json))
  (-> fs (c/add-resource tgt) c/commit!)))


(defn generate-js-func-compile [{:keys [fs func cloud-name] :as conf}]
  (let [tgt (c/tmp-dir!)
        func-dir (clojure.java.io/file tgt cloud-name)]
    (-> func-dir
        (generate-cljs-edn func))
    (assoc conf :fs (-> fs (c/add-source tgt) c/commit!))))

(defn generate-function 
  "Results in index.js and function.json, receiving a function config as declared in hedge.edn."
  [fs cfg]
  (->
    (generate-source fs cfg)
    generate-js-func-compile
    (generate-function-json cfg)))

(defn generate-files 
  "Entry function for generating the output files. Takes hedge.edn as input. Launches generation of one function if :function-to-build is set."
  [edn-config fs]
  (if-let [handler (c/get-env :function-to-build)]
    ;then
    (generate-function fs (one-handler-config handler edn-config))
    ;else
    (do
      (let [configs (select-keys edn-config SUPPORTED_HANDLERS)
            handler-configs (-> (for [config-type (keys configs)]
                                  (map (fn [item] (one-handler-config (first item) edn-config)) (get configs config-type))) 
                                flatten)]
        (reduce generate-function fs handler-configs)))))
