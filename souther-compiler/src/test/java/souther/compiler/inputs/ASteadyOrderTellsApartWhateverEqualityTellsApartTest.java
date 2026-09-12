package souther.compiler.inputs;

import org.junit.jupiter.api.Test;

import souther.compiler.check.Clause;
import souther.compiler.check.ClauseName;
import souther.compiler.check.PartId;
import souther.compiler.check.RuleRef;
import souther.compiler.diag.QuotedFrom;
import souther.compiler.diag.SourceProvenance;
import souther.compiler.regex.Meter;
import souther.compiler.types.SourceConstruct;
import souther.compiler.types.SourceConstructOrigin;
import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbols;
import souther.compiler.types.WrittenOwner;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * A steady order calls two values one exactly where they are one.
 *
 * <p>What an order of this kind is for is that a reader comparing two runs over one source finds a
 * difference only where the model has one. An order that answers "the same" for values this
 * compiler tells apart does not do that: the two fall to whichever order a walk met them in, and a
 * document comes out one way on one run and the other way on the next.
 *
 * <p><b>So the contract is the one a sort needs, and it is checked rather than described.</b> Each
 * of these orders is built out of what its values are made of, and a comparison built out of a
 * rendering of that — a number for the kind, two names run together into one word — is the way the
 * property is lost: a rendering is coarser than what it renders, and the pairs below are exactly
 * the ones a coarser comparison calls equal.
 *
 * <p>Every sum a rule site is reached through is here, because the defect was found in four of them
 * at once and the last one closed only after the first three were.
 */
class ASteadyOrderTellsApartWhateverEqualityTellsApartTest {

    /** Two reasons of one kind, told apart by what that kind carries. */
    @Test
    void aReasonIsToldApartByWhatItCarries() {
        tellsApart(BlockReason.RuleReadingStopped.IN_A_STEADY_ORDER,
                new BlockReason.OrderedExtentTooCostly(Meter.Stopped.ONE_MACHINE),
                new BlockReason.OrderedExtentTooCostly(Meter.Stopped.THE_ANSWER));
    }

    /** And a text out of sight by which provenance published it, not by what that renders as. */
    @Test
    void aTextOutOfSightIsToldApartByWhoPublishedIt() {
        tellsApart(QuotedFrom.inASteadyOrder(),
                new QuotedFrom.TextItCannotShow(
                        new SourceProvenance.APublishedModule("m", "x")),
                new QuotedFrom.TextItCannotShow(
                        new SourceProvenance.TheStandardLibrary("m", "x")));
    }

    /** And an owner by which module wrote it and what it names, compared apart. */
    @Test
    void anOwnerIsToldApartByEachOfItsNames() {
        tellsApart(WrittenOwner.inASteadyOrder(),
                new WrittenOwner.Body("a b", "c"),
                new WrittenOwner.Body("a", "b c"));
    }

    /** And a construct by the owner that counted it, through that owner's own order. */
    @Test
    void aConstructIsToldApartByTheOwnerThatCountedIt() {
        tellsApart(SourceConstructOrigin.inASteadyOrder(),
                new SourceConstructOrigin(new WrittenOwner.Body("a b", "c"), 0, 0,
                        SourceConstruct.CALL),
                new SourceConstructOrigin(new WrittenOwner.Body("a", "b c"), 0, 0,
                        SourceConstruct.CALL));
    }

    /** And a rule site by the declaration a part is of, name and all. */
    @Test
    void aRuleSiteIsToldApartByTheWholeOfWhatAPartIs() {
        tellsApart(RuleSite.IN_A_STEADY_ORDER,
                RuleSite.at(part("a b", "c", Optional.empty())),
                RuleSite.at(part("a", "b c", Optional.empty())));
        tellsApart(RuleSite.IN_A_STEADY_ORDER,
                RuleSite.at(part("m", "N", Optional.of(new ClauseName("lo")))),
                RuleSite.at(part("m", "N", Optional.of(new ClauseName("hi")))));
    }

    /**
     * And a clause nobody named is told from one named with no characters.
     *
     * <p>An absence is not a value that happens to be empty. Nothing an author can write makes the
     * second of these today, and the type admits it: what the order promises is over the values
     * this compiler can hold, so a pair it can hold and cannot tell apart is a hole in the promise
     * whether or not a source reaches it.
     */
    @Test
    void andAnUnnamedClauseIsToldFromOneNamedWithNothing() {
        tellsApart(RuleSite.IN_A_STEADY_ORDER,
                RuleSite.at(part("m", "N", Optional.empty())),
                RuleSite.at(part("m", "N", Optional.of(new ClauseName("")))));
    }

    /** And a site of one kind is told from one of another, which is the coarsest half of it. */
    @Test
    void andTheKindsAreToldApartToo() {
        tellsApart(RuleSite.IN_A_STEADY_ORDER,
                RuleSite.theRuleItself(),
                RuleSite.at(part("m", "N", Optional.empty())));
    }

    /**
     * Two values this compiler tells apart are told apart by {@code order}, both ways round.
     *
     * <p>Both ways because a comparison is two claims: that neither is the same as the other, and
     * that which of them comes first does not turn on which was handed over first.
     */
    private static <T> void tellsApart(Comparator<T> order, T one, T other) {
        assertNotEquals(one, other, "this measures an order over values that are not one value");
        assertNotEquals(0, order.compare(one, other),
                "these are two values, and an order calling them one leaves them wherever the walk"
                        + " put them: " + one + " and " + other);
        assertEquals(Math.signum(order.compare(one, other)),
                -Math.signum(order.compare(other, one)),
                "and which of them comes first is the same question asked either way round");
    }

    private static PartId<RuleRef.Invariant> part(String module, String declared,
                                                  Optional<ClauseName> named) {
        return new PartId<>(new RuleRef.Invariant(new Clause.Ref(
                new Clause.Id(TypeSymbols.declared(new TypeKey(module, declared)), 0), named)), 0);
    }

    /** And one reason of a kind that carries nothing is still told from another kind. */
    @Test
    void andReasonsOfTwoKindsAreToldApart() {
        List<BlockReason.RuleReadingStopped> two = List.of(
                new BlockReason.UnreadComparisonForm(),
                new BlockReason.UnreadComparisonDomain());
        tellsApart(BlockReason.RuleReadingStopped.IN_A_STEADY_ORDER,
                two.getFirst(), two.get(1));
    }
}
