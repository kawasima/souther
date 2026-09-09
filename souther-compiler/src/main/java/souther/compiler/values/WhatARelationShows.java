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
 * <p><b>Which is what this closes and not that the lacks are worked out late.</b> What is passed
 * here is written where the relation is, out of the relation alone: it has no side of the
 * alternative to read, so there is nothing for it to be conditional on. {@link
 * Refusal#ofAnAlternative} asks it once and always.
 *
 * <p>Beside {@link AskedOfARelation} and narrower. That one is how a reader holding the ranges
 * says what a relation comes to against them; this is what any such answer amounts to once it is
 * reached, which is the lacks or none of them.
 *
 * @param <A> what a position is called
 */
@FunctionalInterface
public interface WhatARelationShows<A> {

    /** The lacks, which are none where the relation refuses nothing. */
    Lacks<A> shows();
}
