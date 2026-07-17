# ADR-0019: `=` binds named definitions, `=>` introduces anonymous blocks (SML-style)

Status: Accepted

## Context

Souther has named definitions (`data`, `behavior`, `fn`), anonymous forms (match arms, blocks), and type arrows. It needs a consistent assignment of `=`, `=>`, and `->` across all three, and a decision on whether a named definition may bind a lambda.

## Decision

`=` binds a name to a right-hand side; `=>` introduces the anonymous forms (match arms, blocks); `->` stays the type arrow (behavior declarations, `fn` function-type arguments). Named uses `=`, anonymous uses `=>`. Binding a lambda to a named definition (`fn f = (x) => ...`) is not allowed.

## Consequences

This split follows SML's `fun f x = e` versus `fn x => e`. Keeping the two markers distinct means a reader can tell a named definition from an anonymous form at the marker, and the type arrow never overloads either role.

Disallowing a lambda bound to a named definition follows the ML family's treatment of that form as something to rewrite toward the direct sugar — HLint's "Redundant lambda" and the OCaml manual's preference for `let f x = e` over `let f = fun x -> e`. In those ecosystems the form reads as a signal to rewrite, so Souther omits it rather than admitting a form its own conventions would flag.

## References

- Specification: §5, §13.1
- HLint: "Redundant lambda"
- OCaml manual: `let f x = e` preferred over `let f = fun x -> e`
