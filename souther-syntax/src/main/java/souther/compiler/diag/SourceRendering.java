package souther.compiler.diag;

/**
 * What a report is written against: what to call each source, and how each of them is laid out.
 *
 * <p>Two capabilities carried together and neither of them this. Ask
 * {@code rendering.names().nameOf(id)} and {@code rendering.layouts().resolve(place)} — nothing here
 * answers either question, and nothing here is a third way to ask one. What this is for is that
 * every boundary that writes a result out for somebody to read needs both at once, and saying so
 * once is better than sixteen signatures saying it in step.
 *
 * <p>The two belong together because they are the same kind of thing. A result holds what identifies
 * a source and where in it a place is; what that source is called, and what line that place is at,
 * are both facts about the world the result is being shown in, decided when it is shown and not
 * when it was worked out. A name depends on which other files are being read beside it; a line
 * depends on what the file now says. Neither survives being carried.
 *
 * <p>Not {@link SourceContextResolver}, which is an operation rather than a pair of capabilities:
 * it answers, for one source, the context a diagnostic is rendered against. What builds one of those
 * is this.
 *
 * @param names what to call each source in front of a person
 * @param layouts how each source is laid out at the moment
 */
public record SourceRendering(SourceNameResolver names, SourceLayouts layouts) {

    /**
     * Sources that stand for themselves, laid out as {@code layouts} says.
     *
     * <p>Only the names have an identity to fall back on. Turning a place into a line needs the
     * text, always, so there is nothing here that supplies both halves out of nothing — a caller
     * with no texts hands over {@link SourceLayouts#NONE} and says so.
     */
    public static SourceRendering namedByIdentity(SourceLayouts layouts) {
        return new SourceRendering(SourceNameResolver.identity(), layouts);
    }
}
