package souther.compiler.check;

import souther.compiler.semantics.ArgumentsStand;
import souther.compiler.semantics.DefinitionCase;
import souther.compiler.core.Core;
import souther.compiler.numeric.Rel;

import java.util.ArrayList;
import java.util.List;

/**
 * A value that is one of several: which several, and what decides each one.
 *
 * <p>One question with one answer. It was asked in four places and each wrote its own list — the
 * search for a split in a value position, the reading of what a split's arms are, the test of
 * whether a node is one, and the recording of what a value was computed from. All four said
 * {@code if} and {@code match}, and none said an attempted construction, which answers what it
 * built where its invariant held and what it departs with where it did not. So an attempt was a
 * value with a name and nothing recorded about the values it is one of, which is what
 * {@link Derivation.Chosen} was made for, standing under a third spelling.
 *
 * <p><b>Not every choice is written as one.</b> An operation the library defines by cases answers one
 * of the values it was given — {@code Int.min(a, b)} is {@code a} or it is {@code b} — and which it
 * is, is decided by how the arguments stand. Where the table saying so is read is here, so that a
 * call is one of these for every reader at once: it was read at one reader only, and a value written
 * where no clause stood over it came out of the walk with no range at all (#974).
 *
 * <p><b>What decides an arm is named here and read nowhere.</b> {@link Decides} is the node that
 * decides — a condition and which way it went, a case of a sum, an attempt that held or departed,
 * the arguments standing as a definition's case names — and this puts no interpretation on it. What
 * such a node means is asked of it in two places, and they ask different questions: what choosing the
 * arm <em>binds</em> is {@link Terms#choosing}'s answer, because what a binder denotes is that
 * class's throughout; what choosing it <em>settles</em> is {@link Conditions#settledBy}'s, where a
 * relation is stated, and needs a domain to come to anything. Keeping the two apart is what lets a
 * reader that only wants the values have them, while a reader that wants more is handed the node
 * rather than going back to the tree to find it for itself.
 *
 * <p>Which is also where a reader is made to stop. {@link Kind} on its own stops nobody: it is a
 * label, and a kind nothing produces is inert — adding one and answering for it compiles, and
 * should. What a new choice really needs is a case in {@link #of}, and then a way for each of its
 * arms to be decided. So the sum to switch over is {@link Decides}, which is what a reader has to
 * interpret to do anything at all, and a new way of deciding an arm fails exhaustiveness at every
 * such reader until it is handled. Every reader of it switches, and none tests for the arm it knows:
 * a reader written as an {@code instanceof} is one a new way of deciding passes straight through,
 * answering that nothing is settled where nobody has yet said what is.
 *
 * <p>That distinction is this class's own history. The forcing was first put on {@link Kind}, at a
 * reader that did not open anything, and a kind could be declared a split while no reader opened
 * one — a tripwire that read as holding, which is worse than none, since the words claimed it.
 *
 * <p><b>What a split asks is not here.</b> A split is what a walk over paths makes of a choice, and
 * it needs the one node deciding between the arms in order to read it where it stands. That node is
 * not something every choice has: {@code Int.min(a, b)} is decided by how two arguments stand and by
 * no expression written anywhere. Held here as a component, it was a field standing empty for such a
 * choice — the over-generalisation kept rather than answered — so it is projected out of
 * {@link Decides} by the reader that opens splits, which has an arm per way of deciding and stops at
 * the ways it does not open.
 */
public record Choice(Kind kind, List<Arm> arms) {

    /** What is being chosen between, for a reader whose answer differs by which it is. Named after
     * what decides the choice rather than after the syntax, since that is what a reader wanting more
     * than the values is asking about. */
    public enum Kind {

        /** A condition decides, and the values are its two branches. */
        A_CONDITION,

        /** Which case the scrutinee is decides, and the values are the arms' bodies. */
        A_CASE,

        /** Whether a construction's invariant held decides, and the values are what is answered
         * where it did and where it did not. */
        AN_ATTEMPT,

        /** How the arguments stand decides, and the values are the arguments the library's own
         * definition answers in each of its cases. */
        THE_ARGUMENTS
    }

    /** One value a choice may answer, and the node that decides it is the one. */
    public record Arm(Core answers, Decides decidedBy) {}

    /** Two values standing in a relation, as the values themselves. What a case of a library
     * definition is reached under, lowered out of the table's own way of naming an argument: a
     * reader below this asks what the relation says of two values, and not which position of which
     * call they arrived at. */
    public record ArgumentRelation(Core left, Rel rel, Core right) {}

    /**
     * What decides one arm, as the node that decides it.
     *
     * <p>Sealed, and one arm per way an arm is chosen rather than one per kind of choice: an attempt
     * that held and an attempt that departed are decided by different things and settle different
     * things, so a reader treating them alike would have to tell them apart again.
     */
    public sealed interface Decides {

        /** The condition held, or it did not. */
        record ACondition(Core cond, boolean holding) implements Decides {}

        /** The scrutinee is one of the cases this arm names. The scrutinee travels with it: what an
         * arm binds is the value already there, refined to the case. */
        record ACase(Core.Case arm, Core scrutinee) implements Decides {}

        /** The attempt's invariant held, so the value was built and its {@code as} name stands for
         * it. */
        record ItWasBuilt(Core.IfConstructed attempt) implements Decides {}

        /** The attempt's invariant did not hold, so this departure was taken and nothing was
         * built. */
        record ItDeparted(Core.IfConstructed attempt, Core.ElseArm on) implements Decides {}

        /**
         * The arguments stand as one case of the library's definition names, so the operation
         * answers what that case answers.
         *
         * <p>The relations and nothing about the call they were read off. Which argument of an
         * operation a rule meant is a question the table has already been asked
         * ({@link DischargeRules#chosenBy}), and a reader given the call and the row would be asking
         * it a second time — one table with two readers, which is what putting the arms here is for.
         */
        record ByArgumentRelations(List<ArgumentRelation> relations) implements Decides {

            public ByArgumentRelations {
                relations = List.copyOf(relations);
            }
        }
    }

    public Choice {
        arms = List.copyOf(arms);
        if (arms.isEmpty()) {
            throw new IllegalArgumentException(kind + " is a value that is one of several and it was"
                    + " given none to be one of");
        }
    }

    /**
     * The choice {@code e} is, or null where {@code e} answers one value.
     *
     * <p>Read off the node and not off what stands in it. An arm that is itself a choice is a choice
     * standing in an arm, which is what a reader of the arms finds when it reads them; flattening
     * the two here would answer about a value nothing is written at.
     *
     * <p>A call is asked of the table rather than of its shape. What every value written at an
     * {@code if} has in common is being one of two; what a call answers depends on the operation,
     * and for all but a few of them it is one value.
     */
    public static Choice of(Core e) {
        return switch (e) {
            case Core.If iff -> new Choice(Kind.A_CONDITION, List.of(
                    new Arm(iff.then(), new Decides.ACondition(iff.cond(), true)),
                    new Arm(iff.els(), new Decides.ACondition(iff.cond(), false))));
            case Core.Match m -> new Choice(Kind.A_CASE, m.cases().stream()
                    .map(c -> new Arm(c.body(), new Decides.ACase(c, m.scrutinee())))
                    .toList());
            case Core.IfConstructed ic -> new Choice(Kind.AN_ATTEMPT, attempted(ic));
            case Core.PreservedCall call -> defined(call);
            case null, default -> null;
        };
    }

    /** What an attempt answers: the value built where its invariant held, and what is taken where it
     * did not — one departure per clause the attempt names, and each of them a value of its own. */
    private static List<Arm> attempted(Core.IfConstructed ic) {
        List<Arm> out = new ArrayList<>();
        out.add(new Arm(ic.then(), new Decides.ItWasBuilt(ic)));
        ic.els().forEach(arm -> out.add(new Arm(arm.body(), new Decides.ItDeparted(ic, arm))));
        return out;
    }

    /**
     * The cases {@code call}'s operation is defined in, as arms, or null where it is defined in none.
     *
     * <p>The table is read through once, here, and what comes out of it is written in the values the
     * call was given. A reader below this one asks what a relation says of two values, which is a
     * question about the program; which argument of which operation those values arrived as is a
     * question about the library, and it is answered by the time an arm exists.
     */
    private static Choice defined(Core.PreservedCall call) {
        List<DefinitionCase<DeclaredArgument>> defined = DischargeRules.chosenBy(call);
        if (defined.isEmpty()) {
            return null;
        }
        List<Arm> arms = new ArrayList<>(defined.size());
        for (DefinitionCase<DeclaredArgument> one : defined) {
            List<ArgumentRelation> relations = new ArrayList<>(one.given().size());
            for (ArgumentsStand<DeclaredArgument> stands : one.given()) {
                relations.add(new ArgumentRelation(CallArguments.of(stands.left(), call), stands.rel(),
                        CallArguments.of(stands.right(), call)));
            }
            arms.add(new Arm(CallArguments.of(one.answers(), call), new Decides.ByArgumentRelations(relations)));
        }
        return new Choice(Kind.THE_ARGUMENTS, arms);
    }
}
