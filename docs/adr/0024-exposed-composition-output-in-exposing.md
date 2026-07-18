# ADR-0024: An exposed composition declares its output signature in the exposing list

Status: Accepted

## Context

A `>->` composition behavior infers its output from its stages. ADR-0017 keeps that inference but lets a composition *optionally* declare its output, so a far-away change pins the blame (E1604) to the definition instead of letting the output grow silently.

That optional declaration is weakest exactly where a change is least visible: the module boundary. When a composition is listed in `exposing`, its consumers compile separately — another module, or Java. An upstream stage that adds a case, or a downstream stage that narrows its input, changes the composition's output; the definition line is unchanged and still compiles, so nothing at the definition reports it. Whether the consumer notices depends on how it consumes. A Java exhaustive `switch` over the generated sealed interface (§19.8) fails to compile; a `switch` with a `default`, or JSON serialization at the boundary, does not. Inside the same module the change surfaces wherever the value is matched. Across the export boundary it need not surface at all.

## Decision

A composition (Pipe) behavior listed in `exposing` must declare its output, and the declaration is written in the `exposing` list, not on the definition line:

```
module example.order exposing {
    注文を確定する : 確定注文 | 在庫不足
}
```

The definition stays point-free. The declared signature is checked against the inferred output with the exact-match rule of ADR-0017 (E1604): a mismatch in either direction is a compile error. Non-composition behaviors already carry their full type at the definition, so their exposing entry is just the name; only compositions — whose type is not written at the definition — carry a signature in exposing. Internal, non-exposed compositions keep ADR-0017's optional declaration.

## Consequences

An upstream case addition now makes the exposing signature the error site (E1604): the author must consume the case in the pipeline or update the published signature, at the module that owns the boundary, at recompile time. A change to the published contract is made deliberately rather than discovered downstream.

The exposing list becomes the module's interface description — implementation inferred, boundary explicit — like an OCaml `.mli`, an F# `.fsi`, or a TypeScript `.d.ts`. This is the mainstream arrangement: infer internally, annotate at the module boundary. Rust requires signatures on `let` items; TypeScript's `isolatedDeclarations` errors on an exported declaration without an explicit type; typescript-eslint's `explicit-module-boundary-types` requires types only on exports and leaves internal code to inference; the Haskell convention annotates every top-level binding (`-Wmissing-signatures`). Each is a tool that exists to stop a boundary from being inferred.

The cost is that an exposed composition's output is stated twice — in its upstream stages and in the exposing signature — and an intended case addition must edit both. The second edit is the intent: the boundary changes because someone wrote it, not because inference carried it across.

This narrows ADR-0017 for the exposed case (optional to required) and fixes the location (the exposing list). It revises §14.5, which had the declaration optional and on the definition.

## References

- Specification: §4, §14.5, §19.8
- ADR-0017 (declare, don't infer — this ADR narrows its composed-output case)
- ADR-0007 (unmarked sum; the off-ramp is decided by composition)
- ADR-0001 (one-to-one with the spec DSL — the point-free composition form this preserves)
- TypeScript `isolatedDeclarations`; typescript-eslint `explicit-module-boundary-types`
