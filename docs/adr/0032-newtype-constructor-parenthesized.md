# ADR-0032: A newtype is constructed by applying its name, parenthesized: `金額(500)`

Status: Accepted (decided 2026-07-18; implemented)

## Context

A newtype `data 金額 = Int` is nominally distinct from `Int` (ADR-0014). Until now its values
arose only at the decode boundary; there was no in-language way to build one from a literal
or a computed value.

ML-family languages construct a wrapper by applying the constructor: Haskell and Elm write
`Money 500`, F# `Money 500` or `Money(500)`. The juxtaposition form is unambiguous in
Haskell and Elm because constructors are Capitalized — the parser tells `Money 500`
(construction) from `money - 5` (subtraction) lexically. Souther's identifiers are Unicode
(Japanese domain terms) with no case distinction, so `金額 500` cannot be told from
`金額 - 5` at parse time. And Souther already applies functions with parentheses everywhere
(`f(x)`, `length(value)`) — the F#-tupled branch of ML, not Haskell/Elm currying.

## Decision

Construct a newtype by applying its type name to one argument, parenthesized: `金額(500)`,
`会員ID("m-01")`. It is the record literal `金額 { value = e }` written in call form — the
type name in call position is the constructor. This matches Souther's parenthesized
application and avoids the `金額 -5` ambiguity a juxtaposition form would carry.

An invariant violation *aborts*, as for any data construction (ADR-0029,
`[#violation-destination]`); construction is not fallible in the domain (there is no
`金額 | 不正`). So no constant-only restriction is needed: a runtime argument is allowed and
aborts on violation, exactly like an invariant-bearing product `値引き済み { 額 = x }`.

When the argument is a compile-time constant and the invariant folds to `true`, the
construction cannot abort: it is checked at compile time — so `金額(-5)` is a compile error,
not a runtime abort — and may sit anywhere, including a non-tail `let money = 金額(500)`. A
runtime argument to an invariant-bearing newtype stays restricted to the behavior's result
position (its abort is the outcome), like any invariant-bearing construction.

## Consequences

The parser is unchanged: `金額(500)` already parses as a call. The checker routes a call
whose name is a newtype to construction and, when the argument folds to a constant, records
it. The constant argument is verified by **compile-time function evaluation**: the backend
emits, per invariant-bearing newtype, a Raoh-free `$Ctfe.check(value)` that runs the *same*
invariant bytecode `__construct` runs, and after codegen the compiler loads it and runs each
recorded constant. A violation is a compile error, with no second evaluator that could
disagree with run time. The backend emits a plain construction when the newtype has no
invariant, and the checked `__construct` (which aborts on violation) for a runtime argument
in tail position.

CTFE couples the compiler to `souther-runtime` (a `provided` dependency): it loads generated
code, and a lambda-bearing invariant's bytecode references the runtime `Fn`. When the class
cannot be loaded or run at compile time, CTFE degrades to the run-time check. The compiler
still does not depend on Raoh — CTFE runs the bare boolean invariant, not the
Raoh-returning decoder or `__construct`.

Two alternatives were rejected. A capitalization convention marking constructors (which
would enable `金額 500`) is incompatible with free Japanese naming. Type-ascription
construction (`let money: 金額 = 500`) opens a nominal hole, letting a bare `Int` become a
`金額` by annotation.

## References

- ADR-0014 (a newtype is nominal, declared by `data X = Y`)
- ADR-0029 (an invariant violation is an exception/abort, not a case)
- ADR-0026 (record-literal fields bind with `=`)
- Specification: `[#newtype]`, `[#violation-destination]`
