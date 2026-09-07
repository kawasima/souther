package souther.compiler.regex;

/**
 * A set of strings this compiler can answer about exactly.
 *
 * <p><b>Asking is free and composing is not.</b> Everything a caller may ask one of these — whether
 * it holds a string, whether it holds nothing, whether it holds everything, whether it is the same
 * set as another — is read off the machine in front of it. What is not free is making a new
 * language out of two: a meet is the product of two machines and may be larger than this compiler
 * will build, so every way to a language takes what it is allowed and answers null past it.
 *
 * <p>Which is the one thing a caller has to hold: a language is a set and may be asked anything,
 * and putting two of them together is an operation that can decline. Written the other way round —
 * composition total, questions doing the work — the work would happen inside whichever question was
 * asked first, where there is no allowance and nobody counting.
 *
 * <p><b>And it is why one is not made from a pattern.</b> Whether the work of answering exactly is
 * worth doing is a question about the answer being built, not about any one pattern in it: a
 * pattern read on its own and admitted on its own is one that may still be met with another, and
 * the meet is where the cost is. So a language is compiled from the whole of what a question needs
 * at once ({@link PatternPlan}), and what a reading spends putting them together is held beside the
 * reading ({@code souther.compiler.values.Sets}).
 *
 * <p>Nothing here is about how a language was written. Two patterns that accept the same strings are
 * the same language, and a reader asking what one holds is answered by the strings.
 */
public final class Language {

    /**
     * The one machine that accepts these strings, which is what everything here is read off.
     *
     * <p>Canonical, and stopping on nothing but strings. Both are the type's invariant rather than
     * things a caller arranges, and every way to a language goes through {@link #canonical} — so
     * two of these hold the same table exactly when they hold the same strings, and the questions
     * below are a look at what is in front of them rather than a search nobody counted.
     */
    private final Automaton machine;

    Language(Automaton canonical) {
        if (canonical == null) {
            throw new IllegalArgumentException("a language is some machine");
        }
        this.machine = canonical;
    }

    /**
     * The strings {@code made} stops on, where making the one machine for them is within what
     * {@code meter} allows.
     *
     * <p>Null and never a machine short of canonical. What is being decided is whether this
     * compiler can afford to answer exactly, and a half-made answer handed out is one whose next
     * question does the rest of the work somewhere nobody is counting.
     *
     * <p><b>The sequences no string is read as are taken out by {@link #holdingOnlyStrings}, and
     * nowhere else.</b> Every way to one of these ends there, this one and {@link #not}. A machine
     * steps over what a matcher reads, and a high surrogate followed by a low one is one symbol
     * rather than two — so there are sequences of symbols no string is written as, and a complement
     * holds them like anything else. Left in, two machines telling each other apart only over those
     * would be two languages holding the same strings and comparing unequal, and a machine holding
     * nothing but them would say it holds something. Every question below would then be about
     * sequences where the type says it is about strings, and each of its callers would have to know
     * that and take them out for itself — which is the reading of one thing in two places this file
     * exists to stop.
     *
     * <p>Skipped where it can change nothing, which is asked of the one machine and not of the
     * steps it is written with. A canonical machine is complete, so every one of them has a step
     * over a high surrogate and having one says nothing; what says something is whether such a step
     * leads anywhere a walk that then reads a low surrogate may stop. A pattern naming no surrogate
     * leads to the state nothing stops at, and nothing is built for it.
     *
     * <p>Which is why the one machine is made first and made again only where the answer is yes.
     * Asked of what came in, the walk would have to follow the steps that cost no symbol, and what
     * it is looking for is two symbols in turn.
     */
    static Language canonical(Automaton made, Meter meter) {
        if (made == null) {
            return null;
        }
        Automaton one = made.canonical(meter);
        return one == null ? null : holdingOnlyStrings(one, meter);
    }

    /**
     * The strings {@code one} stops on, where {@code one} is already the one machine for the
     * sequences it stops on.
     *
     * <p>Beside {@link #canonical} rather than inside it, because there is a way to a canonical
     * machine that is not a construction: see {@link #not}. Both end here, which is what makes this
     * the one place the sequences come out; why they have to and where the walk is skipped is
     * written on {@link #canonical}, which is where a reader arrives from.
     */
    private static Language holdingOnlyStrings(Automaton one, Meter meter) {
        if (!one.mayStopHavingReadALoneSurrogatePair()) {
            return new Language(one);
        }
        Automaton held = one.and(RuntimeOrder.READS_ONLY_STRINGS, meter);
        Automaton settled = held == null ? null : held.canonical(meter);
        return settled == null ? null : new Language(settled);
    }

    /** Whether the whole of {@code value} is in it. A walk over the value, which builds nothing. */
    public boolean has(String value) {
        return machine.accepts(value);
    }

    /**
     * Every string that comes before {@code than} on the strings' own order, or null past what
     * {@code meter} allows.
     *
     * <p>The order the runtime makes and a model's {@code <} is, which is not the order the symbols
     * of a machine are in — so this is built rather than read off an alphabet, and where it is built
     * is {@link RuntimeOrder}. Metered like every other way to a language: what a caller is told
     * past the allowance is that this was not made.
     *
     * <p>Here rather than in the layer that reasons about where a language stops, because a language
     * is what this returns and a machine is what makes one. What such a caller does with it — meet
     * it, take it away, ask whether two of them are one — is what a language already answers.
     */
    public static Language before(String than, Meter meter) {
        return canonical(RuntimeOrder.before(than, meter), meter);
    }

    /** Every string there is, which is what a rule saying nothing leaves. */
    public static final Language EVERY_STRING = new Language(RuntimeOrder.EVERY_STRING);

    /**
     * The strings {@code words} and no others, or null past what {@code meter} allows.
     *
     * <p>For a caller holding values a rule wrote out rather than a pattern. What they are is a set
     * of strings like any other, and a reader asking where a rule's values stop has one question
     * whichever way the rule named them — handed only the patterns, it would have to ask the values
     * written out something else, and the two answers would be about the same strings.
     */
    public static Language ofWords(java.util.Collection<String> words, Meter meter) {
        Automaton made = Automaton.ofWords(words, meter);
        return made == null ? null : canonical(made, meter);
    }

    /**
     * The least string it holds, or null where it holds none and where the ones it holds have no
     * least among them.
     *
     * <p>Free, like everything else asked of one of these: read off the canonical machine, and
     * nothing is built. The two nulls are one answer here because neither is a string — a caller
     * telling them apart asks {@link #isEmpty}, which is free as well.
     *
     * <p>Not {@link #some}. That one answers with the shortest, which is a different string: a
     * language of two-letter words holds no shorter one, and which of them comes first is what this
     * is about.
     */
    public String least() {
        return RuntimeOrder.leastOf(machine);
    }

    /** The strings both hold, or null where making them ran past what {@code meter} allows. */
    public Language and(Language other, Meter meter) {
        return canonical(machine.and(other.machine, meter), meter);
    }

    /** The strings either holds, on the same terms. */
    public Language or(Language other, Meter meter) {
        return canonical(machine.or(other.machine, meter), meter);
    }

    /**
     * The strings this does not hold, on the same terms.
     *
     * <p><b>Not made canonical afterwards, because it arrives that way.</b> What is held here is
     * the one machine for these strings, and the complement of such a machine is that machine with
     * the states it stops at turned over ({@link Automaton#not}) — the steps are the same steps, so
     * a machine already smallest and already numbered by a walk over its steps stays both. Made
     * canonical again, the construction would arrive where it started, having paid for the walk.
     *
     * <p>The sequences no string is read as still have to come out. A complement holds them like
     * anything else, which is why this ends where every other way to one of these ends.
     */
    public Language not(Meter meter) {
        Automaton turned = machine.not(meter);
        return turned == null ? null : holdingOnlyStrings(turned, meter);
    }

    /**
     * Whether it holds nothing at all.
     *
     * <p>Read off the canonical machine: one that holds nothing is one state nothing stops at. An
     * observation, as everything below is — the walking was done where it was paid for.
     */
    public boolean isEmpty() {
        return machine.holdsNothing();
    }

    /**
     * Whether it holds every string there is.
     *
     * <p>Asked of the strings and not of how it was written. {@code .*} is not everything — the
     * five line terminators are outside it — and a reader answering from the shape of a pattern
     * would say it was.
     *
     * <p>Which is the one machine every string stops at and not the one state every symbol does:
     * the sequences no string is read as are not in any language here, so a language holding every
     * string is a machine that turns those away and stops on the rest.
     */
    public boolean isEverything() {
        return equals(EVERY_STRING);
    }

    /**
     * One string it holds, or null where it holds none.
     *
     * <p>Never null for a language that has something, which is what ties it to {@link #isEmpty}.
     * The shortest, and among those one a source can carry where there is one — being writable is
     * preferred and is never a condition, since what a rule admits is not narrowed by what a person
     * can paste.
     *
     * <p>A value for a row is a further question and not this one. What is wanted there is a string
     * somebody reads, and a language holds what it holds.
     */
    public String some() {
        return machine.shortest();
    }

    /**
     * One string it holds that a source can carry, or null where it holds none such.
     *
     * <p>What a caller writing a value into a model wants, which is not {@link #some}. That answers
     * with what the language holds and prefers a written string where there is one at the same
     * length; here a string nobody can paste is not an answer, so a language of control characters
     * has one to offer and none to write.
     */
    public String someWritten() {
        return machine.shortestWritten();
    }

    /**
     * The same strings and these others, or null past what {@code meter} allows.
     *
     * <p>Takes the words themselves rather than a plan. What joining them costs is the words — a
     * set of them is a machine as big as their letters — but what the answer costs is what making
     * it canonical costs, and that is a question about the two together. So this is metered like
     * every other way to a language.
     */
    public Language with(java.util.Collection<String> words, Meter meter) {
        if (words.isEmpty()) {
            return this;
        }
        Automaton theirs = Automaton.ofWords(words, meter);
        return theirs == null ? null : canonical(machine.or(theirs, meter), meter);
    }

    /** The same strings less these, on the same terms. */
    public Language without(java.util.Collection<String> words, Meter meter) {
        if (words.isEmpty()) {
            return this;
        }
        Automaton theirs = Automaton.ofWords(words, meter);
        Automaton left = theirs == null ? null : theirs.not(meter);
        return left == null ? null : canonical(machine.and(left, meter), meter);
    }

    /** How many states hold it, which is what a caller answering for its work counts. */
    public int size() {
        return machine.size();
    }

    /**
     * Whether the two hold the same strings.
     *
     * <p>The strings and not the states. A language is a set, and two patterns that accept the same
     * strings are one — so a reading run twice over one model has to come to values that are equal,
     * whatever shape the machines took on the way.
     *
     * <p><b>A comparison of two tables and not a proof about two languages.</b> Every language holds
     * the one machine that accepts its strings ({@link Automaton#canonical}), so two that hold the
     * same strings hold the same table, state for state. Which matters because of who calls this: a
     * set adding a member calls it, and a map looking one up calls it, and neither of them is a
     * place where an answer may go and build automata. The work that settles what a language is was
     * done where it was paid for.
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof Language it && machine.sameAs(it.machine);
    }

    /**
     * Read off the same table, so that two equal languages hash alike.
     *
     * <p>The strings are what makes two of these equal, and the canonical machine is what makes the
     * strings something a hash can be read off: one language is one table, so hashing the table
     * hashes the language.
     */
    @Override
    public int hashCode() {
        return machine.shape();
    }

    /**
     * The whole of what this language is, written out for a caller putting several of them in an
     * order.
     *
     * <p>Agrees with {@link #equals} both ways, being read off the same canonical table, and unlike
     * {@link #hashCode} it says which of two unequal ones comes first without ever saying that of
     * two equal ones. Costs nothing to ask: the machine it reads was built when the language was.
     */
    public void writtenInto(StringBuilder out) {
        machine.writtenInto(out);
    }

    @Override
    public String toString() {
        String some = some();
        return "language of " + (some == null ? "nothing" : "\"" + some + "\" and such");
    }
}
