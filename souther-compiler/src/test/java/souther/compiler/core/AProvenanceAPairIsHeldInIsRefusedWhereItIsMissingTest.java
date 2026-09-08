package souther.compiler.core;

import souther.compiler.KeptCalls;
import souther.compiler.diag.SourcePos;
import souther.compiler.types.Type;
import souther.compiler.types.TypeSymbol;
import souther.compiler.types.TypeSymbols;
import souther.compiler.types.TypeKey;
import souther.compiler.types.ValueName;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A node whose provenance is a pair holds the pair, or is refused.
 *
 * <p>What a fork is a fork of and which copy it stands in are true together, and so are what a kept
 * call applies and why it is here. Held as components apiece, half of either could be built, and the
 * halves were kept in step by a check the node ran over itself. Held as one value, that check is the
 * value's own — {@link Core.ForkPlace} and {@link Core.KeptCallPlace} refuse a half.
 *
 * <p>Which leaves the slot the value stands in. A pair that cannot be half made can still be absent,
 * and a node with no place at all says nothing about where it stands while every reader of it goes
 * on asking — so the first to ask reports a failure of this compiler as a failure of the model. The
 * value is refused where the node is built, which is the same place its halves are refused, so a
 * reader has the whole of it or the node was never made.
 */
class AProvenanceAPairIsHeldInIsRefusedWhereItIsMissingTest {

    private static final SourcePos POS = new SourcePos(1, 1);

    private static final TypeSymbol.AtModule DATA =
            TypeSymbols.declared(new TypeKey("demo", "Thing"));

    /** What each kind of node is, built with nothing where its pair goes. */
    private static Map<String, Runnable> withNoPlace() {
        Map<String, Runnable> out = new TreeMap<>();
        out.put("If", () -> new Core.If(bool(), one(), one(), null, Type.INT, POS));
        out.put("IfConstructed", () -> new Core.IfConstructed(construction(), binder(), one(),
                List.of(), null, Type.INT, POS));
        out.put("Match", () -> new Core.Match(one(), List.of(), null, Type.INT, POS));
        out.put("PreservedCall", () -> new Core.PreservedCall(
                KeptCalls.declared(ValueName.Stdlib.operation("List", "isEmpty")),
                List.of(one()), null, Type.BOOL, POS));
        return out;
    }

    @Test
    void aNodeWhosePlaceIsMissingIsNotBuilt() {
        List<String> built = new ArrayList<>();
        withNoPlace().forEach((kind, make) -> {
            try {
                make.run();
                built.add(kind);
            } catch (IllegalArgumentException expected) {
                // What this asks: the node was refused where it was built.
            }
        });

        assertEquals(List.of(), built,
                "a node built with nothing where its provenance goes is one every reader of it asks"
                        + " a question the node cannot answer");
    }

    private static Core bool() {
        return new Core.Bool(true, Type.BOOL, POS);
    }

    private static Core one() {
        return new Core.Int(1, Type.INT, POS);
    }

    private static Core.Binder binder() {
        return new Core.Binder("x",
                new souther.compiler.types.BindingId(
                        new souther.compiler.types.BindingOwner.OfValue("demo", "b"), 0));
    }

    private static Core.Construct construction() {
        return new Core.Construct(DATA, List.of(), Type.ref(DATA), POS);
    }
}
