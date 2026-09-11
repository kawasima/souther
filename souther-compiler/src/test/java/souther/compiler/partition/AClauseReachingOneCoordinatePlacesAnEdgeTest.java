package souther.compiler.partition;

import souther.compiler.diag.SourceLayouts;
import souther.compiler.diag.SourceRendering;
import org.junit.jupiter.api.Test;

import souther.compiler.check.DefaultBoundOperationFacts;
import souther.compiler.query.Adequacy;
import souther.compiler.query.BorderAssessment;
import souther.compiler.query.Compilation;
import souther.compiler.types.ValueName;
import souther.compiler.report.AdequacyReport;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which coordinates a clause reaches decides whether it places an edge, and the declaration holding
 * the clause does not (spec §a-clause-places-an-edge-where-it-reaches-one-coordinate).
 *
 * <p>A bound was read off the newtype chain of the position's own type and off nothing else, so the
 * same rule was measured or unmeasured according to where the author happened to write it.
 * {@code data Cart = List<Int> invariant List.length(value) >= 1} drew a line at 1 and
 * {@code data Cart = { lines: List<OrderLine> } invariant List.length(lines) >= 1} drew none — and an
 * aggregate with a second field has no choice but the second spelling. The measure rewarded wrapping
 * every constrained field in a newtype of its own, which is a modelling choice and says nothing about
 * the rule being stated (#649).
 *
 * <p>What the older rule protected is the relational case, and that is what the negative half here is
 * for. A clause relating two coordinates still divides neither and still places nothing: taking a
 * range in is not the same act as putting an edge in it, and a position whose only limit is another
 * position's is one the model draws no line through.
 */
class AClauseReachingOneCoordinatePlacesAnEdgeTest {

    /**
     * The three shapes of #649 and the shapes that must stay lineless, in one module.
     *
     * <p>Together on purpose: what the measure says about any one of these is only right if it is
     * still right beside the others, and the reading that produced them is one reading. Every row is
     * written away from the line so that a line which exists shows up as a gap naming its value.
     */
    private static final String MODEL = """
            module demo

            data Tally = { n: Int } invariant n >= 1
            data Bag   = { xs: List<Int> } invariant List.length(xs) >= 1
            data Both  = { n: Int } invariant n >= 1 invariant n <= 10

            data Span  = { startsAt: Int, endsAt: Int } invariant startsAt < endsAt
            data Floor = { n: Int, min: Int } invariant n >= min
            data R     = { a: Int, b: Int } invariant a < b invariant b <= 10

            data B     = Int invariant value <= 10
            data Under = { a: Int, b: B } invariant a < b.value

            data Inner = { n: Int } invariant n >= 1
            data Outer = { inner: Inner }
            data Tight = { inner: Inner } invariant inner.n >= 5

            data Ok = { size: Int }

            behavior onTally : (v: Tally) -> Ok
                constructs Ok
            let onTally (v) = Ok { size = v.n }

            behavior onBag : (v: Bag) -> Ok
                constructs Ok
            let onBag (v) = Ok { size = List.length(v.xs) }

            behavior onBoth : (v: Both) -> Ok
                constructs Ok
            let onBoth (v) = Ok { size = v.n }

            behavior onSpan : (v: Span) -> Ok
                constructs Ok
            let onSpan (v) = Ok { size = v.startsAt }

            behavior onFloor : (v: Floor) -> Ok
                constructs Ok
            let onFloor (v) = Ok { size = v.n }

            behavior onR : (v: R) -> Ok
                constructs Ok
            let onR (v) = Ok { size = v.a }

            behavior onUnder : (v: Under) -> Ok
                constructs Ok
            let onUnder (v) = Ok { size = v.a }

            behavior onOuter : (v: Outer) -> Ok
                constructs Ok
            let onOuter (v) = Ok { size = v.inner.n }

            behavior onTight : (v: Tight) -> Ok
                constructs Ok
            let onTight (v) = Ok { size = v.inner.n }

            example onTally | (Tally { n = 4 }) -> Ok { size = 4 }
            example onBag   | (Bag { xs = [1, 2, 3] }) -> Ok { size = 3 }
            example onBoth  | (Both { n = 4 }) -> Ok { size = 4 }
            example onSpan  | (Span { startsAt = 3, endsAt = 4 }) -> Ok { size = 3 }
            example onFloor | (Floor { n = 4, min = 2 }) -> Ok { size = 4 }
            example onR     | (R { a = 3, b = 4 }) -> Ok { size = 3 }
            example onUnder | (Under { a = 3, b = B(4) }) -> Ok { size = 3 }
            example onOuter | (Outer { inner = Inner { n = 4 } }) -> Ok { size = 4 }
            example onTight | (Tight { inner = Inner { n = 9 } }) -> Ok { size = 9 }
            """;

    /** The same rules written on newtypes, which were measured all along. Held beside the rest so
     *  that the shapes below are read as the same rule reaching the same place, and so that a repair
     *  taking these away would be caught by the test that added the others. */
    private static final String ON_NEWTYPES = """
            module newtypes

            data Name  = String    invariant String.length(value) >= 1
            data Cart  = List<Int> invariant List.length(value) >= 1
            data Count = Int       invariant value >= 1
            data Hop   = { n: Count }
            data Moved = { n: Count } invariant n.value >= 5

            data Ok = { size: Int }

            behavior onName : (v: Name) -> Ok
                constructs Ok
            let onName (v) = Ok { size = String.length(v.value) }

            behavior onCart : (c: Cart) -> Ok
                constructs Ok
            let onCart (c) = Ok { size = List.length(c.value) }

            behavior onHop : (v: Hop) -> Ok
                constructs Ok
            let onHop (v) = Ok { size = v.n.value }

            behavior onMoved : (v: Moved) -> Ok
                constructs Ok
            let onMoved (v) = Ok { size = v.n.value }

            example onName  | (Name("abc"))            -> Ok { size = 3 }
            example onCart  | (Cart([1, 2, 3]))        -> Ok { size = 3 }
            example onHop   | (Hop { n = Count(9) })   -> Ok { size = 9 }
            example onMoved | (Moved { n = Count(9) }) -> Ok { size = 9 }
            """;

    /** A record stating a bound on a number of its own places the edge a newtype over that number
     *  would have placed, and names itself. */
    @Test
    void aRecordsBoundOnItsOwnNumberPlacesAnEdge() {
        String report = report(MODEL);

        assertTrue(report.contains("  Tally\n      ! no row is at the ON point n = 1 (invariant Tally #1)"), report);
    }

    /**
     * And a bound on how much one of its fields holds.
     *
     * <p>The coordinate is the length and not the list, so the line is named by the length: the rule
     * counts the field rather than saying what the field is.
     */
    @Test
    void aRecordsBoundOnALengthOfItsOwnPlacesAnEdge() {
        String report = report(MODEL);

        assertTrue(report.contains(
                "point List.length(xs) = 1 (invariant Bag #1)"), report);
    }

    /** Both ends, so that this is read as the rules being met and not as a floor being special. */
    @Test
    void bothEndsOfARecordsOwnBoundAreEdges() {
        String report = report(MODEL);

        assertTrue(report.contains("  Both\n      ! no row is at the ON point n = 1 (invariant Both #1)"), report);
        assertTrue(report.contains("      ! no row is at the ON point n = 10 (invariant Both #2)"), report);
    }

    /** A clause governing the position from the declaration it sits inside reaches it, and names
     *  that declaration rather than the value it was reached through. */
    @Test
    void aClauseOnTheRecordAPositionSitsInsideReachesIt() {
        String report = report(MODEL);

        assertTrue(report.contains(
                "  Inner\n      ! no row is at the ON point n = 1 (invariant Inner #1)"), report);
    }

    /** And the outer record's own clause where it is the tighter of the two. */
    @Test
    void anOuterClauseThatIsTighterOwnsTheLine() {
        String report = report(MODEL);

        assertTrue(report.contains(
                "no row is at the ON point inner.n = 5 (invariant Tight #1)"), report);
    }

    /**
     * The rules written on names wrapped round a record, at a parameter and inside one.
     *
     * <p>Its own module, and with no rows: what is asked is which lines the model draws, and a row
     * would only say whether one was met.
     *
     * <p>`Held` is the case a wrapper reached only at the top would miss. Nothing is written on
     * `Held` or on `Base`; the rule is `Wrapped`'s, and the position is two steps down from the
     * parameter.
     */
    private static final String WRAPPERS = """
            module wrappers

            data Base        = { n: Int }
            data Wrapped     = Base invariant value.n >= 1
            data Held        = { w: Wrapped }

            data Bag         = { xs: List<Int> }
            data NonEmptyBag = Bag invariant List.length(value.xs) >= 1
            data HeldBag     = { b: NonEmptyBag }

            data W1          = Base
            data W2          = W1 invariant value.value.n >= 2
            data Stacked     = { w: W2 }

            data Ok = { size: Int }

            behavior onWrapped : (v: Wrapped) -> Ok
                constructs Ok
            let onWrapped (v) = Ok { size = v.n }

            behavior onNonEmpty : (v: NonEmptyBag) -> Ok
                constructs Ok
            let onNonEmpty (v) = Ok { size = List.length(v.xs) }

            behavior onHeld : (v: Held) -> Ok
                constructs Ok
            let onHeld (v) = Ok { size = v.w.n }

            behavior onHeldBag : (v: HeldBag) -> Ok
                constructs Ok
            let onHeldBag (v) = Ok { size = List.length(v.b.xs) }

            behavior onStacked : (v: Stacked) -> Ok
                constructs Ok
            let onStacked (v) = Ok { size = v.w.n }
            """;

    /**
     * A wrapper's rule reaches a position inside a record as well as at a parameter.
     *
     * <p>The place a wrapper read once, at the root of the walk, does not reach. `Held` holds a
     * `Wrapped` and states nothing itself, so the line at its `w.n` is `Wrapped`'s and arrives from a
     * name worn part way down. A wrapper met on the way is the general case and a wrapper at the top
     * is one instance of it, so reading only the second leaves the rule true of parameters and false
     * of fields.
     */
    @Test
    void aWrappersRuleReachesAPositionInsideARecord() {
        Map<String, BorderAssessment> lines = linesOf(WRAPPERS, "wrappers");

        assertEquals("invariant Wrapped #1", lines.get("onHeld/v.w.n = 1").origin().saidWithoutAPlace());
        assertEquals("invariant NonEmptyBag #1",
                lines.get("onHeldBag/List.length(v.b.xs) = 1").origin().saidWithoutAPlace());
    }

    /** And through as many names as are worn, since a name wrapped round a value is not a step. */
    @Test
    void aWrappersRuleReachesThroughAStackOfNames() {
        Map<String, BorderAssessment> lines = linesOf(WRAPPERS, "wrappers");

        assertEquals("invariant W2 #1", lines.get("onStacked/v.w.n = 2").origin().saidWithoutAPlace());
    }

    /**
     * A name wrapped round a record reaches the record's positions.
     *
     * <p>The place left over when the record and the declarations under it were read. A wrapper is
     * another place the same rule can be written, so reading the record and not the names round it
     * puts this issue back one level up — and the walk cannot miss the shape, since the positions it
     * takes apart are the record's either way.
     *
     * <p>The line is the wrapper's, and the {@code value} the clause is written through is not in
     * it: a newtype's value is the same location as the newtype, so the clause names the {@code n} a
     * reader of a `Wrapped` sees.
     */
    @Test
    void aNameWrappedRoundARecordReachesItsPositions() {
        Map<String, BorderAssessment> lines = linesOf(WRAPPERS, "wrappers");

        assertEquals("invariant Wrapped #1", lines.get("onWrapped/v.n = 1").origin().saidWithoutAPlace());
        assertEquals("invariant NonEmptyBag #1",
                lines.get("onNonEmpty/List.length(v.xs) = 1").origin().saidWithoutAPlace());
    }

    /**
     * A clause relating two positions places nothing on either, however far it takes their ranges in.
     *
     * <p>The rule this is here to keep. Neither position is divided by it, so an edge derived from it
     * would be a partition of a number the author never bounded (#427).
     *
     * <p>And neither is said to be one the model divides no way. There is a rule about each of them
     * — the one relating them — so what a report can say is that the rule does not divide either,
     * which is where it is sent. An absence there would tell the author their model draws no
     * distinction at a position their model has a rule about (issue #772).
     *
     */
    @Test
    void aClauseRelatingTwoPositionsPlacesNoEdge() {
        String report = report(MODEL);

        assertTrue(report.contains("""
                  onSpan                   implemented   rows 1    pending 0
                    signature   not applicable (this behavior's output is not a sum)
                    partition   not applicable (the rules of this behavior divide no position)
                      · no line: invariant Span #1 — it relates two positions rather than dividing one, about `v.startsAt`
                      · no line: invariant Span #1 — it relates two positions rather than dividing one, about `v.endsAt`
                    border      borders 1   obligations 0/0
                      · no OFF point is owed at v.startsAt = v.endsAt (invariant Span #1): excluded — the rules leave no value there
                      · no OUT point is owed at v.startsAt = v.endsAt (invariant Span #1): excluded — the rules leave no value there
                """), report);
    }

    /**
     * Nor one bounding a field by another field rather than by a constant.
     *
     * <p><b>The line such a rule draws is beside the partition and not in it.</b> It says where the
     * pair parts, and what it parts is on neither position — so it is a border with no classes under
     * it, and the partition above stays what it was. What a declaration's line owes is a row on it
     * and nothing beyond it: nothing outside a declaration's rule can be constructed at all.
     */
    @Test
    void aBoundAgainstAnotherFieldPlacesNoEdge() {
        String report = report(MODEL);

        assertFalse(report.contains("point onFloor/"), report);
        assertTrue(report.contains("""
                  onFloor                  implemented   rows 1    pending 0
                    signature   not applicable (this behavior's output is not a sum)
                    partition   not applicable (the rules of this behavior divide no position)
                      · no line: invariant Floor #1 — it relates two positions rather than dividing one, about `v.n`
                      · no line: invariant Floor #1 — it relates two positions rather than dividing one, about `v.min`
                    border      borders 1   obligations 0/0
                      · no OFF point is owed at v.n = v.min (invariant Floor #1): excluded — the rules leave no value there
                      · no OUT point is owed at v.n = v.min (invariant Floor #1): excluded — the rules leave no value there
                """), report);
    }

    /**
     * A range taken in by another position's bound is not an edge.
     *
     * <p>`b <= 10` places an edge on `b`, and `a < b` carries that as far as `a <= 9` — which is
     * where `b` stops and not something any clause said about `a`. Counting the 9 would put back the
     * whole of what the relational rule was excluded for, by the one route a reader that had stopped
     * asking where an edge came from would take.
     */
    @Test
    void aRangeNarrowedByAnotherPositionsBoundIsNotAnEdge() {
        String report = report(MODEL);

        assertTrue(report.contains("no row is at the ON point b = 10 (invariant R #2)"), report);
        assertFalse(report.contains("point onR/v.a"), report);
    }

    /**
     * The same where the bound is on the other position's own type.
     *
     * <p>The case a reading built out of one clause at a time gets wrong. Seeding `a < b.value`
     * alone still has `B`'s own maximum under it, so `a <= 9` falls out of that clause's own
     * derivation and looks like an edge the clause placed. It is `B`'s edge, reached through `a < b`,
     * and `a` has none.
     */
    @Test
    void aRangeNarrowedThroughAnotherPositionsTypeIsNotAnEdgeEither() {
        String report = report(MODEL);

        assertTrue(report.contains("no row is at the ON point value = 10 (invariant B #1)"), report);
        assertFalse(report.contains("point onUnder/v.a"), report);
    }

    /** The rules written on newtypes are measured as they were. A repair that moved these rather
     *  than adding to them would read as this issue being fixed. */
    @Test
    void theSameRulesOnNewtypesAreMeasuredAsBefore() {
        String report = report(ON_NEWTYPES);

        assertTrue(report.contains(
                "no row is at the ON point String.length(value) = 1 (invariant Name #1)"), report);
        assertTrue(report.contains(
                "no row is at the ON point List.length(value) = 1 (invariant Cart #1)"), report);
        assertTrue(report.contains("no row is at the ON point value = 1 (invariant Count #1)"), report);
    }

    /**
     * A record clause tighter than the type's own owns the line rather than narrowing it.
     *
     * <p>`Moved`'s clause bounds one coordinate against a constant, so it places an edge of its own,
     * and 5 is where the two intersect. Reported as `Count` narrowed within `Moved` it named the rule
     * that did not put the edge where it is.
     */
    @Test
    void aTighterRecordClauseOwnsTheLineRatherThanNarrowingIt() {
        String report = report(ON_NEWTYPES);

        assertTrue(report.contains("no row is at the ON point n = 5 (invariant Moved #1)"), report);
        assertFalse(report.contains("within Moved"), report);
    }

    /**
     * A position whose type is measured two ways, with rules arriving from outside it.
     *
     * <p>A `String` is the one value with two coordinates — its own order and the length of it — so
     * this is where reading more declarations could change which one a position is measured at.
     */
    private static final String TWO_WAYS = """
            module twoways

            data Name   = String invariant value >= "m"
            data Person = { name: Name } invariant String.length(name.value) >= 3

            data R = { s: String }
                invariant s >= "m"
                invariant String.length(s) >= 3

            data Ok = { size: Int }

            behavior onPerson : (v: Person) -> Ok
                constructs Ok
            let onPerson (v) = Ok { size = String.length(v.name.value) }

            behavior onR : (v: R) -> Ok
                constructs Ok
            let onR (v) = Ok { size = String.length(v.s) }

            example onPerson | (Person { name = Name("zzz") }) -> Ok { size = 3 }
            example onR      | (R { s = "zzz" }) -> Ok { size = 3 }
            """;

    /**
     * A rule from outside states an end and does not choose the coordinate.
     *
     * <p>`Name` is measured on its own order because its own clause is about its value, and a record
     * bounding the length of it says where a length stops without making the length what this
     * position is measured at. Read as a choice, the axis switched: the line at `m` — a rule the
     * author wrote and a report had been printing — went out, and the length edge came in beside
     * nothing saying the other had gone.
     */
    @Test
    void aRuleFromOutsideDoesNotChooseWhichCoordinateAPositionIsMeasuredAt() {
        String report = report(TWO_WAYS);

        assertTrue(report.contains("no row is at the ON point value = m (invariant Name #1)"),
                report);
    }

    /**
     * A rule about the length of a name draws its line on the length, beside the name's own.
     *
     * <p>`Name`'s clause is about the order its values sit on and the record's is about how long
     * one is. Two numbers of one place, and neither rule is in the other's way — so both are read
     * and both leave a line, and nothing is reported as unread.
     */
    @Test
    void aRuleAboutTheLengthDrawsItsLineBesideTheOneAboutTheOrder() {
        assertEquals(List.of("String.length(v.name) = 3", "v.name = m"),
                linesUnder(TWO_WAYS, "onPerson"),
                "the record's clause stops the length and the name's own stops the order");
        assertEquals(List.of(), notReadIn(TWO_WAYS, "onPerson"),
                "and neither clause is reported as one nothing could read");
    }

    /** And where a record bounds both numbers of a bare string, both are measured too. */
    @Test
    void rulesAboutBothNumbersAreBothRead() {
        assertEquals(List.of("String.length(v.s) = 3", "v.s = m"), linesUnder(TWO_WAYS, "onR"),
                "both clauses are the record's own, and each is on the number it is about");
        assertEquals(List.of(), notReadIn(TWO_WAYS, "onR"));
    }

    /** The lines {@code behavior} draws, by the label a report shows each under. */
    private static List<String> linesUnder(String source, String behavior) {
        return linesOf(source, "twoways").keySet().stream()
                .filter(each -> each.startsWith(behavior + "/"))
                .map(each -> each.substring(behavior.length() + 1)).sorted().toList();
    }

    /**
     * A floor no value of the type reaches, written both ways.
     *
     * <p>A `Set<Bool>` holds two elements at most, so a floor of three is a line nothing stands at.
     * No row can name such a value, which is why nothing here writes one: what the line comes to is
     * asked of the measure, and the measure answers without a row.
     */
    private static final String UNMEETABLE = """
            module unmeetable

            data FlagsN   = Set<Bool>       invariant Set.size(value) >= 2
            data NumbersN = Set<Int>        invariant Set.size(value) >= 3
            data FlagsR   = { s: Set<Bool> } invariant Set.size(s) >= 2
            data NumbersR = { s: Set<Int> }  invariant Set.size(s) >= 3
            data TextR    = { s: String }    invariant String.length(s) >= 3

            data Ok = { size: Int }

            behavior onFlagsN : (v: FlagsN) -> Ok
                constructs Ok
            let onFlagsN (v) = Ok { size = Set.size(v.value) }

            behavior onNumbersN : (v: NumbersN) -> Ok
                constructs Ok
            let onNumbersN (v) = Ok { size = Set.size(v.value) }

            behavior onFlagsR : (v: FlagsR) -> Ok
                constructs Ok
            let onFlagsR (v) = Ok { size = Set.size(v.s) }

            behavior onNumbersR : (v: NumbersR) -> Ok
                constructs Ok
            let onNumbersR (v) = Ok { size = Set.size(v.s) }

            behavior onTextR : (v: TextR) -> Ok
                constructs Ok
            let onTextR (v) = Ok { size = String.length(v.s) }
            """;

    /** The same rule past what a `Set<Bool>` can hold, written both ways. */
    private static final String BEYOND = """
            module beyond

            data FlagsN   = Set<Bool>       invariant Set.size(value) >= 3
            data FlagsR   = { s: Set<Bool> } invariant Set.size(s) >= 3

            data Ok = { size: Int }

            behavior onFlagsN : (v: FlagsN) -> Ok
                constructs Ok
            let onFlagsN (v) = Ok { size = Set.size(v.value) }

            behavior onFlagsR : (v: FlagsR) -> Ok
                constructs Ok
            let onFlagsR (v) = Ok { size = Set.size(v.s) }
            """;

    /**
     * A floor past what the element has is refused, and refused whichever way it is written.
     *
     * <p>The other half of the rule below, at the layer that decides it. There are two booleans, so a
     * set of three of them is a value nobody can build — and it is one rule, so the record's spelling
     * of it is refused beside the newtype's rather than left as a line somebody owes a row at.
     */
    @Test
    void aFloorPastWhatTheElementHasIsRefusedWhicheverWayItIsWritten() {
        Compilation compilation = Compilation.ofSource(BEYOND, "Main");
        compilation.answerEverything();

        assertEquals(List.of("E1013", "E1013"), compilation.diagnostics().values().stream()
                        .flatMap(List::stream).map(each -> each.diagnostic().code().toString())
                        .toList(),
                "one for each spelling of the one rule");
    }

    /**
     * A line the record placed is settled the way one on a newtype is.
     *
     * <p>The route an edge arrived by is not evidence about whether a row can be written at it. Each
     * of these floors is a row somebody owes, and the record's spelling has to come to that the same
     * way the newtype's does: a repair that made the record's line a counted obligation where the
     * newtype's is not would grant one rule two meanings again.
     *
     * <p>A floor past what its element has is the other half of that and is not here. It is refused
     * before any of this is read, and refused either way it is written, which is the same claim at
     * the layer that now decides it.
     */
    @Test
    void aLineTheRecordPlacedIsSettledLikeOneOnANewtype() {
        Map<String, BorderAssessment> lines = new LinkedHashMap<>();
        Compilation compilation = Compilation.ofSource(UNMEETABLE, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        Adequacy.readingsOf(compilation.db(), "unmeetable")
                .forEach((behavior, read) -> read
                        .forEach(line -> lines.put(behavior + "/" + line.label(), line)));

        assertEquals(Set.of("onFlagsN/Set.size(v) = 2", "onNumbersN/Set.size(v) = 3",
                        "onFlagsR/Set.size(v.s) = 2", "onNumbersR/Set.size(v.s) = 3",
                        "onTextR/String.length(v.s) = 3"),
                lines.keySet(), "one line each, and the record's is on the count of the field");
        assertTrue(onPoint(lines, "onFlagsN/Set.size(v) = 2").writabilityEvidence().known(),
                "two booleans are two booleans");
        assertTrue(onPoint(lines, "onFlagsR/Set.size(v.s) = 2").writabilityEvidence().known(),
                "and writing the rule on the record does not take one away");
        assertTrue(onPoint(lines, "onNumbersN/Set.size(v) = 3").writabilityEvidence().known(),
                "three integers are three integers");
        assertTrue(onPoint(lines, "onNumbersR/Set.size(v.s) = 3").writabilityEvidence().known(),
                "and a record holding them is a value that can be built");
    }

    /** The point on the line of the border named {@code label}, which is what a bound owes. */
    private static souther.compiler.query.ItemAssessment.Owed onPoint(
            Map<String, BorderAssessment> lines, String label) {
        return lines.get(label).owedAt(PointRole.ON);
    }

    /**
     * A wrapper whose clause relates two of the record's fields, beside the record without it.
     *
     * <p>Both fields stop at 10 by their own types, and `a < b` leaves `a` stopping at 9 — the
     * interval shape of #427, written one name up. The bare record beside it is the control: an edge
     * that does not move proves nothing unless the same edge moves elsewhere.
     */
    private static final String WRAPPED_RELATION = """
            module wrappedrelation

            data A    = Int invariant value <= 10
            data B    = Int invariant value <= 10
            data Base = { a: A, b: B }

            data Wrapped = Base invariant value.a.value < value.b.value
            data Held    = { w: Wrapped }

            data Ok = { size: Int }

            behavior onWrapped : (v: Wrapped) -> Ok
                constructs Ok
            let onWrapped (v) = Ok { size = v.a.value }

            behavior onBare : (v: Base) -> Ok
                constructs Ok
            let onBare (v) = Ok { size = v.a.value }

            behavior onHeld : (v: Held) -> Ok
                constructs Ok
            let onHeld (v) = Ok { size = v.w.a.value }
            """;

    /**
     * A wrapper's relational clause narrows the record's positions though it places no edge.
     *
     * <p>The half a wrapper read for its ends alone would drop. Such a clause leaves a range and not
     * a line, so a reader taking only the lines from a wrapper keeps `a` stopping at 10 — a value no
     * `Wrapped` holds, and a row asked for that nobody can write. Placing an edge and taking one in
     * are separate acts, and a wrapper does both.
     *
     * <p>The line stays `A`'s and says who took it in, which is what a narrowed origin is for: the
     * wrapper moved the edge and did not draw it.
     */
    @Test
    void aWrappersRelationNarrowsThePositionsItPlacesNoEdgeOn() {
        Map<String, BorderAssessment> lines = linesOf(WRAPPED_RELATION, "wrappedrelation");

        assertTrue(lines.containsKey("onBare/v.a = 10"),
                "`a` stops where its own type stops when nothing narrows it: " + lines.keySet());
        assertTrue(lines.containsKey("onWrapped/v.a = 9"),
                "and one step lower under the wrapper's clause: " + lines.keySet());
        assertEquals("invariant A #1 within Wrapped",
                lines.get("onWrapped/v.a = 9").origin().saidWithoutAPlace(),
                "the wrapper moved the edge `A` drew and did not draw one");
    }

    /**
     * And the declaration that moved it is named wherever the position is reached from.
     *
     * <p>`Held` holds a `Wrapped` and states nothing. The edge is still `A`'s and it was still
     * `Wrapped`'s clause that took it in, so that is what the line says — read off the value the
     * position sits in, it named a declaration with no clause about the pair at all, and a reader
     * following it finds nothing there. The name is part of what tells one line from another
     * ({@link LineOrigin.Line}) and not only what is printed.
     */
    @Test
    void aNarrowedEdgeNamesTheDeclarationThatMovedItAndNotTheValueItSitsIn() {
        Map<String, BorderAssessment> lines = linesOf(WRAPPED_RELATION, "wrappedrelation");

        assertEquals("invariant A #1 within Wrapped",
                lines.get("onHeld/v.w.a = 9").origin().saidWithoutAPlace(),
                "the clause is `Wrapped`'s wherever a `Wrapped` is held: " + lines.keySet());
    }

    /**
     * A relation that holds an end, and one beside it that holds nothing.
     *
     * <p>`a < b` under a `b` of at most 8 is what leaves `a` at 7. `a < c` under a `c` of at most 100
     * reaches nothing `a` had not already passed, so taking it away moves no end and it decided
     * none. And the two ends of `a` in the second model are held by different declarations, which is
     * a second answer and not the same one twice.
     */
    private static final String WHO_HELD_IT = """
            module whoheldit

            data A = Int invariant value <= 10
            data B = Int invariant value <= 8
            data C = Int invariant value <= 100

            data Base  = { a: A, b: B, c: C }
            data Inner = Base  invariant value.a.value < value.b.value
            data Outer = Inner invariant value.value.a.value < value.value.c.value

            data D     = Int invariant value <= 100
            data Twice = Base  invariant value.a.value < value.b.value
            data Again = Twice invariant value.value.a.value < value.value.b.value
            data Idle  = Again invariant value.value.value.a.value < value.value.value.c.value

            data N     = Int invariant value >= 0 invariant value <= 10
            data Low   = Int invariant value >= 2
            data High  = Int invariant value <= 8

            data Ends  = { n: N, low: Low, high: High }
            data Upper = Ends  invariant value.n.value < value.high.value
            data Both  = Upper invariant value.value.n.value > value.value.low.value

            data Ok = { size: Int }

            behavior onOuter : (v: Outer) -> Ok
                constructs Ok
            let onOuter (v) = Ok { size = v.a.value }

            behavior onBoth : (v: Both) -> Ok
                constructs Ok
            let onBoth (v) = Ok { size = v.n.value }

            behavior onIdle : (v: Idle) -> Ok
                constructs Ok
            let onIdle (v) = Ok { size = v.a.value }
            """;

    /**
     * A relation that moved no end does not get to name one.
     *
     * <p>Having written a relation about a coordinate is not the same as having decided where it
     * stops. Read as the same thing, adding a redundant clause to an outer declaration changed which
     * declaration a line was named by while the domain, the edge and the rule that moved it all
     * stayed where they were — and the name is part of what tells one line from another.
     */
    @Test
    void aRelationThatMovedNoEndDoesNotNameOne() {
        Map<String, BorderAssessment> lines = linesOf(WHO_HELD_IT, "whoheldit");

        assertEquals("invariant A #1 within Inner", lines.get("onOuter/v.a = 7").origin().saidWithoutAPlace(),
                "`Outer`'s clause reaches nothing `a` had not already passed");
    }

    /**
     * Where two clauses each hold the end, the one that holds nothing is still out.
     *
     * <p>`Twice` and `Again` say the same thing about `a`, so taking away either leaves the other
     * saying it and neither is the answer on its own. That is a reason to name both and not a reason
     * to name everything that was asked: `Idle` reaches nothing `a` had not already passed, and a
     * candidate that moves this end nowhere when it is the only one left moved it nowhere here.
     *
     * <p>What is left unresolved is said rather than guessed past: clauses that only reach the end
     * together are not taken apart further, and the set is what is handed over.
     */
    @Test
    void aCandidateThatHoldsNothingIsOutEvenWhereNoOneCandidateHoldsIt() {
        Map<String, BorderAssessment> lines = linesOf(WHO_HELD_IT, "whoheldit");

        assertEquals("invariant A #1 within Again or Twice",
                lines.get("onIdle/v.a = 7").origin().saidWithoutAPlace(),
                "`Idle`'s clause moves this end nowhere");
    }

    /** And the two ends of one position are two answers. */
    @Test
    void eachEndIsHeldByWhicheverDeclarationHoldsIt() {
        Map<String, BorderAssessment> lines = linesOf(WHO_HELD_IT, "whoheldit");

        assertEquals("invariant N #1 within Both", lines.get("onBoth/v.n = 3").origin().saidWithoutAPlace());
        assertEquals("invariant N #2 within Upper", lines.get("onBoth/v.n = 7").origin().saidWithoutAPlace());
    }

    /** A length floor over an element type nothing inhabits. Its own module, since a declaration that
     *  cannot be built leaves the module with nothing to build against. */
    private static final String UNINHABITED = """
            module uninhabited

            data Loop   = { next: Loop }
            data LoopyR = { xs: List<Loop> } invariant List.length(xs) >= 1

            data Ok = { size: Int }

            behavior onLoopyR : (v: LoopyR) -> Ok
                constructs Ok
            let onLoopyR (v) = Ok { size = List.length(v.xs) }
            """;

    /**
     * A length is not proven by the range where the thing counted may not exist.
     *
     * <p>The end-to-end half of the rule below. One is a number the rules leave and a list of one
     * needs an element, and `Loop` has none — so the range says the count is admissible and says
     * nothing about whether a value holds it. This is the case a rule about distinctness would let
     * through: a list repeats an element, which is only an answer once there is an element.
     */
    @Test
    void aLengthOverSomethingUninhabitedIsNotProvenByTheRange() {
        souther.compiler.query.ItemAssessment.Owed line =
                onPoint(linesOf(UNINHABITED, "uninhabited"), "onLoopyR/List.length(v.xs) = 1");

        assertFalse(line.writabilityEvidence().known(),
                "nothing inhabits `Loop`, so nothing holds a list of one");
    }

    /**
     * Which counts the projection may not stand behind, held at the rule rather than through it.
     *
     * <p>Through the measure there is nothing to hold. A length edge in a model this size is settled
     * by the value the generator builds for it, so it stays counted whether or not the projection was
     * entitled to say so, and an assertion on its verdict would pass with the rule reverted. What
     * shows the difference is a corpus: declining the proof at every count takes twenty
     * `String.length` edges out of the denominator across `souther-examples`, and the suite stays
     * green throughout. So the distinction is pinned where it is decided.
     */
    @Test
    void onlyAStringsLengthIsACountEveryValueHas() {
        assertTrue(DefaultBoundOperationFacts.get().everyAnswerItCanGiveHasASourceValue(
                        ValueName.Stdlib.operation("String", "length")),
                "a string of any length is a character repeated");
        assertFalse(DefaultBoundOperationFacts.get().everyAnswerItCanGiveHasASourceValue(
                        ValueName.Stdlib.operation("List", "length")),
                "a list of one needs an element, and a type nothing inhabits has none");
        assertFalse(DefaultBoundOperationFacts.get().everyAnswerItCanGiveHasASourceValue(ValueName.Stdlib.operation("Set", "size")),
                "a set of three needs three that differ");
        assertFalse(DefaultBoundOperationFacts.get().everyAnswerItCanGiveHasASourceValue(ValueName.Stdlib.operation("Map", "size")),
                "and a map of three needs three keys that differ");
    }

    /** Every line one module draws, by the behavior and label it is reported under. */
    private static Map<String, BorderAssessment> linesOf(String source, String module) {
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        Map<String, BorderAssessment> lines = new LinkedHashMap<>();
        Adequacy.readingsOf(compilation.db(), module)
                .forEach((behavior, read) -> read
                        .forEach(line -> lines.put(behavior + "/" + line.label(), line)));
        return lines;
    }

    private static String report(String source) {
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        return AdequacyReport.of(compilation).human(SourceRendering.namedByIdentity(SourceLayouts.NONE));
    }

    /**
     * What {@code behavior} left unread, as the coordinate it is about and the reason.
     *
     * <p>Off the evidence rather than out of the rendered report: what a document prints for a
     * reason is a projection made elsewhere, and a test reading the sentence is held to how it is
     * worded as much as to what it says.
     */
    private static List<String> notReadIn(String source, String behavior) {
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        return AdequacyReport.of(compilation).modules().get(0).behaviors().stream()
                .filter(each -> each.name().equals(behavior))
                .flatMap(each -> each.partition().notRead().stream())
                .map(each -> each.at() + ": " + each.reason()).toList();
    }
}
