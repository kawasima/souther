# ADR-0047: A single-value newtype is compared by the value it wraps

Status: Accepted. Amended — arithmetic, deferred at the time of this decision, was added later
(see the "Arithmetic" paragraph below and spec §newtype-arithmetic / §invariant-discharge).

## Context

`data 金額 = Int` is a newtype: a domain name and an invariant over a single underlying value. The
value is reached as `金額.value`. But `value` is not a name the author writes — the `data X = Y`
syntax generates it. So requiring `.value` to compare amounts (`m.額.value <= 100`) leaks an implicit
detail into every comparison, and reads as ceremony.

Two things were already inconsistent. Equality (`==` / `/=`) works on any data structurally, so
`金額 == 金額` compares the wrapped values without `.value`; but ordering (`<` `<=` `>` `>=`) was
defined only for the five primitive types, so `金額 <= 金額` was a type error and you dropped to
`.value`. And a bare literal never met a newtype: `金額 == 100` did not type-check either.

Souther has already extended ordering beyond Elm. Elm orders only `Int`/`Float`/`Char`/`String`;
Souther adds `Decimal`/`Date`/`DateTime` because the JVM carries them as `Comparable`. A single-value
newtype over an ordered type is "morally" that type with a name and an invariant, so ordering it by
the wrapped value is the same kind of extension — and it removes the `.value` noise the author never
asked to write.

The pull against it is nominal safety: the whole point of `金額` and `数量` being distinct newtypes is
that they must not be confused. Any relaxation has to keep `金額 <= 数量` an error.

## Decision

A single-value newtype (`data X = Y`) is compared by the value it wraps, for both equality and
ordering, so `.value` is not written. The nominal boundary is kept by four rules:

- Two of the same newtype compare their wrapped values: `金額 <= 金額`, `金額 == 金額`.
- A bare literal of the wrapped type takes the newtype from the other operand: `金額 <= 100`,
  `金額 == 0`. Only a source literal is taken this way — this mirrors how `[]` takes its element type
  from context (ADR-0028).
- Two different newtypes over the same base do not compare: `金額 <= 数量` is a type error, even
  though both wrap `Int`.
- A non-literal value of the wrapped type is not taken implicitly: `金額 <= n` (with `n: Int`) is a
  type error — write `金額 <= 金額(n)`.

Ordering additionally requires the wrapped type to be ordered (Int/String/Decimal/Date/DateTime);
equality works over any wrapped type. The unwrap recurses, so a newtype over a newtype
(`管理職 = レベル = Int`) reaches its base. In the backend, a newtype operand of a comparison is opened
to its wrapped value (its `value` accessor) before the primitive comparison, so `金額 <= 金額` emits
the same integer comparison `金額.value <= 金額.value` would.

Arithmetic on a newtype (`金額 + 金額`) was out of scope in the original decision — only comparison
was adopted — because it raised questions this decision did not settle: whether the result re-wraps
(and re-checks the invariant, so `金額 - 金額` could abort on a negative), and which operators make
domain sense. **Arithmetic was added subsequently**, resolving those questions:

- Closed `+`/`-` stay in the newtype (`金額 - 金額 : 金額`): the operator opens each operand to its
  base, computes, and re-wraps, re-checking the invariant. A `金額 - 金額` that goes negative aborts
  inside the domain, or is *discharged* at compile time when a `require` guard establishes it (the
  invariant-discharge check, spec §invariant-discharge).
- Scalar `*`/`/` by a plain number of the base also stay in the newtype (`金額 * 2`) — the dimension
  is unchanged. A product of *two* newtypes (`単価 * 数量`, a dimension change / units) is not modeled
  and stays rejected.

See spec §newtype-arithmetic. The re-wrap/invariant question is answered by the invariant-discharge
check, and the operator question by "dimension-preserving only".

## Consequences

- `m.額.value <= 100` becomes `m.額 <= 100`, and `m.額 <= m.予算` (two amounts) now type-checks. The
  implicit `value` name stays implicit; the author writes the domain name.
- Equality and ordering are now consistent for newtypes (both read the wrapped value), and a bare
  literal compares against a newtype in both.
- `金額` and `数量` remain uncomparable to each other and to a raw `Int` variable, so the nominal
  distinction that motivates newtypes is intact.
- Arithmetic was subsequently added on the same wrapped-value footing — closed `+`/`-` and scalar
  `*`/`/`, re-wrapping and re-checking the invariant. The re-wrap/invariant question this decision
  deferred is resolved by the invariant-discharge check (spec §invariant-discharge); a product of two
  newtypes (units) remains out (no dimensions, ADR-0010).
