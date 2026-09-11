package souther.compiler;

import souther.compiler.diag.SourcePos;
import souther.compiler.cst.SourceLayout;
import souther.compiler.diag.ReportContext;

import souther.compiler.diag.Primary;

import souther.compiler.source.SourceId;
import souther.compiler.diag.QuotedFrom;

import souther.compiler.diag.CompileException;
import souther.compiler.diag.HumanRenderer;
import souther.compiler.diag.Located;
import souther.compiler.diag.SourceContext;
import souther.compiler.diag.SourceContextResolver;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A module's rows, fake tables and values may be written beside it in an {@code examples for} file,
 * and once they are gathered under the module's name a line and a column no longer say which file
 * they came from. What was quoted then was the model file's line at that number — a line the author
 * did not write, and, the two files being different lengths, usually a line about something else
 * entirely (issue #309).
 *
 * <p>The rule these pin down: a report is said on the file the writing it is about was written in.
 * So each of these reads the file back as well as the line — the id alone passed for the wrong
 * reason in half the cases before the fix, since the two files' line numbers happened to agree.
 *
 * <p>The model is long and the attached file is short on purpose. A misattribution then lands on a
 * line about something else, or past the end of the file, rather than on a coincidence.
 */
class AMistakeInAnAttachedFileIsSaidOnThatFileTest {

    /** Long enough that a coordinate from the short file beside it lands on an unrelated line. */
    private static final String MODEL = """
            module shippingfee

            data 都道府県 = { 名前: String }
            data 数量     = { 個数: Int }
            data 送料     = { 円: Int }

            behavior 送料を求める : (県: 都道府県, 数: 数量) -> 送料
                constructs 送料

            let 送料を求める (県, 数) = 送料 { 円 = 数.個数 * 100 }

            data 一般 = { 名前: String }
            """;

    private static final String ATTACHED_PREFIX = "examples for shippingfee\n\n";

    private static Raised raisedBy(String attached) {
        String whole = ATTACHED_PREFIX + attached;
        return new Raised(assertThrows(CompileException.class,
                () -> Compiler.compileModules(List.of(MODEL, whole))), whole);
    }

    /**
     * What a compile refused, and the attached file it was handed.
     *
     * <p>The two together, because where a report points is a place in one of two files and the
     * line it is at is what that file is laid out as — which is the whole of what this test is
     * about. Read against the other file, the numbers came out plausible and about nothing.
     */
    private record Raised(CompileException said, String attached) {

        /** Where the compile says the problem is, as {@code sourceId line:column}. */
        String saidAt() {
            SourcePos at = ((Primary.InSource) said.diagnostic().primary()).place().region().start();
            String text = new SourceId("1").equals(said.sourceId()) ? attached : MODEL;
            return said.sourceId() + " " + WhereItSits.in(text, at);
        }

        SourceId sourceId() {
            return said.sourceId();
        }

        souther.compiler.diag.Diagnostic diagnostic() {
            return said.diagnostic();
        }
    }

    // --- one method per way of getting it wrong -------------------------------------------------

    @Test
    void anUnknownValueNamedByARowIsSaidInTheFileTheRowIsWrittenIn() {
        Raised e = raisedBy("""
                example 送料を求める
                    | "北海道" : (北海道沖縄, 数量 { 個数 = 1 }) -> 送料 { 円 = 100 }
                """);

        assertEquals("1 4:16", e.saidAt(), "the row names it, and the row is in the attached file");
    }

    @Test
    void anUnknownValueInAnAttachedFilesFixtureIsSaidInThatFile() {
        Raised e = raisedBy("""
                let 県 = 都道府県 { 名前 = 北海道沖縄 }

                example 送料を求める
                    | "一つ" : (県, 数量 { 個数 = 1 }) -> 送料 { 円 = 100 }
                """);

        assertEquals("1 3:21", e.saidAt(), "the value is declared in the attached file");
    }

    @Test
    void anUnknownTypeInAnAttachedFilesFixtureIsSaidInThatFile() {
        Raised e = raisedBy("""
                let 県 = 北海道沖縄 { 名前 = "北海道" }

                example 送料を求める
                    | "一つ" : (県, 数量 { 個数 = 1 }) -> 送料 { 円 = 100 }
                """);

        assertEquals("1 3:9", e.saidAt(), "the type is written in the attached file");
    }

    @Test
    void aTypeErrorInAnAttachedFilesFixtureIsSaidInThatFile() {
        String source = """
                let 数 = 数量 { 個数 = "いくつか" }

                example 送料を求める
                    | "一つ" : (都道府県 { 名前 = "北海道" }, 数) -> 送料 { 円 = 100 }
                """;
        Raised e = raisedBy(source);

        assertEquals(new SourceId("1"), e.sourceId(), "the field is given its value in the attached file");
        assertEquals(3, WhereItSits.in(ATTACHED_PREFIX + source, ((Primary.InSource) e.diagnostic().primary()).place().region()).start().line());
    }

    /**
     * A syntax error never reaches the builder, so its position is made where the parser's first
     * error is read rather than where the module is built. That is the one position of a known source
     * that would otherwise say nothing about its file, which would leave a syntax error the single
     * kind of mistake still reported against whatever file the reader guessed at.
     */
    @Test
    void aSyntaxErrorInAnAttachedFileIsSaidInThatFile() {
        Raised e = raisedBy("""
                example 送料を求める
                    | "壊れている" : (
                """);

        assertEquals(new SourceId("1"), e.sourceId());
        assertEquals(new QuotedFrom.ASourceThisCompileHolds(new SourceId("1")),
                ((Primary.InSource) e.diagnostic().primary()).place().region().start().quotedFrom(),
                "the position itself says which file, which is what the id is read off");
    }

    /**
     * What the author actually sees. The id and the line agreeing is the mechanism; the line under
     * the caret being the line they wrote is the point, and it is the only assertion here that would
     * have caught the bug on its own.
     */
    @Test
    void theQuotedLineIsTheLineTheAuthorWrote() {
        String attached = ATTACHED_PREFIX + """
                example 送料を求める
                    | "北海道" : (北海道沖縄, 数量 { 個数 = 1 }) -> 送料 { 円 = 100 }
                """;
        CompileException e = assertThrows(CompileException.class,
                () -> Compiler.compileModules(List.of(MODEL, attached)));
        SourceContextResolver sources = id -> switch (id.value()) {
            case "0" -> new SourceContext("shippingfee.sou", MODEL, SourceLayout.of(MODEL));
            case "1" -> new SourceContext("shippingfee.examples.sou", attached, SourceLayout.of(attached));
            default -> null;
        };

        String out = new HumanRenderer(false).render(
                new Located(e.diagnostic(), ReportContext.inFile(e.sourceId())), sources, Locale.ENGLISH);

        assertTrue(out.contains("shippingfee.examples.sou:4:"), out);
        assertTrue(out.contains("北海道沖縄"), "the line quoted is the row that names it: " + out);
        assertFalse(out.contains("data 一般"),
                "the model's line 4 is not what this is about: " + out);
    }

    /** So the fix is not "always blame the attached file". */
    @Test
    void aMistakeInTheModelFileIsStillSaidThere() {
        CompileException e = assertThrows(CompileException.class,
                () -> Compiler.compileModules(List.of(MODEL + """

                        let 壊れた = 都道府県 { 名前 = 見つからない }
                        """, ATTACHED_PREFIX + """
                        example 送料を求める
                            | "一つ" : (都道府県 { 名前 = "北海道" }, 数量 { 個数 = 1 }) -> 送料 { 円 = 100 }
                        """)));

        assertEquals(new SourceId("0"), e.sourceId(), "it is written in the model file, attached file or not");
    }

    /** One problem is one marker however many questions found it — a helper is checked on its own
     *  and again wherever it is expanded, and both are looking at one line of one file. */
    @Test
    void oneProblemIsOneMarkerHoweverManyQuestionsFoundIt() {
        Map<String, String> byId = new LinkedHashMap<>();
        byId.put("shippingfee.sou", MODEL);
        byId.put("shippingfee.examples.sou", ATTACHED_PREFIX + """
                example 送料を求める
                    | "北海道" : (北海道沖縄, 数量 { 個数 = 1 }) -> 送料 { 円 = 100 }
                """);

        Map<SourceId, List<Located>> found = Compiler.diagnoseModules(byId);

        assertEquals(List.of(), found.get(new SourceId("shippingfee.sou")),
                "the model file is clean: " + found);
        assertEquals(1, found.get(new SourceId("shippingfee.examples.sou")).size(),
                "one problem, one marker: " + found);
    }
}
