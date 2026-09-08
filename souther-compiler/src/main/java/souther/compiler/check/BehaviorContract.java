package souther.compiler.check;

import souther.compiler.ast.Hir;
import souther.compiler.diag.Region;
import souther.compiler.diag.SourcePos;
import souther.compiler.core.Contract;
import souther.compiler.types.BindingId;
import souther.compiler.types.BindingOwner;
import souther.compiler.types.Type;
import souther.compiler.types.TypeSymbol;
import souther.compiler.types.ValueName;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * What a behavior declares about the relation between what it is given and what it answers, read
 * once and specialized once.
 *
 * <p>Five readers want this: the check that a clause is well formed, the emitter that turns it into
 * the method a violation is found by, the classification that says how much of it a caller can
 * assume, the editor that shows that classification, and the analysis that assumes it at a call.
 * Each of them wants the clause resolved, typed, and split into the cases the answer can be. Doing
 * that once is what this is for — a reader arriving here does not go back to {@link Hir} for what a
 * case means or for what the parameters are called.
 *
 * <p>Typed once is not one tree. The language has two readings of an expression — the one that runs,
 * with the operations it is made of inlined, and the one the analysis reads, with the operations it
 * knows the meaning of left standing ({@link Terms.Of}) — and a rule is read as either. What is done
 * once is the resolving, the typing and the specialization; which tree a reader gets is that
 * reader's own question and is answered from the same rule.
 */
public record BehaviorContract(ValueName.Behavior behavior, List<Contract.Param> params,
                        Type output, List<Clause> clauses) {

    public BehaviorContract {
        params = List.copyOf(params);
        clauses = List.copyOf(clauses);
    }

    /** One `ensures`, with the rules its arms state. A violation is reported by the clause, so this
     *  is what carries the name a reader is given. */
    public record Clause(Optional<String> name, List<Rule> rules, SourcePos pos, Region region) {

        public Clause {
            rules = List.copyOf(rules);
        }
    }

    /**
     * One statement: what has to be so of the answer for it to apply, and what is then so.
     *
     * <p>A rule is already specialized — there is one per case an arm names, because {@code value}
     * is read as what that case holds and the cases hold different things. So a reader does not
     * specialize again, and the identity of what it is reading is the rule's own.
     *
     * <p>What a {@link Contract.Guard.Case}'s selector is named by is the type the case is declared as, never
     * the arm's spelling: the selectors come from the output's own declarations, and the arm is
     * matched against them by the type its name resolved to. A reader carrying that name out —
     * into a failure a run leaves behind, into a report — is carrying the case and not the text.
     *
     * <p>It is also the case the rule is <em>about</em>, which is not the case an answer turns out
     * to be. An arm may name a case that has cases of its own, so a rule written for {@code Errors}
     * holds of an answer that is a {@code NotFound}, and a reader that wants the second asks the
     * answer.
     */
    public record Rule(Contract.Guard guard, BindingId value, Hir.Expr statement, RuleId id,
                       SourcePos pos) {

        /** What {@code value} is read as here: what the case holds, or the whole answer where the
         *  rule applies to every answer. */
        public Type valueType(Type output) {
            return guard instanceof Contract.Guard.Case c ? c.selected().bound() : output;
        }

        /**
         * Whether the statement reads the answer.
         *
         * <p>What a rule can be decided from. Where the answer is known only as the case it is —
         * an {@code example} row writing a bare case name has written that and no value — a rule
         * reading only the inputs is decided, and one reading {@code value} is not: there is
         * nothing to read it as, and deciding it against a value nobody wrote would be answering
         * about something the row did not say.
         *
         * <p>A question about the rule and not a search. {@code value} is a binding and the rule
         * carries which one, so what is walked is this statement for that binding.
         */
        public boolean readsAnswer() {
            return reads(statement, value);
        }

        private static boolean reads(Hir.Expr expr, BindingId binding) {
            if (expr instanceof Hir.Var.Denoting var
                    && var.denotes() instanceof ValueName.Local local
                    && local.id().equals(binding)) {
                return true;
            }
            boolean[] found = {false};
            Hir.forEachChild(expr, child -> found[0] |= reads(child, binding));
            return found[0];
        }
    }

    /**
     * Which rule this is: where in the declaration it is written, and which case it is about.
     *
     * <p>Coordinates in the declaration and not steps of a walk. Which clause, which arm of it, and
     * which case that arm names is what the author wrote; nothing here comes from the order some
     * reader happened to visit things in, so two readings of one declaration — the tree that runs and
     * the tree the analysis reads — answer with the same ids without having to be matched up
     * afterwards.
     *
     * <p>{@code arm} is needed and {@code (clause, selector)} would not do: two arms of one clause may
     * name the same case, and {@code Found -> p | Found -> q} is two rules. It moves when arms are
     * added, removed or reordered, which is the declaration changing shape — not something else in the
     * module moving.
     *
     * <p>It counts the arms of the whole behavior and not of its clause, so the arms of the second
     * clause carry on from where the first left off. That is what the binding {@code value} is is
     * numbered by, and one number for both is one thing to keep true instead of two.
     */
    public record RuleId(ValueName.Behavior behavior, int clause, int arm, TypeSymbol selector) {}

    /** Every rule, in the order the clauses and their arms are written. */
    public List<Rule> rules() {
        List<Rule> out = new ArrayList<>();
        for (Clause clause : clauses) {
            out.addAll(clause.rules());
        }
        return out;
    }

    /** The clause a rule was written under, which is what a violation of it is reported by. */
    public Clause clauseOf(Rule rule) {
        return clauses.get(rule.id().clause());
    }

    /**
     * Which rule of the model one of these is, as everything that files a question about it says.
     *
     * <p>Here because this is where both halves of that name stand. The words are the author's for
     * the clause where they wrote a name, and the case the arm is about where they did not — so the
     * choice needs the rule and the clause it was written under, and only what holds the clauses can
     * make it without looking one up. Made by a reader instead, the same rule was named by whichever
     * of the two the reader happened to have, and the report and the reading of lines each spelled
     * it for themselves.
     */
    public RuleRef.Ensures refOf(Rule rule) {
        Optional<String> named = clauseOf(rule).name();
        if (named.isPresent()) {
            return new RuleRef.Ensures(rule.id(), named.get());
        }
        // A clause stating one rule over every answer names no case either, and the behavior's own
        // name is then the whole of what there is to call it — which is the empty word here, and is
        // said where a rule is rendered rather than by leaving the name out.
        return new RuleRef.Ensures(rule.id(),
                rule.id().selector() == null ? "" : rule.id().selector().name());
    }

    /** The names a rule reads its parameters and its answer under. */
    public static BindingOwner ownerOf(ValueName.Behavior behavior) {
        return new BindingOwner.OfSignature(behavior);
    }
}
