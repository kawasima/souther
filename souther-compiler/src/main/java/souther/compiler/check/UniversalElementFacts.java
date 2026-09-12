package souther.compiler.check;

import souther.compiler.semantics.ElementLineage;
import souther.compiler.semantics.SizeAgainstItsSource;
import souther.compiler.core.Core;
import souther.compiler.types.BindingId;
import souther.compiler.numeric.Count;
import souther.compiler.numeric.Endpoint;
import souther.compiler.numeric.Granularity;
import souther.compiler.numeric.NumericDomain;
import souther.compiler.numeric.NumericDomain.Bounds;
import souther.compiler.numeric.LinearForm;
import souther.compiler.types.Type;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What holds of every element a container expression holds, wherever the container came from.
 *
 * <p>Read off the tree the discharge check is given, which is one where the library's own operations
 * are still calls ({@code InliningPolicy#DISCHARGE}). So this is not a record of what some earlier
 * pass observed: it is the same question asked again of the expression a container is written as —
 * what its own type and its own written-out elements say, and what a construction keeps of the
 * container it was built from.
 *
 * <p>Keyed by the path under an element and not by a subject. What holds of an element is a fact
 * about {@code amount} of every element, or about the element itself ({@code ""}); which value the
 * reader is standing at, and what that value is called there, is the reader's own question. That is
 * what lets a producer and a consumer that share no binder share a fact: {@code List.map(x -> x.value,
 * xs)} says of the list it answers that every element of it is at or above nought, and the fold or
 * the accumulation that walks that list never met {@code x}.
 *
 * <p>Everything here holds of <em>every</em> element, so a reader may assume it of the one element
 * it is standing at. Nothing that holds of some elements belongs here, and nothing about how many
 * elements there are: that a container written out with nothing in it makes a walk answer its seed
 * is true and is not this — it is a fact about the count, and stating it here would make a reader
 * that assumes these of an element assume something no element satisfies.
 */
record UniversalElementFacts(Map<RuleKey, Bounds> byPath) {

    UniversalElementFacts {
        byPath = Map.copyOf(byPath);
    }

    private static final UniversalElementFacts NONE = new UniversalElementFacts(Map.of());

    static UniversalElementFacts none() {
        return NONE;
    }

    boolean saysNothing() {
        return byPath.isEmpty();
    }

    /**
     * What holds of every element of {@code container}.
     *
     * <p>Two sources and one transfer. A value's type guarantees what it guarantees of every value
     * of it, so the container's element type says it of every element; a container written out holds
     * the elements written there and nothing else, so every one of them lies between the least and
     * the greatest. Beside those, what a construction was built from says what it kept
     * ({@link #transferred}).
     *
     * <p>Where the container's value is written is asked of {@link Terms#given}, and every reading
     * below is of what comes back — the expression and the environment together. A name is not a
     * third kind of container and neither is a binding: {@code List.sum(ys)} where {@code ys} was
     * bound to {@code List.map(f, xs)}, and the same call with a helper the discharge tree expanded
     * into a binding, are the question written in one line. A reader that followed one of those and
     * not the other would answer them differently, which is the shape this class was written to
     * stop, seen inside it.
     */
    static UniversalElementFacts of(Core written, Denotations at, Terms terms) {
        Symbols symbols = terms.symbols();
        if (written == null) {
            return NONE;
        }
        Terms.Given given = terms.given(written, at);
        Core container = given.value();
        Map<RuleKey, Bounds> held = new LinkedHashMap<>();
        Type element = Terms.elementType(container.type());
        ValueGuarantees.of(element, terms.ruleReading())
                .forEach((path, bounds) -> holds(held, path, bounds));
        writtenOut(container, element, symbols, held);
        transferred(container, given.at(), terms, held);
        return held.isEmpty() ? NONE : new UniversalElementFacts(held);
    }

    /**
     * These of the value at {@code root}, as the subjects a reader files facts under.
     *
     * <p>The instantiation, and the only place a path becomes a subject. A reader hands the place it
     * is standing at and gets back what holds there — which is what keeps the facts themselves free
     * of whatever binder either side happened to be written with.
     */
    Map<FactSubject, Bounds> at(FactSubject root, Terms terms) {
        Map<FactSubject, Bounds> instantiated = new LinkedHashMap<>();
        byPath.forEach((path, bounds) -> {
            FactSubject subject = terms.under(root, path);
            if (subject != null) {
                instantiated.put(subject, bounds);
            }
        });
        return instantiated;
    }

    /**
     * The elements of a container written out, bounded by the elements written there.
     *
     * <p>Only where every one of them is a number this folds. A container written with a computed
     * element says nothing here — the least of what is written is not the least of what is held once
     * one of them is decided at run time — and saying nothing is what leaves a reader unbounded
     * rather than wrongly bounded.
     */
    private static void writtenOut(Core container, Type element, Symbols symbols,
                                   Map<RuleKey, Bounds> held) {
        if (element == null || !(container instanceof Core.ListLit list)
                || list.elements().isEmpty()) {
            return;
        }
        BigDecimal low = null;
        BigDecimal high = null;
        for (Core each : list.elements()) {
            BigDecimal written = Terms.constantNumber(each, symbols);
            if (written == null) {
                return;
            }
            low = low == null || written.compareTo(low) < 0 ? written : low;
            high = high == null || written.compareTo(high) > 0 ? written : high;
        }
        holds(held, RuleKey.THE_VALUE,
                new Bounds(Endpoint.inclusive(Count.of(low)), Endpoint.inclusive(Count.of(high))));
    }

    /**
     * What a construction kept of the container it was built from.
     *
     * <p>Read off the lineage and not off the four words it projects to. The words answer whether
     * what was known survives, which is one bit where this has an answer per alternative: what
     * {@code Map.updateIfPresent} answers holds values the closure made and values that were already
     * there, and a reader with one word for the run can only say the true thing about neither.
     *
     * <p>An answer for each of the four lineages, because a case that fell through to the one beside
     * it is how a reading comes to say of an element something no element satisfies. The same
     * elements keep what was true of every one of them, however many of them there are — how many is
     * {@link SizeAgainstItsSource} and is a different algebra, so {@code Set.map} carries what
     * its closure answered exactly as {@code List.map} does. An element inside what a closure
     * answered is not what the closure answered, and nothing here reads into a list or an optional,
     * so that one keeps nothing. One of several is what holds of every one of them, which is the
     * span of what each keeps.
     */
    private static void transferred(Core container, Denotations at, Terms terms,
                                    Map<RuleKey, Bounds> held) {
        if (!(container instanceof Core.PreservedCall call)) {
            return;
        }
        DischargeRules.Kept kept = DischargeRules.keptFrom(call);
        if (kept == null || kept.container() == null) {
            return;
        }
        keptBy(kept.lineage(), call, kept.container(), at, terms)
                .forEach((path, bounds) -> holds(held, path, bounds));
    }

    /** What one lineage keeps of {@code source}, by the path under an element. */
    private static Map<RuleKey, Bounds> keptBy(ElementLineage<DeclaredArgument> lineage,
                                              Core.PreservedCall call,
                                              Core source, Denotations at, Terms terms) {
        return switch (lineage) {
            case ElementLineage.SameAs<DeclaredArgument> _ ->
                    of(source, at, terms).byPath();
            case ElementLineage.ClosureResult<DeclaredArgument> _ ->
                    throughTheClosure(call, source, at, terms);
            case ElementLineage.InsideClosureResult<DeclaredArgument> _ -> Map.of();
            case ElementLineage.OneOf<DeclaredArgument> one -> {
                Map<RuleKey, Bounds> both = null;
                for (ElementLineage<DeclaredArgument> alternative : one.alternatives()) {
                    Map<RuleKey, Bounds> keeps =
                            keptBy(alternative, call, source, at, terms);
                    both = both == null ? keeps : spanning(both, keeps);
                }
                yield both == null ? Map.of() : both;
            }
        };
    }

    /**
     * What holds of an element that is one of two things: what holds of both.
     *
     * <p>A place either of them says nothing about is a place nothing is said about, and the ends
     * are the outer ones of the two — an element the closure never saw is bounded by what the source
     * guarantees, and one it made is bounded by what it answered, and every element is one of the
     * two.
     */
    private static Map<RuleKey, Bounds> spanning(Map<RuleKey, Bounds> one, Map<RuleKey, Bounds> other) {
        Map<RuleKey, Bounds> both = new LinkedHashMap<>();
        one.forEach((path, bounds) -> {
            Bounds there = other.get(path);
            if (there != null) {
                both.put(path, Bounds.spanning(bounds, there));
            }
        });
        return both;
    }

    /**
     * What holds of what the closure answered, given what holds of every element it is applied to.
     *
     * <p>By the path under what it answered, because that is what these facts are. A closure answers
     * a value, and a value is not always a number: {@code x -> x.inner} answers a record, and what
     * is known of it is what was known of that place of the element; {@code x -> Row { amount = ... }}
     * answers one made here, and what is known of each field is what the field was made from. A
     * reading that could only answer about a whole numeric value would keep the facts a mapping
     * kept only where the mapping ended at a number — the same value provable or not by where the
     * map was written, which is what this class is for.
     */
    private static Map<RuleKey, Bounds> throughTheClosure(Core.PreservedCall call, Core source,
                                                         Denotations at, Terms terms) {
        Combinators.Handed handed = Combinators.handedTo(call, at);
        if (handed == null) {
            return Map.of();
        }
        UniversalElementFacts kept = of(source, at, terms);
        BindingId element = handed.element().binding();
        FactSubject root = terms.placeSubject(element);
        Denotations reading = at.location(element, root, terms.placeTerm(element));
        return answeredBy(handed.step().body(), reading, terms, root, kept);
    }

    /**
     * What holds of the value {@code e} answers, by the path under it, given {@code kept} of the
     * element it was written over and {@code root} the place that element was entered at.
     *
     * <p>Three readings of one value and each may answer where the others do not. A value that is a
     * place of the element carries what was said of that place — and of everything under it, with
     * the place's own steps dropped, since {@code inner.amount} of the element is {@code amount} of
     * what {@code x -> x.inner} answers. A value built here carries, at each field, whatever that
     * field was built from, which is this question again. And a value that is a number is read by
     * the reading that reads recipes and what values carry, at the path {@code ""} that a value
     * itself is.
     *
     * <p>Recursive rather than a case list, because a field of a construction is a value like any
     * other: {@code Row { inner = x.inner }} is a projection under a field, and nothing here has to
     * know that combination for it to be read.
     *
     * <p>Where the value is written is asked of {@link Terms#given} first, so none of the three is
     * about how it was named. A closure calling a helper is a binding once the discharge tree has
     * expanded it, and a reader that dispatched on the shapes it had thought of would answer that
     * one with nothing — which is how this class had already answered a container given a name, and
     * a closure answering a record.
     */
    private static Map<RuleKey, Bounds> answeredBy(Core written, Denotations at, Terms terms,
                                                  FactSubject root, UniversalElementFacts kept) {
        Terms.Given given = terms.given(written, at);
        Core e = given.value();
        Denotations reading = given.at();
        Map<RuleKey, Bounds> answered = new LinkedHashMap<>();
        FactSubject subject = terms.subjectOf(e, reading);
        kept.byPath().forEach((path, bounds) -> {
            RuleKey under = beneath(subject, path, root, terms);
            if (under != null) {
                holds(answered, under, bounds);
            }
        });
        if (e instanceof Core.Construct construct) {
            for (Core.FieldValue field : construct.values()) {
                answeredBy(field.value(), reading, terms, root, kept).forEach((path, bounds) ->
                        holds(answered, path.readFrom(field.field()), bounds));
            }
        }
        // Read after the places inside it are named, which reading it as a form is what does: a
        // range asserted about an atom whose spacing was never recorded is one the domain refuses.
        LinearForm<FactSubject> form = terms.affineOf(e, reading);
        if (form != null) {
            NumericDomain<FactSubject> read = DerivedNumericFacts.refine(
                    assuming(kept.at(root, terms), terms), terms, form.coefs().keySet());
            holds(answered, RuleKey.THE_VALUE, read.isBottom() ? null : read.boundsOf(form));
        }
        return answered;
    }

    /**
     * The path {@code path} names under the value at {@code subject}, or null where it names no
     * place of it.
     *
     * <p>Asked of the subjects and not of the fields written. A newtype's carrier is read as
     * {@code x.value} and is the value itself, so the path that names it is the empty one, and a
     * reader counting the fields it saw would have gone looking for what holds of a place under a
     * number. So the element's own paths are put back under the place it was entered at, one step
     * at a time, and the algebra says which of them the closure answered — as it does everywhere
     * else.
     */
    private static RuleKey beneath(FactSubject subject, RuleKey path, FactSubject root,
                                   Terms terms) {
        if (subject == null) {
            return null;
        }
        List<String> steps = path.steps();
        for (int taken = 0; taken <= steps.size(); taken++) {
            if (subject.equals(terms.under(root, new RuleKey(steps.subList(0, taken))))) {
                return new RuleKey(steps.subList(taken, steps.size()));
            }
        }
        return null;
    }

    /** A domain with every one of {@code facts} taken as holding, for the reading to start from.
     * A place whose spacing was never recorded is one no range can be asserted about, and is left
     * out rather than asserted into a domain that would refuse it. */
    private static NumericDomain<FactSubject> assuming(Map<FactSubject, Bounds> facts, Terms terms) {
        NumericDomain<FactSubject> given = NumericDomain.top();
        for (Map.Entry<FactSubject, Bounds> one : facts.entrySet()) {
            Map<FactSubject, Granularity> spacing = terms.kindsOf(LinearForm.atom(one.getKey()));
            if (!spacing.isEmpty()) {
                given = given.assuming(one.getKey(), one.getValue(), spacing);
            }
        }
        return given;
    }

    /** Records that everything at {@code path} lies between {@code bounds}. Two sources reaching one
     * place are both true of it, so the tighter end of each side is kept. */
    private static void holds(Map<RuleKey, Bounds> held, RuleKey path, Bounds bounds) {
        if (bounds == null || bounds.saysNothing()) {
            return;
        }
        Bounds had = held.get(path);
        held.put(path, had == null ? bounds : had.meet(bounds));
    }
}
