package souther.compiler.check;

import souther.compiler.diag.Placement;
import souther.compiler.DefaultStdlib;
import souther.compiler.source.SourceId;

import souther.compiler.diag.Primary;

import souther.compiler.ast.Hir;
import souther.compiler.diag.CompileException;
import souther.compiler.diag.SourcePos;
import souther.compiler.diag.msg.TypeMessage;
import souther.compiler.types.BindingOwner;
import souther.compiler.types.ReachName;
import souther.compiler.types.ApplicationOrigin;
import souther.compiler.types.SourceConstruct;
import souther.compiler.types.SourceConstructOrigin;
import souther.compiler.types.SourceReferenceOrigin;
import souther.compiler.types.WrittenOwner;
import souther.compiler.types.ValueName;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A kept call refuses its function argument where the argument is written.
 *
 * <p>Both kinds of argument are held to the signature in one method, and they are one rule: a value
 * given to a position it does not fit. A value argument is refused at its own region and a function
 * argument was refused at the callee, because the check the second one goes through is handed two
 * types and the call's position rather than the argument. The path a reader reaches this on is the
 * discharge representation, where the language's own operations are kept standing.
 */
class AKeptCallsFunctionArgumentIsRefusedWhereItIsWrittenTest {

    /** Positions of a file this compile holds, as a parse of one makes them: what is being tested
     *  is which of two lines the caret lands on, and a position naming no source would leave that
     *  to whichever text the caller said it was reading. */
    private static final SourceId SOURCE = new SourceId("0");
    private static final SourcePos CALL = Placement.aFileOfThisCompile(SOURCE).at(1, 1);

    /** The list here is this test's own: no source spells the brackets. */
    private static final SourceConstructOrigin COMPOSED = SourceConstructOrigin.unwritten();

    /** The applications are a body's: this test stands where an author's call stands. */
    private static final ApplicationOrigin WROTE = new ApplicationOrigin.Written(
            SourceConstructOrigin.written(new WrittenOwner.Body("m", "b"), 0, SourceConstruct.CALL));
    private static final SourcePos ARGUMENT = Placement.aFileOfThisCompile(SOURCE).at(3, 5);

    /**
     * {@code List.flatMap : (('a) -> List<'b>, List<'a>) -> List<'b>} — the function argument is
     * declared to answer a list, and this one answers an Int. The block is written two lines below
     * the callee, so only the block's own position puts the caret on a line the reader has to look
     * at.
     */
    @Test
    void aBlockThatAnswersTheWrongTypeIsUnderlinedWhereItIsWritten() {
        Hir.Binders binders = new Hir.Binders(new BindingOwner.OfValue("demo", "test"));
        Hir.Block answersAnInt = new Hir.Block(List.of(binders.binder("x", ARGUMENT)),
                new Hir.IntLit(1, ARGUMENT, null), souther.compiler.types.RuleOrigin.unwritten(), ARGUMENT, null);
        Hir.Expr call = Hir.Apply.synthetic("List.flatMap",
                new ReachName.OfLibrary(ValueName.Stdlib.operation("List", "flatMap")),
                new SourceReferenceOrigin(new WrittenOwner.Body("m", "b"), 0), WROTE,
                List.of(answersAnInt, new Hir.ListLit(List.of(new Hir.IntLit(2, CALL, null)), COMPOSED, CALL, null)),
                CALL, null);

        CompileException e = assertThrows(CompileException.class,
                () -> Elaborator.elaborate(call, Scope.NONE, CheckContext.of(Symbols.none(DefaultStdlib.get()))
                        .preserving(Preserved.byTheLanguagesOwnOperations())));

        assertEquals(ARGUMENT,
                ((Primary.InSource) e.diagnostic().primary()).place().region().start(),
                "the block is written at " + ARGUMENT + " and the callee at " + CALL);
    }

    /**
     * And it is refused by the sentence a value argument is refused by. Which of two sentences a
     * reader gets is not something a reader can see, so one rule states itself one way.
     */
    @Test
    void itIsRefusedBySameSentenceAsAValueArgument() {
        Hir.Binders binders = new Hir.Binders(new BindingOwner.OfValue("demo", "test"));
        Hir.Block answersAnInt = new Hir.Block(List.of(binders.binder("x", ARGUMENT)),
                new Hir.IntLit(1, ARGUMENT, null), souther.compiler.types.RuleOrigin.unwritten(), ARGUMENT, null);
        Hir.Expr call = Hir.Apply.synthetic("List.flatMap",
                new ReachName.OfLibrary(ValueName.Stdlib.operation("List", "flatMap")),
                new SourceReferenceOrigin(new WrittenOwner.Body("m", "b"), 0), WROTE,
                List.of(answersAnInt, new Hir.ListLit(List.of(new Hir.IntLit(2, CALL, null)), COMPOSED, CALL, null)),
                CALL, null);

        CompileException e = assertThrows(CompileException.class,
                () -> Elaborator.elaborate(call, Scope.NONE, CheckContext.of(Symbols.none(DefaultStdlib.get()))
                        .preserving(Preserved.byTheLanguagesOwnOperations())));

        TypeMessage.ItDoesNotHaveTheTypeItNeedsHere said = assertInstanceOf(
                TypeMessage.ItDoesNotHaveTheTypeItNeedsHere.class, e.diagnostic().said());
        assertEquals("argument 1 of List.flatMap", said.what());
    }
}
