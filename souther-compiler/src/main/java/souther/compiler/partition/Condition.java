package souther.compiler.partition;

import souther.compiler.check.Comparison;
import souther.compiler.check.Symbols;
import souther.compiler.core.Core;
import souther.compiler.inputs.InputReads;
import souther.compiler.inputs.ReadMeaning;
import souther.compiler.semantics.ConditionJoin;

/**
 * What a condition of a body is made of.
 *
 * <p><b>One reading, because three readers were each making their own.</b> Which comparisons a fork
 * turns on, what each of them stands under, and what an arm proves are three questions about one
 * structure — and each was answered by matching on the shape of the {@link Core} node in front of
 * it. So each knew, on its own, which shapes are transparent, which combine, which are something to
 * say about, and which are where this compiler stops. A shape one of them learned to see through was
 * a shape the others still could not.
 *
 * <p>They came apart where a helper is called in a condition. An expanded helper binds the call's
 * argument to its own parameter, so what stands in the condition is a binding around the comparison
 * rather than the comparison — and the reading that finds comparisons anywhere in a condition saw it
 * and reported it as a rule written in a form this compiler does not read, while the reading that
 * draws lines never reached it at all. One line went missing, the position it divides came back
 * divided no way, and the region a guard beside it is searched in lost what that comparison
 * establishes. All from moving a comparison into a {@code let}, which changes nothing about what the
 * model says.
 *
 * <p>So the vocabulary is here and the readers fold over it. Three shapes:
 *
 * <ul>
 *   <li>{@link Joined} — two conditions put together, with what their connective makes of them;
 *   <li>{@link Compares} — one comparison, with the reading of the names in force where it stands;
 *   <li>{@link NotRead} — where this stops. A condition can be anything a {@code Bool} is, and what
 *       is not one of the shapes above says nothing here rather than being guessed at.
 * </ul>
 *
 * <p><b>A binding is transparent and is not one of the shapes.</b> What a {@code let} contributes is
 * where the names in its body point, which is why {@link Compares} carries the reading rather than
 * every reader threading one alongside. Carried as a shape instead, each reader would have to know
 * to look through it, which is the arrangement this replaces.
 *
 * <p><b>Not a way of finding comparisons.</b> This says what a boolean subtree means, and it used to
 * be walked to visit the comparisons inside one as well. A walk of a subtree needs a root, the root
 * anything ever gave it was a fork's condition, and so what a row had satisfied on the way to a
 * comparison was established only where a fork was written — {@code A && B} said nothing about
 * {@code B} where {@code if A then B else false} did. Which comparisons a body holds is
 * {@link souther.compiler.coverage.ComparisonCatalog}'s and where each of them stands is
 * {@link ComparisonReadings}'s, and neither has a root to be given.
 */
sealed interface Condition {

    /**
     * Which condition of this reading it is.
     *
     * <p>Kept by every shape, because a reader that could not take one in owes something to name it
     * by. Recovered afterwards from what is under a node, the name would be a child's — a
     * conjunction nothing could turn into a region would be named after whichever operand happened
     * to be first, which is a second account of which condition this is and is the kind of thing
     * this vocabulary exists to have one of.
     *
     * <p>Which condition and not where it is written. Every reader of this wants something to tell
     * one condition from its neighbours, and a place answers that only for as long as no two of
     * them are written alike; where a report about one points is {@link #anchor}'s question, asked
     * of whoever can answer it.
     */
    ConditionOccurrence occurrence();

    /**
     * Which question a report about it asks for its place.
     *
     * <p>Settled here, where what the source wrote is still in hand. Below this a reader holds a
     * condition and no node, and working out which question to put would mean going back to the
     * tree for something the recognition has already answered.
     */
    ConditionReportAnchor anchor();

    /**
     * Two conditions put together, and what the connective makes of them.
     *
     * @param how what the connective composes, which is read off the operator where this is made
     *            and is what a reader asks rather than the operator: a reader holding the operator
     *            reads it again for the same answer, and the two readings can be taught different
     *            ones
     */
    record Joined(ConditionOccurrence occurrence, ConditionReportAnchor anchor, ConditionJoin how,
                  Condition left, Condition right) implements Condition {}

    /**
     * One comparison, and where the names in it point.
     *
     * @param comparison the comparison together with what its operator placed. Recognising it is
     *                   what this shape is, so what the recognition established is carried rather
     *                   than left at the test that established it
     * @param reads      the reading in force where this comparison stands, which is the outer one
     *                   with every binding between here and the top taken in. A comparison inside
     *                   an expanded helper is about the argument the call handed it, and read
     *                   against the outer names it is about nothing
     */
    record Compares(Comparison comparison, ConditionOccurrence occurrence,
                    ConditionReportAnchor anchor, InputReads reads) implements Condition {}

    /** Where this reading stops: a condition of a shape it has no words for. */
    record NotRead(ConditionOccurrence occurrence, ConditionReportAnchor anchor)
            implements Condition {}

    /**
     * {@code e} read as a condition, under {@code reads}, taking its names from {@code numbering}.
     *
     * <p>Bindings are looked through and their names taken in; the two operators are taken apart;
     * everything else is either one comparison or nowhere this reading goes.
     *
     * <p>Called once for the condition it is about, however many outcomes of it a reader goes on to
     * ask about. Reaching one arm of a fork and reaching the other are two things this one
     * condition says, and a second fold for the second arm would name the same condition twice.
     */
    static Condition of(Core e, InputReads reads, Symbols symbols, ConditionNumbering numbering) {
        if (e instanceof Core.LetIn let) {
            return of(let.body(), reads.and(let.binder(), let.value()), symbols, numbering);
        }
        // A name standing for a truth is that truth. What a `let` binds is already carried for the
        // sake of which position a term names, and stopping at the name here left a fork on one
        // proving nothing while the same condition written out proved a comparison — the reading
        // being transparent to one reader and opaque to the other, over one binding.
        //
        // Asked of the one reading of a name rather than of the binding it happens to hold. What a
        // name is comes before what it was given — a parameter, an element an operation handed out,
        // one of several values an arm left standing — and going after the value without asking
        // would be this reader putting those in an order of its own.
        //
        // It terminates because a binder's value can only mention binders introduced before it, so
        // each step of this goes strictly outwards.
        if (e instanceof Core.Read name
                && reads.meaningOf(name, symbols) instanceof ReadMeaning.Through through) {
            return of(through.denotes().value(), through.denotes().at(), symbols, numbering);
        }
        if (e instanceof Core.Binary binary) {
            ConditionJoin joined = ConditionJoin.of(binary.op()).orElse(null);
            if (joined != null) {
                // The whole node is named before its operands are, so that the order the names come
                // in is the order a reader meets the conditions in.
                ConditionOccurrence met = numbering.met();
                return new Joined(met,
                        numbering.anchorOf(binary.origin(), binary.pos(), met), joined,
                        of(binary.left(), reads, symbols, numbering),
                        of(binary.right(), reads, symbols, numbering));
            }
            Comparison comparison = Comparison.of(binary).orElse(null);
            if (comparison != null) {
                ConditionOccurrence met = numbering.met();
                return new Compares(comparison, met,
                        numbering.anchorOf(binary.origin(), binary.pos(), met), reads);
            }
        }
        // Where this stops. A condition may be anything a `Bool` is, and the source wrote no
        // construct here to name one by — so this is placed by the reading that met it, which is
        // what handing no origin over says.
        ConditionOccurrence met = numbering.met();
        return new NotRead(met, numbering.anchorOf(null, e.pos(), met));
    }

    // Which binaries are comparisons is `Comparison#of`'s answer and is asked rather than spelled
    // out again. Written here as "a binary that is not `&&` or `||`", this said arithmetic was a
    // comparison — which nothing in a condition is, so it was a spelling that happened to be right.
}
