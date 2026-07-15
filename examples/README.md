# Souther サンプル

Souther のソース（`.mdl`）例。いずれも `mvn install` 済みのコンパイラで実際にコンパイルできます。

ドメイン定義は **data ＋ invariant ＋ behavior** だけを書きます。decoder/encoder は
Souther の記法にはなく、データ形状から**導出**されます（JSON キー＝フィールド名、
単一プリミティブフィールドの data は newtype＝裸のプリミティブ、直和の判別子フィールドは
`"type"`／タグはアーム名）。カスタムのキー名・正規化・判別子が必要なら、それは境界
（Java / Raoh）の仕事です。

| ファイル | 内容 |
| --- | --- |
| `email.mdl` | 単一フィールド data ＋ invariant（最小例。decoder/encoder は導出） |
| `contact.mdl` | 直和 data（sealed）＋ match 分岐（判別子は導出） |
| `expense.mdl` | `List<T>` ＋ `size` ＋ 算術 ＋ 多分岐成功（`-> A | B`） |
| `businesstrip.mdl` | 出張申請モデルの断面: include（共通項目）／状態遷移 behavior／`require` ガード／spread（`..申請`）／railway |
| `member.mdl` | 会員照会: `required behavior findMember`（外界依存）＋ 型ルーティング `>>`（失敗アームが素通り）。`interop/` の Java 例と対で読む |

## コンパイル

```sh
mvn -q -pl souther-compiler -am install -DskipTests   # 一度だけ
java -cp souther-compiler/target/classes \
     net.unit8.souther.compiler.Main examples/businesstrip.mdl -d /tmp/out
```

生成された `.class` は Java 21+ のプログラムから利用できます。各 data には導出された
`decoder()` / `encoder()`、behavior クラスには `apply(...)` があります。出力に Result は無く、
`apply` は出力アームの値をそのまま返し、`decode` は成功値または `復号失敗`（`DecodeFailure`）を
返します。実行時ランタイム（`Raw` / `Decoder` / `Encoder` / `DecodeFailure` / `Violation` など）は
`souther-runtime` にあります。カスタムの境界コーデックが要るときは、生成された data の
（不変条件を検査する）構築を呼ぶ Raoh デコーダを Java 側で書きます。

> 注: 各機能を端から端まで駆動して検証しているのは `souther-compiler/src/test/java` の
> `Compile*Test`（`.mdl` をコンパイル→バイトコードをロード→decode/encode/apply を実行）です。
> ここの `.mdl` はその中で使っているモデルを読める実ファイルとして取り出したものです。

## Java 相互運用（Spring Boot ＋ jOOQ）

`member.mdl` と `interop/` の Java 例は、生成物を実アプリからどう呼ぶかを示します（`interop/` は
読むための実例で、ビルドには組み込んでいません——Spring / jOOQ 依存は各自のプロジェクト側）。

流れは一方向です。

```text
HTTP → decode（会員ID | 復号失敗）→ behavior >> → 出力アームを match → encode → HTTP
```

`member.mdl` の要点:

```text
required behavior findMember(id: 会員ID) -> 会員 | 会員なし | 保存データ不正 | DB不通
behavior 会員を照会し整形する = findMember >> 会員を表示用に整形する
// 会員を照会し整形する : 会員ID -> 会員表示 | 会員なし | 保存データ不正 | DB不通
```

`findMember` の成功アーム `会員` だけが整形段に流れ、失敗3アームは整形段を素通りして出力に
残ります（型ルーティング。spec 14.2）。要求集合 `{findMember}` はコンパイラが推論します。

生成された `findMember` は**抽象基底クラス**（`Behavior` を継承）で、宣言した単位data の出力アーム
`会員なし` / `保存データ不正` / `DB不通` に対する `protected` ファクトリを持ちます。実装はこれを
`extends` し、失敗アームは継承したファクトリで作ります（`new` ではない）。

| Java ファイル | パッケージ | 役割 |
| --- | --- | --- |
| `JooqFindMember.java` | `app.member`（アプリ側） | `findMember` を **extends** した jOOQ 実装。成功値 `会員` は decoder で組み立て、失敗アームは継承した `会員なし()` 等で作る。DB 例外は `DB不通` アームに畳む |
| `SoutherBeans.java` | `app.member.web` | `会員を照会し整形する.bind(new JooqFindMember(dsl))` でパイプラインを束縛し Bean 化（spec 19.5） |
| `MemberController.java` | `app.member.web` | `@RestController`。入力を `会員ID.decoder()` で decode し、`apply` の出力アームを `switch` で HTTP ステータス（200 / 404 / 500 / 503）へ畳む |
| `RawJson.java` | `app.member.web` | encoder が返す `Raw` を Jackson 用の素の Java 値へ落とす小物 |

生成経路の封じ込め（spec 2.1）が Java 越しでも保たれます。data のコンストラクタは非公開なので、
コントローラは data を構築できず、出力アームを型で見分けて encode するだけ。効果実装（`JooqFindMember`）
だけが、しかも `findMember` を継承することで**その behavior が宣言した出力アームに限って**構築できます。
別パッケージから `new 会員なし()` はコンパイルできません。値の取り出しも encoder を通します（spec 8.5）。
