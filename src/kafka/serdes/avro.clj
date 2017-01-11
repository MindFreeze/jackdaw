(ns kafka.serdes.avro
  (:require [clj-uuid :as uuid]
            [kafka.serdes.avro-schema :as avro-schema])
  (:import [io.confluent.kafka.serializers KafkaAvroDeserializer KafkaAvroSerializer]
           [org.apache.kafka.common.serialization Serdes Serializer Deserializer]))

(set! *warn-on-reflection* true)

(defn avro-record
  "Creates an avro record for serialization, from a clojure type.

  - Clojure maps are converted to a GenericDataRecord.
  - UUIDs are converted to strings if schema is a logical UUID.
  - Primitive types are passed through to the avro serializer."
  [msg schema]
  (cond
    (map? msg) (avro-schema/map->generic-record schema msg)
    (and (uuid/uuid? msg)
         (avro-schema/uuid-schema? (avro-schema/parse-schema schema))) (str msg)
    :else msg))

(deftype CljAvroSerializer [^Serializer serializer schema]
  Serializer
  (close [this]
    (.close serializer))
  (configure [this configs key?]
    (.configure serializer configs key?))
  (serialize [this topic msg]
    (.serialize serializer topic (avro-record msg schema))))

(defn avro-serializer
  "Makes an avro serializer."
  ([schema config key?]
   (avro-serializer nil schema config key?))
  ([registry-client schema {:keys [schema-registry-url]} key?]
   (let [serializer (KafkaAvroSerializer. registry-client)]
     (when schema-registry-url
       (let [config (java.util.HashMap.)]
         (.put config "schema.registry.url" schema-registry-url)
         (.configure serializer config key?)))
     (CljAvroSerializer. serializer schema))))

(deftype CljAvroDeserializer [^Deserializer deserializer schema]
  Deserializer
  (close [this]
    (.close deserializer))
  (configure [this configs key?]
    (.configure deserializer configs key?))
  (deserialize [this topic msg]
    (let [v (.deserialize deserializer topic msg)]
      (if (and (string? v)
               (avro-schema/uuid-schema? (avro-schema/parse-schema schema)))
        (uuid/as-uuid v)
        (avro-schema/generic-record->map v)))))

(defn avro-deserializer
  "Makes an avro deserializer"
  ([schema config key?]
   (avro-deserializer nil schema config key?))
  ([registry-client schema {:keys [schema-registry-url]} key?]
   (let [deserializer (KafkaAvroDeserializer. registry-client)]
     (when schema-registry-url
       (let [config (java.util.HashMap.)]
         (.put config "schema.registry.url" schema-registry-url)
         (.configure deserializer config key?)))
     (CljAvroDeserializer. deserializer schema))))

(defn avro-serde
  "Creates an avro serde."
  [config json-schema key?]
  (Serdes/serdeFrom (avro-serializer json-schema config key?)
                    (avro-deserializer json-schema config key?)))
