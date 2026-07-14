# Souther 言語仕様書

## 1. 目的

Souther は、仕様モデル駆動開発（SMDD）の**仕様DSL**を実装モデルへ素直に書き写すためのJVM向け言語である。

仕様DSLは、業務のルールを `data`（AND / OR / List / `?`）と `behavior`（`->` / `>>`）で表す。ただし値そのものにかかる制約（「金額は0以上」「承認者は申請者の上長」など）、生成経路の閉じ込め、外界依存は、仕様DSLでは意図的にコメントに預けられている。読み合わせの相手（業務担当者・生成AI）が読み通せる分量に収めるためである。

Souther の役割は、この**コメントに預けられた三つを実行可能にすること**にある。

| 仕様DSLがコメントで表すもの | Souther で実行可能にする手段 |
| --- | --- |
| 値の制約（`// 0以上`, `// 承認者は上長`） | `invariant` |
| 「この値は検証済みである」という不変条件 | 生成経路の閉じ込め（`decoder` / `constructs`） |
| `// 依存: 製品カタログ` / `// 副作用: メール送信` | `required behavior`（Java実装を注入） |

仕様DSLの構造（区別・必須・複数性・状態遷移・全域性）はそのまま Souther の型に写り、値制約と外界依存が Souther で型と注入に格上げされる。「構造は型で、値制約はテストと実装の型で、外界依存は注入で守る」という SMDD の分業を、実装モデル側で引き受ける言語である。

### 1.1 対応の原則

仕様DSLの各構成要素は、次のように Souther へ写る。

| 仕様DSL | Souther |
| --- | --- |
| `data 金額 = 整数 // 0以上` | `data 金額 { value: Int  invariant value >= 0 }` |
| `data X = A OR B`（A・Bは名前付きdata） | `data X = A \| B`（直和、アームは既存の名前付きdata） |
| `data 立替 = 単位型` | `data 立替`（フィールドを持たないdata） |
| `data X = A AND b`（Aはレコード、bは追加項目） | `data X { include A  b: B }`（フィールド合成） |
| `List<T>` / `T?` | `List<T>` / `T?` |
| `behavior f = In -> Out`（Outが複数の成功状態） | `behavior f(...) -> Out1 \| Out2`（素の直和） |
| `behavior f = In -> Out OR 失敗` | `behavior f(...) -> Result<Out, 失敗>`（失敗型は `error` 宣言） |
| `f >> g` | `f >> g` |
| `// 依存` / `// 副作用` | `required behavior` |
| `// 事前条件` | `require ... else` |

### 1.2 外界との境界

Souther は外界そのものを扱わない。境界はプリミティブ値・コレクションと Decoder / Encoder で表す。

```text
外界
  ↓
プリミティブ値・コレクション
  ↓ Decoder
内部data
  ↓ behavior
内部data
  ↓ Encoder
プリミティブ値・コレクション
  ↓
外界
```

JVMは、生成された型と振る舞いをJavaプログラムから利用可能にする配布基盤として使う。

---

## 2. 設計原則

### 2.1 dataの生成経路を閉じる

ある `data T` の値を生成できるのは、次のいずれかに限る。

- `T` の Decoder
- `constructs T` を宣言した behavior
- コンパイラ生成コード

戻り値型に `T` を書いただけの通常の behavior は `T` を生成できない。これは、仕様DSLで「検証済みメールアドレスは検証済みであることが型で保証される」（parse-don't-validate）と述べた不変条件を、実装で破れないようにするための規則である。

### 2.2 不変条件は生成時に必ず検査する

`data` に宣言した不変条件は、すべての生成経路で検査する。不変条件に違反する値は返さない。

invariant を持つ data を構築する behavior と Decoder は、生成失敗を表現できる文脈でなければならない（9.4）。

### 2.3 外部表現と内部dataを分離する

Decoder は外部表現から内部dataを生成し、Encoder は内部dataを外部表現へ変換する。外部表現を内部dataへ直接キャストしてはならない。

### 2.4 behaviorを第一級の合成単位とする

内部計算、外界依存、検証、変換はすべて behavior として表現し、`>>` で逐次合成する。behavior は所属（クラス）を持たないトップレベルの入出力関係である。これは仕様DSLの `behavior` が責務配置を後回しにできる性質（クラス図と違い、操作を型に所属させない）をそのまま保つためである。

### 2.5 外界依存をrequired behaviorとして宣言する

DB、HTTP、ファイル、時刻取得、ID生成などの外界依存は言語内で実装しない。仕様DSLで `// 依存:` や `// 副作用:` として注記したものが、これにあたる。言語内では型だけを宣言する。

```text
required behavior findMember(id: MemberId) -> Result<Member, FindMemberError>
```

実装はJava側から注入する。依存（外部を読むだけ）と副作用（外部を変える）の区別は、required behavior の用途を示すドキュメントであり、値の合成規則には影響しない。

### 2.6 失敗を値として表現する

業務上の失敗（**予期する失敗**）は例外ではなく `Result` として返す。メモリ不足やネットワーク断のような**予期しない失敗**は型に並べない。これは仕様DSLが出力の `OR` に業務上の失敗だけを並べ、実装都合の失敗を型に持ち込まない線引きと同じである。

複数の独立した検証エラーは、Decoder 内部で集積する。

### 2.7 Java相互運用は非対称とする

Javaから生成物を利用できる。Souther から任意のJava APIを直接呼び出すことはできない。

```text
Java   -> Souther の生成物     許可
Souther -> 任意のJava API       禁止（required behavior 経由でのみ外界に触れる）
```

---

## 3. 対象外

MVPでは次を実装しない。

- 任意のJava API呼び出し
- 可変状態
- 例外
- null
- スレッド
- 非同期処理
- 継承
- structural intersection types（`A & B`。理由は8.6）
- リフレクション
- マクロ
- 型クラス
- 高階種
- 依存型
- SMTによる証明
- 停止性証明
- 独自VM
- パッケージマネージャ
- REPL

外界依存の実装はJava側へ委譲するため、Souther自身はDB、HTTP、ファイルなどのAPIを持たない。

---

## 4. ソースファイルとモジュール

拡張子は `.mdl` とする。1ファイルにつき1モジュールを定義する。

```text
module example.businesstrip
```

モジュール宣言などのキーワードと標準ライブラリは英語だが、ユーザー定義の識別子（型・フィールド・behavior）はUnicodeを許容する。仕様DSLが日本語で書かれているため、業務語彙をそのまま識別子にできる。

```text
data 出張申請 = 申請準備中 | 提出済み | 事前承認待ち | 事前承認済み
```

公開要素は `exposing` で列挙する。

```text
module example.businesstrip exposing {
    出張申請,
    出張申請.decoder,
    出張申請を提出する,
    事前承認する
}
```

import は明示的に列挙する。ワイルドカードimportは禁止する。循環importはコンパイルエラーとする。

```text
import example.employee {
    従業員,
    従業員ID
}
```

---

## 5. 予約語

```text
module   import   exposing
data     invariant include
behavior required constructs
error
match    case     as       with
let      if       then     else
require
success  failure
true     false
```

演算子・記号: `=` `|` `>>` `?` `->` `.` `..`

`decoder` / `encoder` / `discriminate` は予約語ではない。外部表現との対応づけは構文ではなく、data の形状から導出する（10章・11章）。

---

## 6. 外部表現

Decoder と Encoder が扱う外部表現を `Raw` と呼ぶ。

```text
Raw =
    Null
  | Bool(Bool)
  | Int(Int)
  | Decimal(Decimal)
  | Text(String)
  | List(List<Raw>)
  | Object(Map<String, Raw>)
```

`Null` は Raw 世界にのみ存在する。内部dataではnullを禁止する。

---

## 7. 組み込み型

### 7.1 プリミティブ型

```text
Bool
Int          // 符号付き64ビット整数
Decimal      // java.math.BigDecimal
String
Date         // 年月日。ISO8601。java.time.LocalDate
DateTime     // 年月日時分秒。ISO8601。java.time.LocalDateTime
```

`Date` / `DateTime` は仕様DSLの `年月日` / `年月日時分秒` に対応する。値の生成は Decoder（ISO8601文字列から）を経る。現在時刻の取得は言語内では行わず、required behavior として宣言する（13章）。

### 7.2 コレクション型

```text
List<T>
Map<String, T>
```

コレクションは不変である。

### 7.3 組み込み代数型と補助型

```text
Option<T>    = Some(T) | None
Result<T, E> = Success(T) | Failure(E)
NonEmptyList<T>            // 空でないリスト。Decoder のエラー集積に使う
Never                      // 値を持たない型。失敗しない Result のエラー型
Unit                       // 値が1つだけの型
```

`Never` は純粋 behavior の自動lifting（14.5）に使う。`Unit` は仕様DSLの `単位型` に対応するが、通常は「フィールドを持たない data」（8.3）で表す。

### 7.4 任意型

任意性は `?` で表す。

```text
data 出張完了 {
    事前承認者: 従業員ID?
}
```

これは次へ脱糖する。

```text
事前承認者: Option<従業員ID>
```

`?` はフィールドの任意性以外には使用しない。

Decoder の外部型は次である。

```text
Decoder<T> = Raw -> Result<T, NonEmptyList<DecodeError>>
```

---

## 8. data

`data` は内部世界の型付きデータを定義する。すべてのフィールドは不変である。

### 8.1 直積data（AND）

仕様DSLの `AND` は直積に写る。フィールド名は業務上の役割で、型は別に指定できる。

```text
// 仕様DSL: data 従業員 = 従業員ID AND 役職 AND 上長ID
data 従業員 {
    id:    従業員ID
    役職:   役職
    上長ID: 従業員ID       // 役割名（上長ID）と型（従業員ID）が異なる
}
```

役割と型を分けて書けることは重要である。仕様DSLでは同じ概念が層ごとに別名を持つ（`承認者ID`（操作の入力）→ `事前承認者ID`（状態に保存）→ `pre_approver_id`（DB列））。`事前承認者: 従業員ID` のように、フィールド名に役割を、型に概念を書ける。

### 8.2 フィールド合成（include）

仕様DSLでは、共通項目を持つ状態が `data 提出済み = 出張申請共通項目 AND 提出日時` のように書かれる。`出張申請共通項目` の全フィールドを持ち、さらに `提出日時` を足す、という意味である。Souther はこれを `include` で表す。

```text
data 出張申請共通項目 {
    申請者:    従業員
    予定費用:   金額
    出張先:    String
    出張目的:   String
    出張開始日: Date
    出張終了日: Date
}

// 仕様DSL: data 提出済み = 出張申請共通項目 AND 提出日時
data 提出済み {
    include 出張申請共通項目     // 共通項目のフィールドを平らに取り込む
    提出日時: DateTime
}
```

`include` の意味を次のように定める。

- 取り込んだフィールドは平らに並ぶ。`提出済み` の値は `x.申請者` のように直接参照できる（`x.共通.申請者` のような入れ子にはならない）。
- 取り込んだ data の invariant も引き継ぐ。`出張申請共通項目` が持つ不変条件は `提出済み` の生成時にも検査される。
- フィールド名が衝突したらコンパイルエラーとする。
- `include` は継承ではない。`提出済み` は `出張申請共通項目` の部分型ではなく、両者に代入互換はない。フィールドを共有するだけである。

共通項目を平らにせず、1つの値として持ち回りたい場合は、通常のフィールドとして入れ子にする（`共通: 出張申請共通項目`）。仕様DSLの `AND` は `include`、明示的な入れ子は通常のフィールド、と使い分ける。

### 8.3 直和data（OR）

仕様DSLの `OR` は直和に写る。**アームは既に定義済みの名前付きdataを参照できる**。出張申請モデルのように、状態それぞれが固有のフィールド・invariant・遷移behaviorを持ち、`OR` で束ねられる構造をそのまま表す。

```text
// 仕様DSL: data 出張申請 = 申請準備中 OR 提出済み OR 事前承認待ち OR 事前承認済み
data 出張申請 =
    申請準備中
  | 提出済み
  | 事前承認待ち
  | 事前承認済み
```

入れ子の直和もそのまま書ける。

```text
data 費用負担区分 = 自社負担 | 先方負担
data 自社負担     = 立替 | 仮払い | 会社カード
```

アームにインラインのレコードを書く糖衣も許す。既存の名前付きdataにする必要がないときに使う。

```text
data Contact =
    EmailContact { email: Email }
  | PhoneContact { phone: PhoneNumber }
```

インライン形は、そのアーム名の名前付きdataを暗黙に定義したのと等価に扱う。

### 8.4 単位data

フィールドを持たないdataは、本文を書かずに宣言する。仕様DSLの `= 単位型` に対応する。

```text
// 仕様DSL: data 立替 = 単位型
data 立替
data 仮払い
data 会社カード
data 先方負担
data 一般社員
```

単位dataは直和のアームとして頻出する（`data 役職 = 管理職 | 一般社員` の `一般社員` など）。

### 8.5 フィールドの可視性

フィールドの**参照**は、同一モジュール内のコードから可能である。behavior は自分の入力dataのフィールドを自由に読める。

フィールドはモジュール外（Java側を含む）へは公開しない。外部に値を渡す場合は、値を返す behavior を定義する。

制約されるのは**参照ではなく生成**である。フィールドを読めることと、その data の値を新たに構築できることは別の権限で、構築は 2.1 の経路に限る。

### 8.6 union types と intersection types

Souther は union types を持つ。次の三つの形で現れる。

- 名前付き直和 `data X = A | B`（sealed。網羅性検査の対象）
- behavior 出力の無名 union `-> 提出済み | 事前承認待ち`（12.2）
- エラー型の無名 union `Result<T, E1 | E2>`（14.2）

一方、structural intersection types（`A & B`。「A でも B でもある値」を表す型演算子）は**持たない**。理由は、Souther が nominal な生成経路閉じ込めの上に立っているからである。`A & B` を型として認めると、その値を誰が構築するのか、どの invariant を検査するのかが決まらず、2.1 と 2.2 が破れる。

仕様DSLの `AND` は「A のフィールドをすべて持ち、さらに足す」であって「A かつ B である」ではない。これは structural intersection ではなく nominal なフィールド合成なので、8.2 の `include`（フィールドを平らに取り込み、invariant も引き継ぎ、部分型にはしない）で過不足なく表せる。intersection types を導入しないのは機能不足ではなく、生成経路を閉じるための設計判断である。

---

## 9. 不変条件

### 9.1 宣言

仕様DSLでコメントとして書かれた値制約を、`invariant` として実行可能にする。

```text
// 仕様DSL: data 金額 = 整数 // 0以上、円単位
data 金額 {
    value: Int

    invariant value >= 0
}

data DateRange {
    start: Date
    end:   Date

    invariant start <= end
}
```

### 9.2 MVPの保証

MVPでは不変条件を実行時に検査する。コンパイラは、dataを生成するすべての経路に不変条件検査を挿入する。`include` で取り込んだ不変条件も同じ経路で検査する。

### 9.3 不変条件で許可／禁止する式

許可: 比較、論理演算、算術演算、フィールド参照、組み込み純粋関数、再帰しない述語behavior（要求集合が空で純粋なもの）。

禁止: Decoder、Encoder、required behavior、dataを生成するbehavior、再帰behavior、非決定的処理。

### 9.4 invariant付きdataの構築は失敗チャネルを持つ

invariant を持つ data を構築する behavior と Decoder は、生成失敗を表現できる文脈でなければならない。

- Decoder: 失敗は `NonEmptyList<DecodeError>` に入る（Decoderの外部型がそもそも Result なので常に満たす）。
- behavior: 戻り値を `Result<T, E>` にし、invariant違反を `E` のいずれかに対応させる。

invariant を持つ data を構築する behavior が非Result（素の戻り値）だと、invariant違反を返す先がなくなる。これはコンパイルエラーとする（22.3）。invariant を持たない data の構築は素の戻り値でよい。

---

## 10. Decoder

### 10.1 役割

Decoder は Raw から内部dataを生成する外部入力境界である。仕様DSLの parse-don't-validate（境界で生データを検証し、成功して初めてドメイン型を構築する）を実装する。

```text
Decoder<T> : Raw -> Result<T, NonEmptyList<DecodeError>>
```

Decoder は Souther の構文には現れない。data のフィールド名と型から既定の Decoder を導出する。dataの定義に書くのは `data` / `invariant` / `behavior` だけでよい。外部表現との対応づけ（キー名・正規化・判別子）をドメイン定義に混ぜないための分担である。既定の導出で足りないときは、境界（Java / Raoh）側でカスタム Decoder を書く（10.4）。

### 10.2 導出規則

既定の Decoder は次の規約で data の形状から導出する。

- JSONキーはフィールド名に一致する。Object の各フィールドを同名キーから読む。
- 単一プリミティブフィールドの data は newtype として扱い、Object ではなく裸のプリミティブ（Text / Int）から読む。
- ネストした名前付きdataは、そのdataの導出Decoderで再帰的に読む。
- `List<T>` は要素ごとに `T` の導出Decoderで読む。
- 構築時に invariant を検査し、違反は `DecodeError` になる。Decoder の外部型がそもそも Result なので、失敗チャネルは常に満たす（9.4）。
- 独立したフィールドのエラーは集積する（Applicative、15章）。

```text
// value ひとつだけの data は裸の整数から読む（newtype）
data 金額 {
    value: Int
    invariant value >= 0
}
// 導出Decoder: Int(n) -> 金額 { value: n }（invariant 検査つき）

// 複数フィールドの data は Object から、フィールド名＝キーで読む
data 従業員 {
    id:    従業員ID
    役職:   役職
    上長ID: 従業員ID
}
// 導出Decoder: { "id": ..., "役職": ..., "上長ID": ... } を各フィールドの導出Decoderで読む
```

構築時の invariant 検査と spread（`..`）はレコードリテラルと同じ意味論に従う（8.2、12.4）。

### 10.3 直和dataの判別

直和dataの導出Decoderは、判別子フィールド `"type"` を見てアームの導出Decoderへ振り分ける。タグはアーム名である。

```text
data 出張申請 =
    申請準備中 | 提出済み | 事前承認待ち | 事前承認済み
// 導出Decoder: "type" を見て "申請準備中" => 申請準備中, "提出済み" => 提出済み, ... へ振り分ける
```

判別子キーやタグを業務固有のものにしたいとき（`"status"` を見て `"draft"` / `"submitted"` に振り分ける、永続表現の `representation_version` でバージョンを読み分けるなど）は、外部表現の都合であって data の形状からは導けない。境界側のカスタム Decoder で行う（10.4）。

### 10.4 カスタム境界コーデック

既定の導出で足りないとき（別キー名、正規化、既定値補完、業務固有の判別子、永続表現のバージョン分岐）は、Java 側で Raw を組み立て、data の（invariantを検査する）構築を呼ぶ Decoder を書く。24章の `JdbcFindMember` が `Member.decoder()` を呼ぶのがこの形である。外部表現の変換は境界に閉じ、ドメイン定義には持ち込まない。

### 10.5 Decoderエラー

```text
data DecodeError {
    path:    List<PathElement>
    code:    String
    message: String
}

data PathElement = Field(String) | Index(Int)
```

### 10.6 Raoh連携

導出した Decoder は直接 Raoh API へ変換せず、Decoder IR を経由する。

```text
Source -> Typed AST -> (導出) -> Decoder IR -> Raoh backend
```

Decoder IR のノード:

```text
ReadText  ReadInt  ReadDecimal  ReadBool  ReadDate  ReadDateTime
ReadList  ReadObject
Field  OptionalField  Index
Map  FlatMap  Zip  Alternative  Discriminate  Refine  Succeed  Fail
```

`Discriminate` は 10.3 の判別振り分けに対応する。Raoh固有型は言語の公開APIに露出させない。

---

## 11. Encoder

### 11.1 役割

Encoder は内部dataを Raw へ変換する全域関数である。

```text
Encoder<T> : T -> Raw
```

Decoder と同じく、Encoder も Souther の構文には現れない。data のフィールド名と型から既定の Encoder を導出する（10.1）。

### 11.2 導出規則

既定の Encoder は Decoder と対称の規約で導出する。

- 単一プリミティブフィールドの data は newtype として、裸のプリミティブ（Text / Int）へ書く。
- 複数フィールドの data は Object へ書く。キーはフィールド名、値は各フィールドの導出Encoderで変換する。`include` したフィールドも平らに並ぶ。
- ネストした名前付きdataは、そのdataの導出Encoderで変換する。
- `List<T>` は要素ごとに `T` の導出Encoderで変換する。
- 直和dataは判別子フィールド `"type"` にアーム名のタグを付け、アームの導出Encoderで変換する。

```text
data 金額 { value: Int  invariant value >= 0 }
// 導出Encoder: 金額 -> Int(self.value)（裸の整数）

data 提出済み {
    include 出張申請共通項目
    提出日時: DateTime
}
// 導出Encoder: 提出済み -> { "申請者": ..., "予定費用": ..., ..., "提出日時": ... }
```

キー名や表現を外部仕様に合わせたいときは、境界側でカスタム Encoder を書く（10.4）。

### 11.3 往復則

次を推奨する（要求はしない）。

```text
decode(encode(value)) == Success(value)
```

`encode(decode(raw)) == raw` は要求しない。Decoder は正規化・既定値補完・余剰項目削除を行いうるためである。

---

## 12. behavior

behavior は内部dataに対する振る舞いを定義する。所属を持たないトップレベルの入出力関係である。

### 12.1 純粋behavior

仕様DSLの入力 `AND` は引数リストに写る。パラメータは常に `名前: 型` で書く。

```text
// 仕様DSL: behavior 事前承認要否を判定する = 予定費用 AND 申請者 AND 費用負担区分 -> 事前承認理由リスト
behavior 事前承認要否を判定する(
    予定費用:    金額,
    申請者:      従業員,
    費用負担区分: 費用負担区分
) -> List<事前承認理由> {
    ...
}
```

### 12.2 出力のORを分類する — 多分岐成功と成功＋失敗

仕様DSLの出力 `OR` は二役を持つ。書き写すとき、各アームを成功か失敗かに分類する。失敗型は宣言時に `error` を付けるので、この分類は一度で済み、コンパイラが検査できる（8.3 の素の直和に `error` 型を混ぜたらコンパイルエラー、22.13）。

失敗型を宣言する。

```text
error 承認権限なし
error 却下理由なし
error 不正な実費用
```

**すべて成功（分岐する次状態）** は素の直和で返す。

```text
// 仕様DSL: behavior 出張申請を提出する = 申請準備中 -> 提出済み OR 事前承認待ち
behavior 出張申請を提出する(申請: 申請準備中) -> 提出済み | 事前承認待ち
    constructs 提出済み, 事前承認待ち
{
    ...
}
```

**成功＋業務失敗** は Result で返す。失敗が複数あれば直和にする。

```text
// 仕様DSL: behavior 事前承認する = 事前承認待ち AND 承認者ID -> 事前承認済み OR 承認権限なし
behavior 事前承認する(
    申請:    事前承認待ち,
    承認者ID: 従業員ID
) -> Result<事前承認済み, 承認権限なし>
    constructs 事前承認済み
{
    // 事前条件: 承認者IDが申請者の上長IDと一致すること
    require 承認者ID == 申請.申請者.上長ID
        else 承認権限なし

    事前承認済み { ..申請, 事前承認日時: 現在時刻(), 事前承認者: 承認者ID }
}
```

入力が直和のこともある（`(提出済み OR 事前承認済み) AND ...`）。パラメータ型に直和を書き、内部で `match` する。名前付きの直和にしておくと読みやすい。

```text
// 仕様DSL: behavior 出張を完了する = (提出済み OR 事前承認済み) AND 実費用 AND 出張報告 -> 出張完了 OR 不正な実費用 OR 出張報告なし
behavior 出張を完了する(
    申請:   提出済み | 事前承認済み,
    実費用:  金額,
    出張報告: String
) -> Result<出張完了, 不正な実費用 | 出張報告なし>
    constructs 出張完了
{ ... }
```

### 12.3 data生成権限（constructs）

dataを生成する behavior は `constructs` を宣言する。これは「生成してよい」という権限の明示であり、依存とは別物なので、本体から推論せず必ず書く。

```text
behavior applyChange(input: ChangeEmailRequest) -> Member
    constructs Member
{
    input.member with { email: input.email }
}
```

複数dataを生成する場合は列挙する: `constructs Member, AuditRecord`。

次はdata生成とみなす: レコードリテラル（`型名 { ... }`）、直和dataのコンストラクタ、`with` 更新、単位dataの構築、新しいdataを要素に含むコレクション生成。既存値をそのまま返す場合は生成とみなさない。

required behavior への依存（`requires`）は宣言しない。コンパイラが本体から要求集合を推論して型に載せる（13.5、14.4）。生成権限（`constructs`）だけを書き、依存は書かない、が原則である。

### 12.4 レコードリテラル・spread・with

構築は、構築する型名を書いたレコードリテラルで行う。

```text
事前承認済み {
    申請者:      申請.申請者,
    予定費用:     申請.予定費用,
    提出日時:     申請.提出日時,
    事前承認日時:  現在時刻(),
    事前承認者:   承認者ID
    // ... 共通項目の残りも列挙
}
```

同名フィールドをまとめて埋めたいときは spread `..src` を使う。`src` の同名フィールドをコピーし、残りを明示する。`src` の余分なフィールドは無視し、`型名` に足りないフィールドがあればコンパイルエラーとする。

```text
// 申請（事前承認待ち）の共通項目・提出日時をコピーし、2つを足す
事前承認済み { ..申請, 事前承認日時: 現在時刻(), 事前承認者: 承認者ID }
```

`with` は同じ型の一部フィールドを差し替える糖衣である。`x with { f: v }` は `TypeOf(x) { ..x, f: v }` と等しい。

いずれも生成権限（`constructs`）を要する。構築点に型名が出るので、権限検査もフィールド検査もその場で済む。

---

## 13. required behavior

required behavior は、言語内で型だけを宣言し、実装を外部から提供する振る舞いである。仕様DSLの `// 依存:` と `// 副作用:` に対応する。

### 13.1 宣言

```text
required behavior findMember(id: MemberId) -> Result<Member, FindMemberError>

required behavior 現在時刻() -> DateTime      // 仕様DSL: // 依存: 時刻取得
```

required behavior は本体を持たない。

### 13.2 典型用途

DBクエリ、HTTP呼び出し、ファイル読み書き、時刻取得、ID生成、メッセージ送信。これらの具体的なAPIは言語コアへ持ち込まない。

### 13.3 Java interface生成

```text
required behavior findMember(id: MemberId) -> Result<Member, FindMemberError>
```

から概念的に次を生成する。

```java
public interface FindMember {
    Result<Member, FindMemberError> apply(MemberId id);
}
```

Java側で実装する（24章）。

### 13.4 Java実装の規則

Java実装は、宣言済みの失敗を `Result.Failure` として返す。DB例外、HTTP失敗などを言語側へ例外として漏らしてはならない。VM障害や実装バグは保証対象外とする。

### 13.5 集合不変条件は入力データで渡す

1件のオブジェクトでは判定できない不変条件（日程重複、一意性など）は、隠れた依存としてではなく、判定に必要な集合を入力データとして明示する。既存申請を読む副作用はワークフローの境界（required behavior）に置く。

```text
// 仕様DSL: behavior 日程重複を確認する = 同一申請者の出張申請リスト AND 申請準備中 -> 申請準備中 OR 日程重複
error 日程重複

behavior 日程重複を確認する(
    同一申請者の出張申請リスト: List<出張申請>,
    申請:                    申請準備中
) -> Result<申請準備中, 日程重複> { ... }

required behavior 同一申請者の申請を読む(申請者: 従業員ID) -> List<出張申請>
```

`日程重複を確認する` は純粋な判断のまま保たれ、副作用は外側の required behavior に寄る。これは SMDD の DDDトリレンマの結論（純粋性はレイヤー分離ではなく、どの関数が副作用を持つかを明示すること）に対応する。

### 13.6 requirement propagation

required behavior を参照する behavior は、その要求を引き継ぐ。要求集合は宣言せず、コンパイラが本体から推論する。

```text
behavior loadAndConvert = findMember >> toView
```

このbehaviorは `findMember` の実装を要求する。複数のrequired behaviorを合成した場合、要求集合は和集合になる。要求が満たされないまま呼び出そうとすると E1601 になる。

---

## 14. behavior合成

### 14.1 逐次合成 `>>`

前段の成功出力の型が後段の入力型に一致するとき、`>>` で合成する。`>>` は値ではなく behavior どうしをつなぐ。

```text
behavior handle =
    Email.decoder
    >> findMember
    >> toResponse
    >> MemberResponse.encoder
```

### 14.2 合成規則

```text
f : A -> B            g : B -> C            f >> g : A -> C
f : A -> Result<B,E1> g : B -> C            f >> g : A -> Result<C, E1>
f : A -> B            g : B -> Result<C,E2> f >> g : A -> Result<C, E2>
f : A -> Result<B,E1> g : B -> Result<C,E2> f >> g : A -> Result<C, E1 | E2>
```

`f` が失敗した場合、`g` は実行しない（Railway Oriented）。エラー型は直和として推論し、`(E1 | E2) | E3` は `E1 | E2 | E3` に正規化する。同一のエラー型は重複を畳む。

### 14.3 素の直和を返す段の合成

`>>` は特別扱いをしない。前段の成功出力が素の直和（`提出済み | 事前承認待ち` など）でも、**後段の入力型がその直和を受け取れれば合成できる**。後段が union をパラメータ型に取っていれば型が合う。

```text
// 後段が両方の状態を受け取れるなら、そのまま >> でつなげる
behavior 次工程(申請: 提出済み | 事前承認待ち) -> ...
behavior 一連の流れ = 出張申請を提出する >> 次工程
```

後段が片方の状態しか受け取らない、あるいは状態ごとに違う処理へ分岐したいときは、`>>` ではなく `match` で枝を開く。

```text
match 出張申請を提出する(申請) {
    case 提出済み as s   => ...
    case 事前承認待ち as p => ...
}
```

型が合わない合成はいずれも E1701 になる。素の直和専用のエラーは設けない。

### 14.4 required behaviorを含む合成

behaviorの内部表現を次として捉える。

```text
Behavior<Requirements, Input, Output, Error>
```

合成規則:

```text
f : Behavior<R1, A, B, E1>
g : Behavior<R2, B, C, E2>
f >> g : Behavior<R1 ∪ R2, A, C, E1 | E2>
```

`Requirements` は推論され、宣言しない。実装が内部かJava側かは、値の合成規則に影響しない。

### 14.5 純粋behaviorの自動lifting

```text
A -> B
```

は、失敗可能なパイプライン内では内部的に `A -> Result<B, Never>` へ持ち上げられる。利用者が明示的に `Success` で包む必要はない。同様に、`Result<T, E>` を返す behavior の本体で、成功値をそのまま最後の式に置けば `Success` に包まれる。明示したいときは `success 値` / `failure 値` と書く。

---

## 15. Decoderのエラー集積とbehaviorの失敗伝播

Decoder内部では、独立した入力項目のエラーを集積する（Applicative）。導出された Object の Decoder は、各フィールドを同名キーから読み、独立したフィールドの失敗をすべて集める。

```text
{ "name": ..., "email": ..., "age": ... }
// name / email / age を各フィールドの導出Decoderで読み、独立した失敗をすべて集積する
```

導出Decoderのフィールド取得は互いに独立で、失敗をすべて集める。カスタム境界コーデック（10.4）で、ある取得が先行の結果に依存する場合は、その箇所から逐次（monadic）になり、先行が失敗すれば後続は実行しない。

一方、behaviorの `>>` は逐次合成であり、最初の失敗で停止する。

```text
Decoder内部    独立エラーを集積（Applicative）
behavior >>    失敗時に後続を停止（monadic）
```

Decoderの外部型は `Result<T, NonEmptyList<DecodeError>>` なので、通常のbehaviorと `>>` で接続できる。

---

## 16. 制御構文

### 16.1 let

```text
let normalized = trim(input)
```

再代入は禁止する。

### 16.2 if

```text
if condition then expression1 else expression2
```

両分岐の型は一致しなければならない。

### 16.3 match

```text
match 出張申請 {
    case 申請準備中 as d   => ...
    case 提出済み as s     => ...
    case 事前承認待ち as p  => ...
    case 事前承認済み as a  => ...
}
```

インラインレコードのアームはフィールドを分解束縛できる。

```text
match contact {
    case EmailContact { email } => ...
    case PhoneContact { phone } => ...
}
```

直和dataに対するmatchは網羅的でなければならない。

### 16.4 require

```text
require amount <= balance
    else InsufficientBalance
```

`require` は Decoder または `Result` を返す behavior 内で使う。Decoder内では `else` の識別子が `DecodeError.code` になる。behavior内では `else` の値が `Result.Failure` の中身になる（`error` 宣言した型の値を置く）。

### 16.5 success / failure

```text
success value
failure error
```

それぞれ `Result.Success` と `Result.Failure` を生成する。`Result` を返す behavior では成功値を最後の式に置けば自動で `Success` に包まれるので、`success` は明示したいときだけ使う。

---

## 17. 純粋性

通常behaviorは純粋である。次を禁止する。

可変変数、フィールド代入、static mutable field、乱数、現在時刻、環境変数、ファイル、ネットワーク、スレッド、例外、リフレクション。

外界依存はrequired behaviorとして宣言する。

---

## 18. 標準ライブラリ

### 18.1 String

```text
length  trim  lowercase  uppercase  contains  startsWith  endsWith  substring  concat
```

### 18.2 Int

```text
add  subtract  multiply  divide  remainder  compare
```

ゼロ除算は `Result<Int, DivisionByZero>` を返す。

### 18.3 Decimal

```text
add  subtract  multiply  divide  compare
```

除算は丸め方式を明示する。

### 18.4 List

```text
map  filter  fold  all  any  length  get
```

`get` は `Option<T>` を返す。

### 18.5 Map

```text
get  containsKey  keys  values
```

`get` は `Option<V>` を返す。

### 18.6 Decoderコンビネータ

Souther の構文には現れない。導出（10章）が内部で用い、境界のカスタムコーデック（10.4）が Java 側から使うランタイムAPIである。

```text
field  optionalField  index  list
string  int  decimal  bool  date  dateTime
```

---

## 19. JVM出力

### 19.1 対象JDK

生成物はJava 21以上で動作することを目標とする。コンパイラ実装はJava 25を使用してよい。Unicode識別子はJavaの識別子規則に沿って生成する（必要ならエスケープ／ローマ字化はbackendの責務）。

### 19.2 直積data

private constructorを持つfinal classとして生成する。`include` したフィールドは、生成先のクラスに平らに展開する。

```text
public final class 提出済み {
    private final 従業員 申請者;      // include 出張申請共通項目 由来
    private final 金額 予定費用;       // include 出張申請共通項目 由来
    // ...
    private final LocalDateTime 提出日時;

    private 提出済み(...) { ... }
}
```

### 19.3 直和data

sealed interface と final 実装クラスとして生成する。アームが名前付きdataなら、その final class が sealed interface を implements する。単位dataはフィールドなしの final class（またはシングルトン）として生成する。`error` 宣言した型も data として同様に生成する（失敗型であることはコンパイラ内の分類で、実行時表現は通常のdataと同じ）。

### 19.4 Decoder / Encoder

公開dataには取得APIを生成する。

```text
public static Decoder<出張申請> decoder()
public static Encoder<出張申請> encoder()
```

具体的な Decoder 実装は Raoh backend が生成する。

### 19.5 behavior

トップレベルbehaviorはJavaの関数オブジェクトまたはstatic APIとして生成する。required behaviorを含まないbehaviorは直接呼び出せる。required behaviorを含むbehaviorには、依存実装を束縛するAPIを生成する。

```text
var handle = Handle.bind(new JdbcFindMember(dataSource));
```

生成されるクラス名は behavior 名から導く（先頭を大文字化）。

### 19.6 生成権限の露出防止

生成権限は公開Java APIとして露出させない。プロトタイプでは、dataのconstructorはprivate、内部生成メソッドはpackage-private、生成behaviorを同一生成パッケージへ配置する。Reflection、Unsafe、バイトコード改変は保証対象外とする。

---

## 20. コンパイラ構成

```text
Source -> Lexer -> Parser -> Untyped AST -> Name Resolution -> Typed AST
  -> Construction Capability Check
  -> Requirement Analysis        (要求集合の推論)
  -> Purity Check
  -> Exhaustiveness Check
  -> Invariant Compilation
  -> Domain IR
  -> Decoder IR / Encoder IR / Behavior IR
  -> ClassFile Backend -> .class（バイトコード直接生成）
```

バックエンドは Class-File API（`java.lang.classfile`）で `.class` を直接生成する。javac は経由しない。Domain IR を Java 構文へ依存させないので、将来 Java ソース生成など別のbackendへ差し替えられる。

---

## 21. 中間表現

### 21.1 Domain IR

```text
Module  DataDefinition  ProductData  SumData  UnitData  Include  Field  Invariant
DecoderDefinition  EncoderDefinition  BehaviorDefinition  RequiredBehaviorDefinition
ConstructionCapability  RequirementSet  ErrorType  Expression  Pattern
```

`SumData` のアームは名前付きdataへの参照、またはインライン定義を持つ。`Include` は取り込むdataと展開したフィールド・invariantを持つ。`ErrorType` は `error` 宣言で印づけられた失敗型を区別する。

### 21.2 Behavior IR

```text
InputType  OutputType  ErrorType  RequiredBehaviors  ConstructedData  Body
```

`OutputType` は単一型・直和・Result のいずれか。`RequiredBehaviors` は本体から推論する。

### 21.3 Decoder IR

```text
ReadText  ReadInt  ReadDecimal  ReadBool  ReadDate  ReadDateTime
ReadList  ReadObject  Field  OptionalField  Index
Map  FlatMap  Zip  Alternative  Discriminate  Refine  Succeed  Fail
```

---

## 22. コンパイルエラー

### 22.1 dataの直接構築

```text
error E1001:
Data `Email` cannot be constructed directly.
Use `Email.decoder` or a behavior declared with `constructs Email`.
```

### 22.2 生成権限不足

```text
error E1002:
Behavior `changeEmail` constructs `Member`
but does not declare `constructs Member`.
```

### 22.3 invariant違反の失敗チャネルがない

```text
error E1003:
Behavior `applyChange` constructs `金額`, which has an invariant,
but its return type is not a Result.
Return `Result<金額, E>` so an invariant violation has an error case.
```

### 22.4 include のフィールド名衝突

```text
error E1004:
Field `提出日時` from `include 出張申請共通項目` conflicts with a field of `提出済み`.
```

### 22.5 spread のフィールド不足

```text
error E1005:
Record literal `事前承認済み { ..申請, ... }` is missing field `事前承認者`.
`..申請` does not provide it; supply it explicitly.
```

### 22.6 不変条件型不一致

```text
error E1101:
Invariant expression must have type Bool. Found: String
```

### 22.7 非網羅match

```text
error E1201:
Non-exhaustive match for data `出張申請`.
Missing case: 事前承認済み
```

### 22.8 null使用

```text
error E1301:
`null` is not part of the language. Use an optional field with `?`.
```

### 22.9 例外使用

```text
error E1302:
Exceptions are not supported. Return Result<T, E>.
```

### 22.10 任意Java呼び出し

```text
error E1401:
Calling arbitrary JVM methods is not allowed.
Declare a required behavior and provide its implementation from Java.
```

### 22.11 循環import

```text
error E1501:
Cyclic module dependency detected.
```

### 22.12 required behavior未束縛

```text
error E1601:
Behavior `handle` requires an implementation of `findMember`.
```

### 22.13 error型を成功位置に置いた

```text
error E1801:
`承認権限なし` is declared with `error` and can only appear in the error
position of a Result. It cannot be a success arm of `提出済み | 承認権限なし`.
Return `Result<提出済み, 承認権限なし>` instead.
```

### 22.14 behavior合成型不一致

```text
error E1701:
Cannot compose behaviors.
Left output:  MemberId
Right input:  FindMemberRequest
```

---

## 23. サンプル：出張申請モデル

仕様DSL（`chapter10/types-and-states.dsl` ほか）を Souther へ書き写した例。各定義に対応する仕様DSLをコメントで併記する。

```text
module example.businesstrip exposing {
    出張申請,
    出張申請.decoder,
    出張申請を提出する,
    事前承認する
}

// --- 値の型（仕様DSLの単純な値＋制約コメント → invariant）---
// data 金額 = 整数 // 0以上、円単位
data 金額 {
    value: Int
    invariant value >= 0
}

// data 従業員ID = 文字列 // 空文字列ではない
// value ひとつの newtype。導出Decoderが裸のテキストから読み、invariant を検査する。
// 正規化（trim など）が要るなら境界側のカスタム Decoder で行う（10.4）。
data 従業員ID {
    value: String
    invariant length(value) > 0
}

// --- 単位ケースを含む入れ子の直和 ---
// data 役職 = 管理職 OR 一般社員
data 役職 = 管理職 | 一般社員
data 管理職 { level: Int  invariant level >= 1 && level <= 5 }
data 一般社員

// data 費用負担区分 = 自社負担 OR 先方負担 / data 自社負担 = 立替 OR 仮払い OR 会社カード
data 費用負担区分 = 自社負担 | 先方負担
data 自社負担     = 立替 | 仮払い | 会社カード
data 立替
data 仮払い
data 会社カード
data 先方負担

// --- 直積（役割名≠型名）---
// data 従業員 = 従業員ID AND 役職 AND 上長ID
data 従業員 {
    id:    従業員ID
    役職:   役職
    上長ID: 従業員ID
}

data 出張申請共通項目 {
    申請者:    従業員
    予定費用:   金額
    出張先:    String
    出張目的:   String
    出張開始日: Date
    出張終了日: Date
}

// --- 状態の直和（アーム＝名前付きdata、共通項目は include で平らに取り込む）---
// data 出張申請 = 申請準備中 OR 提出済み OR 事前承認待ち OR 事前承認済み
// 導出Decoderは判別子 "type" を見て、タグ＝アーム名（"申請準備中" など）で振り分ける。
// DBの "status"／英語タグ（"draft" など）で読みたいときは境界側のカスタム Decoder で行う（10.4）。
data 出張申請 =
    申請準備中 | 提出済み | 事前承認待ち | 事前承認済み

data 申請準備中 { include 出張申請共通項目 }
data 提出済み   { include 出張申請共通項目  提出日時: DateTime }

data 事前承認理由 = 高額出張 | 権限不足 | 先方費用負担
data 高額出張    { 基準金額: 金額 }
data 権限不足    { 役職: 役職 }
data 先方費用負担

data 事前承認待ち {
    include 出張申請共通項目
    提出日時:         DateTime
    事前承認理由リスト: List<事前承認理由>
}

data 事前承認済み {
    include 出張申請共通項目
    提出日時:    DateTime
    事前承認日時: DateTime
    事前承認者:  従業員ID
}

// 失敗型は error で印づけ（素の直和に混ぜたら E1801）
error 承認権限なし

required behavior 現在時刻() -> DateTime

// behavior 事前承認要否を判定する = 予定費用 AND 申請者 AND 費用負担区分 -> 事前承認理由リスト
behavior 事前承認要否を判定する(
    予定費用:    金額,
    申請者:      従業員,
    費用負担区分: 費用負担区分
) -> List<事前承認理由>
    constructs 高額出張, 権限不足, 先方費用負担
{
    ...
}

// behavior 出張申請を提出する = 申請準備中 -> 提出済み OR 事前承認待ち（両方成功 → 素の直和）
behavior 出張申請を提出する(申請: 申請準備中) -> 提出済み | 事前承認待ち
    constructs 提出済み, 事前承認待ち
{
    ...
}

// behavior 事前承認する = 事前承認待ち AND 承認者ID -> 事前承認済み OR 承認権限なし（成功＋失敗 → Result）
// 事前条件: 承認者IDが申請者の上長IDと一致すること
// requires は書かない。現在時刻() を呼ぶので要求集合はコンパイラが推論する
behavior 事前承認する(
    申請:    事前承認待ち,
    承認者ID: 従業員ID
) -> Result<事前承認済み, 承認権限なし>
    constructs 事前承認済み
{
    require 承認者ID == 申請.申請者.上長ID
        else 承認権限なし

    事前承認済み { ..申請, 事前承認日時: 現在時刻(), 事前承認者: 承認者ID }
}
```

`事前承認する` の推論型:

```text
事前承認する :
    Behavior<{現在時刻}, (事前承認待ち, 従業員ID), 事前承認済み, 承認権限なし>
```

`..申請` は `事前承認待ち` の共通項目と `提出日時` を同名でコピーし、`事前承認理由リスト`（`事前承認済み` にはない）は無視され、`事前承認日時` と `事前承認者` を足して構築する。

---

## 24. Java側実装例

```java
public final class JdbcFindMember implements FindMember {
    private final DataSource dataSource;

    public JdbcFindMember(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Result<Member, FindMemberError> apply(MemberId id) {
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement(
                 "select id, email, display_name from member where id = ?")) {

            statement.setString(1, MemberId.text(id));

            try (var resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Result.failure(new FindMemberError.NotFound());
                }
                var raw = Raw.object(Map.of(
                    "id",          Raw.text(resultSet.getString("id")),
                    "email",       Raw.text(resultSet.getString("email")),
                    "displayName", Raw.text(resultSet.getString("display_name"))));

                return Member.decoder().decode(raw)
                    .mapError(FindMemberError.InvalidStoredData::new);
            }
        } catch (SQLException e) {
            return Result.failure(new FindMemberError.DatabaseUnavailable());
        }
    }
}
```

合成behaviorへの束縛:

```java
var handle = Handle.bind(new JdbcFindMember(dataSource));
var result = handle.apply(rawInput);
```

---

## 25. MVP実装範囲

### 25.1 必須

`.mdl`読み込み、Lexer、Parser、AST、モジュール、import、`data`（直積・直和・単位・アーム参照・`include`）、`?`、`invariant`、形状からの `decoder` / `encoder` 導出（newtype・JSONキー＝フィールド名・判別子 `"type"`／アーム名タグ）、`behavior`、`required behavior`、`constructs`、`error` 宣言と成功位置検査、要求集合の推論、レコードリテラルと spread と `with`、素の直和出力とResult出力の区別、`>>`、`match`、`let`、`if`、`require`、`success`、`failure`、`Option`、`Result`、`NonEmptyList`、null禁止、例外禁止、網羅性検査、生成権限検査、invariant失敗チャネル検査、behavior合成型検査、ClassFile バイトコード生成、Java interface生成、Raoh Decoder生成、Unicode識別子、単体テスト。

### 25.2 後回し

Javaソース生成backend（人間可読な生成コード）、増分コンパイル、IDEプラグイン、LSP、ソースマップ、ユーザー定義高階behavior、高度な型推論、静的不変条件証明、structural intersection types、手書き decoder / encoder 構文（別キー名・正規化・業務固有の判別子は境界のカスタムコーデックで扱う）、JSON Schema生成、Wasm出力、JavaScript出力、非同期required behavior。

---

## 26. 実装順序

1. Lexer（Unicode識別子を含む）
2. Parser
3. AST
4. `data`（直積）
5. `include`・単位data・直和（アーム参照）
6. `?`
7. 型解決
8. ClassFile バイトコード生成
9. デフォルト decoder の導出（newtype / object、JSONキー＝フィールド名）
10. 直和の判別子導出（`"type"` / アーム名タグ）
11. Raoh連携
12. デフォルト encoder の導出
13. behavior（純粋）
14. `constructs` と invariant失敗チャネル検査
15. `error` 宣言と、`Result` / 素の直和出力の区別
16. `>>` と `match`
17. required behavior と要求集合の推論
18. Java interface生成
19. requirement propagation
20. `invariant`（include 由来を含む）
21. match網羅性検査
22. モジュールとimport
23. エラーメッセージ改善

最初の垂直スライス:

```text
data 金額 / 従業員ID  + invariant + 導出decoder/encoder + Raoh + ClassFile生成
```

次のスライス:

```text
data 出張申請（状態の直和、共通項目は include）
  + 導出decoder（判別子 "type" / アーム名タグ）
  + behavior 事前承認する（Result, require, error 承認権限なし, 現在時刻() 依存の推論）
```

---

## 27. MVP受け入れ条件

### 27.1 状態の直和と導出decoderがコンパイルできる

23章の `出張申請`（アーム＝名前付きdata、共通項目は `include`）と、その導出decoder（判別子 `"type"`／タグ＝アーム名）がコンパイルできる。

### 27.2 生成権限不足がコンパイルエラーになる

```text
behavior createMember(email: Email) -> Member {
    Member { email }
}
```

期待するエラー: `Behavior 'createMember' constructs 'Member' but does not declare 'constructs Member'.`

### 27.3 invariant付きdataを非Result behaviorで構築するとエラーになる

invariant を持つ `金額` を構築する behavior の戻り値が `Result` でなければ E1003 になる。

### 27.4 error型を成功位置に置くとエラーになる

`error 承認権限なし` を `-> 提出済み | 承認権限なし` の成功アームに置くと E1801 になる。

### 27.5 required behaviorをJava実装で束縛できる

```java
var handle = Handle.bind(new JdbcFindMember(dataSource));
```

要求集合は宣言せずコンパイラが推論し、未束縛のまま呼ぶと E1601 になる。

### 27.6 Bが失敗した場合にCを実行しない

`A >> B >> C` で B が `Failure` を返した場合、C を呼び出さない。

### 27.7 Decoderエラーを集積できる

複数フィールドが不正な場合、最初の1件だけでなく、すべての独立エラーを返す。

---

## 28. 非機能要件

- コンパイルエラーにソース位置を含める
- 生成する `.class` は標準の検証を通り、Java 21+ から素直に利用できる形にする
- コンパイラ各段階を独立してテスト可能にする
- ASTとDomain IRを分離する
- Raoh依存をJVM backendへ隔離する
- 言語コアをJavaクラスモデルへ依存させない
- required behaviorの要求集合を静的に推論・追跡する
- 生成コードから不変条件の迂回APIを公開しない
- 仕様DSLの1定義が Souther の1定義へ、翻訳を挟まず対応づく

---

## 29. 最重要の意味論

Souther は、dataについて次の二つを追跡する。

```text
どの値がそのdataであるか
どの式がそのdataを生成する権限を持つか
```

behaviorについては次の四つを追跡する。

```text
入力型   出力型   失敗型   必要なrequired behavior集合
```

したがって、behaviorは概念的に次の型を持つ。

```text
Behavior<Requirements, Input, Output, Error>
```

`>>` は、出力値だけでなく、エラー型と required behavior 集合も合成する。

```text
f : Behavior<R1, A, B, E1>
g : Behavior<R2, B, C, E2>
f >> g : Behavior<R1 ∪ R2, A, C, E1 | E2>
```

この規則が、Souther の中心的な合成意味論である。四要素のうち出力型・失敗型は仕様DSLの `-> 出力 OR 失敗` に、要求集合は `// 依存`・`// 副作用` に対応する。書く側が明示するのは入力・出力・失敗（`error` 印）と生成権限（`constructs`）だけで、要求集合はコンパイラが推論する。Souther は、仕様DSLがコメントに預けた値制約・生成経路・外界依存を型と注入に格上げすることで、仕様モデルを実装モデルへ素直に写す。
```
