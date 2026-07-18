# ADR-0036: Tuples are expression-level first-class values; a module's name lists use parentheses

Status: Accepted

## Context

Souther models multiple values as a named product `data`, so a helper that wants to return two
things must declare a one-off record for them. F#/Elm users reach for a tuple `(a, b)` there and find
the record boilerplate heavy for a throwaway pair. Souther had no tuple.

Adding `(a, b)` for values also frees a syntactic question: a module's name lists — its `exposing`
surface and its `import` lists — were written in braces (`exposing { a, b }`, `import M { a, b }`), but
Elm, which Souther grounds its surface in, writes both in parentheses (`exposing (a, b)`,
`import M exposing (a, b)`) and keeps braces for records. Once `( )` groups values, using it for the
name lists too makes `{ }` mean records and blocks throughout.

## Decision

Add tuples, expression-level and first-class, and move the module's name lists to parentheses.

- **A tuple `(a, b, ...)`** is two or more values carried together (ADR-0036). It is expression-level
  only: it is never a data field's type or a behavior's input/output, so — like a function value
  (ADR-0025) — it never crosses a codec boundary and no codec is derived. The domain vocabulary
  (`data`, `behavior`) stays named. A one-element `(e)` is just a parenthesised expression.
- **It is first-class within that scope**: it can be bound by `let`, passed to a helper, and be a
  `List` element (`List<(a, b)>`), carried at runtime as an `Object[]`.
- **`let (x, y) = t` destructures positionally** — the names bind the elements left to right, and the
  pattern's arity must equal the tuple's in either direction (Elm rejects a mismatch), so a helper that
  returns three things cannot be silently read into two names. It is
  the only way to read a tuple; there is no `fst`/`snd`, since those are two-tuple-only and
  destructuring is general. A lambda's `(a, b) -> ...` is its parameter list (ADR-0025), not a tuple
  pattern, so to open a tuple inside a lambda you take one parameter and `let (a, b) = p` in the body.
  A tuple is not matched with `match` (it has one shape, not cases).
- **`exposing` and `import` take parentheses**: `module X exposing (a, b)`, `exposing (f : A | B)` for
  an exposed composition's output (ADR-0024), and `import M ( a, b )`, matching Elm and leaving `{ }`
  for records and blocks.

## Consequences

Tuples add `Type.TupleOf`, the AST/Core nodes `Tuple` and `TupleGet`, and the parser reads `(a, b)`
as a tuple where a lambda's `(a, b) ->` is already taken by lookahead. `let (x, y) = t` desugars in
the parser to a whole binding plus positional reads (`let $t = t in let x = $t.0 in ...`), so the
checker and backend treat the reads as ordinary element accesses. The backend emits a tuple as a
boxed `Object[]` and a read as an indexed load, cast back to the element type.

Because a tuple is a value that flows through the same passes as any expression, every pass that
walks expressions had to descend into its elements — the qualify-imports rewrite, the newtype-
constructor desugar, helper inlining, the requirement and construct walks, and free-variable
collection — or a call inside a tuple would be missed. A pass with a catch-all `default` that skipped
tuple children was the recurring hazard.

The name-list change is mechanical: the parser reads `( )` for both `exposing` and `import`, and every
`exposing { ... }` and `import M { ... }` migrates to parentheses across the examples, tests, spec, and
README. Braces now mean records and blocks only.

## References

- Specification: `[#tuple]`, `[#blocks]`, `[#modules]`, `[#delimiters]`
- Related: ADR-0025 (first-class functions — the same expression-level, non-crossing-the-boundary
  treatment), ADR-0024 (exposed composition output), ADR-0013 (sum cases are named data)
