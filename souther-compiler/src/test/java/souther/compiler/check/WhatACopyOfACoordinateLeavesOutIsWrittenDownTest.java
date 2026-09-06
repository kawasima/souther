package souther.compiler.check;

import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A record built by copying fields out of another says which it left behind.
 *
 * <p>{@code FieldDomains.NoLine} is written from a coordinate the reading found, one field at a
 * time, because the coordinate is this class's own and the record crosses into {@code inputs}. Two
 * of its fields went in one round late apiece — first which number of the position it is, then
 * which conjunct the reason came from — each after something downstream turned out to need what the
 * producer already had, and each looked like an unrelated fix. The first arrived twice: as a flag
 * saying a number was taken, and then as the number itself, because the flag could not tell two
 * operations over one path apart (#1050).
 *
 * <p>So what the copy leaves out is written down rather than discovered. A field added to the
 * coordinate fails here, which is the point: the answer may well be that the copy does not want it,
 * and that is a decision somebody makes rather than a thing nobody noticed.
 */
class WhatACopyOfACoordinateLeavesOutIsWrittenDownTest {

    private static Set<String> componentsOf(Class<?> of) {
        Set<String> out = new LinkedHashSet<>();
        for (RecordComponent each : of.getRecordComponents()) {
            out.add(each.getName());
        }
        return out;
    }

    /** The reading's coordinate, which is package-private and reached by name. */
    private static Class<?> coordinate() {
        for (Class<?> each : InvariantChecker.class.getDeclaredClasses()) {
            if (each.getSimpleName().equals("Coordinate")) {
                return each;
            }
        }
        throw new AssertionError("the reading no longer has a coordinate");
    }

    /**
     * Every field of the coordinate is carried, except the carrier, which nothing has asked for.
     *
     * <p>Not a rule that a copy must be whole. A copy that leaves something out is fine and is often
     * right; what is not fine is nobody knowing which. The carrier is what a position's values are
     * ordered on, and no reader of an unread rule has wanted it — a reader that does will find it
     * here rather than in a round of its own.
     */
    @Test
    void theCopyCarriesEveryFieldOfTheCoordinateButTheCarrier() {
        Set<String> left = new LinkedHashSet<>(componentsOf(coordinate()));
        left.removeAll(componentsOf(FieldDomains.NoLine.class));

        assertEquals(Set.of("carrier"), left,
                "a field of the coordinate that the copy does not carry, and nobody decided to");
    }

    /** And what it adds is what the copy is for: whose rule it is, and which conjunct. */
    @Test
    void andWhatItAddsIsWhatTheCopyIsFor() {
        Set<String> added = new LinkedHashSet<>(componentsOf(FieldDomains.NoLine.class));
        added.removeAll(componentsOf(coordinate()));

        assertEquals(Set.of("part", "read", "why"), added,
                "which part of which rule this is, that part's own expression, and what stopped"
                        + " it. The name beside the expression because two identical parts of one"
                        + " clause are two lines, and nothing about the expression tells them"
                        + " apart");
        assertTrue(componentsOf(FieldDomains.NoLine.class).contains("at"),
                "beside the one of the coordinate that a reader downstream turned out to need:"
                        + " which number of which position the end was to have been on. One"
                        + " component and not a path beside a flag — a path measured by two"
                        + " operations has two of these, and the flag brought them to one");
    }
}
