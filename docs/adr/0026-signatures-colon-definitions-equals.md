# ADR-0026: Signatures use `:`, definitions use `=`; a function is defined with `let`

Status: Accepted (decided 2026-07-18; implemented). Revised 2026-07-18 — see *Revision*.

## Revision (2026-07-18)

The original Decision said `:` applies "on record fields" without splitting the two places
a field name appears: a `data` **declaration** (`{ id : 会員ID }`), where the field is given
a *type*, and a record **literal** (`会員 { id : x.id }`), where the field is bound to a
*value*. It left the literal on `:`, and — separately — left a declaration's fields
whitespace-separated while a literal's were `,`-separated. Both were too loose: by this
ADR's own principle a value binding is `=`, not `:`, and the declaration/literal split in
delimiters had no reason behind it beyond "one `:` looked uniform".

The correction: a record literal's fields use `=` (a value binding), and a `data`
declaration's fields are `,`-separated like a literal's. Amending an accepted ADR in place
(rather than superseding it with a new one) is a deliberate exception to ADR immutability,
made because the prior wording was under-specified rather than a genuine alternative worth
preserving as history. The Decision text below reflects the corrected rule.

## Context

Three top-level forms bind a name. `data` introduces a type, `behavior` declares a
behavior, and `fn` gives a behavior its implementation or defines a private helper. All
three use `=` today:

```text
data     金額     = Int
behavior 提出する  = (申請: 申請準備中, 提出日時: String) -> 提出済み | 却下
fn       提出する  (申請, 提出日時) = { ... }
```

`=` is a single universal binder; the leading keyword decides whether its right-hand side
is read as a type or a term.

Two things sit awkwardly. `fn` is an abbreviation where `data` and `behavior` are whole
words, and it is not the word any ML-family language uses for a definition. And a
`behavior` line and an `fn` line both use `=` even though one binds a type — the signature
— and the other binds a term — the body. The same symbol carries two meanings.

## Decision

Adopt the ML-family split between signature and definition. A value signature uses `:`; a
definition uses `=`. `fn` becomes `let`, and a function keeps its parameters on the left of
`=`.

```text
data     金額     = Int                                       // type definition
behavior 提出する : (申請: 申請準備中, 提出日時: String) -> 提出済み | 却下   // value signature
let      提出する (申請, 提出日時) = { ... }                     // value definition
behavior 会員照会 = findMember >-> 整形する                      // a composition is a definition
```

`:` now means "has this type" uniformly — on a `data` declaration's fields, on parameters,
and on a behavior's signature, i.e. wherever a name is given a *type*. `=` means "is defined
as" uniformly — a data's type definition, a record literal's field (which binds a *value*),
a `let`'s body, and a behavior defined as a composition. A behavior appears in two forms:
`behavior f : T` states a type whose implementation is a separate `let` or is injected, and
`behavior g = a >-> b` defines `g` as a composition.

```text
data 会員 = { id : 会員ID, メール : メールアドレス }   // declaration fields: `:` (a type)
let f (x) = 会員 { id = x.id, メール = x.メール }       // literal fields: `=` (a value)
```

A `data` declaration's fields and a record literal's fields are both `,`-separated, so the
only difference between the two is exactly the `:`/`=` distinction: a declaration ascribes
a type, a literal binds a value. (`include` is a type-composition clause, not a field — like
`invariant` it stands alone and takes no comma, ADR-0012; only the fields it leads are
comma-separated.) Elm draws the same line — `{ x : Int }` is a record type,
`{ x = 1 }` a record value (and `{ r | x = 2 }` an update, which Souther writes as a
type-named spread `会員 { ..r, x = 2 }`, ADR-0018).

Parameters stay on the left of `=` (`let f (x) = e`), not bound as a lambda
(`let f = (x) -> e`). F#, OCaml, and Haskell all write the named form and treat the
lambda-binding form as redundant; Souther already rejected `fn f = (x) -> ...` on the same
ground (ADR-0019). Because parameters sit left of `=` in a definition and inside the type
to the right of `:` in a signature, the two shapes never collide.

The prior art is uniform: Elm writes `add : Int -> Int -> Int` then `add x y = ...`; F#
writes `val add : int -> int` then `let add x y = ...`; OCaml and Haskell do the same with
`val` and `::`. In every one, the type-definition keyword (`type`) uses `=` and a value
signature uses `:` or `::`.

## Consequences

The asymmetry between `data X = ...` and `behavior f : ...` is the ML boundary between
defining a type and ascribing a type to a value, not an inconsistency. A data's right-hand
side is the type's content; a behavior's is the type of a value whose body lives in the
`let`. Collapsing them was considered and rejected: giving `behavior` its `=` back returns
to the overloaded binder, and giving `data` a `:` would ascribe a type to a type, which is
meaningless.

`behavior` and `let` keep the separation of spec from implementation (ADR-0005). The
signature is the spec-facing surface, the `let` its body, and the two are paired by name —
as a Haskell signature sits above its equation. The behavior/fn split is unchanged in
meaning; only the implementation keyword and the signature's binder change.

## References

- Specification: `[#delimiters]`, `[#behavior]`, `[#fn]`, `[#fn-declaration]`,
  `[#injected-behavior]`
- ADR-0005 (behavior and implementation are separate)
- ADR-0019 (one arrow `->`; this ADR refines what `:` and `=` each bind)
