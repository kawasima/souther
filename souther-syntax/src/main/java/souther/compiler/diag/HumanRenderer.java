package souther.compiler.diag;

import souther.compiler.source.SourceId;


import souther.compiler.text.DisplayColumns;

import java.util.Locale;

/**
 * Renders a diagnostic Elm-style: a title bar with the error name and location, the offending source
 * line with a caret underline, then the message, any found-vs-expected type blocks, and hints. Color
 * is applied only when {@code useColor} is set (the caller decides from TTY and {@code NO_COLOR}).
 *
 * <p>A region in another file is quoted from that file and says where it is, so a problem written
 * in two of them reads as one block rather than as two diagnostics the reader has to pair up.
 */
public final class HumanRenderer implements DiagnosticRenderer {

    /** How wide the title bar is written, in display columns — a translated title holds full-width
     *  characters, and padding it by character count is what makes the bar overrun. */
    private static final int WIDTH = 60;

    private static final String RESET = "[0m";
    private static final String CYAN = "[36m";
    private static final String RED = "[31m";
    private static final String YELLOW = "[33m";
    private static final String DIM = "[2m";

    private final boolean useColor;

    public HumanRenderer(boolean useColor) {
        this.useColor = useColor;
    }

    @Override
    public String render(Located located, SourceContextResolver sources, Locale locale) {
        Diagnostic d = located.diagnostic();
        SourceId own = located.context().filedUnder().orElse(null);
        DiagnosticView view = DiagnosticView.of(d, located.context());
        // The file the header names: the one the anchor is quoted from, or — where nothing is
        // anchored here — the one this report is listed under, which is the whole of what a reader
        // is told about where to look for it.
        SourceContext anchorSource = view.anchor()
                .map(shown -> sources.quotedFrom(shown.spot()))
                .orElseGet(() -> sources.sourceOf(own));
        StringBuilder out = new StringBuilder();
        header(out, d, anchorSource, view.anchor().map(Shown::spot).orElse(null), locale);
        out.append('\n');
        view.anchor().ifPresent(shown -> snippet(out, shown.spot().region(), anchorSource,
                d.severity() == Severity.WARNING ? YELLOW : RED));
        for (Shown other : view.others()) {
            out.append('\n');
            SourceContext src = sources.quotedFrom(other.spot());
            if (view.anchor().isEmpty()
                    || !Spot.knownToBeOneText(other.spot(), view.anchor().get().spot())) {
                out.append(color(DIM, location(startOf(other.spot().region()), src))).append('\n');
            }
            snippet(out, other.spot().region(), src, CYAN);
            if (other instanceof Shown.ALabel(Spot _, souther.compiler.diag.msg.Message said)) {
                out.append(color(DIM, DiagnosticRenderer.qualified(
                        Messages.render(said, locale),
                        startOf(other.spot().region()), locale))).append('\n');
            }
        }
        // A label with nothing to quote, said after the places there are and before the message.
        // It is not a block with no snippet in it: there is no line, no file name and no caret, so
        // what would be left of the block is the sentence, written where the sentences are.
        for (DiagnosticView.Unquotable said : view.unquotable()) {
            out.append('\n').append(color(DIM, DiagnosticRenderer.saidAbout(said, locale)))
                    .append('\n');
        }
        out.append('\n').append(DiagnosticRenderer.body(d, locale)).append('\n');
        if (d.diff() != null) {
            out.append('\n');
            out.append(Messages.get("diag.diff.found", locale)).append('\n');
            out.append("    ").append(d.diff().actualType()).append('\n');
            out.append(Messages.get("diag.diff.expected", locale)).append('\n');
            out.append("    ").append(d.diff().expectedType()).append('\n');
        }
        if (d.repair() != null) {
            out.append(hintLabel(locale))
                    .append(Messages.get("diag.suggestion", locale, d.repair().with())).append('\n');
        }
        for (Note note : d.notes()) {
            out.append(hintLabel(locale))
                    .append(Messages.render(note.said(), locale)).append('\n');
        }
        return out.toString();
    }

    /**
     * The bar over a report: what it is, and where to look.
     *
     * <p>Where to look is the anchor's, not the diagnostic's own place. A report anchored nowhere
     * has a file to be listed under and no line of it to quote, and one whose numbers are of a text
     * the caller did not name has neither — writing its line and column would put them against
     * whichever file the reader had in mind, which is the reading this whole change removes.
     */
    private void header(StringBuilder out, Diagnostic d, SourceContext src, Spot anchor,
                        Locale locale) {
        String title = title(d, locale);
        String code = d.code() == null ? "" : "  " + d.code();
        String left = "-- " + title + code + " ";
        String loc = location(anchor == null ? null : anchor.region().start(), src);
        int dashes = WIDTH - DisplayColumns.width(left) - DisplayColumns.width(loc);
        StringBuilder bar = new StringBuilder(left);
        for (int i = 0; i < dashes; i++) {
            bar.append('-');
        }
        if (!loc.isEmpty()) {
            bar.append(dashes > 0 ? "" : "-").append(loc);
        }
        out.append(color(CYAN, bar.toString())).append('\n');
    }

    /**
     * The header's title: the category the diagnostic states, with its severity beside it, or the
     * severity alone when it states none. A warning is marked either way, since the code and the
     * message read the same for both and the bar is the only place the difference can be seen.
     *
     * <p>The title is not derived from the code. A code names a rule and a title names a category,
     * and one category holds many rules — deriving one from the other would put a per-code title in
     * the catalog for every rule and print the same category under a different wording each time.
     * A coded diagnostic is given its title by {@link DiagnosticCode}, before it reaches here.
     */
    private String title(Diagnostic d, Locale locale) {
        String about = d.titleKey() != null && Messages.has(d.titleKey(), locale)
                ? Messages.get(d.titleKey(), locale)
                : null;
        if (d.severity() != Severity.WARNING) {
            return about != null ? about : Messages.get("diag.error.title", locale);
        }
        String warning = Messages.get("diag.warning.title", locale);
        return about != null ? about + " (" + warning + ")" : warning;
    }

    private static SourcePos startOf(Region region) {
        return region == null ? null : region.start();
    }

    private String location(SourcePos pos, SourceContext src) {
        PhysicalPos sits = src == null ? null : src.resolve(pos);
        if (sits == null) {
            return src == null || src.fileName() == null ? "" : src.fileName();
        }
        String file = src.fileName() == null ? "" : src.fileName() + ":";
        return file + sits;
    }

    private void snippet(StringBuilder out, Region region, SourceContext src, String caretColor) {
        if (region == null || src == null) {
            return;
        }
        PhysicalRegion sits = src.resolve(region);
        if (sits == null) {
            return;
        }
        PhysicalPos start = sits.start();
        String line = src.line(start.line());
        if (line == null) {
            return;
        }
        String gutter = start.line() + "| ";
        out.append(color(DIM, gutter)).append(line).append('\n');
        // A region says where it is in UTF-16 units, which is where it is in the text and not
        // where it is on the screen. The two are asked separately: where the line before the
        // region ends, and how far the region itself carries on from there. A tab in either is
        // measured from the column it starts at, and the gutter is part of that column, since the
        // quoted line and the carets under it are written on the same terminal line.
        int from = Math.min(Math.max(start.column() - 1, 0), line.length());
        int to = Math.min(from + sits.sourceSpan(), line.length());
        int at = DisplayColumns.advance(line.substring(0, from), DisplayColumns.width(gutter));
        int span = Math.max(1, DisplayColumns.advance(line.substring(from, to), at) - at);
        out.append(color(caretColor, " ".repeat(at) + "^".repeat(span))).append('\n');
    }

    private String hintLabel(Locale locale) {
        return Messages.get("diag.hint.label", locale) + " ";
    }

    private String color(String code, String text) {
        return useColor ? code + text + RESET : text;
    }
}
