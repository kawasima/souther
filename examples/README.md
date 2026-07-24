# Souther examples

These examples exercise the whole Souther development lifecycle — write `.sou`, generate, use the
generated types from typed code, compile and test. Each business unit is an independent Maven
module: it generates types from its `.sou`, uses them from typed code, and runs a smoke test over
decode/encode.

A domain definition is just **data + invariant + behavior**. Decoders and encoders are not part of
the Souther notation; they are **derived** from the data shape (JSON key = field name; a data with a
single primitive field is a newtype = the bare primitive; the discriminator field of a sum is
`"type"` and the tag is the case name).

## How generation works: a javac annotation processor

`.sou → .class` is done not by a dedicated build-tool plugin but by a **javac annotation processor**
(`net.unit8.souther.compiler.apt.SoutherProcessor`). Whenever `mvn compile` (or plain javac, or
Gradle) runs, the processor compiles the `.sou` files in `src/main/souther` and emits the generated
types into `target/classes`. Because `target/classes` is on javac's compile classpath, the
hand-written code (and the smoke tests) **compile directly against those generated types**. No exec
step, no separate module, no Souther-specific plugin.

The whole Maven wiring is just this (set once for all modules in `examples/pom.xml`):

```xml
<plugin>
  <artifactId>maven-compiler-plugin</artifactId>
  <configuration>
    <annotationProcessorPaths>
      <path>net.unit8.souther:souther-compiler:0.1.0-SNAPSHOT</path>
    </annotationProcessorPaths>
    <compilerArgs><arg>-Asouther.source=${project.basedir}/src/main/souther</arg></compilerArgs>
  </configuration>
</plugin>
```

`souther-compiler` only sits on `annotationProcessorPaths`; it is not an app dependency and does not
end up in the artifact jar. With Gradle you use the same processor via an `annotationProcessor`
dependency plus the `-Asouther.source` compiler arg.

## Modules

| Module | What it shows |
| --- | --- |
| `email` | A single-field data + invariant (the minimal example: a newtype decoded from a bare string) |
| `contact` | A sum data (sealed) + a discriminated decoder (discriminator `"type"`, tag = case name) |
| `expense` | `List<T>` / nested newtypes / a product decode·encode round trip |
| `cart` | List combinators `map`/`filter`/`all`/`any` (`souther.list` derives them from `fold`) + the empty list `[]`. Actually runs the behavior `quote` and checks its result cases |
| `businesstrip` | include (field composition) + a nested newtype invariant |
| `member` | Member lookup. A `required behavior findMember` (outside-world dependency) + type routing `>->`. Actually compiles the Spring MVC + jOOQ boundary code (below) |
| `account` | Account withdrawal, "read → check → write". Binds `withdraw` (which has two injected behaviors) from **Clojure + Pedestal rather than Java**, connected to H2 inside a transaction (below). It shows that the generated types are used the same way even when the boundary language changes |
| `ordering` | Ordering + stock reservation. Two injected behaviors joined with `>->`, and it **actually starts Spring Boot, connects to H2, and shows transaction control**: if the second stage returns the `OutOfStock` case, the first stage's INSERT is rolled back too (below) |

Modules that are `.sou`-only with no hand-written Java (email/contact/expense/cart/businesstrip)
carry a single minimal `package-info.java` to trigger the processor (javac does not run annotation
processing unless there is at least one source). The smoke tests call the generated
`decoder()`/`encoder()` in a typed way (`decoder()` is `Decoder<…, T>`; `decode(input, Path.ROOT)`
returns `Result<T>`, and `Ok`/`Err` are told apart by pattern match — no wildcard, no cast).

## Running

```sh
mvn -o install -DskipTests              # core (souther-runtime / souther-compiler) into ~/.m2
mvn -o -f examples/pom.xml verify       # generate → compile → smoke-test every example
```

This is kept independent of the core reactor (the root `mvn test`) so the Spring/jOOQ dependencies
do not weigh the core build down.

Only `ordering` actually starts Spring Boot, so **the first build needs network to fetch the
starters** (run it once without `-o` and it lands in `~/.m2`; after that `-o` works). The other
seven examples run offline.

## Java interop (Spring MVC + jOOQ) — member

The `member` module shows, in a typed way, how the generated types are used from a real app, and it
**actually compiles**. The flow is one-directional.

```text
HTTP → decode (Result<会員ID>) → behavior >-> → match the output cases → encode → HTTP
```

The gist of `member.sou`:

```text
behavior findMember : (id: 会員ID) -> 会員 | 会員なし | 保存データ不正    // no impl → injected from Java
behavior 会員を照会し整形する = findMember >-> 会員を表示用に整形する
// 会員を照会し整形する : 会員ID -> 会員表示 | 会員なし | 保存データ不正
```

Only `findMember`'s success case `会員` flows into the formatting stage; the two failure cases pass
through it and remain in the output (type routing, spec 14.2). The required set `{findMember}` is
inferred by the compiler. The output is **domain outcomes only** — a platform failure such as a DB
outage is not a case: the Java binding throws and Souther passes it through (spec 13.4 / ADR-0029).

The generated `findMember` is an **abstract base class** (it implements `Behavior`) that carries
`protected` factories for the declared unit-data output cases `会員なし` / `保存データ不正`. The
implementation `extends` it and builds the failure cases with the inherited factories (not `new`).

| Java file | Package | Role |
| --- | --- | --- |
| `JooqFindMember.java` | `app.member` | The jOOQ impl that **extends** `findMember`. The success value `会員` is built with the decoder; the failure cases with the inherited `会員なし()` / `保存データ不正()`. DB exceptions are not caught — they are thrown (platform failures pass through) |
| `SoutherBeans.java` | `app.member.web` | Binds the pipeline with `会員を照会し整形する.bind(new JooqFindMember(dsl))` and exposes it as a Bean (spec 19.5) |
| `MemberController.java` | `app.member.web` | `@RestController`. Decodes input with `会員ID.decoder()` (branching on `Result`'s `Ok`/`Err`), folds the domain output cases into an HTTP status (200 / 404 / 500) with a `switch`. A platform-failure exception that passed through is mapped to 503 by an `@ExceptionHandler`. encode returns a plain Map, so Spring/Jackson serialize it to JSON as-is |

The generated-path containment (spec 2.1) holds even across the Java boundary. Because data
constructors are non-public, the controller cannot build data — it only tells the output cases apart
by type and encodes them. Only the effect implementation (`JooqFindMember`) can construct, and only
the cases **the behavior it extends declared**. `new 会員なし()` from another package will not
compile. Reading values out also goes through the encoder (spec 8.5).

> `MemberController`'s `@ExceptionHandler` catches Spring's `org.springframework.dao.DataAccessException`
> and maps it to 503 (the boundary type of ADR-0029). jOOQ's own exceptions are not subclasses of
> that type, so the injected `DSLContext` must have Spring's exception translation enabled (Spring
> Boot's jOOQ auto-config adds it by default; `ordering` verifies a real 503 through this path).

## Spring Boot + H2 + transaction control — ordering

Unlike member, `ordering` does not just **compile** the boundary code — it **actually starts Spring
Boot, connects to H2, and verifies transaction control**. The test brings up embedded Tomcat with
`@SpringBootTest(webEnvironment = RANDOM_PORT)` and sends **real HTTP** (the JDK `HttpClient`) to
`POST /orders` — Tomcat → Jackson → controller → service → transaction → H2 → JSON. Where the other
examples keep external dependencies `provided` (never run), this one resolves the Spring Boot 4
starters at real versions and runs them (DataSource / DSLContext / TransactionManager / schema.sql
execution are all left to autoconfig).

The pipeline joins two injected behaviors with `>->`. **The output is domain outcomes only** — it
has no infra case such as "DB unreachable":

```text
behavior 注文を記録する   : (注文: 注文) -> 注文受付              // INSERT orders (injected)
behavior 在庫を引き当てる : (受付: 注文受付) -> 注文確定 | 在庫不足  // UPDATE stock (injected)
behavior 注文を処理する = 注文を記録する >-> 在庫を引き当てる
// 注文を処理する : 注文 -> 注文確定 | 在庫不足
```

The first stage `注文を記録する`'s success case `注文受付` matches the second stage's input type and
flows in (type routing, spec 14.2). The highlight is that **rollback happens in two ways**.

**A domain failure (out of stock) → rolled back programmatically.** Because Souther represents
failure as a **case rather than an exception**, `在庫不足` arrives as a "returned value", not a
"thrown exception". The controller runs the pipeline inside a `TransactionTemplate`, `switch`es on
the output case, and for `在庫不足` calls **`setRollbackOnly()`** (the same switch also decides the
HTTP status). The order row the first stage INSERTed is rolled back by this.

**A platform failure (DB down, etc.) → auto-rolled-back by exception.** This is not a domain
outcome, so it is not a case. The Java binding (the jOOQ impl) throws, and **Souther passes it
through** (the generated `>->` pipeline does not swallow exceptions). `TransactionTemplate`
auto-rolls-back on the RuntimeException, and the boundary's `@ExceptionHandler` maps it to 503. "The
language has no exceptions, but the boundary Java throws; the distinction is domain outcome vs
platform failure" — that is the policy of spec §13.4 / ADR-0029, and this example demonstrates it.

| Java file | Package | Role |
| --- | --- | --- |
| `JooqRecordOrder.java` | `app.ordering` | The jOOQ impl that **extends** `注文を記録する`. INSERTs into orders and builds the assigned `注文受付` with the decoder. DB exceptions are not caught — they are thrown (platform failures pass through) |
| `JooqAllocateStock.java` | `app.ordering` | Extends `在庫を引き当てる`. Reserves stock with a conditional UPDATE; if zero rows change, the inherited `在庫不足()`. On confirmation the remaining stock is read as a jOOQ `Record` and built with **`注文確定.recordDecoder()`** (raoh-jooq's Record-source decoder, spec 10.6). DB exceptions are thrown |
| `OrderingConfig.java` | `app.ordering.web` | Adds only the generated-side beans: the injected impls, `注文を処理する.bind(...)`, `TransactionTemplate`, and a `Settings` that turns off jOOQ identifier quoting (unquoted names are upper-cased by H2, so they match the lower-case table names in code). DataSource / DSLContext / TransactionManager come from autoconfig. The autoconfig DSLContext goes through a `TransactionAwareDataSourceProxy`, so the first stage's INSERT and the second stage's UPDATE join one transaction (the premise for rollback) |
| `OrderController.java` | `app.ordering.web` | `@RestController` + transaction control. Decodes the body with `注文.decoder()` (destructuring `Ok` with a record pattern, `Err` is 400) and runs the pipeline inside `TransactionTemplate.execute`. One `switch` folds the output cases into an HTTP status (confirmed 201 / out of stock 409) and also calls `setRollbackOnly()` for `在庫不足`. A platform-failure exception that passed through is mapped to 503 by an `@ExceptionHandler` |

The test `OrderingTransactionTest` verifies both rollbacks against a real DB — the 409 for out of
stock, and a **503 for a platform failure** triggered by dropping the stock table — and that in both
cases **no order row remains in the DB** (the first stage's INSERT was rolled back). That is the
evidence of transaction control. As with member, the generated-path containment (spec 2.1) holds
across the Java boundary, and reading values out goes through the encoder (spec 8.5).

> Only this example fetches the Spring Boot starters on the first build, so **it needs network**
> (the others run offline). Once they are in `~/.m2`, `-o` works after that. The DB connection info
> is in `src/main/resources/application.properties` (in-memory H2), and the schema and stock seed
> are in `schema.sql` / `data.sql`, both loaded at startup by Boot's autoconfig.

## Clojure + Pedestal interop — account

`account` shows that the boundary using the generated types can be **Clojure rather than Java** and
nothing else changes. The domain is the same "read → check → write" as ordering, with two injected
behaviors. The output is **domain outcomes only**.

```text
behavior currentBalance : (account: AccountNo) -> Balance | NoAccount             // SELECT (injected)
behavior updateBalance  : (account: AccountNo, newBalance: Balance) -> Withdrawn   // UPDATE (injected)
behavior withdraw : (request: WithdrawRequest) -> Withdrawn | InsufficientFunds | NoAccount
    requires currentBalance, updateBalance
```

`withdraw`'s body reads the current balance, passes through if there is no account, otherwise checks
it with `require current.value >= request.amount.value`, and writes the new balance if funds are
enough. The non-negativity of the new balance `Balance(current - amount)` is discharged at compile
time by the require just above it. If funds are short it returns `InsufficientFunds` without writing.

The `.sou`-side compile-time check (`fake` + `example` confirm the three cases with no DB) is run by
the Maven annotation processor. The account module has no hand-written Java, so it carries a single
minimal `package-info.java` to trigger the processor. **The Clojure app puts that generated output
(`target/classes`) straight on its classpath** (`target/classes` is in `:paths` in `deps.edn`).

### Implementing an injected behavior from Clojure — `proxy` + `decoder()`

The generated injected behaviors are **abstract base classes** (`CurrentBalance` implements
`Behavior<AccountNo, CurrentBalanceResult>`; `UpdateBalance` has `apply(AccountNo, Balance)`).
Clojure implements them with `proxy`. But a `proxy` cannot reach the base's `protected` factories,
so it builds the returned domain values (`Balance` / `Withdrawn` / `NoAccount`) through the
**public generated `decoder()`** — the sanctioned boundary path for turning outside values into
domain data, with data constructors staying non-public (spec 8.5 / 2.1). No gen-class, no AOT.

These interop patterns are packaged as a small reusable library under `souther-clj/` (see its
README), written to be lifted out into its own repo unchanged — its source refers to no domain
type and works by reflection over whatever generated classes the caller passes in:

- `souther.decode` — `decode` runs a `decoder()` over Clojure data (keyword keys accepted) and
  returns `[:ok value]` / `[:err issues]` with issues as plain maps; `construct` builds a case value
  through its `decoder()`.
- `souther.behavior` — `defbehavior`, the `proxy` sugar for an injected behavior.
- `souther.match` — `case-of`, which folds a sealed output union and checks **at macro-expansion**
  that the handlers cover exactly the union's permitted subclasses — carrying Souther's `match`
  totality across the boundary (drop a case and it is a compile error, not a silent fall-through).

| Clojure file | Role |
| --- | --- |
| `account/db.clj` | The H2 DataSource, schema, and seed. The dynamic var `*conn*` is the seam that binds "read → check → write" into one transaction: the boundary rebinds it to the transaction's connection, and both behaviors query through `current`, so `currentBalance`'s SELECT and `updateBalance`'s UPDATE join the same connection |
| `account/behaviors.clj` | Implements `currentBalance` / `updateBalance` with `souther.behavior/defbehavior`, building the return values with `souther.decode/construct`. Binds with `Withdraw/bind` (spec 19.5). SQL exceptions are not caught — they are thrown (platform failures pass through) |
| `account/service.clj` | The Pedestal boundary. The whole request is the JSON body `{"account": …, "amount": …}`, handed straight to `WithdrawRequest/decoder` via `souther.decode/decode` (the `Amount` invariant `value >= 0` is rejected here → 400, and the Raoh issues are returned in the body). Then it runs `withdraw` inside `with-transaction` and folds the output with `souther.match/case-of` (Withdrawn 200 / InsufficientFunds 409 / NoAccount 404) — miss a case and it will not compile |
| `account/server.clj` | The `-main` that creates and seeds H2 and starts Jetty |

`InsufficientFunds` / `NoAccount` arrive as **returned values**, and no write happened on those
branches, so there is nothing to roll back. Wrapping read → check → write in one transaction is for
atomicity — so a concurrent withdrawal cannot interleave and double-spend. A platform failure (a SQL
exception) is not a case: it passes through `withdraw` untouched, `with-transaction` auto-rolls-back,
and it propagates to the framework. The full platform-failure → 503 + rollback treatment against a
real DB is shown by `ordering`, so account does not repeat it.

### Running

Generate the types first, then run Clojure (Clojure lives outside the Maven reactor, in its own
`deps.edn`). Generation itself needs no Maven — the `:gen` alias runs `SoutherProcessor` through the
JDK compiler API (`souther.build/generate!`), with `souther-compiler` on the alias classpath only:

```sh
cd examples/account
clojure -X:gen                                   # .sou → target/classes (the .sou examples are checked here too)
clojure -X:test                                  # the souther-clj library, behavior+DB, and Pedestal boundary tests (15 of them)
clojure -M:run                                   # starts on localhost:8890
```

`mvn -o -f examples/pom.xml -pl account compile` generates the same classes if you prefer the Maven
path (it is what `mvn … verify` uses for the whole reactor).

```sh
curl localhost:8890/accounts/acc-1                                            # {"account":"acc-1","balance":1000}
curl -X POST localhost:8890/withdrawals \
     -H 'Content-Type: application/json' -d '{"account":"acc-1","amount":300}'  # {"account":"acc-1","balance":700}
curl -X POST localhost:8890/withdrawals \
     -H 'Content-Type: application/json' -d '{"account":"acc-1","amount":5000}' # 409 {"error":"insufficient_funds","shortfall":...}
```

> Clojure / Pedestal / next.jdbc are fetched from clojars / Central on the first run, so **it needs
> network** (once they are in `~/.m2` and gitlibs, no more). `mvn -o -f examples/pom.xml verify` does
> not include this Clojure app (a separate toolchain). The account module itself is generated and
> checked offline, like the others.
