# Souther サンプル

Souther の開発ライフサイクル（`.sou` を書く → 生成する → 型付き Java から使う → コンパイル・テスト）を
そのまま検証する例です。各業務単位が独立した Maven モジュールになっていて、`.sou` から型を生成し、
その型を Java から使い、smoke テストで decode/encode を回します。

ドメイン定義は **data ＋ invariant ＋ behavior** だけを書きます。decoder/encoder は Souther の記法には
なく、データ形状から**導出**されます（JSON キー＝フィールド名、単一プリミティブフィールドの data は
newtype＝裸のプリミティブ、直和の判別子フィールドは `"type"`／タグはアーム名）。

## 生成の仕組み: javac のアノテーションプロセッサ

`.sou → .class` は、専用のビルドツールプラグインではなく、**javac のアノテーションプロセッサ**
（`net.unit8.souther.compiler.apt.SoutherProcessor`）で行います。`mvn compile`（や素の javac、Gradle）が
走れば、プロセッサが `src/main/souther` の `.sou` をコンパイルし、生成した型を `target/classes` に吐きます。
`target/classes` は javac のコンパイルクラスパスに入るので、手書きの Java（と smoke テスト）がその生成型を
**そのまま参照してコンパイル**されます。exec ステップも別モジュールも Souther 専用プラグインも要りません。

Maven の配線はこれだけ（`examples/pom.xml` で全モジュール共通に設定）:

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

`souther-compiler` は `annotationProcessorPaths` に載るだけで、アプリの依存にも成果物 jar にも入りません。
Gradle なら同じプロセッサを `annotationProcessor` 依存＋`-Asouther.source` の compilerArg で使います。

## モジュール

| モジュール | 内容 |
| --- | --- |
| `email` | 単一フィールド data ＋ invariant（最小例。newtype＝裸の文字列から decode） |
| `contact` | 直和 data（sealed）＋ 判別デコーダ（判別子 `"type"`／タグ＝アーム名） |
| `expense` | `List<T>`／ネストした newtype／直積の decode・encode 往復 |
| `cart` | リストコンビネータ `map`/`filter`/`all`/`any`（`souther.list` が `fold` から導く）＋ 空リスト `[]`。behavior `見積る` を実際に実行して結果アームを検査する |
| `businesstrip` | include（フィールド合成）＋ ネストした newtype invariant |
| `member` | 会員照会。`required behavior findMember`（外界依存）＋ 型ルーティング `>->`。Spring MVC + jOOQ の境界コードを実際にコンパイルする（下記） |
| `ordering` | 注文＋在庫引当。2 つの注入 behavior を `>->` でつなぎ、**実際に Spring Boot を起動し H2 に接続してトランザクション制御を見せる**。後段が `在庫不足` アームを返したら前段の INSERT ごと巻き戻す（下記） |

`.sou` だけで手書き Java の無いモジュール（email/contact/expense/cart/businesstrip）には、プロセッサを起動する
ための最小の `package-info.java` を1つ置いています（javac はソースが1つ以上ないとアノテーション処理を
起動しないため）。smoke テストは生成された `decoder()`/`encoder()` を型付きで叩きます
（`decoder()` は `Decoder<…, T>`、`decode(input, Path.ROOT)` は `Result<T>` を返し、`Ok`/`Err` を
パターンマッチで見分ける ── ワイルドカードもキャストも不要）。

## 実行

```sh
mvn -o install -DskipTests              # コア（souther-runtime / souther-compiler）を ~/.m2 へ
mvn -o -f examples/pom.xml verify       # 全例を生成→コンパイル→smoke テスト
```

コア reactor（ルートの `mvn test`）とは独立させてあり、Spring/jOOQ 依存でコアビルドを重くしません。

`ordering` だけは Spring Boot を実起動するので**初回はスターター取得にネットワークが要ります**
（`-o` を外して一度回すと `~/.m2` に載り、以降は `-o` でも通ります）。他の7例は offline で回ります。

## Java 相互運用（Spring MVC ＋ jOOQ）― member

`member` モジュールは、生成物を実アプリからどう使うかを型付きで示し、かつ**実際にコンパイル**します。
流れは一方向です。

```text
HTTP → decode（Result<会員ID>）→ behavior >-> → 出力アームを match → encode → HTTP
```

`member.sou` の要点:

```text
behavior findMember : (id: 会員ID) -> 会員 | 会員なし | 保存データ不正    // 実装なし → Java 注入
behavior 会員を照会し整形する = findMember >-> 会員を表示用に整形する
// 会員を照会し整形する : 会員ID -> 会員表示 | 会員なし | 保存データ不正
```

`findMember` の成功アーム `会員` だけが整形段に流れ、失敗2アームは整形段を素通りして出力に残ります
（型ルーティング。spec 14.2）。要求集合 `{findMember}` はコンパイラが推論します。出力は**ドメインの帰結
だけ**で、DB ダウンのようなプラットフォーム障害はアームにしません ── Java Binding が例外を投げ、Souther が
素通しします（spec 13.4 / ADR-0029）。

生成された `findMember` は**抽象基底クラス**（`Behavior` を継承）で、宣言した単位data の出力アーム
`会員なし` / `保存データ不正` に対する `protected` ファクトリを持ちます。実装はこれを
`extends` し、失敗アームは継承したファクトリで作ります（`new` ではない）。

| Java ファイル | パッケージ | 役割 |
| --- | --- | --- |
| `JooqFindMember.java` | `app.member` | `findMember` を **extends** した jOOQ 実装。成功値 `会員` は decoder で組み立て、失敗アームは継承した `会員なし()` / `保存データ不正()` で作る。DB 例外は捕まえず投げる（プラットフォーム障害は素通し） |
| `SoutherBeans.java` | `app.member.web` | `会員を照会し整形する.bind(new JooqFindMember(dsl))` でパイプラインを束縛し Bean 化（spec 19.5） |
| `MemberController.java` | `app.member.web` | `@RestController`。入力を `会員ID.decoder()` で decode し（`Result` の `Ok`/`Err` を分岐）、ドメインの出力アームを `switch` で HTTP ステータス（200 / 404 / 500）へ畳む。素通りしてきたプラットフォーム障害の例外は `@ExceptionHandler` が 503 に写す。encode は素の Map を返すので Spring/Jackson がそのまま JSON 化 |

生成経路の封じ込め（spec 2.1）が Java 越しでも保たれます。data のコンストラクタは非公開なので、
コントローラは data を構築できず、出力アームを型で見分けて encode するだけ。効果実装（`JooqFindMember`）
だけが、しかも `findMember` を継承することで**その behavior が宣言した出力アームに限って**構築できます。
別パッケージから `new 会員なし()` はコンパイルできません。値の取り出しも encoder を通します（spec 8.5）。

> `MemberController` の `@ExceptionHandler` は Spring の `org.springframework.dao.DataAccessException`
> を捕まえて 503 に写します（ADR-0029 の境界の型）。jOOQ 自身の例外はこの型のサブクラスではないので、
> 注入する `DSLContext` は Spring の例外変換を効かせておく必要があります（Spring Boot の jOOQ auto-config
> が既定で入れる。`ordering` はこの経路で実際に 503 を検証しています）。

## Spring Boot 起動 ＋ H2 ＋ トランザクション制御 ― ordering

`ordering` は member と違い、境界コードを**コンパイルするだけでなく実際に Spring Boot を起動し、H2 に
接続してトランザクション制御を検証**します。テストは `@SpringBootTest(webEnvironment = RANDOM_PORT)` で
組み込み Tomcat を上げ、`POST /orders` に**本物の HTTP**（JDK の `HttpClient`）を投げます ── Tomcat →
Jackson → コントローラ → サービス → トランザクション → H2 → JSON。他の例が外部依存を `provided`
（実行しない）にしているのに対し、この例だけは Spring Boot 4 系のスターターを実バージョンで解決して
走らせます（DataSource / DSLContext / TransactionManager / schema.sql 実行はすべて autoconfig 任せ）。

パイプラインは 2 つの注入 behavior を `>->` でつなぎます。**出力はドメインの帰結だけ**で、`DB不通` の
ような infra のアームは持ちません:

```text
behavior 注文を記録する   : (注文: 注文) -> 注文受付              // INSERT orders（注入）
behavior 在庫を引き当てる : (受付: 注文受付) -> 注文確定 | 在庫不足  // UPDATE stock（注入）
behavior 注文を処理する = 注文を記録する >-> 在庫を引き当てる
// 注文を処理する : 注文 -> 注文確定 | 在庫不足
```

前段 `注文を記録する` の成功アーム `注文受付` が後段の入力型に一致して流れます（型ルーティング、spec 14.2）。
見せ場は**ロールバックが 2 通り**あることです。

**ドメインの失敗（在庫不足）→ プログラマティックに巻き戻す。** Souther は失敗を**例外でなくアーム**で表す
ので、`在庫不足` は「投げられた例外」でなく「返ってきた値」として届きます。コントローラが
`TransactionTemplate` の中でパイプラインを走らせ、出力アームを `switch` して `在庫不足` のとき
**`setRollbackOnly()`** を呼ぶ（同じ switch で HTTP ステータスも決める）。前段が INSERT した注文行は
これで巻き戻ります。

**プラットフォーム障害（DB ダウン等）→ 例外で自動ロールバック。** これはドメインの帰結ではないので、
アームにしません。Java Binding（jOOQ 実装）が例外を投げ、**Souther がそれを素通しします**（生成された
`>->` パイプラインは例外を握り潰さない）。`TransactionTemplate` が RuntimeException で自動ロールバックし、
境界の `@ExceptionHandler` が 503 に写す。「言語に例外は無いが、境界の Java は投げる。区別はドメインの帰結か
プラットフォーム障害か」── これが spec §13.4 / ADR-0029 の方針で、この例がそれを実演します。

| Java ファイル | パッケージ | 役割 |
| --- | --- | --- |
| `JooqRecordOrder.java` | `app.ordering` | `注文を記録する` を **extends** した jOOQ 実装。orders に INSERT し、採番した `注文受付` を decoder で組む。DB 例外は捕まえず投げる（プラットフォーム障害は素通し） |
| `JooqAllocateStock.java` | `app.ordering` | `在庫を引き当てる` を extends。条件付き UPDATE で在庫を引き当て、更新0行なら継承 `在庫不足()`。確定時の残在庫は jOOQ `Record` として読み、**`注文確定.recordDecoder()`**（raoh-jooq の Record 源 decoder。spec 10.6）で組む。DB 例外は投げる |
| `OrderingConfig.java` | `app.ordering.web` | 生成物側の Bean だけ足す：注入実装・`注文を処理する.bind(...)`・`TransactionTemplate`、および jOOQ の quote を止める `Settings`（未quote 名を H2 が大文字に畳むので小文字の表名と一致させる）。DataSource / DSLContext / TransactionManager は autoconfig 由来。autoconfig の DSLContext は `TransactionAwareDataSourceProxy` 経由なので前段 INSERT と後段 UPDATE が同一トランザクションに入る（ロールバックの前提） |
| `OrderController.java` | `app.ordering.web` | `@RestController`＋トランザクション制御。ボディを `注文.decoder()` で decode し（`Ok` を record パターンで分解、Err は 400）、`TransactionTemplate.execute` の中でパイプラインを走らせる。1 つの `switch` で出力アームを HTTP ステータス（確定 201 / 在庫不足 409）へ畳みつつ、`在庫不足` では `setRollbackOnly()` も呼ぶ。素通りしてきたプラットフォーム障害の例外は `@ExceptionHandler` が 503 に写す |

テスト `OrderingTransactionTest` は 2 通りのロールバックを実 DB で確かめます ── 在庫不足の 409、および
在庫テーブルを落として起こした**プラットフォーム障害の 503**、どちらの時点でも **注文行が DB に残っていない**
（前段 INSERT が巻き戻った）こと。これがトランザクション制御の証拠です。member と同じく生成経路の封じ込め
（spec 2.1）は Java 越しでも保たれ、値の取り出しは encoder を通します（spec 8.5）。

> この例だけは初回ビルドで Spring Boot のスターターを取りに行くため**ネットワークが要ります**
> （他の例は offline で回る）。一度 `~/.m2` に載れば以降は `-o` でも回ります。DB 接続情報は
> `src/main/resources/application.properties`（H2 インメモリ）、スキーマと在庫 seed は `schema.sql` /
> `data.sql` にあり、いずれも Boot の autoconfig が起動時に読み込みます。
