package souther.compiler.partition;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a body decides on is a position of its input or an answer it was given, and what it does
 * with one is read the same way either way.
 *
 * <p>Two sources and three things to do with one: read it for its truth, compare it, fork on it. A
 * reading that had words for some of the six would draw a table whose columns say what this
 * compiler happens to recognise rather than what the body distinguishes — and a distinction it
 * cannot name is carried as a column named by the reading that met it, which two conditions the
 * body does tell apart would share.
 *
 * <p><b>A row controls both of them, which is what makes either a subject.</b> A position is
 * written at and a dependency is stood in for. A value the model computes is neither, and a
 * condition over one stays a condition this compiler read nothing of.
 */
class ADecisionIsDrawnOnWhatARowControlsTest {

    private static final String TYPES = """
            module example.subjects

            data Customer = { id: Int }
            data Score = Int
            data Accepted
            data Rejected
            data Verdict = Accepted | Rejected
            data Known = { id: Int }
            data Unknown
            data Sighting = Known | Unknown
            """;

    /** A value the body asks the truth of is one column whichever source it came from. */
    @Test
    void aTruthOfAnInputAndOfAnAnswerAreBothColumns() {
        assertEquals(List.of("f"), truths(TYPES + """

                behavior onAnInput : (f: Bool) -> Verdict
                let onAnInput (f) = if f then Accepted else Rejected
                """, "onAnInput"));

        // Under the module that declares it: two modules may declare behaviors of one name, and an
        // identity that left the module off would hold one column for two dependencies.
        assertEquals(List.of("example.subjects.permits(c)"), truths(TYPES + """

                behavior permits : (c: Customer) -> Bool

                behavior throughALet : (c: Customer) -> Verdict
                    depends on permits
                let throughALet (c, permits) = {
                    let allowed = permits(c)
                    if allowed then Accepted else Rejected
                }
                """, "throughALet"));
    }

    /**
     * One value asked about twice is one column.
     *
     * <p>Which is what a column is for. Written as two, a rule saying the value held and a rule
     * saying it did not would be one rule of a table that admits both — an assignment no row can be
     * written at and nothing can show impossible.
     */
    @Test
    void oneValueAskedAboutTwiceIsOneColumn() {
        List<DecisionRule> rules = DecisionReadings.readToTheEnd(TYPES + """

                behavior twice : (f: Bool, g: Bool) -> Verdict
                let twice (f, g) =
                    if f then Accepted
                    else if f then Rejected
                    else if g then Accepted
                    else Rejected
                """, "twice");

        assertEquals(List.of("f", "g"), truthsOf(rules),
                "the two values the body asks about are two columns");
        assertEquals(3, rules.size(),
                "and the way through `f` denied and `f` held is no way: " + rules);
    }

    /** A comparison over an answer is the proposition it states, however it was written. */
    @Test
    void aComparisonOverAnAnswerIsOneProposition() {
        DecisionCondition written = onlyColumn(compares("riskScore(c).value >= 700"));
        assertEquals(written, onlyColumn(compares("700 <= riskScore(c).value")),
                "the same comparison written the other way round is the same column");
        assertEquals(written, onlyColumn(compares("riskScore(c).value + 10 >= 710")),
                "and so is the same proposition with the threshold moved");

        DecisionCondition.AComparison comparison =
                assertInstanceOf(DecisionCondition.AComparison.class, written);
        assertEquals(Set.of(new DecisionAtom.OfAnAnswer(new DecisionSubject.AnAnswer(
                        new InjectedAnswer(
                                new souther.compiler.types.ValueName.Behavior(
                                        "example.subjects", "riskScore"),
                                List.of(new DecisionArgument.OfASubject(
                                        new DecisionSubject.AnInput(
                                                souther.compiler.inputs.TermPath.of("c"))))),
                        List.of()))),
                comparison.form().coefs().keySet(),
                "the quantity is what the dependency answered, with the newtype's value looked"
                        + " through: " + comparison);
    }

    /**
     * And one written with a quantity on each side is one proposition either way round.
     *
     * <p>The case a reading that keeps the authored side cannot answer. With a number on one side,
     * which quantity the author put on the left and which one this reading writes first are the
     * same quantity; with one on each side they are not, and the two spellings come out as
     * quantities that are each other negated. A table holding both admits an assignment where one
     * proposition holds and does not.
     */
    @Test
    void aComparisonOfTwoQuantitiesIsOnePropositionEitherWayRound() {
        assertEquals(onlyColumn(comparesTo("riskScore(c).value >= limit.value")),
                onlyColumn(comparesTo("limit.value <= riskScore(c).value")),
                "greater and less over two quantities are one column");
        assertEquals(onlyColumn(comparesTo("riskScore(c).value == limit.value")),
                onlyColumn(comparesTo("limit.value == riskScore(c).value")),
                "and so is an equality, whose relation is the same either way round");
    }

    /**
     * A dependency asked about a number it was written with is one question however often it is
     * asked.
     *
     * <p>What a row controls is what makes a distinction one an author can write a row against;
     * what an argument is is what tells two askings apart, and a number the model settles tells
     * them apart as well as a position does. Read as the first, a call about a written number had
     * no identity at all and two askings were two columns of a table that tells them apart nowhere.
     */
    @Test
    void anAnswerAboutAWrittenNumberIsOneColumnHoweverOftenItIsAsked() {
        assertEquals(1, columnsOf(TYPES + """

                behavior riskAt : (n: Int) -> Score

                behavior twice : (c: Customer) -> Verdict
                    depends on riskAt
                let twice (c, riskAt) =
                    if riskAt(42).value >= 700 then
                        if riskAt(42).value >= 700 then Accepted else Rejected
                    else Rejected
                """, "twice").size(), "one number asked about twice is one column");

        assertEquals(2, columnsOf(TYPES + """

                behavior riskAt : (n: Int) -> Score

                behavior two : (c: Customer) -> Verdict
                    depends on riskAt
                let two (c, riskAt) =
                    if riskAt(42).value >= 700 then
                        if riskAt(43).value >= 700 then Accepted else Rejected
                    else Rejected
                """, "two").size(), "and two numbers are two");
    }

    /** One dependency asked about two things draws two distinctions, and asked twice about one
     *  draws one. */
    @Test
    void anAnswerIsToldApartByWhatTheDependencyWasAskedAbout() {
        assertEquals(2, columnsOf(TYPES + """

                behavior riskScore : (c: Customer) -> Score

                behavior two : (a: Customer, b: Customer) -> Verdict
                    depends on riskScore
                let two (a, b, riskScore) =
                    if riskScore(a).value >= 700 then
                        if riskScore(b).value >= 700 then Accepted else Rejected
                    else Rejected
                """, "two").size(), "two things asked about are two columns");

        assertEquals(1, columnsOf(TYPES + """

                behavior riskScore : (c: Customer) -> Score

                behavior twice : (a: Customer) -> Verdict
                    depends on riskScore
                let twice (a, riskScore) =
                    if riskScore(a).value >= 700 then
                        if riskScore(a).value >= 700 then Accepted else Rejected
                    else Rejected
                """, "twice").size(), "and one thing asked about twice is one column");
    }

    /** The arms of a fork on an answer are answers about that one subject. */
    @Test
    void theArmsOfAForkOnAnAnswerShareOneSubject() {
        List<DecisionRule> rules = DecisionReadings.readToTheEnd(TYPES + """

                behavior lookUp : (c: Customer) -> Sighting

                behavior byAFork : (c: Customer) -> Verdict
                    depends on lookUp
                let byAFork (c, lookUp) =
                    match lookUp(c) with
                        | Known   -> Accepted
                        | Unknown -> Rejected
                """, "byAFork");

        Set<DecisionCondition> columns = columnsIn(rules);
        assertEquals(1, columns.size(), "one fork is one distinction: " + columns);
        DecisionCondition.ACase column =
                assertInstanceOf(DecisionCondition.ACase.class, columns.iterator().next());
        assertInstanceOf(DecisionSubject.AnAnswer.class, column.of(),
                "and it is asked of what the dependency answered");
        assertEquals(2, rules.size(), "with one rule per arm");
    }

    /**
     * A value the model computes is no subject, and the condition over it stays unread.
     *
     * <p>The negative control this whole reading rests on: what makes an answer a subject is that a
     * row can stand the dependency in, and nothing a row can write settles what an operation of the
     * language answers.
     */
    @Test
    void aTruthOfSomethingNoRowControlsIsNotAColumn() {
        Set<DecisionCondition> columns = columnsIn(DecisionReadings.readToTheEnd(TYPES + """

                behavior named : (c: Customer, code: String) -> Verdict
                let named (c, code) =
                    if String.startsWith("JP", code) then Accepted else Rejected
                """, "named"));

        assertEquals(1, columns.size(), columns.toString());
        assertInstanceOf(DecisionCondition.AConditionNotRead.class, columns.iterator().next(),
                "what an operation of the language answers is nothing a row pins: " + columns);
    }

    /** The columns of {@code behavior}, in the order the rules met them. */
    private static Set<DecisionCondition> columnsOf(String model, String behavior) {
        return columnsIn(DecisionReadings.readToTheEnd(model, behavior));
    }

    private static Set<DecisionCondition> columnsIn(List<DecisionRule> rules) {
        return rules.stream().flatMap(rule -> rule.consulted().keySet().stream())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static DecisionCondition onlyColumn(List<DecisionRule> rules) {
        Set<DecisionCondition> columns = columnsIn(rules);
        assertEquals(1, columns.size(), "one comparison is one column: " + columns);
        return columns.iterator().next();
    }

    /** The same, over a body whose input carries a quantity of its own to compare against. */
    private static List<DecisionRule> comparesTo(String condition) {
        return DecisionReadings.readToTheEnd(TYPES + """

                behavior riskScore : (c: Customer) -> Score

                behavior decide : (c: Customer, limit: Score) -> Verdict
                    depends on riskScore
                let decide (c, limit, riskScore) = if %s then Accepted else Rejected
                """.formatted(condition), "decide");
    }

    /** The rules of a body that decides by {@code condition} over what a dependency answered. */
    private static List<DecisionRule> compares(String condition) {
        return DecisionReadings.readToTheEnd(TYPES + """

                behavior riskScore : (c: Customer) -> Score

                behavior decide : (c: Customer) -> Verdict
                    depends on riskScore
                let decide (c, riskScore) = if %s then Accepted else Rejected
                """.formatted(condition), "decide");
    }

    /** What the truths of {@code behavior} are read of, in the order the reading met them. */
    private static List<String> truths(String model, String behavior) {
        return truthsOf(DecisionReadings.readToTheEnd(model, behavior));
    }

    private static List<String> truthsOf(List<DecisionRule> rules) {
        return columnsIn(rules).stream()
                .filter(DecisionCondition.ATruth.class::isInstance)
                .map(column -> ((DecisionCondition.ATruth) column).of().toString()).toList();
    }

    /** Every rule of a body that decides on nothing is one rule, which is what makes the counts
     *  above readable. */
    @Test
    void aBodyThatDecidesNothingStatesOneRule() {
        assertTrue(columnsOf(TYPES + """

                behavior flat : (c: Customer) -> Verdict
                let flat (c) = Accepted
                """, "flat").isEmpty(), "nothing is consulted");
    }
}
