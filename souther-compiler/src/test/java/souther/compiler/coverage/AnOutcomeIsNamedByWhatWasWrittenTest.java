package souther.compiler.coverage;

import org.junit.jupiter.api.Test;

import souther.compiler.core.Core;
import souther.compiler.types.WrittenOwner;
import souther.compiler.query.Bodies;
import souther.compiler.query.Compilation;
import souther.compiler.types.SourceConstruct;
import souther.compiler.types.ConstructOccurrence;
import souther.compiler.types.SourceConstructOrigin;
import souther.compiler.types.Type;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a coverage report calls a construct, and what it calls one way through it.
 *
 * <p>Three constructs of the language become one {@code if} before anything runs — an {@code if}, a
 * {@code guard}, and a comprehension's condition — and the tree that runs is where the report used
 * to read the answer off. It named all three {@code guard}, and named the ways through them
 * {@code then} and {@code else} however the source wrote them, so two of every three readers were
 * sent looking for a form that is not in the file.
 *
 * <p>Held as the pair, because that is where the meaning is. The construct comes from the origin the
 * fork carries, the way through it is a {@link SourceOutcome}, and neither says the other.
 */
class AnOutcomeIsNamedByWhatWasWrittenTest {

    private static final String AN_IF = """
            module demo

            behavior price : (temp: Int) -> Int
            let price (temp) = if temp >= 240 then 1 else 2
            """;

    private static final String A_GUARD = """
            module demo

            data Hot = { at: Int }
            data Low

            behavior price : (temp: Int) -> Hot | Low
                constructs Hot
            let price (temp) = {
                guard temp >= 240 else Low
                Hot { at = temp }
            }
            """;

    private static final String A_COMPREHENSION = """
            module demo

            behavior pick : (temp: Int) -> List<Int>
            let pick (temp) = [ temp | temp >= 240 ]
            """;

    private static final String AN_ATTEMPTED_IF = """
            module demo

            data Warm = Int invariant hot = value >= 240
            data Low

            behavior price : (temp: Int) -> Warm | Low
                constructs Warm
            let price (temp) = if Warm(temp) as w then w else Low
            """;

    private static final String AN_ATTEMPTED_GUARD = """
            module demo

            data Warm = Int invariant hot = value >= 240
            data Low

            behavior price : (temp: Int) -> Warm | Low
                constructs Warm
            let price (temp) = {
                guard Warm(temp) as w else Low
                w
            }
            """;

    /**
     * A comprehension inside a non-recursive helper, which is spliced into the body that calls it.
     *
     * <p>Two guards, so the lowering makes two forks out of one construct; called twice, so an
     * expansion makes two copies of each of those.
     */
    private static final String THROUGH_A_HELPER = """
            module demo

            let picked (n: Int): List<Int> = [ n | n >= 240, n <= 300 ]

            behavior price : (a: Int, b: Int) -> List<Int>
            let price (a, b) = picked(a) ++ picked(b)
            """;

    // --- the matrix ---------------------------------------------------------------------------------

    @Test
    void anIfHasTheTwoArmsItsAuthorWrote() {
        assertEquals(List.of("IF then", "IF else"), namesIn(AN_IF));
    }

    /** The `else` is the author's word; the other way is the rest of the block, which they wrote no
     *  word for at all. */
    @Test
    void aGuardHasAnElseAndTheRestOfTheBlock() {
        assertEquals(List.of("GUARD continued", "GUARD else"), namesIn(A_GUARD));
    }

    /** Neither way through this one has a word in the source, so neither is quoted as one. */
    @Test
    void aComprehensionHasNeitherWord() {
        assertEquals(List.of("COMPREHENSION kept", "COMPREHENSION dropped"),
                namesIn(A_COMPREHENSION));
    }

    /** The shape varies on its own: either conditional may be written as an attempt, and what the
     *  decision was is then the invariant's rather than a condition's. */
    @Test
    void anAttemptCarriesTheConstructItWasWrittenUnder() {
        assertEquals(List.of("IF constructed", "IF departure"), namesIn(AN_ATTEMPTED_IF));
        assertEquals(List.of("GUARD constructed", "GUARD departure"),
                namesIn(AN_ATTEMPTED_GUARD));

        // The short name a report and a document write is the `else` the author put the departure
        // under, which is a word here where the two conditionals have one and the ways through a
        // comprehension have none.
        assertEquals(List.of("constructed", "else"),
                planOf(AN_ATTEMPTED_GUARD).sites().stream()
                        .filter(site -> site instanceof CoverageSites.ArmSite)
                        .map(souther.compiler.report.ArmVocabulary::label).toList());
    }

    /**
     * Copying does not change what was written.
     *
     * <p>Both halves of the answer come along: the helper's comprehension is a comprehension in each
     * body it was spliced into, and each of its two conditions is still one of that comprehension's
     * forks rather than a construct of its own. Held over the copies as a set — what the expansion
     * makes more of is occurrences, and what an author wrote is what this is about.
     */
    @Test
    void anExpansionAndALoweringBothKeepWhatWasWritten() {
        List<String> names = namesIn(THROUGH_A_HELPER);

        assertEquals(8, names.size(), () -> "two guards, lowered and then copied twice: " + names);
        assertTrue(names.stream().allMatch(n -> n.startsWith("COMPREHENSION ")),
                () -> "a copy of a comprehension is a comprehension: " + names);

        // And the two conditions stay apart, which is what `lowered` is for. Four obligations and
        // not two: each condition owes a row through each of its ways.
        assertEquals(4, planOf(THROUGH_A_HELPER).sites().stream()
                        .filter(site -> site instanceof CoverageSites.ArmSite)
                        .map(CoverageSites.Site::obligation).distinct().count(),
                "two forks of the one comprehension, two ways each");
    }

    /** A guard of a comprehension is a fork of that comprehension, and not of something else. */
    @Test
    void aLoweredForkKeepsTheConstructItWasLoweredFrom() {
        SourceConstructOrigin written = SourceConstructOrigin.written(
                new WrittenOwner.Body("m", "b"), 3,
                SourceConstruct.COMPREHENSION);

        assertEquals(SourceConstruct.COMPREHENSION, written.lowered(1).kind());
    }

    /**
     * A binary expression is what the source wrote; being read as a comparison a row can reach is
     * what happened to some of them.
     *
     * <p>{@code a + b} is minted exactly the way {@code a >= b} is — one call of the builder, one
     * ordinal — so naming the construct after the use the analysis puts a few of them to would put a
     * word on every arithmetic node in every body that is untrue of it. The comparisons become sites
     * and the arithmetic does not, and the {@code &&} that puts two comparisons together is neither,
     * so what a construct is and what was made of it have to be two answers.
     */
    @Test
    void everyBinaryTheSourceWroteIsABinaryAndOnlySomeAreSites() {
        String source = """
                module demo

                behavior total : (a: Int, b: Int) -> Int
                let total (a, b) = if a + b >= 10 && a > 0 then a * b else a - b
                """;

        List<SourceConstruct> written = binariesIn(source);
        assertEquals(6, written.size(), () -> "four arithmetic, two comparisons, one `&&`: "
                + written);
        assertTrue(written.stream().allMatch(k -> k == SourceConstruct.BINARY),
                () -> "what the source wrote is a binary expression, whichever operator: " + written);

        // And two of the six are sites: the comparisons. The `&&` is walked into rather than
        // numbered, and the arithmetic answers a number rather than a truth.
        assertEquals(2, planOf(source).sites().stream()
                        .filter(site -> site.outcome() instanceof SourceOutcome.Compared)
                        .count(),
                "a comparison of the condition is a site; a binary expression is not");
    }

    // --- what the pair admits -----------------------------------------------------------------------

    /**
     * Every pair passes through one projection, and it is total over what the language has.
     *
     * <p>Written out because the pair is what carries the meaning: a table here and a switch there
     * is how {@code then} came to be said of a construct that has no {@code then}, and this is what
     * either of them would have to be checked against.
     */
    @Test
    void everyPairTheLanguageHasIsNamed() {
        assertEquals(OutcomeName.THEN, OutcomeName.of(SourceConstruct.IF, held()));
        assertEquals(OutcomeName.ELSE, OutcomeName.of(SourceConstruct.IF, failed()));
        assertEquals(OutcomeName.CONTINUED, OutcomeName.of(SourceConstruct.GUARD, held()));
        assertEquals(OutcomeName.ELSE, OutcomeName.of(SourceConstruct.GUARD, failed()));
        assertEquals(OutcomeName.KEPT, OutcomeName.of(SourceConstruct.COMPREHENSION, held()));
        assertEquals(OutcomeName.DROPPED, OutcomeName.of(SourceConstruct.COMPREHENSION, failed()));
        assertEquals(OutcomeName.CONSTRUCTED, OutcomeName.of(SourceConstruct.IF, built()));
        assertEquals(OutcomeName.DEPARTURE, OutcomeName.of(SourceConstruct.IF, refused()));
        assertEquals(OutcomeName.CONSTRUCTED, OutcomeName.of(SourceConstruct.GUARD, built()));
        assertEquals(OutcomeName.DEPARTURE, OutcomeName.of(SourceConstruct.GUARD, refused()));
        assertEquals(OutcomeName.CASE, OutcomeName.of(SourceConstruct.MATCH,
                new SourceOutcome.Matched(List.of())));
        assertEquals(OutcomeName.COMPARISON, OutcomeName.of(SourceConstruct.BINARY,
                new SourceOutcome.Compared(souther.compiler.types.BinOp.GE)));
    }

    /** A comprehension attempts no construction, and a {@code match} settles no condition. */
    @Test
    void aPairTheLanguageDoesNotHaveIsRefused() {
        assertThrows(IllegalArgumentException.class,
                () -> OutcomeName.of(SourceConstruct.COMPREHENSION, built()));
        assertThrows(IllegalArgumentException.class,
                () -> OutcomeName.of(SourceConstruct.MATCH, held()));
        assertThrows(IllegalArgumentException.class,
                () -> OutcomeName.of(SourceConstruct.BINARY, failed()));
        assertThrows(IllegalArgumentException.class,
                () -> OutcomeName.of(SourceConstruct.NOT_WRITTEN, held()));
    }

    /** And a site cannot be made holding one, which is where the two halves are first put together. */
    @Test
    void aSiteCannotHoldAPairTheLanguageDoesNotHave() {
        SourceConstructOrigin fork = SourceConstructOrigin.written(
                new WrittenOwner.Body("m", "b"), 0,
                SourceConstruct.COMPREHENSION);
        assertThrows(IllegalArgumentException.class, () -> new CoverageSites.ArmSite("b", built(),
                Numberings.armPlace(Numberings.arm(fork, 0), Numberings.arm(1, 0), null), 0,
                new CoverageSites.Obligation("b", fork, 0,
                        souther.compiler.coverage.DecidedBy.THE_DECLARATION)));
    }

    /**
     * What a document lists among the arms is what the measure counts as one.
     *
     * <p>Two predicates over one question — the projection has its own, because the schema's word
     * list is read off it — and this is what holds them to the same answer.
     */
    @Test
    void whatIsAnArmIsTheSameQuestionOnBothSidesOfTheProjection() {
        for (SourceConstruct construct : SourceConstruct.values()) {
            for (SourceOutcome outcome : List.of(held(), failed(), built(), refused(),
                    new SourceOutcome.Matched(List.of()),
                    new SourceOutcome.Compared(souther.compiler.types.BinOp.GE))) {
                OutcomeName name;
                try {
                    name = OutcomeName.of(construct, outcome);
                } catch (IllegalArgumentException _) {
                    continue;   // not a pair the language has, so nothing to agree about
                }
                assertEquals(outcome instanceof SourceOutcome.Arm, name.isArm(),
                        () -> construct + " with " + outcome + " is named " + name);
            }
        }
    }

    /**
     * A tree nothing wrote is refused wherever it is numbered, and not only at a comparison.
     *
     * <p>The invariant-discharge reader rebuilds comparisons so it can read them as such, and that
     * tree is not the tree that runs; the walk said so of a condition's comparisons and left a fork's
     * arms to be turned away by whatever noticed first. Held over a fork because a fork is the half
     * the rule was not written for — the arms are where a row would be owed, and nobody can be owed
     * one for code no source wrote.
     */
    @Test
    void anArmOfSomethingNoSourceWroteIsRefusedWhereItWouldBeNumbered() {
        souther.compiler.diag.SourcePos at = new souther.compiler.diag.SourcePos(1, 1);
        Core answer = new Core.Int(1, Type.INT, at);
        Core fork = new Core.If(new Core.Bool(true, Type.BOOL, at),
                answer, new Core.Int(2, Type.INT, at),
                Core.ForkPlace.asWritten(ConstructOccurrence.unwritten()), Type.INT, at);

        IllegalStateException refused = assertThrows(IllegalStateException.class,
                () -> CoverageSites.of(
                        new ModuleBodies("demo", new java.util.LinkedHashMap<>(Map.of("b", fork))),
                        souther.compiler.coverage.DecisionSources.NONE,
                        souther.compiler.coverage.SuppliedRules.NONE));

        assertTrue(refused.getMessage().contains("no source wrote it"),
                () -> "the walk says what is wrong with the tree: " + refused.getMessage());
    }

    // --- what the origin says a line was drawn in ---------------------------------------------------

    @Test
    void anOriginSaysWhichConstructDrewTheLine() {
        assertEquals(SourceConstruct.IF, drewTheLine(AN_IF));
        assertEquals(SourceConstruct.GUARD, drewTheLine(A_GUARD));
        assertEquals(SourceConstruct.COMPREHENSION, drewTheLine(A_COMPREHENSION));
    }

    // --- helpers ------------------------------------------------------------------------------------

    private static SourceOutcome.Arm held() {
        return new SourceOutcome.Held(new SourceOutcome.HeldBy.Condition());
    }

    private static SourceOutcome.Arm failed() {
        return new SourceOutcome.Failed(new SourceOutcome.FailedBy.Condition());
    }

    private static SourceOutcome.Arm built() {
        return new SourceOutcome.Held(new SourceOutcome.HeldBy.Construction());
    }

    private static SourceOutcome.Arm refused() {
        return new SourceOutcome.Failed(
                new SourceOutcome.FailedBy.Construction(Optional.empty()));
    }

    /** Each arm as the pair that says what it is: what the author wrote, and which way through it. */
    private static List<String> namesIn(String source) {
        return planOf(source).sites().stream()
                .filter(site -> site instanceof CoverageSites.ArmSite)
                .map(site -> site.construct() + " "
                        + site.name().name().toLowerCase(java.util.Locale.ROOT))
                .toList();
    }

    /** Which construct the line on this body's only fork was drawn in. */
    private static SourceConstruct drewTheLine(String source) {
        CoverageSites.Plan plan = planOf(source);
        List<CoverageSites.GuardRef> guards = plan.guards();
        assertEquals(1, guards.size(), () -> "one fork, so one reference: " + guards);
        return guards.get(0).origin().kind();
    }

    /** The construct every binary expression of this body was written as. */
    private static List<SourceConstruct> binariesIn(String source) {
        List<SourceConstruct> out = new java.util.ArrayList<>();
        bodiesOf(source).values().forEach(body -> collectBinaries(body, out));
        return out;
    }

    private static void collectBinaries(Core e, List<SourceConstruct> out) {
        if (e instanceof Core.Binary binary) {
            out.add(binary.origin().kind());
        }
        Core.forEachChild(e, child -> collectBinaries(child, out));
    }

    private static Map<String, Core> bodiesOf(String source) {
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.answerEverything();
        Bodies.Elaborated checked = compilation.db()
                .ask(new Bodies.Checked(compilation.modules().get(0))).value();
        assertNotNull(checked, "the model under test compiles");
        return checked.behaviorBodies();
    }

    private static CoverageSites.Plan planOf(String source) {
        return CoverageSites.of(
                new ModuleBodies("demo", new java.util.LinkedHashMap<>(bodiesOf(source))),
                souther.compiler.coverage.DecisionSources.NONE,
                souther.compiler.coverage.SuppliedRules.NONE);
    }
}
