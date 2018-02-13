(ns boot-hedge.aws.core
  {:boot/export-tasks true}
  (:require
   [boot.core          :as c]
   [boot.util          :as u]
   [boot.task.built-in :refer [sift target zip]]
   [adzerk.boot-cljs :refer [cljs]]
   [clojure.java.io :refer [file]]
   [boot-hedge.common.core :refer [print-and-return now date->unixts
                                   ensure-valid-cron]]
   [boot-hedge.aws.lambda :refer [read-conf generate-files]]
   [boot-hedge.aws.cloudformation-api :as cf-api]
   [boot-hedge.aws.cloudformation :as cf]
   [boot-hedge.aws.s3-api :as s3-api]))

(c/deftask ^:private prepare-lambdas
  []
  (c/with-pre-wrap fs
    (-> fs
        read-conf
        print-and-return
        ensure-valid-cron
        (generate-files fs))))

(c/deftask ^:private upload-artefact
  "Uploads functions-*.zip to S3"
  [n stack-name STACK_NAME str "Stack name"]
  (c/with-pass-thru [fs]
    (let [client (s3-api/client)
          bucket (str "hedge-" stack-name "-deploy")
          artefact (->> fs
                        (c/input-files)
                        (c/by-re #{#"functions-[0-9]*.zip"})
                        (first)
                        (c/tmp-file))
          name (.getName artefact)]
      (u/info (str "Ensuring bucket " bucket " exists\n"))
      (s3-api/ensure-bucket client bucket)
      (u/info (str "Uploading " name " into bucket " bucket "\n"))
      (s3-api/put-object client bucket name artefact))))

(c/deftask ^:private deploy-stack
  "Deploys stack to AWS using Cloudformation template and functions-*.zip in S3"
  [n stack-name STACK str "Name of the stack"]
  (c/with-pass-thru [fs]
    (let [client (cf-api/client)
          cf-file (->> fs
                       (c/input-files)
                       (c/by-name #{"cloudformation.json"})
                       (first)
                       (c/tmp-file))
          artefact (->> fs
                        (c/input-files)
                        (c/by-re #{#"functions-[0-9]*.zip"})
                        (first)
                        (c/tmp-file))
          bucket (str "hedge-" stack-name "-deploy")
          key (.getName artefact)]
      (u/info "Deploying to AWS\n")
      (cf-api/deploy-stack client stack-name cf-file bucket key))))

(c/deftask upload-and-deploy
  "Uploads functions-*.zip and deploys using Cloudformation"
  [n stack-name STACK str "Name of the stack"]
  (comp
   (upload-artefact :stack-name stack-name)
   (deploy-stack :stack-name stack-name)))

(c/deftask build
  "Build lambda(s)"
  [O optimizations LEVEL kw "The optimization level."]
  (c/task-options!
   cljs #(assoc-in % [:compiler-options :target] :nodejs))
  (c/task-options!
   cljs #(assoc-in % [:compiler-options :compiler-stats] true))
  (comp
   (prepare-lambdas)
   (cljs :optimizations optimizations)))

(c/deftask create-template
  "Creates Cloudformation template from hedge.edn"
  []
  (c/with-pre-wrap fs
    (let [tmp (c/tmp-dir!)
          tmp-file (clojure.java.io/file tmp "cloudformation.json")]
      (-> fs
          (read-conf)
          (cf/write-template-file tmp-file))
      (-> fs (c/add-resource tmp) c/commit!))))

(c/deftask create-artefacts
  "Creates artefacts"
  []
  (let [zipfile (str "functions-" (date->unixts (now)) ".zip")]
    (comp
      (create-template)
      (sift :include #{#"\.out" #"\.edn" #"\.cljs"} :invert true)
      (zip :file zipfile))))

(c/deftask build-and-create-artefacts
  "Build lambdas and create artefacts"
  [O optimizations LEVEL kw "The optimization level"]
  (comp
    (build :optimizations (or optimizations :simple))
    (create-artefacts)))

(c/deftask read-files
  "Read files from target directory into task fileset"
  [d directory DIR str "Directory to read from"]
  (c/with-pre-wrap fs
    (c/commit! (c/add-resource fs (file (or directory "target"))))))

; main tasks
(c/deftask deploy-to-directory
  "** Builds lambda(s), creates artefacts and stores output to target **

  Stores artefacts to given directory to to target directory if argument is missing.

  Note: -f is currently for debugging only and it will create artifacts which are
  not compatible with deployment commands."

  [O optimizations LEVEL kw "The optimization level (optional)"
   f function FUNCTION str "Function to compile (optional)"
   d directory DIR str "Directory to deploy into (optional)"]
  (when function (do (c/set-env! :function-to-build function)
                   (u/warn "Note: output of this task when using -f flag is not compatible with deploy-from-directory task")))
  (comp
    (build-and-create-artefacts :optimizations optimizations)
    (target :dir #{(or directory "target")})))

(c/deftask deploy-from-directory
  "** Deploy files from directory. **

  Deploys files from given directory or target if command line
  argument is missing. It is recommended to use deploy-to-directory
  command to create directory with artifacts.

  Name of the stack is required arguments."
  [n stack-name STACK str "Name of the stack"
   d directory DIR str "Directory to deploy from (optional)"]
  (comp
   (read-files :directory directory)
   (upload-and-deploy :stack-name stack-name)))

(c/deftask deploy
  "** Build and deploy function app(s) **

  Build, creates artifacts and deploys with one command.

  Name of the stack of required argument and recommended optimization levels
  are :advanced and :simple."

  [n stack-name STACK str "Name of the stack"
   O optimizations LEVEL kw "The optimization level (optional)"]
  (if (nil? stack-name)
    (throw (Exception. "Missing stack name"))
    (comp
     (build-and-create-artefacts :optimizations optimizations)
     (upload-and-deploy :stack-name stack-name))))
