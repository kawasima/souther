package souther.compiler.publish;

/**
 * What a field of a document says, for the fields that sometimes say it about a rule.
 *
 * <p>A sum and not a string, because whether a handle is in there is what the schema says of the
 * field and what a reader acts on. Handed the finished words, a field could carry a sentence with a
 * handle spelled by whoever built it — which is the shape this whole issue is about, one layer down:
 * the sentence would be right today and free to come apart from the one every other field writes.
 *
 * <p>So the handle is handed over as a handle and the words around it as words, and what puts them
 * together is {@link RuleHandleSurface}. A field that carries one is then a field that carries one
 * because there was no way to write it otherwise.
 */
public sealed interface PublishedSentence {

    /** Words about something that is not a rule — a class, an arm, a case of an input. */
    record Words(String said) implements PublishedSentence {

        public Words {
            if (said == null || said.isEmpty()) {
                throw new IllegalArgumentException("a field a document writes says something");
            }
        }
    }

    /**
     * A rule handle with words of the field's own around it.
     *
     * <p>Either side may be empty, which is a field that says the handle and nothing else. Empty is
     * the words there are and not a missing answer: what a border writes after the handle is the
     * declarations that took an end of its line in, and a line nothing narrowed has none of those.
     */
    record AroundAHandle(String before, PublishedRuleHandle handle, String after)
            implements PublishedSentence {

        public AroundAHandle {
            if (before == null || handle == null || after == null) {
                throw new IllegalArgumentException("a sentence about a rule is words, a handle and"
                        + " words: " + before + " " + handle + " " + after);
            }
        }

        /** The handle by itself, for a field whose whole sentence it is. */
        public static AroundAHandle alone(PublishedRuleHandle handle) {
            return new AroundAHandle("", handle, "");
        }
    }
}
