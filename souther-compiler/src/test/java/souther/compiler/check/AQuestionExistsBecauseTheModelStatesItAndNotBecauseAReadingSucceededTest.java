package souther.compiler.check;

import org.junit.jupiter.api.Test;

import souther.compiler.inputs.BlockReason;
import souther.compiler.query.Compilation;
import souther.compiler.query.Scopes;
import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbol;
import souther.compiler.types.TypeSymbols;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Two clauses stating one line, and both of them raise the question about it.
 *
 * <p>{@code value <= 20} and {@code value <= 10 * 2} say the same thing about which values may
 * stand at the position and about where they stop. What the model states is the same in both: an
 * ordering comparison of the position against an expression naming no position. What differs is
 * whether this compiler can fold the other side to a number, and that is a fact about this
 * compiler.
 *
 * <p>It used to decide whether the model had asked. {@code BOUNDARY} was raised out of a
 * {@code ClauseStates} arm built where {@code InvariantBound.at} came back with an end, so a clause
 * the end reading could not take in left no question standing and the accounting for it came back
 * complete. A question nothing raises is one every model answers by nobody having asked, which is
 * what {@link CoverageObligation} says of itself it is not for.
 *
 * <p>So the question is raised off the shape and the reading answers it. The pair below is the whole
 * of the difference: one line, two clauses, one question each, and one of them left standing.
 */
class AQuestionExistsBecauseTheModelStatesItAndNotBecauseAReadingSucceededTest {

    /** The clause alone, so that nothing written beside it decides the answer. */
    private static FieldDomains read(String clause) {
        String source = """
                module example.rooms

                data Length = Int
                    %s
                """.formatted(clause);
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.answerEverything();
        String module = compilation.modules().get(0);
        Symbols symbols = Scopes.derived(compilation.db(), module).value();
        assertNotNull(symbols);
        TypeSymbol.AtModule named = TypeSymbols.declared(new TypeKey(module, "Length"));
        assertNotNull(symbols.declaredNode(named.key()), "no `Length` declared");
        return FieldDomains.of(named, RuleReadings.of(compilation, module),
                souther.compiler.query.ReadAs.THE_COMPILATION_DOES);
    }

    /** What the one clause of the one declaration raises. */
    private static Set<CoverageObligation> raisedBy(String clause) {
        FieldDomains domains = read(clause);
        assertEquals(1, domains.required().size(),
                () -> "one clause, one rule: " + domains.required().keySet());
        Set<CoverageObligation> out = new LinkedHashSet<>();
        domains.required().values().iterator().next().obligations()
                .forEach(owed -> out.add(owed.obligation()));
        return out;
    }

    /** The questions of that clause that nothing answered. */
    private static Set<CoverageObligation> leftStandingBy(String clause) {
        Set<CoverageObligation> out = new LinkedHashSet<>();
        read(clause).accounting().values().forEach(account ->
                account.unaccounted().forEach(owed -> out.add(owed.obligation())));
        return out;
    }

    /** What became of the line question of that clause. */
    private static RuleAccounting.Outcome lineOf(String clause) {
        return read(clause).accounting().values().stream()
                .flatMap(account -> account.answers().entrySet().stream())
                .filter(e -> e.getKey().obligation() == CoverageObligation.BOUNDARY)
                .map(java.util.Map.Entry::getValue)
                .findFirst().orElseThrow(() -> new AssertionError("no line was asked about"));
    }

    private static final Set<CoverageObligation> BOTH =
            Set.of(CoverageObligation.ADMITTED_VALUES, CoverageObligation.BOUNDARY);

    /** The line read, which is the row the other one is read against. */
    @Test
    void aBoundWrittenAsANumberRaisesTheQuestionAboutItsLineAndIsAnswered() {
        assertEquals(BOTH, raisedBy("invariant top = value <= 20"));
        assertEquals(Set.of(), leftStandingBy("invariant top = value <= 20"));
        assertInstanceOf(RuleAccounting.Outcome.Accounted.class, lineOf("invariant top = value <= 20"));
    }

    /**
     * The same line, written so that this compiler cannot fold it, asked about and left standing.
     *
     * <p>The first {@code BOUNDARY} nothing answers. What is reported is the rule the author wrote,
     * at the line it draws, with the reading that would have answered saying what stopped it — and
     * not a model that came back complete.
     */
    @Test
    void theSameLineWrittenAsAProductIsAskedAboutAndNothingAnswersIt() {
        assertEquals(BOTH, raisedBy("invariant top = value <= 10 * 2"),
                "the model states a line at 20 either way, so both are asked about");
        assertEquals(Set.of(CoverageObligation.BOUNDARY),
                leftStandingBy("invariant top = value <= 10 * 2"),
                "and the one this could not fold is the one nothing answered");

        RuleAccounting.Outcome.Unaccounted open = assertInstanceOf(
                RuleAccounting.Outcome.Unaccounted.class, lineOf("invariant top = value <= 10 * 2"));
        assertInstanceOf(RuleAccounting.Why.TheEndReadingSays.class, open.why(),
                "said by the reading that would have answered, in its own words");
    }

    /**
     * A rule about the strings at the position states where they stop, and raises the question
     * about it like any other rule that does.
     *
     * <p>Not a comparison, and that is the point. What the model states is where the values stop —
     * {@code String.startsWith("JP", value)} admits the strings from {@code "JP"} up to but not
     * including {@code "JQ"} — and which call an author wrote it as is no part of that. Raised off
     * the shape of the clause, the question existed for one of the two spellings and the accounting
     * for the other came back complete.
     */
    @Test
    void aRuleAboutTheStringsThatStatesWhereTheyStopRaisesTheQuestionAboutItsLine() {
        String clause = "invariant top = String.startsWith(\"JP\", value)";
        assertEquals(BOTH, onAString(clause),
                "the model states where the strings stop, so both are asked about");
    }

    /**
     * And one whose strings stop nowhere raises no question about a line.
     *
     * <p>{@code [0-9]{4}} leaves out a string between two it admits, so there is no stretch of the
     * order it holds the values between — which is what the rule states and not what a reading of it
     * managed. A question raised here would be one no line could ever answer.
     */
    @Test
    void aRuleWhoseStringsStopNowhereRaisesNoQuestionAboutALine() {
        assertEquals(Set.of(CoverageObligation.ADMITTED_VALUES),
                onAString("invariant top = String.matches(\"[0-9]{4}\", value)"));
    }

    /**
     * A branch that says nothing about the strings at a position leaves every string standing
     * there, and a choice between that and a run is every string.
     *
     * <p>The one place the two operations part. Held together, a reading that says nothing about a
     * position changes nothing and the other side stands; held as a choice, it is every string and
     * absorbs whatever the other side admits. Read as the one branch that spoke, the choice would
     * stop the values where only one of its branches stops them — a line the model does not draw.
     */
    @Test
    void aBranchSayingNothingAboutTheStringsAbsorbsTheRunOfTheOther() {
        String source = """
                module example.rooms

                data Code = { s: String, b: Bool }
                    invariant one = String.startsWith("JP", s) || b
                """;
        assertEquals(Set.of(CoverageObligation.ADMITTED_VALUES), raisedIn(source, "Code"),
                "the choice leaves every string at `s`, so nothing stops them anywhere");
    }

    /**
     * One clause can state where the values stop on one number and leave the next standing.
     *
     * <p>Both branches of the choice state a run at {@code a}, so the choice does; and at {@code b}
     * the rule is one this reads no further into, so whether it states a line there is a question
     * nothing settled. A classification that said one number would have to choose between them, and
     * one that said "a bound" of the whole clause would lose the second question.
     */
    @Test
    void oneClauseCanBoundOneNumberAndLeaveTheNextStanding() {
        String deep = "(".repeat(201) + "x" + ")".repeat(201);
        String source = """
                module example.rooms

                data Code = { a: String, b: String }
                    invariant one =
                        (String.startsWith("A", a) && String.matches("%s", b))
                            || (String.startsWith("A", a) && String.matches("%s", b))
                """.formatted(deep, deep);
        assertEquals(Set.of(CoverageObligation.ADMITTED_VALUES, CoverageObligation.BOUNDARY),
                raisedIn(source, "Code"),
                "a line on one number and a question about the next");
        assertEquals(Set.of(RuleKey.of("b")), undeterminedIn(source, "Code"),
                "and whether it stops them at `b` is a question with no answer, which is what"
                        + " would be lost if the clause were classified by the number it did stop"
                        + " them on");
    }

    /**
     * One clause can state where the values stop on two numbers, and both questions are raised.
     *
     * <p>Both branches of the choice state a run at each of the two positions, so the choice states
     * one at each. Held as the number a clause stops the values on, one of them would have to be
     * chosen between or refused — and read as the count of questions, the two collapse into one
     * word, so what is asked here is which numbers they are about.
     */
    @Test
    void oneClauseCanBoundTwoNumbers() {
        String source = """
                module example.rooms

                data Code = { a: String, b: String }
                    invariant one =
                        (String.startsWith("A", a) && String.startsWith("B", b))
                            || (String.startsWith("A", a) && String.startsWith("B", b))
                """;
        assertEquals(Set.of(RuleKey.of("a"), RuleKey.of("b")), boundedIn(source, "Code"),
                "a line on each, and neither chosen between");
    }

    /**
     * Two branches stopped by two things leave both of them standing, all the way to the accounting.
     *
     * <p>One branch is written more deeply than this reads and the other is written in a form it
     * does not enter, and those go out under two different words. Kept as one, which of them an
     * author is sent to would turn on which branch they wrote first — and each of the two is a thing
     * that would have to change before the question could be answered, so neither stands for the
     * other.
     */
    @Test
    void twoBranchesStoppedByTwoThingsLeaveBothStanding() {
        String deep = "(".repeat(201) + "x" + ")".repeat(201);
        String source = """
                module example.rooms

                data Code = String
                    invariant one =
                        String.matches("%s", value) || String.matches(value, value)
                """.formatted(deep);

        assertEquals(List.of(new BlockReason.PatternTooDeeplyNested(),
                        new BlockReason.UnreadValueRule()),
                whyUndeterminedIn(source, "Code"),
                "both of them, in the order the clause writes them");
    }

    /** What the one clause of {@code named} leaves the line question standing for. */
    private static List<BlockReason.RuleReadingStopped> whyUndeterminedIn(String source,
                                                                          String named) {
        List<BlockReason.RuleReadingStopped> out = new ArrayList<>();
        rulesOf(source, named).required().values().forEach(required ->
                required.undetermined().forEach(each -> out.addAll(each.why())));
        return out;
    }

    /** The numbers the one clause of {@code named} states the values stop on. */
    private static Set<RuleKey> boundedIn(String source, String named) {
        Set<RuleKey> out = new LinkedHashSet<>();
        rulesOf(source, named).required().values().forEach(required ->
                required.obligations().forEach(owed -> {
                    if (owed instanceof Owed.Boundary line) {
                        out.add(line.on().position());
                    }
                }));
        return out;
    }

    /** The names whose line the one clause of {@code named} leaves undecided. */
    private static Set<RuleKey> undeterminedIn(String source, String named) {
        Set<RuleKey> out = new LinkedHashSet<>();
        rulesOf(source, named).required().values().forEach(required ->
                required.undetermined().forEach(each -> out.add(each.at())));
        return out;
    }

    /** What the one clause of {@code named} raises, over every place it writes. */
    private static Set<CoverageObligation> raisedIn(String source, String named) {
        Set<CoverageObligation> out = new LinkedHashSet<>();
        rulesOf(source, named).required().values().forEach(required ->
                required.obligations().forEach(owed -> out.add(owed.obligation())));
        return out;
    }

    /** The rules of {@code named} in {@code source}, read as the compilation reads them. */
    private static FieldDomains rulesOf(String source, String named) {
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.answerEverything();
        String module = compilation.modules().get(0);
        Symbols symbols = Scopes.derived(compilation.db(), module).value();
        assertNotNull(symbols);
        TypeSymbol.AtModule at = TypeSymbols.declared(new TypeKey(module, named));
        assertNotNull(symbols.declaredNode(at.key()), "no `" + named + "` declared");
        return FieldDomains.of(at, RuleReadings.of(compilation, module),
                souther.compiler.query.ReadAs.THE_COMPILATION_DOES);
    }

    /** The same of a declaration over strings, since a run is a statement about those. */
    private static Set<CoverageObligation> onAString(String clause) {
        String source = """
                module example.rooms

                data Code = String
                    %s
                """.formatted(clause);
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.answerEverything();
        String module = compilation.modules().get(0);
        Symbols symbols = Scopes.derived(compilation.db(), module).value();
        assertNotNull(symbols);
        TypeSymbol.AtModule named = TypeSymbols.declared(new TypeKey(module, "Code"));
        assertNotNull(symbols.declaredNode(named.key()), "no `Code` declared");
        FieldDomains domains = FieldDomains.of(named, RuleReadings.of(compilation, module),
                souther.compiler.query.ReadAs.THE_COMPILATION_DOES);
        assertEquals(1, domains.required().size(),
                () -> "one clause, one rule: " + domains.required().keySet());
        Set<CoverageObligation> out = new LinkedHashSet<>();
        domains.required().values().iterator().next().obligations()
                .forEach(owed -> out.add(owed.obligation()));
        return out;
    }

    /**
     * A bound the order has no value past, which is a rule read to the end and not a rule missed.
     *
     * <p>{@code value > 9223372036854775807} steps off the order: the count beside the one named is
     * one no whole number is at, so the position holds nothing. The question about its line is
     * raised and answered — the reading got there and said what the rule comes to. That no row can
     * be written there is a question about composing a row, which is existential over the whole
     * value and is no rule's to answer (#854).
     */
    @Test
    void aBoundPastWhereTheOrderStopsIsAskedAboutAndIsAnswered() {
        assertEquals(BOTH, raisedBy("invariant none = value > 9223372036854775807"));
        assertInstanceOf(RuleAccounting.Outcome.Accounted.class,
                lineOf("invariant none = value > 9223372036854775807"),
                "the reading read the rule to the end, which is the line understood");
    }

    /**
     * And the reading of values still answers for itself, in its own words.
     *
     * <p>Here so that the rows above are read against a measure that comes out the other way, and so
     * that the two vocabularies are not one. A rule nothing took in leaves its {@code ADMITTED_VALUES}
     * standing, said by the reading that turns clauses into sets of values.
     */
    @Test
    void aRuleNothingReadLeavesTheOtherQuestionStandingSaidByTheOtherReading() {
        assertEquals(Set.of(CoverageObligation.ADMITTED_VALUES),
                leftStandingBy("invariant said = Int.abs(value) >= 2"));

        RuleAccounting.Outcome.Unaccounted open =
                (RuleAccounting.Outcome.Unaccounted) read("invariant said = Int.abs(value) >= 2")
                        .accounting().values().iterator().next().answers().values().stream()
                        .filter(RuleAccounting.Outcome.Unaccounted.class::isInstance)
                        .findFirst().orElseThrow();
        assertInstanceOf(RuleAccounting.Why.TheValueReadingSays.class, open.why());
    }
}
