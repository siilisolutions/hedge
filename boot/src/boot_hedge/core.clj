(ns boot-hedge.core
  {:boot/export-tasks true}
  (:require
   [boot.core          :as c]
   [boot.util          :as util]
   [clojure.string :refer [split]]
   [clojure.java.io :refer [file input-stream]]
   [boot-hedge.function-app :refer [read-conf generate-files]])
  (:import [com.microsoft.azure.management Azure]
           [com.microsoft.rest LogLevel]
           [com.microsoft.azure.management.resources.fluentcore.arm Region]
           [com.microsoft.azure.management.resources.fluentcore.utils SdkContext]
           [org.apache.commons.net.ftp FTPClient FTP]))



(c/deftask function-app
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
    (.withLogLevel LogLevel/BASIC)
    (.authenticate cred-file)
    (.withDefaultSubscription))))

(defn appname [b] (SdkContext/randomResourceName b, 20))

(c/deftask create-function-app
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
  (prn ftp)
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
  (let [pbo (-> (azure)
                .appServices
                .functionApps
                (.getByResourceGroup resource-group function)
                .getPublishingProfile)]
    {:ftp {:url (.ftpUrl pbo)
           :username (.ftpUsername pbo)
           :password (.ftpPassword pbo)}}
    ))

(defn deploy-dir [dir resource-group function]
  (let [fdir (file dir)
        base (.toPath fdir)
        ftp-profile (:ftp (publishing-profile resource-group function))]
    (doseq [f (file-seq fdir) :when (not (.isDirectory f)) :let [path (.relativize base (.toPath f))]]
      (with-open [in (input-stream f)]
        (upload-file ftp-profile (str path) in)))))

(c/deftask azure-publish-profile
  [a app-name APP str "the app name"
   r rg-name RGN str "the resource group name"]
  (util/info (prn-str (publishing-profile rg-name app-name))))

(c/deftask azure-deploy
  [d directory DIR str "directory containing the app"
   a app-name APP str "the app name"
   r rg-name RGN str "the resource group name"]
  (deploy-dir directory rg-name app-name))


(c/deftask hedge-azure
  []
  (util/info "building an azure function app"))
