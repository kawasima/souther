package souther.bench;

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
 * <p><b>Alternatives are not the whole of what a choice costs.</b> A conjunction distributes over a
 * choice ({@code StatedTogether.meet}), so a branch stands in as many places as the clauses met with
 * it put it, and a fate is aggregated back over every one of them. Two declarations can expand to
 * the same number of alternatives and pay differently for them, which is why the expansion is
 * measured two ways rather than one:
 *
 * <pre>
 *   wide   one choice of n alternatives     n alternatives, each branch standing in one place
 *   deep   k choices of two, met together   2^k alternatives, each branch standing in 2^(k-1)
 * </pre>
 *
 * <p>The two are read against each other at the same n. What separates them is how many places one
 * written branch was put in, so the difference between the lines is what distribution costs and
 * nothing else.
 *
 * <p><b>Written flat, a wide choice is not writable.</b> Its alternatives nest one per alternative
 * and the parser refuses the source past {@code CstParser.MAX_DEPTH} — a limit on the shape a source
 * may have, which is a different question from how much combinatorial work an analysis will take on.
 * So the alternatives here are bracketed into a balanced tree, and what that shows is that a source
 * shallow enough for anybody to write can still expand as far as the reading will hold apart.
 *
 * <p>{@link #boundary} is where the reading stops holding alternatives apart and merges them into
 * the one product containing them. Measured on the wide shape and one alternative either side,
 * because that is the only place the two lines differ by the policy alone: taken on the deep shape,
 * the step to the next size doubles the alternatives as well, and what the pair showed would be two
 * changes at once.
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

    static void measure(Report report) {
        for (int alternatives : ALTERNATIVES) {
            line(report, "expansion wide", alternatives, wide(alternatives));
        }
        for (int alternatives : ALTERNATIVES) {
            line(report, "expansion deep", alternatives,
                    deep(Integer.numberOfTrailingZeros(alternatives)));
        }
        for (int alternatives : BOUNDARY) {
            line(report, "boundary", alternatives, wide(alternatives));
        }
        for (int conjuncts : CONJUNCTS) {
            Timing timing = timeOf(conjuncts(conjuncts));
            report.line("CHOICE %-16s conjuncts=%-3d %7.1f ms",
                    "distributed into", conjuncts, timing.medianMillis());
        }
        for (Fate fate : Fate.values()) {
            Timing timing = timeOf(fate.source(FATED_CHOICES));
            report.line("CHOICE %-16s %-14s %7.1f ms", "fate", fate.written(),
                    timing.medianMillis());
        }
    }

    /** One line of an expansion series, with the per-alternative figure beside the total.
     *
     *  <p>Both, because past the limit the two say different things: the alternatives are counted off
     *  the source either way, and a reading that merged them did not hold the number the figure is
     *  divided by. */
    private static void line(Report report, String shape, int alternatives, String source) {
        Timing timing = timeOf(source);
        report.line("CHOICE %-16s n=%-4d %7.1f ms (%6.3f ms/alternative)",
                shape, alternatives, timing.medianMillis(),
                timing.medianMillis() / alternatives);
    }

    private static Timing timeOf(String source) {
        return Timing.of(3, 5, () -> {
            Compilation compilation = Compilation.ofSources(List.of(source), ModulePath.EMPTY);
            compilation.answerEverything();
            compilation.classes();
        });
    }

    /**
     * One choice of {@code alternatives} alternatives at one position.
     *
     * <p>Bracketed into a balanced tree, which is what makes the wide shape writable at all. What
     * the reading takes in is the same either way — the alternatives are what stands between the
     * brackets — so the bracketing changes what a parser will accept and nothing a reading answers.
     */
    static String wide(int alternatives) {
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
    static String deep(int choices) {
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
    static String conjuncts(int conjuncts) {
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
     * The three fates a choice can come to, each at the head of a declaration of {@code choices}
     * choices met together.
     *
     * <p>Under a distribution rather than on its own. What a fate costs is not what deciding one
     * costs — every one of these is two alternatives and three equalities either way — but what the
     * decision saves the places the branch would otherwise have stood in: an alternative nobody can
     * be in is one the clauses written beside it are never met with. Measured on a declaration
     * whose one choice is the whole of it, the three come to the same figure, and the series says
     * nothing.
     *
     * <p>Held still across the three. Each alternative is a conjunction of two equalities whichever
     * fate it is, so what separates them is which values the equalities name and nothing about how
     * much was written or how far it was distributed.
     *
     * <p>{@link #NONE_STANDS} is a declaration nothing satisfies, and the compile says so. That is
     * what reaching the fate takes — a choice admits nothing only where every alternative does — so
     * its line is what the fate costs and not what a model anybody would keep costs.
     */
    enum Fate {

        /** Both alternatives admit something, so the choice is held open and both are distributed
         *  into. */
        BOTH_STAND("both stand", "(a == 0 && b == 0) || (a == 1 && b == 1)"),

        /** One alternative admits nothing, so the answer is the other and a proof crosses the
         *  join. */
        ONE_STANDS("one stands", "(a == 0 && b == 0) || (a == 1 && a == 2)"),

        /** No alternative admits anything, and none of them is at fault for it. */
        NONE_STANDS("none stands", "(a == 0 && a == 3) || (a == 1 && a == 2)");

        private final String written;
        private final String clause;

        Fate(String written, String clause) {
            this.written = written;
            this.clause = clause;
        }

        String written() {
            return written;
        }

        /** This fate at the head of {@code choices} choices, the rest of them plain and alike. */
        String source(int choices) {
            StringBuilder fields = new StringBuilder("a: Int, b: Int");
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
