package souther.compiler.check;

import souther.compiler.core.Core;
import souther.compiler.types.ValueName;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * What an expression is made of, as the reader that looked it up found it.
 *
 * <p>Two questions were being answered by one word. Which positions a side of a comparison names is
 * about what the rule is over; whether an operation stands between those positions and the value
 * being compared is about what it would take to follow the rule back to them. They are not the same
 * question and they do not refuse each other — {@code Int.add(a, b)} names two positions and is an
 * operation's answer — so a classification that made a side one or the other had to drop whichever
 * it was not asked for, and the reason a rule went unread was picked from what was left.
 *
 * <p><b>The walk and the grammar are here; the environment's answers are the caller's.</b> Which
 * nodes hold what — an application of an operation, the language's own arithmetic, a value written
 * out — is a fact about the language, and it is read once. What a name denotes, which expression is
 * a position, and where a value that is no position came from depend on what the reader is looking
 * at, and are asked of {@link Reading}. Written twice, the two copies of this walk disagreed about
 * whether {@code y + 1} named anything (spec §example-partition).
 *
 * @param <K> what the caller calls a position
 */
public sealed interface ValueOrigin<K> {

    /** Every position this names, however deeply, in the order the reader met them. */
    Set<K> positions();

    /** The expression is the position itself, or a number taken of it. */
    record IsAPosition<K>(K at) implements ValueOrigin<K> {

        public IsAPosition {
            Objects.requireNonNull(at, "this one names the position it is");
        }

        @Override
        public Set<K> positions() {
            return Set.of(at);
        }
    }

    /**
     * An operation answered this, over whatever its arguments were made of.
     *
     * <p>The arguments are kept in the order they were written and not gathered into a set. Which
     * argument a position stands at is what an operation's own statement about its result is
     * written in terms of, so {@code Date.daysBetween(a, b)} and {@code Date.daysBetween(b, a)} are
     * two origins; read off the positions alone they are one.
     */
    record Applied<K>(ValueName operation, List<ValueOrigin<K>> arguments)
            implements ValueOrigin<K> {

        public Applied {
            Objects.requireNonNull(operation, "an application names its operation");
            arguments = List.copyOf(arguments);
        }

        @Override
        public Set<K> positions() {
            return across(arguments);
        }
    }

    /**
     * The language's own arithmetic over what stands under it: a sum, a difference, a scaling, a
     * value read out of a name the reader could see through.
     *
     * <p>Told apart from {@link Applied} because what would lift a reading of each is different
     * work. A form the arithmetic does not take apart asks for a wider fragment; an operation's
     * answer asks for a statement about that operation.
     */
    record Composed<K>(List<ValueOrigin<K>> parts) implements ValueOrigin<K> {

        public Composed {
            parts = List.copyOf(parts);
            if (parts.isEmpty()) {
                // Composed of nothing is not composition. An expression with nothing under it is a
                // value written out or one this reader cannot name, and both of those say so.
                throw new IllegalArgumentException("an expression composed of nothing is a leaf");
            }
        }

        @Override
        public Set<K> positions() {
            return across(parts);
        }
    }

    /**
     * A value that is one of several, and what decided which of them it is.
     *
     * <p>Told apart from {@link Composed} because the two are different relations. Everything under
     * a composition contributed to the value; the alternatives of a choice are what the value may
     * be, of which one is. A fork read as a composition puts what it turned on beside the values it
     * chooses between, and a reader asking where the value came from is answered with the position
     * that decided which value it is.
     *
     * <p><b>{@code decidedBy} contributes to dependency positions, but not to value provenance.</b>
     * Which positions the expression depends on includes what it turned on — a comparison over
     * {@code if flag then a else b} is one no reading of {@code flag} is outside of. Where its value
     * came from does not: none of the strings the value is are {@code flag}'s.
     */
    record OneOf<K>(ValueOrigin<K> decidedBy, List<ValueOrigin<K>> alternatives)
            implements ValueOrigin<K> {

        public OneOf {
            Objects.requireNonNull(decidedBy, "a choice names what decided it");
            alternatives = List.copyOf(alternatives);
            if (alternatives.isEmpty()) {
                // A choice between nothing is not a choice. An expression that chooses has the
                // values it chooses between, and one of them is what it comes to.
                throw new IllegalArgumentException("a choice states what it is between");
            }
        }

        @Override
        public Set<K> positions() {
            Set<K> out = new LinkedHashSet<>(decidedBy.positions());
            for (ValueOrigin<K> each : alternatives) {
                out.addAll(each.positions());
            }
            return Collections.unmodifiableSet(out);
        }
    }

    /** A value written out where it stands. */
    record Written<K>() implements ValueOrigin<K> {

        @Override
        public Set<K> positions() {
            return Set.of();
        }
    }

    /**
     * A value that came from a position without being one: an element an operation handed out, and
     * whatever was made of it.
     *
     * <p>Where it came from and not which position it is. A rule about such a value is not a rule
     * about the values standing at the position it came from, and the two are told apart here so
     * that a reader asking either gets the answer to the one it asked.
     */
    record MadeFromAPosition<K>(K at) implements ValueOrigin<K> {

        public MadeFromAPosition {
            Objects.requireNonNull(at, "this one names where the value came from");
        }

        @Override
        public Set<K> positions() {
            return Set.of();
        }
    }

    /** Nothing this reader can say about it. */
    record Unnameable<K>() implements ValueOrigin<K> {

        @Override
        public Set<K> positions() {
            return Set.of();
        }
    }

    /** The positions everything in {@code of} names, in the order they were met. */
    private static <K> Set<K> across(List<ValueOrigin<K>> of) {
        Set<K> out = new LinkedHashSet<>();
        for (ValueOrigin<K> each : of) {
            out.addAll(each.positions());
        }
        return Collections.unmodifiableSet(out);
    }

    /** The position this is made from where it is made from one and names none, or null. Asked of
     *  the whole rather than of a part: a value made from a position is one value however many
     *  operations stand over it. */
    default K madeFrom() {
        return switch (this) {
            case MadeFromAPosition<K> from -> from.at();
            case Applied<K> applied -> firstMadeFrom(applied.arguments());
            case Composed<K> composed -> firstMadeFrom(composed.parts());
            // Only where every value it could be came from the one position, since the value is
            // one of them and nothing here says which. What decided it is not asked: a choice made
            // on what stands at a position is not a value made from it.
            case OneOf<K> choice -> sameMadeFrom(choice.alternatives());
            case IsAPosition<K> _, Written<K> _, Unnameable<K> _ -> null;
        };
    }

    /** The one position everything in {@code of} is made from, or null where they differ or any of
     *  them is made from none. */
    private static <K> K sameMadeFrom(List<ValueOrigin<K>> of) {
        K agreed = null;
        for (ValueOrigin<K> each : of) {
            K from = each.madeFrom();
            if (from == null || (agreed != null && !agreed.equals(from))) {
                return null;
            }
            agreed = from;
        }
        return agreed;
    }

    private static <K> K firstMadeFrom(List<ValueOrigin<K>> of) {
        for (ValueOrigin<K> each : of) {
            K from = each.madeFrom();
            if (from != null) {
                return from;
            }
        }
        return null;
    }

    /** The operation standing over this value, or null where none does. Only the outermost: what a
     *  rule would have to be followed back through first is the one it was written over. */
    default ValueName appliedOperation() {
        return this instanceof Applied<K> applied ? applied.operation() : null;
    }

    /**
     * What this walk asks its caller for: what depends on the reader's environment, and nothing
     * else.
     *
     * @param <K> what the caller calls a position
     * @param <E> what the caller carries as it goes inside a binding
     */
    interface Reading<K, E> {

        /** The position {@code e} is, or null where it is none. Asked of every node before anything
         *  under it, so a position names itself and what is inside it is the same position. */
        K positionOf(Core e, E at);

        /** The position the value {@code e} came from without being one, or null. Asked only where
         *  nothing under {@code e} is a position. */
        K madeFrom(Core e, E at);

        /** The value {@code read}'s name denotes and what to read it in, or null where the name
         *  stands for something of its own. */
        AffineForms.ReadThrough<E> readThrough(Core.Read read, E at);

        /** What {@code li}'s body is read in. */
        E inside(Core.LetIn li, E at);
    }

    /** What {@code e} is made of. */
    static <K, E> ValueOrigin<K> of(Core e, E at, Reading<K, E> reading) {
        return of(e, at, reading, new java.util.HashSet<>());
    }

    private static <K, E> ValueOrigin<K> of(Core raw, E at, Reading<K, E> reading,
                                            Set<souther.compiler.types.BindingId> following) {
        Core e = Terms.asOperator(raw);
        K here = reading.positionOf(e, at);
        if (here != null) {
            return new IsAPosition<>(here);
        }
        if (e instanceof Core.Read read) {
            AffineForms.ReadThrough<E> through = reading.readThrough(read, at);
            if (through != null && following.add(read.binding())) {
                ValueOrigin<K> inside = of(through.value(), through.at(), reading, following);
                following.remove(read.binding());
                return inside;
            }
            // A name standing for several values is not one this walk reads through. What may be
            // said of such a name is what all of its values support, and that is one law with one
            // owner — the arithmetic, which is asked before this walk is
            // ({@link souther.compiler.partition.ComparisonAssessment}). Answered here as well, it
            // would be a second account of it: two values agree as arithmetic and are made of
            // different things, so the two would differ about a name and the stricter would win.
            return leafOf(e, at, reading);
        }
        // The operation a call reaches, asked of {@link Terms} so that what counts as one is
        // settled where the arithmetic already settles it. A two-argument call the language has an
        // operator for is not one of these: {@link Terms#asOperator} has already turned it into the
        // arithmetic it stands for, which is what {@link Composed} is.
        ValueName operation = Terms.operationOf(e);
        if (operation != null) {
            return new Applied<>(operation, partsOf(Terms.argsOf(e), at, reading, following));
        }
        if (writtenOut(e)) {
            return new Written<>();
        }
        // What a {@code let} is made of is its body, read in the binding. The initializer is not a
        // part of the value: {@code let $x = a in 0} is zero, and reading both made a helper that
        // ignores its argument into an expression about the argument. It is reached where the body
        // reads the name, which is what {@link Reading#readThrough} answers — the same way the
        // arithmetic next door reaches it, so the two cannot come to different values for one
        // expression.
        if (e instanceof Core.LetIn li) {
            return of(li.body(), reading.inside(li, at), reading, following);
        }
        // A fork, said as the choice it is. Walked by its children it would come back composed of
        // what it turned on and what it chooses between alike, and the two are not one relation.
        if (e instanceof Core.If iff) {
            return new OneOf<>(of(iff.cond(), at, reading, following),
                    partsOf(List.of(iff.then(), iff.els()), at, reading, following));
        }
        if (e instanceof Core.IfConstructed attempt) {
            // What the attempt builds is what decides the branch: its invariant is the test.
            return new OneOf<>(of(attempt.construct(), at, reading, following),
                    partsOf(armsOf(attempt), at, reading, following));
        }
        if (e instanceof Core.Match match) {
            return new OneOf<>(of(match.scrutinee(), at, reading, following),
                    partsOf(armsOf(match), at, reading, following));
        }
        List<Core> children = new ArrayList<>();
        Core.forEachChild(e, children::add);
        if (children.isEmpty()) {
            return leafOf(e, at, reading);
        }
        return new Composed<>(partsOf(children, at, reading, following));
    }

    /** What an attempted construction may come to: the value it builds, and every departure. */
    private static List<Core> armsOf(Core.IfConstructed attempt) {
        List<Core> out = new ArrayList<>();
        out.add(attempt.then());
        for (Core.ElseArm arm : attempt.els()) {
            out.add(arm.body());
        }
        return out;
    }

    /** What a match may come to: what each of its arms answers. */
    private static List<Core> armsOf(Core.Match match) {
        List<Core> out = new ArrayList<>(match.cases().size());
        for (Core.Case each : match.cases()) {
            out.add(each.body());
        }
        return out;
    }

    private static <K, E> List<ValueOrigin<K>> partsOf(List<Core> of, E at, Reading<K, E> reading,
                                                       Set<souther.compiler.types.BindingId> following) {
        List<ValueOrigin<K>> out = new ArrayList<>();
        for (Core each : of) {
            out.add(of(each, at, reading, following));
        }
        return out;
    }

    /**
     * Whether {@code e} is a value written where it stands.
     *
     * <p>Every one of them and not the four with a number or a string in them. A temporal is a
     * literal by {@link Core}'s own account, and a data with no fields is a value written where a
     * value goes; left out, each was a node this could say nothing about — which reads the same as
     * a value it could not reach, and they are not the same thing.
     */
    private static boolean writtenOut(Core e) {
        return e instanceof Core.Int || e instanceof Core.Decimal || e instanceof Core.Str
                || e instanceof Core.Bool || e instanceof Core.Temporal
                || e instanceof Core.UnitValue;
    }

    /** What a node nothing composes comes to: where its value came from, or nothing said. */
    private static <K, E> ValueOrigin<K> leafOf(Core e, E at, Reading<K, E> reading) {
        K from = reading.madeFrom(e, at);
        return from == null ? new Unnameable<>() : new MadeFromAPosition<>(from);
    }

}
