# ADR-0021: Compiler layers — a Core IR between type-checking and code generation

Status: Accepted; implemented (supersedes the MVP "no separate IR" stance recorded under History)

Implemented: the **Lower** stage (`check/Lower.java`) inlines each behavior body once —
both the type checker's body check and the backend consume it, so the backend no longer
inlines — and desugars list comprehensions. A structural **Core IR** (`core/Core.java`)
carries the lowered behavior-body language, with `fold` made an explicit node; the backend
emits every expression from Core (`genExpr`), and the AST `expr` is a thin `Core.of`
adapter kept for the codec paths, which are still AST-level.

Core is **structural, not typed**: the backend infers types as it emits. One consequence
is that the closure path (a runtime-selected function) asks the type checker — which works
on the AST — whether a `let` value is such a function and for its parameter types, reaching
it through `Core.toAst()`. This is a genuine dependency of the untyped design, not an
unfinished migration: current generics are monomorphized by inline expansion (no typed IR
needed), and the JVM erases generics and boxes collection elements regardless, so a typed
Core buys little for them. A typed Core IR is deferred until a real need appears — recursive
generics lowered to shared methods, or an optimization that needs types on nodes.

The normative specification (`[#compiler-pipeline]`, `[#ast-tracking]`) still holds: Core
is a structural lowering of behavior bodies with no type reification, so "no separate IR" in
the sense of a typed/reified representation remains accurate; the pipeline text is revised
when a typed Core lands, not ahead of it.

## Context

The MVP compiler carried no intermediate representation. Type checking ran on the
AST and the ClassFile backend emitted `.class` directly from that AST. The stated
reason was to keep less machinery in step with a language that was still moving.
That decision is preserved under History below.

Souther is past the MVP. It is being taken toward a practical language, and for a
practical language the runtime speed of the code it emits is what earns adoption.
The "no IR" stance has three costs that now bite:

- **Rewrites are scattered.** Compile-time transformations live wherever they were
  convenient: some as standalone AST rewrites before checking (`Exposing`, invariant
  inlining, `NewtypeDesugar`), some interleaved into type checking (helper inlining,
  branch widening, pipeline flattening), and some hand-written inside the emitter
  (`fold` to a counted loop, `match` to an `instanceof` chain, closure conversion,
  intrinsic dispatch). There is no single place a transformation belongs.
- **Some work is done twice.** Helper inlining is reconstructed independently in both
  the type checker and the backend from the same engine, because there is no shared
  lowered form to compute once and reuse.
- **There is no home for what comes next.** Monomorphizing generic stdlib helpers
  (ADR-0010) needs a lowering target, and it has nowhere to go on the surface AST.

## Decision

The compiler is defined as an explicit layered pipeline with a **Core IR** for
behavior bodies. (As implemented the Core IR is structural, not typed; a typed Core IR
is deferred — see the implementation note above.)

1. **Parse** — source to surface AST. Only concrete-syntax desugars that need no types.
2. **Resolve** — import/`exposing` name resolution (today `Exposing`).
3. **Derive** — fill decoders/encoders into the AST from each data's shape (today
   `Deriver`). Stays at AST level.
4. **Lower** — the surface AST to Core IR. The one place every body-level transformation
   happens, once: helper inlining, the remaining desugars, `fold`-to-loop shaping,
   `match` lowering, closure conversion, intrinsic lowering, and — when it lands —
   monomorphization of generic helpers. It precedes the body check because a behavior's
   permission and `requires` are defined on the inlined body (`[#blocks]`), so the check is
   defined on the lowered form.
5. **TypeCheck** — type, requirement, and construction-permission checking. It consumes
   the lowered bodies for the body check and does not rewrite. Surface checks (data,
   signatures, function arguments) read the original AST, which still carries the
   un-inlined helper calls they inspect.
6. **Optimize** — small passes over Core, kept deliberately thin: unbox where the type
   is statically known (generalizing the `fold` accumulator), hoist constant `Decimal`
   values, fold obviously-constant subtrees. Nothing that duplicates what the JVM JIT
   already does well.
7. **Codegen** — Core to ClassFile bytecode. The backend only emits; it never rewrites.

**Scope.** Core covers the behavior/`let` body language — the expressions that become
bytecode. Data definitions and derived codecs stay at AST level for now and may move to
Core later, when a codec-level transformation needs them.

**Invariants.** (1) All body-level lowering happens once, in Lower. (2) The backend
emits from Core and never rewrites during emission. (3) The performance stance is to
emit JIT-friendly bytecode — typed locals, minimal boxing, monomorphic call sites — and
to lean on the JVM JIT for classical optimization. Optimize stays small on purpose; its
job is to remove JIT-hostile patterns, not to be a middle-end optimizer.

## Consequences

- Transformations that are scattered across parse, type checking, and emission collapse
  into Lower. The emitter shrinks to straight emission from Core.
- Helper inlining is computed once in Lower instead of twice.
- Monomorphization has a defined place (a Lower pass over Core). Generics work builds on
  this layer rather than bolting onto the surface AST.
- The cost is a second representation to keep in step with the language. It is bounded:
  Core is only the expression language, not data or codecs, and the surface AST remains
  the single source for those.
- CTFE constant-construction verification (ADR-0032) runs on Core / after codegen as it
  does today; it is unaffected in kind.
- Migration is incremental. The pipeline can be introduced and transformations moved
  into Lower one at a time; the language surface and generated behavior do not change.

## History

The original decision (MVP), superseded by the above:

> Souther keeps no independent Domain IR / Decoder IR / Encoder IR. Type checking runs
> on the AST, and the ClassFile backend emits `.class` directly from the AST via
> `java.lang.classfile`, not through javac. Derived decoders/encoders are filled into
> the AST from the data shape and generated as Raoh combinator calls directly as
> bytecode. Room is left to insert an IR at this stage if a future backend needs it. The
> MVP does not carry one, so there is less machinery to keep in step with the language.

## References

- Specification: `[#codec-generation]`, `[#compiler-pipeline]`, `[#ast-tracking]`
- ADR-0010: polymorphism limited to the stdlib (the generics/monomorphization this layer hosts)
- ADR-0025: first-class functions (closure conversion is a Lower pass)
- ADR-0028: stdlib as a privileged core over an intrinsic kernel (intrinsic lowering is a Lower pass)
- ADR-0032: newtype constructor (CTFE constant verification)
