package souther.compiler.query;

import org.junit.jupiter.api.Test;

import souther.compiler.diag.Citation;
import souther.compiler.diag.Diagnostic;
import souther.compiler.diag.Primary;
import souther.compiler.diag.SourcePos;
import souther.compiler.diag.msg.ModuleMessage;
import souther.compiler.meta.ModuleReadback;
import souther.compiler.meta.Readback;
import souther.compiler.source.SourceId;
import souther.compiler.diag.Placement;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A report this compilation makes about a module off the class path says which module the code is
 * in, before anything moves it.
 *
 * <p>Both of these used to be built with nothing said about where their code was, and the caret was
 * put on an import line afterwards by the walk that found them ({@code Front.saidAbout}). A report
 * that says nothing may be moved anywhere: {@link Diagnostic#reachedFrom} has one state where it
 * takes the caller's word for where the code is, and that is the state where nothing contradicts it.
 * So the walk was the only thing keeping the module a report is about and the module it is moved as
 * belonging to the same module, and nothing said so.
 *
 * <p>Saying it at the producer puts the two under one check. What is measured here is that the
 * producers say it — not that the walk still works, which the compile-level tests measure — because
 * a producer that went back to saying nothing would leave those green.
 */
class AReportAboutAPathModuleSaysWhichModuleItIsAboutTest {

    private static final List<SourcePos> AN_IMPORT_LINE =
            List.of(Placement.aFileOfThisCompile(new SourceId("0")).at(2, 1));

    @Test
    void aMissingDependencyIsAboutTheModuleThatNeedsIt() {
        Diagnostic said = Front.needs("lib.absent", "lib.held");

        assertEquals(new Primary.Unavailable(ModuleReadback.provenanceOf("lib.held")),
                said.primary(),
                "the code that needs it is written in `lib.held`, which this compile has no file for");
    }

    @Test
    void oneThisCompilerWillNotReadIsAboutThatModule() {
        Diagnostic said = Front.cannotBeReadBack("lib.held",
                new Readback.Failure.Incompatible("some other souther"));

        assertEquals(new Primary.Unavailable(ModuleReadback.provenanceOf("lib.held")),
                said.primary(),
                "the module that cannot be read back is the one the report is about");
    }

    /**
     * Moved to a place that reaches the module it is about, it points there and stays about the same
     * code.
     *
     * <p>The positive control for the refusal below: a move that agrees is a move that happens, so
     * the refusal is a claim about disagreement rather than about moving at all.
     */
    @Test
    void aMoveToWhereThatModuleIsReachedIsTheOrdinaryCase() {
        Diagnostic moved = Front.needs("lib.absent", "lib.held")
                .reachedFrom(AN_IMPORT_LINE, ModuleReadback.provenanceOf("lib.held"),
                        new ModuleMessage.ItIsReachedFromHereToo());

        Citation.Reached reached = assertInstanceOf(Citation.Reached.class,
                Citation.of(((Primary.InSource) moved.primary()).place().region().start()), "moved, it points at a file the reader holds");
        assertEquals(AN_IMPORT_LINE.get(0), reached.at());
        assertEquals("lib.held", reached.provenance().reachedBy(),
                "and is still about the code it was about");
    }

    /**
     * Moved as though it were about another module, it is refused.
     *
     * <p>One of the two producers is enough: what is being checked is
     * {@link Diagnostic#reachedFrom}'s comparison, which is one rule over whatever a report already
     * says, and what the two producers differ in is only which provenance they say.
     */
    @Test
    void aMoveToSomewhereElsesCodeIsRefused() {
        Diagnostic said = Front.needs("lib.absent", "lib.held");

        assertThrows(Diagnostic.MovedSomewhereElsesCode.class,
                () -> said.reachedFrom(AN_IMPORT_LINE, ModuleReadback.provenanceOf("lib.other"),
                        new ModuleMessage.ItIsReachedFromHereToo()),
                "a report saying its code is in one module may not be moved as another's");
    }
}
