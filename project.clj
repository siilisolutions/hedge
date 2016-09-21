(defproject siili/hedge "0.1.0-SNAPSHOT"
  :description "a serverless solution for clojure"
  :url "http://github.com/siilisolutions/hedge"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]]
  :profiles {:dev {:dependencies [[speclj "3.3.1"]]}}
  :plugins [[speclj "3.3.1"]]
  :test-paths ["spec"])
