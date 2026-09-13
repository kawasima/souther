package souther.compiler.check;

import souther.compiler.core.Core;
import souther.compiler.types.BindingId;
import souther.compiler.types.Type;

import java.util.ArrayList;
import java.util.List;

/**
 * Where a value is, as the thing itself rather than as the text that named it: the binding it is
 * rooted at, and the fields read from there.
 *
 * <p>What a fact is about. Two facts are about one value when they are about one of these, and that
 * is decided by the binding a name was answered with — not by the name. A body may bind {@code a}
 * twice and mean two values; an expansion may write one body into another and carry a spelling that
 * means something else where it landed. Neither is a question the text can answer, and both are
 * settled here by construction, because a second binding is a second {@link BindingId} and the two
 * are simply different locations.
 *
 * <p>So nothing has to be forgotten when a name is bound again. A fact about the old binding stays
 * true of the old binding, and nothing reads it under the new one — where reading the key's text and
 * dropping every fact that mentioned the name was the alternative, which drops facts that merely read
 * like they mention it.
 *
 * <p>Which fields are steps is decided from what each is read from and not from how it is spelled
 * ({@link #isStep}), so two types that both declare {@code value} keep their locations apart.
 */
public record Location(BindingId root, List<String> path) {

    public Location {
        path = List.copyOf(path);
    }

    public static Location of(BindingId root) {
        return new Location(root, List.of());
    }

    /**
     * This location with {@code field} read from it, or this location unchanged where the field is a
     * transparent newtype's {@code value}.
     *
     * <p>{@code x} and {@code x.value} are one location when {@code x} is a newtype over a single
     * value: the newtype is what it holds, so a guard on one is a guard on the other. Decided from
     * what the field is read from rather than from the field being spelled {@code value}, so an
     * ordinary data that happens to declare a field of that name keeps its two locations apart.
     */
    public Location then(Type readFrom, String field, DeclarationNewtypes newtypes) {
        if (!isStep(readFrom, field, newtypes)) {
            return this;
        }
        List<String> longer = new ArrayList<>(path);
        longer.add(field);
        return new Location(root, longer);
    }

    /**
     * Whether reading {@code field} from {@code readFrom} reaches somewhere else at all.
     *
     * <p>The rule above, asked on its own. A term this is read of is not a location — nothing binds
     * it — and it is still the same value read the same two ways, so the rule is one and its two
     * readers ask it here rather than each stating it.
     *
     * <p>Asked of how the declaration was written and not of the declaration. Whether a name wears
     * one value was settled when the module was indexed, so where a location goes does not turn on
     * what the declaration says or on where it is written.
     */
    public static boolean isStep(Type readFrom, String field, DeclarationNewtypes newtypes) {
        return !(field.equals("value") && newtypes.wraps(readFrom));
    }

    /**
     * The location {@code e} names, or null where it names none, with {@code rooted} saying where a
     * binding is.
     *
     * <p>A location is a binding and the fields read from it. Anything else — a call, an arithmetic
     * expression, a literal — is a value that was computed, and a computed value is not somewhere a
     * fact can be about.
     *
     * <p>A binding is the location it is unless it was given another one — a helper's parameter is
     * the argument it was handed, and a name given a field chain is that chain — so a reader that
     * knows what its bindings were given answers with what they were given, and the rest of the walk
     * is the same walk.
     */
    public static Location of(Core e, DeclarationNewtypes newtypes,
                              java.util.function.Function<BindingId, Location> rooted) {
        return switch (e) {
            case Core.Read read -> rooted.apply(read.binding());
            case Core.FieldAccess fa -> {
                Location base = of(fa.target(), newtypes, rooted);
                yield base == null ? null : base.then(fa.target().type(), fa.field(), newtypes);
            }
            default -> null;
        };
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(String.valueOf(root));
        for (String field : path) {
            sb.append('.').append(field);
        }
        return sb.toString();
    }
}
