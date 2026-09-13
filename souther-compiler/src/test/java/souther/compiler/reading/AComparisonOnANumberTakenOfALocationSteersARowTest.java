package souther.compiler.reading;

import org.junit.jupiter.api.Test;

import souther.compiler.check.NumericMeasures;
import souther.compiler.check.RuleReadingSource;
import souther.compiler.check.RuleReadings;
import souther.compiler.check.Symbols;
import souther.compiler.core.Core;
import souther.compiler.inputs.InputDomain;
import souther.compiler.inputs.NumericTerm;
import souther.compiler.inputs.TermPath;
import souther.compiler.partition.FixtureTemplate;
import souther.compiler.partition.Generator;
import souther.compiler.query.Adequacy;
import souther.compiler.query.Bodies;
import souther.compiler.query.Compilation;
import souther.compiler.types.Type;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A comparison on a number taken of a location settles a decision, and a row is steered by it.
 *
 * <p>Whether a value brings a comparison out a given way is a question about the number it compares:
 * the rules leave that number a run of values, and a way stands where some value of the run falls on
 * the side the way needs. Read off the shape of the operands instead, {@code String.length(slot.c)
 * <= 3} is a call against a literal and nothing about it varies — so no way was held, no decision was
 * named, and the arm behind the guard was left with no row and nothing saying why.
 *
 * <p>What a row for such an arm needs beside the decision is a value to write, and that is a separate
 * capability with a separate answer. The last case below is the one where the decision is settled and
 * the value cannot be written: the combination is asked for and comes back as one nothing composes,
 * which is what an author can act on. Run together, the way to get there would be to leave the
 * decision unnamed, which is where this started.
 */
class AComparisonOnANumberTakenOfALocationSteersARowTest {

    private static final String TAKEN = """
            module example.taken

            data Yes
            data No
            data Answer = Yes | No

            data Slot = { c: String }
            data Flag = Off | On

            let base = Slot { c = "abcde" }

            behavior gate : (slot: Slot, a: Flag, b: Flag) -> Answer

            let one (f: Flag): Int =
                match f with
                    | Off -> 0
                    | On -> 1

            let gate (slot, a, b) = {
                guard String.length(slot.c) <= 3 else No
                guard one(a) + one(b) >= 2 else No
                Yes
            }

            example gate
                | "wide" : (base, Off, Off) -> No
            """;

    /** The same body with the number written at the position itself, which is where a row was always
     *  steered. */
    private static final String OWN = TAKEN
            .replace("module example.taken", "module example.own")
            .replace("data Slot = { c: String }", "data Slot = { n: Int }")
            .replace("let base = Slot { c = \"abcde\" }", "let base = Slot { n = 5 }")
            .replace("guard String.length(slot.c) <= 3", "guard slot.n <= 3");

    /** A number a row cannot be written for: no fixture here writes a time by its minute. */
    private static final String MINUTE = TAKEN
            .replace("module example.taken", "module example.minute")
            .replace("data Slot = { c: String }", "data Slot = { at: Time }")
            .replace("let base = Slot { c = \"abcde\" }", "let base = Slot { at = Time(\"09:00\") }")
            .replace("guard String.length(slot.c) <= 3", "guard Time.minute(slot.at) >= 30");

    /**
     * The comparison is a decision the reading names, said of the number it is about.
     *
     * <p>The first thing the arms below stand on: a way in that names no decision steers no row, so
     * this is what would be missing if the comparison were read off its operands' shapes.
     */
    @Test
    void aComparisonOnATakenNumberIsADecisionTheReadingNames() {
        Read read = read(TAKEN);
        List<Condition.Side> sides = read.sides();

        assertEquals(List.of(read.lengthOf("slot", "c")),
                sides.stream().map(Condition.Side::at).distinct().toList(),
                "the number the comparison is about, and the decision is said of it");
        assertEquals(2, sides.size(), "both ways of it: the guard holds, and the guard fails");
    }

    /**
     * The same decision where the location is reached through a name the body bound.
     *
     * <p>What a name reads is not a fact about the node that reads it, so the reading admitting the
     * comparison is scoped like the one naming it: inside {@code let s = slot.c} the length of
     * {@code s} is the length of {@code slot.c}, and a reading that stayed outside the binding finds
     * no number named there. Held to the body's root, this comparison went unheld while the same one
     * written without the binding was held — a {@code let} changing what the body does.
     */
    @Test
    void aNumberReachedThroughABindingIsTheSameDecision() {
        String bound = TAKEN
                .replace("module example.taken", "module example.bound")
                .replace("    guard String.length(slot.c) <= 3 else No", """
                            let s = slot.c
                            guard String.length(s) <= 3 else No""");
        Read read = read(bound);

        assertEquals(List.of(read.lengthOf("slot", "c")),
                read.sides().stream().map(Condition.Side::at).distinct().toList(),
                "the number is the location's, whichever name the body reached it through");
    }

    /**
     * The same decision where the location is reached through the name an arm binds.
     *
     * <p>The other place what a name means changes. Inside {@code | Some c -> } the name stands for
     * the value matched read as that case, and the reading of the input already says so; a reading
     * of the comparisons that followed a {@code let} and not an arm was scoped like half of it.
     */
    @Test
    void aNumberReachedThroughAnArmsBindingIsTheSameDecision() {
        String inArm = """
                module example.arm

                data Yes
                data No
                data Answer = Yes | No

                data Named = { c: String }
                data Empty
                data Slot = Named | Empty

                behavior gate : (slot: Slot) -> Answer
                let gate (slot) =
                    match slot with
                        | Named as s -> if String.length(s.c) <= 3 then Yes else No
                        | Empty -> No
                """;
        Read read = read(inArm);
        souther.compiler.types.TypeSymbol named = souther.compiler.types.TypeSymbols.declared(
                new souther.compiler.types.TypeKey("example.arm", "Named"));
        // A leaf is a case that covers itself, which is what the checker's resolution of the arm
        // says, so selecting it narrows the position to that one distinction.
        TermPath underNamed = TermPath.of("slot").refine(
                souther.compiler.inputs.Refinement.of(souther.compiler.types.ResolvedCase.of(
                        souther.compiler.types.CaseSelector.direct(named), List.of(named))))
                .then("c");

        assertEquals(List.of(read.lengthAt(underNamed)),
                read.sides().stream().map(Condition.Side::at).distinct().toList(),
                "the number is the length of the field under the case the arm selects");
    }

    /**
     * Which side of the operator the number was written on is not part of what it says.
     *
     * <p>Read as written, {@code 3 >= String.length(slot.c)} has the number on the right; read
     * into what it says, it is the comparison above turned round. Held to the shape, a second
     * spelling of one rule would be a second rule.
     */
    @Test
    void theNumberIsTheSameWhicheverSideItIsWrittenOn() {
        String reversed = TAKEN.replace("module example.taken", "module example.reversed")
                .replace("guard String.length(slot.c) <= 3", "guard 3 >= String.length(slot.c)");

        assertEquals(armsAnsweredIn(TAKEN), armsAnsweredIn(reversed));
        assertEquals(read(TAKEN).sides().stream().map(Condition.Side::at).toList(),
                read(reversed).sides().stream().map(Condition.Side::at).toList());
    }

    /**
     * A comparison on an ordered enumeration steers a row the same way.
     *
     * <p>The one kind of position whose classes are the cases and whose lines are at places on the
     * order the cases are counted on. The cell for the arm behind the guard is found by asking each
     * case where it lies, and a case that could not be asked left the line in no class — so the
     * arms behind the guard were left without rows, while the partition showed every case.
     */
    @Test
    void aComparisonOnAnOrderedEnumerationSteersARow() {
        String ordered = TAKEN
                .replace("module example.taken", "module example.ordered")
                .replace("data Slot = { c: String }", """
                        data Low
                        data High
                        data Slot = Low | High""")
                .replace("let base = Slot { c = \"abcde\" }", "let base = High")
                .replace("guard String.length(slot.c) <= 3", "guard slot < High");

        assertEquals(armsAnsweredIn(OWN), armsAnsweredIn(ordered),
                "the arms behind the guard are answered as they are for a number");
    }

    /** And a row is offered for an arm behind it, the way one is where the number stands at the
     *  position. */
    @Test
    void aRowIsOfferedForAnArmBehindSuchAComparison() {
        assertFalse(armsAnsweredIn(TAKEN).isEmpty(),
                "the arms behind the guard are answered by rows");
        assertEquals(armsAnsweredIn(OWN), armsAnsweredIn(TAKEN),
                "the same body reading a number of the position answers the same arms");
    }

    /**
     * A number nothing writes a value for is narrowed all the same, and what is missing is said.
     *
     * <p>Both halves. The combination asked for settles the guard's number together with the flags
     * the second guard reads, which is a cell and so a decision the reading named; and what comes
     * back is that nothing composes a value for it, which is the generator's answer about writing a
     * time by its minute. Asked of the minute's class alone, this would pass on the class search,
     * which reaches that class whether or not the comparison was ever a decision.
     */
    @Test
    void aNumberNothingComposesAValueForIsStillNarrowed() {
        Adequacy.Filling filling = generated(MINUTE);

        List<Generator.UnresolvedCombination> cells = filling.composed().unresolved().stream()
                .filter(each -> each.classes().stream()
                                .anyMatch(cls -> cls.contains("30 <= x <= 59"))
                        && each.classes().stream().anyMatch(cls -> cls.startsWith("a=")))
                .toList();

        assertFalse(cells.isEmpty(),
                () -> "the guard's number is settled beside the flags: "
                        + filling.composed().unresolved());
        assertTrue(cells.stream().allMatch(each -> each.reason()
                        == Generator.UnresolvedCombination.Reason.NOTHING_COMPOSES_ONE),
                () -> "and what is short is the value, not the decision: " + cells);
    }

    /**
     * A rule over a number no one position answers is no decision of any position, as before.
     *
     * <p>The other side of what admission is now asked of. {@code Date.daysBetween} is a number of
     * two locations, so no position holds the value it varies over and no class of one is what a row
     * would be steered to. Nothing here widened that: what a comparison is about is the same reading
     * it always was, and this is the case where that reading answers with nothing.
     */
    @Test
    void aRuleOverANumberNoOnePositionAnswersIsNoDecision() {
        String spread = """
                module example.spread

                data Yes
                data No
                data Answer = Yes | No

                data Slot = { from: Date, to: Date }

                behavior gate : (slot: Slot) -> Answer
                let gate (slot) = {
                    guard Date.daysBetween(slot.from, slot.to) >= 3 else No
                    Yes
                }
                """;

        assertEquals(List.of(), read(spread).sides(),
                "no position answers the number, so no decision is said of one");
    }

    /**
     * Which arms rows were offered for, by the number the plan gave each.
     *
     * <p>By number, because what two of these are held against each other is two compiles of two
     * sources. Each has a numbering of its own and an address of one is an address of nothing in
     * the other, so what "the same arms" can mean between them is the numbers — which is what the
     * two sources being one body up to a spelling makes a claim about.
     */
    private static List<Integer> armsAnsweredIn(String source) {
        return generated(source).composed().rows().stream()
                .flatMap(row -> row.purposes().stream())
                .filter(Generator.Purpose.ForAnArm.class::isInstance)
                .map(each -> ((Generator.Purpose.ForAnArm) each).probe().raw())
                .sorted().toList();
    }

    private static Adequacy.Filling generated(String source) {
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        Map<String, Adequacy.Filling> all =
                Adequacy.generatedOf(compilation.db(), compilation.modules().get(0));
        assertNotNull(all, "the model under test compiles");
        Adequacy.Filling filling = all.get("gate");
        assertNotNull(filling, "the behavior under test is generated for");
        return filling;
    }

    /** One model read, with the symbols a term named here is built against. */
    private record Read(CoverageRead.Read read, Symbols symbols) {

        /** Every comparison the reading named, over every way in to every arm. */
        List<Condition.Side> sides() {
            return read.arms().values().stream()
                    .filter(PathAccess.Ways.class::isInstance)
                    .flatMap(access -> ((PathAccess.Ways) access).ways().stream())
                    .flatMap(way -> way.decisions().stream())
                    .map(Decision::constrains)
                    .filter(Condition.Side.class::isInstance)
                    .map(Condition.Side.class::cast)
                    .distinct().toList();
        }

        /** The number a string's length is, built here rather than matched by how it is written. */
        NumericTerm lengthOf(String parameter, String field) {
            return lengthAt(TermPath.of(parameter).then(field));
        }

        NumericTerm lengthAt(TermPath at) {
            NumericTerm.TakenOf taken = NumericTerm.TakenOf.of(
                    NumericMeasures.takenOf(Type.STRING, souther.compiler.check.ScopedDeclarations.wrapsOf(symbols)),
                at, Type.STRING, souther.compiler.check.ScopedDeclarations.wrapsOf(symbols),
                symbols);
            assertNotNull(taken, "the length of a string is a number this compiler names");
            return taken;
        }
    }

    private static Read read(String source) {
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.answerEverything();
        String module = compilation.modules().get(0);
        Bodies.Elaborated checked = compilation.db().ask(new Bodies.Checked(module)).value();
        assertNotNull(checked, "the model under test compiles");
        Core body = checked.behaviorBodies().get("gate");
        assertNotNull(body, "the behavior under test has a body");
        RuleReadingSource rules = RuleReadings.of(compilation, module);
        InputDomain inputs = compilation.db().ask(new Adequacy.Inputs(module)).value().get("gate");
        return new Read(CoverageRead.of("gate", body,
                checked.plan(),
                inputs, rules), rules.symbols());
    }

    /** What a row for such an arm is written as, which is the value the class asks for. */
    @Test
    void theRowOfferedForTheArmHoldsAValueTheGuardAdmits() {
        List<String> rows = generated(TAKEN).composed().rows().stream()
                .filter(row -> row.purposes().stream()
                        .anyMatch(Generator.Purpose.ForAnArm.class::isInstance))
                .map(row -> String.join(", ",
                        row.inputs().stream().map(FixtureTemplate::text).toList()))
                .toList();

        assertFalse(rows.isEmpty(), "an arm behind the guard is offered a row");
        assertTrue(rows.stream().allMatch(row -> row.contains("c = \"\"")),
                () -> "each holds a string the guard lets through: " + rows);
    }
}
