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
| `data 金額 = 整数 // 0以上` | `data 金額 = { value: Int  invariant value >= 0 }` |
| `data X = A OR B`（A・Bは名前付きdata） | `data X = A \| B`（直和、アームは既存の名前付きdata） |
| `data 立替 = 単位型` | `data 立替`（フィールドを持たないdata） |
| `data X = A AND b`（Aはレコード、bは追加項目） | `data X = { include A  b: B }`（フィールド合成） |
| `List<T>` / `T?` | `List<T>` / `T?` |
| `behavior f = In -> Out`（Outが複数の成功状態） | `behavior f = (...) -> Out1 \| Out2`（素の直和） |
| `behavior f = In -> Out OR 失敗` | `behavior f = (...) -> Out \| 失敗`（失敗もただの data。本線離脱は合成で決まる） |
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

### 2.4 behaviorを合成の単位とする

内部計算、外界依存、検証、変換はすべて behavior として表現し、`>>` で逐次合成する。behavior は所属（クラス）を持たないトップレベルの入出力関係である。これは仕様DSLの `behavior` が責務配置を後回しにできる性質（クラス図と違い、操作を型に所属させない）をそのまま保つためである。

合成の単位であることと、値であることは別である。behavior はどのクラスにも属さないが、値として扱えるわけではない——返すことも、data のフィールドに格納することもできない。引数として渡せるのはブロックだけで、それも渡すことに限る（12.5）。

### 2.5 外界依存をrequired behaviorとして宣言する

DB、HTTP、ファイル、時刻取得、ID生成などの外界依存は言語内で実装しない。仕様DSLで `// 依存:` や `// 副作用:` として注記したものが、これにあたる。言語内では型だけを宣言する。

```text
required behavior findMember = (id: MemberId) -> Member | FindMemberError
```

実装はJava側から注入する。依存（外部を読むだけ）と副作用（外部を変える）の区別は、required behavior の用途を示すドキュメントであり、値の合成規則には影響しない。

### 2.6 業務結果を直和で表現する

業務上の結果はすべてドメインdataの直和として返す。「失敗」は言語の一級概念ではない。ある値がエラーかどうかは、ドメインの世界では二分できるものではなく、合成の局面で決まる。behavior の出力は印の無い直和で、`>>` で下流が消費しなかったアームが本線から外れる（14章）。メモリ不足やネットワーク断のような**予期しない失敗**は型に並べない。仕様DSLが出力の `OR` に業務上の結果だけを並べ、実装都合の失敗を型に持ち込まない線引きと同じである。

複数の独立した検証エラーは、Decoder 内部で集積する（15章）。

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

拡張子は `.sou` とする。1ファイルにつき1モジュールを定義する。

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
match    case     as       with
let      if       then     else
require
true     false
```

演算子・記号: `=` `|` `>>` `?` `->` `=>` `.` `..`

`=>` は `match` のアーム（16.3）とブロック（12.5）で使う。

`decoder` / `encoder` / `discriminate` は予約語ではない。外部表現との対応づけは構文ではなく、data の形状から導出する（10章・11章）。`error` / `success` / `failure` も予約語ではない。失敗は特別な型でも構文でもなく、ただのドメインdataであり、本線から外れるかは合成で決まる（2.6・12章・14章）。

---

## 6. 外部表現

Decoder と Encoder が扱う外部表現は、Souther 自身の型ではなく、境界ライブラリ Raoh の中立値モデル（scalar / List / Map からなる木）である。Souther はドメインの型・invariant・behavior を所有し、外部表現とその解析・生成は縁のライブラリ Raoh に委ねる（2.3、10章）。

Souther は data の形状から、decode は境界で使う入力源ごと（素の値/Map＝raoh-core、JSON の `JsonNode`＝raoh-json、jOOQ の `Record`＝raoh-jooq）に、encode は中立な Map（raoh-core）へ導出する。decode の骨組み（フィールド・キー・判別子）は源に依らず共通で、末端のフィールド取得だけが源ごとに変わる。JSON 出力は Map を境界でシリアライズする（`objectMapper.valueToTree` 等。10.6）。

外部表現には null が現れうるが、内部dataではnullを禁止する。null は境界（Raoh の入力）にのみ存在し、Decoder を通ってドメインに入ることはない。

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
NonEmptyList<T>            // 空でないリスト
Never                      // 値を持たない型。起こり得ないアーム（空の直和）
Unit                       // 値が1つだけの型
```

Souther に `Result` / `Either` 型は無い。behavior の出力は印の無いドメイン直和で、成功と失敗を分ける2枠の型を持たない（12章・14章）。

`Never` と `Unit` は**表層で書ける型名ではなく、読み方の概念**である。`Never` は起こり得ないアームを表し、単一アームの出力（`-> A`）は失敗row が空、と読める。`Unit` は仕様DSLの `単位型` に対応するが、フィールドを書く手段としては提供せず、常に「フィールドを持たない data」（8.3）で表す。`NonEmptyList<T>` はランタイムが持つ補助型で、ユーザーがフィールド型として書くものではない。

### 7.4 任意型

任意性は `?` で表す。

```text
data 出張完了 = {
    事前承認者: 従業員ID?
}
```

これは次へ脱糖する。

```text
事前承認者: Option<従業員ID>
```

`?` はフィールドの任意性以外には使用しない。

Decoder の外部型は次である（Raoh の Decoder）。

```text
Decoder<T> = 外部表現 -> Result<T>          // 失敗は Raoh の Issues を運ぶ
```

---

## 8. data

`data` は内部世界の型付きデータを定義する。すべてのフィールドは不変である。

宣言は `data 名前 = 右辺` の形をとる。右辺が `{ ... }` なら直積、`A | B` なら直和、右辺を書かなければ単位である。仕様DSLの `data 名前 = ...` と同じ形にそろえてある。

### 8.1 直積data（AND）

仕様DSLの `AND` は直積に写る。フィールド名は業務上の役割で、型は別に指定できる。

```text
// 仕様DSL: data 従業員 = 従業員ID AND 役職 AND 上長ID
data 従業員 = {
    id:    従業員ID
    役職:   役職
    上長ID: 従業員ID       // 役割名（上長ID）と型（従業員ID）が異なる
}
```

役割と型を分けて書けることは重要である。仕様DSLでは同じ概念が層ごとに別名を持つ（`承認者ID`（操作の入力）→ `事前承認者ID`（状態に保存）→ `pre_approver_id`（DB列））。`事前承認者: 従業員ID` のように、フィールド名に役割を、型に概念を書ける。

### 8.2 フィールド合成（include）

仕様DSLでは、共通項目を持つ状態が `data 提出済み = 出張申請共通項目 AND 提出日時` のように書かれる。`出張申請共通項目` の全フィールドを持ち、さらに `提出日時` を足す、という意味である。Souther はこれを `include` で表す。

```text
data 出張申請共通項目 = {
    申請者:    従業員
    予定費用:   金額
    出張先:    String
    出張目的:   String
    出張開始日: Date
    出張終了日: Date
}

// 仕様DSL: data 提出済み = 出張申請共通項目 AND 提出日時
data 提出済み = {
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

アームは常に別途宣言された名前付きdataへの参照である。アームの位置にインラインのレコードを書くことはできない。仕様DSLの `OR` も同じで、アームは必ず別に宣言される（`data 役職 = 管理職 OR 一般社員` に対し `data 管理職 = レベル` / `data 一般社員 = 単位型`）。参照専用にしておくと、`data X = A | B` を読んだだけで A も B も既存の名前だと分かり、その名前が定義済みかどうかで `|` の意味が変わることがない。

**アームの値は、その直和の値である。** 関数型言語と同じく、アーム型（例: `提出済み`）の値は、その直和型（`出張申請`）が期待されるところ——フィールド代入・引数・戻り値のいずれ——でも透過的に使える。上方向（アーム→直和）だけを許し、下方向（直和→特定アーム）は `match` を要する。ネストした直和（`data 自社負担 = 立替 | 仮払い` が `費用負担区分` のアーム）も同様に、葉のアームまで畳んで判定する。

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

単位dataの値は、**名前をそのまま書いて構築する**（関数型言語で0引数コンストラクタが値になるのと同じ）。`T {}` のような空レコードは使わない。

```text
// behavior 本体で単位を構築する。constructs を要する（2.1・12.3）
[先方費用負担 | 費用負担が先方]     // 先方費用負担 は単位。名前だけで構築
```

式の中の裸の識別子は、束縛済みのローカルがあれば変数、無ければ同名の単位dataの構築、どちらでもなければ未定義、と解決する。Souther は識別子の大小で構文的に区別しない（日本語の業務語彙をそのまま識別子にするため）ので、この解決はシンボルテーブルで行う。

### 8.5 フィールドの可視性

フィールドの**参照**は、同一モジュール内のコードから可能である。behavior は自分の入力dataのフィールドを自由に読める。

フィールドはモジュール外（Java側を含む）へは公開しない。外部に値を渡す場合は、値を返す behavior を定義する。

制約されるのは**参照ではなく生成**である。フィールドを読めることと、その data の値を新たに構築できることは別の権限で、構築は 2.1 の経路に限る。

### 8.6 union types と intersection types

Souther は union types を持つ。次の三つの形で現れる。

- 名前付き直和 `data X = A | B`（sealed。網羅性検査の対象）
- behavior 出力の無名 union `-> 提出済み | 事前承認待ち`（12.2）
- behavior 出力の失敗アームを含む無名 union `-> Out | E1 | E2`（14.2）

一方、structural intersection types（`A & B`。「A でも B でもある値」を表す型演算子）は**持たない**。理由は、Souther が nominal な生成経路閉じ込めの上に立っているからである。`A & B` を型として認めると、その値を誰が構築するのか、どの invariant を検査するのかが決まらず、2.1 と 2.2 が破れる。

仕様DSLの `AND` は「A のフィールドをすべて持ち、さらに足す」であって「A かつ B である」ではない。これは structural intersection ではなく nominal なフィールド合成なので、8.2 の `include`（フィールドを平らに取り込み、invariant も引き継ぎ、部分型にはしない）で過不足なく表せる。intersection types を導入しないのは機能不足ではなく、生成経路を閉じるための設計判断である。

---

## 9. 不変条件

### 9.1 宣言

仕様DSLでコメントとして書かれた値制約を、`invariant` として実行可能にする。

```text
// 仕様DSL: data 金額 = 整数 // 0以上、円単位
data 金額 = {
    value: Int

    invariant value >= 0
}

data DateRange = {
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

### 9.4 invariant付きdataの構築は違反アームを持つ

invariant を持つ data を構築する behavior と Decoder は、違反を表現できる出力でなければならない。

- Decoder: 違反は Raoh の `Result` の失敗（`Issue`）に入る。decode の出力が常に成功か失敗のどちらかなので必ず満たす（10章）。
- behavior: 出力の直和に、違反を運ぶアーム（例: 制約違反）を1つ以上含める。

invariant を持つ data を構築する behavior の出力が単一アーム（違反の行き先が無い直和）だと、違反を返す先がなくなる。これはコンパイルエラーとする（22.3）。invariant を持たない data の構築は単一アームでよい。

---

## 10. Decoder

### 10.1 役割

Decoder は外部入力から内部dataを構築する境界である。仕様DSLの parse-don't-validate（境界で生データを検証し、成功して初めてドメイン型を構築する）を実装する。Souther は data の形状から Raoh の Decoder を導出する。導出された Decoder は外部表現（6章）を読み、成功すれば T を構築し、失敗は Raoh の `Result`（`Issues` を運ぶ失敗側）が表す。

```text
会員ID.decoder() : Decoder<会員ID>     // 外部表現 -> Result<会員ID>（失敗は Raoh の Issues）
```

decode の失敗は Souther のドメイン型ではない。malformed input は業務結果ではなく境界の関心事なので（2.6）、ドメインの直和アームには並べず、Raoh の `Result` の失敗側に置く。decode は behavior ではなく境界の縁であり、`>>` の段にはならない（14.1）。ドメインの世界の合成は behavior と `>>` だけで行う。

Decoder は Souther の構文には現れない。data のフィールド名と型から既定の Decoder を導出する。dataの定義に書くのは `data` / `invariant` / `behavior` だけでよい。外部表現との対応づけ（キー名・正規化・判別子）をドメイン定義に混ぜないための分担である。既定の導出で足りないときは、境界（Java / Raoh）側でカスタム Decoder を書く（10.4）。

### 10.2 導出規則

既定の Decoder は次の規約で data の形状から導出する。

- JSONキーはフィールド名に一致する。Object の各フィールドを同名キーから読む。
- 単一プリミティブフィールドの data は newtype として扱い、Object ではなく裸のプリミティブ（Text / Int）から読む。
- ネストした名前付きdataは、そのdataの導出Decoderで再帰的に読む。
- `List<T>` は要素ごとに `T` の導出Decoderで読む。
- 構築時に invariant を検査し、違反は Raoh の `Result` の失敗（`Issue`）になる。decode は必ず成功か失敗のどちらかなので、違反の行き先は常にある（9.4）。
- 独立したフィールドのエラーは集積する（Applicative、15章）。集積は Raoh の combinator が担う。

```text
// value ひとつだけの data は裸の整数から読む（newtype）
data 金額 = {
    value: Int
    invariant value >= 0
}
// 導出Decoder: Int(n) -> 金額 { value: n }（invariant 検査つき）

// 複数フィールドの data は Object から、フィールド名＝キーで読む
data 従業員 = {
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

入れ子の直和は、葉のアームまで畳んで振り分ける。

```text
data 自社負担     = 立替 | 仮払い | 会社カード
data 費用負担区分 = 自社負担 | 先方負担
// 導出Decoder: "type" は "立替" / "仮払い" / "会社カード" / "先方負担" のいずれか。
// "自社負担" というタグは外部表現に現れない。
```

畳むのは、`"type"` が一つしか無いからである。直接のアームにタグを付けると、`費用負担区分` の encode が `{ "type": "自社負担" }` を書いた時点でどの葉だったかが失われ、続けて `自社負担` の decode が同じキーを読もうとして自分のタグではないと拒む。往復（11.3）が成り立たない。葉で畳めば、どの階層の decoder に渡しても同じ外部表現が読める。アーム→直和の代入が葉まで畳んで判定する（8.3）のと同じ理由である。`match` の網羅性は各階層で見る（16.3）が、それは外部表現の話ではない。

判別子キーやタグを業務固有のものにしたいとき（`"status"` を見て `"draft"` / `"submitted"` に振り分ける、永続表現の `representation_version` でバージョンを読み分けるなど）は、外部表現の都合であって data の形状からは導けない。境界側のカスタム Decoder で行う（10.4）。

### 10.4 カスタム境界コーデック

既定の導出で足りないとき（別キー名、正規化、既定値補完、業務固有の判別子、永続表現のバージョン分岐）は、Java 側で外部表現を組み立て、data の（invariantを検査する）構築を呼ぶ Decoder を書く。24章の `JdbcFindMember` が `Member.decoder()` を呼ぶのがこの形である。外部表現の変換は境界に閉じ、ドメイン定義には持ち込まない。

### 10.5 Decoderエラー

decode の失敗は Raoh の `Result` の失敗側が運ぶ。エラーは Raoh の `Issue`（`path` / `code` / `message` を持つ）で表され、独立したフィールドの失敗は集積される（15章）。Souther はこの失敗語彙を所有しない。`復号失敗` や `DecodeError` というドメイン型は導入せず、失敗の表現は縁のライブラリ Raoh に委ねる。

境界（HTTP 等）では `Result` の失敗を受け取り、適切な応答（例: 400）へ写す。24章の `MemberController` が decode の `Result` を分岐するのがこの形である。

### 10.6 生成方式とRaoh

導出した Decoder は、独立した Decoder IR を経ず、ClassFile backend が Raoh の Decoder（`net.unit8.raoh.decode.Decoder` と combinator）を**直接バイトコードとして生成する**。生成された decoder は外部表現の中立木（6章。Raoh の `Object` / `Map` 入力）から読み、`Result<T>` を返す。

```text
Source -> AST -> (導出で decoder/encoder を AST に充填) -> 型検査 -> ClassFile backend -> .class（Raoh の Decoder を出力）
```

Souther のランタイム（souther-runtime）は Raoh に依存しない。Raoh に依存するのは生成されたバイトコードと、それを使うアプリだけである。プリミティブ解析・エラー集積（Applicative）・判別振り分け（10.3）は Raoh の combinator が担い、Souther は data の形状（キー・型・判別子・アームタグ）を Raoh の decoder 構築へ写すだけである。

decoder は入力源ごとに生成する。素の値 / Map は raoh-core、JSON の `JsonNode` は raoh-json、jOOQ の `Record` は raoh-jooq の combinator を使う。骨組み（キー・型・判別子・アームタグ）は源に依らず共通で、末端のフィールド取得だけが源ごとに差し替わる。各公開 data には、その形状が対応する源ぶんを生成する ── 素の値 / Map は常に、JSON は temporal（`Date` / `DateTime`）を含まない形状に、jOOQ `Record` は入れ子・List・Map を持たない平坦な形状（スカラ列と newtype 列のみ）に対して生成する（raoh-json / raoh-jooq の能力に合わせる。単位dataは入力を無視するので全源に対応する）。

---

## 11. Encoder

### 11.1 役割

Encoder は内部dataを外部表現（6章）へ変換する全域関数である。Souther は data の形状から Raoh の Encoder を導出する。

```text
出張申請.encoder() : Encoder<出張申請>     // 出張申請 -> 外部表現（Raoh の中立木）
```

Decoder と同じく、Encoder も Souther の構文には現れない。data のフィールド名と型から既定の Encoder を導出する（10.1）。encode も境界の縁であり、`>>` の段にはならない。

### 11.2 導出規則

既定の Encoder は Decoder と対称の規約で導出する。

- 単一プリミティブフィールドの data は newtype として、裸のプリミティブ（Text / Int）へ書く。
- 複数フィールドの data は Object へ書く。キーはフィールド名、値は各フィールドの導出Encoderで変換する。`include` したフィールドも平らに並ぶ。
- ネストした名前付きdataは、そのdataの導出Encoderで変換する。
- `List<T>` は要素ごとに `T` の導出Encoderで変換する。
- 直和dataは判別子フィールド `"type"` にアーム名のタグを付け、アームの導出Encoderで変換する。入れ子の直和は葉のアームのタグを書く（10.3）。

```text
data 金額 = { value: Int  invariant value >= 0 }
// 導出Encoder: 金額 -> Int(self.value)（裸の整数）

data 提出済み = {
    include 出張申請共通項目
    提出日時: DateTime
}
// 導出Encoder: 提出済み -> { "申請者": ..., "予定費用": ..., ..., "提出日時": ... }
```

キー名や表現を外部仕様に合わせたいときは、境界側でカスタム Encoder を書く（10.4）。

### 11.3 往復則

次を推奨する（要求はしない）。

```text
decode(encode(value)) == value
```

`encode(decode(raw)) == raw` は要求しない。Decoder は正規化・既定値補完・余剰項目削除を行いうるためである。

---

## 12. behavior

behavior は内部dataに対する振る舞いを定義する。所属を持たないトップレベルの入出力関係である。

宣言は `behavior 名前 = 右辺` の形をとる。右辺は引数リストと本体（12.1）か、`>>` による合成（14章）である。名前をその入出力関係に束縛する、と読める。

### 12.1 純粋behavior

仕様DSLの入力 `AND` は引数リストに写る。パラメータは常に `名前: 型` で書く。

```text
// 仕様DSL: behavior 事前承認要否を判定する = 予定費用 AND 申請者 AND 費用負担区分 -> 事前承認理由リスト
behavior 事前承認要否を判定する = (
    予定費用:    金額,
    申請者:      従業員,
    費用負担区分: 費用負担区分
) -> List<事前承認理由> {
    ...
}
```

### 12.2 出力は印の無い直和

仕様DSLの出力 `OR` は、そのまま印の無い直和に写す。成功／失敗の区別も、`error` のような印も付けない。behavior は「入力から、あり得る業務結果のいずれか」への関数である。

```text
// 仕様DSL: behavior 出張申請を提出する = 申請準備中 -> 提出済み OR 事前承認待ち
behavior 出張申請を提出する = (申請: 申請準備中) -> 提出済み | 事前承認待ち
    constructs 提出済み, 事前承認待ち
{
    ...
}

// 仕様DSL: behavior 事前承認する = 事前承認待ち AND 承認者ID -> 事前承認済み OR 承認権限なし
// 承認権限なし はただの data。error でも Result でもない。
behavior 事前承認する = (
    申請:    事前承認待ち,
    承認者ID: 従業員ID
) -> 事前承認済み | 承認権限なし
    constructs 事前承認済み, 承認権限なし
{
    // 事前条件: 承認者IDが申請者の上長IDと一致すること
    require 承認者ID == 申請.申請者.上長ID
        else 承認権限なし

    事前承認済み { ..申請, 事前承認日時: 現在時刻(), 事前承認者: 承認者ID }
}
```

どのアームが「本線から外れる失敗」かは、この behavior 単体では決まらない。`>>` で合成したとき、後段が消費しないアームが素通りして出力に残る＝それが本線から外れる（14章）。`承認権限なし` を入力に取る後段があればそのアームは処理され、無ければ末尾まで運ばれる。同じ値がある合成では外れ、別の合成では本線に乗る。

`require <条件> else <値>` は、条件が偽なら `<値>`（出力の直和のいずれかのアーム）を返してその behavior を終える。`<値>` はそのアームの data である。

入力が直和のこともある（`(提出済み OR 事前承認済み) AND ...`）。パラメータ型に直和を書き、内部で `match` する。名前付きの直和にしておくと読みやすい。

```text
// 仕様DSL: behavior 出張を完了する = (提出済み OR 事前承認済み) AND 実費用 AND 出張報告 -> 出張完了 OR 不正な実費用 OR 出張報告なし
behavior 出張を完了する = (
    申請:   提出済み | 事前承認済み,
    実費用:  金額,
    出張報告: String
) -> 出張完了 | 不正な実費用 | 出張報告なし
    constructs 出張完了
{ ... }
```

### 12.3 data生成権限（constructs）

dataを生成する behavior は `constructs` を宣言する。これは「生成してよい」という権限の明示であり、依存とは別物なので、本体から推論せず必ず書く。

```text
behavior applyChange = (input: ChangeEmailRequest) -> Member
    constructs Member
{
    input.member with { email: input.email }
}
```

複数dataを生成する場合は列挙する: `constructs Member, AuditRecord`。

次はdata生成とみなす: レコードリテラル（`型名 { ... }`）、直和dataのコンストラクタ、`with` 更新、単位dataの構築、新しいdataを要素に含むコレクション生成。既存値をそのまま返す場合は生成とみなさない。

**本体のどこで生成しても宣言が要る。** `require ... else` の離脱値（16.4）も例外ではない。`else 承認権限なし` は単位dataを一つ作っているので、`constructs 承認権限なし` が要る。失敗を表すアームだからといって権限が免除されることはない——2.1 が閉じているのは生成であって、成功か失敗かは関係しない。`require` が `if` への糖衣である（16.4）ことは、この点でも効いている。本体は一つの式なので、生成の場所を数える側に「文も見る」という追加の仕事が無い。

required behavior への依存（`requires`）は宣言しない。コンパイラが本体から要求集合を推論して型に載せる（13.5、14.3）。生成権限（`constructs`）だけを書き、依存は書かない、が原則である。

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

### 12.5 ブロック

リストの各要素に処理を施すには、引数としてブロックを渡す。ブロックは `引数 => 式` と書く。

```text
// 仕様DSL: data 未検証注文  = ... AND List<未検証注文明細>
//          data 検証済み注文 = ... AND List<検証済み注文明細>
// 明細を1件ずつ検証して詰め替える。ここでブロックが要る。
behavior 明細を検証する = (xs: List<未検証注文明細>) -> List<検証済み注文明細> {
    map(xs, x => 1件を検証する(x))
}
```

ブロックは値ではない。behavior の戻り値にすること、data のフィールドに格納すること、`let` で束縛することを禁じる。許すのは引数として渡すことだけである。

MVPで渡せる先は、ブロックを取る標準ライブラリ（18.4 の `map` / `filter` / `fold` / `all` / `any`）に限る。ユーザーが自分の behavior でブロックを受け取ることは後回しにする（25.2）。ブロックの型を書く場所が要るのはそのときで、MVPの構文にはブロック型そのものが無い。

ブロックは呼び出し先へ展開される。逃げ出せないので、閉包を作る必要がない。これは second-class にしたことの実利である。

#### なぜ値にしないか

ブロックを値にすると、要求集合を書かせることになる。これは 29章の原則（書く側が明示するのは入力・出力と生成権限だけ）と衝突する。

要求集合は推論され、合成時に和集合を取る（14.3）。この規則が成り立つのは、要求集合が具体的な集合として決まるからである。ブロックを返せる・格納できるようにすると、要求集合に変数が入る。すると `R1 ∪ R2` の形の制約を解く必要が生じ、`µ1 ∪ µ2 ∼ µ3 ∪ µ4` は一意に解けないので、和集合の演算子そのものを型言語に持ち込まざるを得なくなる。Koka はこの理由で union による設計を棄却し、代替の subeffect 制約も実装した上で棄却している（Leijen, *Koka: Programming with Row Polymorphic Effect Types*, MSFP 2014）。逃げ道は row polymorphism を入れるか、要求集合を明示的に書かせるかのどちらかで、前者は 3章の対象外（型クラス・高階種）より重く、後者は 29章を撃つ。

渡すことと束縛することは別である。渡すだけなら要求集合は呼び出し元へ外向きに積み上がるだけで、推論は保たれる。壊れるのは、ブロックを引数に取ってその要求を束縛するもの——つまり `bind`（19.5）を言語内のコンビネータにした場合である。Souther の `bind` は生成されたJava側のAPIであって言語内の値ではないので、この制限は何も奪わない。Haskell の implicit parameters が同じ線引きを採り、制約は外側の文脈へ浮上するだけで内側には入らない、として完全な型推論を保っている（Lewis, Launchbury, Meijer, Shields, *Implicit Parameters: Dynamic Scoping with Static Types*, POPL 2000）。

この設計には先行例がある。Effekt は effect 型を「その計算が文脈から要求する capability の集合」と定義し（Souther の要求集合と同じ読み方である）、その意味論を capability を追加の引数として渡す calculus への変換で与えている——`bind` が要求集合を部分適用するのと同じ構造で、変換先の型システムは effect 型を持たない。そしてEffektは、この健全性を保つために関数を値から分離し、すべて second-class として扱う。ブロックを返せると、要求集合が空だと称する計算がハンドラの外へ脱出できてしまうためである（Brachthäuser, Schuster, Ostermann, *Effects as Capabilities*, OOPSLA 2020）。

ブロックを data のフィールドに置けないことは、decoder / encoder の導出（10章・11章）とも整合する。関数は外部表現に写せないので、置けたとしても導出の対象外という例外を作ることになる。

---

## 13. required behavior

required behavior は、言語内で型だけを宣言し、実装を外部から提供する振る舞いである。仕様DSLの `// 依存:` と `// 副作用:` に対応する。

### 13.1 宣言

```text
required behavior findMember = (id: MemberId) -> Member | FindMemberError

required behavior 現在時刻 = () -> DateTime      // 仕様DSL: // 依存: 時刻取得
```

required behavior は本体を持たない。

### 13.2 典型用途

DBクエリ、HTTP呼び出し、ファイル読み書き、時刻取得、ID生成、メッセージ送信。これらの具体的なAPIは言語コアへ持ち込まない。

### 13.3 Java基底クラス生成

required behavior は**抽象基底クラス**として生成する。`Behavior` を継承し、宣言した単位data の出力アームごとに、そのアームを構築する `protected` ファクトリを持つ。

```text
required behavior findMember = (id: 会員ID) -> 会員 | 会員なし | 保存データ不正 | DB不通
```

から概念的に次を生成する。

```java
public abstract class findMember implements Behavior {
    protected findMember() {}
    protected final 会員なし   会員なし()   { ... }   // 宣言した単位アームだけ
    protected final 保存データ不正 保存データ不正() { ... }
    protected final DB不通    DB不通()    { ... }
    // apply(input) は Behavior から継承（未実装）。実装が override する。
}
```

Java側はこれを `extends` して `apply` を実装する（24章）。成功値（`会員` など）は decoder で組み立て、失敗アームは継承した `protected` ファクトリで作る。data のコンストラクタは非公開なので、実装は「その behavior が宣言した出力アーム」に限って構築でき、生成パッケージの外（どのパッケージ）にも置ける。これで生成経路の封じ込め（2.1）が Java 越しでも保たれる。単位でない／invariant を持つ出力アームはファクトリを生成せず、decoder 由来で得る。

### 13.4 Java実装の規則

Java実装は、宣言済みの失敗アームの値（例: `FindMemberError` のいずれか）を返す。DB例外、HTTP失敗などを言語側へ例外として漏らしてはならない。VM障害や実装バグは保証対象外とする。

### 13.5 集合不変条件は入力データで渡す

1件のオブジェクトでは判定できない不変条件（日程重複、一意性など）は、隠れた依存としてではなく、判定に必要な集合を入力データとして明示する。既存申請を読む副作用はワークフローの境界（required behavior）に置く。

```text
// 仕様DSL: behavior 日程重複を確認する = 同一申請者の出張申請リスト AND 申請準備中 -> 申請準備中 OR 日程重複
data 日程重複

behavior 日程重複を確認する = (
    同一申請者の出張申請リスト: List<出張申請>,
    申請:                    申請準備中
) -> 申請準備中 | 日程重複 { ... }

required behavior 同一申請者の申請を読む = (申請者: 従業員ID) -> List<出張申請>
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

`>>` は behavior どうしをつなぐ。前段の出力アームのうち後段の入力型が受け取れるものを後段へ流し、受け取らないアームはそのまま出力へ運ぶ（本線から外れる）。

```text
behavior handle = findMember >> toResponse
```

段は**単一入力**（1引数の behavior）である。`>>` は「1つの値を次段へ渡す」パイプラインの逐次接続なので、複数引数の behavior（例: `事前承認する(申請, 承認者ID)`）は段には置かず、呼び出し（`match ...(x, y) { ... }`）や本体内のインライン呼び出しで使う。

decode / encode は behavior ではなく境界の縁であり、`>>` の段にはならない。境界では先に decode を済ませ、成功したドメイン値だけを behavior パイプラインへ渡し、出力を encode で外へ出す（10章・11章）。`>>` が合成するのはドメインの世界（behavior とその直和出力）だけである。

### 14.2 型ルーティング合成規則

前段の出力を直和 `O_f`、後段の入力型を `I_g` とする。`O_f` のうち `I_g` が受け取れるアームを後段へ流し、残りのアーム `R`（`O_f` から消費分を除いた和）は後段を素通りして出力に積む。

```text
f : A -> B | R           g : B -> C           f >> g : A -> C | R
f : A -> B | R           g : B -> C | S       f >> g : A -> C | S | R
f : A -> B1 | B2         g : B1 | B2 -> C     f >> g : A -> C
```

`R` に運ばれたアームは後段を実行せず末尾まで伝播する。これが Railway だが、「失敗」を特別扱いしているのではなく、消費されなかったアームが素通りするだけである。出力の直和は open error row として扱い、`(C | R) | S` を平坦化し、同名アームは畳む。`O_f` のどのアームも `I_g` に合わなければ合成できない（22.14）。

後段が一部のアームしか受け取らない、あるいはアームごとに違う処理へ分岐したいときは、`>>` ではなく `match` で枝を開く。

```text
match 出張申請を提出する(申請) {
    case 提出済み as s   => ...
    case 事前承認待ち as p => ...
}
```

### 14.3 required behaviorを含む合成

behaviorの内部表現を次として捉える。

```text
Behavior<Requirements, Input, Output>
```

`Output` はあり得る業務結果の直和で、失敗を分ける第4の枠は持たない。合成規則:

```text
f : Behavior<R1, A, O_f>
g : Behavior<R2, B, O_g>        // I_g = B が O_f のアームを受け取る
f >> g : Behavior<R1 ∪ R2, A, (O_g) | (O_f から消費分を除いた和)>
```

`Requirements` は推論され、宣言しない。実装が内部かJava側かは、値の合成規則に影響しない。`Requirements`（必要とする依存）と `Output`（生み出す結果の直和）は独立に合成される。前者は集合和、後者は消費／伝播で決まる。

### 14.4 単一アーム出力の合成

出力が単一アーム（`-> A`）の behavior は、素通りするアームが無い（失敗row が空、`Never`）とみなせる。後段が `A` を受け取ればそのまま流れる。利用者が明示的に何かで包む必要はない。`require ... else` で早期に別アームを返した場合、そのアームは 14.2 の `R` として伝播する。

---

## 15. behaviorのアーム伝播とDecoderのエラー集積

behavior の出力直和は open error row として扱い、`>>` で後段が消費しないアームは素通りで末尾へ伝播する（14.2）。これがドメインの世界の合成様式である。

decode 側のエラー集積は縁のライブラリ Raoh が担う。導出された Object の Decoder は、各フィールドを同名キーから読み、独立したフィールドの失敗をすべて集める（Applicative）。

```text
{ "name": ..., "email": ..., "age": ... }
// name / email / age を各フィールドの導出Decoderで読み、独立した失敗をすべて集積する（Raoh）
```

導出Decoderのフィールド取得は互いに独立で、失敗をすべて集める。集積した失敗は Raoh の `Result` の失敗（`Issues`）にまとまる。カスタム境界コーデック（10.4）で、ある取得が先行の結果に依存する場合は、その箇所から逐次（monadic）になり、先行が失敗すれば後続は実行しない。

集積（applicative）は decode 境界の中の話、アーム伝播は behavior 合成の話で、別の層に属する。decode は `>>` の段ではないので、両者が同じ直和上で混ざることはない（14.1）。集積が成り立つのは、Decoder の各フィールド取得が互いに独立（前の値に依存しない）だからである。

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

`if` は式である。両分岐の型が一致すればその型に、異なる data なら両者を並べた直和になる（`if c then 提出済み{..} else 事前承認待ち{..}` は `提出済み | 事前承認待ち`）。プリミティブどうしが食い違う場合はコンパイルエラーとする。

### 16.3 match

```text
match 出張申請 {
    case 申請準備中 as d   => ...
    case 提出済み as s     => ...
    case 事前承認待ち as p  => ...
    case 事前承認済み as a  => ...
}
```

アームの値は `as` で束縛し、フィールドはそこから参照する（`s.提出日時`）。パターンの中でフィールドを分解束縛する形は持たない。

直和dataに対するmatchは網羅的でなければならない。網羅性は直和が宣言した直接のアームについて判定する。入れ子の直和（`data 費用負担区分 = 自社負担 | 先方負担` の `自社負担` がさらに `立替 | 仮払い | 会社カード`）は、葉のアームを平らに並べるのではなく、`自社負担` を受けて入れ子の `match` で分ける。各階層で独立に網羅性を見るので、アームが重なることがない。アーム→直和の代入（8.3）が葉まで畳んで判定するのとは向きが逆で、こちらは下方向なので階層をたどる。

### 16.4 require

```text
require amount <= balance
    else 残高不足
```

`require` は出力に離脱アームを持つ behavior 内で使う。条件が偽なら `else` の値を返してその場で終える。`else` の値は出力の直和のいずれかのアーム（ただの data）である。behavior の本体は、返したいアームの値を最後の式に置くだけでよい。特別な包み込みは無い。

`require` は **`if` への糖衣**である。次の二つは同じ意味を持つ。

```text
require <条件> else <値>
<残りの本体>
```

```text
if <条件> then <残りの本体> else <値>
```

脱糖は構文解析の時点で行い、以降の段（型検査・生成権限検査・バックエンド）は `if` しか見ない。糖衣として定義する理由は、**値を構築できる場所を式の一系統に保つ**ためである。`require` を独立した文として扱うと、生成権限（12.3）と invariant 検査（9.2）を確かめる側が、式とは別にその位置も歩かなければならない。歩き忘れれば、権限の検査を受けずに invariant を破った値が作れてしまう。脱糖しておけば、その事故が構文上起こり得ない。両分岐の型が違ってよいこと（16.2）が、この脱糖を成り立たせている。

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

ゼロ除算は `Int | DivisionByZero` を返す。

### 18.3 Decimal

```text
add  subtract  multiply  divide  compare
```

除算は丸め方式を明示する。

### 18.4 List

```text
map  filter  fold  all  any  length  get
```

`get` は `Option<T>` を返す。`map` / `filter` / `fold` / `all` / `any` はブロックを引数に取る（12.5）。

```text
map(xs, x => 明細を直す(x))
all(理由リスト, r => 軽微か(r))
```

ブロックの中から required behavior を呼べば、その要求は呼び出し元の behavior の要求集合へ積み上がる。要求集合は書かない（29章）。

### 18.5 Map

```text
get  containsKey  keys  values
```

`get` は `Option<V>` を返す。

### 18.6 Decoders / Encoders

Souther の構文には現れない。data の形状から導出された decoder / encoder は、Raoh の Decoder / Encoder として ClassFile backend が生成する（10.6、11章）。プリミティブ解析・エラー集積・判別振り分け・エンコードは Raoh の combinator が担い、souther-runtime 側に facade は持たない。境界のカスタムコーデック（10.4）は Java 側から Raoh を直接使う。

---

## 19. JVM出力

### 19.1 対象JDK

生成物はJava 21以上で動作することを目標とする。コンパイラ実装はJava 25を使用してよい。Unicode識別子はJavaの識別子規則に沿って生成する（必要ならエスケープ／ローマ字化はbackendの責務）。

生成する `.class` のクラスファイルバージョンは **Java 21（major 65）に固定する**。バックエンドを動かしたJDKの既定に任せてはならない。任せると、生成物のバージョンがビルド環境のJDKに追随し、開発者ごとに非互換な成果物が出る。souther-runtime も生成物側なので、同じく Java 21 を対象にコンパイルする（コンパイラ本体だけが Java 25 でよい）。

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

公開dataには入力源ごとの取得APIを生成する。返すのは Raoh の Decoder / Encoder である（`Decoder<I, T>` は `I -> Result<T>`）。

```text
public static Decoder<Map<String,Object>, 出張申請> decoder()  // Map（raoh-core。newtype は Decoder<Object,T>）
public static Decoder<JsonNode, 出張申請> jsonDecoder()    // JSON（raoh-json）
public static Decoder<Record,   出張申請> recordDecoder()  // DB 行（raoh-jooq）
public static Encoder<出張申請, Map<String,Object>> encoder()  // object/sum -> Map（JSON化は境界で valueToTree）
// newtype は裸のスカラへ encode する。例: 会員ID.encoder() : Encoder<会員ID, String>
```

具体的な実装は ClassFile backend が Raoh の combinator を直接バイトコードとして生成する（10.6）。souther-runtime は Raoh に依存せず、Raoh 依存は生成コードとアプリ側にある。`jsonDecoder()` / `recordDecoder()` はその data の形状が対応する場合にのみ生成する（10.6。JSON は temporal 無しの形状、`Record` は平坦な形状）。`decoder()`（素の値 / Map）は常に生成する。

### 19.5 behavior

トップレベルbehaviorはJavaの関数オブジェクトまたはstatic APIとして生成する。required behaviorを含まないbehaviorは直接呼び出せる。required behaviorを含むbehaviorには、依存実装を束縛するAPIを生成する。

```text
var handle = Handle.bind(new JdbcFindMember(dataSource));
```

生成されるクラス名は behavior 名から導く（先頭を大文字化）。

### 19.6 生成権限の露出防止

生成権限は公開Java APIとして露出させない。dataのconstructorは非公開（package-private）、`__construct` などの内部生成メソッドも非公開にする。required behavior の Java 実装は、生成された抽象基底クラス（13.3）を `extends` し、その behavior が**宣言した出力アームだけ**を、基底の `protected` ファクトリ経由で構築する。data のコンストラクタを呼ぶ抜け道は無いので、実装は生成パッケージの外に置ける（同一パッケージ配置は不要）。生成 behavior（`constructs`）も生成物の一部として同様に閉じる。Reflection、Unsafe、バイトコード改変は保証対象外とする。

---

## 20. コンパイラ構成

```text
Source -> Lexer -> Parser -> AST
  -> Derive (data の形状から decoder/encoder を AST に充填)
  -> Type Check
       名前解決 / 生成権限検査 / 要求集合の推論 / 網羅性検査 / invariant 型検査
  -> ClassFile Backend -> .class（バイトコード直接生成）
```

型検査は AST 上で行い、生成権限・要求集合・網羅性・invariant を一括で確かめる。独立した Domain IR / Decoder IR / Encoder IR は持たず、バックエンドが AST から直接バイトコードを出す。バックエンドは Class-File API（`java.lang.classfile`）で `.class` を生成し、javac は経由しない。将来 Java ソース生成など別の backend が要るときは、この段に IR を挟む余地を残している（MVP では持たない）。

---

## 21. AST とバックエンドが追跡する情報

独立した中間表現は持たず（20章）、次を AST と型検査の結果として保持する。

### 21.1 AST の定義ノード

```text
Module  Data(ProductData)  SumData  UnitData  Include  Field  Invariant
DecoderDef  EncoderDef  BehaviorDef  RequiredBehavior  Import
Construct  FieldInit  Expression  Case  RawExpr  DecRef
```

`SumData` のアームは名前付きdataへの参照だけを持つ（8.3）。`include` は取り込むフィールドと invariant を平らに引き継ぐ。`DecoderDef` / `EncoderDef` は導出（10章・11章）が data の形状から充填する。`require` は脱糖済みなので AST には現れず、`if` として現れる（16.4）。

### 21.2 behavior について追跡する情報

```text
入力型  出力型（あり得る業務結果の直和）  要求集合  生成権限（constructs）  本体
```

出力型は単一型または直和。要求集合（必要な required behavior）は本体から推論する（13.6・14.3）。バックエンドはこの情報を使い、AST から直接バイトコードを生成する。

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

### 22.3 invariant違反の行き先アームがない

```text
error E1003:
Behavior `applyChange` constructs `金額`, which has an invariant,
but its output is a single arm with no place for a violation.
Add an arm (e.g. `金額 | 制約違反`) so an invariant violation has somewhere to go.
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
Exceptions are not supported. Return a failure arm in the output sum.
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

### 22.13 （欠番）

`error` 宣言と E1801 は廃止した。失敗は普通の data で、本線から外れるかは合成で決まるため、成功位置／失敗位置の区別が無い（2.6・12章・14章）。

### 22.14 behavior合成型不一致

```text
error E1701:
Cannot compose behaviors: no output arm of the left behavior is accepted
by the right behavior's input.
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
data 金額 = {
    value: Int
    invariant value >= 0
}

// data 従業員ID = 文字列 // 空文字列ではない
// value ひとつの newtype。導出Decoderが裸のテキストから読み、invariant を検査する。
// 正規化（trim など）が要るなら境界側のカスタム Decoder で行う（10.4）。
data 従業員ID = {
    value: String
    invariant length(value) > 0
}

// --- 単位ケースを含む入れ子の直和 ---
// data 役職 = 管理職 OR 一般社員
data 役職 = 管理職 | 一般社員
data 管理職 = { level: Int  invariant level >= 1 && level <= 5 }
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
data 従業員 = {
    id:    従業員ID
    役職:   役職
    上長ID: 従業員ID
}

data 出張申請共通項目 = {
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

data 申請準備中 = { include 出張申請共通項目 }
data 提出済み = { include 出張申請共通項目  提出日時: DateTime }

data 事前承認理由 = 高額出張 | 権限不足 | 先方費用負担
data 高額出張 = { 基準金額: 金額 }
data 権限不足 = { 役職: 役職 }
data 先方費用負担

data 事前承認待ち = {
    include 出張申請共通項目
    提出日時:         DateTime
    事前承認理由リスト: List<事前承認理由>
}

data 事前承認済み = {
    include 出張申請共通項目
    提出日時:    DateTime
    事前承認日時: DateTime
    事前承認者:  従業員ID
}

// 承認権限なし はただの data（失敗も普通の data。error 宣言は無い）
data 承認権限なし

required behavior 現在時刻 = () -> DateTime

// behavior 事前承認要否を判定する = 予定費用 AND 申請者 AND 費用負担区分 -> 事前承認理由リスト
behavior 事前承認要否を判定する = (
    予定費用:    金額,
    申請者:      従業員,
    費用負担区分: 費用負担区分
) -> List<事前承認理由>
    constructs 高額出張, 権限不足, 先方費用負担
{
    ...
}

// behavior 出張申請を提出する = 申請準備中 -> 提出済み OR 事前承認待ち（両方成功 → 素の直和）
behavior 出張申請を提出する = (申請: 申請準備中) -> 提出済み | 事前承認待ち
    constructs 提出済み, 事前承認待ち
{
    ...
}

// behavior 事前承認する = 事前承認待ち AND 承認者ID -> 事前承認済み OR 承認権限なし（失敗もアーム）
// 事前条件: 承認者IDが申請者の上長IDと一致すること
// requires は書かない。現在時刻() を呼ぶので要求集合はコンパイラが推論する
behavior 事前承認する = (
    申請:    事前承認待ち,
    承認者ID: 従業員ID
) -> 事前承認済み | 承認権限なし
    constructs 事前承認済み, 承認権限なし
{
    require 承認者ID == 申請.申請者.上長ID
        else 承認権限なし

    事前承認済み { ..申請, 事前承認日時: 現在時刻(), 事前承認者: 承認者ID }
}
```

`事前承認する` の推論型:

```text
事前承認する :
    Behavior<{現在時刻}, (事前承認待ち, 従業員ID), 事前承認済み | 承認権限なし>
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
    public FindMemberResult apply(MemberId id) {   // sealed: Member | FindMemberError の直和
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement(
                 "select id, email, display_name from member where id = ?")) {

            statement.setString(1, MemberId.text(id));

            try (var resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return new FindMemberError.NotFound();        // 失敗アームをそのまま返す
                }
                var raw = Map.<String, Object>of(
                    "id",          resultSet.getString("id"),
                    "email",       resultSet.getString("email"),
                    "displayName", resultSet.getString("display_name"));

                // decode は Raoh の Result<Member>。失敗は保存データ不正のアームへ写す
                return switch (Member.decoder().decode(raw)) {
                    case Ok<Member> ok   -> ok.value();
                    case Err<Member> err -> new FindMemberError.InvalidStoredData();
                };
            }
        } catch (SQLException e) {
            return new FindMemberError.DatabaseUnavailable();     // 失敗アームをそのまま返す
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

`.sou`読み込み、Lexer、Parser、AST、モジュール、import、`data`（直積・直和・単位・アーム参照・`include`）、`?`、`invariant`、形状からの `decoder` / `encoder` 導出（newtype・JSONキー＝フィールド名・判別子 `"type"`／アーム名タグ）、`behavior`、`required behavior`（引数ゼロの `()` を含む）、`constructs`、要求集合の推論、レコードリテラルと spread と `with`、ブロック（引数位置のみ。12.5）、印の無い直和出力（失敗もアーム）、`>>`（型ルーティング合成）、`match`、`let`、`if`、`require`（`if` への脱糖。16.4）、`Option`、`NonEmptyList`、null禁止、例外禁止、網羅性検査、生成権限検査、invariant違反アーム検査、behavior合成型検査、ブロックの second-class 検査、ClassFile バイトコード生成（Java 21 のクラスファイルバージョンで出力。19.1）、Java基底クラス生成、Raoh の decoder/encoder としての生成（入力源ごと＝素の値/Map・JSON(`JsonNode`)・jOOQ `Record`、集積・判別は Raoh の combinator、souther-runtime は Raoh 非依存）、Unicode識別子、単体テスト。

### 25.2 後回し

Javaソース生成backend（人間可読な生成コード）、増分コンパイル、IDEプラグイン、LSP、ソースマップ、ユーザー定義の behavior がブロックを取ること（とそのためのブロック型の構文。12.5）、第一級の関数（ブロックを返す・data に格納する。理由は12.5）、高度な型推論、静的不変条件証明、structural intersection types、手書き decoder / encoder 構文（別キー名・正規化・業務固有の判別子は境界のカスタムコーデックで扱う）、入力源を使われ方で絞る生成の最適化（現状は data の形状が対応する源ぶんを生成する。10.6）、JSON temporal / jOOQ 入れ子の対応（現状はそれぞれ JSON temporal を持つ型 / 入れ子を持つ型では該当源の decoder を生成しない）、JSON Schema生成、Wasm出力、JavaScript出力、非同期required behavior。

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
11. Raoh の decoder を出力する ClassFile 生成（中立入力から、集積・判別は Raoh の combinator）
12. デフォルト encoder の導出
13. behavior（純粋）
14. `constructs` と invariant違反アーム検査
15. 印の無い直和出力（失敗もアーム。error / Result / success / failure は無い）
16. `>>`（型ルーティング合成）と `match`
17. required behavior と要求集合の推論
18. Java基底クラス生成
19. requirement propagation
20. `invariant`（include 由来を含む）
21. match網羅性検査
22. モジュールとimport
23. ブロックと、それを取る標準ライブラリ（12.5・18.4。second-class 検査を含む）
24. エラーメッセージ改善

最初の垂直スライス:

```text
data 金額 / 従業員ID  + invariant + 導出decoder/encoder（Raoh 出力）+ ClassFile生成
```

次のスライス:

```text
data 出張申請（状態の直和、共通項目は include）
  + 導出decoder（判別子 "type" / アーム名タグ）
  + behavior 事前承認する（-> 事前承認済み | 承認権限なし, require, 現在時刻() 依存の推論）
```

---

## 27. MVP受け入れ条件

### 27.1 状態の直和と導出decoderがコンパイルできる

23章の `出張申請`（アーム＝名前付きdata、共通項目は `include`）と、その導出decoder（判別子 `"type"`／タグ＝アーム名）がコンパイルできる。

### 27.2 生成権限不足がコンパイルエラーになる

```text
behavior createMember = (email: Email) -> Member {
    Member { email }
}
```

期待するエラー: `Behavior 'createMember' constructs 'Member' but does not declare 'constructs Member'.`

### 27.3 invariant付きdataを単一アーム出力で構築するとエラーになる

invariant を持つ `金額` を構築する behavior の出力に違反アームが無ければ（単一アーム出力なら）E1003 になる。

### 27.4 消費されないアームが素通りで伝播する

`f` が `A -> B | C`、`g` が `B -> D` のとき、`f >> g` は `A -> D | C` になる。`C` は `g` に消費されず素通りして出力に残る。

### 27.5 required behaviorをJava実装で束縛できる

```java
var handle = Handle.bind(new JdbcFindMember(dataSource));
```

要求集合は宣言せずコンパイラが推論し、未束縛のまま呼ぶと E1601 になる。

### 27.6 後段が受け取らないアームはそれ以降を実行しない

`A >> B >> C` で B が C の入力に合わないアームを返した場合、C を呼び出さず、そのアームは末尾まで伝播する。

### 27.7 Decoderエラーを集積できる

複数フィールドが不正な場合、最初の1件だけでなく、すべての独立エラーを返す。

---

## 28. 非機能要件

- コンパイルエラーにソース位置を含める
- 生成する `.class` は標準の検証を通り、Java 21+ から素直に利用できる形にする
- コンパイラ各段階を独立してテスト可能にする
- souther-runtime を Raoh に依存させない。decoder/encoder は生成バイトコードが Raoh を直接使い、Raoh 依存は生成コードとアプリ側に置く
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

behaviorについては次の三つを追跡する。

```text
入力型   出力型（あり得る業務結果の直和）   必要なrequired behavior集合
```

したがって、behaviorは概念的に次の型を持つ。

```text
Behavior<Requirements, Input, Output>
```

失敗を分ける第4の枠は無い。失敗は Output の一部のアームで、本線から外れるかどうかは合成で決まる。`>>` は、出力アームの振り分け（後段が消費するアームを流し、残りを伝播）と required behavior 集合の合成を同時に行う。

```text
f : Behavior<R1, A, O_f>
g : Behavior<R2, B, O_g>
f >> g : Behavior<R1 ∪ R2, A, (O_g) | (O_f から消費分を除いた和)>
```

この規則が、Souther の中心的な合成意味論である。出力は仕様DSLの `-> 出力 OR ...` に、要求集合は `// 依存`・`// 副作用` に対応する。書く側が明示するのは入力・出力（印の無い直和）と生成権限（`constructs`）だけで、要求集合はコンパイラが推論する。Souther は、仕様DSLがコメントに預けた値制約・生成経路・外界依存を型と注入に格上げすることで、仕様モデルを実装モデルへ素直に写す。
