package souther.compiler.doc;

import org.junit.jupiter.api.Test;

import souther.compiler.ast.Hir;
import souther.compiler.ast.WrittenName;
import souther.compiler.diag.SourcePos;
import souther.compiler.types.Type;
import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbol;
import souther.compiler.types.TypeSymbols;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A published result of more than one case is written the way its declaration wrote it.
 *
 * <p>The surface a reader looks a name up on is a declaration: the parameters are the names it
 * wrote, and a result written as several cases is the sequence it wrote. A {@link Type.Union} is a
 * set and cannot say which of two writings it came from, so the order is read off the declaration
 * that is still in hand rather than decided here.
 *
 * <p>And what the declaration does not account for is said after it. A written case naming a sum
 * contributes that sum's cases and not itself, so a member can be one no written case spells; those
 * have no place in a sequence that is the author's, and follow it in the order names are shown in.
 */
class APublishedResultIsWrittenTheWayItWasDeclaredTest {

    private static final SourcePos POS = new SourcePos(0, 0);
    private static final TypeSymbol MINOR = TypeSymbols.declared(new TypeKey("example", "Minor"));
    private static final TypeSymbol ADULT = TypeSymbols.declared(new TypeKey("example", "Adult"));
    private static final TypeSymbol PENSIONER =
            TypeSymbols.declared(new TypeKey("example", "Pensioner"));

    private static Hir.TypeTerm written(TypeSymbol name) {
        return new Hir.TypeRef(WrittenName.of(name.name(), POS), null, null, Type.ref(name), POS);
    }

    private static Hir.RetType declaring(TypeSymbol... cases) {
        List<Hir.TypeTerm> terms = new java.util.ArrayList<>();
        for (TypeSymbol each : cases) {
            terms.add(written(each));
        }
        return new Hir.RetType(List.copyOf(terms), POS);
    }

    private static Set<TypeSymbol> members(TypeSymbol... names) {
        return new LinkedHashSet<>(List.of(names));
    }

    /** The declaration's order, and not the one names are shown in. */
    @Test
    void theCasesComeInTheOrderTheDeclarationWroteThem() {
        assertEquals(List.of(MINOR, ADULT),
                PublishedCaseOrder.asDeclared(members(ADULT, MINOR), declaring(MINOR, ADULT)));
    }

    /** However the set holding the members walks them. */
    @Test
    void theSetTheMembersArriveInDecidesNothing() {
        assertEquals(
                PublishedCaseOrder.asDeclared(members(ADULT, MINOR), declaring(MINOR, ADULT)),
                PublishedCaseOrder.asDeclared(members(MINOR, ADULT), declaring(MINOR, ADULT)));
    }

    /** A member no written case spells follows what was written, in the order names are shown in. */
    @Test
    void whatTheDeclarationDoesNotSpellComesAfterWhatItDoes() {
        assertEquals(List.of(PENSIONER, ADULT, MINOR),
                PublishedCaseOrder.asDeclared(members(MINOR, ADULT, PENSIONER),
                        declaring(PENSIONER)));
    }

    /** And with no declaration in hand there is no writing to show, so the names decide. */
    @Test
    void withNothingDeclaredTheNamesDecide() {
        assertEquals(List.of(ADULT, MINOR, PENSIONER),
                PublishedCaseOrder.asDeclared(members(PENSIONER, MINOR, ADULT), null));
    }
}
