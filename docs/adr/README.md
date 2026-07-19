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
| [0001](0001-one-to-one-with-spec-dsl.md) | The implementation model maps one-to-one onto the spec DSL | `[#purpose]`, `[#design-principles]`, `[#behavior]`, `[#non-functional]`, `[#core-semantics]` |
| [0002](0002-closed-construction-paths.md) | Close data construction paths; permission lives on the behavior | `[#closed-construction]`, `[#constructs]`, `[#java-base-class]` |
| [0003](0003-invariant-violations-abort-in-domain.md) | Invariant violations abort; preconditions are business cases, not contracts | `[#invariant-on-construct]`, `[#algebraic-types]`, `[#violation-destination]`, `[#out-of-scope]`, `[#stdlib-int]`, `[#jvm-abort]` |
| [0004](0004-derive-codecs-delegate-parsing-to-raoh.md) | Derive decoders/encoders from data shape; delegate external representation to Raoh | `[#separate-representation]`, `[#external-representation]`, `[#decoder]`, `[#encoder]` |
| [0005](0005-behavior-as-membership-free-composition-unit.md) | behavior is a membership-free composition unit; spec and implementation are separate | `[#behavior-composition-unit]`, `[#behavior]` |
| [0006](0006-outside-world-via-missing-implementation.md) | Outside-world dependencies are behaviors with no implementation, injected from Java | `[#no-impl-for-outside]`, `[#injected-behavior]` |
| [0007](0007-unmarked-sum-output-and-railway.md) | Business results are an unmarked sum; the off-ramp is decided by composition | `[#unmarked-sum]`, `[#unmarked-output]`, `[#composition]` |
| [0008](0008-asymmetric-java-interop.md) | Java interop is asymmetric | `[#asymmetric-interop]`, `[#out-of-scope]` |
| [0009](0009-decimal-ignores-scale.md) | Decimal does not include scale in identity | `[#primitives]` |
| [0010](0010-polymorphism-limited-to-stdlib.md) | Polymorphism is limited to stdlib types; no user-defined generics | `[#algebraic-types]` |
| [0011](0011-option-never-unit-not-surface-types.md) | Option, Never, and Unit are not surface-writable types | `[#algebraic-types]`, `[#optional]` |
| [0012](0012-nominal-include-no-intersection-types.md) | Field composition is nominal `include`; no structural intersection types | `[#field-spread]`, `[#union-intersection]` |
| [0013](0013-sum-cases-are-named-data-references.md) | Sum-data cases are references to already-declared named data | `[#sum-data]` |
| [0014](0014-explicit-newtype-syntax.md) | newtype is declared explicitly by `data X = Y`, not inferred from shape | `[#newtype]` |
| [0015](0015-cross-module-field-read-construction-closed.md) | Field reads may cross module boundaries; only construction is closed | `[#field-visibility]`, `[#jvm-product]` |
| [0016](0016-requirements-as-arguments-not-effects.md) | The requirement set is injected constructor arguments, not an effect type | `[#blocks]`, `[#requires]`, `[#requirement-propagation]`, `[#core-semantics]` |
| [0017](0017-declare-dont-infer-permissions-and-requirements.md) | Declare, don't infer: requirements and composed output; construction permission is optional | `[#constructs]`, `[#requires]`, `[#declared-composition-output]` |
| [0018](0018-no-dedicated-update-syntax.md) | No dedicated update syntax; use a named record literal with spread | `[#record-literal]` |
| [0019](0019-one-arrow-for-types-and-terms.md) | One arrow `->` for types and terms (lambdas, match); `=` binds names | `[#delimiters]`, `[#fn-declaration]`, `[#match]` |
| [0020](0020-require-desugars-to-if.md) | `require ... else` is sugar for `if` | `[#require]` |
| [0021](0021-compiler-layers-core-ir.md) | Compiler layers — a Core IR between type-checking and code generation | `[#compiler-pipeline]`, `[#ast-tracking]` |
| [0022](0022-pin-classfile-version-to-java-21.md) | Pin the generated class-file version to Java 21 | `[#target-jdk]` |
| [0023](0023-capitalize-behavior-class-names.md) | Capitalize generated behavior class names; collisions with data are errors | `[#jvm-behavior]` |
| [0024](0024-exposed-composition-output-in-exposing.md) | An exposed composition declares its output signature in the exposing list | `[#modules]`, `[#declared-composition-output]`, `[#jvm-anonymous-union]` |
| [0025](0025-first-class-functions.md) | First-class functions: inline when they can't escape, close over the runtime `Fn` when they must | `[#blocks]`, `[#fn-declaration]`, `[#stdlib-list]` |
| [0026](0026-signatures-colon-definitions-equals.md) | Signatures use `:`, definitions use `=`; a function is defined with `let` | `[#delimiters]`, `[#behavior]`, `[#fn]` |
| [0027](0027-match-with-pipe-cases.md) | match reads `match e with \| case -> ...` (F# form) | `[#match]` |
| [0028](0028-stdlib-privileged-core-over-intrinsic-kernel.md) | The stdlib is a privileged core written in Souther over an intrinsic kernel | `[#stdlib]`, `[#primitives]` |
| [0029](0029-platform-failures-are-exceptions-not-cases.md) | Platform failures propagate as exceptions; only domain outcomes are cases | `[#java-impl-rules]`, `[#out-of-scope]` |
| [0030](0030-spread-not-include-keyword.md) | Record composition and update use a `...` spread, not an `include` keyword | `[#product-data]`, `[#field-spread]`, `[#record-literal]` |
| [0031](0031-invariant-follows-the-type-body.md) | A data's `invariant` clause follows the type body, not the record's braces | `[#invariant]`, `[#product-data]` |
| [0032](0032-newtype-constructor-parenthesized.md) | A newtype is constructed by applying its name, parenthesized: `金額(500)` | `[#newtype]`, `[#violation-destination]` |
| [0033](0033-numeric-literals-and-arithmetic-operators.md) | Decimal literals carry an `m` suffix; `+ - * /` work on Int and Decimal | `[#primitives]`, `[#stdlib-int]`, `[#stdlib-decimal]` |
| [0034](0034-value-pipe-and-argument-order.md) | The value pipe `\|>` threads a value through calls; the stdlib takes its main object last | `[#pipe]`, `[#stdlib]`, `[#delimiters]` |
| [0035](0035-match-destructures-fields-as-binds-the-whole.md) | A match case destructures a case's fields; `as` binds the whole; Option binds positionally (`\| Some v`) | `[#match]`, `[#algebraic-types]` |
| [0036](0036-tuples-and-exposing-parentheses.md) | Tuples `(a, b)` are expression-level first-class values; a module's `exposing`/`import` lists use parentheses | `[#tuple]`, `[#modules]`, `[#delimiters]` |
| [0037](0037-tuple-types-in-signatures.md) | Tuple types are writable in helper/stdlib signatures, not at a codec boundary | `[#tuple]`, `[#stdlib-map]` |
| [0038](0038-user-recursion-over-self-referential-data.md) | User helpers may recurse over self-referential data; a recursive helper is lowered to a method | `[#fn-declaration]`, `[#algebraic-types]` |
| [0039](0039-set-collection-type.md) | Set is a collection type; its external representation is a deduplicated array | `[#collections]`, `[#stdlib-set]` |
| [0040](0040-typed-map-keys.md) | A Map key may be a String-backed newtype, not only String | `[#collections]`, `[#stdlib-map]` |
