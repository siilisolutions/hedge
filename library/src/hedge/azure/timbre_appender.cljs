(ns hedge.azure.timbre-appender)

(defn timbre-appender
  [azure-contex-log]
  {:enabled?   true
   :async?     false
   :min-level  nil
   :output-fn  :inherit
   :fn
   (fn [data]
     (let [{:keys [level output_]} data]
       (case level
         ; verbose property is being minified
         ; TODO: add extern for azure's context logger or use simple optimization
         :trace ((goog.object/get azure-contex-log "verbose") (force output_))
         :debug ((goog.object/get azure-contex-log "verbose") (force output_))
         :info (.info azure-contex-log (force output_))
         :warn (.warn azure-contex-log (force output_))
         :error (.error azure-contex-log (force output_))
         :fatal (.error azure-contex-log (force output_))
         :report (.info azure-contex-log (force output_)))))})
