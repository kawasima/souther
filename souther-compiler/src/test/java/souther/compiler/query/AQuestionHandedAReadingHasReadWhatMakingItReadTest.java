package souther.compiler.query;

import souther.compiler.meta.ModulePath;
import souther.compiler.types.TypeKey;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A question handed a reading somebody else made has read what making it read.
 *
 * <p>The reading of a declaration that one question makes is handed to the next question that would
 * have made it, which is what stops a declaration being read once for every question that reaches
 * it. What a question of this store is kept by is what it read: an edit reaches it because
 * something it read came out different. So a question handed the reading and nothing else has read
 * nothing to have it, and is kept over an edit to the very rules the reading is of — the rules of
 * another module, whose own answers say nothing about it.
 *
 * <p>Held at the store and not by an edit, because an edit to a declaration reaches most questions
 * by other roads: the module's own text changes, its declarations change, and everything reading
 * either is recomputed whatever this does. What is asked here is the one thing those roads do not
 * carry — that the question depends on the clauses the reading it was handed was made from.
 */
class AQuestionHandedAReadingHasReadWhatMakingItReadTest {

    /**
     * A declaration with rules of its own in one module, and in another a behavior whose input is
     * that declaration — so the second module's inputs are read from the first module's rules, and
     * the reading of them is the one the first module's own answer made.
     */
    private static final Map<String, String> TWO_MODULES = new LinkedHashMap<>(Map.of(
            "up.sou", """
                    module up exposing ( Amount )

                    data Amount = Int
                        invariant value >= 0
                        invariant value <= 100
                    """,
            "down.sou", """
                    module down

                    import up ( Amount )

                    data Kind = Small | Large

                    behavior sizeOf : (a: Amount) -> Kind
                    let sizeOf (a) = if a.value <= 50 then Small else Large
                    """));

    @Test
    void theInputsOfOneModuleDependOnTheClausesOfTheDeclarationTheyRead() {
        Compilation compilation = Compilation.ofDocuments(TWO_MODULES, Set.of(), ModulePath.EMPTY);
        compilation.answerEverything();
        assertTrue(compilation.diagnostics().values().stream().allMatch(List::isEmpty),
                "the model under test compiles clean");

        Set<Key<?>> read = compilation.db().dependenciesOf(new Adequacy.Inputs("down"));

        assertTrue(read.contains(new Shapes.ClausesExpandedFor(new TypeKey("up", "Amount"))),
                () -> "what the inputs of `down` read does not include the clauses of `up.Amount`,"
                        + " whose rules they were answered out of: they were handed a reading of"
                        + " them and nothing was said of what making it read, so an edit to those"
                        + " rules reaches this by no road at all. What it did read: " + read);
    }
}
