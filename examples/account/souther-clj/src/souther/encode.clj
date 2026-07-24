(ns souther.encode
  "Encoding Souther-generated values back to Clojure data -- the inverse of `souther.decode`.

   Every generated type has a public `encoder()` that yields its external representation: a newtype
   encodes to its bare underlying value, a record/sum to a map (with nested newtypes already
   unwrapped). That is exactly the unwrapping a Clojure boundary wants, so `encode` runs the
   encoder and hands back Clojure data -- no chains of `.value` / field accessors."
  (:import [net.unit8.raoh.encode Encoder]
           [clojure.lang Reflector]
           [java.util Map List]))

(defn- encoder-of [^Class klass]
  (Reflector/invokeStaticMethod klass "encoder" (object-array 0)))

(defn- ->clj
  "Java external representation -> Clojure data: maps become keyword-keyed maps, lists become
   vectors, scalars pass through, all recursively."
  [x]
  (cond
    (instance? Map x) (persistent!
                       (reduce-kv (fn [m k v] (assoc! m (keyword (str k)) (->clj v)))
                                  (transient {}) (into {} x)))
    (instance? List x) (mapv ->clj x)
    :else x))

(defn encode
  "Encode a Souther-generated `value` to Clojure data through its generated `encoder()`. A newtype
   becomes its bare underlying value; a record/sum becomes a keyword-keyed map with nested newtypes
   already unwrapped. The inverse of `souther.decode/decode`."
  [value]
  (->clj (.encode ^Encoder (encoder-of (class value)) value)))

(defn unwrap
  "The bare value inside a newtype wrapper, via `encode`. Throws if `value` is not a single-value
   wrapper (its encoded form is a map/list), so a call site reads as a claim that the value is a
   newtype."
  [value]
  (let [out (encode value)]
    (when (or (map? out) (vector? out))
      (throw (ex-info "unwrap expects a newtype wrapper, not a record/sum"
                      {:class (.getName (class value))})))
    out))
