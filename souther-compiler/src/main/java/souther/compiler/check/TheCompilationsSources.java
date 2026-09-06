package souther.compiler.check;

import java.util.function.Function;

/**
 * Where the source a compilation reads a module's rules under is made.
 *
 * <p>Made and not stamped. What such a source says of itself — that a reading from it is the
 * declaration's own as this compilation reads it — is what admits a reader to the reading another
 * reader made, so it may not be said of a pair somebody else assembled. Handed the parts and asked
 * to certify them, this would be certifying whatever it was given: a reader could bring a scope of
 * its own, or a lookup answering for no clause at all, and be lent a reading of rules it was not
 * reading. So nothing is taken but the module's name, and both halves are read from what the
 * compilation answers.
 *
 * <p>Not what a reader holds. A reading of a declaration borrows from
 * {@link DeclarationReadings}, and what that hands out is what somebody has already made; where a
 * module's rules are read from is not a reader's to decide, and is not on offer there.
 */
public final class TheCompilationsSources {

    private final Function<String, Symbols> scopeOf;
    private final ExpandedClauseLookup clauses;

    /**
     * Makes the sources of a compilation whose modules resolve under {@code scopeOf} and whose
     * declarations' clauses are read from {@code clauses}.
     *
     * <p>Both are the compilation's own, and this is the whole of what it takes to be one: whoever
     * builds this is answering for a compilation, which is a different thing from a reader asking
     * one where to read.
     */
    public TheCompilationsSources(Function<String, Symbols> scopeOf, ExpandedClauseLookup clauses) {
        if (scopeOf == null || clauses == null) {
            throw new IllegalArgumentException(
                    "a compilation reads its modules under a scope and reads clauses somewhere");
        }
        this.scopeOf = scopeOf;
        this.clauses = clauses;
    }

    /** The source {@code module}'s rules are read under, or null where the compilation resolves no
     *  such module and there is nothing to read them under. */
    public RuleReadingSource of(String module) {
        Symbols scope = scopeOf.apply(module);
        return scope == null ? null : new RuleReadingSource(scope, clauses, new AModulesRules(module));
    }
}
