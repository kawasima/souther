package souther.compiler.values;

/**
 * The order the parts of a plan are held in, read off the plan and off nothing else.
 *
 * <p>A plan says what a position admits and says it without an order — two clauses stating the same
 * rules in different words are one plan. What works it out has to do one thing before another all
 * the same, and where that order came from the plan's parts as they happened to arrive, the same
 * plan was worked out two ways: what was built first, what it cost, and which of two answers came
 * back exact all turned on the writing again, one step further along than before.
 *
 * <p>So the order is a function of the plan. What is written here is the whole of a plan and not a
 * digest of it: two plans that differ differ somewhere in this, and comparing the text decides
 * which comes first. A hash would say when two are equal and nothing about which is first, and ties
 * broken on the side would put one pair in either order from one run to the next.
 *
 * <p><b>Structure and not meaning.</b> What a pattern accepts is a machine, and asking for it is
 * the spending a plan exists to arrange rather than to trigger — an order that had to know which
 * strings a leaf holds would build every leaf before deciding not to build any. So a pattern is
 * ordered by how it is written. Two spellings of one language are two leaves until something builds
 * them, which is what they are: they take different work to make, and the plan is about the work.
 *
 * <p>A set already worked out is different, and is ordered by what it holds. Its machine exists —
 * whoever put the set together made it — so reading it off asks for nothing that was not already
 * bought, and two of them holding the same strings are one leaf however either was arrived at.
 */
final class PlanOrder {

    private PlanOrder() {
    }

    /**
     * How {@code plan} is written, for comparing with how another one is.
     *
     * <p>Kinds are numbered so that unlike things never interleave, and every kind writes its own
     * length or terminator, so that no part of one plan can be read as the beginning of another's.
     */
    static String of(AdmittedPlan plan) {
        StringBuilder out = new StringBuilder();
        out.append("%012d;".formatted(states(plan)));
        write(plan, out);
        return out.toString();
    }

    /**
     * How many states this would take, as far as its shape says, and never more than
     * {@link #ENOUGH}.
     *
     * <p>Written first in the key, so that the least of them is put together first. Two languages
     * meet in a machine of about their sizes multiplied, and every meet after that is against what
     * the last one left — so the small ones first is the whole difference between a plan that costs
     * one string's worth of asking and one that costs a product nobody needed.
     *
     * <p>Read off the shape and never off the strings. Which of two patterns really makes the
     * smaller machine is a question about the machines, and asking it means building both, which is
     * the spending being arranged. What is here is what a repetition written {@code {300}} says
     * about itself: three hundred copies of something is three hundred times what that costs.
     *
     * <p>A guess, and it is allowed to be one. What it decides is which order the work is done in,
     * and any order gives the same answer — so being wrong costs states and never correctness. What
     * it may not be is unsteady: it is a function of the plan, so the same plan is worked out the
     * same way wherever it was written.
     */
    private static long states(AdmittedPlan plan) {
        return switch (plan) {
            case AdmittedPlan.Everything _, AdmittedPlan.Nothing _ -> 0;
            // Written out already, and what putting them together costs is arithmetic over values
            // somebody wrote down — except where one of them is a machine, which is as big as it
            // is and is a thing this can simply ask, the machine being made.
            case AdmittedPlan.Of it -> it.isFree() ? 0
                    : ((ValueSet.Matching) it.set()).language().size();
            case AdmittedPlan.Pattern it -> it.plan().states();
            case AdmittedPlan.Both it -> parts(it.parts());
            case AdmittedPlan.Either it -> parts(it.parts());
        };
    }

    private static long parts(java.util.Set<AdmittedPlan> parts) {
        long out = 0;
        for (AdmittedPlan each : parts) {
            out = Math.min(ENOUGH, out + states(each));
        }
        return out;
    }

    /** More than anything this compiler builds, which is where a count stops climbing. */
    private static final long ENOUGH = 999_999_999L;

    private static void write(AdmittedPlan plan, StringBuilder out) {
        switch (plan) {
            case AdmittedPlan.Everything _ -> out.append("0;");
            case AdmittedPlan.Nothing _ -> out.append("1;");
            case AdmittedPlan.Of it -> {
                out.append("2;");
                write(it.set(), out);
            }
            case AdmittedPlan.Pattern it -> {
                out.append("3;");
                it.plan().writtenInto(out);
                out.append(';');
            }
            case AdmittedPlan.Both it -> {
                out.append("4;");
                writeParts(it.parts(), out);
            }
            case AdmittedPlan.Either it -> {
                out.append("5;");
                writeParts(it.parts(), out);
            }
        }
    }

    // Already in the order this puts them in, since that is the order a plan holds its parts in.
    private static void writeParts(java.util.Set<AdmittedPlan> parts, StringBuilder out) {
        out.append(parts.size()).append(';');
        parts.forEach(each -> write(each, out));
    }

    /**
     * How a whole reading is written, for a caller putting several of them in a work order.
     *
     * <p>The same rule one position's plan is ordered by, at the layer whose parts are readings:
     * the states it holds first, so that the least of them are put together first, and then the
     * whole of what it holds, so that two readings that differ are ordered and two that do not are
     * one thing either way.
     *
     * <p><b>A work order and nothing else.</b> What a reading says, and the order the reasons in it
     * are written down in, are the author's — this decides only which pair of sets is built first.
     * Two positions printed alike would tie here, and a tie costs states rather than answers.
     */
    static String of(AdmissibleValues<?> reading) {
        StringBuilder out = new StringBuilder();
        out.append("%012d;".formatted(states(reading.perPosition().values())));
        // What a reading is, written by the reading. Copied out here component by component, this
        // was a second spelling of another type's state that nothing held to it: two of them went
        // missing the first time and the order fell back to the order the readings arrived in,
        // which is the one thing it exists to be independent of.
        reading.schedulingForm(out);
        return out.toString();
    }

    /**
     * The alternatives, which are most of what a meet of two readings costs.
     *
     * <p>What a pairwise meet builds is a set composition for every pair of alternatives, so one
     * box against one is one composition and two against two are four. Left out, two readings whose
     * positions hold the same sets and whose alternatives relate them differently were the same
     * thing here — and a sort that keeps equal things where it found them put them back in the
     * order they arrived.
     *
     * <p>A set of boxes and not a sequence: the alternatives are a union, so each is written out and
     * the writings are sorted. What each box holds is written by position, for the same reason.
     *
     * <p>Both halves of an alternative, because both are what it says. Two alternatives whose sides
     * agree and whose denials do not are two alternatives, and written by their sides alone they
     * would come out alike — which puts the readings back in the order they happened to arrive in.
     */
    static void written(AdmissibleValues.Held<?> held, StringBuilder out) {
        switch (held) {
            case AdmissibleValues.Held.Nothing<?> _ -> out.append("0;");
            case AdmissibleValues.Held.Alternatives<?> it -> {
                out.append("1;").append(it.boxes().size()).append(';');
                it.boxes().stream()
                        .map(box -> {
                            StringBuilder one = new StringBuilder();
                            written(box.at(), one);
                            written(box.apart(), one);
                            return one.toString();
                        })
                        .sorted()
                        .forEach(each -> out.append(each).append(';'));
            }
        }
    }

    /**
     * Which blocks an alternative states to differ, written out in one order whatever order they
     * were stated in.
     *
     * <p>Sorted, for the reason the alternatives are: a relation is a set of pairs, so the same
     * rules written two ways are one relation and have to come out as one writing.
     */
    private static void written(Apartness<?> apart, StringBuilder out) {
        out.append(apart.edges().size()).append(';');
        apart.edges().stream().map(String::valueOf).sorted()
                .forEach(each -> out.append(each).append(';'));
    }

    private static long states(java.util.Collection<ValueSet> sets) {
        long out = 0;
        for (ValueSet each : sets) {
            out = Math.min(ENOUGH, out + (each instanceof ValueSet.Matching it
                    ? it.language().size() : 0));
        }
        return out;
    }

    /**
     * Every position and what it holds, taken by name so that two readings are compared over the
     * same positions however either of them happens to be filed.
     *
     * <p><b>By name, and the name has to tell them apart.</b> A position is an identity and its
     * whole content is what it is called, so there is nothing else to write it as — but if two
     * positions were written alike, two readings that differ would come out equal here, and a sort
     * that keeps equal things where it found them would put them back in the order they arrived.
     * The order would be the arrival order again, at the one place that exists to stop that.
     *
     * <p>So it is asserted rather than assumed. Every compile this test suite runs goes through
     * here, so a position type whose name does not tell its positions apart is found by the corpus
     * rather than by a reader of this comment. An assertion because it is about this compiler and
     * not about any model.
     */
    static void written(java.util.Map<?, ValueSet> at, StringBuilder out) {
        out.append(at.size()).append(';');
        java.util.List<String> named = at.keySet().stream().map(String::valueOf).toList();
        assert java.util.Set.copyOf(named).size() == at.size()
                : "two positions of one reading are written alike, so an order over readings is not"
                        + " one: " + named;
        at.entrySet().stream()
                .map(each -> {
                    StringBuilder one = new StringBuilder(String.valueOf(each.getKey()));
                    one.append('=');
                    write(each.getValue(), one);
                    return one.toString();
                })
                .sorted()
                .forEach(each -> out.append(each).append(';'));
    }

    static void write(ValueSet set, StringBuilder out) {
        switch (set) {
            case ValueSet.Finite it -> {
                out.append("0;");
                write(it.values(), out);
            }
            case ValueSet.Cofinite it -> {
                out.append("1;");
                write(it.excluded(), out);
            }
            // What it accepts and not how it was written, which is what a set already worked out
            // is: the machine was made when the set was, so this asks for nothing new.
            case ValueSet.Matching it -> {
                out.append("2;");
                it.language().writtenInto(out);
                out.append(';');
            }
        }
    }

    private static void write(java.util.Set<Value> values, StringBuilder out) {
        out.append(values.size()).append(';');
        values.stream().map(PlanOrder::of).sorted().forEach(each -> out.append(each).append(';'));
    }

    private static String of(Value value) {
        return switch (value) {
            case Value.Text it -> "0;" + it.value().length() + ";" + it.value();
            case Value.Number it -> "1;" + it.value().toPlainString();
            case Value.Truth it -> "2;" + it.value();
            case Value.Case it -> "3;" + it.data();
        };
    }

}
