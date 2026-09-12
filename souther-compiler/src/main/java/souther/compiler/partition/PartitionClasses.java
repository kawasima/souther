package souther.compiler.partition;

import souther.compiler.check.RuleReadingContext;
import souther.compiler.check.RuleReadingSource;
import souther.compiler.check.Shape;
import souther.compiler.check.TypeView;
import souther.compiler.inputs.Case;
import souther.compiler.inputs.Distinctions;
import souther.compiler.inputs.Refinement;
import souther.compiler.types.Type;
import souther.compiler.types.TypeSymbol;
import souther.compiler.types.TypeReachName;

import java.util.ArrayList;
import java.util.List;

/**
 * How a value of one distinction is named, recognised in a row, and written down.
 *
 * <p>Which distinctions a position has is not asked here. That is one reading, made once
 * ({@link Distinctions}, crossed with the rules where the position is read), and this is what the
 * partition adds to each of them — a name stable within its axis, a classifier for the rows an
 * author already wrote, and a recipe for a value standing for it.
 *
 * <p>Under the names the position wears, because that is how a row writes the value. The names come
 * off to say what the position is and go back on to say what stands at it.
 */
final class PartitionClasses {

    /** The classes a type states, before any rule is crossed with them. What a witness varies over
     *  and what a generator offers for a bare position, which are asked of a type and not of a
     *  position of one. */
    static List<PartitionClass> of(Type type, RuleReadingContext reading,
                                   java.util.Set<TypeSymbol> expanding) {
        RuleReadingSource ruleSource = reading.source();
        TypeView view = TypeView.of(type, ruleSource.symbols(), ruleSource.published());
        return of(Distinctions.ofType(view, ruleSource.symbols(), ruleSource.published()),
                view, reading, expanding);
    }

    /**
     * One class per distinction, in the order they were read.
     *
     * <p>A name the position wears that nothing here writes takes every class with it: the classes
     * are still the position's and a row already sitting in one still covers it, so what is absent
     * is the offer of a new row rather than the class.
     *
     * @param expanding the names whose own value is already being built. A class carries what stands
     *                  for it, and what stands for it is built from the position under it — so the
     *                  walk that reaches a class is the walk that reaches that position, and a class
     *                  built without what is already being built starts the descent again with
     *                  nothing to stop it
     */
    static List<PartitionClass> of(List<Case> cases, TypeView view, RuleReadingContext reading,
                                   java.util.Set<TypeSymbol> expanding) {
        if (cases.isEmpty()) {
            return List.of();
        }
        RuleReadingSource ruleSource = reading.source();
        List<TypeSymbol> worn = view.wrappers();
        // The same names twice over, because two different questions are asked of them. Whether an
        // observed value is under this class is asked of the declarations, and what a row writes it
        // under is asked of the module doing the writing — one of these is an identity and the other
        // is a reference, and a module reaching a name through an alias answers them differently.
        return switch (WornNames.of(view.wrappers(), ruleSource)) {
            case WornNames.Unwritable unwritable ->
                    unwritable(cases, view, reading, worn, unwritable, expanding);
            case WornNames.Spelled spelled -> {
                List<PartitionClass> out = new ArrayList<>();
                for (Case one : cases) {
                    out.add(classOf(one, view, worn, reading, spelled.names(), expanding));
                }
                yield List.copyOf(out);
            }
        };
    }

    /**
     * The classes of a position whose values cannot be written here at all, each saying why.
     *
     * <p>They are counted like any other: a case a module does not expose is still a case of the
     * position, and a row already sitting in one still covers it. What is absent is the offer of a
     * new row, and the reason is the model's — {@code domain} keeps the case to itself — rather than
     * anything about this generator.
     */
    private static List<PartitionClass> unwritable(List<Case> cases, TypeView view,
                                                   RuleReadingContext reading,
                                                   List<TypeSymbol> worn, WornNames.Unwritable unnamed,
                                                   java.util.Set<TypeSymbol> expanding) {
        String why = unnamed.why();
        List<PartitionClass> out = new ArrayList<>();
        // The classes of the same position with no names to write them under. What each is and what
        // reads a value into it are the position's either way; only the recipes are dropped, and
        // they are what there is no writing them.
        for (PartitionClass each : of(cases,
                new TypeView(view.declared(), List.of(), view.shape()), reading, expanding)) {
            out.add(PartitionClass.ungeneratable(each.id(), each.label(),
                    Recognition.Under.of(worn, each.recognises()), why)
                    .holding(each.denotes()).selecting(each.selects()));
        }
        return List.copyOf(out);
    }

    /** What a case's class is called, in one place: a reading that decides which cases the position
     *  holds names the same classes the reading above builds. */
    static String idOfCase(TypeSymbol leaf) {
        return leaf.name();
    }

    /**
     * The class one distinction gets, and the narrowing a row in it meets.
     *
     * <p>Exhaustive over {@link Case}, with no {@code default}: a distinction the reading learns to
     * make later stops this compiling rather than arriving as a class nothing can name.
     *
     * <p><b>Which narrowing it is is asked once, here, of the one reading that relates the two
     * vocabularies.</b> What a position divides into and what a position under it stands beneath are
     * the same statement read twice, and each arm below deciding for itself is how they came apart:
     * the arm for a sum's case spelled the narrowing again and the arm for an optional spelled none,
     * so an optional's classes narrowed nothing — a row could be asked to be `None` here and to hold
     * a class under `Some` at the same time, and nothing could say the two do not go together.
     */
    private static PartitionClass classOf(Case one, TypeView view, List<TypeSymbol> worn,
                                          RuleReadingContext reading,
                                          List<TypeReachName.Written> writes,
                                          java.util.Set<TypeSymbol> expanding) {
        PartitionClass built = switch (one) {
            case Case.Truth truth -> eitherWay(truth.value(), worn, writes);
            case Case.Presence presence ->
                    heldOrNot(presence.present(), view, worn, reading, writes, expanding);
            case Case.SumCase sum ->
                    caseClass(sum, view.declared(), worn, reading, writes, expanding);
            case Case.Named named ->
                    ValueClasses.classAt(named.value(), view, worn, reading.source());
        };
        Refinement narrowing = Refinement.of(one);
        return narrowing == null ? built : built.selecting(narrowing);
    }

    /** One of the two values of a {@code Bool}, under the names the position writes it under. */
    private static PartitionClass eitherWay(boolean value, List<TypeSymbol> worn,
                                            List<TypeReachName.Written> writes) {
        // Each holds the one value it is named after, which is what lets a rule denying that value
        // be read as refusing the whole class.
        String said = Boolean.toString(value);
        return PartitionClass.of(said, said,
                        Recognition.Under.of(worn, new Recognition.Truth(value)),
                        RepresentativeSource.under(writes,
                                RepresentativeSource.of(FixtureTemplate.bool(value))))
                .holding(souther.compiler.values.ValueSet.just(
                        souther.compiler.values.Value.truth(value)));
    }

    /** Whether an optional holds anything, which is the one division its type makes. */
    private static PartitionClass heldOrNot(boolean present, TypeView view, List<TypeSymbol> worn,
                                            RuleReadingContext reading,
                                            List<TypeReachName.Written> writes,
                                            java.util.Set<TypeSymbol> expanding) {
        if (!present) {
            return PartitionClass.of("None", "None",
                    Recognition.Under.of(worn, new Recognition.Held(false)),
                    RepresentativeSource.under(writes,
                            RepresentativeSource.of(FixtureTemplate.none())));
        }
        if (!(view.shape() instanceof souther.compiler.check.Shape.Optional optional)) {
            // The reading states whether an optional holds anything only of an optional. A class
            // for one anywhere else is the two readings of a position disagreeing about its shape.
            throw new IllegalStateException(
                    "`" + Type.show(view.declared()) + "` is asked whether it holds a value, and it"
                            + " is not an optional; the reading of a position and the classes built"
                            + " from it disagree about its shape");
        }
        Type element = optional.element();
        // Under what is already being built: what stands for `Some` is what stands at the element,
        // and the walk that got here is the walk that is building it.
        List<FixtureTemplate> some =
                Partitions.representativesOf(element, reading, null, expanding);
        Recognition is = Recognition.Under.of(worn, new Recognition.Held(true));
        return some.isEmpty()
                ? PartitionClass.ungeneratable("Some", "Some", is,
                        "nothing here composed a value of " + Type.show(element))
                : PartitionClass.of("Some", "Some", is,
                        RepresentativeSource.under(writes, RepresentativeSource.of(some)));
    }

    private static PartitionClass caseClass(Case.SumCase one, Type of, List<TypeSymbol> worn,
                                            RuleReadingContext reading,
                                            List<TypeReachName.Written> writes,
                                            java.util.Set<TypeSymbol> expanding) {
        return holdingWhatItIs(one,
                writableCase(one.leaf(), of, worn, reading, writes, expanding));
    }

    /**
     * The same class, saying which values it holds.
     *
     * <p>Said of the distinction and not of the class that came back, because what a class means and
     * whether a row for it can be written down are two answers and only the second turns on which
     * module is reading. A case another module keeps to itself took the arm that says nothing can be
     * written for it and left without saying what it holds — so a rule denying that case had nothing
     * to prove itself against, and the case stayed in the denominator of every module but the one
     * that declared it.
     */
    private static PartitionClass holdingWhatItIs(Case.SumCase one, PartitionClass built) {
        return one.denotes() == null ? built : built.holding(one.denotes());
    }

    /** The class itself: what it is called, what it recognises, and what can be written for it. */
    private static PartitionClass writableCase(TypeSymbol leaf, Type of, List<TypeSymbol> worn,
                                               RuleReadingContext reading,
                                               List<TypeReachName.Written> writes,
                                               java.util.Set<TypeSymbol> expanding) {
        RuleReadingSource ruleSource = reading.source();
        // Where the case sits on the position's order, decided here where the case, the position's
        // type and the declarations are all in hand. A line a body draws on an ordered enumeration
        // is at one of these places, and the class holding it is asked with the place.
        Recognition is = Recognition.Under.of(worn, new Recognition.OfCase(leaf,
                ValueClasses.placeOf(new souther.compiler.observe.ObservedValue.Unit(leaf), of,
                        ruleSource.symbols(), ruleSource.kinds(), ruleSource.published())));
        // A case whose module does not expose it: a value of the position all the same, and one no
        // author here can write down. Said as that, rather than offered under a spelling that
        // resolves to nothing wherever the row is pasted (issue #696).
        if (!(ruleSource.symbols().scope().reach(leaf) instanceof TypeReachName.Written names)) {
            return PartitionClass.ungeneratable(idOfCase(leaf), leaf.name(), is,
                    WornNames.noSpellingFor(leaf));
        }
        // A case of a primitive-headed union is a primitive or one of the language's own, which
        // no module declares and nothing composes field by field: naming it builds it, the same as
        // a unit data. So the two are told apart here, where the declaration is asked for.
        if (!(leaf instanceof TypeSymbol.AtModule declared)) {
            return namingItBuildsIt(leaf, is, writes, names);
        }
        TypeView held = TypeView.of(Type.ref(declared), ruleSource.symbols(), ruleSource.published());
        if (!held.isWrapped() && !(held.shape() instanceof Shape.Product)) {
            return namingItBuildsIt(leaf, is, writes, names);
        }
        if (held.isWrapped()) {
            // Values of the case, which is a position of its own: it is read like any other, and
            // what comes back already wears the case's own name. Under the position's names as well,
            // since a case of a `data DecisionN = Decision` is written inside that name too.
            List<FixtureTemplate> values = Partitions.representativesOf(
                    Type.ref(declared), reading, null, expanding);
            return values.isEmpty()
                    ? PartitionClass.ungeneratable(idOfCase(leaf), leaf.name(), is,
                            "nothing here composed a value of `" + leaf.name() + "`")
                    : PartitionClass.of(idOfCase(leaf), leaf.name(), is,
                            RepresentativeSource.under(writes, RepresentativeSource.of(values)));
        }
        // A record case is written field by field, which is the generator's composition. So the class
        // names the constructor and the generator does the composing — the same walk every other
        // record goes through, rules between the fields and all. Under the position's names either
        // way: what is composed is an `Approved`, and what the row writes is `DecisionN(Approved
        // { id = 1 })`.
        return PartitionClass.of(idOfCase(leaf), leaf.name(), is,
                RepresentativeSource.under(writes, new RepresentativeSource.Composed(declared)));
    }

    /**
     * The class of a case whose value is written by naming it.
     *
     * <p>What a unit data is, and what a case of a primitive-headed union is: neither is composed
     * field by field, because there are no fields to compose. Written once because the two arrive
     * at it by different questions — one has no declaration of this module's at all, and the other
     * has one that makes no record — and what they come to is the same class.
     */
    private static PartitionClass namingItBuildsIt(TypeSymbol leaf, Recognition is,
                                                   List<TypeReachName.Written> writes,
                                                   TypeReachName.Written names) {
        return PartitionClass.of(idOfCase(leaf), leaf.name(), is,
                RepresentativeSource.under(writes,
                        RepresentativeSource.of(FixtureTemplate.unitCase(names))));
    }

    private PartitionClasses() {}
}
