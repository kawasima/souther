# ADR-0051: fold is a recursive helper, not a privileged loop

Status: Accepted. Amends ADR-0028, ADR-0038.

## Context

The standard library needs one iteration construct the rest is written over. Elm and F# do not privilege `fold`: their `List` is a cons cell, so `fold` falls out of `match` plus structural recursion — the same machinery every function uses. Souther's `List` is a persistent vector with no head/tail pattern, so `fold` cannot be written that way. But Souther already has the two pieces it needs elsewhere: a helper may recurse (ADR-0038), and a function is a first-class value (ADR-0025).

## Decision

`fold` is an ordinary recursive helper in `souther.list`, not a privileged loop.

- **`foldFrom(step, seed, xs, i)`** walks the list by index with `List.get`, threads the accumulator, and recurses on `i + 1`; `List.get` returning `None` past the end is the base case. `fold(step, seed, xs)` starts it at `0`. The combinators — `map` / `filter` / `all` / `any` / `sum` / `distinct` / `partition` / `groupBy`, and `Map.fold` — are `let`s over it.
- **The step is a first-class function value.** A recursive helper may take a function parameter; the step arrives as a closure and is applied per element. A closure passed to a recursive helper MUST be pure — it may not call an injected behavior — so an effect cannot enter the pure method through it.
- **The tail self-call is compiled to a loop.** The backend turns a self-tail-recursive call into a jump that reassigns the parameter slots, so `foldFrom` runs in constant stack.

The irreducible kernel is the intrinsics: `List.get` / `List.length`, arithmetic, the string operations, and `sort` / `reverse` / `max` / `min` / `find` / `sortBy`, which need intermediate storage or a built-in comparison `fold` does not give. `fold` is not among them.

## Consequences

- There is no `fold` node in the Core IR and no `List.fold` built-in; a `fold` call is an ordinary recursive-helper call. The one loop the backend emits is the general tail-call optimization, keyed on the shape of a self-tail-recursive call, not on the name `fold`.
- A user may write a recursive higher-order helper — a tree fold, an accumulate — under the same purity rule the standard library's `fold` obeys.
- An empty-collection seed (`[]`, `Map.empty`) carries a bottom element type; the accumulator's real type is recovered from the step's result, and a case-seeded accumulator grown to its sum is typed at the sum.

## References
- Specification: `[#stdlib]`, `[#blocks]`, `[#fn-declaration]`
- ADR-0025 (functions are first-class values)
- ADR-0028 (the stdlib is Souther over an intrinsic kernel — the kernel no longer includes a fold loop)
- ADR-0038 (a helper may recurse; a recursive helper is pure)
