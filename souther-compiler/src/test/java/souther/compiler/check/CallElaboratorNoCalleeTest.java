package souther.compiler.check;

import souther.compiler.diag.msg.NameMessage;
import souther.compiler.diag.msg.DataMessage;
import souther.compiler.diag.msg.BehaviorMessage;
import souther.compiler.ast.Hir;
import souther.compiler.diag.CompileException;
import souther.compiler.diag.SourcePos;
import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbols;
import souther.compiler.types.BindingId;
import souther.compiler.types.BindingOwner;
import souther.compiler.types.ApplicationOrigin;
import souther.compiler.types.ReachName;
import souther.compiler.types.SourceConstruct;
import souther.compiler.types.SourceConstructOrigin;
import souther.compiler.types.SourceReferenceOrigin;
import souther.compiler.types.ValueName;
import souther.compiler.types.WrittenOwner;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A call this module has no callee for is answered by what resolution said the name is. Every answer
 * it can give by this point is something — a name it could not place is thrown out earlier — so no
 * answer here may be "not a behavior or a builtin", which was said of a behavior, of a type and of a
 * binding alike and sent authors after typos they had not made.
 *
 * <p>Asked of {@code noCallee} directly rather than of a program, because that is what the rule is
 * about: the two classifications an author can write into are reachable from a source, and the rest
 * are reachable only when some earlier pass failed to expand what it should have. Compiling a program
 * to reach them would be compiling a compiler bug.
 */
class CallElaboratorNoCalleeTest {

    /** The applications are a body's: this test stands where an author's call stands. */
    private static final ApplicationOrigin WROTE = new ApplicationOrigin.Written(
            SourceConstructOrigin.written(new WrittenOwner.Body("m", "b"), 0, SourceConstruct.CALL));

    private static final SourcePos AT = new SourcePos(7, 3);

    private static RuntimeException answerFor(ValueName denotes) {
        // As this module reaches it, whichever kind of name it is — which is what the arm of the
        // reference then says, and what this is about is the ones that reach no callee.
        // Over a scope with no module in it: which arm answers is what the name denotes, and the
        // one case that reads a declaration is a newtype applied to a count other than one, which
        // is not what any of these is.
        return CallElaborator.noCallee(
                Hir.Apply.synthetic("f", ReachName.of(denotes, "f", "m"),
                        new SourceReferenceOrigin(new WrittenOwner.Body("m", "b"), 0), WROTE,
                        List.of(new Hir.IntLit(1, AT, null)), AT, null),
                ResolvedSymbols.none(souther.compiler.DefaultStdlib.get()));
    }

    /** A behavior named from a helper `let` or a `>->` composition, neither of which reaches one. */
    @Test
    void aBehaviorIsToldItIsABehavior() {
        RuntimeException e = answerFor(new ValueName.Behavior("m", "f"));
        assertEquals("E1818", assertInstanceOf(CompileException.class, e).code());
        assertInstanceOf(BehaviorMessage.ABehaviorCannotBeCalledFromHere.class, ((CompileException) e).diagnostic().said());
    }

    /**
     * A type applied to an argument is a construction, and every place one is allowed rewrites it
     * before the check reads it. Unreachable from a source once the rewrite covers every place an
     * expression is written — which is the point of holding the arm: the day a place is added and the
     * rewrite is not, the author is told a construction was written somewhere it cannot be, rather
     * than that their own type is not a behavior.
     */
    @Test
    void aTypeIsToldItConstructs() {
        RuntimeException e = answerFor(
                new ValueName.OfType("Yen", TypeSymbols.declared(new TypeKey("m", "Yen"))));
        CompileException c = assertInstanceOf(CompileException.class, e);
        assertInstanceOf(DataMessage.AConstructionCannotBeWrittenHere.class, c.diagnostic().said());
        assertEquals(List.of("Yen"), List.copyOf(c.diagnostic().values().values()));
    }

    /**
     * A binding applied to arguments, whose type here is not a function. Either it is not one — a
     * value applied as though it were — or it has no type yet, which is what an inference probe sees
     * when it types a body before the binding it asks about has one and reads the report to find out.
     * One sentence answers both: at the point of the report, this name is not a function here.
     */
    @Test
    void aBindingIsToldItIsNotAFunction() {
        RuntimeException e = answerFor(new ValueName.Local("f",
                new BindingId(new BindingOwner.OfValue("m.a", "g"), 0)));
        assertInstanceOf(NameMessage.ItIsNotAFunctionHere.class,
                assertInstanceOf(CompileException.class, e).diagnostic().said());
    }

    /**
     * A name the language itself gives, applied. `Some` and `None` are told apart earlier (E1303),
     * so what reaches here is a rounding mode — a bare identifier the operation taking one reads
     * rather than evaluates. An author can write it, so they are told, not shown a compiler bug.
     */
    @Test
    void aBuiltinIsToldItIsNotAFunction() {
        RuntimeException e = answerFor(new ValueName.Builtin("HALF_UP"));
        CompileException c = assertInstanceOf(CompileException.class, e);
        assertInstanceOf(NameMessage.ANameTheLanguageGivesIsNotAFunction.class, c.diagnostic().said());
        assertEquals(List.of("HALF_UP"), List.copyOf(c.diagnostic().values().values()));
    }

    /**
     * The rest name a call that should have been expanded, substituted or rewritten before the check
     * ran. That is the compiler disagreeing with itself, so it is raised as one — an author cannot
     * act on it, and dressing it as a language error would have them try.
     */
    @Test
    void anUnexpandedCallIsAnInternalError() {
        for (ValueName denotes : List.of(
                new ValueName.Helper("m", "f"),
                ValueName.Stdlib.operation("List", "map"))) {
            RuntimeException e = answerFor(denotes);
            assertInstanceOf(IllegalStateException.class, e, denotes.toString());
            assertTrue(e.getMessage().contains(String.valueOf(AT)), () -> "says where: " + e.getMessage());
        }
    }

    /**
     * And an application of something that is not a name, which is where the absent denotation
     * actually comes from: {@code Hir.Apply#denotes} answers for the name it applies, and there is
     * none. Written as a name with no answer instead, this pinned a node the constructor a pass
     * writes an application with no longer builds — a pass applying a name says what it means
     * (ADR-0067). The shape itself is still writable, since the parser has to hold a tree
     * resolution has not seen; what is gone is a pass reaching for it.
     */
    @Test
    void anApplicationOfSomethingThatIsNotANameIsAnInternalError() {
        Hir.Expr block = new Hir.Block(List.of(), new Hir.IntLit(1, AT, null), souther.compiler.types.RuleOrigin.unwritten(), AT, null);
        RuntimeException e = CallElaborator.noCallee(Hir.Apply.synthetic(block,
                List.of(new Hir.IntLit(1, AT, null)), WROTE, AT, null),
                ResolvedSymbols.none(souther.compiler.DefaultStdlib.get()));

        assertInstanceOf(IllegalStateException.class, e);
        assertTrue(e.getMessage().contains(String.valueOf(AT)), () -> "says where: " + e.getMessage());
    }
}
