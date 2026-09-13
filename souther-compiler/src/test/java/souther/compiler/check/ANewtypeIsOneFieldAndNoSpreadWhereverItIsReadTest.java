package souther.compiler.check;

import souther.compiler.ast.Hir;
import souther.compiler.meta.ModulePath;
import souther.compiler.query.Compilation;
import souther.compiler.query.Names;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a declaration written as one value under a name holds.
 *
 * <p>Its one field and no spread. The parser writes the field for the author — there is nowhere in
 * the surface to spread into a newtype and nowhere to write a second field — and every stage below
 * carries both along rather than deciding them again.
 *
 * <p>Held as a check because a reading depends on it. What a name wraps is answered from the
 * declaration's own {@code value} field ({@link NewtypeInners}), which is the same answer as the
 * fields it reaches only while there is nothing to reach through. The day a newtype can spread, that
 * answer has to say which it means, and this is what reddens to say so.
 */
class ANewtypeIsOneFieldAndNoSpreadWhereverItIsReadTest {

    /** Every surface a newtype has: a primitive, an optional written with {@code ?}, a name, and one
     *  written over another newtype. */
    private static final String EVERY_FORM = """
            module m

            data Amount = Int
            data Maybe = Int?
            data Held = Amount
            data Deep = Held

            data Product = { value: Int, beside: Int }
            """;

    @Test
    void everyNewtypeHoldsOneFieldCalledValueAndSpreadsNothing() {
        Hir.Module m = resolved();
        List<String> newtypes = new java.util.ArrayList<>();
        for (Hir.Def def : m.defs()) {
            if (def instanceof Hir.Data data && data.newtype()) {
                newtypes.add(data.name());
                assertEquals(List.of(), data.includes(),
                        () -> data.name() + " spreads something, so what it wraps is no longer the"
                                + " one field it writes");
                assertEquals(List.of(NewtypeInners.THE_ONE_VALUE),
                        data.fields().stream().map(Hir.Field::name).toList(),
                        () -> data.name() + " holds fields beside the one value");
            }
        }
        assertEquals(List.of("Amount", "Maybe", "Held", "Deep"), newtypes,
                "every newtype the surface can write was read");
    }

    /**
     * And a product of one field called {@code value} is not one of them.
     *
     * <p>The negative control: the two are told apart by how they were written and not by what they
     * happen to hold, which is what {@link Names.DeclarationIsNewtype} answers.
     */
    @Test
    void aProductThatHappensToWriteValueIsNoNewtype() {
        Hir.Module m = resolved();
        for (Hir.Def def : m.defs()) {
            if (def.name().equals("Product")) {
                assertTrue(assertInstanceOf(Hir.Data.class, def).fields().stream()
                                .anyMatch(each -> each.name().equals(NewtypeInners.THE_ONE_VALUE)),
                        "the control writes a field of that name");
                assertEquals(false, assertInstanceOf(Hir.Data.class, def).newtype(),
                        "and is no newtype for writing it");
            }
        }
    }

    private static Hir.Module resolved() {
        Map<String, String> byId = new LinkedHashMap<>();
        byId.put("m.sou", EVERY_FORM);
        return Compilation.ofDocuments(byId, Set.of(), ModulePath.EMPTY)
                .db().ask(new Names.Resolved("m")).value();
    }
}
