package souther.compiler.partition;

import org.junit.jupiter.api.Test;

import souther.compiler.check.RuleReadingSource;
import souther.compiler.check.RuleReadings;
import souther.compiler.check.DeclaredSig;
import souther.compiler.inputs.InputDomain;
import souther.compiler.meta.ModulePath;
import souther.compiler.observe.ObservedValue;
import souther.compiler.query.Bodies;
import souther.compiler.query.Compilation;
import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbols;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Four things a class carries, and only one of them is about the module reading it.
 *
 * <p>What a class is called, what it recognises, what it holds, and whether a value for it can be
 * written down. The last turns on where the reader stands — a case another module keeps to itself
 * has no spelling here — and the first three do not: the case is the same case, recognises the same
 * values, and is the same one value whoever is looking.
 *
 * <p>Held as one property rather than as a case, because the way it goes wrong is always the same:
 * the answer that does turn on the reader is worked out in a branch, and one of the three that does
 * not gets worked out inside it. That is how a case another module keeps to itself came to be a
 * class that says nothing about what it holds — and a rule denying that case then had nothing to
 * prove itself against, so the case stayed in the denominator of every module but the one that
 * declared it.
 */
class WhatAClassMeansDoesNotTurnOnWhoIsReadingItTest {

    private static final String LIB = """
            module lib exposing ( State, Present, Held, look )

            data Missing
            data Present = { note: String }
            data State = Missing | Present

            data Flag = Bool

            data Held = { state: State, flag: Flag }

            data Seen = { at: String }

            behavior look : (h: Held) -> Seen
            """;

    private static final String APP = """
            module app exposing ( watch, Watched )

            import lib ( Held )

            data Watched = { at: String }

            behavior watch : (h: Held) -> Watched
            """;

    /** The classes at {@code path}, as the {@code nth} module of this compilation reads them. */
    private static List<PartitionClass> classesAt(int nth, String behavior, String path) {
        Compilation compilation = Compilation.ofSources(List.of(LIB, APP), ModulePath.EMPTY);
        compilation.answerEverything();
        String module = compilation.modules().get(nth);
        Map<String, DeclaredSig> sigs =
                compilation.db().ask(new Bodies.DeclaredSignatures(module)).value();
        RuleReadingSource rules = RuleReadings.of(compilation, module);
        return Partitions.of(behavior, InputDomain.of(sigs.get(behavior), rules, souther.compiler.query.ReadAs.THE_COMPILATION_DOES), rules, souther.compiler.query.ReadAs.THE_COMPILATION_DOES).axes().stream()
                .filter(each -> each.path().toString().equals(path))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no axis at " + path))
                .classes();
    }

    /** The value a row would carry for the case `lib` keeps to itself. */
    private static ObservedValue theHiddenCase() {
        return new ObservedValue.Unit(TypeSymbols.declared(new TypeKey("lib", "Missing")));
    }

    /**
     * The three that are the position's own are the same from either module.
     *
     * <p>Including of a case the importing module cannot name. What it is called, what it
     * recognises in a row, and which values it holds are the model's answers; a module reading them
     * from outside reads the same ones.
     */
    @Test
    void whatAClassIsCalledRecognisesAndHoldsIsTheSameFromEitherModule() {
        List<PartitionClass> declaring = classesAt(0, "look", "h.state");
        List<PartitionClass> importing = classesAt(1, "watch", "h.state");

        assertEquals(declaring.stream().map(PartitionClass::id).toList(),
                importing.stream().map(PartitionClass::id).toList());
        assertEquals(declaring.stream().map(PartitionClass::denotes).toList(),
                importing.stream().map(PartitionClass::denotes).toList(),
                "what a class holds is the model's answer, not the reader's");
        for (int i = 0; i < declaring.size(); i++) {
            assertEquals(declaring.get(i).classifier().membershipOf(theHiddenCase()),
                    importing.get(i).classifier().membershipOf(theHiddenCase()),
                    "a row carrying the hidden case is read into the same class either way");
        }
    }

    /** And the one that is about the reader differs, which is what makes the property worth
     *  asserting: the two are not simply equal values. */
    @Test
    void whetherAValueCanBeWrittenForItIsTheOneThatDiffers() {
        List<PartitionClass> declaring = classesAt(0, "look", "h.state");
        List<PartitionClass> importing = classesAt(1, "watch", "h.state");

        assertNotEquals(declaring.stream().map(PartitionClass::generatable).toList(),
                importing.stream().map(PartitionClass::generatable).toList(),
                "the case `lib` keeps to itself can be written there and not here");
        assertTrue(declaring.stream().allMatch(PartitionClass::generatable));
    }

    /** A case that holds nothing says so from either side, and a case that holds a record says
     *  nothing from either side. */
    @Test
    void aCaseHoldingNothingSaysWhatItIsAndOneHoldingARecordDoesNot() {
        for (List<PartitionClass> classes :
                List.of(classesAt(0, "look", "h.state"), classesAt(1, "watch", "h.state"))) {
            assertEquals(List.of("Missing", "Present"),
                    classes.stream().map(PartitionClass::id).toList());
            assertEquals(souther.compiler.values.ValueSet.just(
                            souther.compiler.values.Value.of(
                                    TypeSymbols.declared(new TypeKey("lib", "Missing")))),
                    classes.get(0).denotes());
            assertEquals(null, classes.get(1).denotes(),
                    "a case holding a record has no end of values and says nothing here");
        }
    }
}
