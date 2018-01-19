(ns boot-hedge.aws.core
  {:boot/export-tasks true}
  (:require
   [boot.core          :as c]
   [boot.util          :as u]
   [boot.task.built-in :refer [sift target zip]]
   [adzerk.boot-cljs :refer [cljs]]
   [clojure.java.io :refer [file]]
   [boot-hedge.common.core :refer [print-and-return]]
   [boot-hedge.aws.lambda :refer [read-conf generate-files]]
   [boot-hedge.aws.cloudformation-api :as cf-api]
   [boot-hedge.aws.s3-api :as s3-api]))

(c/deftask ^:private function-app
  []
  (c/with-pre-wrap fs
    (-> fs
        read-conf
        print-and-return
        (generate-files fs))))

(c/deftask ^:private upload-artefact
  [n stack-name STACK str "Name of the stack"]
  (c/with-pass-thru [fs]
    (let [client (s3-api/client)
          artefact (->> fs
                        (c/input-files)
                        (c/by-name #{"functions.zip"})
                        (first)
                        (c/tmpfile))]
      (s3-api/ensure-bucket client stack-name)
      (s3-api/put-object client stack-name "functions.zip" artefact))))

(c/deftask ^:private deploy-stack
  [n stack-name STACK str "Name of the stack"]
  (c/with-pass-thru [fs]
    (let [client (cf-api/client)
          cf-file (->> fs
                       (c/input-files)
                       (c/by-name #{"cloudformation.yml"})
                       (first)
                       (c/tmpfile))]
      (cf-api/deploy-stack client stack-name cf-file))))

(c/deftask ^:private deploy-to-aws
  "Deploy fileset to Azure"
  [n stack-name STACK str "Name of the stack"]
  (comp
   (zip :file "functions.zip")
   ; zip creates artefact we upload!
   (upload-artefact :stack-name stack-name)
   ;(create-formation template)
   (deploy-stack :stack-name stack-name)))

(c/deftask ^:private compile-function-app
  "Build function app(s)"
  [O optimizations LEVEL kw "The optimization level."]
  (c/task-options!
   cljs #(assoc-in % [:compiler-options :target] :nodejs))
  (comp
   (function-app)
   (cljs :optimizations optimizations)))

(c/deftask create-template
  []
  identity)

; FIXME: 
; * if optimizations :none inject :main option (is it even possible)
; * read :compiler-options from command line and merge with current config
; * rename task later if deployment target for different cloud types is resolved
(c/deftask deploy-to-directory
  "Build lambda(s) and store output to target"
  [O optimizations LEVEL kw "The optimization level"
   f function FUNCTION str "Function to compile"
   d directory DIR str "Directory to deploy into"]
  (when function (c/set-env! :function-to-build function))
  (comp
    (compile-function-app :optimizations (or optimizations :simple))
    (create-template)
    (sift :include #{#"\.out" #"\.edn" #"\.cljs"} :invert true)
    (target :dir #{(or directory "target")})))

(c/deftask ^:private read-files
  "Read files from target directory into task fileset"
  [d directory DIR str "Directory to read from"]
  (c/with-pre-wrap fs
    (c/commit! (c/add-resource fs (file (or directory "target"))))))

(c/deftask deploy-from-directory
  "Deploy files from target directory."
  [n stack-name STACK str "Name of the stack"
   d directory DIR str "Directory to deploy from"]
  (comp
   (read-files :directory directory)
   (deploy-to-aws :stack-name stack-name)))

(c/deftask deploy-aws
  "Build and deploy function app(s)"
  [n stack-name STACK str "Name of the stack"
   O optimizations LEVEL kw "The optimization level."]
  (if (nil? stack-name)
    (throw (Exception. "Missing stack name"))
    (comp
     (compile-function-app :optimizations (or optimizations :advanced))
     (create-template)
     (sift :include #{#"\.out" #"\.edn" #"\.cljs"} :invert true)
     (deploy-to-aws :stack-name stack-name))))
