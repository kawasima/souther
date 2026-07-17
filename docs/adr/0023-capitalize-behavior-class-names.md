# ADR-0023: Capitalize generated behavior class names; collisions with data are errors

Status: Accepted

## Context

A behavior name becomes a generated JVM class name. Behavior names follow the business vocabulary and may start lowercase (`quote`), while data names already start uppercase. Capitalizing a behavior name to form a class name can therefore collide with a same-module data name.

## Decision

Generated class names derive from the behavior name with the first letter capitalized. A behavior `quote` becomes class `Quote` and collides with a same-module `data Quote`; that collision is a compile error, and one of the two must be renamed.

## Consequences

The collision surfaces at compile time rather than producing two classes that clash on the JVM. In practice it is rare: verb behaviors and noun data are naturally different words (`会員を探す` versus `会員`), so the capitalized behavior class and the data class do not usually meet. When they do, renaming one — normally making the verb and the noun distinct — resolves it.

## References

- Specification: §19.5
