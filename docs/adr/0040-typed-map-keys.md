# ADR-0040: A Map key may be a String-backed newtype, not only String

Status: Accepted. The in-language type and operations are implemented; the boundary codec for a
non-String key is deferred (a newtype-keyed map is a compile error as a data field or behavior I/O
until it lands).

## Context

`Map` was fixed to `Map<String, V>`. A domain map is almost always keyed by an identifier that has a
type — `Map<商品ID, 在庫>`, `Map<従業員ID, 権限>` — and forcing the key to `String` throws that type
away: `商品ID` and `従業員ID` become the same key type, and a caller can pass one where the other is
meant. It also runs against Souther's whole stance of putting distinctions in types (closed
construction, newtypes). Elm's `Dict` and F#'s `Map` both key by a typed value.

The key cannot be an arbitrary type, though. A `Map`'s external representation is a JSON object, whose
keys are strings, so a key type must be renderable as (and parseable from) a bare string. A
String-backed newtype (`data 商品ID = String`) is exactly that: nominally distinct, bare-string
represented (ADR-0014).

## Decision

A `Map` key is `String` or a **String-backed newtype** (`data X = String`) — no other type. `MapOf`
carries both a key and a value type. The key is validated at type resolution: `String`, a newtype
over `String`, or (inside `core` only) a key type variable `'k` that monomorphises to one of those.

- **Runtime.** The map is keyed by the key value itself — a `String`, or the newtype wrapper — and
  java's `Map` compares keys by their value equality (ADR-0009, the equality every data already has),
  so `containsKey(商品ID("P-01"), m)` matches a stored `商品ID("P-01")`. `Map.keys` returns
  `List<商品ID>`, keeping the type.
- **Standard library** generalises over the key: `containsKey` / `insert` / `remove` / `singleton` /
  `get` / `keys` / `toList` / `fromList` take and return the key type. `Map.empty` is the
  empty-collection bottom in both key and value, fixed by context like `[]`.
- **Boundary codec is deferred.** Encoding a `Map<商品ID, V>` to a JSON object must render each key
  `商品ID` to its bare string, and decoding must construct a `商品ID` from each string key (checking
  its invariant, and — for an invariant-bearing key — able to fail). That key codec is not yet
  implemented, so a `Map` with a non-`String` key **may not** be a data field or a behavior's
  input/output; it is a clear compile error there. Such a map is fully usable inside a behavior body.

## Consequences

The type-level win lands immediately: `Map<商品ID, 在庫>` and `Map<従業員ID, 権限>` are distinct types,
and the key of a lookup is checked against the map's key type, so the two cannot be confused. Building
and querying a keyed map — the aggregation the review asked for (add/update/remove an entry by a typed
key) — works in a behavior body.

What is deferred is only the *representation* at the boundary. A behavior that must receive or return
a keyed map still keys by `String` for now and wraps the newtype internally; when the typed-key codec
lands, the field/I·O restriction lifts with no change to the type or the operations. Keeping the
restriction an explicit error (rather than silently treating the key as a `String`) means a model
never mis-encodes a typed key.

The key stays String-backed on purpose: admitting an arbitrary value key would need an entry-array
representation (a JSON object cannot key by a non-string), which changes the boundary format. A
String-backed newtype keeps the `Map` a plain JSON object — the minimal, representation-preserving
step (the option chosen over a value-key design).

## References

- ADR-0014 (a newtype is nominal and bare-string represented — why a String-backed newtype is a valid
  key)
- ADR-0009 (value equality — how a newtype-keyed map matches keys)
- ADR-0004 (derived codecs, souther-runtime is Raoh-free — the constraint the deferred key codec must
  meet)
- Specification: `[#collections]`, `[#stdlib-map]`
- Prior art: Elm `Dict`, F# `Map` (typed keys)
