package net.unit8.souther.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** {@link PersistentHashMap} against a {@link HashMap} oracle, including forced hash collisions. */
class PersistentHashMapTest {

    /** A key whose hashCode we control, to force CHAMP collision paths (equal hash, distinct value). */
    private record Key(int id, int hash) {
        @Override
        public int hashCode() {
            return hash;
        }
    }

    @Test
    void putGetRemoveMatchOracleRandomized() {
        Random rnd = new Random(42);
        for (int trial = 0; trial < 40; trial++) {
            Map<Integer, Integer> oracle = new HashMap<>();
            PersistentHashMap<Integer, Integer> phm = PersistentHashMap.empty();
            for (int op = 0; op < 400; op++) {
                int key = rnd.nextInt(80);
                if (rnd.nextInt(3) == 0 && !oracle.isEmpty()) {
                    oracle.remove(key);
                    phm = phm.without(key);
                } else {
                    int val = rnd.nextInt(1000);
                    oracle.put(key, val);
                    phm = phm.assoc(key, val);
                }
                assertEquals(oracle.size(), phm.size(), "size trial=" + trial + " op=" + op);
            }
            for (int k = 0; k < 80; k++) {
                assertEquals(oracle.get(k), phm.get(k), "get(" + k + ")");
                assertEquals(oracle.containsKey(k), phm.containsKey(k));
            }
            assertEquals(oracle, phm);
            assertEquals(phm, oracle);
            assertEquals(oracle.hashCode(), phm.hashCode());
        }
    }

    @Test
    void forcedCollisionsBehaveLikeOracle() {
        Map<Key, Integer> oracle = new HashMap<>();
        PersistentHashMap<Key, Integer> phm = PersistentHashMap.empty();
        // Many keys sharing a handful of hash buckets -> exercises HashCollisionNode + deep tries.
        for (int i = 0; i < 200; i++) {
            Key k = new Key(i, i % 4);   // only 4 distinct hashes for 200 keys
            oracle.put(k, i);
            phm = phm.assoc(k, i);
        }
        assertEquals(oracle.size(), phm.size());
        for (Map.Entry<Key, Integer> e : oracle.entrySet()) {
            assertEquals(e.getValue(), phm.get(e.getKey()));
        }
        assertEquals(oracle, phm);
        // remove half, in a shuffled order
        java.util.List<Key> keys = new java.util.ArrayList<>(oracle.keySet());
        java.util.Collections.shuffle(keys, new Random(7));
        for (int i = 0; i < keys.size(); i += 2) {
            oracle.remove(keys.get(i));
            phm = phm.without(keys.get(i));
        }
        assertEquals(oracle.size(), phm.size());
        assertEquals(oracle, phm);
        for (Key k : keys) {
            assertEquals(oracle.get(k), phm.get(k));
        }
    }

    @Test
    void allKeysSameHash() {
        PersistentHashMap<Key, Integer> phm = PersistentHashMap.empty();
        Map<Key, Integer> oracle = new HashMap<>();
        for (int i = 0; i < 50; i++) {
            Key k = new Key(i, 999);   // every key collides fully
            phm = phm.assoc(k, i);
            oracle.put(k, i);
        }
        assertEquals(50, phm.size());
        assertEquals(oracle, phm);
        phm = phm.without(new Key(10, 999));
        oracle.remove(new Key(10, 999));
        assertEquals(49, phm.size());
        assertNull(phm.get(new Key(10, 999)));
        assertEquals(oracle, phm);
    }

    @Test
    void replaceKeepsSizeNoOpEqualValueSharesInstance() {
        PersistentHashMap<String, Integer> a = PersistentHashMap.<String, Integer>empty()
                .assoc("x", 1).assoc("y", 2);
        PersistentHashMap<String, Integer> b = a.assoc("x", 1);   // equal value -> unchanged
        assertTrue(a == b, "assoc of an equal value must return the same instance");
        PersistentHashMap<String, Integer> c = a.assoc("x", 9);   // replace
        assertEquals(2, c.size());
        assertEquals(9, c.get("x"));
        assertEquals(1, a.get("x"), "original is untouched");
    }

    @Test
    void removeAbsentReturnsSameInstance() {
        PersistentHashMap<String, Integer> a = PersistentHashMap.<String, Integer>empty().assoc("x", 1);
        assertTrue(a == a.without("nope"));
    }

    @Test
    void fromAndIterationOrderDeterministic() {
        Map<String, Integer> src = new LinkedHashMap<>();
        src.put("a", 1);
        src.put("b", 2);
        src.put("c", 3);
        PersistentHashMap<String, Integer> one = PersistentHashMap.from(src);
        PersistentHashMap<String, Integer> two = PersistentHashMap.from(src);
        assertEquals(one.keySet().toString(), two.keySet().toString(),
                "iteration order must be deterministic for the same keys");
        assertEquals(src, one);
        assertTrue(PersistentHashMap.from(one) == one, "from must share a PersistentHashMap");
    }

    @Test
    void isImmutable() {
        PersistentHashMap<String, Integer> a = PersistentHashMap.<String, Integer>empty().assoc("x", 1);
        assertThrows(UnsupportedOperationException.class, () -> a.put("y", 2));
        assertThrows(UnsupportedOperationException.class, () -> a.remove("x"));
        assertThrows(UnsupportedOperationException.class, a::clear);
    }

    @Test
    void emptyMapQueries() {
        PersistentHashMap<String, Integer> e = PersistentHashMap.empty();
        assertEquals(0, e.size());
        assertTrue(e.isEmpty());
        assertNull(e.get("x"));
        assertFalse(e.containsKey("x"));
        assertFalse(e.entrySet().iterator().hasNext());
    }
}
