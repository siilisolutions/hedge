(set-env!
  :source-paths #{"src"}
  :dependencies '[[org.clojure/clojurescript "1.9.946"]
                  [adzerk/boot-cljs "1.7.228-2" :scope "test"]
                  [crisptrutski/boot-cljs-test "0.3.4" :scope "test"]
                  [speclj "3.3.2" :scope "test"]
                  [org.clojars.akiel/async-error "0.2" :scope "test"]
                  [camel-snake-kebab "0.4.0"]
                  [org.clojure/core.async "0.4.474"]
                  [com.taoensso/timbre "4.10.0"]
                  [binaryage/oops "0.5.8"]])

(require
  '[adzerk.boot-cljs :refer [cljs]]
  '[crisptrutski.boot-cljs-test :refer [test-cljs]])

(def +version+ "0.1.2")

(deftask dev
  "Watch/compile files in development"
  []
  (comp
    (watch)
    (cljs :source-map true
          :optimizations :none
          :compiler-options {:target :nodejs})
    (target)))

(deftask prod
  "Compile for production"
  []
  (comp
    (cljs :optimizations :advanced
          :compiler-options {:target :nodejs})
    (sift :include #{#"\.out" #"\.edn" #"\.cljs"} :invert true)

    (target)))

(deftask testing []
  (set-env! :source-paths #(conj % "test"))
  identity)

(ns-unmap 'boot.user 'test)

(deftask test []
  (comp (testing)
        (test-cljs :js-env :node
                   :exit?  true)))

(deftask auto-test []
  (comp (testing)
        (watch)
        (test-cljs :js-env :node)))

(deftask cljs-repl
  "start a node based repl"
  []
  (repl :eval '(do (require 'cljs.repl) (require 'cljs.repl.node) (cljs.repl/repl (cljs.repl.node/repl-env)))))

; Release
(task-options!
 pom {:project 'siili/hedge
      :version +version+
      :description "A serverless framework fo cljs"
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
