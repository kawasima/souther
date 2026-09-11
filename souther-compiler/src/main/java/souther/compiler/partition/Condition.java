package souther.compiler.partition;

import souther.compiler.check.Comparison;
import souther.compiler.check.Symbols;
import souther.compiler.core.Core;
import souther.compiler.inputs.InputReads;
import souther.compiler.inputs.ReadMeaning;
import souther.compiler.semantics.ConditionJoin;
import souther.compiler.types.ConstructOccurrence;
import souther.compiler.types.ModelOccurrence;

import java.util.Optional;

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
 *   <li>{@link Truth} — a value the body asks for the truth of, with the same reading beside it.
 * </ul>
 *
 * <p><b>Three shapes and no fourth for what was not recognised.</b> A condition is a {@code Bool},
 * so a subtree that is neither of the first two is the body asking whether that value holds — which
 * is a distinction it draws whether or not anything here can say what the value is. Written as a
 * shape that says nothing, every such condition was one thing, and a reader with words for the
 * value in front of it had nowhere to put them.
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
    record Compares(Comparison comparison, ConstructOccurrence stands,
                    ConditionOccurrence occurrence,
                    ConditionReportAnchor anchor, InputReads reads) implements Condition {

        /**
         * Which construct of the model this comparison is, where the source wrote one.
         *
         * <p>What a reader joining this to a run joins on: a rule is read where the language's
         * operations stand and a run through it is recorded where they are expanded, and the
         * construct of the model is what the two trees agree about. Empty for a comparison this
         * compiler composed, which states no rule and is nothing a run through can be about.
         */
        Optional<ModelOccurrence> states() {
            return ModelOccurrence.statedAt(stands);
        }
    }

    /**
     * A value the body asks for the truth of.
     *
     * <p>The value itself and not a comparison against {@code true}. A body writing {@code guard
     * allowed} wrote no comparison, and lowering one here would put a construct in front of a
     * reader that no source states and no run is seen at.
     *
     * <p>What the value is is left to whoever folds this. The reading in force is beside it for the
     * reason {@link Compares} carries one: a value named inside an expanded helper is the one the
     * call handed it, and read against the outer names it is about nothing.
     *
     * @param value what the body asks the truth of, as the tree the walk met it in
     */
    record Truth(ConditionOccurrence occurrence, ConditionReportAnchor anchor, Core value,
                 InputReads reads) implements Condition {}

    /**
     * {@code e} read as a condition, under {@code reads}, taking its names from {@code numbering}.
     *
     * <p>Bindings are looked through and their names taken in; the two operators are taken apart;
     * everything else is either one comparison or nowhere this reading goes.
     *
     * <p><b>One condition however often it is read.</b> A subtree is read as a condition more than
     * once — what stands past a short-circuit operator's left operand is read where the walk meets
     * the operator, and again inside whatever encloses it — and the readings are of one condition.
     * So the reading of a site is asked for and filed rather than made again, and the name it goes
     * by comes back with it. Made again, one condition would wear a name per reading, and two
     * accounts of it would agree about nothing.
     *
     * <p>Asked after the way in has been looked through, so that what is filed is the condition
     * rather than the route to it: a truth reached through a binding is the truth.
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
        Condition already = numbering.alreadyRead(e, reads);
        if (already != null) {
            return already;
        }
        // The recognition stays in this one method, which is what the registers of who reads an
        // operator name. Read in a helper beside it, the one place a condition becomes a shape
        // would be somewhere those registers do not look.
        Condition made = null;
        if (e instanceof Core.Binary binary) {
            ConditionJoin joined = ConditionJoin.of(binary.op()).orElse(null);
            Comparison comparison = joined == null ? Comparison.of(binary).orElse(null) : null;
            if (joined != null) {
                // The whole node is named before its operands are, so that the order the names come
                // in is the order a reader meets the conditions in.
                ConditionOccurrence met = numbering.met();
                made = new Joined(met,
                        numbering.anchorOf(binary.origin(), binary.pos(), met), joined,
                        of(binary.left(), reads, symbols, numbering),
                        of(binary.right(), reads, symbols, numbering));
            } else if (comparison != null) {
                ConditionOccurrence met = numbering.met();
                made = new Compares(comparison, binary.occurrence(), met,
                        numbering.anchorOf(binary.origin(), binary.pos(), met), reads);
            }
        }
        if (made == null) {
            // Everything else is the body asking whether a value holds. The source wrote no
            // construct here to name the condition by, so it is placed by the reading that met it,
            // which is what handing no origin over says.
            ConditionOccurrence met = numbering.met();
            made = new Truth(met, numbering.anchorOf(null, e.pos(), met), e, reads);
        }
        numbering.read(e, reads, made);
        return made;
    }

    // Which binaries are comparisons is `Comparison#of`'s answer and is asked rather than spelled
    // out again. Written here as "a binary that is not `&&` or `||`", this said arithmetic was a
    // comparison — which nothing in a condition is, so it was a spelling that happened to be right.
}
