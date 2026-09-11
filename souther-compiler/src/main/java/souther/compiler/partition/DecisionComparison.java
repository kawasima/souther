package souther.compiler.partition;

import souther.compiler.check.AffineForms;
import souther.compiler.check.Comparison;
import souther.compiler.check.ComparisonClaim;
import souther.compiler.check.Location;
import souther.compiler.check.RuleReadingSource;
import souther.compiler.check.Symbols;
import souther.compiler.core.Core;
import souther.compiler.inputs.InputDomain;
import souther.compiler.inputs.InputNumber;
import souther.compiler.inputs.InputReads;
import souther.compiler.inputs.NumericTerm;
import souther.compiler.inputs.PathResolution;
import souther.compiler.numeric.LinearForm;
import souther.compiler.numeric.Rel;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A comparison over what a dependency answered, as the proposition it states.
 *
 * <p>What {@link AffineReading} is for the input, over the quantities a row can control rather than
 * the ones it writes. The arithmetic is the same walk — {@link AffineForms} takes what an atom is
 * from whoever asks — so {@code riskScore(c).value + 10 >= 710} and {@code riskScore(c).value >=
 * 700} are one proposition here for the reason they are one over a position.
 *
 * <p><b>Asked only where the input's arithmetic read nothing.</b> The two vocabularies do not
 * overlap: a comparison every operand of which is a number of the input is read there and a line is
 * drawn on it, and one this answers names at least one answer, which no term of the input is. So a
 * comparison is read once, and a column of the table and a border on the same comparison cannot
 * come from two readings that disagree.
 */
record DecisionComparison(InputDomain inputs, RuleReadingSource rules, DecisionSubjects subjects) {

    /**
     * {@code comparison} coming out {@code held} as a column, or null where nothing here reads it.
     *
     * <p>Null where an operand is neither a number of the input nor a place inside an answer, and
     * null where every operand is a number of the input: that comparison is the arithmetic's to
     * read, and answering it here would be a second reading of it.
     */
    DecidedCondition of(Comparison comparison, InputReads reads, boolean held) {
        List<DecisionAtom> onTheLeft = new ArrayList<>();
        LinearForm<DecisionAtom> left = null;
        for (Core side : List.of(comparison.stated().left(), comparison.stated().right())) {
            List<DecisionAtom> named = left == null ? onTheLeft : new ArrayList<>();
            if (!(AffineForms.outcome(side, reads, reading(named))
                    instanceof AffineForms.Outcome.Composed<DecisionAtom, InputReads>(
                            LinearForm<DecisionAtom> form))) {
                return null;
            }
            if (left == null) {
                left = form;
                continue;
            }
            LinearForm<DecisionAtom> whole = left.minus(form);
            if (whole.coefs().isEmpty()
                    || whole.coefs().keySet().stream()
                            .noneMatch(DecisionAtom.OfAnAnswer.class::isInstance)) {
                return null;
            }
            return stated(whole, comparison, onTheLeft, held);
        }
        throw new IllegalStateException("a comparison has two sides");
    }

    /**
     * The column {@code whole} states, with the quantity facing the way the author wrote it.
     *
     * <p>Turned round where the quantity the left side names comes out negative, which is what
     * {@link AffineReading} does with a line: {@code 700 <= riskScore(c)} and {@code riskScore(c)
     * >= 700} are one proposition, and a reading that kept the first as it met it would hold a
     * quantity no author wrote.
     */
    private DecidedCondition stated(LinearForm<DecisionAtom> whole, Comparison comparison,
                                    List<DecisionAtom> onTheLeft, boolean held) {
        LinearForm<DecisionAtom> quantity = new LinearForm<>(BigDecimal.ZERO, whole.coefs());
        BigDecimal cut = whole.constant().negate();
        boolean turned = facesTheOtherWay(quantity, onTheLeft);
        ComparisonClaim claim = turned ? comparison.stated().claim().turned()
                : comparison.stated().claim();
        if (turned) {
            quantity = quantity.negate();
            cut = cut.negate();
        }
        Rel states = claim.statedRelation();
        Rel rel = held ? states : states.denied();
        // The threshold back in the quantity, which is how a column states `form rel 0`.
        LinearForm<DecisionAtom> form = quantity.minus(LinearForm.constant(cut));
        Rel proposition = rel.orItsDenial();
        return new DecidedCondition.Compared(
                new DecisionCondition.AComparison(form, proposition), rel == proposition);
    }

    /** Whether the quantity the left side named first comes out negative, which is the same
     *  statement written the other way round. */
    private static boolean facesTheOtherWay(LinearForm<DecisionAtom> quantity,
                                            List<DecisionAtom> onTheLeft) {
        for (DecisionAtom named : onTheLeft) {
            BigDecimal coefficient = quantity.coefs().get(named);
            if (coefficient != null) {
                return coefficient.signum() < 0;
            }
        }
        return ordered(quantity).getFirst().getValue().signum() < 0;
    }

    /** The quantity's atoms by their own names, so that which one is first does not depend on how
     *  the comparison was written. */
    private static List<Map.Entry<DecisionAtom, BigDecimal>> ordered(
            LinearForm<DecisionAtom> form) {
        return form.coefs().entrySet().stream()
                .sorted(java.util.Comparator.comparing(each -> each.getKey().toString())).toList();
    }

    /**
     * How a side of the comparison is read, with {@code named} taking the atoms in the order they
     * were met.
     *
     * <p>Everything but the leaf is the reading the input's arithmetic uses, asked of the same
     * answers: what a name denotes and what a field access reads through are facts about the body,
     * and a second answer to either would be this reading disagreeing with the one a line is drawn
     * with about a body they are both reading.
     */
    private AffineForms.Reading<DecisionAtom, InputReads> reading(List<DecisionAtom> named) {
        return new AffineForms.Reading<DecisionAtom, InputReads>() {

            @Override
            public Symbols symbols() {
                return rules.symbols();
            }

            @Override
            public LinearForm<DecisionAtom> leafOf(Core node, InputReads at) {
                NumericTerm term = InputNumber.of(node, inputs, at, rules);
                DecisionAtom atom = term != null ? new DecisionAtom.OfTheInput(term)
                        : subjects.of(node, at) instanceof DecisionSubject.AnAnswer answered
                                ? new DecisionAtom.OfAnAnswer(answered) : null;
                if (atom == null) {
                    return null;
                }
                named.add(atom);
                return LinearForm.atom(atom);
            }

            @Override
            public InputReads inside(Core.LetIn li, InputReads at) {
                return at.and(li.binder(), li.value());
            }

            @Override
            public AffineForms.ReadThrough<InputReads> readThrough(Core.Read read, InputReads at) {
                return NameAnswers.denoting(read, at, rules.symbols());
            }

            @Override
            public List<AffineForms.ReadThrough<InputReads>> alternativesOf(Core.Read read,
                                                                           InputReads at) {
                return NameAnswers.alternativesOf(read, at, rules.symbols());
            }

            @Override
            public boolean readsThrough(Core.FieldAccess fa, InputReads at) {
                boolean stands = switch (at.pathOf(fa.target(), rules.symbols())) {
                    case PathResolution.At _ -> true;
                    case PathResolution.NotAPosition _ -> false;
                    case PathResolution.MayStandAt _ -> true;
                };
                return !stands
                        && !Location.isStep(fa.target().type(), fa.field(), rules.symbols());
            }
        };
    }

    /** The atoms of a form, wrapped as the input's, which is what a comparison the arithmetic read
     *  states here. */
    static LinearForm<DecisionAtom> ofTheInput(LinearForm<NumericTerm> form) {
        Map<DecisionAtom, BigDecimal> coefs = new LinkedHashMap<>();
        form.coefs().forEach((term, coefficient) ->
                coefs.put(new DecisionAtom.OfTheInput(term), coefficient));
        return new LinearForm<>(form.constant(), coefs);
    }
}
