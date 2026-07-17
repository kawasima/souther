# ADR-0003: Invariant violations abort inside the domain; the boundary returns a Result

Status: Accepted

## Context

An invariant declared on a `data` is checked on every construction path. But a violation means different things depending on where it happens. A value arriving from outside that fails validation is an expected outcome — malformed input. A value produced by computing over already-validated values that then breaks a rule is something else: the model computed a value it declared could not exist.

## Decision

A violation caught in a decoder (the boundary) becomes a Raoh `Result` failure carrying an `Issue` (`path` / `code`), an expected result. A violation inside a behavior (the domain) **aborts** — it returns no value and no arm, and there is no place for it in the output type. The two are treated differently even when checking the same invariant.

## Consequences

An abort inside the domain is a **model bug**, not a business result. §2.6's rule that business results are returned as a domain sum (see ADR-0007) is about business *failures* — `承認権限なし`, `残高不足` carry business vocabulary and mean something to the caller. An invariant violation has no business name and is not something the caller should handle: the computation broke a rule the model itself set. So the output type of an invariant-bearing constructor does not change, and no failure arm is required (E1003 was retired for this reason).

An abort is not a Souther expression and there is no syntax to catch it, so it cannot be used for business-flow control — this is the same line §3 draws for exceptions: the ban is on controlling business flow, not on aborting for a bug. At the Java boundary the abort surfaces as `net.unit8.souther.runtime.ConstraintViolation` (carrying the violated data name and invariant location), which the boundary may catch and map to a 500 — deliberately distinct from a business failure, which arrives as an output arm and maps to a 400. Integer overflow follows the same line: `Int` is 64-bit signed and an operation that leaves the range aborts rather than wrapping, because overflow is a model bug, not a business result; zero-division, by contrast, is a possible input and returns an arm (`Int | DivisionByZero`).

If a writer genuinely wants to return a value when a rule is not met, that is a business judgment and is written explicitly with `require ... else` (see ADR-0020) — an invariant is a declaration that "every value passing here is like this," not a branch anticipating that it is not.

Prior art draws the same line. Eiffel defines a contract violation as a bug rather than a business case and does not let the caller handle it. Rust separates recoverable failure (`Result`) from bugs (`panic!`), making array out-of-bounds and integer overflow panic rather than `Result`.

## References

- Specification: §2.2, §3, §7.3, §9.4, §18.2, §19.7
- Eiffel: Design by Contract (contract violation as a bug)
- Rust: `Result` vs. `panic!`; array-bounds and integer-overflow are panics
