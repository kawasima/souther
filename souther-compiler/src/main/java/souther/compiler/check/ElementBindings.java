package souther.compiler.check;

import souther.compiler.semantics.BuiltFrom;
import souther.compiler.core.Core;
import souther.compiler.types.BindingId;
import souther.compiler.types.ValueName;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Which of a body's bindings hold an element of a container, and of which container.
 *
 * <p>A fact about the program and not about any reader of it. What a standard-library operation
 * hands its closure is stated by the library's own signature ({@link Combinators}); this is that
 * statement made about one body's bindings, at the one point in the pipeline where it can still be
 * read.
 *
 * <p><b>Taken before the relation is erased, and not recovered afterwards.</b> The tree the backend
 * emits from has no combinator left in it: a fold that only grows a collection is rewritten into a
 * walk over a builder, and two such walks in a row are joined into one, so what handed a closure an
 * element is gone and which closure a value came from is no longer a question the tree answers. A
 * reader downstream of that has to recognise the shapes the rewrite happens to produce, which makes
 * the set of combinators it can read a consequence of an optimisation — and the day the optimisation
 * learns a new shape, the reading narrows with nothing saying so.
 *
 * <p>So the relation is read where it stands and carried by binding. What survives the rewrite is
 * the binding: it renames nothing, so a fact keyed this way is as true after it as before. What does
 * mint bindings is a body being copied, and there the fact is carried across the copy's own renaming
 * rather than surviving on its own ({@link ElementProvenance.CopyableFactKind}). Nothing
 * here is recovered by matching one tree against another, and no identity is invented for the
 * purpose — {@link BindingId} already tells one occurrence from another, which is exactly what a
 * helper expanded at two call sites needs and exactly what the construct an author wrote must not be
 * asked to do.
 *
 * <p>What a binding holds is kept as the expression it was read from rather than as a position.
 * Which position that expression names is a question about a behavior's declared input, and a
 * container is not always one — it can be what another operation answered, which is where a reading
 * of provenance goes on rather than stopping ({@link ElementLineage}).
 */
public record ElementBindings(Map<BindingId, Core> containers, Map<BindingId, Core> held,
                              ElementProvenance provenance,
                              Map<BindingId, souther.compiler.inputs.ElementProjection> projected) {

    /** Nothing was read, which is what a body with no combinator in it comes to. */
    public static final ElementBindings NONE =
            new ElementBindings(Map.of(), Map.of(), ElementProvenance.NONE, Map.of());

    public ElementBindings {
        containers = Map.copyOf(containers);
        held = Map.copyOf(held);
        projected = Map.copyOf(projected);
    }

    /**
     * Where in the element at {@code binding} the value a walk answered stands, or null where the
     * walk answered no place of it.
     *
     * <p>Keyed by the element and not by the closure's parameter, because the element is what a
     * reader of a walk has: the answer to "which element does this walk hand out" is a binding, and
     * this is what was made of it. What the closure was is neither kept nor answerable from here.
     */
    public souther.compiler.inputs.ElementProjection projectionAt(BindingId binding) {
        return binding == null ? null : projected.get(binding);
    }

    /**
     * What {@code binding} was bound to, wherever in the body it was bound.
     *
     * <p>Over the whole body and not down the path to a reader, which is what a walk answering
     * "what does this name mean here" has to be. A binding tells itself from every other, so there
     * is no shadowing for a lookup by one to get wrong — and what a rule about an element needs is
     * often bound in a sibling of where the rule stands: a container built by one operation and
     * handed to the next is bound beside the closure that reads it, not above it.
     */
    public Core boundTo(BindingId binding) {
        return binding == null ? null : held.get(binding);
    }

    /** The container an element at {@code binding} was taken from, or null where the binding holds
     *  something else. */
    public Core containerOf(BindingId binding) {
        return binding == null ? null : containers.get(binding);
    }

    public boolean isEmpty() {
        return containers.isEmpty();
    }

    /**
     * What {@code body} binds to the elements of what.
     *
     * <p>Read off the signatures and the tree together: which argument holds the container and which
     * of the closure's parameters the element arrives on is {@link Combinators}' answer about the
     * operation, and which bindings those are is this tree's.
     *
     * <p>A closure written as anything but a block is not read. What such an argument stands for is a
     * value some other binding holds, and the parameter an element arrives on is that value's, not
     * this call's to name.
     */
    public static ElementBindings of(Core body, ElementProvenance provenance, Symbols symbols) {
        Map<BindingId, Core> found = new LinkedHashMap<>();
        Map<BindingId, Core> held = new LinkedHashMap<>();
        Map<BindingId, Core> answered = new LinkedHashMap<>();
        Map<BindingId, Core> standing = new LinkedHashMap<>();
        walk(body, found, held, provenance, answered, standing);
        Map<BindingId, souther.compiler.inputs.ElementProjection> projected =
                projections(answered, found, held, provenance, symbols);
        standing.forEach((element, closure) -> {
            souther.compiler.inputs.ElementProjection was =
                    souther.compiler.inputs.ElementProjection.read(closure, element, held, symbols);
            if (was != null) {
                projected.putIfAbsent(element, was);
            }
        });
        return found.isEmpty() && provenance.isEmpty() ? NONE
                : new ElementBindings(found, held, provenance, projected);
    }

    /**
     * What each licensed closure answered, as the way from the element to it.
     *
     * <p>A second pass, because a projection is read through the bindings on the way and the walk
     * that collects them has not finished while it is walking. Resolved inside this call and never
     * carried: what comes out is the path and the expression is dropped.
     *
     * <p>Keyed onto the element the closure was applied to, which is what a reader of a walk has in
     * hand. A closure whose answer is no place of its element leaves nothing here — a branch
     * between two of them, arithmetic over one, something built — and that absence is what says a
     * rule about the answer is not a rule about any position.
     *
     * <p><b>Onto the element of the container the licence names, and onto no other.</b> The licence
     * says which container the walk it was proved of walks, and that is the whole of what makes one
     * element the right one to hang it on. Asked only whether a licence exists, this would put the
     * projection on whichever binding the parameter happened to be bound to — and where two walks
     * are in one body, a wiring that crossed them would state of one run what was proved of the
     * other. That is not a reading lost but a rule attributed to a sequence it was not written
     * about, so the source is read and agreed with rather than discarded.
     */
    private static Map<BindingId, souther.compiler.inputs.ElementProjection> projections(
            Map<BindingId, Core> answered, Map<BindingId, Core> containers,
            Map<BindingId, Core> held, ElementProvenance provenance, Symbols symbols) {
        Map<BindingId, souther.compiler.inputs.ElementProjection> out = new LinkedHashMap<>();
        answered.forEach((parameter, body) -> {
            // The element the closure was applied to, which is what the parameter was bound to.
            if (!(held.get(parameter) instanceof Core.Read read) || read.binding() == null) {
                return;
            }
            if (!readsWhatIsHeldBy(containers.get(read.binding()),
                    provenance.projectedFrom(parameter), held)) {
                return;
            }
            souther.compiler.inputs.ElementProjection projected =
                    souther.compiler.inputs.ElementProjection.read(body, parameter, held, symbols);
            if (projected != null) {
                out.put(read.binding(), projected);
            }
        });
        return out;
    }

    /**
     * Whether {@code e} reads the value {@code binding} holds, through however many names stand
     * between them.
     *
     * <p>A name in the middle is a name and not another value: a container bound once and read
     * under a second name is the same container, and the two ends of a licence meet through it. So
     * the hops are walked and each is compared, rather than the two expressions being matched
     * against each other — which would make the agreement a question of how a body was spelled.
     *
     * <p>By the bindings met, which is what makes it stop: each tells itself from every other, so a
     * name that came round to itself is one already answered for.
     *
     * <p>Reachable to be held to on its own. What it refuses — a licence proved of one walk landing
     * on the element of another — is a wiring no expansion produces today, so there is no model
     * that puts a run at the wrong sequence and nothing a compiled body could show. The invariant is
     * held where it is decided instead, which is here.
     */
    static boolean readsWhatIsHeldBy(Core e, BindingId binding,
                                     Map<BindingId, Core> held) {
        if (binding == null) {
            return false;
        }
        java.util.Set<BindingId> met = new java.util.HashSet<>();
        Core at = e;
        while (at != null) {
            if (at instanceof Core.LetIn let) {
                at = let.body();
            } else if (at instanceof Core.Read read) {
                if (binding.equals(read.binding())) {
                    return true;
                }
                at = met.add(read.binding()) ? held.get(read.binding()) : null;
            } else {
                return false;
            }
        }
        return false;
    }

    private static void walk(Core e, Map<BindingId, Core> found, Map<BindingId, Core> held,
                             ElementProvenance provenance, Map<BindingId, Core> answered,
                             Map<BindingId, Core> standing) {
        if (e instanceof Core.LetIn let && let.binder() != null
                && let.binder().binding() != null) {
            held.putIfAbsent(let.binder().binding(), let.value());
            // The body of a binding is read only where a fact proved before the tree was rewritten
            // says this binding is a closure parameter of a walk answering one per element. The
            // shape connects the two ends; it establishes nothing, and a binding nothing licenses
            // is a `let` like any other.
            if (provenance.projectedFrom(let.binder().binding()) != null) {
                answered.putIfAbsent(let.binder().binding(), let.body());
            }
        }
        // An application of one of the language's own operations, in either of the two shapes a
        // representation gives one: a call to what the name reached where the operation has been
        // expanded away, and the operation standing as itself where it has not. What is asked of it
        // is the same question, so it is asked once.
        if (e instanceof Core.Call call && call.fn() instanceof Core.Reached reached) {
            handed(reached.denotes(), call.args(), found, null);
        }
        if (e instanceof Core.PreservedCall preserved) {
            // Where the operation still stands, what it answers of what it was handed is still
            // there to be asked, so the licence a run needs is read from the declaration rather
            // than from a fact an expansion would have had to leave behind.
            handed(preserved.declared().operation(), preserved.args(), found, standing);
        }
        Core.forEachChild(e, child -> walk(child, found, held, provenance, answered, standing));
    }

    /**
     * What one application gives its closure, where the arguments bear it out.
     *
     * <p>A closure written as anything but a block is not read. What such an argument stands for is
     * a value some other binding holds, and the parameter an element arrives on is that value's,
     * not this call's to name — two calls handed one named lambda would otherwise be two containers
     * put on one binding.
     *
     * <p>{@code standing} is where the run licence goes, and is null for an application the
     * operation has been expanded out of: what such a tree holds is a walk the rewrite left, and
     * what it answers per element is the fact the expansion wrote ({@link ElementProvenance}) rather
     * than anything the declaration is still here to say.
     */
    private static void handed(ValueName operation,
                               java.util.List<Core> args, Map<BindingId, Core> found,
                               Map<BindingId, Core> standing) {
        Combinators.Handed handed = Combinators.handedTo(operation, args,
                closure -> closure instanceof Core.Block block ? block : null);
        if (handed == null || handed.element().binding() == null) {
            return;
        }
        BindingId element = handed.element().binding();
        // The nearest binding of a name stands, as everywhere else: a body binding one twice has
        // two bindings, and each is answered where it is.
        found.putIfAbsent(element, handed.container());
        if (standing != null && answersOnePerElementOf(operation, handed.container(), args)) {
            standing.putIfAbsent(element, handed.step().body());
        }
    }

    /**
     * Whether {@code operation} answers exactly one value per element of what it hands its closure.
     *
     * <p>Two statements about one operation and both of them wanted. That the closure is handed the
     * contents of an argument is the signature's ({@link Combinators}); that the answer holds one
     * result per element of an argument is the declaration's
     * ({@link BuiltFrom#mapsEachElementOf}) — and they are the same
     * licence only where the argument each names is the same one. {@code Set.map} hands its closure
     * elements and answers no run of them, and a reading that asked only the first would state of a
     * set what was proved of a list.
     */
    private static boolean answersOnePerElementOf(ValueName operation,
                                                  Core container, java.util.List<Core> args) {
        BuiltFrom<DeclaredArgument> built =
                DefaultBoundOperationFacts.get().buildsItsResultFrom(operation);
        DeclaredArgument mapsEach =
                built == null ? null : built.mapsEachElementOf();
        if (mapsEach == null) {
            return false;
        }
        int at = CallArguments.positionOf(mapsEach, operation);
        return at >= 0 && at < args.size() && args.get(at) == container;
    }
}
