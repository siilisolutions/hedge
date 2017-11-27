(ns hedge.node)

(defmacro export [e]
  #?@(:clj  `(aset js/module "exports" ~e)
      :cljs `(goog.object/set js/module "exports" ~e)))

(defmacro export [e sub]
  ; FIXME: set module.exports.sub = e
  #?@(:clj  `(aset js/module "exports" ~e)
      :cljs `(goog.object/set js/module "exports" ~e)))

(defmacro cli-main [& body]
  `(set! ~'*main-cli-fn* (fn [] ~@body)))

(defmacro node-module [e]
  `(do (hedge.node/export ~e)
       (hedge.node/cli-main nil)))

(defmacro node-module [e sub]
  `(do (hedge.node/export ~e ~sub)
       (hedge.node/cli-main nil)))
