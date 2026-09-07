package souther.compiler.partition;

import souther.compiler.coverage.ComparisonEmissionSite;
import souther.compiler.types.WrittenOwner;
import souther.compiler.coverage.ComparisonOccurrence;
import souther.compiler.coverage.Numberings;

import org.junit.jupiter.api.Test;

import souther.compiler.check.AReadingOfAPosition;
import souther.compiler.check.Carrier;
import souther.compiler.check.Clause;
import souther.compiler.check.ClauseName;
import souther.compiler.check.MatchedEndAttribution;
import souther.compiler.check.RuleRef;
import souther.compiler.inputs.NumericTerm;
import souther.compiler.inputs.TermOrdersFixtures;
import souther.compiler.inputs.TermPath;
import souther.compiler.numeric.Count;
import souther.compiler.numeric.EndSide;
import souther.compiler.numeric.Endpoint;
import souther.compiler.numeric.Towards;
import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbols;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A clause of a type does not part the values of the position it is about.
 *
 * <p>Nothing outside a bound can be constructed, so a bound has no far side to order against: it is
 * where what it leaves stops rather than a place its values divide. A clause that names one value
 * puts every other value in one class, which is not a run of the order either.
 *
 * <p>Which settles who can be named as stopping a run beside somebody else's line. Only a rule
 * written in a body parts the values, so a run stopping at a line is stopping at a body's rule, and
 * a point owed for such a run exists in that body and nowhere else. A declaration never turns up as
 * the far side of another declaration's line — where it does stop such a run, it stops it by taking
 * the position in, which is an end the rules leave and not a line ({@link FarEnd.AtTheDomain}).
 *
 * <p>Written down because the accounting rests on it. Were a clause to part the values, a run beside
 * an imported line could be owed to a foreign declaration's line, and which module answers for it
 * would be a question this compiler has never been asked.
 */
class AClauseOfATypeDoesNotPartItsValuesTest {

    private static final Carrier WHOLE = new Carrier.Whole();

    /**
     * A clause naming a value of what two positions stand apart keeps neither end of that number.
     *
     * <p>What an end is, is where a run of the values stops, and a rule that names a value leaves
     * the run under it and the run over it — so there is no end it placed and nothing to read one
     * off. Compiled all the same, and measured: the model states a relation and the report says so.
     */
    @Test
    void aClauseNamingAValueOfTwoPositionsKeepsNeitherEnd() {
        String block = souther.compiler.report.AdequacyReport.of(compiled("""
                module example.same

                data R = { lo: Int, hi: Int }
                    invariant level = lo == hi

                data Yes
                data No
                data Answer = Yes | No

                behavior take : (r: R) -> Answer
                let take (r) = if r.lo > 0 then Yes else No

                example take
                    | "one" : (R { lo = 1, hi = 1 }) -> Yes
                """)).human(souther.compiler.diag.SourceNameResolver.identity());

        org.junit.jupiter.api.Assertions.assertTrue(block.contains("example.same"), block);
    }

    /**
     * A clause of a declaration that names one of the value's own numbers draws no line.
     *
     * <p>Which is what says where the words for a role with no point can be reached from. A role
     * goes unplayed only where the rule that drew the line names a value, and a declaration's lines
     * are ends: a clause naming one leaves a range of one, and the ends of that range are what a
     * row is owed at. So the sentence belongs under a behavior, where a body's comparison and an
     * {@code ensures} are, and a report that carried it under the declarations would carry a
     * sentence no model can reach.
     *
     * <p>Asked of the lines rather than reasoned about, because which surface a sentence belongs to
     * turns on it. Which is why this asks what the roles come to and not whether a line was drawn:
     * a clause naming a value does leave the values stopping somewhere, and a row is owed there.
     */
    @Test
    void aClauseNamingOneOfItsOwnValuesDrawsNoLineWithARoleUnplayed() {
        List<String> drawn = new java.util.ArrayList<>();
        souther.compiler.query.Compilation compilation = compiled("""
                module example.only

                data A = Int
                    invariant only = value == 5

                data H = { a: A }
                data Yes
                data No
                data Answer = Yes | No

                behavior take : (h: H) -> Answer
                let take (h) = if h.a.value > 0 then Yes else No

                example take
                    | "one" : (H { a = A(5) }) -> Yes
                """);
        souther.compiler.query.Adequacy.boundariesOf(compilation.db(), "example.only").values()
                .forEach(each -> each.forEach(at -> at.border().inEachRole()
                        .forEach((role, played) -> {
                            if (!(played instanceof RoleAnswer.Played)) {
                                drawn.add(at.label() + " " + role + " " + played);
                            }
                        })));
        assertEquals(List.of(), drawn,
                "the ends of the range a clause naming a value leaves are ends like any other, and"
                        + " no role of a line drawn from one goes unplayed");
    }

    private static souther.compiler.query.Compilation compiled(String model) {
        souther.compiler.query.Compilation compilation =
                souther.compiler.query.Compilation.ofSource(model, "Main");
        compilation.measure(souther.compiler.query.Adequacy.Asked.fullReport());
        compilation.answerEverything();
        return compilation;
    }

    /** A bound is where what it leaves stops, and stops nothing else. */
    @Test
    void aBoundPartsNothing() {
        assertEquals(List.of(), Border.partedBy(aLineAt(100), aBound()),
                "nothing is outside a bound, so there is no run on the far side to be beside");
    }

    /** And a bound another declaration took in is still a bound. */
    @Test
    void aBoundADeclarationTookInPartsNothingEither() {
        Endpoint at = Endpoint.inclusive(Count.of(100));
        assertEquals(List.of(), Border.partedBy(aLineAt(100),
                        LineOrigin.NarrowedOrigin.of(aBound(), at, aDeclarationHolding(at))),
                "taking an end in moves where the position stops, which is not dividing it");
    }

    /**
     * A reading answering that one declaration holds {@code at} on the side {@link #aBound()}
     * placed.
     *
     * <p>The side is the bound's, because a bound is answered for by the end it placed. Written as
     * the other one, this fixture said a minimum had been taken in by whatever holds a maximum —
     * which is the pairing the origin refuses, and which is why it refuses it here rather than
     * downstream where the two are one number.
     */
    private static MatchedEndAttribution aDeclarationHolding(Endpoint at) {
        return AReadingOfAPosition
                .withALowerEndAt(at, TypeSymbols.declared(new TypeKey("example.weigh", "Held")))
                .matching(EndSide.LOWER, at).orElseThrow();
    }

    /** While a comparison in a body does part them, which is why a run can stop at a line at all. */
    @Test
    void aComparisonInABodyPartsThem() {
        assertNotNull(Border.partedBy(aLineAt(100), aComparison()),
                "a body's comparison orders the values around its line, so there is a run either"
                        + " side of it");
    }

    private static BoundaryTarget aLineAt(int value) {
        AxisId axis = new AxisId("weigh", "w.a");
        NumericTerm.ValueOf term = new NumericTerm.ValueOf(TermPath.of(axis.term()));
        return BoundaryTarget.at(
                new BorderQuantity.OfACoordinate(axis.behavior(), term,
                        TermOrdersFixtures.itself(term, WHOLE)),
                new Level.OnACarrier(WHOLE, Count.of(value)));
    }

    private static LineOrigin.InvariantOrigin aBound() {
        return new LineOrigin.InvariantOrigin(
                new souther.compiler.check.PartId<>(new RuleRef.Invariant(new Clause.Ref(
                        new Clause.Id(
                                TypeSymbols.declared(new TypeKey("example.weigh", "Amount")), 0),
                        Optional.of(new ClauseName("cap")))), 0),
                EndSide.LOWER, true);
    }

    /** The place this fixture's comparison is at. One of them, so that two readings built here
     *  address one place. */
    private static final ComparisonEmissionSite WHERE = Numberings.comparison(1, 0);

    private static LineOrigin aComparison() {
        return new LineOrigin.ComparisonOrigin(
                new LineOrigin.ComparisonOrigin.Read(
                        new RuleRef.Comparison("weigh",
                                new souther.compiler.types.SourceConstructOrigin(
                                        new WrittenOwner.Body("example.weigh", "weigh"), 2, 0,
                                        souther.compiler.types.SourceConstruct.BINARY)),
                        souther.compiler.diag.Citation.of(
                                new souther.compiler.diag.SourcePos(3, 5)),
                        List.of(new LineOrigin.ComparisonOrigin.Watched(
                                new ComparisonOccurrence(
                                        "example.weigh", "weigh", 0), WHERE))),
                new LineFacts(new souther.compiler.check.ComparisonClaim.Cut(Towards.BELOW, true)));
    }
}
