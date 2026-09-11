package souther.compiler.query;

import souther.compiler.partition.BorderObligationPoint;
import souther.compiler.partition.GenerationOutcome;
import souther.compiler.partition.Generator;
import souther.compiler.partition.ObligationIdentity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.SequencedMap;

/**
 * The rows a module is owed at the points of its lines, whosever the line is: one answer per point
 * of each authored line, a body's own and its declarations' alike.
 *
 * <p><b>The authority on how many points a line is resolved at, and not on how many rows go out.</b>
 * A point is one piece of work however many positions carry the type, and that is a rule about what
 * is owed rather than about how a block is laid out — so it is settled here, where the search is
 * resolved, and every reader below is a projection of this map. Left to the block, one authored line
 * comes out as up to four answers, one per reading that composed its own.
 *
 * <p>How many rows a person is offered is another count and is made further down: two points can be
 * answered by one row, and a block offers a row once per set of inputs ({@code GeneratedRows}). One
 * resolution per point does not mean one row per point.
 *
 * <p>In the order the lines were read, so that what a block prints is read against the one before
 * it.
 *
 * @param scope    which readings the request was about, which is also what settles the lines it is
 *                 about: a line no reading of the request's carries is not a question this was put.
 *                 Carried rather than left to each reader, because a reader deciding it a second
 *                 time is a reader that can decide it differently
 * @param resolved one answer per point of each line the scope admits
 */
public record BorderAccount(String module, GenerationScope scope,
        SequencedMap<BorderObligationPoint, Answer> resolved) {

    /**
     * What a generation came to at one point, beside the point it came to it at.
     *
     * <p>The point and not words about it. What a row here is owed for, who owes it and what it
     * asks are the point's own answers, and a word kept beside them is a second answer to one of
     * them — one that has to be right for every point this account holds. A clause names what it is
     * about and a comparison in a body names nothing, so a sentence made for every point makes one
     * up wherever the line is a body's.
     *
     * <p>So a reader that writes about a point asks the point, and does it where its own words are
     * true. {@code declaredAxis} is the one word that is not the point's: what the declaration wrote
     * its line on, which exists exactly where a declaration drew the line and nowhere else.
     *
     * @param point        what the readings of the line came to
     * @param declaredAxis what the declaration wrote the line on, or null where no declaration drew
     *                     it — a body's own comparison is on nothing anybody named
     * @param resolution   what a search of the point's readings came to
     */
    public record Answer(BorderObligationPointAssessment point, String declaredAxis,
                         PointResolution resolution) {

        public Answer {
            if (point == null || resolution == null) {
                throw new IllegalArgumentException("an answer is to something, and is an answer");
            }
            // Both ways round and in one place, so that neither side can be read as the other's
            // absence. A word where no declaration drew the line is a name made up for a body's
            // comparison; none where one did is a declaration's own line left unsayable.
            if ((declaredAxis == null)
                    != point.id().owedToTheDeclaration().isEmpty()) {
                throw new IllegalArgumentException("a line is on what a declaration wrote exactly"
                        + " where a declaration drew it: " + point.point() + " " + declaredAxis);
            }
        }

        /** What this point asks of a row, as a report writes it — of a declaration's line only. */
        String said() {
            if (declaredAxis == null) {
                throw new IllegalStateException("what a body's own line is on is not something"
                        + " anybody wrote: " + point.point());
            }
            return point.said(declaredAxis);
        }

        /** Whether a row here is the body's own to write, which is what a block offers first. */
        boolean owedByTheBody() {
            return point.owedToTheReading();
        }
    }

    public BorderAccount {
        resolved = java.util.Collections.unmodifiableSequencedMap(new LinkedHashMap<>(resolved));
    }

    /**
     * The rows themselves, under the behavior each was composed for.
     *
     * <p>Where a row goes and not who owes it. A row is written in one behavior's terms, so it
     * belongs in that behavior's block; what it settles is the declaration's line, which is what the
     * words over it say.
     *
     * <p>A projection and nothing more — no row is chosen here and none is dropped. Which reading
     * composed the row was settled by the search, and a renderer that grouped differently would
     * still be handed the same row for each point. Two points answered by one row are two entries
     * here and one row where the block is written, which is where that count is made.
     *
     * <p><b>The body's own lines first, and the declarations' after them.</b> An order, because what
     * is offered turns on one: a reduction keeps the earlier of two rows that answer the same
     * thing, so that an edit further down the model does not move what is offered above it
     * ({@link Settlements#keeping}). Stated here rather than left to the order the points were
     * gathered in, which is a fact about a walk and about nothing a reader can see.
     *
     * <p>Which is why it is here and not in what the account holds. What is owed and what became of
     * it are the same whichever way they are listed; this is the listing.
     */
    public SequencedMap<String, List<Generator.GeneratedRow>> rowsByCarrier() {
        SequencedMap<String, List<Generator.GeneratedRow>> out = new LinkedHashMap<>();
        take(out, true);
        take(out, false);
        return java.util.Collections.unmodifiableSequencedMap(out);
    }

    /** The rows of the points the body owes, or of the rest, under the behavior that composed each. */
    private void take(SequencedMap<String, List<Generator.GeneratedRow>> out, boolean theBodys) {
        resolved.forEach((_, answer) -> {
            if (answer.owedByTheBody() == theBodys
                    && answer.resolution() instanceof PointResolution.Generated(var at, var row)) {
                out.computeIfAbsent(at.behavior(), _ -> new ArrayList<>()).add(row);
            }
        });
    }

    /**
     * What a block may say about each line nothing composed a row for, whether or not a finding
     * names the point.
     *
     * <p>Beside {@link #dispositions} and not through it. A finding stands where the point was
     * measured and missed <em>and</em> something showed a row can be written there, so a line the
     * search failed at with nothing yet promising a row raises none — and a block that said only
     * what the findings said would go quiet about work it had just tried and failed to do.
     *
     * <p><b>The conclusion is drawn here and handed over, not left to be drawn again.</b> What a
     * search of one reading came to is a fact about that reading; that no row can be written at the
     * line is a claim about every one of them, and which of the two a reader is holding was
     * something the reader had to know. Handed on as a flat list of what the readings said, the one
     * sentence a reader may act on is printed under the declaration's own name for a line one
     * reading has merely refused.
     */
    public SequencedMap<BorderObligationPoint, Unmet> unmet() {
        SequencedMap<BorderObligationPoint, Unmet> out = new LinkedHashMap<>();
        resolved.forEach((at, answer) -> {
            // The lines the declarations own, and not a body's own. A sentence here says what is
            // owed and who owes it, and what a line is owed at is written on the quantity the rule
            // named — which a clause has and a comparison in a body has not: what it was drawn on is
            // the term each reading met it at, and there is one of those per reading. A body's line
            // is said where the lines it drew are said, at each of the places it was drawn.
            if (!answer.owedByTheBody()
                    && answer.resolution() instanceof PointResolution.Unresolved _) {
                out.put(at, unmet(answer));
            }
        });
        return java.util.Collections.unmodifiableSequencedMap(out);
    }

    /**
     * What is left to say about one point nothing composed a row for.
     *
     * <p>The one place the gate is passed. Every reader of this holds an answer whose shape says
     * what it may be spelled as, so none of them carries a copy of the rule.
     *
     * <p>Reachable from the package for the same reason: what it decides is held directly, rather
     * than through a rendering of it or a compilation arranged to produce the evidence.
     *
     * <p>The coverage is read off the answer rather than handed in beside it. Taken as a second
     * argument, a caller could pass one walk's evidence beside another walk's answer, and the two
     * would have to be kept in step by whoever called.
     */
    Unmet unmet(Answer answer) {
        // The declarations that drew the line, and what the line asks in the words they wrote it
        // in. Both are the point's, and both are sayable because the line is a clause's: what a
        // newtype wraps is spelled `value` in every declaration, and every reading of the line
        // meets that same quantity under its own name.
        return unmet(new FindingSubject.OfADeclaration(answer.point().ownersIn(module)).named(),
                answer.said(), answer.resolution());
    }

    /**
     * The same, of the words and the walk alone.
     *
     * <p>Reachable from the package because what it decides is held directly, rather than through a
     * rendering of it or a compilation arranged to produce the evidence.
     */
    static Unmet unmet(String owedBy, String said, PointResolution resolution) {
        if (!(resolution instanceof PointResolution.Unresolved(var coverage))) {
            throw new IllegalStateException(
                    "what is left to say about a point a row was composed at, or none was looked"
                            + " for at: " + resolution);
        }
        List<At> came = new ArrayList<>();
        coverage.came().forEach((reading, search) -> {
            switch (search) {
                case SearchCoverage.ReadingSearch.Attempted(var why) ->
                        came.add(new At.Searched(reading, why));
                // Kept and not skipped. A reading this run could not search is something that
                // happened to it, and dropping it here would put back the absence the coverage was
                // made total to take out: a reader would see the readings that answered and no sign
                // that another was asked.
                case SearchCoverage.ReadingSearch.Unavailable _ ->
                        came.add(new At.CouldNotBeSearched(reading));
                // The request never asked about it, so nothing was spent on it and there is
                // nothing to report. Its absence from this is what the scope already says.
                case SearchCoverage.ReadingSearch.OutOfScope _ -> { }
            }
        });
        if (came.isEmpty()) {
            return new Unmet.NothingWasSearched(owedBy, said);
        }
        return coverage.provesTheLineCannotBeWritten()
                ? new Unmet.TheLineCannotBeWritten(owedBy, said, List.copyOf(came))
                : new Unmet.WhatTheReadingsCameTo(owedBy, said, List.copyOf(came));
    }

    /**
     * What one reading of the line came to, and which reading it was.
     *
     * <p>Two shapes, because a search that ran and a search that could not be made are different
     * news and only the first says anything about the point. Held as one with a reason that might
     * be missing, the second reads as a search that came back empty.
     */
    public sealed interface At {

        /** Which reading this is about. */
        BorderObligationPointAssessment.Reading reading();

        /** The search ran and no row came of it, in its own words. */
        record Searched(BorderObligationPointAssessment.Reading reading,
                        Generator.UnresolvedCombination why) implements At {}

        /** The search of this reading had no answer to give, so nothing was looked for at it. A
         *  fact about this run, and never about the point. */
        record CouldNotBeSearched(BorderObligationPointAssessment.Reading reading) implements At {}
    }

    /**
     * What is left to say about a line no row was composed at.
     *
     * <p>Three shapes because they license three different sentences, and a reader that had one
     * shape for all of them would be choosing which. Only the first is about the line.
     */
    public sealed interface Unmet {

        /** The declaration that drew the line. */
        String owedBy();

        /** What the point asks of a row, in the words the declaration wrote it in. */
        String said();

        /**
         * Every reading was walked and every one of them proves there is nothing at the point.
         *
         * <p>The one arm a sentence about the line may be written from. What each reading said is
         * kept beside it rather than folded, because they prove it of different positions.
         *
         * <p>Every entry here is a search that ran: the gate this arm passes is every reading having
         * been walked, so a reading this run could not search cannot be in one.
         */
        record TheLineCannotBeWritten(String owedBy, String said, List<At> proving)
                implements Unmet {}

        /**
         * What the readings came to, which is not a claim about the line.
         *
         * <p>Some of them may say the rules leave nothing at their own position — and that is what
         * it says: of that position, whose bounds every other rule reaching it takes part in. Said
         * of the line, a reader is told a row cannot be written where another reading writes one.
         */
        record WhatTheReadingsCameTo(String owedBy, String said, List<At> came) implements Unmet {}

        /** The request walked no reading of it, so nothing was looked for and nothing is known. */
        record NothingWasSearched(String owedBy, String said) implements Unmet {}
    }

    /**
     * What the generator can do about each finding a declaration's line raised.
     *
     * <p>Read off the same resolutions the rows are, so the two cannot disagree. Asked separately,
     * a block printed a row two lines above a sentence saying nothing offers one.
     *
     * <p>Findings whose point this holds no resolution for are refused rather than skipped. A walk
     * that quietly passed over one would leave an author told nothing about it while the rows above
     * read as though they filled everything.
     */
    public List<Adequacy.GenerationDisposition> dispositions(List<Adequacy.Finding> findings) {
        // Which lines this request was about, read off what it answered rather than asked of the
        // scope again. The domain was settled where the search was; re-derived here, the resolver
        // and the projection are two authorities on one question and are free to answer it
        // differently the day either moves.
        java.util.Set<souther.compiler.partition.BorderObligationId> asked = resolved.keySet()
                .stream().map(BorderObligationPoint::line)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        List<Adequacy.GenerationDisposition> out = new ArrayList<>();
        for (Adequacy.Finding finding : findings) {
            if (!(finding.about() instanceof About.APointOfADeclaredBorder(var owed))) {
                continue;
            }
            if (!asked.contains(owed.debt().id())) {
                continue;   // a line this request was not put a question about
            }
            BorderObligationPoint at = owed.debt().point();
            out.add(new Adequacy.GenerationDisposition(finding,
                    java.util.Optional.of(new ObligationIdentity.OfALine(at)),
                    outcomeForTheLine(at)));
        }
        return List.copyOf(out);
    }

    /**
     * What a generation can do about the point at {@code at}.
     *
     * <p>The one reading of a resolution as an outcome, whoever owes the point. A row a search
     * composed is the row that answers it; a search that composed none says what every reading of
     * the point came to; and a row that already stands where the point is owed is the point's work
     * done.
     *
     * <p>Asked of the line, whosever it is: what an {@code invariant} states is the same wherever
     * the type is carried, and a body's guard on a name every case spreads is one line read under
     * each case. A finding is about the line, and a row standing at it under any reading answers
     * it.
     *
     * <p>Refused rather than answered where this holds no resolution, or holds one saying nothing
     * measured the point. A finding stands where the point was measured and missed, so a
     * measurement that says it was never made is the finding and the account contradicting each
     * other about one point — and a caller that quietly passed over it would leave an author told
     * nothing while the rows beside it read as though they filled everything.
     */
    GenerationOutcome outcomeForTheLine(BorderObligationPoint at) {
        return switch (answerAt(at).resolution()) {
            case PointResolution.Generated(var _, var row) ->
                    new GenerationOutcome.Generated(List.of(row));
            case PointResolution.Unresolved(var coverage) -> cannot(coverage);
            case PointResolution.NoSearch(var cause) -> settledOr(cause, at);
        };
    }

    private Answer answerAt(BorderObligationPoint at) {
        Answer answer = resolved.get(at);
        if (answer == null) {
            throw new IllegalStateException(
                    "a finding about a line this generation was asked about and holds no answer"
                            + " for: " + at);
        }
        return answer;
    }

    /** What a point nothing was looked for at comes to, which is one of the two reasons there was
     *  nothing to look for. */
    private static GenerationOutcome settledOr(PointResolution.Cause cause,
                                               BorderObligationPoint at) {
        return switch (cause) {
            case A_ROW_ALREADY_STANDS -> new GenerationOutcome.ObligationAlreadySettled();
            case NOTHING_MEASURED -> throw new IllegalStateException(
                    "a finding at a point the measurement says was never measured: " + at);
        };
    }

    /** Whether this account holds an answer at {@code at}, for a caller that has findings from
     *  beyond what this request asked about. */
    boolean holds(BorderObligationPoint at) {
        return resolved.containsKey(at);
    }

    /**
     * What a walk that composed nothing says, as an outcome beside a finding.
     *
     * <p>Read off the same answer the block's sentences are, so the two cannot come apart. Every
     * reading that was walked, and never the weakest or the first of them: they come to different
     * things and none of those orders against the rest, so a single one carried here would be
     * carrying the order the walk happened to take.
     *
     * <p>Where the walk searched no reading at all there is nothing any of them said, and the run
     * says that about itself. Read as a search that came back empty, a request that could not look
     * at the one reading it was about would have reported it as the line refusing a row.
     */
    private static GenerationOutcome.CannotGenerate cannot(SearchCoverage coverage) {
        List<Generator.UnresolvedCombination> said = new ArrayList<>();
        coverage.came().forEach((_, search) -> {
            if (search instanceof SearchCoverage.ReadingSearch.Attempted(var why)) {
                said.add(why);
            }
        });
        // Where no reading was searched there is nothing any of them said, and the run says that
        // about itself, naming the readings it was about. Read as a search that came back empty, a
        // request that could look at none of them would have reported it as the line refusing a
        // row; named by the point instead, a line a body drew would be named by a word the model
        // does not have for what it was drawn on.
        return new GenerationOutcome.CannotGenerate(said.isEmpty()
                ? List.of(new Generator.UnresolvedCombination(
                        coverage.came().keySet().stream()
                                .map(each -> each.target().label()).toList(),
                        Generator.UnresolvedCombination.Reason
                                .NO_READING_OF_THE_LINE_COULD_BE_SEARCHED))
                : List.copyOf(said));
    }
}
