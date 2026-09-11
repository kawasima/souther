package souther.compiler.partition;

import org.junit.jupiter.api.Test;

import souther.compiler.check.RuleReadingContext;
import souther.compiler.check.RuleReadingSource;
import souther.compiler.check.RuleReadings;
import souther.compiler.check.DeclaredSig;
import souther.compiler.core.Core;
import souther.compiler.inputs.InputDomain;
import souther.compiler.query.Bodies;
import souther.compiler.query.Compilation;

import java.util.List;
import java.util.Map;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * The classes of a position are the values it can hold, and evidence only ever tells them apart
 * more finely.
 *
 * <p>Two rules, and they are the same rule read in the two directions the classes can move in.
 *
 * <p>What a type declares and what a position can hold are separate facts, crossed where the
 * position is read: {@code data StageI = Stage invariant value == Qualified} declares three cases
 * and holds one, and the other two are rows nobody can write (E1903). That crossing was made
 * against the intervals alone, so the same rule written as an ordering took the cases away and the
 * same rule written as an equality did not — which is a partition that turns on how a rule is
 * spelled rather than on what it says.
 *
 * <p>And evidence arriving later refines: a body comparing a position it already has classes for
 * draws a line among them rather than replacing them. Replacement is how a class the model states
 * would come to be lost to a rule the body writes, and the loss reads as the model never having
 * stated it.
 */
class AClassIsOneThePositionCanHoldAndNothingTakesItAwayTest {

    private record Read(Compilation compilation, String module, String behavior, DeclaredSig sig,
                        RuleReadingSource rules) {}

    private static Read of(String source, String behavior) {
        return of(List.of(source), behavior, 0);
    }

    /** The same, over several modules, reading the behavior out of the {@code nth} of them. */
    private static Read of(List<String> sources, String behavior, int nth) {
        Compilation compilation = Compilation.ofSources(sources,
                souther.compiler.meta.ModulePath.EMPTY);
        compilation.answerEverything();
        String module = compilation.modules().get(nth);
        Map<String, DeclaredSig> sigs =
                compilation.db().ask(new Bodies.DeclaredSignatures(module)).value();
        RuleReadingSource rules = RuleReadings.of(compilation, module);
        return new Read(compilation, module, behavior, sigs.get(behavior), rules);
    }

    /** The classes of the one position, off the declarations alone. */
    private static List<String> declared(String source, String behavior) {
        Read read = of(source, behavior);
        return classesOf(Partitions.of(read.behavior(), InputDomain.of(read.sig(), read.rules(), souther.compiler.query.ReadAs.THE_COMPILATION_DOES), read.rules(), souther.compiler.query.ReadAs.THE_COMPILATION_DOES));
    }

    /** And the same with what the behavior's own body draws taken in. */
    private static List<String> withBody(String source, String behavior) {
        Read read = of(source, behavior);
        Bodies.Elaborated checked =
                read.compilation().db().ask(new Bodies.Checked(read.module())).value();
        assertNotNull(checked, "the model under test compiles");
        Core body = checked.behaviorBodies().get(behavior);
        assertNotNull(body, "the behavior under test has a body");
        InputDomain inputs = read.compilation().db()
                .ask(new souther.compiler.query.Adequacy.Inputs(read.module())).value()
                .get(behavior);
        GuardThresholds.Guards guards = GuardThresholds.of(behavior,
                checked.analysisBodies().get(behavior), body,
                checked.plan(), inputs, read.rules());
        Partitions.Partitioning base =
                Partitions.of(read.behavior(), inputs, read.rules(), souther.compiler.query.ReadAs.THE_COMPILATION_DOES);
        return classesOf(Partitions.withThresholds(base, inputs.quantities(read.rules()),
                guards.thresholds(),
                RuleReadingContext.unshared(read.rules(),
                        souther.compiler.query.ReadAs.THE_COMPILATION_DOES),
                guards.noLine(), guards.singled(), guards.between(),
                souther.compiler.values.Allowance.of(souther.compiler.regex.PatternPlan.Budget.OF_BEHAVIOR_DISTINCTIONS)));
    }

    /**
     * The classes of the parameter's own position, which is the first axis read.
     *
     * <p>Not the only one. A case that carries a record puts positions under the parameter, and
     * what those divide into is a question about them — asked here, a model whose sum holds a
     * record would be answered with a field's classes or with none.
     */
    private static List<String> classesOf(Partitions.Partitioning partitioning) {
        String parameter = partitioning.axes().get(0).path().head();
        return classesAt(partitioning, parameter);
    }

    /** The classes of the position at {@code path}, where a behavior has more than one. */
    private static List<String> classesAt(Partitions.Partitioning partitioning, String path) {
        return partitioning.axes().stream()
                .filter(each -> each.path().toString().equals(path))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no axis at " + path + " in "
                        + partitioning.axes()))
                .classes().stream().map(PartitionClass::id).toList();
    }

    /** The classes at {@code path}, off the declarations alone. */
    private static List<String> declaredAt(String source, String behavior, String path) {
        Read read = of(source, behavior);
        return classesAt(Partitions.of(read.behavior(), InputDomain.of(read.sig(), read.rules(), souther.compiler.query.ReadAs.THE_COMPILATION_DOES), read.rules(), souther.compiler.query.ReadAs.THE_COMPILATION_DOES),
                path);
    }

    /**
     * A case the position cannot hold is not one of its classes, whichever way the rule is written.
     *
     * <p>{@code >= Qualified} already took the case away, through the intervals. The equality says
     * the same thing about the same position and left all three, so a row was asked for at two
     * values the constructor refuses.
     */
    @Test
    void anEqualityOverCasesTakesTheOnesItRefusesAway() {
        String model = """
                module g

                data Prospecting
                data Qualified
                data Won
                data Stage = Prospecting | Qualified | Won

                data StageI = Stage
                    invariant value == Qualified

                data Accepted = { at: String }

                behavior classify : (s: StageI) -> Accepted
                """;

        assertEquals(List.of("Qualified"), declared(model, "classify"));
    }

    /** And the same of a boolean, which has two values and a rule can leave one. */
    @Test
    void anEqualityOverABooleanLeavesTheValueItNames() {
        assertEquals(List.of("true"), declared("""
                module g

                data Yes = Bool
                    invariant value == true

                data Accepted = { at: String }

                behavior classify : (y: Yes) -> Accepted
                """, "classify"));
    }

    /**
     * And the classes stay in the order the type declares them, not the order the rule names them.
     *
     * <p>The classes are the position's, and a reader lists them as the model wrote them. Ordered
     * by the rule, one partition would be written two ways depending on which value the author put
     * first — in the report, and in the rows a generator offers from it.
     */
    @Test
    void theClassesLeftAreInTheOrderTheTypeDeclaresThem() {
        assertEquals(List.of("Prospecting", "Won"), declared("""
                module g

                data Prospecting
                data Qualified
                data Won
                data Stage = Prospecting | Qualified | Won

                data StageI = Stage
                    invariant either = value == Won || value == Prospecting

                data Accepted = { at: String }

                behavior classify : (s: StageI) -> Accepted
                """, "classify"));
    }

    /**
     * A position that can hold none of its classes keeps them, rather than coming back divided no
     * way.
     *
     * <p>Nothing left is not an empty partition: it is a value nothing can build, which is refused
     * where the declaration is (E1013) and is why this model does not compile. What is asserted is
     * that the reading of it does not answer with the one sentence this whole protocol is against —
     * a position the model divides three ways, reported as one it divides no way, because the rules
     * then refuse all three.
     */
    @Test
    void aPositionThatCanHoldNoneOfItsClassesIsNotOneDividedNoWay() {
        assertEquals(List.of("Prospecting", "Qualified", "Won"), declared("""
                module g

                data Prospecting
                data Qualified
                data Won
                data Stage = Prospecting | Qualified | Won

                data StageI = Stage
                    invariant both = value == Qualified && value == Won

                data Accepted = { at: String }

                behavior classify : (s: StageI) -> Accepted
                """, "classify"));
    }

    /**
     * A case a rule refuses is not a class of the position, said as a denial as well as as a naming.
     *
     * <p>The other half of the same crossing, and the half a mixed sum reaches. Where every value
     * of a type can be written out — a boolean, a sum of unit cases — a denial is turned into the
     * values it leaves where it is read, and the crossing sees a finite set. Where they cannot, it
     * stays a denial: {@code State} has a case holding a record, so {@code /= Missing} is every
     * value but one and names none of the ones left.
     *
     * <p>A class is dropped where the rules leave it nothing, which is one rule and two proofs. A
     * finite set proves it by holding no value of the class; a denial proves it by excluding every
     * value the class holds, which is provable of a class that is one value and not of one that is
     * a record.
     */
    @Test
    void aCaseADenialRefusesIsNotAClassEither() {
        assertEquals(List.of("Present"), declared("""
                module g

                data Missing
                data Present = { note: String }
                data State = Missing | Present

                data AvailableState = State
                    invariant here = value /= Missing

                data Accepted = { at: String }

                behavior classify : (s: AvailableState) -> Accepted
                """, "classify"));
    }

    /** And a denial over a boolean leaves the other value, which is the same rule where the values
     *  can be written out. */
    @Test
    void aDenialOverABooleanLeavesTheOtherValue() {
        assertEquals(List.of("false"), declared("""
                module g

                data No = Bool
                    invariant here = value /= true

                data Accepted = { at: String }

                behavior classify : (n: No) -> Accepted
                """, "classify"));
    }

    /**
     * A denial of one value of a class that holds many takes the class away from nothing.
     *
     * <p>What the proof is for. A record case holds every value its fields can be given, so a rule
     * refusing one of them refuses none of the class — and a crossing that dropped it on a match
     * would take away a class the model states and the position can reach.
     */
    @Test
    void aDenialOfOneValueDoesNotTakeAwayAClassThatHoldsMany() {
        assertEquals(List.of("Missing", "Present"), declaredAt("""
                module g

                data Missing
                data Present = { note: String }
                data State = Missing | Present

                data Held = { state: State, note: String }
                    invariant here = note /= "x"

                data Accepted = { at: String }

                behavior classify : (h: Held) -> Accepted
                """, "classify", "h.state"));
    }

    /**
     * And a case another module keeps to itself is refused by the same rule.
     *
     * <p>What a class means and whether a row for it can be written down are two answers, and only
     * the second turns on which module is reading. A case this module cannot name is still one
     * value, so a rule denying it still refuses the whole class — and the class going missing from
     * the denominator is the same fact wherever the reader stands.
     *
     * <p>Written as one branch, the two came back together: a class built for a case nothing here
     * can write was built without saying what it holds, so the denial had nothing to prove itself
     * against and the case stayed in the denominator of every module but the one that declared it.
     */
    @Test
    void aCaseAnotherModuleKeepsToItselfIsRefusedByTheSameRule() {
        assertEquals(List.of("Present"), declaredAt(List.of("""
                module lib exposing ( State, Present, AvailableState )

                data Missing
                data Present = { note: String }
                data State = Missing | Present

                data AvailableState = State
                    invariant here = value /= Missing
                """, """
                module app exposing ( classify, Accepted )

                import lib ( AvailableState )

                data Accepted = { at: String }

                behavior classify : (s: AvailableState) -> Accepted
                """), "classify", 1, "s"));
    }

    /** The classes at {@code path} of a behavior in the {@code nth} module. */
    private static List<String> declaredAt(List<String> sources, String behavior, int nth,
                                           String path) {
        Read read = of(sources, behavior, nth);
        return classesAt(Partitions.of(read.behavior(), InputDomain.of(read.sig(), read.rules(), souther.compiler.query.ReadAs.THE_COMPILATION_DOES), read.rules(), souther.compiler.query.ReadAs.THE_COMPILATION_DOES),
                path);
    }

    /** A case the rule leaves is still a class, so the crossing takes away only what it must. */
    @Test
    void aRuleThatRefusesNothingLeavesEveryCase() {
        assertEquals(List.of("Prospecting", "Qualified", "Won"), declared("""
                module g

                data Prospecting
                data Qualified
                data Won
                data Stage = Prospecting | Qualified | Won

                data StageI = Stage

                data Accepted = { at: String }

                behavior classify : (s: StageI) -> Accepted
                """, "classify"));
    }

    /**
     * How much of the rules the classes were derived under survives every phase.
     *
     * <p>Nothing reads it yet, which is the whole reason to hold it here: a phase that dropped it
     * would leave every measure saying what it says today, and the loss would show up the day
     * somebody asks whether a class is one a value can be built for. The classes are the values the
     * model singled out either way; what the answer beside them says is that a rule went unread and
     * may yet refuse one of them.
     */
    @Test
    void howMuchWasReadSurvivesTheBodyAsWellAsTheDeclarations() {
        String model = """
                module g

                data Code = String
                    invariant enumerated = value == "a" || value == "b"
                    invariant shape = UNREAD

                data Accepted = { at: String }
                data Refused = { at: String }

                behavior classify : (c: Code) -> Accepted | Refused
                    constructs Accepted, Refused

                let classify (c) =
                    if String.length(c.value) == 1 then Accepted { at = "x" }
                    else Refused { at = "y" }
                """.replace("UNREAD", souther.compiler.ARuleNoReadingTakesIn.about("value"));
        Read read = of(model, "classify");
        Partitions.Partitioning base =
                Partitions.of(read.behavior(), InputDomain.of(read.sig(), read.rules(), souther.compiler.query.ReadAs.THE_COMPILATION_DOES), read.rules(), souther.compiler.query.ReadAs.THE_COMPILATION_DOES);

        assertFalse(base.unanswered().isEmpty(),
                "a rule about this position was taken in by nothing, and the classes were made"
                        + " anyway");
        assertEquals(base.unanswered(), withThresholdsOf(read, base).unanswered(),
                "what a body draws does not change which of the position's rules stand unanswered,"
                        + " nor which number each of them is about — a body's rules re-point the"
                        + " axis at another term, and a question carried on the axis would have"
                        + " gone with it");
    }

    /** The same partitioning, with what the behavior's body draws taken in. */
    private static Partitions.Partitioning withThresholdsOf(Read read,
                                                            Partitions.Partitioning base) {
        Bodies.Elaborated checked =
                read.compilation().db().ask(new Bodies.Checked(read.module())).value();
        assertNotNull(checked, "the model under test compiles");
        Core body = checked.behaviorBodies().get(read.behavior());
        assertNotNull(body, "the behavior under test has a body");
        GuardThresholds.Guards guards = GuardThresholds.of(read.behavior(),
                checked.analysisBodies().get(read.behavior()), body,
                checked.plan(),
                read.compilation().db()
                        .ask(new souther.compiler.query.Adequacy.Inputs(read.module())).value()
                        .get(read.behavior()),
                read.rules());
        return Partitions.withThresholds(base,
                InputDomain.of(read.sig(), read.rules(), souther.compiler.query.ReadAs.THE_COMPILATION_DOES)
                        .quantities(read.rules()),
                guards.thresholds(),
                RuleReadingContext.unshared(read.rules(),
                        souther.compiler.query.ReadAs.THE_COMPILATION_DOES),
                guards.noLine(), guards.singled(), guards.between(),
                souther.compiler.values.Allowance.of(souther.compiler.regex.PatternPlan.Budget.OF_BEHAVIOR_DISTINCTIONS));
    }

    /**
     * And a line drawn on an enumeration supplies no classes, even where its rules left none.
     *
     * <p>Two conditions and two reasons. A position that has classes keeps them, because evidence
     * arriving later refines. A position whose values are an enumeration's cases takes no classes
     * from a line either way: the cases divide it whether or not the rules leave any of them
     * standing, and ranges rebuilt from a cut would be a partition of a position into things that
     * are not its values.
     *
     * <p>Where the two would come apart is a position whose rules leave no case at all. Read as "no
     * classes, so a line may supply some", the report would divide an enumeration into ranges of the
     * count its cases are ordered by. No body reaches that reading now: a declaration whose rules
     * leave its position no value is refused where the declaration is, so there is no elaborated
     * body for a line to be drawn in. What is asserted here is that refusal, because it is what
     * closes the branch — and a change that admitted such a declaration again would have to answer
     * the crossing question this class is about.
     */
    @Test
    void noBodyDrawsALineOnAPositionWhoseRulesLeftNoCase() {
        Compilation compilation = Compilation.ofSource("""
                module g

                data Prospecting
                data Qualified
                data Won
                data Stage = Prospecting | Qualified | Won

                data StageI = Stage
                    invariant past = value > Won

                data Accepted = { at: String }
                data Refused = { at: String }

                behavior classify : (s: StageI) -> Accepted | Refused
                    constructs Accepted, Refused

                let classify (s) =
                    if s.value < Qualified then Accepted { at = "x" }
                    else Refused { at = "y" }
                """, "Main");
        compilation.answerEverything();

        assertEquals(List.of("E1013"), compilation.diagnostics().values().stream()
                        .flatMap(List::stream)
                        .map(each -> each.diagnostic().code())
                        .toList(),
                "`value > Won` names no case, so `StageI` has no value to be built");
    }

    /**
     * A line a body draws on an enumeration leaves the cases the rules left standing.
     *
     * <p>The reachable half of the same crossing. {@code value > Prospecting} leaves two of the
     * three, which are classes of the position; the line the body draws through them divides values
     * they already tell apart, and ranges rebuilt from it would replace the cases with the counts
     * they are ordered by.
     */
    @Test
    void aLineABodyDrawsOnAnEnumerationLeavesTheCasesItsRulesLeft() {
        String model = """
                module g

                data Prospecting
                data Qualified
                data Won
                data Stage = Prospecting | Qualified | Won

                data StageI = Stage
                    invariant past = value > Prospecting

                data Accepted = { at: String }
                data Refused = { at: String }

                behavior classify : (s: StageI) -> Accepted | Refused
                    constructs Accepted, Refused

                let classify (s) =
                    if s.value < Won then Accepted { at = "x" }
                    else Refused { at = "y" }
                """;

        assertEquals(List.of("Qualified", "Won"), declared(model, "classify"));
        assertEquals(declared(model, "classify"), withBody(model, "classify"),
                "a line supplies no classes to a position whose cases are already its classes");
    }

    /**
     * A line a body draws on a position that already has classes leaves them where they are.
     *
     * <p>The classes the model states are finer than the ranges either side of a line — every value
     * of the position is one of them — so ranges rebuilt from the line would take away distinctions
     * the model already made. The line is still a line and still owes its rows.
     */
    @Test
    void anOrderingInABodyDoesNotReplaceTheClassesTheModelStates() {
        String model = """
                module g

                data Small = Int
                    invariant value == 1 || value == 2

                data Accepted = { at: String }
                data Refused = { at: String }

                behavior classify : (n: Small) -> Accepted | Refused
                    constructs Accepted, Refused

                let classify (n) =
                    if n.value >= 2 then Accepted { at = "x" } else Refused { at = "y" }
                """;

        assertEquals(List.of("1", "2"), declared(model, "classify"));
        assertEquals(List.of("1", "2"), withBody(model, "classify"));
    }

    /** And neither does a body singling a value out: the value already has a class of its own, and
     *  the rest of the position is the other classes rather than one lump of everything else. */
    @Test
    void singlingAValueOutInABodyDoesNotReplaceThemEither() {
        String model = """
                module g

                data Small = Int
                    invariant value == 1 || value == 2

                data Accepted = { at: String }
                data Refused = { at: String }

                behavior classify : (n: Small) -> Accepted | Refused
                    constructs Accepted, Refused

                let classify (n) =
                    if n.value == 1 then Accepted { at = "x" } else Refused { at = "y" }
                """;

        assertEquals(List.of("1", "2"), withBody(model, "classify"));
    }
}
