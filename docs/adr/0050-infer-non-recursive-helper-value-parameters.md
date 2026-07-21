# ADR-0050: Infer a non-recursive helper's value parameter types from its call sites

Status: Accepted

## Context

A helper (`let` with no same-named behavior) stated all of its argument types. A behavior takes its type from the specification, but a helper has no specification to draw from, so the types were written by hand. This is redundant where the type is already fixed by how the helper is called: `let double (n) = n * 2` called only with an `Int` leaves nothing for the annotation to add.

A non-recursive helper is inline-expanded at each call site (spec 12.5). At the point of expansion the arguments have concrete types, so a value parameter's type is already determined by its uses. A recursive helper is different: it is lowered to a method rather than inlined, so there is no call-site expansion to read a type from — its return type already has to be declared for the same reason (the cycle cannot be inferred through).

## Decision

A non-recursive helper MAY omit a value parameter's type; it is inferred from the helper's call sites.

- The helper stays monomorphic. The inferred type MUST be the same at every call site. `Int` at one site and `String` at another is a compile error, not an inline-expanded polymorphism — Souther has no user-facing generics, and inference must not introduce them through the back door.
- A function-typed parameter MUST still be annotated. A function type cannot be inferred from a bare name at the call site, and the inliner needs the value/function distinction to expand the call.
- A parameter that no call site can type — an uncalled helper, or an argument bound outside the visible scope — MUST be annotated. Inference is accepted only when it pins a single type; otherwise the annotation is required, as before.
- A recursive helper is unchanged: it writes all of its argument types.

Inference pins each parameter from the call sites (rejecting a divergence), then the helper body is checked against the completed environment — the same standalone check an annotated helper gets — so a mis-declared return type or a mis-passed function argument is caught at the helper, not only where it is later inlined. A body error therefore surfaces at the helper's own source position.

## Consequences

The common helper loses its ceremony while the declarative design holds: a behavior still takes its type from the specification, and a helper still states its type wherever inference cannot. The inference is local — from a helper's own call sites, the same kind of local rule as a lambda argument type inferred from its application — and is distinct from the global type inference left to future work. Because acceptance requires a single inferred type, a helper never silently becomes generic.

## References

- Specification: `[#fn-declaration]`, `[#future-work]`
- ADR-0038 (helpers may recurse), ADR-0017 (declare, don't infer)
