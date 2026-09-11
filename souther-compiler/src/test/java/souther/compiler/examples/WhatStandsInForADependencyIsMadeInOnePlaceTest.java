package souther.compiler.examples;

import souther.compiler.WhatWasCompiled;
import souther.compiler.observe.RowStatements;
import souther.compiler.observe.StoodIn;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * What stands in for a dependency is read once, and what crosses is minted where that is decided.
 *
 * <p>Two halves come of reading a stand-in: something the behavior can be constructed with, which is
 * of the loader the implementation came from, and the values the row states, which are of nothing.
 * They are the same text read once — the helpers a stand-in names are applied where it is built, and
 * a second reading applies them again — so both come out of one place, and this says which.
 *
 * <p>Read off the class files rather than the source. A call is in the caller's constant pool
 * whatever the call site is spelled like, and a lambda's body is compiled into the class that wrote
 * it, so a second reading cannot get out of this by being written somewhere shorter.
 */
class WhatStandsInForADependencyIsMadeInOnePlaceTest {

    /**
     * A stand-in that crosses is made where it is held to what a reader may be given.
     *
     * <p>The one caller is the reading that asks whether every value it states is one these limits
     * keep. Minted anywhere else, a stand-in could carry a value larger than what is kept — and a
     * reader taking the values in one as values that are there would be reading a value nobody
     * wrote.
     */
    @Test
    void whatCrossesIsMintedByTheReadingThatChecksIt() {
        assertEquals(List.of("souther.compiler.observe.RowStatements$StandInRead"),
                List.copyOf(WhatWasCompiled.callersOf(StoodIn.class, "of")),
                "what a row states of a stand-in is made where it is decided that it may be");
    }

    /** And the reading is entered from the one place a row's stand-ins are read. */
    @Test
    void andTheReadingIsEnteredWhereARowIsRead() {
        assertEquals(List.of("souther.compiler.examples.ExampleVerifier"),
                List.copyOf(WhatWasCompiled.callersOf(RowStatements.StandInRead.class, "of")),
                "a row's stand-ins are read where the row is");
    }

    /**
     * And what a run applies the behavior with is minted in one place.
     *
     * <p>Two readings need one and they are different readings: what a written row states, read
     * where the rest of the row is, and what a search composed a candidate to stand in with. Each
     * works out what it answers for itself — that is the reading, and the two above hold it where it
     * belongs — and what a stand-in <em>is</em> is said once. Minted per reader, the day one of them
     * has to carry something more it would be carried by whichever reader was edited.
     */
    @Test
    void andSoIsWhatTheRunAppliesTheBehaviorWith() {
        assertEquals(List.of("souther.compiler.examples.StandingIn"),
                List.copyOf(WhatWasCompiled.callersOf(DependencyStandin.class, "<init>")),
                "what a run stands in with is made in one place");
    }
}
