package souther.compiler.check;

import souther.compiler.core.Core;
import souther.compiler.numeric.Granularity;
import souther.compiler.numeric.LinearForm;
import souther.compiler.numeric.NumericDomain;
import souther.compiler.numeric.NumericDomain.Bounds;
import souther.compiler.types.Type;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What holds of everything a reduction's step is handed besides its accumulator, whatever the
 * container holds and however far the walk has got.
 *
 * <p>Plural, and rooted. A step is handed the accumulator and everything else the operation applies
 * it to, and that is not one value: {@code Map.fold} hands its step a key and a value, and the two
 * are bounded by different declarations. So this is facts about places, each rooted at the parameter
 * it arrives on, rather than a range for "the element" — and every parameter but the accumulator is
 * read, so a key is read the day a step is written over one.
 *
 * <p>What holds of an element is not decided here either. {@link UniversalElementFacts} answers that
 * of the container the walk is over, by the path under an element each bound is about, and this
 * instantiates those paths at the place the element arrives — so a fold over a list and an
 * accumulation over the same list are bounded by one reading, and a fold reads what a
 * {@code List.map} upstream of it kept without having met the closure that built it. What a
 * parameter that is not an element guarantees is the same reading asked of that parameter's own
 * type: a key a {@code Map.fold} hands its step is bounded by its declaration and by nothing about
 * what the container holds.
 *
 * <p>Neither is what a value of a type guarantees. {@link InvariantChecker#seedFields} is what
 * decides it, and both readings above project that one answer — the same projection
 * {@link FieldDomains} makes of the same reading. Decided here instead, only a numeric newtype's own
 * rules would have been found: a record's own invariant bounds its fields, and
 * {@code data Line = { amount: Int } invariant amount >= 0} says nothing about any type
 * {@code DeclaredBounds} would have been asked about. That fact is one the walk into a combinator's
 * closure already has ({@link InvariantChecker#walkCall} enters the element and seeds it), so
 * deciding it a second way here would have been two answers to one question, differing by which of
 * them a reader happened to ask.
 *
 * <p>Bounds and not relations. Two places under one parameter may stand in a relation the
 * declaration states — {@code end >= start} — and the projection drops it, as {@link FieldDomains}'
 * does. That is a narrowing of what can be proved and is written down as one: a reduction whose step
 * needs such a relation gets no bound rather than a wrong one.
 *
 * <p>What is here holds of <em>every</em> value the step is handed, so it may be assumed of the one
 * value the step is read against. Nothing that holds of some elements belongs here: a guard about
 * one element of a list says nothing about the element the walk is at, and putting it here would
 * make the induction prove something no walk establishes.
 */
record StepInputFacts(Map<FactSubject, Bounds> at, Map<FactSubject, Granularity> kinds) {

    StepInputFacts {
        at = Map.copyOf(at);
        kinds = Map.copyOf(kinds);
    }

    static StepInputFacts none() {
        return new StepInputFacts(Map.of(), Map.of());
    }

    /**
     * What holds of everything {@code r}'s step is handed besides its accumulator, where each of
     * those parameters is entered in {@code inside} as the place it is.
     *
     * <p>The element is asked of the container and every other parameter of its own type. Which
     * parameter is which is what the reduction already says, and reading the element's bounds off
     * its type here as well would be the second answer this class exists not to have: what the
     * container's elements are is a question about the container, and the type of what it holds is
     * one of the things {@link UniversalElementFacts} reads to answer it.
     */
    static StepInputFacts of(Reductions.Reducing r, Denotations inside, Terms terms,
                             Set<FactSubject> namedByTheStep) {
        Gathering gathering = new Gathering(terms, namedByTheStep);
        List<Core.Binder> params = r.step().params();
        UniversalElementFacts elements =
                UniversalElementFacts.of(r.container(), inside, terms);
        for (int i = 0; i < params.size(); i++) {
            Core.Binder param = params.get(i);
            if (param == r.accumulator()) {
                continue;
            }
            if (param == r.element()) {
                elements.at(inside.subject(param.binding()), terms).forEach(gathering::holds);
                continue;
            }
            guaranteed(param, handedAt(r, i), inside, terms, gathering);
        }
        return gathering.gathered();
    }

    /**
     * The same for an accumulation, whose step is no block and whose element arrives at a place of
     * the walk rather than at a parameter something was written with.
     *
     * <p>What holds of the element is the same question and the same answer — {@code elements} was
     * read of the container either way — so the two consumers differ in where they instantiate it
     * and in nothing else. There is no other parameter to read: an accumulation applies one step to
     * the accumulator and an element, so a key or an index a reduction might be handed has nowhere
     * to arrive.
     */
    static StepInputFacts ofTheElement(UniversalElementFacts elements, FactSubject element,
                                       Terms terms, Set<FactSubject> namedByTheStep) {
        Gathering gathering = new Gathering(terms, namedByTheStep);
        elements.at(element, terms).forEach(gathering::holds);
        return gathering.gathered();
    }

    /**
     * What the step's parameter at {@code i} is handed, as a type.
     *
     * <p>The same two answers {@link InvariantChecker#walkCall} enters a closure's parameters at:
     * the element arrives at whatever the container holds, and every other parameter at what the
     * closure was typed with. Read the same way here so that a reduction proves against the types
     * the walk into that closure already reads it against.
     */
    private static Type handedAt(Reductions.Reducing r, int i) {
        if (r.step().params().get(i) == r.element()) {
            return Terms.elementType(r.container().type());
        }
        return r.step().type() instanceof Type.FnOf fn && i < fn.params().size()
                ? fn.params().get(i) : null;
    }

    /**
     * What a value of {@code param}'s type guarantees, projected onto the places under the parameter.
     *
     * <p>The reading answers by path — {@code ""} for the value itself, {@code "amount"} for a
     * field, {@code "a.b"} for a field of one — and the walk names those same places under whatever
     * subject the parameter was entered as. So the projection is the path read off one and put back
     * on the other, and nothing here reads a declaration.
     */
    private static void guaranteed(Core.Binder param, Type handed, Denotations inside, Terms terms,
                                   Gathering gathering) {
        FactSubject root = inside.subject(param.binding());
        if (root == null) {
            return;
        }
        ValueGuarantees.of(handed, terms.ruleReading()).forEach(
                (path, bounds) -> gathering.holds(terms.under(root, path), bounds));
    }

    /** {@code domain} with all of this taken as holding. Handed a domain rather than answering with
     * one, because which domain these are assumed into is the caller's question: they hold of every
     * element and so hold under any reading, and the reading is what decides what else is true
     * beside them. */
    NumericDomain<FactSubject> taking(NumericDomain<FactSubject> domain) {
        NumericDomain<FactSubject> out = domain;
        for (Map.Entry<FactSubject, Bounds> one : at.entrySet()) {
            out = out.assuming(one.getKey(), one.getValue(), kinds);
        }
        return out;
    }

    /**
     * A builder that keeps the two tables in step, and that keeps only the places the step names.
     *
     * <p>Both for one reason. A bound recorded without how its atom's values are spaced is one the
     * domain refuses to take, and how they are spaced is what the naming recorded when the step was
     * read — so a place the step does not name has no answer there, and is also a place no bound
     * could be read at. The two questions have one answer and it is asked once.
     */
    static final class Gathering {

        private final Map<FactSubject, Bounds> at = new LinkedHashMap<>();
        private final Map<FactSubject, Granularity> kinds = new LinkedHashMap<>();
        private final Terms terms;
        private final Set<FactSubject> namedByTheStep;

        Gathering(Terms terms, Set<FactSubject> namedByTheStep) {
            this.terms = terms;
            this.namedByTheStep = namedByTheStep;
        }

        /**
         * Records that {@code atom} lies between {@code bounds}, where that says anything and the
         * step names it.
         *
         * <p>Two rules reaching one atom both hold of it, so the tighter end of each side is kept.
         * One place can be reached by more than one source — a type that guarantees a range and a
         * container written out holding narrower values — and both are true of it.
         */
        void holds(FactSubject atom, Bounds bounds) {
            if (atom == null || bounds == null || bounds.saysNothing()
                    || !namedByTheStep.contains(atom)) {
                return;
            }
            Bounds had = at.get(atom);
            at.put(atom, had == null ? bounds : had.meet(bounds));
            kinds.putAll(terms.kindsOf(LinearForm.atom(atom)));
        }

        StepInputFacts gathered() {
            return at.isEmpty() ? none() : new StepInputFacts(at, kinds);
        }
    }

}
