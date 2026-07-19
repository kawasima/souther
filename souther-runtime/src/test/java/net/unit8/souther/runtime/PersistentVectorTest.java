package net.unit8.souther.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.junit.jupiter.api.Test;

/** {@link PersistentVector} against an {@link ArrayList} oracle, across sizes that span several trie
 *  depths (tail boundary 32, root overflow, and multi-level tries). */
class PersistentVectorTest {

    private static final int[] SIZES = {0, 1, 31, 32, 33, 63, 64, 65, 1023, 1024, 1025, 100_000};

    @Test
    void appendGetSizeMatchArrayList() {
        for (int n : SIZES) {
            List<Integer> oracle = new ArrayList<>();
            PersistentVector<Integer> pv = PersistentVector.empty();
            for (int i = 0; i < n; i++) {
                oracle.add(i);
                pv = pv.append(i);
            }
            assertEquals(n, pv.size(), "size at n=" + n);
            for (int i = 0; i < n; i++) {
                assertEquals(oracle.get(i), pv.get(i), "get(" + i + ") at n=" + n);
            }
        }
    }

    @Test
    void iteratorAndForEachAreInOrder() {
        for (int n : SIZES) {
            PersistentVector<Integer> pv = PersistentVector.empty();
            for (int i = 0; i < n; i++) {
                pv = pv.append(i);
            }
            int expected = 0;
            for (Integer v : pv) {
                assertEquals(expected++, v);
            }
            assertEquals(n, expected);

            int[] seen = {0};
            pv.forEach(v -> assertEquals(seen[0]++, v));
            assertEquals(n, seen[0]);
        }
    }

    @Test
    void appendedVersionsAreIndependent() {
        PersistentVector<Integer> a = PersistentVector.empty();
        for (int i = 0; i < 40; i++) {
            a = a.append(i);
        }
        PersistentVector<Integer> b = a.append(999);
        PersistentVector<Integer> aFinal = a;
        assertEquals(40, aFinal.size());
        assertEquals(41, b.size());
        assertEquals(999, b.get(40));
        assertThrows(IndexOutOfBoundsException.class, () -> aFinal.get(40));
    }

    @Test
    void fromSharesPersistentAndCopiesJdkLists() {
        PersistentVector<Integer> pv = PersistentVector.<Integer>empty().append(1).append(2);
        assertTrue(PersistentVector.from(pv) == pv, "from must share an existing PersistentVector");

        List<Integer> jdk = List.of(1, 2, 3, 33, 34);
        PersistentVector<Integer> copy = PersistentVector.from(jdk);
        assertEquals(jdk, copy);
        assertEquals(5, copy.size());
        assertTrue(PersistentVector.from(List.of()) == PersistentVector.EMPTY);
    }

    @Test
    void fromBuildsSameVectorAsAppendAcrossDepths() {
        for (int n : SIZES) {
            List<Integer> oracle = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                oracle.add(i);
            }
            // from() uses the bulk Builder; it must produce a vector identical to element-wise append.
            PersistentVector<Integer> viaBuilder = PersistentVector.from(oracle);
            PersistentVector<Integer> viaAppend = PersistentVector.empty();
            for (int i = 0; i < n; i++) {
                viaAppend = viaAppend.append(i);
            }
            assertEquals(n, viaBuilder.size(), "size at n=" + n);
            assertEquals(oracle, viaBuilder, "equals oracle at n=" + n);
            assertEquals(viaAppend, viaBuilder, "builder vs append at n=" + n);
            for (int i = 0; i < n; i++) {
                assertEquals(i, viaBuilder.get(i), "get(" + i + ") at n=" + n);
            }
            int expected = 0;
            for (Integer v : viaBuilder) {
                assertEquals(expected++, v);
            }
            assertEquals(n, expected);
        }
    }

    @Test
    void equalsAndHashCodeMatchArrayListCrossType() {
        for (int n : new int[] {0, 1, 32, 33, 1025}) {
            List<Integer> oracle = new ArrayList<>();
            PersistentVector<Integer> pv = PersistentVector.empty();
            for (int i = 0; i < n; i++) {
                oracle.add(i);
                pv = pv.append(i);
            }
            assertEquals(oracle, pv);
            assertEquals(pv, oracle);
            assertEquals(oracle.hashCode(), pv.hashCode(), "hashCode at n=" + n);
        }
        PersistentVector<Integer> a = PersistentVector.from(List.of(1, 2, 3));
        assertNotEquals(a, List.of(1, 2, 4));
        assertNotEquals(a, List.of(1, 2));
    }

    @Test
    void isImmutable() {
        PersistentVector<Integer> pv = PersistentVector.from(List.of(1, 2, 3));
        assertThrows(UnsupportedOperationException.class, () -> pv.add(4));
        assertThrows(UnsupportedOperationException.class, () -> pv.set(0, 9));
        assertThrows(UnsupportedOperationException.class, () -> pv.remove(0));
    }

    @Test
    void iteratorExhaustionThrows() {
        PersistentVector<Integer> pv = PersistentVector.ofSingle(7);
        Iterator<Integer> it = pv.iterator();
        assertTrue(it.hasNext());
        assertEquals(7, it.next());
        assertFalse(it.hasNext());
        assertThrows(java.util.NoSuchElementException.class, it::next);
    }
}
