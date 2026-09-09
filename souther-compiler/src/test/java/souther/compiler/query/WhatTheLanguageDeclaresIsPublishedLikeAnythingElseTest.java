package souther.compiler.query;

import souther.compiler.ast.Hir;
import souther.compiler.check.DeclarationMeaning;
import souther.compiler.types.TypeKey;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the language declares is published like anything else.
 *
 * <p>A declaration is asked for by its address, and a reader elsewhere has no way of telling which
 * of them a module wrote. So every one of them is answered about — including the ones no module
 * wrote, which resolve and type like any other and belong to no module here.
 *
 * <p>They are read differently for a reason, and this is what holds the reason to being true. A
 * declaration's clauses are read in a reading made over the scope of the module that wrote it, and
 * asked for the module of one of these a compilation answers that it holds no such module. So what
 * the language declares is read without one — which is only sound because a sum names its cases and
 * a unit names itself, and neither says anything a reading answers.
 */
class WhatTheLanguageDeclaresIsPublishedLikeAnythingElseTest {

    private static final String SOURCE = """
            module demo

            data Amount = Int
                invariant value >= 0
            """;

    @Test
    void everyDeclarationTheLanguageGivesIsAnsweredFor() {
        Compilation c = Compilation.ofSource(SOURCE, "Main");
        c.answerEverything();
        Map<TypeKey, Hir.Def> language =
                c.db().ask(new Front.Library()).value().languageDeclarations();
        assertFalse(language.isEmpty(), "the library declares something, or this asks nothing");

        for (TypeKey declared : language.keySet()) {
            Answer<DeclarationMeaning> said = c.db().ask(new Shapes.MeaningOf(declared));
            assertTrue(said.present(),
                    "`" + declared + "` is declared by the language and nothing is published"
                            + " about it");
            assertTrue(said.value().declares().equals(declared),
                    "`" + declared + "` was published as `" + said.value().declares() + "`");
        }
    }

    /**
     * And a module of the compilation cannot be asked for one of them.
     *
     * <p>The reason the reading is made the other way, held rather than described. Take it away and
     * the answer above is one that happens to work.
     */
    @Test
    void andTheModuleOneIsAddressedInIsNoModuleThisCompilationHolds() {
        Compilation c = Compilation.ofSource(SOURCE, "Main");
        c.answerEverything();
        Map<TypeKey, Hir.Def> language =
                c.db().ask(new Front.Library()).value().languageDeclarations();

        for (TypeKey declared : language.keySet()) {
            assertFalse(c.modules().contains(declared.module()),
                    "`" + declared + "` is addressed in a module this compilation writes, so the"
                            + " reading of it would have been made after all");
        }
    }
}
