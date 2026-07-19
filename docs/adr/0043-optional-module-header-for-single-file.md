# ADR-0043: Optional module header for a single-file compilation unit

Status: Accepted

## Context

Every `.sou` file has, until now, begun with a `module <name>` header, and that name becomes the
package of every generated class and gates the reserved `souther` namespace (ADR-0028). Requiring
the header is right for a module that other modules import — the name is how an `import` reaches it,
and `exposing` is the module's interface (`.mli`/`.fsi`/`.d.ts` in position).

The header is pure ceremony, though, for the smallest thing someone tries first: a single file with
one behavior, run to see its output (the motivating case is `souther run`, which drives a behavior
from the command line). Making the first line `module hello` before any domain content is exactly
the friction that keeps a `.sou` from being something you dash off.

The languages Souther grounds its syntax in (F#/Elm, ML-family) both already resolve this, and they
resolve it the same way: the header is optional, but only for a single file that nobody imports.

- Elm: a file with no `module` declaration is compiled as `Main`. It is a deliberate concession to
  reduce the module system's friction on a beginner's first single-file program.
- F#: a file with no leading `module`/`namespace` declaration becomes an implicit module named after
  the file (the file name without extension, first letter upper-cased) — but *only* for a single-file
  application. A multi-file project or a library must declare the header on every file.

Both gate the convenience on "single file, not imported by others," because an unnamed module cannot
be the target of an import. That gate is not an arbitrary limit; it is the exact line between "a name
is load-bearing" (someone imports it, it has a package others depend on) and "a name is ceremony"
(a leaf run once).

## Decision

Allow a `.sou` to omit its `module` header. The parser accepts a source that starts directly with
`data`/`behavior`/`let`, and the module name is supplied by the caller:

- `souther run <file.sou>` and single-file `souther <file.sou>` name the module after the file stem,
  following F# (`hello.sou` → module `hello`). Souther module names are lower-case package-like
  identifiers, so the stem is used as written, not upper-cased.
- The string entry point `Compiler.compile(String)`, which has no file name to derive from, defaults
  the name to `Main`, following Elm.

A module linked by imports must still be named: `Compiler.compileModules` rejects a header-less
source (a source with no name could not be the target of an `import`). This is F#'s "multi-file
requires an explicit header," and it keeps `exposing`-as-interface intact — an omitted header is only
ever a leaf run on its own, never a name another module depends on.

Because the omitted header resolves to a concrete name (the file stem, or `Main`), nothing downstream
changes: generated classes still land in a named package, and the existing reserved-namespace and
standard-library-qualifier guards (a file named `souther.sou` or `String.sou`) still apply to the
defaulted name.

## Consequences

- The header stops being required for the try-it-out path, which is the point.
- The rule to remember is one line and matches F#/Elm: omit the header only in a single file that
  nothing imports; name it once it is imported or compiled alongside others.
- Threading a default name through `Parser.parse` and `Compiler.compile` is the whole implementation
  cost on the language side; codegen is untouched because it always sees a named module.
- A `null` default (used internally by `compileModules`) is what makes the header required in the
  multi-module path, so the single-file relaxation cannot leak into a linked build.
