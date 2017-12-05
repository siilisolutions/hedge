(ns boot-hedge.aws
  {:boot/export-tasks true}
  (:require
   [boot.core          :as c]
   [boot.task.built-in :refer [sift target]]
   [adzerk.boot-cljs :refer [cljs]]
   [boot-hedge.lambda :refer [read-conf generate-files]]))

(c/deftask ^:private function-app
  []
  (c/with-pre-wrap fs
    (-> fs
        read-conf
        (generate-files fs))))

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
; * rename task later if deployment target for different cloud types is resolved
(c/deftask deploy-to-target
  "Build function app(s) and store output to target"
  [O optimizations LEVEL kw "The optimization level."]
  (comp
    (compile-function-app :optimizations (or optimizations :simple))
    (sift :include #{#"\.edn" #"\.cljs"} :invert true)
    (target)))
