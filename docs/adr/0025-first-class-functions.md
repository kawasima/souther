# ADR-0025: First-class functions — inline when they can, close over when they must

Status: Accepted

## Context

Souther began with second-class blocks: a lambda could be passed to a list combinator, but not returned, bound to a `let`, or stored. The reasoning was that a block that cannot escape need not become a runtime value — the compiler expands it inline. That covers the combinator cases the spec DSL asks for, but it stops short of the ordinary functional-programming idioms: naming a reusable function locally, choosing one of several functions at runtime, or returning a configured function.

The question is whether to make functions first-class, and if so, whether every function becomes a runtime object or only the ones that need to.

## Decision

Functions are first-class values. A lambda `(x) -> e` may be bound with `let`, applied `f(x)`, and returned. Function types `(A) -> B` are written on a helper `fn`'s parameters. A lambda's parameter types are not annotated; they are inferred from how it is applied.

How a function is compiled depends on whether it escapes:

- A function that does not escape — it is applied where it is bound, or passed to a combinator — is expanded inline. No runtime object exists.
- A function that cannot be inlined — one chosen at runtime (`let f = if c then (x) -> ... else (x) -> ...`) or returned as a configured closure (`fn adder (n) = (x) -> x + n`) — compiles to a synthetic class implementing the runtime `Fn` interface. Its free variables are captured into `final` fields; the body compiles into `apply`.

A function still may not be stored in a `data` field or appear in a behavior's input or output. Those are not closure limits — a function has no external representation, so it cannot cross a decoder or encoder boundary (ADR-0004), and a behavior's output is a domain sum, not a function. This mirrors the spec DSL, which keeps a swappable rule as `data`, not as a function.

## Consequences

The common idioms work — local named functions, runtime selection, returned closures — while the representation boundary stays intact: a function never reaches a codec. The inline path keeps the no-closure cost of the original design wherever the function does not escape, so a runtime `Fn` is built only when one is genuinely needed.

Parameter-type inference is deliberately narrow: it reads the argument types at the application sites of a `let`-bound function. A function that is never applied cannot be typed, and is rejected. This is not the general type inference the MVP defers; it is the local rule that the escaping cases need.

Because functions are monomorphic, this does not reintroduce user-defined generics (ADR-0010): a helper's function-typed parameter names concrete types.

## References

- Specification: §12.5 (blocks), §13.1 (fn declaration), §18.4 (combinators)
- ADR-0004 (derived codecs — why a function has no representation)
- ADR-0010 (no user generics — functions stay monomorphic)
- ADR-0019 (the `->` arrow for lambdas)
