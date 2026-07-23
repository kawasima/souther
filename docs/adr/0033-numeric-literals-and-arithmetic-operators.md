# ADR-0033: Decimal literals carry an `m` suffix; `+ - * /` work on Int and Decimal

Status: Accepted

## Context

Souther distinguishes numeric literals lexically, as the ML family does (OCaml, SML, F#):
integer digits are `Int`, a fractional literal was `Decimal`. Two gaps showed up in use:

- There was no way to write an integer-valued `Decimal` literal without a decimal point
  (`500.0`), and a bare fractional literal `1.5` silently became `Decimal` — the only
  fractional type, since Souther has no binary floating point. The `Int`/`Decimal`
  boundary was not explicit at the literal.
- Arithmetic operators were `Int`-only. `+ - *` did not work on `Decimal` (which used the
  `Decimal.add`/`subtract`/`multiply` functions), and there was no `/` operator at all —
  division was only the `Int.divide`/`Decimal.divide` functions, which return
  `X | DivisionByZero`. Everyday arithmetic read as function calls.

## Decision

**Numeric literals.** A `Decimal` literal carries an `m` suffix, as in F# / C#: `500m`,
`1.5m`, `0.5m`. Bare digits are `Int` (`500`). A fractional literal without `m` (`1.5`) is a
compile error — Souther has no floating-point type, so `Decimal` is stated at the literal
rather than defaulted into. This makes the `Int`/`Decimal` choice explicit in the source.

**Arithmetic operators.** `+ - * /` work on two `Int` or two `Decimal` operands (both the
same type, yielding that type). They coexist with the `Int.`/`Decimal.` arithmetic
functions. (A single-value numeric newtype later extended these on its wrapped value:
closed `+`/`-` and scalar `*`/`/` stay in the newtype — spec §newtype-arithmetic, ADR-0047.)

**Abort vs case.** The operators abort on an arithmetic error, returning a plain value:
`+ - *` abort on `Int` overflow (unchanged), and `/` aborts on a zero divisor (a
`ConstraintViolation`, which Souther code cannot catch). The `Int.divide` / `Decimal.divide`
functions are unchanged and still return `X | DivisionByZero` for code that handles a zero
divisor as a business case. The split is: **operator = terse, aborts; function = explicit,
returns a case.**

**Decimal `/` rounding.** The `/` operator on `Decimal` rounds to a significant-digit
precision matching F# / .NET `System.Decimal` (about 29 digits), half away from zero
(HALF_UP), so `10m / 3m` is `3.3333…` rather than aborting on a non-terminating result. When
a specific scale and rounding mode are part of the domain, `Decimal.divide(a, b, scale, mode)`
states them explicitly (ADR keeps the existing "rounding is a domain decision" stance for the
function form; the operator trades that for convenience).

## Consequences

- The lexer reads a trailing `m` as the `Decimal` marker and rejects a fractional literal
  without it. A lone `/` lexes as division (it was previously an error; `/=` is still
  inequality).
- `Ast.BinOp` gains `DIV`; the type checker types `+ - * /` over `Int`/`Decimal`; the backend
  emits `IntMath.divideExact` (Int, abort on zero/overflow) and `DecimalMath.divide` (Decimal,
  default rounding, abort on zero), alongside `BigDecimal` `add`/`subtract`/`multiply` for the
  Decimal operators.
- Existing source that wrote a bare `Decimal` literal must add `m` (`0.08` → `0.08m`).
- Compile-time constant folding (ADR-0032 CTFE) does not fold `/`; a constant division in an
  invariant is checked at run time, avoiding a second definition of the rounding and
  zero-divisor semantics.

## References

- Specification: `[#primitives]` (numeric literals), `[#stdlib-int]`, `[#stdlib-decimal]`
- ADR-0003: invariant violations abort in the domain (the abort model `/` follows)
- ADR-0009: Decimal ignores scale
- ADR-0019: one arrow `->`; ML-family grounding (F#/Elm) — the `m` suffix follows F#
