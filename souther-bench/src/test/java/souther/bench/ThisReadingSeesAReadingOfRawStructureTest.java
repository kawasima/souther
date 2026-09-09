package souther.bench;

import org.junit.jupiter.api.Test;

import souther.bench.PositionReadings.Authority;
import souther.bench.PositionReadings.Traversal;
import souther.test.CompiledClasses;

import java.lang.classfile.ClassModel;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the walk over a stage's readings sees, asked of code written to be read.
 *
 * <p>The rule beside this one says the compiler has no path from its partitioning stage to raw
 * structure. That is a fact about the compiler as it stands, and it stays green if the walk stops
 * seeing something: break the collection of one instruction and, with no such path written today,
 * nothing goes red. What the rule protects would be gone and nothing would say so.
 *
 * <p>So the walk is asked here about a model that holds one of everything. Each fixture is a claim
 * a reader can check by reading it — this reaches a declaration, this takes a compound apart, this
 * one only reads a name — and the walk's answer is held against it. A mutation run once against
 * the compiler proves the walk saw a defect that day; this says it still would.
 *
 * <p>The model is written in sums of its own. What the walk is handed is which sum says what a
 * module wrote and which is built out of others, so a model with its own asks whether those sets
 * are derived from the classes it was given rather than known. Written in the compiler's types, a
 * fixture would be checking a spelling.
 */
class ThisReadingSeesAReadingOfRawStructureTest {

    /** The stage of the model, whose readers are the ones held to the rule. */
    private static final String STAGE = "souther.bench.readings.Stage";

    private static final List<Authority> AUTHORITIES = List.of(
            new Authority("how a name is written where a reader meets one",
                    "souther.bench.readings.Opaque", Traversal.OPAQUE),
            new Authority("what a name comes to on the way",
                    "souther.bench.readings.Transparent", Traversal.TRANSPARENT));

    private static PositionReadings.Over model() {
        return new PositionReadings.Over(written(), STAGE,
                "souther.bench.readings.Written$Declared", "souther.bench.readings.Written$Names#declaredNode",
                "souther.bench.readings.Written$Position$Built",
                "Lsouther/bench/readings/Written$Position;", AUTHORITIES);
    }

    /**
     * The compiled model, which is what this is a reading of.
     *
     * <p>Found through the output this test was compiled into, the way everything else here finds
     * what a build produced. Read from wherever this happens to have been started instead, the
     * model would be missing whenever that was somewhere else — and a walk over no classes finds no
     * reading and says nothing, which is why a package holding none is refused where it is read.
     */
    private static List<ClassModel> written() {
        return CompiledClasses.ofModule(ThisReadingSeesAReadingOfRawStructureTest.class)
                .inPackage("souther.bench.readings");
    }

    private static List<String> bypassing() {
        return PositionReadings.of(model()).named();
    }

    /**
     * Every reader that reaches raw structure is seen, and only those.
     *
     * <p>Both halves at once, and named rather than counted. A walk that saw one shape and not
     * another would pass a check that only asked how many there were, and which shapes it sees is
     * the whole of what this is about.
     */
    @Test
    void everyReaderThatReachesRawStructureIsSeenAndNoOtherIs() {
        assertEquals(List.of(
                        // Its own code reaches a declaration, and its own code takes a compound
                        // apart. The two plainest readings there are.
                        // Asking which compound a position is and taking nothing out of it, which
                        // is the reading a walk over components alone would not see.
                        "asksWhichCompoundItIs",
                        "readsADeclaration",
                        // Past an owner the walk is told to go through, which is what keeps a
                        // caller's own reading from hiding behind one.
                        "readsPastATransparentBoundary",
                        // Two calls away, behind something that answers with a word. What is
                        // followed is the call: a walk over what came back would see neither.
                        "readsThroughAHelper",
                        "takesACompoundApart",
                        // What a component holds is written only in the generic signature, so a
                        // reading over descriptors alone would see this one hold nothing.
                        "takesApartOneHoldingMany",
                        "takesOneApartThroughAHelper"),
                bypassing(),
                "the readers the walk sees, against the ones written to be seen. A reader missing"
                        + " from this is a shape the walk has stopped seeing, and one that is here"
                        + " and should not be reads no structure at all");
    }

    /**
     * A boundary named by its class stands in front of the operation beside it; named by the
     * operation, it does not.
     *
     * <p>Which is the whole of what sizing a boundary decides. The model's authority holds two
     * questions, and the second reaches the declarations for reasons of its own. Named by the
     * class, a reader asking that second question is answered for by an entry about the first —
     * and the walk stops at a boundary that was never about what it is standing in front of.
     */
    @Test
    void namingTheOperationCatchesTheQuestionBesideItAndNamingTheClassDoesNot() {
        assertFalse(bypassing().contains("asksTheQuestionBesideIt"),
                "named by its class, the entry about one question answers for the other too");

        PositionReadings.Over over = model();
        PositionReadings.Over named = new PositionReadings.Over(over.classes(), over.stage(),
                over.declaration(), over.lookup(), over.compound(), over.held(),
                over.authorities().stream()
                        .map(each -> each.owns().equals("souther.bench.readings.Opaque")
                                ? new Authority(each.question(),
                                        "souther.bench.readings.Opaque#spelling", each.traversal())
                                : each)
                        .toList());

        assertTrue(PositionReadings.of(named).named().contains("asksTheQuestionBesideIt"),
                "named by the operation it answers for, the question beside it arrives here");
        assertFalse(PositionReadings.of(named).named().contains("asksAnAuthority"),
                "and the reader of the question that is answered for still is");
    }

    /**
     * What the walk does not call a reading, said as itself.
     *
     * <p>The same list read the other way. These are the four ways of touching the model that are
     * not readings of it, and a walk calling any of them one would refuse code that is doing
     * nothing wrong — which is how a rule stops being read.
     */
    @Test
    void touchingTheModelWithoutReadingItIsNotOne() {
        List<String> seen = bypassing();
        for (String each : List.of("asksAnAuthority", "readsANameOffALeaf", "makesACompound",
                "readsAComponentHoldingNoPosition")) {
            assertFalse(seen.contains(each), () -> each + " reads no structure, and the walk says"
                    + " it does. What it does instead is written on it: " + seen);
        }
    }

    /**
     * Both boundaries are met on a way from the stage, and a helper on one is not mistaken for a
     * boundary.
     *
     * <p>The distinction the walk cannot make and must not be asked to. `Helpers` sits between the
     * stage and a reading exactly as an authority does, and is not one — it is written as a helper
     * and the model says so. What is met is recorded; what answers is the table's to say.
     */
    @Test
    void everyBoundaryOfTheModelIsMetAndAHelperIsNotOne() {
        PositionReadings.Reading read = PositionReadings.of(model());

        assertEquals(List.of("souther.bench.readings.Opaque",
                        "souther.bench.readings.Transparent"),
                read.encountered().stream().sorted().toList(),
                "the places the walk met that the table names, which is both of them and nothing"
                        + " else: a helper is met too and is not named, so it is not here");
    }

    /**
     * A helper between the stage and an authority changes nothing.
     *
     * <p>The way a rule about owners turns into a list of whatever is on the way. Put something
     * ordinary between the two and a check that asked every step to name its question would call
     * the helper unanswered and the authority unmet — and the way to green would be to call the
     * helper an owner. What is on the way is walked through; what answers is met at the end of it.
     */
    @Test
    void aHelperBetweenTheStageAndAnAuthorityChangesNothing() {
        PositionReadings.Reading read = PositionReadings.of(model());

        assertFalse(read.named().contains("asksAnAuthorityThroughAHelper"),
                "the authority answers however many steps away it is: " + read.named());
        assertTrue(read.encountered().contains("souther.bench.readings.Opaque"),
                "and is met on that way, so nothing calls it a boundary that is not there");
    }

    /**
     * An authority the walk never meets is not among those it met.
     *
     * <p>Which is what a boundary that has gone looks like, and what the rule beside this one reads
     * to say so. Named by an operation nothing is written under, this stands nowhere.
     */
    @Test
    void anAuthorityStandingNowhereIsNotMet() {
        assertFalse(alsoNaming("souther.bench.readings.Opaque#spellingAsItWasCalled")
                        .contains("souther.bench.readings.Opaque#spellingAsItWasCalled"),
                "nothing of that name is there to be met");
    }

    /**
     * Nor is one that answers on a way the stage is not on.
     *
     * <p>The harder half, and the one a name that is not there cannot ask about. This authority is
     * written, reached, and reads the declarations — everything a walk backwards from a reading
     * meets on the way. What it is not is anywhere the stage arrives, and the sentence beside the
     * rule says from the stage. Met and standing are two answers, and only the second is the one
     * being made.
     */
    @Test
    void anAuthorityOnAWayTheStageIsNotOnIsNotStanding() {
        assertFalse(alsoNaming("souther.bench.readings.Elsewhere").contains(
                        "souther.bench.readings.Elsewhere"),
                "it answers for a reader of its own, and the stage reaches neither");
        assertTrue(alsoNaming("souther.bench.readings.Elsewhere")
                        .contains("souther.bench.readings.Opaque"),
                "while the one the stage does arrive at is still standing");
    }

    /** What the walk records as standing, with one more entry named. */
    private static Set<String> alsoNaming(String owns) {
        PositionReadings.Over over = model();
        List<Authority> withOneMore = new ArrayList<>(over.authorities());
        withOneMore.add(new Authority("a question nothing on a way from the stage asks", owns,
                Traversal.OPAQUE));
        return PositionReadings.of(new PositionReadings.Over(over.classes(), over.stage(),
                over.declaration(), over.lookup(), over.compound(), over.held(), withOneMore))
                .encountered();
    }

    /**
     * A case of what is built out of types that is not a record is refused.
     *
     * <p>Which components hold a type is read off the record attribute, so a case without one holds
     * nothing this can see. The claim that a case added to the sum joins the rule holds exactly as
     * far as this refuses, which is why the refusal is asked of a model that has one.
     */
    @Test
    void aCaseBuiltOutOfTypesThatIsNotARecordIsRefused() {
        assertEquals(List.of("souther.bench.readings.Written$Position$Built$Awkward"),
                PositionReadings.of(model()).madeOfNonRecords(),
                "the arm of the model written as a class rather than a record: what it holds is"
                        + " not where this reads what a compound holds, so it is named rather than"
                        + " read past");
    }

    /**
     * An opaque boundary stops the walk, and what it reads to answer is its own business.
     *
     * <p>Asked by taking the boundary away: the reader that asks it is not a bypass while the
     * authority is named and is one when it is not. Without this the table could hold entries that
     * change nothing, and an authority that stopped stopping anything would look like a clean
     * compiler.
     */
    @Test
    void anOpaqueBoundaryIsWhatStopsTheWalk() {
        assertFalse(bypassing().contains("asksAnAuthority"),
                "the authority answers, so its caller reaches nothing raw");

        PositionReadings.Over over = model();
        PositionReadings.Over without = new PositionReadings.Over(over.classes(), over.stage(),
                over.declaration(), over.lookup(), over.compound(), over.held(),
                AUTHORITIES.stream()
                        .filter(each -> each.traversal() != Traversal.OPAQUE).toList());
        List<String> reaching = PositionReadings.of(without).named();
        assertTrue(reaching.contains("asksAnAuthority"),
                "with nothing answering for it, the same reader reaches the declaration behind it:"
                        + " " + reaching);
    }
}
