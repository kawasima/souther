package souther.compiler.check;

import souther.compiler.ast.Hir;
import souther.compiler.types.Type;
import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbol;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * What a declaration says, published for a reader in another module.
 *
 * <p>A declaration is already a cut at the module boundary: a reader elsewhere asks for the
 * declaration it names rather than for the module-wide answer it came from. What it says and where
 * it is written are two facts about it, both real and each with readers of its own, and a reader
 * across the boundary means the first. Carried across as the authored tree, the second goes with it
 * — every node of one holds where it was written, so a declaration moved down its file says nothing
 * different and arrives as a declaration that changed.
 *
 * <p><b>Not a projection of {@link Normalized.Def}.</b> The normalized declaration is the authored
 * tree after the constructions in its clauses are written as constructions, and it keeps its
 * positions because the passes below it walk it and report from it. This is a different thing to
 * say, not a filtered version of that one: what goes in is what a module reading somebody else's
 * declaration observes, and a component the tree gains later belongs here only if a reader elsewhere
 * would mean it. Adding one is publishing something, and adding it here silently is what a mirror
 * would do.
 *
 * <p><b>Which kind it is, is what it says.</b> A reader elsewhere asks whether a declaration is a
 * product, a sum or a unit far more often than it asks anything else, so the kinds are the sum
 * rather than a tag beside one field list.
 *
 * <p>Where the declaration is written is answered beside this and never from it, the way
 * {@link ClauseLocations} answers where a clause is: they are two facts about one declaration, and a
 * reader that needs both asks for both under the one address rather than finding the declaration
 * twice.
 */
public sealed interface DeclarationMeaning {

    /** Which declaration this is — the module that wrote it and the name it was written under. */
    TypeKey declares();

    /**
     * What {@code declared} says, read as {@code source} reads a module's declarations.
     *
     * <p>The way in for a caller outside this package, which is every caller that has a compilation
     * rather than a reading of one. What a reading is made of stays here: a caller that assembled
     * one would be choosing which representation a declaration is published in, and that is settled
     * by which module wrote it and not by who is asking.
     */
    public static DeclarationMeaning of(Hir.Def declared, RuleReadingSource source) {
        // A source of its own, because what a clause states is read from somewhere else here: this
        // is where the meanings are made rather than where they are looked up, so a reading under
        // it is not a reading under the one the caller handed over and does not say it is.
        return of(declared, new Clauses(
                new RuleReadingSource(source.symbols(), source.invariants(),
                        ClauseMeanings.THE_ONE_THAT_MAKES_THEM, source.written())));
    }

    /**
     * What the language itself declares says.
     *
     * <p>A second way in, and it is one because there is no reading to make. A reading of a
     * declaration's clauses is made over the scope of the module that wrote it, and no module of a
     * compilation writes what the language declares — asked for one, a compilation answers that it
     * holds no such module, which is true and is not a reason to have nothing to say about the
     * declaration.
     *
     * <p>Nothing is lost by having none. What the library declares is a sum or a unit, and neither
     * of them says anything a reading answers: a sum names its cases and a unit names itself.
     *
     * @throws IllegalStateException where the language declares a product. It would have clauses to
     *     be read, and the reading they would be read in is the thing there is none of. The library
     *     declares none today, and one written tomorrow is a fault in this compiler rather than
     *     something to publish half of
     */
    public static DeclarationMeaning ofLanguage(Hir.Def declared) {
        TypeSymbol.AtModule named = declared.declares();
        return switch (declared) {
            case Hir.SumData sum -> new Sum(named.key(), referencesTo(sum.cases()));
            case Hir.UnitData _ -> new Unit(named.key());
            case Hir.Data _ -> throw new IllegalStateException(
                    "the standard library declares the product `" + named.key() + "`, whose clauses"
                            + " are read in a reading of the module that wrote it, and no module of"
                            + " a compilation writes what the language declares");
        };
    }

    /**
     * The same, over a reading already made.
     *
     * <p>Exhaustive over the kinds a declaration can be, so a kind added to the language arrives
     * here as a compile error rather than as one silently published under whichever arm happened to
     * be last. What each arm leaves out is what says where the declaration stands, and an
     * architecture test walks the components to hold this to it.
     */
    static DeclarationMeaning of(Hir.Def declared, Clauses reading) {
        // The identity the declaration carries, and not one worked out from what it is called. What
        // says which declaration this is, is the component every kind of one holds; a key built
        // from the name and the module beside it would be a second account of the same thing.
        TypeSymbol.AtModule named = declared.declares();
        return switch (declared) {
            case Hir.Data data -> new Product(named.key(), data.newtype(),
                    fieldsOf(data), referencesTo(data.includes()), clausesOf(named, reading));
            case Hir.SumData sum -> new Sum(named.key(), referencesTo(sum.cases()));
            case Hir.UnitData _ -> new Unit(named.key());
        };
    }

    /**
     * The fields written on {@code data}, in the order they are written.
     *
     * <p>Its own and not the ones it reaches. What a value of a product is made of is the fields it
     * spreads followed by the fields it writes, and that closure is a question about this
     * declaration <i>and</i> the ones it names — so it is asked of those meanings together rather
     * than settled inside one of them. Flattened in here, what this declaration says would change
     * when a declaration it spreads gained a field, and the two facts the tree keeps apart would be
     * one.
     *
     * <p>The type is read off the field and nothing else: a field's written type denotes what it
     * denotes wherever the field is read from.
     */
    private static List<Field> fieldsOf(Hir.Data data) {
        List<Field> fields = new ArrayList<>();
        for (Hir.Field each : data.fields()) {
            fields.add(new Field(each.name(), TypeOps.fieldType(each)));
        }
        return fields;
    }

    /**
     * What {@code named} states, clause by clause.
     *
     * <p>In the order the declaration writes them, which is the order a clause is addressed by. Both
     * arms of the reading are carried: a clause this reading has no form for is a clause whose
     * run-time check stands, which is a different thing to publish than a clause that states
     * nothing.
     */
    private static List<ClauseMeaning> clausesOf(TypeSymbol.AtModule named, Clauses reading) {
        List<ClauseMeaning> clauses = new ArrayList<>();
        for (TypeOps.Declared each : reading.of(named).reached()) {
            if (!each.declaredOn().equals(named)) {
                // Written on a declaration this one spreads, and that one publishes it. Carried
                // here as well, what this declaration says would change when another was given a
                // rule it says nothing about — which is what asking for one is for.
                continue;
            }
            Clause.Ref clause = Clause.Ref.of(each);
            clauses.add(switch (reading.typed(each.asExpanded(), named)) {
                case TypedClause.Typed typed -> new ClauseMeaning.Stated(clause,
                        TermMeaning.of(typed.value()),
                        reading.fieldsRead(typed.value(), named),
                        each.shape().asWritten(new RuleRef.Invariant(clause)));
                case TypedClause.Stopped _ -> new ClauseMeaning.Stopped(clause);
            });
        }
        return clauses;
    }

    /** What a list of names in the tree says, which is which declaration each of them reaches. */
    private static List<DeclarationReference> referencesTo(List<Hir.Name> names) {
        List<DeclarationReference> references = new ArrayList<>();
        for (Hir.Name each : names) {
            references.add(switch (each) {
                case Hir.Name.Denoting it -> new DeclarationReference.Named(it.type());
                case Hir.Name.Unanswered it ->
                        new DeclarationReference.Unanswered(it.name().canonical());
            });
        }
        return references;
    }

    /**
     * A product: what a value of it is made of, and what must hold of one.
     *
     * @param fields the fields written on it, in the order they are written — its own, not the ones
     *     it reaches through what it spreads. See {@link Field} for why the order is here at all
     * @param includes what it spreads, which may name a declaration or name nothing
     * @param clauses the clauses written on it, in the order they are written — its own, and not
     *     the ones it reaches through what it spreads, for the reason {@code fields} is its own. The
     *     order is what a reader elsewhere is entitled to: a clause is addressed by which of the
     *     declaration's own it is, so an order is what makes that address mean something
     */
    record Product(TypeKey declares, boolean newtype, List<Field> fields,
                   List<DeclarationReference> includes,
                   List<ClauseMeaning> clauses) implements DeclarationMeaning {

        public Product {
            Objects.requireNonNull(declares, "a declaration is some declaration");
            fields = List.copyOf(fields);
            includes = List.copyOf(includes);
            clauses = List.copyOf(clauses);
        }
    }

    /**
     * One field of a product: what it is called and what type it is.
     *
     * <p>A role and a type, and not the field the author wrote. What a value is read through is the
     * name; how it was spelled and where is the declaring module's business.
     *
     * <p><b>In a list, because the order is something the declaration says.</b> A product's fields
     * are laid out in the order they are reached — what it spreads first, then what it writes — and
     * that order is the order of the parameters of the entry a value of it is built through. A
     * module elsewhere builds one by calling that entry, so two declarations whose fields differ
     * only in order do not say the same thing to it. Carried in a map, they would: what a map
     * compares is which name goes to which type, and it says nothing about the order the pairs come
     * back in.
     */
    record Field(String role, Type type) {

        public Field {
            Objects.requireNonNull(role, "a field is called something");
            Objects.requireNonNull(type, "a field is of some type");
        }
    }

    /** A sum: which declarations a value of it may be one of. */
    record Sum(TypeKey declares, List<DeclarationReference> cases) implements DeclarationMeaning {

        public Sum {
            Objects.requireNonNull(declares, "a declaration is some declaration");
            cases = List.copyOf(cases);
        }
    }

    /**
     * A unit: a declaration with nothing in it.
     *
     * <p>Which is why it carries only its address. There is one value of it, and what a reader
     * elsewhere means by it is that this declaration is the one it is.
     */
    record Unit(TypeKey declares) implements DeclarationMeaning {

        public Unit {
            Objects.requireNonNull(declares, "a declaration is some declaration");
        }
    }
}
