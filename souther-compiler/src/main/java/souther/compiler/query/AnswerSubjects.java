package souther.compiler.query;

import souther.compiler.partition.AnswerDemand;
import souther.compiler.partition.MeasuredInput;
import souther.compiler.types.TypeSymbol;

import java.util.List;
import java.util.Map;

/**
 * What a value for one dependency's answer is composed over.
 *
 * <p>One subject where the answer is a type a position stands at, and one per case where it is a
 * union of them. A behavior may answer with an anonymous union — {@code Shipped | NotWritten} —
 * which no parameter names and no position holds, so there is no one subject to compose over; what
 * there is, is a value of one of its cases, and each of those is a type a position stands at.
 *
 * <p>Which case is not this type's to choose. A way that reads the answer as one of them says which
 * ({@link AnswerDemand.ACase}), and a way that says nothing about it takes the first — so the choice
 * is made where the demands are read and never here.
 *
 * @param whole  the subject for the answer as it stands, or null where it is a union
 * @param cases  the cases of a union, in the order they compare in, and empty where the answer is
 *               not one. Ordered here and taken in order below, so which case a way that says
 *               nothing gets is a function of the model and not of what a set happened to hold or
 *               of which readings this run managed to make first
 * @param byCase the subject for each case a value can be composed over, which is not every case:
 *               one nothing could be read for has no entry, and is not a case this quietly moves
 *               past
 */
record AnswerSubjects(MeasuredInput whole, List<TypeSymbol> cases,
                      Map<TypeSymbol, MeasuredInput> byCase) {

    AnswerSubjects {
        cases = List.copyOf(cases);
        byCase = Map.copyOf(byCase);
    }

    /**
     * The subject to compose against {@code demands}, and the demands left for it to meet, or null
     * where nothing here composes one.
     *
     * <p>Choosing the case of a union answers the demand that named it, so that demand does not
     * travel on: passed to a composer working over the case's own type, it would ask for a
     * narrowing of a position the case does not have.
     */
    Chosen against(List<AnswerDemand> demands) {
        if (whole != null) {
            return new Chosen(whole, demands);
        }
        for (AnswerDemand each : demands) {
            if (each instanceof AnswerDemand.ACase(var _, var _, var steps, var to)
                    && steps.isEmpty()
                    && to instanceof souther.compiler.inputs.Refinement.SumCase sum) {
                MeasuredInput standing = byCase.get(sum.leaf());
                return standing == null ? null
                        : new Chosen(standing, demands.stream()
                                .filter(left -> left != each).toList());
            }
        }
        // Nothing says which case, so the answer is a value of the first of them — the first as
        // they compare, and not the first this run managed to read. Taken the other way, a run
        // that could read one case and a run that could read two would answer differently about
        // one model, and a block read against the last one would differ for no reason an author
        // can see.
        //
        // A way that asks something else of a union answer — its truth, or a comparison over it —
        // is one nothing here composes for: the demand stays in hand, and no value meets it.
        if (cases.isEmpty()) {
            return null;
        }
        MeasuredInput first = byCase.get(cases.getFirst());
        return first == null ? null : new Chosen(first, demands);
    }

    /** A subject, and what is left for a value composed over it to meet. */
    record Chosen(MeasuredInput standing, List<AnswerDemand> demands) {}
}
