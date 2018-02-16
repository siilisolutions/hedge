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
                          [adzerk/bootlaces "0.1.13" :scope "test"]
                          [adzerk/boot-test "1.2.0" :scope "test"]
                          [proto-repl "0.3.1"]
                          [org.clojure/core.match "0.3.0-alpha5"]
                          [metosin/scjsv "0.4.0" :scope "test"]])

(require '[adzerk.bootlaces :refer :all])
(require '[adzerk.boot-test :refer :all])

(def +version+ "0.1.2")

(bootlaces! +version+)

(task-options!
 pom {:project 'siili/boot-hedge
      :version +version+
      :description "Boot tasks to build and deploy a hedge app to cloud"
      :url         "https://github.com/siilisolutions/hedge"
      :scm         {:url "https://github.com/siilisolutions/hedge.git" :dir "../"}
      :license     {"EPL" "http://www.eclipse.org/legal/epl-v10.html"}})

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
