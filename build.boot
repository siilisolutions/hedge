(set-env!
  :source-paths #{"src"}
  :dependencies '[[adzerk/boot-cljs "1.7.228-2" :scope "test"]
                  [org.clojure/clojurescript "1.7.228"                 ]
                  
                  ])

(require
  '[adzerk.boot-cljs :refer [cljs]])

(deftask dev
  "Watch/compile files in development"
  []
  (comp
    (watch)
    (cljs :source-map true
          :optimizations :none
          :compiler-options {:target :nodejs})
    (target)))

(deftask cljs-repl
  "start a node based repl"
  []
  (repl :eval '(do (require 'cljs.repl) (require 'cljs.repl.node) (cljs.repl/repl (cljs.repl.node/repl-env)))))

