package souther.compiler;

import souther.compiler.diag.SourceLayouts;
import souther.compiler.diag.SourceRendering;
import org.junit.jupiter.api.Test;

import souther.compiler.observe.Incompleteness;
import souther.compiler.publish.PublishedIncompleteness;
import souther.compiler.observe.RowIdentity;
import souther.compiler.observe.RowRef;
import souther.compiler.query.Adequacy;
import souther.compiler.query.Compilation;
import souther.compiler.report.AdequacyReport;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Two rows that did not come back are two things to go and look at.
 *
 * <p>A reason a measure could not read everything is kept one per identity — a module's classes
 * failing to be instrumented is one fact however many sources went looking for it — and a row that
 * did not come back used to be identified by the behavior it is written for. So a behavior with two
 * such rows left one reason, and the report said "a row of `take` did not come back" once whichever
 * of them a reader was owed.
 *
 * <p>The other half is the source. A row written with no name is numbered within the source it is
 * written in, so a behavior exampled in its module and in an attached file has a first row in each;
 * carried as the ordinal alone, those two are one identity in a set (issue #996).
 */
class TwoRowsThatDidNotComeBackAreTwoReasonsTest {

    private static List<PublishedIncompleteness> reasonsOf(Compilation compilation, String module) {
        for (AdequacyReport.ModuleReport each : AdequacyReport.of(compilation).modules()) {
            if (each.module().equals(module)) {
                return each.incompleteness().written();
            }
        }
        throw new AssertionError("no module called " + module);
    }

    private static Compilation overrunning(List<String> sources, String target) {
        Compilation compilation = Compilation.ofSources(sources,
                souther.compiler.meta.ModulePath.EMPTY);
        compilation.withJvmExampleDeadlines(DoesNotComeBack.overrunningOn(
                DoesNotComeBack.everythingAboutRowsOf(target)));
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        return compilation;
    }

    /** Two rows of one behavior, in one source. */
    @Test
    void twoRowsOfOneBehaviorLeaveTwoReasons() {
        Compilation compilation = overrunning(List.of("""
                module example.pair

                data Draft = { n: Int }
                data Ok = { n: Int }

                behavior take : (request: Draft) -> Ok
                    constructs Ok

                let take (request) = Ok { n = request.n }

                example take
                    | (Draft { n = 1 }) -> Ok { n = 1 }
                    | (Draft { n = 2 }) -> Ok { n = 2 }
                """), "take");

        assertEquals(List.of("take/0/#1", "take/0/#2"),
                reasonsOf(compilation, "example.pair").stream()
                        .filter(gap -> gap.fact().scope() == Incompleteness.Scope.ROW)
                        .map(gap -> gap.fact().subject()).toList(),
                "each row is its own reason, and says which row it is");
    }

    /**
     * And one row apiece in two sources, both of them the first of their source.
     *
     * <p>The case the ordinal alone cannot tell apart. Both are {@code #1}; what makes them two is
     * the source, which is why a row's identity carries it.
     */
    @Test
    void aFirstRowInEachOfTwoSourcesAreTwoReasons() {
        Compilation compilation = overrunning(List.of("""
                module example.across

                data Draft = { n: Int }
                data Ok = { n: Int }

                behavior take : (request: Draft) -> Ok
                    constructs Ok

                let take (request) = Ok { n = request.n }

                example take
                    | (Draft { n = 1 }) -> Ok { n = 1 }
                """, """
                examples for example.across

                example take
                    | (Draft { n = 2 }) -> Ok { n = 2 }
                """), "take");

        assertEquals(List.of("take/0/#1", "take/1/#1"),
                reasonsOf(compilation, "example.across").stream()
                        .filter(gap -> gap.fact().scope() == Incompleteness.Scope.ROW)
                        .map(gap -> gap.fact().subject()).toList(),
                "the first row of each source is a row of its own");
    }

    /**
     * And a reader sees two of them.
     *
     * <p>The last step of the same thing. What tells the two apart is the source, and the form a
     * person is shown was the behavior and the row's own name — so the identity was widened, the
     * document carried both, and the lines under the module read alike. A test that asked the
     * identity would not have noticed: this asks what is shown.
     */
    @Test
    void andAReaderSeesTwoOfThem() {
        Compilation compilation = overrunning(List.of("""
                module example.shown

                data Draft = { n: Int }
                data Ok = { n: Int }

                behavior take : (request: Draft) -> Ok
                    constructs Ok

                let take (request) = Ok { n = request.n }

                example take
                    | (Draft { n = 1 }) -> Ok { n = 1 }
                """, """
                examples for example.shown

                example take
                    | (Draft { n = 2 }) -> Ok { n = 2 }
                """), "take");

        List<String> shown = reasonsOf(compilation, "example.shown").stream()
                .filter(gap -> gap.fact().scope() == Incompleteness.Scope.ROW)
                .map(gap -> gap.fact().shown(SourceRendering.namedByIdentity(SourceLayouts.NONE)))
                .toList();

        assertEquals(2, shown.size(), () -> "two rows did not come back: " + shown);
        assertEquals(shown.size(), Set.copyOf(shown).size(),
                () -> "and a reader is shown two things rather than one said twice: " + shown);
    }

    /**
     * Which is a property of a row's own form and not of this model.
     *
     * <p>Held on the values, because the shown form is what a reader has and two rows reading alike
     * is the whole of the loss. A named row carries a name no other row of its behavior has, so it
     * says the file it is in nowhere; an unnamed one is numbered within its source, so it does.
     */
    @Test
    void whatIsShownOfARowTellsItFromAnyOther() {
        souther.compiler.source.SourceId first = new souther.compiler.source.SourceId("0");
        souther.compiler.source.SourceId second = new souther.compiler.source.SourceId("1");
        List<RowRef> rows = List.of(
                new RowRef("take", first, new RowIdentity.Unnamed(1)),
                new RowRef("take", second, new RowIdentity.Unnamed(1)),
                new RowRef("take", first, new RowIdentity.Unnamed(2)),
                new RowRef("take", first, new RowIdentity.Named("holds")),
                new RowRef("cancel", first, new RowIdentity.Unnamed(1)));

        List<String> shown = rows.stream().map(row -> row.shown(row.source().value())).toList();

        assertEquals(rows.size(), Set.copyOf(shown).size(),
                () -> "two rows read as one: " + shown);
    }
}
