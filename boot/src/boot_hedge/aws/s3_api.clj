(ns boot-hedge.aws.s3-api
  (:require [clojure.java.io :refer [file]])
  (:import [com.amazonaws.services.s3 AmazonS3ClientBuilder]
           [com.amazonaws.auth DefaultAWSCredentialsProviderChain]
           [com.amazonaws.regions Regions]))

(defn client
  "Creates AmazonCloudFormationClient which is used by other API methods"
  []
  (-> (AmazonS3ClientBuilder/standard)
   (.withRegion Regions/EU_WEST_3)
   (.withCredentials (DefaultAWSCredentialsProviderChain.))
   (.build)))

(defn create-bucket
  [client bucket-name]
  (.createBucket client bucket-name))

(defn get-bucket
  [client bucket-name]
  (as-> client n
      (.listBuckets n)
      (first (filter (fn [x] (= (.getName x) bucket-name)) n)))) 

(defn bucket-exists-globally?
  [client bucket-name]
  (.doesBucketExistV2 client bucket-name))

(defn put-object
  [client bucket-name key filename]
  (.putObject client bucket-name key (if (string? filename) 
                                       (file filename) 
                                       filename)))

(defn ensure-bucket
  [client bucket-name]
  (let [bucket (get-bucket client bucket-name)]
    (if bucket 
      bucket
      (if (not (bucket-exists-globally? client bucket-name))
        (create-bucket client bucket-name)
        (throw (Exception. "Bucket owned by someone else"))))))

#_( 
    (use 'clojure.stacktrace)
    (print-stack-trace *e 55)
    (def c (client))
    (bucket-exists-globally? c "hedge-test-jk")
    (get-bucket c "hedge-test-jk")
    (get-bucket c "hedge-test-jk-fghjaksjasjasjsa")
    (ensure-bucket c "hedge-test-jk")
    (ensure-bucket c "hedge-test-jk-22")
    (put-object c "hedge-test-jk-22" "foo.yml" "/Users/janne.kujanpaa/src/hedge-example-aws/test-deploy/deploy.yml"))
