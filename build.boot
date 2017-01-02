(set-env!
  :source-paths #{"src"}
  :dependencies '[[adzerk/boot-cljs "1.7.228-2" :scope "test"]
                  [crisptrutski.boot-cljs-test :refer [test-cljs]]])

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

(deftask testing []
  (set-env! :source-paths #(conj % "test/cljs"))
  identity)

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

