# ADR-0029: Platform failures propagate as exceptions; only domain outcomes are cases

Status: Accepted (decided 2026-07-18). Amends ADR-0007; revises `[#java-impl-rules]` (§13.4).

## Context

Souther has no exceptions in the language. Business results are an unmarked sum, and the
off-ramp is decided by composition (ADR-0007): expected failures are *cases*, returned as
values, and `.sou` code can neither `throw` nor `catch` (E1302). This is deliberate — it
keeps failure handling in the type and under exhaustiveness checking.

External-world dependencies are behaviors with no implementation, injected from Java
(ADR-0006). The original rule for those Java implementations (§13.4) said: *return one of the
declared failure cases; do not let DB exceptions or HTTP failures leak to the language side.*
Its worked example, `findMember`, therefore declared a `DB不通` (database-unavailable) case and
folded any `DataAccessException` into it.

That conflates two different kinds of failure:

- **Domain outcomes** — results that are part of the model's meaning: *not found*,
  *insufficient stock*, *the stored value violates a domain invariant*. The business cares
  about these and may want to route each one differently.
- **Platform failures** — the infrastructure is unavailable or misbehaving: the database is
  down, the network is partitioned, a host is unreachable. These are not domain results. They
  are not a decision the model makes; they are an abnormal condition that happened *to* it.

Forcing platform failures into the output sum has real costs. Every DB-touching behavior's
domain type must then carry an infrastructure case (`DB不通`), polluting the signature and the
exhaustiveness check with something that is not a domain outcome. Worse, when the database is
genuinely down there is no honest case value to return — the implementation must either invent
an infrastructure case or lie (fold it into `在庫不足` / "insufficient", which it wasn't).

## Decision

Split the two kinds of failure by channel:

- **Domain outcomes are cases.** Success values and declared failure cases (`会員なし`,
  `在庫不足`, `保存データ不正`, …) are returned as values, exactly as before.
- **Platform failures are exceptions.** The Java binding — the injected implementation of a
  behavior — *may throw* for platform/infrastructure failures. Souther does not fold them into
  cases; it propagates them transparently. The generated `>->` pipeline and `Behavior.apply`
  contain no exception handling, so a thrown exception passes straight through to the boundary.

The language (`.sou`) still has no exceptions — E1302 stands, and it bans exceptions for
*business-flow control*. A propagated platform failure is not business-flow control; it is
abnormal termination, in the same category as invariant-violation aborts (ADR-0003) and the
"VM failures and implementation bugs are out of scope" clause that §13.4 already had. The
boundary (the Java/framework side) catches it and handles it as the cross-cutting concern it
is: an HTTP 503, a retry, a transaction rollback.

The dividing test is: **is this a domain outcome or a platform failure?** A domain outcome is
a meaningful business result (not found, insufficient stock, stored data breaks a domain
rule). A platform failure is infrastructure being unavailable or unresponsive. The former is
a case and shows up in the domain type; the latter is an exception and stays out of the domain
type.

`保存データ不正` (the stored value violates a domain invariant when decoded) stays a domain
case. The infrastructure worked and handed back data; that the data breaks a domain rule is a
statement about the *domain*, and the model may legitimately name it as an outcome. Whether a
given corrupt-data case is worth naming is a modeling choice; classifying it is the author's
call, and this ADR does not force it either way.

### Boundary exception type

"The boundary catches the exception" needs a concrete type, or the pattern is unimplementable.
The type is *stack-specific*, and the binding and the boundary must agree on it:

- **jOOQ + Spring** (the `ordering` and `member` examples): the boundary catches
  `org.springframework.dao.DataAccessException` and maps it to HTTP 503. jOOQ's own
  `org.jooq.exception.DataAccessException` is *not* a subtype of Spring's, so this only works
  when jOOQ is configured with Spring's exception translation — which Spring Boot's jOOQ
  auto-configuration installs by default. A binding wired with a bare `DSLContext` (no
  translation) would throw jOOQ's type and slip past the handler; the wiring must enable
  translation.
- **Raw JDBC** (the spec's worked example): `SQLException` is checked and cannot cross
  `Behavior.apply`. The binding wraps it in an exception from the same
  `org.springframework.dao.DataAccessException` family — e.g.
  `DataAccessResourceFailureException` — so the same boundary handler catches it. It does *not*
  throw `IllegalStateException` or a bare `RuntimeException`: those miss a
  `DataAccessException`-typed handler and misrepresent an infrastructure failure as a
  programming error.

The boundary handler is deliberately narrow: it catches the *platform-failure* type
(`DataAccessException`) and nothing else. An implementation bug that throws, say,
`IllegalStateException` is not a platform failure and correctly falls through to a generic 500,
not a 503.

## Consequences

- Injected behaviors no longer declare a `DB不通`-style case for infrastructure availability.
  The `ordering` example (`注文を記録する >-> 在庫を引き当てる`) demonstrates this: a DB outage
  is thrown by the jOOQ implementation, passes through Souther untouched, is auto-rolled-back
  by the transaction boundary, and mapped to 503 by a boundary exception handler — while the
  domain type stays `注文確定 | 在庫不足`.
- The `findMember` example (spec and the `member` module) drops `DB不通`; a DB-down there is a
  thrown platform failure. `会員なし` and `保存データ不正` remain domain cases.
- Callers of injected behaviors must expect that a platform failure surfaces as an exception at
  the boundary, not as a case, and handle it there.
- The most important reason for writing this ADR: the previous §13.4 text stated the opposite
  ("do not let DB exceptions leak"), which actively misled readers into modeling `DB不通` as a
  domain case. The principle was load-bearing but undocumented; this records it.

## Relationship to other decisions

- **ADR-0007** (unmarked-sum business results): this refines it — the sum carries *domain*
  outcomes only; platform failures are not in the sum.
- **ADR-0006** (outside-world via injected Java): the injected boundary is exactly where a
  platform exception originates and where it is caught again.
- **ADR-0003** (invariant violations abort): a propagated platform exception is abnormal
  termination in the same spirit — not a Souther expression, not catchable in `.sou`, so it
  cannot be used for business-flow control.
- **ADR-0008** (asymmetric Java interop): `.sou` cannot throw or catch, but the Java binding on
  the boundary is ordinary Java and may do both.
