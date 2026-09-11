package souther.compiler.query;

import org.junit.jupiter.api.Test;

import souther.compiler.partition.ObligationIdentity;
import souther.compiler.report.AdequacyReport;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A case of an input and the class of its position are one obligation where the behavior has that
 * position, and a case of a behavior that has none is owed at itself.
 *
 * <p>Two derivations meet at one entry while both are about one behavior's own position: the
 * signature counts the cases a row applies the behavior to, and the partition counts the classes a
 * row sits in. A {@code >->} composition takes what its first stage takes and is read there, so it
 * has cases and no positions — and the classes that stage divides are that stage's, under its own
 * axis, discharged by that stage's rows.
 *
 * <p>Which is why the account may not key one there. A finding of the composition pointing at the
 * stage's entry is this account naming another behavior's obligation, which is the identity rebuilt
 * somewhere other than where the obligation is.
 */
class ACaseOfAnInputIsAClassOnlyWhereTheBehaviorHasThePositionTest {

    private static final String MODEL = """
            module example.composed

            data On
            data Off
            data Flag = On | Off
            data Mid = { flag: Flag }
            data Yes
            data No
            data Verdict = Yes | No

            behavior first : (f: Flag) -> Mid
                constructs Mid
            let first (f) = Mid { flag = f }

            behavior second : (m: Mid) -> Verdict
            let second (m) =
                match m.flag with
                    | On  -> Yes
                    | Off -> No

            behavior both = first >-> second

            example first
                | "on" : (On) -> Mid { flag = On }

            example both
                | "on" : (On) -> Yes
            """;

    /** The stage writes its cases at its own position, so the two derivations are one entry. */
    @Test
    void aBehaviorWithItsOwnPositionOwesTheCaseAtTheClassOfThatPosition() {
        About.ACaseNoRowAppliesItTo missing = caseFindingOf("first");
        ObligationIdentity.OfAClass owed = assertInstanceOf(ObligationIdentity.OfAClass.class,
                missing.obligationIdentity(),
                () -> "a case of a declared input is the class of its position: " + missing);
        assertEquals("first/f", owed.classOfAPosition().at().toString(),
                "which is the position the declaration names");
    }

    /** The composition writes its cases at no position of its own, so the case is the entry. */
    @Test
    void aCompositionOwesTheCaseAtTheCaseItself() {
        About.ACaseNoRowAppliesItTo missing = caseFindingOf("both");
        ObligationIdentity.OfAnInputCase owed = assertInstanceOf(
                ObligationIdentity.OfAnInputCase.class, missing.obligationIdentity(),
                () -> "a composition divides no position of its own: " + missing);
        assertEquals("both", owed.behavior(), "and the case is this behavior's");
        assertEquals(0, owed.at(), "at the input the signature takes first");
    }

    /**
     * And the two are not one obligation, however alike the values are.
     *
     * <p>The composition takes what its first stage takes, so the case it is short of and the case
     * the stage is short of are the same case of the same type. A row of the stage discharges the
     * stage's entry and says nothing about the composition's, which is what makes them two.
     */
    @Test
    void theCompositionsCaseIsNotTheStagesClass() {
        assertNotEquals(caseFindingOf("first").obligationIdentity(),
                caseFindingOf("both").obligationIdentity(),
                "a composition's case is not its first stage's class");
    }

    /** And the report is written, which is what the account keying one at a position nothing
     *  declares stopped. */
    @Test
    void theReportIsWrittenForAModelWithAComposition() {
        assertNotNull(AdequacyReport.of(measured()).json(id -> id == null ? null : id.value()),
                "a model with a composition has a report");
    }

    private static About.ACaseNoRowAppliesItTo caseFindingOf(String behavior) {
        List<Adequacy.Finding> found = measured().db()
                .ask(new Adequacy.Findings("example.composed")).value();
        for (Adequacy.Finding finding : found) {
            if (finding.about() instanceof About.ACaseNoRowAppliesItTo about
                    && finding.subject() instanceof FindingSubject.OfABehavior(String named)
                    && named.equals(behavior)) {
                return about;
            }
        }
        throw new AssertionError("no case of an input of `" + behavior + "` is missing: " + found);
    }

    private static Compilation measured() {
        Compilation compilation = Compilation.ofSource(MODEL, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        assertTrue(compilation.errors().isEmpty(),
                () -> "a model that did not compile answers every question with nothing: "
                        + compilation.errors());
        return compilation;
    }
}
