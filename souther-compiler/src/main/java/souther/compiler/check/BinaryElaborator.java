package souther.compiler.check;

import souther.compiler.types.BinOp;
import souther.compiler.ast.Hir;
import souther.compiler.core.Core;
import souther.compiler.diag.CompileException;
import souther.compiler.diag.Diagnostic;
import souther.compiler.diag.msg.DeclarationMessage;
import souther.compiler.diag.msg.TypeMessage;
import souther.compiler.types.Type;
import souther.compiler.types.TypeSymbol;
import java.util.List;

/**
 * Typing a binary operator, including the rules that let a single-value newtype compare with and
 * add to a bare literal of its base without losing its own identity (ADR-0047).
 */
public final class BinaryElaborator {

    private BinaryElaborator() {}

    /**
     * Reads one operand, and gives up on the definition where its type is one the compiler could not
     * work out.
     *
     * <p>An operator wants a particular shape of type — Int or Decimal to add, two lists or two
     * strings to append — and an operand the compiler could not work out has no shape. Absorbing is
     * for a comparison, which can answer "no disagreement"; there is no answer to give here, so the
     * definition is abandoned and the name that denoted nothing stands as what was wrong. Left alone,
     * the operand's type reaches the message as `?`, which names nothing the author could go looking
     * for.
     *
     * <p>{@code &&} and {@code ||} are the operators that ask something of an operand on its own,
     * and that is asked here, before the operand beside it is read: an operand is finished where it
     * stands. The rest ask about the pair and cannot answer until both have been read.
     *
     * <p>What this answers with is what the operator's rule then reads. Asking the question of a
     * reading of its own cost the expression twice its own subtree, and an operand of an operand
     * twice again, so a chain of operators nested one inside the last cost two to the power of its
     * length: twenty-six links took ten seconds and a hundred did not finish.
     */
    private static Core operand(Hir.Expr e, Hir.Binary bin, Scope env, CheckContext ctx) {
        Core read = Elaborator.elaborate(e, env, ctx);
        if (read.type() instanceof Type.Erroneous) {
            throw new Unanswerable(bin.pos());
        }
        if (bin.op().joinsTwoConditions()) {
            Elaborator.requireType(e, read.type(), Type.BOOL, ctx.published(),
                    "operand of logical operator");
        }
        return read;
    }

    static Core elaborateBinary(Hir.Binary bin, Scope env, CheckContext ctx) {
        // Left to right, and the first operand that failed is the one reported: an operand that says
        // what is wrong with it has said it, and what stands beside it is not read at all where this
        // one left nothing to check against.
        Core left = operand(bin.left(), bin, env, ctx);
        Core right = operand(bin.right(), bin, env, ctx);
        return switch (bin.op()) {
            // both operands were asked for a Bool where they were read
            case AND, OR -> new Core.Binary(bin.op(), left, right, ctx.occurrenceOf(bin.origin()), Type.BOOL, bin.pos());
            case LT, LE, GT, GE -> {
                // The ordered primitives: Int numerically, String lexicographically, Decimal by
                // value, Date/DateTime in time. Unlike Elm (which orders only Int/Float/Char/String
                // because it rides JavaScript), Souther sits on the JVM where BigDecimal/LocalDate/
                // LocalDateTime are Comparable, so it orders them too. A single-value newtype over an
                // ordered type is ordered by that value; the operands stay the same newtype (nominal),
                // except that a bare literal takes the other side's newtype from context.
                Type lt = left.type();
                Type rt = right.type();
                if (!orderedComparable(lt, rt, bin.left(), bin.right(), ctx.inners(), ctx.symbols(),
                        ctx.kinds(), ctx.published())) {
                    throw CompileException.of(Diagnostic
                                    .at(bin.pos()).say(new TypeMessage.ComparisonNeedsOrderedValuesOfOneType(Type.show(lt), Type.show(rt))).build());
                }
                yield new Core.Binary(bin.op(), left, right, ctx.occurrenceOf(bin.origin()), Type.BOOL, bin.pos());
            }
            case ADD, SUB, MUL, DIV -> {
                // `+ - * /` work on two Int or two Decimal operands (spec
                // §an-operator-takes-the-types-it-is-defined-for). Int aborts on overflow and `/` aborts on a
                // zero divisor; Decimal `/` rounds by the default scale/mode. Case handling for a zero
                // divisor is the `divide`/`remainder` functions.
                Type lt = left.type();
                Type rt = right.type();
                // The rules live in ArithmeticCheck, which answers with the type the operator gives
                // back or with the rule that refused the operands. Nothing is decided a second time
                // here: a refusal already knows what it is, and this only points it at the source.
                ArithmeticCheck answer = ArithmeticCheck.of(bin.op(), lt, rt,
                        isLiteralExpr(bin.left()), isLiteralExpr(bin.right()), ctx.symbols());
                yield switch (answer) {
                    case ArithmeticCheck.Allowed allowed -> arithmetic(bin, left, right,
                            allowed.resultType(), ctx);
                    case ArithmeticCheck.DeferToPlainTypeCheck _ -> {
                        // One type against another: the found-versus-expected block says it better
                        // than a sentence would, and requireType raises or absorbs it.
                        Elaborator.requireType(bin.right(), rt, lt, ctx.published(),
                                "operand of arithmetic");
                        yield new Core.Binary(bin.op(), left, right, ctx.occurrenceOf(bin.origin()), lt, bin.pos());
                    }
                    case ArithmeticCheck.Refused no -> throw refused(bin, no.refusal(), lt, rt);
                };
            }
            case CONCAT -> {
                // `++` is Elm's appendable operator: two strings concatenate to a string, two lists to a list
                // (spec §an-operator-takes-the-types-it-is-defined-for). Strings are checked first, before
                // the empty-list absorption below.
                Type lraw = left.type();
                Type rraw = right.type();
                if (lraw == Type.STRING && rraw == Type.STRING) {
                    yield new Core.Binary(bin.op(), left, right, ctx.occurrenceOf(bin.origin()), Type.STRING, bin.pos());
                }
                // A bottom operand ({@code Nothing}) is a list read from an accumulator an empty
                // collection seed grows — the value at a key of a `Map.empty`-seeded fold, whose element
                // type is not fixed yet. At run time it is a list, so read it as the empty list and let
                // the other operand fix the element type, as `[] ++ xs` does.
                Type lt = BottomInfer.bottomAsEmptyList(lraw);
                Type rt = BottomInfer.bottomAsEmptyList(rraw);
                if (!(lt instanceof Type.ListOf lo) || !(rt instanceof Type.ListOf ro)) {
                    throw CompileException.of(Diagnostic
                                    .at(bin.pos(), 2)
                                    .secondary(bin.left().reportedAt(), new DeclarationMessage.ThisOperandIs(Type.show(lt, rt)))
                                    .secondary(bin.right().reportedAt(), new DeclarationMessage.ThisOperandIs(Type.show(rt, lt)))
                                    
                                    .say(new TypeMessage.JoinsTwoListsOrTwoStrings(Type.show(lt, rt), Type.show(rt, lt))).build());
                }
                Type element = BottomInfer.unifyElem(lo.element(), ro.element());
                if (element == null) {
                    // Both sides are written operands, one level in from what is compared: the
                    // element types have no source of their own, but the lists holding them do, so
                    // each is labelled where it is written and the message names neither.
                    throw CompileException.of(Diagnostic
                                    .at(bin.pos(), 2)
                                    .secondary(bin.left().reportedAt(), new TypeMessage.ThisListHoldsElementsOf(Type.show(lo.element(), ro.element())))
                                    .secondary(bin.right().reportedAt(), new TypeMessage.ThisListHoldsElementsOf(Type.show(ro.element(), lo.element())))
                                    .hint(new TypeMessage.MakeEveryElementTheSameType())
                                    .say(new TypeMessage.TheTwoListsHoldDifferentElements()).build());
                }
                yield new Core.Binary(bin.op(), left, right, ctx.occurrenceOf(bin.origin()), Type.list(element), bin.pos());
            }
            case EQ, NE -> {
                Type lt = left.type();
                Type rt = right.type();
                // two values of the same data compare by their fields (spec §equality); across different
                // types there is nothing to compare. An operand may be the scalar empty-collection
                // bottom (`Nothing`) when it reads an accumulator a `[]` seed grows — the `e` in
                // `if any(e -> e == x, acc) …` over a `fold(…, [], xs)` is bound to the not-yet-fixed
                // element type (ADR-0028). At run time it holds the other operand's type, so absorb the
                // bottom rather than reject the comparison. (A whole empty list `[]` stays a type error
                // against a non-list, so this does not loosen `[] == 5`.)
                // A sum may also be compared with one of its cases (`役職 == 一般社員`): a case value
                // is a value of its sum (case->sum is transparent everywhere else, spec §sum-data), so
                // this is a sum-vs-sum comparison by case (spec §equality). Check the relation on the
                // top-level case sets directly, not through `assignable` — `assignable` recurses into
                // collections (covariance), which would wrongly let `List<一般社員> == List<役職>` compare;
                // the exemption is only the direct sum<->case scalar relationship. Unrelated types
                // (`金額 == 数量`) have disjoint case sets and still fail.
                // `==` is value equality, and a function value has none: comparing two would fall
                // back to whether they are the same object, which is not a question the language asks
                if (!TypeOps.supportsEquality(lt)
                        || !TypeOps.supportsEquality(rt)) {
                    Type carrier = TypeOps.supportsEquality(lt) ? rt : lt;
                    throw CompileException.of(Diagnostic
                                    .at(bin.pos(), 2).say(new TypeMessage.AFunctionHasNoValueToCompare(Type.show(carrier))).build());
                }
                List<TypeSymbol> lCases = AtomSpace.subjectAtoms(lt, ctx.published());
                List<TypeSymbol> rCases = AtomSpace.subjectAtoms(rt, ctx.published());
                boolean caseOfSum = !lCases.isEmpty() && !rCases.isEmpty()
                        && (lCases.containsAll(rCases) || rCases.containsAll(lCases));
                if (!lt.equals(rt) && !eqCoercible(lt, rt, bin.left(), bin.right(), ctx.inners(), ctx.symbols())
                        && !caseOfSum && !BottomInfer.isBottom(lt) && !BottomInfer.isBottom(rt)) {
                    throw CompileException.of(Diagnostic
                                    .at(bin.pos(), 2)
                                    .secondary(bin.left().reportedAt(), new DeclarationMessage.ThisOperandIs(Type.show(lt, rt)))
                                    .secondary(bin.right().reportedAt(), new DeclarationMessage.ThisOperandIs(Type.show(rt, lt)))
                                    
                                    .say(new TypeMessage.TheseTwoCannotBeCompared(Type.show(lt, rt), Type.show(rt, lt))).build());
                }
                yield new Core.Binary(bin.op(), left, right, ctx.occurrenceOf(bin.origin()), Type.BOOL, bin.pos());
            }
        };
    }

    /** A source literal (Int/Decimal/String/Bool, or a negated literal) — the only thing allowed to
     * take a newtype from the other operand. A variable of the underlying type is not (write the
     * newtype construction, e.g. {@code 金額(x)}). */
    static boolean isLiteralExpr(Hir.Expr e) {
        return e instanceof Hir.IntLit || e instanceof Hir.DecimalLit
                || e instanceof Hir.StringLit || e instanceof Hir.BoolLit
                || (e instanceof Hir.Neg n && isLiteralExpr(n.operand()));
    }

    /**
     * Whether {@code <}/{@code <=}/{@code >}/{@code >=} may compare the operands.
     *
     * <p>Three ways, and every one of them is asked of the operands <em>as written</em>. That is the
     * rule and not an implementation detail: the nominal boundary is the type, so {@code data
     * StageA = Stage} and {@code data StageB = Stage} open to one order and are still not comparable
     * (ADR-0047). A reading that reduced both sides first and then asked what orders them would
     * admit that pair, which is why {@link Ordering#ofComparison} — the reading that does reduce
     * first — is the backend's and says so.
     */
    static boolean orderedComparable(Type lt, Type rt, Hir.Expr le, Hir.Expr re,
                                             NewtypeInners inners,
                                             Symbols symbols, DeclarationKinds kinds,
                                             PublishedDeclarations published) {
        // Two of the same type, where that type has an order: 金額 <= 金額, Stage <= Stage, and
        // StageN <= StageN, whose order is the enumeration it wraps (ADR-0047 over ADR-0069).
        if (lt.equals(rt)) {
            return TypeOps.supportsOrdering(lt, inners, symbols, kinds, published);
        }
        // Two values of one enumeration that are not one type: a case value is a value of its sum
        // (spec §sum-data), so `stage < Won` compares in the sum both sides belong to (issue #161).
        if (TypeOps.comparisonEnumeration(lt, rt, symbols, kinds, published) != null) {
            return true;
        }
        // A newtype and a source literal of what it wraps: 金額 <= 100, but not 金額 <= n for an
        // Int variable, and not 金額 <= 数量. Ordering asks in addition that the wrapped value be
        // ordered, which the equality rule this shares does not.
        return TypeOps.supportsOrdering(lt, inners, symbols, kinds, published)
                && TypeOps.base(lt, inners).equals(TypeOps.base(rt, inners))
                && literalPairsNewtype(lt, rt, le, re, symbols);
    }

    /** Whether {@code ==}/{@code /=} may pair a newtype with a bare literal of its base type (the
     * same-type and bottom cases are handled by the caller). */
    static boolean eqCoercible(Type lt, Type rt, Hir.Expr le, Hir.Expr re,
                                       NewtypeInners inners, Symbols symbols) {
        return TypeOps.base(lt, inners).equals(TypeOps.base(rt, inners))
                && literalPairsNewtype(lt, rt, le, re, symbols);
    }

    /** The refusal, pointed at the source: at the operand it is about, or — where the rule is about
     * the pair — at the operator with each operand named beside it, as a comparison of two
     * unrelated types is. */
    private static CompileException refused(Hir.Binary bin, ArithmeticCheck.Refusal refusal,
                                            Type lt, Type rt) {
        Diagnostic.Builder d = refusal.saying();
        if (refusal.side() == ArithmeticCheck.Side.BOTH) {
            d = d.at(bin.pos())
                    .secondary(bin.left().reportedAt(), new DeclarationMessage.ThisOperandIs(Type.show(lt, rt)))
                    .secondary(bin.right().reportedAt(), new DeclarationMessage.ThisOperandIs(Type.show(rt, lt)));
        } else {
            Hir.Expr faulted = refusal.side() == ArithmeticCheck.Side.LEFT ? bin.left() : bin.right();
            d = d.at(faulted.reportedAt());
        }
        return CompileException.of(d.build());
    }

    /**
     * What {@code op} asks of one operand, given that the operand beside it is {@code other}.
     * {@code onTheRight} says which side the asked-about operand stands on, because the rule is not
     * symmetric.
     *
     * <p>The question a reader of an operand asks — what does standing here make me? — is the same
     * question {@link #elaborateBinary} asks, so it is put to the same rules rather than answered
     * again beside them: each type an operand could be is offered to {@link ArithmeticCheck}, and
     * the one the operator accepts is the answer. Where it accepts neither, nothing follows and the
     * answer is null.
     */
    static Type operandBeside(BinOp op, Type other, boolean onTheRight, Symbols symbols) {
        if (op.joinsTwoConditions()) {
            return Type.BOOL;
        }
        if (other == null) {
            return null;
        }
        Type base = TypeOps.directNumericNewtypeBase(other, symbols);
        if (base == null || !(op == BinOp.MUL || op == BinOp.DIV)) {
            return other;
        }
        for (Type candidate : List.of(other, base)) {
            Type lt = onTheRight ? other : candidate;
            Type rt = onTheRight ? candidate : other;
            if (ArithmeticCheck.of(op, lt, rt, false, false, symbols) instanceof ArithmeticCheck.Allowed) {
                return candidate;
            }
        }
        return null;
    }

    /** One side is a single-value newtype and the other is a bare literal (not itself a newtype). */
    static boolean literalPairsNewtype(Type lt, Type rt, Hir.Expr le, Hir.Expr re,
                                               Symbols symbols) {
        return (TypeOps.isSingleValueNewtype(lt, symbols) && !TypeOps.isSingleValueNewtype(rt, symbols) && isLiteralExpr(re))
                || (TypeOps.isSingleValueNewtype(rt, symbols) && !TypeOps.isSingleValueNewtype(lt, symbols) && isLiteralExpr(le));
    }

    /**
     * The arithmetic, as the value it answers with. Where the operator answers a newtype, that value
     * is built here: each operand is opened to the number it wraps, the operator works on those, and
     * the result is constructed again (spec §newtype-arithmetic). Written as the construction it is,
     * so the invariant is owed where every other invariant is owed, and no reader has to recognise an
     * expression as a construction to find it.
     */
    private static Core arithmetic(Hir.Binary bin, Core left, Core right, Type result,
                                   CheckContext ctx) {
        Type base = TypeOps.directNumericNewtypeBase(result, ctx.symbols());
        // A newtype is a declaration a module wrote, which is what having a base says of it; the
        // pattern is what used to be an unchecked cast below.
        if (base == null || !(result instanceof Type.Ref(TypeSymbol.AtModule wrapper))) {
            return new Core.Binary(bin.op(), left, right, ctx.occurrenceOf(bin.origin()), result, bin.pos());
        }
        Core computed = new Core.Binary(bin.op(), opened(left, ctx), opened(right, ctx),
                ctx.occurrenceOf(bin.origin()),
                base, bin.pos());
        return new Core.Construct(wrapper,
                List.of(new Core.FieldValue(WRAPPED, computed, bin.pos())), result, bin.pos());
    }

    /** The number an operand carries: a newtype is opened to what it wraps, and anything else is
     * already the number. Arithmetic admits one layer of newtype ({@link
     * TypeOps#directNumericNewtypeBase}), so one read reaches it. */
    private static Core opened(Core operand, CheckContext ctx) {
        Type base = TypeOps.directNumericNewtypeBase(operand.type(), ctx.symbols());
        return base == null ? operand
                : new Core.FieldAccess(operand, WRAPPED, base, operand.pos());
    }

    /** What a newtype calls the value it wraps (spec §newtype) — the name {@link TypeOps#wrapped}
     * reads it by, said here so both reach the same field. */
    private static final String WRAPPED = "value";
}
