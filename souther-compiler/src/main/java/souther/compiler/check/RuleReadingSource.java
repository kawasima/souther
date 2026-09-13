package souther.compiler.check;

/**
 * What reading a module's declarations as a static analysis takes: the scope the names resolve in,
 * where the clauses in the representation it reads come from, and where a clause it reports about
 * is written.
 *
 * <p>Together because a reading takes them all, and for no reason beyond that. Most of what carries
 * this reads only the scope and hands the rest on; separated, those would thread several arguments
 * where they thread one, which is plumbing rather than a distinction anyone makes.
 *
 * <p><b>It states no relation between them.</b> Not that they are the same module's, not that one
 * answers for the other, not that a declaration reached through the scope is one the lookup has.
 * There is nothing of that kind to state: a scope belongs to the module being read, and a clause
 * belongs to the declaration that wrote it, wherever that was. This used to require that the scope
 * and the lookup named one module, which is exactly the claim that made an imported declaration's
 * clauses come back in whatever representation the reader happened to hold, so the requirement is
 * gone and nothing here or in a constructor puts it back.
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
 * @param published  where what a declaration says is answered from, which is the declaration
 *                   itself. Which clauses it has, what each of them states and what it spreads are
 *                   all read from here, and that is what a reading of a declaration's rules is
 *                   built on. Beside {@code invariants} and not inside it: {@code invariants} is
 *                   the tree a module expanded its own clauses into, which is what a declaration
 *                   answering for itself reads and what nothing reading another module's
 *                   declaration may
 * @param kinds      which form each declaration was written in. Beside {@code published} and not
 *                   inside it: the form was settled when the module was indexed and what the
 *                   declaration says is worked out well above that, so a reader telling a sum from
 *                   a product depends on the first alone — and may ask it where asking the second
 *                   would be asking for an answer still being worked out
 * @param newtypes   which declarations were written as one value wearing a name. Beside
 *                   {@code kinds} and not a fourth value of it: a product written over again as a
 *                   sum changes form and is no more a newtype than it was, so a reader asking only
 *                   this keeps its answer through that edit
 * @param inners     what each declaration that wears one value wraps. Beside {@code newtypes} and
 *                   not inside it, because the two are answerable at different times: whether a
 *                   declaration wears one value was settled when the module was indexed, and what it
 *                   wraps is not settled until the names in it resolve
 * @param written    where a clause of a declaration is written, for the sentences this reading
 *                   produces that point at one. Beside {@code invariants} and not inside it: what a
 *                   clause states is what the reading is built on, and where it is written is what
 *                   one sentence puts a caret under. Answered together, an edit that moves a clause
 *                   and changes nothing it states is an edit that changes what the model says
 * @param origin     which source this is, for a reader telling two of them apart
 */
public record RuleReadingSource(Symbols symbols, ExpandedClauseLookup invariants,
                                PublishedDeclarations published, DeclarationKinds kinds,
                                DeclarationNewtypes newtypes, NewtypeInners inners,
                                ClauseLocations written, Origin origin) {

    public RuleReadingSource {
        if (symbols == null || invariants == null || published == null || kinds == null
                || newtypes == null || inners == null || written == null || origin == null) {
            throw new IllegalArgumentException(
                    "reading a declaration's rules takes a scope, somewhere to read clauses from,"
                            + " somewhere to read what a declaration says, somewhere to read where"
                            + " one is written, and which source that is");
        }
    }

    /**
     * A source whose reader has not been handed what the declarations wrap.
     *
     * <p>The inners are read off {@code symbols} instead, which is what a reading built out of a
     * scope alone can answer from. Every caller of this is a reading that has not crossed the cut,
     * and a test counts them.
     */
    public RuleReadingSource(Symbols symbols, ExpandedClauseLookup invariants,
                             PublishedDeclarations published, DeclarationKinds kinds,
                             DeclarationNewtypes newtypes, ClauseLocations written) {
        this(symbols, invariants, published, kinds, newtypes, NewtypeInners.asWritten(symbols),
                written);
    }

    /** A source made for a reading of its own, which nobody else can name. */
    public RuleReadingSource(Symbols symbols, ExpandedClauseLookup invariants,
                             PublishedDeclarations published, DeclarationKinds kinds,
                             DeclarationNewtypes newtypes, NewtypeInners inners,
                             ClauseLocations written) {
        this(symbols, invariants, published, kinds, newtypes, inners, written,
                AReadingOfItsOwn.next());
    }

    /**
     * Which source a reading was made from.
     *
     * <p>Two readings are of one source when their sources have one origin. That is what a lender of
     * readings asks, and it is asked of this rather than of the pair above for the reason the pair
     * cannot answer it.
     *
     * <p>What it may say is written where a source is made and nowhere else. A source built by
     * whoever is reading is one of its own, which nothing shares; the one a compilation reads a
     * module's rules under is made where its sources are ({@link TheCompilationsSources}), from
     * what the compilation answers rather than from anything a reader brings. Left as a name
     * anybody could write, a reader assembling a scope of its own could have said its source was
     * the compilation's, and been handed a reading of rules it was not reading.
     */
    public sealed interface Origin permits AModulesRules, AReadingOfItsOwn {}
}
