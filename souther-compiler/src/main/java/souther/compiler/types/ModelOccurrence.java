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
     * What {@code occurrence} is an occurrence of, with the copies the language's operations brought
     * taken off.
     *
     * <p>The one crossing from a reading of a body to what the model states, so the two readings
     * reach one value or neither does.
     *
     * <p>What comes back for a construct written inside an operation's own body is a value like any
     * other, and it is not a value the other reading holds — that reading never enters the body it
     * was written in. Whether the model states anything where a construct stands is answered by
     * looking for it in the reading that states rules, and never by this value on its own.
     *
     * <p>An envelope left open at the end is a construct standing inside an operation's own body:
     * the operation takes no block, or takes one and this stands before it. Nothing closes it and
     * nothing should — what is inside an operation is inside it, and what comes back is the copies
     * the caller made on the way, which is what the other reading holds too.
     *
     */
    public static ModelOccurrence of(ConstructOccurrence occurrence) {
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
        return new ModelOccurrence(occurrence.origin(), model);
    }

    /**
     * Whether {@code step} is the operation's own body re-entering what the caller handed it.
     *
     * <p>Read off what the block belongs to. A block a call handed to a parameter belongs to the
     * copy taking it, and a block an author bound belongs to the body that bound it — so the second
     * is a copy the model makes and stays, and only the first closes anything.
     */
    private static boolean closes(ExpansionLineage.Expansion step,
                                  ExpansionLineage.Expansion operation) {
        return step.expanded() instanceof ValueName.Local block
                && block.id().owner() instanceof BindingOwner.Expansion copy
                && copy.expanded().equals(operation.expanded());
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
