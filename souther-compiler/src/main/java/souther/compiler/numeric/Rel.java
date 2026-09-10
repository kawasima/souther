package souther.compiler.numeric;

/**
 * A comparison of a {@link LinearForm} against zero.
 *
 * <p><b>Six values and what they mean at a number.</b> Nothing here is about what the rules leave
 * anywhere: a relation is what an author wrote between two sides, read into the one direction every
 * writing of it comes to. So a reader working out what a clause states can hold one without holding
 * anything that answers about the numbers it compares.
 *
 * <p>Which is why it is its own type and not {@link NumericDomain}'s, for the reason
 * {@link LinearForm} is: declared inside the domain, the vocabulary a comparison is written in could
 * not be named without naming what a domain answers.
 */
public enum Rel {
    GE, GT, LE, LT, EQ, NE;

    /**
     * Whether this holds where the two sides stand {@code signOfLeftMinusRight} apart.
     *
     * <p>The whole of what a relation means at a value, and the one place it is worked out. Every
     * reader that has a number and a relation asks this: a rule whose positions cancelled leaves
     * what is left over standing some way to nought, an assertion naming no position at all is its
     * own constant standing some way to nought, and a reading looking for a value that brings a
     * comparison out a given way is asking which ways there is anything to stand on. Each had a
     * table of six, and six-armed tables of one thing agree until one of them is edited.
     *
     * <p>Which way the difference is taken is not something the answer shows: read at nought the two
     * agree exactly, so a reader that handed over the difference the other way about would be right
     * about every equality and wrong about every ordering, and nothing between the two readings
     * would say so.
     *
     * @param signOfLeftMinusRight which way the left side of the comparison stands to the right:
     *                             negative below it, nought at it, positive above it
     */
    public boolean holds(int signOfLeftMinusRight) {
        return switch (this) {
            case GE -> signOfLeftMinusRight >= 0;
            case GT -> signOfLeftMinusRight > 0;
            case LE -> signOfLeftMinusRight <= 0;
            case LT -> signOfLeftMinusRight < 0;
            case EQ -> signOfLeftMinusRight == 0;
            case NE -> signOfLeftMinusRight != 0;
        };
    }

    /**
     * The relation that holds exactly where this one does not.
     *
     * <p>A relation's own answer, because it is the same six values read the other way and nothing
     * outside them decides it. A reader keeping its own denial has a second table which agrees with
     * this one only for as long as somebody keeps it so.
     */
    public Rel denied() {
        return switch (this) {
            case GE -> LT;
            case GT -> LE;
            case LE -> GT;
            case LT -> GE;
            case EQ -> NE;
            case NE -> EQ;
        };
    }

    /**
     * The one of this relation and its denial that stands for the pair.
     *
     * <p>Here because the pair is here. A reader that has to write one proposition down once —
     * where {@code n > 100} and {@code n <= 100} are one distinction and not two — needs the two
     * sides to pick the same side, and which side that is is a fact about these six values. Worked
     * out beside them, from the order they are declared in, it would move when anything moves them
     * and would be a second answer to what {@link #denied} already says.
     *
     * <p>Written out rather than computed, so a relation added is a case to decide about. What
     * decides it is nothing a reader is owed a reason for: the two sides of one distinction have to
     * agree, and either of them would do.
     */
    public Rel orItsDenial() {
        return switch (this) {
            case GE, LT -> GE;
            case GT, LE -> GT;
            case EQ, NE -> EQ;
        };
    }
}
