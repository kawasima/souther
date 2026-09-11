package souther.compiler.diag;

/**
 * A text somebody holds, able to say where each of the places in it sits at the moment.
 *
 * <p>What turns a {@link SourcePos} into a {@link PhysicalPos}. A place says which of the things
 * written in a text it is, and where that thing sits is a fact about how the text is laid out now —
 * so it is answered by whoever has the text, and answered again after they are handed another one.
 *
 * <p>Asked for as this rather than as what answers it, because working out which of a text's tokens
 * a place names is reading syntax, and nothing that renders a report reads syntax. The one
 * implementation is {@code SourceLayout}, which is where a text becomes places in the first place.
 */
public interface LaidOutText {

    /** Where {@code place} sits in this text as it now stands. */
    PhysicalPos resolve(SourcePos place);

    /** The same, for both ends of a region. */
    default PhysicalRegion resolve(Region region) {
        return new PhysicalRegion(resolve(region.start()), resolve(region.end()));
    }
}
