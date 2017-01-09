(set-env!
  :source-paths #{"src"}
  :dependencies '[[adzerk/boot-cljs "1.7.228-2" :scope "test"]
                  [speclj "3.3.2" :scope "test"]
                  [org.clojure/clojurescript "1.9.293"]
                  [crisptrutski/boot-cljs-test "0.3.0" :scope "test"]
                  ])

(require
  '[adzerk.boot-cljs :refer [cljs]]
  '[crisptrutski.boot-cljs-test :refer [test-cljs]])

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

