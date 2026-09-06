package souther.compiler.check;

import souther.compiler.ast.Hir;
import souther.compiler.check.BehaviorContract.Rule;
import souther.compiler.check.BehaviorContract.RuleId;
import souther.compiler.core.Contract;
import souther.compiler.core.Core;
import souther.compiler.core.Contract.Guard;
import souther.compiler.core.Contract.Param;
import souther.compiler.diag.CompileException;
import souther.compiler.diag.Diagnostic;
import souther.compiler.diag.DiagnosticRenderer;
import souther.compiler.diag.msg.BehaviorMessage;
import souther.compiler.types.BindingId;
import souther.compiler.types.BindingOwner;
import souther.compiler.types.ResolvedCase;
import souther.compiler.types.Type;
import souther.compiler.types.TypeSymbol;
import souther.compiler.types.ValueName;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads what a behavior declares about its answer into a {@link BehaviorContract}, and holds it to
 * the rules a declaration is subject to.
 *
 * <p>The reading and the checking are one pass because they are one question. What a rule states is
 * what case it applies to and what has to be so there, and every rule this refuses is a rule it
 * could not read that from.
 */
public final class BehaviorChecker {

    private BehaviorChecker() {}

    /**
     * The contract {@code behavior} declares, or the empty one where it declares none.
     *
     * <p>Rule by rule and not stopping at the first. A behavior carrying two clauses that are each
     * wrong is an author with two things to fix, and stopping at the first turns one build into two.
     * What does stop is a signature this cannot be read against at all — a parameter spelled
     * {@code value} leaves every rule ambiguous about what it names, so reading the rules underneath
     * it would report one mistake as several.
     */
    public static CheckedEnsures contractOf(Hir.SpecBehavior behavior, String module, Sig sig,
                                       Symbols symbols, Map<String, Type> helpers) {
        Reading reading = read(behavior, module, sig, symbols);
        BehaviorContract contract = reading.contract();
        // The rules it did read, held to what a rule has to be. Two mistakes in one declaration are
        // two things for an author to fix, and this is the reading that reports them.
        List<Diagnostic> found = new ArrayList<>(reading.unread());
        // What each rule comes to, kept. Typing a rule is elaborating it, and the reading that
        // comes out is the one that runs — dropped here, it was made again by whatever had to run
        // it (issue #1080).
        List<Contract.Rule> checked = new ArrayList<>();
        for (Rule rule : contract.rules()) {
            collect(found, () -> checked.add(checkRule(behavior, contract, rule, helpers, symbols)));
        }
        if (found.size() == 1) {
            throw CompileException.of(found.get(0));
        }
        if (!found.isEmpty()) {
            throw CompileException.ofAll(found, DiagnosticRenderer.legacyBody(found.get(0)));
        }
        return new CheckedEnsures(contract, new Contract(contract.behavior(), contract.params(),
                contract.output(), checked));
    }

    /**
     * The same declaration read again, in whatever representation {@code behavior} is written in, and
     * held to nothing.
     *
     * <p>What the rules are — which case each applies to, what {@code value} is there, which rule is
     * which — is a property of the declaration and not of the representation, so this reads the same
     * rules under the same {@link RuleId}s as {@link #contractOf} does of the tree that runs. What is
     * not done again is the holding: a clause that is not well formed is one mistake, and the reading
     * that reports it is the one the author's build is made of.
     *
     * <p>All of the declaration or none of it. An arm that cannot be read contributes no rule, and a
     * contract missing one is a contract that says the author stated nothing about the cases that arm
     * named — which is the opposite of what they wrote, and is what a reader of it would be told. The
     * refusal is already being reported by the reading that holds the declaration to its rules, so
     * there is nothing to say here beyond declining to answer.
     *
     * @throws Unanswerable where there is no signature to read the rules against
     * @throws CompileException where any of the declaration could not be read, which the executable
     *     reading of it reports as well
     */
    public static BehaviorContract contractAsRead(Hir.SpecBehavior behavior, String module, Sig sig,
                                                  Symbols symbols) {
        return read(behavior, module, sig, symbols).whole();
    }

    /**
     * What reading a declaration came to: the rules it made, and what it could not read.
     *
     * <p>The two are one value because the contract alone does not say it is short of anything. A
     * declaration whose arm could not be read makes a contract that states nothing about the cases
     * that arm named, which is indistinguishable from a declaration that said nothing about them —
     * and a reader deriving what is left unstated would report the opposite of what the author wrote.
     * So what was lost arrives with what was kept, and a caller says which it is asking for.
     */
    private record Reading(BehaviorContract contract, List<Diagnostic> unread) {

        /**
         * The contract where the whole declaration was read.
         *
         * @throws CompileException where any of it was not, which is reported by the reading that
         *     holds the declaration to its rules rather than said twice
         */
        BehaviorContract whole() {
            if (!unread.isEmpty()) {
                throw CompileException.of(unread.get(0));
            }
            return contract;
        }
    }

    /** The declaration as rules, beside what could not be read of it. */
    private static Reading read(Hir.SpecBehavior behavior, String module, Sig sig,
                                Symbols symbols) {
        List<Diagnostic> found = new ArrayList<>();
        ValueName.Behavior name = new ValueName.Behavior(module, behavior.name());
        if (sig == null) {
            // The signature could not be made, which is reported where that failed. A rule is
            // read against the parameters and the answer, so there is nothing to read one against
            // — said again here it would be that one mistake seen from another angle.
            throw new Unanswerable(behavior.pos());
        }
        for (Hir.Param param : behavior.params()) {
            if (param.name().equals("value")) {
                throw CompileException.of(Diagnostic.at(param.pos())
                        .say(new BehaviorMessage.ABehaviorWithAClauseHasAParameterNamedValue(
                                behavior.name())).build());
            }
        }

        BindingOwner owner = BehaviorContract.ownerOf(name);
        List<Param> params = new ArrayList<>();
        for (int i = 0; i < behavior.params().size(); i++) {
            params.add(new Param(new BindingId(owner, i), behavior.params().get(i).name(),
                    sig.inputTypes().get(i)));
        }

        // Which cases the answer can be, and what `value` is in each, come from the same place a
        // `match` over that answer reads them. A clause naming a case a caller could not match is a
        // clause a caller could never assume, so the two admit the same names by construction.
        CaseSpace answer = CaseSpace.of(sig.outputType(), symbols);

        // Arm by arm, and an arm this cannot read leaves the rest readable. Reading and checking are
        // one pass — what a rule states is which case it applies to and what holds there, and every
        // refusal here is this not having been able to read that — so an arm that fails contributes
        // no rule and stops nothing else.
        List<BehaviorContract.Clause> clauses = new ArrayList<>();
        int armOrdinal = 0;
        for (int c = 0; c < behavior.ensures().size(); c++) {
            Hir.EnsuresClause written = behavior.ensures().get(c);
            List<Rule> rules = new ArrayList<>();
            for (Hir.EnsuresArm arm : written.arms()) {
                int ordinal = armOrdinal++;
                int clauseIndex = c;
                collect(found, () -> rules.addAll(
                        read(behavior, arm, answer, symbols, owner, params.size(), clauseIndex, ordinal)));
            }
            clauses.add(new BehaviorContract.Clause(written.name(), rules, written.pos(),
                    written.region()));
        }
        return new Reading(new BehaviorContract(name, params, sig.outputType(), clauses), found);
    }

    /**
     * The clauses as rules: one rule per case an arm names, or one rule over every answer where the
     * arm names none.
     *
     * <p>Specializing here rather than at each reader is what keeps {@code value}'s type from being
     * worked out again — a rule already knows which case it is about, and the cases hold different
     * things. What an arm may name is settled here too, since a rule cannot be read for a case the
     * answer does not have.
     */
    private static List<Rule> read(Hir.SpecBehavior behavior, Hir.EnsuresArm arm, CaseSpace answer,
                                   Symbols symbols, BindingOwner owner, int paramCount, int clause,
                                   int ordinal) {
        boolean hasCases = !(answer instanceof CaseSpace.Plain);
        ValueName.Behavior named = ((BindingOwner.OfSignature) owner).behavior();
        BindingId value = new BindingId(owner, paramCount + ordinal);

        // What form the arm may take is asked before what it names, so an arm written where none
        // may be is that mistake and not the one that follows from it.
        if (hasCases && arm.cases().isEmpty()) {
            throw CompileException.of(Diagnostic.at(arm.pos())
                    .say(new BehaviorMessage.AnEnsuresClauseOverASumNamesNoArm(
                            behavior.name())).build());
        }
        if (!hasCases && !arm.cases().isEmpty()) {
            throw CompileException.of(Diagnostic.at(arm.pos())
                    .say(new BehaviorMessage.AnEnsuresClauseOverASingleTypeNamesAnArm(
                            behavior.name())).build());
        }
        if (arm.cases().isEmpty()) {
            return List.of(new Rule(new Guard.Always(), value, arm.expr(),
                    new RuleId(named, clause, ordinal, null), arm.pos()));
        }

        List<Rule> rules = new ArrayList<>();
        Set<TypeSymbol> seen = new LinkedHashSet<>();
        for (Hir.Name armCase : arm.cases()) {
            if (armCase.answered() == null) {
                // Nothing declares it, which is reported where it is written. That this is not an
                // output case follows from it, and saying so would be the one mistake said twice —
                // the reading is abandoned instead, as a `match` arm's is.
                throw new Unanswerable(armCase.pos());
            }
            ResolvedCase selected = answer.selector(armCase.answered().type(), symbols);
            if (selected == null) {
                throw CompileException.of(Diagnostic.at(armCase.pos())
                        .say(new BehaviorMessage.AnEnsuresArmIsNotAnOutputCase(
                                armCase.written(), behavior.name())).build());
            }
            // A case named twice under one arrow is one rule, not two. What a clause states is a
            // conjunction — every rule whose guard holds applies — so `Found | Found -> p` states
            // `p` and `p`, which is `p`. Kept as two, they would be two rules under one
            // {@link RuleId}, and the readers that agree on a rule by its identity would each hold
            // a different number of them.
            //
            // Redundant and not ambiguous, so it is collapsed rather than refused. `Found -> p |
            // Found -> q` is another matter and is two rules: the arms differ, so the ids do.
            if (!seen.add(selected.name())) {
                continue;
            }
            rules.add(new Rule(new Guard.Case(selected), value, arm.expr(),
                    new RuleId(named, clause, ordinal, selected.name()), arm.pos()));
        }
        return rules;
    }

    /**
     * One rule: what its expression comes to, and that it states a relation.
     *
     * <p>Elaborated over the tree that runs, which is the written rule desugared the way a body is.
     * The emitter used to do that again on its way to the same Core; what comes back from here is
     * what runs, so there is one reading of the rule and this is where it is made.
     *
     * <p>Whether the rule reads the answer is asked of what was written and carried on what runs.
     * It is what the declaration says of the rule (spec §example-ensures), so it does not move when
     * the elaboration gets better at simplifying a term away.
     */
    private static Contract.Rule checkRule(Hir.SpecBehavior behavior, BehaviorContract contract,
                                           Rule rule, Map<String, Type> helpers, Symbols symbols) {
        Core condition = Elaborator.elaborate(Lower.desugarExpr(rule.statement()),
                scopeOf(contract, rule).reaching(helpers),
                CheckContext.executableEnsures(symbols));
        if (condition.type() != Type.BOOL) {
            throw CompileException.of(Diagnostic.at(rule.statement().pos())
                    .say(new BehaviorMessage.AnEnsuresExpressionIsNotBool(
                            behavior.name(), Type.show(condition.type()))).build());
        }
        requireBothSides(behavior, contract, rule);
        return new Contract.Rule(rule.guard(), rule.value(), condition, rule.readsAnswer(),
                contract.clauseOf(rule).name());
    }

    /**
     * What a rule may name: the behavior's parameters, and {@code value} as the case it is written
     * under holds it.
     *
     * <p>Everything it takes is the contract's. A rule is read here, where it is checked, and there
     * where it is classified, and the two would be two scopes to keep saying the same thing if
     * either built it from the declaration instead.
     */
    static Scope scopeOf(BehaviorContract contract, Rule rule) {
        Map<BindingId, Scope.Binding> bindings = new LinkedHashMap<>();
        for (Param param : contract.params()) {
            bindings.put(param.binding(), new Scope.Binding(param.name(), param.type()));
        }
        bindings.put(rule.value(), new Scope.Binding("value", rule.valueType(contract.output())));
        return Scope.of(bindings);
    }

    /**
     * A rule states a relation, so it refers to both sides of one.
     *
     * <p>It refers to the answer through its guard or through {@code value}. A guard is a reference:
     * {@code Missing -> id == tag} says that where the answer is `Missing`, the inputs stood in that
     * relation, which is a statement about the answer whether or not the expression reads it.
     * Asking for the word {@code value} instead would read the text for the relation rather than
     * the rule, and would refuse a rule that states one.
     *
     * <p>What a rule refers to is not what it is <em>about</em>. {@code value == value && id > 0}
     * refers to the answer and says nothing about it, and a guard over an answer with one case holds
     * of everything it answers. Telling those apart is a proof; what this holds is that a relation
     * is written with both of its sides in it.
     */
    private static void requireBothSides(Hir.SpecBehavior behavior, BehaviorContract contract,
                                         Rule rule) {
        Set<BindingId> read = new LinkedHashSet<>();
        collectBindings(rule.statement(), contract.behavior(), read);
        if (rule.guard() instanceof Guard.Always && !rule.readsAnswer()) {
            throw CompileException.of(Diagnostic.at(rule.pos())
                    .say(new BehaviorMessage.AnEnsuresClauseDoesNotNameTheAnswer(
                            behavior.name())).build());
        }
        boolean readsParameter = contract.params().stream()
                .anyMatch(param -> read.contains(param.binding()));
        if (!readsParameter) {
            throw CompileException.of(Diagnostic.at(rule.pos())
                    .say(new BehaviorMessage.AnEnsuresClauseDoesNotNameAParameter(
                            behavior.name())).build());
        }
    }

    /** Runs {@code rule} and keeps what it reported, so the rules after it are read too. */
    private static void collect(List<Diagnostic> found, Runnable rule) {
        try {
            rule.run();
        } catch (CompileException e) {
            found.addAll(e.diagnostics());
        }
    }

    /** Which of the behavior's own names an expression reads. */
    private static void collectBindings(Hir.Expr expr, ValueName.Behavior behavior,
                                        Set<BindingId> out) {
        if (expr instanceof Hir.Var.Denoting var
                && var.denotes() instanceof ValueName.Local local
                && local.id().owner() instanceof BindingOwner.OfSignature signature
                && signature.behavior().equals(behavior)) {
            out.add(local.id());
        }
        TypeChecker.forEachChild(expr, child -> collectBindings(child, behavior, out));
    }
}
