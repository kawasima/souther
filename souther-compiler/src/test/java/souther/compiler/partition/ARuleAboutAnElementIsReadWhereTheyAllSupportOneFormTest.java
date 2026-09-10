package souther.compiler.partition;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * What a rule written over an element of a written container comes to.
 *
 * <p>A position several values can stand at supports what all of them support and nothing else. So a
 * container written with one number under the field a rule compares against states that number, and
 * one written with two states neither — and which of those it is turns on the numbers rather than on
 * how many elements were written or which cases they are.
 *
 * <p>Read at the arithmetic and not at the report. What a comparison inside a repeated closure is
 * then worth to a measure is a second question with an owner of its own; this one is about the
 * number a rule cuts, which is what a reader of the model would have to be able to state before any
 * of that arises.
 */
class ARuleAboutAnElementIsReadWhereTheyAllSupportOneFormTest {

    /** The number is stated where the elements agree on it. */
    @Test
    void elementsThatAgreeOnTheNumberStateIt() {
        assertEquals("n cut 100000", cut("""
                {
                        let ks = [ Big { threshold = 100000 }, Big { threshold = 100000 } ]
                        if List.any((k) -> n >= k.threshold, ks) then Yes else No
                    }"""));
    }

    /** And no number is stated where they do not. */
    @Test
    void elementsThatDisagreeStateNoNumber() {
        assertEquals("stopped", cut("""
                {
                        let ks = [ Big { threshold = 100000 }, Big { threshold = 200000 } ]
                        if List.any((k) -> n >= k.threshold, ks) then Yes else No
                    }"""));
    }

    /**
     * And the member need not be taken apart to be read.
     *
     * <p>Beside the two above and reaching the agreement by the other way in. There a projection out
     * of the name is what the members answer for; here the name is the number, and what a rule
     * compares against is what the members are. A reading that only agreed under an elimination
     * would have this one back as a rule about nothing.
     */
    @Test
    void membersThatAreTheNumberStateItToo() {
        assertEquals("n cut 100000", cut("""
                {
                        let ks = [ 100000, 100000 ]
                        if List.any((k) -> n >= k, ks) then Yes else No
                    }"""));
    }

    /** And state none where they differ, by the same rule. */
    @Test
    void membersThatAreTwoNumbersStateNeither() {
        assertEquals("stopped", cut("""
                {
                        let ks = [ 100000, 200000 ]
                        if List.any((k) -> n >= k, ks) then Yes else No
                    }"""));
    }

    /**
     * An arm narrows the set the number is taken over.
     *
     * <p>The shape a model reaches this by. What the arm settles is which members are still standing
     * where the rule is written, and the number comes from those and not from the arm.
     */
    @Test
    void anArmLeavesTheNumberToWhatItStillAdmits() {
        assertEquals("n cut 100000", cut("""
                {
                        let ks = [ AtMost { threshold = 100000 }, Whatever ]
                        if List.any((k) -> reaches(n, k), ks) then Yes else No
                    }"""));
    }

    /**
     * And an arm admitting a case two elements are of states no number where they differ.
     *
     * <p>The claim an arm looks as though it makes. A list written with two members of one case
     * satisfies the arm at either of them with a different number underneath, so what the arm leaves
     * is both — and two numbers are no number.
     */
    @Test
    void anArmAdmittingBothOfThemStatesNeitherNumber() {
        assertEquals("stopped", cut("""
                {
                        let ks = [ AtMost { threshold = 100000 }, AtMost { threshold = 200000 } ]
                        if List.any((k) -> reaches(n, k), ks) then Yes else No
                    }"""));
    }

    /** And states it where they do not differ, however many of them there are. */
    @Test
    void anArmAdmittingBothOfThemStatesTheNumberTheyShare() {
        assertEquals("n cut 100000", cut("""
                {
                        let ks = [ AtMost { threshold = 100000 }, AtMost { threshold = 100000 } ]
                        if List.any((k) -> reaches(n, k), ks) then Yes else No
                    }"""));
    }

    /**
     * A container an operation built states nothing, whatever went into it.
     *
     * <p>Both halves are written out here and each holds one number. What the operation answers is
     * the elements of both, and a reading that wrote out either would be stating of every element
     * what half of them satisfy.
     */
    @Test
    void aContainerAnOperationBuiltStatesNoNumber() {
        assertEquals("stopped", cut("""
                {
                        let ks = List.append([ Big { threshold = 100000 } ],
                                             [ Big { threshold = 100000 } ])
                        if List.any((k) -> n >= k.threshold, ks) then Yes else No
                    }"""));
    }

    /**
     * One member this cannot read takes the answer away from the members it can.
     *
     * <p>The number the readable member states is the one the answer would be, which is what makes
     * this worth writing out: what is being asked is what every value standing there supports, and a
     * member with nothing read off it supports nothing — so the agreement is not there to be had,
     * however plainly the rest of the list agrees.
     */
    @Test
    void oneMemberWithNothingReadOffItStatesNoNumberForAnyOfThem() {
        assertEquals("stopped", cut("""
                {
                        let ks = [ Big { threshold = 100000 }
                                 , if c then Big { threshold = 100000 }
                                   else Big { threshold = 100000 } ]
                        if List.any((k) -> n >= k.threshold, ks) then Yes else No
                    }"""));
    }

    /**
     * A plurality standing inside a plurality is not read.
     *
     * <p>The inner list is written out and every one of its members is a name standing for two
     * values, so a reading that went on would be asking what every pairing of them agrees on: two
     * members holding two each is four readings, and a rule written down a chain of these is two to
     * the power of its depth. What a rule about a written container is is one container, and the
     * members of it are read with no plurality in them.
     *
     * <p>Every number written here is the same, which is what makes it worth writing out: what
     * refuses this is the rule and not a disagreement between the members.
     */
    @Test
    void aPluralityStandingInsideOneIsNotRead() {
        assertEquals("stopped", cut("""
                {
                        let ks = [ Big { threshold = 100000 }, Big { threshold = 100000 } ]
                        if List.any((k) -> List.any((j) -> n >= j.threshold, [k, k]), ks)
                            then Yes else No
                    }"""));
    }

    /**
     * And a container a member holds is not one this reading arrives at either.
     *
     * <p>Beside the one above and refused before it rather than by it: the way to a written list is
     * followed through the steps with one successor, and a member of a container is not one of
     * those, so there is no second plurality here for the rule above to refuse. The two are written
     * out apart because a reader gaining either would still meet the other.
     */
    @Test
    void aContainerAMemberHoldsStatesNoNumber() {
        assertEquals("stopped", cut("""
                {
                        let ks = [ Holder { items = [ Big { threshold = 100000 } ] }
                                 , Holder { items = [ Big { threshold = 100000 } ] } ]
                        if List.any((k) -> List.any((j) -> n >= j.threshold, k.items), ks)
                            then Yes else No
                    }"""));
    }

    /**
     * A choice between two constructions still states nothing, even between two alike.
     *
     * <p>Which is the boundary as it was. The reading names no values for what stands at a choice,
     * so there is no set here for the numbers to be taken over — and it is that absence rather than
     * a rule about choices, so nothing here has to be kept in step with what a container states.
     */
    @Test
    void aChoiceBetweenTwoConstructionsStatesNoNumber() {
        assertEquals("stopped", cut("""
                {
                        let k = if c then Big { threshold = 100000 }
                                else Big { threshold = 100000 }
                        if n >= k.threshold then Yes else No
                    }"""));
    }

    /**
     * The number the body's comparison cuts, or that the reading stopped.
     *
     * <p>Each of these bodies writes one comparison, which is why the choice they are told apart by
     * is made on a parameter rather than on a number: a second comparison would leave this picking
     * one of them by where it stands, after which a fixture could be about a rule nobody meant.
     */
    private static String cut(String body) {
        String source = """
                module g

                data Big = { threshold: Int }
                data Holder = { items: List<Big> }
                data Yes
                data No

                data AtMost = { threshold: Int }
                data Whatever
                data Reason = AtMost | Whatever

                let reaches (n: Int, reason: Reason): Bool =
                    match reason with
                        | AtMost { threshold } -> n >= threshold
                        | Whatever             -> false

                behavior classify : (n: Int, c: Bool) -> Yes | No
                let classify (n, c) = %s

                example classify
                    | "one" : (1, true) -> No
                """.formatted(body);

        ReadComparisons read = ReadComparisons.of(source, "classify");
        ComparisonReadings.Reading against = read.only();
        return switch (AffineReading.read(against.comparison().stated(), read.inputs(),
                against.reads(),
                read.rules())) {
            case AffineReading.OfAComparison.Stopped _ -> "stopped";
            case AffineReading.OfAComparison.CutsNothing _ -> "cuts nothing";
            case AffineReading.OfAComparison.Cuts cuts -> said(cuts);
        };
    }

    /** The quantity and where it is cut, in the order the positions are named in. */
    private static String said(AffineReading.OfAComparison.Cuts cuts) {
        Map<String, BigDecimal> over = new LinkedHashMap<>();
        AffineReading.ordered(cuts.read().form())
                .forEach(each -> over.put(each.getKey().toString(), each.getValue()));
        return over.entrySet().stream()
                .map(each -> each.getValue().compareTo(BigDecimal.ONE) == 0 ? each.getKey()
                        : each.getValue() + "*" + each.getKey())
                .reduce((a, b) -> a + " + " + b).orElseThrow()
                + " cut " + cuts.read().cut().stripTrailingZeros().toPlainString();
    }
}
