package souther.compiler.regex;

/**
 * What would be built out of some patterns, said before anything is made of it.
 *
 * <p>A machine as it would be written and not the machine: the patterns it names, met and joined
 * and complemented the way it says. Which is what lets a caller hold what a rule comes to without
 * paying for it — a rule stating a pattern is one of these, and whether a machine is ever made of it
 * is settled by what the position it is about finally admits.
 *
 * <p>Compared as what it says. Two of these are the same plan where they name the same patterns
 * under the same steps, and never by what they accept: telling two spellings of one language apart
 * means building both, which is the work a plan exists to arrange rather than to do.
 *
 * <p>Built under a meter and not under a promise. What comes back is either a {@link Language} that
 * was made within what the meter allowed, or nothing — and which of the meter's two limits refused
 * it is the meter's to say ({@link Meter#stoppedBy}), because a machine larger than a machine may be
 * is a fact about the pattern and an allowance run down is not.
 */
public final class PatternPlan {

    /**
     * How much a plan is allowed.
     *
     * <p>States, because that is what everything here costs: a pattern is its states, a meet is the
     * product of two, and what a walk over any of them takes is bounded by them. Counted over the
     * whole of the plan and not per pattern — a plan is admitted as a whole or not at all, so what
     * it is charged is everything it builds.
     *
     * @param mostStates the largest machine a step of the plan may make
     * @param mostBuilt  how many states the plan may make in all, the intermediate ones counted.
     *                   Beside the first because a plan of many small meets is affordable at each
     *                   step and not as a whole
     */
    public record Budget(int mostStates, int mostBuilt) {

        public Budget {
            if (mostStates <= 0 || mostBuilt <= 0) {
                throw new IllegalArgumentException("a budget allows something");
            }
        }

        /** A meter that allows this much and has spent none of it. */
        public Meter meter() {
            return new Meter(mostStates, mostBuilt);
        }

        /**
         * What the values one position finally admits are allowed to cost.
         *
         * <p>The whole of that and not one rule of it. Every rule about a position pays into the
         * same machine — two patterns of one clause meet, and so do two written in separate rules of
         * one declaration — so an allowance per rule would be one nobody could hold the product to.
         *
         * <p>Large enough for the formats a model writes — the longest of them is a couple of dozen
         * characters of classes and counts, which is a few hundred states — and small enough that a
         * pattern nobody meant to write is refused rather than waited for. What it is not is a
         * measurement of anything: it is the size past which this compiler would rather say it did
         * not answer.
         */
        public static final Budget OF_ADMITTED_VALUES = new Budget(50_000, 200_000);

        /**
         * What handing each of a position's rules on as the set it admits is allowed to cost.
         *
         * <p>Its own and not the one above, because the two are about different sets. That one is
         * what a position finally admits, which is every rule of it met together; this is what each
         * of them admits on its own, which a reading promises to whoever draws lines and offers
         * rows. A rule met with its neighbours can be settled without its own machine — a pattern
         * beside a value the rules write out is a question about that value — so the sets handed on
         * are not the sets the answer needed, and charging them to the answer would let what a
         * position is read to admit turn on what somebody downstream was promised.
         *
         * <p>Per position and spent on the whole of it, for the reason the first is: the sets are
         * published as a group or not at all, so an allowance per rule would be one nobody could
         * hold the group to.
         *
         * <p>A machine that already exists is not made again out of this. What the answer built is
         * read where it was built, and what is charged here is only what the answer had no use for.
         *
         * <p>The same numbers as the others today, and a coincidence rather than a fact.
         */
        public static final Budget OF_WHAT_A_RULE_LEAVES = new Budget(50_000, 200_000);

        /**
         * What composing one value somebody can write into a row is allowed to cost.
         *
         * <p>Its own and not the one above, because it bounds a different thing: what a caller here
         * is after is a string for a row and never an answer about a position. So nothing about how
         * a model is read turns on this number: what running out costs is a value to paste, and a
         * position is read to admit what it admits either way.
         *
         * <p><b>What it costs is said as itself, and not as a pattern nothing read.</b> Both leave
         * the row composed out of the rules beside the one that gave nothing, and there they part:
         * an author meeting this raises a figure or writes a smaller machine, and one meeting an
         * unreadable rule has a rule to rewrite. Reported alike, half of them are sent to look at
         * something that is not the matter.
         *
         * <p><b>The whole of what arriving at that string costs, and not one pattern.</b> Which
         * strings a class is left is what the position admits met with the run of the order it is
         * bounded to, less the values a body singled out, and each of those is a machine. Read as
         * one pattern taken a string out of, the figure would be tuned for the cheapest of the
         * questions it actually pays for.
         *
         * <p>The same numbers today, and that is a coincidence rather than a fact. Written as one
         * constant, the day either question wants a different size the other would move with it.
         */
        public static final Budget OF_A_WITNESS = new Budget(50_000, 200_000);

        /**
         * What working out where one rule's strings stop on the order is allowed to cost.
         *
         * <p>One rule's and not one position's. What is being asked is where the strings a single
         * conjunct admits begin and end, and the answer is a line that conjunct drew — so a rule
         * whose language is expensive taking the allowance the rule beside it needed would leave a
         * line standing or going by the order the two happened to be read in.
         *
         * <p>Apart from what the values cost for the same reason {@link #OF_A_WITNESS} is: this is
         * a question asked to write a report, and paying for it out of what the position's own
         * answer is allowed would let a diagnostic decide what the model is read to admit.
         *
         * <p>The same numbers as the two above, and a coincidence rather than a fact.
         */
        public static final Budget OF_AN_ORDERED_EXTENT = new Budget(50_000, 200_000);

        /**
         * What deciding whether a set of values and a range on an order share one is allowed to
         * cost.
         *
         * <p>Its own, and here rather than out of what the position's answer is allowed, because
         * this is what decides whether a declaration has a value. Spent from the position's
         * allowance, the same rules would be decided differently depending on what the readings
         * before them had already built — so a model would be accepted or refused by the order its
         * clauses were walked in.
         *
         * <p>The same numbers as the rest, and a coincidence rather than a fact.
         */
        public static final Budget OF_WHAT_A_SET_AND_A_RANGE_SHARE = new Budget(50_000, 200_000);

        /**
         * What working out the classes a behavior's rules about the strings at one position divide
         * it into is allowed to cost.
         *
         * <p>Its own and not {@link #OF_WHAT_A_RULE_LEAVES}, and the two are told apart by what
         * moving one would do to the other's answer. That one bounds how exactly a declaration's
         * rules are handed on, and this one bounds which classes a behavior draws; a model whose
         * type states a plain rule and whose body states an expensive one must not have the second
         * become answerable because the first was allowed more, and a model the other way round
         * must not have what its declaration is read to admit turn on how much its body was allowed
         * to distinguish. Held as one figure, each of those is what raising it does.
         *
         * <p>Per position and spent on the whole of it. A rule about the strings there divides them
         * from the rest, so what is built is the set it admits and the set it leaves, and the
         * classes are the cells every such rule together leaves — which is one answer, made or not
         * made. An allowance per rule would let the rule read first take what the rule beside it
         * needed, and the position would come back divided one way or the other by the order the
         * body happened to be walked in.
         *
         * <p>A machine that already exists is not made again out of this. What the values a
         * position admits were answered with is read where it was built, and only what that answer
         * had no use for is charged here — which is what borrowing is, and it is not the two
         * questions sharing an allowance: a position whose own answer stopped short still has its
         * body's distinctions read, because what a behavior tells apart is not a projection of what
         * the position admits.
         *
         * <p>The same numbers as the others today, and a coincidence rather than a fact.
         */
        public static final Budget OF_BEHAVIOR_DISTINCTIONS = new Budget(50_000, 200_000);
    }

    /** What one step of a plan does. */
    private sealed interface Step {

        /** A pattern, as the strings it accepts. */
        record Of(PatternSyntax syntax) implements Step {}

        /** The strings two steps both hold. */
        record Both(Step one, Step other) implements Step {}

        /** The strings either of them holds. */
        record Either(Step one, Step other) implements Step {}

        /** The strings one holds and the other does not. */
        record Less(Step one, Step other) implements Step {}
    }

    private final Step step;

    private PatternPlan(Step step) {
        this.step = step;
    }

    /** The plan that is one pattern. */
    public static PatternPlan of(PatternSyntax syntax) {
        if (syntax == null) {
            throw new IllegalArgumentException("a plan is of some pattern");
        }
        return new PatternPlan(new Step.Of(syntax));
    }

    /**
     * The plan for every string {@code syntax} does not accept.
     *
     * <p>Said as a plan rather than worked out by complementing a language afterwards, because the
     * complement is the expensive operation — a machine has to be made deterministic before a walk
     * over it can be turned around — and a plan is what says which spending is worth doing.
     *
     * <p>Every string is written as the symbols and not as a dot, which leaves out the five line
     * terminators: a denial that admitted every string but those would refuse values a model may
     * hold.
     */
    public static PatternPlan notMatching(PatternSyntax syntax) {
        return of(new PatternSyntax.Repeated(
                new PatternSyntax.Symbols(CodePoints.EVERYTHING),
                0, PatternSyntax.Repeated.NO_CEILING)).less(of(syntax));
    }

    /** The plan for what both hold. */
    public PatternPlan and(PatternPlan other) {
        return new PatternPlan(new Step.Both(step, other.step));
    }

    /** The plan for what either holds. */
    public PatternPlan or(PatternPlan other) {
        return new PatternPlan(new Step.Either(step, other.step));
    }

    /** The plan for what this holds and that does not. */
    public PatternPlan less(PatternPlan other) {
        return new PatternPlan(new Step.Less(step, other.step));
    }

    /**
     * Two plans are the same plan where they say the same steps over the same patterns.
     *
     * <p>What is compared is what would be built and not what it would come to. Two spellings of
     * one language are two plans here, which is what they are to whoever has to build them: telling
     * them apart by their strings means making both machines, which is the work a plan exists to
     * arrange rather than to do.
     */
    @Override
    public boolean equals(Object other) {
        return this == other || (other instanceof PatternPlan it && step.equals(it.step));
    }

    @Override
    public int hashCode() {
        return step.hashCode();
    }

    /**
     * How many states building this would take, as far as its shape says.
     *
     * <p>For a caller deciding which of several plans to work out first. Read off the shape and
     * never off the strings: which of two patterns makes the smaller machine is a question about
     * the machines, and asking it means building both. What a repetition written {@code {300}} says
     * about itself is enough — three hundred copies of something is three hundred times what that
     * costs — and that is what tells a large pattern from a small one.
     *
     * <p>Never more than {@link #ENOUGH}, so that a repetition of a repetition of a repetition is a
     * large number rather than one that has gone round.
     */
    public long states() {
        return states(step);
    }

    /** More than anything this compiler builds, which is where a count stops climbing. */
    private static final long ENOUGH = 999_999_999L;

    private static long states(Step step) {
        return switch (step) {
            case Step.Of it -> states(it.syntax());
            case Step.Both it -> both(states(it.one()), states(it.other()));
            case Step.Either it -> Math.min(ENOUGH, states(it.one()) + states(it.other()) + 2);
            // The complement has to make the machine deterministic first, which is the one step
            // whose cost is not about the size of what it started from.
            case Step.Less it -> both(states(it.one()), Math.min(ENOUGH, states(it.other()) * 2));
        };
    }

    private static long both(long one, long other) {
        return one > ENOUGH / Math.max(1, other) ? ENOUGH : one * other;
    }

    private static long states(PatternSyntax syntax) {
        return switch (syntax) {
            case PatternSyntax.Nothing _, PatternSyntax.Never _, PatternSyntax.Anchor _ -> 0;
            case PatternSyntax.Symbols _ -> 1;
            case PatternSyntax.InTurn it -> {
                long out = 0;
                for (PatternSyntax each : it.parts()) {
                    out = Math.min(ENOUGH, out + states(each));
                }
                yield out;
            }
            case PatternSyntax.EitherOf it -> {
                long out = 1;
                for (PatternSyntax each : it.arms()) {
                    out = Math.min(ENOUGH, out + states(each) + 1);
                }
                yield out;
            }
            // The copies it is written out as: the floor, and one more where the ceiling is open.
            case PatternSyntax.Repeated it -> {
                long once = states(it.what());
                long many = it.unbounded() ? it.least() + 1L : it.most();
                yield once > ENOUGH / Math.max(1, many) ? ENOUGH : once * many + 1;
            }
        };
    }

    /**
     * The whole of what this plan says, written out for a caller putting several of them in an
     * order.
     *
     * <p>Its own, because the steps are its own: nothing outside sees them, and an order read off
     * something else would be an order over a part of what a plan is.
     */
    public void writtenInto(StringBuilder out) {
        written(step, out);
    }

    private static void written(Step step, StringBuilder out) {
        switch (step) {
            case Step.Of it -> {
                out.append("0;");
                written(it.syntax(), out);
            }
            case Step.Both it -> {
                out.append("1;");
                written(it.one(), out);
                written(it.other(), out);
            }
            case Step.Either it -> {
                out.append("2;");
                written(it.one(), out);
                written(it.other(), out);
            }
            case Step.Less it -> {
                out.append("3;");
                written(it.one(), out);
                written(it.other(), out);
            }
        }
    }

    private static void written(PatternSyntax syntax, StringBuilder out) {
        switch (syntax) {
            case PatternSyntax.Nothing _ -> out.append("0;");
            case PatternSyntax.Never _ -> out.append("1;");
            case PatternSyntax.Anchor it -> out.append("2;").append(it.end() ? '$' : '^')
                    .append(';');
            case PatternSyntax.Symbols it -> {
                out.append("3;").append(it.held().ranges().size()).append(';');
                it.held().ranges().forEach(each ->
                        out.append(each.from()).append('-').append(each.to()).append(','));
                out.append(';');
            }
            case PatternSyntax.InTurn it -> {
                out.append("4;").append(it.parts().size()).append(';');
                it.parts().forEach(each -> written(each, out));
            }
            // The arms as they are written. A choice accepts what its arms accept in any order, and
            // which order that is cannot be asked without building them.
            case PatternSyntax.EitherOf it -> {
                out.append("5;").append(it.arms().size()).append(';');
                it.arms().forEach(each -> written(each, out));
            }
            case PatternSyntax.Repeated it -> {
                out.append("6;").append(it.least()).append(',').append(it.most()).append(';');
                written(it.what(), out);
            }
        }
    }

    /**
     * The language this plan comes to, spending {@code meter}, or null where building it would
     * cost more than the meter has left.
     *
     * <p>Null and never a smaller language. What a plan says is which strings the answer is about,
     * and a language of fewer states is a different set — handed one, a reader would be measuring a
     * model against something this compiler made up because the real answer was expensive.
     *
     * <p>Everything is built here, the intermediate machines among them. A caller holding what comes
     * back may ask it anything, because the asking is what has already been paid for.
     *
     * <p>A meter and never a budget. A budget is an allowance somebody was granted and a meter is
     * what is left of one, so a caller here is spending an allowance that exists rather than
     * minting itself a fresh one at the moment of asking — which is what makes what a question
     * costs a fact about the question and not about how many times it was asked. Whose allowance
     * each of them is, is written where the budgets are ({@link Budget}).
     *
     * <p>What is counted is what was made, and it is counted where it is made ({@link Meter}). This
     * used to work out what each step would cost from the sizes of its operands, which is a guess
     * about an implementation: a product is at most the two multiplied and is usually far less, so
     * the guess refused answers this could afford and charged for states nobody built.
     */
    public Language compile(Meter meter) {
        // A construction is beginning. The meter is the position's whole allowance and outlives any
        // one of these, so what refused an earlier build is not what refused this.
        meter.starting();
        // Made canonical here and not by whoever holds it. A language is the one machine that
        // accepts its strings, and turning a machine into that one is the largest thing this does —
        // left to the reader, it would happen inside whichever question was asked first, where
        // there is no allowance and nobody counting.
        return Language.canonical(built(step, meter), meter);
    }

    /**
     * One step, or null where it ran past what the meter allows.
     *
     * <p><b>The first refusal stops the whole of it.</b> Every step asks for its operands one at a
     * time and gives up on the first that comes back with nothing. Written as one expression over
     * both, the second operand was built after the first had been refused — states made for a step
     * that was never going to happen, taken out of the same allowance the rest of the answer draws
     * on, and a second refusal to be told about instead of the one that stopped it.
     *
     * <p>No {@code default}: a step added and not built stops the compile rather than arriving here
     * as whichever operation is nearest.
     */
    private static Automaton built(Step step, Meter meter) {
        return switch (step) {
            case Step.Of it -> Automaton.of(it.syntax(), meter);
            case Step.Both it -> {
                Automaton one = built(it.one(), meter);
                Automaton other = one == null ? null : built(it.other(), meter);
                yield other == null ? null : one.and(other, meter);
            }
            case Step.Either it -> {
                Automaton one = built(it.one(), meter);
                Automaton other = one == null ? null : built(it.other(), meter);
                yield other == null ? null : one.or(other, meter);
            }
            // What one holds and the other does not, which is the one step that has to make a
            // machine deterministic. The complement is a machine and its states are what it costs,
            // counted as they are made like everything else.
            case Step.Less it -> {
                Automaton mine = built(it.one(), meter);
                Automaton theirs = mine == null ? null : built(it.other(), meter);
                Automaton left = theirs == null ? null : theirs.not(meter);
                yield left == null ? null : mine.and(left, meter);
            }
        };
    }
}
