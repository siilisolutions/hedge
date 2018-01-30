(ns hedge.azure.common)

(def azure-context-logger-mock #(println (str "LOG :: " %)))
(goog.object/set azure-context-logger-mock "error" #(println (str "LOG :: " %)))
(goog.object/set azure-context-logger-mock "warn" #(println (str "LOG :: " %)))
(goog.object/set azure-context-logger-mock "info" #(println (str "LOG :: " %)))
(goog.object/set azure-context-logger-mock "verbose" #(println (str "LOG :: " %)))
(goog.object/set azure-context-logger-mock "mocked" #(println (str "LOG :: " %)))
