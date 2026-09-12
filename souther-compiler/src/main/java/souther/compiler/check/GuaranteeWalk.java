package souther.compiler.check;

import souther.compiler.core.Core;
import souther.compiler.types.Type;
import souther.compiler.types.TypeSymbol;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * A reader taken over the names readable off a value, told what each of them guarantees.
 *
 * <p>Mechanism and not meaning. What a declaration says is {@link TypeGuarantees}' answer and this
 * asks for it a value at a time; what this owns is getting to the values — how deep to go, which
 * names were to be left out, and stopping where a record holds another of its own kind. Two readers
 * may walk to different depths and still be reading one model, which is the whole point of keeping
 * the two apart: a scope changes which values were visited, never what a declaration states at one.
 *
 * <p>Where a name followed is written down is {@link Location#isStep}'s answer and not a rule of
 * this walk. A newtype's {@code value} is the same value under a name, so the path a walk carries
 * is unchanged by following it, and every other name is one step further in.
 *
 * <p>Nothing here decides what a stop costs. A reader is told where the walk stopped and answers
 * that for itself, because the same stop means different things to different readers: a measurement
 * that has to account for every rule owes a line for what it did not read, and a reading that only
 * wants the relations it can state is short of nothing.
 */
final class GuaranteeWalk {

    private final TypeGuarantees guarantees;

    /** Asked whether following a name reaches somewhere else, which is what says where a name this
     *  walk followed is written down. How the declaration was written is the whole of what that
     *  turns on, so it is what this holds rather than the declarations themselves. */
    private final DeclarationNewtypes newtypes;

    GuaranteeWalk(TypeGuarantees guarantees, DeclarationNewtypes newtypes) {
        this.guarantees = guarantees;
        this.newtypes = newtypes;
    }

    /**
     * How far into a value's fields a walk over a body can afford to read.
     *
     * <p>A type's own invariant is what its fields guarantee, and a field's type carries its own;
     * past a couple of levels what a clause could be read against is a value the body would have had
     * to name, and it names it by reading it.
     *
     * <p>A cost bound and not a rule of the model: a rule four records down refuses the outermost
     * construction exactly as one on its own fields does. What a construction has to satisfy has no
     * depth at all, and a reader answering for that asks for one that has none.
     */
    static final int FIELDS_SEEDED = 2;

    /**
     * How far a reader is asking this walk to go.
     *
     * <p>The last two are asked of different things, and are different types for that reason. A
     * name supposed to hold values is a declaration; a rule left out is a rule, and one a spread
     * carries in was written where it was written whatever value it is read at.
     *
     * @param extent          how far down to read
     * @param stopAt          names this reader is supposing hold values whatever is written under
     *                        them, so nothing under one of them is read
     * @param withoutClauses  rules this reader asked to leave out, what is under the declarations
     *                        that wrote them still being read
     */
    record Scope(Extent extent, Predicate<TypeSymbol> stopAt, RulesLeftOut withoutClauses,
                 PartsLeftOut withoutParts) {

        /** Every rule down to {@code names} names followed, wherever it is written. */
        static Scope asFarAs(int names) {
            return new Scope(new Extent.AsFarAs(names), _ -> false, RulesLeftOut.NONE,
                    PartsLeftOut.NONE);
        }

        /** Every rule the model writes under this value. */
        static Scope everyName() {
            return new Scope(new Extent.EveryName(), _ -> false, RulesLeftOut.NONE,
                    PartsLeftOut.NONE);
        }
    }

    /**
     * How far down a reader reads.
     *
     * <p>Two answers and not one number. A reader that cannot afford the whole value states what it
     * can afford; a reader whose question has no depth in it says so. Held as a number for both,
     * the second has to invent one — and the numbers that get invented are a bound belonging to some
     * other question, or a sentinel standing for "not applicable" inside a type that cannot say it.
     * Either way the depth a reader could afford becomes part of what a declaration is taken to say.
     */
    sealed interface Extent {

        /** Whether a value {@code down} names from the root is one this reader reads. */
        boolean reaches(int down);

        /**
         * As far as the model goes.
         *
         * <p>What a value guarantees has no depth in it: a rule four records down refuses the
         * outermost value exactly as one on the top does, and a reading that stopped short would
         * make what a declaration says depend on how deeply an author nested a field. Terminating
         * because a name met on the way down is not entered again, which is a fact about the type
         * graph and not a budget.
         */
        record EveryName() implements Extent {

            @Override
            public boolean reaches(int down) {
                return true;
            }
        }

        /**
         * As far as {@code names} names down, and no further.
         *
         * <p>A cost bound, and only ever that. A reader states one where reading further is work it
         * cannot afford — never where the question it is asking runs deeper than it wants to go.
         */
        record AsFarAs(int names) implements Extent {

            @Override
            public boolean reaches(int down) {
                return down <= names;
            }
        }
    }

    /** Told what was read, what another reading answers for, and where this walk stopped. */
    interface Reader {

        /** What the declaration at {@code path} guarantees of the value there. */
        void guaranteed(RuleKey path, TypeGuarantee guarantee);

        /** Where the walk went no further, and what stood there. */
        default void stopped(RuleKey path, Type type, Stop why) {}

        /**
         * That rules stand under {@code path} which no reading here takes in, and which a reading
         * opened elsewhere answers for — the cases of a sum, what a container holds.
         *
         * <p>Not a stop. The value was read and what it states was heard; this says only that
         * something below it belongs to somebody else. Reported as a stop, a sum whose cases share a
         * spread would have to be either read or handed on, and it is both.
         */
        default void handedOn(RuleKey path, Type type) {}

        /** That a declaration at {@code path} writes these clauses and this reading could not state
         * them, so what they were about is not among what was handed over. */
        default void lostAClause(RuleKey path, List<RuleRef.Invariant> lost) {}
    }

    /**
     * Why a walk went no further.
     *
     * <p>Every one of them is this walk's own doing. A value it did not enter because nothing there
     * belongs to it is not a stop at all — that is {@link Reader#handedOn}, and it is answered by
     * the reading rather than by the walk. Held here as a stop, a value that both states rules and
     * leaves something below to another reading could only be one of the two.
     */
    enum Stop {

        /** As far down as the reader asked to be taken. Not a limit on the model: a rule four
         * records down refuses the outermost construction exactly as one on its own fields does. */
        PAST_THE_DEPTH,

        /** A name the reader is supposing holds values, so what is under it says nothing here. */
        ASKED_TO_STOP,

        /** Met already on the way down, and read where it was met. A record holding one of its own
         * kind stops here and nothing is short of anything for it. */
        ALREADY_ENTERED
    }

    /**
     * Take {@code reader} over {@code root} and what is readable off it.
     *
     * <p>{@code path} names where {@code root} itself stands, and everything reached is named
     * relative to it.
     */
    void from(Core root, RuleKey path, Denotations at, Scope scope, Reader reader) {
        walk(root, path, at, 0, scope, new HashSet<>(), reader);
    }

    private void walk(Core root, RuleKey path, Denotations at, int depth, Scope scope,
                      Set<TypeSymbol> entered, Reader reader) {
        // Asked one at a time, because a stop says two things and only one of them is the same for
        // all of these: whether the rules under it were read, and whether a construction could have
        // got out of making the value they are about.
        if (!scope.extent().reaches(depth)) {
            reader.stopped(path, root.type(), Stop.PAST_THE_DEPTH);
            return;
        }
        // The reading first, because what stands here is what this walk's own limits are about: the
        // name it was told to stop at and the name it has already entered are the declaration's, and
        // asking the type for one beside the reading is a second answer to that.
        TypeGuarantees.At here = guarantees.at(root, at, scope.withoutParts());
        TypeSymbol name = here.entered();
        if (name != null) {
            if (scope.stopAt().test(name)) {
                reader.stopped(path, root.type(), Stop.ASKED_TO_STOP);
                return;
            }
            // A name already entered was read where it was met, so reading it again would be the
            // same reading done twice — which costs, and which a reader that remembers what it was
            // asked would see twice.
            if (entered.contains(name)) {
                reader.stopped(path, root.type(), Stop.ALREADY_ENTERED);
                return;
            }
        }
        // What the declarations state is read whatever this walk was asked for; which of them this
        // reader hears is the scope's answer, asked of each rule. A rule the reader asked to leave
        // out is not one it was asked to lose, so nothing is reported lost for it either — and the
        // rules read here need not have been written on the declaration standing here, which is why
        // this is not one question about the value.
        List<RuleRef.Invariant> lost = new ArrayList<>();
        if (here.coverage() instanceof TypeGuarantees.At.Coverage.Incomplete incomplete) {
            for (RuleRef.Invariant each : incomplete.lost()) {
                if (!scope.withoutClauses().excludes(each)) {
                    lost.add(each);
                }
            }
        }
        if (!lost.isEmpty()) {
            reader.lostAClause(path, lost);
        }
        for (TypeGuarantee guarantee : here.here()) {
            if (!scope.withoutClauses().excludes(guarantee.rule())) {
                reader.guaranteed(path, guarantee);
            }
        }
        // Said whether or not anything was read here, and never instead of it. Both are true of a
        // sum whose cases share a spread.
        if (here.handedOn() instanceof TypeGuarantees.At.HandedOn.ToAnotherReading) {
            reader.handedOn(path, root.type());
        }
        // Entered before anything under it is walked, so that the one name and the other stay
        // paired: a stop taken after entering would leave the name on the path with nothing to take
        // it off, and the next field of the same type would be passed over as one already read.
        if (name != null) {
            entered.add(name);
        }
        for (TypeGuarantees.At.Readable under : here.readable()) {
            // Whether following the name reaches somewhere else is `Location.isStep`'s answer,
            // asked here because here is where the name is written down. A newtype's `value` is
            // this same value under a name, so a walk into one keeps the path it came with.
            RuleKey there = Location.isStep(root.type(), under.name(), newtypes)
                    ? path.then(under.name()) : path;
            walk(under.value(), there, at, depth + 1, scope, entered, reader);
        }
        if (name != null) {
            entered.remove(name);
        }
    }

}
