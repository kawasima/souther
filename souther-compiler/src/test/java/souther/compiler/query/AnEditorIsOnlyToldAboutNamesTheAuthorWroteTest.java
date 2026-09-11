package souther.compiler.query;

import souther.compiler.cst.SourceLayout;
import souther.compiler.source.SourceId;

import org.junit.jupiter.api.Test;
import souther.compiler.diag.SourcePos;
import souther.compiler.meta.ModulePath;
import souther.compiler.types.ValueName;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The resolve pass gives a binding to more than the names an author wrote. A desugaring binds what
 * it is rewriting to a name of its own — the parameter {@code .field} becomes, the value a
 * {@code match} is held in — and anchors it on the form it came from, which is a place in the source
 * holding something the author did write.
 *
 * <p>Answering a cursor there with one of those would name a binding that is not in the file, at a
 * width that is not its. Renaming from it would then write over whatever is at that place: the
 * getter {@code .v} is three characters wide as `$g0`, and the three characters at its position are
 * `.v,`.
 *
 * <p>The other half is a binding whose name the author did write but whose uses this pass does not
 * record. A field is one: {@code d.v} and {@code D { v = ... }} are resolved by the type of what
 * they are read from, not by what is in scope, and only the ones inside an {@code invariant} come
 * through here. A rename answered from those would rewrite the declaration and leave every read.
 */
class AnEditorIsOnlyToldAboutNamesTheAuthorWroteTest {

    private static final String ID = "a.sou";

    /**
     * Line 3 declares the field `v` at column 12, line 4 reads it in the invariant at column 15,
     * and line 7 writes the getter `.v` at column 36. Line 10 reads `d.v` at column 27.
     */
    private static final String DESUGARED = """
            module a exposing ( D, total )

            data D = { v: Int }
                invariant v >= 0

            behavior total : (ds: List<D>) -> Int
            let total (ds) = List.sum(List.map(.v, ds))

            behavior copy : (d: D) -> D
            let copy (d) = D { v = d.v }
            """;

    /**
     * Line 10 binds the lambda parameters `acc` at column 29 and `d` at column 34; the lambda itself
     * opens at column 28. Lines 14 and 15 bind `w` at column 16 and `z` at column 15.
     */
    private static final String WRITTEN = """
            module a exposing ( D, Shape, total, tag )

            data D = { v: Int }

            data Round = { r: Int }
            data Flat = { f: Int }
            data Shape = Round | Flat

            behavior total : (ds: List<D>) -> Int
            let total (ds) = List.fold((acc, d) -> acc + d.v, 0, ds)

            behavior tag : (s: Shape) -> Int
            let tag (s) = match s with
                | Round as w -> w.r
                | Flat as z -> z.f
            """;

    private static ValueName under(String source, int line, int column) {
        Map<String, String> byId = new LinkedHashMap<>();
        byId.put(ID, source);
        return Compilation.ofDocuments(byId, Set.of(), ModulePath.EMPTY).db()
                .ask(new Names.ValueAt(caret(source, line, column))).value();
    }

    /** What the cursor is on across a whole span, as one string: `X` where something is, `.` where
     * nothing is. */
    private static String across(String source, int line, int from, int to) {
        StringBuilder out = new StringBuilder();
        for (int column = from; column <= to; column++) {
            out.append(under(source, line, column) == null ? '.' : 'X');
        }
        return out.toString();
    }

    // --- a name no one wrote ----------------------------------------------------------------------

    @Test
    void aGetterIsNotABindingAnyoneCanBeSentTo() {
        assertEquals("....", across(DESUGARED, 7, 36, 39),
                "`.v, ` is not a name; the parameter it desugars to is written nowhere");
    }

    @Test
    void theNamesAroundItAreStillAnswered() {
        assertNotNull(under(DESUGARED, 7, 40), "`ds`, the argument beside the getter");
        assertNotNull(under(DESUGARED, 7, 12), "`ds`, where it is bound");
    }

    // --- a name the author wrote whose uses are not names ------------------------------------------

    @Test
    void aFieldDeclarationIsNotOfferedAsALocal() {
        assertNull(under(DESUGARED, 3, 12),
                "renaming `v` here would leave every `d.v` and `D { v = ... }` behind");
    }

    @Test
    void norIsTheOneUseOfItThisPassDoesRecord() {
        assertNull(under(DESUGARED, 4, 15), "the invariant's `v` is the same field, and no more"
                + " renameable for being the one read that resolution sees");
    }

    // --- a name the author wrote, wherever the form it is written in starts ------------------------

    @Test
    void aLambdaParameterIsWhereItIsWrittenAndNotWhereTheLambdaStarts() {
        assertNull(under(WRITTEN, 10, 28), "the lambda opens here and binds no name here");
        assertEquals("acc", under(WRITTEN, 10, 29).name(), "which is where `acc` is written");
    }

    @Test
    void andSoIsTheSecondOne() {
        assertEquals("d", under(WRITTEN, 10, 34).name(),
                "a second parameter is its own name, not the first one's");
        assertEquals("d", under(WRITTEN, 10, 46).name(), "the `d` of `d.v` is that parameter");
    }

    @Test
    void aMatchArmBindsItsNameWhereItIsWritten() {
        assertEquals("w", under(WRITTEN, 14, 16).name(), "`| Round as w`");
        assertEquals("z", under(WRITTEN, 15, 15).name(), "`| Flat as z`");
        assertNull(under(WRITTEN, 14, 7), "the arm starts at the case name, which binds nothing");
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
