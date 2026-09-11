package souther.compiler.diag;

import souther.compiler.cst.SourceLayout;
import org.junit.jupiter.api.Test;

import souther.compiler.diag.msg.DataMessage;

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Where the human renderer draws, held against the line it is drawing under.
 *
 * <p>A region says where it is in the text and the renderer has to say where that is on the screen.
 * The two agree on ASCII, so a case built out of ASCII cannot tell whether the renderer converts
 * between them at all — every case here carries something the two counts disagree about, and the
 * ASCII case is kept beside them to show the conversion moved nothing that was already right.
 *
 * <p>The expected columns are written out rather than measured, because measuring them with the
 * same table the renderer measures with would agree with a wrong table.
 */
class ACaretIsDrawnInColumnsNotCharactersTest {

    /**
     * Line 2 is the shape a model written in Japanese has: the region begins at UTF-16 unit 30 and
     * at display column 45. Line 3 holds a tab, whose own width is decided by the column it starts
     * at — and the gutter is part of that column, since the quoted line and the carets are written
     * on one terminal line.
     */
    private static final SourceContext SRC = new SourceContext("demo.sou",
            "module demo\n"
            + "    免責金額以下 { 請求額 = 診療内容.請求額, 免責金額 = 契約.自己負担割合 }\n"
            + "a\tb\n"
            + "let f (n) = null\n", SourceLayout.of("module demo\n"
            + "    免責金額以下 { 請求額 = 診療内容.請求額, 免責金額 = 契約.自己負担割合 }\n"
            + "a\tb\n"
            + "let f (n) = null\n"));

    private static String caretUnder(int line, int column, int width) {
        Diagnostic d = Diagnostic.at(new SourcePos(line, column), width)
                .say(new DataMessage.SpreadFieldCollision("f", "A", "...B"))
                .build();
        return lines(new HumanRenderer(false).render(d, SRC, Locale.ENGLISH)).get(3);
    }

    private static List<String> lines(String rendered) {
        return List.of(rendered.split("\n", -1));
    }

    /**
     * An accounting of the columns that does not read the width table under test. The BMP ranges
     * below cover every character these cases use, which is all this has to be right about.
     */
    private static int columnsOf(String text) {
        int at = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            boolean wide = (c >= 0x1100 && c <= 0x115F) || (c >= 0x2E80 && c <= 0xA4CF)
                    || (c >= 0xAC00 && c <= 0xD7A3) || (c >= 0xF900 && c <= 0xFAFF)
                    || (c >= 0xFF00 && c <= 0xFF60) || (c >= 0xFFE0 && c <= 0xFFE6);
            at += wide ? 2 : 1;
        }
        return at;
    }

    /** The gutter is three columns, the 29 units before the region are 45, so the caret begins at
     *  48 — where counting units would have put it at 32, under a different subexpression. */
    @Test
    void a_caret_begins_at_the_column_the_region_begins_at() {
        assertEquals(" ".repeat(48) + "^".repeat(8), caretUnder(2, 30, 4));
    }

    /** Four units of {@code 免責金額} underline as eight carets. Placing the caret correctly and
     *  then drawing it four wide would still underline half of it. */
    @Test
    void a_caret_spans_the_columns_the_region_occupies() {
        assertEquals(8, caretUnder(2, 30, 4).chars().filter(c -> c == '^').count());
        assertEquals(2, caretUnder(2, 5, 1).chars().filter(c -> c == '^').count(),
                "one unit of 免 is still two columns");
    }

    /** A tab advances to the next stop from where it stands, so what it moves the token by is not
     *  a property of the tab. Here it stands at column 4 and carries the token to column 8. */
    @Test
    void a_tab_before_a_region_moves_the_caret_by_what_the_tab_advances() {
        assertEquals(" ".repeat(8) + "^", caretUnder(3, 3, 1));
    }

    /** The control. Nothing here is wide, so the conversion has to leave it exactly where counting
     *  units left it. */
    @Test
    void an_ascii_line_is_pointed_at_where_it_always_was() {
        assertEquals(" ".repeat(7) + "^", caretUnder(4, 5, 1));
    }

    /**
     * The bar is padded to a width, and a translated title is written in characters that are two
     * columns each. Asked in two languages so the property is the width and not one catalog entry.
     */
    @Test
    void the_title_bar_is_the_same_width_in_every_language() {
        Diagnostic d = Diagnostic.at(new SourcePos(2, 30), 4)
                .say(new DataMessage.SpreadFieldCollision("f", "A", "...B"))
                .build();
        String english = lines(new HumanRenderer(false).render(d, SRC, Locale.ENGLISH)).get(0);
        String japanese = lines(new HumanRenderer(false).render(d, SRC, Locale.JAPANESE)).get(0);
        assertEquals(60, columnsOf(english));
        assertEquals(60, columnsOf(japanese));
        assertEquals(60, english.length(), "the English bar holds no wide character, so both agree");
    }
}
