(ns hedge.azure.timbre-test
  (:require [cljs.test :refer-macros [deftest is testing run-tests]]
            [taoensso.timbre :as timbre
                              :refer (log  trace  debug  info  warn  error  fatal  report
                                      logf tracef debugf infof warnf errorf fatalf reportf
                                      spy get-env log-env)]
            [hedge.azure.timbre-appender :refer [timbre-appender]]
            [hedge.azure.common :refer [azure-context-logger-mock]]))
(comment
  (deftest foo
    (cljs.pprint/pprint timbre/*config*)
    (info "aaa")
    (debug "bbb")
    (warn "ccc")
    (trace "xyz")
  
    ; note: only this prints stack
    (timbre/with-merged-config {:level :trace}
      (trace "a"))
  
    ; TODO: maybe add stack print if required for azure appender
    (timbre/with-merged-config {:level :trace
                                :appenders {:console nil
                                            :azure (timbre-appender azure-context-logger-mock)}}
      (cljs.pprint/pprint timbre/*config*)
      (trace "halp"))

    ; does not show, reverted back to default configuration with default appender
    (trace "not")))
