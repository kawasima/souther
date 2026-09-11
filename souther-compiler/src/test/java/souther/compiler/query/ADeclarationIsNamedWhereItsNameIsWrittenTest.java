package souther.compiler.query;

import souther.compiler.cst.SourceLayout;
import souther.compiler.source.SourceId;

import org.junit.jupiter.api.Test;
import souther.compiler.diag.SourcePos;
import souther.compiler.meta.ModulePath;
import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbols;
import souther.compiler.types.TypeSymbol;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * A cursor on a declaration's own name is on that declaration. The question was answered by
 * comparing the cursor against where the declaration starts — its {@code data} keyword — which is
 * not where its name is written, so the answer was there for a cursor on the keyword and absent for
 * one on the name.
 *
 * <p>What that cost is not visible from here: an editor asking this and getting nothing falls back
 * to matching by spelling, which answers, and answers about whatever else in the workspace is
 * spelled the same.
 */
class ADeclarationIsNamedWhereItsNameIsWrittenTest {

    private static final String ID = "m.sou";

    /** `data` starts at column 1 and `D` is written at column 6. */
    private static final String SOURCE = """
            module m

            data D = { v: Int }
            behavior f : (d: D) -> D
            let f (d) = d
            """;

    private static TypeSymbol under(int line, int column) {
        Map<String, String> byId = new LinkedHashMap<>();
        byId.put(ID, SOURCE);
        return Compilation.ofDocuments(byId, Set.of(), ModulePath.EMPTY).db()
                .ask(new Names.TypeAt(caret(SOURCE, line, column))).value();
    }

    @Test
    void aCursorOnTheNameIsOnTheDeclaration() {
        assertEquals(TypeSymbols.declared(new TypeKey("m", "D")), under(3, 6));
    }

    @Test
    void aCursorJustPastTheNameIsStillOnIt() {
        assertEquals(TypeSymbols.declared(new TypeKey("m", "D")), under(3, 7));
    }

    @Test
    void aCursorOnTheKeywordIsNotOnTheName() {
        assertNull(under(3, 1), "`data` is not what the declaration is called");
    }

    @Test
    void aCursorOnAUseIsStillOnWhatTheUseDenotes() {
        assertEquals(TypeSymbols.declared(new TypeKey("m", "D")), under(4, 18));
    }

    /**
     * The place an editor's caret is at line {@code line} column {@code column} of {@code source}.
     *
     * <p>A caret is a line and a column — it is where a reader put it — and which of the things
     * written in that file that lands on is the file's to say. Spelled out of the two numbers, it
     * is a place made up rather than read, and what is under it is nothing.
     */
    private static SourcePos caret(String source, int line, int column) {
        SourceLayout laidOut = SourceLayout.of(source, new SourceId(ID));
        return laidOut.placeAt(laidOut.lines().offsetOf(line - 1, column - 1));
    }
}
