# Souther

Souther is a small JVM language for **specification model driven development (SMDD)**. Its job
is to transcribe a *specification DSL* into an *executable implementation model* without a
translation step in between.

The specification DSL describes business rules with just `data` (AND / OR / List / `?`) and
`behavior` (`->` / `>>`). It deliberately leaves three things in comments so a human or an LLM
can still read the whole model in one sitting:

| The spec DSL leaves this in a comment | Souther makes it executable with |
| --- | --- |
| value constraints (`// 0 or greater`, `// approver is the manager`) | `invariant` |
| "this value has been validated" (parse-don't-validate) | closed construction paths (derived `decoder` / `constructs`) |
| `// depends on: catalog`, `// side effect: send mail` | `required behavior` (implementation injected from Java) |

The structure of the spec (distinction, requiredness, multiplicity, state transitions,
totality) maps straight onto Souther's types. The value constraints and the outside-world
dependencies get promoted from comments to types and injection. Nothing else is added.

The full language specification lives in [`specification.md`](specification.md) (written in
Japanese, since the source spec DSL is Japanese and user-defined identifiers may be Unicode).

## A taste

```text
module example.businesstrip exposing {
    出張申請,
    出張申請.decoder,
    事前承認する
}

// A value type: the spec DSL's "// not empty" comment becomes an invariant.
data 従業員ID = {
    value: String
    invariant length(value) > 0
}

data 金額 = {
    value: Int
    invariant value >= 0
}

// A state machine as a sum of named states; the common fields are flattened with `include`.
data 出張申請 = 申請準備中 | 提出済み | 事前承認待ち | 事前承認済み
data 申請準備中 = { include 出張申請共通項目 }
data 提出済み = { include 出張申請共通項目  提出日時: DateTime }

// "承認権限なし" is just a data — not an error type, not a Result.
data 承認権限なし

required behavior 現在時刻 = () -> DateTime   // a dependency: the clock is injected

// The output is an unmarked sum. Whether an arm is a "failure" is decided by composition,
// not by this behavior. The requirement set ({現在時刻}) is inferred, never declared.
behavior 事前承認する = (
    申請:    事前承認待ち,
    承認者ID: 従業員ID
) -> 事前承認済み | 承認権限なし
    constructs 事前承認済み
{
    require 承認者ID == 申請.申請者.上長ID
        else 承認権限なし

    事前承認済み { ..申請, 事前承認日時: 現在時刻(), 事前承認者: 承認者ID }
}
```

The generated classes are consumed from Java; a full boundary pipeline reads

```text
behavior handle = 会員.decoder >> findMember >> toResponse >> 会員応答.encoder
```

which is `Raw -> decode -> behavior -> encode -> Raw`, expressible entirely in the language.

## Core ideas

**Closed construction paths.** A value of `data T` can only be produced by `T`'s derived
decoder, a behavior that declares `constructs T`, or compiler-generated code. Writing `T` as a
return type is not enough. This is how parse-don't-validate ("a validated value is validated by
its type") survives contact with an implementation. Constructors are non-public, so the rule
holds even across the Java boundary.

**Invariants checked at every construction path.** An `invariant` on a `data` runs wherever
that data is built — decoders and behaviors alike, including invariants inherited through
`include`. A behavior that constructs an invariant-bearing data must have an arm for the
violation to go to, or it is a compile error.

**Business results are unmarked sums.** There is no `Result`, no `Either`, no success/error
tag. A behavior maps its input to one of several possible domain results. Which arm counts as
"off the happy path" is decided *at composition time*: `f >> g` routes the arms of `f`'s output
that `g`'s input accepts into `g`, and lets the rest flow straight through to the output. That
is Railway-oriented programming without privileging "failure" — an arm that isn't consumed
simply propagates.

**Outside-world dependencies are `required behavior`s, and their set is inferred.** DB, HTTP,
files, the clock, ID generation — none are implemented in the language; you declare the type and
inject a Java implementation. Conceptually a behavior has the type
`Behavior<Requirements, Input, Output>`, where `Requirements` (the set of required behaviors it
needs) is inferred from the body and unioned through `>>`. You never write it. Binding the
implementations produces a callable behavior:

```java
var handle = Handle.bind(new JdbcFindMember(dataSource));
var result = handle.apply(rawInput);
```

**Boundary codecs are derived, not written.** Decoders and encoders never appear in the
language syntax; they are derived from the shape of the data (JSON key = field name, a
single-primitive-field data is a bare newtype, a sum discriminates on `"type"` with the arm name
as the tag). When the default derivation isn't enough (renamed keys, normalization, a
business-specific discriminator), you write a custom codec on the Java boundary that calls the
data's invariant-checking construction.

**Modules are explicit.** One module per file; a module lists its public surface with
`exposing { ... }` and pulls specific names with `import <module> { ... }`. There are no
wildcard imports, cyclic imports are a compile error, and anything not exposed is emitted as a
package-private class — the boundary is enforced at the JVM level, not just at import
resolution.

## Java interop is asymmetric

```text
Java    -> Souther's generated output      allowed
Souther -> arbitrary Java API              forbidden (the outside world is reached only through required behavior)
```

Java calls the generated types and behaviors. Souther cannot call arbitrary Java. A
`required behavior` is generated as an abstract base class that a Java implementation `extends`;
the implementation may build only the arms that behavior declares, through `protected`
factories, so closed construction is preserved across the boundary.

## What Souther deliberately does not have

Souther is opinionated. The following are left out *by design*, not by omission — each would
undercut one of the ideas above.

- **No exceptions, no `null`, no mutable state, no threads, no async.** Failure is a domain arm
  in the output sum; absence is an optional field (`?`); everything is immutable. `null` and
  `throw` are compile errors that point you at the alternative.
- **No arbitrary JVM calls from Souther.** The only door to the outside world is a
  `required behavior`. This keeps pure behaviors provably pure and keeps effects at the edges.
- **No structural intersection types (`A & B`).** Souther is nominal so that construction paths
  stay closed: an intersection value has no clear constructor and no clear invariant to check.
  The spec DSL's `AND` means "has all of A's fields, plus more", which is nominal field
  composition — `include` — not "is both A and B".
- **No `Result` / `Either`, no dedicated success/failure slot.** Outputs are unmarked domain
  sums; the happy-path/off-path split is a property of a *composition*, not of a value.
- **No type classes / traits, higher-kinded types, dependent types, SMT or termination proofs.**
  Souther tracks two things about data (which values are it, which expressions may construct it)
  and three about a behavior (input, output sum, required-behavior set). That is the whole type
  system.
- **No separate Decoder IR / Domain IR / "Raoh backend".** The ClassFile backend emits
  decode/encode bytecode directly against a small runtime facade; the Raoh dependency is
  isolated inside that facade for primitive parsing and never leaks into the language or the
  generated surface.
- **`>>` composes single-input stages only.** A pipeline threads one value from stage to stage;
  a multi-argument behavior is applied by call / `match`, not placed in a pipeline.
- **`Never` and `Unit` are reading-level concepts, not surface-writable type names.** A unit is
  always a field-less `data`.
- **No package manager, no REPL, no bespoke VM.** The JVM is the distribution substrate; the
  Class-File API (`java.lang.classfile`) generates `.class` files directly, without going
  through `javac`.

### Deferred (not part of the MVP yet)

These are not rejected on principle — they are simply out of scope for now:
user-defined higher-order behaviors (hence no lambdas, hence no `List.map`/`filter`/`fold`),
a human-readable Java-source backend, incremental compilation, an LSP / IDE plugin, static
invariant proofs, hand-written codec syntax, and `Decimal` division with an explicit rounding
mode.

## Building and using

Requirements: JDK 25 (the compiler uses the Class-File API); generated `.class` files target
Java 21+.

```sh
mvn install                 # build souther-runtime and souther-compiler, run the tests

# compile a single .sou file to .class files
java -cp souther-compiler/target/classes:souther-runtime/target/classes \
     net.unit8.souther.compiler.Main examples/businesstrip/src/main/souther/businesstrip.sou -d /tmp/out
```

Programmatically:

```java
// one self-contained module
Map<String, byte[]> classes = Compiler.compile(source);

// several modules linked through their imports
Map<String, byte[]> classes = Compiler.compileModules(List.of(employeeSrc, tripSrc));
```

The repository is a two-module Maven build: `souther-runtime` (the `Raw` model, decoder/encoder
facades, `Option`, `NonEmptyList`, `Behavior`) and `souther-compiler` (lexer, parser, type
checker, deriver, and the ClassFile backend). Runnable `.sou` examples and a Spring Boot + jOOQ
interop example live under [`examples/`](examples/).

## Status

Implemented: all primitive types (`Bool` / `Int` / `Decimal` / `String` / `Date` / `DateTime`),
`List<T>`, `Map<String, T>`, optional fields (`?` → `Option`) with `Some`/`None` matching,
product / sum / unit data, `include`, `invariant`, derived decoders/encoders, `behavior`,
`required behavior` with inferred and propagated requirement sets, inline required-behavior
calls, `constructs`, record literals with spread and `with`, unmarked sum outputs, type-routed
`>>` (including decoder/encoder pipeline stages), union parameter types and matching, `match` /
`let` / `if` / `require`, the String / Int / Decimal / List / Map standard library, modules with
`exposing` / `import` / cyclic detection, JVM visibility hardening, and direct ClassFile
bytecode generation.

## License

Copyright ©kawasima 2026

Released under the [Eclipse Public License 2.0](https://www.eclipse.org/legal/epl-2.0/)
(EPL-2.0). See [`LICENSE`](LICENSE) for the full text.
