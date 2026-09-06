package souther.compiler.inputs;

import org.junit.jupiter.api.Test;

import souther.compiler.query.Adequacy;
import souther.compiler.query.Compilation;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a list holds is a position of the input, read the way every other position is.
 *
 * <p>A coordinate and nothing else is added. Which element it names is not a question a path
 * answers, and the reading here asks the element's own declarations exactly what it asks a bare
 * parameter's — so a bound written on the element's type is a bound at this position, with no
 * combinator, no closure and no body involved in reaching it.
 *
 * <p>Beside the list and not instead of it. A record is given up in favour of its fields because it
 * states nothing of its own; a list carries a length that rules are written about, so it stays a
 * position and what it holds is another one.
 */
class WhatASequenceHoldsIsAPositionTest {

    private static final String MODULE = "example.roster";

    private static final String MODEL = """
            module example.roster

            data Age = Int
                invariant value >= 18
                invariant value <= 120
            data Person =
                { age: Age
                }

            data Size = Int

            behavior roster : (people: List<Person>) -> Size
                constructs Size
            let roster (people) = Size(List.length(people))
            """;

    private static List<Position> positionsOfRoster() {
        Compilation compilation = Compilation.ofSource(MODEL, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        Map<String, InputDomain> inputs =
                compilation.db().ask(new Adequacy.Inputs(MODULE)).value();
        assertNotNull(inputs, "the model under test compiles");
        InputDomain read = inputs.get("roster");
        assertNotNull(read, "roster has an input to read");
        return read.positions();
    }

    private static Position at(String path) {
        return positionsOfRoster().stream()
                .filter(each -> each.path().toString().equals(path)).findFirst()
                .orElseThrow(() -> new AssertionError("no position at " + path + " among "
                        + positionsOfRoster().stream().map(each -> each.path().toString())
                                .toList()));
    }

    /** The list, what it holds, and the field of what it holds. */
    @Test
    void theWalkReachesWhatTheListHolds() {
        assertEquals(List.of("people", "people[*]", "people[*].age"),
                positionsOfRoster().stream().map(each -> each.path().toString()).toList());
    }

    /**
     * The list is still a position of its own.
     *
     * <p>What is under it is read beside it. Read as a position given up in favour of what is
     * inside — the answer a record gets — a {@code guard List.length(people) < 3} would draw a line
     * on a position this reading no longer holds.
     */
    @Test
    void theListItselfIsStillRead() {
        assertInstanceOfInside(at("people").structure());
    }

    private static void assertInstanceOfInside(StructuralInspection structure) {
        assertTrue(structure instanceof StructuralInspection.Retained retained
                        && retained.continuation()
                                instanceof StructuralInspection.Continuation.Elements,
                () -> "a sequence holds a position and stands as one: " + structure);
        assertTrue(structure instanceof StructuralInspection.Retained,
                () -> "and is still to be answered for, unlike a record: " + structure);
    }

    /** A bound on the element's own type is a bound at the element's position. */
    @Test
    void theElementsOwnTypeStatesWhereItsValuesStop() {
        Position age = at("people[*].age");
        PositionBounds own = age.boundsFor(new NumericTerm.ValueOf(age.path()));
        assertNotNull(own.ownEnds(), "the element's field carries the ends its own type states");
        assertEquals(List.of("people[*].age"),
                age.numbers().stream().map(each -> each.position().toString()).distinct().toList(),
                "and every number measured there is a number of that position");
    }

    /**
     * And nothing is left short of rules that were never written.
     *
     * <p>The list below carries no clause about what it holds, so there is nothing for a reading to
     * have missed there and the elements are as read as any other position. Said unconditionally,
     * every element of every list came back as one nothing had read — which stops an absence being
     * reported wherever it is true, and reads to an author as a measurement that could not look.
     */
    @Test
    void nothingIsShortOfARuleThatWasNeverWritten() {
        assertTrue(at("people[*]").rulesLeftUnread().isEmpty(),
                "no clause of this list says anything about what it holds");
        assertTrue(at("people[*].age").rulesLeftUnread().isEmpty(),
                "nor at a field of what it holds");
        assertTrue(at("people").rulesLeftUnread().isEmpty(),
                "and the list's own rules are reached, as any other position's are");
    }

    /**
     * Where a clause does state something of every element, it is the sequence that is short of it.
     *
     * <p>A relation over every element is held as a quantifier over the clause it was written in,
     * and nothing here places one at the position it is about. What that leaves is a question the
     * model raised about the sequence and nothing answered — and the sequence is where the clause is
     * written, which is what an author would edit.
     *
     * <p>Said at the element instead, one rule was short at two positions: the elements carry
     * readings of their own now, and what {@code Person} states about itself is read there. So an
     * element reported as a position nothing had read was reporting the container's clause under the
     * element's name, and no row written at the element could ever discharge it (#1072).
     */
    @Test
    void aClauseStatedOfEveryElementIsShortAtTheSequenceItIsWrittenAbout() {
        Compilation compilation = Compilation.ofSource("""
                module example.roster

                data Age = Int
                data Person =
                    { age: Age
                    }
                data Roster = List<Person>
                    invariant grown = List.all(p -> p.age.value >= 18, value)
                data Size = Int

                behavior roster : (people: Roster) -> Size
                    constructs Size
                let roster (people) = Size(List.length(people))
                """, "Main");
        compilation.answerEverything();
        InputDomain read = compilation.db().ask(new Adequacy.Inputs(MODULE)).value().get("roster");
        assertNotNull(read, "the model under test compiles");

        assertTrue(read.at(pathTo("people", "[*]")).rulesLeftUnread().isEmpty(),
                "the element is read by a reading of its own, and this clause is not its");
        assertTrue(read.positions().get(0).rulesLeftUnread().isEmpty(),
                "and the list itself was read: the clause is written about it and arrived");
        assertFalse(read.positions().get(0).unansweredQuestions().isEmpty(),
                "what the clause leaves is a question about the list that nothing answered");
    }

    /** {@code head} with the steps spelled, for a lookup by the coordinate a report names. */
    private static TermPath pathTo(String head, String... steps) {
        TermPath at = TermPath.of(head);
        for (String step : steps) {
            at = step.equals("[*]") ? at.element() : at.then(step);
        }
        return at;
    }
}
