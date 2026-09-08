package souther.compiler.types;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

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
 * <p><b>A construct stands where whoever wrote it wrote it.</b> Running a block is the copy holding
 * it reaching code somebody else wrote, so what stands inside that block stands where the block was
 * written and not where it was run — and every copy made between the two is a copy of the running
 * and not of what is written. So a crossing leaves them all: a copy made at a
 * {@linkplain ExpansionSite.Supplied supplied} site says which copy was handed the block, and what
 * follows it is the code that handed it over. Nothing here looks a name up in a declaration or asks
 * what bound a block.
 *
 * <p><b>The copies an operation makes are not the model's at all.</b> What the language defines the
 * meaning of is the operation, not the walk it turns into, so a construct still inside one when the
 * copies run out is one the model states nowhere. Every other copy left is one the model makes — a
 * helper spliced into each call of it — and those are what a construct of the model stands in.
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
     * <p>Empty says that and only that. Which materialisations of a construct the model does state
     * the tree that runs holds, and which of those a run through is recorded at, are further
     * questions and different absences ({@code ComparisonEmissionIndex#madeFor}) — a construct may
     * be written into that tree more than once, and each of those may or may not be numbered. They
     * are answered by different things so that none can be read off another.
     *
     * <p>A copy of an operation left open at the end is what says it: the construct stands inside
     * the operation's own body, either because the operation takes no block or because this stands
     * before the block it takes. A crossing out of it says the operation's body reached the code
     * that was handed over, and what stands after that belongs to whoever handed it.
     */
    public static Optional<ModelOccurrence> statedAt(ConstructOccurrence occurrence) {
        // Refused rather than met further in: a construct of the model is some construct, and what
        // a walk over one that was not would come back with is an answer about nothing.
        if (occurrence == null) {
            throw new IllegalArgumentException(
                    "a construct with no place is no occurrence of the model");
        }
        Deque<ExpansionLineage.Expansion> open = new ArrayDeque<>();
        for (ExpansionLineage.Expansion step : copiesIn(occurrence.lineage())) {
            // A copy of a block one of the copies still open was handed: running it is that copy
            // reaching the code whoever wrote the call supplied, so what stands inside it was
            // written where that copy was called — and everything opened since is left with it. A
            // copy passes a block it was given straight on to another, and the application that
            // runs it is written inside the second while what it runs was handed to the first, so
            // what is left is a level and not a bracket.
            if (step.at() instanceof ExpansionSite.Supplied supplied
                    && open.stream().anyMatch(each -> each.step().equals(supplied.copy()))) {
                while (!open.pop().step().equals(supplied.copy())) {
                    // Everything opened on the way to handing the block on.
                }
                continue;
            }
            open.push(step);
        }
        // What the model states nothing at: a construct standing inside one of the language's own
        // operations, which the reading that states rules never enters. Every other copy still open
        // is a copy the model itself makes — a helper spliced into each call of it — and those are
        // what a construct of the model stands in.
        //
        // Read off the name a copy is of, which is what says whose code was copied: everything the
        // library declares is reached under the name the library publishes it by, private helpers
        // included ({@link souther.compiler.check.HelperTable}), and nothing a module declares is
        // reached under one. So a copy of the language's own code is one of these and a copy of a
        // model's code is not, whichever of them the call that made it was written in.
        if (open.stream().anyMatch(each -> each.expanded() instanceof ValueName.Stdlib.Operation)) {
            return Optional.empty();
        }
        ExpansionLineage model = ExpansionLineage.ORIGINAL;
        for (Iterator<ExpansionLineage.Expansion> outermost = open.descendingIterator();
                outermost.hasNext();) {
            ExpansionLineage.Expansion each = outermost.next();
            model = model.copiedInto(each.expanded(), each.at());
        }
        return Optional.of(new ModelOccurrence(occurrence.origin(), model));
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
