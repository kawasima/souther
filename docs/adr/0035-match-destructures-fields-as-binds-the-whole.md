# ADR-0035: A match case destructures a case's fields; `as` binds the whole; Option binds positionally

Status: Accepted

## Context

Souther's `match` is already in F# form (`match e with | Case -> ...`, ADR-0027), but a case's
fields could only be reached by binding the whole case with `as` and reading through it:

```
match c with
    | EmailContact as e -> Label { value = e.email }
```

F#/Elm/Rust users reach for destructuring in the pattern itself — `| EmailContact { email = e }` —
and the `as`-plus-dot form reads as a workaround. Two things were also slightly off relative to F#:
`as` had a per-Option meaning (`| Some as v` bound the *wrapped* value, not the whole), which is the
opposite of F#, where `as` binds the whole and `Some x` binds the payload.

## Decision

Add field destructuring to a case pattern, keep `as` for the whole matched value uniformly, and move
Option's payload binding to the positional F#/Elm form.

- **Field destructuring** mirrors record construction: `| Case { field = binding, ... }`, with the
  same `{ }`, `,`, and `=` as a record literal, and the same `{ field }` shorthand (bind the field to
  a same-name variable). Only the fields named are bound (partial is fine). Destructuring applies to a
  single named case only — an or-pattern binds the sum type and has no case fields.
- **`as` binds the whole matched value**, everywhere: `| 会員 as m` binds the case, `| A | B as x`
  binds the sum type, and `| 会員 { id } as 全体` binds both the field and the whole. There is no
  longer an Option-specific meaning for `as`.
- **Option binds positionally**: `| Some v` binds the wrapped value (F#'s `Some x`, Elm's `Just x`);
  `| None` is nullary. `| Some as v` is rejected with a message pointing at `| Some v`. `Some` is the
  one built-in case with an anonymous payload, so positional binding is meaningful only there; user
  cases are records and use `{ }` or `as`. Because the parser recognises `Some`/`None` by name (it
  cannot see the scrutinee's type), a user data named `Some` or `None` is rejected at symbol-table
  construction, so the name always means the Option case.
- **Fields bind identifiers only.** A field pattern names a variable (or shorthand); it does not match
  a literal (`{ email = "x" }` is not a pattern). `match` dispatches on the case; value conditions go
  in the body or a `require`, since Souther has no `_` wildcard or `when` guard to fall through on
  (Elm's stance). Nested destructuring is one level — to go deeper, bind the field and `match` it
  again, matching the nested-sum rule (each sum level is matched independently, spec §match).

## Consequences

Field destructuring is pure surface sugar. The parser rewrites `| Case { f = x, ... } [as m] -> body`
to a normal `as` binding plus `let` reads — `| Case as $m -> let x = $m.f in ... body` (a fresh `$m`
when no `as` is written) — so the AST, type checker, and backend are unchanged: the field reads are
ordinary `FieldAccess` (an unknown field is the existing field error), and exhaustiveness, or-pattern
binding, and codegen keep working as before. The whole feature is a change to `parseMatch` alone.

The Option change reverses the `| Some as v` reading recorded in ADR-0011, which is amended
accordingly. Existing `| Some as v` sites migrate to `| Some v`.

## References

- Specification: `[#match]`, `[#algebraic-types]`
- Amends: ADR-0011 (Option's `as`-binds-the-wrapped-value reading)
- Related: ADR-0013 (sum cases are named-data references), ADR-0027 (match in pipe-case form),
  ADR-0026 / ADR-0030 (record literal `,` / `=` reused by the destructuring pattern)
