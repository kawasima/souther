package souther.compiler.check;

import souther.compiler.ast.Hir;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The declarations of a module admitted, each under the name it is declared with.
 *
 * <p>The way in from outside {@code check}. {@link SignatureBoundary} stays where it is — a
 * declaration is admitted by the walk and nothing else may reach it — and this is what the query
 * that owns the answer calls. One caller and one walk: two would each build a correct answer and the
 * phases below would read one of them while the check read the other.
 *
 * <p>Only behaviors that wrote parameters are here. A composition declares stages, and what it takes
 * is its first stage's already-admitted inputs, so there is no declaration of its own to admit; its
 * signature is worked out in {@link PipelineSigs} and is a {@link Sig} with no parameters to name.
 */
public final class SignatureDeclarations {

    private SignatureDeclarations() {}

    /**
     * What each behavior that declares parameters was admitted as.
     *
     * <p>A declaration resting on a name that denotes nothing is left out rather than refused: the
     * name was reported where it was written, and there is no boundary to admit under it. Everything
     * else the boundary does not carry is refused here, which is what stops a module from being
     * checked past a declaration nothing below could have read.
     */
    public static Map<String, DeclaredSig> of(List<Hir.BehaviorDef> behaviors, Symbols symbols,
                                              DeclarationKinds kinds,
                                              PublishedDeclarations published) {
        Map<String, DeclaredSig> declared = new LinkedHashMap<>();
        for (Hir.BehaviorDef b : behaviors) {
            if (b instanceof Hir.SpecBehavior spec) {
                try {
                    declared.put(spec.name(),
                            SignatureBoundary.of(spec, symbols, kinds, published));
                } catch (Unanswerable _) {
                    // deliberately empty: see above
                }
            }
        }
        return declared;
    }
}
