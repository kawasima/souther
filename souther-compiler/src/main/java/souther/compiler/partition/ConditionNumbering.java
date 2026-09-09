package souther.compiler.partition;

import souther.compiler.diag.Citation;
import souther.compiler.diag.SourcePos;
import souther.compiler.sites.WrittenCondition;
import souther.compiler.types.SourceConstructOrigin;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The names one reading gives the conditions it meets, while it is meeting them.
 *
 * <p>A number and what it names are one act: the fold asks for the next name as it recognises a
 * condition, so there is no moment at which a name exists and which condition it is has still to be
 * worked out. Which is what the whole of the identity rests on — a number paired with a condition
 * anywhere else would be that correspondence built a second time, by something that had not done
 * the recognising.
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

    private final String module;
    private final String behavior;
    private final Map<ConditionOccurrence, Citation> metAt = new LinkedHashMap<>();
    private int next;

    ConditionNumbering(String module, String behavior) {
        this.module = module;
        this.behavior = behavior;
    }

    /** The name of the condition being recognised now. */
    ConditionOccurrence met() {
        return new ConditionOccurrence(behavior, next++);
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
