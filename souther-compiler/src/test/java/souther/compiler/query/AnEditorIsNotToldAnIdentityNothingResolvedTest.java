package souther.compiler.query;

import souther.compiler.cst.SourceLayout;
import souther.compiler.source.SourceId;

import org.junit.jupiter.api.Test;
import souther.compiler.diag.SourcePos;
import souther.compiler.meta.ModulePath;
import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbols;
import souther.compiler.types.TypeSymbol;
import souther.compiler.types.ValueName;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * A name nothing answered is a name the editor has no answer for.
 *
 * <p>What resolution worked out is what an editor is told, and a name that denotes nothing is not
 * something it worked out. Standing an identity in for the absence — a {@code TypeSymbol} of a
 * declaration nobody wrote, a {@code ValueName} nothing binds — makes the reader's question come
 * back answered, so go-to-definition, find-references and rename each act on a declaration that is
 * not there. Absence is the answer, and a reader that has none falls back to what it can say
 * honestly.
 *
 * <p>Each absence is asserted beside a name written in the same form that did resolve, and both in
 * one test. Held apart, the absence would be the answer a compilation that recorded nothing at all
 * gives — a reader with no facts to consult says nothing either — and the proposition here is the
 * other one: the facts are there, they say what the pass worked out, and they say nothing about
 * what it did not.
 */
class AnEditorIsNotToldAnIdentityNothingResolvedTest {

    private static final String ID = "m.sou";

    /** `D` is written at column 15 and `Nowhere` at column 21. */
    private static final String TYPES = """
            module m

            data D = { v: Int }
            data E = { v: D, w: Nowhere }
            """;

    /** `A` is written at column 16 and `Nowhere` at column 19. */
    private static final String CONSTRUCTS = """
            module m exposing ( A, f )

            data A = { n: Int }

            behavior f : (n: Int) -> A
                constructs A
            let f (n) = A { n = n }
            """;

    /** The binder `n` is at column 8 and `nowhere` at column 13. */
    private static final String VALUES = """
            module m exposing ( f )

            behavior f : (n: Int) -> Int
            let f (n) = nowhere
            """;

    private static Object under(String source, Key<?> key) {
        Map<String, String> byId = new LinkedHashMap<>();
        byId.put(ID, source);
        return Compilation.ofDocuments(byId, Set.of(), ModulePath.EMPTY).db().ask(key).value();
    }

    private static TypeSymbol typeAt(int line, int column) {
        return (TypeSymbol) under(TYPES, new Names.TypeAt(caret(TYPES, line, column)));
    }

    private static ValueName valueAt(int line, int column) {
        return (ValueName) under(VALUES, new Names.ValueAt(caret(VALUES, line, column)));
    }

    /** Both names are written in one field list, so what separates them is what they name. */
    @Test
    void aFieldTypeIsAnsweredAndOneNamingNothingIsNot() {
        assertEquals(TypeSymbols.declared(new TypeKey("m", "D")), typeAt(4, 15));
        assertNull(typeAt(4, 21), "no declaration is named `Nowhere`, so the field's type is none");
    }

    /** Both names are written in one {@code constructs} clause. */
    @Test
    void aClauseEntryIsAnsweredAndOneNamingNothingIsNot() {
        assertEquals(TypeSymbols.declared(new TypeKey("m", "A")),
                (TypeSymbol) under(CONSTRUCTS, new Names.TypeAt(caret(CONSTRUCTS, 6, 16))));
        assertNull((TypeSymbol) under(CONSTRUCTS, new Names.TypeAt(caret(CONSTRUCTS, 6, 19))),
                "no declaration is named `Nowhere`, so the clause names none");
    }

    /** Both names are written in one definition, one as its binder and one in its body. */
    @Test
    void aBinderIsAnsweredAndANameNothingBindsIsNot() {
        assertInstanceOf(ValueName.Local.class, valueAt(4, 8));
        assertNull(valueAt(4, 13), "nothing binds `nowhere`, so the cursor is on no value");
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
