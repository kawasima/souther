(ns souther.match
  "Exhaustive folding of a Souther sealed output union at the Clojure boundary.

   Souther's `match` is total: every case of a union is handled. That guarantee is a compile-time
   property of the generated code and is lost the moment a value crosses into Clojure, where a
   forgotten case just silently falls through. `case-of` restores it: the generated union interface
   is sealed, so its permitted subclasses are known by reflection, and `case-of` checks -- at macro
   expansion -- that the handler map covers exactly those cases before emitting the dispatch."
  (:require [clojure.set :as set]))

(defn- permitted-subclasses [^Class union]
  (let [ps (.getPermittedSubclasses union)]
    (when (nil? ps)
      (throw (ex-info "case-of expects a sealed union type" {:union (.getName union)})))
    (set ps)))

(defmacro case-of
  "Dispatch `value` to a handler in `handlers`, a map literal of {CaseClass handler-fn}. Each
   handler-fn is called with the value. `union` is the sealed union class the cases belong to.

   At macro-expansion time this verifies that `handlers` covers exactly the union's permitted
   subclasses -- a missing or stray case is a compile error, carrying Souther's match totality
   across the boundary."
  [union value handlers]
  (let [union-cls (resolve union)]
    (when-not (instance? Class union-cls)
      (throw (ex-info "case-of: unknown union class" {:union union})))
    (let [permitted (permitted-subclasses union-cls)
          handled (into {} (for [[sym f] handlers]
                             (let [c (resolve sym)]
                               (when-not (instance? Class c)
                                 (throw (ex-info "case-of: unknown case class" {:case sym})))
                               [c f])))
          covered (set (keys handled))
          missing (set/difference permitted covered)
          stray (set/difference covered permitted)]
      (when (or (seq missing) (seq stray))
        (throw (ex-info "case-of is not exhaustive"
                        {:union (.getName union-cls)
                         :missing (mapv #(.getName ^Class %) missing)
                         :stray (mapv #(.getName ^Class %) stray)})))
      (let [v (gensym "value")]
        `(let [~v ~value]
           (condp instance? ~v
             ~@(mapcat (fn [[sym f]] [sym `(~f ~v)]) handlers)
             (throw (ex-info "case-of: no case matched"
                             {:class (.getName (class ~v))}))))))))
