package souther.compiler.diag;

import souther.compiler.diag.msg.WrittenAtMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Turns a {@link Diagnostic} into text: {@link HumanRenderer} for people, {@link JsonRenderer} for
 * tools. The source snippets come from {@code sources}, which answers null for a source that is
 * unavailable (e.g. a file that cannot be read); the renderer then omits the quoted line. */
public interface DiagnosticRenderer {

    /**
     * One diagnostic, quoted from the files its regions are in. {@code located} carries the source
     * the primary region is in; a secondary that names one of its own is quoted from that.
     */
    String render(Located located, SourceContextResolver sources, Locale locale);

    /**
     * One diagnostic written wholly in one file, quoted from {@code src}. Everything it points at is
     * in that file, so there is nothing to resolve and nothing to name.
     */
    default String render(Diagnostic d, SourceContext src, Locale locale) {
        return render(new Located(d, ReportContext.ofTheTextItself(src)), id -> src, locale);
    }

    /**
     * Renders each of {@code located}, one string per diagnostic — never one string for the list, so
     * a JSON caller prints one object per line rather than an array.
     *
     * <p>One string per diagnostic and not per file: a problem written in two of them is one
     * diagnostic here, quoted at both of the places it is written. What is said twice in an editor,
     * which puts a marker in each file, is said once on a terminal, which is read top to bottom.
     */
    static List<String> renderAll(List<Located> located, SourceContextResolver sources,
                                  DiagnosticRenderer renderer, Locale locale) {
        List<String> rendered = new ArrayList<>(located.size());
        for (Located one : located) {
            rendered.add(renderer.render(one, sources, locale));
        }
        return List.copyOf(rendered);
    }

    /**
     * The body a {@code CompileException} builds its {@code getMessage()} from — that text with
     * the position and the code put in front of it.
     *
     * <p>There is no language to pass. An adapter prints that text only for an exception carrying no
     * diagnostic, and the two sites that build this always supply one, so nothing rendering for a
     * reader ever reads it; what reads it is {@code getMessage()} itself, in a test or an embedding
     * caller. What it has to do is not change when the language a reader is answered in is decided
     * again — a different requirement from being English, and the reason the caller does not get to
     * choose one. English is what this text is.
     */
    static String legacyBody(Diagnostic d) {
        Locale written = Locale.ENGLISH;
        StringBuilder said = new StringBuilder(body(d, written));
        if (d.diff() != null) {
            said.append(' ').append(Messages.get("diag.diff.found", written))
                    .append(' ').append(d.diff().actualType())
                    .append(' ').append(Messages.get("diag.diff.expected", written))
                    .append(' ').append(d.diff().expectedType());
        }
        for (Note note : d.notes() == null ? List.<Note>of() : d.notes()) {
            said.append(' ').append(Messages.render(note.said(), written));
        }
        if (d.repair() != null) {
            said.append(' ').append(Messages.get("diag.suggestion", written, d.repair().with()));
        }
        return said.toString();
    }

    /**
     * The message body, from what it says or the compatibility literal, with what its caret stands
     * in for said after it.
     *
     * <p>Here because this is what every reader reads: the terminal, the JSON, an editor, and the
     * text an exception carries. A caret that stands in for code written out of sight is in the
     * caller's file and is not where the code is, and a body that did not say so reads as a claim
     * about the line under it — which is what {@code E2011} made of a call that constructs nothing.
     * Said off the caret rather than at the sites that report one, so a rule checked on a copied body
     * says it without being written to.
     */
    static String body(Diagnostic d, Locale locale) {
        java.util.Objects.requireNonNull(locale, Messages.NEEDS_A_LANGUAGE);
        String said = d.literalMessage() != null ? d.literalMessage()
                : d.said() == null ? "" : Messages.render(d.said(), locale);
        // Asked of what the report points at rather than of a position, because one of the answers
        // is that there is no position and the code is written somewhere this compile cannot show.
        // Read off the position alone that came back as nothing to say, and a reader was left with a
        // sentence about a place, no place, and no account of why.
        return switch (d.primary()) {
            case Primary.Unavailable(SourceProvenance from) ->
                    with(said, new WrittenAtMessage.TheCodeIsWrittenWhereThisCompileCannotShowIt(
                            from.reachedBy()), locale);
            case Primary.InSource(DiagnosticPlace.InSource place) ->
                    qualified(said, place.region().start(), locale);
            case Primary.InAnUnnamedText(UnnamedRegion where) ->
                    qualified(said, where.region().start(), locale);
            // Nothing pointed at is nothing to qualify: the sentence is about a file or about the
            // compile, and a clause saying where the code is written would be about nothing.
            case Primary.Nowhere _ -> said;
        };
    }

    /**
     * {@code said}, with what the place it is shown against stands in for said after it.
     *
     * <p>Every sentence a report puts beside a coordinate, and not only the one under the caret. A
     * label on a second region is a sentence about the place it points at as much as the body is,
     * and a second region pointing into a copied body is at a call in the caller's file — so a
     * label saying what is there says it of code that is somewhere else. Which is what the caret
     * did before this was said off it at all, repeated one region over the moment a rule started
     * pointing at a guard.
     *
     * <p>So it is a rule of rendering rather than a thing the body does. A renderer that shows text
     * against a place calls this; a renderer written later that does not is missing the same
     * sentence the first one was.
     *
     * @param at where the text is shown against, or null where it is shown against nothing — there
     *        being no claim about a place to qualify
     */
    static String qualified(String said, SourcePos at, Locale locale) {
        java.util.Objects.requireNonNull(locale, Messages.NEEDS_A_LANGUAGE);
        // Both arms that say the code is elsewhere, because this sentence is about where the code is
        // written and the two agree about that. What they differ about is whether there was anywhere
        // to point, which is the caret's question and not this one.
        if (at == null || !(Citation.of(at) instanceof Citation.Elsewhere out)) {
            return said;
        }
        return with(said, new WrittenAtMessage.TheCodeIsWrittenOutOfSight(
                out.provenance().reachedBy()), locale);
    }

    /** {@code said} with {@code about} after it, or {@code about} alone where there was nothing to
     *  put it after. */
    private static String with(String said, WrittenAtMessage about, Locale locale) {
        String rendered = Messages.render(about, locale);
        return said.isEmpty() ? rendered : said + " " + rendered;
    }

    /**
     * What a label with nothing to point at says: what it says, and where that code is.
     *
     * <p>Its own wording. The sentence a caret carries ends by saying what the place under it is,
     * and there is no place under this one — a label about a clause of a published module points at
     * nothing, so explaining a caret would be explaining something the reader cannot see.
     */
    static String saidAbout(DiagnosticView.Unquotable unquotable, Locale locale) {
        return with(Messages.render(unquotable.said(), locale),
                new WrittenAtMessage.TheCodeIsWrittenWhereThisCompileCannotShowIt(
                        unquotable.place().provenance().reachedBy()), locale);
    }
}
