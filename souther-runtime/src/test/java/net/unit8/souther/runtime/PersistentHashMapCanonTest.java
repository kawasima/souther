package net.unit8.souther.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** After a {@code without} collapses a sub-node to a single entry, the trie must be canonicalized
 *  (that entry inlined into the parent), so a map reached by inserting then removing has the same
 *  shape — and therefore the same deterministic iteration order — as one built directly from the
 *  surviving entries. */
class PersistentHashMapCanonTest {

    /** A key with a controlled hash, to place entries at chosen trie positions. */
    private record Key(int id, int hash) {
        @Override
        public int hashCode() {
            return hash;
        }
    }

    @Test
    void collapsedSubNodeIsInlined() {
        // A and B share the first 5-bit chunk (3) so they form a sub-node at level 1, where their
        // second chunks (5, 6) differ. Z sits inline at the root under a different first chunk (10).
        Key a = new Key(1, 3 | (5 << 5));
        Key b = new Key(2, 3 | (6 << 5));
        Key z = new Key(3, 10);

        PersistentHashMap<Key, Integer> m = PersistentHashMap.<Key, Integer>empty()
                .assoc(a, 1).assoc(b, 2).assoc(z, 3)
                .without(b);   // the {A,B} sub-node collapses to just A -> must inline into the root

        PersistentHashMap<Key, Integer> direct = PersistentHashMap.<Key, Integer>empty()
                .assoc(a, 1).assoc(z, 3);

        assertEquals(direct, m);
        assertEquals(direct.keySet().toString(), m.keySet().toString(),
                "a canonicalized trie iterates in the same order as one built directly");
    }

    @Test
    void deepCollapseBubblesUp() {
        // A and B agree on the first two chunks and differ on the third, forming a two-level chain of
        // single-child nodes down to their sub-node. Removing B must bubble A's entry back to the root.
        Key a = new Key(1, 7 | (2 << 5) | (9 << 10));
        Key b = new Key(2, 7 | (2 << 5) | (11 << 10));

        PersistentHashMap<Key, Integer> m = PersistentHashMap.<Key, Integer>empty()
                .assoc(a, 1).assoc(b, 2).without(b);
        PersistentHashMap<Key, Integer> direct = PersistentHashMap.<Key, Integer>empty().assoc(a, 1);

        assertEquals(direct, m);
        assertEquals(direct.keySet().toString(), m.keySet().toString());
    }

    @Test
    void randomizedShapeMatchesDirectBuild() {
        Random rnd = new Random(11);
        for (int trial = 0; trial < 50; trial++) {
            Map<Integer, Integer> present = new HashMap<>();
            PersistentHashMap<Integer, Integer> phm = PersistentHashMap.empty();
            for (int op = 0; op < 300; op++) {
                int key = rnd.nextInt(120);
                if (rnd.nextInt(2) == 0 && !present.isEmpty()) {
                    present.remove(key);
                    phm = phm.without(key);
                } else {
                    present.put(key, op);
                    phm = phm.assoc(key, op);
                }
            }
            // Build a map directly from the surviving entries; a canonical trie must match its shape,
            // hence its iteration order, exactly.
            PersistentHashMap<Integer, Integer> direct = PersistentHashMap.empty();
            List<Integer> keys = new ArrayList<>(present.keySet());
            for (Integer k : keys) {
                direct = direct.assoc(k, present.get(k));
            }
            assertEquals(direct, phm, "trial=" + trial);
            assertEquals(new ArrayList<>(direct.keySet()), new ArrayList<>(phm.keySet()),
                    "iteration order (shape) mismatch at trial=" + trial);
        }
    }
}
