package souther.compiler.check;

import souther.compiler.ast.Hir;
import souther.compiler.types.TypeSymbol;

import java.util.ArrayList;
import java.util.List;

/**
 * The conjuncts of the rules written on a type, in the order the author wrote them.
 *
 * <p>Every name the value wears, not the outermost one. A rule written on the type a newtype wraps
 * bounds the value as much as one written on the newtype does, and the two are read together:
 * {@code Inner: value >= 0} under {@code Outer: value <= 10} is a range of {@code [0, 10]}, and
 * neither layer alone says so.
 *
 * <p>The names are handed in, never worked out here. Which names a position wears is what reading
 * its type comes to ({@link TypeView#wrappers}), and a second walk to the same place is a second
 * answer to how far a newtype reaches — they would part the day either of them changed. So this is
 * a projection of that reading and takes it as its subject.
 *
 * <p>Grouped by the name each rule is written on, because a caller that has to put the rules in an
 * order has only this to take it from. Which rules govern a value does not turn on the order — every
 * name's do — so nothing here reads one into them, and a caller enumerating them says for itself
 * which end it starts at.
 *
 * <p>Walked once, because the number a conjunct carries is part of an identity. A line is named by
 * the clause and the conjunct it came out of ({@link DeclaredBounds.Drawn}), counted over every
 * conjunct and not over the ones something came of — so a reading that makes nothing of one
 * conjunct still numbers the next as a reading that makes something of it does. A second walk with
 * its own counter would call one authored line two the day the two disagreed about which conjuncts
 * there are.
 *
 * <p>Nothing is read out of a conjunct here. What a conjunct is <em>about</em> is the canonical
 * quantity its arithmetic came to, which the reading that turns clauses into constraints works out,
 * and where it leaves the values is that same reading's. A reader taking a conjunct's own outermost
 * node for what it states answers about fewer of them than the author wrote — a rule reached
 * through a helper, one written as the denial of its opposite, one naming the value twice — so what
 * is handed out here is the text, for readers whose question is which rule it is.
 */
public final class DeclaredClauses {

    /**
     * One conjunct of one rule of a declaration.
     *
     * @param part which part of which rule it is, as the split that wrote the parts down named it
     * @param expr the conjunct itself
     */
    public record Conjunct(PartId<RuleRef.Invariant> part, Hir.Expr expr) {

        public Conjunct {
            if (part == null || expr == null) {
                throw new IllegalArgumentException("a conjunct is some clause's text");
            }
        }
    }

    /**
     * The conjuncts written on one of the names a value wears.
     *
     * @param worn      the name, as the reading of the position read it off
     * @param conjuncts every conjunct of every rule written on it, in the order the author wrote
     *                  them; empty where the name carries none
     */
    public record OnAName(TypeSymbol worn, List<Conjunct> conjuncts) {

        public OnAName {
            conjuncts = List.copyOf(conjuncts);
        }
    }

    /** Every conjunct of every rule written on the names {@code worn}, one entry per name, in the
     *  order the names were read off the position. */
    public static List<OnAName> of(List<TypeSymbol> worn, RuleReadingSource source) {
        List<OnAName> out = new ArrayList<>();
        for (TypeSymbol wears : worn) {
            out.add(new OnAName(wears, writtenOn(wears, source)));
        }
        return List.copyOf(out);
    }

    private static List<Conjunct> writtenOn(TypeSymbol wears, RuleReadingSource source) {
        // A name a module wrote is the only one there are rules of to read: what the language
        // declares carries none. Which declaration that name is, and what it states, are the walk's
        // to read from the world and the lookup it holds.
        if (!(wears instanceof TypeSymbol.AtModule named)) {
            return List.of();
        }
        List<Conjunct> out = new ArrayList<>();
        // The clauses with the declaration each was written on, which is what names the line
        // (ADR-0090). Read flat, every clause a spread brought in was named after the type that
        // spread it, and two clauses of one declaration were one rule.
        for (TypeOps.Declared declared : TypeOps.expandedInvariants(
                named, source.symbols(), source.invariants()).reached()) {
            // The parts the clause was split into, with the tree the expansion made of each. Split
            // again here, this would be a second answer to which parts a clause has — taken off a
            // tree an expansion left, where what a helper's body joined is as much a conjunction as
            // what the author wrote.
            for (AuthoredShape.Written each : declared.parts()) {
                out.add(new Conjunct(each.id(), each.read()));
            }
        }
        return out;
    }

    private DeclaredClauses() {}
}
