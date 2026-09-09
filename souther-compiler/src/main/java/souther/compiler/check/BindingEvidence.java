package souther.compiler.check;

import souther.compiler.ast.Hir;
import souther.compiler.types.Type;

/**
 * What a binding has to say about its own type, before anything is inferred.
 *
 * <p>Two ways a declaration answers for one, and they are two because they are read from different
 * places. A {@code let} names an expression, and what the binding is is whatever that expression is
 * declared to be — one more step of the same walk. A behavior's parameter names nothing: the
 * signature above it says what arrives there, and the walk has the answer without going anywhere. A
 * type an author wrote on a {@code let} is the second of these as well, being a declaration.
 *
 * <p>Kept as one alternative rather than as two environments a walk consults in turn. Two would be
 * two places a binding could be, and a binding in both would have two answers with nothing saying
 * which — where a walk handed one of these has the answer the binding has.
 */
public sealed interface BindingEvidence {

    /** The binding stands for an expression, and takes whatever that is declared to be. */
    record BoundTo(Hir.Expr expression) implements BindingEvidence {}

    /** A declaration says outright what arrives here. */
    record DeclaredAs(Type type) implements BindingEvidence {}
}
