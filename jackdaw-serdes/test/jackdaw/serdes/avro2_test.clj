(ns jackdaw.serdes.avro2-test
  (:require [clojure.test :refer [deftest is testing]]
            [jackdaw.serdes.avro2 :as avro2]
            [clojure.data.json :as json]
            [clojure.spec.test.alpha :as stest]
            [clj-uuid :as uuid]
            [clojure.java.io :as io])
  (:import (org.apache.avro Schema$Parser Schema)
           (org.apache.avro.generic GenericData$Array GenericData$Record GenericData$EnumSymbol)
           (java.util Collection HashMap)
           (org.apache.kafka.common.serialization Serializer Deserializer Serde)
           (io.confluent.kafka.schemaregistry.client MockSchemaRegistryClient)))

(stest/instrument)

(def topic-config
  {:avro/schema (slurp (io/resource "resources/example_schema.avsc"))
   :avro/is-key false
   :schema.registry/url "http://localhost:8081"})

(defn parse-schema [clj-schema]
  (.parse (Schema$Parser.) ^String (json/write-str clj-schema)))

(defn with-mock-client [config]
  (assoc config :schema.registry/client (MockSchemaRegistryClient.)))

(deftest avro-serde
  (testing "schema can be serialized by registry client"
    (let [config (avro2/serde-config :value (with-mock-client topic-config))
          serde (avro2/avro-serde config)]
      (let [msg {:customer-id (uuid/v4)
                 :address {:value "foo"
                           :key-path "foo.bar.baz"}}]
        (let [serialized (-> (.serializer serde)
                             (.serialize "foo" msg))
              deserialized (-> (.deserializer serde)
                               (.deserialize "foo" serialized))]
          (is (= deserialized msg)))))))

(deftest schema-type
  (testing "boolean"
    (let [avro-schema (parse-schema {:type "boolean"})
          schema-type (avro2/schema-type avro-schema)
          clj-data true
          avro-data true]
      (is (= clj-data (avro2/avro->clj schema-type avro-data)))
      (is (= avro-data (avro2/clj->avro schema-type clj-data)))))
  (testing "double"
    (let [avro-schema (parse-schema {:type "double"})
          schema-type (avro2/schema-type avro-schema)
          clj-data 2.0
          avro-data 2.0]
      (is (= clj-data (avro2/avro->clj schema-type avro-data)))
      (is (= avro-data (avro2/clj->avro schema-type clj-data)))))
  (testing "float"
    (let [avro-schema (parse-schema {:type "float"})
          schema-type (avro2/schema-type avro-schema)
          clj-data (float 2)
          avro-data (float 2)]
      (is (= clj-data (avro2/avro->clj schema-type avro-data)))
      (is (= avro-data (avro2/clj->avro schema-type clj-data)))))
  (testing "int"
    (let [avro-schema (parse-schema {:type "int"})
          schema-type (avro2/schema-type avro-schema)
          clj-data 2
          avro-data 2]
      (is (= clj-data (avro2/avro->clj schema-type avro-data)))
      (is (= avro-data (avro2/clj->avro schema-type clj-data)))))
  (testing "long"
    (let [avro-schema (parse-schema {:type "long"})
          schema-type (avro2/schema-type avro-schema)
          clj-data 2
          avro-data 2]
      (is (= clj-data (avro2/avro->clj schema-type avro-data)))
      (is (= avro-data (avro2/clj->avro schema-type clj-data)))))
  (testing "string"
    (let [avro-schema (parse-schema {:type "string"
                                     :name "postcode"
                                     :namespace "com.fundingcircle"})
          schema-type (avro2/schema-type avro-schema)
          clj-data "test-string"
          avro-data "test-string"]
      (is (= clj-data (avro2/avro->clj schema-type avro-data)))
      (is (= avro-data (avro2/clj->avro schema-type clj-data)))))
  (testing "null"
    (let [avro-schema (parse-schema {:type "null"})
          schema-type (avro2/schema-type avro-schema)
          clj-data nil
          avro-data nil]
      (is (= clj-data (avro2/avro->clj schema-type avro-data)))
      (is (= avro-data (avro2/clj->avro schema-type clj-data)))))
  (testing "array"
    (let [avro-schema (parse-schema {:type "array", :items "string"})
          schema-type (avro2/schema-type avro-schema)
          clj-data ["a" "b" "c"]
          avro-data (GenericData$Array. ^Schema avro-schema
                                        ^Collection clj-data)]
      (is (= clj-data (avro2/avro->clj schema-type avro-data)))
      (is (= avro-data (avro2/clj->avro schema-type clj-data)))))
  (testing "map"
    (let [avro-schema (parse-schema {:type "map", :values "long"})
          schema-type (avro2/schema-type avro-schema)
          clj-data {:a 1 :b 2}
          avro-data (doto (HashMap.)
                      (.put "a" 1)
                      (.put "b" 2))]
      (is (= clj-data (avro2/avro->clj schema-type avro-data)))
      (is (= avro-data (avro2/clj->avro schema-type clj-data)))))
  (testing "union"
    (let [avro-schema (parse-schema ["long" "string"])
          schema-type (avro2/schema-type avro-schema)
          clj-data-long 123
          avro-data-long 123
          clj-data-string "hello"
          avro-data-string "hello"]
      (is (= clj-data-long (avro2/avro->clj schema-type avro-data-long)))
      (is (= avro-data-long (avro2/clj->avro schema-type clj-data-long)))
      (is (= clj-data-string (avro2/avro->clj schema-type avro-data-string)))
      (is (= avro-data-string (avro2/clj->avro schema-type clj-data-string)))))
  (testing "enum"
    (let [avro-schema (parse-schema {:type "enum"
                                     :name "Suit"
                                     :symbols ["SPADES"
                                               "HEARTS"
                                               "DIAMONDS"
                                               "CLUBS"]})
          schema-type (avro2/schema-type avro-schema)
          clj-data :SPADES
          avro-data (GenericData$EnumSymbol. avro-schema "SPADES")]
      (is (= clj-data (avro2/avro->clj schema-type avro-data)))
      (is (= avro-data (avro2/clj->avro schema-type clj-data)))))
  (testing "record"
    (let [nested-schema-json {:name "nestedRecord"
                              :type "record"
                              :fields [{:name "a"
                                        :type "long"}]}
          nested-schema-parsed (parse-schema nested-schema-json)
          avro-schema (parse-schema {:name "testRecord"
                                     :type "record"
                                     :fields [{:name "stringField"
                                               :type "string"}
                                              {:name "longField"
                                               :type "long"}
                                              {:name "recordField"
                                               :type nested-schema-json}]})
          schema-type (avro2/schema-type avro-schema)
          clj-data {:stringField "foo"
                    :longField 123
                    :recordField {:a 1}}
          avro-data (doto (GenericData$Record. avro-schema)
                      (.put "stringField" "foo")
                      (.put "longField" 123)
                      (.put "recordField"
                            (doto (GenericData$Record. nested-schema-parsed)
                              (.put "a" 1))))]
      (is (= clj-data (avro2/avro->clj schema-type avro-data)))
      (is (= avro-data (avro2/clj->avro schema-type clj-data))))))
