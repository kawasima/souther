package souther.compiler.types;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.ArrayList;
import java.util.List;

/**
 * One construct of the model, wherever a reading of a body meets it: which construct the source
 * wrote, and which of the copies the model itself makes it stands in.
 *
 * <p>What the two readings of a body agree about. A body is read twice — once with the language's
 * own operations expanded into what they do, and once with them standing — so a construct written
 * in a block handed to one of them stands in copies in the first reading and where it was written in
 * the second. Both are true of the reading they are of ({@link ConstructOccurrence}), and neither is
 * what a rule of the model is about.
 *
 * <p><b>Between the rule and the place.</b> {@code RuleRef} says which rule the source states, and a
 * helper called twice states one; a construct occurrence says which materialisation of it a tree
 * holds, and the two readings hold different ones. This is the third thing: which copy of the rule
 * the model makes, which a helper called twice has two of and an operation's own copies none of. A
 * reader joining the two readings on the first is joining many to many; on the second it is joining
 * nothing.
 *
 * <p><b>The copies an operation makes are not the model's.</b> What the language defines the meaning
 * of is the operation, not the walk it turns into, so the copies made inside one are how a backend
 * writes it out. They come in an envelope: it opens where an operation is expanded and closes where
 * the operation's own body re-enters the block the caller handed it, and it nests. What closes one is
 * read off the block — a block belongs to the copy that bound it — so nothing here looks a name up in
 * a declaration.
 */
public record ModelOccurrence(SourceConstructOrigin origin, ExpansionLineage lineage) {

    public ModelOccurrence {
        if (origin == null || lineage == null) {
            throw new IllegalArgumentException(
                    "a construct of the model is some construct, in some copy the model makes: "
                            + origin + " in " + lineage);
        }
    }

    /**
     * Which construct of the model {@code occurrence} is a materialisation of, or empty where the
     * model states nothing where it stands.
     *
     * <p><b>Partial, and that is what the name says.</b> A construct written inside one of the
     * language's own operations is materialised once per call of that operation, and the model
     * states nothing at any of them: what the language defines the meaning of is the operation, and
     * the reading that states rules never enters its body. Made total, those materialisations would
     * all come back as one construct of the model — one key over as many places as a body calls the
     * operation, which is what a reader asking where a run is recorded cannot have.
     *
     * <p>Empty says that and only that. Where a run through a construct the model does state is
     * recorded is a further question and a different absence
     * ({@code ComparisonEmissionIndex#siteOf}), and the two are answered by different things so that
     * neither can be read off the other.
     *
     * <p>An envelope left open at the end is what says it: the construct stands inside an
     * operation's own body, either because the operation takes no block or because this stands
     * before the block it takes. A closed one says the operation's body reached the code its caller
     * handed over, and what stands after that is the caller's again.
     */
    public static java.util.Optional<ModelOccurrence> statedAt(ConstructOccurrence occurrence) {
        // A term with its places taken out is a key for comparing two readings of one body and not
        // a construct of the model ({@link Core#withoutItsPlace}). Refused rather than met further
        // in: what such a walk would come back with is an answer about a node that says it stands
        // nowhere, and every caller here is walking a tree whose places are still on it.
        if (occurrence == null) {
            throw new IllegalArgumentException(
                    "a construct with no place is no occurrence of the model");
        }
        Deque<ExpansionLineage.Expansion> open = new ArrayDeque<>();
        ExpansionLineage model = ExpansionLineage.ORIGINAL;
        for (ExpansionLineage.Expansion step : copiesIn(occurrence.lineage())) {
            if (step.expanded() instanceof ValueName.Stdlib.Operation) {
                open.push(step);
                continue;
            }
            // A block belonging to one of the operations still open: applying it is that
            // operation's body reaching the code its caller handed over, so everything opened since
            // is left too. An operation passes a block it was given straight on to another, and the
            // application that runs it is written inside the second while what it runs was handed to
            // the first — so what is left is a level and not a bracket.
            if (open.stream().anyMatch(each -> closes(step, each))) {
                while (!closes(step, open.pop())) {
                    // Everything the operation's own body opened on the way to handing the block on.
                }
                continue;
            }
            if (open.isEmpty()) {
                model = model.copiedInto(step.expanded(), step.at());
            }
        }
        return open.isEmpty()
                ? java.util.Optional.of(new ModelOccurrence(occurrence.origin(), model))
                : java.util.Optional.empty();
    }

    /**
     * Whether {@code step} is the operation's own body re-entering what the caller handed it.
     *
     * <p>Read off where the copy was made ({@link ExpansionSite.Supplied}), which is the one thing
     * that says a callable came from outside the operation. A copy made at code the caller supplied
     * is the operation's body reaching back out to it, whatever the caller wrote there — a lambda at
     * the call, a name they bound first, a second name for either — and a copy the operation made of
     * its own code is not.
     *
     * <p><b>Not what the applied binding belongs to.</b> That was read here, and it answered by
     * standing next to the fact rather than being it: a lambda written at the call is bound by the
     * expansion taking it, so its owner was the operation's copy and the reading worked; a lambda
     * the author bound to a name first is bound by their own body, so the same reading said the
     * operation's body was still running its own code and the envelope never closed. One model
     * spelled two ways came out as two, and the second stopped the compile.
     */
    private static boolean closes(ExpansionLineage.Expansion step,
                                  ExpansionLineage.Expansion operation) {
        return step.at() instanceof ExpansionSite.Supplied supplied
                && supplied.copy() instanceof ExpansionLineage.Expansion took
                && took.expanded().equals(operation.expanded());
    }

    /** The copies of {@code lineage}, outermost first. */
    private static List<ExpansionLineage.Expansion> copiesIn(ExpansionLineage lineage) {
        List<ExpansionLineage.Expansion> out = new ArrayList<>();
        for (ExpansionLineage each = lineage;
                each instanceof ExpansionLineage.Expansion step; each = step.within()) {
            out.add(0, step);
        }
        return out;
    }

    @Override
    public String toString() {
        return lineage instanceof ExpansionLineage.Original ? String.valueOf(origin)
                : origin + " in " + lineage;
    }
}
