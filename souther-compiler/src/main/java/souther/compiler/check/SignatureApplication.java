package souther.compiler.check;

import souther.compiler.types.Type;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;

/**
 * A declared signature applied to what stands at its parameters.
 *
 * <p>The one step every reader of a declared signature takes, and nothing beside it. What a
 * signature's variables are settled to by a result the context expects and by arguments that state
 * a type is here; making a Core, refusing an argument that does not fit, typing a function
 * argument and reporting any of it are the caller's. A reader that only knows what the arguments
 * state can take this step and no further, which is what lets the reading that types a text and
 * the reading that says what declarations already state share the rule rather than each writing
 * one.
 *
 * <p>Nothing here is about a call. What was written, where it points and how many arguments it has
 * are a caller's questions, and a step that took a call would be one only a caller could take.
 */
final class SignatureApplication {

    private SignatureApplication() {
    }

    /**
     * What applying a signature settles of its variables, before any function argument is typed.
     *
     * <p>Two things state something about a polymorphic signature's variables, and they are asked
     * in this order because the order is the whole of the rule.
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
     * keeps one standing, the walk that reads one to learn what a function it was handed takes, and
     * the walk that reads what declarations state about an application. They differ in what they do
     * with a function argument afterwards — a fold reads its result as the accumulator to grow, an
     * ordinary application does not — and in nothing before it. Said once because a difference here
     * is not a failure but a variable settled to the wrong type, which is reported somewhere else as
     * something else.
     *
     * @param params   what the signature declares it takes
     * @param result   what the signature declares it answers
     * @param expected what the position the application stands in requires of the result, or null
     *                 where the reader has no position to read
     * @param stated    what stands at each parameter, asked by position and asked once
     * @param published what each declaration the types name says about itself
     */
    static Map<String, Type> settledByValues(List<Type> params, Type result, Type expected,
                                             IntFunction<Type> stated,
                                             PublishedDeclarations published) {
        Map<String, Type> bind = new HashMap<>();
        if (params.stream().anyMatch(Type.FnOf.class::isInstance)) {
            BottomInfer.pinResultTypeVars(result, expected, bind, published);
        }
        // Each value argument is asked once, here, in the order it is written. What the ordering
        // below decides is which of them settles a variable first, and nothing about how many times
        // an argument is read: typing one can decide a variable of the application it stands in, so a
        // second reading is a second answer, and then the argument classified and the argument
        // unified are not the same reading of it.
        Type[] at = new Type[params.size()];
        List<Integer> stating = new ArrayList<>();
        List<Integer> bottoms = new ArrayList<>();
        for (int i = 0; i < params.size(); i++) {
            if (params.get(i) instanceof Type.FnOf) {
                continue;
            }
            at[i] = stated.apply(i);
            (Type.mentions(at[i], BottomInfer::answersNoValue) ? bottoms : stating).add(i);
        }
        stating.addAll(bottoms);
        // What an argument settles, and not whether it fits: that is required of each argument once
        // the substitution is complete, and required there because that is where the argument itself
        // is in hand. A refusal from here would name the argument in words and point at the callee,
        // the two being as far apart as an argument list is long.
        for (int i : stating) {
            TypeOps.bindVars(params.get(i), at[i], bind, published);
        }
        return bind;
    }
}
