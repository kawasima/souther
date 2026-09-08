package souther.compiler.check;

import souther.compiler.stdlib.Stdlib;
import souther.compiler.ast.Hir;
import souther.compiler.core.CompleteSignature;
import souther.compiler.core.Core;
import souther.compiler.core.Kernel;
import souther.compiler.core.KernelSignature;
import souther.compiler.diag.CompileException;
import souther.compiler.diag.Diagnostic;
import souther.compiler.diag.Region;
import souther.compiler.diag.msg.DeclarationMessage;
import souther.compiler.diag.msg.DataMessage;
import souther.compiler.diag.msg.NameMessage;
import souther.compiler.diag.msg.BehaviorMessage;
import souther.compiler.diag.msg.TypeMessage;
import souther.compiler.diag.Localizable;
import souther.compiler.diag.SourcePos;
import souther.compiler.types.ReachName;
import souther.compiler.types.Type;
import souther.compiler.types.ValueName;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Typing a call: a builtin, a helper, an injected behavior, or a data construction. Resolves the
 * argument types, binds any type variables the callee declares, and picks the result from them.
 */
public final class CallElaborator {

    private CallElaborator() {}

    /** Where the element whose order is required sits in a kernel's result. */
    private enum OrderedElement { OF_THE_LIST, OF_THE_OPTION }

    /**
     * The kernels whose element must be an ordered value, and where in the result to find it.
     *
     * <p>Souther has no type classes, so "the element is ordered" is not something a signature can
     * say (ADR-0053). The declaration states the shape and this states the rest, keyed by the kernel
     * it constrains — which is what {@code sort} has always done, written out by hand.
     */
    private static final Map<Kernel, OrderedElement> ORDERED_ELEMENT = Map.of(
            Kernel.LIST_SORT, OrderedElement.OF_THE_LIST,
            Kernel.LIST_MAX, OrderedElement.OF_THE_OPTION,
            Kernel.LIST_MIN, OrderedElement.OF_THE_OPTION);

    /**
     * Refuses an element with no natural order. An ordered primitive has one, and so does a newtype
     * over one — it carries its ordering as {@code Comparable}. A product data does not and would
     * throw at run time, so it is refused here. The empty-list literal (element {@code Nothing}) is
     * fine: it sorts to itself and its max is {@code None}.
     */
    private static void requiresOrdering(Kernel kernel, Hir.Apply call, Type result,
                                         CheckContext ctx) {
        OrderedElement where = ORDERED_ELEMENT.get(kernel);
        if (where == null) {
            return;
        }
        boolean inOption = where == OrderedElement.OF_THE_OPTION;
        Type element = switch (result) {
            case Type.OptionOf o when inOption -> o.element();
            case Type.ListOf l when !inOption -> l.element();
            default -> null;
        };
        if (element == null || element instanceof Type.Nothing
                || TypeOps.supportsOrdering(element, ctx.symbols())) {
            return;
        }
        String name = call.written().substring(call.written().indexOf('.') + 1);
        throw needsOrdered(call.pos(), name, element,
                name + " needs a list of ordered values (Int, String, Decimal, Date, Time, DateTime, Instant, a"
                        + " newtype over one of these, or an enumeration), but the element is "
                        + element + " — use its ordered field instead (e.g. map to it first)");
    }

    /**
     * Refuses a sort key with no natural order. The constraint is on what the key answers, not on
     * what the list holds, so it reads the binding the key's declared result took rather than the
     * call's result.
     */
    private static void requiresOrderedKey(Kernel kernel, Hir.Apply call, Type.FnOf declaredKey,
                                           Map<String, Type> bindings, CheckContext ctx) {
        if (kernel != Kernel.LIST_SORT_BY) {
            return;
        }
        Type answered = TypeOps.substitute(declaredKey.result(), bindings);
        if (BottomInfer.isBottom(answered) || answered instanceof Type.Var
                || TypeOps.supportsOrdering(answered, ctx.symbols())) {
            return;
        }
        throw CompileException.of(Diagnostic
                        .at(call.pos())
                        .hint(new TypeMessage.MapToAnOrderedFieldFirst()).say(new TypeMessage.TheKeyMustBeAnOrderedValue(call.written(), Type.show(answered))).build());
    }

    /**
     * A library name written where a value goes, or null where the name is not a library value.
     *
     * <p>A declaration with no parameter list is a value ([#fn-declaration]), and the library's are
     * the two empty collections. Their type comes from the context the way {@code []}'s does: the
     * expected type pins what the declaration left open, and what it does not pin is the bottom that
     * a later position fixes (ADR-0028).
     *
     * <p>The Core is the one the application spelling produced, so nothing downstream of here tells
     * the two apart — which is what keeps a growing fold's seed the seed it was.
     */
    static Core libraryValue(Hir.Var.Denoting v, CheckContext ctx, Type expected) {
        // An operation of the library. The namespace applied is a construction and not a value the
        // library declares, so it takes none of the paths below.
        if (!(v.denotes() instanceof ValueName.Stdlib.Operation lib)) {
            return null;
        }
        Stdlib.Entry entry = ctx.symbols().library().entry(lib);
        if (entry == null || !entry.declaration().params().isEmpty()) {
            return null;
        }
        Type declared = entry.signature().result();
        Map<String, Type> bindings = new HashMap<>();
        BottomInfer.pinResultTypeVars(declared, expected, bindings, ctx.symbols());
        return new Core.Call(reached(new ReachName.OfLibrary(lib), ctx),
                List.of(), TypeOps.toBottom(TypeOps.substitute(declared, bindings)), v.pos());
    }

    /**
     * What a call to a name applies, as this compiler has settled it: the name the module reaches
     * the callee by, what that name denotes, and — where the declaration behind it is a kernel of
     * the standard library — which kernel that is.
     *
     * <p>The one place a {@link Core.Reached} is built. Two things reach it — an application, and a
     * library value written on its own — and each of them asking the library whether what it reached
     * is a kernel would be one rule kept in as many places as there are callers, which is how one of
     * them comes to be the one that forgets. {@code Core.Call} takes what is applied and no longer
     * takes a name, so there is no way past here to a call target built out of a spelling.
     *
     * <p>Asked of what the name denotes rather than of how it renders. Whether an operation is a
     * kernel is a property of the declaration the resolver picked, and the two are one string apart
     * only for as long as they happen to be.
     */
    private static Core.Reached reached(ReachName.Declaration name, CheckContext ctx) {
        if (name instanceof ReachName.OfLibrary library) {
            Stdlib.Intrinsic kernel = ctx.symbols().library().intrinsicOf(library.denotes());
            if (kernel != null) {
                return new Core.Reached.OfKernel(library, kernel.kernel());
            }
        }
        return new Core.Reached.OfDeclaration(name);
    }

    static Core elaborateCall(Hir.Apply call, Scope env, CheckContext ctx,
                                      Type expected) {
        if (call.function() instanceof Hir.Var.Unanswered) {
            // reported where the name was written; this definition has no meaning to work out
            throw new Unanswerable(call.pos());
        }
        // What this applies, as the name that names it — null where what is applied is not a name,
        // which the typing below refuses where it reads the callee.
        Hir.Var.Denoting callee = call.answered();
        // A call this representation said it keeps standing, asked before anything tries to expand or
        // resolve it: what it names is settled, and the only question left is its signature. Asked of
        // the representation and not of the operation — whether anything downstream has a rule about
        // it is not this walk's business, and a kept call with no rule types like any other.
        CompleteSignature kept = callee == null
                ? null : ctx.preserved().signatureOf(callee.denotes());
        if (kept != null) {
            return preservedCall(call, kept, env, ctx, expected);
        }
        CallArgs ca = new CallArgs(call.args(), env, ctx);
        // A temporal written out is a value, not an application: which it is was settled when the
        // callee was resolved, and it becomes a value of its own here so that nothing downstream is
        // left asking a call whether it is one. Three readers were asking, each off a different
        // thing in reach — the spelling, the answered type, the shape of the argument — and each was
        // right only about programs that name none of the four temporals themselves. Asked of what
        // the name denotes, which is what the library says about itself.
        if (callee != null && callee.denotes() instanceof ValueName.Stdlib library
                && library.constructs() instanceof Type.Prim kind) {
            return temporalLiteral(call, kind, ca);
        }
        Type result = typeOfCall(ca, call, env, ctx, expected);
        // applying something this body binds is a different operation from calling something
        // declared elsewhere, and it is the only one that carries a binding into the emitted tree
        if (callee != null && callee.denotes() instanceof ValueName.Local local
                && env.typeOf(local.id()) instanceof Type.FnOf) {
            // The binding's own name, as every other read of one is built with. What the author
            // applied is the application's answer and not this read's: where a lowering bound what
            // was applied, the two are a field read and the name it was bound to.
            return new Core.Apply(
                    new Core.Read(local.name(), local.id(), env.typeOf(local.id()), call.pos()),
                    ca.cores(), result, call.pos());
        }
        // Typing the call above refuses what is not a name outright, so what is left here names a
        // declaration and says which one and how this module reaches it.
        //
        // A trailing parameter an implementation takes its `depends on` as is written as a binding
        // and reaches the behavior the clause named: the call is to that behavior, and everything
        // below asks what a call reaches rather than which parameter carried it here.
        //
        // Which makes it a different reference, and it is worked out as one. The parameter's is a
        // reference to a binding, so keeping it beside the behavior would be a route to one thing
        // paired with a denotation of another — the pairing `ReachName` holds both halves to
        // prevent. What this module reaches the behavior by is asked of the behavior.
        ValueName dependency = callee.denotes() instanceof ValueName.Local local
                ? ctx.dependencyOf(local.id()) : null;
        ReachName reaches = dependency == null
                ? callee.reachedAs()
                : ReachName.of(dependency, callee.name(), ctx.symbols().module());
        if (!(reaches instanceof ReachName.Declaration declaration)) {
            // The typing above refused what is not a name, and a binding applied became an apply
            // rather than a call. So what is left reaches a declaration, and one that does not is
            // this compiler having built a call for something no method stands behind.
            throw new IllegalStateException("`" + call.written() + "` was elaborated as a call and"
                    + " reaches " + reaches + ", which no method is emitted for");
        }
        return new Core.Call(reached(declaration, ctx), ca.cores(), result, call.pos());
    }

    /**
     * A kept call, typed by applying the signature it was declared with.
     *
     * <p>The same application a recursive helper's call goes through: a declaration that is not
     * expanded is typed from what it declares, and its type variables are settled by the arguments it
     * was given. What differs is only that this one is a node of its own, so a reader that has no
     * business with a call left standing meets it as itself rather than as an ordinary call it might
     * try to emit.
     *
     * <p>What is applied comes from {@code kept} and is not taken from the call a second time. The
     * signature was looked up under what the callee denotes, and it says which operation it is the
     * signature of, so the two cannot come apart.
     */
    private static Core preservedCall(Hir.Apply call, CompleteSignature kept, Scope env,
                                      CheckContext ctx, Type expected) {
        List<Type> params = kept.params();
        CallArgs ca = new CallArgs(call.args(), env, ctx);
        if (call.args().size() != params.size()) {
            arity(call, params.size());
        }
        Map<String, Type> bind = settledByValues(call, params, kept.result(), expected, ca::type, ctx);
        requireValueArgs(call, params, ca, bind);
        for (int i = 0; i < params.size(); i++) {
            if (params.get(i) instanceof Type.FnOf declared) {
                Type.FnOf at = (Type.FnOf) TypeOps.substitute(declared, bind);
                Type answered = ca.block(i, call.written(), at.params());
                // What the function answers settles the rest: this is an application of a declared
                // signature and nothing more. The fold rule that reads a step's result as an
                // accumulator to grow is one operation's meaning, and an operation kept standing is
                // kept because its meaning belongs to whoever reads it, not to this.
                //
                // Refused here rather than inside the walk, and by the sentence a value argument is
                // refused by: both kinds of argument are one rule, and this is the reader that
                // still has the argument to point at.
                if (TypeOps.unify(declared.result(), answered, bind, ctx.symbols())
                        instanceof Fit.Disagrees d) {
                    throw Elaborator.doesNotFit(call.args().get(i), d.actual(), d.expected(),
                            "argument " + (i + 1) + " of " + call.written());
                }
            }
        }
        // The operation as the signature that just typed this call says it: what was applied and
        // what it takes are one answer, and asking anything a second time for the name would be
        // reaching for a declaration this already has in hand.
        // Which application of which source this is, taken from the node the source was read into.
        // Worked out here instead, it would be a second answer to a question the frontend settled.
        // Both answers carried and neither decided here. Which occurrence of the operation's name
        // this applies is the callee's, and why the application is here is the application's — and
        // a call kept for a reader to quote is not always one an author wrote, a library operation
        // used as a value being expanded into a block whose application is kept the same way.
        return new Core.PreservedCall(kept.declaring(), ca.cores(),
                new Core.KeptCallPlace(call.answered().origin(), call.application()),
                TypeOps.substitute(kept.result(), bind), call.pos());
    }

    /**
     * What a call settles of the signature it applies, before any function argument is typed.
     *
     * <p>Two things state something about a polymorphic signature's variables, and they are asked in
     * this order because the order is the whole of the rule.
     *
     * <ol>
     *   <li>What the context expects of the result, where the signature takes a function at all. A
     *       variable a function parameter mentions has to be decided before that function is typed,
     *       and where no argument decides it the position the call stands in is the only thing that
     *       does.</li>
     *   <li>What each value argument states, the ones that state something first. An argument that
     *       answers no value — an empty collection carries a bottom — says nothing about what it
     *       holds, and letting it settle a variable would hold every other argument to the element
     *       type of nothing. A bottom then widens to what the others settled instead of the other way
     *       round.</li>
     * </ol>
     *
     * <p>Every reader of a declared signature asks this: the call that expands one, the call that
     * keeps one standing, and the walk that reads one to learn what a function it was handed takes.
     * They differ in what they do with a function argument afterwards — a fold reads its result as
     * the accumulator to grow, an ordinary application does not — and in nothing before it. Said once
     * because a difference here is not a failure but a variable settled to the wrong type, which is
     * reported somewhere else as something else.
     */
    static Map<String, Type> settledByValues(Hir.Apply call, List<Type> params, Type result,
                                             Type expected,
                                             java.util.function.IntFunction<Type> argType,
                                             CheckContext ctx) {
        Map<String, Type> bind = new HashMap<>();
        if (params.stream().anyMatch(Type.FnOf.class::isInstance)) {
            BottomInfer.pinResultTypeVars(result, expected, bind, ctx.symbols());
        }
        // Each value argument is asked once, here, in the order it is written. What the ordering
        // below decides is which of them settles a variable first, and nothing about how many times
        // an argument is read: typing one can decide a variable of the application it stands in, so a
        // second reading is a second answer, and then the argument classified and the argument
        // unified are not the same reading of it.
        Type[] stated = new Type[params.size()];
        List<Integer> stating = new ArrayList<>();
        List<Integer> bottoms = new ArrayList<>();
        for (int i = 0; i < params.size(); i++) {
            if (params.get(i) instanceof Type.FnOf) {
                continue;
            }
            stated[i] = argType.apply(i);
            (Type.mentions(stated[i], BottomInfer::answersNoValue) ? bottoms : stating).add(i);
        }
        stating.addAll(bottoms);
        // What an argument settles, and not whether it fits: that is required of each argument once
        // the substitution is complete, and required there because that is where the argument itself
        // is in hand. A refusal from here would name the argument in words and point at the callee,
        // the two being as far apart as an argument list is long.
        for (int i : stating) {
            TypeOps.bindVars(params.get(i), stated[i], bind, ctx.symbols());
        }
        return bind;
    }

    /**
     * The arguments of one call, each elaborated once, as the call's typing rule reaches it. A rule
     * types its arguments in its own order and shape — some through a required type, a step through
     * the accumulator the other arguments fixed — so the Core for each argument is collected here
     * rather than by a separate walk that would have to reconstruct that context.
     */
    static final class CallArgs {
        private final List<Hir.Expr> args;
        private final Core[] cores;
        private final Scope env;
        private final CheckContext ctx;

        CallArgs(List<Hir.Expr> args, Scope env, CheckContext ctx) {
            this.args = args;
            this.cores = new Core[args.size()];
            this.env = env;
            // An argument is not the value a `?` field is being given, whatever encloses the call, so
            // it does not make an optional (ADR-0011). An argument is also typed with no expected
            // type, which already refuses `None`; dropping the permission states the rule here rather
            // than leaving it to rest on that.
            this.ctx = ctx.makingAnOptional(false);
        }

        /**
         * The type of argument {@code i}, elaborated with no expected type (bottom-up) the first
         * time it is asked and remembered.
         *
         * <p>Remembered because elaborating an argument is not a question with a stable answer:
         * typing it can decide a variable of the application it stands in, and the decision is
         * written into the enclosing substitution. A second elaboration would then read the state the
         * first one left, so two readers of one argument would be reading two arguments — and the
         * Core that reached the tree would be the later one while the type a rule reasoned about was
         * the earlier. A rule may ask in whatever order it types in ({@link #requireTyped} already
         * rests on this), so the guarantee belongs here rather than in each rule remembering to ask
         * once.
         */
        Type type(int i) {
            if (cores[i] == null) {
                cores[i] = Elaborator.elaborate(args.get(i), env, ctx);
            }
            return cores[i].type();
        }

        /** Argument {@code i} checked against {@code expected}, as {@link Elaborator#requireType} does. */
        void require(int i, Type expected, String what) {
            Core c = Elaborator.elaborate(args.get(i), env, ctx);
            cores[i] = c;
            Elaborator.requireType(args.get(i), c.type(), expected, ctx.symbols(), what);
        }

        /** Argument {@code i}, elaborated once by {@link #type}, required to fit {@code required}
         *  now that the signature's variables are settled. Reads the stored core — the
         *  expression's own type did not change, only what is asked of it — so nothing is
         *  elaborated twice. */
        void requireTyped(int i, Type required, String what) {
            if (cores[i] == null) {
                throw new IllegalStateException(
                        "argument " + (i + 1) + " required before it was typed");
            }
            Elaborator.requireType(args.get(i), cores[i].type(), required, ctx.symbols(), what);
        }

        /** Argument {@code i} as a block (or a function value standing in for one), returning the
         * result type the block yields at {@code paramTypes}. */
        Type block(int i, String fnName, List<Type> paramTypes) {
            Core c = Elaborator.elaborateBlockArg(fnName, args.get(i), paramTypes, env, ctx);
            cores[i] = c;
            return ((Type.FnOf) c.type()).result();
        }

        void put(int i, Core c) {
            cores[i] = c;
        }

        /** The elaborated arguments. Every argument must have been reached: a rule that yields a type
         * without touching one of its arguments would leave the emitter a node with no type. */
        List<Core> cores() {
            List<Core> out = new ArrayList<>();
            for (int i = 0; i < cores.length; i++) {
                if (cores[i] == null) {
                    throw new IllegalStateException(
                            "argument " + (i + 1) + " was never typed at " + args.get(i).pos());
                }
                out.add(cores[i]);
            }
            return out;
        }
    }

    /**
     * The answer for a call this module has no callee for. Resolution already said what the name is,
     * and by here every answer it can give is something: a name it could not place is thrown out at
     * the top of {@link #typeOfCall}, where the report belongs to the position it was written at.
     * Telling the author of a name the compiler classified that it "is not a behavior or a builtin"
     * is discarding what the compiler knows and sending them after a typo they did not make — what is
     * wrong is the position, not the name.
     *
     * <p>Exhaustive over {@link ValueName} on purpose, with no default: a classification added later
     * cannot quietly fall through to a sentence that would be false about it. The three that reach an
     * author are the three a program can be written into. The rest name a call that should have been
     * expanded, substituted or rewritten before the check ran, which is this compiler disagreeing
     * with itself and not something an author can act on.
     */
    static RuntimeException noCallee(Hir.Apply call, Symbols symbols) {
        if (call.answered() == null) {
            return new IllegalStateException("`" + call.written()
                    + "` applies something that is not a name, at " + call.pos());
        }
        return switch (call.answered().denotes()) {
            // a behavior named from a helper `let` or a `>->` composition, neither of which reaches
            // one (spec [#calling-a-behavior])
            case ValueName.Behavior _ -> CompileException.of(Diagnostic.at(call.appliedAt())
                            .hint(new BehaviorMessage.WhatReachesABehavior(call.written()))
                            .say(new BehaviorMessage.ABehaviorCannotBeCalledFromHere(call.written())).build());
            // A type applied to an argument is a construction, and every place a construction is
            // allowed rewrites it before the check reads it — where what was written is one. A
            // newtype wraps a single value, so an application of one to any other count is not a
            // construction of it and is left as it was written; it reaches here from a place the
            // rewrite covers, and what is wrong with it is the count and not the place.
            case ValueName.OfType named when isANewtype(named, symbols) && call.args().size() != 1 ->
                    CompileException.of(Diagnostic.at(call.appliedAt())
                            .say(new DataMessage.ANewtypeWrapsOneValue(
                                    call.written(), String.valueOf(call.args().size())))
                            .build());
            // Any other application of a type reached here from somewhere no rewrite covers, so say
            // what it is rather than what it is not.
            case ValueName.OfType named -> CompileException.of(Diagnostic
                            .at(call.appliedAt()).say(new DataMessage.AConstructionCannotBeWrittenHere(named.name())).build());
            // A binding applied to arguments, whose type here is not a function. Either it is not one
            // — a value applied as though it were — or it has no type yet, which is what an inference
            // probe sees when it types a body before the binding it asks about has one and reads the
            // report to find out. The same sentence answers both: at the point of the report, this
            // name is not a function here.
            case ValueName.Local _ -> CompileException.of(Diagnostic
                            .at(call.appliedAt()).say(new NameMessage.ItIsNotAFunctionHere(call.written())).build());
            case ValueName.Helper _ -> unelaborated("a helper", call);
            case ValueName.Stdlib _ -> unelaborated("a standard-library function", call);
            // A name the language itself gives (`None`), applied. `Some`/`None` applications are
            // told apart earlier (E1303), so reaching here is a value position no rewrite covers.
            case ValueName.Builtin b -> CompileException.of(Diagnostic
                            .at(call.appliedAt()).say(new NameMessage.ANameTheLanguageGivesIsNotAFunction(b.name())).build());
            case null -> unelaborated("nothing", call);
        };
    }

    /** Whether the applied name is a newtype — read from the declaration, which says so, and not
     *  from the shape of what was written. */
    private static boolean isANewtype(ValueName.OfType named, Symbols symbols) {
        return symbols != null
                && symbols.declaredNode(named.type()) instanceof Hir.Data data && data.newtype();
    }

    private static IllegalStateException unelaborated(String what, Hir.Apply call) {
        return new IllegalStateException("`" + call.written() + "` denotes " + what
                + " and reached call elaboration unexpanded, at " + call.pos());
    }

    /** What one application of a declared signature settled: the declared result with the
     *  signature's variables substituted, and that substitution itself — for the checks a kernel
     *  runs on the outcome. Settled at construction: the map is copied, not shared with the
     *  unifier. */
    private record Applied(Type result, Map<String, Type> substitution) {
        private Applied {
            substitution = Map.copyOf(substitution);
        }
    }

    /**
     * Types an application against a declared signature — the same four steps whichever holds the
     * signature, a kernel declaration or a function type in scope (a function-typed parameter, a
     * recursive helper's signature):
     *
     * <ol>
     *   <li>pin the result's type variables from the expected type, only where a function argument
     *       waits on them — a fold whose seed is {@code []}/{@code Map.empty} has its accumulator
     *       bound from the context before the step is checked (issue #70). With no function
     *       argument nothing waits, and pinning anyway would answer an argument mismatch against
     *       the type the context wanted rather than the one the declaration asks for;</li>
     *   <li>type the value arguments, settle what they say of the signature's variables, and
     *       require each of them against the parameter it was given to;</li>
     *   <li>type the function arguments last, against parameter types the earlier arguments
     *       settled. A failure that is genuinely an empty-collection seed nothing typed — one that
     *       reported the unresolved bottom — is re-pointed at the seed rather than at the
     *       arithmetic the bottom reached; an unrelated error in the step is rethrown untouched
     *       (issue #70);</li>
     *   <li>substitute into the declared result.</li>
     * </ol>
     *
     * <p>The value arguments are required before a function argument is typed because a function
     * argument is typed against what they settled. Where one of them does not fit, what the
     * signature says the function takes was worked out from a type the call does not have — and a
     * body checked against that reports something wrong with the block, which is the reader's cue
     * to look at a block that is not the problem. Held in the other order, {@code List.sortBy(x ->
     * String.length(x), n)} on an {@code n} that is no list answers about {@code String.length}.
     *
     * <p>Arity is the caller's to check first, in its own words; this throws where the two
     * disagree rather than walking off the shorter list.
     */
    private static Applied applySignature(Hir.Apply call, Type.FnOf signature, CallArgs ca,
                                          Type expected, Scope env, CheckContext ctx) {
        List<Hir.Expr> args = call.args();
        if (args.size() != signature.params().size()) {
            throw new IllegalStateException("`" + call.written() + "` reached signature application"
                    + " with " + args.size() + " argument(s) against " + signature.params().size());
        }
        Map<String, Type> bind = settledByValues(call, signature.params(), signature.result(),
                expected, ca::type, ctx);
        requireValueArgs(call, signature.params(), ca, bind);
        try {
            for (int i = 0; i < args.size(); i++) {
                if (signature.params().get(i) instanceof Type.FnOf declaredStep) {
                    ca.put(i, Elaborator.resolveStepBinding(call.written(), declaredStep,
                            args.get(i), bind, env, ctx));
                }
            }
        } catch (CompileException stepError) {
            int seed = BottomInfer.untypedEmptySeed(ctx.symbols().library(), args, signature, bind);
            if (seed < 0 || !BottomInfer.reportsUnresolvedBottom(stepError)) {
                throw stepError;
            }
            Diagnostic.Builder b = Diagnostic
                    .at(args.get(seed).reportedAt());
            // The step's own place, where it has one a reader can be sent to. A report about code
            // out of sight, or one in a text this compile cannot name, has no place to lend: the
            // region it holds is not one this label could be read at.
            if (stepError.diagnostic() != null
                    && stepError.diagnostic().primary()
                            instanceof souther.compiler.diag.Primary.InSource(
                                    souther.compiler.diag.DiagnosticPlace.InSource place)) {
                b.secondary(place, new NameMessage.TheAccumulatorsTypeStaysUnknown());
            }
            throw CompileException.of(b.say(new NameMessage.TheElementTypeCannotBeInferredHere()).build());
        }
        return new Applied(TypeOps.substitute(signature.result(), bind), bind);
    }

    /** Each value argument held to the parameter it was given to, at its own position — the
     * refusal {@link #settledByValues} leaves to whoever has the argument in hand. */
    private static void requireValueArgs(Hir.Apply call, List<Type> params, CallArgs ca,
                                         Map<String, Type> bind) {
        for (int i = 0; i < params.size(); i++) {
            Type param = params.get(i);
            if (!(param instanceof Type.FnOf)) {
                ca.requireTyped(i, TypeOps.substitute(param, bind),
                        "argument " + (i + 1) + " of " + call.written());
            }
        }
    }

    /**
     * The type of a call, by what the name it applies denotes.
     *
     * <p>Which of these a name is was answered when the module's names were resolved, so the order
     * the cases are written in here decides nothing. Before that this was a sequence of attempts —
     * the library, then a function-typed binding, then an injected behavior — and a name that could
     * be read two ways was whichever came first.
     */
    static Type typeOfCall(CallArgs ca, Hir.Apply call, Scope env, CheckContext ctx, Type expected) {
        List<Hir.Expr> args = call.args();
        if (call.function() instanceof Hir.Var.Unanswered) {
            // reported where the name was written; this definition has no meaning to work out
            throw new Unanswerable(call.pos());
        }
        Hir.Var.Denoting callee = call.answered();
        if (callee == null) {
            throw new IllegalStateException("`" + call.written()
                    + "` applies something that is not a name, at " + call.pos());
        }
        // Asked with the name the library keys itself by, which the denotation carries. Asked with
        // the reference rendered, this would be the library looked up by a spelling — the same
        // string today, and the library's to change.
        Stdlib.Entry entry = callee.denotes() instanceof ValueName.Stdlib.Operation operation
                ? ctx.symbols().library().entry(operation) : null;
        // A declaration written with no parameter list is a value ([#fn-declaration]), and an empty
        // `()` would be a second spelling of it. The library was the last place that spelling was
        // still accepted.
        if (entry != null && entry.declaration().params().isEmpty()) {
            throw CompileException.of(Diagnostic
                            .at(call.appliedAt())
                            .hint(new NameMessage.WriteItOnItsOwn(call.written())).say(new NameMessage.ItIsNotAFunctionHere(call.written())).build());
        }
        // A shipped kernel behaves like a built-in: check the call against the declared signature
        // and yield its result type; the backend emits the primitive the call says it reaches. A
        // Souther-bodied library call — a recursive helper such as `List.foldFrom` — is not one of
        // these and takes the paths below, as any helper does.
        Stdlib.Intrinsic declaredKernel = callee.denotes() instanceof ValueName.Stdlib.Operation
                operation
                ? ctx.symbols().library().intrinsicOf(operation) : null;
        if (declaredKernel != null) {
            Kernel kernel = declaredKernel.kernel();
            KernelSignature intrinsic = declaredKernel.signature();
            if (args.size() != intrinsic.parameters().size()) {
                throw CompileException.of(Diagnostic
                                .at(call.appliedAt())
                                .say(new DeclarationMessage.AppliedToAnotherNumberOfArguments(call.written(), String.valueOf(intrinsic.parameters().size()), String.valueOf(args.size()))).build());
            }
            Applied applied = applySignature(call,
                    new Type.FnOf(intrinsic.parameters(), intrinsic.result()), ca, expected, env, ctx);
            // What remains is the kernel's own: constraints the kernel places on the outcome the
            // signature could not state, and the emitter's special cases. They read the settled
            // substitution and result — they are checks on what the application became, not part
            // of how an application is typed.
            for (Type param : intrinsic.parameters()) {
                if (param instanceof Type.FnOf declaredStep) {
                    requiresOrderedKey(kernel, call, declaredStep, applied.substitution(), ctx);
                }
            }
            requiresOrdering(kernel, call, applied.result(), ctx);
            if (kernel == Kernel.LIST_SUM || kernel == Kernel.LIST_PRODUCT) {
                return numericFold(call, applied.result(), expected);
            }
            if (kernel == Kernel.STRING_MATCHES) {
                validateRegexPattern(args.get(0), ctx.symbols());
            }
            return applied.result();
        }
        // a function-typed value in scope (a helper's function parameter) applied to
        // arguments — f(x) (spec §fn-declaration). A newtype construction 金額(500) never
        // reaches here — NewtypeDesugar has lowered it to a NewData literal.
        // a function value in force, or a recursive helper's signature: which of the two
        // is the denotation's to say, and only one of them is bound here.
        //
        // Looked up by what the callee reaches. The signatures are keyed by the reference a call is
        // left standing on, and what a report quotes is the name the author applied — which a
        // rewrite of the callee leaves alone, so a lookup on that finds the sugar and not the
        // operation it stands for.
        if (env.of(callee.denotes(), callee.reaches()) instanceof Type.FnOf fn) {
            if (args.size() != fn.params().size()) {
                throw CompileException.of(Diagnostic
                                .at(call.appliedAt())
                                .say(new DeclarationMessage.AppliedToAnotherNumberOfArguments(call.written(), String.valueOf(fn.params().size()), String.valueOf(args.size()))).build());
            }
            return applySignature(call, fn, ca, expected, env, ctx).result();
        }
        // A library name that matched no builtin or intrinsic above. Which of the two it is the
        // library says, and the two are not one report. A name it declares reached here without
        // having been expanded or bound, and neither is something an author can write or undo —
        // it is this compiler disagreeing with itself, which is what the helper arm below says of
        // the same failure and is said the same way here. A spelling it declares nothing under is
        // a wrong stdlib call (spec §stdlib) and is reported as that, not as a missing behavior.
        //
        // A sugar declares nothing, so a call of one that got this far is a call the rewrite did
        // not take: `List.fold` written with two arguments is not the three-argument call it is
        // sugar for, and what is wrong with it is what was written.
        //
        // Asked of which kind of name this reaches and not of whether the spelling holds a dot: a
        // field read applied (`deps.count(x)`) is quoted with a dot in it and reaches a binding,
        // and what is wrong with it is that it is not a function, which the report below says.
        if (callee.reachedAs() instanceof ReachName.OfLibrary) {
            if (entry != null) {
                throw unelaborated("a standard-library function", call);
            }
            throw CompileException.of(Diagnostic
                            .at(call.appliedAt()).say(new NameMessage.NotAStandardLibraryFunction(call.written())).build());
        }
        // A helper another module declares is expanded where it is called, or — where it
        // recurses — bound as a signature and answered above. Reaching here it is neither,
        // which is this compiler having failed to do one of them rather than anything the
        // author wrote. Said outright: reported as a wrong library call, it named a library
        // the author never wrote.
        if (callee.denotes() instanceof ValueName.Helper) {
            throw unelaborated("a helper", call);
        }
        // a required behavior called inline (spec §unmarked-output, §fn), or one that requires nothing and
        // is called by name (spec [#calling-a-behavior]). Both are typed against the callee's
        // declaration; where the behavior comes from at run time is the backend's to know.
        // Asked of the declaration the call reaches. Two modules may declare a behavior
        // of one name, and a table asked with the name this module writes answers for
        // whichever of them the entry happens to be.
        // A behavior named outright, or the trailing parameter an implementation takes it
        // as — which is a binding, and which behavior it stands for was settled where the
        // `depends on` clause was resolved.
        ValueName.Behavior reached = switch (callee.denotes()) {
            case ValueName.Behavior behavior -> behavior;
            case ValueName.Local local -> ctx.dependencyOf(local.id());
            default -> null;
        };
        ReqSig required = reached == null ? null : ctx.reqs().get(reached);
        if (required == null && reached != null) {
            required = ctx.callees().get(reached);
        }
        if (required == null) {
            Elaborator.optionCaseWritten(call.written(), call.pos());
            CompileException bareLibraryName = StdlibNames.writtenBare(
                    ctx.symbols().library().names(), call.written(), call.written(),
                    call.applied().reportedAt());
            if (bareLibraryName != null) {
                throw bareLibraryName;
            }
            throw noCallee(call, ctx.symbols());
        }
        arity(call, required.params().size());
        for (int i = 0; i < required.params().size(); i++) {
            ca.require(i, required.params().get(i), "argument " + (i + 1) + " of " + call.written());
        }
        return required.success();
    }

    /**
     * The constant a newtype construction wraps, if its {@code value} argument folds — for
     * {@code 金額(500)} (lowered to {@code 金額 { value = 500 }} by NewtypeDesugar) or the record
     * form written directly. Empty when the argument is a runtime value or the data is not a
     * single-{@code value} wrapper (e.g. a product).
     */
    static Optional<Object> newtypeConstantArg(Hir.NewData nd, Symbols symbols) {
        if (nd.spreads().isEmpty() && nd.inits().size() == 1
                && nd.inits().get(0).name().equals("value")) {
            return ConstEval.against(symbols).eval(nd.inits().get(0).value());
        }
        return Optional.empty();
    }

    /** The pattern of {@code String.matches} must evaluate to a string at compile time, so it is
     * validated (and can be compiled) there: a malformed regex is a compile error, not a runtime
     * exception, and the value it constrains is proven at construction (spec §stdlib-string). A
     * literal is one such expression and so is a {@code ++} of literals and of a module's values,
     * which is what lets several formats share a part (issue #208). What is validated is the string
     * the whole expression composes to, not the pieces it was written in. */
    static void validateRegexPattern(Hir.Expr e, Symbols symbols) {
        String pattern = ConstEval.against(symbols).evalString(e).orElse(null);
        if (pattern == null) {
            throw CompileException.of(Diagnostic
                            .at(e.pos()).say(new TypeMessage.ThePatternMustBeWrittenOut()).build());
        }
        try {
            java.util.regex.Pattern.compile(pattern);
        } catch (java.util.regex.PatternSyntaxException ex) {
            // getDescription() is the one-line reason ("Unclosed character class near index 3");
            // getMessage() would also dump the pattern and a caret, which the source region already shows.
            throw CompileException.of(Diagnostic
                            .at(e.pos()).say(new TypeMessage.ThePatternIsNotARegularExpression(ex.getDescription())).build());
        }
    }

    /** A stdlib argument-type error: {@code subject} (a function name) expects a container of kind
     * {@code kindKey} (a localized phrase such as "a List"), but got {@code actual}. */
    static CompileException expects(SourcePos pos, String subject, String kindKey, Type actual,
                                            String legacy) {
        return CompileException.of(Diagnostic.at(pos)
                        .say(new DeclarationMessage.ItExpectsAnotherType(subject, Localizable.of(kindKey), Type.show(actual))).build());
    }

    /**
     * The element {@code List.sum} / {@code List.product} answers with. It is {@code Int} or
     * {@code Decimal} — the two types {@code +} and {@code *} are defined for — and nothing else:
     * a newtype over one of them declares neither an addition nor a zero, so it is rejected here
     * rather than folded as the value it wraps.
     *
     * <p>Over the empty-list literal there is no element to read, and the seed the fold answers with
     * is {@code 0} or {@code 0.0m} by which of the two this is. That comes from the position the call
     * is written in — the field, the annotated binding, the declared output it feeds. A {@code ?}
     * field asks for the value it wraps rather than for an optional (ADR-0011), so the optional is
     * peeled before the position is read, exactly as a written literal has it peeled.
     *
     * <p>Three things the position can be, and they are three different reports. It states one of
     * the two: that is the answer. It states nothing: the answer is asked for rather than defaulted,
     * since defaulting to {@code Int} would make a numeric-literal default rule out of one library
     * function. It states something else: nothing about the element is unknown, and what is wrong is
     * that a sum does not go here — asking for an annotation would send the reader after something
     * the position already carries.
     */
    private static Type numericFold(Hir.Apply call, Type element, Type expected) {
        if (element == Type.INT || element == Type.DECIMAL) {
            return element;
        }
        if (BottomInfer.isBottom(element)) {
            Type position = expected instanceof Type.OptionOf o ? o.element() : expected;
            if (position == Type.INT || position == Type.DECIMAL) {
                return position;
            }
            if (position == null || BottomInfer.isBottom(position)) {
                throw CompileException.of(Diagnostic
                                .at(call.appliedAt())
                                .hint(new TypeMessage.AnnotateThePositionTheCallFeeds()).say(new TypeMessage.OverTheEmptyListTheSeedDecides(call.written())).build());
            }
            throw CompileException.of(Diagnostic
                            .at(call.appliedAt())
                            
                            .hint(new TypeMessage.ANewtypeIsBuiltFromTheResult(call.written())).say(new TypeMessage.ItAnswersANumberAndThisPositionNeedsAnother(call.written(), Type.show(position))).build());
        }
        throw CompileException.of(Diagnostic
                        .at(call.appliedAt())
                        
                        .hint(new TypeMessage.MapToTheNumericFieldFirst(call.written())).say(new DeclarationMessage.ItNeedsANumericElement(call.written(), Localizable.of("kind.numeric.list"), Type.show(element))).build());
    }

    /** A stdlib error where a list's element (or a key) must be an ordered primitive to sort/compare. */
    static CompileException needsOrdered(SourcePos pos, String subject, Type element, String legacy) {
        return CompileException.of(Diagnostic.at(pos)
                        
                        .hint(new TypeMessage.MapToAnOrderedFieldFirst()).say(new DeclarationMessage.ItNeedsAnOrderedElement(subject, Localizable.of("kind.ordered.list"), Type.show(element))).build());
    }

    /** A written temporal — {@code Date("2026-07-01")}, {@code Time("09:00")},
     * {@code DateTime("2026-07-01T09:00")}, {@code Instant("2026-07-01T09:00:00Z")}. The argument
     * must be a string literal: the compiler parses it here, so malformed text fails the build
     * rather than a run (and an {@code example} fixture, which may only hold literals, can carry a
     * temporal at all). This form spells one out; a temporal computed from values comes from the
     * boundary, from the arithmetic, or from {@code Date.fromParts} / {@code Time.fromParts}, which
     * answer a case where the parts name no such moment.
     *
     * <p>{@code kind} is handed in rather than read off the spelling: which temporal this builds is
     * what the callee's denotation said ({@link ValueName.Stdlib#constructs()}), and the spelling is
     * only what a report quotes. The node this answers with is the value itself, so the text is read
     * here once and nothing downstream reconstructs it from a call. */
    static Core.Temporal temporalLiteral(Hir.Apply call, Type.Prim kind, CallArgs ca) {
        arity(call, 1);
        ca.type(0);   // the text, typed where it stands
        if (!(call.args().get(0) instanceof Hir.StringLit lit)) {
            throw CompileException.of(Diagnostic
                            .at(call.appliedAt()).say(new TypeMessage.ATemporalTakesAWrittenString(call.written())).build());
        }
        parseTemporal(kind, call.written(), lit.value(), lit.reportedAt());
        // The construction this was written as, carried across the fold. A temporal reaches Core as
        // the value it denotes, and the application it was spelled with is folded away — not which
        // one it was, which a reader writing the construction back out needs and the place cannot
        // answer for.
        return new Core.Temporal(kind, lit.value(), call.application(), call.pos());
    }

    /** Parses a written temporal, reporting a malformed one against {@code at} — the text the
     * message quotes, which is the part of the form a reader cannot work out from the message.
     * Returns the parsed value so the backend and the example verifier share this one reading of
     * the text.
     *
     * <p>{@code kind} decides which parse runs and {@code fn} is only what a report quotes. They
     * were one value, and the caller that had a name for a temporal it had not resolved got the
     * parse the name spelled. */
    public static Object parseTemporal(Type.Prim kind, String fn, String text, Region at) {
        Object parsed;
        try {
            parsed = switch (kind) {
                case DATE -> java.time.LocalDate.parse(text);
                case TIME -> java.time.LocalTime.parse(text);
                case DATETIME -> java.time.LocalDateTime.parse(text);
                case INSTANT -> instantInUtc(text, at);
                case INT, STRING, BOOL, DECIMAL, RAW ->
                        throw new IllegalStateException("`" + fn + "` names no temporal");
            };
        } catch (java.time.format.DateTimeParseException _) {
            throw CompileException.of(Diagnostic
                            .at(at).say(new TypeMessage.ThatIsNotATemporalOfThatKind(fn, text)).build());
        }
        return toTheSecond(parsed, fn, text, at);
    }

    /**
     * A written {@code Instant}: refused where it names a second that does not exist, and where it is
     * not spelled in UTC.
     *
     * <p>A leap second is the substitution this type exists not to make. {@code Instant.parse} takes
     * {@code 23:59:60} and answers {@code 23:59:59}, so a written moment the language cannot
     * represent would become a different one with nothing saying so — the defect this whole rule is
     * about, made by the reader that enforces it. Java hands the fact over separately
     * ({@code DateTimeFormatter.parsedLeapSecond}), and that is what is read.
     *
     * <p>The {@code Z} form is the other rule and a different kind of thing. A numeric offset names
     * the same moment — {@code 09:30+09:00} and {@code 00:30Z} are one instant, and either determines
     * it — so it is not refused for being wrong. It is refused because a written value is written the
     * way the value is written back (spec §fixture-is-written-not-carried), and an {@code Instant} is
     * written in UTC. A boundary reads either form.
     *
     * <p>An offset is a spelling and not a zone. A zone is a place with rules about when its offset
     * changes ({@code ZoneId}), an offset is a displacement from UTC ({@code ZoneOffset}), and this
     * language names neither — which is why the refusal above is about the written form and not
     * about a zone leaking in.
     *
     * <p>Both are asked after parsing, so text that is no instant at all is still reported as that.
     */
    private static java.time.Instant instantInUtc(String text, Region at) {
        java.time.Instant parsed = java.time.Instant.parse(text);
        if (Boolean.TRUE.equals(java.time.format.DateTimeFormatter.ISO_INSTANT.parse(text)
                .query(java.time.format.DateTimeFormatter.parsedLeapSecond()))) {
            throw CompileException.of(Diagnostic
                            .at(at).say(new TypeMessage.ALeapSecondIsNotAMoment(text)).build());
        }
        if (!text.endsWith("Z")) {
            throw CompileException.of(Diagnostic
                            .at(at).say(new TypeMessage.AnInstantIsWrittenInUtc(text)).build());
        }
        return parsed;
    }

    /** A written time of day is spelled to the second. {@code Time} and {@code DateTime} hold no
     * finer (spec §temporal-literal), so text carrying a fraction is refused where it stands rather
     * than losing it on the way in. {@code Instant} is the temporal that keeps a sub-second reading,
     * and a {@code Date} has no time of day to carry one. */
    private static Object toTheSecond(Object parsed, String fn, String text, Region at) {
        int nano = switch (parsed) {
            case java.time.LocalTime t -> t.getNano();
            case java.time.LocalDateTime d -> d.getNano();
            default -> 0;
        };
        if (nano != 0) {
            throw CompileException.of(Diagnostic
                            .at(at).say(new TypeMessage.ATimeOfDayIsWrittenToTheSecond(fn, text)).build());
        }
        return parsed;
    }

    static void arity(Hir.Apply call, int n) {
        if (call.args().size() != n) {
            throw CompileException.of(Diagnostic
                            .at(call.appliedAt())
                            .say(new DeclarationMessage.AppliedToAnotherNumberOfArguments(call.written(), String.valueOf(n),
                                    String.valueOf(call.args().size()))).build());
        }
    }
}
