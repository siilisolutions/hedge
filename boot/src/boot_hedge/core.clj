(ns boot-hedge.core
  {:boot/export-tasks true}
  (:require
    [boot.core          :as c]
    [boot.util          :as util]
    [boot-hedge.function-app :refer [read-conf generate-files]]))



(c/deftask function-app
  []
  (c/with-pre-wrap fs
    (-> fs
        read-conf
        (generate-files fs))))

(c/deftask azure
  []
  (util/info "building an azure function app"))
