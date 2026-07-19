package net.unit8.souther.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** {@link PersistentHashSet} against a {@link HashSet} oracle. */
class PersistentHashSetTest {

    @Test
    void withWithoutContainsMatchOracleRandomized() {
        Random rnd = new Random(9);
        for (int trial = 0; trial < 40; trial++) {
            Set<Integer> oracle = new HashSet<>();
            PersistentHashSet<Integer> phs = PersistentHashSet.empty();
            for (int op = 0; op < 300; op++) {
                int v = rnd.nextInt(60);
                if (rnd.nextInt(3) == 0) {
                    oracle.remove(v);
                    phs = phs.without(v);
                } else {
                    oracle.add(v);
                    phs = phs.with(v);
                }
                assertEquals(oracle.size(), phs.size());
            }
            for (int v = 0; v < 60; v++) {
                assertEquals(oracle.contains(v), phs.contains(v));
            }
            assertEquals(oracle, phs);
            assertEquals(phs, oracle);
            assertEquals(oracle.hashCode(), phs.hashCode());
        }
    }

    @Test
    void algebraMatchesJdkOps() {
        Random rnd = new Random(3);
        for (int trial = 0; trial < 30; trial++) {
            Set<Integer> a = new HashSet<>();
            Set<Integer> b = new HashSet<>();
            for (int i = 0; i < 40; i++) {
                a.add(rnd.nextInt(50));
                b.add(rnd.nextInt(50));
            }
            PersistentHashSet<Integer> pa = PersistentHashSet.from(a);
            PersistentHashSet<Integer> pb = PersistentHashSet.from(b);

            Set<Integer> union = new HashSet<>(a);
            union.addAll(b);
            assertEquals(union, PersistentHashSet.union(pa, pb));

            Set<Integer> inter = new HashSet<>(a);
            inter.retainAll(b);
            assertEquals(inter, PersistentHashSet.intersect(pa, pb));

            Set<Integer> diff = new HashSet<>(a);
            diff.removeAll(b);
            assertEquals(diff, PersistentHashSet.difference(pa, pb));
        }
    }

    @Test
    void fromDedupsAndSharesPersistent() {
        PersistentHashSet<Integer> s = PersistentHashSet.from(List.of(1, 2, 2, 3, 3, 3));
        assertEquals(Set.of(1, 2, 3), s);
        assertEquals(3, s.size());
        assertTrue(PersistentHashSet.from(s) == s);
        assertTrue(PersistentHashSet.from(List.of()) == PersistentHashSet.EMPTY);
    }

    @Test
    void iterationOrderDeterministic() {
        Set<String> src = new LinkedHashSet<>(List.of("a", "b", "c", "d"));
        assertEquals(PersistentHashSet.from(src).toString(), PersistentHashSet.from(src).toString());
    }

    @Test
    void isImmutable() {
        PersistentHashSet<Integer> s = PersistentHashSet.from(List.of(1, 2, 3));
        assertThrows(UnsupportedOperationException.class, () -> s.add(4));
        assertThrows(UnsupportedOperationException.class, () -> s.remove(1));
    }

    @Test
    void emptyQueries() {
        PersistentHashSet<Integer> e = PersistentHashSet.empty();
        assertEquals(0, e.size());
        assertTrue(e.isEmpty());
        assertFalse(e.contains(1));
        assertFalse(e.iterator().hasNext());
    }
}
