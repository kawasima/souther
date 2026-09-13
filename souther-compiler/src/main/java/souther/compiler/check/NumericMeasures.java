package souther.compiler.check;

import souther.compiler.core.Core;
import souther.compiler.types.ReachName;
import souther.compiler.types.Type;
import souther.compiler.types.ValueName;

import java.util.List;
import java.util.Set;

/**
 * The standard-library operations that count what a location holds, and the reading that finds a
 * number taken of one wherever it is written.
 *
 * <p><b>Two questions and two sets, since #1027.</b> Counting what it is given is one account of
 * what an operation takes of the one value it is given, and {@link #calls()} and {@link #isMeasure}
 * hold that narrow set: what an emptiness check means, what bounds how many a generated container
 * holds, what a clause of a value has a word for. {@link #takenIn} asks the wider question — is this
 * call a number taken of one location at all — and answers for every operation that declares an
 * account of any kind. Asked the narrow question where the wide one was meant, a guard on anything
 * but a size drew no line and nothing said so.
 *
 * <p>Neither set is enumerated here, and neither was. Both are read off the declarations, which is
 * what this class was made for the first time it happened.
 *
 * <p>Which they are is declared with the rest of what is true of the language's operations
 * ({@link souther.compiler.semantics.OperationFacts}) and read from what binds those to the
 * library ({@link BoundOperationFacts}). This once held the list
 * itself, which is what made it the first fact promoted out of a check when a second reader wanted
 * it — two lists of the same operations disagreed, and a rule discharged in one place was reported
 * in the other as a rule the model does not state.
 *
 * <p>What is here is what reading one takes: a type in a compilation's symbols, and a call. A partition reading an invariant
 * asks what counts the value in front of it, and a partition reading a guard asks whether the call
 * written there is one of these; answered from two places, adding a measure would be read by one of
 * them and not the other — the same drift one size down.
 *
 * <p>Not what the codec reads. {@code InvariantConstraints} maps a clause onto a decoder constraint,
 * which is a question about what Raoh can enforce rather than about what is a number: it has no
 * entry for {@code Set.size}, because a set crosses the boundary as a list and a constraint chained
 * after the mapping that drops duplicates would count the wrong things. That absence is a fact about
 * the decoder and would be wrong to take from here.
 *
 * <p>Held as the names they resolve to rather than as spellings. Two ways of writing a call that
 * reach one operation are one operation, and comparing {@code "String.length"} against a rendering
 * is reading a name back out of its text.
 */
public final class NumericMeasures {

    /** Every operation that counts what it is given, which is the narrow set. */
    public static Set<ValueName> calls() {
        return DefaultBoundOperationFacts.get().countsWhatItIsGiven();
    }

    /** Whether {@code operation} counts what it is given. Not whether it answers a number taken of
     *  one value, which is {@link #takenIn}'s wider question. */
    public static boolean isMeasure(ValueName operation) {
        return calls().contains(operation);
    }

    /** One such call: which operation, and what it is taken of. */
    public record Measured(ValueName.Stdlib operation, Core of) {}

    /**
     * The number {@code e} takes of one value and where it takes it, or null where it takes none.
     *
     * <p>Asked here rather than matched on a call's shape, because the same call arrives in two
     * shapes and which of them is not a detail of the walk. The tree that runs holds a
     * language-defined operation as a call of what it resolved to; the tree a declaration's own
     * rules are read in keeps it standing ({@link Core.PreservedCall}). A reader that knew one shape
     * drew the line a {@code guard} puts on a length and not the one a clause puts on the same
     * length — the same drift the list above exists to stop, one representation down.
     *
     * <p>The argument has to be one thing. A measure of several is not one of these, and what it
     * would be counted at is not a place either.
     */
    public static Measured takenIn(Core e) {
        ValueName operation = switch (e) {
            case Core.Call call when call.fn() instanceof Core.Reached reached
                    && reached.name() instanceof ReachName.OfLibrary library ->
                    library.denotes();
            case Core.PreservedCall preserved -> preserved.operation();
            case null, default -> null;
        };
        List<Core> args = switch (e) {
            case Core.Call call -> call.args();
            case Core.PreservedCall preserved -> preserved.args();
            case null, default -> List.of();
        };
        // Any operation that answers a number taken of the one value it is given, and not the
        // measures alone. `Time.hour(t)` names a number of `t` the way `String.length(s)` names one of
        // `s`, and a reading that asked the narrower question drew a line on the second and none on
        // the first — with nothing said about the guard it passed over (#1027).
        return operation instanceof ValueName.Stdlib named
                && DefaultBoundOperationFacts.get().takenAs(named) != null
                && args.size() == 1
                ? new Measured(named, args.get(0)) : null;
    }

    /**
     * The operation that counts what a value of {@code type} holds, or null where nothing counts it.
     *
     * <p>Which one it is follows from what the value is, so a rule read off a declaration and an
     * observation read off a row cannot disagree about which count was meant. Reaches through as
     * many newtypes as the type is written with: a name wrapped round a list is still a list.
     */
    public static ValueName.Stdlib takenOf(Type type, NewtypeInners inners) {
        Type carried = TypeOps.base(type, inners);
        if (carried == Type.STRING) {
            return ValueName.Stdlib.operation("String", "length");
        }
        if (carried instanceof Type.ListOf) {
            return ValueName.Stdlib.operation("List", "length");
        }
        if (carried instanceof Type.SetOf) {
            return ValueName.Stdlib.operation("Set", "size");
        }
        if (carried instanceof Type.MapOf) {
            return ValueName.Stdlib.operation("Map", "size");
        }
        return null;
    }

    private NumericMeasures() {}
}
