package souther.compiler.partition;

import souther.compiler.check.RuleReadingContext;
import souther.compiler.check.Carrier;
import souther.compiler.numeric.Place;
import souther.compiler.check.RuleReadingSource;
import souther.compiler.check.Shape;
import souther.compiler.check.TypeView;
import souther.compiler.types.Type;
import souther.compiler.types.TypeSymbol;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Values built to hold what a rule says a value must hold.
 *
 * <p>What stands for a type used to come from the carrier alone — the empty one for every collection,
 * one character for a string — and a rule saying the collection is not empty is then the one rule that
 * refuses the one value on offer. Read the minimum before the value is chosen and it is instead the
 * size the value is built at.
 *
 * <p>Proposals, and every one of them. Nothing here decides whether a newtype accepts what it is
 * handed: that is the decoder's answer and it is asked afterwards, which is what lets a value be built
 * from the part of a rule this can read while the rest of the rule refuses it. A position offering one
 * value is exactly the defect this is about, so a value built around another carries every proposal the
 * inner one made — a format and a minimum reaching the decoder separately at a position is a choice
 * that has to keep reaching it from inside a collection.
 *
 * <p>Which is why a map's key and value meet each other. They are two parts of one value and the
 * search around this is over the behavior's positions, so a key at its second proposal beside a value
 * at its first is a map nothing outside here can arrive at. Nearest first, so that a search which stops
 * has walked the low proposals of both rather than every one of the key's against the value's first.
 *
 * <p>What is bounded is what this invents rather than what it read. A set's second element and a map's
 * second key are variations made up to tell values apart, and they stop as soon as there are enough;
 * the count a rule asked for stops being worth building long before a row carrying it is one anybody
 * would read. Nothing prunes a candidate some rule was read to produce — the search that walks them is
 * bounded and says so when it stops, which is the honest place for a limit.
 *
 * <p>A tree, because a collection holds values that have rules of their own, and what stands for a list
 * of a newtype is what stands for that newtype in a list. That question is the one the position itself
 * asked, so it is asked of the same chooser. A type written in terms of itself would ask it forever, so
 * the names being expanded are carried down and a name met twice is given up on — which is the right
 * answer there, since no value of such a type exists.
 */
final class Witnesses {

    /** What this stops at, named where every budget of this compiler's is named. The figures are
     *  {@link CompositionBudget}'s so that a reader of a stopped search reaches the one it ran out
     *  of. */
    private static final int MOST_ELEMENTS =
            CompositionBudget.ELEMENTS_A_PROPOSAL_HOLDS.maximum();

    private static final int MOST_CHARACTERS =
            CompositionBudget.CHARACTERS_A_PROPOSAL_HOLDS.maximum();

    private static final int MOST_PAIRINGS =
            CompositionBudget.PAIRINGS_BUILT_AT_ONCE.maximum();

    /**
     * Values of a count, and whether they are all the values of it there were.
     *
     * <p>Two halves of one answer. A caller reading only the first says every value was refused where
     * some were never built, which is a different thing to tell an author and a different thing for a
     * measure to record.
     */
    record Sized(List<FixtureTemplate> values, Set<CompositionBudget> heldBack) {

        Sized {
            values = List.copyOf(values);
            heldBack = Set.copyOf(heldBack);
        }

        static Sized all(List<FixtureTemplate> values) {
            return new Sized(values, Set.of());
        }
    }

    /**
     * Values of {@code position} whose count is exactly {@code size}, or none where this can build
     * none.
     *
     * <p>The narrower of the two promises about a count. A caller holding a line drawn on one needs
     * the count itself and not a value that merely clears it: a row at {@code String.length = 5} is a
     * row carrying five characters, and four or six is a row at some other edge. {@link #holding}
     * reads the same build for what it asks, and neither is written in terms of the other.
     *
     * <p>Which is why nothing is the answer at a size of none and the empty value is the answer at a
     * size of zero. A rule asking a collection to hold at least none of anything has asked for no
     * value in particular; a line drawn at zero is the edge the empty one stands on, and it is a
     * value like any other.
     *
     * <p>And why what was built is counted rather than taken on trust. A set of three is three values
     * no two of which are equal, which a type of two values has not got; the two that exist are a
     * proposal for a floor and are not the count asked for here, and offering them would put a row of
     * two under a line drawn at three.
     */
    static Sized ofSize(TypeView position, int size, RuleReadingContext reading,
                        Set<TypeSymbol> expanding) {
        Shape carrier = position.shape();
        if (size == 0) {
            // The same shapes {@link #sized} builds for, and for the same reason they are written
            // out: what a value of none looks like is a question about each shape.
            return Sized.all(switch (carrier) {
                case Shape.Scalar scalar when scalar.prim() == Type.Prim.STRING ->
                        List.of(FixtureTemplate.string(""));
                case Shape.Sequence _, Shape.Mapping _ ->
                        List.of(FixtureTemplate.collection(List.of()));
                case Shape.Scalar _, Shape.Product _, Shape.Sum _, Shape.Unit _, Shape.Optional _,
                     Shape.Unresolved _, Shape.Cases _, Shape.Tuple _, Shape.Function _,
                     Shape.Uninhabited _, Shape.Bottom _, Shape.Erroneous _,
                     Shape.Undecided _ -> List.<FixtureTemplate>of();
            });
        }
        Built built = sized(position, size, reading, expanding);
        return new Sized(built.exactly(size), built.heldBack());
    }

    /**
     * Why no value of {@code position} counting exactly {@code size} was built, or null where one
     * was.
     *
     * <p>The same decision {@link #ofSize} reads, asked for its other half, so that what could not be
     * built and why are one answer given twice rather than two answers that may disagree. Whenever
     * that one offers nothing this names a reason: a caller left to supply its own default for the
     * silence would be deciding again what this is here to answer.
     *
     * <p>The word and not which budget it was. A budget that stopped this is the build's own answer
     * ({@link Sized#heldBack()}) and travels as itself; what is here is the word a search comes back
     * with, for readers that have only ever wanted that.
     */
    static Generator.UnresolvedCombination.Reason reasonForSize(TypeView position, int size,
                                                                RuleReadingContext reading) {
        Sized made = ofSize(position, size, reading, Set.of());
        if (!made.values().isEmpty()) {
            return null;
        }
        return made.heldBack().isEmpty()
                ? Generator.UnresolvedCombination.Reason.NOTHING_COMPOSES_ONE
                : Generator.UnresolvedCombination.Reason.wordFor(made.heldBack());
    }

    /**
     * What to put to the decoder at a position a rule gives a floor of {@code least}, or nothing where
     * this can build nothing.
     *
     * <p>Proposals and not witnesses, which is the whole of the difference from {@link #ofSize}. What
     * comes back holds the floor where the type has that much to give and falls short of it where the
     * type does not, and either way it is the decoder that answers — so a caller reading this as
     * "values of at least {@code least}" is reading a promise nothing here makes.
     *
     * <p>The minimum itself and not one element. Recognising {@code >= 3} and then offering a list of
     * one would refuse the row for the reason the empty list was refused, having read the rule and not
     * used it.
     *
     * <p>So a value is built at the minimum, and a value short of it is offered too where the type has
     * no more to give: it is the value the rule refuses, put to the decoder so that the refusal is the
     * decoder's and is said in its terms. Both of those are this method's reading of the build, not
     * {@link #ofSize}'s — a caller with a line to stand on takes that one, where a set the type has
     * too few values for comes back as nothing at all. The two read one build and neither is written
     * in terms of the other, so a cheaper value for a floor cannot move a line.
     */
    static List<FixtureTemplate> holding(TypeView position, int least, RuleReadingContext reading,
                                         Set<TypeSymbol> expanding) {
        return least <= 0 ? List.of() : sized(position, least, reading, expanding).all();
    }

    /**
     * Which budgets of this compiler's stopped a value of {@code position} holding {@code least} from
     * being built in full, and empty where none did.
     *
     * <p>The same decision {@link #holding} reads, asked for its other half. Written once because the
     * two have to agree: a reader was told a search stopped short of pairings nothing had asked to
     * build, back when this was a second reading of the same conditions.
     *
     * <p>Not {@link #reasonForSize}, for the same reason the two halves above are not one method. A
     * floor nothing was built for is a position offering what it ordinarily offers, and naming a
     * reason there would put "nothing composes one" under every position that has no floor at all.
     */
    static Set<CompositionBudget> heldBackFor(TypeView position, int least,
                                              RuleReadingContext reading) {
        return least <= 0 ? Set.of()
                : sized(position, least, reading, Set.of()).heldBack();
    }

    /**
     * A value that was built, and how many of whatever counts it it turned out to hold.
     *
     * <p>Carried rather than assumed. A string of five characters is built by writing five and a list
     * of five by repeating one, but a set of five is five values no two of which are equal and the
     * type may not have five — so what was asked for and what was made are two numbers, and only the
     * caller that needs them equal is entitled to require it.
     */
    private record Made(FixtureTemplate value, int count) {}

    /** What a value of the position counting {@code size} comes to: what was built, and which
     * budgets of this compiler's stopped the rest of it being built. */
    private record Built(List<Made> proposals, Set<CompositionBudget> heldBack) {

        static final Built NONE = new Built(List.of(), Set.of());

        static Built of(List<Made> proposals) {
            return new Built(List.copyOf(proposals), Set.of());
        }

        static Built stoppedBy(CompositionBudget budget) {
            return new Built(List.of(), Set.of(budget));
        }

        /** Those holding exactly {@code size}, which is what a line drawn at a count is met by. */
        List<FixtureTemplate> exactly(int size) {
            return proposals.stream().filter(each -> each.count() == size)
                    .map(Made::value).toList();
        }

        /** All of them, which is what a floor to clear is offered: one short of the floor is a value
         * the decoder refuses for the reason the floor was written, and that is an answer. */
        List<FixtureTemplate> all() {
            return proposals.stream().map(Made::value).toList();
        }
    }

    private static Built sized(TypeView position, int least, RuleReadingContext reading,
                               Set<TypeSymbol> expanding) {
        if (least <= 0) {
            return Built.NONE;
        }
        // Exhaustive over what a position can be, with no `default`. Whether a value of a shape
        // counts anything is a question about each of them, and a chain of tests answers "no" for a
        // shape added later without being asked.
        return switch (position.shape()) {
            // A string is counted by its characters. Which characters is a question the count does
            // not answer, so what the position's own rules admit of that many comes first and a
            // string of any characters at all after it — both are proposals, and the one every rule
            // admits is the one a caller with a single chance needs.
            case Shape.Scalar scalar when scalar.prim() == Type.Prim.STRING ->
                    least > MOST_CHARACTERS
                            ? Built.stoppedBy(CompositionBudget.CHARACTERS_A_PROPOSAL_HOLDS)
                            : Built.of(ofThatManyCharacters(position, least, reading));
            case Shape.Sequence sequence -> least > MOST_ELEMENTS
                    ? Built.stoppedBy(CompositionBudget.ELEMENTS_A_PROPOSAL_HOLDS)
                    : ofSequence(sequence, least, reading, expanding);
            case Shape.Mapping mapping -> least > MOST_ELEMENTS
                    ? Built.stoppedBy(CompositionBudget.ELEMENTS_A_PROPOSAL_HOLDS)
                    : ofMapping(mapping, least, reading, expanding);
            // Nothing else has a count this builds to. A number is one value however many the rules
            // ask for, a record holds its fields and not a number of them, and the shapes that are
            // not value shapes have no value to count.
            case Shape.Scalar _, Shape.Product _, Shape.Sum _, Shape.Unit _, Shape.Optional _,
                 Shape.Unresolved _, Shape.Cases _, Shape.Tuple _, Shape.Function _,
                 Shape.Uninhabited _, Shape.Bottom _, Shape.Erroneous _,
                 Shape.Undecided _ -> Built.NONE;
        };
    }

    /**
     * Strings of exactly {@code least} characters: the one the position's rules admit, and the one
     * any string of that many is.
     *
     * <p>Both, and in that order. What a string of that length looks like is a question about
     * strings and is the same answer for every position — which is why the second is always here,
     * and why what this reports when nothing builds stays a claim about strings rather than an
     * opinion about one type. The first is that answer narrowed by what the position's own rules
     * say its strings are, which is a value the decoder can accept where the second is one a format
     * refuses.
     */
    private static List<Made> ofThatManyCharacters(TypeView position, int least,
                                                   RuleReadingContext reading) {
        List<Made> out = new ArrayList<>();
        for (FixtureTemplate each : Partitions.admittedStringOfSize(position, reading, least)) {
            out.add(new Made(each, least));
        }
        out.add(new Made(FixtureTemplate.string("x".repeat(least)), least));
        return out;
    }

    /**
     * A list of that many, or a set of that many no two of which are equal.
     *
     * <p>A list may hold the same element as many times as it needs to. A set of three is three
     * elements no two of which are equal, which the element's own values have to supply.
     */
    private static Built ofSequence(Shape.Sequence carrier, int least, RuleReadingContext reading,
                                    Set<TypeSymbol> expanding) {
        List<Made> out = new ArrayList<>();
        for (FixtureTemplate seed
                : proposalsFor(carrier.element(), reading, expanding)) {
            List<FixtureTemplate> elements = new ArrayList<>();
            if (carrier.kind() == Shape.Sequence.Kind.LIST) {
                for (int i = 0; i < least; i++) {
                    elements.add(seed);
                }
            } else {
                elements.addAll(
                        distinctFrom(seed, carrier.element(), least, reading, expanding));
            }
            out.add(new Made(FixtureTemplate.collection(elements), elements.size()));
        }
        return Built.of(out);
    }

    /** A map of that many entries, no two of which share a key. The values under the keys are free
     *  to repeat. */
    private static Built ofMapping(Shape.Mapping map, int least, RuleReadingContext reading,
                                   Set<TypeSymbol> expanding) {
        List<FixtureTemplate> keys = proposalsFor(map.key(), reading, expanding);
        List<FixtureTemplate> values = proposalsFor(map.value(), reading, expanding);
        if (keys.isEmpty() || values.isEmpty()) {
            return Built.NONE;
        }
        List<Made> out = new ArrayList<>();
        // Every pair, nearest first. A key's rules and a value's are answered together or not at all —
        // the pair is inside one position, so no search outside can put a key's second proposal beside
        // a value's first — and taking them in step would offer only the pairs whose two proposals
        // happen to have been read in the same order. Nearest first is what makes the bound below cost
        // the least: what it drops is the pairs furthest from what either side proposed first.
        // Recorded where the budget is reached and not worked out afterwards from how many were
        // built. How many pairs there are and how many were made differ whenever this stops, and
        // they are the same two numbers whether or not it was this that stopped it — so a reader
        // asking which budget ran out would be reading it off a subtraction that does not know.
        boolean stopped = false;
        // What a key proposal's other keys are, worked out once however many values it is put
        // beside. The question is the key's and the count's — what stands under them is not in it —
        // so a reading per pair is one walk over the element's values run again for every value the
        // other side proposed.
        List<List<FixtureTemplate>> keysFrom =
                new ArrayList<>(Collections.nCopies(keys.size(), null));
        pairing:
        for (int apart = 0; apart <= keys.size() + values.size() - 2; apart++) {
            for (int i = Math.max(0, apart - values.size() + 1);
                    i <= Math.min(apart, keys.size() - 1); i++) {
                if (out.size() == MOST_PAIRINGS) {
                    stopped = true;
                    break pairing;
                }
                if (keysFrom.get(i) == null) {
                    keysFrom.set(i,
                            distinctFrom(keys.get(i), map.key(), least, reading, expanding));
                }
                FixtureTemplate value = values.get(apart - i);
                List<FixtureTemplate> entries = new ArrayList<>();
                for (FixtureTemplate key : keysFrom.get(i)) {
                    entries.add(FixtureTemplate.entry(key, value));
                }
                out.add(new Made(FixtureTemplate.collection(entries), entries.size()));
            }
        }
        return new Built(out,
                stopped ? Set.of(CompositionBudget.PAIRINGS_BUILT_AT_ONCE) : Set.of());
    }

    /**
     * What the position at {@code type} would itself be offered, which is what a value built around it
     * has to keep offering.
     *
     * <p>All of them. Each is what one rule was read to produce — the shortest string a format accepts,
     * a string of the length a minimum asks for, the value the carrier stands for — so a budget over
     * this list drops a candidate on the strength of how many rules were read before it. The minimum's
     * is added last of those, which is exactly the one such a budget takes away.
     */
    private static List<FixtureTemplate> proposalsFor(Type type, RuleReadingContext reading,
                                                      Set<TypeSymbol> expanding) {
        return Partitions.representativesOf(type, reading, null, expanding);
    }

    /**
     * A collection of {@code carrier} holding {@code chosen} and counting {@code needed}, or null
     * where none was built.
     *
     * <p>For a row being built around one element. What the rest are is not what was asked for, and
     * what they may be is: a list may hold the same value again and a set may not, and a caller
     * padding one by hand would have to know which — the thing this reader is for.
     *
     * <p><b>How many, and not a floor to read one off.</b> What the rules leave the position and
     * what a collection holding the chosen value comes to are two numbers, and the second is made
     * from the first in one place ({@link ConstructionPlan#neededToHold}). Read again here, the
     * conversion would be written twice and a caller handing over the floor would build the same
     * collection as one handing over the count.
     */
    static FixtureTemplate holdingAlso(souther.compiler.check.Shape.Sequence carrier,
                                       FixtureTemplate chosen, int needed,
                                       RuleReadingContext reading) {
        if (needed < 1) {
            throw new IllegalArgumentException(
                    "a collection built around a value holds it: " + needed);
        }
        if (carrier.kind() == souther.compiler.check.Shape.Sequence.Kind.LIST) {
            List<FixtureTemplate> elements = new ArrayList<>();
            while (elements.size() < needed) {
                elements.add(chosen);
            }
            return FixtureTemplate.collection(elements);
        }
        List<FixtureTemplate> elements =
                distinctFrom(chosen, carrier.element(), needed, reading, Set.of());
        // Fewer than asked for is a type with too few values, which is a set the rules want and
        // nothing can build — said as nothing built rather than as a set of the wrong size.
        return elements.size() < needed ? null : FixtureTemplate.collection(elements);
    }

    /**
     * {@code seed} and up to {@code least} values in all, no two of them equal.
     *
     * <p>Fewer than asked where the type runs out, which is not the same as failing: a set of three
     * booleans is a thing no value satisfies, and offering the two that exist is a proposal the decoder
     * answers the way it answers any other.
     */
    private static List<FixtureTemplate> distinctFrom(FixtureTemplate seed, Type type, int least,
                                                      RuleReadingContext reading,
                                                      Set<TypeSymbol> expanding) {
        Set<String> written = new LinkedHashSet<>();
        List<FixtureTemplate> out = new ArrayList<>();
        written.add(seed.text());
        out.add(seed);
        // One more than needed, since the seed is likely to be among them.
        for (FixtureTemplate each : distinctValuesOf(type, least + 1, reading, expanding)) {
            if (out.size() >= least) {
                break;
            }
            if (written.add(each.text())) {
                out.add(each);
            }
        }
        return List.copyOf(out);
    }

    /**
     * Up to {@code many} values of {@code type}, no two of them equal.
     *
     * <p>One place that answers what a type's values are, in the order a collection should reach for
     * them. Written once because it was written three times: a set of a carrier this could not step was
     * offered one element, and adding a carrier meant remembering every reader that walked one.
     *
     * <p>What the type divides into comes first. A {@code Bool} is two values and a sum is its cases,
     * and each of those is a value of the type rather than a proposal about it — a set of two booleans
     * is built from both of them or from nothing. Then the values the rules about the strings admit,
     * which are values of the type in the same way and are as many as the rules leave. Then values
     * made by stepping the carrier, which stay inside the rules the type carries. The type's own
     * proposals come last: each is what one rule asked for and any of them may be one the whole of
     * the rules refuses, so a collection filled from them is refused for its elements — which is
     * still better than a collection short of its size, and is all there is where the carrier
     * neither divides nor steps.
     *
     * <p><b>Which is why the rules about the strings are asked before the carrier is stepped.</b> A
     * string stepped by a character is a value of the carrier and not of the type: what the carrier
     * answers is how many characters, and a type whose rule says which of them gets a string it
     * refuses at every element after the first. The rules are what tell the values apart there, the
     * same as a range does for a number ({@link Partitions#numberInside}).
     */
    private static List<FixtureTemplate> distinctValuesOf(Type type, int many,
                                                          RuleReadingContext reading,
                                                          Set<TypeSymbol> expanding) {
        Set<String> written = new LinkedHashSet<>();
        List<FixtureTemplate> out = new ArrayList<>();
        for (FixtureTemplate each : dividesInto(type, reading, expanding)) {
            if (out.size() >= many) {
                return List.copyOf(out);
            }
            if (written.add(each.text())) {
                out.add(each);
            }
        }
        for (FixtureTemplate each : Partitions.admittedStrings(type, reading, many)) {
            if (out.size() >= many) {
                return List.copyOf(out);
            }
            if (written.add(each.text())) {
                out.add(each);
            }
        }
        for (int i = 0; out.size() < many; i++) {
            FixtureTemplate each = varied(type, i, reading);
            if (each == null) {
                break;
            }
            if (written.add(each.text())) {
                out.add(each);
            }
        }
        if (out.size() >= many) {
            return List.copyOf(out);
        }
        // Asked last and only where there is room for what it answers. What a position is offered
        // is a reading of every rule on it and costs what that reading costs, and a loop that asks
        // for the list and then leaves it alone pays for it at every count already filled.
        for (FixtureTemplate each : Partitions.representativesOf(type, reading, null, expanding)) {
            if (out.size() >= many) {
                break;
            }
            if (written.add(each.text())) {
                out.add(each);
            }
        }
        return List.copyOf(out);
    }

    /**
     * The values a type divides into, under every name it wears: a {@code Bool} is two of them and a
     * sum is its cases. Empty where the type is not divided, which is where a range or a length is
     * what tells its values apart instead.
     *
     * <p>Asked once. This used to ask again of what the names wrap and put them back on itself,
     * because the reading it asks stopped at a name — so the same rule about what a newtype divides
     * into was written here as well, and the two could differ about how far to look. The reading
     * goes through the names now and hands the values back written under them.
     */
    private static List<FixtureTemplate> dividesInto(Type type, RuleReadingContext reading,
                                                     Set<TypeSymbol> expanding) {
        List<FixtureTemplate> out = new ArrayList<>();
        for (PartitionClass each : PartitionClasses.of(type, reading, expanding)) {
            out.addAll(Partitions.standingFor(each.representatives(), reading, expanding));
        }
        return out;
    }

    /**
     * The {@code index}th value of {@code type} made by stepping its carrier, or null where the carrier
     * has no order to step.
     *
     * <p>A string grows by a character from the length its rules ask for, and a whole number steps
     * through the range they leave. Which characters is a question the carrier has no answer to, so
     * a type whose rules say which of them is answered before this is reached
     * ({@link Partitions#admittedStrings}) and what is left here is the length.
     *
     * <p>A date or a record has no such step that keeps every rule the
     * position carries, and inventing one would put a value in a row the type's own chooser had reason
     * not to offer — which is what the values a type divides into are for, above.
     */
    private static FixtureTemplate varied(Type type, int index, RuleReadingContext reading) {
        RuleReadingSource ruleSource = reading.source();
        TypeView view = TypeView.of(type, ruleSource.symbols(), ruleSource.published());
        if (view.shape() instanceof Shape.Scalar scalar && scalar.prim() == Type.Prim.STRING) {
            return WornNames.under(view.wrappers(), FixtureTemplate.string(
                    "x".repeat(Math.max(1, Partitions.leastHeld(view, reading)) + index)),
                    ruleSource);
        }
        Place at = Partitions.numberInside(view, reading, index);
        if (at == null) {
            return null;
        }
        Carrier carrier = view.shape() instanceof Shape.Scalar scalar
                && scalar.prim() == Type.Prim.DECIMAL ? Carrier.DENSE : Carrier.WHOLE;
        return WornNames.under(view.wrappers(), FixtureTemplate.on(
                carrier, at, ruleSource.symbols().scope()::reach), ruleSource);
    }

    private Witnesses() {}
}
