package souther.compiler.partition;

import org.junit.jupiter.api.Test;

import souther.compiler.inputs.BlockReason;
import souther.compiler.inputs.BlockedDescent;
import souther.compiler.inputs.RulesLeftUnread;
import souther.compiler.inputs.TermPath;
import souther.compiler.types.Type;
import souther.compiler.query.Adequacy;
import souther.compiler.query.Compilation;
import souther.compiler.query.PartitionEvidence;
import souther.compiler.query.Weakening;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * One position this compiler could not enter is one finding, and the findings beside it are not
 * folded away with it.
 *
 * <p>Issue #1084. A {@code Map} was reported twice: once as a position the walk could not reach
 * into, and once as a position whose rules nothing reached — which is the first said from the other
 * end, because the rules under the map are read by a reading nothing opened. Both sentences are
 * true, and only one of them is something that went wrong.
 *
 * <p><b>Held against sources, and the pairs asserted rather than a count.</b> What is being fixed is
 * which findings a document carries, so a test that only counted them would pass over the wrong one
 * surviving. Each of these names the finding it wants and the finding it does not.
 */
class AStopThisCompilerMadeIsSaidOnceTest {

    /** The model from the issue. Nothing measures the map, so the axis is still waiting on the
     *  structural reading when the closure is taken. */
    private static final String A_MAP_NOTHING_MEASURES = """
            module probe.map

            data Ok
            data Amount = Int
                invariant ranged = value >= 0 && value <= 100
            data Req = { cost: Map<String, Amount> }

            behavior f : (r: Req) -> Ok
                constructs Ok
            let f (r) = Ok
            """;

    /**
     * The same map, with a rule that measures it.
     *
     * <p>The one model where what a position is waiting on and what its reading came to disagree.
     * {@code Map.size} divides the position, so the axis is answered for and keeps no continuation —
     * and the walk still never went into what the map holds.
     */
    private static final String A_MAP_A_RULE_MEASURES = """
            module probe.mapsize

            data Ok
            data Amount = Int
                invariant ranged = value >= 0 && value <= 100
            data Req = { cost: Map<String, Amount> }
                invariant nonEmpty = Map.size(cost) > 0

            behavior f : (r: Req) -> Ok
                constructs Ok
            let f (r) = Ok
            """;

    /**
     * The same map again, with the line drawn by the body rather than by a declaration.
     *
     * <p>The other way what a position is measured at is rebuilt. Nothing divides the map, so the
     * measure is made at the number the body names ({@link PositionMeasurements#measuredAt}) — a
     * caller writing the parts of a position's measurements out by hand, and the place a position
     * that had stopped once came back with nothing to say so. Named for a module of its own because
     * {@code guard} is a word of the language.
     */
    private static final String A_MAP_A_BODY_MEASURES = """
            module probe.bodyline

            data Amount = Int
                invariant ranged = value >= 0 && value <= 100
            data Req = { cost: Map<String, Amount> }

            behavior f : (r: Req) -> Int
            let f (r) = if Map.size(r.cost) > 0 then 1 else 2
            """;

    /**
     * A rule about the map's own size that nothing answered, at a position the walk could not enter.
     *
     * <p>Two facts about two different rules at one path. What {@code notEmpty} says about the size
     * was read — {@code M} is a declaration and its clause reached a reader — and no reading turned
     * it into the values the position may hold. That the contents of the map are out of reach says
     * nothing about it.
     */
    private static final String A_QUESTION_AT_A_POSITION_NOTHING_ENTERED = """
            module probe.question

            data Domestic
            data Overseas
            data Kind = Domestic | Overseas
            data K = String
            data V = String
            data M = Map<K, V>
                invariant notEmpty = Map.size(value) /= 0
            data T = { kind: Kind, m: M }

            behavior look : (t: T) -> Int
            let look (t) = 1
            """;

    /**
     * The stop is reported as the stop, and the handing over it left standing is not reported again.
     *
     * <p>The pair the issue is about. {@code RulesNotReached} here would be the consequence of the
     * finding beside it and not a second thing an author could act on.
     */
    @Test
    void aPositionTheWalkCouldNotEnterIsOneFinding() {
        List<Weakening> said = weakeningOf(A_MAP_NOTHING_MEASURES, "f");

        assertEquals(1, said.size(), () -> "one stop, one finding: " + said);
        assertTrue(said.getFirst() instanceof Weakening.ModelReadingIncomplete(
                        ClosureGap.PositionNotReachedInto gap)
                        && gap.why() instanceof BlockReason.UnsupportedTraversal,
                () -> "and it is the stop itself: " + said);
    }

    /**
     * And it goes on being reported once something else measures the position.
     *
     * <p>What the fold would have cost if it were taken from what the axis is waiting on. This
     * position is answered for, so it keeps no continuation; read from there, the handing over
     * folds into a finding nothing writes and the model says nothing about the map at all.
     */
    @Test
    void andTheStopSurvivesARuleThatMeasuresThePosition() {
        List<Weakening> said = weakeningOf(A_MAP_A_RULE_MEASURES, "f");

        assertTrue(said.stream().anyMatch(each -> each instanceof Weakening.ModelReadingIncomplete(
                        ClosureGap.PositionNotReachedInto _)),
                () -> "the walk did not go into the map, whatever divides it: " + said);
        assertTrue(said.stream().noneMatch(each -> each instanceof Weakening.ModelReadingIncomplete(
                        ClosureGap.RulesNotReached _)),
                () -> "and the handing over it left standing is that same stop: " + said);
    }

    /**
     * And it survives a measure being made at the number a body names.
     *
     * <p>Both ways what a position is measured at is rebuilt, because what holds the two facts
     * together is that they travel as one value and every rebuild is a place a caller can drop one.
     * This one goes through {@link PositionMeasurements#measuredAt}; the test above goes through the
     * division. The finding names the term the measure was made at, which is the measure carrying
     * the fact and not the position that was blocked — the position is that measure's path.
     */
    @Test
    void andItSurvivesTheAxisBeingRePointedAtWhatABodyMeasures() {
        List<Weakening> said = weakeningOf(A_MAP_A_BODY_MEASURES, "f");

        assertEquals(1, said.size(), () -> "one stop, one finding: " + said);
        assertTrue(said.getFirst() instanceof Weakening.ModelReadingIncomplete(
                        ClosureGap.PositionNotReachedInto gap)
                        && gap.why() instanceof BlockReason.UnsupportedTraversal,
                () -> "the walk did not go into the map, and the axis is now the size: " + said);
    }

    /**
     * A question standing at such a position stands.
     *
     * <p>The other half of not folding on the path. A question is raised by a rule this compiler
     * read and neither reader answered, so the rules a stop left unread raise none — and the only
     * questions a suppression at the path could ever reach are the real ones. This one was being
     * dropped because the map's contents are out of reach, which is a fact about other rules
     * entirely.
     */
    @Test
    void aQuestionAtSuchAPositionIsStillAsked() {
        List<Weakening> said = weakeningOf(A_QUESTION_AT_A_POSITION_NOTHING_ENTERED, "look");

        assertTrue(said.stream().anyMatch(each -> each instanceof Weakening.ModelReadingIncomplete(
                        ClosureGap.QuestionUnanswered _)),
                () -> "the rule about the map's size was read and nothing answered it: " + said);
        assertTrue(said.stream().anyMatch(each -> each instanceof Weakening.ModelReadingIncomplete(
                        ClosureGap.PositionNotReachedInto _)),
                () -> "and the map's contents are still out of reach: " + said);
    }

    /**
     * A handing over left standing by a blocked descent may not travel without the descent.
     *
     * <p>The transport half. Which of the two the reading found is settled where both were in hand,
     * and every rebuild of an axis after that is a place one of them can be dropped — a position
     * whose elements could not be reached came back out of the second phase with nothing to say it
     * had ever stopped, once. Dropped here, the arm would fold into a finding nothing writes and the
     * stop would go unsaid, so the pair is refused rather than reported short.
     */
    @Test
    void anArmNamingABlockedDescentMayNotTravelWithoutIt() {
        assertThrows(IllegalArgumentException.class,
                () -> new ReadingResidue(null,
                        Set.of(new RulesLeftUnread.Handoff(
                                new RulesLeftUnread.HandoffUnread.FromBlockedDescent()))),
                "the arm names a descent this residue does not carry");
    }

    /**
     * Every way the two facts can arrive together, and what each comes to.
     *
     * <p><b>The relation and not one direction of it.</b> Which findings a residue comes to is a
     * biconditional between what the ledger recorded and what the walk found, crossed with the one
     * fold. Asserted a case at a time from whichever side a reader happens to be building, each
     * assertion holds the half it was written for — which is how one stop came to be written from
     * two ends with nothing relating them in the first place (issue #1084).
     *
     * <p>Built here rather than compiled from sources. Two of the six rows are this compiler
     * contradicting itself and no model produces them, and a row of the other four needs a position
     * that both loses a clause of its own and cannot be entered, which no model this compiler
     * accepts carries either. What is being held is the arithmetic over the arms, and that is what a
     * synthetic axis is exactly good for.
     */
    @Test
    void whatEachPairOfFactsComesTo() {
        // The walk could not go in, and the handing over it left standing is that same stop.
        assertEquals(Set.of(ClosureGap.PositionNotReachedInto.class),
                gapKindsOf(residue(BLOCKED, fromBlockedDescent())),
                "the stop, once");
        // And a clause this reading lost beside it is a finding of its own, at the same path.
        // The one the issue names: folded on the path, this one goes with the other.
        assertEquals(Set.of(ClosureGap.PositionNotReachedInto.class,
                        ClosureGap.RulesNotReached.class),
                gapKindsOf(residue(BLOCKED,
                        new RulesLeftUnread.ClauseOfThisReadingWasUnread(),
                        fromBlockedDescent())),
                "a clause this reading lost is not the stop, and does not fold into it");
        // The walk went on and left a recipient with no reading. Nothing else says so.
        assertEquals(Set.of(ClosureGap.RulesNotReached.class),
                gapKindsOf(residue(null, notFullyAccepted())),
                "a recipient nothing opened is its own finding");
        assertEquals(Set.of(ClosureGap.RulesNotReached.class),
                gapKindsOf(residue(null, new RulesLeftUnread.ClauseOfThisReadingWasUnread())),
                "and so is a clause lost where nothing stopped the walk");

        // And the two rows where the ledger and the walk contradict each other. Neither is a state
        // of a model: over an owed handing over, nobody was named as a recipient exactly where the
        // walk could not go in. Let through, the second is #1084's two entries reached another way.
        assertThrows(IllegalArgumentException.class,
                () -> residue(BLOCKED, notFullyAccepted()),
                "a recipient was named at a position the walk could not enter");
        assertThrows(IllegalArgumentException.class,
                () -> residue(null, fromBlockedDescent()),
                "nobody was named at a position the walk went into");
    }

    private static final BlockedDescent BLOCKED = new BlockedDescent(
            new BlockReason.UnsupportedTraversal(BlockReason.Traversal.MAPPING_CONTENT));

    private static RulesLeftUnread fromBlockedDescent() {
        return new RulesLeftUnread.Handoff(
                new RulesLeftUnread.HandoffUnread.FromBlockedDescent());
    }

    private static RulesLeftUnread notFullyAccepted() {
        return new RulesLeftUnread.Handoff(
                new RulesLeftUnread.HandoffUnread.NotFullyAccepted());
    }

    private static ReadingResidue residue(BlockedDescent blocked, RulesLeftUnread... unread) {
        return new ReadingResidue(blocked, Set.of(unread));
    }

    /**
     * Which kinds of gap one axis carrying {@code residue} leaves the partition measure short of.
     *
     * <p>The kinds and not the values: what a gap is keyed by is asserted where that decision is
     * ({@link #theStopIsNamedForThePositionItIsAt}), and repeating it in every row here would make
     * every row of the arithmetic fail the day the identity is revisited.
     */
    private static Set<Class<?>> gapKindsOf(ReadingResidue residue) {
        MeasureClosure.Both closed = MeasureClosure.of(
                List.of(new PositionAccount("f", TermPath.of("r").then("cost"), Type.BOOL, residue, souther.compiler.values.ValueSet.ANY,
                        null, List.of(), List.of())),
                List.of(), new LinesRead());
        return ((MeasureClosure.OfThePartition.Open) closed.partition()).by().stream()
                .map(Object::getClass).collect(java.util.stream.Collectors.toSet());
    }

    /**
     * The stop is named for the position it is at, and not for a number that position is measured
     * at.
     *
     * <p>Where the two come apart. The map's contents are what the walk could not reach and the
     * size is what a rule measures, so the position and the number are different words here.
     *
     * <p><b>This used to name the number, and that reading needed one measure per position.</b> The
     * argument for it was that a gap beside a measurement is about the measure a reader holds — but
     * a location is measured at as many numbers as the rules name of it, and one stop under such a
     * location came out once per number, which is a compiler's own state counted several times. What
     * weakens one measurement is said per measurement elsewhere; these are one behavior's account of
     * what its reading of the model came to, and a reader holding a measure reaches its position by
     * the path it reads from.
     */
    @Test
    void theStopIsNamedForThePositionItIsAt() {
        List<Weakening> said = weakeningOf(A_MAP_A_BODY_MEASURES, "f");

        assertEquals(List.of("r.cost"),
                said.stream()
                        .filter(each -> each instanceof Weakening.ModelReadingIncomplete(
                                ClosureGap.PositionNotReachedInto _))
                        .map(each -> ((ClosureGap.PositionNotReachedInto)
                                ((Weakening.ModelReadingIncomplete) each).cause()).at().toString())
                        .toList(),
                () -> "the position the walk stopped at, not a number measured of it: " + said);
    }

    private static List<Weakening> weakeningOf(String source, String behavior) {
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        Map<String, PartitionEvidence> partitions = compilation.db()
                .ask(new Adequacy.Coverage(compilation.modules().get(0))).value();
        return List.copyOf(partitions.get(behavior).weakening().causes());
    }
}
