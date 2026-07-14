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

| Java ファイル | 役割 |
| --- | --- |
| `JooqFindMember.java` | `required behavior findMember` の jOOQ 実装。**生成パッケージ `example.member` に置く**（spec 19.6）——失敗アームは非公開コンストラクタで、この境界実装だけが構築できる。DB 例外は `DB不通` アームに畳む |
| `SoutherBeans.java` | `会員を照会し整形する.bind(new JooqFindMember(dsl))` でパイプラインを束縛し Bean 化（spec 19.5） |
| `MemberController.java` | `@RestController`。入力を `会員ID.decoder()` で decode し、`apply` の出力アームを `switch` で HTTP ステータス（200 / 404 / 500 / 503）へ畳む |
| `RawJson.java` | encoder が返す `Raw` を Jackson 用の素の Java 値へ落とす小物 |

要は、アプリ側（コントローラ）は **data を構築できず**、出力アームを型で見分けて encode するだけ。
data を生成できるのは decoder と、生成パッケージ内に置いた境界実装だけ——これがドメインの
生成経路の封じ込め（spec 2.1）を Java 越しでも保つ仕掛けです。

> **設計メモ（未実装）: 効果実装のアーム構築。**
> いまの `JooqFindMember` は失敗アーム（`会員なし` 等）を `new` で作っています。これはコンストラクタが
> package-private で、実装を生成パッケージ `example.member` に同居させたから動くだけで、in-package なら
> **任意の data を `new` できてしまう**——生成経路の封じ込め（spec 2.1）を厳密には満たさない暫定策です。
> あるべき形は「required behavior の実装に、その behavior が**宣言した出力アームだけ**を構築する capability
> を渡す」こと。具体的には、コンパイラが required behavior ごとに抽象基底クラスを生成し、宣言アームの
> `protected` ファクトリ（`会員なし()` 等）を持たせ、実装はそれを継承する。すると実装は **どのパッケージにも
> 置け**、**宣言アーム以外は作れず**、`new` による抜け道が塞がる。data のコンストラクタは非公開のまま。
> spec 13.3 / 19.6 とコンパイラ（`generateRequiredInterface` と `bind`）の変更が要る。
