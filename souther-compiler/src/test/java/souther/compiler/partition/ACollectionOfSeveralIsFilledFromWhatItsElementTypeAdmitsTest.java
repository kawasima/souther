package souther.compiler.partition;

import org.junit.jupiter.api.Test;

import souther.compiler.check.DeclaredSig;
import souther.compiler.check.RuleReadingContext;
import souther.compiler.check.RuleReadingSource;
import souther.compiler.check.RuleReadings;
import souther.compiler.inputs.InputDomain;
import souther.compiler.query.Bodies;
import souther.compiler.query.Compilation;
import souther.compiler.query.ReadAs;
import souther.compiler.types.Type;
import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbols;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The elements after the first come from what the element's type admits.
 *
 * <p>A collection the rules ask several elements of used to be filled by composing the first out of
 * the element's rules and the rest by stepping the carrier — a string grown by a character. Which
 * is a value of the carrier and not of the type: where the type states which strings, every element
 * after the first was one the type refuses, and the row came back as one every value tried was
 * refused at while the strings the rule admits are as many as anybody could want.
 *
 * <p>Held at the fill and not at the report. What a collection of several holds is decided where
 * values are built, and a test on the block an author reads would go on passing for a behavior
 * whose elements happen to be told apart another way.
 */
class ACollectionOfSeveralIsFilledFromWhatItsElementTypeAdmitsTest {

    /** A record whose one collection field the rules ask {@code floor} of, over {@code held}. */
    private static String model(String held, String floor, String measure) {
        return """
                module ex.fill

                data ContactId = String
                    invariant String.matches("003[0-9]{12}", value)

                data Held =
                    { xs: %s
                    }
                    invariant enough = %s

                data Ok = { n: Int }

                behavior countThem : (flag: Bool, h: Held) -> Ok
                    constructs Ok

                let countThem (flag, h) = Ok { n = %s }
                """.formatted(held, floor, measure);
    }

    private static FillResult filled(String source) {
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.answerEverything();
        String module = compilation.modules().get(0);
        Map<String, DeclaredSig> sigs =
                compilation.db().ask(new Bodies.DeclaredSignatures(module)).value();
        assertNotNull(sigs, "the model did not compile");
        RuleReadingSource rules = RuleReadings.of(compilation, module);
        InputDomain domain =
                InputDomain.of(sigs.get("countThem"), rules, ReadAs.THE_COMPILATION_DOES);
        Partitions.Partitioning partitioning =
                Partitions.of("countThem", domain, rules, ReadAs.THE_COMPILATION_DOES);
        return Generator.fill(MeasuredInput.of("countThem", domain.reading(rules), partitioning),
                List.of(), Generator.CandidateCheck.ANY, Budgets.generation());
    }

    /** The collection the first row was offered, which is the one the search reached first. */
    private static String heldInTheFirstRow(String source) {
        FillResult filled = filled(source);
        assertEquals(List.of(), filled.unresolved(), "nothing should have gone unresolved");
        return filled.rows().get(0).inputs().get(1).text();
    }

    /**
     * Two elements, and the rule that says which strings is read for both of them.
     *
     * <p>Two and not one, so that a fill reading the rule once is not mistaken for one reading it
     * for every element it composes.
     */
    @Test
    void aSetOfTwoHoldsTwoStringsTheRuleAccepts() {
        assertEquals("Held { xs = [ContactId(\"003000000000000\"),"
                + " ContactId(\"003000000000001\")] }",
                heldInTheFirstRow(model("Set<ContactId>", "Set.size(xs) >= 2", "Set.size(h.xs)")));
    }

    /**
     * And three, which is the same rule read again rather than a second element got another way.
     *
     * <p>A fill that answered two by stepping once off the first would pass the test above and have
     * nothing to offer here.
     */
    @Test
    void aSetOfThreeGoesOnTakingThemFromTheSameRule() {
        assertEquals("Held { xs = [ContactId(\"003000000000000\"),"
                + " ContactId(\"003000000000001\"), ContactId(\"003000000000002\")] }",
                heldInTheFirstRow(model("Set<ContactId>", "Set.size(xs) >= 3", "Set.size(h.xs)")));
    }

    /**
     * A list holds the same element again, which is what a list is.
     *
     * <p>Held beside the sets because the two are one change away from each other. What tells a
     * set's elements apart is the set's own rule, and a fill that took the values apart wherever it
     * could would write a list nothing asked to be told apart.
     */
    @Test
    void aListOfTwoHoldsOneOfThemTwice() {
        assertEquals("Held { xs = [ContactId(\"003000000000000\"),"
                + " ContactId(\"003000000000000\")] }",
                heldInTheFirstRow(model("List<ContactId>",
                        "List.length(xs) >= 2", "List.length(h.xs)")));
    }

    /** A map's keys are told apart the way a set's elements are, and the values under them are
     * free to repeat. */
    @Test
    void aMapOfTwoIsKeyedByTwoStringsTheRuleAccepts() {
        assertEquals("Held { xs = [(ContactId(\"003000000000000\"), 0),"
                + " (ContactId(\"003000000000001\"), 0)] }",
                heldInTheFirstRow(model("Map<ContactId, Int>",
                        "Map.size(xs) >= 2", "Map.size(h.xs)")));
    }

    /**
     * Two rules about the strings are met with each other, and every value clears both.
     *
     * <p>Which is not what a position is offered for itself. There each rule is a reason to propose
     * another value and the decoder says which of them the whole of the rules admits — a proposal
     * refused there costs one candidate. Inside a collection a refused element takes the whole
     * collection with it and no other element makes up for it, so what goes in comes from the meet.
     *
     * <p>Asked of the values and not of a row, because a row carries the element the collection was
     * built around as well, and that one is still a proposal of one rule's.
     */
    @Test
    void twoRulesAboutTheStringsAreBothReadForEveryValue() {
        Model model = modelOf("""
                module ex.narrow

                data Narrow = String
                    invariant shape = String.matches("003[0-9]{12}", value)
                    invariant begins = String.startsWith("0031", value)
                """);

        assertEquals(List.of("Narrow(\"003100000000000\")", "Narrow(\"003100000000001\")"),
                model.admitted("Narrow", 2));
    }

    /**
     * And nothing where no rule says which strings, which is where the length is what one more of
     * them is.
     *
     * <p>The negative control of the one above. Asked of every string type, this would answer for a
     * position carrying no rule about its strings at all, and the values a collection tells apart
     * would stop coming from the carrier for a reason nothing stated.
     */
    @Test
    void aTypeWithNoRuleAboutItsStringsAdmitsNoneOfThemHere() {
        Model model = modelOf("""
                module ex.plain

                data Plain = String
                    invariant long = String.length(value) >= 3
                """);

        assertEquals(List.of(), model.admitted("Plain", 2));
    }

    /** A model to read a type's own rules off, held to compiling. */
    private record Model(RuleReadingSource rules, String module) {

        /** Up to {@code many} of the strings {@code type}'s rules admit, as they would be written. */
        List<String> admitted(String type, int many) {
            Type named = new Type.Ref(TypeSymbols.declared(new TypeKey(module, type)));
            return Partitions.admittedStrings(named,
                            RuleReadingContext.unshared(rules, ReadAs.THE_COMPILATION_DOES), many)
                    .stream().map(FixtureTemplate::text).toList();
        }
    }

    private static Model modelOf(String source) {
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.answerEverything();
        String module = compilation.modules().get(0);
        assertEquals(List.of(), compilation.diagnostics().values().stream()
                        .flatMap(List::stream).map(each -> each.diagnostic().code()).toList(),
                "the model under test is a program that can be written");
        return new Model(RuleReadings.of(compilation, module), module);
    }

    /**
     * A type whose rules say nothing about which strings is still stepped by the carrier.
     *
     * <p>The other side of the claim, and the one a change here takes away by accident. Where no
     * rule about the strings was read there is nothing to take the values from, and a length is
     * what one more of them is — a fill that went to the rules whatever they said would have
     * nothing to offer at a position that carries none.
     */
    @Test
    void aStringNoRuleSpeaksOfIsStillToldApartByItsLength() {
        assertEquals("Held { xs = [\"x\", \"xx\"] }",
                heldInTheFirstRow(model("Set<String>", "Set.size(xs) >= 2", "Set.size(h.xs)")));
    }

    /**
     * A type with fewer values than the rules ask for composes nothing, and no row goes out.
     *
     * <p>{@code Bool} is two values and a set of three of them is a thing no value satisfies. What
     * is held to is that no row is offered: which word the search comes back with is a question
     * about how a search that found nothing is reported, and this one is about what is filled.
     */
    @Test
    void aTypeOfTooFewValuesIsNotFilledOutToTheCountAsked() {
        FillResult filled = filled(model("Set<Bool>", "Set.size(xs) >= 3", "Set.size(h.xs)"));

        assertEquals(List.of(), filled.rows(), "no row holds a set of three booleans");
        assertTrue(!filled.unresolved().isEmpty(), "and the combinations are said to be unresolved");
    }
}
