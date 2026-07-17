# ADR-0027: match reads `match e with | arm -> ...`

Status: Accepted (decided 2026-07-18; implemented)

## Context

match is written as a braced block with a `case` keyword before each arm:

```text
match 出張申請を提出する(申請) {
    case 提出済み as s   -> ...
    case 事前承認待ち as p -> ...
}
```

That shape is the C-family `switch { case }`, and it is neither of the ML forms Souther
grounds in. F# writes `match e with | P -> ...`; Elm writes `case e of` followed by
layout-indented arms.

## Decision

Adopt the F# form. `|` separates the arms, and there are no braces and no `case` keyword.

```text
match 出張申請を提出する(申請) with
| 提出済み as s   -> ...
| 事前承認待ち as p -> ...
```

`|` is already the arm separator of a sum type (`data X = A | B`); F# uses the same symbol
inside `match`, and Souther follows. An or-pattern is `| A | B -> ...`.

Elm's `case ... of` was not taken because it delimits arms by layout, and Souther is not
layout-sensitive. F#'s `|` delimits arms without layout, so it is the ML form that fits a
grammar built on braces and tokens.

## Consequences

match loses the braces that made it look like Souther's other blocks, but it gains the ML
reading and drops the redundant `case` keyword. After `with`, a `|` is unambiguous: a match
arm is a pattern, not an expression, so it cannot be confused with the sum-type `|` in a
type position or the `||` operator in a term.

The arm still binds by `as` and dispatches on a named data reference (ADR-0013); an
or-pattern still binds a variable at the scrutinee's sum type. Only the surrounding
syntax — `with` and `|` in place of braces and `case` — changes.

## References

- Specification: `[#match]`, `[#delimiters]`
- ADR-0013 (sum arms are named data references — a pattern is an arm name)
- ADR-0019 (one arrow `->` for match arms)
