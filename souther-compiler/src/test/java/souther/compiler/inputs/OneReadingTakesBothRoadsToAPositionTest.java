package souther.compiler.inputs;

import org.junit.jupiter.api.Test;

import souther.compiler.check.DeclaredSig;
import souther.compiler.check.RuleReadingSource;
import souther.compiler.check.RuleReadings;
import souther.compiler.query.Bodies;
import souther.compiler.query.Compilation;
import souther.compiler.query.ReadAs;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * One reading, reached two ways, with the demands handed to it directly.
 *
 * <p>What a body names is collected somewhere else and arrives here as paths, so this is where the
 * reading's own side of the arrangement can be asked without a body in the way: hand it one deep
 * path and see what comes back. What must come back is that path and the positions the enumeration
 * finds — and not the occurrences walked through on the way, which exist so the reading knows which
 * step to take and whose rules reach the end.
 */
class OneReadingTakesBothRoadsToAPositionTest {

    private static final String CHAIN = """
            module g

            data Nil
            data Cons = { head: Int, tail: Chain }
            data Chain = Nil | Cons

            data Ok

            behavior read : (c: Chain) -> Ok
            """;

    /** Four links down, which the enumeration stops three links short of. */
    private static TermPath fourLinksDown() {
        TermPath at = TermPath.of("c");
        for (int i = 0; i < 4; i++) {
            at = at.refine(caseOf("Cons")).then(i == 3 ? "head" : "tail");
        }
        return at;
    }

    /**
     * The demanded path is a position, and what was walked through to reach it is not.
     *
     * <p>The whole of the distinction, in one reading. Between {@code c} and the head four links
     * down there are three more {@code Chain}s and three more {@code Cons}es; the reading opened
     * every one of them, because that is how it knows the fourth {@code head} is an {@code Int} and
     * whose clauses bound it. None of them is reported, because nothing named them.
     */
    @Test
    void whatWasWalkedThroughIsNotReported() {
        InputDomain read = reading(fourLinksDown());

        assertNotNull(read.at(fourLinksDown()), () -> spelled(read));
        assertNull(read.at(TermPath.of("c").refine(caseOf("Cons")).then("tail")
                        .refine(caseOf("Cons")).then("head")),
                () -> "one link down is on the way to it and nothing named it: " + spelled(read));
    }

    /** And the enumeration is unchanged by the demand: it stops where it always stops. */
    @Test
    void theEnumerationIsWhatItWasWithoutTheDemand() {
        InputDomain alone = readingOf(CHAIN, "read", InputDemand.NONE);
        InputDomain asked = reading(fourLinksDown());

        for (Position each : alone.positions()) {
            assertNotNull(asked.at(each.path()),
                    () -> each.path() + " is what the enumeration found: " + spelled(asked));
        }
        assertEquals(alone.positions().size() + 1, asked.positions().size(),
                () -> "and one more, which is what was named: " + spelled(asked));
    }

    /**
     * Two demands sharing a prefix read that prefix once.
     *
     * <p>Held on what the rules of the values the reading opened placed. Opening a declaration is
     * what puts entries there, so a walk replayed once per demand would open every declaration on
     * the shared prefix again and place its rules again — and the account a build has to answer for
     * would grow with how many demands happened to run through it.
     */
    @Test
    void twoDemandsSharingAPrefixOpenItOnce() {
        TermPath head = fourLinksDown();
        TermPath tail = sibling();

        assertEquals(placedIn(reading(List.of(head))), placedIn(reading(List.of(head, tail))),
                "the prefix the two share is opened once, so it places its rules once");
    }

    /** How many placements the reading came back with, which is one per rule of every value it
     *  opened. */
    private static int placedIn(InputDomain read) {
        return read.placements().size();
    }

    /** The `tail` beside the demanded `head`, four links down. */
    private static TermPath sibling() {
        TermPath at = TermPath.of("c");
        for (int i = 0; i < 4; i++) {
            at = at.refine(caseOf("Cons")).then("tail");
        }
        return at;
    }

    /** And the answer does not turn on which order the demands arrived in. */
    @Test
    void theOrderTheDemandsArrivedInDecidesNothing() {
        TermPath head = fourLinksDown();
        TermPath tail = sibling();

        assertEquals(spelled(reading(List.of(head, tail))), spelled(reading(List.of(tail, head))));
    }

    /** The narrowing to one leaf, spelled the way the checker's resolution of an arm spells it: a
     *  leaf is a case that covers itself, so selecting it narrows to that one distinction. */
    private static Refinement caseOf(String name) {
        souther.compiler.types.TypeSymbol leaf = souther.compiler.types.TypeSymbols.declared(
                new souther.compiler.types.TypeKey("g", name));
        return Refinement.of(souther.compiler.types.ResolvedCase.of(
                souther.compiler.types.CaseSelector.direct(leaf), List.of(leaf)));
    }

    private static InputDomain reading(TermPath demanded) {
        return reading(List.of(demanded));
    }

    private static InputDomain reading(List<TermPath> demanded) {
        return readingOf(CHAIN, "read", new InputDemand(demanded));
    }

    private static String spelled(InputDomain read) {
        return read.positions().stream().map(each -> each.path().toString()).toList().toString();
    }

    private static InputDomain readingOf(String source, String behavior, InputDemand demand) {
        Compilation compilation =
                Compilation.ofSources(List.of(source), souther.compiler.meta.ModulePath.EMPTY);
        compilation.answerEverything();
        String module = compilation.modules().get(0);
        Map<String, DeclaredSig> sigs =
                compilation.db().ask(new Bodies.DeclaredSignatures(module)).value();
        RuleReadingSource rules = RuleReadings.of(compilation, module);
        return InputDomain.of(sigs.get(behavior), List.of(), rules,
                ReadAs.THE_COMPILATION_DOES, demand);
    }
}
