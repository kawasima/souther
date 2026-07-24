# ADR-0056: Injected behaviors are multi-argument; injection is orthogonal to composition

Status: Accepted.

## Context

An injected behavior — a `behavior` with no `let`, implemented from Java — was limited to a single input. Declaring two or more parameters was silently accepted and every parameter after the first was dropped: the natural call `send(a, b)` failed with an unexplained arity error, while `send(a)` on a two-input spec compiled and quietly discarded the second input. This is a correctness hole and it is inconsistent with a `let`-implemented (fn) behavior, which already takes several arguments (`会員へ通知する : (会員, 件名) -> …`).

The single-input assumption was baked into three layers: the checker's `ReqSig` held one `param` and the call-check hard-coded arity 1; the injected base class always `implements Behavior` (whose `apply` is unary); and every required-call site emitted a unary `Behavior.apply(Object)` with at most one argument.

The unary `Behavior<I, O>` is deliberate — it is the composition contract for `>->`, where a pipeline stage takes one value and yields one. The mistake was treating "injected behavior" as a synonym for "unary `Behavior<I, O>`". A fn behavior already avoids that: its codegen branches on parameter count — one input `extends Behavior<In, Out>` (composes with `>->`), two or more is a standalone functional interface with a typed `apply(A, B, …)` (does not compose).

Separately, a collection parameter of any multi-argument `apply` degraded to `Object` (and a single-input behavior with a collection input/output lost its whole `Behavior<In, Out>` signature), so a Java/Kotlin/Scala author received a raw `Object` to cast rather than a typed `List<明細>`.

## Decision

**Treat "injected-or-not" and "unary-or-multi" as orthogonal, reusing the fn-behavior template.** An injected behavior with 0 or 1 input is unchanged — a `Behavior<In, Out>` that composes with `>->`. With 2+ inputs it is a standalone abstract class with a typed `apply(A, B, …)` and no `Behavior` supertype (it takes one argument, so it is inherently not a `>->` stage — the existing "only a single-input behavior composes" rule covers it, no new rule needed). The protected `constructs` factories and the protected constructor are unchanged. This *removes* the injected special case rather than adding one; `Behavior<I, O>` stays the unary composition contract.

Because a multi-argument base has no shared `Behavior` interface, a required multi-argument behavior is stored and injected by its own base class and called with `invokevirtual <base>.apply(A, B, …)`, mirroring how a multi-argument fn behavior is already stored and called. A required single/zero-argument behavior stays a `Behavior` field called with `invokeinterface`. The `bind(...)` factory's external signature is unchanged (it already takes the named base type); only the `$Impl` field, constructor parameter, and call opcode become behavior-specific.

**Collection parameters keep their runtime interface type.** A `List`/`Map`/`Set` parameter or return is the raw `java.util` interface with a generic `Signature` carrying the element type (an `Option` is the runtime `net.unit8.souther.runtime.Option`), the same convention a collection-typed data field already uses — not `Object`. This applies to both the multi-argument typed `apply` and the single-input `Behavior<In, Out>` signature.

## Consequences

- `通知メールを送る : (宛先: アクティベート済み, 件名: String) -> 送信済み` is written directly, and the Java implementation overrides `apply(アクティベート済み, String)`. The record wrapper (`data 通知 = { 宛先, 件名 }`) that only existed to work around the single-input limit is removed from the member example.
- The base is an ordinary JVM abstract class, so Kotlin/Scala/Clojure implement it. A collection parameter arrives typed, which a Clojure persistent collection (a `java.util` collection) satisfies directly. Clojure uses `proxy`/`gen-class` and reaches the `protected` factories via `gen-class`'s `:exposes-methods`; the factories stay `protected` because that is the construction confinement (ADR-0015), not an interop knob.
- Example evaluation (`fake`) supports a multi-argument injected dependency: its base is an abstract class (not the unary `Behavior` a JDK `Proxy` needs), so the fake is a runtime-generated subclass whose typed `apply` looks the input tuple up in the table.
- The compiler's unary-vs-multi dispatch is a single decision (`CodegenContext`) shared by the base class, the `$Impl` field/constructor, the `bind` factory, and every call site, so they cannot drift; the example verifier reads it back from the fake's runtime type.
