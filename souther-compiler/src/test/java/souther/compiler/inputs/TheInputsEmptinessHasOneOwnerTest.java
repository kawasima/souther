package souther.compiler.inputs;

import org.junit.jupiter.api.Test;

import souther.compiler.check.DeclaredSig;
import souther.compiler.check.RuleReadingSource;
import souther.compiler.check.RuleReadings;
import souther.compiler.check.Emptiness;
import souther.compiler.check.FieldDomains;
import souther.compiler.numeric.LinearForm;
import souther.compiler.numeric.Rel;
import souther.compiler.values.AdmissibleValues;
import souther.compiler.query.Bodies;
import souther.compiler.query.Compilation;
import souther.compiler.query.ReadAs;

import java.lang.reflect.Method;
import java.util.List;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Whether anything is left of an input is answered in one place.
 *
 * <p>It used to be answered in two. Each parameter's reading was asked first and the rules of the
 * whole input second, and the numbers were in both — the second is built by renaming the first's and
 * meeting them, so a numeric contradiction inside one parameter was proved twice over and the first
 * answer won because it was asked first. Two readings of one question, one of them strictly weaker,
 * and the ordering deciding between them.
 *
 * <p>So the parameters supply rules and answer nothing. What is checked here is that every shape of
 * contradiction — whichever domain holds it, whether it is inside one parameter or between two —
 * comes back from the same reading, and that there is no second reading left to ask.
 */
class TheInputsEmptinessHasOneOwnerTest {

    /**
     * A contradiction neither the numbers nor the ordering has a word for.
     *
     * <p>Held by the values and by the predicates, both of them, which is the arrangement and not a
     * duplication: an equality is a set of admissible values and is a predicate about a position,
     * and each domain abstracts it in a way that is safe on its own. So dropping either of them from
     * the conjunction leaves the other answering, and it takes dropping both to lose this.
     */
    @Test
    void aContradictionNoNumberOrOrderingHasAWordFor() {
        assertEquals(new Emptiness.ConflictingRules(), why("""
                module example.values

                data Tag = String
                    invariant both = value == "A" && value /= "A"

                data P = { tag: Tag }

                data Taken

                behavior take : (p: P) -> Taken
                """));
    }

    /**
     * A contradiction the ordering holds, and it names the place.
     *
     * <p>Over a date, which the numbers have no word for at all — so this is the ordering answering
     * on its own, and the place is one only it can spell.
     */
    @Test
    void aContradictionInTheOrderingOfOneParameterNamesItsPosition() {
        assertEquals(new Emptiness.AtAField(new Emptiness.AtAField.Where.In("p.when"),
                        new Emptiness.EmptyOrderedInterval()), why("""
                module example.ordered

                data Day = Date
                    invariant crossed = value >= Date("2026-01-01") && value < Date("2020-01-01")

                data P = { when: Day }

                data Taken

                behavior take : (p: P) -> Taken
                """));
    }

    /**
     * A contradiction only the numbers can hold, between two positions of one parameter.
     *
     * <p>Nothing names a place. An ordering bounds one position against a written value and cannot
     * say that two of them cross, so there is no position the rules leave empty — and the proof says
     * so rather than reaching for the parameter to stand in for one.
     */
    @Test
    void aRelationBetweenTwoPositionsOfOneParameter() {
        assertEquals(new Emptiness.ConflictingRules(), why("""
                module example.related

                data P = { x: Int, y: Int }
                    invariant crossed = x > y && y > x

                data Taken

                behavior take : (p: P) -> Taken
                """));
    }

    /**
     * And a contradiction between two parameters, which no reading of either can see.
     *
     * <p>The rule relating them is one a caller took in rather than one a declaration wrote, because
     * a declaration is written about one value and has no way to name two. That is the shape #911
     * puts a body's conditions into, and it is why the whole input has to be the one that answers:
     * asked of either parameter's reading, both are perfectly satisfiable.
     */
    @Test
    void aRelationBetweenTwoParameters() {
        Read read = read("""
                module example.two

                data P = { x: Int }
                    invariant atLeast = x >= 1

                data Q = { y: Int }
                    invariant atLeast = y >= 1

                data Taken

                behavior take : (p: P, q: Q) -> Taken
                """, "take");
        Quantities asked = read.inputs().quantities(read.rules());

        // Neither parameter's rules leave nothing, and neither can be told about the other.
        assertTrue(asked.emptiness().isEmpty());

        // x + y <= 0, which no declaration could have written and which nothing but the whole input
        // can hold against the two floors.
        Map<NumericTerm, BigDecimal> coefs = new LinkedHashMap<>();
        coefs.put(new NumericTerm.ValueOf(TermPath.of("p").then("x")), BigDecimal.ONE);
        coefs.put(new NumericTerm.ValueOf(TermPath.of("q").then("y")), BigDecimal.ONE);
        SearchRegion crossed = asked.region().assuming(
                new LinearForm<>(BigDecimal.ZERO, coefs), Rel.LE);

        assertEquals(Optional.of(new EmptyInput.ProvedByTheRules(new Emptiness.ConflictingRules())),
                crossed.emptiness());
    }

    /**
     * And saying them together does not multiply them.
     *
     * <p>One reading per parameter, each over positions the others do not name. A conjunction of
     * readings is a union of products, so met into one it distributes — ten parameters of a record
     * whose clauses leave two alternatives are a thousand and twenty-four alternatives, and the
     * budget that admitted each reading was counted per declaration and says nothing about their
     * conjunction. Held apart, thirteen parameters are thirteen readings of two.
     *
     * <p>Measured as the size of what is held rather than as the time it takes. A time is a fact
     * about the machine it was taken on; what went wrong here is that a representation grew as the
     * product of the parameters, and that is what this reads.
     */
    @Test
    void theReadingsOfManyParametersAreNotMultipliedTogether() {
        StringBuilder source = new StringBuilder("""
                module example.many

                data P = { a: String, b: String }
                    invariant either = (a == "x" && b == "y") || (a == "p" && b == "q")

                data Taken

                behavior take : (""");
        for (int at = 0; at < 13; at++) {
            source.append(at == 0 ? "" : ", ").append("p").append(at).append(": P");
        }
        source.append(") -> Taken\n");

        Read read = read(source.toString(), "take");
        Quantities asked = read.inputs().quantities(read.rules());
        assertTrue(asked.emptiness().isEmpty(), "nothing here contradicts");

        List<?> factors = factorsOf(asked);
        assertEquals(13, factors.size(), "one reading per parameter");
        for (Object each : factors) {
            assertEquals(2, ((AdmissibleValues.Held.Alternatives<?>)
                            ((AdmissibleValues<?>) each).held()).boxes().size(),
                    "and each holds the two its own declaration left");
        }
    }

    /**
     * How the input's one state holds the readings of its parameters apart.
     *
     * <p>Reached by reflection because neither the state nor the factoring is anybody's to ask for.
     * What went wrong was a representation growing as the product of the parameters, and reading it
     * is the only way to measure that it does not — a time would be a fact about the machine it was
     * taken on.
     */
    private static List<?> factorsOf(Quantities asked) {
        try {
            java.lang.reflect.Method held = asked.getClass()
                    .getDeclaredMethod("constraints", StructuralContext.class);
            held.setAccessible(true);
            // Asked assuming nothing, which is every parameter: what is being measured is that the
            // readings of thirteen of them are held apart, and no narrowing is involved in it.
            Object values = ((souther.compiler.check.ConstraintState<?>)
                    held.invoke(asked, StructuralContext.NONE)).values();
            java.lang.reflect.Method factors = values.getClass().getDeclaredMethod("factors");
            factors.setAccessible(true);
            return (List<?>) factors.invoke(values);
        } catch (ReflectiveOperationException e) {
            throw new LinkageError(e.getMessage(), e);
        }
    }

    /**
     * A tripwire, and not the proof above it.
     *
     * <p>What the tests above establish is that the readings the compiler makes today are all
     * answered by the one state. They cannot establish it of a reading added tomorrow, and the
     * failure that would leave is the silent one: a second answerer grows back on what a parameter
     * hands over, gets asked first because it is nearer, and goes on winning wherever it can say
     * anything at all — which is exactly what this change removed and nothing else would notice.
     *
     * <p>So what a parameter hands over may not answer whether anything is left. It hands over
     * rules; the question belongs to whatever those rules were said together with.
     */
    @Test
    void whatAParameterHandsOverAnswersNoSuchQuestion() {
        List<String> offered = java.util.Arrays.stream(FieldDomains.Settled.class.getMethods())
                .filter(each -> each.getDeclaringClass() == FieldDomains.Settled.class)
                .map(Method::getName)
                .sorted()
                .toList();

        assertEquals(List.of("constraintsOver"), offered,
                "a reading of one parameter supplies rules and answers nothing, so handing them"
                        + " over is the whole of what it offers. Whatever was added here answers"
                        + " something, and whether anything is left is answered by the state the"
                        + " rules are said together in");
    }

    /** What proves the input holds nothing, read off the one thing that answers. */
    private static Emptiness why(String source) {
        Read read = read(source, "take");
        EmptyInput held = read.inputs().quantities(read.rules()).emptiness().orElseThrow();
        return ((EmptyInput.ProvedByTheRules) held).why();
    }

    private record Read(InputDomain inputs, RuleReadingSource rules) {}

    private static Read read(String source, String behavior) {
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.answerEverything();
        String module = compilation.modules().get(0);
        Map<String, DeclaredSig> sigs =
                compilation.db().ask(new Bodies.DeclaredSignatures(module)).value();
        RuleReadingSource rules = RuleReadings.of(compilation, module);
        return new Read(InputDomain.of(sigs.get(behavior), rules,
                ReadAs.THE_COMPILATION_DOES), rules);
    }
}
