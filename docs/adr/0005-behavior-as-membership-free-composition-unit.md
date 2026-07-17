# ADR-0005: behavior is a membership-free composition unit; spec and implementation are separate

Status: Accepted

## Context

The spec DSL's `behavior` defers responsibility placement: unlike a class diagram, it does not attach an operation to a type. To keep the one-to-one correspondence (see ADR-0001), the implementation model has to preserve that property rather than force each behavior into some owning class.

## Decision

Internal computation, outside-world dependency, validation, and transformation are all expressed as behaviors and composed sequentially with `>->`. A behavior is a top-level input/output relation with no class membership.

## Consequences

A behavior, as a type, states only the **spec**; a simple behavior has no body. Its implementation is written as a `let` (see the let chapter), or, if none is written, is injected from Java (see ADR-0006). The one exception is `>->` composition: it appears in the spec DSL as composition, yet written out it is itself the implementation.

Only behaviors that appear in the spec DSL are behaviors; implementation helpers stay as `let`. Keeping helpers out of the behavior list means the list of behaviors never diverges from the list in the spec DSL — a helper `let` cannot be placed as a `>->` stage and does not appear in `exposing`.

## References

- Specification: §2.4, §12
