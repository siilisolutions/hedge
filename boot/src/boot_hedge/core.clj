(ns boot-hedge.core
  {:boot/export-tasks true}
  (:require
   [boot.core          :as c]
   [boot.util          :as util]
   [boot.task.built-in :refer [sift target]]
   [adzerk.boot-cljs :refer [cljs]] 
   [clojure.string :refer [split]]
   [clojure.java.io :refer [file input-stream]]
   [boot-hedge.function-app :refer [read-conf generate-files]])
  (:import [com.microsoft.azure.management Azure]
           [com.microsoft.rest LogLevel]
           [com.microsoft.azure.management.resources.fluentcore.arm Region]
           [com.microsoft.azure.management.resources.fluentcore.utils SdkContext]
           [org.apache.commons.net.ftp FTPClient FTP]))



(c/deftask ^:private function-app
  []
  (c/with-pre-wrap fs
    (-> fs
        read-conf
        (generate-files fs))))



(defn azure
  ([]
   (azure  (file ( System/getenv "AZURE_AUTH_LOCATION"))))
  ([cred-file]
   (->
    (Azure/configure)
    (.withLogLevel LogLevel/NONE)
    (.authenticate cred-file)
    (.withDefaultSubscription))))

(defn appname [b] (SdkContext/randomResourceName b, 20))

(c/deftask ^:private create-function-app
  [a app-name APP str "the app name"
   r rg-name RGN str "the resource group name"
   f auth-location AFL str "authorization file location"]
  (-> (azure)
      .appServices
      .functionApps
      (.define app-name)
      (.withRegion Region/EUROPE_NORTH)
      (.withNewResourceGroup rg-name)
      .create))


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

(defn publishing-profile [resource-group function]
  (let [faps (-> (azure)
                .appServices
                .functionApps)
        pbo   (-> faps
                  (.getByResourceGroup resource-group function)
                  .getPublishingProfile)]
   
    {:ftp {:url (.ftpUrl pbo)
           :username (.ftpUsername pbo)
           :password (.ftpPassword pbo)}}))


(c/deftask ^:private azure-publish-profile
  [a app-name APP str "the app name"
   r rg-name RGN str "the resource group name"]
  (util/info (prn-str (publishing-profile rg-name app-name))))

(c/deftask ^:private azure-deploy
  [a app-name APP str "the app name"
   r rg-name RGN str "the resource group name"]
  (c/with-pass-thru [fs]
    (let [pprofile (publishing-profile rg-name app-name)
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
(c/deftask deploy-to-target
  "Build function app(s) and store output to target"
  [O optimizations LEVEL kw "The optimization level."
   f function FUNCTION str "Function to compile"]
  (c/set-env! :function-to-build (or function :all))
  (comp
    (compile-function-app :optimizations (or optimizations :simple))
    (sift :include #{#"\.out" #"\.edn" #"\.cljs"} :invert true)
    (target)))

; FIXME: check env. variables for deployment
(c/deftask hedge-azure
  "Build and deploy function app(s)"
  [a app-name APP str "the app name"
   r rg-name RGN str "the resource group name"]
  (if (or (nil? app-name) (nil? rg-name))
    (throw (Exception. "Missing function app or resource group name"))
    (comp
     (compile-function-app :optimizations :simple)
     (sift :include #{#"\.out" #"\.edn" #"\.cljs"} :invert true)
     (azure-deploy :app-name app-name :rg-name rg-name))))
