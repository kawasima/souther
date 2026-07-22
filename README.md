# Souther

Souther is a small JVM language for describing business data, value constraints, and state transitions, then generating types and behaviors that Java can use.

You write business rules with `data` and `behavior`. `invariant` makes value constraints explicit, while behaviors implemented in Java make dependencies such as a database or a clock explicit. This keeps the domain model's boundary intact as it becomes an implementation.

```text
external input -> decoder -> Souther data / behavior -> encoder -> external output
                                   ^
                           Java injects dependencies
```

Souther is intended to turn the specification DSL of [Specification Model-Driven Development (SMDD)](#specification-model-driven-development-smdd) into an executable implementation model. It makes executable the constraints, validated construction, and outside-world dependencies that the specification DSL leaves in comments.

## Start with an example

This example either moves a travel request into the `Submitted` state or rejects it. An `Amount` cannot be negative, and the behavior produces either `Submitted` or `Rejected`.

```text
module example.trip
import String ( length )

data EmployeeId = String
    invariant length(value) > 0

data Amount = Int
    invariant value >= 0

data DraftRequest = { applicant: EmployeeId, plannedCost: Amount }
data Submitted = { ...DraftRequest, submittedAt: String }
data Rejected = { reason: String }

behavior submit : (request: DraftRequest, submittedAt: String) -> Submitted | Rejected
    constructs Submitted, Rejected

let submit (request, submittedAt) = {
    require request.plannedCost.value <= 100000 else Rejected { reason = "high_cost" }
    Submitted { ...request, submittedAt = submittedAt }
}
```

The example introduces Souther's central ideas:

- `data` represents domain values and states. `|` means alternatives, and `...` composes fields.
- An `invariant` is checked every time that `data` is constructed by a decoder or behavior.
- A `behavior` declares its input and possible business outcomes; `constructs` grants it authority to construct those values.
- `require ... else ...` is a business branch. `Rejected` is an ordinary domain value, not an exception.

The complete runnable example is in [`examples/businesstrip/`](examples/businesstrip/).

## Try it

Souther requires JDK 25 and Maven. The compiler uses the finalized JDK Class-File API (JDK 24+), so JDK 25 is a build-time toolchain, not a runtime floor: generated `.class` files and `souther-runtime` target Java 21, so an application consuming Souther's output runs on Java 21 and later. Because `SoutherProcessor` generates bytecode during the host build (see [`examples/README.md`](examples/README.md)), a project that runs it as an annotation processor also needs JDK 25 as its build toolchain, even though its production runtime stays on Java 21+.

```sh
# Build the runtime and compiler, and run the tests.
mvn install

# Compile a .sou file to .class files.
java -cp souther-compiler/target/classes:souther-runtime/target/classes \
     net.unit8.souther.compiler.Main \
     compile examples/businesstrip/src/main/souther/businesstrip.sou -d /tmp/out
```

To try a behavior without writing any Java, `souther run` compiles a `.sou` in memory and drives one behavior: it decodes the `--input` JSON through the behavior's derived decoders, applies it, and prints the result through its derived encoder. A single file run on its own may omit the `module` header — it is named after the file (ADR-0043).

The `souther-cli` module bundles the compiler, runtime, and their dependencies into one really-executable jar, so no classpath or `java -jar` is needed:

```sh
# Build target/souther — a self-contained executable (a launcher stub prepended to an uber jar).
mvn -pl souther-cli -am -DskipTests install

# hello.sou  (no module header needed)
#   behavior greet : (name: String) -> String
#   let greet (name) = "Hello, " ++ name
./souther-cli/target/souther run hello.sou --behavior greet --input '"world"'
# => "Hello, world"
```

`--behavior` may be omitted when the module holds exactly one runnable behavior, and `--input` when the behavior takes no argument. A multi-argument behavior takes a JSON array (`--input '[3, 7]'`). Only a self-contained behavior with a `let` and no `requires` can be driven; an injected behavior or a `>->` pipeline is refused with a reason.

The same `souther` binary also compiles to `.class` files (`souther compile hello.sou -d out`). It runs on any Unix shell; on Windows, use it as a plain jar (`java -jar souther-cli/target/souther.jar …`).

To integrate Souther into an application's Maven build, configure `SoutherProcessor` as an annotation processor. [`examples/README.md`](examples/README.md) contains that configuration and examples using the generated types from Java, Spring Boot, and jOOQ.

The Java API compiles a source string containing either one module or several linked modules:

```java
Map<String, byte[]> classes = Compiler.compile(source);
Map<String, byte[]> linked = Compiler.compileModules(List.of(employeeSource, tripSource));
```

## What Souther guarantees

### Construction of invalid data is confined

Only a derived decoder, a behavior declaring `constructs T`, that behavior's Java implementation, or compiler-generated code may construct `data T`. Merely using `T` as a return type does not grant construction authority. Generated constructors are non-public, so the rule holds across the Java boundary.

### Business outcomes are not exceptions

A behavior's output is a sum of named data, such as `Submitted | Rejected`; it has no `Result` / `Either` wrapper or privileged success/failure slot. `f >-> g` sends only the output cases that `g` accepts to the next stage, and passes the rest through unchanged. Whether a case leaves the main path is a property of composition, not of the value itself.

The runtime's `Result` for malformed decoder input is separate from a behavior's domain outcome: the former belongs to the boundary, the latter is domain data.

### The outside world stays at the Java boundary

Souther does not directly call databases, HTTP services, files, clocks, or ID generators. Instead, it declares a behavior with no implementation and Java injects that implementation.

```text
behavior currentTime : () -> DateTime

behavior approve : (request: AwaitingApproval) -> Approved
    requires currentTime
```

Souther cannot call arbitrary Java APIs; Java can use the generated data and behaviors. This asymmetry makes the boundary between pure domain computation and external effects explicit.

## Language shape

Souther is deliberately small:

- immutable product / sum / unit data, `List<T>`, `Map<String, T>`, and optional fields (`T?`)
- `invariant`, `match`, `let`, `if`, `require`, record literals, and field spread
- `behavior`, Java injection, `requires`, `constructs`, and type-routed `>->` composition
- derived decoders / encoders and explicit modules with `exposing` / `import`

It intentionally does not provide exceptions, `null`, mutable state, asynchronous execution, arbitrary JVM calls, type classes or higher-kinded types, a package manager, or a REPL. These omissions keep construction paths, value constraints, and outside-world dependencies tractable.

Not yet implemented: a Java-source backend, incremental compilation, source maps, static invariant proofs, handwritten codec syntax, and JSON Schema / Wasm / JavaScript output. An LSP server ships (`souther-lsp`); its name resolution is per-module, and workspace-wide (cross-module) resolution is future work.

## Details and examples

- [Language specification (Japanese)](specification.adoc): the normative syntax and semantics
- [ADRs](docs/adr/README.md): design decisions, alternatives, and prior art
- [Examples](examples/README.md): Maven / Gradle integration, decoders / encoders, and Java, Spring Boot, and jOOQ interop

The repository has two Maven modules:

- `souther-runtime`: `Option`, `Behavior`, `Fn`, boundary `Result`, `ConstraintViolation`, and numeric / collection helpers
- `souther-compiler`: lexer, parser, type checker, deriver, and ClassFile backend

## Specification Model-Driven Development (SMDD)

The SMDD specification DSL expresses business rules with `data` (AND / OR / List / `?`) and `behavior` (`->` / `>->`). It leaves value constraints, the fact that a value has been validated, and outside-world dependencies in comments. Souther maps those respectively to `invariant`, closed construction paths (`decoder` / `constructs`), and behaviors injected from Java.

The [language specification](specification.adoc) has the full mapping and design principles.

## License

Copyright © kawasima 2026

Released under the [Eclipse Public License 2.0](https://www.eclipse.org/legal/epl-2.0/).
