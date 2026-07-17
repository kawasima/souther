# ADR-0019: One arrow `->` for types and terms; `=` binds names

Status: Accepted (revised — was SML-style `=>`/`->` split)

## Context

Souther has named definitions (`data`, `behavior`, `let`), anonymous forms (match arms, lambdas), and type arrows. It needs a consistent assignment of `=` and the arrow across all three, and a decision on whether a named definition may bind a lambda.

An earlier version split the arrows SML-style: `->` for type arrows and `=>` for the anonymous forms. That split is dropped.

## Decision

`=` binds a name to a right-hand side. A single arrow `->` serves everywhere else: the type arrow of a behavior declaration and a `let` function-type argument (`(A) -> B`), the body of a lambda (`(x) -> e`), and a match arm (`| A -> body`). There is no `=>`.

Binding a lambda to a named definition (`let f = (x) -> ...`) is still not allowed.

## Consequences

Souther follows the ML family it descends from — F#, whose railway composition and unmarked-sum output it mirrors (ADR-0007), and Elm — where one arrow `->` covers function types, lambdas, and match. Using `=>` for lambdas and match is the C#/Scala/TypeScript lineage, which Souther does not model. The behavior signature already uses `->` because the spec DSL does (ADR-0001), so aligning lambdas and match to the same arrow keeps the surface uniform rather than splitting type-position from term-position.

Disallowing a lambda bound to a named definition follows the ML family's treatment of that form as something to rewrite toward the direct sugar — HLint's "Redundant lambda" and the OCaml manual's preference for `let f x = e` over `let f = fun x -> e`.

## References

- Specification: §5 (delimiters), §13.1 (let declaration), §16.3 (match)
- ADR-0001 (one-to-one with the spec DSL), ADR-0007 (F#-derived railway)
- ADR-0025 (first-class functions)
