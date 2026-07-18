# ADR-0003: Invariant violations abort; preconditions are business cases, not contracts

Status: Accepted

## Context

The spec model constrains a domain three ways: a `data`'s own invariant, a `behavior`'s precondition, and its postcondition. When one of these does not hold at runtime, the language has to decide where the failure goes, and two forces pull on that decision. One is *totality*: the spec model aims to be defined over every input a behavior can actually receive, leaving nothing "undefined" and nothing to an uncaught exception. The other is honesty about bugs: a value computed from already-validated values that then breaks a rule the model declared impossible is not an input error — it is the model contradicting itself.

An invariant is checked on every construction path, so the same check fires at two very different places: at the boundary, where a value arrives from outside and may be malformed, and inside the domain, where every value has already passed validation.

## Decision

**An invariant violation depends on where it happens.** Caught in a decoder (the boundary) it becomes a Raoh `Result` failure carrying an `Issue` (`path` / `code`): malformed input is an expected outcome. Inside a behavior (the domain) it **aborts** — no value, no case, and no place for it in the output type. Same invariant, different treatment, because the two mean different things.

**Preconditions are not Design-by-Contract contracts.** In the DbC tradition a precondition violation is the caller's fault and the routine guarantees nothing — an exception. Souther does not do this. A precondition that can realistically fail is enumerated as an ordinary domain `data` and returned through `require ... else` (a named case in the output sum: `承認権限なし`, `残高不足`, `差額が負`). The failure becomes part of the specification — visible in the output type and impossible to forget — rather than an exception thrown past the model. Only a precondition that is *guaranteed* to hold, whose violation would be a model bug, aborts, and it does so through the invariant of whatever it then constructs; there is no separate precondition-contract construct.

**Overflow aborts; zero-division returns a case.** `Int` is 64-bit signed; an operation leaving the range aborts rather than wrapping, because overflow is a model bug, not a business result. Zero-division, by contrast, is a possible input and returns `Int | DivisionByZero`.

## Consequences

Totality is what routes preconditions to cases instead of contracts: a precondition-failing input is real, so its result is named and defined, not left undefined or thrown. This is a deliberate divergence from Eiffel, which blames the caller and does nothing — here the boundary is a first-class concern and the failure is enumerated so the model stays total.

An abort inside the domain is a **model bug**, not a business result. §2.6's rule that business results are a domain sum (ADR-0007) is about business *failures* — they carry vocabulary the caller acts on. An invariant violation has no business name and is not the caller's to handle, so the output type of an invariant-bearing constructor does not change and no failure case is required (E1003 was retired for this reason).

This leaves one thing invisible in the types, on purpose. A behavior typed `(金額, 金額) -> 金額` that computes `a - b` can abort when `b > a` makes the result violate `金額`'s `value >= 0`: it is a partial function whose type looks total. Souther does not surface that partiality. Putting `| 制約違反` on every constructor that might violate an invariant would pollute every such signature with a bug that has no business meaning. The discipline is instead: if the failure is real, enumerate it as a case (`-> 金額 | 差額が負`) and it *is* in the type; if it cannot happen, the abort is the safety net for the day the assumption breaks. Proving statically that a computation cannot violate an invariant is refinement-type / SMT territory, which Souther excludes, so a missing guard is caught by a test, not the compiler.

An abort is not a Souther expression and there is no syntax to catch it, so it cannot drive business flow — the same line §3 draws for exceptions: the ban is on controlling business flow, not on aborting for a bug. At the Java boundary the abort surfaces as `net.unit8.souther.runtime.ConstraintViolation` (carrying the violated data name and invariant location), which the boundary may catch and map to a 500 — deliberately distinct from a business failure, which arrives as an output case and maps to a 400.

If a writer genuinely wants to return a value when a rule is not met, that is a business judgment written explicitly with `require ... else` (ADR-0020). An invariant is a declaration that "every value passing here is like this," not a branch anticipating that it is not.

## References

- Specification: §2.2, §3, §7.3, §9.4, §18.2, §19.7
- SMDD book, Design by Contract / totality: preconditions are modeled as sum-type cases so the model stays total, not as contract exceptions
- Eiffel: Design by Contract (a contract violation is a caller-blamed bug) — Souther keeps invariant violation as a bug but reroutes preconditions to business cases
- Rust: `Result` vs. `panic!`; array-bounds and integer-overflow panic
- ADR-0007 (business results are an unmarked sum), ADR-0020 (`require ... else`)
