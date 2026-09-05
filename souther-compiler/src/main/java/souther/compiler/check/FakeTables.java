package souther.compiler.check;

import souther.compiler.ast.Hir;
import souther.compiler.types.ValueName;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;
import java.util.function.UnaryOperator;

/**
 * What a module's {@code fake} blocks declare, read once from what resolution answered.
 *
 * <p>A block is where a stand-in is written; it is not the stand-in. The rows of one form an
 * ordered decision list — the first row stating an input is the one that answers — and there is no
 * order between a module's own source and the {@code examples for} files attached to it that could
 * say which of two blocks comes first. So two blocks naming one behavior cannot be composed into
 * one table, and neither of them is the one that answers: what a behavior has here is a count, and
 * the three counts are told apart rather than folded into whether a lookup found something.
 *
 * <p>The three are different facts and a reader acts differently on each. Nothing written is a
 * dependency with no stand-in, which is what a row needs one for. One block written is a table to
 * dispatch with and to hold against the rows recorded for the behavior. More than one is a
 * refusal — the blocks are written and are read as tables in their own right, and none of them
 * stands in for anything. A lookup answering only "found" and "not found" gives a reader the first
 * and third under one answer, and the reader then has to decide which it was.
 *
 * <p>Made in one place because the association is resolution's answer and the count is over the
 * whole module. A consumer holding {@link Hir.Fake}s and reading {@code standsInFor} off them would
 * be making the association again, and one walking the module's list to pick a table for a
 * behavior would be making the count again — which is how a table came to be chosen by the order
 * its file was handed to the compile.
 */
public final class FakeTables {

    private final List<Occurrence> written;
    private final SequencedMap<ValueName.Behavior, Declaration> declared;

    private FakeTables(List<Occurrence> written,
                       SequencedMap<ValueName.Behavior, Declaration> declared) {
        this.written = List.copyOf(written);
        this.declared = new LinkedHashMap<>(declared);
    }

    /**
     * One {@code fake} block as it was written, and the behavior it names where the name reached
     * one.
     *
     * <p>Two arms rather than a behavior that may be absent. A block whose target denoted no
     * behavior is refused where the name is read, and it is still a block: its rows stand where they
     * are written, and a reader over what a module has written in it reaches them. What it cannot
     * take part in is a count of the blocks naming one behavior, there being no behavior it names —
     * nor a reading of what its rows state, there being no declaration to state anything against.
     */
    public sealed interface Occurrence {

        /** The block, as written. */
        Hir.Fake read();

        /** A block whose target reached a behavior. */
        record Resolved(ValueName.Behavior behavior, Hir.Fake read) implements Occurrence {}

        /** A block whose target reached none, which is said where the name is read. */
        record Unresolved(Hir.Fake read) implements Occurrence {}
    }

    /** How many blocks of this module name one behavior. */
    public sealed interface Declaration {

        /** None does, so nothing here stands in for it. */
        record Missing() implements Declaration {}

        /** One does, and it is the table that answers for the behavior. */
        record One(Occurrence.Resolved table) implements Declaration {}

        /**
         * More than one does, so none of them establishes what stands in for the behavior.
         *
         * <p>Every block that names it, in the order they were read, and none of them marked as
         * the one to keep: which is the one to write differently is not something the language
         * answers, the blocks being what they are whichever order the compile was handed its files
         * in.
         */
        record Conflict(ValueName.Behavior behavior, List<Occurrence.Resolved> tables)
                implements Declaration {

            public Conflict {
                tables = List.copyOf(tables);
            }
        }
    }

    /**
     * The blocks {@code module} writes, classified by the behavior each names.
     *
     * <p>The only place a {@code fake} block is asked what it stands in for, and the only place the
     * module's list of them is walked to find out. Everything below reads the association off
     * {@link Occurrence.Resolved} and the count off {@link Declaration}.
     *
     * <p>The module and not a list of blocks, so that what is counted is every block the module
     * has: its own source's and every attached {@code examples for} file's, which are joined into
     * it before it is resolved. A caller passing a list it had gathered itself would be choosing
     * the population this is here to take whole.
     */
    public static FakeTables classify(Hir.Module module) {
        List<Occurrence> occurrences = new ArrayList<>();
        SequencedMap<ValueName.Behavior, List<Occurrence.Resolved>> naming = new LinkedHashMap<>();
        for (Hir.Fake block : module.fakes()) {
            ValueName.Behavior behavior = block.standsInFor();
            if (behavior == null) {
                occurrences.add(new Occurrence.Unresolved(block));
                continue;
            }
            Occurrence.Resolved resolved = new Occurrence.Resolved(behavior, block);
            occurrences.add(resolved);
            naming.computeIfAbsent(behavior, _ -> new ArrayList<>()).add(resolved);
        }
        SequencedMap<ValueName.Behavior, Declaration> declared = new LinkedHashMap<>();
        naming.forEach((behavior, blocks) -> declared.put(behavior, blocks.size() == 1
                ? new Declaration.One(blocks.get(0))
                : new Declaration.Conflict(behavior, blocks)));
        return new FakeTables(occurrences, declared);
    }

    /** Two classifications are one where the blocks and the counts over them are, which is what an
     *  answer this was read from is asked when something is compiled again. */
    @Override
    public boolean equals(Object o) {
        return o instanceof FakeTables other
                && written.equals(other.written) && declared.equals(other.declared);
    }

    @Override
    public int hashCode() {
        return written.hashCode() * 31 + declared.hashCode();
    }

    /** Every block this module writes, in the order they were read, whether or not its target
     *  reached a behavior. */
    public List<Occurrence> written() {
        return written;
    }

    /** How many blocks name {@code behavior}. */
    public Declaration declaredFor(ValueName.Behavior behavior) {
        Declaration found = declared.get(behavior);
        return found == null ? new Declaration.Missing() : found;
    }

    /**
     * Every block whose target reached a behavior, in the order they were read.
     *
     * <p>What is built and held to what it states, whether or not it is the block that stands in
     * for its behavior: a table is built because it is written, and a block standing in for nothing
     * because another names the same behavior is still a block whose own rows say something. A
     * block whose target reached nothing is not here — there is no signature to build it against,
     * and what is wrong with it is said where the name is read.
     */
    public List<Occurrence.Resolved> naming() {
        List<Occurrence.Resolved> found = new ArrayList<>();
        for (Occurrence each : written) {
            if (each instanceof Occurrence.Resolved resolved) {
                found.add(resolved);
            }
        }
        return List.copyOf(found);
    }

    /** Each behavior one block names, under that block, in the order the blocks were read: the
     *  blocks that stand in for something. */
    public SequencedMap<ValueName.Behavior, Occurrence.Resolved> answering() {
        SequencedMap<ValueName.Behavior, Occurrence.Resolved> only = new LinkedHashMap<>();
        declared.forEach((behavior, declaration) -> {
            if (declaration instanceof Declaration.One(Occurrence.Resolved table)) {
                only.put(behavior, table);
            }
        });
        return only;
    }

    /** The behaviors more than one block names, in the order the first block naming each was
     *  read. */
    public List<Declaration.Conflict> conflicts() {
        List<Declaration.Conflict> found = new ArrayList<>();
        declared.forEach((_, declaration) -> {
            if (declaration instanceof Declaration.Conflict conflict) {
                found.add(conflict);
            }
        });
        return List.copyOf(found);
    }

    /**
     * These blocks with every name in them that denotes another module's definition written out,
     * classified as they already are.
     *
     * <p>Writing a name out leaves what a block stands in for alone, and the count with it.
     * Classifying the result again would ask resolution's question a second time, of a tree a later
     * pass wrote, and the two answers would agree for exactly as long as nobody changed either.
     *
     * <p>The rewrite is this one and is not handed in. A method taking any rewrite and answering
     * with a classification would let the association resolution made be claimed of blocks
     * resolution never read — which is the whole of what this type is here to make impossible.
     */
    public static FakeTables namesWrittenOut(FakeTables declared, String self) {
        return declared.rewritten(block -> HelperNames.qualifyImportsIn(block, self));
    }

    private FakeTables rewritten(UnaryOperator<Hir.Fake> rewrite) {
        List<Occurrence> mapped = new ArrayList<>();
        // By identity: what is being paired is each block with what the rewrite made of it, and two
        // blocks that read alike are two blocks.
        Map<Hir.Fake, Hir.Fake> by = new IdentityHashMap<>();
        for (Occurrence each : written) {
            Hir.Fake now = rewrite.apply(each.read());
            by.put(each.read(), now);
            mapped.add(switch (each) {
                case Occurrence.Resolved(ValueName.Behavior behavior, Hir.Fake _) ->
                        new Occurrence.Resolved(behavior, now);
                case Occurrence.Unresolved _ -> new Occurrence.Unresolved(now);
            });
        }
        SequencedMap<ValueName.Behavior, Declaration> carried = new LinkedHashMap<>();
        declared.forEach((behavior, declaration) -> carried.put(behavior, switch (declaration) {
            case Declaration.Missing missing -> missing;
            case Declaration.One(Occurrence.Resolved table) ->
                    new Declaration.One(new Occurrence.Resolved(behavior, by.get(table.read())));
            case Declaration.Conflict(ValueName.Behavior named, List<Occurrence.Resolved> tables) -> {
                List<Occurrence.Resolved> moved = new ArrayList<>();
                for (Occurrence.Resolved table : tables) {
                    moved.add(new Occurrence.Resolved(named, by.get(table.read())));
                }
                yield new Declaration.Conflict(named, moved);
            }
        }));
        return new FakeTables(mapped, carried);
    }
}
