# ADR-0039: Set is a collection type; its external representation is a deduplicated array

Status: Accepted

## Context

Souther had two collection types, `List<T>` and `Map<String, T>`. A domain often needs a third: a
collection with no duplicates and no significant order — a set of tags, a set of permissions, a set
of applicable product codes. Modeled as a `List`, "no duplicates" is not in the type; it has to be
asserted with an invariant, and the stdlib had no `distinct` to build one. Elm (`Set`) and F#
(`Set`) both carry a set type, and the domain vocabulary distinguishes `List<タグ>` (order and
repetition matter) from `Set<タグ>` (neither does).

Unlike a tuple or a function, a set *does* have an external representation — a JSON array — so it can
cross the codec boundary and be a data field or a behavior's input/output, the way `List` and `Map`
are.

## Decision

Add `Set<T>` as a third collection type, alongside `List` and `Map`.

- **Value semantics.** A `Set` is immutable and unordered, with no duplicate elements, compared by
  the elements' value equality (ADR-0009) — the same equality the generated `equals`/`hashCode`
  already give every data. Building operations (`insert`, `remove`, the algebra) return a fresh set.
- **External representation is a JSON array, deduplicated on decode.** The derived decoder reads the
  array with the existing list decoder and maps the result through a `List -> Set` deduplication;
  duplicates in the input are dropped, not rejected (Elm's `Set.fromList`, F#'s `Set.ofList`). The
  encoder writes the set back as an array. Encode and `toList` use first-seen (insertion) order, so
  the representation is deterministic even though the type is semantically unordered.
- **Covariant and immutable**, like `List`/`Map`: a `Set<A>` is assignable to a `Set<S>` when `A` is a
  case of `S`, sound because a set cannot be mutated.
- **Standard library** in the reserved `souther.set` namespace: `singleton`, `insert`, `remove`,
  `contains`, `union`, `intersect`, `difference`, `size`, `isEmpty`, `toList`, `fromList`. `Set.empty`
  is a compiler built-in whose element type is fixed by context — the empty-collection bottom the
  `[]` list and `Map.empty` already use — so it takes no argument to learn a type variable from.
- **No set literal.** A set is built from `Set.empty` / `singleton` / `insert` / `fromList`; there is
  no `{a, b}` surface form (braces are records and blocks).

## Consequences

The domain can now state "these are distinct" in the type rather than in an invariant, and the
distinction between an ordered list and an unordered set is visible at every field and signature. The
set algebra (`union` / `intersect` / `difference`) covers the common membership computations without a
fold.

Deduplication happens exactly once, at the boundary: a `Set` value in the language can never hold a
duplicate, so nothing downstream re-checks. The decode-time `List -> Set` map is emitted with an
`invokedynamic` binding `Sets::fromList` as a `Function`, so the dedup lives in the Raoh-free runtime
(`souther-runtime` stays Raoh-independent, ADR-0004) and the generated decoder is the existing list
decoder plus one `map`.

Choosing silent deduplication over a decode error is the Elm/F# convention: a repeated element in the
input is not a domain outcome, it is just how a set was written down. A model that genuinely wants to
reject duplicate input can still decode a `List` and check.

## References

- ADR-0009 (Decimal / value equality — a set's membership and dedup use the same value equality)
- ADR-0004 (derived codecs, souther-runtime is Raoh-free — why the dedup map is bound via
  invokedynamic rather than a Raoh combinator)
- ADR-0028 (the empty-collection bottom `[]`; `Set.empty` follows it)
- Specification: `[#collections]`, `[#stdlib-set]`
- Prior art: Elm `Set` (`Set.fromList` dedups); F# `Set` / `Set.ofList`
