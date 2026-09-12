package souther.compiler.check;

import souther.compiler.types.TypeSymbol;

import java.util.ArrayList;
import java.util.List;

/**
 * The rules that govern a value, as the declarations that wrote them publish them.
 *
 * <p>The pair of {@link ExpandedRules} and the one a reading takes. Both answer which clauses apply
 * to a value and whether the walk that found them was short; they differ in what a clause comes
 * back as. A clause here is what its declaration says it states ({@link ClauseMeaning}), which
 * holds no tree and no place — so a reading built on these is a reading nothing about where the
 * declaration is written reaches.
 *
 * <p>{@link #everyRuleReached} is what a reading turns into the widening it already has a word for.
 * It is not "the reading understood every clause" — a clause published as one this compiler has no
 * form for is reached, and is that reading's own limit to report. It is the narrower claim that
 * nothing was left out before the reading began.
 */
public record PublishedRules(List<ClauseMeaning> reached, boolean everyRuleReached) {

    public PublishedRules {
        reached = List.copyOf(reached);
    }

    /** These and {@code other}'s together, reaching everything only where both did. */
    PublishedRules and(PublishedRules other) {
        List<ClauseMeaning> both = new ArrayList<>(reached);
        both.addAll(other.reached);
        return new PublishedRules(both, everyRuleReached && other.everyRuleReached);
    }

    /**
     * The rules that govern a value of {@code named}: the clauses that declaration publishes and
     * the clauses of everything it spreads.
     *
     * <p>Which declarations the walk visits and what each of them states are two authorities with
     * two questions, and neither is asked the other's. What a declaration spreads is that
     * declaration's own answer about itself and is read from what it publishes; whether a name this
     * could not read is one nothing declares at all is the world's, and is the only thing
     * {@code symbols} is asked here.
     *
     * <p>A declaration nothing answered for is rules not reached and not rules there are none of.
     * The two are the same empty list and opposite facts: a declaration that states nothing holds
     * every value its type does, and one whose module nobody could read holds whatever its author
     * wrote. Only where nothing declares it at all are there no rules to be short of — and then
     * there is nothing it spreads to be short of either, which is why the walk stops there.
     */
    static PublishedRules governing(TypeSymbol.AtModule named, Symbols symbols,
                                    PublishedDeclarations published) {
        DeclarationMeaning said = published.of(named.key());
        if (!(said instanceof DeclarationMeaning.Product product)) {
            return new PublishedRules(List.of(),
                    said != null || symbols.declaredNode(named) == null);
        }
        PublishedRules found = new PublishedRules(List.of(), true);
        for (DeclarationReference each : product.includes()) {
            if (each instanceof DeclarationReference.Named it
                    && it.declaration() instanceof TypeSymbol.AtModule spread) {
                found = found.and(governing(spread, symbols, published));
            }
        }
        return found.and(new PublishedRules(product.clauses(), true));
    }
}
