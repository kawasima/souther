package souther.compiler.partition;

import souther.compiler.check.AffineForms;
import souther.compiler.check.Comparison;
import souther.compiler.check.ComparisonClaim;
import souther.compiler.check.Location;
import souther.compiler.check.DeclarationKinds;
import souther.compiler.check.NewtypeInners;
import souther.compiler.check.PublishedDeclarations;
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
        LinearForm<DecisionAtom> left = null;
        for (Core side : List.of(comparison.stated().left(), comparison.stated().right())) {
            if (!(AffineForms.outcome(side, reads, reading())
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
            return stated(whole, comparison.stated().claim(), held);
        }
        throw new IllegalStateException("a comparison has two sides");
    }

    /**
     * The column {@code whole} states, facing the one way this reading writes it.
     *
     * <p><b>Which way is a fact about the proposition and not about the source.</b> A line keeps
     * the quantity its author put on the left, because a report sends a reader to the comparison
     * they wrote; a column is shown to nobody — what a sentence points at is the construct — and
     * what it has to do is come out the same for two bodies that state one thing. Read off the
     * authored side, {@code a >= b} and {@code b <= a} are two columns over quantities that are
     * each other negated, and a table with both admits an assignment where one proposition holds
     * and does not.
     *
     * <p>So the quantity is turned until its first coefficient is positive, taken in the order the
     * atoms name themselves, and the relation turns with it. Which atom is first does not depend on
     * how the comparison was written, so neither does the answer.
     */
    private DecidedCondition stated(LinearForm<DecisionAtom> whole, ComparisonClaim written,
                                    boolean held) {
        boolean turned = facesTheOtherWay(whole);
        LinearForm<DecisionAtom> form = turned ? whole.negate() : whole;
        Rel states = (turned ? written.turned() : written).statedRelation();
        Rel rel = held ? states : states.denied();
        Rel proposition = rel.orItsDenial();
        return new DecidedCondition.Compared(
                new DecisionCondition.AComparison(form, proposition), rel == proposition);
    }

    /**
     * Whether the quantity is the one this reading writes negated.
     *
     * <p>The first coefficient by the atoms' own order, which is what makes this total: settled by
     * every coefficient being negative, a quantity with one of each sign would face neither way and
     * two spellings of it would stay two columns.
     */
    private static boolean facesTheOtherWay(LinearForm<DecisionAtom> whole) {
        return ordered(whole).getFirst().getValue().signum() < 0;
    }

    /** The quantity's atoms by what each of them is, so that which one is first does not depend on
     *  how the comparison was written. */
    private static List<Map.Entry<DecisionAtom, BigDecimal>> ordered(
            LinearForm<DecisionAtom> form) {
        return form.coefs().entrySet().stream()
                .sorted(java.util.Comparator.comparing(each -> each.getKey().spelled())).toList();
    }

    /**
     * How a side of the comparison is read.
     *
     * <p>Everything but the leaf is the reading the input's arithmetic uses, asked of the same
     * answers: what a name denotes and what a field access reads through are facts about the body,
     * and a second answer to either would be this reading disagreeing with the one a line is drawn
     * with about a body they are both reading.
     */
    private AffineForms.Reading<DecisionAtom, InputReads> reading() {
        return new AffineForms.Reading<DecisionAtom, InputReads>() {

            @Override
            public Symbols symbols() {
                return rules.symbols();
            }

            @Override
            public PublishedDeclarations published() {
                return rules.published();
            }

            @Override
            public DeclarationKinds kinds() {
                return rules.kinds();
            }

            @Override
            public NewtypeInners inners() {
                return rules.inners();
            }

            @Override
            public LinearForm<DecisionAtom> leafOf(Core node, InputReads at) {
                NumericTerm term = InputNumber.of(node, inputs, at, rules);
                DecisionAtom atom = term != null ? new DecisionAtom.OfTheInput(term)
                        : subjects.of(node, at) instanceof DecisionSubject.AnAnswer answered
                                ? new DecisionAtom.OfAnAnswer(answered) : null;
                return atom == null ? null : LinearForm.atom(atom);
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
                        && !Location.isStep(fa.target().type(), fa.field(), rules.newtypes());
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
