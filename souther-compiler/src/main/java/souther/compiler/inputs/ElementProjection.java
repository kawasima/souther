package souther.compiler.inputs;

import souther.compiler.check.DeclarationNewtypes;
import souther.compiler.check.Location;
import souther.compiler.core.Core;
import souther.compiler.types.BindingId;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Where in an element the value a walk answered stands: the steps from the element to it.
 *
 * <p>What is left of a closure once it has been read. A {@code map} over a list answers, for each
 * element, some value made from it; where that value is a place inside the element, this is the way
 * there and the expression is not kept. Held as the expression, every reader of a run would be
 * reading a body the inliner happened to leave, and what may be read off one would grow with
 * whatever the inliner does next.
 *
 * <p>Relative, so it can be joined to whichever element position the run turns out to be over. The
 * closure was written about one element and the run is over all of them, and the two meet by this
 * being the part that does not depend on which.
 *
 * <p>Steps of a path and no other kind. A closure answering something built rather than something
 * read has no projection and is not one of these — which is what keeps a rule about what a branch
 * chose from being read as a rule about a field.
 */
public record ElementProjection(List<String> steps) {

    public ElementProjection {
        steps = List.copyOf(steps);
    }

    /**
     * What a closure whose parameter is {@code element} answered, as the way from the element to it,
     * or null where the answer is not read out of the element.
     *
     * <p>Read here and not where a position of the input is worked out. That reading asks which
     * position of a behavior's input an expression names and has the roots, the containers and the
     * provenance of elements to answer with; this one asks where inside one value another value
     * stands, and a name, a {@code let} and a field are the whole of what it takes. Answered by the
     * same walk, the steps that reach a behavior's parameters would be reachable from here, and the
     * relative way inside an element would quietly become an absolute one.
     *
     * <p><b>Null wherever the answer is not read out of the element.</b> A branch chooses between
     * two of them and is neither; arithmetic over one is a value the element does not hold; a
     * construction is something new. None of those is a place a row writes, so a rule about what a
     * walk answered is not a rule about any position, and saying so is this reading's whole job on
     * that side.
     *
     * <p>Nothing here says the answer is one per element. That is a fact about the operation that
     * handed the closure its elements, proved where that operation stood; a caller wanting a run
     * needs both, and this is the half about the reading.
     *
     * @param held what each binding on the way holds, over the whole body the closure was left in
     */
    public static ElementProjection read(Core answer, BindingId element,
                                         Map<BindingId, Core> held,
                                         DeclarationNewtypes newtypes) {
        List<String> steps = new Reading(held, newtypes).from(answer, element);
        return steps == null ? null : new ElementProjection(steps);
    }

    /**
     * One reading of one closure's answer.
     *
     * <p>A value rather than a run of static calls, so that the bindings it is inside travel with
     * it. What stops it is that walk over the binding graph coming back to a binding it is already
     * answering ({@link BindingTrail}), which is the same law the reading of an input position
     * stops by — the one thing the two readings share.
     */
    private record Reading(Map<BindingId, Core> held, DeclarationNewtypes newtypes) {

        private List<String> from(Core e, BindingId element) {
            return steps(e, element, new BindingTrail());
        }

        private List<String> steps(Core e, BindingId element, BindingTrail trail) {
            return switch (e) {
                // What a `let` comes to is what its body comes to, and the name it bound is answered
                // where it is read. Ordinary binding semantics, and what a helper applied to the
                // element leaves behind once it is spliced in: `amountOf(line).value` is a field of
                // a binding holding the element, and reading only the field would stop at the
                // splice.
                case Core.LetIn let -> steps(let.body(), element, trail);
                case Core.Read read -> {
                    if (element.equals(read.binding())) {
                        yield List.of();
                    }
                    Core through = held.get(read.binding());
                    yield through == null ? null
                            : trail.through(read.binding(),
                                    () -> steps(through, element, trail));
                }
                case Core.FieldAccess fa -> {
                    List<String> base = steps(fa.target(), element, trail);
                    if (base == null) {
                        yield null;
                    }
                    if (!Location.isStep(fa.target().type(), fa.field(), newtypes)) {
                        yield base;
                    }
                    List<String> longer = new ArrayList<>(base);
                    longer.add(fa.field());
                    yield List.copyOf(longer);
                }
                case null, default -> null;
            };
        }
    }

    /** The position this reaches from {@code element}. */
    public TermPath from(TermPath element) {
        TermPath at = element;
        for (String step : steps) {
            at = at.then(step);
        }
        return at;
    }

    @Override
    public String toString() {
        return steps.isEmpty() ? "the element itself" : String.join(".", steps);
    }
}
