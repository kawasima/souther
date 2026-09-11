package souther.compiler.partition;

import org.junit.jupiter.api.Test;

import souther.compiler.check.DeclaredSig;
import souther.compiler.check.RuleReadingContext;
import souther.compiler.check.RuleReadingSource;
import souther.compiler.check.RuleReadings;
import souther.compiler.core.Core;
import souther.compiler.inputs.InputDomain;
import souther.compiler.query.Adequacy;
import souther.compiler.query.Bodies;
import souther.compiler.query.Compilation;
import souther.compiler.query.ReadAs;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A name a {@code match} arm binds is the scrutinee's position, narrowed.
 *
 * <p>Nothing followed an arm's binder before, so a rule written inside an arm named no position at
 * all: the axes had none under a case and the walk had no way to reach one. Both halves are one
 * change — a line drawn at a position the reading does not hold is an axis no row ever covers, and
 * a position nothing draws on is one the model looks silent about.
 */
class AnArmsBinderNamesTheNarrowedPositionTest {

    private static final String MODEL = """
            module example.arm

            data Limit = Int
                invariant value >= 1

            data GlobalQuery = { limit: Limit }
            data FeedQuery = { limit: Limit }
            data ArticleQuery = GlobalQuery | FeedQuery
            data Page = { n: Int }

            behavior read : (query: ArticleQuery) -> Page
                constructs Page

            let read (query) =
                match query with
                    | GlobalQuery as g -> {
                        guard g.limit.value > 10 else Page { n = 0 }
                        Page { n = 1 }
                      }
                    | FeedQuery as f -> Page { n = 2 }
            """;

    private static List<Axis> axes() {
        Compilation compilation = Compilation.ofSource(MODEL, "Main");
        compilation.answerEverything();
        String module = compilation.modules().get(0);
        RuleReadingSource rules = RuleReadings.of(compilation, module);
        Map<String, DeclaredSig> sigs =
                compilation.db().ask(new Bodies.DeclaredSignatures(module)).value();
        Bodies.Elaborated checked = compilation.db().ask(new Bodies.Checked(module)).value();
        assertNotNull(checked, "the model under test compiles");
        Core body = checked.behaviorBodies().get("read");
        assertNotNull(body, "the behavior under test has a body");
        InputDomain inputs = compilation.db().ask(new Adequacy.Inputs(module)).value().get("read");
        GuardThresholds.Guards guards = GuardThresholds.of("read",
                checked.analysisBodies().get("read"), body,
                checked.plan(),
                inputs, rules);
        InputDomain read = InputDomain.of(sigs.get("read"), rules,
                ReadAs.THE_COMPILATION_DOES);
        Partitions.Partitioning base = Partitions.of("read", read, rules,
                ReadAs.THE_COMPILATION_DOES);
        return Partitions.withThresholds(base, read.quantities(rules), guards.thresholds(),
                RuleReadingContext.unshared(rules, ReadAs.THE_COMPILATION_DOES),
                guards.noLine(), guards.singled(), guards.between(),
                souther.compiler.values.Allowance.of(souther.compiler.regex.PatternPlan.Budget.OF_BEHAVIOR_DISTINCTIONS)).axes();
    }

    /** The comparison inside the arm draws its line on the position under the case. */
    @Test
    void aGuardInsideAnArmDividesThePositionUnderThatCase() {
        Axis limit = axes().stream()
                .filter(each -> each.path().toString().equals("query@GlobalQuery.limit"))
                .findFirst().orElseThrow(() -> new AssertionError("no axis under the case; there are "
                        + axes().stream().map(each -> each.path().toString()).toList()));

        assertTrue(limit.derivable(), "the guard divides it: " + limit.classes());
        // The bound the case's own field type places, and the line the arm's guard draws. Both are
        // about the same position and both are lines a row is owed at; the second is the one
        // nothing could reach before, because the name the arm binds named no position.
        assertEquals(List.of("1", "10"),
                limit.cuts().stream().map(each -> each.at().toString()).toList());
    }

    /** And the same position under the other case is left where it was. */
    @Test
    void thePositionUnderTheOtherCaseIsNotDivided() {
        Axis limit = axes().stream()
                .filter(each -> each.path().toString().equals("query@FeedQuery.limit"))
                .findFirst().orElseThrow();

        assertEquals(List.of(), limit.classes(),
                "a comparison written under one case says nothing about the other");
    }
}
