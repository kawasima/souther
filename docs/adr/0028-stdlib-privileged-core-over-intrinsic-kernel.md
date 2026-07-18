# ADR-0028: The standard library is a privileged core written in Souther over an intrinsic kernel

Status: Implemented (decided 2026-07-18). Amends ADR-0010.

## Context

The standard library — `length`, `map`, `filter`, `fold`, `contains`, and the rest — is
compiler machinery. The type checker knows each name's type and the backend emits its
bytecode; none of it is written in Souther. ADR-0010 recorded the user-facing half of this:
the only polymorphic types are the stdlib ones, and users write no generics.

The question is whether the stdlib itself should be written in Souther, as Elm and F# write
theirs, instead of living entirely inside the compiler.

Three of Souther's own rules stand in the way of writing it as ordinary Souther. The
combinators are generic (`map : (List<'a>, ('a) -> 'b) -> List<'b>`), and there are no user
generics (ADR-0010). List traversal needs recursion, and helper functions may not recurse —
they are expanded inline. The string and arithmetic primitives bottom out in JVM calls, and
Souther cannot call arbitrary JVM (ADR-0008).

## Decision

The stdlib is a set of privileged `core` modules written in Souther, over a small intrinsic
kernel — the Elm model, not the F# one.

The choice between the two reference languages is forced by the interop rule. F# sits on
.NET and calls the base library directly, so its primitives are ordinary interop and its
users write generics and recursion freely. Elm cannot call JavaScript except through
privileged Kernel modules, so its primitives are thin wrappers over a Kernel that only core
packages may write, and the rest of its stdlib is Elm on top. Souther forbids arbitrary JVM
calls exactly as Elm forbids arbitrary JS (ADR-0008), so only the Elm shape is consistent.

Concretely:

- The irreducible primitives — string operations, arithmetic (already operators), one list
  fold, the map accessors — are declared in `core` with an intrinsic body:
  `let length (s: String): Int = intrinsic "string.length"`. The backend emits the JVM
  primitive for each key. Writing an `intrinsic` body is a privilege of `core`.
- The derivable layer is written in Souther over the kernel. The one irreducible loop is
  `fold`, and the four combinators are `souther.list` helpers over it:
  `let all (xs: List<'a>, p: ('a) -> Bool) = fold(xs, true, (acc, x) -> acc && p(x))`, and
  `map`/`filter` the same, seeding an empty list `[]` and growing it. Each expands inline at
  its call site, where its type variables resolve to concrete types, so `map(xs, x -> ...)`
  reads exactly as the built-in did. String functions stay largely intrinsic, since Souther
  has no `Char` and index recursion over a string is not worth writing — Elm keeps most of
  `String` in the Kernel for the same reason.
- Generics and recursion are opened, but only inside `core`. A type variable is `'a`
  (F#/OCaml): a leading apostrophe marks it where Souther's case-insensitive identifiers
  rule out the lowercase convention Haskell and Elm rely on. A type variable needs no `<A>`
  declaration list; it is used inline and generalised over the signature, which also keeps
  it clear of the `List<T>` angle brackets. A core function may recurse because it is
  lowered to a method, as a behavior already is; a user helper stays inline and
  non-recursive. User modules remain bounded — no generics, no recursion, no intrinsic.
- The privilege is tied to a reserved namespace the compiler ships and auto-imports —
  `souther.string`, `souther.list`, `souther.map`, `souther.bool` — mirroring Elm's
  `String`/`List`/`Dict` and F#'s per-type modules. A user module cannot take a reserved
  name, so it cannot grant itself the privilege.
- `not` becomes the first self-hosted function (`let not (b) = if b then false else true`),
  and the prefix `!` operator is removed. Inequality changes from `!=` to `/=`, pairing with
  the `==` Souther already has, as in Elm.

## Consequences

The user-facing restriction of ADR-0010 stands — a business model still writes no generics
and no recursion — but its rationale narrows: generics and recursion are not absent from the
language, they are confined to a core that ships with the compiler. The stdlib gains a
single definition site, and its derivable half is exercised in Souther rather than asserted
by the compiler.

Confining recursion to `core` would matter only for processing recursive user data, but
Souther does not support self-referential data — no derived codec traverses a cycle — and a
business model in the SMDD sense (state machines, records, lists of records) does not ask
for it. So the confinement costs the model no expressiveness it would use, the same argument
ADR-0010 makes for the absence of user generics.

Deriving `map`/`filter` from `fold` forced one language relaxation: the empty-list literal
`[]`, which `[#stdlib-list]` had forbidden because its element type could not be determined.
It is now admitted with its type fixed by context — the accumulator a `fold` seed grows into,
the other operand of `++`, an `if`/`match` case, or the `List<T>` a position expects — which is
sound because an empty list is element-agnostic at runtime. Without it, no list-producing
combinator could start from an empty accumulator in Souther.

Self-hosting shifts where a combinator misuse is reported: a non-`Bool` `filter` predicate now
surfaces through the `if` the derivation expands to, not a bespoke check. To keep this from
pointing at shipped source, a type error inside an inlined prelude helper is stamped with the
call site, so it points at the user's call. The message still names the derivation's `if`; a
combinator-level message would need argument type-checking against the declared parameter,
which is left for later.

The value pipe `|>` is not adopted alongside this. Souther's combinators take the collection
first (`map(xs, f)`), where Elm and F# take it last precisely so `|>` threads, and the
behavior level already composes with `>->`.

## References

- Specification: `[#stdlib]`, `[#stdlib-list]`, `[#stdlib-string]`, `[#stdlib-map]`,
  `[#primitives]`, `[#delimiters]`
- ADR-0008 (asymmetric interop — why the Elm Kernel model, not F#'s open interop)
- ADR-0010 (no user generics — amended: generics live in a privileged core)
- ADR-0004 (derived codecs — why no traversal of recursive data)
