package souther.compiler.partition;

import org.junit.jupiter.api.Test;

import souther.compiler.check.ReadingPolicy;
import souther.compiler.check.RuleReadingSource;
import souther.compiler.check.RuleReadings;
import souther.compiler.check.Symbols;
import souther.compiler.check.TypeView;
import souther.compiler.inputs.Case;
import souther.compiler.inputs.Distinctions;
import souther.compiler.inputs.Refinement;
import souther.compiler.inputs.TermPath;
import souther.compiler.observe.ObservedValue;
import souther.compiler.types.Type;
import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbol;
import souther.compiler.types.TypeSymbols;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * A row is read at a position by what the reading exposes there, and never by what its value
 * happens to carry.
 *
 * <p>Two ways of getting this wrong and they are opposite. A walk that asks where a value is
 * <em>written</em> reaches nothing at a name every case of a sum spreads — the name is readable at
 * the sum and a row writes one of the cases — so every point on a line over such a total came back
 * undecided while the row standing on it was in the file. A walk that took the name off whatever
 * value it held instead would read a case's own field on the rows that are that case and refuse it
 * on the rest, which is a readability decided per row and is not something the model states.
 *
 * <p>So a field is admitted by {@code check.ReadableFields} and then taken from the value in hand.
 * The rows here carry the second half of that: a {@code Card} holds its own {@code cardNumber} as
 * well as the {@code amount} it spreads, so the case that must not be readable is one every row of
 * it does carry.
 *
 * <p><b>And a narrowing is how a case's own field is reached.</b> A path that names the case reads
 * what only that case declares, which the model says by spelling the case; a row at another case
 * takes that step and stands nowhere below it, which is a row that is somewhere else and not a walk
 * that could not be made. Those two answers are what tells a point nothing covers from a point
 * nothing could be told about.
 */
class AnObservedValueIsReadByWhatIsReadableAndNotByWhatItCarriesTest {

    /** Two cases spreading one declaration, each with a field of its own. */
    private static final String SPREAD = """
            module g

            data Common = { amount: Int }

            data Card = { ...Common, cardNumber: String }
            data Cash = { ...Common, note: String }

            data Method = Card | Cash

            data Entry = { method: Method, settled: Bool }

            data Page = { count: Int }

            behavior readArticles : (ns: List<Entry>) -> Page
            """;

    /** The same model with the amount on the element, which is the shape that always worked. */
    private static final String ON_THE_ELEMENT = """
            module g

            data Method = { amount: Int, cardNumber: String }

            data Entry = { method: Method, settled: Bool }

            data Page = { count: Int }

            behavior readArticles : (ns: List<Entry>) -> Page
            """;

    private static final ReadingPolicy POLICY = new ReadingPolicy(64, 12,
            souther.compiler.values.AsACompilationAllows.admittedValues(),
            souther.compiler.values.AsACompilationAllows.whatARuleLeaves());

    /**
     * The name every case spreads is read at each of them, whichever case the row turned out to be.
     *
     * <p>Both values and in the order the row wrote them, so what comes back is the run a total over
     * this path adds up rather than the elements one case happened to be at.
     */
    @Test
    void aNameEveryCaseSpreadsIsReadAtEveryCase() {
        assertEquals(List.of(new ObservedValue.Integer(6), new ObservedValue.Integer(7)),
                stood(SPREAD, aCardAndACash(), amount()),
                "the amount is readable at the sum, so a row is read at it whichever case it wrote");
    }

    /**
     * And a case's own field is not readable at the sum, though the row carrying it holds it.
     *
     * <p>The value is right there: the same row answers with it under a path that names the case
     * ({@link #aNarrowingIsHowACaseOwnFieldIsReached}), so what refuses this walk is the reading and
     * not the row. Admitted off the value instead, this path would answer for the cards and refuse
     * the rest, and what a model may name would be decided by which rows somebody wrote.
     *
     * <p><b>Every element a card, and that is the point of the row.</b> A row holding one of the
     * other case is refused by that element having no such field, which a walk admitting names off
     * the value it holds does too — so the row that tells the two apart is the one where every value
     * carries the name and the reading is the only thing that can refuse it.
     */
    @Test
    void aFieldOnlyOneCaseDeclaresIsNotReadableAtTheSum() {
        assertInstanceOf(WalkResult.CouldNotWalk.class,
                read(SPREAD, List.of(card(6), card(7)), cardNumber()),
                "nothing is readable at the sum but what its cases share, so this walk cannot be"
                        + " made at all");
    }

    /** And the row does hold it, which is what makes the refusal above a rule and not an absence. */
    @Test
    void aNarrowingIsHowACaseOwnFieldIsReached() {
        assertEquals(List.of(new ObservedValue.Text("x")),
                stood(SPREAD, aCardAndACash(), cardNumberAsACard()),
                "a path that names the case may read what only that case declares");
    }

    /**
     * A row at another case stands nowhere below the narrowing, which is not a walk that failed.
     *
     * <p>The two answers a caller tells apart: nothing to read here, and could not look. Answered
     * alike, a point no row covers would be reported as one this compiler could not decide, and the
     * line would be owed a row it already has.
     */
    @Test
    void aRowAtAnotherCaseStandsNowhereRatherThanFailingTheWalk() {
        assertInstanceOf(WalkResult.Reached.class,
                read(SPREAD, List.of(cash(7)), cardNumberAsACard()),
                "the narrowing is a step this path may take, whatever the row wrote");
        assertEquals(List.of(), stood(SPREAD, List.of(cash(7)), cardNumberAsACard()),
                "and a row at the other case stands nowhere below it");
    }

    /**
     * The two models come back with the same values, each having answered with the row's own first.
     *
     * <p>Which is the whole of what the sum was supposed not to change: a total read through a name
     * the cases share is the same run of numbers as one read off the element, and every question
     * about the line follows from the run.
     *
     * <p><b>Each held to the number in the row before they are put beside each other.</b> Two walks
     * that both came back with nothing are equal, so a comparison alone would go on passing over the
     * defect this is about — the model with the amount on the element is the shape that always
     * worked and is not the answer either of them is checked against.
     */
    @Test
    void aSpreadNameReadsLikeOneDeclaredOnTheElement() {
        List<ObservedValue> written = List.of(new ObservedValue.Integer(6));

        assertEquals(written, stood(SPREAD, List.of(card(6)), amount()),
                "the run holds the number the row wrote at the name the cases spread");
        assertEquals(written, stood(ON_THE_ELEMENT, List.of(onTheElement(6)), amount()),
                "and the same number where the element declares it");
        assertEquals(stood(ON_THE_ELEMENT, List.of(onTheElement(6)), amount()),
                stood(SPREAD, List.of(card(6)), amount()),
                "the cases spreading the name change nothing about what a run of it holds");
    }

    /**
     * The steps that are not a field admit what they admit here, said apart from the written walk.
     *
     * <p>An element and a narrowing come to the same type under either relation today, and the two
     * walks answer them separately so that they may stop. Pinned here and in
     * {@link WhatIsReadableAndWhatIsBuiltAgreeAtARecordAndPartAtASumTest} rather than compared, for
     * the reason the four questions about a shape are: two answers held against each other are one
     * answer wearing two names, and the day somebody means to move one of them, what the other says
     * has to fail on its own.
     *
     * <p>An element of a list, a narrowing to the case the row wrote, and a narrowing to the other
     * one. The last is the step being taken and finding nothing, which is what tells the pair apart
     * from a step that cannot be taken at all.
     */
    @Test
    void whatTheStepsBesideAFieldAdmitIsSaidHere() {
        assertEquals(List.of(new ObservedValue.Integer(6), new ObservedValue.Integer(7)),
                stood(SPREAD, aCardAndACash(), amount()),
                "an element of the list is every element the row wrote");
        assertEquals(List.of(new ObservedValue.Text("x")),
                stood(SPREAD, aCardAndACash(), cardNumberAsACard()),
                "and a narrowing keeps what the row wrote as that case");
        assertEquals(List.of(), stood(SPREAD, List.of(cash(7)), cardNumberAsACard()),
                "and drops what it wrote as another, which is a step taken");
    }

    private static TermPath amount() {
        return TermPath.of("ns").element().then("method").then("amount");
    }

    private static TermPath cardNumber() {
        return TermPath.of("ns").element().then("method").then("cardNumber");
    }

    /** The card's own number, read at the position the case narrows the sum to. */
    private static TermPath cardNumberAsACard() {
        return TermPath.of("ns").element().then("method")
                .refine(theCase(SPREAD, "Method", "Card")).then("cardNumber");
    }

    /** The narrowing to {@code leaf}, taken from what the position's type divides into. */
    private static Refinement theCase(String source, String sum, String leaf) {
        RuleReadingSource rules = RuleReadings.ofSource(source);
        Symbols symbols = rules.symbols();
        TypeSymbol wanted = sym(leaf);
        for (Case one : Distinctions.ofType(TypeView.of(named(sum), symbols, rules.published()),
                symbols, rules.published())) {
            if (one instanceof Case.SumCase found && found.leaf().equals(wanted)) {
                return Refinement.of(one);
            }
        }
        throw new AssertionError("the model under test declares `" + leaf + "` as a case of `"
                + sum + "`");
    }

    /** What the walk at {@code path} came to, off a row holding {@code entries}. */
    private static WalkResult<List<ObservedValue>> read(String source, List<ObservedValue> entries,
                                                        TermPath path) {
        RuleReadingSource rules = RuleReadings.ofSource(source);
        BehaviorInputs inputs = new BehaviorInputs(List.of("ns"),
                List.of(new Type.ListOf(named("Entry"))), rules, POLICY);
        return inputs.valuesAt(List.of(new ObservedValue.Sequence(
                entries.stream().map(AnObservedValueIsReadByWhatIsReadableAndNotByWhatItCarriesTest
                        ::entry).toList())), path);
    }

    /** The same where the walk was taken, which is what a test about the values is asking for. */
    private static List<ObservedValue> stood(String source, List<ObservedValue> entries,
                                             TermPath path) {
        if (read(source, entries, path) instanceof WalkResult.Reached(List<ObservedValue> values)) {
            return values;
        }
        throw new AssertionError("the walk down " + path + " could not be taken");
    }

    private static List<ObservedValue> aCardAndACash() {
        return List.of(card(6), cash(7));
    }

    private static ObservedValue entry(ObservedValue method) {
        return new ObservedValue.Constructed(sym("Entry"),
                Map.of("method", method, "settled", new ObservedValue.Bool(true)));
    }

    private static ObservedValue card(int amount) {
        return new ObservedValue.Constructed(sym("Card"),
                Map.of("amount", new ObservedValue.Integer(amount),
                        "cardNumber", new ObservedValue.Text("x")));
    }

    private static ObservedValue cash(int amount) {
        return new ObservedValue.Constructed(sym("Cash"),
                Map.of("amount", new ObservedValue.Integer(amount),
                        "note", new ObservedValue.Text("y")));
    }

    private static ObservedValue onTheElement(int amount) {
        return new ObservedValue.Constructed(sym("Method"),
                Map.of("amount", new ObservedValue.Integer(amount),
                        "cardNumber", new ObservedValue.Text("x")));
    }

    private static Type named(String data) {
        return Type.ref(sym(data));
    }

    private static TypeSymbol sym(String data) {
        return TypeSymbols.declared(new TypeKey("g", data));
    }
}
