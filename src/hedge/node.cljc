(ns hedge.node)

(defmacro export [e]
  `(aset js/module "exports" ~e))


(defmacro cli-main [& body]
  `(set! ~'*main-cli-fn* (fn [] ~@body)))

(defmacro node-module [e]
  `(do (hedge.node/export ~e)
       (hedge.node/cli-main nil)))
