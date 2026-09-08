package souther.compiler.inputs;

import souther.compiler.check.ProjectionEvidence;
import souther.compiler.check.RuleRef;
import souther.compiler.check.TypeView;
import souther.compiler.types.Type;
import souther.compiler.types.TypeSymbol;
import souther.compiler.values.AdmissibleSet;

import java.util.List;
import java.util.Set;

/**
 * One position of a behavior's input, read once.
 *
 * <p>What every measure means by "what can arrive here". The cases a signature is owed, the classes
 * a position divides into, the arms a row is owed and the claims a body makes about a case are four
 * questions with one answer behind them, and four derivations of that answer are four chances to
 * disagree — which is how a report came to ask for a row at a case its own rules refuse.
 *
 * <p><b>Settled questions only.</b> What is published here is what was decided, not what it was
 * decided from: the reading itself stays inside this package. Handed the readings, each caller
 * would interpret them again, and a fifth derivation is what this exists to stop.
 *
 * <p>Reached through {@link InputDomain} and nowhere else. There is no way to write one of these
 * down, which is the whole of what keeps the answer single: a caller cannot assemble a position
 * out of a claim a body made, and a widening cannot be applied twice because it is applied where
 * the position is made.
 */
public sealed interface Position permits ReadPosition {

    /** Where the position sits, which is how a rule written about it and a row walked to it meet. */
    TermPath path();

    /** The position's type as the signature wrote it. */
    Type type();

    /** The same, read through the names it is written under — what a value of it is, and what a row
     *  writes it as. */
    TypeView view();

    /**
     * What the rules leave each of the position's numbers, one entry per number.
     *
     * <p><b>There is no number that stands for the position.</b> A {@code String} has its own order
     * and the length of it, and a rule about either is a rule about that one — so which of them a
     * class divides, which of them a line lies on and which of them a range is a range of are
     * answered by the number, never by the position. Answered with one chosen number, a rule about
     * the other has to be either mislabelled or thrown away, and both were done here.
     *
     * <p>Which numbers there are is the type's answer and not the rules'. What stands at the
     * position is always one of them; the second is there where the type declares an operation that
     * counts its values. A rule mentioning some other number of the place — what an absolute value
     * comes to, say — is a rule about a number the position has not, and it gets no entry here.
     *
     * <p>An entry exists wherever a number does, whatever the rules said about it. A number nobody
     * bounded has an entry saying so, because "no rule wrote about this" and "this position has no
     * such number" are different answers and only the second is about the model.
     */
    List<PositionBounds> bounds();

    /** The numbers this position has, which is what {@link #bounds()} is keyed by. */
    default List<NumericTerm.FromOnePosition> numbers() {
        return bounds().stream().map(PositionBounds::term).toList();
    }

    /** What the rules leave {@code term}, or null where the position has no such number. */
    default PositionBounds boundsFor(NumericTerm.FromOnePosition term) {
        for (PositionBounds each : bounds()) {
            if (each.term().equals(term)) {
                return each;
            }
        }
        return null;
    }

    /** Whether the rules of the value this position sits in contradict, so that no value of it
     *  exists to have positions at all. */
    boolean nothingExists();

    /**
     * How much of what the rules say the bounds of the value this position sits in are able to
     * state.
     *
     * <p>Asked of the whole value and not of this position, because that is what it is about: a rule
     * the bounds cannot express is a way the value can be refused wherever in it the rule is
     * written, so an edge at one of its positions is only as certain as the projection of all of
     * them.
     *
     * <p>Not whether a rule was answered, which is the wider question and is not this one:
     * {@code invariant nonzero = value /= 0} is read whole by the reading that turns clauses into
     * sets of values, and the edge at 0 is a row nobody can write all the same
     * ({@link souther.compiler.check.RuleAccounting} answers the other one).
     */
    ProjectionEvidence projection();

    /** What crossing the position's distinctions with the rules came to, as the reading found it. */
    ReadingResult reading();

    /** What rows are owed here, which is the reading with this compiler's own widenings applied. */
    ObligationDomain obligations();

    /** The distinctions rows are owed, which is {@link #obligations()} read for its cases. */
    default List<Case> obligationCases() {
        return obligations().cases();
    }

    /**
     * What the declarations say about one distinction standing here.
     *
     * <p>Answered from the reading and from the widening together, so that a distinction present
     * only because the reading was set aside is never read as one the rules admit. A distinction
     * this position does not have at all is unsettled rather than refused: nothing was read about
     * it, and the two are different answers.
     */
    Admits admissionOf(Case one);

    /**
     * The same, of a case named by the declaration it is.
     *
     * <p>What a measure counting a sum's cases has in hand is the declaration, and what the reading
     * holds is a distinction it made from that declaration. Asked here so that the two meet by
     * identity: a caller rebuilding the distinction to look it up would have to know what the
     * reading knew when it made one, and would find nothing on the day that changed.
     */
    Admits admissionOf(TypeSymbol leaf);

    /**
     * The same, of the distinction a narrowing names.
     *
     * <p>What a reader holding a {@code match} arm has. An arm is written by a name, and a name is
     * not a distinction of a position: an optional's carriers name none of them, and a case that is
     * itself a sum names the leaves under it rather than any one of them. Asked by name, such an
     * arm is answered {@link Unsettlement.NoSuchDistinction} — which is true, and is a fact about
     * the key rather than about the model, so a caller reading it as the position falling short
     * reports a limit of this compiler as an answer about what the rules leave (#1252).
     *
     * <p>So the key is the narrowing, which is what both vocabularies agree on
     * ({@link Refinement}), and what an arm covers is asked of every distinction it reaches
     * ({@link Refinement#allOf}).
     */
    Admits admissionOf(Refinement narrowing);

    /**
     * How much of what the rules say about this position's values one reading took in.
     *
     * <p>That reading's account of itself, and nothing else. Nothing downstream decides anything
     * from it: the reading that turns clauses into sets of values has no word for a range, so it is
     * short of the rules at every numeric position an invariant bounds while two other readings
     * have those rules whole — and a measure written off this said a model had gone unread on the
     * strength of a fact about this compiler (issue #842). What a report is about is
     * {@link #unansweredQuestions()}.
     *
     * <p>Kept because the two are different answers and saying so is what stops them being merged
     * again: a position can carry a partial reading here and no question standing there, and a test
     * that could not state the pair could not hold the difference.
     *
     * <p>Read off what the position admits, which carries both: the values its rules leave and how
     * much of those rules a reading took in are one answer, and a position that kept only the
     * second could not say what a behavior's rules have left to divide.
     */
    default AdmissibleSet.Completeness completeness() {
        return admitted().completeness();
    }

    /**
     * What the position's own rules leave it, and how much of them was read.
     *
     * <p>Kept whole rather than as the completeness alone. What a behavior's rules divide is what
     * the declarations left standing here — an invariant restricts and a behavior divides what is
     * left — so a reader composing classes out of the strings rather than out of these would draw a
     * class the position never holds a value in, and a rule the position rules out would come back
     * dividing it.
     */
    AdmissibleSet admitted();

    /**
     * The questions the rules written about this position raise that nothing answered, each naming
     * the rule that raised it.
     *
     * <p>Not {@link #completeness()}. That is one reading's account of itself, and a reading being
     * short of a position's rules says nothing about whether the rules went unread: the reading
     * that turns a clause into a set of values has no word for a range, so it is short at every
     * numeric position an invariant bounds — while the reading that turns the same clause into
     * where the values stop had it whole. Read as the model's completeness, that is a rule reported
     * unread two lines above the boundary drawn from it (issue #842).
     *
     * <p>Empty where every rule about the position was taken in by something, whichever reading
     * that was. A reader deciding whether the numbers beside this position rest on a complete
     * reading of the model wants this and not a reading's own account.
     */
    List<StandingQuestion> unansweredQuestions();

    /**
     * The rules this position was owed that went unread, each as the reason it went unread.
     *
     * <p>Beside {@link #unansweredQuestions()} and not among them. A rule nothing took in is a rule
     * this compiler saw and made nothing of; here nothing was seen, so there is no rule to name and
     * no question to raise — and an empty list of questions says every rule was accounted for,
     * which is the opposite (issue #791).
     *
     * <p><b>Owed, which is narrower than written underneath.</b> Two things are true here and only
     * these two: the reading that owns this position lost a clause of its own, or this position
     * handed its rules on and no reading was opened to take them ({@link RuleHandoffs}). A rule
     * written under a container, a case, or an optional is not one of them — it is read where it
     * governs, one position down, and a row meets it there. Said here, the measure was short of
     * something nobody could supply, because the walk had already gone to it (#1072).
     *
     * <p><b>The reasons and not a boolean.</b> Both can hold at once, and one of them is the same
     * stop {@link BlockedDescent} reports from the other end — so a reader deciding whether what it
     * holds is already said elsewhere has to be able to ask which this is. Answered {@code true},
     * that reader could not ask, and reported one stop as two (issue #1084).
     *
     * <p>Empty where the rules this position was owed were all reached. In the order they were
     * found, so that two runs over one model produce the same value.
     */
    Set<RulesLeftUnread> rulesLeftUnread();

    /**
     * The rules written about this position that the reading of ends drew no line from.
     *
     * <p>Both ways of there being none. A rule that reading got partway through, and one it read
     * from end to end that places no line — a relation between two positions, a quantity the
     * position cancels out of. Each is worth saying at a position a report is asked about, and they
     * are opposite sentences about this compiler; which of the two is the reason's to say and is
     * not read back out of this list's name.
     *
     * <p>Whichever value they are written on: a clause of this position's own type and a clause of
     * the value it sits in are two ways of saying where its values stop, and both come from the one
     * reading that draws lines from clauses.
     *
     * <p>Beside whatever the position is otherwise left with and not folded into it. A position
     * carries more than one statement, so an end read at it says nothing about the rule beside it —
     * kept as what the position was left with if nothing divided it, a bound on a field's own type
     * answered for the record's clause about the same field, and the clause was dropped in silence
     * (issue #868).
     */
    List<RuleWithoutALine> rulesWithoutALine();

    /**
     * The rules whose end here a choice in them left open.
     *
     * <p>Beside {@link #rulesWithoutALine()} and not read out of it. That list is what a report
     * says became of a rule at this position, and what it says is the same sentence about a rule
     * this compiler read to the end and about one it did not — which is why it holds neither
     * measure open. This is the other question: whether the reading that draws lines here ran out,
     * which a rule read to the end never leaves it doing.
     *
     * <p><b>Only where a choice is answerable.</b> An end left open under a conjunction is one the
     * rule's own questions already say nothing answered, and the measure hears it from there. What
     * a choice leaves open reaches nothing else, because the walk that classifies a rule stops at
     * one.
     *
     * <p>The rule and nothing else. Where an author is sent is the rule, and how many choices of it
     * are behind the answer is what the findings beside this say.
     */
    List<RuleRef> endsLeftOpenByAChoice();

    /**
     * Whether the values at this position are read from a product this reading cannot show the
     * rules admit.
     *
     * <p>Beside {@link #reading()} and answering a different question. That one says what stopped
     * the reading; this one is true where nothing stopped it — every rule arrived and every
     * rule was taken in, and what is held is one set per position standing for a relation the two
     * of them cannot state. Read off {@link #completeness()} rather than carried, since it is the
     * same fact said in the vocabulary a caller here already has.
     */
    default boolean valuesNotSeparated() {
        return completeness() instanceof AdmissibleSet.Completeness.Wider wider
                && wider.why().contains(new AdmissibleSet.Widening.AlternativesNotSeparated());
    }

    /** Whether the position is made of positions, and what it is left with if nothing answers for
     *  it. */
    StructuralInspection structure();

}
