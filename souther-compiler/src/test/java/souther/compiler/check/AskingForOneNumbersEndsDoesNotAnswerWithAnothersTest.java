package souther.compiler.check;

import org.junit.jupiter.api.Test;

import souther.compiler.numeric.Count;
import souther.compiler.numeric.Endpoint;
import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbol;
import souther.compiler.types.TypeSymbols;
import souther.compiler.types.ValueName;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Ends placed on one number of a position are not answered with another number's.
 *
 * <p>Asked of the selector and not of the values it selects between. That two coordinates at one
 * path are two values is the record's own business and is true the moment the operation is carried;
 * what regressed before is the reading beside it — {@code d.path().equals(where.path()) &&
 * d.measured() == where.measured()}, which is two projections agreeing rather than two numbers
 * being one. A selector written that way again passes every test that only compares identities.
 *
 * <p>{@link DeclaredBounds#placed} is where that selector is. It took a boolean and takes a
 * {@link NumberAt.OfWhatNumber}, and the path is the caller's already — {@code placedAt(path)}
 * is what the list is — so the kind is the whole of what it has to tell apart.
 *
 * <p>Two operations over one path is not a shape the language writes today, because a shape declares
 * one number taken of it ({@code NumericMeasures.takenOf}). That is why this is asked here rather
 * than of a model: the day a second is declared is the day a rule about one comes back as a bound on
 * the other, and nothing about either looks like a failure.
 */
class AskingForOneNumbersEndsDoesNotAnswerWithAnothersTest {

    private static final TypeSymbol.AtModule ON = TypeSymbols.declared(new TypeKey("m", "R"));

    private static final ValueName LENGTH = ValueName.Stdlib.operation("List", "length");
    private static final ValueName SIZE = ValueName.Stdlib.operation("Set", "size");

    /** A rule, told from another by where it sits among its declaration's clauses. */
    private static RuleRef.Invariant rule(int at) {
        return new RuleRef.Invariant(new Clause.Ref(new Clause.Id(ON, at), Optional.empty()));
    }

    /** The clauses an end names, which is what these assertions are about: which conjunct of each
     *  drew it is beside that and is not what a reader is sent to look at. */
    private static List<RuleRef.Invariant> rulesOf(DeclaredBounds.End end) {
        return end.found().stream().map(each -> each.part().rule()).toList();
    }

    /** An end above one number of `names`, placed by the clause at {@code by}. */
    private static FieldDomains.Placed upTo(NumberAt<RuleKey> on, int by, int at) {
        return new FieldDomains.Placed(on, statementOf(by), false,
                Endpoint.inclusive(Count.of(at)));
    }

    /** And one below it. */
    private static FieldDomains.Placed from(NumberAt<RuleKey> on, int by, int at) {
        return new FieldDomains.Placed(on, statementOf(by), true,
                Endpoint.inclusive(Count.of(at)));
    }

    /** The one statement of the first conjunct of clause {@code by}. */
    private static LineProvenance statementOf(int by) {
        return new LineProvenance.Direct(new InvariantStatementId(
                new PartId<>(rule(by), 0), 0));
    }

    private static final NumberAt<RuleKey> HOW_LONG =
            NumberAt.takenOf(RuleKey.of("names"), LENGTH);
    private static final NumberAt<RuleKey> HOW_MANY =
            NumberAt.takenOf(RuleKey.of("names"), SIZE);
    private static final NumberAt<RuleKey> ITSELF =
            NumberAt.valueOf(RuleKey.of("names"));

    /** Both, at one path, each with an end of its own and each named by a clause of its own. */
    private static final List<FieldDomains.Placed> AT_ONE_PATH =
            List.of(upTo(HOW_LONG, 0, 3), upTo(HOW_MANY, 1, 7));

    /**
     * Each operation's end, and its own rule beside it.
     *
     * <p>The rule and not only the number: an end answered from the wrong coordinate sends a reader
     * to a clause that says nothing about what they are looking at, which is the half of this a
     * value alone would not catch.
     */
    @Test
    void eachNumbersEndIsItsOwn() {
        DeclaredBounds.Bounds howLong = DeclaredBounds.placed(AT_ONE_PATH, HOW_LONG.of(),
                Carrier.WHOLE);
        DeclaredBounds.Bounds howMany = DeclaredBounds.placed(AT_ONE_PATH, HOW_MANY.of(),
                Carrier.WHOLE);

        assertEquals(Count.of(3), howLong.max().at().at(), "the end the rule about the length drew");
        assertEquals(List.of(rule(0)), rulesOf(howLong.max()), "and the clause that drew it");
        assertEquals(Count.of(7), howMany.max().at().at(), "the end the rule about the size drew");
        assertEquals(List.of(rule(1)), rulesOf(howMany.max()), "and the clause that drew it");
    }

    /**
     * And what the position itself holds is neither of them.
     *
     * <p>The arm a selector that read a flag would fall into for anything it did not recognise: both
     * of these are numbers taken of the position, so "not taken" is the one answer that is right
     * about neither.
     */
    @Test
    void andWhatThePositionHoldsIsNeither() {
        assertNull(DeclaredBounds.placed(AT_ONE_PATH, ITSELF.of(), Carrier.WHOLE),
                "no rule here says where the values of the list itself stop");
    }

    /**
     * Both ends of one number come back, and neither side takes the other number's.
     *
     * <p>Asked of the two sides apart, because a selector can leak on one of them. A rule bounding
     * the size below is not a floor under the length, and a reader handed one would report a length
     * that cannot be short — which reads as a fact about the model rather than as the two numbers
     * having been mixed.
     */
    @Test
    void bothEndsOfOneNumberComeBackAndNeitherSideTakesTheOthers() {
        List<FieldDomains.Placed> bothSides = List.of(
                upTo(HOW_LONG, 0, 3), from(HOW_LONG, 1, 1), from(HOW_MANY, 2, 5));

        DeclaredBounds.Bounds howLong = DeclaredBounds.placed(bothSides, HOW_LONG.of(),
                Carrier.WHOLE);

        assertEquals(Count.of(3), howLong.max().at().at(), "the length's own upper end");
        assertEquals(Count.of(1), howLong.min().at().at(),
                "and its own lower one, which is not the floor the rule about the size put there");
        assertEquals(List.of(rule(1)), rulesOf(howLong.min()), "named by the clause that drew it");
    }
}
