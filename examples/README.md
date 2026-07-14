# Souther サンプル

Souther のソース（`.mdl`）例。いずれも `mvn install` 済みのコンパイラで実際にコンパイルできます。

| ファイル | 内容 |
| --- | --- |
| `email.mdl` | 単一フィールド data ＋ invariant ＋ decoder/encoder（最小例） |
| `contact.mdl` | 直和 data（sealed）＋ discriminate デコーダ ＋ match 分岐 |
| `expense.mdl` | `List<T>` のデコード（`[index]` エラー集積）＋ `size` ＋ 算術 ＋ 多分岐成功 |
| `businesstrip.mdl` | 出張申請モデルの断面: include（共通項目）／状態遷移 behavior／`require` ガード／spread（`..申請`）／railway |

## コンパイル

```sh
mvn -q -pl souther-compiler -am install -DskipTests   # 一度だけ
java -cp souther-compiler/target/classes \
     net.unit8.souther.compiler.Main examples/businesstrip.mdl -d /tmp/out
```

生成された `.class` は Java 21+ のプログラムから利用できます（`decoder()` / `encoder()` /
behavior クラスの `apply(...)`）。振る舞いの実行時に必要なランタイム（`Raw` / `Result` /
`Decoder` など）は `souther-runtime` にあります。

> 注: 各機能を端から端まで駆動して検証しているのは `souther-compiler/src/test/java` の
> `Compile*Test`（`.mdl` をコンパイル→バイトコードをロード→decode/encode/apply を実行）です。
> ここの `.mdl` はその中で使っているモデルを読める実ファイルとして取り出したものです。
