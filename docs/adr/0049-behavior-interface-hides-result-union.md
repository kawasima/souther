# ADR-0049: A behavior is a public interface that hides the generated result union; the result suffix is `Result`

Status: Accepted (decided 2026-07-21). Revises `[#jvm-behavior]` (`[#jvm-behavior]`) and `[#jvm-anonymous-union]` (`[#jvm-anonymous-union]`).

## Context

A behavior whose output is an anonymous union (`-> A | B`) has no anonymous sum type on the
JVM to land in. To let a Java caller receive the result as one value and check it exhaustively
with a `switch` that has no `default`, the backend synthesises a sealed interface the leaf
cases implement — the `<Behavior名>結果` type (ADR-0008, `[#jvm-anonymous-union]`). The model
author never writes that name.

The name still leaked into Java code, in two places:

- **The field/bean type.** A fn or `>->` behavior was generated as a concrete `final class`
  that implemented `Behavior<In, Out>`. That class defined its own erased `apply(Object)Object`,
  so a reference **typed as the behavior class** got `Object` back from `apply`, not the union —
  the covariant return from the interface was shadowed by the class's own method. To get a typed
  result the caller had to type the field `Behavior<Cart, Quote結果>`, spelling the synthetic
  name. (An injected behavior's abstract base carried the generic signature and no `apply`, so
  *its* name already gave a typed `apply` — which is why injected fields could be declared by the
  behavior name alone. The concrete let/pipe class was the odd one out.)
- **A multi-input behavior** did not implement `Behavior` at all (that interface takes one
  argument), so it was a bare class with `apply(Object, Object)Object`. A caller cast the
  `Object` result to `Quote結果` — the synthetic name again.

Separately, the `結果` suffix is a Japanese word inside otherwise-Latin generated Java identifiers.

## Decision

**Generate each fn/`>->` behavior as a public interface named after the behavior, with the
implementation in a `<Behavior名>$Impl` class.** Java declares the behavior by the interface name
and never names the implementation.

- A **single-input** behavior's interface `extends Behavior<In, Out>`. Because the interface
  carries the generic signature and defines no `apply` body, a reference typed as the interface
  gets the typed result from `apply` by inheritance (the same mechanism that already worked for
  an injected abstract base). It composes with `>->` unchanged.
- A **multi-input** behavior's interface is a standalone functional interface declaring the
  typed, multi-argument `apply(<refs...>): <Out>` directly. Its `$Impl` keeps the erased
  `apply(Object...)Object` the pipeline calls internally and adds a covariant bridge that
  satisfies the typed method.
- The interface carries the static factory: `of()` for an empty requirement set, `bind(...)`
  for one that injects dependencies. Both return the interface and build the `$Impl`.

A caller writes `private final Quote quote;` (not `Behavior<Cart, Quote結果>`), calls
`quote.apply(cart)`, and matches the cases with `switch` — the result-union name appears nowhere
in the caller's source.

**Rename the synthetic result union `<Behavior名>結果` to `<Behavior名>Result`.**

An **injected behavior** (a `behavior` with no `let`) stays an abstract class the Java
implementation extends (`[#java-base-class]`), unchanged apart from the rename: it needs a class
so the implementation can inherit the `protected` case factories, which an interface cannot
provide.

## Consequences

- The synthetic result-union name no longer appears in any field, bean, or parameter type in
  hand-written Java. It remains only as the `apply` return type an **injected** behavior's Java
  implementation must write, because an override's return type is spelled explicitly and the
  union is anonymous. Removing that last occurrence would require naming the union as a `data`
  sum in the model (the named-sum path of `[#jvm-anonymous-union]`), which is out of scope here.
- A behavior name now denotes a type in two shapes — an interface (fn/`>->`) or an abstract
  class (injected). Both are declared by the behavior name; the difference is invisible at the
  use site.
- A pipeline instantiates a stage's `$Impl`, not the interface. `of()`/`bind()` move from the
  concrete class to the interface, so `new Quote()` becomes `Quote.of()`; `Place.bind(...)` is
  unchanged in spelling.
