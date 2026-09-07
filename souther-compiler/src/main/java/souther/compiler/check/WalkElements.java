package souther.compiler.check;

import souther.compiler.core.Core;
import souther.compiler.core.GrowingFold;
import souther.compiler.inputs.InputReads;
import souther.compiler.inputs.ReadMeaning;
import souther.compiler.types.BindingId;

/**
 * Which binding a walk hands one element to, whichever representation the walk is in.
 *
 * <p>One question, two shapes. Where the language's own operations stand, the operation says what it
 * hands its closure ({@link Combinators}) and the block is right there; where they have been
 * expanded into what they do, the walk is what a rewrite left and the binding is recovered from that
 * shape ({@link GrowingFold#elementBindingOf}). A reader wanting the binding wants it either way, so
 * it asks here rather than knowing which tree it is holding.
 *
 * <p><b>An identity and not a meaning.</b> What comes back is which binding the element arrives
 * under. Whether the walk answers one value per element, where in an element the answer stands and
 * which position the elements are at are three further questions with three other owners — and a
 * reader that took this for any of them would be reading a meaning off a shape.
 */
public final class WalkElements {

    private WalkElements() {}

    /**
     * The binding one element of {@code walk} arrives under, or null where {@code walk} is not a
     * walk this can read.
     *
     * <p>{@code where} is what the names around the walk stand for, which is how a closure written
     * as a name reaches the block it is. Asked of the reading that owns that question rather than
     * read off the tree: a model of any size binds the block before handing it over.
     */
    public static BindingId elementBindingOf(Core walk, InputReads where, Symbols symbols) {
        if (walk instanceof Core.PreservedCall call) {
            Combinators.Handed handed =
                    Combinators.handedTo(call, closure -> blockOf(closure, where, symbols));
            return handed == null ? null : handed.element().binding();
        }
        return GrowingFold.elementBindingOf(walk);
    }

    /** The block {@code closure} is, following the names it was given through. */
    private static Core.Block blockOf(Core closure, InputReads where, Symbols symbols) {
        Core at = closure;
        InputReads reads = where;
        java.util.Set<BindingId> met = new java.util.HashSet<>();
        while (at instanceof Core.Read read) {
            // By the bindings met, so a name that came round to itself stops rather than being
            // followed again.
            if (!met.add(read.binding())
                    || !(reads.meaningOf(read, symbols) instanceof ReadMeaning.Through through)) {
                return null;
            }
            at = through.denotes().value();
            reads = through.denotes().at();
        }
        return at instanceof Core.Block block ? block : null;
    }
}
