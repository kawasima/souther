# ADR-0007: Business results are an unmarked sum; the off-ramp is decided by composition

Status: Accepted. Refined by ADR-0029 (the sum carries domain outcomes only; platform failures are exceptions).

## Context

A behavior returns one of several possible business results. Many languages force a
success/failure split at this point (`Result`, `Either`, an `error` tag). But whether a
given result is "the failure that leaves the mainline" is not an intrinsic property of the
value — a `会員なし` handled by the next stage is on the mainline, while the same value
left unconsumed is an off-ramp. Souther wants the behavior's type to state the business
results without prejudging which is a failure.

## Decision

A behavior's output is an *unmarked* domain sum — no success/failure marker, no `error`
tag, no `Result`/`Either`, no fourth slot for failure. It is a function from input to "one
of the possible business results." Which arm leaves the mainline is decided by `>->`
composition, not by the behavior alone: the arm the *immediately next stage* does not
consume leaves the mainline (Railway); every remaining arm rides to the next stage.

## Consequences

The same value leaves the mainline in one composition and rides it in another
(§12.2). "Failure" is not a language-level concept; an off-ramp arm is just an ordinary
business result, not a `Result`-style success/failure wrapper. This mirrors the spec DSL,
which lists only business results in the output `OR` and keeps implementation-driven
failures out of the type.

Unexpected failures — out of memory, network partition — are never listed in the output.
Invariant violations are also *not* here: they are model bugs with no business vocabulary,
so they abort rather than appear as an arm (see ADR-0003). Independent validation errors
are accumulated inside the Decoder (§15), which is a boundary concern, not a domain arm.

The mainline/off-ramp split is carried as composition *plumbing*, not as a mark on the
value. Because the split is remembered by the composition and folds back into a plain sum
at the pipeline's ends, `>->` is associative: pulling a sub-pipeline out under a name, or
re-associating the stages, does not change the result (`f >-> g >-> h` equals `(f >-> g) >->
h` including the off-ramped arms). That associativity is what lets a name be an
abstraction boundary — renaming a sub-pipeline preserves meaning. `>->` is therefore not
plain function composition: it threads the mainline through each stage while off-ramping
the arms no stage consumed, and carries that off-ramp so the whole stays associative.

The operator is spelled `>->`, and the spec DSL adopts the same spelling so the one-to-one
correspondence (ADR-0001) is preserved. An earlier spelling was `>>`, but `>>` reads as
sequencing (Haskell's `Control.Monad.>>`) or as plain forward function composition (F#,
`Control.Category.>>>`), and this operator is neither. `>=>` (Kleisli composition) is closer
in lineage — Railway-Oriented Programming spells its `Result` composition `>=>` — but it
implies a fixed monad with a privileged success track, and this operator has neither: the
off-ramp row is open and accumulates over the pipeline, and no arm is privileged as the
success. `>->` follows Haskell's `pipes`, whose `>->` composes pipeline stages by matching
one stage's output interface to the next stage's input, carrying no success/failure
connotation — the same shape as stage routing here.

## References

- Specification: §2.6, §12.2, §14 (esp. §14.2, §14.3), §29
- ADR-0003 (invariant violations abort in the domain)
