package net.unit8.souther.compiler;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The List standard library beyond map/filter/all/any (spec 18.4): the further Elm combinators
 *  derived from {@code fold}, plus the native {@code sort} primitive and String ordering. */
class CompileListLibTest {

    @Test
    void foldDerivedCombinators() throws Exception {
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile("""
                module demo

                import List ( reverse, sum, product, member, isEmpty )

                data In = { ns: List<Int> }
                data Out = {
                    reversed: List<Int>
                    , total: Int
                    , prod: Int
                    , hasTwo: Bool
                    , none: Bool
                }

                behavior run : (i: In) -> Out constructs Out

                let run (i) = Out {
                    reversed = reverse(i.ns),
                    total = sum(i.ns),
                    prod = product(i.ns),
                    hasTwo = member(2, i.ns),
                    none = isEmpty(i.ns)
                }
                """), getClass().getClassLoader());

        Object behavior = loader.loadClass("demo.Run" + "$Impl").getConstructor().newInstance();
        Object out = Codecs.apply(behavior, decodeIn(loader, List.of(1L, 2L, 3L)));

        Map<?, ?> m = encode(loader, out);
        assertEquals(List.of(3L, 2L, 1L), m.get("reversed"));
        assertEquals(6L, m.get("total"));
        assertEquals(6L, m.get("prod"));
        assertEquals(true, m.get("hasTwo"));
        assertEquals(false, m.get("none"));
    }

    @Test
    void sortOfAnEmptyListLiteralIsAllowedAndYieldsEmpty() throws Exception {
        // The empty-list literal types as `List<Nothing>`; sorting it is valid (it sorts to itself),
        // so the ordered-element guard must let the bottom element through.
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile("""
                module demo

                import List ( sort )

                data In = { ns: List<Int> }
                data Out = { xs: List<Int> }

                behavior run : (i: In) -> Out constructs Out

                let run (i) = Out { xs = sort([]) }
                """), getClass().getClassLoader());

        Object behavior = loader.loadClass("demo.Run" + "$Impl").getConstructor().newInstance();
        Object out = Codecs.apply(behavior, decodeIn(loader, List.of()));
        assertEquals(List.of(), ((Map<?, ?>) Codecs.encode(loader, "demo.Out", out)).get("xs"));
    }

    @Test
    void flattenAndSortAndStringOrder() throws Exception {
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile("""
                module demo

                import List ( sort, concat )

                data In = { tags: List<String> }
                data Out = {
                    sorted: List<String>
                    , joinedNested: List<Int>
                    , ascending: Bool
                }

                behavior run : (i: In) -> Out constructs Out

                let run (i) = Out {
                    sorted = sort(i.tags),
                    joinedNested = concat([[1, 2], [3]]),
                    ascending = "alpha" < "beta"
                }
                """), getClass().getClassLoader());

        Object in = Codecs.decoded(loader, "demo.In", Map.of("tags", List.of("gamma", "alpha", "beta")));
        Object behavior = loader.loadClass("demo.Run" + "$Impl").getConstructor().newInstance();
        Object out = Codecs.apply(behavior, in);

        Map<?, ?> m = encode(loader, out);
        assertEquals(List.of("alpha", "beta", "gamma"), m.get("sorted"));
        assertEquals(List.of(1L, 2L, 3L), m.get("joinedNested"));
        assertEquals(true, m.get("ascending"));
    }

    @Test
    void distinctKeepsFirstOccurrenceAndDropsLaterDuplicates() throws Exception {
        // `distinct` reads the accumulator (via `member`) before growing it — the shape whose
        // accumulator element type must be recovered from the block, not the empty `[]` seed.
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile("""
                module demo

                import List ( distinct )

                data In = { ns: List<Int> }
                data Out = { ys: List<Int> }

                behavior run : (i: In) -> Out constructs Out

                let run (i) = Out { ys = distinct(i.ns) }
                """), getClass().getClassLoader());

        Object behavior = loader.loadClass("demo.Run" + "$Impl").getConstructor().newInstance();
        Object out = Codecs.apply(behavior, decodeIn(loader, List.of(3L, 1L, 3L, 2L, 1L)));
        assertEquals(List.of(3L, 1L, 2L), encode(loader, out).get("ys"));
    }

    @Test
    void partitionSplitsByPredicateKeepingOrderOnEachSide() throws Exception {
        // partition folds a pair of empty lists ([], []) — a tuple accumulator whose real shape is
        // recovered from the block, not the bottom seed.
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile("""
                module demo

                import List ( partition )

                data In = { ns: List<Int> }
                data Out = { big: List<Int>, small: List<Int> }

                behavior run : (i: In) -> Out constructs Out

                let run (i) = {
                    let (b, s) = partition(n -> n >= 100, i.ns)
                    Out { big = b, small = s }
                }
                """), getClass().getClassLoader());

        Object behavior = loader.loadClass("demo.Run" + "$Impl").getConstructor().newInstance();
        Object out = Codecs.apply(behavior, decodeIn(loader, List.of(50L, 200L, 10L, 300L, 100L)));
        Map<?, ?> m = encode(loader, out);
        assertEquals(List.of(200L, 300L, 100L), m.get("big"));
        assertEquals(List.of(50L, 10L), m.get("small"));
    }

    @Test
    void groupByBucketsByKeyInFirstSeenOrder() throws Exception {
        // groupBy folds Map.empty — a Map accumulator read with Map.get and grown with Map.insert,
        // its key and value types recovered from the block. The result Map is read back in-body.
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile("""
                module demo

                import List ( groupBy )
                import Map ( size )

                data In = { ns: List<Int> }
                data Out = { groups: Int, positives: List<Int> }

                behavior run : (i: In) -> Out constructs Out

                let 符号 (n: Int) = if n >= 0 then "pos" else "neg"
                let run (i) = Out {
                    groups = Map.size(groupBy(符号, i.ns)),
                    positives = match Map.get("pos", groupBy(符号, i.ns)) with
                        | None -> []
                        | Some b -> b
                }
                """), getClass().getClassLoader());

        Object behavior = loader.loadClass("demo.Run" + "$Impl").getConstructor().newInstance();
        Object out = Codecs.apply(behavior, decodeIn(loader, List.of(3L, -1L, 5L, -2L, 8L)));
        Map<?, ?> m = encode(loader, out);
        assertEquals(2L, m.get("groups"), "two buckets: pos and neg");
        assertEquals(List.of(3L, 5L, 8L), m.get("positives"), "the pos bucket keeps first-seen order");
    }

    @Test
    void maxAndMinReturnOptionAndAreNoneForAnEmptyList() throws Exception {
        // max/min are native builtins returning Option, like List.get — fold cannot build them
        // (Souther has no in-language Some/None to fold into).
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile("""
                module demo

                import List ( max, min )

                data In = { ns: List<Int> }
                data Out = { hi: Int, lo: Int, hasHi: Bool }

                behavior run : (i: In) -> Out constructs Out

                let run (i) = Out {
                    hi = match List.max(i.ns) with | None -> 0 | Some m -> m,
                    lo = match List.min(i.ns) with | None -> 0 | Some m -> m,
                    hasHi = match List.max(i.ns) with | None -> false | Some m -> true
                }
                """), getClass().getClassLoader());

        Object run = loader.loadClass("demo.Run" + "$Impl").getConstructor().newInstance();
        Map<?, ?> m = encode(loader, Codecs.apply(run, decodeIn(loader, List.of(3L, 9L, 1L, 7L))));
        assertEquals(9L, m.get("hi"));
        assertEquals(1L, m.get("lo"));
        assertEquals(true, m.get("hasHi"));

        Map<?, ?> empty = encode(loader, Codecs.apply(run, decodeIn(loader, List.of())));
        assertEquals(0L, empty.get("hi"), "max of [] is None");
        assertEquals(false, empty.get("hasHi"));
    }

    @Test
    void findReturnsTheFirstMatchOrNone() throws Exception {
        // find takes a predicate as a first-class function value (materialised as an Fn), returns
        // Option; it cannot be a fold (no in-language Some/None).
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile("""
                module demo

                import List ( find )

                data In = { ns: List<Int> }
                data Out = { firstBig: Int, found: Bool }

                behavior run : (i: In) -> Out constructs Out

                let run (i) = Out {
                    firstBig = match List.find(n -> n >= 100, i.ns) with | None -> 0 | Some n -> n,
                    found = match List.find(n -> n >= 100, i.ns) with | None -> false | Some n -> true
                }
                """), getClass().getClassLoader());

        Object run = loader.loadClass("demo.Run" + "$Impl").getConstructor().newInstance();
        Map<?, ?> hit = encode(loader, Codecs.apply(run, decodeIn(loader, List.of(3L, 50L, 200L, 400L))));
        assertEquals(200L, hit.get("firstBig"), "the first element >= 100, in order");
        assertEquals(true, hit.get("found"));
        Map<?, ?> miss = encode(loader, Codecs.apply(run, decodeIn(loader, List.of(1L, 2L, 3L))));
        assertEquals(false, miss.get("found"), "no match is None");
    }

    @Test
    void sortByOrdersByAKeyFunction() throws Exception {
        // sortBy takes a key function (an Fn) and sorts by the key's natural order.
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile("""
                module demo

                import List ( sortBy, fold )

                data In = { ns: List<Int> }
                data Out = { byNegation: List<Int> }

                behavior run : (i: In) -> Out constructs Out

                // sort descending by keying on the negated value
                let run (i) = Out {
                    byNegation = List.sortBy(n -> 0 - n, i.ns)
                }
                """), getClass().getClassLoader());

        Object behavior = loader.loadClass("demo.Run" + "$Impl").getConstructor().newInstance();
        Object out = Codecs.apply(behavior, decodeIn(loader, List.of(3L, 1L, 4L, 1L, 5L)));
        assertEquals(List.of(5L, 4L, 3L, 1L, 1L), encode(loader, out).get("byNegation"));
    }

    private static Object decodeIn(BytesClassLoader loader, List<Long> ns) throws Exception {
        return Codecs.decoded(loader, "demo.In", Map.of("ns", ns));
    }

    private static Map<?, ?> encode(BytesClassLoader loader, Object out) throws Exception {
        return (Map<?, ?>) Codecs.encode(loader, "demo.Out", out);
    }
}
