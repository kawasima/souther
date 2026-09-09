package souther.compiler.check;

import souther.compiler.ast.Hir;
import souther.compiler.types.Type;

/**
 * What a binding has to say about its own type, before anything is inferred.
 *
 * <p>Three ways a declaration answers for one, and they are three because they are read from
 * different places. A {@code let} names an expression, and what the binding is is whatever that
 * expression is declared to be — one more step of the same walk. A behavior's parameter names
 * nothing: the signature above it says what arrives there, and the walk has the answer without
 * going anywhere. A binding an inlining wrote names both — the argument it stands for and the
 * parameter type the callee declared — and which of the two it takes is a rule of its own.
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

    /**
     * The binding stands for an expression and carries the type the callee declared for the
     * parameter it was written for — what an inlined application leaves behind
     * ({@link Hir.LetIn#declaredType()} with no annotation over it).
     *
     * <p>Both halves, because neither answers on its own. A declared parameter type that is a sum is
     * what the body was written against and is wider than the case that arrived, so it wins; one
     * that stands for whatever this application decides says less than the argument does, and the
     * argument wins. Which of the two is the elaboration's to say, and it is asked there rather than
     * decided again here; it needs the argument's type to say it, so a reader holding only one of
     * the halves could not ask.
     */
    record Carried(Hir.RetType declared, Hir.Expr expression) implements BindingEvidence {}
}
