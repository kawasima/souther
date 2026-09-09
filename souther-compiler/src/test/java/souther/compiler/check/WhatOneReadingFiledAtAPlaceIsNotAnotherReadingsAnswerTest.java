package souther.compiler.check;

import org.junit.jupiter.api.Test;

import souther.compiler.core.Core;
import souther.compiler.diag.SourcePos;
import souther.compiler.types.BinOp;
import souther.compiler.types.ConstructOccurrence;
import souther.compiler.types.Type;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Two readings of one place in one clause are two answers, and neither is the other's.
 *
 * <p>A rule is read once per place the walk opens a value at. A record holding a bounded type at
 * two of its fields is held to that type's clause at both, and each reading raises the questions of
 * the field it was read at — so one written conjunct has as many answers as there were readings,
 * and they are not the same answer.
 *
 * <p>Where in the clause a conjunct stands does not say which of them is speaking. Every clause has
 * a first place and both readings walk the same one, so an answer filed under the coordinate alone
 * lands where the other reading's answer already is — and what a reader takes out is the two of them
 * put together, which is what neither reading said.
 *
 * <p>The node the answer used to be filed under said both things at once: each reading walked the
 * tree a substitution built for it, so which object it was said which reading it was. Held apart,
 * both halves have to be in the address.
 *
 * <p>The control is below: the two answers put together is a third answer, and it is what the wrong
 * address hands back.
 */
class WhatOneReadingFiledAtAPlaceIsNotAnotherReadingsAnswerTest {

    private static final SourcePos POS = new SourcePos(1, 1);

    /** {@code a == a && b == b}, read into the shape that hands out the places. */
    private static final ClauseExpr.Joined CLAUSE = (ClauseExpr.Joined) ClauseExpr.of(
            new Core.Binary(BinOp.AND, leaf("a"), leaf("b"),
                    ConstructOccurrence.unwritten(), Type.BOOL, POS), true);

    /** What one reading made of the left conjunct, and what another made of the same conjunct.
     *  Two answers a reader can tell apart, so finding the wrong one is a failure. */
    private static final Required RELATES =
            Required.ofInvariant(new ClauseStates.ARelation());
    private static final Required CONSTRAINS_NOTHING =
            Required.ofInvariant(new ClauseStates.NoRestriction());

    @Test
    void twoReadingsOfOneConjunctFileTwoAnswers() {
        InvariantChecker.ReadingPlace here = new InvariantChecker.ReadingPlace(
                new InvariantChecker.ReadingId(0), CLAUSE.left().at());
        InvariantChecker.ReadingPlace there = new InvariantChecker.ReadingPlace(
                new InvariantChecker.ReadingId(1), CLAUSE.left().at());
        assertEquals(here.at(), there.at(),
                "the same conjunct of the same clause, which is what the two readings share");

        Map<InvariantChecker.ReadingPlace, Required> raised = filed(here, there);

        assertEquals(2, raised.size(),
                "two readings of one conjunct are two answers about it");
        assertEquals(RELATES, raised.get(here),
                "what the first reading made of the conjunct, asked for where it filed it");
        assertEquals(CONSTRAINS_NOTHING, raised.get(there),
                "and what the second made of it, which is a different answer about one conjunct");
    }

    /**
     * And the control: what an address short of the reading hands back.
     *
     * <p>The two are gathered the way a clause's conjuncts are, so an address the two readings
     * share does not lose one of them — it answers with both, for a reading that said one.
     */
    @Test
    void andTheTwoTogetherIsWhatNeitherReadingSaid() {
        Required both = Required.and(RELATES, CONSTRAINS_NOTHING);
        assertNotEquals(RELATES, both,
                "the two put together is not what the first reading said");
        assertNotEquals(CONSTRAINS_NOTHING, both,
                "nor what the second did");
    }

    /** The two answers, filed as the walk files them. */
    private static Map<InvariantChecker.ReadingPlace, Required> filed(
            InvariantChecker.ReadingPlace here, InvariantChecker.ReadingPlace there) {
        Map<InvariantChecker.ReadingPlace, Required> raised = new LinkedHashMap<>();
        raised.merge(here, RELATES, Required::and);
        raised.merge(there, CONSTRAINS_NOTHING, Required::and);
        return raised;
    }

    /** A clause of no connective, named by which of them it is. */
    private static Core leaf(String named) {
        return new Core.Binary(BinOp.EQ, new Core.Str(named, Type.STRING, POS),
                new Core.Str(named, Type.STRING, POS), ConstructOccurrence.unwritten(),
                Type.BOOL, POS);
    }
}
