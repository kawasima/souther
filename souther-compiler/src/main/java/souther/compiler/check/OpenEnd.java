package souther.compiler.check;

/**
 * One line a leaf states on a number an operation answers that nothing here could place.
 *
 * <p>The occurrence and not the number. A rule can state a line on one length in two places, and
 * what a choice does to one of them is not what it does to the other: an end an alternative beside
 * it settles is gone, and an end written outside that choice stands. Told apart by the number
 * alone, the second would answer for the first — the reading strikes one off, the number is still
 * open because of the other, and the choice an author is sent to comes back for an end that choice
 * had already settled.
 *
 * <p><b>So this is equal to itself and to nothing else</b>, and it is made where the leaf is read —
 * once per line an author wrote, as a {@link ClauseOccurrence} is made once per occurrence of a
 * clause. The tree is walked
 * once and the settled reading is composed over what that walk produced, so two of these are two
 * places in the source and never one place counted twice.
 *
 * <p><b>It reaches no answer this compiler publishes.</b> What goes out is the number and the
 * choice ({@code FieldDomains.EndLeftOpen}), which compare as values; this is how the two sides of
 * one reading find each other on the way there, and a published answer holding it would be one that
 * says nothing of itself.
 */
final class OpenEnd {

    private final DerivedNumber number;

    OpenEnd(DerivedNumber number) {
        if (number == null) {
            throw new IllegalArgumentException("an end left open is an end of some number");
        }
        this.number = number;
    }

    /** Which number this is an end of. */
    DerivedNumber number() {
        return number;
    }

    @Override
    public String toString() {
        return "an unplaced end of " + number;
    }
}
