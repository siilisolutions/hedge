(set-env! :source-paths #{"test"}
          :resource-paths #{"src"}
          :dependencies '[[org.clojure/clojure "1.9.0"]
                          [boot/core "2.7.2" :scope "provided"]
                          [adzerk/boot-cljs "2.1.4"]
                          [cheshire "5.8.0"]
                          [camel-snake-kebab "0.4.0"]
                          [com.fasterxml.jackson.core/jackson-databind "2.7.0"]
                          [com.microsoft.azure/azure "1.2.1"]
                          [com.amazonaws/aws-java-sdk-cloudformation "1.11.264"]
                          [com.amazonaws/aws-java-sdk-s3 "1.11.264"]
                          [com.velisco/clj-ftp "0.3.9"]
                          [adzerk/boot-test "1.2.0" :scope "test"]
                          [proto-repl "0.3.1"]
                          [org.clojure/core.match "0.3.0-alpha5"]
                          [metosin/scjsv "0.4.0" :scope "test"]])

(require '[adzerk.boot-test :refer :all])

(def +version+ "0.1.4-SNAPSHOT")

; (proto)repl
(deftask dev
  "Profile setup for development.
  	Starting the repl with the dev profile...
  	boot dev repl"
  []
  (println "Dev profile running")
  (set-env!
   :init-ns 'user
   :source-paths #(into % ["test" "dev"])
   :dependencies #(into % '[[org.clojure/tools.namespace "0.2.11"]]))

  ;; Makes clojure.tools.namespace.repl work per https://github.com/boot-clj/boot/wiki/Repl-reloading
  (require 'clojure.tools.namespace.repl)
  (eval '(apply clojure.tools.namespace.repl/set-refresh-dirs
                (get-env :directories)))

  identity)

;Release
(task-options!
 pom {:project 'siili/boot-hedge
      :version +version+
      :description "Boot tasks to build and deploy a hedge app to cloud"
      :url         "https://github.com/siilisolutions/hedge"
      :scm         {:url "https://github.com/siilisolutions/hedge.git" :dir "../"}
      :license     {"EPL" "http://www.eclipse.org/legal/epl-v10.html"}})

(defn- get-creds []
  (mapv #(System/getenv %) ["CLOJARS_USER" "CLOJARS_PASS"]))

(deftask ^:private collect-clojars-credentials
  "Collect CLOJARS_USER and CLOJARS_PASS from the user if they're not set."
  []
  (fn [next-handler]
    (fn [fileset]
      (let [[user pass] (get-creds), clojars-creds (atom {})]
        (if (and user pass)
          (swap! clojars-creds assoc :username user :password pass)
          (do (println "CLOJARS_USER and CLOJARS_PASS were not set; please enter your Clojars credentials.")
              (print "Username: ")
              (#(swap! clojars-creds assoc :username %) (read-line))
              (print "Password: ")
              (#(swap! clojars-creds assoc :password %)
               (apply str (.readPassword (System/console))))))
        (merge-env! :repositories [["deploy-clojars" (merge @clojars-creds {:url "https://clojars.org/repo"})]])
        (next-handler fileset)))))

(deftask build-jar
  "Build jar and install to local repo."
  []
  (comp (pom) (jar) (install)))

(deftask push-release
  "Deploy release version to Clojars.

  Note: can be used with build-jar task. push-release will pick jars from fileset"
  [f file PATH str                 "The jar file to deploy."
   _ disable-gpg-sign bool         "Disable jar signing using GPG private key."
   k gpg-user-id KEY      str      "The name or key-id used to select the signing key."
   p gpg-passphrase PASS  str      "The passphrase to unlock GPG signing key."]
  (comp
   (collect-clojars-credentials)
   (push
    :file           file
    :gpg-sign       (not disable-gpg-sign)
    :ensure-release true
    :repo           "deploy-clojars"
    :gpg-user-id    gpg-user-id
    :gpg-passphrase gpg-passphrase)))

(deftask push-snapshot
  "Deploy snapshot version to Clojars.

  Note: can be used with build-jar task. push-snapshot will pick jars from fileset"
  [f file PATH str                 "The jar file to deploy."
   _ disable-gpg-sign bool         "Disable jar signing using GPG private key."
   k gpg-user-id KEY      str      "The name or key-id used to select the signing key."
   p gpg-passphrase PASS  str      "The passphrase to unlock GPG signing key."]
  (comp
   (collect-clojars-credentials)
   (push
    :file           file
    :gpg-sign       (not disable-gpg-sign)
    :ensure-snapshot true
    :repo           "deploy-clojars"
    :gpg-user-id    gpg-user-id
    :gpg-passphrase gpg-passphrase)))
