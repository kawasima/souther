package net.unit8.souther.runtime;

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;

/**
 * An immutable hash map backed by a CHAMP trie (Compressed Hash-Array Mapped Prefix-tree). Each
 * node packs its inline key/value pairs and its sub-nodes into a single array addressed by two
 * bitmaps (a 5-bit hash chunk per level), so {@code get}/{@code put}/{@code remove} are O(log32 n)
 * and each new version shares all untouched structure with the previous one — which is what makes
 * a {@code groupBy} that grows a map O(n log n) instead of the O(n²) a whole-map copy would cost.
 *
 * <p>It implements {@link java.util.Map} so it flows through codegen and the boundary unchanged.
 * Extending {@link AbstractMap} supplies the immutable, mutator-throwing defaults and the
 * order-INSENSITIVE {@code Map.equals}/{@code hashCode} — the contract Souther value equality
 * ({@code ==} via {@code Objects.equals}, ADR-0009) relies on. Keys use their own {@code hashCode}/
 * {@code equals}; value-class keys already generate contract-correct ones.
 *
 * <p>Iteration order is a deterministic, implementation-defined hash order (not insertion order):
 * the same set of keys always yields the same order, so boundary encoding stays reproducible.
 *
 * <p>Values are never null (a Souther collection models absence with {@code Option}), so
 * {@link #get} returning {@code null} unambiguously means "absent".
 *
 * <p>{@code remove} keeps the trie canonical: an emptied sub-node is dropped and a sub-node that
 * collapses to a single entry is inlined back into its parent (bubbling up a level when the parent
 * would otherwise become a lone-entry inner node). So a key set reached by deletions has the same
 * shape — and the same deterministic iteration order — as one built directly.
 */
public final class PersistentHashMap<K, V> extends AbstractMap<K, V> {

    private static final int BITS = 5;
    private static final int MASK = (1 << BITS) - 1;   // 31
    private static final Object NOT_FOUND = new Object();

    public static final PersistentHashMap<?, ?> EMPTY =
            new PersistentHashMap<>(BitmapIndexedNode.EMPTY, 0);

    private final Node root;
    private final int size;

    private PersistentHashMap(Node root, int size) {
        this.root = root;
        this.size = size;
    }

    @SuppressWarnings("unchecked")
    public static <K, V> PersistentHashMap<K, V> empty() {
        return (PersistentHashMap<K, V>) EMPTY;
    }

    /** Wraps {@code m} as a PersistentHashMap, sharing when it already is one, else rebuilding by
     *  {@code put} (later entries win). */
    @SuppressWarnings("unchecked")
    public static <K, V> PersistentHashMap<K, V> from(Map<? extends K, ? extends V> m) {
        if (m instanceof PersistentHashMap<?, ?> phm) {
            return (PersistentHashMap<K, V>) phm;
        }
        PersistentHashMap<K, V> out = empty();
        for (Map.Entry<? extends K, ? extends V> e : m.entrySet()) {
            out = out.assoc(e.getKey(), e.getValue());
        }
        return out;
    }

    /** Spreads the key's hash so that low-order bits (the first chunk consumed) carry entropy. */
    private static int spread(int h) {
        return h ^ (h >>> 16);
    }

    private static int hashOf(Object key) {
        return key == null ? 0 : spread(key.hashCode());
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    @SuppressWarnings("unchecked")
    public V get(Object key) {
        Object r = root.find(key, hashOf(key), 0);
        return r == NOT_FOUND ? null : (V) r;
    }

    @Override
    public boolean containsKey(Object key) {
        return root.find(key, hashOf(key), 0) != NOT_FOUND;
    }

    /** This map with {@code key} mapped to {@code val} (added, or overwriting an equal-keyed entry).
     *  Named {@code assoc} rather than {@code put} because {@link java.util.Map#put} is the mutating
     *  operation and returns the old value — this returns a new map. */
    public PersistentHashMap<K, V> assoc(K key, V val) {
        Box added = new Box();
        Node newRoot = root.put(key, hashOf(key), val, 0, added);
        if (newRoot == root) {
            return this;   // key present with an equal value: unchanged
        }
        return new PersistentHashMap<>(newRoot, added.value ? size + 1 : size);
    }

    /** This map without {@code key}; {@code this} when the key is absent. A removal always returns a
     *  new node and a no-op returns the same node, so reference identity alone tells us whether the
     *  size dropped — no separate "was it removed" flag is threaded through. */
    public PersistentHashMap<K, V> without(K key) {
        Node newRoot = root.remove(key, hashOf(key), 0);
        if (newRoot == root) {
            return this;
        }
        return new PersistentHashMap<>(newRoot, size - 1);
    }

    @Override
    public Set<Map.Entry<K, V>> entrySet() {
        return new AbstractSet<>() {
            @Override
            public int size() {
                return size;
            }

            @Override
            @SuppressWarnings("unchecked")
            public Iterator<Map.Entry<K, V>> iterator() {
                return (Iterator<Map.Entry<K, V>>) (Iterator<?>) new EntryIterator(root, size);
            }
        };
    }

    // --- nodes ---

    /** A mutable one-bit flag {@code put} sets to report whether it added a new key (versus replacing
     *  an existing key's value), so the map's size is adjusted correctly. {@code remove} needs no such
     *  flag: it decides by reference identity (see {@link #without}). */
    private static final class Box {
        boolean value;
    }

    private interface Node {
        Object find(Object key, int keyHash, int shift);

        Node put(Object key, int keyHash, Object val, int shift, Box addedLeaf);

        Node remove(Object key, int keyHash, int shift);

        int payloadArity();

        Object keyAt(int i);

        Object valAt(int i);

        int nodeArity();

        Node nodeAt(int i);
    }

    /** Builds a node holding exactly two entries with distinct keys, descending until their hash
     *  chunks differ (or bottoming out in a {@link HashCollisionNode} when the full hashes match). */
    private static Node mergeTwoPairs(Object k0, int h0, Object v0,
                                      Object k1, int h1, Object v1, int shift) {
        if (h0 == h1) {
            return new HashCollisionNode(h0, new Object[]{k0, v0, k1, v1});
        }
        int mask0 = (h0 >>> shift) & MASK;
        int mask1 = (h1 >>> shift) & MASK;
        if (mask0 != mask1) {
            int dataMap = (1 << mask0) | (1 << mask1);
            Object[] contents = mask0 < mask1
                    ? new Object[]{k0, v0, k1, v1}
                    : new Object[]{k1, v1, k0, v0};
            return new BitmapIndexedNode(dataMap, 0, contents);
        }
        Node sub = mergeTwoPairs(k0, h0, v0, k1, h1, v1, shift + BITS);
        return new BitmapIndexedNode(0, 1 << mask0, new Object[]{sub});
    }

    /**
     * A CHAMP node. {@code contents} holds {@code payloadArity} inline {@code [key, value]} pairs at
     * the front, then {@code nodeArity} sub-nodes in forward order at the tail. {@code dataMap} marks
     * the hash-chunk bits with an inline pair, {@code nodeMap} the bits with a sub-node.
     */
    private static final class BitmapIndexedNode implements Node {
        static final BitmapIndexedNode EMPTY = new BitmapIndexedNode(0, 0, new Object[0]);

        final int dataMap;
        final int nodeMap;
        final Object[] contents;

        BitmapIndexedNode(int dataMap, int nodeMap, Object[] contents) {
            this.dataMap = dataMap;
            this.nodeMap = nodeMap;
            this.contents = contents;
        }

        private int dataIndex(int bitpos) {
            return Integer.bitCount(dataMap & (bitpos - 1));
        }

        private int nodeIndex(int bitpos) {
            return Integer.bitCount(nodeMap & (bitpos - 1));
        }

        @Override
        public int payloadArity() {
            return Integer.bitCount(dataMap);
        }

        @Override
        public int nodeArity() {
            return Integer.bitCount(nodeMap);
        }

        @Override
        public Object keyAt(int i) {
            return contents[2 * i];
        }

        @Override
        public Object valAt(int i) {
            return contents[2 * i + 1];
        }

        @Override
        public Node nodeAt(int i) {
            return (Node) contents[2 * payloadArity() + i];
        }

        @Override
        public Object find(Object key, int keyHash, int shift) {
            int bitpos = 1 << ((keyHash >>> shift) & MASK);
            if ((dataMap & bitpos) != 0) {
                int i = dataIndex(bitpos);
                return Objects.equals(keyAt(i), key) ? valAt(i) : NOT_FOUND;
            }
            if ((nodeMap & bitpos) != 0) {
                return nodeAt(nodeIndex(bitpos)).find(key, keyHash, shift + BITS);
            }
            return NOT_FOUND;
        }

        @Override
        public Node put(Object key, int keyHash, Object val, int shift, Box addedLeaf) {
            int bitpos = 1 << ((keyHash >>> shift) & MASK);
            if ((dataMap & bitpos) != 0) {
                int i = dataIndex(bitpos);
                Object currentKey = keyAt(i);
                if (Objects.equals(currentKey, key)) {
                    if (Objects.equals(valAt(i), val)) {
                        return this;
                    }
                    return copyAndSetValue(bitpos, val);
                }
                Node sub = mergeTwoPairs(currentKey, hashOf(currentKey), valAt(i),
                        key, keyHash, val, shift + BITS);
                addedLeaf.value = true;
                return copyAndMigrateInlineToNode(bitpos, sub);
            }
            if ((nodeMap & bitpos) != 0) {
                int i = nodeIndex(bitpos);
                Node sub = nodeAt(i);
                Node newSub = sub.put(key, keyHash, val, shift + BITS, addedLeaf);
                return newSub == sub ? this : copyAndSetNode(bitpos, newSub);
            }
            addedLeaf.value = true;
            return copyAndInsertValue(bitpos, key, val);
        }

        @Override
        public Node remove(Object key, int keyHash, int shift) {
            int bitpos = 1 << ((keyHash >>> shift) & MASK);
            if ((dataMap & bitpos) != 0) {
                int i = dataIndex(bitpos);
                if (Objects.equals(keyAt(i), key)) {
                    return copyAndRemoveValue(bitpos);
                }
                return this;
            }
            if ((nodeMap & bitpos) != 0) {
                int i = nodeIndex(bitpos);
                Node sub = nodeAt(i);
                Node newSub = sub.remove(key, keyHash, shift + BITS);
                if (newSub == sub) {
                    return this;
                }
                switch (sizePredicate(newSub)) {
                    case 0:
                        return copyAndRemoveNode(bitpos);
                    case 1:
                        // The sub-node collapsed to a single entry: inline it here so the trie stays
                        // canonical (a HashCollisionNode that dropped to one pair, or a sub-node whose
                        // sole surviving key belongs one level up). When this node is itself just that
                        // one sub-node, bubble the entry up instead so this node does not become a
                        // non-canonical lone-entry inner node — re-keyed to this level's bit, since the
                        // surviving key shares the removed key's chunk here and only diverged deeper.
                        if (payloadArity() == 0 && nodeArity() == 1) {
                            return new BitmapIndexedNode(bitpos, 0,
                                    new Object[]{newSub.keyAt(0), newSub.valAt(0)});
                        }
                        return copyAndMigrateNodeToInline(bitpos, newSub);
                    default:
                        return copyAndSetNode(bitpos, newSub);
                }
            }
            return this;
        }

        /** 0 for empty, 1 for a single inline entry (a candidate to inline into the parent), 2 for
         *  anything larger. */
        private static int sizePredicate(Node n) {
            if (n.payloadArity() == 0 && n.nodeArity() == 0) {
                return 0;
            }
            if (n.nodeArity() == 0 && n.payloadArity() == 1) {
                return 1;
            }
            return 2;
        }

        private BitmapIndexedNode copyAndSetValue(int bitpos, Object val) {
            Object[] c = contents.clone();
            c[2 * dataIndex(bitpos) + 1] = val;
            return new BitmapIndexedNode(dataMap, nodeMap, c);
        }

        private BitmapIndexedNode copyAndSetNode(int bitpos, Node node) {
            Object[] c = contents.clone();
            c[2 * payloadArity() + nodeIndex(bitpos)] = node;
            return new BitmapIndexedNode(dataMap, nodeMap, c);
        }

        private BitmapIndexedNode copyAndInsertValue(int bitpos, Object key, Object val) {
            int idx = 2 * dataIndex(bitpos);
            Object[] c = new Object[contents.length + 2];
            System.arraycopy(contents, 0, c, 0, idx);
            c[idx] = key;
            c[idx + 1] = val;
            System.arraycopy(contents, idx, c, idx + 2, contents.length - idx);
            return new BitmapIndexedNode(dataMap | bitpos, nodeMap, c);
        }

        private BitmapIndexedNode copyAndRemoveValue(int bitpos) {
            int idx = 2 * dataIndex(bitpos);
            Object[] c = new Object[contents.length - 2];
            System.arraycopy(contents, 0, c, 0, idx);
            System.arraycopy(contents, idx + 2, c, idx, contents.length - idx - 2);
            return new BitmapIndexedNode(dataMap ^ bitpos, nodeMap, c);
        }

        private BitmapIndexedNode copyAndRemoveNode(int bitpos) {
            int idx = 2 * payloadArity() + nodeIndex(bitpos);
            Object[] c = new Object[contents.length - 1];
            System.arraycopy(contents, 0, c, 0, idx);
            System.arraycopy(contents, idx + 1, c, idx, contents.length - idx - 1);
            return new BitmapIndexedNode(dataMap, nodeMap ^ bitpos, c);
        }

        /** Removes the inline pair at {@code bitpos} and inserts {@code node} at that bit in the node
         *  section (data shrinks by a pair, nodes grow by one). */
        private BitmapIndexedNode copyAndMigrateInlineToNode(int bitpos, Node node) {
            int p = payloadArity();
            int n = nodeArity();
            int d = dataIndex(bitpos);
            int ni = nodeIndex(bitpos);
            Object[] c = new Object[contents.length - 1];   // -2 data, +1 node
            System.arraycopy(contents, 0, c, 0, 2 * d);                       // data before the pair
            System.arraycopy(contents, 2 * d + 2, c, 2 * d, 2 * (p - 1) - 2 * d);  // data after the pair
            int nodeBase = 2 * (p - 1);
            System.arraycopy(contents, 2 * p, c, nodeBase, ni);              // nodes before ni
            c[nodeBase + ni] = node;
            System.arraycopy(contents, 2 * p + ni, c, nodeBase + ni + 1, n - ni);  // nodes from ni
            return new BitmapIndexedNode(dataMap ^ bitpos, nodeMap | bitpos, c);
        }

        /** Inlines {@code node}'s single entry at {@code bitpos} and drops it from the node section
         *  (data grows by a pair, nodes shrink by one) — the inverse of {@link
         *  #copyAndMigrateInlineToNode}, restoring canonical form after a removal. */
        private BitmapIndexedNode copyAndMigrateNodeToInline(int bitpos, Node node) {
            int p = payloadArity();
            int n = nodeArity();
            int d = dataIndex(bitpos);
            int ni = nodeIndex(bitpos);
            Object[] c = new Object[contents.length + 1];   // +2 data, -1 node
            System.arraycopy(contents, 0, c, 0, 2 * d);                       // data before the pair
            c[2 * d] = node.keyAt(0);
            c[2 * d + 1] = node.valAt(0);
            System.arraycopy(contents, 2 * d, c, 2 * d + 2, 2 * (p - d));      // data after the pair
            int nodeBase = 2 * (p + 1);
            System.arraycopy(contents, 2 * p, c, nodeBase, ni);              // nodes before ni
            System.arraycopy(contents, 2 * p + ni + 1, c, nodeBase + ni, n - ni - 1);  // nodes after ni
            return new BitmapIndexedNode(dataMap | bitpos, nodeMap ^ bitpos, c);
        }
    }

    /** A bucket of entries whose full hashes collide (distinct keys, same {@code hash}). */
    private static final class HashCollisionNode implements Node {
        final int hash;
        final Object[] pairs;   // [k0,v0,k1,v1,...], length >= 4

        HashCollisionNode(int hash, Object[] pairs) {
            this.hash = hash;
            this.pairs = pairs;
        }

        @Override
        public Object find(Object key, int keyHash, int shift) {
            if (keyHash != hash) {
                return NOT_FOUND;
            }
            for (int i = 0; i < pairs.length; i += 2) {
                if (Objects.equals(pairs[i], key)) {
                    return pairs[i + 1];
                }
            }
            return NOT_FOUND;
        }

        @Override
        public Node put(Object key, int keyHash, Object val, int shift, Box addedLeaf) {
            if (keyHash != hash) {
                // A key that reaches this node with a different hash: wrap the bucket in a bitmap node
                // at this level, then insert into that.
                Node wrapper = new BitmapIndexedNode(0, 1 << ((hash >>> shift) & MASK),
                        new Object[]{this});
                return wrapper.put(key, keyHash, val, shift, addedLeaf);
            }
            for (int i = 0; i < pairs.length; i += 2) {
                if (Objects.equals(pairs[i], key)) {
                    if (Objects.equals(pairs[i + 1], val)) {
                        return this;
                    }
                    Object[] p = pairs.clone();
                    p[i + 1] = val;
                    return new HashCollisionNode(hash, p);
                }
            }
            addedLeaf.value = true;
            Object[] p = Arrays.copyOf(pairs, pairs.length + 2);
            p[pairs.length] = key;
            p[pairs.length + 1] = val;
            return new HashCollisionNode(hash, p);
        }

        @Override
        public Node remove(Object key, int keyHash, int shift) {
            if (keyHash != hash) {
                return this;
            }
            for (int i = 0; i < pairs.length; i += 2) {
                if (Objects.equals(pairs[i], key)) {
                    if (pairs.length == 4) {
                        int other = i == 0 ? 2 : 0;
                        // Fall back to a one-entry bitmap node at this level for the surviving key.
                        return new BitmapIndexedNode(1 << ((hash >>> shift) & MASK), 0,
                                new Object[]{pairs[other], pairs[other + 1]});
                    }
                    Object[] p = new Object[pairs.length - 2];
                    System.arraycopy(pairs, 0, p, 0, i);
                    System.arraycopy(pairs, i + 2, p, i, pairs.length - i - 2);
                    return new HashCollisionNode(hash, p);
                }
            }
            return this;
        }

        @Override
        public int payloadArity() {
            return pairs.length / 2;
        }

        @Override
        public Object keyAt(int i) {
            return pairs[2 * i];
        }

        @Override
        public Object valAt(int i) {
            return pairs[2 * i + 1];
        }

        @Override
        public int nodeArity() {
            return 0;
        }

        @Override
        public Node nodeAt(int i) {
            throw new IndexOutOfBoundsException();
        }
    }

    /** Depth-first iterator over the trie: emits a node's inline pairs, then descends its sub-nodes.
     *  Deterministic (bitmap order), O(1) amortized per entry. */
    private static final class EntryIterator implements Iterator<Map.Entry<Object, Object>> {
        private final Deque<Node> stack = new ArrayDeque<>();
        private Node payloadNode;
        private int payloadIndex;

        EntryIterator(Node root, int size) {
            if (size > 0) {
                stack.push(root);
            }
            advance();
        }

        private void advance() {
            payloadNode = null;
            while (!stack.isEmpty()) {
                Node n = stack.pop();
                for (int i = n.nodeArity() - 1; i >= 0; i--) {
                    stack.push(n.nodeAt(i));
                }
                if (n.payloadArity() > 0) {
                    payloadNode = n;
                    payloadIndex = 0;
                    return;
                }
            }
        }

        @Override
        public boolean hasNext() {
            return payloadNode != null;
        }

        @Override
        public Map.Entry<Object, Object> next() {
            if (payloadNode == null) {
                throw new NoSuchElementException();
            }
            Object k = payloadNode.keyAt(payloadIndex);
            Object v = payloadNode.valAt(payloadIndex);
            payloadIndex++;
            if (payloadIndex >= payloadNode.payloadArity()) {
                advance();
            }
            return new SimpleImmutableEntry<>(k, v);
        }
    }
}
