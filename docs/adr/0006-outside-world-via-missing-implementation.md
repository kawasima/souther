# ADR-0006: Outside-world dependencies are behaviors with no implementation, injected from Java

Status: Accepted

## Context

Souther must not implement outside-world effects — database queries, HTTP calls, file
access, clock reads, id generation, message sending. These are exactly the things the
specification DSL annotates with `// 依存:` (depends on) and `// 副作用:` (side effect).
The language needs a way to name such a dependency as a type while leaving its
implementation outside, and to do so without adding surface that the spec DSL does not
have.

## Decision

A behavior whose type is declared but that has no implementation is an outside-world
dependency: its implementation is injected from Java. There is no keyword — the *absence*
of an implementation is what means "supply this from outside." An implementation is
either a same-named `let` (`[#fn-declaration]`) or a `>->` composition on the right-hand side (`[#composition]`); a
behavior with neither is the injection target.

## Consequences

The spec DSL has no `required` word either; a dependency is marked only by a comment
note. Because Souther also uses no keyword, the DSL line survives verbatim — `behavior
現在時刻 = () -> DateTime` with no `let` is the whole declaration.

Code that uses such a behavior lists its name under `requires`, which then surfaces as an
argument of the using `let` (see ADR-0016). The read-only "// 依存" versus mutating
"// 副作用" distinction is documentation of intent only; it does not affect the value
composition rules.

`constructs` is still required on a non-implemented behavior (`[#constructs]`): the declaration
reads the same as if it were implemented in Souther — `findMember` mints its failure cases
but does *not* mint `会員` (it reads an outside value through a decoder). The generated
Java base class (`[#java-base-class]`) hands out factories for the declared unit cases from here.

Which behaviors get a `let` and which are injected is not mechanically derivable from the
DSL: `// 依存:` is a note, not an obligation, so its absence does not prove a behavior is
internal. The one-to-one correspondence (ADR-0001) is therefore at the level of the
*declaration*, not the implementation form (`let` / injection / `>->`); the modeler chooses
the form using the `// 依存:` / `// 副作用:` notes as a guide.

## References

- Specification: `[#no-impl-for-outside]`, `[#injected-behavior]`, `[#java-base-class]`
- ADR-0001 (one-to-one with the spec DSL), ADR-0016 (requirements as arguments)
