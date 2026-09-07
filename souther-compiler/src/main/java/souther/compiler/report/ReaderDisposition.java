package souther.compiler.report;

import souther.compiler.observe.RunSensitivity;
import souther.compiler.query.NotMeasuredReason;
import souther.compiler.query.PartitionEvidence;
import souther.compiler.publish.WeakeningVocabulary;

import java.util.ArrayList;
import java.util.List;

/**
 * Where one thing this report says leaves the person reading it.
 *
 * <p>Beside {@link Subject} and not derived from it. A subject says what an entry is about; this
 * says how far the compiler can take a reader from there, and one subject reaches more than one
 * answer — a behavior is named by a measure nobody made, by a boundary nothing could be worked out
 * for, and by a space too large to walk, and a wider run reaches the last of those and neither of
 * the others. So this is read from the whole of what an entry says.
 *
 * <p><b>What it is not is a remediation.</b> An arm names an operation this compiler knows is
 * appropriate, or a decision only a person can make and the place to make it at. It never names a
 * change to the model as necessary: whether five cases at a position are more than a behavior needs
 * is a judgement, and a compiler that answered it would be spending evidence it does not have. What
 * follows from {@link ReconsiderWhatThisBehaviorNeedsToDistinguish} may be narrowing the input,
 * splitting the behavior, or leaving it exactly as it is, and all three are right answers to
 * different models.
 *
 * <p><b>Nothing publishes this.</b> A consumer keyed on it would be keyed on a vocabulary of
 * guidance, which is what both of the issues behind it declined to put in the document. What it is
 * for is that the sentences this report writes about an entry follow from what the compiler worked
 * out rather than from what a formatter guessed, and that every entry has one — which a
 * {@code switch} with no {@code default} is what holds.
 */
public sealed interface ReaderDisposition {

    /**
     * A run of this compiler allowing more would answer it.
     *
     * <p>Asked first, and of the entry rather than of what it is about. The one thing a reader of an
     * undetermined verdict wants to know is whether measuring again reaches any of it, and a space
     * too large to walk is a number away while a rule this compiler has no reading for is not.
     */
    record WidenTheRun(Subject subject) implements ReaderDisposition {}

    /** The rule that could not be read, and what stopped the reading of it. */
    record LookAtTheRule(Subject subject) implements ReaderDisposition {}

    /**
     * What a measure went without, in the word this document has for it.
     *
     * <p>For everything a weakening names that is neither a rule nor a fork nor an arm: a module
     * whose bodies were not made, a behavior whose boundary nothing worked out, a source nothing
     * was observed from. There is no rule to send a reader to at any of them, and an arm that said
     * there was would be naming a thing the fact never carried.
     */
    record LookAtWhatTheMeasureWentWithout(Subject subject, WeakeningVocabulary said)
            implements ReaderDisposition {}

    /**
     * Why no measurement was made.
     *
     * <p>The reason and not the word a document writes for it. What a person is shown is the
     * sentence that reason already has, and what a consumer matches on is the word; carried as the
     * word, the sentence would have to be written a second time here.
     */
    record LookAtWhyNothingWasMeasured(Subject subject, NotMeasuredReason why)
            implements ReaderDisposition {}

    /** The point, and what did or did not show that a row can be written at it. */
    record LookAtWhatShowedNoRow(Subject subject) implements ReaderDisposition {}

    /** The fork whose occurrences nothing told apart. */
    record LookAtTheFork(Subject subject) implements ReaderDisposition {}

    /**
     * The proof, which is this compiler's and not the model's.
     *
     * <p>Its own arm because it sends a reader somewhere else entirely. Everything else here points
     * at a model or at what a run of this compiler did with one; this says an analysis the numbers
     * were computed with does not hold, and nothing an author writes is the answer to it.
     */
    record LookAtThisCompilersProof(Subject subject) implements ReaderDisposition {}

    /**
     * Whether this behavior needs the distinction a position of its input carries.
     *
     * <p>A decision and not an operation. What the compiler has is that the position holds more
     * classes than this behavior's rules composed, and that combinations those classes take part in
     * are unknown because no row reaches a combination the behavior never tells apart. What that is
     * worth is the author's: a value passed through untouched is as ordinary as an input wider than
     * it needs to be.
     */
    record ReconsiderWhatThisBehaviorNeedsToDistinguish(Subject subject)
            implements ReaderDisposition {}

    /**
     * Nothing further to look at.
     *
     * <p>Not "nothing is owed", which is the other axis and is answered elsewhere. A combination no
     * bar asks for a row at is not owed and may still be worth a look; this says the entry itself
     * leads nowhere further, which is what a point the rules refuse comes to.
     */
    record Settled() implements ReaderDisposition {}

    /**
     * Where one thing holding a verdict open leaves a reader.
     *
     * <p>The sensitivity first, then the entry, then what it is about. A wider run reaching it is an
     * operation this compiler knows is appropriate, and it is the same answer whatever the entry is
     * about — so it is asked once, above, rather than repeated below it.
     *
     * <p>Then the entry, because what is open about a place is the entry's answer and not the
     * place's: one point is named both by a measurement nobody made and by a showing that came to
     * nothing, and a reader is sent to different things by the two.
     *
     * <p>Switches with no {@code default}, so an opening or a subject added later is a compile
     * error here rather than an entry this report has nothing to say about.
     */
    static ReaderDisposition of(AdequacyOpening opening) {
        Subject subject = opening.subject();
        if (opening.runSensitivity() == RunSensitivity.MAY_CHANGE) {
            return new WidenTheRun(subject);
        }
        return switch (opening) {
            case AdequacyOpening.NotMeasured it ->
                    new LookAtWhyNothingWasMeasured(subject, it.why());
            case AdequacyOpening.ShowingStopped _,
                 AdequacyOpening.NothingShowedARowCanBeWritten _ ->
                    new LookAtWhatShowedNoRow(subject);
            case AdequacyOpening.ByWeakening it -> switch (subject) {
                case Subject.AtARule _ -> new LookAtTheRule(subject);
                case Subject.AtAFork _ -> new LookAtTheFork(subject);
                case Subject.AtAnArm _ -> new LookAtThisCompilersProof(subject);
                // The rest name a place a measure went without something at, and the word for what
                // it went without is what a reader reads next. Answered as a rule, an entry about
                // a module whose bodies were never made would send them after one that is not
                // there.
                case Subject.OfAModule _, Subject.OfABehavior _, Subject.OfASource _,
                     Subject.OfARow _, Subject.AtASpelledPosition _, Subject.AtAPosition _,
                     Subject.AtAnInput _, Subject.AtABorder _, Subject.AtAPoint _,
                     Subject.OfAMeasure _, Subject.OfAnAxisMeasure _ ->
                        new LookAtWhatTheMeasureWentWithout(subject, wordOf(it));
            };
        };
    }

    /**
     * Where the combinations a behavior's rows do not reach leave a reader.
     *
     * <p>Nobody is owed a row at one, so the question is whether there is anything to look at. There
     * is where a position the relations run between holds more classes than this behavior's rules
     * composed: the combinations those classes take part in are ones no row can reach, and what to
     * do about that is a judgement about the model rather than a row.
     *
     * <p>Where every position is divided as far as its rules divide it, the combinations left are
     * ones the rows happen not to sit in, and there is nothing further here — which is what
     * {@link Settled} says and is not the same as saying nothing is owed.
     *
     * <p>Read from the axes and not from the count. How many are unknown says how much of the
     * product the rows reach; whether any of it is out of their reach is the positions' answer.
     */
    static ReaderDisposition of(PartitionEvidence.PairSpace pairs,
                                List<PartitionEvidence.AxisCoverage> axes) {
        return widerThanTheyAreSeparated(pairs, axes).isEmpty()
                ? new Settled()
                : new ReconsiderWhatThisBehaviorNeedsToDistinguish(new Subject.OfABehavior(
                        widerThanTheyAreSeparated(pairs, axes).getFirst().at().behavior()));
    }

    /**
     * The positions a behavior takes wider than its own rules separate, and whose classes take part
     * in a relation no row reaches.
     *
     * <p>Both halves, and the second of them per relation. A position with more classes than the
     * rules composed is nothing to weigh where every relation it is in was reached: what makes it
     * worth a reader's time is that some of what it carries is out of every row's reach, and that
     * is a fact about one relation and not about the space. Asked of the space, a position whose
     * own relations are all covered would be raised because another two positions left something
     * unknown.
     *
     * <p>Here rather than beside the sentence that prints them. The line under a behavior's
     * combinations and the decision this leaves a reader are one answer, and worked out twice they
     * are two that can differ.
     */
    static List<PartitionEvidence.AxisCoverage> widerThanTheyAreSeparated(
            PartitionEvidence.PairSpace pairs, List<PartitionEvidence.AxisCoverage> axes) {
        if (pairs.counted().made().isEmpty()) {
            return List.of();
        }
        List<PartitionEvidence.AxisCoverage> out = new ArrayList<>();
        for (PartitionEvidence.AxisCoverage axis : axes) {
            if (axis.cutOrParted() || axis.divides().size() >= axis.classes().size()) {
                continue;
            }
            for (PartitionEvidence.PairSpace.AxisPair pair : pairs.space()) {
                boolean here = pair.between().one().equals(axis.at())
                        || pair.between().other().equals(axis.at());
                if (here && pairs.unknown(pair) > 0) {
                    out.add(axis);
                    break;
                }
            }
        }
        return List.copyOf(out);
    }

    /** What the weakening behind an entry is called, asked of the one projection that names it. */
    private static WeakeningVocabulary wordOf(AdequacyOpening.ByWeakening it) {
        return AdequacyReport.vocabularyOf(it.cause());
    }
}
