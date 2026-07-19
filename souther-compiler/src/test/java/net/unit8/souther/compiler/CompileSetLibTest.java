package net.unit8.souther.compiler;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The Set standard library ([#stdlib-set]): building and the set algebra, exercised in a behavior
 *  body (a Set is not yet a data field until its codec lands). */
class CompileSetLibTest {

    @Test
    void buildAndCombineSets() throws Exception {
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile("""
                module demo

                import Set ( singleton, insert, contains, union, intersect, difference, size, isEmpty, toList, fromList )

                data In = { xs: List<String> }
                data Out = {
                    n: Int
                    , hasA: Bool
                    , unionList: List<String>
                    , interList: List<String>
                    , diffList: List<String>
                    , emptyFlag: Bool
                }

                behavior run : (i: In) -> Out constructs Out

                let run (i) = {
                    let s = insert("c", insert("b", insert("a", Set.empty())))
                    let t = fromList(i.xs)
                    Out {
                        n = size(s),
                        hasA = contains("a", s),
                        unionList = toList(union(s, t)),
                        interList = toList(intersect(s, t)),
                        diffList = toList(difference(s, t)),
                        emptyFlag = isEmpty(Set.empty())
                    }
                }
                """), getClass().getClassLoader());

        Object in = Codecs.decoded(loader, "demo.In", Map.of("xs", List.of("b", "d")));
        Object behavior = loader.loadClass("demo.Run").getConstructor().newInstance();
        Object out = Codecs.apply(behavior, in);

        Map<?, ?> m = (Map<?, ?>) Codecs.encode(loader, "demo.Out", out);
        assertEquals(3L, m.get("n"), "the set {a, b, c} has three members");
        assertEquals(true, m.get("hasA"));
        // Set iteration order is a deterministic hash order, not first-seen — compare membership.
        assertEquals(Set.of("a", "b", "c", "d"), Set.copyOf((List<?>) m.get("unionList")));
        assertEquals(Set.of("b"), Set.copyOf((List<?>) m.get("interList")));
        assertEquals(Set.of("a", "c"), Set.copyOf((List<?>) m.get("diffList")));
        assertEquals(true, m.get("emptyFlag"));
    }
}
