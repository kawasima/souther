# ADR-0044: Diagnostics are data rendered by a locale-aware layer

Status: Accepted

## Context

Souther grounds its syntax in Elm, and Elm's error messages are a large part of why people reach for
it: a titled report, the offending source line quoted with a caret under the problem, the two types
that disagree shown side by side, and a hint on how to fix it. Souther's compile errors did not come
close. A single `CompileException` carried a `SourcePos` and an optional code, and its message was
built by string concatenation at the throw site and frozen into the exception at construction. The
CLI printed `e.getMessage()` verbatim — one line of `line:col CODE: message`, no source snippet, no
caret, no color. About 180 throw sites each spelled their message inline in English; only ~15 codes
were wired. There was no message catalog and no locale: the text was English, always.

Three things were wanted, and they turned out to share one prerequisite.

- Elm-quality human output — a snippet, a caret, found-vs-expected type blocks, hints.
- Machine-readable output for tools and agents — a stable code and source region a program can act
  on without parsing prose. (JSON is not "more readable" to a model; its value is a stable identity
  and coordinates that do not move when the prose or the locale changes.)
- Japanese and English messages. The author, the book (smdd-book), and the expected audience are
  Japanese; English is the second locale.

The shared prerequisite is to stop freezing a formatted string and instead carry the diagnostic as
data, then render it. Once a diagnostic is data, i18n is choosing a message template by locale, the
Elm view is one renderer, and JSON is another.

## Decision

Model a diagnostic as a value (`net.unit8.souther.compiler.diag.Diagnostic`): a severity, an optional
code, a primary `Region`, optional secondary `Region`s each with a label, a message (a catalog key
plus arguments, or a compatibility literal), an optional found-vs-expected type pair, hints, and a
suggestion. A `DiagnosticRenderer` turns it into text — `HumanRenderer` (Elm-style, with color when
stderr is a TTY) or `JsonRenderer`. Prose comes from a `ResourceBundle` catalog: `messages_ja`
(default) over an English base (`messages.properties`); a key missing from Japanese falls back to
English, a key missing from both renders as itself, so the compiler never crashes on an unmigrated
site. Locale is resolved once: `--lang` > `SOUTHER_LANG` > the JVM default > Japanese. The code and
the type strings are locale-independent — the stable identity; titles, messages, hints, and labels
follow the locale.

`CompileException` now wraps a `Diagnostic`. Its `getMessage()` still returns the old one-line form,
so existing callers and tests are unchanged, while a renderer can take `diagnostic()` and produce the
snippet or JSON. An unmigrated throw site is wrapped as a `literal` diagnostic and renders through the
same pipeline immediately — a snippet and a localized frame, with the message body still the old
English string. The CLI (`compile` and `run`) gained `--format human|json`, `--lang`, and `--color`.

Two decisions bound the scope.

- Underlines are token-width, not full expression spans. `SourcePos` stays a point; a region is the
  point plus the offending token's length, computed at the throw site. The `Diagnostic` model is
  nevertheless region-capable (a `Region` is start-plus-end, and secondary regions are first-class),
  so an error that needs to point at more than one place — the two branches of a disagreeing `if`,
  the two sides of a failed composition — does so today, and precise multi-line spans can be added
  later without repainting the throw sites. Threading an end position through every AST node and
  every desugar pass was not worth its cost for a token-width default.
- The high-value errors are rewritten, the rest are migrated as-is. A handful of errors carry most
  of the felt quality — a type mismatch (found vs expected), an `if` whose branches disagree (each
  branch pointed at with its type), a non-exhaustive match (every missing case listed). These are
  written Elm-style with a `Type.show` that reads like source (`List<Int>`, `A | B`, `Int?`) rather
  than the record `toString`. The remaining ~148 messages move onto catalog keys mechanically, with
  the English literal as the fallback until each is translated.

## Consequences

- Every compile error renders Elm-style — titled, with a quoted line and caret — and in the selected
  locale, including sites not yet migrated onto a catalog key.
- `getMessage()` keeps its old text, so the ~30 tests that assert on message substrings stay green;
  the richer content lives on `diagnostic()`. The renderer's improved prose and the legacy one-liner
  can diverge until a site's tests are moved onto the structured diagnostic.
- A `code` and a `region` are available as JSON for an editor, CI, or agent; prose can be localized
  without breaking anything that keys on the code.
- Full Japanese coverage of all ~180 messages is a follow-through, not a single change: the mechanism
  and the high-value messages ship bilingual, and each remaining message becomes Japanese as it is
  migrated, falling back to English until then.
- A multi-file build has no per-diagnostic file identity (SourcePos carries no file), so a snippet is
  quoted only for a single-file compile; a linked build renders the frame and message without the
  source line until a file handle is added to the position.
