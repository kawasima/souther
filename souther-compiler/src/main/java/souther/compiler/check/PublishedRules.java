package souther.compiler.check;

import souther.compiler.types.TypeSymbol;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
     *
     * <p><b>{@code found} is what this walk has already worked out, and it is asked at every step.</b>
     * A declaration reached twice is walked once: what governs it does not turn on which of the
     * types that spread it was asked, so a second walk of it is the same walk. It is asked at every
     * step rather than only at the top because a reading walks each declaration it is asked about,
     * and one spread deep in a chain is under many of them.
     *
     * <p>What it is not is a way of counting a clause once. Whether a value can reach one
     * declaration by two paths at all is the language's to say, and it says no: each path brings
     * that declaration's fields with it and the second is refused as a field written twice
     * ({@code TwoSpreadsThatMeetOneDeclarationAreRefusedTest}). So the table is asked twice for one
     * declaration only down one path, where the answer is the same answer — and a language that let
     * the paths meet would be one where this has a question to answer, which is what that check is
     * there to make come back.
     *
     * <p>An entry is written only once the walk under it has come back. A declaration that spreads
     * its way round to itself therefore meets no entry of its own, as it met none before there was a
     * table here; standing one in for a walk still running would make what a type is held to turn on
     * which of the types in the ring was asked for first. No reading meets that today — a ring is
     * refused nowhere and the compile ends in the walk over what a value's fields are, before any
     * rule of it is read — so what this says is only that the table did not decide it.
     */
    static PublishedRules governing(TypeSymbol.AtModule named, Symbols symbols,
                                    PublishedDeclarations published,
                                    Map<TypeSymbol.AtModule, PublishedRules> found) {
        PublishedRules known = found.get(named);
        if (known != null) {
            return known;
        }
        PublishedRules out = walked(named, symbols, published, found);
        found.put(named, out);
        return out;
    }

    /** What {@code named} publishes and what its spreads do, walked — see {@link #governing}. */
    private static PublishedRules walked(TypeSymbol.AtModule named, Symbols symbols,
                                         PublishedDeclarations published,
                                         Map<TypeSymbol.AtModule, PublishedRules> found) {
        DeclarationMeaning said = published.of(named.key());
        if (!(said instanceof DeclarationMeaning.Product product)) {
            // Whether anything declares it, which is all the world is asked here. Reaching for the
            // declaration to find out would make a walk over what declarations publish depend on
            // where one of them is written.
            return new PublishedRules(List.of(), said != null || !symbols.declares(named.key()));
        }
        PublishedRules out = new PublishedRules(List.of(), true);
        for (DeclarationReference each : product.includes()) {
            if (each instanceof DeclarationReference.Named it
                    && it.declaration() instanceof TypeSymbol.AtModule spread) {
                out = out.and(governing(spread, symbols, published, found));
            }
        }
        return out.and(new PublishedRules(product.clauses(), true));
    }
}
