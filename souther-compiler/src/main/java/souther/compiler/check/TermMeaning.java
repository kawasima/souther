package souther.compiler.check;

import souther.compiler.core.Core;
import souther.compiler.types.BindingId;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * A term read for what it says, with where it stands not among the questions it answers.
 *
 * <p>A term says what it says wherever in a file it stands, but the tree carries where: the position
 * each node was written at, the ordinal the module numbered its constructs from one end with, and
 * the same of the names inside. Both move when anything above the declaration is edited, so a blank
 * line makes every term below it a different tree. A reader that depends on what a term says would
 * be recomputed by an edit the term cannot see.
 *
 * <p>So this is what such a reader depends on. It holds the term the declaration was read into —
 * whose places are the ones the author wrote, and are true — and answers {@code equals} without
 * reading them. Nothing here gives the term back. A value that could be unwrapped would be a value
 * two of which compare equal and can then be told apart, which is what {@link
 * souther.compiler.query.Db} keeps answers from being: what {@code equals} says is what stops the
 * work downstream, so an answer says what it means and not where it came from.
 *
 * <p>Held rather than rewritten. A term with its places taken out is a term that says it was written
 * nowhere and, where it is a fork, that it stands in the body as the author wrote it — answers
 * about a term that nothing asked and nothing can check. What is wanted is not a term missing its
 * places but a reading that does not have them to give.
 *
 * <p>The two operations a caller does with what a declaration states are here, because they are the
 * two that may cross this. Substituting the call's arguments in answers one of these again, so a
 * reading stays a reading however far it is carried; reading it as an assumption goes to
 * {@link Predicates}, which is where every other reading of a term goes.
 */
public final class TermMeaning {

    /**
     * The term this is a reading of, with the places its author wrote still on it.
     *
     * <p>What the identity is computed from rather than something held beside one. Two of these are
     * equal where the projections of their terms are ({@link #projectionOf}), so the term is the
     * state and the projection is how the state is compared — and nothing takes the term back out,
     * the two operations below answering a reading and what the predicates made of one.
     */
    private final Core term;

    /**
     * What the projection of {@code term} hashes to, worked out once.
     *
     * <p>Compared first as a way of saying no, so two readings that say different things are usually
     * told apart without walking either. It is of the same projection {@code equals} compares, so
     * the two cannot come to read different components of a term — which is a pair that calls two
     * terms equal and hashes them apart.
     */
    private final int hash;

    private TermMeaning(Core term) {
        if (term == null) {
            throw new IllegalArgumentException("a reading is a reading of a term");
        }
        this.term = term;
        this.hash = Arrays.hashCode(projectionOf(term));
    }

    /** {@code term} read for what it says. */
    public static TermMeaning of(Core term) {
        return new TermMeaning(term);
    }

    /**
     * The same reading with {@code given} put where the names it reads stand — what a caller does
     * with a rule written in the declaration's names.
     *
     * <p>Answers one of these and not a term. The values put in are the call's own and carry the
     * places they were written at, so a term handed back would be a term this had let out; and the
     * reading of a rule at a call is read for what it says, as the rule was.
     */
    TermMeaning substituted(Map<BindingId, Core> given) {
        return new TermMeaning(Clauses.substituted(term, given));
    }

    /**
     * What this states where it is taken as holding, read by {@code predicates} over the names in
     * {@code at}.
     *
     * <p>The entry is here rather than an overload beside {@link Predicates#assumed}, which would
     * need the term to be got out of this to be passed in. The reading itself is still the
     * predicates' — this hands the term to the reader that owns the rules about it and answers what
     * that reader said.
     */
    Predicates.Owed assumedBy(Predicates predicates, Denotations at, boolean decidesFalse) {
        return predicates.assumed(term, at, decidesFalse);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof TermMeaning it
                && hash == it.hash
                && Arrays.equals(projectionOf(term), projectionOf(it.term));
    }

    @Override
    public int hashCode() {
        return hash;
    }

    /**
     * What this read, in the order it read it.
     *
     * <p>The projection and not a count of it. Two of these are told apart by what they say, so a
     * report that gave only how much was said would leave whoever is reading it unable to see which
     * of the two they have — which is what a failed comparison of two readings is asking.
     */
    @Override
    public String toString() {
        Object[] said = projectionOf(term);
        StringBuilder out = new StringBuilder("TermMeaning(");
        for (int i = 0; i < said.length; i++) {
            if (i > 0) {
                out.append(' ');
            }
            out.append(said[i] instanceof Class<?> kind ? kind.getSimpleName()
                    : String.valueOf(said[i]));
        }
        return out.append(')').toString();
    }

    /**
     * {@code term} as this compares it: every node's kind and what it says, and none of what says
     * where it stands.
     *
     * <p>Worked out where it is wanted rather than held. What a reading is compared by is what its
     * term says, so holding the answer beside the term would be a second statement of it — one that
     * agrees with the term until a pass hands this a term it did not build.
     */
    private static Object[] projectionOf(Core term) {
        List<Object> said = new ArrayList<>();
        project(term, said);
        return said.toArray();
    }

    /**
     * Every node of {@code e}, as what kind it is and what it says.
     *
     * <p>A case per node kind, over a sealed type, so a node kind added later arrives here as a
     * compile error rather than as one whose place is quietly compared. What a case leaves out is
     * what says where the node stands: the position, the occurrence a construct is of the model, the
     * copy of the body a fork stands in, and the reference and application a call kept was written
     * as.
     *
     * <p>The kind goes in ahead of what it says, so two nodes of different kinds that happen to say
     * the same things are told apart, and the length of a list goes in ahead of its elements, so
     * that where one list ends and the next begins is said rather than inferred.
     */
    private static void project(Core e, List<Object> out) {
        switch (e) {
            case Core.Int x -> {
                out.add(Core.Int.class);
                out.add(x.value());
                out.add(x.type());
            }
            case Core.Decimal x -> {
                out.add(Core.Decimal.class);
                out.add(x.value());
                out.add(x.type());
            }
            case Core.Str x -> {
                out.add(Core.Str.class);
                out.add(x.value());
                out.add(x.type());
            }
            case Core.Bool x -> {
                out.add(Core.Bool.class);
                out.add(x.value());
                out.add(x.type());
            }
            // The kind is the type, so the type is not put in beside it.
            case Core.Temporal x -> {
                out.add(Core.Temporal.class);
                out.add(x.kind());
                out.add(x.text());
            }
            case Core.Read x -> {
                out.add(Core.Read.class);
                out.add(x.name());
                out.add(x.binding());
                out.add(x.type());
            }
            case Core.UnitValue x -> {
                out.add(Core.UnitValue.class);
                out.add(x.data());
                out.add(x.type());
            }
            case Core.OptionNone x -> {
                out.add(Core.OptionNone.class);
                out.add(x.type());
            }
            case Core.Unreachable x -> {
                out.add(Core.Unreachable.class);
                out.add(x.reason());
                out.add(x.type());
            }
            case Core.Neg x -> {
                out.add(Core.Neg.class);
                out.add(x.type());
                project(x.operand(), out);
            }
            case Core.FieldAccess x -> {
                out.add(Core.FieldAccess.class);
                out.add(x.field());
                out.add(x.type());
                project(x.target(), out);
            }
            case Core.Binary x -> {
                out.add(Core.Binary.class);
                out.add(x.op());
                out.add(x.type());
                project(x.left(), out);
                project(x.right(), out);
            }
            case Core.Call x -> {
                out.add(Core.Call.class);
                out.add(x.fn());
                out.add(x.type());
                projectAll(x.args(), out);
            }
            case Core.PreservedCall x -> {
                out.add(Core.PreservedCall.class);
                out.add(x.declared());
                out.add(x.type());
                projectAll(x.args(), out);
            }
            case Core.Apply x -> {
                out.add(Core.Apply.class);
                out.add(x.type());
                project(x.fn(), out);
                projectAll(x.args(), out);
            }
            case Core.If x -> {
                out.add(Core.If.class);
                out.add(x.type());
                project(x.cond(), out);
                project(x.then(), out);
                project(x.els(), out);
            }
            case Core.IfConstructed x -> {
                out.add(Core.IfConstructed.class);
                out.add(x.binder());
                out.add(x.type());
                project(x.construct(), out);
                project(x.then(), out);
                out.add(x.els().size());
                for (Core.ElseArm arm : x.els()) {
                    out.add(arm.clause());
                    project(arm.body(), out);
                }
            }
            case Core.LetIn x -> {
                out.add(Core.LetIn.class);
                out.add(x.binder());
                out.add(x.type());
                project(x.value(), out);
                project(x.body(), out);
            }
            case Core.Block x -> {
                out.add(Core.Block.class);
                out.add(x.params());
                out.add(x.type());
                project(x.body(), out);
            }
            case Core.ListLit x -> {
                out.add(Core.ListLit.class);
                out.add(x.type());
                projectAll(x.elements(), out);
            }
            case Core.OptionSome x -> {
                out.add(Core.OptionSome.class);
                out.add(x.type());
                project(x.value(), out);
            }
            case Core.Tuple x -> {
                out.add(Core.Tuple.class);
                out.add(x.type());
                projectAll(x.elements(), out);
            }
            case Core.TupleGet x -> {
                out.add(Core.TupleGet.class);
                out.add(x.index());
                out.add(x.arity());
                out.add(x.type());
                project(x.tuple(), out);
            }
            case Core.Construct x -> {
                out.add(Core.Construct.class);
                out.add(x.typeName());
                out.add(x.type());
                out.add(x.values().size());
                for (Core.FieldValue value : x.values()) {
                    out.add(value.field());
                    project(value.value(), out);
                }
            }
            case Core.Match x -> {
                out.add(Core.Match.class);
                out.add(x.type());
                project(x.scrutinee(), out);
                out.add(x.cases().size());
                for (Core.Case arm : x.cases()) {
                    out.add(arm.pattern());
                    out.add(arm.binder());
                    project(arm.body(), out);
                }
            }
        }
    }

    private static void projectAll(List<Core> es, List<Object> out) {
        out.add(es.size());
        for (Core each : es) {
            project(each, out);
        }
    }
}
