package souther.compiler.partition;

import org.junit.jupiter.api.Test;

import souther.compiler.check.RuleReadingSource;
import souther.compiler.check.RuleReadings;
import souther.compiler.check.DeclaredSig;
import souther.compiler.core.Core;
import souther.compiler.coverage.CoverageSites;
import souther.compiler.inputs.InputDomain;
import souther.compiler.query.Bodies;
import souther.compiler.query.Compilation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Every shape a rule on a string takes, and what each is measured at.
 *
 * <p>Written out rather than left to be inferred, for the reason the carrier table is: a string is
 * the one carrier whose values have a least one and no next one, so what it can give up differs from
 * one end of a range to the other, and a rule that reads right for `+<+` can be silently wrong for
 * `+>+`. A table is what makes that visible — one row differing from its mirror is a question to
 * answer rather than a number to accept.
 *
 * <p>Three numbers per row and not one. The classes are what the rows are measured against, the
 * representatives are what a generated row would carry, and the obligations are what a build refuses
 * over — and a change that gets the first right and the third wrong is a partition nothing has to
 * cover.
 *
 * <p><b>What a string cannot give.</b> Above a value, nothing: every string with that one as a
 * prefix is greater, so a value exists and picking which would put a character nobody wrote into a
 * row somebody has to read. Below one, the empty string, which is the least there is rather than a
 * choice. That asymmetry is the whole of what a string costs, and it is why the rows below are not
 * symmetric and should not be made so.
 */
class WhatARuleOnAStringIsMeasuredAtTest {

    /**
     * One row.
     *
     * @param classes        the labels, in the order the position divides into them
     * @param representative what stands for each, or {@code none} where nothing here composes one
     * @param obligations    the lines a row is owed at, which is a build's denominator
     */
    private record Measured(List<String> classes, List<String> representative,
                            List<String> obligations) {}

    @Test
    void aLineBelowAValueGivesBothSidesAValue() {
        assertEquals(new Measured(
                        List.of("x < b", "b <= x"),
                        List.of("[]", "[b]"),
                        List.of("OFF b")),
                measured("guard x < QbQ else Newer"));
        assertEquals(new Measured(
                        List.of("x < a", "a <= x"),
                        List.of("[]", "[a]"),
                        List.of("ON a")),
                measured("guard x >= QaQ else Newer"));
    }

    /**
     * A line above a value gives the side below it a value and the side above it none.
     *
     * <p>The mirror of the pair above, and deliberately not its equal. The class above a strict
     * bound holds values — every string with the bound as a prefix — and none of them is one this
     * names, so it says it composed nothing rather than composing something.
     */
    @Test
    void aLineAboveAValueLeavesTheSideAboveItWithoutOne() {
        assertEquals(new Measured(
                        List.of("x <= b", "b < x"),
                        List.of("[b]", "none"),
                        List.of("ON b")),
                measured("guard x <= QbQ else Newer"));
        assertEquals(new Measured(
                        List.of("x <= a", "a < x"),
                        List.of("[a]", "none"),
                        List.of("OFF a")),
                measured("guard x > QaQ else Newer"));
    }

    /**
     * A range closed at the top gives up its top, whichever way the bottom is written.
     *
     * <p>The end a range holds is a value the model wrote, and taking it is not inventing anything.
     * Read off the bottom alone, `+"a" < x <= "b"+` came back with nothing while holding `+"b"+`.
     */
    @Test
    void aRangeClosedAtTheTopGivesUpItsTop() {
        assertEquals(new Measured(
                        List.of("x <= a", "a < x <= b", "b < x"),
                        List.of("[a]", "[b]", "none"),
                        List.of("OFF a", "ON b")),
                measured("guard x > QaQ && x <= QbQ else Newer"));
        assertEquals(new Measured(
                        List.of("x < a", "a <= x < b", "b <= x"),
                        List.of("[]", "[a]", "[b]"),
                        List.of("ON a", "OFF b")),
                measured("guard x >= QaQ && x < QbQ else Newer"));
    }

    /**
     * A value singled out leaves everything else, and the least string stands for it.
     *
     * <p>Both spellings divide the position the same way. What is left over is not a range and does
     * not have to be: what a class needs is a way to say whether a value is in it and a value that
     * stands for it, and the empty string is one wherever it is not the value singled out.
     *
     * <p>What the two spellings do not share is which point the row at the value is. {@code == "foo"}
     * puts the value inside the partition it names, so a row there is its {@code ON} point;
     * {@code /= "foo"} puts it outside, so the same row is that border's {@code OFF} point. One row
     * and two readings of it, which is what naming the point is for.
     */
    @Test
    void aValueSingledOutLeavesTheLeastStringForEverythingElse() {
        assertEquals(new Measured(
                        List.of("= foo", "/= foo"),
                        List.of("[foo]", "[]"),
                        List.of("ON foo")),
                measured("guard x == QfooQ else Newer"));
        assertEquals(new Measured(
                        List.of("= foo", "/= foo"),
                        List.of("[foo]", "[]"),
                        List.of("OFF foo")),
                measured("guard x /= QfooQ else Newer"));
    }

    /**
     * And where the least string is the one singled out, the rest is still stood for.
     *
     * <p>The value is looked for in what the class holds, which is the strings the position admits
     * less the one singled out. Looked for in what the position admits and refused afterwards
     * where it turns out to be the singled one, the class would come back with nothing to stand
     * for it — and every string but the empty one is in it.
     */
    @Test
    void singlingOutTheLeastStringStillLeavesTheRestOne() {
        assertEquals(new Measured(
                        List.of("= ", "/= "),
                        List.of("[]", "[a]"),
                        List.of("ON ")),
                measured("guard x == QQ else Newer"));
    }

    private static Measured measured(String guard) {
        String source = """
                module example.shape

                data Newer
                data Older
                data Era = Newer | Older

                behavior f : (x: String) -> Era
                let f (x) = {
                    GUARD
                    Older
                }
                """.replace("GUARD", guard.replace("Q", "\""));
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.answerEverything();
        String module = compilation.modules().get(0);
        RuleReadingSource rules = RuleReadings.of(compilation, module);
        Map<String, DeclaredSig> sigs =
                compilation.db().ask(new Bodies.DeclaredSignatures(module)).value();
        Bodies.Elaborated checked = compilation.db().ask(new Bodies.Checked(module)).value();
        assertNotNull(checked, "the model under test compiles: " + guard);

        CoverageSites.Plan plan = checked.plan();
        Core body = checked.behaviorBodies().get("f");
        GuardThresholds.Guards guards = GuardThresholds.of("f",
                checked.analysisBodies().get("f"), body, plan,
                compilation.db().ask(new souther.compiler.query.Adequacy.Inputs(module)).value().get("f"), rules);
        InputDomain read = InputDomain.of(sigs.get("f"), rules,
                souther.compiler.query.ReadAs.THE_COMPILATION_DOES);
        souther.compiler.inputs.Quantities reading = read.quantities(rules);
        Partitions.Partitioning p = Partitions.withThresholds(
                Partitions.of("f", read, rules, souther.compiler.query.ReadAs.THE_COMPILATION_DOES),
                reading,
                guards.thresholds(), rules, souther.compiler.query.ReadAs.THE_COMPILATION_DOES,
                souther.compiler.inputs.RulesWithNoLine.NONE, guards.singled(),
                souther.compiler.values.Allowance.of(souther.compiler.regex.PatternPlan.Budget.OF_BEHAVIOR_DISTINCTIONS));

        List<String> classes = new ArrayList<>();
        List<String> stands = new ArrayList<>();
        List<String> owed = new ArrayList<>();
        for (Axis axis : p.axes()) {
            for (PartitionClass each : axis.classes()) {
                classes.add(each.label());
                List<FixtureTemplate> made =
                        Partitions.standingFor(each.representatives(), rules, souther.compiler.query.ReadAs.THE_COMPILATION_DOES, java.util.Set.of());
                stands.add(made.isEmpty() ? "none"
                        : made.stream().map(FixtureTemplate::text)
                                .map(WhatARuleOnAStringIsMeasuredAtTest::bare).toList().toString());
            }
            Partitions.bordersOf(axis, reading, reading.runsBetween(axis.term()), new LinesRead())
                    .forEach(border -> border.answers().keySet().stream()
                            .filter(DomainPoint::againstTheLine)
                            .filter(point -> border.demand(point).criterion() != null)
                            .forEach(point -> owed.add(border.named(point) + " "
                                    + border.demand(point).criterion()
                                            .asked(border.cut().of()).substring(2))));
        }
        return new Measured(classes, stands, owed);
    }

    /** A fixture writes a string quoted; what this row is about is the string. */
    private static String bare(String written) {
        return written.startsWith("\"") && written.endsWith("\"")
                ? written.substring(1, written.length() - 1) : written;
    }
}
