# ADR-0013: Sum-data cases are references to already-declared named data

Status: Accepted

## Context

A sum `data X = A | B` needs to decide what can appear at a case position. The spec DSL always declares its `OR` cases separately (`data 役職 = 管理職 OR 一般社員`, with `管理職` and `一般社員` declared on their own). The implementation model can either follow that or allow inline records at case positions.

## Decision

A case is always a reference to an already-declared named data; you cannot write an inline record at a case position.

## Consequences

Reference-only means reading `data X = A | B` tells you at once that A and B are existing names, and the meaning of `|` never depends on whether a name happens to be defined yet. This mirrors the DSL, whose cases are always separately declared.

A case value **is** a value of the sum: as in functional languages, a value of a case type (e.g. `提出済み`) is transparently usable wherever the sum type (`出張申請`) is expected — in a field assignment, argument, or return. Only the up direction (case → sum) is implicit; the down direction (sum → a specific case) requires `match`. Nested sums (a sum that is itself a case of another sum) fold to their leaf cases for this judgment.

## References

- Specification: `[#sum-data]`
