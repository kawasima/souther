package souther.compiler.check;

import souther.compiler.core.Core;
import souther.compiler.semantics.ConstantArguments;
import souther.compiler.types.ValueName;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * The argument a bound fact names, found in a call to the operation the fact is about.
 *
 * <p>Between the fact and the call. A {@link DeclaredArgument} already carries its position — the
 * binder settled it against the declaration — so nothing here interprets a word or reads a
 * signature; what is here is the one step from "argument three of {@code List.take}" to the
 * expression standing there in this call.
 *
 * <p><b>The call has to be a call of the declaration the argument is of.</b> A position is a number,
 * and a number is right in a call of any operation that takes enough arguments — so an argument of
 * {@code List.filter} handed a call of {@code List.map} would answer with whatever stands third in
 * the map, with nothing looking wrong. The argument knows what it is an argument of and the call
 * knows what it calls, and the two are held to each other here, at the one place a position is
 * applied.
 */
public final class CallArguments {

    /** The argument {@code call} passes where {@code argument} stands. */
    public static Core of(DeclaredArgument argument, Core.PreservedCall call) {
        return call.args().get(positionOf(argument, call));
    }

    /** {@code call} with that argument replaced by {@code value}. */
    public static Core.PreservedCall replacedIn(DeclaredArgument argument,
                                                Core.PreservedCall call, Core value) {
        List<Core> args = new ArrayList<>(call.args());
        args.set(positionOf(argument, call), value);
        // The same call with one argument read another way: what it applies and why it is here have
        // not moved, so both are carried rather than made again.
        return new Core.PreservedCall(call.declared(), args, call.place(), call.type(), call.pos());
    }

    /**
     * What {@code call}'s arguments read as, for a fact that names one of them.
     *
     * <p>Between the fact and the call, as everything here is: the fact names an argument and this
     * finds it, and what the value there reads as is the caller's answer — a name given a constant
     * is that constant — so the reading is handed in rather than taken off the syntax at the call.
     *
     * <p>What is done with the answer is not here. Whether a condition on the arguments is met is
     * one question asked by two readers ({@link ConstantArguments#satisfy}), and answering it here
     * as well would be the second place it is read.
     */
    public static ConstantArguments<DeclaredArgument> readAs(
            Core.PreservedCall call,
            Function<Core, BigDecimal> folded) {
        return argument -> Optional.ofNullable(folded.apply(of(argument, call)));
    }

    private static int positionOf(DeclaredArgument argument, Core.PreservedCall call) {
        return positionOf(argument, call.declared().operation());
    }

    /**
     * Where {@code argument} stands among the arguments of a call of {@code operation}.
     *
     * <p>For a reader holding a call the language resolved but did not keep standing — the
     * runnable tree's call, or an expansion the inliner is reading — which has the operation's
     * name and its arguments and no {@link Core.PreservedCall}. The one thing a kept call answers
     * for that such a call does not is that its argument count is the declaration's, so what a
     * caller here does with the position is index its own argument list; what is held here, as
     * for a kept call, is that the argument is an argument of this operation.
     */
    public static int positionOf(DeclaredArgument argument, ValueName operation) {
        if (!argument.of().operation().equals(operation)) {
            throw new IllegalStateException("argument " + (argument.position() + 1) + " of `"
                    + argument.of() + "` was asked for in a call of `" + operation
                    + "`, which is another declaration");
        }
        return argument.position();
    }

    private CallArguments() {}
}
