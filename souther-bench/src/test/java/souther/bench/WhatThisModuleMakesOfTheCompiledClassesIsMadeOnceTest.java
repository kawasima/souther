package souther.bench;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void theReactorsCodeIsDecodedOnceHoweverManyTimesItIsAsked() {
        Compiled.sites();
        Compiled.sites();

        assertEquals(1, Compiled.decodings(),
                "the reactor's code was decoded more than once, so every check here decodes it for"
                        + " itself and sharing the files bought nothing");
    }

    /**
     * And both of what it comes to are that one walk.
     *
     * <p>Asked of the sites themselves rather than of a count. A count says how many times something
     * filled a store, which is a fact about the store: a second walk written beside it fills nothing
     * and is counted nowhere, and the count goes on saying one. What cannot be true of two walks is
     * that the site an invocation carries <em>is</em> the site the other answer holds — two walks
     * build two of it, equal and not the same.
     */
    @Test
    void andBothOfWhatItComesToAreThatOneWalk() {
        Set<Compiled.Site> byIdentity = Collections.newSetFromMap(new IdentityHashMap<>());
        byIdentity.addAll(Compiled.sites());

        Compiled.Site carried = Compiled.invocations().getFirst().site();

        assertTrue(byIdentity.contains(carried),
                "what a call is was worked out twice: once as a site saying a call was made and"
                        + " once as the same site with the text read at it. They are two projections"
                        + " of one walk, and answered apart they are two walks");
    }

    /**
     * And what is shared cannot be changed by whoever asked for it first.
     *
     * <p>Made per ask, a list was the caller's own to spoil. Shared for the length of the fork, the
     * lifetime is shared and so is every way of changing it: one check emptying what it was handed
     * would answer for every check after it, and each of those would be reading the reactor's code
     * as somebody else left it.
     */
    @Test
    void andWhatIsSharedIsNotOneCallersToChange() {
        int held = Compiled.sites().size();

        assertThrows(UnsupportedOperationException.class, () -> Compiled.sites().clear());
        assertEquals(held, Compiled.sites().size(),
                "a caller changed what the rest of this fork reads");
    }
}
