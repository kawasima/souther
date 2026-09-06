package souther.compiler.check;

/**
 * What reading a module's declarations as a static analysis takes: the scope the names resolve in,
 * and where the clauses in the representation it reads come from.
 *
 * <p>The two together because a reading takes both, and for no reason beyond that. Most of what
 * carries this reads only the scope and hands the pair on; separated, those would thread two
 * arguments where they thread one, which is plumbing rather than a distinction anyone makes.
 *
 * <p><b>It states no relation between them.</b> Not that they are the same module's, not that one
 * answers for the other, not that a declaration reached through the scope is one the lookup has.
 * There is nothing of that kind to state: a scope belongs to the module being read, and a clause
 * belongs to the declaration that wrote it, wherever that was. This pair used to require that the
 * two named one module, which is exactly the claim that made an imported declaration's clauses come
 * back in whatever representation the reader happened to hold, so the requirement is gone and
 * nothing here or in a constructor puts it back.
 *
 * <p>Nothing else about the reading goes in here. What a reading may spend is a bound on the work
 * and not part of what is being read ({@link ReadingPolicy}), and it stays a separate argument:
 * joined, how much a declaration may cost and where its clauses come from would be one value, and a
 * caller changing either would be changing both.
 *
 * <p>{@link Origin} is not of that kind and is here for the opposite reason: it says nothing about
 * what is read and everything about which source this is. The pair itself cannot say — a scope and
 * a lookup are capabilities, so two of them built from one compilation for one module are two
 * objects that answer alike and compare unlike — and a reader that wanted to know whether two
 * readings were of the same source would be left comparing capabilities. So whoever makes one says
 * which it is, where it is made.
 *
 * @param symbols    the module's resolved scope
 * @param invariants where a declaration's clauses in the representation this reads are answered from
 * @param origin     which source this is, for a reader telling two of them apart
 */
public record RuleReadingSource(Symbols symbols, ExpandedClauseLookup invariants, Origin origin) {

    public RuleReadingSource {
        if (symbols == null || invariants == null || origin == null) {
            throw new IllegalArgumentException(
                    "reading a declaration's rules takes a scope, somewhere to read clauses from,"
                            + " and which source that is");
        }
    }

    /**
     * A source made for one reading, which nobody else can name.
     *
     * <p>The answer where nothing else is said, and the safe one: a reading made from it is a
     * reading nothing shares, so a reader that wraps a lookup to watch what it is asked — or builds
     * a scope of its own — gets what it built and no one else's.
     */
    public RuleReadingSource(Symbols symbols, ExpandedClauseLookup invariants) {
        this(symbols, invariants, Origin.ofOneReading());
    }

    /**
     * Which source a reading was made from, as a value.
     *
     * <p>Two readings are of one source when their sources have one origin. That is what a lender of
     * readings asks, and it is asked of this rather than of the pair above for the reason the pair
     * cannot answer it.
     */
    public sealed interface Origin {

        /**
         * The reading of {@code module}'s rules that a compilation holds: the one every reader that
         * asks the compilation where to read is given, however many of them ask and however many
         * pairs are built to hand it over.
         *
         * <p>Written by whoever answers for a compilation and by nobody else. Written elsewhere, it
         * would say of a scope somebody assembled that it is the one the compilation reads under,
         * and a reading made from it would be lent to readers of the compilation's own.
         */
        record OfAModulesRules(String module) implements Origin {

            public OfAModulesRules {
                if (module == null) {
                    throw new IllegalArgumentException("a module's rules are read under its name");
                }
            }
        }

        /** A source somebody made for a reading of their own. Two of these are never one. */
        record OfOneReading(long ordinal) implements Origin {}

        /** One nobody else can name. */
        static Origin ofOneReading() {
            return new OfOneReading(READINGS.incrementAndGet());
        }

        java.util.concurrent.atomic.AtomicLong READINGS =
                new java.util.concurrent.atomic.AtomicLong();
    }
}
