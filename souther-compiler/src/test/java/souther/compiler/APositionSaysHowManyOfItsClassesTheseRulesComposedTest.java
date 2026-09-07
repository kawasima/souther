package souther.compiler;

import souther.compiler.diag.SourceNameResolver;
import souther.compiler.meta.ModulePath;
import souther.compiler.query.Adequacy;
import souther.compiler.query.Compilation;
import souther.compiler.report.AdequacyReport;
import souther.compiler.report.ReaderDisposition;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A position divided into more classes than the behavior's rules composed says both numbers.
 *
 * <p>The cases of a sum are classes of the position whether or not a rule of this behavior looks at
 * them. So a behavior may take five cases and decide one two-way question with them: every measure
 * over the position comes back full, and the combinations those classes take part in stay unknown,
 * because no row can reach a combination the model has and the behavior never tells apart. A reader
 * handed that count and nothing else reads it as work owed and writes a row that buys a combination
 * and no evidence.
 *
 * <p><b>The rules that composed the classes, and not the lines the behavior draws.</b> A line is
 * where a row is owed at either side of it; what put a value in a class is the axis's own answer,
 * and the two part exactly where this sentence is about — a sum whose cases nothing compares is
 * divided into classes by nobody's rule and has no line either.
 *
 * <p>Neither number is a finding and no row answers either. Whether to narrow the input, split the
 * behavior, or leave it as a value this one passes through is the author's, and this compiler knows
 * none of it: what it can say is what it worked out.
 */
class APositionSaysHowManyOfItsClassesTheseRulesComposedTest {

    /**
     * Five cases the behavior never asks about, and a line drawn somewhere else.
     *
     * <p>The strongest form of the shape, and the one a guard against it would have to allow: the
     * rules compose none of the position's classes at all.
     */
    @Test
    void aSumNoRuleHereLooksAtSaysTheRulesComposedNoneOfIt() {
        String human = report("""
                module example.untouched

                data TooShort
                data TooLong
                data WrongCase
                data Repeated
                data Banned
                data Refusal = TooShort | TooLong | WrongCase | Repeated | Banned

                data Ok = { n: Int }

                behavior judge : (why: Refusal, n: Int) -> Ok
                    constructs Ok

                let judge (why, n) = {
                    guard n > 10 else Ok { n = 0 }
                    Ok { n = 1 }
                }

                example judge
                    | (TooShort, 5)  -> Ok { n = 0 }
                    | (TooLong, 50)  -> Ok { n = 1 }
                """);

        assertTrue(human.contains("holds 5 classes and this behavior's rules compose 0 of them"),
                () -> "the cases are classes and no rule here composed one of them: " + human);
    }

    /**
     * And one the rules compose every class of says nothing.
     *
     * <p>The half without which the line above would be one this report writes about every
     * position: a number cut where its rules cut it has as many classes as they composed, and there
     * is nothing here for an author to weigh.
     */
    @Test
    void aPositionTheRulesComposeEveryClassOfSaysNothing() {
        String human = report("""
                module example.composed

                data Ok = { n: Int }

                behavior judge : (n: Int) -> Ok
                    constructs Ok

                let judge (n) = {
                    guard n > 10 else Ok { n = 0 }
                    Ok { n = 1 }
                }

                example judge
                    | (5)  -> Ok { n = 0 }
                    | (50) -> Ok { n = 1 }
                """);

        assertFalse(human.contains("this behavior's rules compose"),
                () -> "its rules composed what it is divided into: " + human);
    }

    /**
     * And the count of what no row reaches says that no row is owed there.
     *
     * <p>The sentence #1444 is about. Every measure over the model below comes back full and a
     * number in the middle says some combinations are unknown; a reader who takes that as work to
     * do writes a row, moves the number by one, and buys no evidence with it. What stops that is
     * said where the number is.
     */
    @Test
    void whatNoRowReachesSaysNobodyIsOwedARowThere() {
        String human = report("""
                module example.owed

                data TooShort
                data TooLong
                data WrongCase
                data Refusal = TooShort | TooLong | WrongCase

                data Ok = { n: Int }

                behavior judge : (why: Refusal, n: Int) -> Ok
                    constructs Ok

                let judge (why, n) = {
                    guard n > 10 else Ok { n = 0 }
                    Ok { n = 1 }
                }

                example judge
                    | (TooShort, 5)  -> Ok { n = 0 }
                    | (TooLong, 50)  -> Ok { n = 1 }
                """);

        assertTrue(human.contains("unknown; no row is owed at one"),
                () -> "the count says nobody is behind on it: " + human);
    }

    /**
     * And what a reader is left with there is a decision, at the position.
     *
     * <p>The other half of the sentence: the report says the two numbers, and what follows from
     * them is that somebody weighs whether this behavior needs the distinction. Not that they
     * narrow anything — a value passed through untouched is as ordinary as an input wider than it
     * needs to be — and not a row, since nobody is owed one at a combination.
     */
    @Test
    void aBehaviorWiderThanItSeparatesLeavesADecisionAtThePosition() {
        var compilation = compiled("""
                module example.left

                data TooShort
                data TooLong
                data WrongCase
                data Refusal = TooShort | TooLong | WrongCase

                data Ok = { n: Int }

                behavior judge : (why: Refusal, n: Int) -> Ok
                    constructs Ok

                let judge (why, n) = {
                    guard n > 10 else Ok { n = 0 }
                    Ok { n = 1 }
                }

                example judge
                    | (TooShort, 5)  -> Ok { n = 0 }
                    | (TooLong, 50)  -> Ok { n = 1 }
                """);
        var partition = compilation.db()
                .ask(new Adequacy.Coverage("example.left")).value().get("judge");

        assertInstanceOf(ReaderDisposition.ReconsiderWhatThisBehaviorNeedsToDistinguish.class,
                ReaderDisposition.of(partition.pairs(), partition.axes()),
                () -> "a position it takes wider than it separates: " + partition.pairs());
    }

    /**
     * And where every position is divided as far as its rules divide it, nothing further.
     *
     * <p>Without this the arm above would be one this report reaches for whenever a combination is
     * unknown, which is most models: what the rows happen not to sit in is not the same as what
     * they cannot reach.
     */
    @Test
    void combinationsTheRowsMerelyMissLeaveNothingFurther() {
        var compilation = compiled("""
                module example.missed

                data Ok = { n: Int }

                behavior judge : (a: Int, b: Int) -> Ok
                    constructs Ok

                let judge (a, b) = {
                    guard a > 10 else Ok { n = 0 }
                    guard b > 10 else Ok { n = 1 }
                    Ok { n = 2 }
                }

                example judge
                    | (5, 5)   -> Ok { n = 0 }
                    | (50, 50) -> Ok { n = 2 }
                """);
        var partition = compilation.db()
                .ask(new Adequacy.Coverage("example.missed")).value().get("judge");

        assertInstanceOf(ReaderDisposition.Settled.class,
                ReaderDisposition.of(partition.pairs(), partition.axes()),
                () -> "every position is divided by its own rules: " + partition.pairs());
    }

    private static Compilation compiled(String model) {
        return Compiler.analyzedModules(List.of(model), ModulePath.EMPTY, new ArrayList<>(),
                Adequacy.Asked.fullReport());
    }

    private static String report(String model) {
        List<souther.compiler.diag.Located> warnings = new ArrayList<>();
        Compilation compilation = Compiler.analyzedModules(List.of(model), ModulePath.EMPTY,
                warnings, Adequacy.Asked.fullReport());
        return AdequacyReport.of(compilation).human(SourceNameResolver.identity());
    }
}
