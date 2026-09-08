package souther.compiler.check;

import souther.compiler.KeptCalls;
import souther.compiler.core.Core;
import souther.compiler.diag.SourcePos;
import souther.compiler.types.ApplicationOrigin;
import souther.compiler.types.SourceConstruct;
import souther.compiler.types.SourceConstructOrigin;
import souther.compiler.types.SourceReferenceOrigin;
import souther.compiler.types.Type;
import souther.compiler.types.ValueName;
import souther.compiler.types.WrittenOwner;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The size a comparison is read as says where it came from, and says it from the call it was read
 * off.
 *
 * <p>{@link Conditions#asSizeComparison} answers with a call no source wrote: the size the written
 * one means, composed so that the rule can be read as the comparison it states. Giving it the
 * written call's own identity would put two applications under one, so it is a name and an
 * application of that pass's — each derived from the one the call it is read off reached.
 *
 * <p>Which makes what it derives from the question. A call an author wrote and one a generator
 * composed are two different things to have been read off, and the answer says which: composed is
 * not the same answer as saying nothing, and a reader telling only "does this have an identity"
 * from "which of the three is it" reports the second as the first.
 */
class ASizeComparisonIsDerivedFromTheCallItIsReadOffTest {

    private static final SourcePos POS = new SourcePos(1, 1);

    /** {@code List.isEmpty(xs)}, as a call this compiler kept for a reader. */
    private static Core.PreservedCall emptiness() {
        return KeptCalls.to(ValueName.Stdlib.operation("List", "isEmpty"),
                List.of(new Core.Str("", Type.STRING, POS)), Type.BOOL, POS);
    }

    /** The same, as an author's: it carries the name it applies and the application it is. */
    private static Core.PreservedCall written() {
        Core.PreservedCall kept = emptiness();
        return new Core.PreservedCall(kept.declared(), kept.args(),
                new SourceReferenceOrigin(new WrittenOwner.Body("demo", "b"), 0),
                new ApplicationOrigin.Written(SourceConstructOrigin.written(
                        new WrittenOwner.Body("demo", "b"), 0, SourceConstruct.CALL)),
                kept.type(), kept.pos());
    }

    /** Read off a term that carries its places, the size is an occurrence of its own. */
    @Test
    void anEmptinessCheckIsReadAsASizeComparison() {
        Core stated = Conditions.asSizeComparison(written());

        Core.Binary compared = assertInstanceOf(Core.Binary.class, stated);
        Core.PreservedCall size = assertInstanceOf(Core.PreservedCall.class, compared.left());
        assertInstanceOf(ApplicationOrigin.Derived.class, size.application());
    }

    /**
     * And read off a value the generator composed, it stays composed.
     *
     * <p>Composed is not the same answer as saying nothing. A fixture's call carries why it is
     * there, and turning that into a term with nothing behind it would say a normalization had
     * happened where none did — which is what a reader telling only "does this have an identity"
     * from "which of the three is it" comes to.
     */
    @Test
    void andAValueTheGeneratorComposedStaysComposed() {
        Core stated = Conditions.asSizeComparison(emptiness());

        Core.Binary compared = assertInstanceOf(Core.Binary.class, stated);
        Core.PreservedCall size = assertInstanceOf(Core.PreservedCall.class, compared.left());
        assertInstanceOf(ApplicationOrigin.ComposedFixture.class, size.application());
    }
}
