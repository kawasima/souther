package souther.compiler.partition;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import souther.compiler.KeptCalls;
import souther.compiler.core.Core;
import souther.compiler.diag.SourcePos;
import souther.compiler.types.ValueName;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A walk over what a fork's answer turns on asks each question once.
 *
 * <p>What stops it. The walk follows what the library says an answer turns on and follows a name to
 * what it stands for, and what a name stands for is not this walk's to bound: a reading that
 * answered a closure with something the walk is already inside would go round for ever. So a
 * question already asked is one already answered.
 *
 * <p>Held against a reading that does go round, because nothing else can hold it. The models this
 * compiler has do not write one — a {@code let} cannot read the name it binds — so a walk over them
 * says nothing about whether the walk would stop if one arrived, and the alternative to stopping is
 * a compile that never finishes.
 *
 * <p>And it is the question that is asked once and not the object standing for it. A question is
 * built where it is asked, so two of them about one expression are two objects and one question —
 * which is what {@code Asked} says, and what says it holds is that whatever collects them asks it.
 */
class AWalkOverWhatAnAnswerTurnsOnAsksEachQuestionOnceTest {

    private static final ValueName.Stdlib.Operation ANY =
            new ValueName.Stdlib.Operation("List", "any");

    private static final SourcePos POS = new SourcePos(1, 1);

    /** {@code List.any} over a closure and a list, which is a truth its closure decides. */
    private static Core.PreservedCall anyOverAClosure() {
        return KeptCalls.to(ANY, POS);
    }

    /**
     * A closure that answers with the very call it is handed to.
     *
     * <p>The walk asks what the closure comes to, gets the call back, and asks the call what it
     * turns on again. Told each question once it says the rule is not among them; told nothing, it
     * asks for ever.
     */
    @Test
    @Timeout(10)
    void aClosureThatAnswersWithWhatItWasHandedToIsAskedOnce() {
        Core.PreservedCall call = anyOverAClosure();
        assertEquals(List.of(call),
                WhatAForkTests.partsOfTheAnswer(call, e -> e == call ? e : call),
                "a reading that leads back to the question it came from answers it once");
    }

    /**
     * And the walk still reaches what the closure decides where the reading is an ordinary one.
     *
     * <p>The negative control for the one above: a stop that fired on the first question asked
     * would make every fork turn on nothing, and both tests would pass.
     */
    @Test
    void aClosureIsStillReadForWhatItDecides() {
        Core.PreservedCall call = anyOverAClosure();
        assertEquals(List.of(call.args().get(0)), WhatAForkTests.partsOfTheAnswer(call, e -> e),
                "what the answer turns on is reached");
    }
}
