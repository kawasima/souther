package souther.compiler.check;

import souther.compiler.core.Core;
import souther.compiler.types.BindingId;
import souther.compiler.types.TypeSymbol;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What each conjunct of a declaration's invariants states, statement by statement.
 *
 * <p>The one way from a conjunct an author wrote to what it says. A conjunct states as many
 * statements as a reading of its shape arrives at — a denial carried into a choice states one per
 * branch, and a helper whose body joins two rules states both — and each of them is recognised
 * where the clause's connectives were read once ({@link ClauseExpr}) rather than off the tree the
 * expansion left.
 *
 * <p><b>What crosses here is what a statement states.</b> A comparison arrives with its denial and
 * its orientation already spent, and an operation arrives as the operation it is. A consumer handed
 * the conjunct's tree instead reads the shape a second time, which is the reading that has no word
 * for a binding and no word for a denial — and an author who named a rule loses what an author who
 * repeated it keeps.
 *
 * <p><b>Every statement or none.</b> A conjunct answers with all of what it states, the ones this
 * made nothing of included ({@link InvariantStatement.Unread}), so a consumer acting on part of a
 * conjunct can see that it is a part. Answering with the recognised ones alone, a conjunct stating
 * two rules would be indistinguishable from one stating the rule that was recognised.
 */
public final class InvariantStatements {

    private final Clauses clauses;
    /** One answer per declaration, because a conjunct is asked about once per consumer and the
     *  clauses of a declaration are read to answer for any of them. */
    private final Map<TypeSymbol.AtModule, Map<PartId<RuleRef.Invariant>, List<InvariantStatement>>>
            read = new LinkedHashMap<>();

    private final Symbols symbols;

    private InvariantStatements(Clauses clauses, Symbols symbols) {
        this.clauses = clauses;
        this.symbols = symbols;
    }

    /**
     * The text {@code expression} stands for, or null where nothing works one out.
     *
     * <p>Asked here because it is a reading of a term and not a question about whatever the caller
     * is mapping onto: a rule whose text is composed of what a module's own {@code let} holds says
     * the same thing as one written out, and a caller that folded only the literals it could see
     * for itself would read the second and decline the first.
     *
     * <p>Over the term alone, which is what a statement from here is. The bindings a reading crossed
     * to reach it are already spent, so there is no environment left for this to be asked in.
     */
    public String textOf(Core expression) {
        return Terms.folded(expression, symbols, Denotations.none()) instanceof String text
                ? text : null;
    }

    /** The statements of the declarations {@code source} reads. */
    public static InvariantStatements of(RuleReadingSource source) {
        return new InvariantStatements(new Clauses(source), source.symbols());
    }

    /**
     * What {@code part} states, or null where the reading has no form for the clause it is of.
     *
     * <p>Null and not an empty list. A conjunct states at least one thing — the walk that numbers
     * the statements arrives at a part however little is made of it — so an empty answer could only
     * mean a clause that was never read, and a consumer reading it as "this conjunct constrains
     * nothing" would drop the rule.
     */
    public List<InvariantStatement> of(PartId<RuleRef.Invariant> part) {
        return statesOf(part.rule().clause().id().declaredOn()).get(part);
    }

    private Map<PartId<RuleRef.Invariant>, List<InvariantStatement>> statesOf(
            TypeSymbol.AtModule declaration) {
        return read.computeIfAbsent(declaration, named -> {
            Map<PartId<RuleRef.Invariant>, List<InvariantStatement>> out = new LinkedHashMap<>();
            for (ClauseMeaning clause : clauses.declared(named)) {
                Clauses.AsStated stated = clauses.stated(clause);
                if (stated == null) {
                    continue;
                }
                // The parts as subtrees of the one reading of the clause, read where every other
                // reader of a declaration's rules reads them. Read into a shape here, this would be
                // a second answer to what the clause's author wrote.
                for (Clauses.StatedPart part : clauses.partsOf(stated)) {
                    out.put(part.id(), statementsOf(part));
                }
            }
            return Map.copyOf(out);
        });
    }

    private static List<InvariantStatement> statementsOf(Clauses.StatedPart part) {
        List<InvariantStatement> out = new ArrayList<>();
        for (ConjunctStatements.Reached each : ConjunctStatements.of(part.of(), part.id())) {
            out.add(states(each));
        }
        return List.copyOf(out);
    }

    private static InvariantStatement states(ConjunctStatements.Reached reached) {
        // What the names under the bindings mean, which is what entering them comes to here. A
        // consumer handed the bindings instead reads a rule about the helper's parameter and has to
        // work out which of the declaration's values that parameter stands for — which is the
        // reading that has no word for a helper, arriving one reader further on.
        Map<BindingId, Core> given = new LinkedHashMap<>();
        for (ClauseExpr.Scoped each : reached.crossed()) {
            Core.LetIn binding = each.binding();
            given.put(binding.binder().binding(), Clauses.substituted(binding.value(), given));
        }
        // Of the part and its polarity together, so what comes back is the comparison the clause
        // states rather than the one its author spelled.
        StatedComparison compares = StatedComparison.of(reached.said());
        if (compares != null) {
            return new InvariantStatement.Compares(reached.statement(),
                    new StatedComparison(compares.claim(),
                            Clauses.substituted(compares.left(), given),
                            Clauses.substituted(compares.right(), given)));
        }
        // What the part states with the denials above it taken off, which is the operation itself
        // where one was kept standing. Stated only: what holds where an operation does not is not
        // that operation, and there is nothing here to name it by.
        if (reached.said().positive()
                && Clauses.substituted(reached.said().of(), given)
                        instanceof Core.PreservedCall call) {
            return new InvariantStatement.Applies(reached.statement(), call);
        }
        return new InvariantStatement.Unread(reached.statement());
    }
}
