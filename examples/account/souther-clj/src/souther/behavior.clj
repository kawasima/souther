(ns souther.behavior
  "Implementing a Souther injected behavior from Clojure.

   A `required`/injected behavior is generated as an abstract base class (it carries the protected
   factories for its declared output cases, aimed at a Java subclass). Clojure implements it with
   `proxy`. Because a proxy cannot reach those protected factories, results are built through the
   public `decoder()` instead -- see `souther.decode/construct`. `defbehavior` is the small amount
   of sugar over `proxy` that names the implementation and mirrors the generated `apply` signature.")

(defmacro defbehavior
  "Define `name` as a factory fn: its `deps` vector closes over the implementation's dependencies,
   and it returns a `proxy` of the abstract injected-behavior class `base` implementing the single
   method form `(apply [args...] body...)`. Build return values with `souther.decode/construct`.

     (defbehavior current-balance-impl [ds]
       CurrentBalance
       (apply [account] ... (construct Balance n) ...))"
  [name deps base method]
  `(defn ~name ~deps
     (proxy [~base] []
       ~method)))
