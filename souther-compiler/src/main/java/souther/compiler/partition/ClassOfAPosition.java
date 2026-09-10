package souther.compiler.partition;

/**
 * One class of one position: an axis, and which of the classes that axis divides it into.
 *
 * <p>What a row is owed for, and what tells one such thing from every other. The axis names the
 * behavior, so two behaviors dividing their own positions the same way are two of these.
 *
 * <p>Its own type rather than the generator's, because the generator is not what says which classes
 * there are. What a position divides into is the model's answer, read by the partition; a search is
 * one of the readers that asks about it, beside the account that keeps it and the document that
 * publishes it. Held inside the search, the identity every one of them joins on would have been the
 * search's own vocabulary, and an account rooted there says a row is owed for what a search happens
 * to be able to look for.
 */
public record ClassOfAPosition(AxisId at, String classId) {

    public ClassOfAPosition {
        if (at == null || classId == null) {
            throw new IllegalArgumentException(
                    "a class of a position is some class of some axis: " + at + "/" + classId);
        }
    }
}
