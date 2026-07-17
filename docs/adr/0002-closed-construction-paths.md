# ADR-0002: Close data construction paths; permission lives on the behavior

Status: Accepted

## Context

The spec DSL states, via parse-don't-validate, that "a validated email address is guaranteed to be validated by its type." For that guarantee to hold in the implementation, it must be impossible to produce a value of an invariant-bearing `data` without going through validation. Merely writing `T` as a return type must not be enough to mint a `T`.

## Decision

A value of `data T` can be produced only by: `T`'s derived decoder, the implementation (Souther `let` or injected Java) of a behavior whose construction set includes T, or compiler-generated code. The authority to construct is held by the *behavior*, not by its implementation. That set is written with `constructs` on the behavior; on an let-backed behavior it may be omitted and inferred from the visible body, while an injected behavior must declare it (no body to infer from, and it drives factory generation — ADR-0006, §13.3).

## Consequences

Permission is always on the behavior's declaration. A helper `let` (one with no corresponding behavior) may also construct data, but it does not declare `constructs` itself — its construction set is inferred transitively and must be contained in the `constructs` of the behavior that calls it (otherwise E1002). Blocks work the same way, constructing under the enclosing behavior's authority. So refactoring an anonymous block out into a named helper `let` never changes whether a construction is allowed.

`constructs` is **not** what guards the invariant — `__construct` and the decoder do that, and both check on every construction path (see ADR-0003). Deleting `constructs` would not let anyone build an unvalidated `検証済みメールアドレス`. What `constructs` buys is *model reading*: from the declaration alone you can tell whether a behavior **mints** a new value or merely **passes through** one it was given, which the output type cannot tell you. But that is a reading aid, not a dependency. Unlike `requires`, a construction permission is invisible to callers — whether a behavior mints Member or passes it through, the caller calls it identically — so inferring it changes no outward contract (the failure mode that makes `requires` worth declaring, ADR-0017, does not arise here). So on an let-backed behavior, whose body is visible, `constructs` is **optional**: omit it and the set is inferred; declare it and it is checked exactly (under-declaration E1002, over-declaration E1006), so a written clause stays a truthful record of mint-versus-pass-through. It lives on `behavior` because "this behavior produces a new 事前承認済み" is a statement of the spec (exactly what the DSL's state transition says) for whoever chooses to write it.

The guarantee is "no *unvalidated* value can be built," not "no value can be built." Factories for unit arms are handed out to injected Java implementations for convenience, and unit data have their decoders public (a unit decodes by ignoring input), so anyone can build a unit value — but a unit data has nothing to validate. Invariant-bearing data are what closed construction actually protects: however they are built, they are checked.

## References

- Specification: §2.1, §12.3, §13.3
