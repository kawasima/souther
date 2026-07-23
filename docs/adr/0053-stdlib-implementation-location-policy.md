# ADR-0053: Standard-library implementation-location policy — one core declaration seam

Status: Accepted.

## Context

The standard library grew with an ease-of-development bias and never settled where a function's implementation should live. Three locations accumulated:

1. **Self-hosted `.sou`** — a Souther expression, inlined at its call sites (`List.map`/`filter`/`all`/`distinct` over `foldFrom`, `Bool.not`). This is the "written in Souther" layer.
2. **`.sou` `intrinsic "key"` + a JVM kernel** — a core declaration whose body is `= intrinsic "key"`, backed by a runtime static (or a JDK method) and a bytecode emitter. This is the "kernel" layer (most of `String`/`Set`/`Map`/`Date`).
3. **Compiler built-in** — no `.sou` at all: the signature is hardcoded in the type checker and the codegen in the backend. All `Int`/`Decimal` arithmetic functions were here, plus `List.length`/`get`/`find`/`max`/`min`/`sortBy`, `String.length`, `Map.get`/`empty`, `Set.empty`.

Elm has only the first two: every function is declared in a module with a type signature, and its body is either Elm or a Kernel (JS) primitive. Souther's third location has no Elm analogue, and `[#intrinsics]` already states the intended rule — "this declaration, not a hardcoded compiler table, decides its arguments and return value." The compiler built-ins contradict that rule, and they do not scale: each addition (e.g. `Int.modBy`) means editing the checker and the backend rather than adding a line to a core module. This ADR settles the policy so the library can grow.

## Decision

**Every standard-library function is declared in a core module (`souther.*`). The declaration is the single source of truth for its signature.** Its implementation is one of two layers, mirroring Elm:

- a **self-hosted** Souther expression, when it is expressible from existing primitives; or
- a **kernel intrinsic** `= intrinsic "key"`, backed by a runtime method and a backend emitter.

A pure compiler built-in (a signature that lives only in Java) is not the way to add a function. The compiler-integrated set is closed to three narrow cases, each justified by something a core declaration cannot yet capture:

- **the operators** (`+ - * /` and `== < <= > >=`) — they are syntax, and the arithmetic ones carry the closed-newtype rules (ADR-0047, ADR-0033) that a plain function does not have;
- **the ordered-constrained generics** (`List.sort`/`max`/`min`/`sortBy` and the comparison operators) — Souther deliberately has no `comparable` type class (the user language stays bounded), so "the element is an ordered value" is enforced in the compiler rather than expressed in a signature;
- **the primitive-headed-union functions** (`Int.divide`/`Int.remainder`/`Decimal.divide`, returning `Int | DivisionByZero`) — `successType` requires every union member to be a data type, so a core return type cannot yet name a union whose first member is a primitive. Relaxing that is a language-rule change, deferred.

This pass moves the `Int`/`Decimal` `add`/`subtract`/`multiply`/`compare` functions to new `souther.int`/`souther.decimal` modules as intrinsic declarations, backed by the kernels that already implement the operators (`IntMath.addExact` etc., `BigDecimal.add` etc.) plus small `compare` wrappers, and deletes their dead type-checker/backend arms. It also adds `Int.modBy` (Issue #50) as a first-class citizen of the seam — Elm-style floored modulo, divisor first, aborting on a zero divisor, returning a plain `Int` so it reads in an invariant.

## Consequences

- Adding an `Int`/`Decimal` function is now a core declaration plus a kernel, not compiler surgery. `Int.modBy` demonstrates the path.
- `Int.compare` no longer accepts two `Decimal` operands (the old shared `numericOp` was loose); it is `(Int, Int) -> Int` as declared. A minor, sound tightening.
- The remaining compiler built-ins — the ordered-constrained `List` generics, the primitive-headed-union divisions, and the still-hardcoded `List.length`/`get`/`find`, `String.length`, `Map.get`/`empty`, `Set.empty` — are candidates for later alignment: `length`/`get`/`find` need only kind-checking derivable from a declared signature; the union-returning divisions wait on a union-signature relaxation; the ordered generics wait on (or forgo) an ordered-constraint notation. None is required now.
- The operators stay compiler-integrated by design; the closed-newtype arithmetic (ADR-0047) depends on that.

## References
- Specification: `[#intrinsics]`, `[#stdlib]`, `[#stdlib-int]`, `[#stdlib-decimal]`
- ADR-0033 (numeric literals and the `+ - * /` operators)
- ADR-0047 (closed-newtype arithmetic depends on the operators staying integrated)
- ADR-0051 (`fold` is a self-hosted recursive helper — the self-hosted layer)
- ADR-0052 (recursion total by default — constrains what the self-hosted layer may write)
- Prior art: Elm (every function declared in a module; body is Elm or a Kernel primitive; no per-function compiler table)
