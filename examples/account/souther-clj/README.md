# souther-clj

A thin Clojure interop layer for Souther-generated code. It carries three things across the
JVM boundary that are otherwise hand-rolled at every call site:

- **`souther.decode`** — `decode` runs a generated `decoder()` over Clojure data (keyword keys are
  accepted) and returns `[:ok value]` or `[:err issues]`, with issues as plain Clojure maps
  (`{:path :code :message}`). `construct` builds a domain value of a given class through its
  `decoder()` — the public construction path for a non-`extends` boundary, since data constructors
  are non-public. `decode!` throws instead of returning `[:err …]`.
- **`souther.behavior`** — `defbehavior` is sugar over `proxy` for implementing an injected
  behavior's abstract base, mirroring the generated `apply` signature.
- **`souther.match`** — `case-of` folds a sealed output union to a value and, at macro-expansion,
  checks that the handler map covers exactly the union's permitted subclasses. This restores at the
  Clojure boundary the totality that Souther's `match` guarantees inside generated code.

The runtime source above refers to no domain type: it works entirely by reflection over the classes
the caller passes in, so it depends only on Clojure and Raoh.

- **`souther.build`** (build-time only) — `generate!` runs `SoutherProcessor` through the JDK
  compiler API to turn a module's `.sou` into classes, so a project can generate from `deps.edn`
  (`clojure -X:gen`) instead of Maven. It imports nothing beyond the JDK; the caller supplies
  `souther-compiler` on the invoking alias's classpath, where it is discovered by ServiceLoader.

## Status

Currently vendored under `examples/account` (on that project's classpath via `souther-clj/src`), to
be developed against a real Souther module. It is written to be lifted out into its own repository
unchanged — this directory already carries a standalone `deps.edn`. Extraction then means adding its
own test fixtures (a small generated module) and publishing coordinates.

## Usage sketch

```clojure
(require '[souther.decode :refer [decode construct]]
         '[souther.behavior :refer [defbehavior]]
         '[souther.match :refer [case-of]])

;; Implement an injected behavior (abstract base CurrentBalance), building results via decoders.
(defbehavior current-balance-impl [ds]
  CurrentBalance
  (apply [account]
    (if-let [row (read-row ds (.value account))]
      (construct Balance (:balance row))
      (construct NoAccount {}))))

;; Decode input at the boundary, then fold the sealed output exhaustively.
(let [[tag req] (decode (WithdrawRequest/decoder) json-body)]
  (when (= tag :ok)
    (case-of WithdrawResult (.apply withdraw req)
             {Withdrawn          on-withdrawn
              InsufficientFunds  on-short
              NoAccount          on-missing})))   ; omit a case → compile error
```
