package souther.bench;

import org.junit.jupiter.api.Test;

import souther.compiler.diag.Diagnostic;
import souther.compiler.diag.Located;
import souther.compiler.diag.Severity;
import souther.compiler.meta.ModulePath;
import souther.compiler.query.Compilation;

import java.util.ArrayList;
import java.util.List;

/**
 * That the shapes these measurements are taken against still compile.
 *
 * <p>{@link CorpusTest} makes this claim about the sources carried in this module. The generated
 * ones had nobody making it, and they are the arrangement most of the reasoning here rests on: a
 * language change that leaves one of them behind leaves a measurement being taken of a compile that
 * stops early, which is faster than one that finishes and reads as an improvement. Nothing about a
 * timing says which of the two it timed.
 *
 * <p>At small sizes. What is asked here is whether the shape is admissible, and a shape that is
 * admissible at ten modules is admissible at two hundred — the sizes are what the measurement varies
 * and are no part of this. Held small on purpose: a test that took as long as the benchmark would be
 * a benchmark, and would stop being run.
 */
class EveryShapeThatIsTimedStillCompilesTest {

    /** Enough of each shape to be that shape: a chain has a middle, and the widest module imports
     *  its full width rather than however many happen to be written before it. */
    private static final int MODULES = 2 * Scale.WIDTHS[Scale.WIDTHS.length - 1];

    /** Enough values that a fan-out fans and a chain has links that are not its ends. */
    private static final int VALUES = 8;

    @Test
    void everyWorkspaceShapeCompiles() {
        compiles("independent", Scale.independent(MODULES));
        compiles("chain", Scale.chain(MODULES));
        for (int width : Scale.WIDTHS) {
            compiles("imports=" + width, Scale.imports(MODULES, width));
        }
    }

    /**
     * And every shape the choice measurement times comes to what its point says it comes to.
     *
     * <p>At the sizes it is timed at, unlike the shapes above: what a choice shape comes to is what
     * the sizes vary — one side of the guardrail holds its alternatives apart and the other merges
     * them — so a smaller one is a different shape rather than the same shape smaller.
     *
     * <p>Both answers, because one of them is a shape written to be refused. A fate a reading only
     * reaches where a declaration admits nothing is a fate that comes with a refusal, and a claim
     * that every shape compiles would be a claim this measurement could only meet by not reaching
     * the fate. What is held is that each shape comes to the one its line was written for, so a
     * shape that started being refused is caught either way round.
     */
    @Test
    void everyChoiceShapeComesToWhatItsPointSays() {
        for (Choices.Point point : Choices.points()) {
            String where = point.series() + " " + point.label();
            switch (point.completion()) {
                case MAKES_CLASSES -> compiles(where, List.of(point.source()));
                case IS_REFUSED -> refused(where, List.of(point.source()));
            }
        }
    }

    /** That a shape written to be refused still is, and is refused rather than failing to reach the
     *  reading its line is about. */
    private static void refused(String shape, List<String> sources) {
        Compilation compilation = Compilation.ofSources(sources, ModulePath.EMPTY);
        boolean said = Located.diagnosticsOf(compilation.diagnostics()).values().stream()
                .flatMap(List::stream)
                .anyMatch(diagnostic -> diagnostic.severity() == Severity.ERROR);
        if (!said) {
            throw new AssertionError(shape + " is written to be refused and was not, so its line is"
                    + " a compile of a different kind from the one it says it is");
        }
    }

    @Test
    void everyModuleShapeCompiles() {
        compiles("chain", List.of(Values.chain(VALUES, false)));
        compiles("chain bottom-up", List.of(Values.chain(VALUES, true)));
        compiles("chain via helpers", List.of(Values.throughHelpers(VALUES, false)));
        compiles("same, bottom-up", List.of(Values.throughHelpers(VALUES, true)));
        compiles("fan-out", List.of(Values.fanOut(VALUES)));
        compiles("flat", List.of(Values.flat(VALUES)));
    }

    /**
     * That each width is that width, and that widths are all that separate them.
     *
     * <p>Both halves, because either alone passes on a shape that measures nothing. A loop that
     * produced one import at every width would still compile, still be timed, and still be reported
     * under a number saying it was eight. And a shape whose bodies grew with its imports would
     * differ by width in what it declares as well as in what it imports, so a difference between two
     * lines would be about both and attributable to neither — which is what the first attempt at
     * this did, importing a name and using it in a construction, a call, a field read and an
     * addition.
     *
     * <p>The floor is among the widths asked, and is the one it matters most to ask of: every other
     * line is read as what it has above that one, so a body that differed only there would move
     * every reading and nothing would say it had.
     */
    @Test
    void aWidthIsItsImportsAndNothingElse() {
        String previous = null;
        for (int width : Scale.WIDTHS) {
            String last = Scale.imports(MODULES, width).getLast();
            long imports = last.lines().filter(line -> line.startsWith("import ")).count();
            if (imports != width) {
                throw new AssertionError("a module at width " + width + " imports " + imports
                        + " module(s):\n" + last);
            }
            String body = last.lines().filter(line -> !line.startsWith("import "))
                    .collect(java.util.stream.Collectors.joining("\n"));
            if (previous != null && !previous.equals(body)) {
                throw new AssertionError("what a module declares changes with its width, so a"
                        + " difference between two widths is not about the imports:\n" + previous
                        + "\n--- against ---\n" + body);
            }
            previous = body;
        }
    }

    /**
     * That the number of imports reported beside a time is the number there are.
     *
     * <p>Counted rather than multiplied out, and this says why it has to be: the first modules
     * import what is written before them and there is less of it, so {@code modules * width} is over
     * by a widening amount. A run reporting that number would say a workspace held more links than
     * it does, and anyone reading a cost out of a time and a count would read it against the wrong
     * one.
     */
    @Test
    void theLinksReportedAreTheLinksThereAre() {
        for (int width : Scale.WIDTHS) {
            long written = Scale.imports(MODULES, width).stream()
                    .flatMap(String::lines)
                    .filter(line -> line.startsWith("import "))
                    .count();
            if (written != Scale.links(MODULES, width)) {
                throw new AssertionError("at width " + width + " the shape writes " + written
                        + " import(s) and reports " + Scale.links(MODULES, width));
            }
        }
    }

    private static void compiles(String shape, List<String> sources) {
        Compilation compilation = Compilation.ofSources(sources, ModulePath.EMPTY);
        List<String> errors = new ArrayList<>();
        for (List<Diagnostic> found : Located.diagnosticsOf(compilation.diagnostics()).values()) {
            for (Diagnostic diagnostic : found) {
                if (diagnostic.severity() == Severity.ERROR) {
                    errors.add(diagnostic.code() + " at " + diagnostic.primary());
                }
            }
        }
        if (!errors.isEmpty()) {
            throw new AssertionError("the `" + shape + "` shape no longer compiles:\n  "
                    + String.join("\n  ", errors));
        }
        // Asked after the errors, and asked at all: a shape that compiles to nothing is one the
        // measurement walks past, and the timing would be of a compile that reached no back end.
        if (compilation.classes().isEmpty()) {
            throw new AssertionError("the `" + shape + "` shape generated no classes");
        }
    }
}
