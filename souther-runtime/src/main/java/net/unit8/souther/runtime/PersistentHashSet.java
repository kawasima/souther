package net.unit8.souther.runtime;

import java.util.AbstractSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.Set;

/**
 * An immutable set backed by a {@link PersistentHashMap} whose values are a shared sentinel, so it
 * reuses the CHAMP trie, structural sharing, deterministic hash-order iteration, and value-equality
 * key handling. It implements {@link java.util.Set}, extending {@link AbstractSet} for the immutable,
 * mutator-throwing defaults and the order-INSENSITIVE {@code Set.equals}/{@code hashCode} that Souther
 * value equality (ADR-0009) relies on.
 *
 * <p>Iteration order is a deterministic, implementation-defined hash order (not first-seen order),
 * stable for the same element set so boundary encoding stays reproducible.
 *
 * <p>A dedicated key-only CHAMP node (no value slots) would save memory; the sentinel-map form is
 * behaviorally identical and is the minimal first cut.
 */
public final class PersistentHashSet<E> extends AbstractSet<E> {

    private static final Object PRESENT = new Object();

    public static final PersistentHashSet<?> EMPTY =
            new PersistentHashSet<>(PersistentHashMap.empty());

    private final PersistentHashMap<E, Object> map;

    private PersistentHashSet(PersistentHashMap<E, Object> map) {
        this.map = map;
    }

    @SuppressWarnings("unchecked")
    public static <E> PersistentHashSet<E> empty() {
        return (PersistentHashSet<E>) EMPTY;
    }

    public static <E> PersistentHashSet<E> ofSingle(E value) {
        return PersistentHashSet.<E>empty().with(value);
    }

    /** Wraps {@code src} as a PersistentHashSet, sharing when it already is one, else building by
     *  adding each element (dropping duplicates). */
    @SuppressWarnings("unchecked")
    public static <E> PersistentHashSet<E> from(Collection<? extends E> src) {
        if (src instanceof PersistentHashSet<?> phs) {
            return (PersistentHashSet<E>) phs;
        }
        PersistentHashSet<E> out = empty();
        for (E e : src) {
            out = out.with(e);
        }
        return out;
    }

    /** This set with {@code value} added (unchanged when already present). */
    public PersistentHashSet<E> with(E value) {
        PersistentHashMap<E, Object> m = map.assoc(value, PRESENT);
        return m == map ? this : new PersistentHashSet<>(m);
    }

    /** This set without {@code value} (unchanged when absent). */
    public PersistentHashSet<E> without(E value) {
        PersistentHashMap<E, Object> m = map.without(value);
        return m == map ? this : new PersistentHashSet<>(m);
    }

    @Override
    public int size() {
        return map.size();
    }

    @Override
    public boolean contains(Object value) {
        return map.containsKey(value);
    }

    @Override
    public Iterator<E> iterator() {
        return map.keySet().iterator();
    }

    /** All elements of {@code a} and {@code b} (adds the smaller into the larger). */
    public static <E> PersistentHashSet<E> union(Set<? extends E> a, Set<? extends E> b) {
        Set<? extends E> larger = a.size() >= b.size() ? a : b;
        Set<? extends E> smaller = a.size() >= b.size() ? b : a;
        PersistentHashSet<E> out = from(larger);
        for (E e : smaller) {
            out = out.with(e);
        }
        return out;
    }

    /** The elements in both {@code a} and {@code b} (scans the smaller). */
    public static <E> PersistentHashSet<E> intersect(Set<? extends E> a, Set<? extends E> b) {
        Set<?> larger = a.size() >= b.size() ? a : b;
        Set<? extends E> smaller = a.size() >= b.size() ? b : a;
        PersistentHashSet<E> out = empty();
        for (E e : smaller) {
            if (larger.contains(e)) {
                out = out.with(e);
            }
        }
        return out;
    }

    /** The elements of {@code a} that are not in {@code b}. */
    public static <E> PersistentHashSet<E> difference(Set<? extends E> a, Set<? extends E> b) {
        PersistentHashSet<E> out = from(a);
        for (Object e : b) {
            @SuppressWarnings("unchecked")
            E el = (E) e;
            out = out.without(el);
        }
        return out;
    }
}
