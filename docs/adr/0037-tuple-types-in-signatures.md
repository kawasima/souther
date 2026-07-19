# ADR-0037: Tuple types are writable in helper and stdlib signatures, not at a codec boundary

Status: Accepted. Amends ADR-0036 (tuples were expression-level only).

## Context

ADR-0036 added tuples as expression-level, first-class values and stated they are "never a data
field's type or a behavior's input/output" — so a tuple had *no* written type at all. A tuple could
be built (`(a, b)`), bound (`let (x, y) = t`), and passed to a helper whose parameter type was
inferred, but it could not be *named* in a signature. The parser rejected `(A, B)` in any type
position.

That blocks stdlib functions whose shape is genuinely a tuple. `Map.toList` returns the entries as a
list of key/value pairs — `List<(String, V)>` — and `Map.fromList` takes one; `List.partition`
returns the two sub-lists as `(List<'a>, List<'a>)`. None of these can be written without a tuple in
the signature, and they are ordinary, expected stdlib operations (Elm's `Dict.toList`, F#'s
`List.partition`).

The reason ADR-0036 kept tuples out of *data fields and behavior I/O* is real: a tuple has no
external representation, so no decoder/encoder can be derived for it (ADR-0004). But a helper or
stdlib signature is not a codec boundary — a tuple there is created and consumed inside a computation
and never serialized. The ban was drawn one notch too wide.

## Decision

A tuple type `(A, B, ...)` may be written in a helper or standard-library signature — as a parameter
type, a return type, or nested inside a `List` / `Map` / `Option` type argument
(`List<(String, 'a)>`). It still may **not** appear as a data field's type or as a behavior's input
or output, because those are the positions where a codec is derived (ADR-0004) and a tuple has no
external representation.

- The parser reads `(A, B, ...)` (two or more types) as a tuple type wherever a type argument or a
  return type is parsed. A one-element `(T)` is not a tuple and is rejected. A bare tuple *parameter*
  is not written — a helper parameter's leading `(` is a function type (`(A) -> B`); a tuple reaches
  a parameter only inside a type argument.
- The checker resolves it to the existing `Type.TupleOf` and threads it through unification,
  substitution, and assignability (element-wise, same arity), so a generic stdlib signature such as
  `toList : Map<String, 'a> -> List<(String, 'a)>` monomorphises at its call site like any other.
- A tuple in a **data field** is rejected during codec derivation, and a tuple in a **behavior's
  input or output** is rejected at signature check, each with a message pointing at ADR-0036/0037.

## Consequences

The tuple stays exactly as expression-level as before at every boundary that matters — it never
crosses a decoder or encoder. What changes is only that the *interior* plumbing may now name it, so a
stdlib or helper function can take and return the tuple it already builds. `Map.toList` / `fromList`
land immediately; `List.partition` and the group/dedup helpers that return a tuple or a
tuple-carrying list become writable in `core` without a one-off record type.

The boundary rule is now stated positively: a tuple type is legal everywhere a type is written
*except* the two codec positions (data field, behavior I/O). This is the same line ADR-0036 drew for
tuple *values* — they flow through a computation but never reach a codec — now extended consistently
to tuple *types*.

## References

- ADR-0036 (tuples are expression-level, first-class values — this widens where their type may be
  written, keeping the codec-boundary ban)
- ADR-0004 (derived codecs — why a tuple, having no external representation, stays out of data
  fields and behavior I/O)
- Specification: `[#tuple]`, `[#stdlib-map]`
- Prior art: Elm `Dict.toList` / `Dict.fromList`; F# `List.partition`
