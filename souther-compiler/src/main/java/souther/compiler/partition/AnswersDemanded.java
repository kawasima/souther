package souther.compiler.partition;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What a way through a body asks of the answers a row stands its dependencies in with.
 *
 * <p>Both halves, for the reason {@link WayToTheBorder} keeps both of its: what was stated is what a
 * search composes an answer against, and what could not be stated is why a search may be composing
 * an answer that does not take the way. Split apart, "the way asks nothing of any answer" and
 * "everything it asks was stated" come out as the same empty answer, and only the second is a way a
 * composed row reaches.
 *
 * @param stated   the demands, in the order the walk met the conditions they came from
 * @param declined the conditions about an answer that this has no way of stating, each named by
 *                 where a report about it asks for its place
 */
public record AnswersDemanded(List<AnswerDemand> stated,
                              List<ConditionReportAnchor> declined) {

    /** A way that asks nothing of any answer, which is what a body deciding on its input alone
     *  states. */
    public static final AnswersDemanded NOTHING = new AnswersDemanded(List.of(), List.of());

    public AnswersDemanded {
        stated = List.copyOf(stated);
        declined = List.copyOf(declined);
    }

    /** Whether everything this way asks of an answer is something a value can be composed for. */
    public boolean whole() {
        return declined.isEmpty();
    }

    /** The demands on each answer, in the order the answers were first asked about. */
    public Map<InjectedAnswer, List<AnswerDemand>> byAnswer() {
        Map<InjectedAnswer, List<AnswerDemand>> out = new LinkedHashMap<>();
        for (AnswerDemand each : stated) {
            out.computeIfAbsent(each.of(), _ -> new ArrayList<>()).add(each);
        }
        return out;
    }
}
