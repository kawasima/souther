package souther.program.api;

import souther.compiler.cst.SourceLayout;
import souther.compiler.diag.QuotedFrom;
import souther.compiler.observe.Expectation;
import souther.compiler.observe.Mismatch;
import souther.compiler.observe.ObservedValue;
import souther.compiler.observe.PathElement;
import souther.compiler.observe.RowIdentity;
import souther.compiler.observe.RowStatement;
import souther.compiler.observe.Verdict;
import souther.compiler.program.CheckedBehavior;
import souther.compiler.program.CheckedModule;
import souther.compiler.program.CheckedProgram;
import souther.compiler.program.CheckedRow;
import souther.compiler.program.StandsIn;
import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbol;
import souther.compiler.types.TypeSymbols;
import souther.compiler.types.ValueName;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a behavior's {@code example} rows said, read by an output that is not this compiler.
 *
 * <p>An output can emit a program; what it could not do is show that what it emitted means what the
 * language says it means. Its only oracle was a test it wrote in its own artifact, over inputs it
 * picked itself — so two outputs of one program agreed about it as far as whoever wrote the second
 * one's tests thought of the same cases, and neither the set that was walked nor the set that was
 * not was stated anywhere.
 *
 * <p>The rows are that statement, and they are already written, already bound and already run. What
 * crosses here is what each row states, and the question of whether an answer keeps it — asked of
 * the language rather than decided by whoever reads it.
 */
class AnOutputReadsWhatABehaviorsExamplesSaidTest {

    private static final String MODULE = """
            module demo

            data Name = String
            data Amount = Int
            data Receipt = { name: Name, total: Amount }
            data Refused = { why: String }

            behavior receiptFor : (name: Name, total: Amount) -> Receipt | Refused
                constructs Receipt, Refused

            let receiptFor (name, total) =
                if total.value > 0 then Receipt { name = name, total = total }
                else Refused { why = "nothing to bill" }

            example receiptFor
                | "a billed line" : (Name("ada"), Amount(3)) -> Receipt { name = Name("ada"), total = Amount(3) }
                | (Name("ada"), Amount(0)) -> Refused
            """;

    private static final TypeSymbol.AtModule NAME = declared("Name");
    private static final TypeSymbol.AtModule AMOUNT = declared("Amount");
    private static final TypeSymbol.AtModule RECEIPT = declared("Receipt");
    private static final TypeSymbol.AtModule REFUSED = declared("Refused");

    private static TypeSymbol.AtModule declared(String name) {
        return TypeSymbols.declared(new TypeKey("demo", name));
    }

    private static CheckedBehavior behavior(CheckedProgram program, String module, String name) {
        CheckedModule of = program.module(module);
        return of.behavior(new ValueName.Behavior(module, name));
    }

    /** A row an output runs on its own, which is the arm for a behavior that depends on nothing. */
    private static CheckedRow.SelfContained selfContained(CheckedRow row) {
        return assertInstanceOf(CheckedRow.SelfContained.class, row.statement());
    }

    /** Every row a behavior has, in the order they are written. */
    @Test
    void everyRowRecordedForABehaviorCrosses() {
        List<CheckedRow> rows =
                behavior(CheckedProgram.of(List.of(MODULE)), "demo", "receiptFor").rows();

        assertEquals(2, rows.size(), () -> "the rows that crossed are " + rows);
        assertEquals(new RowIdentity.Named("a billed line"), rows.get(0).identity());
        assertEquals(new RowIdentity.Unnamed(2), rows.get(1).identity(),
                "a row written without a name is shown as which of its behavior's rows it is");
        assertInstanceOf(QuotedFrom.ASourceThisCompileHolds.class, rows.get(0).at().quotedFrom(),
                "and where a row is written says which source it is in, not only which line");
    }

    /**
     * A row's inputs cross exactly, as the values they are.
     *
     * <p>Not the text they were written as. What an output hands its own emission is a value, and a
     * value written under a name is that name's — an {@code Amount} holding three and the number
     * three are not one value, which is the whole of what a comparison is about.
     */
    @Test
    void aRowsInputsCrossAsTheValuesTheyAre() {
        CheckedRow row = behavior(CheckedProgram.of(List.of(MODULE)), "demo", "receiptFor")
                .rows().get(0);

        assertEquals(List.of(newtype(NAME, new ObservedValue.Text("ada")),
                        newtype(AMOUNT, new ObservedValue.Integer(3))),
                selfContained(row).states().inputs());
    }

    /**
     * Whether an answer keeps what a row states is asked of the language.
     *
     * <p>The output says what its answer was, in the form a value crosses in, and asks. It does not
     * hold two values up against each other: which of them are the same value is a decision the
     * language makes, and an output free to make it again would answer differently about the same
     * row.
     */
    @Test
    void whetherAnAnswerKeepsARowIsAskedRatherThanDecided() {
        CheckedRow row = behavior(CheckedProgram.of(List.of(MODULE)), "demo", "receiptFor")
                .rows().get(0);

        assertEquals(new Verdict.Held(), selfContained(row).holds(receipt("ada", 3)));

        Mismatch differs = notHeld(selfContained(row).holds(receipt("ada", 4)));
        assertEquals(Mismatch.Reason.VALUE, differs.reason());
        assertEquals(List.of(new PathElement.Field("total"), new PathElement.Field("value")),
                differs.path(), "and where inside the two values they part");
    }

    /**
     * And what the answer is a value <em>of</em> is part of it.
     *
     * <p>An output that emitted the number where an {@code Amount} stands has emitted something
     * else, and a comparison over the run-time values alone would call the two one value.
     */
    @Test
    void andTwoValuesDifferWhereTheirTypesDoAsMuchAsWhereTheirContentsDo() {
        CheckedRow row = behavior(CheckedProgram.of(List.of(MODULE)), "demo", "receiptFor")
                .rows().get(0);

        Map<String, ObservedValue> fields = ObservedValue.fields();
        fields.put("name", newtype(NAME, new ObservedValue.Text("ada")));
        fields.put("total", new ObservedValue.Integer(3));

        Mismatch differs =
                notHeld(selfContained(row).holds(new ObservedValue.Constructed(RECEIPT, fields)));
        assertEquals(Mismatch.Reason.TYPE, differs.reason());
        assertEquals(List.of(new PathElement.Field("total")), differs.path());
    }

    /**
     * A row that named a case is held to the case and to nothing under it.
     *
     * <p>{@code | Refused} says which case the answer is and says nothing about what is in it. Held
     * to a whole value it would report a difference nobody stated.
     */
    @Test
    void aRowThatNamedACaseIsHeldToTheCase() {
        CheckedRow row = behavior(CheckedProgram.of(List.of(MODULE)), "demo", "receiptFor")
                .rows().get(1);

        assertEquals(new Expectation.TheCase(REFUSED), selfContained(row).states().expects());

        Map<String, ObservedValue> why = ObservedValue.fields();
        why.put("why", new ObservedValue.Text("anything at all"));
        assertEquals(new Verdict.Held(),
                selfContained(row).holds(new ObservedValue.Constructed(REFUSED, why)));
        assertEquals(Mismatch.Reason.TYPE,
                notHeld(selfContained(row).holds(receipt("ada", 3))).reason());
    }

    /**
     * An answer nothing can be read out of does not hold the row.
     *
     * <p>The same verdict the compile reaches about the same answer. A third state — held, differs,
     * cannot say — would be this reading deciding for itself what the language settles, and two
     * outputs would then disagree about a row neither of them could read.
     */
    @Test
    void anAnswerThatCouldNotBeReadDoesNotHoldTheRow() {
        CheckedRow row = behavior(CheckedProgram.of(List.of(MODULE)), "demo", "receiptFor")
                .rows().get(0);

        assertEquals(Mismatch.Reason.UNREADABLE,
                notHeld(selfContained(row)
                        .holds(new ObservedValue.Unknown("the output could not read it")))
                        .reason());
    }

    /**
     * A row of a behavior that takes something injected crosses with what stood in for it.
     *
     * <p>A row runs against a bound implementation, and what stands in for an injected dependency is
     * the rest of what makes it runnable. An output whose dependency is an import has nothing of its
     * own to answer that import with, so it is handed what the row states the dependency answers —
     * as values and a rule, rather than as anything it has to apply.
     */
    @Test
    void aRowOfABehaviorThatNeedsAStandInCrossesWithWhatStoodInForIt() {
        String source = """
                module demo

                data Rate = Int
                data Price = Int

                behavior rateNow : () -> Rate

                behavior priceOf : (base: Price) -> Price
                    depends on rateNow
                    constructs Price

                let priceOf (base, rateNow) = Price(base.value * rateNow().value)

                example priceOf
                    | "at twice" : (Price(10)) with rateNow = Rate(2) -> Price(20)
                """;
        CheckedProgram program = CheckedProgram.of(List.of(source));

        CheckedRow row = behavior(program, "demo", "priceOf").rows().get(0);
        // An arm of its own, so that a reader written for rows that run on their own cannot be
        // handed one that does not: what it has to do with this row is in the type.
        CheckedRow.WithStandIns states =
                assertInstanceOf(CheckedRow.WithStandIns.class, row.statement());
        assertEquals(List.of(newtype(declared("Price"), new ObservedValue.Integer(10))),
                states.states().inputs(), "and it hands over its inputs like any other row");

        StandsIn rateNow = states.standsIn().get(0);
        assertEquals(new ValueName.Behavior("demo", "rateNow"), rateNow.dependency());
        // What a `with` answers, asked rather than read: it lists nothing and answers everything,
        // which is the same shape a table with a `_` row is.
        assertEquals(new StandsIn.Answer.TheValue(
                        newtype(declared("Rate"), new ObservedValue.Integer(2))),
                rateNow.answering(List.of()));
    }

    /**
     * A value a limit stopped crosses as unavailable rather than as a row holding a shortened value.
     *
     * <p>A row whose input is larger than what is kept states a value nothing here holds. Crossing
     * as much of it as fits would be an output comparing its answer against a value nobody wrote,
     * and crossing as no row at all would have the behavior read as saying nothing about an input
     * someone wrote down.
     */
    @Test
    void aValueThatWasNotKeptCrossesAsUnavailable() {
        StringBuilder elements = new StringBuilder();
        for (int i = 0; i < 65; i++) {
            elements.append(i == 0 ? "" : ", ").append(i);
        }
        String source = """
                module demo

                data Count = Int

                behavior countOf : (xs: List<Int>) -> Count constructs Count

                let countOf (xs) = Count(List.length(xs))

                example countOf
                    | "a long list" : ([ %s ]) -> Count(65)
                """.formatted(elements);
        CheckedProgram program = CheckedProgram.of(List.of(source));

        CheckedRow row = behavior(program, "demo", "countOf").rows().get(0);
        CheckedRow.NotReproducible states =
                assertInstanceOf(CheckedRow.NotReproducible.class, row.statement());
        assertEquals(new RowStatement.Incomplete(new RowStatement.Side.AnInput(0),
                        souther.compiler.observe.Incompleteness.Code.VALUE_TRUNCATED),
                states.why(),
                "the row is there, and says which of its values was not kept");
    }

    /**
     * And a value a limit stopped inside a stand-in leaves the row unavailable too.
     *
     * <p>A stand-in's values are values. A row crossing with a shortened one would have an output
     * answer the dependency with something nobody wrote, which is the row running against a fake
     * this program does not hold — so the row states nothing, and says which value and where it is
     * written.
     */
    @Test
    void andSoDoesAValueAStandInStatesThatWasNotKept() {
        StringBuilder elements = new StringBuilder();
        for (int i = 0; i < 65; i++) {
            elements.append(i == 0 ? "" : ", ").append(i);
        }
        String source = """
                module demo

                data Count = Int

                behavior everyTag : () -> List<Int>

                behavior countOf : () -> Count
                    depends on everyTag
                    constructs Count

                let countOf (everyTag) = Count(List.length(everyTag()))

                fake everyTag
                    | _ -> [ %s ]

                example countOf
                    | "a long list stood in" : () -> Count(65)
                """.formatted(elements);
        CheckedProgram program = CheckedProgram.of(List.of(source));

        CheckedRow row = behavior(program, "demo", "countOf").rows().get(0);
        CheckedRow.NotReproducible states =
                assertInstanceOf(CheckedRow.NotReproducible.class, row.statement());
        RowStatement.StandInUnavailable why =
                assertInstanceOf(RowStatement.StandInUnavailable.class, states.why());
        assertEquals(new ValueName.Behavior("demo", "everyTag"), why.dependency());
        assertEquals(new RowStatement.StandInUnavailable.Why.AValueOfIt(
                        souther.compiler.observe.Incompleteness.Code.VALUE_TRUNCATED), why.why());
        // The table's row and not the table: a table states a value on each of its rows and one
        // more where it answers what it does not list, so naming the table would leave a reader to
        // find which of them nothing could be made of. Line 13 is `fake everyTag` and line 17 is
        // the example row; 14 is the value.
        assertEquals(14, SourceLayout.of(source).resolve(why.at()).line(),
                "quoted at the value that was not kept, which is where it is written");
    }

    /**
     * And which value of the stand-in it was, where a row states more than one.
     *
     * <p>A table's row states one value for each of the dependency's arguments and one more for the
     * answer. Told only which row nothing could be made of, an author is left to work out which of
     * them it was — so what is said is the place that value is written, which is what tells the
     * second argument from the first.
     */
    @Test
    void andWhichOfTheValuesOnTheRowItWas() {
        StringBuilder elements = new StringBuilder();
        for (int i = 0; i < 65; i++) {
            elements.append(i == 0 ? "" : ", ").append(i);
        }
        String source = """
                module demo

                data Count = Int

                behavior tally : (label: String, xs: List<Int>) -> Count

                behavior countOf : (label: String) -> Count
                    depends on tally

                let countOf (label, tally) = tally(label, [ ])

                fake tally
                    | ("a",
                       [ %s ])
                        -> Count(1)
                    | _ -> Count(0)

                example countOf
                    | "counted by what stands in" : ("a") -> Count(0)
                """.formatted(elements);
        CheckedProgram program = CheckedProgram.of(List.of(source));

        CheckedRow row = behavior(program, "demo", "countOf").rows().get(0);
        RowStatement.StandInUnavailable why = assertInstanceOf(
                RowStatement.StandInUnavailable.class,
                assertInstanceOf(CheckedRow.NotReproducible.class, row.statement()).why());

        // The row begins on line 13 with its first argument, its second is on 14, and the answer it
        // states for them is on 15. What was not kept is the second argument, and neither the row
        // nor the answer is what a reader is sent to.
        assertEquals(14, SourceLayout.of(source).resolve(why.at()).line(),
                "quoted at the value that was not kept, which is the second argument");
    }

    /**
     * What a field holds is read from what declares it, all the way from the program.
     *
     * <p>A row writing {@code [ 1, 2 ]} says which elements and not which order they are in, and
     * what says whether the order is part of the value is the field's declared type. So a
     * comparison made without the program's declarations would hold an answer to an order nobody
     * wrote — which is the one thing the reading handed to a row has to carry, end to end.
     */
    @Test
    void andWhatAFieldHoldsIsReadFromWhatThisProgramDeclares() {
        String source = """
                module demo

                data Tags = { of: Set<Int> }

                behavior tagsOf : (xs: List<Int>) -> Tags constructs Tags

                let tagsOf (xs) = Tags { of = Set.fromList(xs) }

                example tagsOf
                    | "a set of two" : ([ 1, 2 ]) -> Tags { of = [ 1, 2 ] }
                """;
        CheckedProgram program = CheckedProgram.of(List.of(source));

        CheckedRow.SelfContained row = selfContained(
                behavior(program, "demo", "tagsOf").rows().get(0));
        Map<String, ObservedValue> of = ObservedValue.fields();
        of.put("of", new ObservedValue.Sequence(List.of(new ObservedValue.Integer(2),
                new ObservedValue.Integer(1))));

        assertEquals(new Verdict.Held(),
                row.holds(new ObservedValue.Constructed(declared("Tags"), of)),
                "the field is declared a set, so the order the answer stands in is not part of it");
    }

    /**
     * A row naming a case that carries nothing is held to that case.
     *
     * <p>A unit case is a name, and its value is the name. What an answer's case is has to be read
     * for it as well as for a case with fields under it, or a row naming one would be held to a
     * case nothing answers with.
     */
    @Test
    void aRowNamingACaseThatCarriesNothingIsHeldToIt() {
        String source = """
                module demo

                data Missing
                data Found = { name: String }

                behavior lookUp : (there: Bool) -> Found | Missing constructs Found

                let lookUp (there) = if there then Found { name = "ada" } else Missing

                example lookUp
                    | "not there" : (false) -> Missing
                """;
        CheckedProgram program = CheckedProgram.of(List.of(source));

        CheckedRow.SelfContained row = selfContained(
                behavior(program, "demo", "lookUp").rows().get(0));
        assertEquals(new Expectation.TheCase(declared("Missing")), row.states().expects());
        assertEquals(new Verdict.Held(),
                row.holds(new ObservedValue.Unit(declared("Missing"))));

        Map<String, ObservedValue> found = ObservedValue.fields();
        found.put("name", new ObservedValue.Text("ada"));
        assertEquals(Mismatch.Reason.TYPE,
                notHeld(row.holds(new ObservedValue.Constructed(declared("Found"), found)))
                        .reason());
    }

    /** And a behavior nobody exampled has no rows, which is not the same as one whose rows were
     *  not read. */
    @Test
    void aBehaviorNothingExamplesHasNoRows() {
        String source = """
                module demo

                data Amount = Int

                behavior twice : (a: Amount) -> Amount constructs Amount

                let twice (a) = Amount(a.value * 2)
                """;
        CheckedProgram program = CheckedProgram.of(List.of(source));

        assertEquals(List.of(), behavior(program, "demo", "twice").rows());
    }

    /**
     * Every row written in the program is one an output is handed.
     *
     * <p>What must never happen is a row that cannot be reproduced arriving as no row: an output
     * would then read "this behavior has no rows" and count a set it never walked as one it walked
     * and found empty. Counted over a program whose rows are of every kind there is.
     */
    @Test
    void everyRowWrittenCrossesAsARow() {
        String source = """
                module demo

                data Amount = Int
                data Rate = Int

                behavior rateNow : () -> Rate

                behavior twice : (a: Amount) -> Amount constructs Amount

                let twice (a) = Amount(a.value * 2)

                behavior scaled : (a: Amount) -> Amount
                    depends on rateNow
                    constructs Amount

                let scaled (a, rateNow) = Amount(a.value * rateNow().value)

                example twice
                    | "one" : (Amount(1)) -> Amount(2)
                    | (Amount(2)) -> Amount(4)

                example scaled
                    | "with a rate" : (Amount(2)) with rateNow = Rate(3) -> Amount(6)
                """;
        CheckedProgram program = CheckedProgram.of(List.of(source));

        List<CheckedRow> every = new ArrayList<>();
        for (CheckedModule module : program.modules()) {
            for (CheckedBehavior behavior : module.behaviors()) {
                every.addAll(behavior.rows());
            }
        }
        assertEquals(3, every.size(), () -> "the rows that crossed are " + every);
        assertTrue(every.stream().anyMatch(row -> row.statement()
                        instanceof CheckedRow.WithStandIns),
                "including the one that needs something stood in for");
    }

    private static Mismatch notHeld(Verdict verdict) {
        return assertInstanceOf(Verdict.NotHeld.class, verdict).differs();
    }

    /** A receipt as an output would say it answered with. */
    private static ObservedValue receipt(String name, long total) {
        Map<String, ObservedValue> fields = ObservedValue.fields();
        fields.put("name", newtype(NAME, new ObservedValue.Text(name)));
        fields.put("total", newtype(AMOUNT, new ObservedValue.Integer(total)));
        return new ObservedValue.Constructed(RECEIPT, fields);
    }

    /** A value under a name, which is the single field every newtype is written with. */
    private static ObservedValue newtype(TypeSymbol.AtModule name, ObservedValue value) {
        Map<String, ObservedValue> fields = new LinkedHashMap<>();
        fields.put("value", value);
        return new ObservedValue.Constructed(name, fields);
    }
}
