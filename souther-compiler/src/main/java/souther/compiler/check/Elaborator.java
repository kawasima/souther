package souther.compiler.check;

import souther.compiler.ast.Hir;
import souther.compiler.core.CompleteSignature;
import souther.compiler.core.Core;
import souther.compiler.diag.CompileException;
import souther.compiler.diag.Diagnostic;
import souther.compiler.diag.Region;
import souther.compiler.diag.msg.DeclarationMessage;
import souther.compiler.diag.msg.DataMessage;
import souther.compiler.diag.msg.NameMessage;
import souther.compiler.diag.msg.TypeMessage;
import souther.compiler.diag.msg.ModuleMessage;
import souther.compiler.diag.msg.AttemptMessage;
import souther.compiler.diag.msg.HelperMessage;
import souther.compiler.diag.SourcePos;
import souther.compiler.types.BindingId;
import souther.compiler.types.Type;
import souther.compiler.types.TypeSymbol;
import souther.compiler.types.ApplicationDerivationCause;
import souther.compiler.types.ApplicationOrigin;
import souther.compiler.types.DerivedReferenceOrigin;
import souther.compiler.types.ReferenceDerivationCause;
import souther.compiler.types.ReferenceOrigin;
import souther.compiler.types.ValueName;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Types an expression and produces the Core node for it, carrying the type this decided (issue #81):
 * the backend emits from what this returns instead of inferring the same types a second time.
 *
 * <p>The switch over expression kinds is here; the kinds with rules of their own live next door —
 * {@link CallElaborator}, {@link BinaryElaborator}, {@link MatchElaborator} — and the bottom an
 * empty collection carries is {@link BottomInfer}'s.
 */
public final class Elaborator {

    private Elaborator() {}

    // --- expression typing (shared with the backend) ---

    /** No required behaviors are in scope (decoders, encoders, invariants — spec §invariant-expressions, §purity). */
    static final Map<ValueName.Behavior, ReqSig> NO_REQS = Map.of();


    public static Type typeOf(Hir.Expr e, Scope env, CheckContext ctx) {
        return typeOf(e, env, ctx, null);
    }

    /** The type of {@code e}, discarding the Core the elaboration produced — for the checks that ask
     * only whether an expression types (a decoder, a helper's standalone check). What a clause of a
     * declaration comes to is not one of them: it is elaborated once and kept, because something
     * runs it (issue #1080). */
    public static Type typeOf(Hir.Expr e, Scope env, CheckContext ctx, Type expected) {
        return elaborate(e, env, ctx, expected).type();
    }

    public static Core elaborate(Hir.Expr e, Scope env, CheckContext ctx) {
        return elaborate(e, env, ctx, null);
    }

    /**
     * Types {@code e} and produces the Core node for it, carrying the type this decided (issue #81).
     * The backend emits from what this returns instead of inferring the same types a second time.
     *
     * <p>Bidirectional typing: {@code expected} is the type this expression is checked against, pushed
     * down from the surrounding context (a declared field type, a declared return type). It may be
     * {@code null} — no context — in which case this behaves exactly as pure bottom-up synthesis.
     * Only a few arms consume it: the empty-collection leaves ([], Map.empty, Set.empty) adopt it,
     * and a generic call pre-binds its result-type variables from it, so a fold whose seed is an
     * empty collection has its accumulator pinned by context before the step is checked.
     */
    public static Core elaborate(Hir.Expr e, Scope env, CheckContext ctx, Type expected) {
        return equatableCollections(elaborating(e, env, ctx, expected), ctx);
    }

    /**
     * Refuses a {@code Set} or a {@code Map} that would have to compare a function. Asked of what
     * elaboration decided rather than of what was written, because such a collection need not be
     * written to exist: {@code List.distinct} grows a seen-set of its elements, and only the
     * elaborated type says what those are.
     */
    private static Core equatableCollections(Core c, CheckContext ctx) {
        TypeOps.UncomparableIn bad = TypeOps.uncomparableCollection(c.type(), ctx.symbols());
        if (bad == null) {
            return c;
        }
        // which collection asked is part of the answer: a Set asks whether two elements are equal and
        // a Map whether two keys are, and the report says which of the two it was
        throw CompileException.of(Diagnostic.at(c.pos())
                .say(bad instanceof TypeOps.UncomparableIn.MapKey
                        ? new TypeMessage.AMapKeyIsComparedAndAFunctionIsNot(Type.show(bad.type()))
                        : new TypeMessage.ASetElementIsComparedAndAFunctionIsNot(
                                Type.show(bad.type())))
                .build());
    }

    private static Core elaborating(Hir.Expr e, Scope env, CheckContext ctx,
                                    Type expected) {
        return switch (e) {
            case Hir.IntLit x -> new Core.Int(x.value(), Type.INT, x.pos());
            case Hir.DecimalLit x -> new Core.Decimal(x.value(), Type.DECIMAL, x.pos());
            case Hir.StringLit x -> new Core.Str(x.value(), Type.STRING, x.pos());
            case Hir.BoolLit x -> new Core.Bool(x.value(), Type.BOOL, x.pos());
            // It answers no value, so its type is `Never` — but the position it stands in still has a
            // shape to leave behind, and where the position states one that is what is recorded. A
            // position that states none leaves `Never`, and a sibling branch may still fix it at the
            // join (a `match` arm beside one that answers a value).
            case Hir.Unreachable u ->
                    new Core.Unreachable(u.reason(), expected == null ? Type.NEVER : expected, u.pos());
            case Hir.Tuple tup -> {
                // A pushed-down tuple type reaches each element, so a written `(Set<Int>, List<Int>)`
                // fixes the empty collections the tuple seeds rather than leaving them bottoms (#74).
                List<Type> want = expected instanceof Type.TupleOf te
                        && te.elements().size() == tup.elements().size() ? te.elements() : null;
                List<Core> elems = new ArrayList<>();
                List<Type> elemTypes = new ArrayList<>();
                for (int i = 0; i < tup.elements().size(); i++) {
                    Core el = elaborate(tup.elements().get(i), env, ctx,
                            want == null ? null : want.get(i));
                    elems.add(el);
                    elemTypes.add(el.type());
                }
                yield new Core.Tuple(elems, Type.tuple(elemTypes), tup.pos());
            }
            case Hir.TupleGet tg -> {
                Core tuple = elaborate(tg.tuple(), env, ctx);
                Type tt = tuple.type();
                if (!(tt instanceof Type.TupleOf to)) {
                    throw CompileException.of(Diagnostic
                                    .at(tg.pos()).say(new TypeMessage.ATuplePatternNeedsATuple(Type.show(tt))).build());
                }
                if (to.elements().size() != tg.arity()) {   // exact arity, in either direction (Elm)
                    throw CompileException.of(Diagnostic
                                    .at(tg.pos()).say(new TypeMessage.ThePatternBindsAnotherNumberOfNames(String.valueOf(tg.arity()), String.valueOf(to.elements().size()))).build());
                }
                yield new Core.TupleGet(tuple, tg.index(), tg.arity(),
                        to.elements().get(tg.index()), tg.pos());
            }
            case Hir.Neg neg -> {
                Core operand = elaborate(neg.operand(), env, ctx);
                Type t = operand.type();
                if (t != Type.INT && t != Type.DECIMAL) {
                    throw CompileException.of(Diagnostic
                                    .at(neg.reportedAt())
                                    .say(new TypeMessage.UnaryMinusNeedsANumber(Type.show(t))).build());
                }
                yield new Core.Neg(operand, t, neg.pos());
            }
            case Hir.LetIn li -> {
                Type annotation = annotatedType(li, ctx.symbols());
                Core value;
                Type bindType;
                if (annotation instanceof Type.FnOf declared && producesFunction(li.value())) {
                    // the written type says what the function takes, so nothing has to be read off
                    // the applications — which is what a function passed on rather than applied has
                    // none of, and what a function applied only inside a lambda cannot give
                    value = elaborateFunctionValue(li.value(), declared.params(), env, ctx);
                    checkLetAnnotation(li, declared, value.type(), ctx.symbols());
                    bindType = declared;
                } else if (isFunctionSelection(li.value())) {
                    // a lambda bound to a local that could not be inlined (e.g. chosen by an `if`):
                    // it is a first-class function value. Its parameter types are unannotated, so
                    // infer them from how the body applies it (spec §blocks).
                    if (annotation != null) {
                        throw functionAnnotation(li);   // an ordinary type does not describe a function
                    }
                    List<Type> paramTypes = inferFnParamTypes(li.binder(), li.body(), env, ctx);
                    value = elaborateFunctionValue(li.value(), paramTypes, env, ctx);
                    bindType = value.type();
                } else if (annotation != null) {
                    // the written type is the value's expected type, so an empty collection bound here
                    // takes its element/value type from the annotation rather than staying a bottom
                    value = elaborate(li.value(), env, ctx, annotation);
                    checkLetAnnotation(li, annotation, value.type(), ctx.symbols());
                    bindType = annotation;
                } else {
                    value = elaborate(li.value(), env, ctx);
                    bindType = carriedType(li, value.type(), ctx.symbols());
                }
                if (li.opens() != null) {
                    checkOpens(li, bindType, ctx.symbols());
                }
                // the binding is visible only inside the body, so a sibling branch cannot see it
                Scope inner = env.with(li.binder(), bindType);
                Core body = elaborate(li.body(), inner, ctx, expected);
                yield new Core.LetIn(CoreBinders.of(li.binder()), value, body, body.type(), li.pos());
            }
            case Hir.Expansion ex -> expansion(ex, env, ctx, expected);
            // reached only where a block escapes: it may be passed as an argument, or bound to a
            // `let` and applied, but it is not a value that can be returned or stored, because that
            // would need a runtime closure (spec §blocks)
            // a lambda where a function is expected is that function: the context said what it takes,
            // so nothing has to be read off its applications
            case Hir.Block block when expected instanceof Type.FnOf want ->
                    elaborateFunctionValue(block, want.params(), env, ctx);
            case Hir.Block block -> throw CompileException.of(Diagnostic
                            .at(block.pos()).say(new NameMessage.ABlockIsNotAValue()).build());
            // What the name is was answered when the module's names were resolved; what is left here
            // is its type. A binding is looked up, a unit data is its own value (spec §unit-data), and
            // anything else is not a value — reported below under the name that was written.
            // A name nothing answered has no meaning to work out, and the definition it is in has
            // none either: reported where it is written, and abandoned here.
            case Hir.Var.Unanswered v -> throw new Unanswerable(v.pos());
            case Hir.Var.Denoting v -> switch (v.denotes()) {
                // A binding with no type here is not a naming question: an inference probe types a
                // body before the binding it asks about has one, and reads the report to find out.
                case ValueName.Local local when env.typeOf(local.id()) != null ->
                        new Core.Read(v.name(), local.id(), env.typeOf(local.id()), v.pos());
                case ValueName.OfType named
                        when ctx.symbols().declaredNode(named.type()) instanceof Hir.UnitData ->
                        new Core.UnitValue(named.type(), Type.ref(named.type()), v.pos());
                // `None` where a `?` field is being given a value: the empty optional (spec §algebraic-types).
                // What puts it here is the field, which the context says (ADR-0011) — not the expected
                // type, since a model may name `Option<T>` where it reads one and an expectation would
                // then license making one anywhere the name is written (issue #202).
                case ValueName.Builtin b when b.name().equals("None")
                        && ctx.makingAnOptional() && expected instanceof Type.OptionOf ->
                        new Core.OptionNone(expected, v.pos());
                // a library name written with no parameter list where it was declared: a value,
                // typed from the context as `[]` is
                case ValueName.Stdlib _ -> {
                    Core value = CallElaborator.libraryValue(v, ctx, expected);
                    if (value == null) {
                        throw notAValue(v, env);   // a library function, not a library value
                    }
                    yield value;
                }
                // A value this representation kept standing, read under the signature its own check
                // settled. What the value is a constant of is not asked here: that is folded into
                // the reference where the reference is written, so nothing downstream of this has
                // to learn that a name can stand for one.
                case ValueName.Helper _ when ctx.preserved().valueKept(v.denotes()) != null ->
                        keptValue(ctx.preserved().valueKept(v.denotes()), v.origin(), v.pos());
                default -> throw notAValue(v, env);
            };
            case Hir.FieldAccess fa -> elaborateFieldAccess(fa, env, ctx);
            case Hir.Apply call -> CallElaborator.elaborateCall(call, env, ctx, expected);
            case Hir.Binary bin -> BinaryElaborator.elaborateBinary(bin, env, ctx);
            case Hir.NewData nd -> {
                if (!(nd.typeName() instanceof Hir.Name.Denoting built)) {
                    // reported where the name is written; this definition has no meaning to work out
                    throw new Unanswerable(nd.pos());
                }
                if (!(built.type() instanceof TypeSymbol.AtModule constructed)
                        || !(ctx.symbols().declaredNode(constructed)
                                instanceof Hir.Data owner)) {
                    throw CompileException.of(Diagnostic
                                    .at(built.name().reportedAt())
                                    .say(new DataMessage.ItCannotBeConstructedHere(built.name().quoted())).build());
                }
                // By here every spread that names a value names a binding in force: a value spread
                // was bound ahead of the construction when it was inlined, so Core reads the binding
                // it copies from. What remains unbound is a name that stands for no value — a
                // behavior, a function, a name nothing declares — and a spread reads a value's
                // fields, so it is refused the way the name would be anywhere else a value goes.
                List<Core.Read> spreads = new ArrayList<>();
                for (Hir.Var s : nd.spreads()) {
                    if (!(s instanceof Hir.Var.Denoting named
                            && named.denotes() instanceof ValueName.Local local)) {
                        throw notAValue(s, env);
                    }
                    spreads.add(new Core.Read(named.denotes().name(), local.id(), env.typeOf(local.id()),
                            s.pos()));
                }
                List<Core.FieldValue> values = DataChecker.checkConstruction(built.written(),
                        nd.inits(), spreads, nd.pos(),
                        TypeOps.fieldTypes(owner, ctx.symbols()), env, ctx,
                        nd.mayOmitOptionalFields());
                yield new Core.Construct(constructed, values, Type.ref(constructed), nd.pos());
            }
            case Hir.Match m -> MatchElaborator.elaborateMatch(m, env, ctx, expected);
            case Hir.If iff -> {
                Core cond = requireTyped(iff.cond(), Type.BOOL, env, ctx, "if condition");
                // Each branch is lifted before the join, so a field taking `T?` can be given a value
                // on one side and `None` on the other and still have one type (spec §algebraic-types).
                Core then = liftIntoOption(elaborate(iff.then(), env, ctx, expected), expected,
                        ctx.symbols());
                Core els = liftIntoOption(elaborate(iff.els(), env, ctx, expected), expected,
                        ctx.symbols());
                Type tt = then.type();
                Type et = els.type();
                Type joined = TypeOps.join(tt, et);
                if (joined == null) {
                    joined = TypeOps.joinAt(expected, tt, et);
                }
                if (joined != null) {
                    yield new Core.If(cond, then, els,
                            new Core.ForkPlace(ctx.occurrenceOf(iff.origin()), ctx.within()),
                            joined, iff.pos());
                }
                throw CompileException.of(Diagnostic
                                .at(iff.pos(), 2)
                                .secondary(iff.then().reportedAt(),
                                        new TypeMessage.TheThenBranchProduces(Type.show(tt)))
                                .secondary(iff.els().reportedAt(),
                                        new TypeMessage.TheElseBranchProduces(Type.show(et)))
                                .hint(new TypeMessage.MakeBothBranchesProduceOneType())
                                .say(new TypeMessage.TheBranchesOfThisIfDisagree()).build());
            }
            case Hir.IfConstructed ic -> {
                Core built = elaborate(ic.construct(), env, ctx);
                if (!(built instanceof Core.Construct construct)) {
                    throw CompileException.of(Diagnostic.at(ic.construct().reportedAt())
                            .say(new AttemptMessage.ThisIsNotAConstruction())
                            .hint(new AttemptMessage.WriteTheConstructionWhoseInvariantDecides())
                            .build());
                }
                // What decides the branch is the invariant, so a type with none has no failing side
                // and the else value could never be reached. Reported rather than compiled into a
                // branch that is not one — the same call a unit data's forbidden invariant makes.
                if (!DataChecker.isInvariantBearing(construct.typeName(), ctx.symbols())) {
                    throw CompileException.of(Diagnostic.at(ic.construct().reportedAt())
                            .say(new AttemptMessage.TheTypeDeclaresNoInvariant(
                                    construct.typeName().name()))
                            .hint(new AttemptMessage.ConstructItDirectlyOrGiveItAnInvariant(
                                    construct.typeName().name()))
                            .build());
                }
                checkArmsAnswerClauses(ic, construct.typeName(), ctx.symbols());
                // The binder names the built value, so the success branch reads it at the data's own
                // type — with the invariant established, which is why the discharge check may seed it.
                Scope inner = env.with(ic.binder(), construct.type());
                Core then = liftIntoOption(elaborate(ic.then(), inner, ctx, expected), expected,
                        ctx.symbols());
                List<Core.ElseArm> arms = new ArrayList<>();
                Type joined = then.type();
                for (Hir.ElseArm arm : ic.els()) {
                    Core body = liftIntoOption(elaborate(arm.body(), env, ctx, expected), expected,
                            ctx.symbols());
                    arms.add(new Core.ElseArm(arm.clause(), body));
                    Type next = TypeOps.join(joined, body.type());
                    if (next == null) {
                        // An `else` answering per clause folds its arms, so what this arm conflicts
                        // with is the join of the branches before it and not the `then` branch's
                        // type — labelling `then` with it named a type the join was not refused at
                        // and left the arms between them out of the report altogether.
                        throw CompileException.of(Diagnostic.at(arm.body().reportedAt())
                                        .say(new TypeMessage.ThisBranchAndThePrecedingOnes(
                                                Type.show(body.type()), Type.show(joined)))
                                        .hint(new TypeMessage.MakeBothBranchesProduceOneType())
                                        .build());
                    }
                    joined = next;
                }
                yield new Core.IfConstructed(construct, CoreBinders.of(ic.binder()), then, arms,
                        new Core.ForkPlace(ctx.occurrenceOf(ic.origin()), ctx.within()), joined,
                        ic.pos());
            }
            case Hir.ListLit lit -> {
                if (lit.elements().isEmpty()) {
                    // `[]`: element type fixed by context (ADR-0028); adopt an expected list type
                    yield new Core.ListLit(List.of(),
                            expected instanceof Type.ListOf le ? le : Type.EMPTY_LIST, lit.pos());
                }
                List<Core> elements = new ArrayList<>();
                Type elem = null;
                // an expected list type reaches each element, so a list of functions says what its
                // elements take without every one of them being annotated
                Type want = expected instanceof Type.ListOf le ? le.element() : null;
                for (Hir.Expr el : lit.elements()) {
                    Core c = elaborate(el, env, ctx, want);
                    elements.add(c);
                    if (elem == null) {
                        elem = c.type();
                        continue;
                    }
                    Type joined = BottomInfer.unifyElem(elem, c.type());
                    if (joined == null) {
                        // The element is where the join was refused and is pointed at. What it
                        // conflicts with is `elem`, the join of the elements before it — one
                        // element's type only when one has been read, which is a fact about how
                        // long the list is rather than about where the type came from. It is said
                        // instead, so `[1, "x", 2]` and `[1, 2, "x"]` are reported the one way.
                        throw CompileException.of(Diagnostic.at(el.reportedAt())
                                .say(new TypeMessage.ThisElementAndThePrecedingOnes(
                                        Type.show(c.type()), Type.show(elem)))
                                .hint(new TypeMessage.MakeEveryElementTheSameType())
                                .build());
                    }
                    elem = joined;
                }
                yield new Core.ListLit(elements, Type.list(elem), lit.pos());
            }
            // A row's brackets, which say which collection they are only through the position they
            // stand at (spec §example-evaluable). Resolved into the form a body writes for that
            // collection and elaborated as that form, so a row and a body come to one value by one
            // reading — there is no second account of what a set or a map written this way is.
            //
            // What is read from the position is which collection the brackets are and nothing else.
            // Whether the value belongs at the position is a different question, and one an input is
            // held to while an expectation is not ({@link RowPosition}), so it is not asked here.
            case Hir.RowCollection row -> {
                // The same brackets the author wrote, read as the list they are: one collection of
                // the source and not a second one made here.
                Hir.Expr written = new Hir.ListLit(row.elements(), row.origin(), row.pos(),
                        row.region());
                Brackets brackets = contextualCollection(expected);
                // `[ ]` at a set or a map is the empty one, which is the value a body names rather
                // than a conversion of an empty list: what it holds has no element to say, and the
                // name takes it from the position as `Set.empty` does wherever it is written.
                boolean none = row.elements().isEmpty();
                yield switch (brackets) {
                    case SET -> elaborate(none ? empty("Set", row) : fromList("Set", written, row),
                            env, ctx, expected);
                    case MAP -> elaborate(none ? empty("Map", row) : fromList("Map", written, row),
                            env, ctx, expected);
                    case LIST -> elaborate(written, env, ctx, expected);
                };
            }
            case Hir.ListComp comp -> {
                // A comprehension never reaches the backend: the Lower stage rewrites it to an `if`
                // before a body is emitted, and the codec emitters desugar the expression they hold
                // before elaborating it. It still types here, on the pre-lowering paths (a helper's
                // standalone check), so the node produced is a type carrier, not something to emit.
                for (Hir.Expr g : comp.guards()) {
                    requireType(g, Type.BOOL, env, ctx, "guard of a comprehension");
                }
                Core element = elaborate(comp.element(), env, ctx);
                yield new Core.ListLit(List.of(element), Type.list(element.type()), comp.pos());
            }
        };
    }

    /** Which collection a row's brackets are, read off the type the position contributes. */
    private enum Brackets { LIST, SET, MAP }

    /**
     * The collection {@code contextual} says the brackets are.
     *
     * <p>A list where nothing says, which is what the brackets are in a body and so what a row's
     * are wherever the position adds nothing — inside a lambda, at a `let` with no written type,
     * or at an expectation of a type that is not a collection. An optional position is read through:
     * a `?` field holding a set is a set written where the field admits absence, and the notation
     * question is about the collection rather than about whether it may be missing.
     */
    private static Brackets contextualCollection(Type contextual) {
        return switch (contextual) {
            case Type.SetOf _ -> Brackets.SET;
            case Type.MapOf _ -> Brackets.MAP;
            case Type.OptionOf o -> contextualCollection(o.element());
            case null, default -> Brackets.LIST;
        };
    }

    /** {@code <collection>.fromList(written)} — the form a body writes for the collection a row
     *  wrote in brackets, applied where the row wrote them. */
    private static Hir.Expr fromList(String collection, Hir.Expr written, Hir.RowCollection row) {
        souther.compiler.types.ValueName.Stdlib.Operation fromList =
                souther.compiler.types.ValueName.Stdlib.operation(collection, "fromList");
        // The operation is this pass's and the collection is the author's, so which reference of it
        // this is comes from the brackets they wrote. A row writes one collection and the value it
        // stands for is one operation, so it is the first thing that collection derived — and the
        // empty one below is the same first thing, the two being the two ways one row is read.
        return Hir.Apply.synthetic(collection + ".fromList",
                new souther.compiler.types.ReachName.OfLibrary(fromList),
                new DerivedReferenceOrigin(
                        new ReferenceDerivationCause.CollectionLiteral(row.origin()), 0),
                new ApplicationOrigin.Derived(
                        new ApplicationDerivationCause.CollectionLiteral(row.origin()), 0),
                List.of(written), row.pos(), row.region());
    }

    /** {@code <collection>.empty} — the value a body names for the empty collection a row writes
     *  in brackets, standing where the row wrote them. */
    private static Hir.Expr empty(String collection, Hir.RowCollection row) {
        souther.compiler.types.ValueName.Stdlib.Operation empty =
                souther.compiler.types.ValueName.Stdlib.operation(collection, "empty");
        // No source wrote this reference: the author wrote an empty collection, and the operation
        // it stands for is this pass's. Which reference it is comes from the brackets they did
        // write — the first thing that collection derived, as the filled one is.
        return Hir.Var.respelled(collection + ".empty",
                new souther.compiler.types.ReachName.OfLibrary(empty),
                new DerivedReferenceOrigin(
                        new ReferenceDerivationCause.CollectionLiteral(row.origin()), 0),
                row.pos(), row.region());
    }

    /** Elaborates {@code e} and checks it against {@code expected}, returning its Core. The check is
     * bottom-up, as {@link #requireType} is: the expected type is not pushed into the expression. */
    static Core requireTyped(Hir.Expr e, Type expected, Scope env, CheckContext ctx, String what) {
        Core c = elaborate(e, env, ctx);
        requireType(e, c.type(), expected, ctx.symbols(), what);
        return c;
    }


    static Core elaborateFieldAccess(Hir.FieldAccess fa, Scope env, CheckContext ctx) {
        Core targetCore = elaborate(fa.target(), env, ctx);
        Type target = targetCore.type();
        // What a `.` may name here, asked of the one reader of it: a record's own fields, a name
        // every case of a sum spreads, and a newtype's `value` and nothing of what it wraps. Which
        // of those this position is, and how far the names it wears come off, are answered there —
        // an elaboration deciding either for itself is a second reading of a field access, and the
        // reading a text is typed by and the reading an editor is told would then be two.
        Type read = fieldRead(ctx).of(target, fa.field());
        if (read != null) {
            return new Core.FieldAccess(targetCore, fa.field(), read, fa.pos());
        }
        // Nothing is readable, and what is left here is saying so. Asked of a sum only: what a type
        // is made of answers for anything — a data that is no sum is the one atom it is — and the
        // question is whether there are cases the author could open, which a type that is its own
        // single leaf has not.
        if (TypeOps.isSumType(target, ctx.symbols())) {
            List<TypeSymbol> cases = AtomSpace.subjectAtoms(target, ctx.symbols());
            // A sum carries no fields of its own — its cases do, and which case it is is not known
            // until it is opened. Saying that is the difference between "this value has no such
            // field" and "read it in each case", which is what the author has to write.
            List<String> without = new ArrayList<>();
            for (TypeSymbol c : cases) {
                if (!(ctx.symbols().declaredNode(c) instanceof Hir.Data cd)
                        || !TypeOps.hasField(cd, fa.field(), ctx.symbols())) {
                    without.add(c.name());
                }
            }
            Diagnostic.Builder d = Diagnostic
                    .at(fa.name().reportedAt());
            if (!without.isEmpty()) {
                d = d.hint(new ModuleMessage.TheseCasesHaveNoSuchField(fa.field(), String.join(", ", without)));
            } else if (target instanceof Type.Ref) {
                // Every case has the field and the read still fails, so what is missing is the shared
                // spread. Without saying so the author reads "a sum has no fields" while looking at
                // the field in every case.
                d = d.hint(new ModuleMessage.EveryCaseDeclaresItsOwn(fa.field()));
            }
            throw CompileException.of(d.say(new ModuleMessage.CannotReadAFieldOnASum(fa.field(), Type.show(target))).build());
        }
        throw CompileException.of(Diagnostic
                        .at(fa.name().reportedAt()).say(new DeclarationMessage.CannotReadAFieldOnThisValue(fa.field())).build());
    }

    /** What a {@code .} may name, in the world an elaboration reads: the declarations as this text
     *  has resolved them, which is what a check settling them has to go on — and refusing where one
     *  of them does not read, which a check is what reports. */
    private static FieldRead fieldRead(CheckContext ctx) {
        return new FieldRead(ctx.symbols(), new ResolvedFieldTypes(ctx.symbols()),
                FieldRead.Unreadable.REFUSED);
    }

    /**
     * Resolves the accumulator type for one function argument (a fold's step) of a helper call,
     * updating {@code bind}. The step is first typed at the accumulator the value arguments fixed —
     * the seed's type, which may be a narrow case. That type stands when the step is a fixpoint there
     * (it reads the seed's fields and returns the same case). Only when the narrow case does not type
     * (the step matches on the accumulator, which needs its sum) or is not a fixpoint (the step grows
     * the accumulator into its sum) is the accumulator widened to the sum that case belongs to, and the
     * step re-typed there. An empty-collection seed's bottom is refined from the block's result along
     * the way. Shared by the checker's call typing and the backend's step materialization, so the two
     * resolve identically.
     */
    public static Core resolveStepBinding(String fnName, Type.FnOf declaredStep, Hir.Expr stepArg,
                                          Map<String, Type> bind, Scope env, CheckContext ctx) {
        Type.FnOf narrow = (Type.FnOf) TypeOps.substitute(declaredStep, bind);
        Core narrowCore = null;
        Type narrowGot = null;
        CompileException narrowFailed = null;
        try {
            narrowCore = elaborateBlockArg(fnName, stepArg, narrow.params(), env, ctx);
            narrowGot = ((Type.FnOf) narrowCore.type()).result();
        } catch (CompileException e) {
            narrowFailed = e;
        }
        if (narrowGot != null) {
            BottomInfer.refineBottom(declaredStep.result(), narrowGot, bind);
            Type want = TypeOps.substitute(declaredStep.result(), bind);
            if (want instanceof Type.Var || TypeOps.assignable(narrowGot, want, ctx.symbols())) {
                return narrowCore;   // the narrow accumulator is a fixpoint
            }
        }
        // The step matches on, or grows the accumulator into, the sum the seed's case belongs to.
        if (declaredStep.result() instanceof Type.Var accVar) {
            Type sum = TypeOps.enclosingSum(TypeOps.substitute(declaredStep.result(), bind), ctx.symbols());
            if (sum != null) {
                Map<String, Type> widened = new HashMap<>(bind);
                widened.put(accVar.name(), sum);
                Core widenedCore = elaborateBlockArg(fnName, stepArg,
                        ((Type.FnOf) TypeOps.substitute(declaredStep, widened)).params(), env, ctx);
                Type got = ((Type.FnOf) widenedCore.type()).result();
                if (TypeOps.assignable(got, sum, ctx.symbols())) {
                    bind.put(accVar.name(), sum);
                    return widenedCore;   // the step is emitted at the widened accumulator
                }
            }
        }
        if (narrowGot == null) {
            throw narrowFailed;   // the narrow type errored and there was no sum to fall back to
        }
        throw CompileException.of(Diagnostic.at(answerRegion(stepArg))
                .say(new HelperMessage.TheStepAnswersAnotherTypeThanTheAccumulator(fnName,
                        Type.show(narrowGot),
                        Type.show(TypeOps.substitute(declaredStep.result(), bind))))
                .build());
    }

    /**
     * Types a block argument, binding its parameters to {@code paramTypes} (spec §blocks). The node
     * it answers with carries the {@link Type.FnOf} of the block — the parameter types the call
     * fixed, and the body's result.
     *
     * <p>The parameters are visible only inside the block's body, and its requirement set is
     * whatever it calls — which flows outward into the enclosing behavior's, so nothing about
     * requirements has to be written down (spec §requirement-propagation).
     */
    static Core elaborateBlockArg(String fnName, Hir.Expr arg, List<Type> paramTypes,
                                  Scope env, CheckContext ctx) {
        if (!(arg instanceof Hir.Block block)) {
            // a function-typed value — a helper's function parameter (spec §fn-declaration) —
            // stands in for a block: check its shape and yield its result type.
            Core value = elaborate(arg, env, ctx);
            if (value.type() instanceof Type.FnOf fn) {
                if (fn.params().size() != paramTypes.size()) {
                    throw CompileException.of(Diagnostic.at(arg.pos())
                            .say(new HelperMessage.TheFunctionTakesAnotherNumberOfArguments(fnName,
                                    String.valueOf(paramTypes.size()),
                                    String.valueOf(fn.params().size())))
                            .build());
                }
                for (int i = 0; i < paramTypes.size(); i++) {
                    if (!TypeOps.assignable(paramTypes.get(i), fn.params().get(i), ctx.symbols())) {
                        throw CompileException.of(Diagnostic.at(arg.pos())
                                .say(new HelperMessage.TheFunctionTakesAnotherType(fnName,
                                        Type.show(paramTypes.get(i)),
                                        Type.show(fn.params().get(i))))
                                .build());
                    }
                }
                return value;
            }
            throw CompileException.of(Diagnostic.say(new HelperMessage.ThisExpectsABlock(fnName)).at(arg.pos()).build());
        }
        if (block.params().size() != paramTypes.size()) {
            throw CompileException.of(Diagnostic.at(block.pos())
                    .say(new HelperMessage.TheBlockIsWrittenWithAnotherNumberOfParameters(
                            String.valueOf(paramTypes.size()),
                            String.valueOf(block.params().size())))
                    .build());
        }
        Scope inner = env;
        for (int i = 0; i < paramTypes.size(); i++) {
            inner = inner.with(block.params().get(i), paramTypes.get(i));
        }
        Core body = elaborate(block.body(), inner, ctx);
        return new Core.Block(CoreBinders.all(block.params()), body, Type.fn(paramTypes, body.type()),
                block.pos());
    }

    /** Whether an expression bound to a {@code let} is a function value: a lambda, or an {@code if}
     * whose branches are functions. Such a value cannot be inlined (the inliner leaves it), so it
     * becomes a first-class {@code Fn} (spec §blocks). */
    public static boolean producesFunction(Hir.Expr e) {
        return switch (e) {
            case Hir.Block _ -> true;
            case Hir.If iff -> producesFunction(iff.then()) || producesFunction(iff.els());
            // a lambda returned under its capture bindings, e.g. inlining `adder(5)` leaves
            // `let $n = 5 in (x) -> x + $n` (spec §blocks)
            case Hir.LetIn li -> producesFunction(li.body());
            case Hir.Expansion ex -> producesFunction(ex.body());
            default -> false;
        };
    }

    /**
     * A constructor pattern outside a {@code match} — {@code let Tags(xs) = t}, a parameter written
     * as {@code (Tags(xs))}. Two things have to hold, and neither has anything standing behind it
     * here: a {@code match} arm's name is one of the scrutinee's cases, which the exhaustiveness
     * pass has already established, while a binding names a type on its own authority. So the name
     * MUST be a newtype — a product has fields to read by name and a sum has arms, neither of which
     * a binding opens — and the value MUST be of that very type, or the pattern claims a shape the
     * value does not have and {@code .value} would be read off the wrong nominal type.
     */
    private static void checkOpens(Hir.LetIn li, Type valueType, Symbols symbols) {
        String opened = li.opens().written();
        // A name nothing declares was reported where it is written, and what is left here is not a
        // question about it: what the binding opens has no type, so the body under it would be
        // checked against a shape nothing states. Abandoned as a name standing anywhere else in a
        // body is, rather than passed on as an absent type for the reading below to take for one.
        if (!(li.opens() instanceof Hir.Name.Denoting opens)) {
            throw new Unanswerable(li.opens().pos());
        }
        TypeSymbol layer = opens.type();
        if (TypeOps.newtypeInner(layer, symbols) == null) {
            throw CompileException.of(Diagnostic
                            .at(li.pos())
                            .hint(new TypeMessage.ABindingOpensOnlyWhatEveryValueHas()).say(new TypeMessage.NotANewtypeToOpenInABinding(opened)).build());
        }
        // compared as types, not as the text of a name: a type is reachable through the module that
        // declares it, so `probe.a.Tags` and an imported bare `Tags` are the one type
        if (!(valueType instanceof Type.Ref r) || !r.name().equals(layer)) {
            String shown = layer.name();
            String actual = Type.show(valueType);
            throw CompileException.of(Diagnostic
                            .at(li.pos())
                            .diff(actual, shown).say(new TypeMessage.ThePatternOpensAnotherType(shown, actual)).build());
        }
    }

    /** The type a source annotation declares on a binding ({@code let x: T = e}), or null when the
     * binding carries none. Read wherever a binding's type is needed, so the annotation and the
     * inference, the checker and the backend cannot drift apart. */
    static Type annotatedType(Hir.LetIn li, Symbols symbols) {
        return li.annotation() == null ? null : TypeOps.successType(li.annotation());
    }

    /**
     * A local binding's written type must be the type of its value (spec §let). A declared type that
     * the value does not have is an error rather than a comment the checker ignores — the same rule a
     * helper's declared return type follows.
     */
    static void checkLetAnnotation(Hir.LetIn li, Type declared, Type valueType,
                                           Symbols symbols) {
        if (TypeOps.assignable(valueType, declared, symbols)) {
            return;
        }
        throw CompileException.of(Diagnostic
                        .at(li.pos())
                        .diff(Type.show(valueType, declared), Type.show(declared, valueType)).say(new NameMessage.TheBindingDeclaresAnotherType(li.name(), Type.show(declared), Type.show(valueType))).build());
    }

    /**
     * The type to bind an un-annotated binding at. A binding carrying an inlined helper's declared
     * parameter type keeps that type when it is a sum: a case argument widens to its sum (spec §sum-data),
     * so a {@code match} in the body still sees the sum rather than the argument's specific case.
     * Other declared types (a type variable in a generic prelude helper, a record, a list) are left to
     * the argument's own type, which monomorphisation and the call-site check already handle.
     */
    static Type carriedType(Hir.LetIn li, Type valueType, Symbols symbols) {
        return carriedType(li.declaredType(), valueType, symbols);
    }

    static Type carriedType(Hir.RetType declared, Type valueType, Symbols symbols) {
        return declared == null ? valueType
                : carriedType(TypeOps.resolveParamType(declared), valueType, symbols);
    }

    private static Type carriedType(Type declared, Type valueType, Symbols symbols) {
        if (Type.mentions(declared, x -> x instanceof Type.MetaVar)) {
            return valueType;   // it stands for what this application decides, and is not a sum
        }
        if (TypeOps.isSumType(declared, symbols) && TypeOps.assignable(valueType, declared, symbols)) {
            return declared;
        }
        return valueType;
    }

    /**
     * One application of a helper, typed as one thing.
     *
     * <p>The callee's signature arrives instantiated into this application's own variables
     * ({@link Type.MetaVar}), over its parameters and its result together. Reading the arguments
     * against the parameters decides them, and the result is read with what they decided already
     * written in — which is how a declaration that says only that its result holds what its argument
     * held ({@code (xs: List<'a>) : List<'a>}) reaches a body that says nothing about what it
     * answers.
     *
     * <p>The order is not an accident. Once the arguments have been read, a variable still open
     * cannot appear in any parameter that became a binding — if it did, that binding's value would
     * have decided it — so nothing in scope holds a type this could still change, and the
     * substitution stays inside this call. What remains open is in the result or in a function
     * parameter, and neither is in scope as a value.
     *
     * <p>What it produces is the bindings it always produced. Grouping them is a statement about
     * typing, not about what runs.
     */
    private static Core expansion(Hir.Expansion ex, Scope env, CheckContext ctx, Type expected) {
        Applied applied = arguments(ex, env, ctx);
        Substitution decided = applied.decided();
        List<Core> values = applied.values();
        Scope inner = applied.inner();
        Type declaredResult = declaredResult(ex);
        // The declaration is what an empty collection inside the body has to go on: at a call site
        // that expects nothing concrete, nothing else says what it holds. Pushed in only once this
        // application has settled it — before that it names variables, and a variable states no type.
        Type want = declaredResult != null && !decided.open(declaredResult)
                ? decided.zonk(declaredResult) : expected;
        // Everything the copy holds stands in this expansion, which is what a fork inside it needs
        // in order to say which copy it is — the call site is here and nowhere below.
        Core body = elaborate(ex.body(), inner.deciding(decided), ctx.inside(ex.application(), ex.callee(), ex.at()), want);
        Type type = body.type();
        if (declaredResult != null) {
            // What the body answers decides a variable the arguments left open — a result the
            // signature relates to a function parameter rather than to a value one.
            decide(decided, declaredResult, ex.body(), body.type(), ctx.symbols(),
                    "the result of `" + shown(ex) + "`");
            if (ex.callee() instanceof ValueName.Local) {
                // A function the caller supplied, expanded where the callee applies it. What it was
                // declared to answer is what the caller was held to when it handed the function
                // over, so a body answering something else is the caller's error and is reported
                // here — a helper's own declared return is its promise, and is reported against it.
                hold(decided, declaredResult, ex.body(), body.type(), ctx.symbols(),
                        "what `" + shown(ex) + "` was given to answer");
            }
            type = decided.settle(declaredResult);
            if (!TypeOps.assignable(body.type(), type, ctx.symbols())) {
                type = body.type();   // the declaration is wrong, and is reported against the helper
            }
        }
        if (Type.mentions(type, x -> x instanceof Type.MetaVar)) {
            // The expansion is where its variables live and die. One leaving here would be a type
            // nothing below can read: neither what may be assigned to what, nor what to emit for it,
            // is a question about a variable one call left open.
            throw new IllegalStateException(
                    "an expansion answered with a type it had not decided: " + Type.show(type));
        }
        // wrapped innermost-first, so the value parameters bind in declared order
        Core out = body;
        for (int i = ex.bound().size() - 1; i >= 0; i--) {
            out = new Core.LetIn(CoreBinders.of(ex.bound().get(i).binder()), values.get(i), out, type,
                    ex.pos());
        }
        return out;
    }

    /** What reading this application's arguments decided, and the scope its body is read in. */
    private record Applied(Substitution decided, List<Core> values, Scope inner) {}

    /**
     * The arguments of one application, read against the signature it instantiated.
     *
     * <p>The one place an application's arguments are typed. What the callee answers — a value, or a
     * function the caller binds and applies — decides what is done with the body afterwards and
     * nothing about the arguments, so the two readers of an expansion come through here rather than
     * each doing this again. That a helper answers a function is not a reason for its own parameters
     * to go unchecked.
     */
    private static Applied arguments(Hir.Expansion ex, Scope env, CheckContext ctx) {
        Substitution decided = new Substitution(ex.application(), env.decisions());
        List<Core> values = new ArrayList<>();
        Scope inner = env;
        for (Hir.Bound b : ex.bound()) {
            // A lambda bound rather than applied needs to be told what it takes — it is a block, and a
            // block is a value only where something says its parameter types. The declaration this
            // binding came from says them, and nothing else here does.
            Type says = b.value() instanceof Hir.Block && b.declaredType() != null
                    ? TypeOps.resolveParamType(b.declaredType()) : null;
            Core value = elaborate(b.value(), env, ctx, says instanceof Type.FnOf ? says : null);
            Type bindType = value.type();
            if (b.declaredType() != null) {
                Type declared = TypeOps.resolveParamType(b.declaredType());
                // The argument is held to everything the declaration states, whether or not the
                // callee's body ever reads it, and however many variables stand inside it.
                constrain(decided, declared, b.value(), value.type(), ctx.symbols(),
                        argument(ex, b.binder().name()));
                Type required = decided.zonk(declared);
                if (states(required)) {
                    bindType = carriedType(required, value.type(), ctx.symbols());
                }
            }
            values.add(value);
            inner = inner.with(b.binder(), bindType);
        }
        givenFunctions(ex, decided, env, ctx);
        return new Applied(decided, values, inner);
    }

    /**
     * The functions this call was given, held to what the callee declared of them.
     *
     * <p>Only the ones the callee never applies. Where it applies one, that application is an
     * expansion like any other and carries what the callee declared of the parameter, so the
     * function is read there — in the one place the types this application decided are in force.
     * Where it never does, there is no such place, and this is the only reader that holds the
     * signature and the argument at once.
     *
     * <p>The whole function type is read, parameters and result together. A signature says something
     * at each position — {@code (f: ('a) -> Bool, x: 'a)} relates the argument to what {@code f}
     * takes, {@code (f: (Int) -> 'a, x: 'a)} to what it answers — and reading one position would
     * carry one of them and drop the other.
     */
    private static void givenFunctions(Hir.Expansion ex, Substitution decided, Scope env,
                                       CheckContext ctx) {
        for (Hir.Given g : ex.given()) {
            if (g.declaredType() == null
                    || !(TypeOps.resolveParamType(g.declaredType())
                            instanceof Type.FnOf declared)) {
                continue;
            }
            Type arrives = arrivesAs(g, ex, env);
            if (arrives != null) {
                // Both sides are one statement each, so both are read as one. Each was instantiated
                // once — the receiving declaration when this call was expanded, the arriving one
                // here — and reading them against each other decides those variables together: a
                // variable written at two positions is one variable, and the two readings of it must
                // agree. `('a) -> 'a` given `(Int) -> String` is refused for that reason and no
                // other, because at each position on its own there is nothing wrong.
                // What this reading settles is this application's, like every other. A signature
                // relates a function parameter to the rest of what it wrote — `(f: ('a) -> Bool):
                // List<'a>` answers a list of what the function takes — so what the function says
                // about a variable is what that variable is at every other position of the call.
                constrain(decided, declared, g.value(), arrives, ctx.symbols(),
                        argument(ex, "a function"));
                continue;
            }
            if (g.applied() || !inSight(g.value(), env)) {
                continue;   // a lambda the callee applies is read at that application
            }
            List<Type> takes = takes(declared, decided, g.value(), env, ctx);
            if (takes == null) {
                continue;   // nothing says what it takes, and its own body does not either
            }
            constrain(decided, declared, g.value(),
                    elaborateFunctionValue(g.value(), takes, env, ctx).type(), ctx.symbols(),
                    argument(ex, "a function"));
        }
    }

    /**
     * What the function this call was given is declared as, or null where nothing declares it.
     *
     * <p>Asked rather than worked out. A function reaching a parameter under a name is declared
     * where it is bound — a helper's own parameter, a binding, a function an enclosing call
     * supplied — and reading that declaration is what a boundary does. Only a lambda written at the
     * call has no declaration of its own, and it is read at the application that decides it.
     */
    private static Type arrivesAs(Hir.Given g, Hir.Expansion ex, Scope env) {
        Type is = g.arrivesAs() != null ? TypeOps.resolveParamType(g.arrivesAs())
                : g.value() instanceof Hir.Var.Denoting v
                        && env.of(v.denotes(), v.reaches()) instanceof Type.FnOf fn ? fn : null;
        return is == null ? null : instantiated(is, ex);
    }

    /**
     * {@code declared} with the variables it left open standing at this boundary's own.
     *
     * <p>One per variable, not one per position: {@code ('a) -> 'a} is a function answering what it
     * was given, and instantiating each occurrence separately would say only that it takes something
     * and answers something.
     *
     * <p>And one per scope, which within one expansion is one. The declarations that arrive under a
     * variable are the enclosing definition's — a helper handing its own function parameters on
     * writes them in one signature, where one spelling is one variable — so two of them sharing a
     * spelling share a variable, and instantiating each boundary separately would lose that. What
     * arrives from anywhere else arrives already instantiated, carrying the variables of the call
     * that supplied it, and has no spelling left to share.
     *
     * <p>It is instantiated here because the declaration is another's — the receiving side was
     * instantiated when this call was expanded — and the two are read against each other once both
     * stand at variables this application decides.
     */
    private static Type instantiated(Type declared, Hir.Expansion ex) {
        Map<String, Type> theirs = new HashMap<>();
        Type.mentions(declared, t -> {
            if (t instanceof Type.Var v) {
                theirs.computeIfAbsent(v.name(),
                        name -> new Type.MetaVar(ex.application(), "given " + name));
            }
            return false;   // a collector, not a test: every position is visited
        });
        return theirs.isEmpty() ? declared : TypeOps.substituteVars(declared, theirs);
    }

    /**
     * Whether a declared type says what a value at that position is. A variable does not, whichever
     * kind it is: one this application has not decided stands for what it settles on, and one the
     * declaration wrote stands for whatever each use makes it. Holding a value to either would be
     * comparing it against something that is not a type.
     */
    private static boolean states(Type t) {
        return !Type.mentions(t, x -> x instanceof Type.Open);
    }

    /**
     * Whether a declared type says what a value at that position is <em>and</em> is done saying it.
     * A type carrying the empty-collection bottom is an answer so far: it says what the value is
     * made of and not what it holds, and a later reading widens it (ADR-0028).
     */
    private static boolean settled(Type t) {
        return states(t) && !Type.mentions(t, x -> x instanceof Type.Nothing);
    }

    /**
     * Whether a function argument is something this scope can type at all.
     *
     * <p>A name given to a function parameter is not always a value: where a helper hands its own
     * function parameter on to another, the name stands for a block the inliner is holding and
     * answers by substituting it into the body. Nothing binds it, so there is nothing here to read
     * it as, and what it is declared as is carried on the boundary instead.
     */
    private static boolean inSight(Hir.Expr function, Scope env) {
        if (function instanceof Hir.Var.Denoting v
                && v.denotes() instanceof ValueName.Local local) {
            return env.holds(local.id());
        }
        return reads(function, env);
    }

    /**
     * Whether every name inside {@code e} is one this scope can answer. A recursive helper is
     * reached by its declaration rather than by a binding, and which declarations a scope reaches
     * depends on where it was built — so a function argument whose body calls one may be readable
     * where it is applied and not here. Where it is not, it is left to the body that applies it.
     */
    private static boolean reads(Hir.Expr e, Scope env) {
        if (e instanceof Hir.Apply call && call.function() instanceof Hir.Var.Denoting callee
                && callee.denotes() instanceof ValueName.Helper
                && env.of(callee.denotes(), callee.reaches()) == null) {
            return false;
        }
        boolean[] all = {true};
        Hir.forEachChild(e, child -> all[0] &= reads(child, env));
        return all[0];
    }

    /**
     * What to read a function this call was given at: what the signature settled, and for a position
     * it left open, what the lambda's own body says.
     *
     * <p>Reading it off the body is right here and nowhere else. This is a function the callee never
     * applies, so no application of it will ever settle what it takes — where one would, that
     * application is the reader and this is not asked at all. Null where a position is open and the
     * body does not say what stands there either, which is a function nothing in the program
     * describes.
     */
    private static List<Type> takes(Type.FnOf declared, Substitution decided, Hir.Expr function,
                                    Scope env, CheckContext ctx) {
        Type.FnOf want = decided.zonk(declared) instanceof Type.FnOf w ? w : declared;
        List<Type> takes = new ArrayList<>();
        for (int i = 0; i < want.params().size(); i++) {
            Type stated = want.params().get(i);
            if (settled(stated)) {
                takes.add(stated);
                continue;
            }
            if (!(function instanceof Hir.Block lambda) || i >= lambda.params().size()) {
                return null;
            }
            Type read = HelperParams.readFromBody(lambda.params().get(i), lambda.body(), env, ctx,
                    settled(want.result()) ? want.result() : null);
            if (read == null || !settled(read)) {
                return null;
            }
            takes.add(read);
        }
        return takes;
    }

    private static String argument(Hir.Expansion ex, String name) {
        return "`" + name + "` of `" + shown(ex) + "`";
    }

    /** The one type the callee's declaration gives its result, or null where it declared none or
     * declared a union — a union names one type where the body may answer several, so there is
     * nothing single to hold the body to. */
    private static Type declaredResult(Hir.Expansion ex) {
        return ex.declaredReturn() == null || ex.declaredReturn().cases().size() != 1 ? null
                : TypeOps.resolveParamType(ex.declaredReturn());
    }

    /** The callee as the caller wrote it, for a message about the call. A function the caller
     * supplied is bound under the parameter it was given to, marked as the inliner's own; the mark
     * says nothing to whoever wrote the call, so it is not shown. */
    private static String shown(Hir.Expansion ex) {
        if (ex.callee() == null) {
            return "a helper";
        }
        String name = ex.callee().name();
        return name.startsWith("$") ? name.substring(1) : name;
    }

    /** {@code let f: T = <function>} where {@code T} is not a function type. No ordinary type describes
     * a function, so the annotation settles nothing. Raised from the surface check below and from the
     * value path, so the two shapes a function binding takes (a bare lambda, one an {@code if} chooses)
     * read the same. */
    static CompileException functionAnnotation(Hir.LetIn li) {
        return CompileException.of(Diagnostic.at(li.pos())
                .say(new HelperMessage.AnAnnotationOnAFunctionBindingIsNotAFunctionType(li.name()))
                .hint(new HelperMessage.WriteAFunctionTypeOrLeaveTheAnnotationOff())
                .build());
    }

    /**
     * Checks an annotation on a lambda binding, read on the surface body: lowering expands such a
     * binding away at its applications, so by the time the body is checked the annotation is gone.
     * What it states is still held against the lambda here — a function type, of the arity the lambda
     * binds. An ordinary type does not describe a lambda at all.
     */
    static void checkAnnotatedLambdaBindings(Hir.Expr e, Symbols symbols) {
        if (e instanceof Hir.LetIn li && li.annotation() != null
                && li.value() instanceof Hir.Block lambda) {
            Hir.FnType declared = li.annotation().asFn();
            if (declared == null) {
                throw functionAnnotation(li);
            }
            if (declared.params().size() != lambda.params().size()) {
                throw CompileException.of(Diagnostic.at(lambda.pos())
                        .say(new HelperMessage.TheLambdaIsAppliedWithAnotherNumberOfArguments(
                                String.valueOf(lambda.params().size()),
                                String.valueOf(declared.params().size())))
                        .build());
            }
        }
        TypeChecker.forEachChild(e, sub -> checkAnnotatedLambdaBindings(sub, symbols));
    }

    /** A value that is a function, whatever it was written as. One applied where it is bound is
     * expanded there and never reaches this; what does is one that escaped, and it becomes a
     * first-class {@code Fn} at the parameter types the context or its applications give. */
    public static boolean isFunctionSelection(Hir.Expr e) {
        return producesFunction(e);
    }


    /** Infers a let-bound function's parameter types from how the body applies it: every
     * {@code f(args)} in the body must agree on the argument types (spec §blocks). A function that
     * is never applied cannot have its type inferred. */
    static List<Type> inferFnParamTypes(Hir.Binder binder, Hir.Expr body, Scope env,
                                                CheckContext ctx) {
        List<List<Type>> uses = new ArrayList<>();
        collectApplications(binder, body, env, ctx, uses, Set.of());
        if (uses.isEmpty()) {
            throw CompileException.of(Diagnostic.at(body.pos())
                    .say(new HelperMessage.TheFunctionsTypeCannotBeRead(binder.name()))
                    .build());
        }
        List<Type> first = uses.get(0);
        for (List<Type> u : uses) {
            if (!u.equals(first)) {
                throw CompileException.of(Diagnostic.at(body.pos())
                        .say(new HelperMessage.TheFunctionIsAppliedAtTwoTypes(binder.name(),
                                first.toString(), u.toString()))
                        .build());
            }
        }
        return first;
    }

    /**
     * Collects the argument-type lists of every application {@code name(args)} in {@code e} that this
     * scope can read.
     *
     * <p>{@code inner} names what a binder below this point has introduced — a lambda's parameters, a
     * {@code let}, a {@code match} arm's binding. An application whose arguments reach one of those is
     * not in this scope and says nothing about the parameter types here, which is exactly where a
     * combinator's own expansion puts the application. It is skipped rather than typed: typing it
     * would report a name that is bound, only not here, and the report would name what the expansion
     * renamed it to.
     *
     * <p>Every other application is typed against {@code env}, so a mistake inside its arguments is
     * still reported as the mistake it is.
     */
    static void collectApplications(Hir.Binder binder, Hir.Expr e, Scope env,
                                            CheckContext ctx, List<List<Type>> out,
                                            Set<BindingId> inner) {
        // Handed to something that declares what it takes. A call a representation keeps standing
        // holds the application inside itself, so a function passed to one is applied nowhere this
        // walk can see — but the declaration says the parameter types as plainly as an application
        // would, and it says them at the position the function was given to.
        if (e instanceof Hir.Apply call) {
            handedOver(binder, call, env, ctx, out);
        }
        if (e instanceof Hir.Apply call && call.answered() != null
                && call.answered().denotes() instanceof ValueName.Local applied
                && applied.id().equals(binder.id())
                && call.args().stream().noneMatch(a -> reaches(a, inner))) {
            List<Type> argTypes = new ArrayList<>();
            for (Hir.Expr a : call.args()) {
                argTypes.add(typeOf(a, env, ctx));
            }
            out.add(argTypes);
        }
        switch (e) {
            case Hir.Block b -> collectApplications(binder, b.body(), env, ctx, out,
                    with(inner, b.params()));
            case Hir.LetIn li -> {
                collectApplications(binder, li.value(), env, ctx, out, inner);
                collectApplications(binder, li.body(), env, ctx, out, with(inner, List.of(li.binder())));
                // A name given to this function is this function: what the second name is used for is
                // what the first one is used for. Followed rather than left to the applications alone,
                // because an alias may be the only thing that is ever applied or handed over.
                if (li.value() instanceof Hir.Var.Denoting v
                        && v.denotes() instanceof ValueName.Local local
                        && local.id().equals(binder.id())) {
                    collectApplications(li.binder(), li.body(), env, ctx, out, inner);
                }
            }
            case Hir.IfConstructed ic -> {
                collectApplications(binder, ic.construct(), env, ctx, out, inner);
                collectApplications(binder, ic.then(), env, ctx, out,
                        with(inner, List.of(ic.binder())));
                for (Hir.ElseArm arm : ic.els()) {
                    collectApplications(binder, arm.body(), env, ctx, out, inner);
                }
            }
            case Hir.Match m -> {
                collectApplications(binder, m.scrutinee(), env, ctx, out, inner);
                for (Hir.Case c : m.cases()) {
                    collectApplications(binder, c.body(), env, ctx, out,
                            c.binding() == null ? inner : with(inner, List.of(c.binding())));
                }
            }
            default -> TypeChecker.forEachChild(e,
                    sub -> collectApplications(binder, sub, env, ctx, out, inner));
        }
    }

    /**
     * What {@code call} says the parameter types are, where {@code binder} is the function it was
     * given at a position declared to take one.
     *
     * <p>The declaration is polymorphic, so what it says depends on the call: {@code List.filter}
     * takes {@code ('a) -> Bool} and the list beside it decides {@code 'a}. The other arguments are
     * read first for that reason, and one that cannot be read yet leaves this call saying nothing —
     * another use, or the author's annotation, answers instead.
     */
    private static void handedOver(Hir.Binder binder, Hir.Apply call, Scope env, CheckContext ctx,
                                   List<List<Type>> out) {
        CompleteSignature kept = call.answered() == null
                ? null : ctx.preserved().signatureOf(call.answered().denotes());
        if (kept == null || kept.params().size() != call.args().size()) {
            return;
        }
        for (int i = 0; i < call.args().size(); i++) {
            if (!(call.args().get(i) instanceof Hir.Var.Denoting v)
                    || !(v.denotes() instanceof ValueName.Local local)
                    || !local.id().equals(binder.id())
                    || !(kept.params().get(i) instanceof Type.FnOf declared)) {
                continue;
            }
            Map<String, Type> bind;
            try {
                // The same settling the call itself does — this walk is reading that call's own
                // question one position early, and an answer that differed from the one the call
                // reaches would be a parameter type nothing later agrees with.
                bind = CallElaborator.settledByValues(call, kept.params(), kept.result(), null,
                        j -> typeOf(call.args().get(j), env, ctx), ctx);
            } catch (CompileException _) {
                return;   // this call decides nothing here; what is wrong with it is reported there
            }
            Type.FnOf settled = (Type.FnOf) TypeOps.substitute(declared, bind);
            if (settled.params().stream().noneMatch(p -> Type.mentions(p, t -> t instanceof Type.Var))) {
                out.add(settled.params());
            }
        }
    }

    /** {@code bindings} with what {@code added} introduces. */
    private static Set<BindingId> with(Set<BindingId> bindings, List<Hir.Binder> added) {
        if (added.isEmpty()) {
            return bindings;
        }
        Set<BindingId> out = new HashSet<>(bindings);
        added.forEach(binder -> out.add(binder.id()));
        return out;
    }

    /**
     * Whether {@code e} reads a binding a binder below the inference point introduced. A binder
     * inside {@code e} introduces another binding, so nothing has to be taken off the set for what it
     * covers: how either happens to be spelled decides nothing.
     */
    private static boolean reaches(Hir.Expr e, Set<BindingId> inner) {
        if (inner.isEmpty()) {
            return false;
        }
        ValueName denotes = switch (e) {
            case Hir.Var.Denoting v -> v.denotes();
            case Hir.Apply c when c.answered() != null -> c.answered().denotes();
            default -> null;
        };
        if (denotes instanceof ValueName.Local local && inner.contains(local.id())) {
            return true;
        }
        return switch (e) {
            case Hir.Block b -> reaches(b.body(), inner);
            case Hir.LetIn li -> reaches(li.value(), inner) || reaches(li.body(), inner);
            case Hir.IfConstructed ic -> reaches(ic.construct(), inner)
                    || reaches(ic.then(), inner)
                    || ic.els().stream().anyMatch(arm -> reaches(arm.body(), inner));
            case Hir.Match m -> {
                if (reaches(m.scrutinee(), inner)) {
                    yield true;
                }
                for (Hir.Case c : m.cases()) {
                    if (reaches(c.body(), inner)) {
                        yield true;
                    }
                }
                yield false;
            }
            default -> {
                boolean[] found = {false};
                TypeChecker.forEachChild(e, sub -> found[0] |= reaches(sub, inner));
                yield found[0];
            }
        };
    }



    /**
     * Types a function value against inferred parameter types: a lambda binds its parameters and
     * yields {@code FnOf(params, resultOfBody)}; an {@code if} requires both branches to be the same
     * function type (spec §blocks).
     *
     * <p>A function's body is not the value a {@code ?} field is being given, whatever encloses the
     * function, so it does not make an optional (ADR-0011): a step handed to {@code List.filterMap}
     * answers an optional it read. A body is typed with no expected type, which already refuses
     * {@code None} — this drops the permission as well, so the rule holds here on its own rather than
     * resting on how an argument happens to be typed elsewhere.
     */
    static Core elaborateFunctionValue(Hir.Expr value, List<Type> paramTypes, Scope env,
                                          CheckContext outer) {
        CheckContext ctx = outer.makingAnOptional(false);
        return switch (value) {
            case Hir.Block b -> {
                if (b.params().size() != paramTypes.size()) {
                    throw CompileException.of(Diagnostic.at(b.pos())
                            .say(new HelperMessage.TheLambdaIsAppliedWithAnotherNumberOfArguments(
                                    String.valueOf(b.params().size()),
                                    String.valueOf(paramTypes.size())))
                            .build());
                }
                Scope inner = env;
                for (int i = 0; i < paramTypes.size(); i++) {
                    inner = inner.with(b.params().get(i), paramTypes.get(i));
                }
                Core body = elaborate(b.body(), inner, ctx);
                yield new Core.Block(CoreBinders.all(b.params()), body, Type.fn(paramTypes, body.type()), b.pos());
            }
            case Hir.If iff -> {
                Core cond = requireTyped(iff.cond(), Type.BOOL, env, ctx, "if condition");
                Core then = elaborateFunctionValue(iff.then(), paramTypes, env, ctx);
                Core els = elaborateFunctionValue(iff.els(), paramTypes, env, ctx);
                Type t = then.type();
                Type f = els.type();
                if (!t.equals(f)) {
                    throw CompileException.of(Diagnostic.at(iff.pos(), 2)
                            .say(new HelperMessage.TheBranchesAnswerDifferentFunctionTypes(
                                    Type.show(t), Type.show(f)))
                            .build());
                }
                yield new Core.If(cond, then, els,
                        new Core.ForkPlace(ctx.occurrenceOf(iff.origin()), ctx.within()), t,
                        iff.pos());
            }
            // a helper that answers a function: `adder(5)` expands to the lambda under the bindings
            // its arguments became, and what those captured is what the lambda closes over
            case Hir.Expansion ex -> {
                Applied applied = arguments(ex, env, ctx);
                Core out = elaborateFunctionValue(ex.body(), paramTypes, applied.inner(), ctx);
                for (int i = ex.bound().size() - 1; i >= 0; i--) {
                    out = new Core.LetIn(CoreBinders.of(ex.bound().get(i).binder()), applied.values().get(i), out,
                            out.type(), ex.pos());
                }
                yield out;
            }
            case Hir.LetIn li -> {
                // a capture binding around the function (e.g. `let $n = 5 in (x) -> x + $n`)
                Core bound = elaborate(li.value(), env, ctx);
                Scope inner = env.with(li.binder(), bound.type());
                Core body = elaborateFunctionValue(li.body(), paramTypes, inner, ctx);
                yield new Core.LetIn(CoreBinders.of(li.binder()), bound, body, body.type(), li.pos());
            }
            default -> elaborate(value, env, ctx);
        };
    }

    /** Built-in values written as bare identifiers ({@code None}): a binding may not take one of
     * these names, because it would shadow the built-in and make it unreachable. A unit data name
     * — a rounding mode included — is ordinary and may be bound, the local taking precedence
     * ([#unit-data]). */
    static final Set<String> BUILTIN_VALUES = Set.of("None");

    static void rejectBuiltinShadow(String name, SourcePos pos) {
        if (BUILTIN_VALUES.contains(name)) {
            throw CompileException.of(Diagnostic
                            .at(pos, name.length()).say(new NameMessage.ABindingMayNotShadowABuiltIn(name)).build());
        }
    }

    /** Rejects any binder in {@code e} — a {@code let}, {@code match} binding, or lambda parameter —
     * that takes a built-in value's name. */
    static void rejectBuiltinShadowing(Hir.Expr e) {
        switch (e) {
            case Hir.LetIn li -> {
                rejectBuiltinShadow(li.name(), li.pos());
                rejectBuiltinShadowing(li.value());
                rejectBuiltinShadowing(li.body());
            }
            case Hir.Block b -> {
                for (String p : b.paramNames()) {
                    rejectBuiltinShadow(p, b.pos());
                }
                rejectBuiltinShadowing(b.body());
            }
            case Hir.Match m -> {
                rejectBuiltinShadowing(m.scrutinee());
                for (Hir.Case c : m.cases()) {
                    if (c.binding() != null) {
                        rejectBuiltinShadow(c.bindingName(), c.pos());
                    }
                    rejectBuiltinShadowing(c.body());
                }
            }
            default -> TypeChecker.forEachChild(e, Elaborator::rejectBuiltinShadowing);
        }
    }

    static void requireType(Hir.Expr e, Type expected, Scope env, CheckContext ctx, String what) {
        requireType(e, typeOf(e, env, ctx), expected, ctx.symbols(), what);
    }

    /** As {@link #requireType}, but with the
     * operand's type already computed — a caller that has typed {@code e} does not re-type its
     * subtree. */
    static void requireType(Hir.Expr e, Type actual, Type expected,
                                    Symbols symbols, String what) {
        if (!TypeOps.assignable(actual, expected, symbols)) {   // a case widens to its sum (spec §sum-data)
            throw doesNotFit(e, actual, expected, what);
        }
    }

    /**
     * The report for a value that does not have the type its position states, drawn at the operand
     * that supplied it.
     *
     * <p>One rule, so one sentence. Every check that refuses a value for this reason builds its
     * report here rather than writing a second one beside it: which of two sentences a reader gets
     * would otherwise depend on which check ran, and that is not something a reader can see.
     */
    static CompileException doesNotFit(Hir.Expr operand, Type actual, Type expected, String what) {
        return doesNotFit(operand.reportedAt(), actual, expected, what);
    }

    private static CompileException doesNotFit(souther.compiler.diag.Region at, Type actual,
                                               Type expected, String what) {
        return CompileException.of(Diagnostic
                        .at(at)
                        .diff(Type.show(actual, expected), Type.show(expected, actual))
                        .hint(new TypeMessage.AdjustTheValueOrThePosition())
                        .say(new TypeMessage.ItDoesNotHaveTheTypeItNeedsHere(what)).build());
    }

    /**
     * Reads {@code actual} against {@code declared} for this application: records what it says about
     * the variables {@code declared} carries, and holds it to everything {@code declared} states.
     *
     * <p>A variable is a hole, not a licence. {@code List<'a>} says the value is a list and leaves
     * what it holds open; {@code Map<String, 'a>} says it is a map with String keys. So an argument
     * is held to the constructors, the arities and the ground positions of a declared type however
     * many variables stand inside it — and only the positions a variable stands at are free.
     *
     * <p>A variable takes the first type it is read at, and every later reading must agree. The
     * empty-collection bottom is the one exception, in both directions: it settles nothing, so a
     * variable standing at the bottom is widened by a later concrete reading and a bottom reading
     * leaves a concrete one alone (ADR-0028). That is what lets an empty seed take its element type
     * from the argument that decided it rather than from itself.
     *
     * <p>A disagreement is the caller's error and is reported here rather than swallowed on the
     * grounds that another pass would report it too: what {@link Substitution} holds is the whole
     * signature at once, so it is the only reader that can see two positions of one variable
     * disagree. Reported here rather than inside it because this is what still has {@code operand}.
     */
    static void constrain(Substitution decided, Type declared, Hir.Expr operand, Type actual,
                          Symbols symbols, String what) {
        decide(decided, declared, operand, actual, symbols, what);
        hold(decided, declared, operand, actual, symbols, what);
    }

    /** What {@code actual} says about the variables {@code declared} carries, refusing a variable
     * this application has already read at a type that does not go with this one. */
    static void decide(Substitution decided, Type declared, Hir.Expr operand, Type actual,
                       Symbols symbols, String what) {
        if (decided.decide(declared, actual, symbols) instanceof Fit.Disagrees d) {
            throw CompileException.of(Diagnostic
                            .at(operand.reportedAt())
                            .diff(Type.show(d.actual(), d.expected()), Type.show(d.expected(), d.actual()))
                            .say(new TypeMessage.ItExpectedOneTypeAndGotAnother(what,
                                    Type.show(d.expected(), d.actual()),
                                    Type.show(d.actual(), d.expected()))).build());
        }
    }

    /** {@code actual} held to what {@code declared} states, recording nothing about its variables. */
    static void hold(Substitution decided, Type declared, Hir.Expr operand, Type actual,
                     Symbols symbols, String what) {
        if (decided.hold(declared, actual, symbols) instanceof Fit.Disagrees d) {
            throw CompileException.of(Diagnostic
                            .at(operand.reportedAt())
                            .diff(Type.show(d.actual(), d.expected()), Type.show(d.expected(), d.actual()))
                            .say(new DeclarationMessage.ItExpectsAnotherType(what,
                                    Type.show(d.expected(), d.actual()),
                                    Type.show(d.actual(), d.expected()))).build());
        }
    }

    /**
     * Wraps a value being given to a {@code ?} field, so {@code Out { note = n }} puts {@code n}
     * where an optional is asked for (spec §algebraic-types). Construction is the one place this happens: an
     * expected optional only ever arrives from a field's own type, because nowhere else in a model
     * can {@code T?} be written (ADR-0011) — a lambda handed to a stdlib combinator is typed with no
     * expected type at all ({@code HelperTyping}), so a step for {@code List.filterMap} still has to
     * answer an optional of its own. That keeps this a rule about building a data rather than a
     * coercion applied wherever two shapes nearly line up.
     *
     * <p>A value that is already the optional — read from another field, or {@code None} — is
     * returned untouched, and so is one whose type has nothing to do with the field, which the
     * caller then reports as the mismatch it is.
     */
    static Core liftIntoOption(Core value, Type expected, Symbols symbols) {
        if (!(expected instanceof Type.OptionOf opt)
                || TypeOps.assignable(value.type(), expected, symbols)
                || !TypeOps.assignable(value.type(), opt.element(), symbols)) {
            return value;
        }
        return new Core.OptionSome(value, expected, value.pos());
    }

    /**
     * A reference to a value the representation keeps standing, as the node one becomes.
     *
     * <p>An application of no arguments, which is what a reference to a value is: reading a value's
     * name is running its body (ADR-0072), and a representation that did not substitute it kept
     * that. So it is built the way a kept call is — from the signature the name was settled with —
     * and is held to it the same way. Nothing about this node says it came from a name rather than
     * from a call, because nothing downstream asks.
     */
    private static Core keptValue(CompleteSignature settled, ReferenceOrigin reference,
                                  SourcePos pos) {
        // A name read where a value goes, and no application was written over it. What it is built
        // as is a call, and that call is this reading's: nobody applied anything there, so it is
        // derived from the name that was written rather than being an application of a source. The
        // reference is that name's and is carried, not made again.
        return new Core.PreservedCall(settled.declaring(), List.of(),
                new Core.KeptCallPlace(reference, new ApplicationOrigin.Derived(
                        new ApplicationDerivationCause.NameReadAsAValue(reference), 0)),
                settled.result(), pos);
    }

    /**
     * A name written where a value goes that is not one: an unknown name, a type that is not a unit
     * data, a function named without being applied, or one of Option's cases outside the one place
     * an optional is made.
     */
    private static RuntimeException notAValue(Hir.Var v, Scope env) {
        if (v instanceof Hir.Var.Unanswered) {
            // reported where the name was written; this definition has no meaning to work out
            return new Unanswerable(v.pos());
        }
        optionCaseWritten(v.name(), v.pos());
        // A name that was answered is not an unknown one, and saying it is sends the reader looking
        // for a spelling mistake. What it denotes is what cannot stand here, so that is the report.
        String denotes = switch (v.answered().denotes()) {
            case ValueName.Behavior _ -> "a behavior";
            case ValueName.Builtin _ -> "written at the position that reads it, not evaluated";
            // Reached from a name slot the inliner does not substitute into — a spread. In an
            // expression slot the inliner has already put the helper's block here, and the block is
            // refused as one.
            case ValueName.Helper _ -> "a function";
            case null, default -> null;
        };
        if (denotes != null) {
            return CompileException.of(Diagnostic
                            .at(v.written().reportedAt()).say(new DeclarationMessage.ItCannotBeHeldAsAValueHere(v.name(), denotes)).build());
        }
        return CompileException.of(Diagnostic
                        .at(v.written().reportedAt())
                        
                        .suggestion(Suggest.candidate(v.name(), env.spellings()))
                        .say(new NameMessage.NoValueOfThatNameInScope(v.name())).build());
    }

    /**
     * Rejects {@code Some} / {@code None} written where no {@code ?} field is being given a value
     * (E1303). Giving a field its value is the one place an optional is made — the wrap is implicit
     * and {@code None} is the empty one (spec §algebraic-types) — and everywhere else an optional is read and
     * passed on. Neither an unknown-name report nor an arbitrary-call report says that, and the
     * latter sent the reader off to write a Java binding, which makes no optional either (issue
     * #166). Patterns do not come through here: {@code | Some v} is matched, not evaluated.
     */
    static void optionCaseWritten(String name, SourcePos pos) {
        boolean some = name.equals("Some");
        if (!some && !name.equals("None")) {
            return;
        }
        throw CompileException.of(Diagnostic.at(pos, name.length())
                .say(some ? new DeclarationMessage.SomeIsNotACall()
                        : new DeclarationMessage.NothingHereIsAskingForNone())
                .hint(some ? new DeclarationMessage.WriteTheValueOnItsOwn()
                        : new DeclarationMessage.MakeAbsenceACaseOfItsOwnSum())
                .build());
    }

    /**
     * That an attempt's departure arms answer the clauses of the type being attempted, and answer all
     * of them. A failure reaches exactly one arm, so the arms have to be total over what can fail:
     * every named clause is answered by name, and {@code | _ -> …} answers the clauses carrying no
     * name — required when there are such clauses and refused when there are none, so that reading an
     * arm says which rule it is for and reading {@code _} says there are rules it cannot name.
     *
     * <p>Nothing is checked for the {@code else e} form: it already answers any failure.
     */
    private static void checkArmsAnswerClauses(Hir.IfConstructed ic, TypeSymbol.AtModule typeName,
                                               Symbols symbols) {
        if (!ic.mapsClauses()) {
            return;
        }
        LinkedHashSet<String> named = new LinkedHashSet<>();
        boolean unnamed = false;
        for (InvariantHeader clause : TypeOps.invariantHeadersGoverning(typeName, symbols)) {
            if (clause.name().isPresent()) {
                named.add(clause.name().get());
            } else {
                unnamed = true;
            }
        }
        boolean wildcard = false;
        Set<String> answered = new HashSet<>();
        for (Hir.ElseArm arm : ic.els()) {
            if (arm.clause().isEmpty()) {
                wildcard = true;
                continue;
            }
            String name = arm.clause().get();
            answered.add(name);
            if (!named.contains(name)) {
                throw CompileException.of(Diagnostic.at(arm.pos(), name.length())
                        .say(new AttemptMessage.NoClauseOfThatName(name, typeName.name()))
                        .hint(new AttemptMessage.TheClausesThatCanBeAnswered(
                                named.isEmpty() ? "-" : String.join(", ", named)))
                        .build());
            }
        }
        List<String> missing = new ArrayList<>(named);
        missing.removeAll(answered);
        if (!missing.isEmpty()) {
            throw CompileException.of(Diagnostic.at(ic.pos(), 2)
                    .say(new AttemptMessage.TheseClausesHaveNoArm(String.join(", ", missing),
                            typeName.name()))
                    .hint(new AttemptMessage.AnswerEachOfThemOrGiveTheElseOneValue())
                    .build());
        }
        if (unnamed && !wildcard) {
            throw CompileException.of(Diagnostic.at(ic.pos(), 2)
                    .say(new AttemptMessage.UnnamedClausesAreLeftUnanswered(typeName.name()))
                    .hint(new AttemptMessage.AddACatchAllArmOrNameThem())
                    .build());
        }
        if (!unnamed && wildcard) {
            throw CompileException.of(Diagnostic.at(ic.pos(), 2)
                    .say(new AttemptMessage.TheCatchAllArmAnswersNothing(typeName.name()))
                    .hint(new AttemptMessage.DropTheCatchAllArm(typeName.name()))
                    .build());
        }
    }

    /**
     * What a report about the value a function argument answered with underlines.
     *
     * <p>A block answers with its body, and the block's own position is where its parameters start —
     * so a report drawn there names what the block returned while pointing at what takes its
     * arguments. The bindings written above the answer are stepped over: a {@code let} holds the
     * value it binds, which is not the value the block answered with.
     *
     * <p>Nothing else is stepped over. An {@code if} and a {@code match} have the type their arms
     * join to, so no one arm supplied it and the construct is what did. An expansion is left alone
     * for its own reason: its body is the callee's, and a report drawn inside it names a place the
     * author did not write this value at.
     *
     * <p>A function value written where a block goes is eta-expanded into a block by the time this
     * is asked, and the expansion it answers with is what stops the descent — the expression that
     * answered is in that function's own declaration, and the argument is as far as this source
     * goes.
     */
    public static Region answerRegion(Hir.Expr fnArg) {
        Hir.Expr answered = fnArg instanceof Hir.Block block ? answering(block.body()) : fnArg;
        return answered.reportedAt();
    }

    /** {@code body} with the bindings written above its answer stepped over. */
    private static Hir.Expr answering(Hir.Expr body) {
        return body instanceof Hir.LetIn li ? answering(li.body()) : body;
    }

}
