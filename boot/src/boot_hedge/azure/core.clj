(ns boot-hedge.azure.core
  {:boot/export-tasks true}
  (:require
   [boot.core          :as c]
   [boot.util          :as util]
   [boot.task.built-in :refer [sift target]]
   [adzerk.boot-cljs :refer [cljs]]
   [clojure.string :refer [split]]
   [clojure.java.io :refer [file input-stream]]
   [boot-hedge.azure.function-app :refer [read-conf generate-files]]
   [boot-hedge.common.core :refer [print-and-return fail-if-false]]
   [clojure.spec.alpha :as spec])
  (:import [com.microsoft.azure.management Azure]
           [com.microsoft.rest LogLevel]
           [com.microsoft.azure.management.resources.fluentcore.arm Region]
           [com.microsoft.azure.management.resources.fluentcore.utils SdkContext]
           [com.microsoft.azure.credentials ApplicationTokenCredentials]
           [org.apache.commons.net.ftp FTPClient FTP]))


;;; Define the required parameters
;; File related spec
(spec/def ::file-exists? (spec/and string? #(-> (clojure.java.io/as-file %) .exists)))
(spec/def ::azure-principal-file-parameter ::file-exists?)
(spec/def ::azure-principal-file-env ::file-exists?)

;; Command line credentials spec
(spec/def ::is-uuid? #(try (-> (read-string (str "#uuid \"" % "\"")) uuid?)
                           (catch IllegalArgumentException e nil)))
(spec/def ::client-id (spec/and string? ::is-uuid?))
(spec/def ::client-domain (spec/and string? ::is-uuid?))
(spec/def ::client-secret (spec/and string? ::is-uuid?))
(spec/def ::azure-id-domain-secret
  (spec/keys :req-un [::client-id
                      ::client-domain
                      ::client-secret]))

;; Command line and file credentials spec combined
(spec/def ::azure-credentials
  (spec/keys :req-un [(or ::azure-id-domain-secret
                          ::azure-principal-file-parameter
                          ::azure-principal-file-env)]))

(defn add-env-credential-file
  "Add credential file from ENV to the credential map if defined."
  [credentials]
  (let [creds-env-file (System/getenv "AZURE_AUTH_LOCATION")]
    (if creds-env-file
      (assoc credentials :azure-principal-file-env creds-env-file)
      credentials)))

(defn resolve-azure-credentials
  "Attempt to resolve the Azure credentials to use or fail.
  The credentials are attempted to retrieve from:
  1. Command line parameters for id, domain and secret
  2. Command line parameter pointing to security principal
  3. Environment variable pointing to security principal"
  [initial-credentials]
  (let [credentials-with-env (add-env-credential-file initial-credentials)
        result (spec/conform ::azure-credentials credentials-with-env)]
    (if (spec/invalid? result)
      (do
        (util/fail "No valid Azure credentials provided:\n%s"
                   (spec/explain-data ::azure-credentials credentials-with-env))
        (util/exit-error))
      result)))

(defn map-credentials
  "Map the command line credentials to the format spec expects.
  If any credential related information is given expect the others too."
  [client-id client-domain client-secret principal-file]
  (let [id-domain-secret (if (or client-id client-domain client-secret)
                           {:azure-id-domain-secret {:client-id client-id
                                                     :client-domain client-domain
                                                     :client-secret client-secret}}
                           {})
        credentials (if principal-file
                      (merge id-domain-secret
                             {:azure-principal-file-parameter principal-file})
                      id-domain-secret)]
    credentials))

(c/deftask ^:private function-app
  "Generates fileset for cljs and deployment files from hedge.edn"
  []
  (c/with-pre-wrap fs
    (-> fs
        read-conf
        print-and-return
        (generate-files fs))))

(defn azure-from-credentials
  "Create authentication credential object for Azure.authenticate function."
  [credentials]
  (ApplicationTokenCredentials. (:client-id credentials)
                                (:client-domain credentials)
                                (:client-secret credentials)
                                nil))

(defn azure-from-principal-file
  "Create authentication credential file object for Azure.authenticate function.
  First attempt to use the file from the command line and then try the file from ENV."
  [param-cred-file env-cred-file]
  (file (if param-cred-file param-cred-file env-cred-file)))

(defn azure
  [all-credentials]
  (let [credentials (if (:azure-id-domain-secret all-credentials)
                      (azure-from-credentials (:azure-id-domain-secret all-credentials))
                      (azure-from-principal-file (:azure-principal-file-parameter all-credentials)
                                                 (:azure-principal-file-env all-credentials)))]
    (->
     (Azure/configure)
     (.withLogLevel LogLevel/NONE)
     (.authenticate credentials)
     (.withDefaultSubscription))))

(defn appname [b] (SdkContext/randomResourceName b, 20))

(defn check-app-rg-names [app-name rg-name]
  (fail-if-false app-name "Application name (-a/--app-name) is required but not set")
  (fail-if-false rg-name "Resource group name (-r/--rg-name) is required but not set"))

(c/deftask create-function-app
  "Creates given function app resource"
  [a app-name APP str "the app name"
   r rg-name RGN str "the resource group name"]
  (check-app-rg-names app-name rg-name)
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
      [(.substring path 0 lastslash)
       (.substring path (inc lastslash))])
    [path ""]))

(defn ftp-info [client]
  (util/info (.getReplyString client)))

(defn change-and-report [client dir]
  (let [result (.changeWorkingDirectory client dir)]
    (ftp-info client)
    result))

(defn files-in-dir
  "Map files by their upload folder to minimize changing the folder during the
  FTP connection."
  [path-map new-file]
  (let [local-directory (:dir new-file)
        [remote-directory filename] (split-path (:path new-file))
        old-files (get path-map remote-directory)
        new-file {:filename filename
                  :local-directory local-directory}]
    (assoc path-map remote-directory (conj old-files new-file))))

(defn ftp-goto-directory
  "Change to the given directory creating it if it does not exist."
  [ftp-client directory]
  (let [path-segments (seq (.split directory "/"))]
    (util/info (str "moving to directory: " directory "\n"))
    (when-not (change-and-report ftp-client directory)
      (doseq [segment path-segments]
        (doto ftp-client
          (.makeDirectory segment)
          (ftp-info)
          (.changeWorkingDirectory segment)
          (ftp-info))))))

(defn upload-files [ftp-client initial-dir files]
  (doseq [[remote-path local-files] (reduce files-in-dir {} files)]
    (.changeWorkingDirectory ftp-client initial-dir)
    (ftp-goto-directory ftp-client remote-path)
    (doseq [{:keys [filename local-directory]} local-files
            :let [f (.toFile (.resolve (.toPath local-directory)
                                       (str remote-path "/" filename)))]]
      (util/info "uploading %s...\n" filename)
      (with-open [file-stream (input-stream f)]
        (.storeFile ftp-client filename file-stream)
        (util/info (.getReplyString ftp-client))))))

(defn ftp-upload
  "Open the FTP connection and upload the output files."
  [{:keys [url username password] :as ftp} fileset]
  (let [ftp-client (FTPClient.)
        ftp-url-segments (split url #"/" 2)
        server (first ftp-url-segments)
        initial-directory "site/wwwroot"
        output-files (vals (:tree (c/output-fileset fileset)))]
    (util/info (str "connecting to server: " server "\n"))
    ;; Initialize the FTP-connection
    (doto ftp-client
      (.connect server)
      (ftp-info)
      (.login username password)
      (ftp-info)
      (.setFileType FTP/ASCII_FILE_TYPE)
      (ftp-info)
      (.enterLocalPassiveMode)
      (ftp-info))
    ;; Change to the initial directory
    (ftp-goto-directory ftp-client initial-directory)
    ;; Upload the files and disconnect
    (upload-files ftp-client (.printWorkingDirectory ftp-client) output-files)
    (.disconnect ftp-client)))

(defn publishing-profile [resource-group function credentials]
  (let [faps (-> (azure credentials)
                .appServices
                .functionApps)
        pbo   (-> faps
                  (.getByResourceGroup resource-group function)
                  .getPublishingProfile)]

    {:ftp {:url (.ftpUrl pbo)
           :username (.ftpUsername pbo)
           :password (.ftpPassword pbo)}}))

(c/deftask show-publish-profile
  "Shows details of publishing profile
  
  Azure authentication resolving priority:
  1. Command line parameters for id, domain and secret
  2. Command line parameter pointing to security principal
  3. Environment variable pointing to security principal (no optional parameters given)"

  [a app-name APP str "the app name"
   r rg-name RGN str "the resource group name"
   p principal-file PRINCIPAL str "Azure principal file (optional)"
   i client-id CLIENT str "Azure client id (optional)"
   t tenant-id TENANT str "Azure tenant id (optional)"
   s secret SECRET str "Azure client secret (optional)"]
  (check-app-rg-names app-name rg-name)
  (let [credential-candidates (map-credentials client-id tenant-id secret
                                               principal-file)
        credentials (resolve-azure-credentials credential-candidates)]
    (util/info (prn-str (publishing-profile rg-name app-name credentials)))))

(c/deftask ^:private deploy-to-azure
  "Deploy fileset to Azure"
  [a app-name APP str "the app name"
   r rg-name RGN str "the resource group name"
   c credentials CREDENTIALS code "Pre-resolved credentials map"]
  (c/with-pass-thru [fs]
    (let [pprofile (publishing-profile rg-name app-name credentials)
          ftp-profile (:ftp pprofile)]
      (ftp-upload ftp-profile fs))))


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
  "** Build function app(s) and store output to target **
  
  If directory is not given, will default to ./target"
  [O optimizations LEVEL kw "The optimization level (optional)"
   f function FUNCTION str "Function to compile"
   d directory DIR str "Directory to deploy into (optional)"]
  (when function (c/set-env! :function-to-build function))
  (comp
    (compile-function-app :optimizations (or optimizations :advanced))
    (sift :include #{#"\.out" #"\.edn" #"\.cljs"} :invert true)
    (target :dir #{(or directory "target")})))

(c/deftask ^:private read-files
  "Read files from target directory into task fileset"
  [d directory DIR str "Directory to read from"]
  (c/with-pre-wrap fs
    (c/commit! (c/add-resource fs (file (or directory "target"))))))

(c/deftask deploy-from-directory
  "** Deploy files from directory. **
  
  Azure authentication resolving priority:
  1. Command line parameters for id, domain and secret
  2. Command line parameter pointing to security principal
  3. Environment variable pointing to security principal (no optional parameters given)"

  [a app-name APP str "the app name"
   r rg-name RGN str "the resource group name"
   d directory DIR str "Directory to deploy from"
   p principal-file PRINCIPAL str "Azure principal file (optional)"
   i client-id CLIENT str "Azure client id (optional)"
   t tenant-id TENANT str "Azure tenant id (optional)"
   s secret SECRET str "Azure client secret (optional)"]
  (check-app-rg-names app-name rg-name)
  (let [credential-candidates (map-credentials client-id tenant-id secret
                                               principal-file)
        credentials (resolve-azure-credentials credential-candidates)]
    (comp
     (read-files :directory directory)
     (sift :include #{#"\.out" #"\.edn" #"\.cljs"} :invert true)
     (deploy-to-azure :app-name app-name :rg-name rg-name
                      :credentials credentials))))

; FIXME: check env. variables for deployment
(c/deftask deploy
  "** Build and deploy function app(s) **

  Either client-id, tenant-id and secret or a principal file has to be provided.
  The principal file can be provided using the -p/--principal-file parameter or
  via AZURE_AUTH_LOCATION environment variable.
  
  Azure authentication resolving priority:
  1. Command line parameters for id, domain and secret
  2. Command line parameter pointing to security principal
  3. Environment variable pointing to security principal (no optional parameters given)"

  [a app-name APP str "the app name"
   r rg-name RGN str "the resource group name"
   f function FUNCTION str "Function to deploy (optional)"
   O optimizations LEVEL kw "The optimization level. (optional)"
   p principal-file PRINCIPAL str "Azure principal file (optional)"
   i client-id CLIENT str "Azure client id (optional)"
   t tenant-id TENANT str "Azure tenant id (optional)"
   s secret SECRET str "Azure client secret (optional)"]
  (check-app-rg-names app-name rg-name)
  (when function (c/set-env! :function-to-build function))
  (let [credential-candidates (map-credentials client-id tenant-id secret
                                               principal-file)
        credentials (resolve-azure-credentials credential-candidates)]
    (comp
     (compile-function-app :optimizations (or optimizations :advanced))
     (sift :include #{#"\.out" #"\.edn" #"\.cljs"} :invert true)
     (deploy-to-azure :app-name app-name :rg-name rg-name
                      :credentials credentials))))
