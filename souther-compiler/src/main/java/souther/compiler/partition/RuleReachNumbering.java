package souther.compiler.partition;

import souther.compiler.check.RuleReportAnchor;
import souther.compiler.diag.Citation;
import souther.compiler.types.SourceConstructOrigin;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The places one reading of a body met rules it has to place itself, and the address each goes by.
 *
 * <p><b>A register of what was met and not a count of the reading.</b> One rule is read more than
 * once by one reading of a body, and a rule met twice at one call is one thing a document sends a
 * reader to. Counted per reading, one entry would wear as many addresses as the walk happens to
 * produce, and a document choosing between them would be choosing between copies of one sentence.
 * So the number is handed out against the place, and a reading that meets the same place again is
 * answered with what the first came to.
 *
 * <p>Which is why the place and not the rule is the key. Two rules met at one call share the entry
 * and are still two handles: what tells them apart is the rule beside the anchor
 * ({@link souther.compiler.check.RuleCitation.Written}), so this is a table of places and never one
 * of identities.
 *
 * <p><b>One of these per body read, and every reader of that body takes its addresses here.</b> A
 * comparison, a fork and a predicate are three readers of one body, and an address means a place
 * only under something that says which addresses were being handed out. Numbered apart, one number
 * would name three places and nothing downstream could tell them apart.
 *
 * <p>It also says which question places a rule, because that is settled where the rule is read and
 * nowhere else. Whether the code is written somewhere a reader can open is what a position already
 * says, and it is the one thing about a position that survives the code moving — so it is the last
 * thing read off the position, and what comes out is which question a report puts later.
 *
 * <p>Where the answer is this reading's own, the place is written down in the same act. So there is
 * one entry per place a report can ask about and none for the rules whose construct a reader can go
 * and open.
 */
public final class RuleReachNumbering {

    private final String module;
    private final String behavior;
    /** The address each place already has, so that one call met twice is one entry. Beside
     *  {@link #reachedAt} rather than searched for in it: what this is for is that the same place
     *  is not handed two addresses, and reading it back out of the answer would make what it costs
     *  to name a place grow with the number of places named. */
    private final Map<Citation, Integer> addresses = new HashMap<>();
    private final Map<Integer, Citation> reachedAt = new LinkedHashMap<>();
    private int next;

    public RuleReachNumbering(String module, String behavior) {
        this.module = module;
        this.behavior = behavior;
    }

    /**
     * Where a report about a rule written as {@code construct}, and cited at {@code where}, points.
     *
     * <p>The writing module answers where it wrote a construct of its own that a reader can open.
     * A rule written in a file this compilation holds none of is placed by this reading, which is
     * the only thing that met it.
     *
     * <p>Handed the citation the reading already made rather than the position under it. Projected
     * again here, this would be a second answer to what a position may be said as, and the one that
     * decides the question would be whichever this happened to use.
     */
    public RuleReportAnchor anchorOf(SourceConstructOrigin construct, Citation where) {
        if (where instanceof Citation.Written && construct != null && construct.isWritten()) {
            return new RuleReportAnchor.ByTheModuleThatWroteIt();
        }
        return metHere(where);
    }

    /** An address of this reading, with the place it addresses written down in the same act. */
    private RuleReportAnchor metHere(Citation where) {
        int reach = addresses.computeIfAbsent(where, met -> {
            int next = this.next++;
            reachedAt.put(next, met);
            return next;
        });
        return new RuleReportAnchor.ByTheReadingThatMetIt(module, behavior, reach);
    }

    /** Where this reading met each rule it places itself. */
    public Map<Integer, Citation> reachedAt() {
        return Map.copyOf(reachedAt);
    }
}
