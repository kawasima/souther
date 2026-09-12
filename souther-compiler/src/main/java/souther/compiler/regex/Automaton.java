package souther.compiler.regex;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The strings a pattern accepts, as states to walk between.
 *
 * <p>An automaton with steps that cost no symbol. Which is what the shape of a pattern asks for: a
 * choice is a step into either arm, a repetition is a step back to where it started, and neither
 * spends anything. Built without them, every one of those is a copy of what is under it and a
 * pattern of a few dozen characters is a great many states.
 *
 * <p>What labels a step is a set of symbols and never one. A class is one step over the whole of
 * what it holds, so a step is as cheap for `[^a]` as for `a` — read as one step per symbol, a
 * negated class over the whole universe would be a million of them, which is the same language and
 * a table nobody can hold.
 *
 * <p>Nothing here is about how a pattern was written. Two patterns that accept the same strings may
 * come to different automata, and that is the point of the next questions being asked of this rather
 * than of the syntax: what a language holds is not what its author typed.
 */
final class Automaton {

    /**
     * Where a walk begins.
     *
     * <p>Always zero, and the construction depends on it: a machine is built by adding states to the
     * end, so the first one made is the one everything else hangs from.
     */
    static final int START = 0;

    /** For each state, the steps that cost a symbol. */
    private final List<List<Step>> steps;

    /** For each state, the steps that cost nothing. */
    private final List<int[]> free;

    /** The states a walk may stop at. */
    private final BitSet accepting;

    /** One step, and what it costs to take. */
    record Step(CodePoints over, int to) {}

    private Automaton(List<List<Step>> steps, List<int[]> free, BitSet accepting) {
        this.steps = steps;
        this.free = free;
        this.accepting = accepting;
    }

    /** How many states it has, which is what a caller bounding its work counts. */
    int size() {
        return steps.size();
    }

    /**
     * A machine written out state by state, for a builder in this package that has one to write.
     *
     * <p>Beside {@link #of} and not instead of it. That one is handed a pattern and works out the
     * states; a builder here holds a machine whose states follow from something that is not a
     * pattern — where a string sits against another on the runtime's order, which no pattern says —
     * and has nothing to be read from. Both end in the same three tables, which is what keeps a
     * machine made this way answerable to everything below.
     *
     * <p>No free steps: a builder writing its own states writes what each one leads to, so a step
     * costing no symbol is a state it did not need. One that wants them builds through {@link #of}.
     */
    static Automaton madeOf(List<List<Step>> steps, BitSet accepting) {
        List<int[]> free = new ArrayList<>();
        for (int at = 0; at < steps.size(); at++) {
            free.add(new int[0]);
        }
        return new Automaton(steps, free, accepting);
    }

    /**
     * The steps out of one state, for a reader in this package walking a canonical machine.
     *
     * <p>Only ever asked of one that is canonical, where a walk is the whole of what there is to do:
     * the machine is deterministic and complete, so every symbol leads somewhere and where it leads
     * is a fact about the symbol. Asked of a machine that is not, a reader would be walking one of
     * the ways the pattern happened to be written.
     */
    List<Step> stepsFrom(int state) {
        return steps.get(state);
    }

    /** Whether a walk may stop at {@code state}. */
    boolean stopsAt(int state) {
        return accepting.get(state);
    }

    /**
     * Whether a walk over this may stop having read a high surrogate and a low one in turn.
     *
     * <p>That pair of symbols is the one thing no string is read as, so a machine that stops on no
     * sequence holding them already accepts nothing but strings and taking them out would change
     * nothing.
     *
     * <p><b>Whether it stops, and not whether it has a step.</b> A canonical machine is complete —
     * every symbol leads somewhere from every state — so every one of them has a step over a high
     * surrogate and asking that says only that the machine is complete. Where such a step leads is
     * the question: a pattern naming no surrogate leads to the state nothing stops at, and the
     * sequence is refused there as it always was.
     *
     * <p>A walk over the states and nothing built. What it saves is a product every language would
     * otherwise be put through.
     */
    boolean mayStopHavingReadALoneSurrogatePair() {
        CodePoints high = CodePoints.between(0xD800, 0xDBFF);
        CodePoints low = CodePoints.between(0xDC00, 0xDFFF);
        boolean[] reaches = reachingSomewhereItStops();
        for (int at = 0; at < steps.size(); at++) {
            for (Step first : steps.get(at)) {
                if (first.over().and(high).isEmpty()) {
                    continue;
                }
                for (Step second : steps.get(first.to())) {
                    if (!second.over().and(low).isEmpty() && reaches[second.to()]) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * For each state, whether a walk from it may still reach one it stops at.
     *
     * <p>Here because it is a fact about the machine and about nothing else, and because more than
     * one reader wants it: what a walk looking for a string does with a step is settled by whether
     * anything is stopped at past it, and so is whether a shape the machine has is one a string
     * ever reaches. Worked out by each of them, the two would be one walk written twice.
     */
    boolean[] reachingSomewhereItStops() {
        List<List<Integer>> back = new ArrayList<>();
        for (int at = 0; at < steps.size(); at++) {
            back.add(new ArrayList<>());
        }
        for (int at = 0; at < steps.size(); at++) {
            for (Step each : steps.get(at)) {
                back.get(each.to()).add(at);
            }
        }
        boolean[] out = new boolean[steps.size()];
        List<Integer> waiting = new ArrayList<>();
        for (int at = accepting.nextSetBit(0); at >= 0; at = accepting.nextSetBit(at + 1)) {
            out[at] = true;
            waiting.add(at);
        }
        for (int at = 0; at < waiting.size(); at++) {
            for (int from : back.get(waiting.get(at))) {
                if (!out[from]) {
                    out[from] = true;
                    waiting.add(from);
                }
            }
        }
        return out;
    }

    /**
     * The machine for {@code syntax}, or null where building it would take more than
     * {@code mostStates}.
     *
     * <p>Null rather than a smaller machine. A repetition written large is a language with a great
     * many strings in it and no smaller machine accepts the same ones — so what a caller is told is
     * that this was not built, which is a fact about this compiler, and never that the pattern
     * accepts less than it does.
     *
     * <p>What to do about it is the caller's. Which is why the bound is an argument: whether a
     * pattern is worth this many states is a question about the answer being built, and nothing here
     * knows what that answer is for.
     */
    static Automaton of(PatternSyntax syntax, Meter meter) {
        // What the anchors come to, worked out before anything is made of them. Whoever read the
        // pattern has already asked whether they can be settled, so what comes back here is a tree.
        PatternSyntax placed = PatternSyntax.withoutAnchors(syntax);
        if (placed == null) {
            throw new IllegalArgumentException(
                    "a pattern whose anchors have no answer is not read, so nothing builds it");
        }
        Building building = new Building(meter.making());
        try {
            int start = building.state();
            int accept = building.build(placed, start);
            BitSet accepting = new BitSet();
            accepting.set(accept);
            return new Automaton(building.frozenSteps(), building.frozenFree(), accepting);
        } catch (TooMany _) {
            return null;
        }
    }

    /**
     * Whether the whole of {@code value} is accepted.
     *
     * <p>Walked a symbol at a time, where a symbol is what the engine reads: a code point where the
     * string holds a well-formed pair, and half of one where it holds half. Read a unit at a time,
     * a pattern naming a character past the basic plane would want two steps for what the engine
     * takes in one.
     */
    boolean accepts(String value) {
        BitSet here = closure(only(START));
        int at = 0;
        while (at < value.length()) {
            int symbol = value.codePointAt(at);
            at += Character.charCount(symbol);
            BitSet next = new BitSet();
            for (int state = here.nextSetBit(0); state >= 0; state = here.nextSetBit(state + 1)) {
                for (Step each : steps.get(state)) {
                    if (each.over().has(symbol)) {
                        next.set(each.to());
                    }
                }
            }
            if (next.isEmpty()) {
                return false;
            }
            here = closure(next);
        }
        return here.intersects(accepting);
    }

    /**
     * The machine accepting exactly {@code words} and nothing else.
     *
     * <p>No allowance is asked for. What it costs is the words themselves — one state per symbol of
     * each — so a caller holding a handful of values has an answer without going back for anything,
     * and a language met with them is the cheap operation rather than the one that has to be
     * counted.
     */
    static Automaton ofWords(java.util.Collection<String> words, Meter meter) {
        Meter.Making making = meter.making();
        List<List<Step>> steps = new ArrayList<>();
        List<int[]> free = new ArrayList<>();
        if (!making.state()) {
            return null;
        }
        steps.add(new ArrayList<>());
        free.add(new int[0]);
        BitSet accepting = new BitSet();
        for (String word : words) {
            int at = START;
            int i = 0;
            while (i < word.length()) {
                int symbol = word.codePointAt(i);
                i += Character.charCount(symbol);
                if (!making.state()) {
                    return null;
                }
                steps.add(new ArrayList<>());
                free.add(new int[0]);
                int made = steps.size() - 1;
                steps.get(at).add(new Step(CodePoints.of(symbol), made));
                at = made;
            }
            // The word of no symbols ends where it began, which makes the beginning one a walk may
            // stop at rather than a state of its own.
            accepting.set(at);
        }
        return new Automaton(steps, free, accepting);
    }

    /**
     * Either of them.
     *
     * <p>A new beginning that steps freely into both. Cheap: the states are the two machines'
     * together and one more, and nothing is copied.
     */
    Automaton or(Automaton other, Meter meter) {
        int mine = size();
        // What it will be: a beginning, and the two machines beside it. Asked before any of it is
        // made, since knowing the size and allocating it anyway is the thing a limit is for.
        if (!meter.making().states(1L + mine + other.size())) {
            return null;
        }
        List<List<Step>> steps = new ArrayList<>();
        List<int[]> free = new ArrayList<>();
        steps.add(new ArrayList<>());
        free.add(new int[] {1, 1 + mine});
        shifted(this, 1, steps, free);
        shifted(other, 1 + mine, steps, free);
        BitSet accepting = new BitSet();
        shiftInto(accepting, this.accepting, 1);
        shiftInto(accepting, other.accepting, 1 + mine);
        return new Automaton(steps, free, accepting);
    }

    /**
     * Both of them, as the pairs of states a walk is in at once.
     *
     * <p>A step is taken where both machines have one over the same symbols, and what it is over is
     * what the two labels share — so the symbols neither has in common carry the pair nowhere, which
     * is what a conjunction says.
     *
     * <p>The free steps are each machine's own, taken one side at a time. A pair where one side
     * moves for nothing is the same pair with that side further on, which is exactly what a step
     * costing no symbol means.
     *
     * <p><b>The pairs a walk reaches, and not every pair there is.</b> Grown from the pair a walk
     * begins at, so a pair nothing leads to is never made. One that is made costs a state, the
     * steps out of it worked out over both machines' labels, and an allowance charged for both —
     * and a pair no string is ever in is all of that spent on a state the minimisation afterwards
     * would throw away.
     *
     * <p>Which is why nothing is asked of the meter before the first state is made. The two sizes
     * multiplied is what a product could come to and hardly ever what it comes to, so a caller
     * refused on that number is refused an answer it could have afforded.
     */
    Automaton and(Automaton other, Meter meter) {
        try {
            Pairs pairs = new Pairs(other.size(), meter.making());
            List<List<Step>> steps = new ArrayList<>();
            List<int[]> free = new ArrayList<>();
            BitSet accepting = new BitSet();
            pairs.at(START, START);
            // Grows while it is walked: a pair first reached here is one more to take the steps
            // out of, and the walk is over when nothing new has been reached.
            for (int at = 0; at < pairs.count(); at++) {
                int mine = pairs.mine(at);
                int theirs = pairs.theirs(at);
                List<Step> out = new ArrayList<>();
                for (Step one : this.steps.get(mine)) {
                    for (Step two : other.steps.get(theirs)) {
                        CodePoints over = one.over().and(two.over());
                        if (!over.isEmpty()) {
                            out.add(new Step(over, pairs.at(one.to(), two.to())));
                        }
                    }
                }
                List<Integer> freely = new ArrayList<>();
                for (int to : this.free.get(mine)) {
                    freely.add(pairs.at(to, theirs));
                }
                for (int to : other.free.get(theirs)) {
                    freely.add(pairs.at(mine, to));
                }
                int[] freeOut = new int[freely.size()];
                for (int i = 0; i < freeOut.length; i++) {
                    freeOut[i] = freely.get(i);
                }
                steps.add(out);
                free.add(freeOut);
                if (this.accepting.get(mine) && other.accepting.get(theirs)) {
                    accepting.set(at);
                }
            }
            return new Automaton(steps, free, accepting);
        } catch (TooMany _) {
            return null;
        }
    }

    /**
     * The pairs of states a walk over two machines is in at once, numbered as they are reached.
     *
     * <p>Where a meet's states come from and where they are counted. A pair asked for twice is the
     * same state both times, which is what makes the walk finish; a pair asked for the first time
     * is a state, and it is charged for there — so what a meet spends is the pairs it reached.
     */
    private static final class Pairs {

        private final int wide;
        private final Meter.Making making;
        private final Map<Long, Integer> known = new HashMap<>();
        private final List<Integer> mine = new ArrayList<>();
        private final List<Integer> theirs = new ArrayList<>();

        Pairs(int wide, Meter.Making making) {
            this.wide = wide;
            this.making = making;
        }

        /** Which state the pair is, making it where it has not been reached before. */
        int at(int mine, int theirs) {
            // A long, because the two sizes multiplied is what this could run to and nothing has
            // been asked about that: a key that overflowed would put two pairs in one state.
            Long key = (long) mine * wide + theirs;
            Integer had = known.get(key);
            if (had != null) {
                return had;
            }
            if (!making.state()) {
                throw new TooMany();
            }
            int made = this.mine.size();
            this.mine.add(mine);
            this.theirs.add(theirs);
            known.put(key, made);
            return made;
        }

        int count() {
            return mine.size();
        }

        int mine(int state) {
            return mine.get(state);
        }

        int theirs(int state) {
            return theirs.get(state);
        }
    }

    /**
     * Everything it does not accept.
     *
     * <p><b>The states a walk may stop at, turned over.</b> Which is the whole of it where a walk
     * is only ever in one state and every symbol takes it somewhere: a string ends in one state and
     * every string ends somewhere, so the ones this does not accept are the ones ending where it
     * does not stop. The steps do not move and neither does what each is numbered, so nothing is
     * built and nothing is charged.
     *
     * <p><b>And a caller holding the one machine for its strings ({@link #canonical}) gets the one
     * machine for the rest.</b> A string tells two states apart when a walk from one of them stops
     * on it and a walk from the other does not. Turning the states over turns both of those answers
     * over together, so a string that told two states apart tells them apart still and a string
     * that told them nothing tells them nothing still — the strings a walk from a state stops on
     * become the strings it does not, and which states no string separates is not a question those
     * answers changed. A machine no two of whose states a string could tell apart still has none.
     * The steps do not move, so the walk that numbers them meets the same states in the same order,
     * and being smallest and being numbered that way are the whole of what makes a machine the one
     * machine.
     *
     * <p><b>And why anything else has to be made deterministic first.</b> What a walk ends in has
     * to be one answer before the answer can be turned over, and a machine that steps for nothing
     * or twice over a symbol has not said what a string ends in. That is the one place this
     * construction is needed — acceptance never needs it, and neither do the two questions about
     * holding nothing and holding everything, both of which are read off the one state a canonical
     * machine has.
     */
    Automaton not(Meter meter) {
        if (everySymbolLeadsOneWay()) {
            return turnedOver();
        }
        try {
            Subsets subsets = new Subsets(meter.making());
            Meter.Making making = meter.making();
            List<List<Step>> steps = new ArrayList<>();
            List<int[]> free = new ArrayList<>();
            BitSet accepting = new BitSet();
            for (int at = 0; at < subsets.count(); at++) {
                if (!making.state()) {
                    return null;
                }
                steps.add(new ArrayList<>());
                free.add(new int[0]);
                if (!subsets.acceptingAt(at)) {
                    accepting.set(at);
                }
                for (Subsets.Move each : subsets.movesFrom(at)) {
                    steps.get(at).add(new Step(each.over(), each.to()));
                }
            }
            return new Automaton(steps, free, accepting);
        } catch (TooMany _) {
            return null;
        }
    }

    /**
     * Whether a walk over this is only ever in one state, and every symbol takes it somewhere.
     *
     * <p>Asked of the machine and not of where it came from. A machine carries no account of what
     * was done to it, and a reader that took one operation's word for the shape of its answer would
     * be reading the caller rather than the thing in front of it.
     *
     * <p>The labels out of a state say both. Together they are every symbol there is, so nothing
     * leads nowhere; and they are as wide apart as they are wide, so no symbol is on two of them.
     * A step costing no symbol is a second state to be in and there are none of those either.
     *
     * <p>Those steps are looked for first, over the whole machine. A machine that has one is one
     * this is going to say no about, and finding that out costs a length where the labels cost
     * every symbol they hold — so what a pattern's machine pays to be turned down is a walk over
     * the states and nothing more.
     */
    private boolean everySymbolLeadsOneWay() {
        for (int[] each : free) {
            if (each.length > 0) {
                return false;
            }
        }
        for (int at = 0; at < steps.size(); at++) {
            CodePoints together = CodePoints.NONE;
            long apart = 0;
            for (Step each : steps.get(at)) {
                together = together.or(each.over());
                apart += each.over().size();
            }
            if (!together.isEverything() || apart != together.size()) {
                return false;
            }
        }
        return true;
    }

    /**
     * The same machine with the states it stops at turned over — see {@link #not}.
     *
     * <p>Nothing is asked of an allowance, because no state is made. The steps are the steps that
     * were already there and so is what each one is numbered, and what is built is the answer to
     * one question about each of them. A meter counts the states a construction makes, and a
     * construction that shares the ones it was handed made none.
     */
    private Automaton turnedOver() {
        BitSet stops = new BitSet();
        stops.set(0, size());
        stops.andNot(accepting);
        return new Automaton(steps, free, stops);
    }

    /**
     * The one machine of its kind that accepts what this accepts, or null past {@code mostStates}.
     *
     * <p>Two patterns accepting the same strings come to this same machine, state for state and
     * step for step. Which is what lets everything a reader asks of a language afterwards be a look
     * at what is in front of it: whether two languages are one is a walk over two tables of the same
     * shape, and nothing about the answer is left for the asking to work out.
     *
     * <p>Three things make it the one machine. It is deterministic and complete, so which state a
     * string ends in is a fact about the string; it is smallest, so no two states are told apart by
     * nothing, which is what makes the shape the language's rather than the pattern's; and its
     * states are numbered by walking it from the start over the symbols in order, so the one shape
     * is written down one way.
     *
     * <p>The steps out of a state are over as few runs of symbols as say where they go. What the
     * subsets were cut over is the labels the pattern happened to carry, and two ways of writing one
     * language cut it differently — gathered by where they lead, the runs are the language's own.
     */
    Automaton canonical(Meter meter) {
        try {
            Subsets subsets = new Subsets(meter.making());
            List<CodePoints> alphabet = subsets.alphabet();
            List<int[]> table = new ArrayList<>();
            BitSet accepting = new BitSet();
            for (int at = 0; at < subsets.count(); at++) {
                if (subsets.acceptingAt(at)) {
                    accepting.set(at);
                }
                int[] row = new int[alphabet.size()];
                List<Subsets.Move> out = subsets.movesFrom(at);
                for (int over = 0; over < row.length; over++) {
                    row[over] = out.get(over).to();
                }
                table.add(row);
            }
            return numbered(table, smallest(table, accepting), accepting, alphabet,
                    meter.making());
        } catch (TooMany _) {
            return null;
        }
    }

    /**
     * Which states of a complete machine no string tells apart, as a block per state.
     *
     * <p>Told apart to begin with by whether a walk may stop there, and after that by where the
     * symbols lead: two states in one block that step into different blocks are two states, and
     * asking that over and over until nothing moves is what leaves the blocks a string could tell
     * apart and no others.
     *
     * <p>What a round asks of a state is a row — the block it is in, and then the block each symbol
     * leads to — and what it groups is the states whose rows are equal. A row is as wide as the runs
     * the labels cut, and two rows are equal exactly when their cells are, so the rows of a round go
     * into one array that the next round writes over and the grouping is an open table read off
     * those cells: the hash below is the cells, and a collision is settled by comparing them. The
     * table is sized once for the states there are, which is as many rows as a round can have, so no
     * round grows it. Blocks are numbered in the order a row is first seen; which numbers they get
     * is nothing {@link #numbered} reads, since it renames them by walking the machine.
     */
    private static int[] smallest(List<int[]> table, BitSet accepting) {
        int states = table.size();
        int width = 1 + table.get(0).length;
        int[] block = new int[states];
        for (int state = 0; state < states; state++) {
            block[state] = accepting.get(state) ? 1 : 0;
        }
        int[] cells = new int[states * width];
        int[] next = new int[states];
        int slots = 4;
        while (slots < states * 2) {
            slots <<= 1;
        }
        int[] seen = new int[slots];
        for (int blocks = 2, was = 0; blocks != was;) {
            was = blocks;
            Arrays.fill(seen, 0);
            int found = 0;
            for (int state = 0; state < states; state++) {
                int at = state * width;
                cells[at] = block[state];
                int[] row = table.get(state);
                for (int over = 0; over < row.length; over++) {
                    cells[at + 1 + over] = block[row[over]];
                }
                int hash = 1;
                for (int cell = at; cell < at + width; cell++) {
                    hash = 31 * hash + cells[cell];
                }
                int probe = (hash ^ (hash >>> 16)) & (slots - 1);
                while (true) {
                    int held = seen[probe];
                    if (held == 0) {
                        seen[probe] = state + 1;
                        next[state] = found++;
                        break;
                    }
                    if (Arrays.equals(cells, (held - 1) * width, held * width,
                            cells, at, at + width)) {
                        next[state] = next[held - 1];
                        break;
                    }
                    probe = (probe + 1) & (slots - 1);
                }
            }
            System.arraycopy(next, 0, block, 0, states);
            blocks = found;
        }
        return block;
    }

    /**
     * The blocks as a machine, numbered by walking it from the start.
     *
     * <p>The order a walk finds them in and not the order they were made in. What refining the
     * blocks leaves is the right partition however it is labelled, and a labelling that came out of
     * the order the subsets happened to be built in would make one language two machines.
     */
    private static Automaton numbered(List<int[]> table, int[] block, BitSet accepting,
                                      List<CodePoints> alphabet, Meter.Making making) {
        int[] renamed = new int[block.length];
        java.util.Arrays.fill(renamed, -1);
        int[] first = new int[block.length];
        java.util.Arrays.fill(first, -1);
        List<Integer> order = new ArrayList<>();
        order.add(block[START]);
        renamed[block[START]] = 0;
        first[block[START]] = START;
        for (int at = 0; at < order.size(); at++) {
            for (int to : table.get(first[order.get(at)])) {
                if (renamed[block[to]] < 0) {
                    renamed[block[to]] = order.size();
                    first[block[to]] = to;
                    order.add(block[to]);
                }
            }
        }
        List<List<Step>> steps = new ArrayList<>();
        List<int[]> free = new ArrayList<>();
        BitSet stops = new BitSet();
        for (int at = 0; at < order.size(); at++) {
            if (!making.state()) {
                throw new TooMany();
            }
            int[] row = table.get(first[order.get(at)]);
            if (accepting.get(first[order.get(at)])) {
                stops.set(at);
            }
            // Gathered by where they lead, so that what a step is over is as wide as it can be.
            java.util.Map<Integer, CodePoints> leading = new java.util.LinkedHashMap<>();
            for (int over = 0; over < row.length; over++) {
                leading.merge(renamed[block[row[over]]], alphabet.get(over), CodePoints::or);
            }
            List<Step> out = new ArrayList<>();
            new java.util.TreeMap<>(leading).forEach((to, over) -> out.add(new Step(over, to)));
            steps.add(out);
            free.add(new int[0]);
        }
        return new Automaton(steps, free, stops);
    }

    /**
     * Whether two canonical machines are the same machine.
     *
     * <p>A walk over two tables of the same shape and nothing more, which is what {@link #canonical}
     * is for. Asked of machines that are not canonical it is a question about how they were written.
     */
    boolean sameAs(Automaton other) {
        if (size() != other.size() || !accepting.equals(other.accepting)) {
            return false;
        }
        for (int at = 0; at < steps.size(); at++) {
            if (!steps.get(at).equals(other.steps.get(at))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether a canonical machine accepts nothing, and whether it accepts everything.
     *
     * <p>One state either way. What tells a language apart from another is a string one of them
     * stops on and the other does not, and neither of these two has one — so the smallest machine
     * for each is a single state that every symbol leads back to, accepting or not.
     */
    boolean holdsNothing() {
        return size() == 1 && !accepting.get(START);
    }

    /** The other of the two — see {@link #holdsNothing}. */
    boolean holdsEverything() {
        return size() == 1 && accepting.get(START);
    }

    /**
     * The whole table written out, for a caller putting machines in an order.
     *
     * <p>Read off the canonical machine, so two machines accepting the same strings write the same
     * thing and two accepting different strings do not — which is {@link #sameAs} spelled as
     * something that can be compared for less as well as for equal. The number beside it
     * ({@link #shape}) is a hash of the same table and agrees with it on equality only: two
     * different tables may hash alike, and an order that broke its ties on the hash would put the
     * same pair in either order on different runs.
     */
    void writtenInto(StringBuilder out) {
        out.append(steps.size());
        for (int at = 0; at < steps.size(); at++) {
            out.append(accepting.get(at) ? "!" : ".");
            for (Step each : steps.get(at)) {
                out.append(each.to()).append(':');
                for (CodePoints.Range range : each.over().ranges()) {
                    out.append(range.from()).append('-').append(range.to()).append(',');
                }
                out.append(';');
            }
            out.append('/');
        }
    }

    /** A number that agrees with {@link #sameAs}, read off the same table. */
    int shape() {
        int out = accepting.hashCode();
        for (List<Step> each : steps) {
            out = out * 31 + each.hashCode();
        }
        return out;
    }

    /**
     * One string it accepts, or null where it accepts none.
     *
     * <p>Total wherever there is anything to answer with. Which is what ties it to
     * {@link #holdsNothing}: a language that holds something and hands back nothing would be one
     * whose emptiness two readers disagree about, and the one that produces values would be
     * believed.
     *
     * <p>The shortest, and among the strings of that length one written out of symbols a source can
     * carry where there is one. Being writable is a preference and never a condition: it is about
     * what a person can paste back, and a language holds what it holds. So the length is settled
     * first, over every symbol there is, and only then is a string of that length looked for among
     * the symbols that can be written.
     *
     * <p>Deterministic under both: the symbol taken out of a set is the least of it, and the states
     * are walked in the order they were made. Two runs over one model produce one value.
     */
    String shortest() {
        String any = shortest(CodePoints.EVERYTHING, -1);
        if (any == null) {
            return null;
        }
        // The length first, over every symbol there is, and only then a string of that length out
        // of the ones a source can carry. Asked the other way round, a language holding a control
        // character and a longer word of letters answers with the longer one — which is reaching
        // for what can be written at the price of what was asked for.
        String written = shortest(WRITABLE, any.codePointCount(0, any.length()));
        return written != null ? written : any;
    }

    /**
     * The shortest string it accepts that a source can carry, or null where every string it accepts
     * is one a source cannot.
     *
     * <p>Beside {@link #shortest}, and a different question. That one answers with what the
     * language holds and prefers a written string at the price of nothing; this one is asked by a
     * caller writing a value into a model, where a string nobody can paste is not an answer at all.
     * A pattern admitting only control characters has a shortest string and no value to offer.
     */
    String shortestWritten() {
        return shortest(WRITABLE, -1);
    }

    /**
     * The symbols a value can be written out of and read back.
     *
     * <p>A control character other than the three a literal spells reaches a source as itself, and
     * half of a pair has nothing to be encoded as — so what a person pastes is not what was chosen.
     * Nothing about the language: a rule admitting one of these admits it, and this is only which of
     * them a value is preferably built from.
     */
    private static final CodePoints WRITABLE = CodePoints.EVERYTHING
            .less(CodePoints.between(0, 8))
            .less(CodePoints.between(0x0B, 0x0C))
            .less(CodePoints.between(0x0E, 0x1F))
            .less(CodePoints.of(0x7F))
            .less(CodePoints.between(0xD800, 0xDFFF));

    /**
     * The shortest string it accepts out of {@code these} and no longer than {@code mostSymbols},
     * or null where there is none. A length of {@code -1} is no bound at all.
     *
     * <p>Walked a length at a time, so the first accepting state met is met by a shortest string.
     * A state is kept the first time it is reached and not again: another way to the same state is
     * no shorter, and what follows it is the same either way.
     */
    private String shortest(CodePoints these, int mostSymbols) {
        java.util.Map<Integer, String> reached = new java.util.LinkedHashMap<>();
        BitSet seen = closure(only(START));
        for (int state = seen.nextSetBit(0); state >= 0; state = seen.nextSetBit(state + 1)) {
            reached.put(state, "");
        }
        int walked = 0;
        while (!reached.isEmpty()) {
            for (java.util.Map.Entry<Integer, String> each : reached.entrySet()) {
                if (accepting.get(each.getKey())) {
                    return each.getValue();
                }
            }
            if (mostSymbols >= 0 && walked == mostSymbols) {
                return null;
            }
            walked++;
            java.util.Map<Integer, String> next = new java.util.LinkedHashMap<>();
            for (java.util.Map.Entry<Integer, String> each : reached.entrySet()) {
                for (Step step : steps.get(each.getKey())) {
                    CodePoints over = step.over().and(these);
                    if (over.isEmpty()) {
                        continue;
                    }
                    String said = each.getValue() + new String(Character.toChars(over.least()));
                    BitSet after = closure(only(step.to()));
                    for (int state = after.nextSetBit(0); state >= 0;
                            state = after.nextSetBit(state + 1)) {
                        next.putIfAbsent(state, said);
                    }
                }
            }
            // Every state met at any shorter length is one nothing longer improves on, so a walk
            // that came back to one has nothing left to find down that way. Held over the whole
            // walk and not over the step before it: a state first met three symbols ago is no less
            // met for the step between.
            next.keySet().removeIf(seen::get);
            if (next.isEmpty()) {
                return null;
            }
            next.keySet().forEach(seen::set);
            reached = next;
        }
        return null;
    }

    /** The states reachable from {@code from} without spending a symbol, those included. */
    private BitSet closure(BitSet from) {
        BitSet out = (BitSet) from.clone();
        int[] pending = new int[out.cardinality()];
        int count = 0;
        for (int each = out.nextSetBit(0); each >= 0; each = out.nextSetBit(each + 1)) {
            pending[count++] = each;
        }
        while (count > 0) {
            int state = pending[--count];
            for (int to : free.get(state)) {
                if (!out.get(to)) {
                    out.set(to);
                    if (count == pending.length) {
                        pending = java.util.Arrays.copyOf(pending, count * 2 + 1);
                    }
                    pending[count++] = to;
                }
            }
        }
        return out;
    }

    private static BitSet only(int state) {
        BitSet out = new BitSet();
        out.set(state);
        return out;
    }

    /** The same machine with every state moved up by {@code by}, written into what is being built. */
    private static void shifted(Automaton machine, int by,
                                List<List<Step>> steps, List<int[]> free) {
        for (int state = 0; state < machine.size(); state++) {
            List<Step> mine = new ArrayList<>();
            for (Step each : machine.steps.get(state)) {
                mine.add(new Step(each.over(), each.to() + by));
            }
            steps.add(mine);
            int[] theirs = machine.free.get(state);
            int[] moved = new int[theirs.length];
            for (int i = 0; i < theirs.length; i++) {
                moved[i] = theirs[i] + by;
            }
            free.add(moved);
        }
    }

    private static void shiftInto(BitSet out, BitSet from, int by) {
        for (int each = from.nextSetBit(0); each >= 0; each = from.nextSetBit(each + 1)) {
            out.set(each + by);
        }
    }

    /**
     * The machine read as one where a walk is only ever in one state, made as it is asked for.
     *
     * <p>Where a machine has not already said what a string ends in, this is what says it: a
     * complement of one that steps for nothing or twice over a symbol, and the one machine any
     * language is kept as. Grown a subset at a time, because the whole of a deterministic machine
     * is what the worst case costs and a walk usually meets far fewer.
     *
     * <p><b>Complete, the subset of no states among them.</b> A symbol with nowhere to go is a walk
     * that ends outside the language, and a machine that simply had no step there would leave every
     * reader to remember what a missing step means.
     *
     * <p>What a step is over is worked out once. The labels of a machine cut the symbols into runs
     * none of which any label splits, so one symbol out of each run answers for the whole of it —
     * and a deterministic machine over a million symbols has as many steps as the pattern has
     * distinct classes.
     */
    private final class Subsets {

        private final List<CodePoints> alphabet = new ArrayList<>();
        private final List<BitSet> subsets = new ArrayList<>();
        private final java.util.Map<BitSet, Integer> known = new java.util.HashMap<>();
        private final List<List<Move>> moves = new ArrayList<>();

        /** One step of the deterministic machine. */
        record Move(CodePoints over, int to) {}

        /** What making these deterministic states is charged to. Its own, because the subsets are
         *  a machine — one this throws away, and one whose states were made all the same. */
        private final Meter.Making making;

        /**
         * Whether anything here steps for nothing, which is whether the closure has anything to
         * add.
         *
         * <p>Asked once, of the machine. Where nothing does, the states reachable without spending
         * a symbol are the states themselves, and the closure of a subset is that subset — so what
         * it comes to is a copy of what it was handed, made for every symbol out of every state.
         */
        private final boolean anyFree;

        Subsets(Meter.Making making) {
            this.making = making;
            boolean found = false;
            for (int[] each : free) {
                found = found || each.length > 0;
            }
            this.anyFree = found;
            cutTheAlphabet();
            at(reached(only(START)));
        }

        /** The subset a walk is in, having got to {@code these}. */
        private BitSet reached(BitSet these) {
            return anyFree ? closure(these) : these;
        }

        int count() {
            return subsets.size();
        }

        /** The runs of symbols this machine tells apart, in the order a step out of a state
         *  answers for them. */
        List<CodePoints> alphabet() {
            return alphabet;
        }

        boolean acceptingAt(int state) {
            return subsets.get(state).intersects(accepting);
        }

        /** The steps out of {@code state}, worked out the first time they are asked for. */
        List<Move> movesFrom(int state) {
            List<Move> said = moves.get(state);
            if (said != null) {
                return said;
            }
            List<Move> out = new ArrayList<>();
            BitSet here = subsets.get(state);
            for (CodePoints run : alphabet) {
                int symbol = run.least();
                BitSet next = new BitSet();
                for (int one = here.nextSetBit(0); one >= 0; one = here.nextSetBit(one + 1)) {
                    for (Step each : steps.get(one)) {
                        if (each.over().has(symbol)) {
                            next.set(each.to());
                        }
                    }
                }
                out.add(new Move(run, at(reached(next))));
            }
            moves.set(state, out);
            return out;
        }

        private int at(BitSet subset) {
            Integer had = known.get(subset);
            if (had != null) {
                return had;
            }
            // Where making a machine deterministic runs away with itself, and so where it is
            // stopped. A subset already met costs nothing; a new one is a state.
            if (!making.state()) {
                throw new TooMany();
            }
            int made = subsets.size();
            subsets.add(subset);
            moves.add(null);
            known.put(subset, made);
            return made;
        }

        /**
         * The runs of symbols no label of this machine tells apart.
         *
         * <p>Cut at every place a label begins or ends. Inside a run every symbol is over exactly
         * the same steps, so one of them answers for all of them — which is what makes a
         * deterministic machine over the whole of Unicode a small thing.
         */
        private void cutTheAlphabet() {
            java.util.TreeSet<Integer> cuts = new java.util.TreeSet<>();
            cuts.add(0);
            for (List<Step> out : steps) {
                for (Step each : out) {
                    for (CodePoints.Range run : each.over().ranges()) {
                        cuts.add(run.from());
                        if (run.to() < CodePoints.LAST) {
                            cuts.add(run.to() + 1);
                        }
                    }
                }
            }
            List<Integer> starts = new ArrayList<>(cuts);
            for (int i = 0; i < starts.size(); i++) {
                int from = starts.get(i);
                int to = i + 1 < starts.size() ? starts.get(i + 1) - 1 : CodePoints.LAST;
                alphabet.add(CodePoints.between(from, to));
            }
        }
    }

    /** What a machine is while it is being made. */
    private static final class Building {

        private final Meter.Making making;
        private final List<List<Step>> steps = new ArrayList<>();
        private final List<List<Integer>> free = new ArrayList<>();

        Building(Meter.Making making) {
            this.making = making;
        }

        int state() {
            if (!making.state()) {
                throw new TooMany();
            }
            steps.add(new ArrayList<>());
            free.add(new ArrayList<>());
            return steps.size() - 1;
        }

        void step(int from, CodePoints over, int to) {
            steps.get(from).add(new Step(over, to));
        }

        void freely(int from, int to) {
            free.get(from).add(to);
        }

        /**
         * The states for {@code syntax}, walked into from {@code from}, and where it leaves off.
         *
         * <p>One entry and one exit apiece, which is what makes the shapes compose without any of
         * them knowing what it is inside. No {@code default}: a shape of syntax added and not built
         * stops the compile rather than being read as whichever arm is nearest.
         */
        int build(PatternSyntax syntax, int from) {
            return switch (syntax) {
                case PatternSyntax.Nothing _ -> from;
                // Nothing leads out of it, so nothing after it is reached and no string gets to
                // the end: a state made and left where it is says exactly that.
                case PatternSyntax.Never _ -> state();
                // Read before anything is built, so there are none left by the time this runs.
                case PatternSyntax.Anchor _ -> throw new IllegalStateException(
                        "an anchor is read into what it comes to before a machine is made of it");
                case PatternSyntax.Symbols it -> {
                    int to = state();
                    step(from, it.held(), to);
                    yield to;
                }
                case PatternSyntax.InTurn it -> {
                    int at = from;
                    for (PatternSyntax each : it.parts()) {
                        at = build(each, at);
                    }
                    yield at;
                }
                case PatternSyntax.EitherOf it -> {
                    int out = state();
                    for (PatternSyntax each : it.arms()) {
                        int in = state();
                        freely(from, in);
                        freely(build(each, in), out);
                    }
                    yield out;
                }
                case PatternSyntax.Repeated it -> repeated(it, from);
            };
        }

        /**
         * A repetition, as the copies it is.
         *
         * <p>The floor is copies one after another; what is above it is copies each of which may be
         * stepped over. An unbounded ceiling is one more copy with a step back to where it began.
         *
         * <p>Written out rather than held as a count. What a machine walks is states, and a
         * repetition of a thing is that thing however many times — so the states are the cost of the
         * language, and the bound a caller passes is what says whether that cost is worth paying.
         */
        int repeated(PatternSyntax.Repeated it, int from) {
            int at = from;
            for (int i = 0; i < it.least(); i++) {
                at = build(it.what(), at);
            }
            if (it.unbounded()) {
                int loop = state();
                freely(at, loop);
                freely(build(it.what(), loop), loop);
                return loop;
            }
            int out = state();
            freely(at, out);
            for (int i = it.least(); i < it.most(); i++) {
                at = build(it.what(), at);
                freely(at, out);
            }
            return out;
        }

        List<List<Step>> frozenSteps() {
            List<List<Step>> out = new ArrayList<>(steps.size());
            steps.forEach(each -> out.add(List.copyOf(each)));
            return List.copyOf(out);
        }

        List<int[]> frozenFree() {
            List<int[]> out = new ArrayList<>(free.size());
            for (List<Integer> each : free) {
                int[] to = new int[each.size()];
                for (int i = 0; i < to.length; i++) {
                    to[i] = each.get(i);
                }
                out.add(to);
            }
            return List.copyOf(out);
        }
    }

    /** More states than the caller allowed, carried to the one place that answers for it. */
    private static final class TooMany extends RuntimeException {

        private static final long serialVersionUID = 1L;

        TooMany() {
            super(null, null, false, false);
        }
    }
}
