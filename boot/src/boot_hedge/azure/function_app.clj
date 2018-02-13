(ns boot-hedge.azure.function-app
  (:require
   [boot.core          :as c]
   [boot.util          :as util]
   [clojure.string :as str]
   [clojure.core.match :refer [match]]
   [boot.filesystem :as fs]
   [boot-hedge.common.core :refer [serialize-json 
                                   AZURE_FUNCTION 
                                   one-handler-config
                                   one-handler-configs]]
   [clojure.spec.alpha :as spec]
   [boot-hedge.common.validation :as validation]))

(spec/check-asserts true)

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
      (spit `(~azure-function ~(symbol (str 'handler "/" handler-func)) 
                               :inputs ~(-> function :inputs) 
                               :outputs ~(-> function :outputs)) 
            :append true))
    (clojure.pprint/pprint (slurp ff))
    {:fs (-> fs (c/add-source tgt) c/commit!)
     :func func-ns
     :cloud-name (generate-cloud-name handler)}))

(defn generate-cljs-edn [dir fns]
  (doto (clojure.java.io/file dir "index.cljs.edn")
    clojure.java.io/make-parents
    (spit  {:require [fns]}))
  dir)

(defn output->binding
  "returns the single binding for single output"
  [output]
  (match [(-> output :type) (-> output :topic) (-> output :accessRights)]
    ; table storage queue
    [:queue nil nil] {:type "queue"
                      :name (-> output :key)
                      :queueName (-> output :name)
                      :connection (-> output :connection)
                      :direction "out"}
    ; servicebus queue
    [:queue nil _]   {:type "serviceBus"
                      :name (-> output :key)
                      :queueName (-> output :name)
                      :accessRights (-> output :accessRights)
                      :connection (-> output :connection)
                      :direction "out"}
    ; servicebus topic queue
    [:queue true _]  {:type "serviceBus"
                      :name (-> output :key)
                      :topicName (-> output :name)
                      :accessRights (-> output :accessRights)
                      :connection (-> output :connection)
                      :direction "out"}
    ; table storage table
    [:table nil nil] {:type "table"
                      :name (-> output :key)
                      :tableName (-> output :name)
                      :connection (-> output :connection)
                      :direction "out"}
    ; cosmosdb collection
    [:db nil nil]    {:type "documentDB"
                      :name (-> output :key)
                      :databaseName (-> output :name)
                      :collectionName (-> output :collection)
                      :connection (-> output :connection)
                      :createIfNotExists false
                      :direction "out"}))

(defn outputs->bindings
  "adds outputs to function.json bindings"
  [bindings cfg]
  (if (-> cfg :function :outputs)
    ; here we need to map different structures and types
    (-> 
      (concat  
        (map output->binding (-> cfg :function :outputs)) 
        bindings)
      vec)    
    ; else just pass the bindings
    bindings))

(defn input->binding 
  "returns the single binding of given single input"
  [input]
  (match [(-> input :type)]
    ; table storage table
    [:table] {:type "table"
              :tableName (-> input :name)
              :name (-> input :key)
              :connection (-> input :connection)
              :direction "in"}
    ; table storage table
    [:db] {:type "documentDB"
              :databaseName (-> input :name)
              :collectionName (-> input :collection)
              :name (-> input :key)
              :connection (-> input :connection)
              :direction "in"}))

(defn inputs->bindings
  "adds inputs to function.json bindings"
  [bindings cfg]
  (if (-> cfg :function :inputs)
    ; here we need to map different structures and types
    (-> 
      (concat
        (map input->binding (-> cfg :function :inputs)) 
        bindings) 
      vec)
    ; else just pass the bindings
    bindings))

(defn function-json-for-api 
  "function.json constructor for function of type api (http in / http out)"
  [cfg]
  (let [trigger {:authLevel (name (if-let [authorization (-> cfg :function :authorization)] 
                                        authorization 
                                        :anonymous)),
                 :type "httpTrigger",
                 :direction "in",
                 :name "req"
                 :route (-> cfg :path)}]
    {:bindings (-> 
                 (outputs->bindings [trigger {:type "http", :direction "out", :name "$return"}] cfg)
                 (inputs->bindings cfg))      
    :disabled false}))

(defn function-json-for-timer
  "function.json constructor for function of type timer (timer trigger in). 
  Generates the seconds field as zero."
  [cfg]
  (let [trigger {:name "timer",
                 :type "timerTrigger",
                 :direction "in",
                 :schedule (str "0 " (-> cfg :function :cron))}]
   {:bindings (->
                (outputs->bindings [trigger] cfg)
                (inputs->bindings cfg))
     :disabled false}))

(defn function-json-for-storage-queue
  "function.json constructor for function of type storage queue."
  [cfg]
  (let [trigger {:name "message",
                 :type "queueTrigger",
                 :direction "in",
                 :queueName (-> cfg :function :queue),
                 :connection (-> cfg :function :connection)}]
  {:bindings (->
              (outputs->bindings [trigger] cfg)
              (inputs->bindings cfg))
   :disabled false}))

(defn function-json-for-servicebus-queue
  "function.json constructor for function of type service bus queue."
  [cfg]
  (let [trigger {:name "message",
                 :type "serviceBusTrigger",
                 :direction "in",
                 :queueName (-> cfg :function :queue),
                 :accessRights (-> cfg :function :accessRights),
                 :connection (-> cfg :function :connection)}]
  {:bindings (->
              (outputs->bindings [trigger] cfg)
              (inputs->bindings cfg))
   :disabled false}))

(defn function-json-for-servicebus-topic
  "function.json constructor for function of type service bus topic queue."
  [cfg]
  (let [trigger {:name "message",
                 :type "serviceBusTrigger",
                 :direction "in",
                 :topicName (-> cfg :function :queue),
                 :accessRights (-> cfg :function :accessRights),
                 :subscriptionName (-> cfg :function :subscription),
                 :connection (-> cfg :function :connection)}]
  {:bindings (-> 
              (outputs->bindings [trigger] cfg)
              (inputs->bindings cfg))
   :disabled false}))

(defn function-json-for-queue
  "decides between storage queue, servicebusqueue and servicebustopic depending on present parameters"
  [cfg]
  (match [(-> cfg :function :accessRights) (-> cfg :function :subscription)]
    [nil nil] (function-json-for-storage-queue cfg)
    [_ nil] (function-json-for-servicebus-queue cfg)
    [_ _] (function-json-for-servicebus-topic cfg)))

(defn function-type->function-json
  [cfg]
  (let [variants {:api   (function-json-for-api cfg)
                  :timer (function-json-for-timer cfg)
                  :queue (function-json-for-queue cfg)}]

    (if-let [json (get variants (-> cfg :type))]
      ; return
      json
      ; or throw expception
      (throw 
        (Exception. 
          (str "Cannot create function.json for unsupported handler type " (-> cfg :type)))))))

(defn generate-function-json 
  [{:keys [fs cloud-name]} cfg]
  (let [tgt (c/tmp-dir!)
        func-dir (clojure.java.io/file tgt cloud-name)
        function-json (function-type->function-json cfg)] 
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
  "Entry function for generating the output files. Takes hedge.edn as input.
  Launches generation of one function if :function-to-build is set."
  [edn-config fs]
  {:pre [(if-let [result (spec/valid? ::validation/hedge-edn edn-config)]
           result
           (throw (AssertionError. (str "Failed validating hedge.edn: \n" (spec/explain-data ::validation/hedge-edn edn-config)))))]}
  (if-let [handler (c/get-env :function-to-build)]
    ;then
    (generate-function fs (one-handler-config handler edn-config))
    ;else
    (reduce generate-function fs (one-handler-configs edn-config))))
