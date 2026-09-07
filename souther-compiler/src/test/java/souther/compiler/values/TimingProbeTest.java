package souther.compiler.values;

import org.junit.jupiter.api.Test;

import java.util.Set;

class TimingProbeTest {

    private static Apartness<String> partite(int groups) {
        Apartness<String> out = Apartness.nothing();
        for (int one = 0; one < groups * 3; one++) {
            for (int other = one + 1; other < groups * 3; other++) {
                if (one / 3 != other / 3) {
                    out = out.and(Apartness.of("p" + one, "p" + other));
                }
            }
        }
        return out;
    }

    @Test
    void whatTheAdmittedWorstShapeCosts() {
        for (int groups : new int[] {5, 7, 10}) {
            Apartness<String> partite = partite(groups);
            Apartness.WhatABlockAdmits<String> two =
                    (_, _) -> new Admits.These(Set.of(Value.text("A"), Value.text("B")));
            long at = System.nanoTime();
            Apartness.Reduction<String> said = partite.reduce(two);
            long reduced = System.nanoTime() - at;
            Lacks<String> lacks = ((Apartness.Reduction.Nothing<String>) said).lacks();
            at = System.nanoTime();
            Lacks<String> both = lacks.sharedWith(lacks);
            long shared = System.nanoTime() - at;
            at = System.nanoTime();
            Lacks<String> either = lacks.and(lacks);
            long anded = System.nanoTime() - at;
            System.out.println("groups=" + groups + " blocks=" + partite.extent().blocks()
                    + " edges=" + partite.extent().edges() + " lacks=" + lacks.size()
                    + " reduce=" + reduced / 1_000_000 + "ms"
                    + " sharedWith=" + shared / 1_000_000 + "ms(" + both.size() + ")"
                    + " and=" + anded / 1_000_000 + "ms(" + either.size() + ")");
        }
    }
}
