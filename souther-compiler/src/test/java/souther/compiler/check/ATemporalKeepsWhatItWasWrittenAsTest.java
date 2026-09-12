package souther.compiler.check;

import souther.compiler.DefaultStdlib;
import souther.compiler.ast.Hir;
import souther.compiler.core.Core;
import souther.compiler.diag.SourcePos;
import souther.compiler.types.ApplicationOrigin;
import souther.compiler.types.ReachName;
import souther.compiler.types.SourceConstruct;
import souther.compiler.types.SourceConstructOrigin;
import souther.compiler.types.Type;
import souther.compiler.types.ValueName;
import souther.compiler.types.WrittenOwner;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * A temporal keeps which construction it was written as, across the fold that makes it a value.
 *
 * <p>{@code Date("2026-01-01")} is written as a construction and reaches Core as the value it
 * denotes, so that nothing below has to decide which calls are temporals. What is folded away is the
 * application; which application it was is not, and a reader writing the construction back out for a
 * report needs it. The place cannot answer instead — one helper is expanded at each of its calls, so
 * every copy of the construction sits at one place.
 *
 * <p>Asked here because the fold is where a root goes missing quietly. Nothing downstream refuses a
 * temporal that lost one: it would carry a construction nobody wrote, and the report built from it
 * would read as being about a call this compiler composed.
 */
class ATemporalKeepsWhatItWasWrittenAsTest {

    private static final SourcePos POS = new SourcePos(1, 1);

    private static Core.Temporal folded(ApplicationOrigin written) {
        ValueName.Stdlib.Namespace date = ValueName.Stdlib.namespace("Date");
        Hir.Expr call = Hir.Apply.synthetic("Date", new ReachName.TheNamespace(date), null, written,
                List.of(new Hir.StringLit("2026-01-01", POS, null)), POS, null);

        return assertInstanceOf(Core.Temporal.class,
                Elaborator.elaborate(call, Scope.NONE,
                        CheckContext.of(Symbols.none(DefaultStdlib.get()),
                                PublishedDeclarations.NONE, DeclarationKinds.NONE)));
    }

    /** The construction the author wrote arrives on the value it was folded into. */
    @Test
    void aTemporalTheAuthorWroteKeepsTheirConstruction() {
        ApplicationOrigin wrote = new ApplicationOrigin.Written(SourceConstructOrigin.written(
                new WrittenOwner.Body("demo", "b"), 3, SourceConstruct.CALL));

        Core.Temporal value = folded(wrote);

        assertEquals(Type.DATE, value.kind());
        assertEquals(wrote, value.application(),
                "the application is folded away; which one it was is not");
    }

    /**
     * And one composed for a fixture keeps that, rather than being read as something an author
     * wrote. The two are told apart by what arrives here, so a report about the second cannot come
     * to quote a construction of the first.
     */
    @Test
    void aTemporalComposedForAFixtureKeepsThat() {
        ApplicationOrigin composed = new ApplicationOrigin.ComposedFixture();

        assertEquals(composed, folded(composed).application());
    }
}
