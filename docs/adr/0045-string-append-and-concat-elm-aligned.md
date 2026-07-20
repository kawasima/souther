# ADR-0045: `++` over strings and Elm-aligned `String.append` / `String.concat`

Status: Accepted

## Context

Until now Souther had no string-concatenation operator. `++` was list-only (both sides had to be
lists), `+` was numeric-only (Int/Decimal), and joining two strings meant calling
`String.concat(a, b)`. That function carried the name of Elm's `String.concat` while having the
signature and meaning of Elm's `String.append` — Elm's `concat` is `List String -> String`, not the
two-string form. Anyone reading `String.concat(a, b)` against Elm would expect a list-flattening
function and find a binary append instead.

The language grounds its syntax in the ML family (F#/Elm), and there the two live at different
operators on purpose. SML and OCaml use `^` for strings and keep `+` numeric; Haskell and Elm use
`++` for both strings and lists (an `appendable`/`Semigroup` join) and keep `+` numeric. F# is the
family's outlier: it overloads `+` for strings and uses `@` for lists. Souther's list operator is
already `++` (Elm's, not F#'s `@`) and its `+` is already numeric-only, so the codebase was already
on the Elm side of this split. Adding string concatenation as `+` would have meant overloading the
arithmetic operator — exactly what Elm avoids — and would have left `++` (lists, Elm) and `+`
(strings, F#) as a mix matching neither language.

## Decision

Make `++` Elm's `appendable`: it concatenates two lists to a list or two strings to a string. A
mixed pair (a list and a string, or either paired with a non-list non-string) stays a type error.
`+` remains numeric-only. In the type checker the string case is settled before the empty-list
absorption that the list case needs; in codegen two strings emit `String.concat` (the JVM
`a.concat(b)`) rather than the list join.

Align the two `String` functions to Elm by name and signature:

- `String.append(a: String, b: String) : String` — joins two strings in written order
  (`append(a, b)` equals `a ++ b`), matching Elm's `String.append`. The old `String.concat(a, b)`
  is this function under its correct name.
- `String.concat(xs: List<String>) : String` — flattens a list of strings with no separator
  (`concat(["a", "b", "c"])` is `"abc"`), matching Elm's `String.concat`. It is `join("", xs)`.

Both `++` and `String.append` are kept, as Elm keeps both: `++` is the common inline form, and the
named function is the value passed where a function is expected. This is a deliberate departure from
the earlier "add a standard-library function only when an example demands it" rule — the language is
past that MVP stage, and full fidelity to Elm's String module is the target.

## Consequences

- `"a" ++ "b"` is now the idiomatic way to join strings; the examples and tests that called the old
  binary `String.concat` were rewritten to `++`.
- `String.concat` changes meaning (binary → `List<String>`). No source outside the compiler's own
  tests and examples used it, so the break is contained; it is called out here because the name is
  reused, not merely renamed.
- The `++` type-mismatch diagnostic now reads "two lists or two strings"; the checker and codegen
  each gained one string branch, and the runtime gained `Strings.concat(List<String>)`.
- The rule to remember matches Elm: `++` joins sequences (lists and strings), `+` is numbers only,
  `String.append` is the two-string function form, `String.concat` flattens a list of strings.
