package souther.compiler.diag;

import souther.compiler.source.SourceId;

import java.util.List;

/**
 * A compile error with a source position. Carries an error code (e.g. {@code E1101})
 * when one applies (spec §compile-errors), otherwise a bare message for lex/parse errors.
 *
 * <p>The exception now wraps a structured {@link Diagnostic}. {@link #getMessage()} still returns the
 * one-line {@code line:col code: message} form (so existing callers and tests are unchanged), while a
 * renderer can take {@link #diagnostic()} and produce the Elm-style snippet or JSON. A site that has
 * not been moved onto a catalog key throws with a literal message, wrapped as a
 * {@link Diagnostic#literal literal} diagnostic.
 *
 * <p>Most phases stop at the first error, so the exception carries one diagnostic. A phase that finds
 * several independent errors at once — every failing {@code example} row — throws them all through
 * {@link #diagnostics()}, and the CLI and the annotation processor print each. {@link #diagnostic()}
 * is then the first: the one a single-diagnostic caller reads.
 */
public class CompileException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    /**
     * What this error found, each with what the compile can say about where it is listed.
     *
     * <p>One list and not two. It was a list of diagnostics beside a list of sources with one entry
     * per diagnostic, which is a pair kept in step by hand: two builders checked the sizes matched
     * and every reader indexed both by the same {@code i}. A diagnostic and the file it is listed
     * under are one thing to carry, and {@link Located} is that thing.
     */
    private final transient List<Located> reported;

    /** Throws with a fully structured diagnostic (a migrated site). */
    public CompileException(Diagnostic diagnostic, String legacyMessage) {
        this(List.of(new Located(diagnostic, ReportContext.NONE)), legacyMessage);
    }

    /**
     * The same, wording the message the way {@link #of} words it — for an error that carries
     * something beside its diagnostic and so cannot be made by that factory.
     *
     * <p>The wording is here and not repeated there. What an error says is what its diagnostic says,
     * and a subclass left to word its own would be a second sentence for one rule, differing from
     * the first the day either is changed.
     */
    protected CompileException(Diagnostic diagnostic) {
        this(diagnostic, format(positionOf(diagnostic), diagnostic.code(),
                DiagnosticRenderer.legacyBody(diagnostic)));
    }

    private CompileException(List<Located> reported, String legacyMessage) {
        super(legacyMessage);
        this.reported = List.copyOf(reported);
    }

    /**
     * A throw site, whose {@link #getMessage()} is what the diagnostic says, rendered in English.
     *
     * <p>The body used to be handed in beside the diagnostic, so every site wrote its message twice
     * — once as a catalog key with its values, once as a Java string — and the two drifted. What a
     * rule says is in the catalog, and this is where it is read.
     */
    public static CompileException of(Diagnostic diagnostic) {
        return new CompileException(diagnostic);
    }

    /**
     * A throw site that found several errors in one pass and reports each; {@code diagnostics} must
     * not be empty. The first drives {@link #pos()}, {@link #code()} and the one-line
     * {@code legacyBody} prefix; a renderer walks {@link #diagnostics()} and prints them all.
     */
    public static CompileException ofAll(List<Diagnostic> diagnostics, String legacyBody) {
        if (diagnostics == null || diagnostics.isEmpty()) {
            // An error with nothing to report is a caller that failed to check whether it found
            // anything; say so here rather than at the `get(0)` below.
            throw new IllegalArgumentException("a compile error carries at least one diagnostic");
        }
        Diagnostic first = diagnostics.get(0);
        return new CompileException(
                diagnostics.stream().map(d -> new Located(d, ReportContext.NONE)).toList(),
                format(positionOf(first), first.code(), legacyBody));
    }

    /**
     * One error, with the file it is listed under.
     *
     * <p>What {@link #of(Diagnostic)} answers with and the file as well. A caller that has the file
     * and hands over the bare diagnostic drops it, and where a row is written is not always the
     * module it contributes to — an {@code examples for} file is a file of its own.
     */
    public static CompileException ofReported(Located reported) {
        Diagnostic only = reported.diagnostic();
        return new CompileException(List.of(reported),
                format(positionOf(only), only.code(), DiagnosticRenderer.legacyBody(only)));
    }

    /**
     * Several errors found across several sources, each carrying the file it is listed under — a
     * multi-module compile reporting every module's failing examples rather than the first module's.
     */
    public static CompileException ofAllReported(List<Located> reported, String legacyBody) {
        if (reported == null || reported.isEmpty()) {
            throw new IllegalArgumentException("a compile error carries at least one diagnostic");
        }
        Diagnostic first = reported.get(0).diagnostic();
        return new CompileException(List.copyOf(reported),
                format(positionOf(first), first.code(), legacyBody));
    }

    /**
     * Where the leading diagnostic points, as a single position, or none where it points nowhere.
     *
     * <p>Read off what it points at rather than off a region it might not have. A report about a
     * module, and one whose code is out of sight, have no position at all; one in a text this
     * compile could not name has numbers, and they are numbers of that text — which is what the
     * one-line message below says them as, and is not a place anything sends a reader to.
     */
    public SourcePos pos() {
        return positionOf(diagnostic());
    }

    private static SourcePos positionOf(Diagnostic d) {
        return d == null ? null : switch (d.primary()) {
            case Primary.InSource(DiagnosticPlace.InSource place) -> place.region().start();
            case Primary.InAnUnnamedText(UnnamedRegion where) -> where.region().start();
            case Primary.Unavailable _, Primary.Nowhere _ -> null;
        };
    }

    public String code() {
        Diagnostic d = diagnostic();
        return d == null ? null : d.code();
    }

    /** The structured diagnostic behind this error, for a renderer; the first when the error carries
     * several. */
    public Diagnostic diagnostic() {
        List<Diagnostic> all = diagnostics();
        return all.isEmpty() ? null : all.get(0);
    }

    /** Every diagnostic this error carries, in the order they were found — one for most phases,
     * several when a pass reports each of its independent failures. Empty when the structured form
     * did not survive (the field is transient, so a deserialized error has only its message). */
    public List<Diagnostic> diagnostics() {
        return Located.diagnosticsOf(locatedDiagnostics());
    }

    /**
     * Which of the sources a multi-module compile was handed this error came from, or null when it
     * names none. Untagged covers a single-source compile — where the caller knows the file — and an
     * error a linked compile cannot pin on one source, such as a failing example the compiler merged
     * in from an {@code examples for} file. A caller reads null as "quote no line", never as "the
     * first source".
     */
    public SourceId sourceId() {
        return sourceIdOf(0);
    }

    /** Which source the {@code i}-th of {@link #diagnostics()} is listed under, or null when the
     *  compile could name none. A renderer walking the list quotes each diagnostic's own file. */
    public SourceId sourceIdOf(int i) {
        List<Located> all = locatedDiagnostics();
        return i >= all.size() ? null : all.get(i).context().filedUnder().orElse(null);
    }

    /** Every diagnostic this error carries, each with what the compile can say about where it is
     *  listed — what a renderer walks, and the shape a warning arrives in too, so one loop renders
     *  both. */
    public List<Located> locatedDiagnostics() {
        return reported == null ? List.of() : reported;
    }

    /**
     * This error, also carrying {@code more} — what a caller that collected several errors and has
     * one of them as the exception a pass raised answers with.
     *
     * <p>Built from this one rather than from its diagnostic, so the message stays the message this
     * error was raised with. A pass that words its own summary — the one that reports every failing
     * {@code example} row — words it over what it found, and rendering the leading diagnostic again
     * here would answer with a sentence about one row instead.
     *
     */
    public CompileException alsoReporting(List<Located> more) {
        if (more.isEmpty()) {
            return this;
        }
        List<Located> all = new java.util.ArrayList<>(locatedDiagnostics());
        all.addAll(more);
        CompileException joined = new CompileException(all, getMessage());
        joined.setStackTrace(getStackTrace());
        return joined;
    }

    /**
     * The same error, tagged with the source being compiled when it was thrown. The first tag wins:
     * an inner phase that already named its source keeps it, so a surrounding loop may tag freely.
     */
    public CompileException inSource(SourceId sourceId) {
        List<Located> all = locatedDiagnostics();
        if (sourceId == null || all.stream().allMatch(one -> one.context().filedUnder().isPresent())) {
            return this;   // already named, or nothing to name it with
        }
        CompileException tagged = new CompileException(
                all.stream().map(one -> one.context().filedUnder().isPresent() ? one
                        : new Located(one.diagnostic(), ReportContext.inFile(sourceId))).toList(),
                getMessage());
        tagged.setStackTrace(getStackTrace());
        return tagged;
    }

    private static String format(SourcePos pos, String code, String message) {
        String where = pos == null ? "" : pos + " ";
        String c = code == null ? "" : code + ": ";
        return where + c + message;
    }
}
