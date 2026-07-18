# ADR-0017: Declare, don't infer: requirements and composed output (construction permission is optional)

Status: Accepted

## Context

A behavior is a specification. Its construction permission (`constructs`), its dependencies (`requires`), and — for a pipeline — its output could all be inferred from the implementation. Inference is possible here; the question is whether it should be used.

## Decision

Requirements are declared on the behavior and checked against the implementation, with exact match required — even an over-broad, safe-side declaration is rejected. Construction permission (`constructs`) is checked the same way *when written* (under-declaration E1002, over-declaration E1006), but on an let-backed behavior it is optional and inferred when omitted (ADR-0002): unlike a requirement, it is invisible to callers, so inferring it changes no outward contract. An injected behavior must still declare it — no visible body to infer from, and it drives factory generation. `>->` composition is inferred from the union over its stages; even there the output may be optionally declared to pin the blame for far-away changes to that definition.

## Consequences

An inferred type equals the implementation by definition, so an implementation could never violate it. A behavior that is meant to be a specification would then be checking nothing — inference amounts to throwing the check away. This holds for whatever forms the behavior's *outward contract*: its input/output type and its requirements, which callers and injectors depend on. It does **not** hold for `constructs`, which is an internal permission a caller never sees; inferring it changes no contract, so on an let-backed behavior it may be omitted and inferred (ADR-0002). When written, it is still checked exactly (under-declaration E1002, over-declaration E1006), so a reader who wants the mint-versus-pass-through record — a fact the output type does not show — can opt into it.

Requirements are checked the same way: the set of implementation-less behaviors a `let` uses must equal the `requires` it declares — too few is E1602, too many is E1603. Over-broad declarations are refused, so `requires` names exactly what is used. Composed output, when declared, must match the inferred output exactly — an upstream stage that adds a case makes the composition's declaration site the error, rather than letting the output grow silently downstream (E1604).

Prior art takes the same position. Flix requires top-level type *and* effect annotations and forbids even sub-effecting (over-broad effect declarations), for three reasons: signatures work as documentation, they point to responsibility precisely, and type inference can be parallelized (Madsen, *The Principles of the Flix Programming Language*, Onward! 2022, Principle 7). Flix's type/effect inference is complete (Hindley-Milner), and it still demands the annotation. Unison shows the other outcome: it treats a bare `->` as "infer the abilities," so a definition annotated pure (`hello : '()`) whose body calls `printLine` is accepted and its type is rewritten to `'{IO} ()`; the reporter wrote that they "expected the hand-written type signature to be rejected" (unison #691). Inference makes the declaration follow the implementation, which is not a specification.

## References

- Specification: §12.3, §12.6, §14.5
- Madsen, *The Principles of the Flix Programming Language*, Onward! 2022, Principle 7
- Unison issue #691
