package souther.bench;

import souther.compiler.check.ChoicesRead;
import souther.compiler.meta.ModulePath;
import souther.compiler.query.Compilation;

import java.util.ArrayList;
import java.util.List;

/**
 * What settling a choice costs, and what makes it cost that.
 *
 * <p>Generated rather than carried, for the reason {@link Scale} and {@link Values} are: this asks
 * how a cost grows with a shape, and a shape's question is answered by holding everything else still
 * and varying the one thing. The corpora answer the other question — what a model somebody wrote
 * costs — and neither can be made to answer both.
 *
 * <p><b>Every point carries what it must reach.</b> A measurement of a path nothing arrives at
 * reports a number and answers nothing, and the two are indistinguishable in a report. So what is
 * timed is not a source but a {@link Point}: the source, and the {@link Claim} that says which
 * reading the figure would be about. The measurement times the points and a test walks the same
 * ones ({@code EveryChoiceMeasurementReachesTheReadingItIsAboutTest}), so a point cannot be timed
 * without being held to arriving, and a series added here is checked by having been added.
 *
 * <p><b>Alternatives are not the whole of what a choice costs.</b> A conjunction distributes over a
 * choice ({@code StatedTogether.meet}), so a branch stands in as many places as the clauses met with
 * it put it, and a fate is aggregated back over every one of them. Two declarations can expand to
 * the same number of alternatives and pay differently for them, which is why the expansion is
 * measured two ways rather than one:
 *
 * <pre>
 *   wide   n alternatives at one position, as n-1 written choices, none met with another
 *   deep   2^k alternatives, as k written choices of two, each met with all the others
 * </pre>
 *
 * <p>The two are read against each other at the same n, where the settlement walks the same number
 * of places either way: a balanced tree of n alternatives has n-1 nodes, and k choices met together
 * distribute into 2^k-1. What differs is how often one written choice is the choice at one of those
 * places — once in the wide shape and 2^(k-1) times in the deep one — so the deep shape aggregates
 * a fate over occurrences where the wide one has nothing to aggregate.
 *
 * <p>What the gap between the lines is evidence of is that, and not of that alone. The two sources
 * are not the same size: one field and one clause against k of each, so what the front end does with
 * them differs too. What the pair shows is that the same expansion, settled at the same number of
 * places, comes to different times — which a series varying the alternatives alone cannot show at
 * all, and which is what makes the alternatives the wrong thing to read a cost against.
 *
 * <p><b>Written flat, a wide choice is not writable.</b> Its alternatives nest one per alternative
 * and the parser refuses the source past {@code CstParser.MAX_DEPTH} — a limit on the shape a source
 * may have, which is a different question from how much combinatorial work an analysis will take on.
 * So the alternatives here are bracketed into a balanced tree, and what that shows is that a source
 * shallow enough for anybody to write can still expand as far as the reading will hold apart.
 *
 * <p>The boundary series is where the reading stops holding alternatives apart and merges them into
 * the one product containing them. Measured on the wide shape and one alternative either side,
 * which is the smallest change to a source that crosses the policy's boundary: an alternative more,
 * and the reading answers the other way. Taken on the deep shape, the step to the next size doubles
 * the alternatives as well, and the pair would be showing two changes at once.
 */
final class Choices {

    private Choices() {}

    /** Doubling, so the ratio between two lines names the exponent. The last is past the limit a
     *  compilation holds apart, and is the fallback rather than a wider reading. */
    private static final int[] ALTERNATIVES = {1, 2, 4, 8, 16, 32, 64, 128};

    /** Either side of the limit and the limit itself. */
    private static final int[] BOUNDARY = {63, 64, 65};

    /** Clauses met with one choice of two, doubling from none. */
    private static final int[] CONJUNCTS = {0, 1, 2, 4, 8};

    /** Choices the fated one is met with. The largest the reading still holds apart, because what
     *  separates the fates is how much distribution a dead alternative takes out of the walk, and
     *  there is least of that to take at the small end. */
    private static final int FATED_CHOICES = 6;

    /** The most alternatives a compilation holds apart, which the boundary series is written
     *  around. Held to by the two points either side of it rather than read from the policy: what
     *  the series is for is the step, and a series that took the limit from the same place the
     *  reading does would step wherever the reading did and never say so. */
    private static final int HELD_APART = 64;

    /**
     * What one point must reach for its figure to be about what the point says it is about.
     *
     * <p>Beside the source rather than in a test of its own. A claim written where the tests are is
     * a second statement of what a point measures, and the two agree until somebody changes one.
     */
    interface Claim {

        /** Why {@code read} is not the reading this point measures, or null where it is. */
        String unmetBy(ChoicesRead.Snapshot read);
    }

    /**
     * What a point's compile comes to, which is the other half of what its figure is worth.
     *
     * <p>Beside the claim and not folded into it. What a run reached is about the run; what a
     * source comes to is about the source, and a figure taken over a compile that stopped early is
     * a smaller number for a reason that has nothing to do with what the point varies. Held by
     * {@code EveryShapeThatIsTimedStillCompilesTest} with the other shapes that are timed.
     */
    enum Completion {

        /** Compiles with nothing to say and reaches the back end, as the timed shapes do. */
        MAKES_CLASSES,

        /** Is refused, and so leaves the back end nothing to do. */
        IS_REFUSED
    }

    /**
     * One thing that is timed: what is compiled, what the line calls it, what it must reach, and
     * what its compile comes to.
     *
     * @param alternatives what to divide the figure by, or nought where the series has no such
     *                     figure. Reported beside the total rather than instead of it, because past
     *                     the limit the two say different things — the alternatives are counted off
     *                     the source either way, and a reading that merged them did not hold the
     *                     number the figure is divided by
     */
    record Point(String series, String label, String source, Claim claim, int alternatives,
                 Completion completion) {}

    /** Rounds a point is warmed for, and rounds its figure is the median of. */
    private static final int WARMUP = 3;
    private static final int MEASURED = 5;

    static void measure(Report report) {
        for (Point point : points()) {
            Timing timing = time(point, WARMUP, MEASURED).figure();
            // A refused compile leaves the back end nothing to do, so its figure is smaller for a
            // reason the point does not vary. Said on the line, because a column of times is read
            // against itself and nothing else about a line says it.
            String said = point.completion() == Completion.IS_REFUSED
                    ? "  (a refused compile: not read against the lines above)" : "";
            if (point.alternatives() > 0) {
                report.line("CHOICE %-16s %-14s %7.1f ms (%6.3f ms/alternative)%s",
                        point.series(), point.label(), timing.medianMillis(),
                        timing.medianMillis() / point.alternatives(), said);
            } else {
                report.line("CHOICE %-16s %-14s %7.1f ms%s",
                        point.series(), point.label(), timing.medianMillis(), said);
            }
        }
    }

    /**
     * One point run: its figure, and what the runs the figure was taken over read.
     *
     * <p>The one way a point is run. A reader wanting only the figure takes the figure and a reader
     * wanting only the reading takes the reading, and neither has a compile of its own to make — so
     * what is held to arriving is what is timed, down to which questions were asked of the store.
     *
     * <p>The round counts are the caller's because they are the one thing a reader of the reading
     * does not need what the figure needs. A figure wants enough rounds for the JIT to settle; a
     * reading is the same reading after one round as after forty, since how long a store has been
     * running does not change which declarations a source has.
     */
    static Taken<Timing> time(Point point, int warmup, int measured) {
        return Taken.from(measuring ->
                Timing.of(warmup, measured, () -> compile(point.source()), measuring));
    }

    /** Every point this measurement times, in the order it times them. */
    static List<Point> points() {
        List<Point> points = new ArrayList<>();
        for (int alternatives : ALTERNATIVES) {
            points.add(new Point("expansion wide", "n=" + alternatives, wide(alternatives),
                    claimOfWide(alternatives), alternatives, Completion.MAKES_CLASSES));
        }
        for (int alternatives : ALTERNATIVES) {
            int choices = Integer.numberOfTrailingZeros(alternatives);
            points.add(new Point("expansion deep", "n=" + alternatives, deep(choices),
                    claimOfDeep(choices, alternatives), alternatives, Completion.MAKES_CLASSES));
        }
        for (int alternatives : BOUNDARY) {
            points.add(new Point("boundary", "n=" + alternatives, wide(alternatives),
                    claimOfWide(alternatives), alternatives, Completion.MAKES_CLASSES));
        }
        for (int conjuncts : CONJUNCTS) {
            points.add(new Point("distributed into", "conjuncts=" + conjuncts, conjuncts(conjuncts),
                    multipliesNothing(), 0, Completion.MAKES_CLASSES));
        }
        for (Fate fate : Fate.values()) {
            points.add(new Point("fate", fate.written(), fate.source(FATED_CHOICES),
                    reaches(fate, FATED_CHOICES), 0, fate.completion()));
        }
        return points;
    }

    /** A wide shape states one fewer choice than it has alternatives, and one of them states
     *  none. Past the limit it is the fallback, however it got there. */
    private static Claim claimOfWide(int alternatives) {
        if (alternatives == 1) {
            return statesNoChoice();
        }
        return alternatives > HELD_APART ? merges() : holdsApart(alternatives);
    }

    private static Claim claimOfDeep(int choices, int alternatives) {
        if (choices == 0) {
            return statesNoChoice();
        }
        return alternatives > HELD_APART ? merges() : distributesInto(choices);
    }

    private static void compile(String source) {
        Compilation compilation = Compilation.ofSources(List.of(source), ModulePath.EMPTY);
        compilation.answerEverything();
        compilation.classes();
    }

    // === What the points claim ===
    //
    // None of them is a count. A declaration is read as many times as the questions put to it need
    // — its own reading, and one more for each counterfactual somebody asks — so a figure is that
    // many times what one reading did, and how many that is belongs to the callers. What is claimed
    // is what holds whatever the number is: how the figures stand to each other.

    /** The floor of a series, which is the same declaration with nothing to choose between. */
    private static Claim statesNoChoice() {
        return read -> read.stated() == 0 ? null
                : "states " + read.stated() + " choices, and a floor states none";
    }

    /** A wide shape: every alternative held apart, standing where it was written, and standing. */
    private static Claim holdsApart(int alternatives) {
        return read -> {
            if (read.stated() == 0 || read.stated() % (alternatives - 1) != 0) {
                return "states " + (alternatives - 1) + " choices in each reading of it, and this"
                        + " reading stated " + read.stated();
            }
            if (read.merged() > 0) {
                return "was merged, so the reading held none of its alternatives apart";
            }
            if (read.placesMet() != read.stated()) {
                return "put a branch in " + read.placesMet() + " places for " + read.stated()
                        + " choices, and nothing was met with it to put it anywhere else";
            }
            if (read.everyAlternativeStood() != read.stated()) {
                return "lost an alternative: " + read.everyAlternativeStood() + " of "
                        + read.stated() + " choices kept both";
            }
            return null;
        };
    }

    /** A deep shape: each branch in every place the choices met with it put it. */
    private static Claim distributesInto(int choices) {
        return read -> {
            if (read.stated() == 0 || read.stated() % choices != 0) {
                return "states " + choices + " choices in each reading of it, and this reading"
                        + " stated " + read.stated();
            }
            if (read.merged() > 0) {
                return "was merged, so nothing was distributed into anything";
            }
            // Every choice met with every other, so the tree the settlement walks is a full binary
            // one of this many choices and its nodes are the places a branch stands. Written as the
            // ratio the two figures stand in, which is the same however many readings were made.
            if (read.placesMet() * choices != read.stated() * ((1L << choices) - 1)) {
                return "put a branch in " + read.placesMet() + " places for " + read.stated()
                        + " choices, which is not every place its neighbours put one";
            }
            return null;
        };
    }

    /**
     * Past the limit: the alternatives merged, and every choice read all the same.
     *
     * <p>Two things and not one. That the guardrail was reached is what {@code merged} says, and it
     * is said where the policy decides — before a clause has been looked at, so a reading that
     * stopped anywhere after would say it just the same. That the choices were then read is the
     * other half, and it is what makes the figure a figure for reading them merged rather than for
     * whatever a reading that stopped costs.
     */
    private static Claim merges() {
        return read -> {
            if (read.merged() == 0) {
                return "held its alternatives apart, so it is not past the limit";
            }
            if (read.stated() == 0) {
                return "reached the guardrail and then read no choice at all";
            }
            if (read.settledOffDescriptions() != read.stated()) {
                return "settled " + read.settledOffDescriptions() + " of its " + read.stated()
                        + " choices off the descriptions, and a merged reading settles them all"
                        + " there";
            }
            if (read.placesMet() > 0) {
                return "distributed into " + read.placesMet() + " places, and a merged reading has"
                        + " no branches left to put anywhere";
            }
            return null;
        };
    }

    /** Clauses met with a choice, none of which multiplies where its branches stand. */
    private static Claim multipliesNothing() {
        return read -> {
            if (read.stated() == 0) {
                return "states no choice for the clauses to be met with";
            }
            if (read.placesMet() != read.stated()) {
                return "put a branch in " + read.placesMet() + " places for " + read.stated()
                        + " choices, so what it varies is not what it says it varies";
            }
            return null;
        };
    }

    /**
     * A fated shape: the fated choice came to the fate, and the ones met with it did not.
     *
     * <p>Of the head choice and not of the shape. The choices written beside it are alike and all
     * stand, and a claim that some choice of the shape came to a fate is one they answer: the head
     * could come to any fate at all and a shape written for one of them would go on passing, with
     * three lines beside each other measuring the same thing.
     *
     * <p>So what is claimed is the whole division, and the three are not one formula. A head that
     * keeps both alternatives leaves every choice of the reading standing. A head that loses one
     * leaves the other, and the choices met with what is left go on standing. A head that admits
     * nothing takes them with it: a conjunction with an empty conjunct is empty whatever stands
     * beside it, so every choice of that reading comes to the same nothing. Each is a ratio against
     * how many choices the shape states, and holds however many readings were made.
     */
    private static Claim reaches(Fate fate, int choices) {
        return read -> {
            if (read.stated() == 0 || read.stated() % choices != 0) {
                return "states " + choices + " choices in each reading of it, and this reading"
                        + " stated " + read.stated();
            }
            long readings = read.stated() / choices;
            long every = switch (fate) {
                case BOTH_STAND -> read.stated();
                case ONE_STANDS -> read.stated() - readings;
                case NONE_STANDS -> 0;
            };
            String wrong = wrongCount("every alternative stood", read.everyAlternativeStood(), every);
            if (wrong == null) {
                wrong = wrongCount("one alternative stood", read.oneAlternativeStood(),
                        fate == Fate.ONE_STANDS ? readings : 0);
            }
            if (wrong == null) {
                wrong = wrongCount("no alternative stood", read.noAlternativeStood(),
                        fate == Fate.NONE_STANDS ? read.stated() : 0);
            }
            if (wrong != null) {
                return "is written so that its head choice comes to " + fate.written()
                        + ", and " + wrong;
            }
            if (fate != Fate.NONE_STANDS && read.placesMet() == 0) {
                return "distributed into nothing, and what a fate saves is the places it is not met"
                        + " in";
            }
            return null;
        };
    }

    private static String wrongCount(String what, long came, long owed) {
        return came == owed ? null : what + " for " + came + " of its choices and not " + owed;
    }

    // === The shapes ===

    /**
     * One choice of {@code alternatives} alternatives at one position.
     *
     * <p>Bracketed into a balanced tree, which is what makes the wide shape writable at all. What
     * the reading takes in is the same either way — the alternatives are what stands between the
     * brackets — so the bracketing changes what a parser will accept and nothing a reading answers.
     */
    private static String wide(int alternatives) {
        List<String> written = new ArrayList<>();
        for (int i = 0; i < alternatives; i++) {
            written.add("a == " + i);
        }
        return """
                module choices exposing ( P )
                data P = { a: Int }
                    invariant chosen = %s
                """.formatted(balanced(written));
    }

    /** The same alternatives, bracketed as shallowly as their number allows. */
    private static String balanced(List<String> written) {
        if (written.size() == 1) {
            return written.getFirst();
        }
        int half = written.size() / 2;
        return "(" + balanced(written.subList(0, half)) + " || "
                + balanced(written.subList(half, written.size())) + ")";
    }

    /**
     * {@code choices} choices of two, one per position, met by being written beside each other.
     *
     * <p>They expand to {@code 2^choices} alternatives, and each written branch stands in half of
     * them: the clauses of a declaration are met, and a conjunction of a choice is the choice
     * between the conjunctions. At {@code choices} of nought this is the same declaration with a
     * rule that states no choice, which is the floor the rest are read against.
     */
    private static String deep(int choices) {
        StringBuilder fields = new StringBuilder();
        StringBuilder clauses = new StringBuilder();
        if (choices == 0) {
            fields.append("f0: Int");
            clauses.append("    invariant c0 = f0 == 0%n".formatted());
        }
        for (int i = 0; i < choices; i++) {
            fields.append(i == 0 ? "" : ", ").append("f").append(i).append(": Int");
            clauses.append("    invariant c%d = f%d == 0 || f%d == 1%n".formatted(i, i, i));
        }
        return """
                module choices exposing ( P )
                data P = { %s }
                %s""".formatted(fields, clauses);
    }

    /**
     * One choice of two with {@code conjuncts} clauses stating no choice met with it.
     *
     * <p>What varies is what each branch of one choice takes in, and not how many places the branch
     * stands in: a clause that states no choice multiplies nothing, so the declaration expands to
     * two however many of them are written. That is the half of distribution the expansion series
     * holds still.
     */
    private static String conjuncts(int conjuncts) {
        StringBuilder clauses = new StringBuilder("    invariant chosen = a == 0 || a == 1%n"
                .formatted());
        for (int i = 0; i < conjuncts; i++) {
            clauses.append("    invariant c%d = b >= %d%n".formatted(i, i));
        }
        return """
                module choices exposing ( P )
                data P = { a: Int, b: Int }
                %s""".formatted(clauses);
    }

    /**
     * The three fates a choice can come to, each at the head of a declaration of choices met
     * together.
     *
     * <p>Under a distribution rather than on its own. What a fate costs is not what deciding one
     * costs — every one of these is two alternatives and three equalities either way — but what the
     * decision saves the places the branch would otherwise have stood in: an alternative nobody can
     * be in is one the clauses written beside it are never met with. Measured on a declaration whose
     * one choice is the whole of it, the three come to the same figure and the series says nothing.
     *
     * <p>Held still across the three, and held still in what a reading is sensitive to rather than
     * in how much was written. Every alternative of every one of them is a pair of bounds on the
     * same one position, so the three name the same position, the same operators and the same
     * number of comparisons, and what separates them is which numbers the bounds are drawn at.
     * Written as equalities on two positions, a dead alternative also read one position fewer than
     * a live one, and what the pair showed would have been the fate and that together.
     *
     * <p><b>Two of the three are comparable and the third is not.</b> A choice admits nothing only
     * where every alternative does, so the declaration admits nothing, and a declaration nothing
     * satisfies is refused — the compile says so and the back end is left with nothing to do. Its
     * figure is therefore a compile that stopped as well as a fate that was cheap, and the two
     * cannot be told apart in it. It is kept, because the fate is one a reading reaches and a
     * measurement that reached two of three would be back where this started, and its line says
     * what it is. What is read against what is the first two.
     */
    enum Fate {

        /** Both alternatives admit something, so the choice is held open and both are distributed
         *  into. */
        BOTH_STAND("both stand", "(a >= 0 && a <= 1) || (a >= 2 && a <= 3)",
                Completion.MAKES_CLASSES),

        /** One alternative admits nothing, so the answer is the other and a proof crosses the
         *  join. */
        ONE_STANDS("one stands", "(a >= 0 && a <= 1) || (a >= 3 && a <= 2)",
                Completion.MAKES_CLASSES),

        /** No alternative admits anything, and none of them is at fault for it — which is a
         *  declaration nothing satisfies, and is refused. */
        NONE_STANDS("none stands", "(a >= 1 && a <= 0) || (a >= 3 && a <= 2)",
                Completion.IS_REFUSED);

        private final String written;
        private final String clause;
        private final Completion completion;

        Fate(String written, String clause, Completion completion) {
            this.written = written;
            this.clause = clause;
            this.completion = completion;
        }

        String written() {
            return written;
        }

        Completion completion() {
            return completion;
        }

        /** This fate at the head of {@code choices} choices, the rest of them plain and alike. */
        private String source(int choices) {
            StringBuilder fields = new StringBuilder("a: Int");
            StringBuilder clauses = new StringBuilder("    invariant chosen = %s%n"
                    .formatted(clause));
            for (int i = 1; i < choices; i++) {
                fields.append(", f").append(i).append(": Int");
                clauses.append("    invariant c%d = f%d == 0 || f%d == 1%n".formatted(i, i, i));
            }
            return """
                    module choices exposing ( P )
                    data P = { %s }
                    %s""".formatted(fields, clauses);
        }
    }
}
