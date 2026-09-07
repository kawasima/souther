package souther.compiler.check;

import souther.compiler.WhatWasCompiled;
import souther.compiler.semantics.ArgumentRef;
import souther.compiler.semantics.OperationFact;
import souther.compiler.semantics.OperationFacts;
import souther.compiler.types.ValueName;

import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A fact about an operation is read against the library once, and everything below reads what that
 * made.
 *
 * <p>The declarations ({@link OperationFacts}) are written in an authoring vocabulary — a name for
 * the operation, a word for an argument ({@link ArgumentRef}) — that says nothing about whether the
 * library has such an operation or such an argument. {@link OperationFactBinder} holds every one of
 * them to the library and answers with {@link BoundOperationFact}s, in which every name has been
 * read against its declaration; a reader below it holds one of those and has nothing left to
 * resolve. What that arrangement can lose is a second reader of the authoring vocabulary: one that
 * takes the authored word and puts the binder's question again, by reading the declarations, or
 * the signature, or what {@link Combinators} says an operation hands its closure — and gets an
 * answer that agrees with the binder's for exactly as long as nobody changes either.
 *
 * <p>So the boundary is counted from what javac made of the module, on both sides. Above it, the
 * authoring vocabulary is named by its own package and by the binder and by nothing else, and the
 * declarations are read by the binder alone. Across it, each of the three steps from an authored
 * fact to a fact a reader holds is taken by the binder alone: a bound argument, a bound fact, and
 * the gathering of them are each minted nowhere else — since a bound fact a reader wrote for itself
 * would say a fact was held to the library where nothing held it, and would read no authoring
 * vocabulary for the census above to see. Below it, no arm of a bound fact carries an authored name
 * or word, and the readers of what an operation hands its closure are each written down with what
 * they want it for — since a reader that wanted it to find a fact's argument would be the binder's
 * question asked again.
 *
 * <p>Each set is compared whole rather than counted. A count lets one name be dropped and another
 * added and says nothing about it, which is exactly the edit this is here to catch.
 */
class OnlyTheBinderReadsTheAuthoringVocabularyTest {

    private static final String AUTHORING_PACKAGE = OperationFacts.class.getPackageName() + ".";

    private static final String BINDER = OperationFactBinder.class.getName();

    /** The authoring vocabulary: the declarations, the facts, and the word for an argument. Named
     *  by outer class, so an arm nested in one counts as the class that nests it. */
    private static final List<Class<?>> AUTHORING =
            List.of(OperationFacts.class, OperationFact.class, ArgumentRef.class);

    /** The names no bound fact may carry: the authoring word for an argument, the authored fact,
     *  and the bare name of an operation — the last because a bound fact names an operation as a
     *  declaration read, and a name beside that would be the half a reader could re-derive from. */
    private static final List<Class<?>> NOT_BELOW_THE_BINDING =
            List.of(ArgumentRef.class, OperationFact.class, ValueName.class);

    private static boolean names(Set<String> named, Class<?> type) {
        String outer = type.getName();
        return named.stream().anyMatch(each -> each.equals(outer) || each.startsWith(outer + "$"));
    }

    /** Every class outside the authoring package that names any of the authoring vocabulary. */
    private static Set<String> readersOfTheAuthoringVocabulary() {
        Set<String> readers = new TreeSet<>();
        for (String each : WhatWasCompiled.classes()) {
            if (each.startsWith(AUTHORING_PACKAGE)) {
                continue;
            }
            Set<String> named = WhatWasCompiled.typesNamedBy(each);
            if (AUTHORING.stream().anyMatch(type -> names(named, type))) {
                readers.add(each);
            }
        }
        return readers;
    }

    @Test
    void onlyTheBinderNamesTheAuthoringVocabularyOutsideItsOwnPackage() {
        assertEquals(Set.of(BINDER), readersOfTheAuthoringVocabulary(),
                "a class below the binding that names the declarations, an authored fact or the"
                        + " word for an argument is a reader that can put the binder's question"
                        + " again, and would get an answer that agrees with the binder's only for"
                        + " as long as nobody changes either");
    }

    /** And the binder is such a reader, so the assertion above counted something. */
    @Test
    void andTheBinderDoesNameIt() {
        Set<String> named = WhatWasCompiled.typesNamedBy(BINDER);
        for (Class<?> type : AUTHORING) {
            assertTrue(names(named, type), BINDER + " does not name " + type.getSimpleName()
                    + ", so it is not reading the authoring vocabulary and the census above is"
                    + " counting nothing");
        }
    }

    /**
     * The declarations are the whole of what the authoring side publishes.
     *
     * <p>Not read off who calls what today, since that would hold only until a lookup was added
     * beside the list and read from inside the authoring package — where the census above does not
     * look, because the vocabulary itself lives there. What is held is the surface: every method
     * and field of the declarations that is not private is the one that hands the list to the
     * binder. Non-private and not merely public, because a package-private lookup would be
     * reachable from exactly the readers the census above cannot see.
     */
    @Test
    void theDeclarationsPublishTheListAndNothingElse() {
        Set<String> surface = new TreeSet<>();
        for (java.lang.reflect.Method method : OperationFacts.class.getDeclaredMethods()) {
            if (!java.lang.reflect.Modifier.isPrivate(method.getModifiers())
                    && !method.isSynthetic()) {
                surface.add(method.getName());
            }
        }
        for (java.lang.reflect.Field field : OperationFacts.class.getDeclaredFields()) {
            if (!java.lang.reflect.Modifier.isPrivate(field.getModifiers())
                    && !field.isSynthetic()) {
                surface.add(field.getName());
            }
        }
        assertEquals(Set.of("declarations"), surface,
                "the authoring side publishes the list of declarations and nothing else: a lookup"
                        + " beside it would be a way to a fact that does not pass through the"
                        + " binding, whoever could reach it");
    }

    /**
     * And the bound facts are read whole by two callers and no others.
     *
     * <p>The binder asks what it has just bound before publishing it, and the one procedure that
     * counts the readings of a number across every kind of fact walks them. A third reader of the
     * list would be sorting the facts for itself, and could arrive at a second answer to a question
     * one of the holder's queries already owns.
     */
    @Test
    void onlyTheBinderAndTheReadingsWalkTheBoundFactsWhole() {
        assertEquals(Set.of(BINDER, NumericReadings.class.getName()),
                WhatWasCompiled.callersOf(BoundOperationFacts.class, "all"),
                "a reader walking every bound fact is one that can classify them for itself,"
                        + " beside the query that already does");
    }

    @Test
    void onlyTheBinderReadsTheDeclarations() {
        assertEquals(Set.of(BINDER),
                WhatWasCompiled.callersOf(OperationFacts.class, "declarations"),
                "the declarations are the one thing the authoring side publishes, and the binder"
                        + " is their one reader: a second is a way to a fact that does not pass"
                        + " through the binding");
    }

    @Test
    void onlyTheBinderMintsABoundArgument() {
        Set<String> minting = WhatWasCompiled.callersOf(DeclaredArgument.class, "<init>");
        assertEquals(Set.of(BINDER), minting,
                "a bound argument says a word was read against a declaration, and the binder is"
                        + " where that is done; one made anywhere else was read against nothing");
    }

    /** The other half of the pair: somebody does mint one, so the rule above is about something. */
    @Test
    void andTheBinderDoesMintOne() {
        assertFalse(WhatWasCompiled.callersOf(DeclaredArgument.class, "<init>").isEmpty(),
                "nothing mints a bound argument, so the rule about who may is about nothing");
    }

    /** Every concrete arm of a bound fact, through both families. */
    private static List<Class<?>> boundArms() {
        List<Class<?>> arms = new ArrayList<>();
        for (Class<?> family : BoundOperationFact.class.getPermittedSubclasses()) {
            arms.addAll(List.of(family.getPermittedSubclasses()));
        }
        assertFalse(arms.isEmpty(), "there are arms for this to be about");
        return arms;
    }

    /**
     * Every arm of a bound fact is made by the binder and by nothing else.
     *
     * <p>A bound fact says a fact was held to the library, which is true of a value only where the
     * holding made it. An arm a reader wrote for itself — one naming no argument needs nothing but
     * an operation to be written — would say the same thing about a fact nothing checked, and no
     * census of who reads the authoring vocabulary would see it, since it reads none. So who calls
     * each arm's constructor is counted, arm by arm, and the answer is the binder.
     */
    @Test
    void onlyTheBinderMintsABoundFact() {
        Map<String, Set<String>> minted = new TreeMap<>();
        for (Class<?> arm : boundArms()) {
            Set<String> minting = WhatWasCompiled.callersOf(arm, "<init>");
            if (!minting.equals(Set.of(BINDER))) {
                minted.put(arm.getSimpleName(), minting);
            }
        }
        assertEquals(Map.of(), minted,
                "a bound fact made anywhere but the binder says a fact was held to the library"
                        + " where nothing held it");
    }

    /** And the binder makes every one of them, so the rule above is about every arm. */
    @Test
    void andTheBinderMintsEveryArm() {
        Set<String> unmade = new TreeSet<>();
        for (Class<?> arm : boundArms()) {
            if (WhatWasCompiled.callersOf(arm, "<init>").isEmpty()) {
                unmade.add(arm.getSimpleName());
            }
        }
        assertEquals(Set.of(), unmade,
                "an arm nothing makes is one the rule about who may make it is about nothing");
    }

    /** And the facts are gathered by the binder alone: a set gathered anywhere else would say
     *  every fact in it was bound. */
    @Test
    void onlyTheBinderGathersTheBoundFacts() {
        assertEquals(Set.of(BINDER), WhatWasCompiled.callersOf(BoundOperationFacts.class, "<init>"),
                "the bound facts are what a binding came to, and are gathered where it ran");
    }

    /**
     * Every arm of a bound fact is in one of the two families, and no arm carries an authored name
     * or word.
     *
     * <p>The families are what decide how an arm is collected — once per operation, or as many
     * times as declared — so an arm answering the sealed type directly would be one nothing knows
     * how to file. And an arm carrying a {@link ValueName} or an {@link ArgumentRef} would be a
     * bound fact with an unbound half, which is the shape a reader re-derives from.
     */
    @Test
    void everyBoundArmIsInAFamilyAndCarriesOnlyBoundNames() {
        assertEquals(Set.of(BoundOperationFact.OneAboutAnOperation.class,
                        BoundOperationFact.SeveralAboutAnOperation.class),
                Set.of(BoundOperationFact.class.getPermittedSubclasses()),
                "a bound fact is one of two families, and an arm is in one of them");
        Map<String, String> unbound = new TreeMap<>();
        for (Class<?> arm : boundArms()) {
            assertTrue(arm.isRecord(), arm + " is a bound fact and is not a record");
            for (RecordComponent component : arm.getRecordComponents()) {
                String type = component.getGenericType().getTypeName();
                for (Class<?> forbidden : NOT_BELOW_THE_BINDING) {
                    if (type.equals(forbidden.getName()) || type.contains(forbidden.getName() + "<")
                            || type.contains(forbidden.getName() + "$")
                            || type.contains("<" + forbidden.getName() + ">")
                            || type.contains(", " + forbidden.getName() + ">")
                            || type.contains("<" + forbidden.getName() + ",")) {
                        unbound.put(arm.getSimpleName() + "." + component.getName(), type);
                    }
                }
            }
        }
        assertEquals(Map.of(), unbound,
                "a bound fact carries an authored name or word, which is the half a reader below"
                        + " the binding could re-derive from");
    }

    /** A reader of what an operation hands its closure, and what it wants it for. */
    private record Reader(String who, String why) { }

    /**
     * The readers of {@link Combinators}, each with what it asks.
     *
     * <p>What {@code Combinators} answers is which argument of an operation hands a closure the
     * contents of which container. Asked to find the argument a fact names, that is the binder's
     * question — the one {@link ArgumentRef.TheContainer} and {@link ArgumentRef.TheClosure} are
     * resolved by — and a second asker would be a second binding. So every reader is written down
     * with what it wants the answer for, and a reader arriving here is a finding before it is a
     * line.
     */
    private static final List<Reader> READERS_OF_COMBINATORS = List.of(
            new Reader(BINDER,
                    "the one place the authored word for an argument becomes a position, which is"
                            + " what the combinator's positions are read for"),
            new Reader("souther.compiler.check.HelperInliner",
                    "which binding an expansion wrote for a parameter: a function argument leaves"
                            + " none, so the closure's own position is asked to count past it —"
                            + " about the expansion's bindings, and not about which argument a fact"
                            + " names"),
            new Reader("souther.compiler.check.ElementBindings",
                    "what a call hands its closure, for the value a closure parameter is bound to"),
            new Reader("souther.compiler.check.Reductions",
                    "what a call hands its closure, for a walk from a seed"),
            new Reader("souther.compiler.check.Predicates",
                    "what a call hands its closure, for what a predicate over the elements"
                            + " establishes"),
            new Reader("souther.compiler.check.UniversalElementFacts",
                    "what a call hands its closure, for what holds of what the closure answered"),
            new Reader("souther.compiler.check.TotalityChecker",
                    "what a call hands its closure, for crediting an element as a sub-term of its"
                            + " container"),
            new Reader("souther.compiler.check.InvariantChecker",
                    "what a call hands its closure, for a construction inside the closure"),
            new Reader("souther.compiler.check.WalkElements",
                    "what a call hands its closure, for which binding one element of a walk arrives"
                            + " under whichever representation the walk is in"),
            new Reader("souther.compiler.check.Question",
                    "which operations are answered for what they hand their closure, for the range"
                            + " a question is asked over"));

    @Test
    void everyReaderOfWhatAnOperationHandsItsClosureIsWrittenDownWithWhatItAsks() {
        String combinators = Combinators.class.getName();
        Set<String> found = new TreeSet<>();
        for (String each : WhatWasCompiled.classes()) {
            if (each.equals(combinators) || each.startsWith(combinators + "$")) {
                continue;
            }
            if (names(WhatWasCompiled.typesNamedBy(each), Combinators.class)) {
                found.add(each.contains("$") ? each.substring(0, each.indexOf('$')) : each);
            }
        }
        Map<String, String> declared = new LinkedHashMap<>();
        READERS_OF_COMBINATORS.forEach(reader -> declared.put(reader.who(), reader.why()));
        assertEquals(new TreeSet<>(declared.keySet()), found,
                "a reader of what an operation hands its closure is written here with what it asks."
                        + " Asked to find the argument a fact names, it is the binder's question"
                        + " put a second time. What each of these asks: " + declared);
    }
}
