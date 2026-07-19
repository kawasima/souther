# ADR-0022: Pin the generated class-file version to Java 21

Status: Accepted

## Context

The backend generates `.class` files. Their class-file version could be left to the JDK that runs the backend, or pinned explicitly. The compiler itself may be built on a newer JDK (Java 25) than the artifacts it targets.

## Decision

Generated `.class` files are pinned to the Java 21 class-file version (major 65). The backend must not defer to the running JDK's default.

## Consequences

Deferring to the build JDK's default would make artifact versions track each developer's JDK, producing mutually incompatible outputs across a team. Pinning fixes the target regardless of build environment. The compiler itself may run on Java 25, but every generated artifact — including souther-runtime, which is a generated-side artifact rather than part of the compiler — targets Java 21.

## References

- Specification: `[#target-jdk]`
