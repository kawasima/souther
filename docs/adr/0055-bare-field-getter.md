# ADR-0055: A bare `.field` is a getter block

Status: Accepted.

## Context

To project a field, Souther always required a lambda: `List.map(x -> x.value, xs)`. There is no point-free way to pass "the field accessor," so the value pipe reads `xs |> List.map(x -> x.value)` where a reader expects `xs |> List.map(.value)`. The recurring case is `.value` on a newtype (ADR-0014), which after the newtype work is the single-layer accessor and shows up wherever a wrapped value is mapped over a list.

Several languages give a point-free field projection, but by different mechanisms. Elm has a first-class getter function `.field : record -> field`. Scala writes `_.field`, sugar for `x => x.field`. Swift key paths `\.name` and Kotlin `Person::name` are first-class callables; Clojure `(:k m)` makes the keyword itself a function of a map. F# and OCaml have none — the idiom is `fun x -> x.field`.

Souther's functions are not first-class everywhere (ADR-0025): a non-escaping block is inlined, and only an escaping one becomes a runtime `Fn`; a function cannot be stored in a data field or cross a codec boundary. A getter must fit that model rather than introduce a new first-class value.

## Decision

**A bare field access `.field` in an expression is sugar for the getter `(x) -> x.field`, lowered at parse time to an ordinary `Ast.Block`.** So `List.map(.value, xs)` and `xs |> List.map(.value)` project the field with no lambda written. The getter is single-layer (`.value`, not `.a.b`; a newtype over a newtype is opened with the destructuring pattern of ADR-0054) and works for any field, so a newtype's `.value` and a record's `.a` read the same way.

The notation is Elm's `.field`; the meaning is Scala's `_.field` — a lambda shorthand, **not** a first-class getter value. Because it desugars to a second-class block, it may sit in an argument position or be `let`-bound and applied, but returning or storing it hits the existing `block is not a value` error, exactly like any block. No new first-class function value is introduced.

The lowering is confined to the frontend: the parser adds one `FIELD_GETTER` node for a leading `.` before an identifier (a leading `.` was otherwise always a syntax error, and `.5` lexes as a decimal, so it is unambiguous), and the AST builder turns it into `Block([$g], FieldAccess(Var($g), field))` with a synthesized `$`-prefixed parameter. The type checker, the inliner, and the backend see an ordinary single-parameter block over a field access — no new Core node, no new codegen.

## Consequences

- `.value` on a newtype composes with the pipe point-free, the case the feature is for. The existing corpus has few sites to rewrite (two combinator lambdas), so the value is in future writing, not cleanup.
- A getter never collides with the bare `value` an invariant refers to (ADR-0031): inside an invariant `value` is a field name in scope, while `.value` is a getter in argument position. Existing invariant syntax is unchanged.
- Keeping the getter second-class (a block, not an Elm-style first-class value) preserves the ADR-0025 constraint that functions do not cross data fields or codec boundaries; the getter has no more reach than a hand-written lambda.
- The single-layer limit keeps the getter simple; reaching through nested newtypes stays with the ADR-0054 destructuring pattern, so there is one way to do each.
