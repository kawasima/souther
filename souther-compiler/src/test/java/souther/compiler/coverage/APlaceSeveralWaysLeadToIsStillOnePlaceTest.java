package souther.compiler.coverage;

import org.junit.jupiter.api.Test;

import souther.compiler.core.Core;
import souther.compiler.types.WrittenOwner;
import souther.compiler.diag.SourcePos;
import souther.compiler.types.BinOp;
import souther.compiler.types.BindingId;
import souther.compiler.types.BindingOwner;
import souther.compiler.types.SourceConstruct;
import souther.compiler.types.ConstructOccurrence;
import souther.compiler.types.SourceConstructOrigin;
import souther.compiler.types.Type;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A node several ways lead to is one place, bound once, and numbered once.
 *
 * <p>A body is a tree to read and a graph to walk. Nothing a source writes makes one node stand in
 * two slots, but a pass that rewrote two equal subtrees into one leaves exactly that, and the
 * corpora hold such a place. What that place must not become is two: two names for one binder, or
 * two numbers for one arm — the second of which the emitter lights on no run and every count reads
 * as a place nothing reaches.
 *
 * <p>The trees here are built rather than compiled, because no source this compiler accepts makes
 * one. They are what a pass would leave, and they are handed to the numbering as bodies.
 */
class APlaceSeveralWaysLeadToIsStillOnePlaceTest {

    private static final SourcePos AT = new SourcePos(1, 1);

    private static final SourceConstructOrigin FORK =
            SourceConstructOrigin.written(new WrittenOwner.Body("demo", "b"), 0,
                    SourceConstruct.IF);

    /** One fork standing in both sides of a comparison, which is two ways to one place. The
     *  comparison itself is one no source wrote: what this is about is the fork under it. */
    private static Core sharedFork() {
        Core fork = new Core.If(new Core.Bool(true, Type.BOOL, AT),
                new Core.Int(1, Type.INT, AT), new Core.Int(2, Type.INT, AT),
                Core.ForkPlace.asWritten(ConstructOccurrence.asWritten(FORK)), Type.INT, AT);
        return new Core.Binary(BinOp.ADD, fork, fork, ConstructOccurrence.unwritten(), Type.INT,
                AT);
    }

    /** One {@code let} standing in both sides, which is two ways to one binder. */
    private static Core sharedBinder() {
        BindingId bound = new BindingId(new BindingOwner.OfValue("demo", "b"), 0);
        Core let = new Core.LetIn(new Core.Binder("x", bound),
                new Core.Int(1, Type.INT, AT),
                new Core.Read("x", bound, Type.INT, AT), Type.INT, AT);
        return new Core.Binary(BinOp.ADD, let, let, ConstructOccurrence.unwritten(), Type.INT, AT);
    }

    @Test
    void bothWaysToAPlaceAreItsAddress() {
        NodeAddresses places = NodeAddresses.of("b", sharedFork());
        Core fork = ((Core.Binary) sharedForkOf(places)).left();

        assertEquals(2, places.of(fork).occurrences().size(),
                () -> "the fork stands under both sides: " + places.of(fork));
        assertEquals(Set.of(
                        CorePath.ROOT.then(new CoreStructure.Edge.BinaryLeft()),
                        CorePath.ROOT.then(new CoreStructure.Edge.BinaryRight())),
                places.of(fork).occurrences(),
                "and the address says which sides");
    }

    /** A binder several ways lead to is one binder, at one place. */
    @Test
    void aBinderSeveralWaysLeadToIsBoundOnce() {
        Core body = sharedBinder();
        NodeAddresses places = NodeAddresses.of("b", body);
        Binders binders = Binders.of("demo", places);

        BindingId bound = ((Core.LetIn) ((Core.Binary) body).left()).binder().binding();
        Core let = ((Core.Binary) body).left();

        assertEquals(new BinderAddress.Local(places.of(let), new BinderSlot.LetBinder()),
                binders.at(bound),
                "one binder, at the one place both ways lead to");
        assertEquals(2, places.of(let).occurrences().size(),
                () -> "and that place is both ways: " + places.of(let));
    }

    /**
     * And numbering it twice is refused.
     *
     * <p>The arms of a fork are made once per arrival, so a fork two ways lead to would take two
     * numbers for one place. The second is a site the emitter lights on no run, which every count
     * reads as an arm nothing reaches — the shape of a real omission, reported as one.
     */
    @Test
    void aPlaceIsNumberedOnceOrRefused() {
        Map<String, Core> bodies = new LinkedHashMap<>();
        bodies.put("b", sharedFork());

        IllegalStateException refused = assertThrows(IllegalStateException.class,
                () -> CoverageSites.of(new ModuleBodies("demo", new LinkedHashMap<>(bodies)),
                        DecisionSources.NONE, SuppliedRules.NONE));

        assertTrue(refused.getMessage().contains("twice"),
                () -> "the numbering says the place was numbered twice: " + refused.getMessage());
    }

    /** The body this test's addresses were taken over, so the fork asked about is the one in it. */
    private static Core sharedForkOf(NodeAddresses places) {
        return places.all().keySet().stream()
                .filter(Core.Binary.class::isInstance)
                .findFirst().orElseThrow();
    }
}
