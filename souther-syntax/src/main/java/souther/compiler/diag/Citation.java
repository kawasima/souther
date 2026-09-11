package souther.compiler.diag;

import souther.compiler.source.SourceId;

import java.util.LinkedHashMap;
import java.util.SequencedMap;

/**
 * Where the code something is about is written, as a report is entitled to say it.
 *
 * <p>A position was put to two uses and only one of them could be wrong. Sending a reader to a
 * position in a file the reader holds — a caret, a range, an editor jumping — is right whatever code
 * is at it, because it is where this compile met that code. Saying the code is <em>written</em>
 * there is right only where it is. Nothing distinguished the two, so every surface that wanted the
 * first published the second: an unreached arm of {@code List.filter} was reported at the caller's
 * {@code 15:23} by the adequacy report in both of its renderings and by the JSON a diagnostic is
 * read from, and one renderer out of four said what the position stood for.
 *
 * <p>And a position in a text the reader does not hold is not the first either, which is the other
 * half and is why this has five arms rather than two: whether a reader can be sent somewhere is a
 * question about the text, and what is written there is a question about the code.
 *
 * <p>So the two are separate values. {@link SourcePos} is where this compile placed a node, and every
 * pass reads it freely. This is the other one, and it is what a report holds: a coverage site, a
 * finding, a reason carries this rather than a position, so a surface written later is handed the
 * answer rather than the raw place.
 *
 * <h2>The five arms</h2>
 *
 * <p>The same two questions a {@link Placement} answers, said in what a report may write. Whether
 * this compilation can show a reader the text the position is in, and whether the code the position
 * names is written at it:
 *
 * <ul>
 *   <li>{@link Written} — the file is one the reader holds, and the code is at the position.
 *   <li>{@link Unplaced} — the code is at the position, and this compilation cannot name the text
 *       it is in. An editor's unsaved buffer is one, and so is a snippet somebody parsed.
 *   <li>{@link Reached} — the file is one the reader holds, and what is written there is a call the
 *       code was reached through. {@link Elsewhere#provenance()} says where the code is.
 *   <li>{@link UnplacedElsewhere} — the code is elsewhere, and the position is in a text this
 *       compilation cannot name: only whoever handed that text over can use it.
 *   <li>{@link OutOfSight} — the code is elsewhere and there is nothing to point at: the position is
 *       in a text put back together out of what a module published, which no reader holds.
 * </ul>
 *
 * <p>{@link Reached} and {@link OutOfSight} used to be one arm, whose accessor said its position was
 * "a place in a file the reader holds" and was handed the second case's position — a line of a text
 * nobody has. Every surface that read it was right about the reports that had been anchored to an
 * import line and wrong about any that had not, and nothing told the two apart.
 *
 * <p>No accessor spans a case that has a position and one that does not. A reader that wants the
 * position — to put a caret at it — says which case it is in first, and having said so has seen that
 * the others exist. An {@code at()} on this interface would be the shortest way back to the defect:
 * every caller would write it, the sum would decorate nothing, and the cases with nothing to point
 * at would go unread again.
 *
 * <p>{@link Elsewhere} spans the three arms that say the code is elsewhere, and carries only what
 * they agree about: where that code is. Which is the safe half. A renderer qualifying a sentence with
 * "the code is written in {@code List.filter}" says the same thing whether or not there was somewhere
 * to point, and would otherwise ask the question twice.
 *
 * <p>Built one way. {@link #of} asks the position's {@link Placement} to project itself, which is the
 * only thing a placement will do for a caller and is package-private so that it stays so, and the arms
 * cannot be built or implemented from outside this package. So there is no independent construction
 * path: every citation is a projection of what a position already carries.
 *
 * <p>Which is a rule about where a statement comes from and not a claim that provenance is a secret.
 * A caller may stamp a position itself and project that, and may compare a provenance against one it
 * builds. What it may not do is read the declaration off a position and write a place of its own,
 * which is the defect this exists to close.
 */
public sealed interface Citation permits Citation.Written, Citation.Unplaced, Citation.Elsewhere {

    /** The code is written at {@link #at()}, in a file this compilation holds — so the position is
     *  both where to send a reader and where the code is. */
    sealed interface Written extends Citation permits WrittenCitation {
        SourcePos at();
    }

    /**
     * The code is written at {@link #at()}, in a text this compilation cannot name.
     *
     * <p>The line and the column are real: somebody wrote that code and a parser read it there. What
     * is missing is which text, so a reader cannot be sent to it by this alone and whoever is showing
     * the report says which text is being read. That is where an editor's unsaved buffer arrives, and
     * it is a state a compile has rather than a state left over from one.
     *
     * <p>Not a {@link Written} with a piece missing. A report that quoted this as though the file were
     * known would quote whatever sat at those numbers in the file the reader had in mind, which is the
     * defect one arm over.
     */
    sealed interface Unplaced extends Citation permits UnplacedCitation {
        SourcePos at();
    }

    /**
     * The code is written where {@link #provenance()} says, which this compile has no file for.
     *
     * <p>What the three arms under this agree about, and the whole of it. Whether there is anywhere
     * to send a reader — a file the reader holds, a position only whoever handed the text over can
     * use, or nothing at all — is the other question and is what tells them apart.
     */
    sealed interface Elsewhere extends Citation permits Reached, UnplacedElsewhere, OutOfSight {

        /** Where that code came from, and the name a reader here reaches it by:
         *  {@code List.filter}. */
        SourceProvenance provenance();
    }

    /**
     * The code is out of sight and {@link #at()} is where this compile met it — the call a body was
     * spliced into, the import line a report was moved to.
     *
     * <p>A place in a file the reader holds, and the type says so: the position's placement names a
     * source, by construction, because a splice keeps whichever text it spliced into and this arm is
     * only reached from one that had a name.
     */
    sealed interface Reached extends Elsewhere permits ReachedCitation {
        SourcePos at();
    }

    /**
     * The code is out of sight and {@link #at()} is a position in a text this compilation cannot
     * name — a buffer the caller handed over, with a body from elsewhere spliced into it.
     *
     * <p>The caller can point at it and nobody else can, which is what tells this from
     * {@link Reached}. What tells it from {@link Unplaced} is that there is a declaration to name,
     * so a report still says where the code is written even though it cannot say which file the
     * reader is looking at.
     */
    sealed interface UnplacedElsewhere extends Elsewhere permits UnplacedElsewhereCitation {
        SourcePos at();
    }

    /**
     * The code is out of sight and the position is inside it, in a text put back together out of what
     * a module published.
     *
     * <p>No position, because there is no place. Line 4 of that text exists and no reader holds a file
     * those numbers are of, so what a report says here is where the code came from instead of where to
     * look — which is what {@link DiagnosticPlace.Unavailable} is downstream of this.
     */
    sealed interface OutOfSight extends Elsewhere permits OutOfSightCitation {
    }

    /**
     * What a report may say about where {@code pos} names code written.
     *
     * <p>Of the position, and a citation holds that position with nothing beside it. The position has
     * said which text it is in since positions were gathered across files, so an identity held next to
     * one is a second answer to a question already answered, the two can disagree, and a citation
     * holding both is a place that is two places — which is how a report came to write one source's
     * identity beside another source's line and column.
     */
    static Citation of(SourcePos pos) {
        return pos.placement().cite(pos);
    }

    /**
     * What a document writes about this, as the fields it writes them under.
     *
     * <p>One writer for two documents. The adequacy report and the JSON a diagnostic is read from
     * both say this, and a consumer reading one of them should not have to learn a second vocabulary
     * to read the other — which is what would have happened, the two being rendered in different
     * modules by different machinery.
     *
     * <p>Written on every arm, {@code here} included. The field is added to a schema that already has
     * documents without it, so its absence has to go on meaning "this document does not say" — and an
     * emitter that wrote it only where the answer was interesting would put "the code is here" and
     * "written before anyone asked" under one silence.
     *
     * <p>Two words for five arms, and that is the field's question rather than an omission: this says
     * where the code is written, and the arms that differ about where to send a reader agree about
     * that.
     */
    default SequencedMap<String, String> writtenAtFields() {
        return switch (this) {
            case Written _, Unplaced _ -> hereFields();
            case Elsewhere out -> outOfSightFields(out.provenance());
        };
    }

    /** The same fields for code that is written where a report is pointing. */
    static SequencedMap<String, String> hereFields() {
        SequencedMap<String, String> fields = new LinkedHashMap<>();
        fields.put("kind", "here");
        return fields;
    }

    /**
     * The same fields for code out of sight, said of a provenance rather than of a citation.
     *
     * <p>For a report with nowhere to point: a label about a clause of a module this compile holds
     * no file for has the provenance and no position to project. One writer, so the words a document
     * uses for "the code is elsewhere" are the same whether or not there was a caret.
     */
    static SequencedMap<String, String> outOfSightFields(SourceProvenance provenance) {
        return outOfSightFields(provenance.reachedBy());
    }

    /** The same, said of the name a reader reaches the code by rather than of the provenance that
     *  gives it — for a place that has been projected to what a document writes and no longer
     *  holds how this compiler came to be pointing at it. */
    static SequencedMap<String, String> outOfSightFields(String reachedBy) {
        SequencedMap<String, String> fields = new LinkedHashMap<>();
        fields.put("kind", "outOfSight");
        fields.put("declaration", reachedBy);
        return fields;
    }

    /**
     * This as a line of a report about {@code sectionSource}, with the sources under the names
     * {@code names} gives them.
     *
     * <p>A line and a column are a place only beside a file. They are written on their own where the
     * section already names the file, and with the file where it does not — a position from another
     * source, printed bare, points at whatever happens to sit at those numbers in the one the reader
     * has in mind.
     *
     * <p>An out-of-sight citation says where the code is and stops there. It used to say "reached at"
     * and the numbers, which was true of a report anchored to an import line and was a line of nothing
     * for one that had not been.
     */
    default String said(SourceRendering rendering, SourceId sectionSource) {
        return switch (this) {
            case Written written -> place(written.at(), rendering, sectionSource);
            case Unplaced unplaced -> place(unplaced.at(), rendering, sectionSource);
            case Reached reached -> "`" + reached.provenance().reachedBy() + "`, reached at "
                    + place(reached.at(), rendering, sectionSource);
            case UnplacedElsewhere out -> "`" + out.provenance().reachedBy() + "`";
            case OutOfSight out -> "`" + out.provenance().reachedBy() + "`";
        };
    }

    /** Never handed a null: every arm that has a place requires it. The tolerance the report's own
     *  writer used to have is gone rather than carried over, and a fallback standing in for it would
     *  read as a case somebody had thought about. */
    private static String place(SourcePos at, SourceRendering rendering, SourceId sectionSource) {
        String sits = String.valueOf(rendering.layouts().resolve(at));
        if (!(at.quotedFrom() instanceof QuotedFrom.ASourceThisCompileHolds(SourceId file))
                || file.equals(sectionSource)) {
            return sits;
        }
        return rendering.names().nameOf(file) + ":" + sits;
    }
}
