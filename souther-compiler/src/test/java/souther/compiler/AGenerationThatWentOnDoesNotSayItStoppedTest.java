package souther.compiler;

import org.junit.jupiter.api.Test;

import souther.compiler.check.RuleReadingSource;
import souther.compiler.check.RuleReadings;
import souther.compiler.check.DeclaredSig;
import souther.compiler.diag.SourceNameResolver;
import souther.compiler.inputs.InputDomain;
import souther.compiler.observe.Classification;
import souther.compiler.observe.Incompleteness;
import souther.compiler.partition.Axis;
import souther.compiler.partition.AxisId;
import souther.compiler.partition.Budgets;
import souther.compiler.partition.GenerationReason;
import souther.compiler.partition.Generator;
import souther.compiler.partition.MeasuredInput;
import souther.compiler.partition.Partitions;
import souther.compiler.query.Adequacy;
import souther.compiler.query.Bodies;
import souther.compiler.query.Compilation;
import souther.compiler.report.GeneratedRows;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the block says happened is what happened.
 *
 * <p>The note under a generated block was written from the presence of a reason: any reason at all
 * and it said the generation had stopped. A position left out is a reason and is not a stop — the
 * generator drops that position and fills the rest — so a block came out offering rows with a line
 * under them saying it had offered none. An author reading it is told both that there is work here
 * and that there is none.
 *
 * <p>Which of the two happened is the generator's to say and not the renderer's to infer, so it is
 * held in the value. These hold the two apart from the outside: one run that went on, one that
 * stopped, and the words that come out of each.
 */
class AGenerationThatWentOnDoesNotSayItStoppedTest {

    private static final String TRIP = """
            module example.trip

            data Domestic
            data Overseas
            data Kind = Domestic | Overseas

            data Request = { kind: Kind, urgent: Bool }

            data Accepted = { at: String }

            behavior submit : (request: Request) -> Accepted
                constructs Accepted

            let submit (request) = Accepted { at = "now" }
            """;

    /** The model these read, compiled once. Held rather than compiled per question, because the
     *  block below asks it where the rules it names are written. */
    private static final Compilation COMPILED = compiled();

    private static Compilation compiled() {
        Compilation compilation = Compilation.ofSource(TRIP, "Main");
        compilation.answerEverything();
        return compilation;
    }

    private static MeasuredInput subject() {
        Compilation compilation = COMPILED;
        String module = compilation.modules().get(0);
        Map<String, DeclaredSig> sigs =
                compilation.db().ask(new Bodies.DeclaredSignatures(module)).value();
        RuleReadingSource rules = RuleReadings.of(compilation, module);
        InputDomain domain = InputDomain.of(sigs.get("submit"), rules,
                souther.compiler.query.ReadAs.THE_COMPILATION_DOES);
        return MeasuredInput.of("submit", domain.reading(rules),
                Partitions.of("submit", domain, rules,
                        souther.compiler.query.ReadAs.THE_COMPILATION_DOES));
    }

    private static String written(souther.compiler.partition.FillResult result) {
        // The composition and not what is offered: this filling is built here rather than searched
        // for, so there is no store to ask what its rows would settle.
        return GeneratedRows.of(souther.compiler.query.EveryRowOfIt.offered(
                        souther.compiler.query.Composition.composed(
                        souther.compiler.query.OfferingRequest.overTheModule("example.trip", false),
                        Map.of("submit", new Adequacy.Filling(result,
                                Generator.GenerationResult.NONE,
                                Adequacy.Generated.RowsForRules.NOTHING, List.of())), null)),
                Map.of(), SourceNameResolver.identity(), COMPILED.db()).text();
    }

    /** A run asked for nothing, which is what a reason about the run alone is written against. */
    private static souther.compiler.partition.FillResult stoppedWith(GenerationReason why) {
        souther.compiler.partition.GenerationPlan plan =
                new souther.compiler.partition.GenerationPlan(subject(), List.of(), List.of());
        return new souther.compiler.partition.FillResult(plan, new LinkedHashMap<>(), List.of(),
                List.of(why),
                souther.compiler.partition.Discharge.NOTHING);
    }

    /**
     * One position could not be read and the others could, so the rest was filled.
     *
     * <p>The recorded reason is a truncation and what comes back is a withheld position, which is the
     * generator's own answer and not the classification's. What it decided is that no work is offered
     * there, and it decided that for either kind of unreadable value.
     */
    @Test
    void aPositionLeftOutIsNotAGenerationThatStopped() {
        MeasuredInput subject = subject();
        Axis first = subject.axes().get(0);
        Axis second = subject.axes().get(1);
        Map<AxisId, Classification> row = new LinkedHashMap<>();
        row.put(first.id(), new Classification.Unclassified(Incompleteness.atPosition(
                Incompleteness.Code.VALUE_TRUNCATED, first.id().behavior(), first.id().term())));
        row.put(second.id(), Classification.in(second.classes().get(0).id()));

        souther.compiler.partition.FillResult filled =
                Generator.fill(subject, List.of(Generator.ObservedRow.unseen(row)),
                        Generator.CandidateCheck.ANY, Budgets.generation());

        assertFalse(filled.rows().isEmpty(), "the positions it could read were filled");
        assertInstanceOf(GenerationReason.PositionWithheld.class, filled.reasons().get(0));

        String written = written(filled);
        assertTrue(written.contains("example submit"), written);
        assertTrue(written.contains("no rows offered at `" + first.id() + "`"), written);
        assertFalse(written.contains("generation stopped"),
                "it offered rows two lines above: " + written);
    }

    /** And a run that did stop says so, in words a reader can act on. */
    @Test
    void aSearchThatRanOutSaysItStopped() {
        String written = written(stoppedWith(new GenerationReason.SearchLimit("submit", 12)));

        assertEquals("// generation stopped for `submit`: 12 classes and arms past the row limit"
                + System.lineSeparator(), written);
    }

    /** One left is one thing on the plan, which is a class or an arm and never a combination. */
    @Test
    void theCountIsWrittenAsACount() {
        String written = written(stoppedWith(new GenerationReason.SearchLimit("submit", 1)));

        assertTrue(written.contains("1 class or arm past"), written);
    }
}
