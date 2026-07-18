# ADR-0030: Record composition and update use a `...` spread, not an `include` keyword

Status: Accepted (decided 2026-07-18; implemented)

## Context

ADR-0012 introduced `include X` as a standalone clause inside a product `data`: it
flattens another data's fields into this one and inherits its invariants, nominally (not
subtyping, and Souther has no intersection types). ADR-0018 separately introduced a
type-named spread for value-level construction and update, `T { ..src, x = 2 }`, spelled
with `..` (two dots).

Two things sit awkwardly. `include` reads like OOP inheritance, and it is a bespoke
standalone clause taking no comma — unlike everything else between the braces, which is
comma-separated. And the value-level spread `..` and the type-level `include` are two
different notations for the same idea: "splice another record's fields in here."

## Decision

Replace the `include X` clause with a `...X` **spread** that is a comma-separated member of
the product's brace list, following ReScript's record type spread
(`type c = { ...a, ...b, active: bool }`). Several spreads are allowed and a spread
conventionally leads. The spread flattens the referenced data's fields and inherits its
invariants; the semantics of ADR-0012 (nominal, no subtyping, no intersection types) are
unchanged — only the surface syntax changes.

Unify the spread spelling on `...` (three dots) for **both** the type-level composition and
the value-level record literal and update (ADR-0018), replacing the former `..` (two dots).
One spelling everywhere, matching TypeScript and JavaScript, where `...` is spread and rest
uniformly.

```text
data 提出済み = {
    ...出張申請共通項目,
    提出日時: String
}

let f (申請, 提出日時) = 提出済み { ...申請, 提出日時 = 提出日時 }
```

## Consequences

A product `data`'s braces hold a single comma-separated list of members, each a field
`name: Type` or a `...Type` spread. No member is a bespoke no-comma clause anymore; the
`invariant` clause leaves the braces entirely (ADR-0031).

`...` is nominal composition, not row polymorphism. The result is a distinct closed type
carrying the inherited invariants, not an open row. This differs from Elm and PureScript
extensible records — which are open, structural, and carry no invariants — and matches
ReScript's copy-paste record type spread and ML's module `include` semantics.

Field-name collisions across spreads, or with the data's own fields, remain a compile error.

The `include` keyword is removed; `include` is an ordinary identifier again.

## References

- ADR-0012 (nominal include, no intersection types; this ADR changes its surface syntax only)
- ADR-0018 (type-named spread for update; this ADR changes its dots `..` → `...`)
- ADR-0031 (the `invariant` clause moves out of the braces)
- Specification: `[#product-data]`, `[#field-spread]`, `[#record-literal]`
- Prior art: ReScript record type spread; TypeScript `...` spread/rest
