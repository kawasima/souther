# ADR-0008: Java interop is asymmetric

Status: Accepted

## Context

Souther generates artifacts that run on the JVM and are meant to be used from Java. At the
same time, the language guarantees that no unvalidated value can be built (ADR-0002) and
that business branching stays visible in types (ADR-0007). Letting Souther call arbitrary
Java APIs would open a hole in both guarantees.

## Decision

Interop is one-way. Java may use Souther's generated artifacts; Souther may not call
arbitrary Java APIs. The only way Souther touches the outside world is through a behavior
with no implementation (ADR-0006), whose implementation is injected from Java.

## Consequences

```text
Java    -> Souther's generated artifacts   allowed
Souther -> arbitrary Java API              forbidden (only via a non-implemented behavior)
```

Because Souther cannot reach into Java freely, it also ships no DB/HTTP/file APIs of its
own — those live behind injected behaviors. Arbitrary JVM calls are a compile error
(E1401), which points the modeler at declaring a behavior without an `fn` and providing
the implementation from Java.

Keeping the arrow one-way is what preserves the closed construction paths (ADR-0002) and
keeps every business branch in the output sum rather than hidden in a foreign call.

## References

- Specification: §2.7, §3, §22.10 (E1401)
- ADR-0002 (closed construction paths), ADR-0006 (outside-world via missing implementation), ADR-0007 (unmarked sum output)
