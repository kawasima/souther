package souther.compiler.partition;

import org.junit.jupiter.api.Test;

import souther.compiler.check.RuleCitation;
import souther.compiler.query.Adequacy;
import souther.compiler.query.Compilation;
import souther.compiler.query.PartitionEvidence;
import souther.compiler.query.Sites;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A rule an author wrote about the strings of a value an operation made is named at every position
 * that value came from.
 *
 * <p>Not measured there. What {@code String.uppercase} answers is made from what stands at a
 * position and is not those strings, so a line drawn there would be at values the rule is not about.
 * What it must not be is silent: the author wrote a rule, and a reading that placed it nowhere
 * reported a model that states nothing at the position — which is the answer a body with no rule in
 * it gives.
 *
 * <p><b>At every position and not the first.</b> A value joined out of two positions is made out of
 * both, and an author who wrote a rule about the joined string is owed the sentence at each of them.
 * Filed at one, the other comes back as a position the model says nothing about.
 *
 * <p>What the value is made of is {@link souther.compiler.check.ValueOrigin}'s answer, which is the
 * same answer the reading of a comparison beside this one is filed from. Asked of where a subject's
 * value came from instead, a rule about anything an operation stands over is a rule about nothing:
 * that question is answered for a leaf, and an application is not one.
 */
class ARuleAboutTheStringsOfAMadeValueIsNamedWhereItCameFromTest {

    private static final String MODULE = "example.codes";

    private static final String ONE_POSITION = """
            module example.codes

            data Answer = Yes | No

            behavior f : (code: String) -> Answer
            let f (code) =
                if String.startsWith("JP", String.uppercase(code)) then Yes else No
            """;

    private static final String TWO_POSITIONS = """
            module example.codes

            data Answer = Yes | No

            behavior f : (a: String, b: String) -> Answer
            let f (a, b) =
                if String.startsWith("JP", String.append(a, b)) then Yes else No
            """;

    private static final String NO_POSITION = """
            module example.codes

            data Answer = Yes | No

            behavior f : (code: String) -> Answer
            let f (code) =
                if String.startsWith("JP", "written") then Yes else No
            """;

    private static final String A_VALUE_CHOSEN_BETWEEN = """
            module example.codes

            data Answer = Yes | No

            behavior f : (flag: Bool, a: String, b: String) -> Answer
            let f (flag, a, b) =
                if String.startsWith("JP", if flag then a else b) then Yes else No
            """;

    private static final String A_CHOICE_DECIDED_BY_A_STRING = """
            module example.codes

            data Answer = Yes | No

            behavior f : (sel: String, a: String, b: String) -> Answer
            let f (sel, a, b) =
                if String.startsWith("JP",
                        if String.startsWith("X", sel) then a else b) then Yes else No
            """;

    private static final String A_VALUE_CHOSEN_BY_A_MATCH = """
            module example.codes

            data Answer = Yes | No
            data Pick = First | Second

            behavior f : (pick: Pick, a: String, b: String) -> Answer
            let f (pick, a, b) =
                if String.startsWith("JP", match pick with
                        | First -> a
                        | Second -> b) then Yes else No
            """;

    private static final String AN_ELEMENT_IT_CAME_FROM = """
            module example.codes

            data Person = { code: String }
            data Count = Int

            behavior f : (people: List<Person>) -> Count
                constructs Count
            let f (people) =
                Count(List.length(
                    List.filter(s -> String.startsWith("JP", s),
                        List.map(q -> q.code, people))))
            """;

    private static PartitionEvidence measured(String model) {
        Compilation compilation = Compilation.ofSource(model, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        PartitionEvidence f = compilation.db()
                .ask(new Adequacy.Coverage(MODULE)).value().get("f");
        assertNotNull(f, "the model under test compiles");
        return f;
    }

    /** Where a rule that came to nothing was said, once per position. */
    private static List<String> derived(PartitionEvidence measured) {
        return measured.notRead().stream()
                .filter(each -> each.reason()
                        == UndividedPosition.Reason.RULE_ABOUT_A_DERIVED_VALUE)
                .map(PartitionEvidence.NotRead::at).toList();
    }

    /** The rule is named, at the position the strings it is about were made from. */
    @Test
    void aValueMadeFromOnePositionIsNamedThere() {
        assertEquals(List.of("code"), derived(measured(ONE_POSITION)),
                () -> "said where the strings came from: " + measured(ONE_POSITION).notRead());
    }

    /** And at every position it was made from, since the rule is about what all of them came to. */
    @Test
    void aValueMadeFromTwoPositionsIsNamedAtBoth() {
        assertEquals(List.of("a", "b"), derived(measured(TWO_POSITIONS)),
                () -> "said at each: " + measured(TWO_POSITIONS).notRead());
    }

    /** A rule about a string written where it stands is about no position, and is said nowhere. */
    @Test
    void aValueWrittenWhereItStandsIsNamedNowhere() {
        assertEquals(List.of(), derived(measured(NO_POSITION)));
    }

    /**
     * And a rule about a value chosen between two is named where either value came from, and not
     * where the choice was decided.
     *
     * <p>The value is what {@code a} is or what {@code b} is, so each of them is a position an
     * author who wrote a rule about it has something to be told about. What decided which of them
     * it is holds none of the strings the rule is about, and a reader sent to {@code flag} is sent
     * to a position the rule says nothing of.
     */
    @Test
    void aValueChosenBetweenTwoIsNamedWhereEachAlternativeCameFrom() {
        assertEquals(List.of("a", "b"), derived(measured(A_VALUE_CHOSEN_BETWEEN)),
                () -> "said at each value it may be, and not at what decided which: "
                        + measured(A_VALUE_CHOSEN_BETWEEN).notRead());
    }

    /**
     * And what decided it is left out for being what decided it, not for being of another kind.
     *
     * <p>The one above turns on a {@code Bool}, which is no position a rule about strings could be
     * filed at anyway. Here the choice turns on a string of its own, read by a rule of the same
     * shape as the one under test — so the only thing telling {@code sel} from {@code a} and
     * {@code b} is which side of the choice it stands on.
     */
    @Test
    void whatDecidedTheChoiceIsNotNamedEvenWhereItCouldBe() {
        assertEquals(List.of("a", "b"), derived(measured(A_CHOICE_DECIDED_BY_A_STRING)),
                () -> "sel decided which value the subject is and holds none of its strings: "
                        + measured(A_CHOICE_DECIDED_BY_A_STRING).notRead());
    }

    /** And a value chosen by a match is named at every arm, the scrutinee being what decided it. */
    @Test
    void aValueChosenByAMatchIsNamedAtEveryArm() {
        assertEquals(List.of("a", "b"), derived(measured(A_VALUE_CHOSEN_BY_A_MATCH)),
                () -> "said at each arm: " + measured(A_VALUE_CHOSEN_BY_A_MATCH).notRead());
    }

    /**
     * And a value handed out by an operation is named at the position it was taken from.
     *
     * <p>Beside the three above and reached the other way: nothing was applied to what the rule is
     * about, and what says where it came from is the edge an expansion wrote. Left out, the day
     * filing is read off the positions a walk <em>named</em> — which is not what any of these are —
     * this case is the one that goes quiet.
     */
    @Test
    void anElementAnOperationHandedOutIsNamedWhereItCameFrom() {
        assertEquals(List.of("people[*]"), derived(measured(AN_ELEMENT_IT_CAME_FROM)),
                () -> "said where the elements came from: "
                        + measured(AN_ELEMENT_IT_CAME_FROM).notRead());
    }

    /**
     * And each of them names the rule, as the question standing there.
     *
     * <p>These are not positions nothing was written at, and they are not rules read to the end
     * either: what the rule states of the values here is what nothing worked out, so the measure at
     * the position rests on the question rather than closing over it.
     */
    @Test
    void everyOneOfThemNamesTheRuleAsAQuestionStandingThere() {
        for (String model : List.of(ONE_POSITION, TWO_POSITIONS, AN_ELEMENT_IT_CAME_FROM,
                A_VALUE_CHOSEN_BETWEEN, A_VALUE_CHOSEN_BY_A_MATCH)) {
            PartitionEvidence measured = measured(model);
            assertTrue(measured.notRead().stream()
                            .filter(each -> each.reason()
                                    == UndividedPosition.Reason.RULE_ABOUT_A_DERIVED_VALUE)
                            .allMatch(each -> each instanceof PartitionEvidence.NotRead
                                    .AnUnclassifiedRule),
                    () -> "a rule was read, so the finding has one to name: " + measured.notRead());
        }
    }

    /**
     * And the document writes a handle for it, which is a place this compilation worked out.
     *
     * <p>The other half of what such a finding is owed. A reader told a rule was written and not
     * where it is has been sent nowhere, and the handle a document writes is what this compilation
     * already worked out about where the rule stands.
     */
    @Test
    void theDocumentWritesAHandleForTheRule() {
        Compilation compilation = Compilation.ofSource(ONE_POSITION, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        List<RuleCitation.Written> cited = compilation.db()
                .ask(new Adequacy.Coverage(MODULE)).value().get("f").notRead().stream()
                .flatMap(each -> each.cited().stream())
                .filter(RuleCitation.Written.class::isInstance)
                .map(RuleCitation.Written.class::cast).toList();
        assertEquals(1, cited.size(), () -> "one rule to place: " + cited);

        assertNotNull(Sites.placeOf(compilation.db(), cited.getFirst()),
                "where the rule the report names is written");
    }

    /**
     * No line is drawn at any of them, since the strings the rule is about are not the ones there.
     *
     * <p>The models with a choice in them are not here: a fork's own condition is a rule about the
     * values at the position it turns on, and the line drawn there is that rule's and not this
     * one's.
     */
    @Test
    void noLineIsDrawnAtAnyOfThem() {
        for (String model : List.of(ONE_POSITION, TWO_POSITIONS, AN_ELEMENT_IT_CAME_FROM)) {
            assertEquals(List.of(), measured(model).axes().stream()
                    .map(PartitionEvidence.AxisCoverage::path).toList());
        }
    }
}
