package souther.compiler.partition;

import org.junit.jupiter.api.Test;

import souther.compiler.check.RuleReadingContext;
import souther.compiler.check.RuleReadingSource;
import souther.compiler.check.RuleReadings;
import souther.compiler.inputs.InputDomain;
import souther.compiler.inputs.Position;
import souther.compiler.inputs.TermPath;
import souther.compiler.types.Type;
import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbols;
import souther.compiler.types.TypeSymbol;

import java.util.List;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What one reading of a position comes to, which is what that reading found and nothing more.
 *
 * <p>{@code Open} says this reading found no division. It is not a sentence about the model, and
 * a reader that took it for one would be reading a tally: a producer added later stays outside
 * what "everything was asked" meant, which is how a position whose invariant names the two values
 * it may hold was reported as one the model divides no way.
 *
 * <p>So the two answers are held from the outside, and so is the line between what this reading
 * says and what follows about the model. Whether an absence follows is answered where the
 * position's standing questions and a body's rules are, and the one thing {@code Open} may not do
 * is decide it.
 *
 * <p>Over the positions the language can currently be in, which is the whole claim. A position
 * carrying both a division and children would say something further about the precedence, and no
 * model can be written that has one — only products have children, and a product carries neither
 * classes nor cuts. Building one out of a hand-made {@code Shape} would fix the implementation's
 * product space rather than the language's, so the rows here are the reachable ones.
 */
class AnOpenPositionIsWhatThisReadingFoundTest {

    private static final String MODULE = """
            module demo

            data Prospecting
            data Qualified
            data Won
            data Stage = Prospecting | Qualified | Won

            data Amount = Int invariant value >= 100
            data Plain = Int
            data Slot = { hour: Int, room: String }
            data Gender = String invariant value == "A" || value == "B"
            data Email = String invariant UNREAD
            """.replace("UNREAD", souther.compiler.ARuleNoReadingTakesIn.about("value"));

    private final RuleReadingSource rules = RuleReadings.ofSource(MODULE);

    private TypeSymbol named(String type) {
        return TypeSymbols.declared(new TypeKey(rules.symbols().module(), type));
    }

    /** As a parameter is read: under the declaration the signature wrote, with what is written
     *  about it. */
    private Position read(String type) {
        return InputDomain.of(
                        List.of(new InputDomain.Parameter("x", null, Type.ref(named(type)))),
                        rules, souther.compiler.query.ReadAs.THE_COMPILATION_DOES)
                .at(TermPath.of("x"));
    }

    /** What the reading of {@code type} came to. */
    private LocalPartition partitionOf(String type) {
        return LocalInspection.of(read(type), RuleReadingContext.unshared(rules,
                souther.compiler.query.ReadAs.THE_COMPILATION_DOES));
    }

    /** A type that states cases divides the position, and no line is drawn through them. */
    @Test
    void classesAndNoLineIsADivision() {
        DeclaredMeasure found = theOneMeasure("Stage");

        assertEquals(List.of("Prospecting", "Qualified", "Won"),
                found.classes().stream().map(PartitionClass::id).toList());
        assertInstanceOf(CutEvidence.None.class, found.cuts());
    }

    /** A rule that says where the values stop divides it too, and puts no class either side of the
     *  line — everything outside it is refused at construction. */
    @Test
    void aLineAndNoClassIsADivisionToo() {
        DeclaredMeasure found = theOneMeasure("Amount");

        assertEquals(List.of(), found.classes());
        assertInstanceOf(CutEvidence.Present.class, found.cuts());
        assertTrue(found.cuts().cuts().size() >= 1);
    }

    /** And a rule that names the values it may hold divides it into those, which is the third way
     *  and was read by nothing. */
    @Test
    void theValuesARuleNamesAreADivisionAsWell() {
        DeclaredMeasure found = theOneMeasure("Gender");

        assertEquals(2, found.classes().size(), found.classes().toString());
    }

    /** The one measure the reading of {@code type} made, where it made one. */
    private DeclaredMeasure theOneMeasure(String type) {
        LocalPartition.Divided found =
                assertInstanceOf(LocalPartition.Divided.class, partitionOf(type));
        assertEquals(1, found.measures().size(),
                () -> type + " is measured at one number: " + found.measures());
        return found.measures().get(0);
    }

    /** Nothing written about the position, read to the end: the model divides it no way, and that
     *  is what licenses asking what is under it. */
    @Test
    void nothingWrittenAndReadToTheEndIsOpen() {
        assertInstanceOf(LocalPartition.Open.class, partitionOf("Plain"));
        assertInstanceOf(LocalPartition.Open.class, partitionOf("Slot"));
    }

    /**
     * A rule written here that nothing could read is open as well, and the position is not an
     * absence.
     *
     * <p>What this reading found is that nothing it read divides the position, which is the same
     * answer it gives where nothing is written at all. What keeps the absence above from being
     * claimed here is not held by this reading: the rule raises a question, and no reading answered
     * it, so the position is one nothing about the model follows from. Answered here instead, a
     * reading short of a rule that another reading took in wrote the position down as one this
     * compiler could not read.
     */
    @Test
    void aRuleNothingCouldReadLeavesAQuestionRatherThanAnAnswerHere() {
        assertInstanceOf(LocalPartition.Open.class, partitionOf("Email"));

        assertEquals(1, read("Email").unansweredQuestions().size(),
                "the rule nothing read raises a question, and nothing answered it");
        assertEquals(List.of(), read("Gender").unansweredQuestions(),
                "and a rule a reading took in raises none that stands");
    }

    /** The reading is there whichever answer it is: what the position is measured at, and what its
     *  rules leave its values, are not things only a divided position has. */
    @Test
    void theReadingIsTheSameValueWhicheverAnswerItIs() {
        for (String type : List.of("Stage", "Amount", "Plain", "Slot", "Gender", "Email")) {
            Position position = read(type);
            assertTrue(!position.numbers().isEmpty(), type + " has a number of its own");
            assertNotNull(position.reading(), type + " says which values it may hold");
            assertNotNull(position.completeness(), type + " says how much of its rules was read");
            assertNotNull(position.rulesWithoutALine(), type + " says which of its rules went unread");
        }
    }

    /** An answer that says nothing cannot be written as one that says something. */
    @Test
    void anEmptyDivisionIsNotAnAnswer() {
        assertThrows(IllegalArgumentException.class,
                () -> new LocalPartition.Divided(List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new DeclaredMeasure(new souther.compiler.inputs.NumericTerm.ValueOf(
                        TermPath.of("x")), List.of(), new CutEvidence.None(),
                        souther.compiler.check.NarrowedBounds.NOTHING),
                "and neither can a measure of a number nothing measured");
    }

    /**
     * A class of one number cannot be filed under another.
     *
     * <p>The negative control for what the reading above is for. Every class the declarations state
     * is a class of what stands at the position, and a reading that handed one to the measure of a
     * count taken of that position would be labelling it with a number it was never about — which
     * is what a measure holding both would have to do to hold them.
     */
    @Test
    void aClassIsNotFiledUnderANumberItIsNotAClassOf() {
        DeclaredMeasure found = theOneMeasure("Gender");
        souther.compiler.inputs.NumericTerm.FromOnePosition length =
                read("Gender").numbers().stream()
                        .filter(souther.compiler.inputs.NumericTerm.TakenOf.class::isInstance)
                        .findFirst().orElseThrow();

        assertThrows(IllegalArgumentException.class,
                () -> new DeclaredMeasure(length, found.classes(), found.cuts(),
                        found.narrowed()));
    }

    /**
     * There is no way to make one of these except by deriving it.
     *
     * <p>What holds the sentence up. A conclusion its reading does not support — an open position
     * off a reading short of the rules, classes said to be read in full off one that was not — is
     * not refused when somebody writes it; there is nowhere to write it. Both halves are asserted,
     * because the discipline is now split across them: the reading is a value nothing outside the
     * package that makes it can construct, and the conclusion has one derivation and no other way
     * in. Held here because it is a property of the boundary rather than of any one value: a
     * constructor added later would put back the discipline this replaced, and nothing else in the
     * suite would notice.
     */
    @Test
    void nothingButTheDerivationMakesOne() {
        assertEquals(List.of(), java.util.Arrays.stream(LocalInspection.class.getConstructors())
                .map(java.lang.reflect.Constructor::toString).toList(),
                "a conclusion anybody can make is one anybody can make disagree with its reading");
        assertEquals(List.of("of"),
                java.util.Arrays.stream(LocalInspection.class.getDeclaredMethods())
                        .filter(each -> !java.lang.reflect.Modifier
                                .isPrivate(each.getModifiers()))
                        .map(java.lang.reflect.Method::getName).sorted().toList(),
                "one derivation, and it is the one that reads the position");
        assertTrue(Position.class.isSealed(),
                "a reading anybody can implement is one anybody can answer with");
        for (Class<?> each : Position.class.getPermittedSubclasses()) {
            assertEquals(0, each.getConstructors().length,
                    each.getSimpleName() + " can be written down outside the reading that makes it");
        }
    }

    /** And neither can no lines at all be written as lines. */
    @Test
    void noCutsIsNotAPresentCut() {
        assertThrows(IllegalArgumentException.class,
                () -> new CutEvidence.Present(List.of(),
                        new souther.compiler.check.ProjectionEvidence.CertifiedExact(
                                new souther.compiler.numeric.ProjectionCertificate
                                        .ByBoxAndClosedDifferences())));
    }
}
