package souther.compiler.coverage;

import org.junit.jupiter.api.Test;

import souther.compiler.core.Core;
import souther.compiler.diag.SourcePos;
import souther.compiler.types.BinOp;
import souther.compiler.types.BindingId;
import souther.compiler.types.BindingOwner;
import souther.compiler.types.ConstructOccurrence;
import souther.compiler.types.SourceConstruct;
import souther.compiler.types.SourceConstructOrigin;
import souther.compiler.types.Type;
import souther.compiler.types.WrittenOwner;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Two comparisons standing at one occurrence stop the enumeration.
 *
 * <p>What tells one comparison from another is what the source wrote and which copies it stands in,
 * so two of them are equal wherever those agree. Whether that means one comparison is what the
 * enumeration decides, and a pair it cannot tell apart is a pair whose readers would each be
 * answered about the other: a place a run is recorded at, a rule read off it and a line drawn on it
 * are all filed under this, so the two would land on top of each other wherever they were kept.
 *
 * <p><b>Written by hand, because the models cannot make one.</b> A library operation applying a
 * block it was handed twice used to make such a pair, and the site a copy is made at says which
 * application it was made at, so it makes one no longer. What the walk does about a pair it cannot
 * tell apart is not a fact about the models, and leaving it to them is leaving it to be discovered
 * by whichever provenance goes missing next.
 *
 * <p>The other two things that arrive under one occurrence are here beside it, because what the walk
 * has to do is tell them apart: a node the walk reaches twice is one comparison and stands, and a
 * node standing in two bodies is refused for a reason of its own.
 */
class TwoComparisonsUnderOneOccurrenceAreRefusedTest {

    private static final SourcePos POS = new SourcePos(1, 1);

    private static final BindingOwner OWNER = new BindingOwner.OfValue("demo", "go");

    private static ConstructOccurrence at(int ordinal) {
        return ConstructOccurrence.asWritten(SourceConstructOrigin.written(
                new WrittenOwner.Body("demo", "go"), ordinal, SourceConstruct.BINARY));
    }

    private static Core read(int ordinal) {
        return new Core.Read("n", new BindingId(OWNER, ordinal), Type.INT, POS);
    }

    /** One comparison, at {@code occurrence}. A fresh object each call, which is what makes two of
     *  these two nodes. */
    private static Core.Binary comparison(ConstructOccurrence occurrence) {
        return new Core.Binary(BinOp.GE, read(0), read(1), occurrence, Type.BOOL, POS);
    }

    /** Both of them under one {@code &&}, which combines comparisons rather than being one. */
    private static Core both(Core left, Core right) {
        return new Core.Binary(BinOp.AND, left, right, at(9), Type.BOOL, POS);
    }

    private static ComparisonCatalog catalogue(Map<String, Core> bodies) {
        return ComparisonCatalog.of(new ModuleBodies("demo", new LinkedHashMap<>(bodies)));
    }

    @Test
    void twoNodesUnderOneOccurrenceAreRefused() {
        ConstructOccurrence shared = at(0);
        Core body = both(comparison(shared), comparison(shared));

        IllegalStateException refused = assertThrows(IllegalStateException.class,
                () -> catalogue(Map.of("go", body)));

        assertTrue(refused.getMessage().contains("two comparisons under one occurrence"),
                () -> "the walk says which of the three things under one occurrence this is: "
                        + refused.getMessage());
    }

    /**
     * And one node met twice is one comparison.
     *
     * <p>The control for the refusal above. A body may hold one node in two places, and a walk that
     * refused whatever it had met before would refuse that — so what the refusal rests on is the
     * node and not the occurrence, and this is what says the two are told apart.
     */
    @Test
    void oneNodeMetTwiceIsOneComparison() {
        Core.Binary once = comparison(at(0));
        Core body = both(once, once);

        assertEquals(1, catalogue(Map.of("go", body)).all().size(),
                "a node the walk reaches twice is one comparison written once");
    }

    /**
     * And one node standing in two bodies is refused, saying so.
     *
     * <p>Its own reason and its own words. Everything below files what it says per body, so two
     * bodies holding one comparison is two readings each answered with the other's place — which is
     * a different thing from two comparisons the walk cannot tell apart, and a reader told the wrong
     * one would go looking for the wrong repair.
     */
    @Test
    void oneNodeInTwoBodiesIsRefused() {
        Core.Binary shared = comparison(at(0));
        Map<String, Core> bodies = new LinkedHashMap<>();
        bodies.put("go", both(shared, comparison(at(1))));
        bodies.put("also", both(shared, comparison(at(2))));

        IllegalStateException refused =
                assertThrows(IllegalStateException.class, () -> catalogue(bodies));

        assertTrue(refused.getMessage().contains("one comparison of two bodies"),
                () -> "and this one is told from the other by what it says: "
                        + refused.getMessage());
    }
}
