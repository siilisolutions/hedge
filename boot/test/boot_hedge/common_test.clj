(ns boot-hedge.common_test
  (:require [clojure.test :refer :all]
            [boot-hedge.common.core :refer :all]))

; note that the class names are prefixed with ', we are testing data structure, not functionality
(def hedge-edn
  {:api {"api1" {:handler 'my_cool_function.core/crunch-my-data :authorization :anonymous}
         "api2" {:handler 'my_cool_function.core/hello :authorization :function}}

   :timer {"timer1" {:handler 'my_cool_function.core/timer-handler :cron "*/10 * * * *"}
           "timer2" {:handler 'my_cool_function.core/timer-handler-broken :cron "*/10 * * * *"}}
          
   :disabled-api {"api2" {:handler 'my_cool_function.core/hello :authorization :anonymous}
                 "api3" {:handler 'my_cool_function.core/crunch-my-data :authorization :anonymous}}})

(deftest one-handler-config-test
  (testing "Tests if a one-handler-config can be created with hedge.edn input"
    (testing "if configs can be handled individually"      
      
      (is (= (one-handler-config "api1" hedge-edn) 
             {:type :api
              :function {:handler 'my_cool_function.core/crunch-my-data :authorization :anonymous}
              :path "api1"}))
              
      (is (= (one-handler-config "timer2" hedge-edn) 
             {:type :timer
              :function {:handler 'my_cool_function.core/timer-handler-broken :cron "*/10 * * * *"}
              :path "timer2"})))))

(deftest one-handler-configs-test
  (testing "Tests if a sequence of one-handler-configs can be created with hedge.edn input"
    (testing "if configs can be handled individually"      
      (let [configs (one-handler-configs hedge-edn)]
        (is (= (-> configs count) 
               4))

        (is (= (-> (filter #(= (-> % :type) :api) configs) count)
              2))

        (is (= (-> (filter #(= (-> % :type) :timer) configs) count)
              2))

        (is (zero? (-> (filter #(= (-> % :type) :disabled-api) configs) count)))

        (is (= (-> (filter #(= (-> % :path) "api1") configs) first)
               {:type :api
                :function {:handler 'my_cool_function.core/crunch-my-data :authorization :anonymous}
                :path "api1"}))))))
