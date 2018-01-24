(ns boot-hedge.core
  {:boot/export-tasks true}
  (:require
   [boot.core          :as c]
   [boot.util          :as util]
   [boot.task.built-in :refer [sift target]]
   [adzerk.boot-cljs :refer [cljs]] 
   [clojure.string :refer [split]]
   [clojure.java.io :refer [file input-stream]]
   [boot-hedge.function-app :refer [read-conf generate-files]]
   [boot-hedge.common.core :refer [print-and-return fail-if-false]])
  (:import [com.microsoft.azure.management Azure]
           [com.microsoft.rest LogLevel]
           [com.microsoft.azure.management.resources.fluentcore.arm Region]
           [com.microsoft.azure.management.resources.fluentcore.utils SdkContext]
           [org.apache.commons.net.ftp FTPClient FTP]))



(def param-missing-msg ; define the common failure messages for missing params
  {:app-name "Function app name (-a) required"
   :rg-name "Resource group name (-r) required"
   :azure-filename "AZURE_AUTH_LOCATION environment variable is required to point to Azure principal file"
   :azure-file "Azure credential file \"%s\" does not exist"})

(c/deftask ^:private function-app
  "Generates fileset for cljs and deployment files from hedge.edn"
  []
  (c/with-pre-wrap fs
    (-> fs
        read-conf
        print-and-return
        (generate-files fs))))

(defn azure
  [cred-file]
  (->
   (Azure/configure)
   (.withLogLevel LogLevel/NONE)
   (.authenticate cred-file)
   (.withDefaultSubscription)))

(defn appname [b] (SdkContext/randomResourceName b, 20))

(defn split-path [path]
  (if (.contains path "/")
    (let [lastslash (.lastIndexOf path "/")]
      [(str "/" (.substring path 0 lastslash))
       (.substring path (inc lastslash))])
    [path ""]))

(defn ftp-info [client]
  (util/info (.getReplyString client))
  (util/info "\n"))

(defn change-and-report [client dir]
  (let [result (.changeWorkingDirectory client dir)]
    (ftp-info client)
    result))

(defn upload-file [{:keys [url username password] :as ftp} file-path file-stream]
  (let [ftp-client (FTPClient.)
        ftp-url-segments (split url #"/" 2)
        server (first ftp-url-segments)
        [local-path file-name ] (split-path file-path)
        path  (str "site/wwwroot" local-path)]
    (util/info (str "connecting to server: " server "\n"))
    (doto ftp-client
      (.connect server)
      (ftp-info)
      (.login username password)
      (ftp-info)
      (.setFileType FTP/ASCII_FILE_TYPE)
      (ftp-info)
      (.enterLocalPassiveMode)
      (ftp-info))
    (doseq [segment (seq (.split path "/"))]
      (util/info (str "moving to directory: " segment "\n"))
      (when-not (change-and-report ftp-client segment)
        (doto ftp-client 
          (.makeDirectory segment)
          (ftp-info)
          (.changeWorkingDirectory segment)
          (ftp-info))))
    (util/info (str "storing file: " file-name " "))
    (util/info "success: ")
    (util/info (str (.storeFile ftp-client file-name file-stream)))
    (util/info "\n")
    (util/info (.getReplyString ftp-client))
    (.disconnect ftp-client)))

(defn publishing-profile [resource-group function credential-file]
  (let [faps (-> (azure (file credential-file))
                .appServices
                .functionApps)
        pbo   (-> faps
                  (.getByResourceGroup resource-group function)
                  .getPublishingProfile)]
   
    {:ftp {:url (.ftpUrl pbo)
           :username (.ftpUsername pbo)
           :password (.ftpPassword pbo)}}))

(defn check-parameters
  "Check the command line parameters and fail if mandatory ones are missing.
  Error messages are read from `param-missing-msg`."
  [parameters]
  (let [checks (map (fn ([[kw [value str-param]]]
                         {:val value
                          :msg (format (kw param-missing-msg) str-param)}))
                    parameters)]
    (fail-if-false checks)))

(defmacro param
  "Create input parameter for check-parameters."
  ([kw] {(keyword kw) [kw nil]})
  ([kw str-param] {(keyword kw) [kw str-param]}))

(defn get-and-check-azure-credential-filename []
 (let [azure-filename (System/getenv "AZURE_AUTH_LOCATION")
       azure-file (if (and azure-filename
                           (-> (clojure.java.io/as-file azure-filename) .exists))
                   (file azure-filename) nil)]
   (check-parameters (conj (param azure-filename)
                           (param azure-file azure-filename)))
   azure-filename))


;; Task definitons follow

(c/deftask create-function-app
  "Creates given function app"
  [a app-name APP str "the app name"
   r rg-name RGN str "the resource group name"]
  (check-parameters (conj (param app-name) (param rg-name)))
  (let [credential-filename (get-and-check-azure-credential-filename)]
    (-> (azure)
        .appServices
        .functionApps
        (.define app-name)
        (.withRegion Region/EUROPE_NORTH)
        (.withNewResourceGroup rg-name)
        .create)))

(c/deftask azure-publish-profile
  "Shows details of publishing profile"
  [a app-name APP str "the app name"
   r rg-name RGN str "the resource group name"]
  (check-parameters (conj (param app-name) (param rg-name)))
  (util/info (prn-str (publishing-profile
                       rg-name app-name (get-and-check-azure-credential-filename)))))

(c/deftask ^:private deploy-to-azure
  "Deploy fileset to Azure"
  [a app-name APP str "the app name"
   r rg-name RGN str "the resource group name"
   p az-principal PRINCIPAL str "path to the azure principal file"]
  (check-parameters (conj (param app-name) (param rg-name)))
  (c/with-pass-thru [fs]
    (let [pprofile (publishing-profile rg-name app-name az-principal)
          ftp-profile (:ftp pprofile)]
      (doseq [{:keys [dir path] :as fi} (vals (:tree (c/output-fileset fs)))
              :let [f (.toFile (.resolve (.toPath dir) path))]]
        (util/info "uploading %s...\n" path dir)
        (with-open [in (input-stream f)]
          (upload-file ftp-profile (str path) in))))))


(c/deftask ^:private compile-function-app
  "Build function app(s)"
  [O optimizations LEVEL kw "The optimization level."]
  (c/task-options!
   cljs #(assoc-in % [:compiler-options :target] :nodejs))
  (comp
   (function-app)
   (cljs :optimizations optimizations)))

; FIXME: 
; * if optimizations :none inject :main option (is it even possible)
; * read :compiler-options from command line and merge with current config
(c/deftask deploy-to-directory
  "Build function app(s) and store output to target"
  [O optimizations LEVEL kw "The optimization level"
   f function FUNCTION str "Function to compile"
   d directory DIR str "Directory to deploy into"]
  (when function (c/set-env! :function-to-build function))
  (comp
    (compile-function-app :optimizations (or optimizations :simple))
    (sift :include #{#"\.out" #"\.edn" #"\.cljs"} :invert true)
    (target :dir #{(or directory "target")})))

(c/deftask ^:private read-files
  "Read files from target directory into task fileset"
  [d directory DIR str "Directory to read from"]
  (c/with-pre-wrap fs
    (c/commit! (c/add-resource fs (file (or directory "target"))))))

(c/deftask deploy-azure-from-directory
  "Deploy files from target directory to Azure."
  [a app-name APP str "the app name"
   r rg-name RGN str "the resource group name"
   d directory DIR str "Directory to deploy from"]
  (check-parameters (conj (param app-name) (param rg-name)))
  (let [credential-filename (get-and-check-azure-credential-filename)]
    (comp
     (read-files :directory directory)
     (sift :include #{#"\.out" #"\.edn" #"\.cljs"} :invert true)
     (deploy-to-azure :app-name app-name :rg-name rg-name :az-principal credential-filename))))

; FIXME: check env. variables for deployment
(c/deftask deploy-azure
  "Build and deploy function app(s)"
  [a app-name APP str "the app name"
   r rg-name RGN str "the resource group name"
   f function FUNCTION str "Function to deploy"
   O optimizations LEVEL kw "The optimization level."]
  (check-parameters (conj (param app-name) (param rg-name)))
  (let [credential-filename (get-and-check-azure-credential-filename)]
    (when function (c/set-env! :function-to-build function))
    (comp
     (compile-function-app :optimizations (or optimizations :advanced))
     (sift :include #{#"\.out" #"\.edn" #"\.cljs"} :invert true)
     (deploy-to-azure :app-name app-name :rg-name rg-name :az-principal credential-filename))))

(c/deftask hedge-azure
  "(Deprecated: use deploy-azure) Build and deploy function app(s)"
  [a app-name APP str "the app name"
   r rg-name RGN str "the resource group name"
   f function FUNCTION str "Function to deploy"
   O optimizations LEVEL kw "The optimization level."]
  (check-parameters (conj (param app-name) (param rg-name)))
  (deploy-azure :app-name app-name
                :rg-name rg-name
                :function function
                :optimizations optimizations))
