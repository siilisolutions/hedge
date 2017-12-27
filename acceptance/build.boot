(set-env!
  :source-paths #{"test"}
  :resource-paths  #{"spec"}
  :dependencies '[[org.clojure/clojure "1.8.0"]
                  [camel-snake-kebab "0.4.0"]
                  [junit/junit "4.12"]
                  [org.concordion/concordion "2.1.0"]
                  [radicalzephyr/boot-junit "0.2.1"]
                  [radicalzephyr/cljunit "0.2.0"]
                  [zprint "0.4.2"]
                  [clojure-future-spec "1.9.0-alpha17"]
                  [backtick "0.3.4"]
                  [me.raynes/conch "0.8.0"]
                  [http-kit "2.2.0"]])


(require '[radicalzephyr.boot-junit :refer (junit)])

(require '[boot.pod  :as pod])

(require '[boot.util  :as util])

(require '[clojure.string :as str])

(def pod-deps
  '[[radicalzephyr/cljunit "0.2.0"]])

(defn- init [fresh-pod]
  (pod/with-eval-in fresh-pod
    (require '[cljunit.core :refer [run-tests-in-classes]])))

(defn- construct-class-name [prefix path]
  (let [path-seq (-> path
                     (str/replace prefix "")
                     (str/replace #"\.md$" "")
                     (str/split #"/"))]
    (->> path-seq
         (remove empty?)
         (interpose ".")
         (apply str))))

(defn- path->class-name [class-name]
  (let [prefix (str (tmp-dir class-name))
        file (tmp-file class-name)]
    (construct-class-name prefix (str file))))

(deftask concordion
  "Run the concordion with jUnit test runner."
  [c class-names  CLASSNAME #{str} "The set of Java class names to run tests from."
   p packages     PACKAGE   #{str} "The set of package names to run tests from."]
  (let [worker-pods (pod/pod-pool (update-in (get-env) [:dependencies] into pod-deps)
                                  :init init)
        tgt (tmp-dir!)
        cd (clojure.java.io/file tgt "concordion")]
    (cleanup (worker-pods :shutdown))
    (with-pre-wrap fileset
      (let [worker-pod (worker-pods :refresh)
            spec-files (by-ext [".md"] (input-files fileset))
            class-files (by-ext [".class"] (output-files fileset))
            all-class-names (map path->class-name spec-files)]

        (when-not (seq class-files)
          (util/warn "No .class files found in `output-files`, did you forget to run `aot` or `javac`?\n"))
        (if-let [result (try
                          (.mkdirs cd)
                          (pod/with-eval-in worker-pod

                            (System/setProperty "concordion.output.dir" ~(.getPath cd))
                            (run-tests-in-classes '~all-class-names
                                                 :classes ~class-names
                                                 :packages ~packages))
                          (catch ClassNotFoundException e
                            (util/warn "Could not load class: %s...\n" (.getMessage e))
                            {:failures 1}))]
          (when (> (:failures result) 0)
            (throw (ex-info "Some tests failed or errored" {})))
          (util/warn "Nothing was tested.")))
      (-> fileset (add-asset tgt) commit!))))

(deftask test
  "Compile and run my concordion tests."
  []
  (comp (aot :all true)
        (concordion)))
