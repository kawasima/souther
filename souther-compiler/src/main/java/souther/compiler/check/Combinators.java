package souther.compiler.check;

import souther.compiler.DefaultStdlib;
import souther.compiler.stdlib.Stdlib;
import souther.compiler.semantics.Combinator;
import souther.compiler.ast.Hir;
import souther.compiler.core.Core;
import souther.compiler.types.Type;
import souther.compiler.types.ValueName;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Which library operations hand a closure the contents of a container, and where: the closure is
 * argument {@code closureArg}, the value it receives is closure parameter {@code elementParam}, and
 * the container it comes from is argument {@code containerArg}.
 *
 * <p>Two checks read this and neither states it. The totality check credits a value a closure is
 * handed as a sub-term of the container, so recursing on it is structural; the invariant-discharge
 * check binds that value to the container's element type, so a construction inside the closure is
 * analyzed rather than left opaque. What each does with the answer is its own; what it asks is one
 * question about the operation.
 *
 * <p>Nothing here is written down. The library's own signature already says which argument takes a
 * function and which parameter of that function has the type of what a container holds, so the rules
 * are read off {@link Stdlib}: an operation the library gains is answered for by being declared. A
 * signature this cannot read off — two function arguments, or two closure parameters that could each
 * be the element — raises rather than answering half, because a combinator nobody registered is a
 * check that quietly stops crediting an element.
 *
 * <p>Each reader asks under the name it holds. The totality check reads the tree an author wrote,
 * where {@code List.fold} still spells itself; the discharge check reads one where the rewrite to
 * {@code List.foldFrom} has happened. So a {@linkplain Stdlib#rewrites() sugared} name is answered
 * with what it rewrites to, over the arguments the rewrite keeps in place — and the discharge side
 * never asks, because {@link Preserved} is built from declarations and a sugar has none.
 */
final class Combinators {

    /** What {@code operation} hands its closure, or null where it hands one nothing a container
     * holds — including where it applies no closure at all, and where the name applied is not a
     * library operation. */
    static Combinator of(ValueName operation) {
        return operation == null ? null : Derived.RULES.get(operation);
    }

    /** What a call hands its closure: the argument that takes the function, the block that argument
     * is, the parameter the element arrives on, and the container it comes from. */
    record Handed(Core closure, Core.Block step, Core.Binder element, Core container) {}

    /** The same, off the tree an author wrote, where a closure is the block as written. */
    record Written(Hir.Block step, Hir.Binder element, Hir.Expr container) {}

    /**
     * What {@code call} hands its closure, or null where it hands one nothing a container holds — or
     * where what stands in the closure argument is not a block this can read.
     *
     * <p>{@code at} is what the names around the call denote, since a closure may be written as a
     * name bound to a block. It is the denotations where the call stands: what a name means depends
     * on which bindings it is under, so it is asked per call and not once per body.
     */
    static Handed handedTo(Core.PreservedCall call, Denotations at) {
        return handedTo(call, closure -> Terms.blockOf(closure, at));
    }

    /**
     * The same, told how to reach the block a closure is.
     *
     * <p>What a closure is written as is the one thing a reader of this needs that differs between
     * readers: a walk inside a check has the denotations it built, and a reading of the input has
     * its own answer about what a name stands for. What the operation hands over does not differ, so
     * it is read once here and the difference is a parameter.
     */
    static Handed handedTo(Core.PreservedCall call,
                           java.util.function.Function<Core, Core.Block> blockOf) {
        return handedTo(call.operation(), call.args(), blockOf);
    }

    /**
     * The same, of an application named by the operation it applies and the arguments it passes.
     *
     * <p>Which of the two shapes a representation gives an application is not a difference this
     * table has anything to say about: the rule is about the operation, and the arguments are the
     * arguments. So the question is asked once, of the two things it is about, and a reader holding
     * either shape hands over the operation it resolved to and the arguments it carries.
     */
    static Handed handedTo(ValueName operation, List<Core> args,
                           java.util.function.Function<Core, Core.Block> blockOf) {
        Combinator rule = of(operation);
        if (rule == null || rule.closureArg() >= args.size()
                || rule.containerArg() >= args.size()) {
            return null;
        }
        Core closure = args.get(rule.closureArg());
        Core.Block step = blockOf.apply(closure);
        return step == null || rule.elementParam() >= step.params().size() ? null
                : new Handed(closure, step, step.params().get(rule.elementParam()),
                        args.get(rule.containerArg()));
    }

    /**
     * The same, off the tree an author wrote — where a closure written as anything but a block is one
     * this says nothing about, the tree not yet having been read for what names denote.
     *
     * <p>Nor is a call the operation would not have accepted — written with fewer arguments than it
     * takes, or handed a block with fewer parameters than it applies one to. That is not this table
     * disagreeing with a signature: it is what the surface tree still holds, the walk that reads it
     * running beside the checks that report an arity rather than after them, so a call already known
     * to be wrong reaches here. A sugar is how both arrive — it has no declaration of its own, so
     * what is said about the arity of {@code List.fold} is said against the call it becomes, and by
     * then this has already read it. Nothing about arguments or parameters a call does not have is
     * true, so nothing is said, and the arity is reported by the check whose question it is.
     *
     * <p>The tree a representation keeps standing needs the first of those answers and not the
     * second, and the two come from different places. A {@code PreservedCall} has the arguments its
     * declaration takes, so the argument positions this table names are positions it has — that is
     * the node's own and holds however one was built. What stands in the closure argument having as
     * many parameters as the rule reaches for is a separate matter, settled where the block was
     * typed against the signature; nothing about a call says it.
     */
    static Written handedTo(Hir.Apply call) {
        // A call applying a name nothing declares hands its closure to no operation this table
        // has: there is no declaration to find a rule under, and what is wrong with it is reported
        // where the name is written.
        Combinator rule = call.answered() == null ? null : of(call.answered().denotes());
        if (rule == null || rule.closureArg() >= call.args().size()
                || rule.containerArg() >= call.args().size()
                || !(call.args().get(rule.closureArg()) instanceof Hir.Block step)) {
            return null;
        }
        if (rule.elementParam() >= step.params().size()) {
            return null;
        }
        return new Written(step, step.params().get(rule.elementParam()),
                call.args().get(rule.containerArg()));
    }

    /**
     * The operations that take a function and hand it nothing a container holds. The library has
     * none: every function it takes is applied to what a container argument holds, which is why the
     * rules can be read off the signatures at all.
     *
     * <p>A signature the derivation gets no rule out of is not a gap it left — it is an operation
     * whose closure is handed something else, and saying so is what tells the next reader which of
     * the two a missing rule is. {@link Question#COMBINATOR} holds this to that.
     */
    static final Set<ValueName> HANDS_ITS_CLOSURE_NOTHING = Set.of();

    /** The operations a rule was read off, for the check that a rule answers a question its
     * operation is asked. */
    static Set<ValueName> answered() {
        return Derived.RULES.keySet();
    }

    /** The operations there is a rule for, for the tests that hold them to firing. Handed over as
     *  the operations, so a reader holding one asks the library with it rather than with a spelling
     *  this rendered on the way out. */
    static Set<ValueName.Stdlib.Operation> named() {
        Set<ValueName.Stdlib.Operation> named = new LinkedHashSet<>();
        Derived.RULES.keySet().forEach(operation -> {
            if (operation instanceof ValueName.Stdlib.Operation library) {
                named.add(library);
            }
        });
        return named;
    }

    /** Read off the library on the first ask. The library is the same library for every module
     * compiled, and reading it is answering the question for all of them at once. */
    private static final class Derived {
        private static final Map<ValueName, Combinator> RULES =
                read(DefaultStdlib.get());
    }

    /** A pure function of the library, so the holder above is the only thing here that reaches for
     *  the process's own — {@link souther.compiler.DefaultStdlib} says who may and why the loader
     *  may not. */
    private static Map<ValueName, Combinator> read(Stdlib stdlib) {
        Map<ValueName, Combinator> rules = new LinkedHashMap<>();
        stdlib.entries().forEach((operation, entry) -> {
            Combinator rule = ruleFor(operation, entry.signature().params());
            if (rule != null) {
                rules.put(operation, rule);
            }
        });
        stdlib.rewrites().forEach((sugar, rewrite) -> {
            Combinator target = rules.get(rewrite.target());
            if (target == null) {
                return;   // what it becomes hands its closure nothing, so neither does it
            }
            if (target.closureArg() >= rewrite.keptArgs() || target.containerArg() >= rewrite.keptArgs()) {
                throw new IllegalStateException(sugar + " is sugar for " + rewrite.target()
                        + ", whose closure or container is not among the arguments the rewrite keeps"
                        + " in place — what it hands its closure cannot be said of the sugar");
            }
            rules.put(sugar, target);
        });
        return Collections.unmodifiableMap(rules);
    }

    /**
     * The rule the signature of {@code qualified} states, or null where it states none.
     *
     * <p>The closure is the argument that takes a function. The container is an argument holding
     * something whose element type is the type of one of that closure's parameters — which is what
     * "hands its closure the contents of" means, said in types. An operation whose signature admits
     * more than one reading of either is one this cannot answer for.
     */
    private static Combinator ruleFor(ValueName.Stdlib.Operation qualified, List<Type> params) {
        int closureArg = -1;
        for (int i = 0; i < params.size(); i++) {
            if (params.get(i) instanceof Type.FnOf) {
                if (closureArg >= 0) {
                    throw new IllegalStateException(qualified + " takes two functions, so which one is"
                            + " handed the container's elements is not read off its signature");
                }
                closureArg = i;
            }
        }
        if (closureArg < 0) {
            return null;
        }
        List<Type> closureParams = ((Type.FnOf) params.get(closureArg)).params();
        Combinator found = null;
        for (int c = 0; c < params.size(); c++) {
            if (c == closureArg) {
                continue;   // the closure is what receives; it is not what is received from
            }
            Type element = Terms.elementType(params.get(c));
            if (element == null) {
                continue;
            }
            for (int p = 0; p < closureParams.size(); p++) {
                if (!element.equals(closureParams.get(p))) {
                    continue;
                }
                if (found != null) {
                    throw new IllegalStateException(qualified + " could be handing its closure the"
                            + " contents of more than one of its arguments, or on more than one"
                            + " parameter, so which is not read off its signature");
                }
                found = new Combinator(closureArg, p, c);
            }
        }
        return found;
    }

    private Combinators() {}
}
