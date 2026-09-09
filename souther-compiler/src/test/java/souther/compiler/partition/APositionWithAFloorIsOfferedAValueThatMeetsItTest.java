package souther.compiler.partition;

import org.junit.jupiter.api.Test;

import souther.compiler.check.DeclaredSig;
import souther.compiler.check.RuleReadingSource;
import souther.compiler.check.RuleReadings;
import souther.compiler.inputs.InputDomain;
import souther.compiler.query.Bodies;
import souther.compiler.query.Compilation;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * What a position is offered where a rule says how much it holds.
 *
 * <p>The value built for the floor, and built first. A position offering the value that holds
 * nothing before the one the rule asks for is a position whose first assignment is refused, and the
 * search that walks assignments is bounded — so which of them comes first is not a matter of taste.
 */
class APositionWithAFloorIsOfferedAValueThatMeetsItTest {

    private static MeasuredInput subjectOf(String source, String behavior) {
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.answerEverything();
        String module = compilation.modules().get(0);
        Map<String, DeclaredSig> sigs =
                compilation.db().ask(new Bodies.DeclaredSignatures(module)).value();
        RuleReadingSource rules = RuleReadings.of(compilation, module);
        assertNotNull(sigs, "the model did not compile");
        InputDomain domain = InputDomain.of(sigs.get(behavior), rules,
                souther.compiler.query.ReadAs.THE_COMPILATION_DOES);
        Partitions.Partitioning partitioning = Partitions.of(behavior, domain, rules, souther.compiler.query.ReadAs.THE_COMPILATION_DOES);
        return MeasuredInput.of(behavior, domain.reading(rules), partitioning);
    }

    /** The value at the position the row wrote, which is the one the search reached first. */
    private static String firstValueAt(String source, String behavior, int position) {
        FillResult filled =
                Generator.fill(subjectOf(source, behavior), List.of(), Generator.CandidateCheck.ANY, Budgets.generation());
        assertEquals(List.of(), filled.unresolved(), "nothing should have gone unresolved");
        return filled.rows().get(0).inputs().get(position).text();
    }

    /**
     * A count of two is a list of two, and not a list of one that the rule then refuses.
     *
     * <p>Two rather than one, so that a position offered a single element for every floor is not
     * mistaken for one that read the rule.
     */
    @Test
    void aListFieldIsOfferedAsManyElementsAsTheRecordAsksFor() {
        assertEquals("Bag { xs = [0, 0] }", firstValueAt("""
                module example.bag

                data Bag =
                    { xs: List<Int>
                    }
                    invariant atLeastTwo = List.length(xs) >= 2

                data Ok = { n: Int }

                behavior countThem : (flag: Bool, bag: Bag) -> Ok
                    constructs Ok

                let countThem (flag, bag) = Ok { n = List.length(bag.xs) }
                """, "countThem", 1));
    }

    /** And a string of three where the record counts its characters, which is the same rule read
     * through the measure the position's own type names rather than through anything about lists. */
    @Test
    void aStringFieldIsOfferedAsManyCharactersAsTheRecordAsksFor() {
        assertEquals("Named { name = \"xxx\" }", firstValueAt("""
                module example.named

                data Named =
                    { name: String
                    }
                    invariant longEnough = String.length(name) >= 3

                data Ok = { n: Int }

                behavior measureIt : (flag: Bool, who: Named) -> Ok
                    constructs Ok

                let measureIt (flag, who) = Ok { n = String.length(who.name) }
                """, "measureIt", 1));
    }

    /** A field of a field is answered like a field: the floor is read at the path it was left at,
     * which is where {@link souther.compiler.check.FieldDomains#at} already reads a number's. */
    @Test
    void aFieldOfAFieldIsOfferedTheSame() {
        assertEquals("Outer { inner = Inner { xs = [0, 0] } }", firstValueAt("""
                module example.nested

                data Inner =
                    { xs: List<Int>
                    }
                    invariant atLeastTwo = List.length(xs) >= 2

                data Outer = { inner: Inner }

                data Ok = { n: Int }

                behavior countThem : (flag: Bool, o: Outer) -> Ok
                    constructs Ok

                let countThem (flag, o) = Ok { n = List.length(o.inner.xs) }
                """, "countThem", 1));
    }

    /**
     * A set of two is two elements no two of which are equal, which is not a list of two repeated.
     *
     * <p>Held because what a floor comes to is the measure's answer and not the list's. A machinery
     * that filled every collection by repeating one element would meet {@code List.length} and leave
     * a set of one under a line drawn at two.
     */
    @Test
    void aSetFieldIsOfferedAsManyDistinctElementsAsTheRecordAsksFor() {
        assertEquals("Tagged { tags = [0, 1] }", firstValueAt("""
                module example.tagged

                data Tagged =
                    { tags: Set<Int>
                    }
                    invariant atLeastTwo = Set.size(tags) >= 2

                data Ok = { n: Int }

                behavior countThem : (flag: Bool, t: Tagged) -> Ok
                    constructs Ok

                let countThem (flag, t) = Ok { n = Set.size(t.tags) }
                """, "countThem", 1));
    }

    /** And a map of two is two entries no two of which share a key, which is the same measure asked
     * of the third collection the rules can count. Keyed by a string, which is what a map crossing
     * the boundary is keyed by (E1314). */
    @Test
    void aMapFieldIsOfferedAsManyEntriesAsTheRecordAsksFor() {
        assertEquals("Priced { by = [(\"x\", 0), (\"xx\", 0)] }", firstValueAt("""
                module example.priced

                data Priced =
                    { by: Map<String, Int>
                    }
                    invariant atLeastTwo = Map.size(by) >= 2

                data Ok = { n: Int }

                behavior countThem : (flag: Bool, p: Priced) -> Ok
                    constructs Ok

                let countThem (flag, p) = Ok { n = Map.size(p.by) }
                """, "countThem", 1));
    }

    /**
     * A floor the rule does not stand on is the next count up.
     *
     * <p>Four characters and not three. The reading that turns an end into a count is one function,
     * and this is the record's floor arriving through it — a second reading here could take the
     * number and drop whether the end is one the rule admits.
     */
    @Test
    void aFloorWrittenStrictlyIsMetByTheNextCountUp() {
        assertEquals("Named { name = \"xxxx\" }", firstValueAt("""
                module example.named

                data Named =
                    { name: String
                    }
                    invariant longerThanThree = String.length(name) > 3

                data Ok = { n: Int }

                behavior measureIt : (flag: Bool, who: Named) -> Ok
                    constructs Ok

                let measureIt (flag, who) = Ok { n = String.length(who.name) }
                """, "measureIt", 1));
    }

    /**
     * Where both the type and the record say how much, the value holds the higher of the two.
     *
     * <p>Both are rules the construction has to satisfy, so this is neither floor on its own. Written
     * both ways round because the two are combined and not chosen between: a reading that preferred
     * the record's would build two here, and one that preferred the type's would build two below.
     */
    @Test
    void theRecordsFloorWinsWhereItIsTheHigher() {
        assertEquals("Box { xs = AtLeastTwo([0, 0, 0]) }", firstValueAt("""
                module example.box

                data AtLeastTwo = List<Int>
                    invariant atLeastTwo = List.length(value) >= 2

                data Box =
                    { xs: AtLeastTwo
                    }
                    invariant atLeastThree = List.length(xs.value) >= 3

                data Ok = { n: Int }

                behavior countThem : (flag: Bool, b: Box) -> Ok
                    constructs Ok

                let countThem (flag, b) = Ok { n = List.length(b.xs.value) }
                """, "countThem", 1));
    }

    @Test
    void theTypesFloorWinsWhereItIsTheHigher() {
        assertEquals("Box { xs = AtLeastThree([0, 0, 0]) }", firstValueAt("""
                module example.box

                data AtLeastThree = List<Int>
                    invariant atLeastThree = List.length(value) >= 3

                data Box =
                    { xs: AtLeastThree
                    }
                    invariant atLeastTwo = List.length(xs.value) >= 2

                data Ok = { n: Int }

                behavior countThem : (flag: Bool, b: Box) -> Ok
                    constructs Ok

                let countThem (flag, b) = Ok { n = List.length(b.xs.value) }
                """, "countThem", 1));
    }

    /** Where nothing counts the position, the value that holds nothing is still what stands for it.
     * Held so that the rows above are read as the rule doing it. */
    @Test
    void aListNoRuleCountsIsStillOfferedTheEmptyOne() {
        assertEquals("Bag { xs = [] }", firstValueAt("""
                module example.bag

                data Bag =
                    { xs: List<Int>
                    }

                data Ok = { n: Int }

                behavior countThem : (flag: Bool, bag: Bag) -> Ok
                    constructs Ok

                let countThem (flag, bag) = Ok { n = List.length(bag.xs) }
                """, "countThem", 1));
    }
}
