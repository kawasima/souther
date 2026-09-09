package souther.compiler.partition;

import souther.compiler.core.Core;
import souther.compiler.diag.Citation;
import souther.compiler.diag.SourcePos;
import souther.compiler.inputs.InputReads;
import souther.compiler.sites.WrittenCondition;
import souther.compiler.types.SourceConstructOrigin;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The conditions one reading of a body met, and the name each of them goes by.
 *
 * <p><b>A register of what was met and not a count of the reading.</b> A subtree is read as a
 * condition more than once — the walk reads a short-circuit operator's left operand to say what
 * stands past it, and reads it again inside whatever encloses it — and every one of those readings
 * is of the same condition. Counted per reading, one condition would wear as many names as the
 * shape of the tree happens to produce, and a reader joining two accounts on the name would join
 * them on nothing.
 *
 * <p>So a name and what it names are one act: the first reading of a site asks for the next name,
 * and every reading after it is answered with what the first came to. A number paired with a
 * condition anywhere else would be that correspondence built a second time, by something that had
 * not done the recognising.
 *
 * <p><b>One of these per body read, and every condition of that reading takes its name here.</b> A
 * condition of a guard and an arm of a fork are both things a row had to satisfy to get somewhere,
 * so they are counted together: numbered apart, two of them would wear one name and nothing
 * downstream could tell them apart.
 *
 * <p>It also says which question places a condition, because that is settled where the condition is
 * read and nowhere else. Whether the code is written somewhere a reader can open is what a position
 * already says, and it is the one thing about a position that survives the code moving — so it is
 * the last thing read off the position, and what comes out is which question a report puts later.
 *
 * <p>Where the answer is this reading's own, the place is written down in the same act. So there is
 * one entry per condition a report can ask about and none for the ones whose construct a reader can
 * go and open.
 */
final class ConditionNumbering {

    /**
     * One condition of this reading: the node a fold arrives at, under the names in force there.
     *
     * <p><b>The two halves are compared differently, and each for its own reason.</b> A node is the
     * node and not one shaped like it: {@link Core} is a tree of records, so two comparisons an
     * author wrote in two places are equal — read as one site, two conditions this compiler tells
     * apart would take one name. The names in force are what they say and not which object says
     * them: a {@code let} body reached by two folds is under two environments built the same way,
     * and read as two sites the condition inside it would be named twice.
     *
     * <p>The node the fold arrives at, which is not the node it was handed. Bindings and a name
     * standing for a truth are looked through on the way in, so a condition reached through one is
     * the same condition as the one reached without.
     */
    private record Site(Core node, InputReads reads) {

        @Override
        public boolean equals(Object other) {
            return other instanceof Site that && node == that.node && reads.equals(that.reads);
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(node) * 31 + reads.hashCode();
        }
    }

    private final String module;
    private final String behavior;
    private final Map<Site, Condition> read = new HashMap<>();
    /** The name each arm of each fork goes by. Beside {@link #read} because an arm is not a fold of
     *  a subtree: what is met is the fork and which of its arms, and there is no node of its own to
     *  be at. */
    private final Map<Core.Match, Map<Integer, ConditionOccurrence>> arms = new IdentityHashMap<>();
    private final Map<ConditionOccurrence, Citation> metAt = new LinkedHashMap<>();
    private int next;

    ConditionNumbering(String module, String behavior) {
        this.module = module;
        this.behavior = behavior;
    }

    /** What this reading already made of the condition at {@code at} under {@code reads}, or null
     *  where it has not met it. */
    Condition alreadyRead(Core at, InputReads reads) {
        return read.get(new Site(at, reads));
    }

    /** Files {@code condition} as what the condition at {@code at} under {@code reads} is. */
    void read(Core at, InputReads reads, Condition condition) {
        read.put(new Site(at, reads), condition);
    }

    /** The name of the condition being recognised now. */
    ConditionOccurrence met() {
        return new ConditionOccurrence(behavior, next++);
    }

    /**
     * The name reaching {@code part} of {@code fork} goes by.
     *
     * <p>Refused where this reading has already named that arm, rather than answered with the name
     * it gave the first time. A fold of a subtree is asked for more than once and is registered
     * because of it; an arm is met where the walk meets the fork, and a second naming would be a
     * walk that had come to reach one arm twice — which is a walk saying a row satisfied one thing
     * two ways, and nothing downstream could tell the two apart. So it is raised here rather than
     * resolved by keeping one of them.
     */
    ConditionOccurrence metEntering(Core.Match fork, int part) {
        Map<Integer, ConditionOccurrence> named =
                arms.computeIfAbsent(fork, _ -> new LinkedHashMap<>());
        ConditionOccurrence already = named.putIfAbsent(part, met());
        if (already != null) {
            throw new IllegalStateException(
                    "this reading has already named arm " + part + " of a fork: " + already);
        }
        return named.get(part);
    }

    /**
     * Where a report about the condition {@code met} points, it having been written as
     * {@code construct} at {@code at}.
     *
     * <p>The writing module answers where it wrote a construct of its own that a reader can open.
     * Everything else — a construct this compiler composed, a condition of a shape the reading has
     * no words for, code in a file this compilation does not hold — is placed by this reading,
     * which is the only thing that met it.
     */
    ConditionReportAnchor anchorOf(SourceConstructOrigin construct, SourcePos at,
                                   ConditionOccurrence met) {
        Citation where = Citation.of(at);
        if (where instanceof Citation.Written && construct != null && construct.isWritten()) {
            return new ConditionReportAnchor.WhereItIsWritten(
                    new WrittenCondition.Construct(construct));
        }
        return metHere(met, where);
    }

    /** The same, for reaching one arm of a fork the source wrote as {@code fork}. */
    ConditionReportAnchor anchorOfArm(SourceConstructOrigin fork, int part, SourcePos at,
                                      ConditionOccurrence met) {
        Citation where = Citation.of(at);
        if (where instanceof Citation.Written && fork != null && fork.isWritten()) {
            return new ConditionReportAnchor.WhereItIsWritten(
                    new WrittenCondition.ForkArm(fork, part));
        }
        return metHere(met, where);
    }

    /** An address of this reading, with the place it addresses written down in the same act. */
    private ConditionReportAnchor metHere(ConditionOccurrence met, Citation where) {
        metAt.put(met, where);
        return new ConditionReportAnchor.WhereTheReadingMetIt(module, met);
    }

    /** Where this reading met each condition it places itself. */
    Map<ConditionOccurrence, Citation> metAt() {
        return Map.copyOf(metAt);
    }
}
