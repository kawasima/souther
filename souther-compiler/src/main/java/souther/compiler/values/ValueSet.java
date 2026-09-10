package souther.compiler.values;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Which values one position may hold, as far as the rules about it were read.
 *
 * <p>Two shapes and not one, because {@code /=} does not leave a finite answer. {@code == "A"}
 * admits one value and {@code /= "A"} admits every value but one, and a reading with only the first
 * shape would have to answer the second with "anything" — which is true and is the answer that
 * loses {@code /= true} beside {@code /= false}. Written as the pair, the two are closed under
 * everything the connectives do to them.
 *
 * <p>{@link Cofinite} is over a carrier whose values are not counted out here. Where they can be
 * counted out — a boolean, an enumeration — what is excluded is taken away from them where the rule
 * is read, and what is left is a {@link Finite}. So a set that admits nothing is {@code Finite} with
 * nothing in it, and that is the only shape emptiness has: every reading that could reach it went
 * through values it had in hand.
 *
 * <p>{@link #ANY} is what a position nothing was read about holds, which is every value there is. It
 * is not "unread": whether a reading could take in the rules about a position is a separate answer
 * and is held separately ({@link AdmissibleValues}), because a position the model says nothing about
 * and a position this could not read say the same thing here and mean opposite things to a reader.
 *
 * <p><b>A set, and nothing about what it cost.</b> Two of these are put together by {@link Sets},
 * which is where the allowance for building a machine lives — so a set is a value wherever it came
 * from, and two equal sets are equal. Written here, a meet would be an operation with no answer for
 * the case where the exact one is too much work, and the only thing left to do would be to fail in
 * the middle of a method that is supposed to be total.
 */
public sealed interface ValueSet {

    /** These values and no others. */
    record Finite(Set<Value> values) implements ValueSet {

        public Finite {
            values = held(values);
        }
    }

    /** Every value of the carrier except these. */
    record Cofinite(Set<Value> excluded) implements ValueSet {

        public Cofinite {
            excluded = held(excluded);
        }
    }

    /**
     * The strings a pattern admits, which are neither finitely many nor finitely many short of
     * everything.
     *
     * <p>A third shape because a format is a third kind of answer. {@code T[0-9]{13}} names ten
     * thousand billion strings and leaves out every other string there is, so neither of the two
     * above holds it — written as either, a reading would have to answer that the rule admits
     * everything, which is the answer that loses the rule.
     *
     * <p><b>Never empty and never everything.</b> Those two have their shapes already, and a second
     * spelling of either would be a set that {@link #isEmpty} and {@link #isAny} answer about by
     * asking which shape it is. {@link #matching} is where that is settled, and it is the only way
     * to one of these.
     *
     * <p>What the language holds is the whole of what this says. Two patterns accepting the same
     * strings are one set here, because a {@link souther.compiler.regex.Language} is its strings —
     * so a reading run twice over one model comes to values that are equal.
     *
     * <p><b>Over strings, as every set here is over one carrier.</b> {@link Cofinite} already means
     * every value of the carrier but these, so a set does not describe values of two kinds and never
     * did; what stands at a position is of the position's type. So a finite set met or joined with
     * one of these holds strings, and a value of another kind in it is a set belonging to no
     * position rather than a case for this to answer.
     */
    record Matching(souther.compiler.regex.Language language) implements ValueSet {

        public Matching {
            if (language == null) {
                throw new IllegalArgumentException("a pattern admits the strings of some language");
            }
        }
    }

    /**
     * A set kept as it was given, and unable to be changed after.
     *
     * <p>The order is the order the model writes the values in, and it is kept because something
     * will be written out of one of these: a position admitting three cases of an enumeration is
     * three classes, and which order a reader lists them in is not a thing to leave to how a set
     * happened to store them. {@code Set.copyOf} is what this is not — its iteration order is
     * settled per run of the compiler, so the same model would list them one way today and another
     * way tomorrow.
     */
    private static Set<Value> held(Set<Value> values) {
        return Collections.unmodifiableSet(new LinkedHashSet<>(values));
    }

    /** Every value there is, which is what the rules leave where they say nothing. */
    ValueSet ANY = new Cofinite(Set.of());

    /** Nothing at all. */
    ValueSet NONE = new Finite(Set.of());

    /** Just this one value. */
    static ValueSet just(Value value) {
        return new Finite(Set.of(value));
    }

    /** Everything but this one value. */
    static ValueSet allBut(Value value) {
        return new Cofinite(Set.of(value));
    }

    /** These values and no others. */
    static ValueSet oneOf(Set<Value> values) {
        return new Finite(values);
    }

    /**
     * The strings {@code language} admits.
     *
     * <p>The one way to a {@link Matching}, and where a language that turns out to be one of the
     * two shapes already here becomes that shape. A pattern nothing satisfies is the empty set and
     * not a pattern with no strings; one that accepts everything is every value and not a pattern
     * that happens to leave nothing out. Held otherwise, {@link #isEmpty} would be a question about
     * which shape a set is written in, and the answer would turn on how a rule was spelled.
     *
     * <p>Reached through {@link Sets}, which has paid for the machine being asked those two
     * questions. What comes back from here is a set every later reader may observe for nothing.
     */
    static ValueSet matching(souther.compiler.regex.Language language) {
        if (language.isEmpty()) {
            return NONE;
        }
        if (language.isEverything()) {
            return ANY;
        }
        return new Matching(language);
    }

    /**
     * Whether {@code value} is one of these.
     *
     * <p>Here, so that a reader wanting it does not answer it by asking which shape a set is. Every
     * shape has its own way of holding what it holds — one names them, one names what it leaves out,
     * one holds a language — and a caller reading that for itself is a second place the shapes are
     * enumerated, which the day a third arrived is exactly where it was not.
     */
    default boolean has(Value value) {
        return switch (this) {
            case Finite it -> it.values().contains(value);
            case Cofinite it -> !it.excluded().contains(value);
            // A language is a set of strings, so nothing that is not one is in it.
            case Matching it -> value instanceof Value.Text text && it.language().has(text.value());
        };
    }

    /**
     * Whether some value is in both, so far as that can be said without building anything.
     *
     * <p>A question a reader asks about a distinction it is deciding whether to keep, and not a
     * meet. What is wanted there is whether the position can still reach the case, and the set the
     * two come to is never looked at — so answering it by composing them would pay for a machine to
     * throw away, and would put a caller with no allowance in front of an operation that needs one.
     *
     * <p><b>One-sided where neither side can be counted out.</b> Two languages share a value only if
     * their product does, and that product is the expensive thing this exists to avoid. So the
     * answer there is that they may, which is what a reader with no proof does anyway: a case stays
     * unless something showed the position cannot reach it, and a case taken away on less than a
     * proof is a distinction the model states going missing.
     */
    default boolean sharesAnythingWith(ValueSet other) {
        if (this instanceof Finite it) {
            return it.values().stream().anyMatch(other::has);
        }
        if (other instanceof Finite) {
            return other.sharesAnythingWith(this);
        }
        // Neither is finite, so each admits values without end and no finite thing either of them
        // holds out can be the whole of what the other has.
        return true;
    }

    /**
     * One value this set holds that a source can carry, or null where there is none to offer.
     *
     * <p>Here because every reader wanting a value out of a set wants the same one, and the shapes
     * are enumerated once. Worked out at each reader, a set would answer one of them with a string
     * and the next with nothing, and the day a fourth shape arrived only the readers somebody
     * remembered would learn about it.
     *
     * <p>What a source can carry, which is not the same as what the set holds: a set of control
     * characters has a string to offer and none to write, and a row nobody can paste is not a row.
     *
     * <p>Null is this having none to offer and never the set being empty. Nothing here enumerates a
     * language, so what a caller may say about a null is that nothing composed a value.
     *
     * <p>A value of the set and never a value of a position. Whether a position holds what comes
     * back is its carrier's question — a set over strings is met with a position counting numbers
     * wherever a rule was read in one vocabulary and the values stand in another.
     */
    default Value some() {
        return switch (this) {
            case Finite it -> it.values().stream().findFirst().orElse(null);
            // What the language holds and a source can carry.
            case Matching it -> {
                String some = it.language().someWritten();
                yield some == null ? null : Value.text(some);
            }
            // Every value but a few, so the shortest string that is not among them. Asked by trying
            // rather than by naming one, because which values are excluded is the set's answer.
            case Cofinite it -> {
                for (int length = 0; length <= it.excluded().size(); length++) {
                    Value tried = Value.text("a".repeat(length));
                    if (!it.excluded().contains(tried)) {
                        yield tried;
                    }
                }
                yield null;
            }
        };
    }

    /** Whether no value is admitted, which is what refuses a declaration. */
    default boolean isEmpty() {
        return this instanceof Finite it && it.values().isEmpty();
    }

    /** Whether every value is admitted, so that nothing has been said. */
    default boolean isAny() {
        return this instanceof Cofinite it && it.excluded().isEmpty();
    }
}
