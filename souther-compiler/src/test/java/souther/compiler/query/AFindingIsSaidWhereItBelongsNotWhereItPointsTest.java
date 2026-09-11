package souther.compiler.query;

import souther.compiler.diag.Placement;
import souther.compiler.source.SourceId;

import org.junit.jupiter.api.Test;
import souther.compiler.diag.Diagnostic;
import souther.compiler.diag.Region;
import souther.compiler.diag.SourcePos;
import souther.compiler.diag.msg.ExampleMessage;
import souther.compiler.diag.msg.InvariantMessage;
import souther.compiler.diag.msg.NameMessage;
import souther.compiler.meta.ModulePath;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Where a report is said is read off what it says, and off nothing else.
 *
 * <p>The question a second region answers is whether the diagnostic passes judgment on it too. A
 * rule, a declaration or a definition shown because the primary was judged against it is not
 * somewhere the problem is written, however necessary it is to understanding the problem; two
 * statements that contradict, or the two ends of a relation that may not exist, are, because
 * neither of them is the premise the other is measured by.
 *
 * <p>So none of this turns on which module a compile happens to be walking. The same two regions
 * are the same problem in the same two files whichever of them the question was asked about, and a
 * caret that left its module says nothing about whether the module it landed in is in the wrong.
 */
class AFindingIsSaidWhereItBelongsNotWhereItPointsTest {

    private static final String A = """
            module a
            data N = { v: Int }
            """;

    private static final String B = """
            module b
            data M = { w: Int }
            """;

    private static Compilation twoModules() {
        Map<String, String> byId = new LinkedHashMap<>();
        byId.put("a.sou", A);
        byId.put("b.sou", B);
        return Compilation.ofDocuments(byId, Set.of(), ModulePath.EMPTY);
    }

    private static SourcePos in(String sourceId) {
        return Placement.aFileOfThisCompile(new SourceId(sourceId)).at(2, 1);
    }

    /** A report found about {@code module}, its caret in {@code caretIn}, pointing at {@code
     *  pointsAt} to say where a definition it names is written. */
    private static Db.Found pointingAtADefinition(String module, String caretIn, String pointsAt) {
        Diagnostic d = Diagnostic.say(new NameMessage.NoValueOfThatNameInScope("x"))
                .at(in(caretIn), 4)
                .secondary(Region.point(in(pointsAt)),
                        new NameMessage.ItIsExposedByAnotherModule("other", "x"))
                .build();
        return new Db.Found(module, null, Report.of(d));
    }

    /** The same, pointing at the rule the thing under the caret was judged against. */
    private static Db.Found judgedAgainstARuleIn(String module, String caretIn, String ruleIn) {
        Diagnostic d = Diagnostic
                .say(new InvariantMessage.TheValueIsOneTheInvariantRejects("Yen", "nonNegative"))
                .at(in(caretIn), 4)
                .secondary(Region.point(in(ruleIn)),
                        new InvariantMessage.ThisClauseRejectsThisValue())
                .build();
        return new Db.Found(module, null, Report.of(d));
    }

    /** The same, where the two regions are the ends of a relation that may not exist. */
    private static Db.Found aForbiddenRelationBetween(String module, String oneEnd,
                                                      String otherEnd) {
        Diagnostic d = Diagnostic
                .say(new InvariantMessage.TheNamedClauseConstructsAData("Table", "Yen", "ok"))
                .at(in(oneEnd), 4)
                .secondary(Region.point(in(otherEnd)),
                        new InvariantMessage.TheClauseReachesThatConstruction("ok"))
                .build();
        return new Db.Found(module, null, Report.of(d));
    }

    /** The same, where the two regions are statements that contradict each other. */
    private static Db.Found twoStatementsDisagreeingIn(String module, String one, String other) {
        Diagnostic d = Diagnostic.say(new ExampleMessage.TheRowAndTheFakeDisagree("f"))
                .at(in(one), 4)
                .secondary(Region.point(in(other)),
                        new ExampleMessage.TheFakeRowIsHere("f"))
                .build();
        return new Db.Found(module, null, Report.of(d));
    }

    // --- what a second region has to be for the report to be said there --------------------------

    /**
     * A rule the primary was judged against is the premise, not a second thing found wrong. Saying
     * the report there puts a marker on a line the diagnostic asserts nothing about — and where the
     * rule is a library's, on one its author cannot act on.
     */
    @Test
    void aRuleTheFindingIsJudgedAgainstIsOnlyAnExplanation() {
        Compilation c = twoModules();

        List<SourceId> saidAt = c.publishSourceIdsOf(judgedAgainstARuleIn("b", "a.sou", "b.sou"));

        assertEquals(List.of(new SourceId("a.sou")), saidAt,
                "the value is what is judged; the clause is what it was judged by");
    }

    /** Nor is a definition pointed at to explain a mistake written elsewhere. */
    @Test
    void aDefinitionPointedAtToExplainTheFindingIsOnlyAnExplanation() {
        Compilation c = twoModules();

        List<SourceId> saidAt = c.publishSourceIdsOf(pointingAtADefinition("b", "a.sou", "b.sou"));

        assertEquals(List.of(new SourceId("a.sou")), saidAt,
                "the caret left module b, which says nothing about where the problem is written");
    }

    /**
     * What is wrong is that the two are connected at all, so neither end is the premise the other
     * is measured by and the problem is written at both.
     */
    @Test
    void bothEndsOfAForbiddenRelationBelongToTheFinding() {
        Compilation c = twoModules();

        List<SourceId> saidAt = c.publishSourceIdsOf(aForbiddenRelationBetween("b", "a.sou", "b.sou"));

        assertEquals(List.of(new SourceId("a.sou"), new SourceId("b.sou")), saidAt);
    }

    /** Two statements that contradict: which of them the model is to be held to is not readable
     *  from the text, so neither is the rule and both are said. */
    @Test
    void twoConflictingStatementsBothBelongToTheFinding() {
        Compilation c = twoModules();

        List<SourceId> saidAt = c.publishSourceIdsOf(twoStatementsDisagreeingIn("b", "a.sou", "b.sou"));

        assertEquals(List.of(new SourceId("a.sou"), new SourceId("b.sou")), saidAt);
    }

    // --- and nothing about which module was being walked -----------------------------------------

    /**
     * The two ends are the same problem whichever module the question was asked about. A rule that
     * reads which of them the compile is walking is a rule about the traversal, and the author
     * editing either file is the same author either way.
     */
    @Test
    void theSameTwoRegionsAreSaidInTheSameFilesWhicheverModuleIsBeingChecked() {
        Compilation c = twoModules();

        List<SourceId> caretAway = c.publishSourceIdsOf(aForbiddenRelationBetween("b", "a.sou", "b.sou"));
        List<SourceId> caretHere = c.publishSourceIdsOf(aForbiddenRelationBetween("a", "a.sou", "b.sou"));

        assertEquals(caretAway, caretHere,
                "which module was being checked is not part of where a problem is written");
    }

    /** Two regions in one file are one place to say it. What is published is where to go, and a
     *  reader sent there twice is sent there once. */
    @Test
    void twoRegionsInOneFileStillMakeOnePublication() {
        Compilation c = twoModules();

        List<SourceId> saidAt = c.publishSourceIdsOf(aForbiddenRelationBetween("a", "a.sou", "a.sou"));

        assertEquals(List.of(new SourceId("a.sou")), saidAt);
    }
}
