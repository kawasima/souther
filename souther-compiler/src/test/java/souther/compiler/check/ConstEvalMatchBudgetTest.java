package souther.compiler.check;

import souther.compiler.DefaultStdlib;
import souther.compiler.ast.Hir;
import souther.compiler.diag.SourcePos;
import souther.compiler.types.ApplicationOrigin;
import souther.compiler.types.ReachName;
import souther.compiler.types.SourceConstruct;
import souther.compiler.types.SourceConstructOrigin;
import souther.compiler.types.SourceReferenceOrigin;
import souther.compiler.types.ValueName;
import souther.compiler.types.WrittenOwner;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Folding {@code String.matches} answers where answering is cheap and declines where it is not. A
 * backtracking engine recurses over its subject, and the walk that asks this fails open on a
 * {@code RuntimeException} — which a {@code StackOverflowError} is not — so an unbounded attempt
 * would end the compilation rather than the fold.
 */
class ConstEvalMatchBudgetTest {

    private static final SourcePos POS = new SourcePos(1, 1);

    private static Optional<Object> fold(String pattern, String subject) {
        ValueName.Stdlib.Operation matches = ValueName.Stdlib.operation("String", "matches");
        // Folded against the real library, because which operation folds is asked as the kernel
        // that library declares it to be. No module of its own: the call names a library operation
        // and nothing else.
        return ConstEval.against(Symbols.none(DefaultStdlib.get())).eval(Hir.Apply.synthetic("String.matches",
                new ReachName.OfLibrary(matches),
                new SourceReferenceOrigin(new WrittenOwner.Body("m", "b"), 0),
                new ApplicationOrigin.Written(SourceConstructOrigin.written(
                        new WrittenOwner.Body("m", "b"), 0, SourceConstruct.CALL)),
                List.of(new Hir.StringLit(pattern, POS, null), new Hir.StringLit(subject, POS, null)),
                POS, null));
    }

    @Test
    void aPatternOverAWrittenSubjectFolds() {
        assertEquals(Optional.of(true), fold("[0-9][A-E]", "1A"));
        assertEquals(Optional.of(false), fold("[0-9][A-E]", "zz"));
    }

    @Test
    void aSubjectPastTheBudgetIsLeftToTheRunTime() {
        // Far past what the budget allows the engine to read, and far past what it can recurse over
        assertTrue(fold("(a|b)*", "a".repeat(100_000)).isEmpty(),
                "declined rather than answered, and the compilation goes on");
    }
}
