# ADR-0018: No dedicated update syntax; use a named record literal with spread

Status: Accepted

## Context

Many languages provide a functional-update form (`{ x with 住所 = 新住所 }`) to replace some fields of an immutable record. Souther considered such a form (an earlier `with`) and dropped it.

## Decision

To replace some fields of a value of the same type, write the type name plus spread: `Member { ..x, 住所: 新住所 }`. There is no dedicated update syntax.

## Consequences

In Souther, business-meaningful change crosses a type boundary (`申請 → 事前承認済み`), so same-type rewrites are rare. Adding a "no type name" construction form for that rare case would break the rule that every construction names its type — the rule that lets construction-permission and field checks happen at the construction point (ADR-0002). Keeping the type name on every literal, including updates, keeps that rule intact and keeps one construction form to reason about.

## References

- Specification: §5, §12.4, §25.1
