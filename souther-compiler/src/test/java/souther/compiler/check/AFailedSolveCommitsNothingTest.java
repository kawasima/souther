package souther.compiler.check;

import souther.compiler.types.Type;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a solving walk leaves behind when it does not fit.
 *
 * <p>A walk settles variables as it goes and stops at the first position that disagrees, so the
 * positions it had already settled were settled by a reading the walk went on to refuse. Every
 * caller that tries a solve it may not keep reads the same map afterwards, and a caller cannot tell
 * a variable this walk settled from one that was there before. So a walk that does not fit settles
 * nothing.
 */
class AFailedSolveCommitsNothingTest {

    /** {@code ('a, Int)} read against {@code (String, Bool)}: the first position settles
     * {@code 'a}, the second disagrees. The disagreement is the whole answer, so {@code 'a} was
     * never settled. */
    @Test
    void aVariableSettledBeforeTheDisagreementIsNotKept() {
        Map<String, Type> bind = new HashMap<>();
        Type param = Type.tuple(List.of(Type.var("'a"), Type.INT));
        Type arg = Type.tuple(List.of(Type.STRING, Type.BOOL));

        Fit fit = TypeOps.unify(param, arg, bind, PublishedDeclarations.NONE);

        assertInstanceOf(Fit.Disagrees.class, fit);
        assertTrue(bind.isEmpty(),
                "a walk that did not fit settled " + bind + " on the way to refusing");
    }

    /** The same walk when it does fit keeps what it settled — the contract is about failure, and
     * a success that settled nothing would be no contract at all. */
    @Test
    void aWalkThatFitsKeepsWhatItSettled() {
        Map<String, Type> bind = new HashMap<>();
        Type param = Type.tuple(List.of(Type.var("'a"), Type.INT));
        Type arg = Type.tuple(List.of(Type.STRING, Type.INT));

        Fit fit = TypeOps.unify(param, arg, bind, PublishedDeclarations.NONE);

        assertInstanceOf(Fit.Fits.class, fit);
        assertEquals(Type.STRING, bind.get("'a"));
    }

    /**
     * What the contract says is that the map is as the caller left it, which is not the same as
     * empty. A walk that emptied it on the way out would answer this test's first case and still
     * take away what another reading had settled before this one was tried.
     */
    @Test
    void whatWasSettledBeforeTheWalkIsStillThereAfterwards() {
        Map<String, Type> bind = new HashMap<>();
        bind.put("'settled", Type.STRING);
        Type param = Type.tuple(List.of(Type.var("'a"), Type.INT));
        Type arg = Type.tuple(List.of(Type.BOOL, Type.STRING));

        assertInstanceOf(Fit.Disagrees.class, TypeOps.unify(param, arg, bind, PublishedDeclarations.NONE));

        assertEquals(Map.of("'settled", Type.STRING), bind,
                "a walk that did not fit left the map as " + bind);
    }

    /** The answer names the position that disagreed, not the pair the walk started from — the two
     * types a reader is shown are the ones that did not go together. */
    @Test
    void theAnswerCarriesTheTypesAtThePositionThatDisagreed() {
        Type param = Type.tuple(List.of(Type.var("'a"), Type.INT));
        Type arg = Type.tuple(List.of(Type.STRING, Type.BOOL));

        Fit.Disagrees d = assertInstanceOf(Fit.Disagrees.class,
                TypeOps.unify(param, arg, new HashMap<>(), PublishedDeclarations.NONE));

        assertEquals(Type.INT, d.expected());
        assertEquals(Type.BOOL, d.actual());
    }
}
