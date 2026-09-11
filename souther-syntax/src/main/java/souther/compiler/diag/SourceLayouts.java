package souther.compiler.diag;

/**
 * The texts a caller holds, able to say how any one of them is laid out.
 *
 * <p>What a pass that has to turn places into lines and columns is handed. Which text a place is in
 * is the place's own to say ({@link SourcePos#quotedFrom()}), and that is what this is asked —
 * rather than a file name, because the texts a compile reads places in are not all files. A body
 * spliced in from another module carries places in that module's published text, and a snippet
 * somebody parsed carries places in a text nothing has a name for; asked for a file, a caller
 * holding either would have nothing to ask with.
 *
 * <p>Handed in for the length of a pass and not kept. Whoever implements this is holding texts, and
 * a text is what a compilation was last given; an answer that kept one would be an answer about a
 * workspace that has moved on.
 */
public interface SourceLayouts {

    /** How the text {@code place} is in is laid out, or null where this holds no such text. */
    LaidOutText of(SourcePos place);

    /** Where {@code place} sits, or null where this holds no text to ask. */
    default PhysicalPos resolve(SourcePos place) {
        LaidOutText text = place == null ? null : of(place);
        return text == null ? null : text.resolve(place);
    }

    /** The same, for both ends of a region. */
    default PhysicalRegion resolve(Region region) {
        LaidOutText text = region == null ? null : of(region.start());
        return text == null ? null : text.resolve(region);
    }

    /** One that holds nothing — for a caller with no texts to hand over. */
    SourceLayouts NONE = _ -> null;
}
