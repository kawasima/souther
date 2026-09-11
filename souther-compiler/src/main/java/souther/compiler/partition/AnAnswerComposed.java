package souther.compiler.partition;

import souther.compiler.inputs.NumericTerm;
import souther.compiler.inputs.Requirements;
import souther.compiler.inputs.SearchRegion;
import souther.compiler.inputs.TermPath;
import souther.compiler.numeric.LinearForm;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A value for one answer a rule asks something of.
 *
 * <p>The same realization a value at a position goes through, asked about a type rather than about
 * a behavior's input. What a demand asks — a case, a truth, a comparison — is what a way already
 * asks of a position, so what composes a value for one composes a value for the other, and a second
 * composer written here would be a second answer to which values a type's own rules leave.
 *
 * <p><b>An adapter and not a promotion.</b> The subject handed in stands for one value and is read
 * by nothing else: an answer is not a position of anybody's input, and what a search may assume
 * about an input is not what a row may pin a dependency to. Where such a subject is made is where
 * every reading of a behavior's input is made, so that nothing here is a second maker of one. What
 * leaves is the value, which belongs to whoever asked.
 *
 * <p>Nothing put through a decoder. What certifies a composed answer is the run: a row is stood up
 * with it, applied, and asked which rule it took, and a value the model refuses is a stand-in that
 * will not build and a rule left where it was. So a value from here is a candidate and never a
 * witness — which is the same standing a value composed at a position has before its row is run.
 */
public final class AnAnswerComposed {

    private AnAnswerComposed() {}

    /** A value for the answer, or the reason nothing here composed one. */
    public sealed interface Outcome {

        /** A value that satisfies every demand this could state. */
        record Composed(FixtureTemplate value) implements Outcome {}

        /** Nothing came of it, in the words a search comes back with. Never a statement that no
         *  value exists. */
        record NothingComposed(Generator.UnresolvedCombination.Reason why) implements Outcome {}
    }

    /**
     * A value of what {@code subject} stands for that meets {@code demands}.
     *
     * <p>The demands of one answer and no other. What a row stands two dependencies in with is two
     * values, each composed against what the way asks of it; a comparison relating them is not a
     * demand on either, and is one this reading leaves unstated rather than splitting.
     *
     * @param subject one position, standing for what the dependency answers. Made where every
     *                reading of an input is made, and holding one parameter because what is
     *                composed here is one value
     * @param demands what the way asks of it, every one of them about this answer
     */
    public static Outcome of(MeasuredInput subject, List<AnswerDemand> demands) {
        if (subject.parameters().size() != 1) {
            throw new IllegalArgumentException(
                    "an answer is one value, and this stands for " + subject.parameters().size());
        }
        String at = subject.parameters().getFirst();
        Boolean truth = truthOfTheWholeAnswer(demands);
        if (truth != null) {
            // A truth asked of the answer itself, which is two values and holds no position — so
            // there is nothing to narrow and nothing to place, and the value is the answer. A truth
            // asked of a place inside one is not this, and is a demand nothing here states.
            return demands.size() == 1 ? new Outcome.Composed(FixtureTemplate.bool(truth))
                    : new Outcome.NothingComposed(
                            Generator.UnresolvedCombination.Reason.NOTHING_COMPOSES_ONE);
        }
        Requirements required = Requirements.NONE;
        for (AnswerDemand each : demands) {
            if (!(each instanceof AnswerDemand.ACase(var _, var _, var steps, var to))) {
                continue;
            }
            if (!(required.merge(path(at, steps).refine(to).requirements())
                    instanceof Requirements.Merge.Merged(var both))) {
                // The way asks the answer to be two cases at once, which no value is. A fact about
                // what was asked rather than about what was tried, and it comes back as nothing
                // composed for the reason every other answer here does: what a caller does with it
                // is settle the rule elsewhere, and a second word for it here would be a second
                // account of a way nothing takes.
                return new Outcome.NothingComposed(
                        Generator.UnresolvedCombination.Reason.NOTHING_COMPOSES_ONE);
            }
            required = both;
        }
        SearchRegion region = subject.quantities().region();
        List<OnTheWay.TakenIn> cuts = new ArrayList<>();
        for (AnswerDemand each : demands) {
            if (!(each instanceof AnswerDemand.AComparison(var _, var anchor, var form, var rel))) {
                continue;
            }
            LinearForm<NumericTerm> over = overThisAnswer(at, form);
            region = region.assuming(over, rel);
            cuts.add(new OnTheWay.TakenIn(anchor, new ReachingCuts.Cut(over, rel)));
        }
        return composed(subject, new Reachability.Reaching(region, required, cuts));
    }

    /**
     * The one value the subject's realization came to, or why it came to none.
     *
     * <p>One parameter and one value, which is what the reading built here has. A subject of more
     * would be a subject somebody else made, and the answer read off the first of its values would
     * be an answer about whichever position came first.
     */
    private static Outcome composed(MeasuredInput subject, Reachability.Reaching reaching) {
        Generator.BoundaryAttempt attempt = Generator.probeFixing(subject,
                "an answer of a dependency", Map.of(), reaching,
                // Nothing refuses a candidate here. What a decoder answers about a value is asked
                // where the row that carries it is run, and a check written here would be this
                // reading building the dependency's own boundary a second way.
                Generator.CandidateCheck.ANY);
        if (attempt instanceof Generator.BoundaryAttempt.Built(var row, var _)
                && row.inputs().size() == 1) {
            return new Outcome.Composed(row.inputs().getFirst());
        }
        return new Outcome.NothingComposed(attempt instanceof Generator.BoundaryAttempt.Unresolved(
                var why, var _) ? why.reason()
                        : Generator.UnresolvedCombination.Reason.NOTHING_COMPOSES_ONE);
    }

    /**
     * Which way a truth asked of the whole answer came out, or null where nothing asks one.
     *
     * <p>Of the answer itself and not of a place inside it. A {@code Bool} divides a position into
     * two values and puts nothing under it, so the demand is the value; a truth read off a field is
     * a demand about a position this reading has no way of placing, and saying it here would
     * compose a value that satisfies the field's type and not the demand.
     */
    private static Boolean truthOfTheWholeAnswer(List<AnswerDemand> demands) {
        for (AnswerDemand each : demands) {
            if (each instanceof AnswerDemand.ATruth(var _, var _, var at, var held)
                    && at.isEmpty()) {
                return held;
            }
        }
        return null;
    }

    /** The place inside the answer, under the name the subject gives the whole of it. */
    private static TermPath path(String head, List<TermPath.Step> steps) {
        return new TermPath(head, steps);
    }

    /**
     * A comparison's quantity in the terms of the reading built here.
     *
     * <p>The same quantity and not a second reading of it. Each atom is a place inside this answer,
     * which is a position of the one value this composes — so what the comparison states about the
     * dependency's answer is what it states about that position, spelled in the vocabulary the
     * realization works in.
     */
    private static LinearForm<NumericTerm> overThisAnswer(String head,
                                                          LinearForm<DecisionAtom> form) {
        Map<NumericTerm, BigDecimal> coefs = new LinkedHashMap<>();
        form.coefs().forEach((atom, coefficient) -> coefs.put(
                new NumericTerm.ValueOf(path(head, ((DecisionAtom.OfAnAnswer) atom).at().steps())),
                coefficient));
        return new LinearForm<>(form.constant(), coefs);
    }
}
