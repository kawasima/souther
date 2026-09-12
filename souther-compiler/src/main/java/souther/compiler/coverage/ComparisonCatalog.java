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

    private ComparisonCatalog(Map<ConstructOccurrence, Catalogued> byOccurrence) {
        this.byOccurrence = byOccurrence;
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
        Map<ConstructOccurrence, Met> met = new LinkedHashMap<>();
        for (Map.Entry<String, Core> body : of.bodies().entrySet()) {
            walk(body.getValue(), body.getKey(), byOccurrence, met);
        }
        return new ComparisonCatalog(byOccurrence);
    }

    /**
     * Where a comparison of this module was first met: the node it was standing at, and the body the
     * walk was in.
     *
     * <p>Kept while the walk runs and not after it. What the catalog answers is about a comparison,
     * and which node the walk happened to reach it at is about the walk — held on the finished
     * value, it would be a second way in to the trees beside the occurrence, and a reader could join
     * on either.
     *
     * <p>The node by identity, which is what tells a second arrival at one comparison from a second
     * comparison. An occurrence is worked out from what the source wrote and which copies it stands
     * in, so two of them are equal whenever those agree — and whether that means one comparison is
     * exactly what this is here to decide, so it cannot be decided by comparing them.
     */
    private record Met(Core.Binary node, String behavior) {}

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
                             Map<ConstructOccurrence, Met> met) {
        // What a representation kept standing for an analysis to read. What a run does is measured
        // over the tree that runs, which keeps none of these, so reaching one would mean this
        // enumeration was taken over a tree nothing executes.
        if (e instanceof Core.PreservedCall preserved) {
            throw preserved.unexpectedIn("the comparisons of a body");
        }
        if (e instanceof Core.Binary binary && binary.origin() != null
                && binary.origin().isWritten()) {
            // Recognised once and here, so what stands under an occurrence and what it carries are
            // one answer. Gathered as nodes and recognised again where the entry is made, this
            // would be the same question asked twice about one binary, with a case to answer for
            // the second answer being different.
            Comparison.of(binary).ifPresent(comparison ->
                    catalogue(binary, comparison, behavior, byOccurrence, met));
        }
        Core.forEachChild(e, child -> walk(child, behavior, byOccurrence, met));
    }

    /**
     * The entry one recognised comparison becomes, under the occurrence the node it was recognised
     * from carries.
     *
     * <p>A name, a recognition and a place put together, which is what makes this the one place they
     * are paired: each of the three is read off the node in hand, so none of them is a caller's to
     * supply.
     */
    private static void catalogue(Core.Binary binary, Comparison comparison, String behavior,
                                  Map<ConstructOccurrence, Catalogued> byOccurrence,
                                  Map<ConstructOccurrence, Met> met) {
        filed(binary, behavior, met);
        byOccurrence.put(binary.occurrence(), new Catalogued(binary.occurrence(),
                comparison, Citation.of(binary.pos()), binary.origin()));
    }

    /**
     * That this comparison may stand under the occurrence it carries, or why it may not.
     *
     * <p>What every reader below rests on, asked once and here. This is the one enumeration of what
     * a module's bodies hold, and everything that files anything about a comparison asks it which
     * comparison a node is — so a place two comparisons could share is a place two of their readers'
     * answers would land on top of each other, wherever the reader keeps them.
     *
     * <p><b>Three things arrive under one occurrence and only one of them is one comparison.</b> The
     * walk reaches a node twice where a body holds it twice over; a node stands in two bodies where
     * something spliced one tree into two; and two nodes carry one occurrence where what an
     * occurrence is made of does not yet tell two copies apart. The first is the state this walk is
     * built to expect and the other two are findings, so which of the three it is has to be decided
     * where all three are still distinguishable — after the entry is written they are one map key.
     *
     * <p><b>Refused rather than filed once or filed twice.</b> Filing once puts a run's place under
     * a comparison that is not the one it was numbered for; filing twice puts two under a name that
     * addresses one. Neither is something a reader could be told about, because both leave a
     * catalog that answers.
     */
    private static void filed(Core.Binary binary, String behavior,
                              Map<ConstructOccurrence, Met> met) {
        Met before = met.putIfAbsent(binary.occurrence(), new Met(binary, behavior));
        if (before == null) {
            return;
        }
        // Another node under one occurrence: what an occurrence is made of does not tell these two
        // copies apart. A library operation that applies a block it was handed more than once makes
        // such a pair, and what says which of them a run was recorded at is the copy the block was
        // applied at ({@link souther.compiler.types.ExpansionSite.Supplied}).
        if (before.node() != binary) {
            throw new IllegalStateException("two comparisons under one occurrence: "
                    + before.node().pos() + " and " + binary.pos() + " both stand at "
                    + binary.occurrence());
        }
        // One node in two bodies. Everything below files what it says per body — the plan numbers a
        // place in the body it is emitting, and a reading of a body asks that plan — so two bodies
        // holding one comparison is two readings each answered with the other's place.
        if (!before.behavior().equals(behavior)) {
            throw new IllegalStateException("one comparison of two bodies: " + binary.pos()
                    + " stands in " + before.behavior() + " and in " + behavior);
        }
    }

    /**
     * Which comparison {@code node} is, or empty where it is not one of this module's.
     *
     * <p>What a walk over the tree asks instead of matching on the shape of the node. Empty where
     * the occurrence the node carries is not one this catalog holds, which is the answer for a node
     * that is not a comparison, for one written where this compile has no source, and for one whose
     * comparison belongs to another module.
     *
     * <p>Asked of what the node carries and not of an index from nodes. The occurrence is the
     * tree's own answer to which construct this is, so a second index would be a second answer, and
     * whether this catalog holds it is the only part left for the catalog to say. So a node of
     * another tree is answered too, where the occurrence it carries is one of these: that is the
     * same construct met in another derivation of the same source, and what the two would be told
     * apart by is which walk reached them, which is not a fact about either.
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
