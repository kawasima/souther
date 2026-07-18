# ADR-0011: Option, Never, and Unit are not surface-writable types

Status: Accepted

## Context

`Never`, `Unit`, and `Option<T>` are useful as reading concepts, but letting them be written as ordinary types in the surface language raises questions the model does not want to answer — chiefly, how `>->` routing should treat an `Option` in a behavior's output.

## Decision

`Never` and `Unit` are reading concepts, not writable type names. `Option<T>` cannot be written as a type either; it appears only where a `T?` field desugars and as stdlib return types (`List.get` / `Map.get`). None of the three may appear in a behavior output.

## Consequences

`Never` denotes an impossible case (an empty sum); a single-case output (`-> A`) reads as "the failure row is empty." `Unit` corresponds to the DSL's 単位型 but is always expressed as a field-less `data`, never as a written type. `Option` cannot appear in a behavior output because "might not be found" as a business result is a domain sum — `-> 会員 | 会員なし` — which reads closer to the DSL than `Option<会員>`. Allowing `Option` in output would also force `>->` routing to decide whether it consumes `Some` / `None` or treats `Option<会員>` as one case; saying it in business vocabulary removes the question.

`Some(T)` is the one exception to the rule that a sum case carries no payload (see ADR-0013). Because `Option` is built in, users cannot imitate the form. Building a `?` field needs no `constructs`: `Option` is an auxiliary type with no invariant, so closed construction (ADR-0002) has nothing to protect there — and there is no way to write `constructs Some` anyway, since `Option` is not a writable type. The wrapped value is taken out with `match`, where `| Some as v` binds `v` to the contained value rather than to `Some`.

## References

- Specification: §7.3, §7.4
- ADR-0002 (closed construction paths), ADR-0013 (sum cases are named-data references)
