package souther.bench;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The reading of the class files is shared with the whole fork; what this module makes of it is
 * shared here.
 *
 * <p>They are two things and only the first is somebody else's. Sharing the files and then decoding
 * them again per check would leave the reading shared and the work not: every check here walks the
 * code of every class the reactor built, and the walk allocates a site for every call, field access
 * and case there is. Read per check, that is the same decoding over and over on classes that were
 * already parsed.
 *
 * <p>Counted rather than compared. What these hold is a site for every call the reactor's code
 * makes, and a check asking whether it was handed the same list twice says so by printing both of
 * them. How many times one was worked out is the same fact in a number.
 */
class WhatThisModuleMakesOfTheCompiledClassesIsMadeOnceTest {

    @Test
    void everythingTheCompiledClassesDoIsWorkedOutOnce() {
        Compiled.sites();
        Compiled.sites();

        assertEquals(1, Compiled.timesWorkedOut("sites"),
                "what the compiled classes do was worked out more than once, so every check here"
                        + " decodes the reactor's code for itself and sharing the files bought"
                        + " nothing");
    }

    @Test
    void andSoIsTheTextReadAtEachCall() {
        Compiled.invocations();
        Compiled.invocations();

        assertEquals(1, Compiled.timesWorkedOut("invocations"),
                "the text read at each call was worked out more than once, beside the sites and for"
                        + " the same walk over the same classes");
    }
}
