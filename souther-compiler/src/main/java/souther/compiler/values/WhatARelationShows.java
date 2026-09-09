package souther.compiler.values;

/**
 * Every lack the denials between one alternative's blocks show, asked whatever its sides were left.
 *
 * <p>A question and not an answer, so that what asks it decides nothing about when it is asked.
 * Where a proof takes the lacks themselves, whoever handed them over has already decided whether to
 * look for them — and the decision they had to hand was whether some side of the alternative was
 * left nothing, which is the other half of the same proof. A proof built that way names whichever
 * of its two witnesses was asked about first.
 *
 * <p><b>Closed, and not a question anybody may write.</b> A question handed over as a lambda is one
 * its author may write over anything in reach, the blocks of the alternative included — so a type
 * that only promised to ask it would be promising about what its callers happened to write. What
 * may be asked is the two below. Neither maker is given a question about a block or an answer to
 * one, so a relation left unread because a block was refused is not something there is a way to
 * say.
 *
 * <p>Which leaves {@link Refusal#ofAnAlternative} asking this once and always, and the ordering
 * this type exists to rule out unwritable rather than merely unwritten.
 *
 * @param <A> what a position is called
 */
public final class WhatARelationShows<A> {

    private final Apartness<A> apart;

    /** How a reader holding the ranges settles the relation, or nothing where what is asked needs
     *  no values. */
    private final AskedOfARelation<A> relating;

    /** What the alternative says its blocks hold, which is what {@link #relating} settles the
     *  relation against. Nothing where there is no such reader. */
    private final AdmissibleValues.Box<A> product;

    private WhatARelationShows(Apartness<A> apart, AskedOfARelation<A> relating,
                               AdmissibleValues.Box<A> product) {
        this.apart = apart;
        this.relating = relating;
        this.product = product;
    }

    /**
     * What the denials say on their own, which is where a block is stated to differ from itself.
     *
     * <p>The whole of what a relation refuses without values in hand: no assignment satisfies a
     * pair whose ends are one block, whatever those blocks are left. Everything else a denial says
     * waits for the sets, and is the other question below.
     */
    public static <A> WhatARelationShows<A> statedApart(Apartness<A> apart) {
        return new WhatARelationShows<>(apart, null, null);
    }

    /**
     * What a reader holding the ranges settles the denials to, against what the blocks hold.
     *
     * <p>The product and not the blocks a walk found refused. What this question is about is the
     * relation read against what the alternative says its blocks admit, which is the alternative's
     * own account of them — where a walk asking it got to is not part of it, and is what would
     * make the answer turn on which question was asked first.
     */
    public static <A> WhatARelationShows<A> askedOf(AskedOfARelation<A> relating,
                                                    Apartness<A> apart,
                                                    AdmissibleValues.Box<A> product) {
        return new WhatARelationShows<>(apart, relating, product);
    }

    /** The lacks, which are none where the relation refuses nothing. */
    Lacks<A> shows() {
        if (relating == null) {
            return apart.apartFromThemselves();
        }
        return relating.of(apart, product) instanceof Apartness.Reduction.Nothing<A> it
                ? it.lacks() : Lacks.none();
    }
}
