package souther.compiler.inputs;

import org.junit.jupiter.api.Test;

import souther.compiler.check.DeclarationNewtypes;
import souther.compiler.core.Core;
import souther.compiler.diag.SourcePos;
import souther.compiler.types.BindingId;
import souther.compiler.types.BindingOwner;
import souther.compiler.types.Type;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * How many names a closure's answer stands under does not decide where in the element it is.
 *
 * <p>The law beside the one held of input positions, and not the same law. That one is about a walk
 * to a position of the input, with the roots, the containers and the provenance of elements to
 * reach it by; this is about a way inside one value, read from a name, a {@code let} and a field
 * and nothing else. Two readings, two statements, and a number reintroduced into either is caught
 * by its own.
 *
 * <p>Held here rather than through a model, because what this reading takes is a closure's answer
 * and the names in the body it was left in — and a body binds those names for reasons of its own,
 * so a model grown until the chain is long is a model grown for something else. What the chain is
 * long by is the point.
 */
class HowManyNamesAClosuresAnswerStandsUnderDoesNotDecideItsProjectionTest {

    private static final SourcePos POS = new SourcePos(0, 0);
    private static final BindingOwner OWNER = new BindingOwner.OfValue("example", "f");
    private static final BindingId ELEMENT = new BindingId(OWNER, 0);

    private static Core.Read read(String name, BindingId binding) {
        return new Core.Read(name, binding, Type.INT, POS);
    }

    /**
     * A closure answering the element's amount, under {@code names} names standing for each other.
     *
     * <p>Which is what a body leaves behind once a helper applied to the element is spliced into it,
     * one name per helper: the answer is a name, and following it arrives at the field.
     */
    private static ElementProjection projectionUnder(int names) {
        Map<BindingId, Core> held = new LinkedHashMap<>();
        for (int n = 1; n < names; n++) {
            held.put(new BindingId(OWNER, n), read("a" + (n + 1), new BindingId(OWNER, n + 1)));
        }
        held.put(new BindingId(OWNER, names), new Core.FieldAccess(read("一件", ELEMENT), "金額",
                Type.INT, POS));
        return ElementProjection.read(read("a1", new BindingId(OWNER, 1)), ELEMENT, held,
                DeclarationNewtypes.NONE);
    }

    /** One name between the answer and the field, or sixteen: the way inside the element is one
     *  step either way. */
    @Test
    void theSameProjectionIsReadHoweverManyNamesStandBetween() {
        Map<Integer, ElementProjection> read = new LinkedHashMap<>();
        for (int names : new int[] {1, 4, 8, 16}) {
            read.put(names, projectionUnder(names));
        }

        assertEquals(Map.of(1, new ElementProjection(List.of("金額")),
                        4, new ElementProjection(List.of("金額")),
                        8, new ElementProjection(List.of("金額")),
                        16, new ElementProjection(List.of("金額"))),
                read, () -> "one way inside the element however many names stand between: " + read);
    }

    /** And a name that stands for nothing this reading has is not a way anywhere, at any length. */
    @Test
    void aNameStandingForNothingIsNoProjectionAtAnyLength() {
        Map<BindingId, Core> held = new LinkedHashMap<>();
        for (int n = 1; n < 16; n++) {
            held.put(new BindingId(OWNER, n), read("a" + (n + 1), new BindingId(OWNER, n + 1)));
        }

        assertEquals(null, ElementProjection.read(read("a1", new BindingId(OWNER, 1)), ELEMENT,
                held, DeclarationNewtypes.NONE));
    }
}
