# ADR-0038: User helpers may recurse over self-referential data

Status: Accepted

## Context

The inline-only helper model kept recursion out of the user language, and ADR-0028 confined it to the privileged
`core` namespace. ADR-0028 justified the confinement with a factual claim:

> Confining recursion to `core` would matter only for processing recursive user data, but
> Souther does not support self-referential data — no derived codec traverses a cycle.

Both halves of that claim were wrong. A `data` may already refer to itself — an org chart is
ordinary business data:

```
data 社員 = { 上司: 社員?, 氏名: String }
```

The compiler accepts this, and the derived decoder/encoder already traverse it: a nested
`{"氏名":"a","上司":{"氏名":"b"}}` decodes to a `社員` whose `上司` is a `社員`. Self-referential
data is not absent from Souther; it works. What was missing is the ability to *compute* over it.
`fold` is the one loop primitive and it folds a `List` of fixed structure; it cannot walk a
reporting line to the top, collect all ancestors, or measure the depth of a tree, because the
depth is unbounded. A user helper could express these, but helpers were inlined, so a self- or
mutual call had no finite expansion and was rejected up front.

F#/Elm users reach for recursion for exactly this — walking a self-referential structure — and
the SMDD domain has such structures (org charts, category trees, bill-of-materials). The
confinement bought the model nothing here; it removed expressiveness the model does use.

## Decision

A user helper may recurse. Overturn the "no user recursion" of ADR-0028 and the inline-helper model for named
helpers.

- **A recursive helper is lowered to a method, not inlined.** The helper inliner already detects
  which helpers lie on a call cycle (self or mutual). Those are left standing at their call sites
  and emitted as `static` methods on a package-private `$Fns` class; a self- or mutual call is a
  plain `invokestatic`. Non-recursive helpers are still inlined, unchanged — combinators keep
  self-hosting over `fold`, and the inliner keeps stamping prelude-helper errors at the call site.
- **A recursive helper must declare its return type**: `let 深さ (s: 社員): Int = ...`. The result
  cannot be inferred through the cycle without a fixpoint; the declared type lets a self-call be
  typed before the body is checked. Parameter types are already declared for helpers. A
  non-recursive helper still infers its return type, so the annotation is required only when the
  helper actually recurses.
- **Mutual recursion is allowed.** Every member of a call cycle is lowered together, so
  `ping`/`pong` calling each other work the same as a self-call — the cycle detector already
  identifies the whole group.
- **A recursive helper is pure.** It is a `static` method with no injected fields, so it cannot
  call an injected behavior (a `requires`); the effect belongs in the behavior that calls the
  helper. Recursion is for traversing data, not for reaching the outside world.
- **A recursive helper may construct data**, and its constructions are attributed to the behavior
  that calls it. Because the helper is not inlined, a caller's body shows only a call, not the
  construction inside it; the `constructs` inference follows the call into the helper (transitively,
  through mutual recursion) so a rebuilt tree or a constructed result counts toward the behavior's
  `constructs` clause exactly as an inlined helper's construction would. An invariant violation
  inside the helper aborts, as any interior construction does.
- **Divergence is a platform failure.** Souther proves no termination (as it proves no
  invariants statically). A non-terminating recursive helper overflows the JVM stack, and a
  `StackOverflowError` propagates like any other platform failure (ADR-0029). There is no fuel
  counter and no depth cap.
- **Local recursion stays out.** A `let`-bound lambda still may not refer to itself: it is a
  β-reduced inline block, and making it recurse would need a runtime closure. Recursion is a
  property of a named module helper only.

Because self-referential data is now a deliberate, supported shape, one soundness gap is closed
alongside: a `data` whose construction requires constructing itself through **mandatory** fields
with no base case is uninhabitable and is rejected at compile time. `上司: 社員` (no `?`) is a
base-less cycle — no value can ever be built — so it is a compile error, not a runtime overflow.
An optional (`?`) field or a `List`/`Map` field is a base case (`None`, the empty collection) and
breaks the cycle. A sum is OR-composed, so the check does not propagate through one.

## Consequences

The user-facing bound narrows again: a business model still writes no generics (ADR-0010),
but it may now write recursion over its own self-referential data. The "helpers never recurse" of
`[#fn-declaration]` is replaced by "a recursive helper is lowered to a method"; the earlier inline-only model
survives for the non-recursive majority.

The recursion mechanism reuses the behavior-body emission path (`emitBodyTail` over the Core IR,
ADR-0021), so it adds a lowering target rather than a new backend. Recursive-helper signatures are
registered in the type environment as ordinary function types, so a self- or mutual call type-checks
through the existing function-application path with no new checker parameter.

Confining recursion to `core` (ADR-0028) is no longer the boundary; `core` keeps its generics and
monomorphization privilege, but recursion is shared with user helpers. ADR-0004's "derived codecs do
not traverse recursive data" is corrected: they do traverse a self-referential shape, bottoming out
at the optional/list break that makes the shape inhabitable.

## References

- Specification: `[#fn-declaration]`, `[#algebraic-types]`
- ADR-0010 (no user generics — the recursion ban was the inline-helper model's, not 0010's)
- ADR-0028 (recursion confined to core — amended: its "no self-referential data" premise was false)
- ADR-0029 (platform failures are exceptions — a non-terminating recursion overflows the stack)
- ADR-0021 (Core IR and the Lower stage — the recursion lowering reuses the behavior-body path)
- ADR-0004 (derived codecs — corrected: they do traverse a self-referential shape)
