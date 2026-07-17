# Architecture Decision Records

This directory records the decisions behind Souther's design — the reasoning, the
alternatives weighed, and the prior art consulted. The language specification
([`../../specification.adoc`](../../specification.adoc)) states *what* the language does;
these ADRs state *why*.

Referencing is one-directional: each ADR points back to the spec sections whose
rationale it holds, but the specification itself does not link to ADRs — it states
the rules and examples, nothing more.

Format: [Nygard-style](https://cognitect.com/blog/2011/11/15/documenting-architecture-decisions)
(Status / Context / Decision / Consequences). ADRs are written in English; the
specification is in Japanese.

| ADR | Decision | Spec |
| --- | --- | --- |
| [0001](0001-one-to-one-with-spec-dsl.md) | The implementation model maps one-to-one onto the spec DSL | §1, §2, §12, §28, §29 |
| [0002](0002-closed-construction-paths.md) | Close data construction paths; permission lives on the behavior | §2.1, §12.3, §13.3 |
| [0003](0003-invariant-violations-abort-in-domain.md) | Invariant violations abort inside the domain; the boundary returns a Result | §2.2, §7.3, §9.4, §3, §18.2, §19.7 |
| [0004](0004-derive-codecs-delegate-parsing-to-raoh.md) | Derive decoders/encoders from data shape; delegate external representation to Raoh | §2.3, §6, §10, §11 |
| [0005](0005-behavior-as-membership-free-composition-unit.md) | behavior is a membership-free composition unit; spec and implementation are separate | §2.4, §12 |
| [0006](0006-outside-world-via-missing-implementation.md) | Outside-world dependencies are behaviors with no implementation, injected from Java | §2.5, §13.2 |
| [0007](0007-unmarked-sum-output-and-railway.md) | Business results are an unmarked sum; the off-ramp is decided by composition | §2.6, §12.2, §14 |
| [0008](0008-asymmetric-java-interop.md) | Java interop is asymmetric | §2.7, §3 |
| [0009](0009-decimal-ignores-scale.md) | Decimal does not include scale in identity | §7.1 |
| [0010](0010-polymorphism-limited-to-stdlib.md) | Polymorphism is limited to stdlib types; no user-defined generics | §7.3 |
| [0011](0011-option-never-unit-not-surface-types.md) | Option, Never, and Unit are not surface-writable types | §7.3, §7.4 |
| [0012](0012-nominal-include-no-intersection-types.md) | Field composition is nominal `include`; no structural intersection types | §8.2, §8.6 |
| [0013](0013-sum-arms-are-named-data-references.md) | Sum-data arms are references to already-declared named data | §8.3 |
| [0014](0014-explicit-newtype-syntax.md) | newtype is declared explicitly by `data X = Y`, not inferred from shape | §8.7 |
| [0015](0015-cross-module-field-read-construction-closed.md) | Field reads may cross module boundaries; only construction is closed | §8.5, §19.2 |
| [0016](0016-requirements-as-arguments-not-effects.md) | The requirement set is injected constructor arguments, not an effect type | §12.5, §12.6, §13.6, §29 |
| [0017](0017-declare-dont-infer-permissions-and-requirements.md) | Declare, don't infer: requirements and composed output; construction permission is optional | §12.3, §12.6, §14.5 |
| [0018](0018-no-dedicated-update-syntax.md) | No dedicated update syntax; use a named record literal with spread | §12.4 |
| [0019](0019-sml-style-named-vs-anonymous-binding.md) | `=` binds named definitions, `=>` introduces anonymous blocks (SML-style) | §5, §13.1 |
| [0020](0020-require-desugars-to-if.md) | `require ... else` is sugar for `if` | §16.4 |
| [0021](0021-no-separate-ir-direct-bytecode.md) | No separate IR; the backend emits bytecode directly from the AST | §20, §21 |
| [0022](0022-pin-classfile-version-to-java-21.md) | Pin the generated class-file version to Java 21 | §19.1 |
| [0023](0023-capitalize-behavior-class-names.md) | Capitalize generated behavior class names; collisions with data are errors | §19.5 |
| [0024](0024-exposed-composition-output-in-exposing.md) | An exposed composition declares its output signature in the exposing list | §4, §14.5, §19.8 |
