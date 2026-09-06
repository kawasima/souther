package souther.compiler.ast;

import souther.compiler.diag.SourcePos;
import souther.compiler.types.ReachName;
import souther.compiler.types.SourceReferenceOrigin;
import souther.compiler.types.ValueName;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A name says what it denotes and how this module reaches it, or it says neither.
 *
 * <p>Both answers come from resolution and they are two halves of one question. A reader that had
 * one and not the other would hold a reference that resolves to a declaration and reaches nothing,
 * or the other way round — and the reader after it takes the spelling instead, which is what a table
 * keyed by names answers with silence.
 *
 * <p>Which is why there is one half to carry. {@link ReachName} is the answer and it holds both, so
 * a rewrite has nowhere to put a declaration without the route it was reached by; what is refused
 * here is the name that carries no reference at all.
 */
class ANameAnsweredHalfwayIsRefusedTest {

    private static final SourcePos POS = new SourcePos(1, 1);

    private static final ValueName.Helper DECLARED = new ValueName.Helper("demo", "spin");

    /**
     * A name the parser read says what it is written as and nothing else. It has no question about
     * a declaration to refuse, because it carries no slot for one — the answers are
     * {@link Hir.Var}'s, and this is the other representation.
     */
    @Test
    void aNameTheParserReadSaysOnlyWhatItIsWrittenAs() {
        Ast.Var written = Ast.Var.written("spin", POS,
                new SourceReferenceOrigin(new souther.compiler.types.WrittenOwner.Body("m", "b"), 0));

        assertEquals("spin", written.name());
        assertEquals(Ast.Var.class, written.getClass(),
                "one form: what a name means is not something this representation can hold");
    }

    /**
     * Half an answer is not a state a rewrite may leave behind. There is one slot to leave empty,
     * and leaving it empty is refused where the name is put together.
     */
    @Test
    void aNameAnsweredOnOneCountOnlyCannotBeBuilt() {
        IllegalArgumentException noReference = assertThrows(IllegalArgumentException.class,
                () -> new Hir.Var.Denoting(WrittenName.of("spin", POS), null, null, null));

        assertEquals(true, noReference.getMessage().contains("spin"), noReference.getMessage());
    }

    /**
     * And the other half has nowhere to be left behind: a declaration is held by the reference that
     * reached it, so a rewrite that has a declaration and no route has nothing to build.
     *
     * <p>Asked of {@link ReachName} rather than of the name, because that is where the pairing now
     * is. A route with nothing on the other side of it is what would let one name's declaration sit
     * beside another's route further down.
     */
    @Test
    void andAReferenceReachesSomething() {
        assertThrows(IllegalArgumentException.class, () -> new ReachName.Own(null));
        assertThrows(IllegalArgumentException.class, () -> new ReachName.OfModule(null));
        assertThrows(IllegalArgumentException.class, () -> new ReachName.OfLibrary(null));
        assertThrows(IllegalArgumentException.class, () -> new ReachName.TheNamespace(null));
        assertThrows(IllegalArgumentException.class, () -> new ReachName.InScope(null));
    }

    /**
     * And a pass applying a name says what it means. The application a pass writes takes both
     * answers and takes them as answers: the constructor that took a spelling alone is gone, and
     * this is the one that replaced it, so a caller with nothing to say cannot say it here either.
     *
     * <p>Refused at the application rather than left to {@link Hir.Var}: what a pass hands in is a
     * spelling and the reference, and a caller with nothing to say would otherwise write a name for
     * someone downstream to resolve, which is what ADR-0067 rules out.
     */
    @Test
    void anApplicationAPassWritesCannotLeaveItsNameUnanswered() {
        assertThrows(NullPointerException.class,
                () -> Hir.Apply.synthetic("spin", null, null, List.of(), POS, null));
    }

    /** Answered, it says both. */
    @Test
    void aResolvedNameSaysWhatItDenotesAndHowItIsReached() {
        WrittenName spin = WrittenName.of("spin", POS);
        Hir.Var resolved = new Hir.Var.Denoting(spin, new ReachName.OfModule(DECLARED),
                new SourceReferenceOrigin(new souther.compiler.types.WrittenOwner.Body("demo", "b"), 0),
                spin.region());

        assertEquals("spin", resolved.answered().denotes().name());
        assertEquals("demo.spin", resolved.answered().reaches());
    }
}
