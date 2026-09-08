package souther.compiler.examples;

import souther.compiler.ast.Hir;
import souther.compiler.diag.SourcePos;
import souther.compiler.observe.ObservedValue;

import java.util.List;

/**
 * One explicit entry of a {@code fake} table standing in for a bound behavior: an input and the
 * answer the table states for it.
 *
 * <p>A {@code fake} is a written statement about what a behavior answers, and ADR-0093 compares it
 * with that behavior's recorded rows without giving either precedence. What that check cannot reach
 * is a faked behavior with no rows of its own — nothing is there to compare it with, and every faked
 * behavior in the examples repository is injected. Once an implementation is bound, each entry is an
 * input and an answer in the same form a recorded row is, and nothing new has to be written to run
 * it.
 *
 * <p>Two things come of running them. A row of the behavior that stands this one in ran through the
 * table, so a table that disagrees with the implementation means that row held against an answer
 * nothing produces. And a table usually states inputs the dependency's own rows never mention — they
 * were written to drive a branch of the composite — so running them adds observed evidence for free.
 *
 * <p>{@link #inputs} and {@link #stated} are structural and loader-free, beside the shown text
 * rather than instead of it. What a machine reads and what a person reads must not be one String: a
 * caller arranging the world for an entry would otherwise switch on presentation text, which is the
 * dependence {@link RowKey} was introduced to remove, rebuilt one field over.
 */
public final class StandinEntry {

    private final BoundExamples of;
    private final String behavior;
    private final SourcePos table;
    private final ExampleStatements.Standin.Explicit written;
    private final List<ObservedValue> inputs;
    private final ObservedValue stated;
    private final List<String> shownInputs;
    private final String shownStated;
    private final List<RecordedRow> alsoBy;

    StandinEntry(BoundExamples of, String behavior, SourcePos table,
                 ExampleStatements.Standin.Explicit written,
                 List<ObservedValue> inputs, ObservedValue stated, List<String> shownInputs,
                 String shownStated, List<RecordedRow> alsoBy) {
        this.of = of;
        this.behavior = behavior;
        this.table = table;
        this.written = written;
        this.inputs = List.copyOf(inputs);
        this.stated = stated;
        this.shownInputs = List.copyOf(shownInputs);
        this.shownStated = shownStated;
        this.alsoBy = List.copyOf(alsoBy);
    }

    /** The faked behavior, which is the bound one. */
    public String behavior() {
        return behavior;
    }

    /**
     * Where the table this entry is of is written.
     *
     * <p>A place and not the table. What a caller has a question about is which table an entry came
     * from and where to look, and a compile-stage representation handed out for that would be an
     * entrance into the pipeline this face exists to stand in front of — a classified block reaches
     * {@link Hir} from a reader that has no business there.
     */
    public SourcePos table() {
        return table;
    }

    /** Where this entry itself is written. */
    public SourcePos at() {
        return written.row().pos();
    }

    /** The inputs the entry states. */
    public List<ObservedValue> inputs() {
        return inputs;
    }

    /** The answer the fake states for them. */
    public ObservedValue stated() {
        return stated;
    }

    /**
     * The recorded rows of this behavior that state this entry's input.
     *
     * <p>Zero or more. #718 made behavior × name unique and not behavior × input, so two rows may
     * state one input, under two names or under none, and may even expect different things of it —
     * which is itself something a consumer wants shown. They are handles and not keys, which keeps
     * unnamed rows in and is what lets a caller draw the correlation an apportionment needs, under a
     * world it holds still:
     *
     * <pre>
     * reset(); seedFor(entry);
     * StandinObservation fake = examples.observe(entry);
     * for (RecordedRow row : entry.alsoBy()) {
     *     correlate(examples.evaluate(row), fake);
     * }
     * </pre>
     *
     * <p>This API does not draw that correlation itself. An implementation that answers out of world
     * state can agree with the row under the state the row was given and disagree with the fake under
     * another, and nothing about who is wrong follows from that; the only holder of world identity is
     * the caller. What is contributed here is the static half — which rows state the entry's input —
     * and the dynamic half is theirs.
     */
    public List<RecordedRow> alsoBy() {
        return alsoBy;
    }

    /** The inputs as they read. */
    public List<String> shownInputs() {
        return shownInputs;
    }

    /** The stated answer as it reads. */
    public String shownStated() {
        return shownStated;
    }

    @Override
    public String toString() {
        return behavior + " " + shownInputs + " -> " + shownStated;
    }

    /** The enumeration this came from, so one binding does not observe another's entry. */
    BoundExamples enumeratedBy() {
        return of;
    }

    ExampleStatements.Standin.Explicit written() {
        return written;
    }
}
