# ADR-0001: The implementation model maps one-to-one onto the spec DSL

Status: Accepted

## Context

Souther implements the *specification DSL* of specification model driven development (SMDD). The spec DSL describes business rules with `data` (AND / OR / List / `?`) and `behavior` (`->` / `>->`), but it deliberately leaves three things in comments — value constraints (`// 0 or greater`), the "this value has been validated" invariant (parse-don't-validate), and outside-world dependencies (`// depends on:`, `// side effect:`) — so that a human or an LLM can read the whole model in one sitting. Souther's job is to make those three executable without inserting a translation layer that would let the two models drift apart.

## Decision

Souther promotes exactly the three commented-out concerns into `invariant`, closed construction paths (`decoder` / `constructs`), and `requires` + Java injection. One spec DSL definition maps to one Souther definition; nothing else is added, and no intermediate representation stands between them.

## Consequences

The spec DSL's structure (distinction, requiredness, multiplicity, state transitions, totality) already lands directly on Souther's types, so only value constraints and outside-world dependencies get promoted. The split between spec and implementation is not there because `data` happens to work that way — it exists so that the single spec DSL line survives intact. As `invariant` rides on `data`, `constructs` rides on `behavior`; both are the part Souther made executable out of what the DSL left in a comment.

Because the correspondence is at the level of *declarations*, it does not dictate the *form* of the implementation. A spec `behavior` line maps to one Souther `behavior` declaration, but whether that behavior is realized as an `fn`, an injection, or a `>->` composition is not mechanically derivable from the DSL — the `// 依存:` / `// 副作用:` notes are hints, and the modeler chooses. A `// 依存:` note is documentation, not an obligation, so its absence does not prove a behavior is internal.

The non-functional requirement that one spec DSL definition corresponds to one Souther definition without a translation step is the invariant every other decision here is measured against. When a rule would force the DSL line to be rewritten to be transcribed, that is the signal the rule is wrong (see the routing rule keeping the head stage of a pipeline unconstrained, and the injection form carrying no keyword — ADR-0006).

## References

- Specification: §1, §1.1, §2, §12, §13.2, §28, §29
