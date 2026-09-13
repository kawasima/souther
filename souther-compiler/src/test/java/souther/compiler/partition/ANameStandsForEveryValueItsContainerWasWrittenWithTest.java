package souther.compiler.partition;

import org.junit.jupiter.api.Test;
import souther.compiler.check.Symbols;
import souther.compiler.core.Core;
import souther.compiler.inputs.InputReads;
import souther.compiler.inputs.ReadMeaning;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * What a name an operation handed an element on stands for, where the container was written out.
 *
 * <p>A plurality this reading can write out and one it cannot are different answers. An element of a
 * list written in the source stands for those values and no others, and that is what lets a reader
 * state what holds of every one of them; an element of a container an operation built stands for
 * values this reading has not got, and a statement about all of them cannot be made from it. Read as
 * one answer, the second would be the first with members missing, which is worse than either.
 *
 * <p>None of this says what a rule about such a name comes to. Which of the values a name may be
 * read as, and what an arithmetic makes of them, are the readers' own questions asked of the answer
 * here.
 */
class ANameStandsForEveryValueItsContainerWasWrittenWithTest {

    /** The values a list was written with are what its element stands for. */
    @Test
    void aWrittenListNamesEveryValueItsElementCanTake() {
        assertEquals("OneOf[g.Big, g.Big]", standingOn("""
                {
                        let ks = [ Big { threshold = 100000 }, Big { threshold = 200000 } ]
                        if List.any((k) -> n >= k.threshold, ks) then Yes else No
                    }"""));
    }

    /** And a helper that writes one out is the same list a name further away. */
    @Test
    void aHelperThatWritesTheListOutNamesThemToo() {
        assertEquals("OneOf[g.Big, g.Big]", standingOn(
                "if List.any((k) -> n >= k.threshold, twoBigs(n)) then Yes else No"));
    }

    /**
     * A container an operation built names none of them.
     *
     * <p>Both halves were written out here and the operation holds the elements of both, so a
     * reading that took either would have written out a set missing the other. It is answered as the
     * plurality it is and left there.
     */
    @Test
    void aContainerAnOperationBuiltNamesNoneOfThem() {
        assertEquals("Element", standingOn("""
                {
                        let ks = List.append([ Big { threshold = 100000 } ],
                                             [ Big { threshold = 200000 } ])
                        if List.any((k) -> n >= k.threshold, ks) then Yes else No
                    }"""));
    }

    /**
     * An arm narrows the set to the members it admits.
     *
     * <p>Which is the whole of what an arm settles. It takes out what it does not admit; what is
     * left is what the name can stand for, and how many that is is the container's answer.
     */
    @Test
    void anArmTakesOutTheMembersItDoesNotAdmit() {
        assertEquals("OneOf[g.AtMost]", standingOn("""
                {
                        let ks = [ AtMost { threshold = 100000 }, Whatever ]
                        if List.any((k) -> reaches(n, k), ks) then Yes else No
                    }"""));
    }

    /**
     * And leaves two where two are of the case it admits.
     *
     * <p>The claim the arm is most likely to be read as making, and does not make. A list may be
     * written with two members of one case, and an arm admitting that case has told them apart in no
     * way at all — so what stands under the arm's name is still both of them.
     */
    @Test
    void anArmAdmittingOneCaseLeavesBothMembersOfIt() {
        assertEquals("OneOf[g.AtMost, g.AtMost]", standingOn("""
                {
                        let ks = [ AtMost { threshold = 100000 }, AtMost { threshold = 200000 } ]
                        if List.any((k) -> reaches(n, k), ks) then Yes else No
                    }"""));
    }

    /**
     * A list written empty names no value, which is not a set of none.
     *
     * <p>A statement about every member of nothing holds whatever it says, so an empty set would be
     * the strongest answer this could give and the one least entitled to be believed.
     */
    @Test
    void aListWrittenEmptyNamesNoValueRatherThanNoneOfThem() {
        assertEquals("Element", standingOn("""
                {
                        let ks: List<Big> = []
                        if List.any((k) -> n >= k.threshold, ks) then Yes else No
                    }"""));
    }

    /**
     * What the name the threshold side is read off stands for.
     *
     * <p>Followed through the names that denote one value, which is what puts the reading inside the
     * arm and behind the field: the answer wanted is about the name the values arrive under, and
     * every name between here and it stands for one thing.
     */
    private static String standingOn(String body) {
        String source = """
                module g

                data Big = { threshold: Int }
                data Yes
                data No

                data AtMost = { threshold: Int }
                data Whatever
                data Reason = AtMost | Whatever

                let twoBigs (c: Int): List<Big> =
                    [ Big { threshold = 100000 }, Big { threshold = 200000 } ]

                let reaches (n: Int, reason: Reason): Bool =
                    match reason with
                        | AtMost { threshold } -> n >= threshold
                        | Whatever             -> false

                behavior classify : (n: Int) -> Yes | No
                let classify (n) = %s

                example classify
                    | "one" : (1) -> No
                """.formatted(body);

        ReadComparisons read = ReadComparisons.of(source, "classify");
        ComparisonReadings.Reading only = read.only();
        Symbols symbols = read.rules().symbols();

        Core side = only.comparison().right();
        InputReads at = only.reads();
        while (true) {
            if (side instanceof Core.FieldAccess field) {
                side = field.target();
                continue;
            }
            if (!(side instanceof Core.Read name)) {
                return "not a name: " + side.getClass().getSimpleName();
            }
            ReadMeaning meaning = at.meaningOf(name, symbols, read.rules().newtypes());
            if (!(meaning instanceof ReadMeaning.Through through)) {
                return said(meaning);
            }
            side = through.denotes().value();
            at = through.denotes().at();
        }
    }

    /** The answer, by which of the five it is and which values a plurality holds. */
    private static String said(ReadMeaning meaning) {
        return switch (meaning) {
            case ReadMeaning.OneOf one -> "OneOf" + one.alternatives().stream()
                    .map(each -> switch (each.value()) {
                        case Core.Construct made -> made.typeName().toString();
                        case Core.UnitValue unit -> unit.data().toString();
                        default -> each.value().getClass().getSimpleName();
                    })
                    .toList();
            case ReadMeaning.Position position -> "Position[" + position.path() + "]";
            case ReadMeaning.Element _ -> "Element";
            case ReadMeaning.Unknown _ -> "Unknown";
            case ReadMeaning.Through _ -> throw new IllegalStateException("followed already");
        };
    }

    /** Every answer this test can get, so a case added to the reading arrives here rather than
     *  under a word one of these tests already expects. */
    @Test
    void theFiveAnswersAreTheOnesThisTestKnows() {
        Map<String, String> byName = new LinkedHashMap<>();
        for (Class<?> each : ReadMeaning.class.getPermittedSubclasses()) {
            byName.put(each.getSimpleName(), each.getSimpleName());
        }
        assertEquals(Map.of("Position", "Position", "Through", "Through", "OneOf", "OneOf",
                "Element", "Element", "Unknown", "Unknown"), byName);
    }
}
