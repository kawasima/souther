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
 * Taking a term's places out does not take away what it states.
 *
 * <p>{@link Core#withoutItsPlace} drops what a call applies and why it is here, so that two readings
 * of one term compare equal however the file around them was edited. What it does not drop is the
 * meaning: a caller reads its assumptions out of exactly such a term — {@code Bodies.Stated} answers
 * with one — so a reader that declined to read a normalized term would take a meaning away from that
 * caller for the sake of an identity the caller never asked for.
 *
 * <p>So an emptiness check is read as the size comparison it means either way. What differs is only
 * how much the answer can say about where it came from: read off a term that carries its places, the
 * size is an occurrence of its own derived from the one the comparison reached; read off one that
 * does not, it says as little as the term it was read off does.
 */
class ANormalizedTermStatesWhatTheTermItCameFromStatesTest {

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
     * And read off the same term with its places taken out, it is read as the same comparison. The
     * answer says as little about where it came from as its input does, and no less about what it
     * means.
     */
    @Test
    void andItIsReadTheSameWayOnceThePlacesAreTakenOut() {
        Core normalized = Core.withoutItsPlace(written());

        Core stated = Conditions.asSizeComparison(normalized);

        Core.Binary compared = assertInstanceOf(Core.Binary.class, stated);
        Core.PreservedCall size = assertInstanceOf(Core.PreservedCall.class, compared.left());
        assertEquals(Type.INT, size.type());
        assertNull(size.application(),
                "read off a term that says nothing about where it came from, it says nothing either");
        assertNull(size.reference());
    }

    /**
     * And the two come to one term once the places are out of both, which is what taking them out
     * is for: a caller reading a normalized contract must reach the value it would have reached
     * from the term that contract was normalized from.
     *
     * <p>Compared with the places taken out of each. What the rewrite mints for the comparison it
     * builds is its own, and a place is exactly what this walk exists to make two readings agree
     * about — so comparing before taking them out would be asking the two orders to agree about the
     * thing neither is being read for.
     */
    @Test
    void andTheTwoReadingsComeToOneTermOnceThePlacesAreOut() {
        Core fromWritten = Core.withoutItsPlace(Conditions.asSizeComparison(written()));
        Core fromNormalized =
                Core.withoutItsPlace(Conditions.asSizeComparison(Core.withoutItsPlace(written())));

        assertEquals(fromWritten, fromNormalized,
                "reading a term and reading its normalized form come to one value");
    }
}
