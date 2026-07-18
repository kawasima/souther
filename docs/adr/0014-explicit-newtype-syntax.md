# ADR-0014: newtype is declared explicitly by `data X = Y`, not inferred from shape

Status: Accepted

## Context

The spec DSL writes wrappers as `data X = Y` where the right-hand side is a single name (`data 従業員ID = 文字列`). Souther needs a rule for when a data is a bare wrapper (external representation identical to the wrapped type) versus an object. Inferring "bare wrapper" from "has one field" would make the external representation depend on a field count that can silently change.

## Decision

When the right-hand side is a single type name `Y`, `X` is a newtype wrapping `Y` — nominally distinct from `Y` but with the same (bare) external representation. Whether something is a newtype is decided by this syntax, not inferred from having a single field.

## Consequences

`data X = Y` reads and writes `Y`'s representation bare (`従業員ID` is `"e-01"`), while `data X = { value: Y }` is always an Object (`{"value":"e-01"}`) even with one field. So adding a field to a braced single-field data never silently changes its representation; a bare representation is requested explicitly by writing `data 金額 = Int`.

Because there is no `|`, a single-name right-hand side is never read as a one-case sum — sums always carry `|` (ADR-0013). When `Y` is itself a sum, `X`'s representation is `Y`'s discriminated representation. As a sum case, a newtype's payload cannot carry `"type"` on a bare scalar, so it goes under a `"value"` key; nested-sum folding stops at the newtype (a newtype is not a sum), and the inner sum is discriminated independently under that `"value"`.

## References

- Specification: §8.7, §10.3, §11.2
