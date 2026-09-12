package souther.compiler.check;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

/**
 * Where the source a compilation reads a module's rules under is made.
 *
 * <p>Made and not stamped. What such a source says of itself — that a reading from it is the
 * declaration's own as this compilation reads it — is what admits a reader to the reading another
 * reader made, so it may not be said of a pair somebody else assembled. Handed the parts and asked
 * to certify them, this would be certifying whatever it was given: a reader could bring a scope of
 * its own, or a lookup answering for no clause at all, and be handed a reading of rules it was not
 * reading. So nothing is taken here but the module's name, and both halves are read from what the
 * compilation answers.
 *
 * <p>And what is minted here says it came from here. Anybody may build one of these over a scope
 * and a lookup of their own — there is no keeping that from them, since answering for a
 * compilation is exactly what it is to hold those two — but a source minted by one of these is
 * shared only with readers whose source came from the same one. So a rival mint over a scope it
 * borrowed and a lookup answering for nothing mints sources nobody else's readers are handed,
 * whatever module name it writes on them. What makes a source the compilation's is that the
 * compilation's own sources made it, and a store holds one of these.
 *
 * <p>Not what a reader holds. A reading of a declaration borrows from
 * {@link DeclarationReadings}, and what that hands out is what somebody has already made; where a
 * module's rules are read from is not a reader's to decide, and is not on offer there.
 */
public final class TheCompilationsSources {

    private static final AtomicLong MINTS = new AtomicLong();

    private final Function<String, Symbols> scopeOf;
    private final ExpandedClauseLookup clauses;
    private final PublishedDeclarations published;
    private final DeclarationKinds kinds;
    private final ClauseLocations written;

    /** Which mint this is, told to nobody: what it stamps says this and what another stamps says
     *  another, which is the whole of how a source of one is told from a source of the other. */
    private final long mint = MINTS.incrementAndGet();

    /**
     * Makes the sources of a compilation whose modules resolve under {@code scopeOf}, whose
     * declarations' clauses are read from {@code clauses}, and where a clause is written is read
     * from {@code written}.
     *
     * <p>All the compilation's own, and this is the whole of what it takes to be one: whoever builds
     * this is answering for a compilation, which is a different thing from a reader asking one where
     * to read.
     */
    public TheCompilationsSources(Function<String, Symbols> scopeOf, ExpandedClauseLookup clauses,
                                  PublishedDeclarations published, DeclarationKinds kinds,
                                  ClauseLocations written) {
        if (scopeOf == null || clauses == null || published == null || kinds == null
                || written == null) {
            throw new IllegalArgumentException(
                    "a compilation reads its modules under a scope, reads clauses somewhere, reads"
                            + " what a declaration says somewhere, and reads where one is written"
                            + " somewhere");
        }
        this.scopeOf = scopeOf;
        this.clauses = clauses;
        this.published = published;
        this.kinds = kinds;
        this.written = written;
    }

    /** The source {@code module}'s rules are read under, or null where the compilation resolves no
     *  such module and there is nothing to read them under. */
    public RuleReadingSource of(String module) {
        Symbols scope = scopeOf.apply(module);
        return scope == null ? null
                : new RuleReadingSource(scope, clauses, published, kinds, written,
                        new AModulesRules(mint, module));
    }
}
