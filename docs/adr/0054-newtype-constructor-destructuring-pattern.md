# ADR-0054: Constructor-destructuring pattern for a newtype in `match`

Status: Accepted.

## Context

A newtype may wrap another newtype: an email address is `data メールアドレス = String`, and its confirmation state is `data アクティベート済み = メールアドレス` / `data 未アクティベート = メールアドレス`, joined into `data メール = アクティベート済み | 未アクティベート`. Modelling the state as a newtype over `メールアドレス` (rather than a record `{ アドレス: メールアドレス }`) is the honest "is-a": an activated value *is* a mail address in a state, not a record that *has* one.

Reading the base `String` out of that stack cost `.value.value` — the newtype's implicit accessor (ADR-0014) chained twice. That chain is the wart. In ML-family languages the wrapped value is not reached by chaining an accessor deeper into an expression; it is destructured at the boundary. Elm has no accessor at all (`Activated (Email s) -> s`), and F#'s idiom is a pattern (`let (Email s) = e`) or a one-line projection helper; both reach any depth in a single nested pattern, so `.value.value` never arises. Souther had a record-destructuring pattern (`| 会員 { id } -> ...`) and a whole-value `as` binding, but no way to open a newtype in a pattern, so depth had to be taken with accessors.

The alternative directions for removing the chain were considered and rejected. Making `.value` recurse to the base type trades a uniform "peel one layer" rule for a depth-dependent one and loses access to the intermediate layer. Transparent read-coercion (a value usable as its base without `.value`) is Rust's `Deref`-on-a-newtype, which its own guidelines (C-DEREF) discourage, and it erodes the nominal boundary. Erasing the newtype at runtime (TypeScript branded types, Scala 3 opaque types) removes the wrapper but also removes the two properties Souther sells: the invariant re-checked at the JSON boundary, and runtime discrimination of a union whose variants wrap the same base (`アクティベート済み` and `未アクティベート` would both erase to `String` and `match` could not tell them apart). On the JVM the settled answer keeps an explicit accessor: Kotlin's `@JvmInline value class` and Scala 2's `AnyVal` both expose `.value` and stay nominal everywhere.

## Decision

**Add a constructor-destructuring pattern `X(v)` — the inverse of construction `X(v)` (ADR-0032) — that opens a newtype case in a `match`, nestable as `X(Y(s))` to reach through a newtype over a newtype in one pattern.** `| アクティベート済み(メールアドレス(s)) -> s` binds `s` to the base `String`; the shorter `| アクティベート済み(a) -> ...` binds `a` to the one wrapped layer. `.value` stays as the single-layer accessor for inline reads, positioned as a Kotlin value-class property is — an explicit, nominal-everywhere projection. The pattern is for reaching through several layers; `.value` is for peeling one.

Each name written in the pattern MUST be the newtype opened at its layer. `アクティベート済み(ソース(s))` is a compile error when `アクティベート済み` wraps `メールアドレス`, not `ソース`, and opening a non-newtype with the form is likewise rejected. This matches F# and Elm, where the constructor in the pattern is type-checked.

The pattern is pure sugar. The parser keeps it structural (every token bumped, so the CST stays lossless); the AST builder lowers `X(Y(s))` to the same `LetIn` + `FieldAccess(_, "value")` chain a record destructuring lowers to, so the type checker, the totality check, the inliner, and the backend see only forms that already exist — no new Core node, no new codegen. The only genuinely new check is the layer-name match, carried as a small list on the match case and verified where the case is type-checked.

## Consequences

- The double accessor `.value.value` disappears from the one place it occurred (the `アドレス値` helper in the member example), and the example becomes the first non-primitive newtype in the corpus.
- `.value` is deliberately kept, not removed. Removing it would require transparent read-coercion or runtime erasure, both rejected above; a single-layer `.value` is the JVM norm and reads fine.
- The name check gives an early, precise error for a wrong or non-newtype constructor in a pattern, at the cost of one extra field on the match-case AST node threaded through the passes that rebuild it.
- The form is defined for opening a newtype. Record cases keep `{ }`; the whole value is still bound with `as`.
