# ADR-0012: Field composition is nominal `include`; no structural intersection types

Status: Accepted. Surface syntax superseded by ADR-0030 — the `include X` keyword became a
`...X` spread. The semantics below (nominal, flattened fields, inherited invariants, no
intersection types) are unchanged.

## Context

The spec DSL writes shared fields as `data 提出済み = 出張申請共通項目 AND 提出日時` — "has all of 出張申請共通項目's fields, and adds 提出日時." A structural reading of `AND` would suggest an intersection type (`A & B`, "a value that is both A and B"), but that reading does not fit a language built on nominal closed construction.

## Decision

Field composition is `include`: it flattens another data's fields, inherits its invariants, and is **not** inheritance (no subtype relation, no assignment compatibility — the two only share fields). Souther has union types but deliberately has **no** structural intersection types (`A & B`).

## Consequences

`include` gives exactly what the DSL's `AND` means: flatten the fields (`x.申請者`, not `x.共通.申請者`), carry the included invariants into the composed data's construction, and error on a field-name collision. It is not a subtype, so `提出済み` is not assignable where `出張申請共通項目` is expected. When you want to keep the shared fields as one nested value instead, use an ordinary field (`共通: 出張申請共通項目`).

Admitting `A & B` as a type would break the foundation: it leaves undetermined who constructs the value and which invariants are checked, defeating closed construction (ADR-0002) and the invariant guarantee (ADR-0003). The DSL's `AND` is nominal field composition, not structural intersection, and `include` expresses it with nothing left over. Leaving intersection types out is a design choice to keep construction paths closed, not a missing feature.

## References

- Specification: §8.2, §8.6
- ADR-0002 (closed construction paths), ADR-0003 (invariant violations abort in the domain)
