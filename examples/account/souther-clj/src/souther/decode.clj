(ns souther.decode
  "Decoding and constructing Souther-generated domain values from Clojure data.

   Souther generates, for every data type, a public `decoder()` (a Raoh `Decoder`) that runs the
   type's invariants. Because data constructors are non-public, this decoder is the construction
   path for a boundary that does not `extends` the generated code -- a Clojure `proxy`, or any
   non-JVM-subclass consumer. This namespace wraps that path in Clojure terms: keyword-keyed maps
   are accepted (keys are stringified to the field names the decoder expects), and a decode failure
   comes back as Clojure data (Raoh Issues) rather than a Java Result to pick apart."
  (:require [clojure.walk :as walk])
  (:import [net.unit8.raoh Ok Err Issue Issues]
           [net.unit8.raoh.decode Decoder]
           [clojure.lang Reflector]))

(defn- ->raw
  "Coerce Clojure input into the external representation a generated decoder expects: map keys
   become the field-name strings (recursively), so keyword-keyed maps work directly."
  [x]
  (walk/stringify-keys x))

(defn- issue->map [^Issue issue]
  {:path (str (.path issue))
   :code (.code issue)
   :message (.message issue)})

(defn- issues->data [^Issues issues]
  (mapv issue->map (.asList issues)))

(defn decode
  "Decode `raw` (Clojure data; keyword keys allowed) with a generated `decoder`. Returns
   `[:ok value]` on success or `[:err issues]` on failure, where issues is a vector of
   `{:path :code :message}` maps."
  [^Decoder decoder raw]
  (let [result (.decode decoder (->raw raw))]
    (if (instance? Ok result)
      [:ok (.value ^Ok result)]
      [:err (issues->data (.issues ^Err result))])))

(defn decode!
  "Like `decode`, but returns the value or throws `ex-info` carrying the issues under `:issues`.
   Use at a trusted seam (e.g. building a result whose invariants cannot fail); prefer `decode` at
   an untrusted boundary so the caller can report the issues."
  [decoder raw]
  (let [[tag v] (decode decoder raw)]
    (if (= tag :ok)
      v
      (throw (ex-info "decode failed" {:issues v})))))

(defn- decoder-of [klass]
  (Reflector/invokeStaticMethod ^Class klass "decoder" (object-array 0)))

(defn construct
  "Build an instance of the generated data type `klass` from Clojure `raw`, through the type's
   public `decoder()` (which enforces its invariants). Throws `ex-info` if `raw` violates them.
   For a newtype pass the bare value (`(construct Balance 700)`); for a unit case pass `{}`; for a
   record pass a keyword-keyed map."
  [klass raw]
  (decode! (decoder-of klass) raw))
