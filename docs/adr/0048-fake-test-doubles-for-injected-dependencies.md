# ADR-0048: `fake` — test doubles for injected dependencies in examples

Status: Accepted

## Context

`example` (ADR-0046) evaluates a behavior at compile time, but only if the compiler can run it.
A behavior that `requires` an injected dependency (`事前承認する requires 現在時刻`; ADR-0029) could not
be evaluated: the dependency has no in-language implementation (it is provided from Java at the
boundary). Yet behaviors that depend on time, id generation, or an external lookup are most of a real
domain. The injected dependency itself needs no test (it is boundary code), but the behavior that
*uses* it does. So an example needs a way to stand a test double in for the dependency.

How other languages supply a test double for an injected dependency (light survey): algebraic-effect
handlers (Unison, Koka, OCaml, Eff) provide an implementation at the *use scope* (`handle e with h`);
ZIO/Cats build a test `ZLayer` and `provide` it; the Haskell handle pattern passes a record of stub
functions; F# passes stub functions, but its first advice is *dependency rejection* — move the
dependency to an input so no double is needed. Two lessons: the double is supplied at the use scope
(not global), and the best double is none. A third constraint is Souther's own: users have no
first-class functions (lambdas are second-class), so a function-shaped double is more naturally
*data* than a function.

## Decision

An example supplies a fake for each `requires` dependency of its target, at the example (never
global), reusing the behavior's existing injecting constructor (`bind`; the constructor takes one
`Behavior` per requirement). A fake is a `Behavior` proxy built at evaluation — it produces no
run-time class, so it never ships (like an example, zero Jar footprint).

Dependencies split by shape:

- A *value dependency* (a constant faked result) is given on the row with `with dep = value`:
  `… with 現在時刻 = "2026-07-20T09:00" -> …`. The value is a fixture decoded into the dependency's
  output type.
- A *function dependency* (result varies with input) is given by a `fake dep | (in) -> out | …`
  declaration — an input→output table matched by value equality (using newtype `==`, ADR-0047, so the
  key type is unrestricted, unlike a `Map` key). This reuses the example row grammar. A `_ -> out`
  row is an explicit default; without one, a lookup miss is a compile error (E1909). A value
  dependency is the degenerate zero-input case, so `with` covers it inline rather than a table.

A missing fake for a requirement is E1908. Best practice, kept from F#/smdd-book §8.5.5, is to write
an outside-world value as an input (dependency rejection) and need no fake at all.

Scope. Fakes are local to their module in this version; a shared injected behavior can already be
imported, so only the fake is written per consumer. Cross-module fake sharing (a shared test-layer,
as in ZIO) is deferred, and when added would use a test-scoped channel, not the domain `exposing`.
A fake for a multi-argument dependency, and a computed (non-tabular) fake, are also out of scope; the
latter is a sign the dependency should become an input.

## Consequences

- An example can now evaluate a behavior that `requires` an injected dependency, extending compile-
  time example checking to the effectful core of a domain — while the injected behavior's real
  implementation stays Java at the boundary.
- The function-dependency fake is data (a lookup table), which fits Souther's no-first-class-functions
  stance and is exactly what a test needs — concrete answers for concrete inputs — with a loud miss
  rather than a silent default.
- Nothing new ships: a fake is a proxy at evaluation and adds no class to the output. (The existing
  CTFE `$Ctfe` helper does ship; a fake deliberately does not follow that.)
- The design pushes modelers toward dependency rejection: the cleanest example has no fake because the
  dependency is an input.
