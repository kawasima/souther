package souther.compiler.partition;

import souther.compiler.check.AnalysisBody;
import souther.compiler.check.ElementBindings;
import souther.compiler.check.RuleReadingSource;
import souther.compiler.check.RuleReadings;
import souther.compiler.diag.Severity;
import souther.compiler.inputs.InputDomain;
import souther.compiler.inputs.InputReads;
import souther.compiler.query.Adequacy;
import souther.compiler.query.Bodies;
import souther.compiler.query.Compilation;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * What a test hands a reading of one body's decision.
 *
 * <p>Read the way whoever asks the account reads it, and held to the model having compiled first: a
 * module the compile stopped in answers every question with nothing, so a measurement taken off one
 * comes back saying the body decides nothing and reads the same as a body that does.
 */
final class DecisionReadings {

    private DecisionReadings() {}

    /** The decision {@code behavior} of {@code source} states. */
    static DecisionReading of(String source, String behavior) {
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.answerEverything();
        assertEquals(List.of(), compilation.diagnostics().values().stream()
                        .flatMap(List::stream)
                        .filter(said -> said.diagnostic().severity() == Severity.ERROR)
                        .map(said -> said.diagnostic().code() + " " + said.diagnostic().titleKey())
                        .toList(),
                "a model that did not compile answers every question with nothing");
        String module = compilation.modules().get(0);
        Bodies.Elaborated checked = compilation.db().ask(new Bodies.Checked(module)).value();
        AnalysisBody analysis = checked.analysisBodies().get(behavior);
        assertNotNull(analysis, "no body of `" + behavior + "` was elaborated");
        RuleReadingSource rules = RuleReadings.of(compilation, module);
        InputDomain inputs =
                compilation.db().ask(new Adequacy.Inputs(module)).value().get(behavior);
        return DecisionReading.of(behavior, analysis.core(), inputs.reading(rules),
                InputReads.ofParametersWhereCallsStand(inputs.parameterReads(),
                        ElementBindings.of(analysis.core(), analysis.elements(), rules.symbols())));
    }

    /** The rules of that decision, with the reading held to having been made to the end. */
    static List<DecisionRule> readToTheEnd(String source, String behavior) {
        DecisionReading read = of(source, behavior);
        assertEquals(new DecisionReading.Enumeration.Complete(), read.enumeration(),
                "a body this small has its ways held apart");
        assertEquals(List.of(), read.found().stream().filter(ruled -> !ruled.whole()).toList(),
                "and every one of them is written down whole");
        return read.rules();
    }
}
