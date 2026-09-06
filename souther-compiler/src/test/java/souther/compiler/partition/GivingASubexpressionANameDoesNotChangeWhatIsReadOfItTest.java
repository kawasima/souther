package souther.compiler.partition;

import org.junit.jupiter.api.Test;

import souther.compiler.ast.Hir;
import souther.compiler.check.RuleReadingSource;
import souther.compiler.check.RuleReadings;
import souther.compiler.check.Prepared;
import souther.compiler.core.Core;
import souther.compiler.query.Adequacy;
import souther.compiler.query.BorderAssessment;
import souther.compiler.query.Bodies;
import souther.compiler.query.Compilation;
import souther.compiler.query.PartitionEvidence;
import souther.compiler.query.Shapes;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * A name given a subexpression is that subexpression, so binding one changes nothing about what the
 * model is read to say.
 *
 * <p>What a {@code let} binds is evaluated on the way to the answer, so a body that names an
 * expression and then compares the name is comparing the expression. A reading that stopped at the
 * name answered a position or nothing, and a name given arithmetic over positions is neither — so
 * one such name took the whole rule away: the line the model draws was not drawn, the positions the
 * rule is about were not named, and the reason a rule went unread was not given either, because no
 * reader got as far as having a rule to report.
 *
 * <p>Measured against the spelling that writes the expression where it is compared, rather than
 * against numbers written down here. What the two have to agree on is the positions, the lines and
 * the reasons, and an expectation repeated twice would go on holding if both fell silent together.
 *
 * <p>The last pair is the one that must not agree by reading through. An operation's answer holding
 * what a closure made of an element is not the element's position, so a name standing for one of
 * those values is not the expression the closure wrote — put where the name stands, it would draw a
 * line at a position whose values are not the ones the rule is about, which an author cannot tell
 * from a line their model states.
 */
class GivingASubexpressionANameDoesNotChangeWhatIsReadOfItTest {

    private static final String MODEL = """
            module example.named

            data Temp = Int
                invariant value >= 0
                invariant value <= 500

            data Mile = Int
                invariant value >= 0
                invariant value <= 200000

            data Low = { v: Int }
            data High = { v: Int }
            data Level = Low | High

            behavior onePosition : (t: Temp) -> Level
                constructs Low, High
            let onePosition (t) = if t.value < 240 then Low { v = 1 } else High { v = 2 }
            example onePosition
                | "under" : (Temp(1)) -> Low { v = 1 }

            behavior onePositionNamed : (t: Temp) -> Level
                constructs Low, High
            let onePositionNamed (t) = {
                let v = t.value
                if v < 240 then Low { v = 1 } else High { v = 2 }
            }
            example onePositionNamed
                | "under" : (Temp(1)) -> Low { v = 1 }

            behavior overOnePosition : (t: Temp) -> Level
                constructs Low, High
            let overOnePosition (t) = if t.value + 10 < 240 then Low { v = 1 } else High { v = 2 }
            example overOnePosition
                | "under" : (Temp(1)) -> Low { v = 1 }

            behavior overOnePositionNamed : (t: Temp) -> Level
                constructs Low, High
            let overOnePositionNamed (t) = {
                let v = t.value + 10
                if v < 240 then Low { v = 1 } else High { v = 2 }
            }
            example overOnePositionNamed
                | "under" : (Temp(1)) -> Low { v = 1 }

            behavior overTwoPositions : (last: Mile, this: Mile) -> Level
                constructs Low, High
            let overTwoPositions (last, this) =
                if last.value + this.value < 25000 then Low { v = 1 } else High { v = 2 }
            example overTwoPositions
                | "under" : (Mile(1), Mile(1)) -> Low { v = 1 }

            behavior overTwoPositionsNamed : (last: Mile, this: Mile) -> Level
                constructs Low, High
            let overTwoPositionsNamed (last, this) = {
                let total = last.value + this.value
                if total < 25000 then Low { v = 1 } else High { v = 2 }
            }
            example overTwoPositionsNamed
                | "under" : (Mile(1), Mile(1)) -> Low { v = 1 }
            """;

    /** The spellings that bind first, each against the one that writes the expression where it is
     *  compared. */
    private static final Map<String, String> PAIRS = Map.of(
            "onePositionNamed", "onePosition",
            "overOnePositionNamed", "overOnePosition",
            "overTwoPositionsNamed", "overTwoPositions");

    private static Map<String, PartitionEvidence> measured(String model, String module) {
        Compilation compilation = Compilation.ofSource(model, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        return compilation.db().ask(new Adequacy.Coverage(module)).value();
    }

    /** The lines each behavior's positions met, whosever the row at each point is. */
    private static Map<String, List<BorderAssessment>> lines(String model, String module) {
        Compilation compilation = Compilation.ofSource(model, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        return Adequacy.readingsOf(compilation.db(), module);
    }

    private static List<String> axesOf(PartitionEvidence evidence) {
        return evidence.axes().stream().map(axis -> axis.path() + ": " + axis.classes()).toList();
    }

    /** Every point of every line, and whether a row is owed at it. Not only the owed ones: a line
     *  whose points no row can be written at is still the line the rule draws, and a spelling that
     *  lost it would look like a spelling that kept it under a filter for what is owed. */
    private static List<String> linesOf(List<BorderAssessment> lines) {
        return BorderAssessment.pointsOf(lines).stream()
                .filter(point -> point.role().againstTheLine())
                .map(point -> point.label() + " " + point.role()
                        + (point.owed() != null ? " owed" : " excluded"))
                .sorted()
                .toList();
    }

    /** What each rule this could not read is about and why. The behavior and the citation are left
     *  out: those are what differs between two spellings of one rule, and everything else is what
     *  must not. */
    private static List<String> reasonsOf(PartitionEvidence evidence) {
        return evidence.notRead().stream()
                .map(found -> found instanceof PartitionEvidence.NotRead.ARule rule
                        ? rule.finding().at() + " " + rule.finding().why() : found.toString())
                .sorted()
                .toList();
    }

    /** A position named first, which is what already held. */
    @Test
    void aNameGivenAPositionIsThatPosition() {
        agree("onePositionNamed");
    }

    /** And arithmetic over a position named first, which is what took the line away. */
    @Test
    void aNameGivenArithmeticOverAPositionIsThatArithmetic() {
        // The pair says the two spellings agree; this says what they agree on is a line, so a
        // reading that lost it in both would not pass for agreement.
        assertFalse(linesOf(lines(MODEL, "example.named").get("overOnePosition")).isEmpty(),
                "the spelling that writes the arithmetic draws a line");
        agree("overOnePositionNamed");
    }

    /**
     * And arithmetic over two positions, where what has to agree is the reason as much as the line.
     *
     * <p>A rule relating two positions draws a line and divides neither of them, and the report says
     * so at each position. The bound spelling said nothing at all — which reads as a model with no
     * rule there rather than a rule this could not turn into a class.
     */
    @Test
    void aNameGivenArithmeticOverTwoPositionsIsThatArithmetic() {
        Map<String, PartitionEvidence> measured = measured(MODEL, "example.named");
        assertFalse(reasonsOf(measured.get("overTwoPositions")).isEmpty(),
                "the spelling that writes the arithmetic reports a rule it could not read");
        agree("overTwoPositionsNamed");
    }

    private static void agree(String named) {
        Map<String, PartitionEvidence> measured = measured(MODEL, "example.named");
        Map<String, List<BorderAssessment>> lines = lines(MODEL, "example.named");
        PartitionEvidence written = measured.get(PAIRS.get(named));
        PartitionEvidence bound = measured.get(named);
        assertEquals(cutsOf(PAIRS.get(named)), cutsOf(named), named);
        assertEquals(axesOf(written), axesOf(bound), named);
        assertEquals(linesOf(lines.get(PAIRS.get(named))), linesOf(lines.get(named)), named);
        assertEquals(reasonsOf(written), reasonsOf(bound), named);
    }

    /**
     * What each of a behavior's comparisons cuts, which is the affine reading itself.
     *
     * <p>Asked here as well as through the report because everything else is downstream of it. Two
     * readings that came out as different quantities can be projected onto one line — a coefficient
     * dropped and the threshold moved to match reads as the same line at the same value — and the
     * statement this is about is that the two spellings are read as one quantity, not that what
     * survives the projection matches.
     *
     * <p>Read off the reading rather than built here. What a comparison is read in is the walk's
     * environment, and a test that assembled one would be a second walk with its own account of what
     * the names on the way meant, which is the defect this is about one layer up.
     */
    private static List<String> cutsOf(String behavior) {
        Compilation compilation = Compilation.ofSource(MODEL, "Main");
        compilation.answerEverything();
        String module = compilation.modules().get(0);
        Prepared prepared = compilation.db().ask(new Shapes.Prepared(module)).value();
        RuleReadingSource rules = RuleReadings.of(compilation, module);
        Bodies.Elaborated checked = compilation.db().ask(new Bodies.Checked(module)).value();
        Hir.SpecBehavior spec = (Hir.SpecBehavior) prepared.behaviors().stream()
                .filter(each -> each.name().equals(behavior)).findFirst().orElseThrow();
        Core body = checked.behaviorBodies().get(spec.name());
        GuardThresholds.Guards guards = GuardThresholds.of(behavior, body,
                checked.plan(),
                compilation.db().ask(new Adequacy.Inputs(module)).value().get(behavior), rules);
        List<String> out = new java.util.ArrayList<>();
        // The quantity a line is on and where it cuts it, with what names the behavior left out:
        // an axis and an origin carry the behavior's own name, which is the one thing two spellings
        // of one rule cannot agree on.
        guards.thresholds().forEach(each ->
                out.add(each.term() + " " + each.parts().below() + "|" + each.parts().above()
                        + " belongs=" + each.valueBelongs()));
        guards.between().forEach(each -> out.add(quantityOf(each.cuts().of())
                + " at " + each.cuts().at() + " " + each.cuts().claim()));
        return out.stream().sorted().toList();
    }

    private static String quantityOf(BorderQuantity quantity) {
        return switch (quantity) {
            case BorderQuantity.OfACoordinate one -> one.term() + " on " + one.of();
            case BorderQuantity.OverAForm form -> form.form() + " on " + form.on();
            case BorderQuantity.Apart apart ->
                    apart.onTerm() + " vs " + apart.againstTerm()
                            + " on " + apart.on() + ", " + apart.against();
        };
    }

    private static final String DERIVED = """
            module example.roster

            data Person = { age: Int }
            data Count = Int

            behavior counted : (people: List<Person>) -> Count
                constructs Count
            let counted (people) =
                Count(List.length(List.filter(n -> n >= 18, List.map(p -> p.age, people))))

            behavior countedNamed : (people: List<Person>) -> Count
                constructs Count
            let countedNamed (people) = {
                let ages = List.map(p -> p.age, people)
                Count(List.length(List.filter(n -> n >= 18, ages)))
            }
            """;

    /**
     * And a name standing for what a closure made of an element is not that closure's expression.
     *
     * <p>The two spellings agree here as well, and what they agree on is that nothing is claimed.
     * Reading the name through to what the expansion bound it to would reach {@code people[*].age}
     * and draw a line there, at values the rule about the mapped elements is not about.
     */
    @Test
    void butANameStandingForWhatAClosureMadeIsNotWhatTheClosureWrote() {
        Map<String, PartitionEvidence> measured = measured(DERIVED, "example.roster");
        assertEquals(List.of(), axesOf(measured.get("counted")));
        assertEquals(List.of(), axesOf(measured.get("countedNamed")));
        Map<String, List<BorderAssessment>> lines = lines(DERIVED, "example.roster");
        assertEquals(List.of(), linesOf(lines.get("counted")));
        assertEquals(List.of(), linesOf(lines.get("countedNamed")));
    }
}
