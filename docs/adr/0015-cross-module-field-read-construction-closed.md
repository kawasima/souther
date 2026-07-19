# ADR-0015: Field reads may cross module boundaries; only construction is closed

Status: Accepted

## Context

Souther closes data construction paths (ADR-0002) so that no unvalidated value can be built. A separate question is whether *reading* a field must be equally restricted. If reading were closed too, passing a value across a module boundary would require a getter-only behavior for every field.

## Decision

Reading a field is possible wherever the data's type is visible: within a module, any data; across modules, any exposed data (exposed means importable, and all of its fields are readable — there is no per-field exposure). A behavior may read the fields of its input data even when that data comes from another module. What is constrained is construction, not reading.

## Consequences

Reading a field cannot break an invariant; only construction can, and construction is limited to the paths of ADR-0002. So allowing cross-boundary reads does not weaken the "no unvalidated value" guarantee — reading and constructing are separate permissions. No getter-only behavior is needed to pass a value outward: such a getter would not appear in the spec DSL, so it could not be a behavior at all (ADR-0005).

On the JVM, exposed data get public read accessors, because module = package and a package-private field cannot be read across the boundary. Constructors stay non-public, so Java can read a value's fields but must go through the invariant-checking decoder to build one (`[#jvm-construction-privacy]`). Reading a field from Java is the same exposure the encoder already gives by emitting every field as JSON; what is protected is construction.

## References

- Specification: `[#field-visibility]`, `[#jvm-product]`, `[#jvm-construction-privacy]`
