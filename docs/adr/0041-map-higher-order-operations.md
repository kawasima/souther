# ADR-0041: Map's higher-order operations fold over the entries; update is a value step, not an Option step

Status: Accepted

## Context

`Map` had accessors (`get`, `containsKey`, `keys`, `values`), building operations (`empty`,
`singleton`, `insert`, `remove`, `isEmpty`, `size`), and the entry-list conversions (`toList`,
`fromList`), but nothing that takes a step function. A domain that keeps state in a
`Map<商品ID, 在庫>` could not aggregate over it (sum the stock), transform its values, or update one
key's value without leaving the language for a Java binding. Elm's `Dict` and F#'s `Map` both carry
`fold`, `map`, and `update`, and the specification already named these three as the missing piece.

Two of Souther's own rules shape how they can be added. The standard library is written in Souther
over one privileged loop, `List.fold` (ADR-0028); a second privileged loop for maps would be a new
primitive to justify. And `Option` is not a surface-writable type (ADR-0011): a value cannot be
written as `Some v`, `None` is not a value expression, and a bare value does not wrap into an
`Option` position in the language. So a step function cannot return an `Option`.

## Decision

Add `fold`, `map`, and `update` to `souther.map`, all self-hosted — no new intrinsic, no map loop.

- **`fold(step, seed, m)` folds `List.fold` over the entries.** `Map.fold` is
  `List.fold(step', seed, Map.toList(m))`: the one list-fold loop stays the sole primitive, and the
  entries combine in the map's deterministic hash order (`toList`'s order, [#stdlib-map]), so a fold
  that combines its values reproduces the same result each run. The step is `(acc, key, value)`, the
  key and value passed separately — F#'s `Map.fold` folder is `state key value`, Elm's `Dict.foldl`
  is `k v acc`. The entry tuple `toList` yields is destructured inside the fold and handed on.
- **`map(f, m)` keeps the keys and rewrites the values**, `f` being `(key, value) -> value` (Elm's
  `Dict.map`, F#'s `Map.map`). It grows a fresh map through `Map.fold`, inserting `f(key, value)`
  under each key.
- **`update(key, f, m)` rewrites one present key's value with `f: ('a) -> 'a`; an absent key is a
  no-op.** This is *not* Elm's `Dict.update : comparable -> (Maybe v -> Maybe v) -> Dict -> Dict`.
  Elm's step both reads and returns a `Maybe`, so it can insert a new key (`Nothing -> Just v`) and
  remove one (`Just v -> Nothing`) in the same call. In Souther that step is unwritable: `Option` is
  not a surface type (ADR-0011), so `f` can neither take nor return one. `update` therefore covers
  only the "change what is already there" case; a new key is added with `insert` and a key is dropped
  with `remove`.

## Consequences

The three per-key patterns a stateful map needs — aggregate, transform, modify — are now in the
language, and the review's `Map<商品ID, 在庫>` (issue stock, tally, update one key) no longer falls
through to a Java binding. `fold` being `List.fold` over `toList` means every property already true
of list folding — the inlined step, the empty-map bottom, the deterministic order — holds for maps
for free; `map` and `update` inherit it in turn.

`update`'s narrower shape is a visible consequence of ADR-0011, not an oversight. Modeling "insert or
remove depending on what is there" through a single `Maybe -> Maybe` step is an Elm idiom that leans
on `Maybe` being an ordinary constructible value. Souther deliberately keeps absence in the domain as
a named sum (`-> 会員 | 会員なし`) rather than an `Option` users pass around, so the idiom has no home
here; `insert` / `remove` / `update` split the three cases that Elm's one function merges. If a later
change ever makes `Option` surface-writable, `update` could widen to the Elm shape without breaking
the value-step form (a value step is the `Just v -> Just (f v)` case of it).

## References

- ADR-0028 (the stdlib is Souther over one privileged `List.fold`; `Map.fold` adds no second loop)
- ADR-0011 (`Option` is not a surface-writable type — why `update`'s step is `value -> value`)
- ADR-0037 (tuple types in signatures — `toList`'s `List<(K, V)>`, which `fold` destructures)
- ADR-0040 (typed map keys — the key a step receives is `String` or a String-backed newtype)
- Specification: `[#stdlib-map]`
- Prior art: Elm `Dict.foldl` / `Dict.map` / `Dict.update`; F# `Map.fold` / `Map.map`
