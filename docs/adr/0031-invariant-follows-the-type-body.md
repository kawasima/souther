# ADR-0031: A data's `invariant` clause follows the type body, not the record's braces

Status: Accepted (decided 2026-07-18; implemented)

## Context

A newtype writes its invariant after the type body, on the following line, referencing the
implicit `value`:

```text
data 数量 = Int
    invariant value > 0
```

A product wrote its invariant **inside** the braces, as a standalone no-comma clause among
the fields:

```text
data X = { cost: Int  invariant cost >= 0 }
```

So the two forms placed the invariant differently relative to the type body: after it for a
newtype, inside it for a product.

The braces of a product are the record *type* — a set of fields (Elm's `{ x : Int }`). An
invariant is a predicate over a value, not a member of that field set. Putting it inside the
field braces conflates "what fields exist" with "what must hold of a value." And a newtype
cannot put its invariant in braces at all: it has none, and `data X = { value: Y }` is a
different, Object-encoded type (ADR-0014). The only way to make the two forms consistent is
to place the product's invariant outside the braces too — the newtype's cannot move in.

## Decision

A data's `invariant` clause follows the type body — `= Y` for a newtype, `{ ... }` for a
product — never inside the record braces. Several `invariant` lines all apply, conjoined.

```text
data 会員 = {
    id: 会員ID,
    表示名: String
}
    invariant length(表示名) > 0
```

The `...` spread (ADR-0030) stays a comma-separated member inside the braces, because it
composes structure (it adds fields). The braces hold structure — fields and spreads — and
the invariant is a trailing predicate clause on the declaration.

## Consequences

A product's braces hold only a comma-separated list of fields and spreads. The invariant is
the sole trailing clause of the declaration, uniform with the newtype form.

The placement follows ML-family practice, which never makes a whole-record predicate a
sibling inside the field list. F* attaches a refinement to a field's type or wraps the whole
type (`a:t{ P a }`); Ada attaches `Type_Invariant` after the type via a `with` aspect;
LiquidHaskell's `invariant` annotation sits outside the data braces. Idris expresses an
intrinsic invariant as a genuine (erased proof) field, never as a pseudo-field. Souther,
having no proof language, uses the trailing-clause form.

## References

- ADR-0014 (newtype `data X = Y`; its invariant already follows the body — this ADR aligns
  products to it)
- ADR-0030 (the `...` spread stays inside the braces as structure)
- Specification: `[#invariant]`, `[#product-data]`
