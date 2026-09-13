package souther.compiler.partition;

import org.junit.jupiter.api.Test;

import souther.compiler.check.RuleCitation;
import souther.compiler.query.Adequacy;
import souther.compiler.query.Compilation;
import souther.compiler.query.PartitionEvidence;
import souther.compiler.query.Sites;

import java.util.ArrayList;
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

    private static final String AN_ARM_BINDS_WHAT_IT_MATCHED = """
            module example.codes

            data Answer = Yes | No
            data Plain = { code: String }
            data Special = { code: String }
            data Item = Plain | Special

            behavior f : (item: Item) -> Answer
            let f (item) =
                if String.startsWith("JP", match item with
                        | Plain as p -> p.code
                        | Special as s -> s.code) then Yes else No
            """;

    private static final String ONE_ARM_COMES_TO_NO_VALUE = """
            module example.codes

            data Answer = Yes | No

            behavior f : (flag: Bool, a: String) -> Answer
            let f (flag, a) =
                if (if flag then String.length(String.uppercase(a))
                    else unreachable "the flag is set wherever this is read") > 10
                    then Yes else No
            """;

    private static final String AN_ARM_BUILDS_A_VALUE_OUT_OF_AN_UNREACHABLE = """
            module example.codes

            data Answer = Yes | No
            data Count = Int
            data Boxed = { n: Count }

            behavior f : (flag: Bool, a: String) -> Answer
                constructs Count, Boxed
            let f (flag, a) =
                if (if flag then String.length(String.uppercase(a))
                    else Boxed { n = Count(unreachable "no number to give") }.n.value) > 10
                    then Yes else No
            """;

    private static final String READ_OUT_OF_A_CONSTRUCTION = """
            module example.codes

            data Answer = Yes | No
            data Code = String

            behavior f : (a: String, b: String) -> Answer
                constructs Code
            let f (a, b) = {
                let code = Code(a)
                if String.startsWith("JP", String.append(code.value, b)) then Yes else No
            }
            """;

    private static final String A_CONSTRUCTION_AND_NOTHING_ELSE = """
            module example.codes

            data Answer = Yes | No
            data Code = String

            behavior f : (a: String, b: String) -> Answer
                constructs Code
            let f (a, b) = {
                let code = Code(a)
                if String.startsWith("JP", code.value) then Yes else No
            }
            """;

    private static final String ONE_FIELD_OF_TWO = """
            module example.codes

            data Answer = Yes | No
            data Pair = { left: String, right: String }

            behavior f : (a: String, b: String) -> Answer
                constructs Pair
            let f (a, b) = {
                let pair = Pair { left = a, right = b }
                if String.startsWith("JP", pair.left) then Yes else No
            }
            """;

    private static final String A_CONSTRUCTION_GIVEN_WHAT_AN_OPERATION_MADE = """
            module example.codes

            data Answer = Yes | No
            data Code = String

            behavior f : (a: String, b: String) -> Answer
                constructs Code
            let f (a, b) = {
                let code = Code(String.uppercase(a))
                if String.startsWith("JP", String.append(code.value, b)) then Yes else No
            }
            """;

    private static final String A_PATH_THROUGH_THE_INPUT = """
            module example.codes

            data Answer = Yes | No
            data Code = String
            data Request = { code: Code, tag: String }

            behavior f : (request: Request) -> Answer
            let f (request) =
                if String.startsWith("JP", request.code.value) then Yes else No
            """;

    private static final String TWO_CONSTRUCTIONS_COMPARED = """
            module example.codes

            data Answer = Yes | No
            data Pair = { left: String, right: String }

            behavior f : (a: String, b: String) -> Answer
                constructs Pair
            let f (a, b) =
                if Pair { left = a, right = b } == Pair { left = "x", right = "y" }
                    then Yes else No
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

    /**
     * Every position this model's report names its rule at, whichever way it names it.
     *
     * <p>Read off whether an entry names a rule at all and off the axes a rule drew, and never off
     * the word an entry came with. Where an author is owed a sentence is one question and which
     * sentence they are given is another: a test selecting by the word states as its contract
     * whatever this compiler calls the finding today, so the day the word moves the claim about
     * where the rule is named moves with it and neither claim is being checked any more.
     *
     * <p>A line the rule drew names the position as much as a question standing there does. Which
     * of the two a model gets is asked once, of the one test whose subject that is.
     */
    private static List<String> named(PartitionEvidence measured) {
        List<String> out = new ArrayList<>();
        for (PartitionEvidence.NotRead each : measured.notRead()) {
            if (!each.cited().isEmpty() && !out.contains(each.at())) {
                out.add(each.at());
            }
        }
        for (PartitionEvidence.AxisCoverage each : measured.axes()) {
            if (!out.contains(each.path())) {
                out.add(each.path());
            }
        }
        return out;
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
     * And an arm is read where the arm stands, so what it binds is a name with a position.
     *
     * <p>What a {@code match} arm binds is the value that was matched read as the case the arm
     * selects, and that name exists inside the arm and nowhere else. Read where the fork stands,
     * the arm's own answer is a name standing for nothing and the rule is shown nowhere — which is
     * the same silence a model with no rule in it gives.
     */
    @Test
    void whatAnArmBindsIsNamedWhereTheArmNarrowedIt() {
        assertEquals(List.of("item@Plain.code", "item@Special.code"),
                derived(measured(AN_ARM_BINDS_WHAT_IT_MATCHED)),
                () -> "said under each case the arms select: "
                        + measured(AN_ARM_BINDS_WHAT_IT_MATCHED).notRead());
    }


    /**
     * And an arm that comes to no value is not one of the values the rule is about.
     *
     * <p>A departure is not another value the subject may be. Counted among them, the arm with
     * nothing on it answers for the subject beside the arm that has a value, and the one value this
     * subject may be — a length an operation took of the strings at a position — comes back as a
     * form nothing read, which sends an author after a syntax that is not the difficulty.
     *
     * <p>A comparison, so both places it names carry the word: which positions it depends on
     * includes what the choice turned on, and one comparison has one arithmetic. Which of them the
     * value came from is the other question, and it is the one the tests above ask.
     */
    @Test
    void anArmThatComesToNoValueIsNotOneOfTheValuesTheRuleIsAbout() {
        assertEquals(List.of("flag", "a"), derived(measured(ONE_ARM_COMES_TO_NO_VALUE)),
                () -> "the one value the subject may be was made from the strings here: "
                        + measured(ONE_ARM_COMES_TO_NO_VALUE).notRead());
    }

    /**
     * And the arm need not be an {@code unreachable} to be one: it is enough that it has to
     * evaluate one.
     *
     * <p>The case a rule written over the shape of the node gets wrong. There is no fork in this
     * arm and its outermost node is a field taken of a construction, so an arm counted by what it
     * is made of is counted here — and the value the expression may be goes back to being one of
     * two, of which one was made by no operation.
     */
    @Test
    void anArmThatHasToEvaluateAnUnreachableIsNotOneOfTheValuesEither() {
        assertEquals(List.of("flag", "a"),
                derived(measured(AN_ARM_BUILDS_A_VALUE_OUT_OF_AN_UNREACHABLE)),
                () -> "the one value the subject may be was made from the strings here: "
                        + measured(AN_ARM_BUILDS_A_VALUE_OUT_OF_AN_UNREACHABLE).notRead());
    }

    /**
     * And a value read back out of a construction is named where the construction was given it.
     *
     * <p>What the construction was handed is the value the field comes back as, so the rule is about
     * the strings standing at {@code a} as much as about the ones at {@code b}. Read as a form
     * nothing takes apart, the construction answered for neither and the rule was named at {@code b}
     * alone — which tells an author the model states nothing at the position the value came from.
     *
     * <p>Where and not in which words: what these four ask is the sentence an author is owed, and
     * the word it is given in is one test's subject and not theirs.
     */
    @Test
    void aValueReadOutOfAConstructionIsNamedWhereTheConstructionWasGivenIt() {
        assertEquals(List.of("a", "b"), named(measured(READ_OUT_OF_A_CONSTRUCTION)),
                () -> "said where the construction was given each string: "
                        + measured(READ_OUT_OF_A_CONSTRUCTION).notRead());
    }

    /**
     * And a rule about nothing but the constructed value is named there too.
     *
     * <p>The one the construction answers for alone. Nothing else in the body reaches a position, so
     * a reading that cannot say where a constructed value came from places the rule nowhere at all —
     * the answer a body with no rule in it gives.
     */
    @Test
    void aRuleAboutNothingButAConstructedValueIsNamedThere() {
        assertEquals(List.of("a"), named(measured(A_CONSTRUCTION_AND_NOTHING_ELSE)),
                () -> "said where the construction was given it: "
                        + measured(A_CONSTRUCTION_AND_NOTHING_ELSE).notRead());
    }

    /**
     * And at the field the reader asked for, and not at the others the construction was given.
     *
     * <p>What tells the two readings apart. A construction read for everything it was given names
     * every position a field of it came from, whichever field was taken back out — and the rule here
     * is about the strings at {@code left} and holds none of the ones at {@code right}. A single
     * field cannot tell the two apart, since every field of such a construction is the one asked
     * for.
     */
    @Test
    void oneFieldOfAConstructionIsNamedWithoutTheOthers() {
        assertEquals(List.of("a"), named(measured(ONE_FIELD_OF_TWO)),
                () -> "b was given to the field the rule does not read: "
                        + measured(ONE_FIELD_OF_TWO).notRead());
    }

    /**
     * And a field given what an operation made is named where that operation's arguments stand.
     *
     * <p>The field comes back as what it was given, whatever that is. So a construction is not a
     * step that turns what is under it into a position: what was handed in was made from {@code a}
     * by an operation, and that is what the rule is about.
     */
    @Test
    void aFieldGivenWhatAnOperationMadeIsNamedWhereItsArgumentsStand() {
        assertEquals(List.of("a", "b"),
                named(measured(A_CONSTRUCTION_GIVEN_WHAT_AN_OPERATION_MADE)),
                () -> "said at what the operation was applied to: "
                        + measured(A_CONSTRUCTION_GIVEN_WHAT_AN_OPERATION_MADE).notRead());
    }

    /**
     * And a construction standing as a side of a comparison is a form nothing read, not a value an
     * operation made.
     *
     * <p>The other question about the same arm. Where the value came from is answered for a
     * construction — both positions are named — and what an author is told is still not the word that
     * promises an operation to be followed back: there is none, and a reader sent looking for one is
     * sent after something nobody wrote. Two data compare by their fields, which is what puts a
     * construction here; an invariant's clause cannot, since a clause observes and does not build.
     */
    @Test
    void aConstructionComparedIsAFormNothingReadRatherThanAValueAnOperationMade() {
        PartitionEvidence measured = measured(TWO_CONSTRUCTIONS_COMPARED);

        assertEquals(List.of("a", "b"), named(measured),
                () -> "the constructions were given what stands at each: " + measured.notRead());
        assertEquals(List.of(), derived(measured),
                () -> "and no operation made either of them: " + measured.notRead());
        assertEquals(List.of(UndividedPosition.Reason.UNSUPPORTED_SYNTAX,
                        UndividedPosition.Reason.UNSUPPORTED_SYNTAX),
                measured.notRead().stream().map(PartitionEvidence.NotRead::reason).toList(),
                () -> "what stopped the reading is the form: " + measured.notRead());
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
     * A predicate read over a field of a construction is measured at the position the construction
     * was given it.
     *
     * <p>Which is what naming it there was owed all along: the strings the rule is about are the ones
     * standing at the position, the construction having been handed them, so the rule draws its line
     * there like a rule written over the position itself. What a line falls on is a question about the
     * values and this is the one place it is asked of these models — the tests above ask where the
     * rule is named and are answered by a line as readily as by a question standing there.
     *
     * <p>Nothing here is derived. A value read back out of a construction is the value that went in,
     * and a word promising an operation to be followed back would send an author looking for one
     * nobody wrote. An operation standing over such a projection is another matter and keeps the word
     * it had: what {@code String.append} answered is not the strings at either position, and the
     * tests above are where that rule is named.
     */
    @Test
    void aPredicateOverAProjectedConstructionIsMeasuredAtItsSourcePosition() {
        assertEquals(List.of("a"), measured(A_CONSTRUCTION_AND_NOTHING_ELSE).axes().stream()
                        .map(PartitionEvidence.AxisCoverage::path).toList(),
                () -> "a line falls where the construction was given the string: "
                        + measured(A_CONSTRUCTION_AND_NOTHING_ELSE).notRead());
        assertEquals(List.of("a"), measured(ONE_FIELD_OF_TWO).axes().stream()
                        .map(PartitionEvidence.AxisCoverage::path).toList(),
                "and at the position the field the rule reads was given, and no other");
        for (String model : List.of(A_CONSTRUCTION_AND_NOTHING_ELSE, ONE_FIELD_OF_TWO)) {
            assertEquals(List.of(), derived(measured(model)),
                    () -> "and nothing about it is a value an operation made: "
                            + measured(model).notRead());
        }
    }

    /**
     * And a newtype's value inside the input is still the position it stands under.
     *
     * <p>Not this rule. There is no construction here to eliminate: the value is what the input holds
     * at a position, and whether a newtype's own value is a step of a path is what the declarations
     * say ({@code Location.isStep}). Read as an elimination, a field of the input would be a rule
     * about a position nothing wrote.
     */
    @Test
    void aNewtypesValueInsideTheInputIsThePositionItStandsUnder() {
        assertEquals(List.of("request.code"), measured(A_PATH_THROUGH_THE_INPUT).axes().stream()
                        .map(PartitionEvidence.AxisCoverage::path).toList(),
                () -> "the position the newtype stands at: "
                        + measured(A_PATH_THROUGH_THE_INPUT).notRead());
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
