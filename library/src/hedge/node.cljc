(ns hedge.node)

; FIXME: multiarity macros are hard

(defmacro export1 [e]
  `(goog.object/set js/module "exports" ~e))

(defmacro export2 [e sub]
  `(do
     (let [tmp# (js/Object.)]
       (goog.object/set tmp# ~sub ~e)
       (goog.object/set js/module "exports" tmp#))))

(defmacro cli-main [& body]
  `(set! ~'*main-cli-fn* (fn [] ~@body)))

(defmacro node-module1 [e]
  `(do (hedge.node/export1 ~e)
       (hedge.node/cli-main nil)))

(defmacro node-module2 [e sub]
  `(do (hedge.node/export2 ~e ~sub)
       (hedge.node/cli-main nil)))
