package souther.compiler.check;

import souther.compiler.core.Core;
import souther.compiler.semantics.ConditionJoin;
import souther.compiler.types.BinOp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A clause as its connectives, read out of the tree once.
 *
 * <p>What a clause is written out of — a conjunction, a choice, a denial — is the clause's own
 * shape. It used to be read by each reading that wanted it, which was one mapping from {@link Core}
 * per reading and as many chances for them to drift; and it had to be read again by anything that
 * wanted to look at the clause before answering it, which is how a walk that decided what a reading
 * could afford came to interpret {@code &&} a second time.
 *
 * <p>So the shape is read here and nowhere else. What comes out is this, and everything downstream
 * — what the values leave, where the ends are, what has to be built and what it costs — is an
 * evaluator over it. Two evaluators over one structure are not two readings of one clause: they
 * agree about what the clause is by construction, because neither of them is the thing that decided.
 *
 * <p><b>Denials are gone by the time this exists.</b> A denial is carried to the leaves as the tree
 * is read, so a conjunction denied is the choice between its parts denied, and a leaf says which of
 * the two it states. What is left holds no {@code not}, and a reader below never has to ask whether
 * it is inside one.
 *
 * <p><b>The tree's own nodes are kept.</b> A reader that walks the clause afterwards looks up what
 * this made of the very node it is holding, so every node that mapped to a shape is written down —
 * a denial and what is under it map to the same shape and both are named. Shapes are not gathered
 * across brackets for the same reason: {@code (a && b) && c} holds a node for {@code a && b}, and a
 * reader asking about it is asking about something the author wrote.
 *
 * <p><b>A binding is a shape here, and it is not a connective.</b> What a helper call expands to is
 * a binding holding the argument and the helper's body written against it (spec
 * §invariant-discharge-representation), so a clause stating its rule through a helper is a clause
 * under a binding. {@link Scoped} is where the environment a reading carries changes and nothing
 * else: it composes no statements, and what is under it is read exactly as the same rule written out
 * would be. Left as a leaf, it was a form every reading below had no word for, and an author who
 * named a rule lost the reading an author who repeated it kept.
 */
sealed interface ClauseExpr {

    /**
     * The tree nodes this stands for, outermost first.
     *
     * <p>More than one where a denial was carried down: {@code not(x)} and {@code x} are one shape
     * and two nodes, and a reader holding either of them is holding this.
     */
    List<Core> spelled();

    /**
     * Which occurrence of the clause's structure this is, as the one reading of that structure
     * issued it.
     *
     * <p>What a reader files an answer about this shape under. Two shapes of one clause are two of
     * these and are told apart by being different occurrences, which is what the nodes were doing
     * before: a reader that keyed an answer by the tree node held an address only for as long as
     * every reader was handed the very same objects, and what a clause states is the same clause
     * however many trees were built to say it.
     *
     * <p>Issued here because this is where the structure is decided. A reader that numbered the
     * occurrences for itself would be a second answer to how many there are, and two answers to
     * that is what reading the connectives twice came to.
     */
    Occurrence at();

    /**
     * One occurrence of a clause's structure, counted from the outside in.
     *
     * <p>A position among the occurrences of one clause and not a name, so it means something only
     * beside the clause it is of — two clauses each have an occurrence numbered nought, and they
     * are two occurrences. What pairs it with the clause is the reader's, which holds the rule and
     * the part it is reading.
     *
     * <p>Independent of how the clause stands. A denial changes what a connective composes and
     * changes nothing about which occurrences there are, so a clause read as stated and the same
     * clause read as denied number their occurrences alike — and an answer filed about one is an
     * answer about the other.
     */
    record Occurrence(int ordinal) {

        /** The clause itself, which takes the first number before anything under it does — see
         *  {@link ClauseExpr#under}. */
        static Occurrence ofTheClause() {
            return new Occurrence(0);
        }

        public Occurrence {
            if (ordinal < 0) {
                throw new IllegalArgumentException(
                        "an occurrence of a clause is counted from zero: " + ordinal);
            }
        }

        @Override
        public String toString() {
            return "#" + ordinal;
        }
    }

    /**
     * Whether what this stands for is stated, or denied where it is not.
     *
     * <p>On every shape and not on the leaves alone. A denial is carried to the leaves, so a reader
     * that walks down to them is never asked to work it out — but a reader that stops at a node and
     * takes what is under it whole is asked exactly that, and the answer is here. Worked out from
     * what a shape was spelled as instead, a reader would be reading the polarity off the nodes a
     * restatement left, and a restatement that denies nothing looks the same there as one that
     * does.
     */
    boolean positive();

    /**
     * The subtree of the tree this shape was written as, which is the outermost of what it is
     * spelled as.
     *
     * <p>What an author wrote at this position, denials included. A reading that names a part it
     * declined to descend into names it by this: {@link Leaf#of} and {@link Joined#of} have the
     * denials taken off, which is what a reading of the part wants and not what a report about
     * where it was written does.
     */
    default Core written() {
        return spelled().get(0);
    }

    /**
     * What a reading is handed: a part of no connective, or a connective it takes whole.
     *
     * <p>A binding standing in the clause is not one of these. Where the environment changes is the
     * fold's to find and {@link ClauseScope}'s to answer, so a reading is never handed one as a
     * part — which is a fact about the types here and not a rule anybody has to keep.
     *
     * <p>What is inside a part is another matter. A binding nested there is still part of that
     * leaf, and each question the part language asks about its own inside crosses it by asking the
     * environment (ADR-0106) — which is the one answer again and not a second account of it.
     */
    sealed interface Part extends ClauseExpr permits Leaf, Joined {

        /** The part itself, with the denials above it taken off, which is the innermost of what it
         *  is spelled as. */
        Core of();
    }

    /** One part of no connective, stated where {@code positive} and denied where it is not. */
    record Leaf(List<Core> spelled, boolean positive, Occurrence at) implements Part {

        public Leaf {
            spelled = named(spelled);
        }

        /** The part itself, which is the innermost of what it is spelled as. */
        @Override
        public Core of() {
            return spelled.get(spelled.size() - 1);
        }
    }

    /**
     * Two parts and what holding this one says of them.
     *
     * @param how what the connective composes, with the denial the tree was read under already
     *            applied: what is left holds no {@code not}, so a reader below asks this and never
     *            the operator the clause was written with. What it composes and how the whole
     *            stands are two answers: a choice denied composes both of its parts denied, so
     *            {@code BOTH} beside {@code positive} being false is a choice and not a conjunction
     */
    record Joined(List<Core> spelled, boolean positive, Occurrence at, ConditionJoin how,
                  ClauseExpr left, ClauseExpr right) implements Part {

        public Joined {
            spelled = named(spelled);
        }

        /**
         * The connective itself, which is the innermost of what it is spelled as.
         *
         * <p>What an author wrote and where they wrote it, which is the operator: a reading with
         * something to say about the choice sends a reader to the {@code ||}, not to the operand it
         * happens to begin at. A denial carried down leaves the operator the author typed here and
         * the {@code not} above it, and the operator is the innermost of the two.
         */
        @Override
        public Core of() {
            return spelled.get(spelled.size() - 1);
        }

        /**
         * The two subtrees this composes, as the author wrote them.
         *
         * <p>For a reading that takes the connective whole and still has something to say about
         * what stands under it — that the author named these two values, say. Read back off the
         * operator instead, such a reading would be recovering a structure it has already been
         * given, which is the shape being worked out twice.
         *
         * <p>Two halves and no further. What is under each of them is that half's own shape, and
         * this hands over the halves rather than the leaves below them.
         */
        List<Core> writtenHalves() {
            return List.of(left.written(), right.written());
        }
    }

    /**
     * A binding and the clause under it, which is read in the environment the binding makes.
     *
     * <p>Not a connective: it composes nothing, and what it says is what {@link #body} says. What it
     * marks is where the environment changes, so that a reading meets the leaves under it holding
     * what their names mean rather than the names alone.
     *
     * @param binding the binding as the tree holds it, which the fold hands to {@link ClauseScope}
     *                — the shape says a binding stands here, and what it means is settled elsewhere
     */
    record Scoped(List<Core> spelled, boolean positive, Occurrence at, Core.LetIn binding,
                  ClauseExpr body) implements ClauseExpr {

        public Scoped {
            spelled = named(spelled);
        }
    }

    private static List<Core> named(List<Core> spelled) {
        if (spelled == null || spelled.isEmpty()) {
            throw new IllegalArgumentException("a shape is what some part of the tree was written as");
        }
        return Collections.unmodifiableList(new ArrayList<>(spelled));
    }

    /**
     * The shape of {@code clause}, stated where {@code positive} and denied where it is not.
     *
     * <p>The one mapping from a tree to a shape. A part this does not recognise as a connective is
     * a leaf, whatever it is — what a leaf means is the evaluator's, and which parts are
     * connectives is the language's.
     */
    static ClauseExpr of(Core clause, boolean positive) {
        return under(clause, positive, new int[1]);
    }

    /**
     * One shape and the occurrences under it, taking the next number for itself before its parts
     * take theirs.
     *
     * <p>The outside in, and the left of a connective before its right, which is the order a
     * clause is written in. What decides the order is the structure alone: which occurrences there
     * are is settled by the tree and by nothing about how it stands, so the same clause read as
     * stated and read as denied hands out the same numbers.
     */
    private static ClauseExpr under(Core clause, boolean positive, int[] counted) {
        return of(clause, positive, List.of(), new Occurrence(counted[0]++), counted);
    }

    private static ClauseExpr of(Core clause, boolean positive, List<Core> above, Occurrence at,
                                 int[] counted) {
        List<Core> spelled = new ArrayList<>(above);
        spelled.add(clause);
        // The scope first, so that what is read under it is read in the environment its names mean
        // something in. Everything below — a denial, a connective, a leaf — is then the same rule
        // written out, and a helper whose body denies or joins is this one rule and not another.
        if (clause instanceof Core.LetIn let) {
            return new Scoped(spelled, positive, at, let, under(let.body(), positive, counted));
        }
        // A restatement is this shape said another way, so it keeps this occurrence rather than
        // taking one of its own: a reader holding either spelling is holding one thing.
        ClauseExpr restated = restating(clause, positive, spelled, at, counted);
        if (restated != null) {
            return restated;
        }
        if (clause instanceof Core.Binary bin) {
            // Stated, a conjunction gives both sides; denied, it gives the choice between their
            // denials. And the same the other way round, which is the whole of what a denial does
            // to a connective, and is why the denial is applied to what the connective composes.
            ConditionJoin joined = ConditionJoin.of(bin.op()).map(one -> one.under(positive))
                    .orElse(null);
            if (joined != null) {
                return new Joined(spelled, positive, at, joined,
                        under(bin.left(), positive, counted),
                        under(bin.right(), positive, counted));
            }
        }
        return new Leaf(spelled, positive, at);
    }

    /**
     * The shape of what {@code clause} is written in terms of, or null where it is written in terms
     * of nothing.
     *
     * <p>A restatement moves the polarity and nothing else, so what comes back is the shape of what
     * is written under it, spelled as both nodes. Held here because it is the same question the
     * connectives are — which part of the tree this clause is, and how it stands — and answering it
     * anywhere else is a second reading of the shape. Private, so that there is nowhere else for
     * one to be.
     *
     * <p>Read in the analysis representation and in no other. {@code Bool.not} is an ordinary
     * helper, which that representation keeps as a call; the settled representation an imported
     * clause is read in has expanded it into a body, and a rule about the operation has nothing to
     * be about there. Reading the expansion too would be this deciding what a clause means from the
     * shape a lowering happened to leave, for one helper out of every one the settling expands —
     * the fragment an imported clause falls outside of is the whole of them (spec
     * §invariant-discharge-representation).
     *
     * <p>So: the call, the {@code if} an author wrote themselves, and a comparison against a
     * written {@code true} or {@code false} — {@code p == false} and {@code p /= true} deny what
     * {@code p} states, and the other two state it.
     *
     * <p>The equivalence class and not the spellings. What a reading is given is a part and a
     * polarity, so {@code not (p == false)} and {@code p} arrive as the same pair — and a reader
     * that learned one spelling at a time would answer for the ones somebody had got to.
     */
    private static ClauseExpr restating(Core clause, boolean positive, List<Core> spelled,
                                        Occurrence at, int[] counted) {
        if (clause instanceof Core.PreservedCall call && call.operation().equals(DischargeRules.NOT)
                && call.args().size() == 1) {
            return of(call.args().get(0), !positive, spelled, at, counted);
        }
        if (clause instanceof Core.If iff
                && iff.then() instanceof Core.Bool t && !t.value()
                && iff.els() instanceof Core.Bool f && f.value()) {
            return of(iff.cond(), !positive, spelled, at, counted);
        }
        return againstATruthValue(clause, positive, spelled, at, counted);
    }

    /**
     * The same of a comparison one side of which is a written {@code true} or {@code false}.
     *
     * <p>Whether it denies is whether the two disagree: an equality against {@code true} and a
     * disequality against {@code false} state what the other side states, and the other pair deny
     * it. Read off the operator and the literal rather than written out as four cases, so a fifth
     * way to write the same thing arrives here as one of the two answers and not as a case nobody
     * added.
     */
    private static ClauseExpr againstATruthValue(Core clause, boolean positive,
                                                 List<Core> spelled, Occurrence at, int[] counted) {
        if (!(clause instanceof Core.Binary bin)
                || (bin.op() != BinOp.EQ && bin.op() != BinOp.NE)) {
            return null;
        }
        boolean holds = bin.op() == BinOp.EQ;
        if (bin.right() instanceof Core.Bool truth) {
            return of(bin.left(), (truth.value() != holds) != positive, spelled, at, counted);
        }
        return bin.left() instanceof Core.Bool truth
                ? of(bin.right(), (truth.value() != holds) != positive, spelled, at, counted) : null;
    }
}
