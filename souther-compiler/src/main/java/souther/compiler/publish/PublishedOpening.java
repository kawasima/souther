package souther.compiler.publish;

import souther.compiler.observe.RunSensitivity;

import java.util.Optional;

/**
 * One entry of what a document says holds an adequacy verdict open, as the document writes it.
 *
 * <p>The whole of what the schema has room for: which kind of thing it is, what it is about, the
 * reason beside it where the kind has one, and whether a wider run of this compiler could answer
 * it. What the verdict rests on is how the entry came to be, and a reader is shown none of that.
 *
 * <p><b>Which is why two of these can be equal.</b> The array's unit is the fact, so two measures
 * that went without two rules this compiler could not read are two entries; the document calls both
 * of them the same thing, and it says it twice. Folded, the array would count kinds, which is what
 * the array beside it already does.
 *
 * <p>Two alike are now two the document says the same thing about at the same place, which is a
 * smaller set than it was: what an entry is about used to be left out of what a document writes, so
 * every measure of one module that nobody made came out as one entry repeated.
 *
 * <p>So an order over these tells apart everything a reader can and nothing a reader cannot, and
 * two entries it cannot tell apart are two entries a document writes alike
 * ({@link CanonicalArrangement}).
 */
public record PublishedOpening(Kind kind, Optional<NotMeasuredWord> reason,
                               RunSensitivity runSensitivity, PublishedSubject about) {

    public PublishedOpening {
        if (kind == null || runSensitivity == null) {
            throw new IllegalArgumentException("a verdict held open by nothing is settled");
        }
        if (about == null) {
            throw new IllegalArgumentException("a verdict is held open on something");
        }
        reason = reason == null ? Optional.empty() : reason;
    }

    /**
     * What a document calls one of these, in the two vocabularies it has for them.
     *
     * <p>A measure that went without something writes the word that weakening already has, because
     * that vocabulary exists and a second spelling of it would be a second thing to keep in step.
     * Everything else is a word of this array's own. Held apart rather than folded to the string
     * both come out as, so that the order over them is over the words and not over the alphabet.
     */
    public sealed interface Kind {

        /** A measure was made and went without something, said in the word that weakening has. */
        record AWeakening(WeakeningVocabulary said) implements Kind {

            public AWeakening {
                if (said == null) {
                    throw new IllegalArgumentException("a measure went without something");
                }
            }
        }

        /** One of the ways a verdict stays open that no weakening covers. */
        record AnOpening(AdequacyOpeningWord said) implements Kind {

            public AnOpening {
                if (said == null) {
                    throw new IllegalArgumentException("a verdict is open on something");
                }
            }
        }
    }
}
