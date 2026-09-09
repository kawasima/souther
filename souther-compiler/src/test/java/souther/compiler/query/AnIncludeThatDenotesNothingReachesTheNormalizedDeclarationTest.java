package souther.compiler.query;

import souther.compiler.ast.Hir;
import souther.compiler.check.Normalized;
import souther.compiler.types.TypeKey;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A name a declaration spreads that denotes nothing is still there when the declaration is
 * normalized.
 *
 * <p>Asked because it decides what an answer about a declaration has to be able to say. A reading
 * published across a module boundary carries what a declaration includes; if an unresolved name is
 * gone by the rung that reading is taken at, what it carries is a list of declarations, and if it is
 * not, then which name went unanswered is part of what the declaration says — two declarations
 * spreading two different names that denote nothing do not say the same thing.
 *
 * <p>The source is one this compiler refuses. That is the state being asked about: name resolution
 * reports and carries on, and what this holds is what the passes below it are handed.
 */
class AnIncludeThatDenotesNothingReachesTheNormalizedDeclarationTest {

    private static final String SPREADING_NOTHING = """
            module demo

            data Named = { name: String }

            data Item = { ...Named, ...Absent }

            behavior f : (i: Item) -> String
            let f (i) = i.name
            """;

    @Test
    void whatADeclarationSpreadsIsThereWhetherOrNotItDenotes() {
        Compilation compilation = Compilation.ofSource(SPREADING_NOTHING, "Main");
        // Asked after the questions are, because what a compilation says is read off the reports its
        // answers carry: asked of a store nothing has been asked of, every source is silent.
        compilation.answerEverything();
        assertFalse(compilation.errors().isEmpty(),
                "this source is supposed to name something nothing declares");

        Answer<Normalized.Def> normalized =
                compilation.db().ask(new Shapes.NormalizedDef(new TypeKey("demo", "Item")));
        assertTrue(normalized.present(),
                "`Item` has no normalized declaration at all, so nothing below the settling is"
                        + " answered about a declaration that spreads a name nothing declares");

        assertTrue(normalized.value().node() instanceof Hir.Data,
                "`Item` is a product and came back as " + normalized.value().node());
        Hir.Data item = (Hir.Data) normalized.value().node();

        List<String> denoting = new ArrayList<>();
        List<String> unanswered = new ArrayList<>();
        for (Hir.Name each : item.includes()) {
            switch (each) {
                case Hir.Name.Denoting it -> denoting.add(it.type().name());
                case Hir.Name.Unanswered it -> unanswered.add(it.name().canonical());
            }
        }
        assertEquals(List.of("Named"), denoting, "what `Item` spreads and reaches");
        assertEquals(List.of("Absent"), unanswered,
                "what `Item` spreads and does not reach — and which name it was");
    }
}
