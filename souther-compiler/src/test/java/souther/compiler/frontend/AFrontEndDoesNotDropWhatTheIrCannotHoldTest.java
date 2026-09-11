package souther.compiler.frontend;

import souther.compiler.WhereItSits;
import org.junit.jupiter.api.Test;

import souther.compiler.query.Scopes;
import souther.compiler.Compiler;
import souther.compiler.ast.Hir;
import souther.compiler.check.Symbols;
import souther.compiler.diag.CompileException;
import souther.compiler.query.Compilation;
import souther.compiler.types.TypeKey;

import java.lang.reflect.RecordComponent;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A source construct the IR it is lowered into cannot represent is refused where it is still there.
 *
 * <p>Not a rule about sums. The parser takes an {@code invariant} clause after a declaration of any
 * form, and each form is lowered into a declaration of its own kind; where the kind it reaches has
 * no slot for a clause, the clause has nowhere to go. Reaching that point and carrying on drops what
 * the author wrote, and with it anything wrong inside it — a clause calling a function that does not
 * exist compiles clean.
 *
 * <p>What that costs is not one silent clause. A reading that asks "what rules are written about
 * this position" answers from the IR, so a rule the IR never received reads as a rule nobody wrote,
 * and a report goes on to say the model draws no distinction there. An absence proved from a
 * declaration that lost its rules is not an absence.
 *
 * <p>So the property is written over the declaration kinds rather than over the one that was found
 * to have the hole: writing the clause has to change something. Either the declaration holds it, or
 * a diagnostic appears that was not there without it. The population comes from
 * {@link Hir.Def}'s own permitted subclasses, so a declaration kind added later arrives here with no
 * sample and fails rather than being quietly outside the property.
 */
class AFrontEndDoesNotDropWhatTheIrCannotHoldTest {

    /** One declaration of each kind, written twice: with the clause and without it. */
    private record Sample(String withClause, String withoutClause) {}

    private static final Map<String, Sample> BY_KIND = new LinkedHashMap<>(Map.of(
            "Data", new Sample("""
                    module m
                    data T = { a: Int }
                        invariant a >= 0
                    """, """
                    module m
                    data T = { a: Int }
                    """),
            "SumData", new Sample("""
                    module m
                    data A
                    data B
                    data T = A | B
                        invariant value == A
                    """, """
                    module m
                    data A
                    data B
                    data T = A | B
                    """),
            "UnitData", new Sample("""
                    module m
                    data T
                        invariant true
                    """, """
                    module m
                    data T
                    """)));

    @Test
    void everyDeclarationKindHasASample() {
        List<String> kinds = java.util.Arrays.stream(Hir.Def.class.getPermittedSubclasses())
                .map(Class::getSimpleName).sorted().toList();

        assertEquals(kinds, BY_KIND.keySet().stream().sorted().toList(),
                "a declaration kind with no sample is one this property says nothing about");
    }

    @Test
    void aClauseIsEitherHeldOrReported() {
        BY_KIND.forEach((kind, sample) -> {
            Read with = read(sample.withClause());
            Read without = read(sample.withoutClause());

            assertTrue(with.holdsAClause() || with.diagnostics() > without.diagnostics(),
                    kind + ": the clause was neither held by the declaration nor reported."
                            + " It was written and then dropped, which leaves a reader of the IR"
                            + " unable to tell it from a declaration nobody wrote a rule on");
        });
    }

    /**
     * The one kind that was found dropping a clause, said as the rule it is.
     *
     * <p>Beside the property rather than instead of it. The property says a clause has to change
     * something; this says which diagnostic a reader gets and where it points, which is what an
     * author acts on. The same pair is written for a unit data in {@code CompileUnitValueTest}.
     */
    @Test
    void aSumCannotCarryAnInvariant() {
        String module = """
                module demo
                data A
                data B
                data T = A | B
                    invariant value == A
                data Out = { s: String }
                behavior k : (t: T) -> Out constructs Out
                let k (t) = Out { s = "x" }
                """;
        CompileException refused = assertThrows(CompileException.class, () -> Compiler.compile(module));

        assertEquals("E1107", refused.code());
        assertEquals(5, WhereItSits.in(module, refused.pos()).line(), "must point at the clause, not the declaration");
    }

    /** The clause is refused before it is elaborated, so this would otherwise compile clean with an
     *  unbound name inside it — which is how the drop was found. */
    @Test
    void aSumInvariantIsRefusedEvenWhenItsExpressionIsNonsense() {
        CompileException refused = assertThrows(CompileException.class, () -> Compiler.compile("""
                module demo
                data A
                data B
                data T = A | B
                    invariant nonexistent(value) > 0
                data Out = { s: String }
                behavior k : (t: T) -> Out constructs Out
                let k (t) = Out { s = "x" }
                """));

        assertEquals("E1107", refused.code());
    }

    /** What compiling one source came to: how much was said, and whether the declaration kept the
     *  clause. Both from the one compile, since a second one is a second reading. */
    private record Read(int diagnostics, boolean holdsAClause) {}

    private static Read read(String source) {
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.answerEverything();
        int said = compilation.diagnostics().values().stream().mapToInt(List::size).sum();
        if (compilation.modules().isEmpty()) {
            return new Read(said, false);
        }
        String module = compilation.modules().get(0);
        Symbols symbols = Scopes.derived(compilation.db(), module).value();
        if (symbols == null) {
            return new Read(said, false);
        }
        return new Read(said, holdsAClause(
                symbols.declaredNode(new TypeKey(module, "T"))));
    }

    /**
     * Whether a declaration is holding an invariant clause.
     *
     * <p>Asked of the record's components rather than of a list of the kinds that have one. Which
     * declarations can hold a clause is what the IR's own shape says, and a reading that named them
     * here would be a copy of that shape kept in step by hand.
     */
    private static boolean holdsAClause(Hir.Def def) {
        if (def == null) {
            return false;
        }
        for (RecordComponent component : def.getClass().getRecordComponents()) {
            if (!(component.getGenericType() instanceof java.lang.reflect.ParameterizedType list)
                    || list.getActualTypeArguments().length != 1
                    || list.getActualTypeArguments()[0] != Hir.InvariantClause.class) {
                continue;
            }
            try {
                if (!((List<?>) component.getAccessor().invoke(def)).isEmpty()) {
                    return true;
                }
            } catch (ReflectiveOperationException e) {
                throw new AssertionError(e);
            }
        }
        return false;
    }
}
