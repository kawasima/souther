package souther.compiler.check;

import souther.compiler.diag.DiagnosticPlace;
import souther.compiler.meta.ModulePath;
import souther.compiler.query.Bodies;
import souther.compiler.query.Compilation;
import souther.compiler.query.Key;
import souther.compiler.query.Shapes;
import souther.compiler.types.TypeKey;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a construction was judged against, and where the clause it was judged against is written.
 *
 * <p>Two answers to two questions, and an edit that moves the clause without changing a word of it
 * moves one of them. What the judgment says is what the declaration states, which the edit leaves
 * alone; where a report sends a reader is where the text is now, which the edit decides.
 *
 * <p>They used to be one answer. A judgment held the clause with the place it is written at, so a
 * line inserted above the declaration made a judgment that was not the judgment before it — of the
 * same clause, stating the same thing, about the same construction. Every reader of a judgment was
 * a reader of where the clause is written, whether it pointed anywhere or not.
 *
 * <p>What this fixes is not a report this compiler gets wrong today. It is what a cut at the module
 * boundary would get wrong: a boundary that let a reading stand because the clauses still mean what
 * they meant would leave the judgment cached, and a judgment carrying a place is a report pointing
 * at where the clause used to be. Asked of the declaration instead, the place is worked out again
 * whether or not the judgment was.
 */
class WhatAClauseMeansAndWhereItIsWrittenAreTwoAnswersTest {

    private static final String DECLARING = """
            module limits exposing ( Small )

            data Small = String
                invariant String.length(value) <= 3
            """;

    /** Constructs the imported value, which is what makes its clause something to be judged by. */
    private static final String CONSTRUCTING = """
            module app

            import limits ( Small )

            behavior make : (s: String) -> Small
                constructs Small

            let make (s) = Small(s)
            """;

    /** The same declaration, a line further down, with nothing it states changed. */
    private static final String MOVED = DECLARING.replace("data Small",
            "// what an author types while reading their own model back\ndata Small");

    private static final TypeKey SMALL = new TypeKey("limits", "Small");

    @Test
    void anEditThatMovesTheClauseLeavesWhatTheConstructionWasJudgedAgainst() {
        InvariantChecker.Judgment before = judgmentOn(DECLARING);
        InvariantChecker.Judgment after = judgmentOn(MOVED);

        assertFalse(before.found().isEmpty(),
                "the construction is judged against the clause the import declares, or the two"
                        + " answers below are being compared over nothing");
        assertEquals(before, after,
                "the clause states what it stated, so the judgment is the judgment it was");
    }

    @Test
    void andTheSameEditMovesWhereAReportIsSentToFindIt() {
        assertEquals(List.of(4), clauseLines(DECLARING),
                "the `invariant` line the declaration writes");
        assertEquals(List.of(5), clauseLines(MOVED),
                "and the line it writes it on once a line is inserted above it");

        assertNotEquals(clauseLines(DECLARING), clauseLines(MOVED));
    }

    /**
     * And the check that reports about the construction depends on where the clause is written.
     *
     * <p>The edge is what this is all for, and it is the one thing neither answer above shows: two
     * answers that differ say nothing about whether the reader of the first asks for the second. A
     * report that worked the place out without recording that it had would go on saying where the
     * clause was, on the day a cut lets the reading itself stand.
     */
    @Test
    void andTheCheckThatReportsAboutTheConstructionDependsOnWhereTheClauseIsWritten() {
        Compilation compilation = answered(DECLARING);

        Set<Key<?>> read =
                compilation.db().dependenciesOf(new Bodies.CheckedBehavior("app", "make"));

        assertTrue(read.contains(new Shapes.ClauseLocationsFor(SMALL)),
                "checking the body asks where the clause it reports about is written");
        assertFalse(read.contains(new Shapes.ClauseLocationsFor(new TypeKey("limits", "Large"))),
                "and what it read is what it asked for: nothing declares a `Large` to ask about");
    }

    /**
     * The judgment for the construction in {@code app}, taken through the seam the check reports
     * through.
     *
     * <p>Only {@code limits} is edited between the two compiles, so nothing about the constructing
     * module — where its body is, what it names — differs. What is left to differ is what the
     * imported declaration says, and the edit says nothing new.
     */
    private static InvariantChecker.Judgment judgmentOn(String declaring) {
        List<InvariantChecker.Said> said = Collections.synchronizedList(new ArrayList<>());
        InvariantChecker.WATCHING = said;
        try {
            answered(declaring);
        } finally {
            InvariantChecker.WATCHING = null;
        }
        return said.stream().filter(one -> one.type().equals("Small")).findFirst().orElseThrow()
                .judgment();
    }

    /** The lines a reader is sent to for {@code Small}'s clauses, asked of the declaration. */
    private static List<Integer> clauseLines(String declaring) {
        return answered(declaring).db().ask(new Shapes.ClauseLocationsFor(SMALL)).value().stream()
                .map(place -> ((DiagnosticPlace.InSource) place).region().start().line())
                .toList();
    }

    private static Compilation answered(String declaring) {
        Map<String, String> byId = new LinkedHashMap<>();
        byId.put("limits.sou", declaring);
        byId.put("app.sou", CONSTRUCTING);
        Compilation compilation = Compilation.ofDocuments(byId, Set.of(), ModulePath.EMPTY);
        compilation.answerEverything();
        return compilation;
    }
}
