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
| `businesstrip` | include（フィールド合成）＋ ネストした newtype invariant |
| `member` | 会員照会。`required behavior findMember`（外界依存）＋ 型ルーティング `>->`。Spring MVC + jOOQ の境界コードを実際にコンパイルする（下記） |

`.sou` だけで手書き Java の無いモジュール（email/contact/expense/businesstrip）には、プロセッサを起動する
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

## Java 相互運用（Spring MVC ＋ jOOQ）― member

`member` モジュールは、生成物を実アプリからどう使うかを型付きで示し、かつ**実際にコンパイル**します。
流れは一方向です。

```text
HTTP → decode（Result<会員ID>）→ behavior >-> → 出力アームを match → encode → HTTP
```

`member.sou` の要点:

```text
behavior findMember : (id: 会員ID) -> 会員 | 会員なし | 保存データ不正 | DB不通    // 実装なし → Java 注入
behavior 会員を照会し整形する = findMember >-> 会員を表示用に整形する
// 会員を照会し整形する : 会員ID -> 会員表示 | 会員なし | 保存データ不正 | DB不通
```

`findMember` の成功アーム `会員` だけが整形段に流れ、失敗3アームは整形段を素通りして出力に残ります
（型ルーティング。spec 14.2）。要求集合 `{findMember}` はコンパイラが推論します。

生成された `findMember` は**抽象基底クラス**（`Behavior` を継承）で、宣言した単位data の出力アーム
`会員なし` / `保存データ不正` / `DB不通` に対する `protected` ファクトリを持ちます。実装はこれを
`extends` し、失敗アームは継承したファクトリで作ります（`new` ではない）。

| Java ファイル | パッケージ | 役割 |
| --- | --- | --- |
| `JooqFindMember.java` | `app.member` | `findMember` を **extends** した jOOQ 実装。成功値 `会員` は decoder で組み立て、失敗アームは継承した `会員なし()` 等で作る。DB 例外は `DB不通` アームに畳む |
| `SoutherBeans.java` | `app.member.web` | `会員を照会し整形する.bind(new JooqFindMember(dsl))` でパイプラインを束縛し Bean 化（spec 19.5） |
| `MemberController.java` | `app.member.web` | `@RestController`。入力を `会員ID.decoder()` で decode し（`Result` の `Ok`/`Err` を分岐）、`apply` の出力アームを `switch` で HTTP ステータス（200 / 404 / 500 / 503）へ畳む。encode は素の Map を返すので Spring/Jackson がそのまま JSON 化 |

生成経路の封じ込め（spec 2.1）が Java 越しでも保たれます。data のコンストラクタは非公開なので、
コントローラは data を構築できず、出力アームを型で見分けて encode するだけ。効果実装（`JooqFindMember`）
だけが、しかも `findMember` を継承することで**その behavior が宣言した出力アームに限って**構築できます。
別パッケージから `new 会員なし()` はコンパイルできません。値の取り出しも encoder を通します（spec 8.5）。
