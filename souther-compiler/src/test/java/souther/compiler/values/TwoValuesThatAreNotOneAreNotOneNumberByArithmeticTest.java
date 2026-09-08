package souther.compiler.values;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * What one of these values holds several of, it holds in no order — and what went with what is
 * still part of what it says.
 *
 * <p>A value that gathered its parts and handed the number up as gathered would leave those parts
 * separable in whatever hashes it next, and what hashes it next adds numbers up: a set sums what it
 * holds and a record carries its last component up unchanged. So the sum above cancels the pairing,
 * and the same parts shared out the other way come to one number.
 *
 * <p>Written as the arithmetic and not as a choice of values. Each of these holds under the old
 * shape for any parts at all, so nothing here is arranged to fail — which is what makes it a
 * witness rather than an example. None of them asks that two values never share a number, which
 * nothing can promise of an {@code int}.
 */
class TwoValuesThatAreNotOneAreNotOneNumberByArithmeticTest {

    private static final Sameness.Block<String> P = Sameness.Block.of("p");
    private static final Sameness.Block<String> Q = Sameness.Block.of("q");
    private static final Sameness.Block<String> R = Sameness.Block.of("r");

    private static final Value A = Value.text("A");
    private static final Value B = Value.text("B");

    /** Two lacks and two routes, shared out both ways: which route reached which lack is what an
     *  author is sent to read. */
    @Test
    void twoLacksReachedTwoWaysAreNotOneArgument() {
        RelationalLack<String> one = new RelationalLack.NoValueLeftForIt<>(P);
        RelationalLack<String> other = new RelationalLack.NoValueLeftForIt<>(Q);
        RelationalEvidence<String> byOne = route(P, A, 1, Q);
        RelationalEvidence<String> byAnother = route(Q, B, 2, R);

        Lacks<String> reached = Lacks.of(List.of(new Shown<>(one, byOne),
                new Shown<>(other, byAnother)));
        Lacks<String> otherwise = Lacks.of(List.of(new Shown<>(one, byAnother),
                new Shown<>(other, byOne)));

        assertNotEquals(reached, otherwise);
        assertNotEquals(reached.hashCode(), otherwise.hashCode(),
                "the same lacks and routes shared out two ways came to one number");
    }

    /** And two removals with each other's blockers: what a removal says is which neighbours left
     *  the value nowhere to go. */
    @Test
    void andTwoRemovalsBlockedByEachOthersNeighboursAreNotOneRoute() {
        Provenance<String> blocked = new Provenance<>(ordered(
                new Provenance.Removal<>(P, A, 1, ordered(Q)),
                new Provenance.Removal<>(Q, B, 2, ordered(R))));
        Provenance<String> otherwise = new Provenance<>(ordered(
                new Provenance.Removal<>(P, A, 1, ordered(R)),
                new Provenance.Removal<>(Q, B, 2, ordered(Q))));

        assertNotEquals(blocked, otherwise);
        assertNotEquals(blocked.hashCode(), otherwise.hashCode(),
                "the same removals blocked two ways came to one number");
    }

    /**
     * And two claims about one block are two claims.
     *
     * <p>Which case a lack is is the whole of what tells these two apart — a block stated apart
     * from itself and a block the denials left no value name one block and say different things —
     * and an argument holds several lacks in no order.
     */
    @Test
    void andTwoCasesOfALackAboutOneBlockAreTwoClaims() {
        RelationalLack<String> itself = new RelationalLack.ABlockApartFromItself<>(P);
        RelationalLack<String> nothingLeft = new RelationalLack.NoValueLeftForIt<>(P);

        assertNotEquals(itself, nothingLeft);
        assertNotEquals(itself.hashCode(), nothingLeft.hashCode(),
                "two claims about one block came to one number");
    }

    /** One route, of one removal. */
    private static RelationalEvidence<String> route(Sameness.Block<String> block, Value value,
                                                    int round, Sameness.Block<String> blocker) {
        return RelationalEvidence.of(new Provenance<>(
                ordered(new Provenance.Removal<>(block, value, round, ordered(blocker)))));
    }

    @SafeVarargs
    private static <T> Set<T> ordered(T... these) {
        Set<T> out = new LinkedHashSet<>();
        for (T each : these) {
            out.add(each);
        }
        return out;
    }
}
