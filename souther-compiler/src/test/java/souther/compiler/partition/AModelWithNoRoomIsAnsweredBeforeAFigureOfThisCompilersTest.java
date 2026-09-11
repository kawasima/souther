package souther.compiler.partition;

import org.junit.jupiter.api.Test;

import souther.compiler.check.DeclaredBounds;
import souther.compiler.check.RuleReadingContext;
import souther.compiler.check.FieldDomains;
import souther.compiler.check.RuleKey;
import souther.compiler.check.RuleReadingSource;
import souther.compiler.check.RuleReadings;
import souther.compiler.numeric.Count;
import souther.compiler.query.Adequacy;
import souther.compiler.query.Compilation;
import souther.compiler.types.Type;
import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbols;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A collection the rules leave no room in is answered as that, and not as a figure this compiler
 * reached while finding out.
 *
 * <p>The two can be true of one point at once, and it is the ordinary case rather than a contrived
 * one: where the model has no row, the search refuses everything it composes and goes on composing
 * until it meets a bound. So which of the two a reader is told turns on nothing about the model,
 * and everything about which was asked first.
 *
 * <p><b>What settles it is that only one of them is actionable.</b> A proof that no collection
 * holds what is being placed in it stands however far this compiler read; an author sent to raise
 * the figure beside it would raise it and be told the same thing. So the proof is made where the
 * plan is, before any search runs, and the search that would have named a figure never happens.
 *
 * <p>The pair below is the same rule read against two states of the row. Where the cap's own field
 * is settled at nought, the rules leave no room and the plan refuses; where it is settled at one,
 * they leave room for exactly what is placed, and a row is written. The proof is not a property of
 * the rule but of the rule and what the row has fixed.
 */
class AModelWithNoRoomIsAnsweredBeforeAFigureOfThisCompilersTest {

    /**
     * A list capped at a field beside it, asked for with a value inside it.
     *
     * <p>The line asks both at once, so a combination fixes {@code cap} and asks for a class at an
     * element of {@code xs}. Where it fixes {@code cap} at nought the two cannot both hold: the
     * rules cap the list at none, and what is asked for is a list holding one.
     *
     * <p>The fields beside them are what make the search wide enough to meet its bound. They relate
     * to nothing here and are refused for nothing of their own, which is the point — the figure a
     * reader would be sent to raise has nothing to do with why no row exists.
     */
    private static final String CAPPED = """
            module example.placing

            data Awkward = Int
                invariant lo = value >= 0
                invariant hi = value <= 10
                invariant no3 = value /= 3
                invariant no4 = value /= 4
                invariant no7 = value /= 7

            data Box =
                { cap: Int
                , xs: List<Awkward>
                , a: Awkward
                , b: Awkward
                , c: Awkward
                , d: Awkward
                , e: Awkward
                , f: Awkward
                , g: Awkward
                , h: Awkward
                , i: Awkward
                , j: Awkward
                , k: Awkward
                , l: Awkward
                }
                invariant floor = cap >= 0
                invariant capped = List.length(xs) <= cap

            data Yes
            data No
            data Verdict = Yes | No

            behavior placing : (box: Box) -> Verdict
            let placing (box) =
                if box.cap >= 1 && List.length(List.filter(x -> x.value >= 5, box.xs)) >= 1
                then Yes
                else No
            """;

    /**
     * The combination the rules leave no room for is answered by the model, and by nothing else.
     *
     * <p>Both halves. That the word is the model's is what sends an author somewhere they can act;
     * that no figure of this compiler's is named is what keeps them from raising one that changes
     * nothing. Before the refusal was made where the plan is, this combination came back saying the
     * search had left something untried — which was true and was not what a reader needed.
     */
    @Test
    void theCombinationWithNoRoomIsAnsweredByTheModel() {
        List<Generator.UnresolvedCombination> made = unresolved();

        assertFalse(made.isEmpty(), "the combination is one no row was written for");
        for (Generator.UnresolvedCombination each : made) {
            assertEquals(Generator.UnresolvedCombination.Reason.NOTHING_COMPOSES_ONE,
                    each.reason(),
                    () -> "the model settles it, whatever the search then did: " + each);
        }
    }

    /** And every one of them says which collection, how many it needs, and how many it may hold. */
    @Test
    void theAnswerSaysWhatTheRulesLeaveRoomFor() {
        for (Generator.UnresolvedCombination each : unresolved()) {
            String said = each.detail();

            assertNotNull(said, () -> "the answer says what it is about: " + each);
            assertTrue(said.contains("box.xs") && said.contains("hold 1")
                            && said.contains("room for 0"),
                    () -> "and says the collection, what it would have to hold, and what the rules"
                            + " leave room for: " + said);
        }
    }

    /**
     * What the rules leave the list, at each of the three states the row can put its cap in.
     *
     * <p><b>The control the refusal above rests on, held where the count is nameable.</b> What is
     * refused turns on the cap the row has fixed, and the account has no way to say which of its
     * searches was asked with which cap — so a reading of it either picks its subjects out of how a
     * class is spelled or says something weaker than it means. Both were tried here and both were
     * wrong about which searches they were about.
     *
     * <p>So it is asked of the reading itself. Nothing settled leaves the list capped in no way,
     * which is what keeps a rule naming an unchosen field from proving anything; a cap of nought
     * leaves no room and a cap of one leaves room for what is placed. The refusal reads that
     * middle number and nothing else, so these three are the whole of what it can come to.
     */
    @Test
    void whatTheRulesLeaveTheListFollowsTheCapTheRowFixed() {
        assertEquals(Integer.MAX_VALUE, roomInTheList(Map.of()).most(),
                "nothing has chosen the cap, so the rules cap the list in no way");
        assertEquals(0, roomInTheList(Map.of(RuleKey.of("cap"), Count.of(0))).most(),
                "a cap of nought leaves no room for a value to be placed in it");
        assertEquals(1, roomInTheList(Map.of(RuleKey.of("cap"), Count.of(1))).most(),
                "and a cap of one leaves room for the one being placed");
    }

    /** How many the rules let {@code xs} hold, read with {@code settled} fixed as the row fixes it. */
    private static DeclaredBounds.CountRange roomInTheList(Map<RuleKey, Count> settled) {
        RuleReadingSource rules = RuleReadings.of(measured(), "example.placing");
        assertNotNull(rules, "the model under test compiles");
        FieldDomains read = FieldDomains.of(
                TypeSymbols.declared(new TypeKey("example.placing", "Box")), rules,
                souther.compiler.query.ReadAs.THE_COMPILATION_DOES, settled);
        return Partitions.heldRange(
                new Type.ListOf(new Type.Ref(
                        TypeSymbols.declared(new TypeKey("example.placing", "Awkward")))),
                RuleReadingContext.unshared(rules,
                        souther.compiler.query.ReadAs.THE_COMPILATION_DOES),
                read.heldAt(RuleKey.of("xs")));
    }

    /**
     * And the same rule leaves the rows it has room for.
     *
     * <p>The other control. What is refused above turns on the cap the row fixed and not on the
     * rule alone, so a cap of one leaves a list holding one.
     */
    @Test
    void theSameRuleLeavesTheRowsItHasRoomFor() {
        assertTrue(rowsOf("placing").stream()
                        .anyMatch(each -> each.contains("cap = 1") && !each.contains("xs = []")),
                "a row holding one under a cap of one is written: " + rowsOf("placing"));
    }

    /** The combinations no row was written for, as the filling records them. */
    private static List<Generator.UnresolvedCombination> unresolved() {
        Adequacy.Filling filling = measured().db()
                .ask(new Adequacy.Generated("example.placing", "placing")).value();
        assertNotNull(filling, "rows are asked for");
        return filling.composed().unresolved();
    }

    /** The rows the behavior was offered, as they are written out. */
    private static List<String> rowsOf(String behavior) {
        Adequacy.Filling filling = measured().db()
                .ask(new Adequacy.Generated("example.placing", behavior)).value();
        assertNotNull(filling, "rows are asked for");
        return filling.composed().rows().stream()
                .map(row -> row.inputs().get(0).text()).toList();
    }

    /**
     * The one model here, measured once.
     *
     * <p>Every answer below is about the same model, so measuring it per question is the same work
     * done over: the source is compiled, the report asked for and every question answered each
     * time. Held because what the assertions read back is what one measurement came to, which is
     * also what an author gets — two of them agreeing is not something this is about.
     */
    private static Compilation measured;

    private static synchronized Compilation measured() {
        if (measured != null) {
            return measured;
        }
        Compilation compilation = Compilation.ofSource(CAPPED, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        assertEquals(List.of(), compilation.diagnostics().values().stream()
                        .flatMap(List::stream)
                        .map(each -> each.diagnostic().code()).toList(),
                "the model under test compiles");
        measured = compilation;
        return measured;
    }
}
