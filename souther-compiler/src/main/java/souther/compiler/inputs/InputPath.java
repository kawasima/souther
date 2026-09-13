package souther.compiler.inputs;

import souther.compiler.check.CallArguments;
import souther.compiler.check.DeclaredArgument;
import souther.compiler.check.DeclarationNewtypes;
import souther.compiler.check.DefaultBoundOperationFacts;
import souther.compiler.check.Location;
import souther.compiler.core.Core;
import souther.compiler.semantics.BuiltFrom;
import souther.compiler.types.BindingId;
import souther.compiler.types.ValueName;

import java.util.ArrayList;
import java.util.List;

/**
 * Which position of a behavior's input an expression names, and that it names none where it names
 * none ({@link PathResolution}).
 *
 * <p>One answer, for every reader of a body that has something to say about a position. A
 * {@code guard} comparing a field, a {@code match} on a parameter and an arm declaring a case
 * cannot arrive are three statements about the same positions, and each working out for itself
 * which position it was talking about is three spellings of one path — of which the axes carry one,
 * so the other two would be about positions nothing measures.
 *
 * <p><b>Asked of the binding and never of the spelling.</b> A body may bind a name its own behavior
 * already binds, and the two are different values under one word: {@code let f = defaulted(f)}
 * leaves every read below it naming the local, whose values are whatever the call answers with.
 * Read by name, the reads below it are the parameter's, and what is said about the parameter's rules
 * is said about a value they never reached ({@link BindingId} states this for every reader at once).
 *
 * <p><b>Two kinds of step and no third.</b> Descending an expression — a field's target, a call's
 * argument — stays inside one finite tree and needs nothing to stop it. Crossing to another
 * expression goes through a binding: what a name holds, what handed a name the elements of a
 * container, which binding another's elements are the same as. Those steps are a walk over the
 * binding graph and {@link BindingTrail} is the whole of what stops them, so a step added here is
 * one of the two and never a share of somebody's allowance. Nothing counts how far a walk has come:
 * a count of how many names a reading may pass through is a count of how a model was written, and
 * one name more than it allows is a position this reports as one nothing names.
 *
 * <p><b>What a name is decides which step is taken, and one step is taken.</b> A binding is a
 * parameter, or what an operation handed an element of a container on, or what a name was given, in
 * that order and asked once ({@link BindingRole}). A binding can be more than one: two walks over one
 * collection joined into one leave the second walk's element bound to what the first walk's closure
 * made. Tried as roads and raced, the winner is whichever reaches a position, and there it is the
 * value the rewrite left under the name — a rule about a value <em>made from</em> a position read as
 * a rule about the position. So an element is answered by its container and by nothing beside it,
 * whatever that answer comes to.
 *
 * <p>An expression that binds a name of its own is descended under that name. That is what a
 * {@code let} means, and it is what a helper applied to an argument is left as; a shape a splice
 * happens to leave is not a reason to stop, and stopping there left every claim inside an expanded
 * helper about a position nothing here could name.
 *
 * <p>What is known of the names comes in as answers and nothing else ({@link BindingEnvironment}).
 * How many names there are is not a fact about how far a value's provenance runs — a name bound in
 * an arm is read under bindings written elsewhere — and a reading of what a name means is built on
 * this one rather than beside it, so neither is something a walk here can reach for.
 *
 * <p>Nothing about the reading of an input reaches this and nothing here reaches it: what a position
 * can hold is read from the declarations ({@link InputDomain}), and this only says which position an
 * expression is pointing at.
 */
final class InputPath {

    private final DeclarationNewtypes newtypes;
    private final ElementQuestion asked;
    private final BindingTrail trail = new BindingTrail();

    private InputPath(DeclarationNewtypes newtypes, ElementQuestion asked) {
        this.newtypes = newtypes;
        this.asked = asked;
    }

    /**
     * The position {@code e} names, read where {@code reads} has got to.
     *
     * <p>Which fields are steps is {@link Location}'s rule, asked here rather than restated: a
     * newtype's {@code value} is not one, so {@code request.cost} and {@code request.cost.value}
     * are one position, and if the two spellings disagreed the same position would become two axes,
     * one of which no row would ever cover.
     *
     * <p>The root is the parameter as the behavior declares it. What a behavior takes is what it
     * declares, and a declared parameter is not a binding — a behavior with no implementation has
     * positions all the same — so a path is rooted at the declaration and {@link Location} at the
     * binding a body gave it.
     *
     * <p>Through what a run of {@code let}s bound on the way, since what a {@code let} binds is
     * evaluated on the way to the answer: a body that names its argument and then matches the name
     * is matching the argument. That is what a helper expanded into a body looks like, and reading
     * only the outermost name would leave every claim inside an expanded helper about a position
     * nothing here can name.
     */
    static PathResolution of(Core e, BindingEnvironment names, DeclarationNewtypes newtypes) {
        return new InputPath(newtypes, ElementQuestion.NAMED_POSITION).named(e, names);
    }

    /**
     * Where {@code e}'s value came from, and that it came from none where it did.
     *
     * <p>Beside {@link #of} and a different question, not a wider or a narrower one. That one
     * answers which position an expression names, and what a row writes at a position is what a rule
     * about it is about; this answers where a value came from. So the two cross different edges: an
     * edge saying these elements were made from another binding's is one this goes on through and
     * one that must stop, and a value made from a position is not that position — a rule about it is
     * not a rule about the values there, and nothing here says what it comes to for them.
     *
     * <p>So what this is for is saying that a rule was written. An author who filters what a
     * {@code map} answered wrote a comparison, and a reading that could place it nowhere said
     * nothing at all — which reads as a model with no rule there rather than a rule this could not
     * follow.
     */
    static PathResolution cameFrom(Core e, BindingEnvironment names,
                                   DeclarationNewtypes newtypes) {
        return new InputPath(newtypes, ElementQuestion.VALUE_ORIGIN).named(e, names);
    }

    /**
     * Where an element handed to {@code binding} stands, and that it stands at none where it does.
     *
     * <p>What an operation of the language hands its closure is an element of the container it was
     * given, so the name it arrives under stands at that container's position, inside it. Asked of
     * the binding rather than of the container's expression: a container built by one operation and
     * handed to the next names no position of its own, and the elements are the same elements.
     */
    static PathResolution elementAt(BindingId binding, BindingEnvironment names,
                                    DeclarationNewtypes newtypes) {
        return new InputPath(newtypes, ElementQuestion.NAMED_POSITION).elementOf(binding, names);
    }

    private PathResolution named(Core e, BindingEnvironment names) {
        return switch (e) {
            // What the name is, asked once, and the step that answers is the answer. An element is
            // answered by the container it came from even where that container is at no position:
            // the value a rewrite left under such a name is what the walk before it made of an
            // element, and reaching a position through it would put a line at a place whose values
            // are not the ones a rule about the name is about.
            case Core.Read r -> switch (names.roleOf(r.binding())) {
                case BindingRole.Root(var stands) -> new PathResolution.At(stands);
                case BindingRole.Element _ -> elementOf(r.binding(), names);
                // An element of more than one container is at one of their places and which is not
                // settled. Answered with the first, a rule under this name would be filed at a
                // sequence it says nothing about; answered with none, it would read as a name that
                // holds nothing of the input, and a rule the author wrote about their input would
                // leave the measurement without a word.
                case BindingRole.ElementOfSeveral(var containers) ->
                        oneOfTheElementsOf(r.binding(), containers, names);
                case BindingRole.Alias(var held) ->
                        trail.through(r.binding(), () -> named(held, names));
                case BindingRole.Unknown _ -> new PathResolution.NotAPosition();
            };
            // A field of what the target stands at, at every place the target stands. Where the
            // field is not a step of a path — a newtype's own value is the value under it — the
            // place is the target's, which is the step this takes there.
            case Core.FieldAccess fa -> named(fa.target(), names).deeper(
                    // Asked of {@link Location}, which owns the rule, from the answer this walk was
                    // handed. Whether a name wears one value was settled when the module was
                    // indexed, so a path through a field does not turn on what the declaration
                    // says or on where it is written.
                    Location.isStep(fa.target().type(), fa.field(), newtypes)
                            ? base -> base.then(fa.field()) : base -> base);
            // What an expression that binds a name comes to is what its body comes to, under that
            // name. Whether the name may stand for the position its value names is not asked here
            // and is not a question about this shape: it is asked where the name is read, of what
            // the name is.
            case Core.LetIn let -> named(let.body(), names.inside(let.binder(), let.value()));
            // A call kept standing names no location. Where the walk is over a tree that keeps them
            // that is the answer, and where it is not, its presence says this walk was handed a
            // representation it does not read — said rather than answered with "no path", which
            // would be the same answer a number gives.
            case Core.PreservedCall p -> {
                if (!names.callsStand()) {
                    throw p.unexpectedIn("an input position");
                }
                yield new PathResolution.NotAPosition();
            }
            // And every other shape names no position, said one at a time. A shape swallowed by a
            // default would come back as a model that states nothing, which is what a reader acts
            // on — so a shape added to the language is one this does not compile without.
            //
            // A value written out. The input holds it nowhere; it is what a body put beside what the
            // input holds.
            case Core.Int _, Core.Decimal _, Core.Str _, Core.Bool _, Core.Temporal _,
                 Core.UnitValue _, Core.ListLit _, Core.Tuple _, Core.OptionSome _,
                 Core.OptionNone _, Core.Construct _ -> new PathResolution.NotAPosition();
            // A value made from others. What arithmetic and what an operation answered came from
            // positions and are not positions, which is a reading of its own
            // ({@link #cameFrom}).
            case Core.Neg _, Core.Binary _, Core.Call _, Core.Apply _ ->
                    new PathResolution.NotAPosition();
            // A choice between values, which stands at no one place.
            case Core.If _, Core.IfConstructed _, Core.Match _ -> new PathResolution.NotAPosition();
            // A place inside a tuple, which is no position of an input: what a path is made of is a
            // field, an element of a sequence and a case ({@link TermPath.Step}), and a tuple's
            // places are none of the three. This is what the model says and not what this walk
            // declined to follow — there is nothing here to name.
            case Core.TupleGet _ -> new PathResolution.NotAPosition();
            // A closure is a value the language hands an operation, and what it answers about an
            // element is read where the operation stood ({@link ElementProjection}).
            case Core.Block _ -> new PathResolution.NotAPosition();
            // A place no row arrives at holds no value to be about.
            case Core.Unreachable _ -> new PathResolution.NotAPosition();
            // Nothing at all, which a caller may hand over where a body has no expression there.
            case null -> new PathResolution.NotAPosition();
        };
    }

    /**
     * Where an element of any of {@code containers} stands.
     *
     * <p>The binding takes an element of a different one on each run, so where it stands is where
     * an element of each of them stands, taken together ({@link PathResolution#anyOf}). A container
     * standing at no position of the input leaves the runs through it saying nothing and takes
     * nothing away from the runs through the others: a block handed to a walk over the input and to
     * a walk over a list written in the body states the caller's rule about the input on the first
     * run whatever the second does.
     */
    private PathResolution oneOfTheElementsOf(BindingId binding, List<Core> containers,
                                              BindingEnvironment names) {
        List<PathResolution> each = new ArrayList<>();
        for (Core container : containers) {
            each.add(trail.through(binding, () -> containerPath(container, names))
                    .deeper(TermPath::element));
        }
        return PathResolution.anyOf(each);
    }

    private PathResolution elementOf(BindingId binding, BindingEnvironment names) {
        if (!(names.roleOf(binding) instanceof BindingRole.Element(var container))) {
            return new PathResolution.NotAPosition();
        }
        // The container names no position of this behavior's input — it is what another operation
        // answered, or something this does not read — so neither does an element of it. Where a
        // reading of provenance goes on from there is not this walk's.
        return trail.through(binding, () -> containerPath(container, names))
                .deeper(TermPath::element);
    }

    /**
     * Which position holds the elements {@code e} holds, and that none does where none does.
     *
     * <p>Beside {@link #named} and not the same question. That one answers what an expression names,
     * and an operation's answer names no position — {@code List.reverse(xs)} is a value, not a place
     * a row writes at. What is asked here is where the elements of that value are, and the library
     * says: a {@code reverse} answers the elements it was given and a {@code filter} some of them,
     * so an element of either is an element of what went in.
     *
     * <p>Only where they are the same values. Where an answer holds what a closure made of an
     * element, what it holds came from a position and is not one — and a line drawn there would be
     * at a position whose values are not the ones the rule is about, which an author cannot tell
     * from a line their model states.
     */
    private PathResolution containerPath(Core e, BindingEnvironment names) {
        // And where the expression names no position, its elements may still be at one, so the ways
        // an operation's answer holds them are tried beside it.
        return switch (named(e, names)) {
            case PathResolution.At at -> at;
            // Each is a way to the same place, and neither is asked unless the other came back
            // without it, so whichever reached a position is the answer.
            case PathResolution.NotAPosition _ -> elementsOf(e, names);
            // A container standing at one of several places is where its elements are, and there
            // are as many of those as there are of it. Read further for one of them, the elements
            // would come back at a single place while the container they are of stands at more.
            case PathResolution.MayStandAt among -> among;
        };
    }

    /** The ways an operation's answer holds the elements of what it was given, and no position
     *  where the expression is not one of them. */
    private PathResolution elementsOf(Core e, BindingEnvironment names) {
        if (e instanceof Core.Read r) {
            return switch (names.stepFrom(r.binding(), asked)) {
                // Through a binding an expansion wrote, where the operation it removed answered the
                // elements it was given. The operation is gone from this tree, so what says so was
                // written where it still stood.
                case ElementStep.Through(var same) -> trail.through(r.binding(),
                        () -> containerPath(new Core.Read(r.name(), same, r.type(), r.pos()),
                                names));
                // The question this walk is asking does not cross what was written here, which is
                // an answer and not a road not taken. What the binding holds is what the walk on
                // the other side of that edge made, so reading it is the crossing said another way.
                case ElementStep.Refused _ -> new PathResolution.NotAPosition();
                // And where nothing says where these elements came from, what the binding holds is
                // all there is. Looked up over the whole body and not down the path to here: a
                // container built by one operation and handed to the next is bound beside the
                // closure that reads it rather than above it.
                case ElementStep.NoEdge _ -> {
                    Core held = names.heldAnywhereBy(r.binding());
                    yield held == null ? new PathResolution.NotAPosition()
                            : trail.through(r.binding(), () -> containerPath(held, names));
                }
            };
        }
        // Or through an operation that answers what it was given, in either of the two shapes a
        // representation gives an application: the call a name reached where the operation has been
        // expanded away, and the operation standing as itself where it has not. What the library
        // says its answer holds is said of the operation, so it is asked once of that.
        ValueName operation;
        List<Core> args;
        switch (e) {
            case Core.Call call when call.fn() instanceof Core.Reached reached -> {
                operation = reached.denotes();
                args = call.args();
            }
            case Core.PreservedCall kept -> {
                operation = kept.declared().operation();
                args = kept.args();
            }
            default -> {
                return new PathResolution.NotAPosition();
            }
        }
        BuiltFrom<DeclaredArgument> built =
                DefaultBoundOperationFacts.get().buildsItsResultFrom(operation);
        DeclaredArgument holds = built == null ? null : built.holdsTheElementsOf();
        // What is made from a position came from it and is not it, so an answer holding only that
        // is crossed by the walk after where a value came from and not by the walk after which
        // position an expression names — the same two licences an edge written by an expansion
        // carries ({@link souther.compiler.check.ElementProvenance#stepFrom}), read here from the
        // declaration that states them because the operation is still standing to be asked.
        DeclaredArgument which = holds != null ? holds
                : switch (asked) {
                    case VALUE_ORIGIN -> built == null ? null : built.derivesItsElementsFrom();
                    case NAMED_POSITION -> null;
                };
        // The call may be the runnable tree's and not a kept one, so its argument count is checked
        // here rather than by a kept call's own constructor.
        int argument = which == null ? -1 : CallArguments.positionOf(which, operation);
        return argument < 0 || argument >= args.size() ? new PathResolution.NotAPosition()
                : containerPath(args.get(argument), names);
    }
}
