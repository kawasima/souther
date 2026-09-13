package souther.compiler.check;

import souther.compiler.semantics.BuiltFrom;
import souther.compiler.core.Core;
import souther.compiler.inputs.ElementProjection;
import souther.compiler.types.BindingId;
import souther.compiler.types.ValueName;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
public record ElementBindings(Map<BindingId, List<Core>> containers,
                              Map<BindingId, Core> held,
                              ElementProvenance provenance,
                              Map<BindingId, ElementProjection> projected) {

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
    public ElementProjection projectionAt(BindingId binding) {
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

    /**
     * The containers an element at {@code binding} was taken from, empty where the binding holds
     * something else.
     *
     * <p>More than one where one block was handed to more than one walk: the binding takes an
     * element of a different container on each run, which is a fact about the model and not a
     * reading that stopped. Answered as none, a name that plainly holds an element would read as a
     * name from nowhere; answered as whichever came first, a rule inside the block would be filed
     * at a container it says nothing about.
     */
    public List<Core> containersOf(BindingId binding) {
        return binding == null ? List.of() : containers.getOrDefault(binding, List.of());
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
     * <p>A closure written as a name is the block that name was bound to. Read as nothing, one
     * model would be read two ways depending on whether the author bound the closure before handing
     * it over — and the rules inside it would be about no position at all.
     *
     * <p><b>And a closure two calls share names the elements of both.</b> One block handed to two
     * operations has one parameter and two containers, so what arrives under that binding is an
     * element of a different container on each run. Kept as whichever call was met first, a rule
     * inside the closure would be filed at a sequence it says nothing about; taken back out, the
     * name would read as one that holds nothing of the input at all, and a rule the author plainly
     * wrote about their input would leave the measurement without a word. So both are kept, and
     * which of them a rule inside the closure is about is what nothing here can say.
     */
    public static ElementBindings of(Core body, ElementProvenance provenance,
                                     DeclarationNewtypes newtypes) {
        Map<BindingId, List<Core>> found = new LinkedHashMap<>();
        Map<BindingId, Core> held = new LinkedHashMap<>();
        Map<BindingId, Core> answered = new LinkedHashMap<>();
        Map<BindingId, Core> standing = new LinkedHashMap<>();
        walk(body, found, held, provenance, answered, standing);
        // What one walk hands its closure is what a licence and a projection are about, and a
        // binding taking elements of more than one container has no one walk to be about. The
        // binding is still an element of each — what says so is what came back above — and what
        // cannot be said is which, so nothing here that needs one container is said of it.
        found.forEach((element, containers) -> {
            if (containers.size() > 1) {
                standing.remove(element);
            }
        });
        Map<BindingId, ElementProjection> projected =
                projections(answered, found, held, provenance, newtypes);
        standing.forEach((element, closure) -> {
            ElementProjection was =
                    ElementProjection.read(closure, element, held, newtypes);
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
    private static Map<BindingId, ElementProjection> projections(
            Map<BindingId, Core> answered, Map<BindingId, List<Core>> containers,
            Map<BindingId, Core> held, ElementProvenance provenance,
            DeclarationNewtypes newtypes) {
        Map<BindingId, ElementProjection> out = new LinkedHashMap<>();
        answered.forEach((parameter, body) -> {
            // The element the closure was applied to, which is what the parameter was bound to.
            if (!(held.get(parameter) instanceof Core.Read read) || read.binding() == null) {
                return;
            }
            // Of the one container the walk is over. A binding taking elements of more than one
            // has no one walk for a projection to be of, and which of them this answer is a place
            // in is the very thing that could not be worked out.
            List<Core> from =
                    containers.getOrDefault(read.binding(), List.of());
            if (from.size() != 1
                    || !readsWhatIsHeldBy(from.get(0), provenance.projectedFrom(parameter), held)) {
                return;
            }
            ElementProjection projected =
                    ElementProjection.read(body, parameter, held, newtypes);
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
        Set<BindingId> met = new HashSet<>();
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

    private static void walk(Core e, Map<BindingId, List<Core>> found,
                             Map<BindingId, Core> held,
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
            handed(reached.denotes(), call.args(), found, null, held);
        }
        if (e instanceof Core.PreservedCall preserved) {
            // Where the operation still stands, what it answers of what it was handed is still
            // there to be asked, so the licence a run needs is read from the declaration rather
            // than from a fact an expansion would have had to leave behind.
            handed(preserved.declared().operation(), preserved.args(), found, standing, held);
        }
        Core.forEachChild(e, child ->
                walk(child, found, held, provenance, answered, standing));
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
                               List<Core> args,
                               Map<BindingId, List<Core>> found,
                               Map<BindingId, Core> standing, Map<BindingId, Core> held) {
        Combinators.Handed handed = Combinators.handedTo(operation, args,
                closure -> blockOf(closure, held));
        if (handed == null || handed.element().binding() == null) {
            return;
        }
        BindingId element = handed.element().binding();
        // The nearest binding of a name stands, as everywhere else: a body binding one twice has
        // two bindings, and each is answered where it is. One closure handed to two calls is one
        // binding taking elements of two containers, and both are kept — which of them a run is in
        // is what the binding cannot say, and saying neither would lose the element as well.
        List<Core> from =
                found.computeIfAbsent(element, _ -> new ArrayList<>());
        if (from.stream().noneMatch(each -> each == handed.container())) {
            from.add(handed.container());
        }
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
                                                  Core container, List<Core> args) {
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

    /**
     * The block {@code closure} is, following the names it was given through.
     *
     * <p>Through what the body bound rather than through a reading of the names in force. This is a
     * walk of one body collecting a relation, and the binding a name reads is the same binding
     * wherever it is read — so what a name holds is the answer, and there is nothing about where it
     * is read for the answer to turn on.
     *
     * <p><b>A name it has already been through is refused rather than left.</b> What binds a name
     * is written before the name can be read, so the names a body binds to other names run one way
     * and this walk goes down them — a body where they came round would be one nothing in this
     * compiler builds, and reading it as a closure nobody wrote would file the rule inside it
     * nowhere and say nothing about having done so.
     */
    private static Core.Block blockOf(Core closure, Map<BindingId, Core> held) {
        Core at = closure;
        Set<BindingId> met = new HashSet<>();
        while (at instanceof Core.Read read) {
            if (!met.add(read.binding())) {
                throw new IllegalStateException(
                        "a name bound to itself through the names it is bound to: "
                                + read.binding());
            }
            at = held.get(read.binding());
        }
        return at instanceof Core.Block block ? block : null;
    }
}
