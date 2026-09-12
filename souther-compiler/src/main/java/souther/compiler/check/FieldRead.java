package souther.compiler.check;

import souther.compiler.diag.CompileException;
import souther.compiler.observe.FieldTypes;
import souther.compiler.types.Type;
import souther.compiler.types.TypeSymbol;

import java.util.Map;

/**
 * What a {@code .} written on a value standing at a position may name.
 *
 * <p>The one reading of a field access, and the only place a type is crossed at a {@code .}. What is
 * readable off a value is not what a value of a declaration is laid out as: a sum is laid out as
 * nothing — a value of it is a value of one of its cases — while a name every one of its cases
 * spreads is readable on every value of it. Asked of the layout, a reader is told a shared name is a
 * field nothing declares; asked here, it is told what the language reads there.
 *
 * <p><b>The nominal barrier is here.</b> A name worn over a value is not looked through: a
 * {@code data AmountN = Int} reads its own {@code value} and nothing of the {@code Int}, and a
 * {@code data StageN = Stage} reads no case's field of the sum it names. That is what a {@code .}
 * means and not a policy of whoever is reading — an elaboration, a walk that types what a text
 * wrote, and an editor asking what may follow a {@code .} are one answer, so a reader adding a
 * second decision about the names would be answering a question this has already answered.
 *
 * <p><b>Which world, from the caller, in both halves.</b> Which names are readable is nominal and
 * comes from the declarations ({@link ReadableFields}); what one of them holds is {@code world}'s.
 * An accepted program's fields are what its check settled and a text that has not checked has what
 * its declarations resolve to so far, and neither reader may fall back to the other's answer. The
 * nominal half has the same two worlds and {@code unreadable} is where they part: a declaration
 * that does not read is a thing a check reports and a thing an editor is asked in front of.
 *
 * @param symbols    what the names in a position denote
 * @param world      what a declaration holds under each of its names, in the world this reading is
 *                   being made in
 * @param unreadable what this reading does where the declarations at a position do not read
 */
public record FieldRead(Symbols symbols, PublishedDeclarations published, DeclarationKinds kinds,
                        FieldTypes world,
                        Unreadable unreadable) {

    /**
     * What a reading does where reading a position refuses.
     *
     * <p>Any refusal it raises, and not a list of them. What a value at a position is cannot be
     * settled without following the names it wears and the spreads under them, so a reading of a
     * position meets whatever those refuse — today a data that declares one name twice, a spread of
     * something that is no product, two spreads supplying one name, and tomorrow whatever else is
     * found there. Naming three would be a closure this does not have, and the reader that has to
     * answer has to answer whichever it met.
     *
     * <p>Two worlds and not a fallback. Which of them a reader is in is what it was built with, the
     * way {@link FieldTypes} already is for what a field holds, so that neither has to decide what
     * an absence meant.
     */
    public enum Unreadable {

        /** Refused where it is read. A check has to report a declaration that does not read, and
         *  this is one of the walks that reaches it. */
        REFUSED,

        /** Nothing is readable there, which is an answer. A module still being typed has
         *  declarations that do not read yet and an author reading a name in it is owed what does —
         *  the rule {@link ResolvedFieldTypes} follows for what a field holds, followed here for
         *  which names there are. What was wrong is reported where the module is checked, so
         *  nothing is lost by this reading not repeating it. */
        MAKES_NOTHING_READABLE
    }

    public FieldRead {
        if (symbols == null || world == null || unreadable == null) {
            throw new IllegalArgumentException(
                    "a field read is made against declarations, a world that says what they hold,"
                            + " and what it does where they do not read");
        }
    }

    /**
     * Every name a {@code .} on a value of {@code position} may write, with what it holds.
     *
     * <p>The surface and not one lookup, because the surface is the question: an editor listing what
     * may follow a {@code .} and an elaboration typing the one name that was written read the same
     * answer, and a second producer of it is how the two came to disagree.
     *
     * <p>Empty where nothing is readable without opening a case first — which is an answer about the
     * position and not a failure to read it.
     */
    public Map<String, Type> at(Type position) {
        return switch (surfaceOf(position)) {
            case Surface.OfAName(TypeSymbol worn) -> {
                Type held = wrapped(worn);
                yield held == null ? Map.of() : Map.of(WRITTEN_WITH, held);
            }
            case Surface.OfAShape(Shape shape) -> ReadableFields.of(shape).in(world);
            case Surface.OfNothing _ -> Map.of();
        };
    }

    /**
     * What {@code field} holds on a value of {@code position}, or null where nothing readable there
     * is written under that name.
     *
     * <p>The same question as {@link #at} asked about one name, and answered without every name at
     * the position being asked of the world and copied out to index one of them. Off the same
     * reading of the position, so there is no second rule here about how far the names a value wears
     * come off and no name answered here that {@link #at} does not also give.
     */
    public Type of(Type position, String field) {
        return switch (surfaceOf(position)) {
            case Surface.OfAName(TypeSymbol worn) ->
                    WRITTEN_WITH.equals(field) ? wrapped(worn) : null;
            case Surface.OfAShape(Shape shape) -> ReadableFields.at(shape, field, world);
            case Surface.OfNothing _ -> null;
        };
    }

    /**
     * The one name a value written under a name makes readable (spec §newtype).
     *
     * <p>Nominal, and said here rather than left to the world. A newtype is written with a single
     * implicit field and that is the whole of what a {@code .} on it may take; read as whatever the
     * world happens to hold for that declaration, a world with more to say about it would widen what
     * a name makes readable, which is not a world's to decide.
     */
    private static final String WRITTEN_WITH = "value";

    /** What the name is written over, as {@code world} says the declaration holds it. */
    private Type wrapped(TypeSymbol worn) {
        return world.of(worn).get(WRITTEN_WITH);
    }

    /** What a {@code .} on a value here reads: the one declaration a worn name is, the shape a
     *  value under no name has, or neither where the declarations do not read. */
    private sealed interface Surface {

        /** A name the value is written under. Its own declaration and no further — the single field
         *  a newtype is written with (spec §newtype), and nothing of what that field holds. */
        record OfAName(TypeSymbol worn) implements Surface {}

        /** A value under no name of its own: what the shape makes readable. */
        record OfAShape(Shape shape) implements Surface {}

        /** The declarations here do not read, and this reading answers rather than refusing. */
        record OfNothing() implements Surface {}
    }

    /**
     * Which of those {@code position} is.
     *
     * <p>Beside the two entries and in neither, so the names a value wears come off in one place
     * however wide the question being asked is. Decided in each, the surface a reader lists and the
     * name it looks up could stop being the same reading.
     *
     * <p>Reading the position is what finds a declaration that does not read: what a value here is
     * cannot be settled without following the spreads under it. So the refusal arrives here, and
     * what becomes of it is the world this reading was built for — passed on where a check is being
     * made, and a position nothing is readable at where a text is being typed.
     */
    private Surface surfaceOf(Type position) {
        TypeView view;
        try {
            view = TypeView.of(position, symbols, published);
        } catch (CompileException doesNotRead) {
            if (unreadable == Unreadable.REFUSED) {
                throw doesNotRead;
            }
            return new Surface.OfNothing();
        }
        return view.isWrapped() ? new Surface.OfAName(view.wrappers().getFirst())
                : new Surface.OfAShape(view.shape());
    }
}
