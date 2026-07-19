# ADR-0016: The requirement set is injected constructor arguments, not an effect type

Status: Accepted

## Context

A behavior that touches the outside world declares what it requires (ADR-0006). A natural design would track those requirements as an effect row on the function type, the way effect systems do. This is where Souther diverges from effect-typed languages, and the reason is worth recording so the difference is not mistaken for an oversight.

## Decision

Souther's requirement set is not an effect row. A required behavior's implementation is injected as a constructor argument; once the object is built (`new 記録する(clock)`), the dependency is inside it and the result is an ordinary function. The `requires` names appear as ordinary trailing arguments of the implementing `let`, and `bind` partially applies them — so the behavior's declared type is the post-injection (partially-applied) type, which is exactly why the `let` has `requires`-many more parameters than the behavior.

## Consequences

There is no dynamic scope, so there is no "outside" for a capability to escape to. A function that receives a closed value need not know what that value required; the requirement is paid by the side that wrote the name, and that is statically known. In the `let` body, an injected name like `現在時刻` is just an argument — not an effect, not a special type — so there is no place to write `requires` on the `let` itself, and helper `let`s that take a function argument fit the same shape without any propagation.

Requirement sets contain no variables, only concrete sets. That is what makes reconciling declaration against implementation, and computing the union-over-stages of `>->` (ADR-0017), decidable at all. Because a value carrying its dependency is closed when passed, a higher-order `let` such as `map(xs, p)` has an empty requirement set regardless of what `p` required — `p` arrives closed.

Effekt states this directly. It reads effect types as a requirement on the call site — an additional *input* to the function (the contextual reading, the same reading as Souther's `requires`) — and gives the semantics by translating to a calculus that passes capabilities as extra arguments, whose type system has no effect types (Brachthäuser, Schuster, Ostermann, *Effects as Capabilities*, OOPSLA 2020). The difference is only that Souther surfaces that translation instead of keeping it inside the compiler; it can, because it has no effect variables to hide.

Consequently, Koka's reason for rejecting a union-based design — `µ1 ∪ µ2 ∼ µ3 ∪ µ4` is not uniquely solvable (Leijen, *Koka: Programming with Row Polymorphic Effect Types*, MSFP 2014) — does not reach Souther, because that concerns effect *variables*, which Souther never has. In Koka and Unison an effect fires when a function is called and is caught dynamically by an outer handler, so `map` calling `f` performs `f`'s effect and must record it in the type (`map : (list<a>, a -> µ b) -> µ list<b>`); Souther's requirements close at injection, so nothing propagates. Effekt makes blocks second-class for the same reason Souther does not need to — a capability tied to a handler's dynamic extent floats free when it escapes.

## References

- Specification: `[#blocks]`, `[#requires]`, `[#requirement-propagation]`, `[#core-semantics]`
- Brachthäuser, Schuster, Ostermann, *Effects as Capabilities*, OOPSLA 2020
- Leijen, *Koka: Programming with Row Polymorphic Effect Types*, MSFP 2014
- Unison (effect rows / abilities)
