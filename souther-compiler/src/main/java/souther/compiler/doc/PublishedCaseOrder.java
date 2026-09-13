package souther.compiler.doc;

import souther.compiler.ast.Hir;
import souther.compiler.types.CanonicalNameOrder;
import souther.compiler.types.Type;
import souther.compiler.types.TypeSymbol;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The cases of a published result, in the order the declaration writes them.
 *
 * <p>What this command publishes is a declaration, not a value. The parameters are already the
 * names the declaration wrote rather than anything the checker worked out, and a result written as
 * more than one case is the same: {@code Int | DivisionByZero} is what somebody wrote, and a reader
 * looking it up is looking up what they would have to write.
 *
 * <p>Which is why the order is not {@link CanonicalNameOrder}'s. A {@link Type.Union} is a set — two
 * writings of one union are one value, so a renderer given only the type cannot tell which of them
 * it came from, and shows the order this compiler decides. Here the declaration is still in hand, so
 * there is a writing to show and nothing has to be decided. No case is the result and no case is the
 * reason there is none (ADR-0007); the order carries no such claim, and is the author's for the same
 * reason the parameter names are.
 *
 * <p><b>What a written case does not account for is shown after it.</b> A case that names a sum
 * contributes that sum's own cases and not itself, so the members can hold names no written case
 * spells. Those have no place in the author's sequence — the sequence is over what was written —
 * and they come after it, in the order names are shown in. Reading them as the author's would be
 * claiming a writing for an expansion the author did not do.
 */
public final class PublishedCaseOrder {

    private PublishedCaseOrder() {
    }

    /**
     * {@code members}, those the declaration spells first and in its order, then the rest.
     *
     * <p>Answers the members and only the members: a written case that denotes something the union
     * does not hold is not published as one, whatever it says about the declaration.
     */
    public static List<TypeSymbol> asDeclared(Set<TypeSymbol> members, Hir.RetType declared) {
        Set<TypeSymbol> out = new LinkedHashSet<>();
        if (declared != null) {
            for (Hir.TypeTerm each : declared.cases()) {
                TypeSymbol spelled = named(each);
                if (spelled != null && members.contains(spelled)) {
                    out.add(spelled);
                }
            }
        }
        List<TypeSymbol> shown = new ArrayList<>(out);
        for (TypeSymbol each : CanonicalNameOrder.shown(members)) {
            if (!out.contains(each)) {
                shown.add(each);
            }
        }
        return List.copyOf(shown);
    }

    /**
     * The name a written case goes by, or nothing where it is not one a union holds.
     *
     * <p>Read off what the reference denotes rather than off the characters. What the source wrote —
     * a bare name, a qualified one, an alias — was settled during resolution, and a case matched by
     * its spelling would miss the writing that spelled it another way.
     */
    private static TypeSymbol named(Hir.TypeTerm term) {
        if (!(term instanceof Hir.TypeRef ref)) {
            return null;
        }
        return switch (ref.type()) {
            case Type.Ref r -> r.name();
            case Type.Prim p -> TypeSymbol.primitive(p.shown());
            default -> null;
        };
    }
}
