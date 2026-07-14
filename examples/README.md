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

## コンパイル

```sh
mvn -q -pl souther-compiler -am install -DskipTests   # 一度だけ
java -cp souther-compiler/target/classes \
     net.unit8.souther.compiler.Main examples/businesstrip.mdl -d /tmp/out
```

生成された `.class` は Java 21+ のプログラムから利用できます。各 data には導出された
`decoder()` / `encoder()`、behavior クラスには `apply(...)` があります。実行時ランタイム
（`Raw` / `Result` / `Decoder` / `Encoder` など）は `souther-runtime` にあります。カスタムの
境界コーデックが要るときは、生成された data の（不変条件を検査する）構築を呼ぶ Raoh
デコーダを Java 側で書きます。

> 注: 各機能を端から端まで駆動して検証しているのは `souther-compiler/src/test/java` の
> `Compile*Test`（`.mdl` をコンパイル→バイトコードをロード→decode/encode/apply を実行）です。
> ここの `.mdl` はその中で使っているモデルを読める実ファイルとして取り出したものです。
