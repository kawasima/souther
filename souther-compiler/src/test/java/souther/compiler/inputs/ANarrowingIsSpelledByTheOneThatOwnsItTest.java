package souther.compiler.inputs;

import org.junit.jupiter.api.Test;

import souther.compiler.check.DeclaredSig;
import souther.compiler.check.RuleReadingSource;
import souther.compiler.check.RuleReadings;
import souther.compiler.check.TypeView;
import souther.compiler.core.Core;
import souther.compiler.query.Bodies;
import souther.compiler.query.Compilation;
import souther.compiler.query.ReadAs;
import souther.compiler.types.ResolvedCase;
import souther.compiler.types.TypeSymbol;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A narrowing is spelled where narrowings are owned, and nowhere else.
 *
 * <p>Two vocabularies say which narrowing a place carries. What a position's type divides into is
 * {@link Case}, read by {@link Distinctions}; which of those divisions a written arm selected is
 * {@link ResolvedCase}, decided by the checker. Both settle a narrowing on their own, so both have
 * a way in here — and what may not have one is a value that settles less than a narrowing.
 *
 * <p>A name is such a value, and it falls short twice over. An optional's present carrier and a
 * case of a sum declared as {@code Some} are written the same word. And a case that is itself a sum
 * is one name over several leaves (spec §sum-data) while a position divides into the leaves, so
 * {@code OnceKind} names no one of them. Both were live: {@code Refinement.sumCase(TypeSymbol)} was
 * a second way in, and the reading of a {@code match} arm used it, so a body inside {@code | Some v}
 * wrote at {@code x@Some} carrying a sum's case where the reading carried a presence, and a body
 * inside {@code | OnceKind as x} wrote at {@code k@OnceKind}, which the reading has no position for
 * at all (#1252).
 *
 * <p>Nothing compared them: a reader that looks such a path up finds nothing and says nothing.
 *
 * <p>What holds the rule is that the variants have no constructor a caller can reach and that the
 * two ways in take the two values that determine a narrowing. What holds the correspondence is
 * {@link #everySelectionTheCheckerMakesAnswersOneNarrowingOrNone}, below: exhaustiveness stops a
 * variant being added without an answer, and only a comparison stops an existing one being answered
 * wrongly.
 */
class ANarrowingIsSpelledByTheOneThatOwnsItTest {

    /** A leaf, a case that is itself a sum, an or-pattern, and both of an optional's carriers. */
    private static final String A_MODEL_OF_EVERY_SHAPE = """
            module m

            data Station  = { at: String }
            data Hospital = { at: String }
            data Renkei   = { at: String }
            data OnceKind  = Station | Hospital
            data VisitKind = OnceKind | Renkei

            data Tagged = { tag: Int? }
            data Ack = { at: String }

            behavior visit : (k: VisitKind, t: Tagged) -> Ack
            let visit (k, t) = {
                let named = match k with
                    | OnceKind as once ->
                        match once with
                            | Station as s -> s.at
                            | Hospital as h -> h.at
                    | Renkei as r -> r.at
                let both = match k with
                    | Station | Hospital -> "once"
                    | Renkei as r -> r.at
                let held = match t.tag with
                    | Some v -> v
                    | None -> 0
                Ack { at = named }
            }
            """;

    @Test
    void aNarrowingHasNoConstructorAReaderCanReach() {
        for (Class<?> variant : new Class<?>[] {Refinement.SumCase.class, Refinement.Presence.class}) {
            for (Constructor<?> each : variant.getDeclaredConstructors()) {
                assertTrue(Modifier.isPrivate(each.getModifiers()),
                        "a reader could spell a narrowing of its own: " + each);
            }
            assertEquals(0, variant.getConstructors().length,
                    () -> "and none of " + variant.getSimpleName()
                            + " is reachable by reflection either");
        }
    }

    /**
     * And every way in takes a value that already settles which narrowing it is.
     *
     * <p>Named rather than counted. A count is tripped by a private helper, which is nobody's way
     * in, and the reading of it that fits is to raise the number — which lets a way in be added
     * later with nothing having said so. What is checked beside the names is what each of them
     * takes: a way in whose parameter is a name, a string, a list of case types or a selector that
     * has not been resolved against the declarations is one that cannot answer for a case standing
     * over several leaves, whatever it is called.
     *
     * <p>{@code allOf} is the third name and not a third answer. It takes the same
     * {@link ResolvedCase} and says every distinction that selection covers; {@code of} is that
     * asked for the one case where there is exactly one, and is written in terms of it. Two readers
     * want the two — a path takes a single narrowing or none, and what the rules leave an arm is
     * asked of every distinction it can arrive at — and the check below is that the second is the
     * first read again rather than a second decision about what a selection covers.
     */
    @Test
    void everyWayInTakesAValueThatSettlesTheNarrowing() {
        List<java.lang.reflect.Method> ways =
                java.util.Arrays.stream(Refinement.class.getDeclaredMethods())
                        .filter(each -> Modifier.isStatic(each.getModifiers())
                                && !Modifier.isPrivate(each.getModifiers()))
                        .toList();
        assertEquals(java.util.Set.of("of", "allOf"),
                ways.stream().map(java.lang.reflect.Method::getName)
                        .collect(java.util.stream.Collectors.toSet()),
                "a way to spell a narrowing that is not one of these is a decision, not an accident");
        assertEquals(java.util.Set.of(Case.class, ResolvedCase.class),
                ways.stream().map(each -> each.getParameterTypes()[0])
                        .collect(java.util.stream.Collectors.toSet()),
                "a narrowing is made from what a position divides into or from what an arm was"
                        + " resolved to select, and from nothing that settles less than either");
    }

    /** And the singular reading is the plural one, asked for the case where there is exactly one. */
    @Test
    void theOneNarrowingIsTheOnlyOneCovered() {
        Read read = read(A_MODEL_OF_EVERY_SHAPE);

        for (Core.Case arm : armsOf(read.body)) {
            if (arm.selectedCase().isEmpty()) {
                continue;
            }
            ResolvedCase selected = arm.selectedCase().orElseThrow();
            List<Refinement> covered = Refinement.allOf(selected);
            assertEquals(covered.size() == 1 ? covered.get(0) : null, Refinement.of(selected),
                    () -> "the one narrowing of `" + selected + "` is whatever it alone covers");
        }
    }

    /**
     * Every selection the checker makes answers one of the position's distinctions, or none.
     *
     * <p>The correspondence itself, and the reason the exhaustive {@code switch}es are not the whole
     * of the rule. Nothing stops {@code OptionPresent} being answered with {@code Presence(false)}:
     * every arm would be covered, every reading would compile, and an optional's arms would swap
     * which position they wrote at.
     *
     * <p><b>Read forward, off what the checker put on the arms.</b> A test that built a selector for
     * each distinction and compared the two would only ever meet the selections it thought of, and
     * the one that mattered is the one it would not have thought of: {@code OnceKind} has no
     * distinction to be paired with, so it never appears among the pairs and its narrowing goes
     * unasked about. So the arms of the model are walked, every selection the checker resolved is
     * taken as it stands, and each is required to answer either one distinction the position states
     * or none at all.
     *
     * <p>The model states each shape once: a case that is a leaf, a case that is itself a sum, an
     * or-pattern, and both of an optional's carriers.
     */
    @Test
    void everySelectionTheCheckerMakesAnswersOneNarrowingOrNone() {
        Read read = read(A_MODEL_OF_EVERY_SHAPE);

        Map<String, Refinement> answered = new LinkedHashMap<>();
        int orPatterns = 0;
        for (Core.Case arm : armsOf(read.body)) {
            if (arm.selectedCase().isEmpty()) {
                orPatterns++;
                continue;
            }
            ResolvedCase selected = arm.selectedCase().orElseThrow();
            answered.put(selected.toString(), Refinement.of(selected));
        }

        assertEquals(List.of(
                        "m.OnceKind covering [m.Station, m.Hospital]",
                        "m.Renkei covering [m.Renkei]",
                        "m.Station covering [m.Station]",
                        "m.Hospital covering [m.Hospital]",
                        "Some covering [Some]",
                        "None covering [None]"),
                List.copyOf(answered.keySet()),
                "the model states each shape of selection once");
        assertEquals(1, orPatterns,
                "and one arm selects several cases, which is the shape that selects no one of them");

        // A case standing over several leaves answers no one distinction. Read from its name it
        // would answer `@OnceKind`, which is a place the reading of the position never holds.
        assertNull(answered.get("m.OnceKind covering [m.Station, m.Hospital]"),
                "a case that is itself a sum narrows to several of the position's distinctions,"
                        + " and so to no one of them");

        // And every other selection answers exactly one of them, matched against what the position
        // itself states rather than against a narrowing written here.
        Map<Refinement, String> stated = new LinkedHashMap<>();
        for (Case each : distinctionsAt(read, TermPath.of("k"))) {
            stated.put(Refinement.of(each), "k");
        }
        for (Case each : distinctionsAt(read, TermPath.of("t").then("tag"))) {
            stated.put(Refinement.of(each), "t.tag");
        }
        for (Map.Entry<String, Refinement> each : answered.entrySet()) {
            if (each.getValue() == null) {
                continue;
            }
            assertTrue(stated.containsKey(each.getValue()),
                    () -> "the checker resolved `" + each.getKey() + "` to "
                            + each.getValue().discriminated()
                            + ", which no position of this input divides into: " + stated.keySet()
                                    .stream().map(Refinement::discriminated).toList());
        }
        assertEquals(stated.size(), answered.values().stream().filter(java.util.Objects::nonNull)
                        .distinct().count(),
                "and between them the arms reach every distinction the two positions state");
    }

    /**
     * And a presence is not a case of a sum, which is what makes the correspondence load-bearing.
     *
     * <p>A presence and a case of a sum are unequal however they are spelled, so a reader that
     * built one where the other belongs writes at a position nothing else reaches. Were they equal
     * instead, the mistake would be invisible and the correspondence above would say nothing.
     */
    @Test
    void aPresenceIsNotACaseOfASumHoweverItIsWritten() {
        Refinement declaredSome = aLeafNamed("m", "Some");

        assertEquals("Some", declaredSome.spelled());
        assertEquals("Some", Refinement.of(new Case.Presence(true)).spelled());
        assertNotEquals(Refinement.of(new Case.Presence(true)), declaredSome,
                "two places written `@Some` are two places where one is an optional's carrier");
        assertNotEquals(declaredSome.discriminated(),
                Refinement.of(new Case.Presence(true)).discriminated(),
                "and a message about this compiler says which of them it holds");
    }

    /**
     * A narrowing is what it narrows to, and not which object it is.
     *
     * <p>Load-bearing rather than tidy. Every reader deciding whether two requirements hold together
     * does it by equality ({@link Requirements#merge}), and every branch of a position is found by
     * comparing the narrowing it carries — so an identity comparison would have no two narrowings
     * ever agree, which reads as a model whose cases are all incompatible.
     */
    @Test
    void twoNarrowingsToTheSameThingAreOne() {
        assertEquals(anInt(), anInt());
        assertEquals(anInt().hashCode(), anInt().hashCode());
        assertNotEquals(anInt(), aLeaf(TypeSymbol.primitive("Bool")));

        assertEquals(Refinement.of(new Case.Presence(true)), Refinement.of(new Case.Presence(true)));
        assertNotEquals(Refinement.of(new Case.Presence(true)),
                Refinement.of(new Case.Presence(false)));
        assertFalse(Refinement.of(new Case.Presence(true)).equals(anInt()),
                "and a presence is not a case of a sum");
    }

    private static Refinement anInt() {
        return aLeaf(TypeSymbol.primitive("Int"));
    }

    private static Refinement aLeafNamed(String module, String name) {
        return aLeaf(souther.compiler.types.TypeSymbols.declared(
                new souther.compiler.types.TypeKey(module, name)));
    }

    /** The narrowing to one leaf, spelled the way the checker's resolution of an arm spells it: a
     *  leaf is a case that covers itself, so selecting it narrows to that one distinction. */
    private static Refinement aLeaf(TypeSymbol leaf) {
        return Refinement.of(ResolvedCase.of(
                souther.compiler.types.CaseSelector.direct(leaf), List.of(leaf)));
    }

    /** Every arm of every {@code match} the body holds, outermost first. */
    private static List<Core.Case> armsOf(Core body) {
        List<Core.Case> out = new ArrayList<>();
        collect(body, out);
        return out;
    }

    private static void collect(Core at, List<Core.Case> out) {
        if (at instanceof Core.Match match) {
            out.addAll(match.cases());
        }
        Core.forEachChild(at, each -> collect(each, out));
    }

    /** What the type at one position divides into. */
    private static List<Case> distinctionsAt(Read read, TermPath at) {
        return Distinctions.ofType(
                TypeView.of(read.inputs.at(at).view().declared(), read.rules.symbols()),
                read.rules.symbols());
    }

    private record Read(InputDomain inputs, Core body, RuleReadingSource rules) {}

    private static Read read(String source) {
        Compilation compilation =
                Compilation.ofSources(List.of(source), souther.compiler.meta.ModulePath.EMPTY);
        compilation.answerEverything();
        String module = compilation.modules().get(0);
        Map<String, DeclaredSig> sigs =
                compilation.db().ask(new Bodies.DeclaredSignatures(module)).value();
        RuleReadingSource rules = RuleReadings.of(compilation, module);
        Bodies.Elaborated checked = compilation.db().ask(new Bodies.Checked(module)).value();
        return new Read(
                InputDomain.of(sigs.get("visit"), rules, ReadAs.THE_COMPILATION_DOES),
                checked.behaviorBodies().get("visit"), rules);
    }
}
