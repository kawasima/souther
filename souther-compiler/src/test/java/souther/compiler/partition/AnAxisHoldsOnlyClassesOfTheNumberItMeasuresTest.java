package souther.compiler.partition;

import org.junit.jupiter.api.Test;

import souther.compiler.check.NumericMeasures;
import souther.compiler.check.Symbols;
import souther.compiler.inputs.NumericTerm;
import souther.compiler.inputs.TermPath;
import souther.compiler.query.Adequacy;
import souther.compiler.query.Compilation;
import souther.compiler.query.Scopes;
import souther.compiler.types.Type;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An axis is a run of classes over one number, and the classes it holds are classes of that number.
 *
 * <p>Two things a class can be of, and only one of them can be asked with a place on the number's
 * order: a class about a count says which number it counts, and every other class is about the value
 * standing at the position. A class of the second kind on an axis measuring something taken of the
 * position leaves the two with nothing to compare — a class of times has no answer about which run a
 * minute falls in — and asking anyway comes back as the class not being found, which reads as the
 * rules dividing the position nowhere.
 *
 * <p>Built by hand here rather than searched for in a model. No model this compiler reads today
 * produces such an axis, which is the reason to hold the rule at the way in rather than to rely on
 * the corpus: what is being fixed is that the axis cannot be built, and a fixture that has to be
 * found first would be a test of which models exist.
 */
class AnAxisHoldsOnlyClassesOfTheNumberItMeasuresTest {

    private static final String MODEL = """
            module example.subject

            data Yes
            data No
            data Answer = Yes | No

            data Slot =
                { c: String
                , n: Int
                , on: Bool
                }

            behavior gate : (slot: Slot) -> Answer
            let gate (slot) = {
                guard String.length(slot.c) >= 3 else No
                guard slot.n >= 5 else No
                Yes
            }
            """;

    /** What the model divides, which is where the classes below come from. */
    private static Partitions.Partitioning divided() {
        Compilation compilation = Compilation.ofSource(MODEL, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        return compilation.db()
                .ask(new Adequacy.Divided(compilation.modules().get(0), "gate")).value();
    }

    /** The same measure, offered another run of classes and another set of lines. */
    private static Axis carrying(Axis axis, List<PartitionClass> classes, List<Cut> cuts) {
        return new Axis(axis.id(), axis.term(), classes, cuts);
    }

    private static Axis axisOf(Partitions.Partitioning read, String id) {
        Axis found = read.axes().stream().filter(each -> each.id().toString().equals(id))
                .findFirst().orElse(null);
        assertNotNull(found, "the model is measured at " + id + ", among " + read.axes().stream()
                .map(each -> each.id().toString()).toList());
        return found;
    }

    /**
     * The classes each axis has are of its own number, which is what the rest of this is about.
     *
     * <p>Asserted so that the refusals below are about a rule that lets the model through. A check
     * that refused every class would pass them and leave nothing measured.
     */
    @Test
    void aModelsOwnAxesHoldTheirOwnClasses() {
        Partitions.Partitioning read = divided();

        Axis length = axisOf(read, "gate/String.length(slot.c)");
        Axis count = axisOf(read, "gate/slot.n");
        Axis truth = axisOf(read, "gate/slot.on");

        assertEquals(List.of(length.term()), length.classes().stream()
                .map(PartitionClass::of).distinct().toList(),
                "every class of the length is a class of the length");
        assertEquals(List.of(count.term()), count.classes().stream()
                .map(PartitionClass::of).distinct().toList());
        assertEquals(List.of(truth.term()), truth.classes().stream()
                .map(PartitionClass::of).distinct().toList(),
                "and a truth is a class of the value standing at the position it was read for");
        assertFalse(truth.classes().isEmpty(), "of which this model has some");
    }

    /**
     * A class of one position's value is not a class of another's.
     *
     * <p>The two are told apart by the measure they were built for and never by what they mean: a
     * truth means the same thing at every position, and a rule reading which number a class is of
     * out of its meaning has nothing to read here — so both positions' classes would answer alike
     * and either would pass for the other.
     */
    @Test
    void aClassOfAnotherPositionsValueIsRefused() {
        Partitions.Partitioning read = divided();
        Axis count = axisOf(read, "gate/slot.n");
        Axis truth = axisOf(read, "gate/slot.on");

        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> carrying(count, truth.classes(), List.of()));

        assertTrue(refused.getMessage().contains("slot.on")
                        && refused.getMessage().contains("slot.n"),
                refused.getMessage());
    }

    /** And a class built for no measure at all is not one either, whichever axis is offered it. */
    @Test
    void aClassBuiltForNoMeasureIsRefused() {
        Partitions.Partitioning read = divided();
        Axis count = axisOf(read, "gate/slot.n");
        List<PartitionClass> unstamped = count.classes().stream()
                .map(each -> PartitionClass.of(each.id(), each.label(), each.recognises(),
                        each.representatives()))
                .toList();

        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> carrying(count, unstamped, List.of()));

        assertTrue(refused.getMessage().contains("no measure"), refused.getMessage());
    }

    /** A class of one number is not a class of another, whichever position both are read from. */
    @Test
    void aClassOfAnotherNumberIsRefused() {
        Partitions.Partitioning read = divided();
        Axis length = axisOf(read, "gate/String.length(slot.c)");
        Axis count = axisOf(read, "gate/slot.n");

        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> carrying(length, count.classes(), List.of()));

        assertTrue(refused.getMessage().contains("slot.n")
                        && refused.getMessage().contains("String.length(slot.c)"),
                refused.getMessage());
    }

    /**
     * A class about the value standing at a position is not a class of a number taken of it.
     *
     * <p>The mixed axis a reading would produce by keeping what the declarations divide a position
     * into while measuring the position at something taken of it.
     */
    @Test
    void aClassOfTheValueAtThePositionIsRefusedWhereTheNumberIsTakenOfIt() {
        Partitions.Partitioning read = divided();
        Axis length = axisOf(read, "gate/String.length(slot.c)");
        Axis truth = axisOf(read, "gate/slot.on");

        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> carrying(length, truth.classes(), List.of()));

        assertTrue(refused.getMessage().contains("String.length(slot.c)"), refused.getMessage());
    }

    /**
     * And is one of the axis measuring the value itself, where what stands at the position is the
     * number.
     */
    @Test
    void aClassOfTheValueAtThePositionIsKeptWhereTheAxisMeasuresThatValue() {
        Partitions.Partitioning read = divided();
        Axis truth = axisOf(read, "gate/slot.on");

        Axis again = carrying(truth, truth.classes(), List.of());

        assertEquals(truth.classes(), again.classes());
    }

    /**
     * A position measured at a number taken of it holds that measure and no other.
     *
     * <p>Which is what keeps the classes of one number off an axis of another: a measure is made
     * from the rules about one number and holds what they gave it, so there is nothing for a second
     * number to inherit. A string the body compares the length of has one measure, of the length —
     * and none of the string's own value, which no rule here divides.
     */
    @Test
    void aPositionHoldsTheMeasuresTheRulesNameOfItAndNoOthers() {
        Partitions.Partitioning read = divided();

        assertEquals(List.of("gate/String.length(slot.c)"), idsAt(read, "slot.c"));
        assertEquals(List.of("gate/slot.n"), idsAt(read, "slot.n"));
        assertFalse(axisOf(read, "gate/String.length(slot.c)").classes().isEmpty(),
                "and the one measure is the rules' own division of that number");
    }

    /** What one position of the model is measured at, named as a report names it. */
    private static List<String> idsAt(Partitions.Partitioning read, String path) {
        PositionMeasurements at = read.measurements().stream()
                .filter(each -> each.position().path().toString().equals(path))
                .findFirst().orElse(null);
        assertNotNull(at, "the behavior takes " + path + ", among " + read.measurements().stream()
                .map(each -> each.position().path().toString()).toList());
        return at.axes().stream().map(each -> each.id().toString()).toList();
    }

    /**
     * A class that cannot be asked where on the number it lies is not put on an axis with lines.
     *
     * <p>Every line on an axis falls in the class holding its place, so a class with no place to be
     * asked about holds no line at all, and an axis of such classes with a line on it would answer
     * that the line falls nowhere. Refused where the axis is built, where the two are in hand
     * together.
     */
    @Test
    void aClassWithNoPlaceIsRefusedWhereTheAxisHasLines() {
        Partitions.Partitioning read = divided();
        Axis count = axisOf(read, "gate/slot.n");
        assertFalse(count.cuts().isEmpty(), "the guard drew a line on the number");
        List<PartitionClass> placeless = count.classes().stream()
                .map(each -> PartitionClass.of(each.id(), each.label(), new Recognition.Nothing(),
                        each.representatives()).ofTheNumber(count.term()))
                .toList();

        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> carrying(count, placeless, count.cuts()));

        assertTrue(refused.getMessage().contains("lines"), refused.getMessage());
        assertEquals(placeless, carrying(count, placeless, List.of()).classes(),
                "while with no line to hold, such classes are what a position with no order has");
    }

    /**
     * Which number a class is of is said once.
     *
     * <p>A class of one number is not made a class of another by saying so. Were it, the rule the
     * axis holds would be a label the last writer owns, and nothing an axis could do with it would
     * be worth more than that writer's word.
     */
    @Test
    void aClassOfOneNumberIsNotMadeAClassOfAnother() {
        Partitions.Partitioning read = divided();
        Axis count = axisOf(read, "gate/slot.n");
        Axis truth = axisOf(read, "gate/slot.on");
        PartitionClass ofTheTruth = truth.classes().get(0);

        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> ofTheTruth.ofTheNumber(count.term()));

        assertTrue(refused.getMessage().contains("slot.on")
                && refused.getMessage().contains("slot.n"), refused.getMessage());
        assertEquals(ofTheTruth, ofTheTruth.ofTheNumber(truth.term()),
                "while saying the number it is of again says nothing new");
    }

    /**
     * A class about the value at a position is not a class of a number taken of it, whatever place
     * it was given.
     *
     * <p>The place a case or a named value carries is on the order the position's value is written
     * on; a number taken of the position is counted on an order of its own. A {@code Place} does
     * not say which order it is on, so a class of the first kind said to be of the second would be
     * asked about lines in a vocabulary it was never in — and answer, where the two orders happen to
     * count alike.
     */
    @Test
    void aClassAboutTheValueAtAPositionIsNotSaidToBeOfANumberTakenOfIt() {
        Partitions.Partitioning read = divided();
        Axis length = axisOf(read, "gate/String.length(slot.c)");
        PartitionClass named = PartitionClass.of("three", "three",
                new Recognition.AtAValue(souther.compiler.values.Value.number(
                        java.math.BigDecimal.valueOf(3)), souther.compiler.numeric.Count.of(3L)),
                RepresentativeSource.of(FixtureTemplate.integer(3)));
        PartitionClass ofACase = PartitionClass.of("Won", "Won",
                new Recognition.OfCase(souther.compiler.types.TypeSymbols.declared(
                        new souther.compiler.types.TypeKey("example.subject", "Won")),
                        souther.compiler.numeric.Count.of(3L)),
                RepresentativeSource.of(FixtureTemplate.integer(3)));

        assertThrows(IllegalArgumentException.class, () -> named.ofTheNumber(length.term()),
                "a value the rules named is on the position's order");
        assertThrows(IllegalArgumentException.class, () -> ofACase.ofTheNumber(length.term()),
                "and so is a case");
        assertNotNull(named.ofTheNumber(new NumericTerm.ValueOf(TermPath.of("slot").then("n"))),
                "while either is a class of a position's own value");
    }

    /**
     * A meaning that carries a number is a class of that number and of no other.
     *
     * <p>A class about a count reads that count out of a row, and it is owed to whichever number it
     * was said to be of. Let those differ and the two readers an axis keeps together — which class
     * a line falls in, and which row is owed for it — are about different numbers again.
     */
    @Test
    void aClassAboutOneCountIsNotSaidToBeOfAnother() {
        Partitions.Partitioning read = divided();
        Axis length = axisOf(read, "gate/String.length(slot.c)");
        Axis count = axisOf(read, "gate/slot.n");
        PartitionClass ofTheLength = length.classes().get(0);
        PartitionClass unsaid = PartitionClass.of(ofTheLength.id(), ofTheLength.label(),
                ofTheLength.recognises(), ofTheLength.representatives());

        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> unsaid.ofTheNumber(count.term()));

        assertTrue(refused.getMessage().contains("String.length(slot.c)")
                && refused.getMessage().contains("slot.n"), refused.getMessage());
    }

    /**
     * Two namings of one number are one subject, which is what the rule compares with.
     *
     * <p>A term built here and the one the reading built are different objects and the same number,
     * and the classes of that number belong to an axis measuring it however it was arrived at. Held
     * by identity rather than by what a term is, the rule would refuse them and would do so
     * depending on where the axis had been built.
     */
    @Test
    void aClassIsOfTheNumberAndNotOfTheObjectThatNamesIt() {
        Compilation compilation = Compilation.ofSource(MODEL, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        String module = compilation.modules().get(0);
        Partitions.Partitioning read = compilation.db()
                .ask(new Adequacy.Divided(module, "gate")).value();
        Symbols symbols = Scopes.derived(compilation.db(), module).value();
        Axis length = axisOf(read, "gate/String.length(slot.c)");

        TermPath at = TermPath.of("slot").then("c");
        NumericTerm.TakenOf again = NumericTerm.TakenOf.of(
                NumericMeasures.takenOf(Type.STRING, souther.compiler.check.ScopedDeclarations.wrapsOf(symbols)),
                at, Type.STRING, souther.compiler.check.ScopedDeclarations.wrapsOf(symbols),
                symbols);

        assertNotNull(again, "the length of a string is a number this compiler names");
        assertNotSame(length.term(), again, "a second naming, built here");
        assertEquals(length.term(), again, "of the number the reading already named");

        Axis measuring = new Axis(length.id(), again, length.classes(), length.cuts());

        assertSame(again, measuring.term(),
                "so the classes of that number are the classes of an axis measuring it");
        assertEquals(length.classes(), measuring.classes());
    }
}
