package souther.compiler.check;

import souther.compiler.types.Type;
import souther.compiler.types.TypeSymbol;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The atoms a type is made of: the cases a value of it can be that are not themselves sums.
 *
 * <p>A sum whose case is a sum is transparent as a value — anything of the inner one is of the
 * outer one (spec §sum-data) — so what a value of the outer sum can be is not its case list but
 * what descending that list reaches. That descent is what this is, and it is written once. Four
 * readers used to write it themselves, each keyed on what suited it, and they answered differently
 * where the descent reaches one case twice.
 *
 * <p>An atom here is a <em>case</em> identity and not the atom of {@link Terms} and the numeric
 * readings, which is a term a reading cannot take further apart. The two never meet: this answers
 * what a value may be, and that one names where a value came from. Said because the word carries
 * both in this compiler and a reader arriving at one from the other has to be told which.
 *
 * <p>An atom is one per {@link TypeSymbol}. Two cases naming one type are one atom however many
 * declarations reach it: the type is what a value is, what a codec tags, and what an arm tests, so
 * a reader keyed on anything else counts one thing as two.
 *
 * <p>What comes out is unique and deterministic. Within a declaration it is the order the cases are
 * written in, first reach keeping the place — a derived codec writes its variants in it (spec
 * §sum-discrimination), so an order that came out of whichever collection was reached for would
 * move a generated artifact nothing about the program had changed. An anonymous union holds its
 * members as a set and states no order of its own, so its members are taken in their name's order
 * rather than in the set's: an order nothing decided is one that can differ between two runs of one
 * compiler, which is the same defect from further away.
 */
public final class AtomSpace {

    private AtomSpace() {}

    /**
     * What a value of {@code t} can be, descending every case that is itself a sum.
     *
     * <p>Answers for anything, not only a sum: a type that is no sum is the one atom it is, and a
     * type that names no case at all — {@code Raw}, an optional, a type the compiler could not work
     * out — has none. Which of those a reader treats as an answer and which as a refusal is the
     * reader's, and asking here does not decide it.
     *
     * <p>One entry and not one per shape. A sum asked about through its declaration rather than its
     * type would be a second way of deciding where the descent starts, and the two would answer
     * alike until the day one of them was extended.
     *
     * <p>Asked of what the declarations publish and not of a world's declarations. Which cases a sum
     * has is what that declaration says about itself, so a reader here means nothing by the tree it
     * was written in — and reading one would answer differently for the same sum every time a line
     * above it moved.
     */
    public static List<TypeSymbol> subjectAtoms(Type t, PublishedDeclarations published) {
        Set<TypeSymbol> atoms = new LinkedHashSet<>();
        descend(roots(t), published, atoms, new HashSet<>());
        return List.copyOf(atoms);
    }

    /**
     * The names to descend from, in the order the type states them.
     *
     * <p>A union states none — it holds a set — so one is put on it here. Everything else names one
     * type and there is nothing to order.
     */
    private static List<TypeSymbol> roots(Type t) {
        if (t instanceof Type.Union union) {
            return statedBy(union);
        }
        return List.copyOf(TypeOps.namesOf(t));
    }

    /**
     * The members of {@code union}, in the order a union states them.
     *
     * <p>A union states none — {@link Type.Union} holds a set — so one is put on it, and it is put
     * on here so that there is one. Every reader of a union's members answers about the same union,
     * and two of them ordering it differently is two answers to a question the type does not have:
     * a set built one way iterates one way and a set built another iterates another, so an order
     * taken from the collection is an order that can differ between two runs of one compiler.
     *
     * <p>By name, which is the only thing a union's members have that the union itself did not
     * choose. What is downstream of it is a derived artifact's contents and the order a report
     * reads in, and neither may move because of how a set happened to be built.
     *
     * <p>Not what {@link Type#show} does with a union, which renders the members as it finds them.
     * Putting this there would render the standard library's `+Int | DivisionByZero+` as
     * `+DivisionByZero | Int+` — the order a reader wants there is the one the author wrote, and a
     * union does not keep it. That is a wider question than the order an answer comes out in.
     */
    static List<TypeSymbol> statedBy(Type.Union union) {
        List<TypeSymbol> members = new ArrayList<>(union.members());
        members.sort(null);
        return members;
    }

    /**
     * Collects the atoms under {@code names}, descending the ones that are sums.
     *
     * <p>{@code expanded} is the sums already taken apart, which is what a sum reaching itself
     * terminates on and what keeps a sum reached through two cases from being descended twice.
     * Such a declaration is refused where it is written ({@link DataChecker}); this only has to
     * come back.
     */
    private static void descend(Iterable<TypeSymbol> names, PublishedDeclarations published,
                                Set<TypeSymbol> atoms, Set<TypeSymbol> expanded) {
        for (TypeSymbol name : names) {
            // What the language declares is a case and never a sum, and it has no address to ask
            // about: a name that is not a module's is an atom without anything being asked.
            if (name instanceof TypeSymbol.AtModule at
                    && published.of(at.key()) instanceof DeclarationMeaning.Sum sum) {
                if (expanded.add(name)) {
                    descend(declaredCases(sum), published, atoms, expanded);
                }
            } else {
                atoms.add(name);
            }
        }
    }

    /** The declarations {@code sum} lists, in the order it lists them. A name it lists that reaches
     *  nothing is no case: it is reported where it is written, and a reader counting what a value
     *  can be counts what is there. */
    static List<TypeSymbol> declaredCases(DeclarationMeaning.Sum sum) {
        List<TypeSymbol> named = new ArrayList<>();
        for (DeclarationReference each : sum.cases()) {
            if (each instanceof DeclarationReference.Named it) {
                named.add(it.declaration());
            }
        }
        return named;
    }
}
