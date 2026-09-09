package souther.compiler.coverage;

import souther.compiler.check.Comparison;
import souther.compiler.core.Core;
import souther.compiler.diag.Citation;
import souther.compiler.types.ConstructOccurrence;
import souther.compiler.types.SourceConstructOrigin;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Which comparisons the bodies of a module hold, as a fact about the bodies.
 *
 * <p>One answer, and everything that asks about a comparison asks this. A number is handed out for
 * one, a line is drawn on one, a run is proved never to reach one, and a path is named by one — four
 * readers that each used to decide for themselves what a comparison is, by descending {@code &&} and
 * {@code ||} from a fork's condition. Four descents of one shape are four sets that can drift apart,
 * and the one they agreed on was not the comparisons of a body: it was the comparisons a fork was
 * written directly around.
 *
 * <p>So where a comparison stands is not part of this. {@code a > 1} tested by an {@code if}, given
 * a name a line above it, returned as the behavior's answer, or written inside a function value
 * handed to a combinator is one construct put to four uses — and which use it was put to is a
 * question about the body that the readings answer, each in its own terms.
 *
 * <p>Atomic and no wider. {@code &&} and {@code ||} combine comparisons rather than being ones, and
 * {@code +} is not one at all; what this holds is exactly what leaves a truth on the stack for a
 * probe to copy, which is what lets the numbering be checked rather than remembered.
 *
 * <p><b>The node gets a reader in and goes no further.</b> A walk over the tree — the emitter, the
 * numbering — meets a node and has to ask whether it is a comparison of these bodies, and
 * {@link #occurrenceAt} is that question. What comes back is the
 * {@link souther.compiler.types.ConstructOccurrence} the node carries, which is what every reader
 * below joins on, and there is no way back from one to the node it was found at. That is what stops
 * the tree being the join key: a reader holding an occurrence cannot fall back on matching objects,
 * and cannot go to the node for an operator the recognition has already read.
 */
public final class ComparisonCatalog {

    /**
     * One comparison of one body, as the source wrote it.
     *
     * <p>Occurrence and not comparison. A non-recursive helper is spliced into each body that calls
     * it, so one comparison the author wrote stands here once per call — each reached under its
     * caller's own conditions, and each its own thing to say something about.
     *
     * @param which      which comparison of which body this is, which is what every reader joins on
     * @param comparison what the recognition established: what the operator placed, and the two
     *                   sides it placed it on. Recognising the node as a comparison is what puts it
     *                   here, so what the recognition established travels with it and a reader
     *                   below has no operator left to read again
     * @param at         where it is written, as a report may say it. A {@link Citation} and not a
     *                   position, because a comparison spliced in from another module is written in
     *                   that module's file and reached from a call in this one — and it is here
     *                   rather than taken again wherever a report needs one, so that a rule and the
     *                   line it draws are found at one place because they read one answer
     * @param origin     what wrote it, which is how a report names the rule it states. Beside the
     *                   citation because they are one question — which written thing this is — and
     *                   a reader that had to go to the tree for either would have the tree, and
     *                   with it everything the recognition already answered
     */
    public record Catalogued(ConstructOccurrence which, Comparison comparison, Citation at,
                             SourceConstructOrigin origin) {

        public Catalogued {
            if (which == null || comparison == null || at == null || origin == null) {
                throw new IllegalArgumentException(
                        "a catalogued comparison is one comparison, named, placed and attributed");
            }
        }
    }

    /** What the module holds, under the occurrence each stands at. */
    private final Map<ConstructOccurrence, Catalogued> byOccurrence;

    /** Which body each of them was walked from, for the one question that is about the walk. */
    private final Map<ConstructOccurrence, String> bodyOf;

    private ComparisonCatalog(Map<ConstructOccurrence, Catalogued> byOccurrence,
                              Map<ConstructOccurrence, String> bodyOf) {
        this.byOccurrence = byOccurrence;
        this.bodyOf = bodyOf;
    }

    /**
     * The comparisons of every behavior body of one module.
     *
     * <p>Taken as one value because what this answers is about the pair: the module says whose
     * bodies these are, and a caller handed the two apart could put one module's name beside
     * another's trees. What comes back would be a catalog of somebody else's comparisons, and no
     * later check could refuse it, since the catalog being asked is the one that was built.
     */
    public static ComparisonCatalog of(ModuleBodies of) {
        Map<ConstructOccurrence, Catalogued> byOccurrence = new LinkedHashMap<>();
        Map<ConstructOccurrence, String> bodyOf = new LinkedHashMap<>();
        for (Map.Entry<String, Core> body : of.bodies().entrySet()) {
            walk(body.getValue(), body.getKey(), byOccurrence, bodyOf);
        }
        return new ComparisonCatalog(byOccurrence, bodyOf);
    }

    /**
     * Whether {@code which} is one this issued.
     *
     * <p>What tells a comparison this catalog does not instrument from one it never held. The two
     * read alike to whoever asks about runs — neither has a site — and they are not the same thing:
     * the first is a comparison of this module that no run reaches, and the second is somebody
     * asking this module about another module's comparison.
     */
    boolean holds(ConstructOccurrence which) {
        return byOccurrence.containsKey(which);
    }

    /** Every comparison of one body, recognised where the walk meets it. */
    private static void walk(Core e, String behavior,
                             Map<ConstructOccurrence, Catalogued> byOccurrence,
                             Map<ConstructOccurrence, String> bodyOf) {
        // What a representation kept standing for an analysis to read. What a run does is measured
        // over the tree that runs, which keeps none of these, so reaching one would mean this
        // enumeration was taken over a tree nothing executes.
        if (e instanceof Core.PreservedCall preserved) {
            throw preserved.unexpectedIn("the comparisons of a body");
        }
        // A comparison this catalog already holds, met again while walking another body. One
        // construct of the model belongs to whoever wrote it and is one thing to say something
        // about, and everything below files what it says per body: the plan numbers a place in the
        // body it is emitting, and a reading of a body asks that plan. Two bodies holding one
        // construct is two bodies whose readings would each be answered with the other's place, so
        // it is refused rather than filed twice or filed once.
        if (e instanceof Core.Binary shared && shared.occurrence() != null
                && bodyOf.containsKey(shared.occurrence())
                && !bodyOf.get(shared.occurrence()).equals(behavior)) {
            throw new IllegalStateException("one comparison of two bodies: " + shared.pos()
                    + " stands in " + bodyOf.get(shared.occurrence()) + " and in " + behavior);
        }
        if (e instanceof Core.Binary binary && binary.origin() != null
                && binary.origin().isWritten()) {
            // Recognised once and here, so what stands under an occurrence and what it carries are
            // one answer. Gathered as nodes and recognised again where the entry is made, this
            // would be the same question asked twice about one binary, with a case to answer for
            // the second answer being different.
            //
            // A node this walk arrives at twice is one comparison written once, and arriving again
            // writes the entry it already wrote. Nothing has to be remembered to make that so: what
            // the entry is filed under is the occurrence the node carries, and a second arrival at
            // one node carries one occurrence.
            Comparison.of(binary).ifPresent(comparison -> {
                byOccurrence.put(binary.occurrence(), new Catalogued(binary.occurrence(),
                        comparison, Citation.of(binary.pos()), binary.origin()));
                bodyOf.put(binary.occurrence(), behavior);
            });
        }
        Core.forEachChild(e, child -> walk(child, behavior, byOccurrence, bodyOf));
    }

    /**
     * Which comparison {@code node} is, or empty where it is not one of this module's.
     *
     * <p>What a walk over the tree asks instead of matching on the shape of the node. Empty is the
     * answer for a node that is not a comparison, for one written where this compile has no source,
     * and for one standing in a tree this catalog was not taken over — three things a reader
     * deciding for itself would have had to remember to ask about separately.
     *
     * <p>Asked of what the node carries and not of an index from nodes. The occurrence is the
     * tree's own answer to which construct this is, so a second index would be a second answer, and
     * whether this catalog holds it is the only part left for the catalog to say.
     */
    public Optional<ConstructOccurrence> occurrenceAt(Core node) {
        return node instanceof Core.Binary binary && binary.occurrence() != null
                && byOccurrence.containsKey(binary.occurrence())
                ? Optional.of(binary.occurrence()) : Optional.empty();
    }

    /** The same, together with what was recognised there and where it is written. */
    public Optional<Catalogued> at(Core node) {
        return occurrenceAt(node).map(byOccurrence::get);
    }

    /** Every comparison the module holds, in the order the bodies were walked. Kept to this
     *  package: what a reader outside asks is about one comparison it was handed, and a list to
     *  walk is how a reader comes to have its own idea of which comparisons there are. */
    List<Catalogued> all() {
        return List.copyOf(byOccurrence.values());
    }
}
