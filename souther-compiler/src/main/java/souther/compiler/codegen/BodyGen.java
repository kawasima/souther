package souther.compiler.codegen;

import souther.compiler.check.Scope;
import souther.compiler.check.Symbols;
import souther.compiler.diag.CompileException;
import souther.compiler.diag.Diagnostic;
import souther.compiler.diag.msg.NameMessage;
import souther.compiler.ast.Hir;
import souther.compiler.check.CheckContext;
import souther.compiler.check.DataChecker;
import souther.compiler.check.ReqSig;
import souther.compiler.types.BindingId;
import souther.compiler.types.Type;
import souther.compiler.types.TypeSymbol;
import souther.compiler.numeric.Rel;
import souther.compiler.check.Comparison;
import souther.compiler.check.ComparisonClaim;
import souther.compiler.check.Ordering;
import souther.compiler.core.Core;
import souther.compiler.core.Kernel;
import souther.compiler.core.KernelSignature;
import souther.compiler.core.GrowingFold;

import souther.compiler.core.EnsuresEnforcement;
import souther.compiler.jvm.GeneratedClass;
import souther.compiler.types.Refinement;
import souther.compiler.types.ValueName;
import java.lang.classfile.ClassFile;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.Label;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.DynamicConstantDesc;
import java.lang.constant.MethodHandleDesc;
import java.lang.constant.MethodTypeDesc;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import static souther.compiler.codegen.Descriptors.*;
import static souther.compiler.codegen.JvmTypes.*;

/**
 * Emits a behavior (or helper, or escaping lambda) body: it lowers each Core IR expression to
 * bytecode (ADR-0021), threading a slot environment and the injected requirements in scope. It is the
 * one generator that never reads a codec; the value-class and codec generators build a fresh instance
 * per method and drive it. Name resolution and the synthetic-class sink come from {@link
 * CodegenContext}; the type/bytecode helpers from {@link JvmTypes}; the shipped primitives from
 * {@link Intrinsics}.
 */
final class BodyGen {

    private final CodegenContext ctx;
    /** Aliases of {@link CodegenContext#pkg}/{@link CodegenContext#symbols}, read as bare names. */
    private final String pkg;
    private final Symbols symbols;

    /** What {@code kernel} was declared to take and answer. */
    KernelSignature kernelSignature(Kernel kernel) {
        return ctx.kernelSignature(kernel);
    }

    private ClassDesc cd(TypeSymbol typeName) {
        return ctx.cd(typeName);
    }

    private Map<String, Type> fieldTypes(Hir.Data data) {
        return ctx.fieldTypes(data);
    }

    private ClassDesc jvmType(Type type) {
        return JvmTypes.jvmType(type, ctx);
    }

    private ClassDesc[] fieldDescs(Map<String, Type> fields) {
        return JvmTypes.fieldDescs(fields, ctx);
    }

    private void unbox(CodeBuilder code, Type type, int slot) {
        JvmTypes.unbox(code, type, slot, ctx);
    }

    private void castFromObject(CodeBuilder code, Type type) {
        JvmTypes.castFromObject(code, type, ctx);
    }

        private final CodeBuilder code;
        private final Hir.Data data;
        private final ClassDesc cdName;
        /**
         * Where each binding this body holds lives.
         *
         * <p>Keyed by the binding rather than by its spelling, so an inner binding of the same
         * spelling takes a slot of its own and leaves the outer one where it was. Nothing has to be
         * put back when a scope ends, which is what a name-keyed table needed and did not always do.
         */
        private final Map<BindingId, Var> locals = new HashMap<>();
        private int nextSlot;
        private Set<ValueName.Behavior> reqNames = Set.of();
        private Map<ValueName.Behavior, Type> reqSuccess = Map.of();
        private Map<ValueName.Behavior, List<Type>> reqParams = Map.of();
        /** The fields the class this body is emitted into keeps its injected behaviors in. */
        private InjectionSlots held = InjectionSlots.none();
        /** The last line already bound in this method's {@code LineNumberTable}; skips consecutive
         * same-line entries. Fresh per method, since one {@code BodyGen} emits one method's code. */
        private int lastEmittedLine = -1;
        /** Set while emitting a recursive helper method: the helper's name, its parameters, and the
         * label bound at the body entry. A tail-position call to this same helper reassigns the
         * parameter slots and jumps to {@code tcoEntry} rather than recursing, so a self-tail-recursive
         * helper runs in constant stack. Null for any other body (a behavior never self-recurses). */
        private String tcoName;
        private List<Hir.FnParam> tcoParams;
        private Label tcoEntry;
        /** Members of this body's declared output union that reach it through a bridge case; empty
         * for every other body. @see #injectsInto */
        private List<TypeSymbol> injectMembers = List.of();
        /**
         * Whether the arms of this body are ones a coverage plan counted.
         *
         * <p>Off unless said otherwise, because most of what goes through here is not a behavior's
         * body: an invariant's clause, a codec, a recursive helper shared by every behavior that
         * calls it. None of those is a fork in any one behavior
         * ({@link souther.compiler.coverage.CoverageSites}), and the plan holds no arm for them.
         *
         * <p>It was the other way round, on the reasoning that a path nobody had thought about should
         * fail loudly rather than go unmeasured. It does not fail loudly: the generation is abandoned
         * and the whole module's arms come back unmeasured, which is the quietest failure there is.
         * What makes the omission loud is counting the arms that were emitted against the arms that
         * were planned, which is done once at the end.
         */
        private boolean armsAreCounted = false;

        /** Emits this body recording where a run went through it. Said where the plan was made from
         * these very nodes, which is a behavior's body and what it encloses. */
        void armsAreCounted() {
            this.armsAreCounted = true;
        }

        BodyGen(CodegenContext ctx, CodeBuilder code, Hir.Data data, ClassDesc cdName, int firstSlot) {
            this.ctx = ctx;
            this.pkg = ctx.pkg;
            this.symbols = ctx.symbols;
            this.code = code;
            this.data = data;
            this.cdName = cdName;
            this.nextSlot = firstSlot;
        }

        /**
         * Makes this body the implementation of a behavior whose declared output is a union, so its
         * returns leave the union's JVM representation rather than a bare Souther value.
         *
         * <p>Inside a body every value is a Souther value. It becomes a member of the result union at
         * the return, and a caller turns it back at the call. The two conversions are the only places
         * the two forms meet — nothing in between ever holds a bridge case, so no return can wrap one
         * a second time.
         */
        void injectsInto(Type out) {
            this.injectMembers = ctx.bridgedMembers(out);
        }

        /** Makes injected required behaviors callable inline from this body (spec §unmarked-output, §fn). */
        void requireds(Set<ValueName.Behavior> names, Map<ValueName.Behavior, Type> success,
                       Map<ValueName.Behavior, List<Type>> params, InjectionSlots held) {
            this.reqNames = names;
            this.reqSuccess = success;
            this.reqParams = params;
            this.held = held;
        }

        /**
         * The behavior {@code call} reaches, or null where it reaches something that is no behavior.
         *
         * <p>Read off what the call was resolved to. The rendered reach name is what a method is
         * spelled from and is not an identity: this module's own behavior and another module's may
         * be reached by one name, and a table asked with that name answers for one of them.
         */
        private static ValueName.Behavior behaviorOf(Core.Call call) {
            return call.fn() instanceof Core.Reached reached
                    && reached.denotes() instanceof ValueName.Behavior behavior ? behavior : null;
        }

        /** A {@code ReqSig} view of the injected behaviors in scope, for re-typing a closure body. */
        private Map<ValueName.Behavior, ReqSig> reqSigs() {
            Map<ValueName.Behavior, ReqSig> sigs = new HashMap<>();
            for (ValueName.Behavior n : reqNames) {
                sigs.put(n, new ReqSig(reqParams.get(n), reqSuccess.get(n)));
            }
            return sigs;
        }

        void bind(Core.Binder binder, int slot, Type type) {
            bind(binder.binding(), binder.name(), slot, type);
        }

        void bind(BindingId binding, String name, int slot, Type type) {
            put(locals, binding, new Var(slot, type, name));
        }

        private <K> void put(Map<K, Var> where, K key, Var var) {
            where.put(key, var);
            nextSlot = Math.max(nextSlot, var.slot() + width(var.type()));
        }

        int slot(Type type) {
            int s = nextSlot;
            nextSlot += width(type);
            return s;
        }

        /**
         * The environment this body's slots hold, as an expression is checked against it.
         *
         * <p>Answered and not used here. Elaborating an AST expression is the codec emitters' —
         * they are the one AST-level path left in this backend (ADR-0021) — and what this emitter
         * knows that they do not is which names are bound to which slots. So the environment
         * crosses and the elaboration does not: a body, an invariant and a rule reach this emitter
         * as the Core the checker made (issue #1080).
         */
        CheckContext context() {
            return new CheckContext(symbols, ctx.published, ctx.kinds, data, reqSigs());
        }

        /**
         * Reads a field onto the stack through the data's accessor. A data is a record, so its backing
         * field is private and the accessor is the read — for a data of this module as much as for an
         * imported one, whose field is out of reach across the module = package boundary anyway
         * (spec §field-visibility, §jvm-product).
         */
        private void emitFieldRead(CodeBuilder code, TypeSymbol ownerName, String field, Type ft) {
            MethodTypeDesc mtd = MethodTypeDesc.of(jvmType(ft));
            if (symbols.declaredNode(ownerName) instanceof Hir.SumData) {
                // a field every case spreads is declared on the sum's sealed interface (issue #160)
                code.invokeinterface(cd(ownerName), field, mtd);
            } else {
                code.invokevirtual(cd(ownerName), field, mtd);
            }
        }

        /** Opens a single-value newtype on the stack to its underlying value (recursively, so a
         * newtype over a newtype reaches the base primitive), returning that value's type; leaves a
         * non-newtype operand untouched. Used so comparison operators read the value a newtype wraps. */
        private Type unwrapNewtypeValue(Type t) {
            if (t instanceof Type.Ref ref
                    && symbols.declaredNode(ref.name()) instanceof Hir.Data d && d.newtype()) {
                Type inner = fieldTypes(d).get("value");
                if (inner != null) {
                    emitFieldRead(code, ref.name(), "value", inner);
                    return unwrapNewtypeValue(inner);
                }
            }
            return t;
        }

        private static String captureField(int i) {
            return "c" + i;
        }

        /** Generates a synthetic {@code Fn} class for an escaping lambda: captured free variables become
         * {@code final} fields set by the constructor, and the body compiles into {@code apply}, which
         * unboxes its arguments from the {@code Object[]} and boxes its result (spec §blocks). */
        private byte[] generateLambdaClass(ClassDesc cd, List<Core.Binder> params, Core body,
                                           List<Type> paramTypes,
                                           List<Core.Read> captures,
                                           List<ValueName.Behavior> injectedNames,
                                           Map<ValueName.Behavior, Type> reqSuccess,
                                           Map<ValueName.Behavior, List<Type>> reqParams) {
            // The lambda is a class of its own, so it keeps the behaviors it calls in fields of its
            // own — at its own positions, which are not the enclosing class's.
            InjectionSlots carried = InjectionSlots.of(injectedNames, ctx);
            return build(cd, cb -> {
                cb.withFlags(ClassFile.ACC_FINAL | ClassFile.ACC_SUPER);
                cb.withInterfaceSymbols(CD_Fn);
                for (int i = 0; i < captures.size(); i++) {
                    cb.withField(captureField(i), jvmType(captures.get(i).type()),
                            ClassFile.ACC_PRIVATE | ClassFile.ACC_FINAL);
                }
                for (InjectionSlots.Slot slot : carried.all()) {   // what requiredCall reads
                    cb.withField(slot.fieldName(), slot.type(),
                            ClassFile.ACC_PRIVATE | ClassFile.ACC_FINAL);
                }
                List<ClassDesc> ctor = new ArrayList<>();
                for (Core.Read c : captures) {
                    ctor.add(jvmType(c.type()));
                }
                for (InjectionSlots.Slot slot : carried.all()) {
                    ctor.add(slot.type());
                }
                cb.withMethodBody("<init>", MethodTypeDesc.of(ConstantDescs.CD_void, ctor.toArray(new ClassDesc[0])),
                        ClassFile.ACC_PUBLIC, code -> {
                    code.aload(0);
                    code.invokespecial(CD_Object, "<init>", MTD_void);
                    int slot = 1;
                    for (int i = 0; i < captures.size(); i++) {
                        Type ct = captures.get(i).type();
                        code.aload(0);
                        load(code, slot, ct);
                        code.putfield(cd, captureField(i), jvmType(ct));
                        slot += width(ct);
                    }
                    for (InjectionSlots.Slot carriedSlot : carried.all()) {
                        code.aload(0);
                        code.aload(slot);
                        code.putfield(cd, carriedSlot.fieldName(), carriedSlot.type());
                        slot += 1;
                    }
                    code.return_();
                });
                if (captures.isEmpty() && injectedNames.isEmpty()) {
                    // Captures are this class's only fields, so capturing nothing makes it stateless
                    // and one instance per lambda site is enough. Two sites never share one, and
                    // Souther has no function equality, so this is unobservable. Package-private:
                    // every use site is in the module's own package.
                    emitSharedInstance(cb, cd, 0, null);
                }
                cb.withMethodBody("apply", MTD_Fn_apply, ClassFile.ACC_PUBLIC, code -> {
                    BodyGen g = new BodyGen(ctx, code, null, cd, 2);   // slot 0 = this, slot 1 = the Object[] args
                    // A lambda lifted out of a body is still that body's forks.
                    if (armsAreCounted) {
                        g.armsAreCounted();
                    }
                    if (!injectedNames.isEmpty()) {
                        // the captured behaviors live in this closure's own fields; requiredCall reads
                        // `this.<name>`, so route them the same way the enclosing behavior does
                        Map<ValueName.Behavior, Type> succ = new HashMap<>();
                        Map<ValueName.Behavior, List<Type>> parm = new HashMap<>();
                        for (ValueName.Behavior inj : injectedNames) {
                            succ.put(inj, reqSuccess.get(inj));
                            parm.put(inj, reqParams.get(inj));
                        }
                        g.requireds(new HashSet<>(injectedNames), succ, parm, carried);
                    }
                    for (int i = 0; i < paramTypes.size(); i++) {
                        Type pt = paramTypes.get(i);
                        int s = g.slot(pt);
                        code.aload(1);
                        pushInt(code, i);
                        code.aaload();
                        unbox(code, pt, s);
                        g.bind(params.get(i), s, pt);
                    }
                    for (int i = 0; i < captures.size(); i++) {
                        Core.Read c = captures.get(i);
                        int s = g.slot(c.type());
                        code.aload(0);
                        code.getfield(cd, captureField(i), jvmType(c.type()));
                        store(code, s, c.type());
                        g.bind(c.binding(), c.name(), s, c.type());
                    }
                    Type rt = g.genExpr(body);
                    box(code, rt);
                    code.areturn();
                });
            });
        }

        /**
         * Emits {@code e} in tail position: every path ends in an {@code areturn}.
         *
         * <p>Constructing an invariant-bearing data goes through {@code __construct}, which checks the
         * invariant and returns a {@code Result}; {@code ConstraintViolation.orThrow} turns that into
         * either the value (returned) or a thrown {@code ConstraintViolation} — an invariant violation
         * aborts rather than riding an output case (spec §algebraic-types, §violation-destination).
         * Because a desugared {@code guard} (spec §guard) is an {@code if} whose branches are tail,
         * this is reached for constructions on both sides of a guard — there is no second, unchecked
         * construction path.
         */
        void emitTail(Core e, ClassDesc cdB, Set<ValueName.Behavior> requiredNames,
                      Map<ValueName.Behavior, Type> requiredSuccess) {
            emitTail(e, cdB, requiredNames, requiredSuccess, null);
        }

        // {@code expected} is the declared return/output type of the body being emitted (issue #70): it
        // is threaded to a tail-position fold the same way {@link #genExpr} threads it in value
        // position, so a fold over an empty-collection seed materialises its step at the accumulator
        // type the checker pinned rather than a bottom. Null when no declared type is in scope.
        void emitTail(Core e, ClassDesc cdB, Set<ValueName.Behavior> requiredNames,
                      Map<ValueName.Behavior, Type> requiredSuccess,
                      Type expected) {
            emitLine(e);
            switch (e) {
                case Core.LetIn li -> {
                    if (li.value() instanceof Core.Call call && behaviorOf(call) != null
                            && requiredNames.contains(behaviorOf(call))) {
                        // call an injected required behavior; requiredCall handles both the unary
                        // Behavior contract and a multi-input base (issue #57), leaving the success
                        // value cast on the stack
                        Type letType = call.type();
                        requiredCall(call);
                        int vSlot = slot(letType);
                        store(code, vSlot, letType);
                        bind(li.binder(), vSlot, letType);
                    } else {
                        Type vt = li.value().type();
                        if (vt instanceof Type.FnOf fn) {
                            // a lambda chosen at runtime (e.g. by an `if`) — a first-class Fn
                            // (spec §blocks), at the parameter types the checker inferred for it
                            emitFunctionValue(li.value(), fn.params());
                        } else {
                            genExpr(li.value(), vt);
                        }
                        int slot = slot(vt);
                        store(code, slot, vt);
                        bind(li.binder(), slot, vt);
                    }
                    emitLine(li);   // re-pin: a bound value may have moved the line off the call
                    emitTail(li.body(), cdB, requiredNames, requiredSuccess, expected);
                }
                case Core.If iff -> {
                    genExpr(iff.cond());
                    Label elseL = code.newLabel();
                    code.ifeq(elseL);
                    probe(iff, 0);
                    emitTail(iff.then(), cdB, requiredNames, requiredSuccess, expected);
                    code.labelBinding(elseL);
                    probe(iff, 1);
                    emitTail(iff.els(), cdB, requiredNames, requiredSuccess, expected);
                }
                // Both branches stay in tail position, so a self-recursive helper guarded by an
                // attempt loops exactly as one guarded by a plain condition does. Falling through to
                // the value emitter would leave the recursive call a real call and grow the stack.
                case Core.IfConstructed ic -> {
                    Attempt a = emitAttempt(ic);
                    bind(ic.binder(), a.slot(), ic.construct().type());
                    probe(ic, 0);
                    emitTail(ic.then(), cdB, requiredNames, requiredSuccess, expected);
                    code.labelBinding(a.elseLabel());
                    // Each departure is in tail position too, so it returns on its own and needs no
                    // jump past the ones emitted after it.
                    emitDepartures(ic, a,
                            body -> emitTail(body, cdB, requiredNames, requiredSuccess, expected),
                            null);
                }
                case Core.Match m -> emitTailMatch(m, cdB, requiredNames, requiredSuccess, expected);
                case Core.Call call when tcoName != null && call.name().equals(tcoName)
                        && call.args().size() == tcoParams.size() -> emitSelfTailCall(call);
                case Core.Construct nd when DataChecker.isInvariantBearing(nd.typeName(), symbols) -> {
                    ClassDesc cdType = cd(nd.typeName());
                    Map<String, Type> flds = fieldTypes((Hir.Data) symbols.declaredNode(nd.typeName()));
                    emitFieldValues(flds, nd.values());
                    emitLine(nd);   // re-pin: a field init may have moved the line off the construction
                    code.invokestatic(cdType, "__construct", MethodTypeDesc.of(CD_Result, fieldDescs(flds)));
                    code.invokestatic(CD_ConstraintViolation, "orThrow", MTD_orThrow);
                    returnValue();
                }
                default -> {
                    Type rt = genExpr(e, expected);
                    box(code, rt);
                    returnValue();
                }
            }
        }

        /**
         * Returns the Souther value on the stack as a member of the declared output union: a member
         * this module declared is the member itself, any other is put into its bridge case. The
         * dispatch runs over the union's members, which are known here, so nothing is guessed from
         * the value alone.
         */
        private void returnValue() {
            ResultBoundary.inject(code, ctx, injectMembers, slot(Type.NOTHING));
        }

        /** Marks the entry of a self-tail-recursive helper. The parameters are already bound to their
         * slots; a later tail-position self-call jumps back here after reassigning them, so the helper
         * loops instead of recursing (see {@link #emitTail} and {@link #emitSelfTailCall}). */
        void beginSelfRecursion(String name, List<Hir.FnParam> params) {
            this.tcoName = name;
            this.tcoParams = params;
            this.tcoEntry = code.newLabel();
            code.labelBinding(tcoEntry);
        }

        /** A tail-position call to the helper being emitted: recompute the arguments — each still reads
         * the current parameter slots — then overwrite the slots and loop. Arguments are pushed
         * left-to-right and stored in reverse so no slot is overwritten before every argument has been
         * read (e.g. {@code loop(acc + n, n - 1)} reads both {@code acc} and {@code n}). */
        private void emitSelfTailCall(Core.Call call) {
            List<Var> params = new ArrayList<>(tcoParams.size());
            for (Hir.FnParam p : tcoParams) {
                params.add(locals.get(p.binder().id()));
            }
            for (int i = 0; i < call.args().size(); i++) {
                Type at = genExpr(call.args().get(i));
                Type pt = params.get(i).type();
                if (isReference(pt) && !isReference(at)) {
                    box(code, at);
                }
            }
            for (int i = call.args().size() - 1; i >= 0; i--) {
                store(code, params.get(i).slot(), params.get(i).type());
            }
            countOneStep();
            code.goto_(tcoEntry);
        }

        /** One counted point — see {@link CodegenContext#countOneStep}. */
        private void countOneStep() {
            ctx.countOneStep(code);
        }

        /** Pushes what each field is given, in the order the construction holds them — which is
         * declaration order, and which a construction settled when it was built (ADR-0021). Nothing
         * is worked out here: a field a spread supplied holds the read of it, like any other value. */
        void emitFieldValues(Map<String, Type> fields, List<Core.FieldValue> values) {
            for (Core.FieldValue given : values) {
                // push the field's declared type so a field valued by a fold over an empty seed
                // materialises its step closure at the field's type (issue #70)
                genExpr(given.value(), fields.get(given.field()));
            }
        }

        /** Builds an ArrayList of the literal's elements and returns it immutably. An empty {@code []}
         * adopts a pushed-down list type, as {@code Map.empty}/{@code Set.empty} do and as the checker
         * already does: a written {@code let xs: List<Int> = []} otherwise emits at {@code List<_>},
         * and reading an element back unboxes the bottom (issue #74). */
        private void listLit(Core.ListLit lit) {
            code.new_(CD_ArrayList);
            code.dup();
            code.invokespecial(CD_ArrayList, "<init>", MTD_void);
            for (Core el : lit.elements()) {
                code.dup();
                if (el.type() instanceof Type.FnOf fn) {
                    // a function held in a list is a value like any other, so it is materialised as
                    // an Fn here rather than expanded into a call site it does not have
                    emitFunctionValue(el, fn.params());
                } else {
                    box(code, genExpr(el));
                }
                code.invokevirtual(CD_ArrayList, "add", MTD_ArrayList_add);
                code.pop();
            }
            code.invokestatic(CD_List, "copyOf", MTD_List_copyOf, true);
        }

        /** Builds a tuple {@code (e1, e2, ...)}, boxing each element (ADR-0036). The elements go into
         * an array and the array is handed to {@code Tuple.of}, which takes ownership of it: it was
         * built one instruction earlier and nothing else holds it. A pushed-down tuple type reaches
         * each element, as it does in the checker, so a written {@code (Map<String, Int>,
         * List<String>)} seed materialises at those types (issue #74). */
        private void tuple(Core.Tuple t) {
            if (t.elements().size() == 2) {
                // the arity every fold-carried tuple has: built outright, with no array between
                code.new_(CD_TuplePair);
                code.dup();
                box(code, genExpr(t.elements().get(0)));
                box(code, genExpr(t.elements().get(1)));
                code.invokespecial(CD_TuplePair, "<init>", MTD_TuplePair_init);
                return;
            }
            pushInt(code, t.elements().size());
            code.anewarray(CD_Object);
            for (int i = 0; i < t.elements().size(); i++) {
                code.dup();
                pushInt(code, i);
                box(code, genExpr(t.elements().get(i)));
                code.aastore();
            }
            code.invokestatic(CD_Tuple, "ofOwned", MTD_Tuple_ofOwned, true);
        }

        /** Reads a tuple element by index, cast back to the element's type. */
        private void tupleGet(Core.TupleGet tg) {
            genExpr(tg.tuple());
            if (tg.tuple().type() instanceof Type.TupleOf tu && tu.elements().size() == 2) {
                code.getfield(CD_TuplePair, tg.index() == 0 ? "first" : "second", CD_Object);
            } else {
                pushInt(code, tg.index());
                code.invokeinterface(CD_Tuple, "get", MTD_Tuple_get);
            }
            castFromObject(code, tg.type());
        }

        /**
         * Records that this comparison produced a value and which value it was, where it is one a
         * guard's condition is made of.
         *
         * <p>After the value and not before it. What a boundary is met by is the comparison having
         * answered, and an operand can abort on the way — {@code x /= 0 && 100 / x > 1} is why the
         * operators stop when the answer is settled in the first place. A probe in front of the
         * emission would record a comparison that never produced anything as one that did.
         *
         * <p>The value itself is handed over, copied off the stack rather than recomputed. Which way
         * a comparison came out is not something the arm below it can say — a condition stops as soon
         * as it is settled — so anything that worked it out afterwards would be reasoning where the
         * value is right there. {@code comparisonMaterialize} has already brought it to an
         * {@code iconst_0} or {@code iconst_1}, so the copy is of a plain boolean and the original is
         * left exactly as the emission that follows expects it.
         *
         * <p>Absent is ordinary here, unlike an arm's: what has a site is every comparison of a
         * condition this plan instruments, and the emitter walks comparisons everywhere else too.
         */
        private void comparisonProbe(Core.Binary bin) {
            if (!armsAreCounted || !ctx.measuring()) {
                return;
            }
            ctx.comparisonSiteOf(bin).ifPresent(site -> {
                ctx.emitted(site.raw());
                code.dup();
                code.loadConstant(site.raw());
                code.invokestatic(CD_Probe, "compared", MTD_Probe_compared);
            });
        }

        /**
         * Records that this arm ran, where this generation is one that measures.
         *
         * <p>Nothing before it on the stack and nothing after: an {@code int} constant in, nothing out.
         * So it can go at the head of any arm without the arm's own emission having to know it is
         * there, and a measuring build and a shipping build differ by these calls and by nothing else.
         */
        private void probe(Core node, int arm) {
            if (!armsAreCounted || !ctx.measuring()) {
                return;
            }
            int site = ctx.probesOf(node)[arm];
            if (site == souther.compiler.coverage.CoverageSites.NO_SITE) {
                return;   // an arm that answers nothing is not one a row can be in
            }
            ctx.emitted(site);
            code.loadConstant(site);
            code.invokestatic(CD_Probe, "hit", MTD_Probe_hit);
        }

        /**
         * Binds the bytecode that follows to {@code e}'s source line, for the {@code LineNumberTable}
         * (spec §target-jdk). Every {@code Core} node keeps its {@code SourcePos}, so a runtime stack
         * trace — an invariant abort above all — points back to the {@code .sou} line. Consecutive
         * nodes on the same line (a subexpression tree, or a tail node re-lined by {@code genExpr})
         * collapse to one entry.
         *
         * <p>A node written in another text writes nothing. The class carries one
         * {@code SourceFile}, which is this module's, and a helper declared in another file of the
         * same compile keeps the positions it was written at — so its line is a line of a file this
         * class does not name, and the file it does name may be shorter than it. Leaving the entry
         * out is what the table has for saying so: the pc then falls under the entry before it,
         * which is a line of this class's own file because that is the only kind of line written
         * here.
         *
         * <p>Which entry stands there is the {@code let} the copy is the body of. The bindings an
         * expansion makes carry the call's own position, and between them and the copy the bound
         * value is emitted — an argument written on its own line binds that line, and the copy binds
         * nothing to move it back. So the call is bound again before the body, which is the same
         * re-pin a construction does once its fields are on the stack. Binding it where the body
         * binds a line of its own costs nothing: two lines at one offset are one entry, the last.
         */
        private void emitLine(Core e) {
            souther.compiler.diag.PhysicalPos sits = ctx.sits(e.pos());
            int line = sits == null ? 0 : sits.line();
            if (line > 0 && line != lastEmittedLine) {
                code.lineNumber(line);
                lastEmittedLine = line;
            }
        }

        /**
         * Emits a Core expression — the single expression emitter (ADR-0021); every node kind is
         * handled here. A {@code let} whose value is a runtime-selected function still asks the
         * type checker (which works on the AST) whether the value is such a function and for its
         * parameter types, so those calls go through the tree the checker reads: Core is untyped and type
         * inference lives in the checker, so the backend reuses it rather than re-deriving types.
         */
        Type genExpr(Core e) {
            return genExpr(e, null);
        }

        /**
         * Emits {@code e} and yields its type: the one the checker decided, read off the node
         * (issue #81). Nothing here derives a type of its own — where a type drives the emission
         * (which instruction, which descriptor, which slot), it is read from the node that carries it.
         *
         * <p>{@code expected} mirrors the checker's bidirectional pass (issue #70): it is pushed into
         * a fold call so codegen materialises the step closure at the same accumulator type the
         * checker validated. Only the passthrough arms (if/let/match/call) forward it.
         */
        Type genExpr(Core e, Type expected) {
            if (e.type() == null) {
                throw new IllegalStateException("this node reached the emitter untyped: "
                        + e.getClass().getSimpleName() + " at " + e.pos());
            }
            emitLine(e);
            switch (e) {
                // A call a representation kept standing for an analysis to read. Refused by what the
                // node is, not by whether the operation is one this emitter knows: the tree this
                // emits from keeps none, and one arriving means it was handed another tree.
                case Core.PreservedCall p -> throw p.unexpectedIn("the emitter");
                case Core.Int x -> code.loadConstant(x.value());
                // A literal is a constant of the class it is written in, and is loaded as one: a
                // dynamic constant the JVM resolves once, by running the BigDecimal constructor
                // on the spelling, and answers from the constant pool thereafter. Constructing it
                // where it stands would parse the spelling on every evaluation, and a literal in
                // the step of a fold is evaluated once per element.
                case Core.Decimal x -> code.ldc(DynamicConstantDesc.ofNamed(
                        ConstantDescs.BSM_INVOKE, "decimal", CD_BigDecimal,
                        MethodHandleDesc.ofConstructor(CD_BigDecimal, CD_String),
                        x.value().toString()));
                case Core.Str x -> code.loadConstant(x.value());
                case Core.Bool x -> {
                    if (x.value()) code.iconst_1(); else code.iconst_0();
                }
                case Core.Temporal t -> temporal(t);
                case Core.Read v -> {
                    Var var = locals.get(v.binding());
                    if (var == null) {
                        throw new IllegalStateException("unbound identifier `" + v.name() + "`");
                    }
                    load(code, var.slot(), var.type());
                }
                // a unit type has exactly one value, and naming it is that value (spec §unit-data).
                // Which unit is on the node: a body expanded in from another module's published
                // helper names that module's unit, which this module need not declare at all — and,
                // if it declares one spelled the same, is not the same unit.
                case Core.UnitValue u -> loadSharedInstance(code, cd(u.data()));
                // Negating a Decimal goes to the runtime that owns Decimal arithmetic, as the
                // binary operators do (ADR-0112). This one is total, so calling BigDecimal here
                // would be sound — what it would cost is the next reader having to work out which
                // of these are (BodyGen.java:1725).
                case Core.Neg n -> {
                    if (genExpr(n.operand()) == Type.DECIMAL) {
                        code.invokestatic(CD_DecimalMath, "negate",
                                MethodTypeDesc.of(CD_BigDecimal, CD_BigDecimal));
                    } else {
                        code.lneg();               // Int is carried as a long
                    }
                }
                case Core.FieldAccess fa -> {
                    Type targetType = genExpr(fa.target());
                    emitFieldRead(code, ((Type.Ref) targetType).name(), fa.field(), fa.type());
                }
                case Core.If iff -> {
                    genExpr(iff.cond());
                    Label elseL = code.newLabel();
                    Label end = code.newLabel();
                    Type want = shapeOf(iff, expected);
                    code.ifeq(elseL);
                    probe(iff, 0);
                    genExpr(iff.then(), want);
                    code.goto_(end);
                    code.labelBinding(elseL);
                    probe(iff, 1);
                    genExpr(iff.els(), want);
                    code.labelBinding(end);
                }
                case Core.IfConstructed ic -> {
                    Attempt a = emitAttempt(ic);
                    Label end = code.newLabel();
                    bind(ic.binder(), a.slot(), ic.construct().type());
                    probe(ic, 0);
                    genExpr(ic.then(), shapeOf(ic, expected));
                    code.goto_(end);

                    code.labelBinding(a.elseLabel());
                    emitDepartures(ic, a, body -> genExpr(body, shapeOf(ic, expected)), end);
                    code.labelBinding(end);
                }
                case Core.OptionSome s -> {
                    // `Option.some` takes the value as an Object, so a primitive element boxes here
                    // the way a list element does.
                    genExpr(s.value());
                    JvmTypes.box(code, s.value().type());
                    code.invokestatic(CD_Option, "some",
                            MethodTypeDesc.of(CD_Option, ConstantDescs.CD_Object), true);
                }
                case Core.OptionNone _ ->
                        code.invokestatic(CD_Option, "none", MethodTypeDesc.of(CD_Option), true);
                case Core.Unreachable u -> unreachable(u, expected);
                case Core.ListLit lit -> listLit(lit);
                case Core.Tuple t -> tuple(t);
                case Core.TupleGet tg -> tupleGet(tg);
                case Core.Binary bin -> {
                    if (binary(bin) != null) {
                        comparisonProbe(bin);
                    }
                }
                case Core.Construct nd -> construct(nd);
                case Core.Match m -> match(m, expected);
                case Core.Call c -> call(c);
                case Core.Apply a -> applyFn(a, (Type.FnOf) a.fn().type());
                case Core.LetIn li -> {
                    // a `let` outside tail position: bind, then value the body
                    Type vt = li.value().type();
                    if (vt instanceof Type.FnOf fn) {
                        // a lambda chosen at runtime (e.g. by an `if`): a first-class Fn (spec §blocks),
                        // at the parameter types the checker inferred from its applications
                        emitFunctionValue(li.value(), fn.params());
                    } else {
                        genExpr(li.value(), vt);
                    }
                    int s = slot(vt);
                    store(code, s, vt);
                    bind(li.binder(), s, vt);
                    emitLine(li);   // re-pin: a bound value may have moved the line off the call
                    genExpr(li.body(), expected);
                }
                // a block has no value of its own; it is inlined by the call it is passed to
                case Core.Block _ -> throw new IllegalStateException("a block is not a value");
            }
            // `unreachable` is typed Never, and what is on the stack is the shape the position asked
            // for, so that is what the caller is told is there.
            if (e instanceof Core.Unreachable && expected != null && !(expected instanceof Type.Never)) {
                return expected;
            }
            return e.type();
        }

        /** The type the branches of {@code e} leave on the stack: what the position asked for, or —
         * where it asked for nothing — the one the checker joined the branches at. A branch that
         * answers {@code unreachable} has no type of its own to merge with the others, so it takes
         * this one. */
        private Type shapeOf(Core e, Type expected) {
            return expected != null ? expected : e.type();
        }

        /**
         * {@code unreachable "reason"}: the abort, and the shape the position it stands in holds.
         *
         * <p>The abort is a call rather than an {@code athrow} so that the arm leaves a value where
         * its siblings leave one, and the code around it verifies as any other arm's does. The shape
         * is what the position asked for, or what the checker recorded when it asked for nothing;
         * neither leaves one where {@code Never} survives to here, and that position is refused
         * rather than emitted.
         */
        private void unreachable(Core.Unreachable u, Type expected) {
            Type shape = expected != null ? expected : u.type();
            if (shape instanceof Type.Never) {
                throw CompileException.of(Diagnostic
                                .at(u.pos(), "unreachable".length())
                                .hint(new NameMessage.WriteItWhereTheTypeIsStated()).say(new NameMessage.NothingSaysWhatThisPositionHolds()).build());
            }
            code.loadConstant(abortMessage(u));
            code.invokestatic(CD_UnreachableReached, "reached", MTD_reached);
            stackCast(shape);
        }

        /**
         * What the abort says: the reason the model wrote, and the line and column it wrote it at.
         *
         * <p>No file name. This compiler is not given one — a generated class's {@code SourceFile}
         * is derived from its module name rather than threaded down from a path — and deriving one
         * here would name a file that need not exist. A reader at run time has the frame's own file
         * and line; a reader of E1911 has the row's place beside this one.
         *
         * <p>Which is why an {@code unreachable} that came in with an inlined helper of another file
         * says its reason and no numbers. A place with no file beside it is read against the frame's,
         * and the frame's file is this class's — so a line of the helper's file put here would be
         * read as a line of this one. The reason is what the model wrote and stands on its own.
         */
        private String abortMessage(Core.Unreachable u) {
            souther.compiler.diag.PhysicalPos sits = ctx.sits(u.pos());
            return sits == null ? u.reason() : u.reason() + " (" + sits + ")";
        }

        private void match(Core.Match m, Type expected) {
            Type st = genExpr(m.scrutinee());
            int sSlot = slot(st);
            store(code, sSlot, st);
            Label end = code.newLabel();
            Type want = shapeOf(m, expected);
            for (int i = 0; i < m.cases().size(); i++) {
                Core.Case c = m.cases().get(i);
                Label nextCase = code.newLabel();
                // A case binding is scoped to its arm: save any outer binding it shadows and restore it
                // after the arm, or a later arm reusing the name would resolve to this arm's slot.

                emitCaseGuard(c, sSlot, st, nextCase);
                probe(m, i);
                genExpr(c.body(), want);
                if (c.binder() != null) {
                }
                code.goto_(end);
                code.labelBinding(nextCase);
            }
            emitMatchFallthrough();
            code.labelBinding(end);
        }

        /** Emits {@code match} in tail position: each arm body is emitted through {@link #emitTail}, so
         * a tail-position self-call inside an arm (as a self-hosted fold makes, matching {@code
         * List.get}) loops rather than recursing. Each arm returns (or tail-loops), so no join label is
         * needed — the next arm's dispatch follows its predecessor's {@code nextCase}. */
        private void emitTailMatch(Core.Match m, ClassDesc cdB,
                                   Set<ValueName.Behavior> requiredNames,
                                   Map<ValueName.Behavior, Type> requiredSuccess, Type expected) {
            Type st = genExpr(m.scrutinee());
            int sSlot = slot(st);
            store(code, sSlot, st);
            for (int i = 0; i < m.cases().size(); i++) {
                Core.Case c = m.cases().get(i);
                Label nextCase = code.newLabel();
                // A case binding is scoped to its arm (see {@link #match}): restore any outer binding it
                // shadows before the next arm's dispatch.

                emitCaseGuard(c, sSlot, st, nextCase);
                probe(m, i);
                emitTail(c.body(), cdB, requiredNames, requiredSuccess, expected);
                if (c.binder() != null) {
                }
                code.labelBinding(nextCase);
            }
            emitMatchFallthrough();
        }

        /**
         * The dispatch and case binding for one {@code match} arm; on no match, jumps to
         * {@code nextCase}. Shared by value-position {@link #match} and tail-position
         * {@link #emitTailMatch} so the two stay in step.
         *
         * <p>What each selector tests and what the arm binds are read off the pattern the checker
         * resolved. Nothing here asks whether the subject was an optional or what a case name means:
         * a carrier that is the value, one that wraps it, and one that holds nothing are three arms
         * of {@link Refinement}, and the emission follows the arm rather than working out which it
         * would have been.
         */
        private void emitCaseGuard(Core.Case c, int sSlot, Type st, Label nextCase) {
            CaseGen.jumpUnlessAny(code, ctx, c.pattern().selectors(), sSlot, nextCase);
            bindArm(c, sSlot, st);
        }

        /** Reads the arm's value out of the carrier and binds it. A wrapping carrier is opened
         *  whether or not the arm names what it holds, as it always was: opening it is how the value
         *  under it is reached at all. */
        private void bindArm(Core.Case c, int sSlot, Type st) {
            switch (c.pattern().binding()) {
                case Refinement.OptionPresent wrapped -> {
                    Type element = wrapped.bound();
                    CaseGen.pushBound(code, wrapped, sSlot);
                    int bslot = slot(element);
                    unbox(code, element, bslot);
                    if (c.binder() != null) {
                        bind(c.binder(), bslot, element);
                    }
                }
                case Refinement.Direct itself -> {
                    Type bound = itself.bound();
                    if (c.binder() == null || bound == null) {
                        return;
                    }
                    if (bound.equals(st)) {
                        // nothing narrowed it: the value is the subject, where it already is
                        bind(c.binder(), sSlot, st);
                        return;
                    }
                    // a data case binds the instance; a primitive case (e.g. Int) unboxes the value
                    CaseGen.pushBound(code, itself, sSlot);
                    int bslot = slot(bound);
                    unbox(code, bound, bslot);
                    bind(c.binder(), bslot, bound);
                }
                case Refinement.OptionAbsent _ -> { }
            }
        }

        /** The unreachable tail of a {@code match}: it is exhaustive by construction (the checker), so
         * falling past every arm throws rather than returning a bogus value. */
        private void emitMatchFallthrough() {
            code.new_(CD_IllegalStateException);
            code.dup();
            code.invokespecial(CD_IllegalStateException, "<init>", MTD_void);
            code.athrow();
        }

        private void construct(Core.Construct nd) {
            Hir.Data owner = (Hir.Data) symbols.declaredNode(nd.typeName());
            Map<String, Type> flds = fieldTypes(owner);
            ClassDesc cdType = cd(nd.typeName());
            TypeSymbol.AtModule built = nd.typeName();
            // A type of another module is built through its checked entry: `new` reaches a constructor
            // that is not public, and the checked entry is the declared path either way.
            if (DataChecker.isInvariantBearing(built, symbols) || symbols.scope().isForeign(built)) {
                // In value position (a match arm, a non-tail let, a call argument, ...) the checked
                // construction goes through __construct just as it does in tail (see emitTail): the
                // invariant runs and orThrow either yields the value or aborts with a
                // ConstraintViolation. orThrow returns Object, so narrow it back to the value type.
                emitFieldValues(flds, nd.values());
                emitLine(nd);   // re-pin: a field init may have moved the line off the construction
                finishInvariantConstruct(cdType, flds);
                return;
            }
            MethodTypeDesc ctor = MethodTypeDesc.of(ConstantDescs.CD_void, fieldDescs(flds));
            if (!walksInside(nd)) {
                code.new_(cdType);
                code.dup();
                emitFieldValues(flds, nd.values());
                code.invokespecial(cdType, "<init>", ctor);
                return;
            }
            // A field built by a walk is built through a loop, and a value under construction may not
            // be live across a backwards branch: the reference `new` leaves is uninitialised until
            // `<init>`, and the verifier will not carry one over a jump. So the fields are built first
            // and held in slots, exactly as a newtype's single field already is, and the construction
            // itself is the straight line at the end.
            emitFieldValues(flds, nd.values());
            List<Type> fieldTypes = new ArrayList<>(flds.values());
            int[] held = new int[fieldTypes.size()];
            for (int i = fieldTypes.size() - 1; i >= 0; i--) {
                held[i] = slot(fieldTypes.get(i));
                store(code, held[i], fieldTypes.get(i));
            }
            code.new_(cdType);
            code.dup();
            for (int i = 0; i < fieldTypes.size(); i++) {
                load(code, held[i], fieldTypes.get(i));
            }
            code.invokespecial(cdType, "<init>", ctor);
        }

        /** Whether emitting {@code e} emits a loop — a fold, or the walk a fold that grows a collection
         *  became. What it decides is whether a value being constructed can stay on the stack while its
         *  fields are built. */
        private boolean walksInside(Core e) {
            if (e instanceof Core.Call c
                    && ((c.fn() instanceof Core.Reached r
                            && ctx.symbols.theWalk().equals(r.denotes()))
                    || c.fn() == Core.Emitted.BUILD_LIST || c.fn() == Core.Emitted.BUILD_MAP)) {
                return true;
            }
            boolean[] found = {false};
            Core.forEachChild(e, child -> found[0] |= walksInside(child));
            return found[0];
        }

        /** Where an attempt's failing side continues, and the slot holding the value its success side
         * names. The binder is the caller's to scope, because how far the success branch reaches
         * differs between value and tail position. */
        private record Attempt(Label elseLabel, int slot, int resultSlot) {}

        /**
         * Emits an attempted construction up to its branch: the same {@code __construct} a plain
         * construction calls, with the {@code Result} branched on rather than handed to
         * {@code ConstraintViolation.orThrow}. The success value is left in the returned slot and the
         * Err side jumps to the returned label; the caller emits the two branches, which is where
         * value position and tail position part company.
         */
        private Attempt emitAttempt(Core.IfConstructed ic) {
            Core.Construct nd = ic.construct();
            Map<String, Type> flds = fieldTypes((Hir.Data) symbols.declaredNode(nd.typeName()));
            ClassDesc cdType = cd(nd.typeName());
            emitFieldValues(flds, nd.values());
            emitLine(ic);   // re-pin: a field init may have moved the line off the construction
            code.invokestatic(cdType, "__construct", MethodTypeDesc.of(CD_Result, fieldDescs(flds)));

            int rSlot = slot(Type.STRING);   // a reference slot, as the codecs take for the same Result
            code.astore(rSlot);
            code.aload(rSlot);
            code.instanceOf(CD_ResultErr);
            Label elseL = code.newLabel();
            code.ifne(elseL);

            int bound = slot(nd.type());
            code.aload(rSlot);
            code.checkcast(CD_ResultOk);
            code.invokevirtual(CD_ResultOk, "value", MTD_Object);
            code.checkcast(cdType);
            store(code, bound, nd.type());
            return new Attempt(elseL, bound, rSlot);
        }

        /**
         * Emits an attempt's departures at its else label. One departure naming no clause is the whole
         * of it — any failure is that value. Several are a lookup on the clause the {@code Result}
         * carries: the arms are compared in turn and one is left to fall through, so nothing is emitted
         * for the case where no clause matches. The checker has established there is none — every named
         * clause has an arm, and {@code | _ ->} is there exactly when clauses carry no name.
         *
         * <p>{@code end} is where a departure jumps once its value is built, or null in tail position,
         * where each departure returns instead.
         */
        private void emitDepartures(Core.IfConstructed ic, Attempt a, Consumer<Core> emit, Label end) {
            List<Core.ElseArm> arms = ic.els();
            // The attempt's own arm is 0, so a departure's number is one past where it sits.
            if (arms.size() == 1 && arms.get(0).clause().isEmpty()) {
                probe(ic, 1);
                emit.accept(arms.get(0).body());
                return;
            }
            code.aload(a.resultSlot());
            code.checkcast(CD_ResultErr);
            code.invokevirtual(CD_ResultErr, "error", MTD_Object);
            code.checkcast(CD_InvariantFailure);
            code.invokevirtual(CD_InvariantFailure, "clause", MTD_failureClause);
            int cSlot = slot(Type.STRING);
            code.astore(cSlot);
            // One arm is left to fall through, so it needs no comparison: the `| _ ->` where there is
            // one, and otherwise the last arm — with every clause named and answered, a failure that
            // matched none of the others is that one.
            int fallthrough = arms.size() - 1;
            for (int i = 0; i < arms.size(); i++) {
                if (arms.get(i).clause().isEmpty()) {
                    fallthrough = i;
                }
            }
            List<Label> labels = new ArrayList<>();
            for (int i = 0; i < arms.size(); i++) {
                if (i == fallthrough) {
                    labels.add(null);
                    continue;
                }
                Label armL = code.newLabel();
                labels.add(armL);
                // The clause is null for a rule declared without a name, so the constant is the
                // receiver of the comparison and the read value the argument.
                code.loadConstant(arms.get(i).clause().get());
                code.aload(cSlot);
                code.invokevirtual(CD_String, "equals", MTD_equalsObject);
                code.ifne(armL);
            }
            probe(ic, fallthrough + 1);
            emit.accept(arms.get(fallthrough).body());
            for (int i = 0; i < arms.size(); i++) {
                if (i == fallthrough) {
                    continue;
                }
                if (end != null) {
                    code.goto_(end);
                }
                code.labelBinding(labels.get(i));
                probe(ic, i + 1);
                emit.accept(arms.get(i).body());
            }
        }

        /** Emits the checked-construction tail — {@code __construct(fields) -> Result}, {@code orThrow}
         * (yield, or abort on invariant violation), and a narrowing cast — with the field values
         * already on the stack. */
        private void finishInvariantConstruct(ClassDesc cdType, Map<String, Type> flds) {
            code.invokestatic(cdType, "__construct", MethodTypeDesc.of(CD_Result, fieldDescs(flds)));
            code.invokestatic(CD_ConstraintViolation, "orThrow", MTD_orThrow);
            code.checkcast(cdType);
        }

        Type varType(Core.Read read) {
            return locals.get(read.binding()).type();
        }

        // --- the surface Intrinsics drives to emit a shipped primitive (ADR-0028) ---

        /** Boxes an {@code Int}/{@code Bool} on the stack for an erased ({@code Object}) runtime slot;
         * a no-op for a reference, which is already an {@code Object}. */
        void emitBox(Type type) {
            box(code, type);
        }

        void emitInvokeStatic(ClassDesc owner, String method, MethodTypeDesc desc) {
            code.invokestatic(owner, method, desc);
        }

        void emitInvokeVirtual(ClassDesc owner, String method, MethodTypeDesc desc) {
            code.invokevirtual(owner, method, desc);
        }

        /** Puts a function value on the stack as an {@code Fn}, at the parameter types the position
         * fixed. A kernel that applies a function needs one; see {@link Intrinsics.TakesAFunction}. */
        void emitFn(Core value, List<Type> paramTypes) {
            emitFunctionValue(value, paramTypes);
        }

        /**
         * Emits a call to a kernel.
         *
         * <p>What is written here is what a table row cannot say, and it is of two kinds. An
         * enumeration's order lives on its sum, so the ordered family is handed a comparator rather
         * than reading a {@code Comparable} off the value (issue #161) — the arm puts the comparator
         * on the stack and the row still says what is called with it. A partial Int division answers
         * a case rather than a number when its divisor is zero, so it emits a branch, which the row
         * shape of one call with one result has nowhere to put; those two are the whole of what this
         * emits itself, and {@code WRITTEN_OUT} is where they are named. {@code Decimal.divide} was
         * a third: it is an ordinary kernel now, and its zero divisor is answered by the runtime
         * that owns the operation (ADR-0112).
         *
         * <p>An ordered arm falls through to the table where its own condition does not hold — an
         * element the JVM already compares, a {@code sortBy} whose key answers something with no sum
         * to take an ordering off. What no arm and no row answers is this backend being behind the
         * library, which {@link Intrinsics#emit} says.
         */
        private void kernel(Kernel kernel, Core.Call call) {
            if (ORDERED_BY_COMPARATOR.contains(kernel)) {
                TypeSymbol ordering = orderingFor(kernel, call);
                // No sum to take an ordering off: an element the JVM already compares, or a `sortBy`
                // whose key answers one. Those go to the table row, which is the same runtime method
                // without the comparator.
                if (ordering != null) {
                    code.invokestatic(cd(ordering), ORDERING_METHOD, MTD_ordering, true);
                    Intrinsics.emitWithComparator(this, kernel, call);
                    return;
                }
            }
            switch (kernel) {
                case INT_DIVIDE -> {
                    intDivide(call, true);
                    return;
                }
                case INT_TRUNCATING_REMAINDER -> {
                    intDivide(call, false);
                    return;
                }
                default -> { }
            }
            Intrinsics.emit(this, kernel, call);
        }

        /** The sum an ordered kernel takes its comparator off, or null where there is none.
         *
         * <p>{@code sortBy} orders by what its key answers, not by what the list holds, so its
         * comparator is read off the key's result type; the rest order the elements themselves. */
        private TypeSymbol orderingFor(Kernel kernel, Core.Call call) {
            if (kernel == Kernel.LIST_SORT_BY) {
                return call.args().get(0).type() instanceof Type.FnOf key
                        ? sumOrdering(key.result()) : null;
            }
            return elementOrdering(call.args().get(0));
        }

        /** The kernels whose runtime method takes a comparator ahead of what the declaration names,
         *  where the element has a sum to take an ordering off. Read by the arm above rather than
         *  written out in it, so that the kernels routed there are the kernels this names — what
         *  holds the derived boundary form of one is a test, and a test can only reach the ones it
         *  can be told about. */
        static final Set<Kernel> ORDERED_BY_COMPARATOR = Set.of(
                Kernel.LIST_SORT, Kernel.LIST_MAX, Kernel.LIST_MIN, Kernel.LIST_SORT_BY);

        /** The kernels this emits itself, which are the kernels {@link Intrinsics}' table has no row
         *  for. Named rather than left to be read off the arms above, so the two sets can be held
         *  apart: a kernel emitted here and held there too would be one operation with two answers,
         *  and the one that ran would be whichever the arm above happened to reach first. */
        static final Set<Kernel> WRITTEN_OUT =
                Set.of(Kernel.INT_DIVIDE, Kernel.INT_TRUNCATING_REMAINDER);

        private void call(Core.Call call) {
            // Which kernel a call reaches is on the call, so what is emitted for one is asked of
            // the operation. Matched against the rendered reach name instead, these arms would turn
            // on the alias the library publishes the operation under.
            if (call.fn() instanceof Core.Reached.OfKernel(_, Kernel kernel)) {
                kernel(kernel, call);
                return;
            }
            if (call.fn() == Core.Emitted.BUILD_LIST) {
                buildList(call);
                return;
            }
            if (call.fn() == Core.Emitted.GROW_LIST) {
                growList(call);
                return;
            }
            if (call.fn() == Core.Emitted.BUILD_MAP) {
                buildMap(call);
                return;
            }
            if (call.fn() == Core.Emitted.PUT_MAP) {
                putIntoMap(call);
                return;
            }
            // What running this call means, asked of the call. Matched against the tables this
            // emitter happens to hold, the answer was whichever table the rendered name hit first —
            // and a helper the module holds under a name this call renders differently was no
            // helper at all.
            if (!(call.fn() instanceof Core.Reached.OfDeclaration reached)) {
                throw new IllegalStateException("unknown function `" + call.name() + "`");
            }
            switch (reached.reaches()) {
                case Core.Reaches.AHelper _ -> {
                    // The one loop the language has is emitted where it stands, not called.
                    if (!ctx.symbols.theWalk().equals(reached.denotes()) || !folded(call)) {
                        recursiveHelperCall(call);
                    }
                }
                // Which of the two it is, is where the value of the behavior stands in this frame:
                // one supplied to the class being emitted is read off it, one implemented elsewhere
                // is called. Neither is a question about what the call reaches.
                case Core.Reaches.ABehavior(ValueName.Behavior behavior) -> {
                    if (reqNames.contains(behavior)) {
                        requiredCall(call);
                    } else if (ctx.calleeSig(behavior) != null) {
                        behaviorCall(call);
                    } else {
                        throw new IllegalStateException("`" + call.name() + "` reaches the behavior "
                                + behavior + ", which is neither supplied to this class nor"
                                + " implemented by a module this one was told about");
                    }
                }
            }
        }

        /** A temporal the source spelled out. The checker read the text when it typed the form, so
         * this is a parse of a constant that is known to parse, and which parse to run is the kind
         * on the node.
         *
         * <p>It used to be a call whose spelling this compared against the four temporals, so a
         * model declaring a behavior of its own named {@code Date} had its own behavior emitted as
         * this. A written temporal is a value here, and a call is a call. */
        private void temporal(Core.Temporal t) {
            ClassDesc cd = JvmTypes.boxedPrim(t.kind());
            code.loadConstant(t.text());
            code.invokestatic(cd, "parse", MethodTypeDesc.of(cd, CD_CharSequence));
        }

        /** Calls a recursive helper as a static method on {@code $Fns} (spec §fn-declaration): each argument is
         * evaluated and boxed, the {@code invokestatic} returns {@code Object}, and the result is cast
         * back to the helper's declared return type. A self- or mutual call reaches here the same way.
         * A function parameter is passed as a first-class {@code Fn} value (a closure): the argument
         * block is materialised rather than evaluated as a plain value, and an {@code Fn} is already a
         * reference, so it fits the {@code Object} slot without boxing. */
        private void recursiveHelperCall(Core.Call call) {
            // The checker resolved this call's type variables when it typed it — the accumulator a
            // fold's step runs at, the result the caller casts to — and left the decision on the
            // nodes, so nothing is resolved a second time here (issue #81).
            for (Core arg : call.args()) {
                if (arg.type() instanceof Type.FnOf fn) {
                    if (stepNeverRuns(fn)) {
                        code.getstatic(CD_Fn, "NEVER", CD_Fn);
                    } else {
                        emitFunctionValue(arg, fn.params());
                    }
                } else {
                    box(code, genExpr(arg));
                }
            }
            invokeRecursiveHelper(call);
            castFromObject(code, call.type());
        }

        /**
         * A fold that only grows a list: the walk carries a builder and seals it at the end
         * ({@code GrowingFold}). The step is materialised as it is for the fold this was rewritten
         * from — an empty list still hands over {@code Fn.NEVER} — and the seed is gone, because the
         * builder is the seed.
         */
        private void buildList(Core.Call call) {
            if (walked(call, CD_Lists, MTD_Lists_builder, MTD_Lists_sealed)) {
                return;
            }
            emitStep(call.args().get(0));
            genExpr(call.args().get(1));      // the list walked
            genExpr(call.args().get(2));      // the index walked from (a long)
            code.invokestatic(CD_Lists, "build", MTD_Lists_build);
        }

        /**
         * {@code acc ++ …} inside such a walk: the accumulator is the builder, so this adds to it
         * rather than building a new vector. As with {@code ++} itself, a one-element list literal on
         * the right pushes its element instead of the list around it.
         */
        private void growList(Core.Call call) {
            genExpr(call.args().get(0));
            Core added = call.args().get(1);
            if (added instanceof Core.ListLit lit && lit.elements().size() == 1) {
                box(code, genExpr(lit.elements().get(0)));
                code.invokestatic(CD_Lists, "grow", MTD_Lists_grow);
            } else {
                genExpr(added);
                code.invokestatic(CD_Lists, "growAll", MTD_Lists_growAll);
            }
        }

        /** The same walk for a fold accumulating a map: the builder is the seed and the map it built
         *  is handed over at the end. */
        private void buildMap(Core.Call call) {
            if (walked(call, CD_Maps, MTD_Maps_builder, MTD_Maps_sealed)) {
                return;
            }
            emitStep(call.args().get(0));
            genExpr(call.args().get(1));      // the list walked
            genExpr(call.args().get(2));      // the index walked from (a long)
            code.invokestatic(CD_Maps, "build", MTD_Maps_build);
        }

        /**
         * A fold as the loop it is: the seed in a local, the list walked by its iterator, and the
         * step's own body as the loop body reading the accumulator and the element from locals.
         * Answers whether it was emitted that way — a walk that starts past the head, or whose step
         * is one that never runs, is left to the {@code foldFrom} method (spec §fn-declaration).
         *
         * <p>This is what makes a fold cost what a loop costs, and {@code fold} is the one loop the
         * language has, so it is the whole of what a program's loops cost. Handed to a method instead,
         * the step has to be a first-class {@code Fn}: a class of its own, an {@code Object[]} of
         * boxed arguments per element, an accumulator that is boxed because {@code apply} answers with
         * an {@code Object} — and, where the method walks for every fold in the program, a call site
         * seeing so many different steps that it stops being inlined at all. Emitted here, the step's
         * body reads the locals it closes over directly, an {@code Int} accumulator stays a
         * {@code long} in its slot, and each walk is straight-line code the JIT sees on its own.
         */
        private boolean folded(Core.Call call) {
            if (!(call.args().get(3) instanceof Core.Int from) || from.value() != 0) {
                return false;
            }
            Core seed = call.args().get(1);
            return walked(call.args().get(0), call.args().get(2), () -> {
                Type produced = genExpr(seed);
                asAccumulator(produced, accumulatorOf(call.args().get(0)));
            }, () -> { });
        }

        /** The same walk, accumulating into a builder that is sealed at the end (see
         *  {@link GrowingFold}): the seed is the builder, and the list or map it built is what the
         *  walk answers with. */
        private boolean walked(Core.Call call, ClassDesc helpers,
                               MethodTypeDesc builder, MethodTypeDesc sealed) {
            return walked(call.args().get(0), call.args().get(1),
                    () -> code.invokestatic(helpers, "builder", builder),
                    () -> code.invokestatic(helpers, "sealed", sealed));
        }

        /** The accumulator's type, as the checker put it on the step. */
        private static Type accumulatorOf(Core step) {
            return ((Type.FnOf) step.type()).params().get(0);
        }

        /** Leaves the value on the stack in the accumulator's own form: boxed where the accumulator is
         *  a reference, and left as it is where the accumulator is an {@code Int} or a {@code Bool},
         *  which stays in its slot as a primitive for the length of the walk. */
        private void asAccumulator(Type produced, Type accType) {
            if (accType != Type.INT && accType != Type.BOOL) {
                box(code, produced);
            }
        }

        private boolean walked(Core stepValue, Core walked, Runnable seed, Runnable answer) {
            if (!(stepValue instanceof Core.Block step)
                    || !(step.type() instanceof Type.FnOf fn) || stepNeverRuns(fn)) {
                return false;
            }
            Type accType = fn.params().get(0);
            Type elementType = fn.params().get(1);

            int iterator = slot(Type.STRING);   // a reference slot; the type is not read back
            genExpr(walked);
            code.invokeinterface(CD_List, "iterator", MTD_iterator);
            code.astore(iterator);

            int acc = slot(accType);
            seed.run();
            store(code, acc, accType);

            // The step's body is emitted here rather than in a class of its own, so the names it binds
            // would otherwise stay bound after the walk — and a step that destructures its accumulator
            // binds ordinary names (`let (i, ys) = acc`), which are the caller's names as often as not.
            // The whole scope is put back, the slots it took staying taken.
            Map<BindingId, Var> enclosing = new HashMap<>(locals);
            bind(step.params().get(0), acc, accType);
            int element = slot(elementType);
            bind(step.params().get(1), element, elementType);

            Label next = code.newLabel();
            Label done = code.newLabel();
            code.labelBinding(next);
            code.aload(iterator);
            code.invokeinterface(CD_Iterator, "hasNext", MTD_hasNext);
            code.ifeq(done);
            code.aload(iterator);
            code.invokeinterface(CD_Iterator, "next", MTD_next);
            unbox(code, elementType, element);
            Type stepped = genExpr(step.body());   // the accumulator the step answers with
            asAccumulator(stepped, accType);
            store(code, acc, accType);
            countOneStep();
            code.goto_(next);
            code.labelBinding(done);

            locals.clear();
            locals.putAll(enclosing);
            load(code, acc, accType);
            answer.run();
            return true;
        }

        /** {@code Map.insert(key, value, acc)} inside such a walk: a write into the builder. */
        private void putIntoMap(Core.Call call) {
            genExpr(call.args().get(0));
            box(code, genExpr(call.args().get(1)));
            box(code, genExpr(call.args().get(2)));
            code.invokestatic(CD_Maps, "put", MTD_Maps_put);
        }

        /** The step of a build, as the fold it was rewritten from would have materialised it — an
         *  empty list still hands over {@code Fn.NEVER}. */
        private void emitStep(Core step) {
            if (step.type() instanceof Type.FnOf fn && !stepNeverRuns(fn)) {
                emitFunctionValue(step, fn.params());
            } else {
                code.getstatic(CD_Fn, "NEVER", CD_Fn);
            }
        }

        /**
         * Whether a step closure would never be applied: one of its parameters is the bare bottom, so
         * it is the element of an empty-literal list and there are no elements — {@code foldFrom} over
         * {@code []} yields the seed. Such a step is passed as {@link souther.runtime.Fn#NEVER} rather
         * than materialised, since materialising it would unbox the bottom element (as {@code acc + x}
         * does with {@code x}) and crash. An empty *seed* (a {@code List<Nothing>} accumulator) is a
         * reference and still materialises.
         */
        private static boolean stepNeverRuns(Type.FnOf fn) {
            for (Type p : fn.params()) {
                if (p instanceof Type.Nothing) {
                    return true;
                }
            }
            return false;
        }

        private void invokeRecursiveHelper(Core.Call call) {
            ClassDesc[] params = new ClassDesc[call.args().size()];
            java.util.Arrays.fill(params, CD_Object);
            code.invokestatic(ctx.cd(new GeneratedClass.Helpers(pkg)), CodegenContext.helperMethod(call.name()),
                    MethodTypeDesc.of(CD_Object, params));
        }

        /**
         * {@code divide}/{@code remainder} on Int: a zero divisor takes the DivisionByZero case,
         * otherwise the quotient/remainder is boxed (spec §stdlib-int).
         *
         * <p>The quotient is the operator's own. {@code Int.divide} answers a case where {@code /}
         * aborts on a zero divisor and answers the same number everywhere else, which is what the
         * check reads it as — so the one pair no {@code Int} holds a quotient of has to abort here
         * as it does there. A raw {@code ldiv} stood here and wrapped {@code Long.MIN_VALUE / -1}
         * back to {@code Long.MIN_VALUE}, which is the overflow §stdlib-int says aborts, answered as
         * a quotient.
         *
         * <p>The remainder is a raw {@code lrem}: it is exact for every pair, {@code MIN_VALUE}
         * against {@code -1} included, so there is no overflow for it to abort on.
         */
        private void intDivide(Core.Call call, boolean divide) {
            genExpr(call.args().get(0));
            int aSlot = slot(Type.INT);
            code.lstore(aSlot);
            genExpr(call.args().get(1));
            int bSlot = slot(Type.INT);
            code.lstore(bSlot);
            code.lload(bSlot);
            code.lconst_0();
            code.lcmp();
            Label zero = code.newLabel();
            Label end = code.newLabel();
            code.ifeq(zero);                       // b == 0 -> DivisionByZero case
            code.lload(aSlot);
            code.lload(bSlot);
            if (divide) {
                code.invokestatic(CD_IntMath, "divideExact", MTD_intExact);
            } else {
                code.lrem();
            }
            code.invokestatic(CD_Long, "valueOf", MTD_Long_valueOf);   // box the quotient
            code.goto_(end);
            code.labelBinding(zero);
            code.getstatic(CD_DivisionByZero, "INSTANCE", CD_DivisionByZero);
            code.labelBinding(end);
        }

        /**
         * Calls a behavior that depends on nothing (spec {@code [#calling-a-behavior]}). It is built
         * here rather than read out of a field: with an empty requirement set there is nothing to
         * inject, so its {@code $Impl} has a no-argument constructor. This is the same sequence a
         * {@code >->} stage already emits for a behavior it builds, and it reaches an imported one
         * the same way, because {@link CodegenContext#cdBehaviorImpl} resolves the declaring
         * module's package.
         *
         * <p>A single-input behavior's interface extends {@code Behavior}, so the erased
         * {@code apply} links; one with any other arity declares a typed {@code apply} of its own.
         */
        private void behaviorCall(Core.Call call) {
            ValueName.Behavior callee = behaviorOf(call);
            ReqSig sig = ctx.calleeSig(callee);
            ClassDesc impl = ctx.cdBehaviorImpl(callee);
            code.new_(impl);
            code.dup();
            code.invokespecial(impl, "<init>", MTD_void);
            if (sig.params().size() == 1) {
                Type at = genExpr(call.args().get(0));
                box(code, at);
                code.invokeinterface(CD_Behavior, "apply", MTD_apply);
                project(callee, sig.success());
                stackCast(sig.success());
                return;
            }
            for (Core arg : call.args()) {
                Type at = genExpr(arg);
                box(code, at);
            }
            code.invokeinterface(ctx.cdBehavior(callee), "apply",
                    ctx.typedApplyDesc(callee, sig.params(), sig.success()));
            project(callee, sig.success());
            stackCast(sig.success());
        }

        /** Emits an inline call to an injected required behavior, leaving its success value on
         * the stack cast to the success type (spec §unmarked-output, §fn). */
        private void requiredCall(Core.Call call) {
            ValueName.Behavior callee = behaviorOf(call);
            Type success = reqSuccess.get(callee);
            // An injected behavior's body is supplied from outside, so there is no `apply` of this
            // compiler's to hold it to what it declared. The line is the one the Decoder draws: where
            // an answer enters the domain. What the arguments were has to survive the call to be
            // handed to the check, so they are put in slots first — the call consumes what it is
            // pushed.
            List<Integer> saved = ctx.ensuresCheckOf(callee) instanceof EnsuresEnforcement.AtEachCrossing
                    ? new ArrayList<>() : null;
            if (ctx.isStandaloneRequired(callee)) {
                // other than one input: the required behavior is its own base class, called with a
                // typed invokevirtual apply(A,B,…); each arg is left as its declared param type
                // (issue #57). A `() -> R` produces, so the call hands it nothing.
                MethodTypeDesc desc = ctx.requiredApplyDesc(callee);
                code.aload(0);
                code.getfield(cdName, held.of(callee).fieldName(), ctx.cdBehavior(callee));
                for (Core arg : call.args()) {
                    Type at = genExpr(arg);
                    box(code, at);   // a primitive boxes to its apply-param type; a reference already matches
                    keepForTheCheck(saved);
                }
                code.invokevirtual(ctx.cdBehavior(callee), "apply", desc);
                project(callee, success);
                checkAtCrossing(callee, saved);
                stackCast(success);
                return;
            }
            code.aload(0);
            code.getfield(cdName, held.of(callee).fieldName(), CD_Behavior);
            Type at = genExpr(call.args().get(0));
            box(code, at);
            keepForTheCheck(saved);
            code.invokeinterface(CD_Behavior, "apply", MTD_apply);
            project(callee, success);
            checkAtCrossing(callee, saved);
            stackCast(success);
        }

        /** Keeps a copy of the boxed argument on the stack in a slot of its own, where a check is
         *  going to want it after the call has consumed it. Does nothing where none is coming. */
        private void keepForTheCheck(List<Integer> saved) {
            if (saved == null) {
                return;
            }
            int slot = slot(Type.NOTHING);
            code.dup();
            code.astore(slot);
            saved.add(slot);
        }

        /**
         * Holds an injected behavior's answer to what it declared, with the answer on the stack as
         * the boxed carrier {@code project} left it.
         *
         * <p>Between the projection and the cast, which is where the value is a Souther value and is
         * not yet the representation the code below runs on. A rule is written about the answer, so
         * it is read after the boundary's carrier is off it and before a primitive is taken out of
         * it.
         */
        private void checkAtCrossing(ValueName.Behavior callee, List<Integer> saved) {
            if (saved == null) {
                return;
            }
            int carrier = slot(Type.NOTHING);
            code.astore(carrier);
            for (int slot : saved) {
                code.aload(slot);
            }
            code.aload(carrier);
            List<ClassDesc> params = new ArrayList<>();
            for (int i = 0; i <= saved.size(); i++) {
                params.add(CD_Object);
            }
            code.invokestatic(ctx.cd(new GeneratedClass.Ensures(
                            new GeneratedClass.BehaviorInterface(callee.module(), callee.name()))),
                    "check",
                    MethodTypeDesc.of(ConstantDescs.CD_void, params.toArray(new ClassDesc[0])));
            code.aload(carrier);
        }

        /**
         * Turns the result of a behavior call back into a Souther value: a member the callee's module
         * declared arrives as itself, one that reached the union through a bridge case has its value
         * taken out. Runs over the callee's union members, which are known at the call, so the value
         * is never asked whether it happens to be wrapped.
         *
         * <p>Done at the call rather than at the {@code match} arm that reads it, because not every
         * result is matched: a body may answer with a call's result directly, and the bridge cases of
         * the callee's module are not members of this module's union. Projected here, the value is a
         * Souther value again and this behavior's own return puts it into its own bridge case.
         */
        private void project(ValueName.Behavior callee, Type calleeOut) {
            List<TypeSymbol> bridged = ctx.bridgedMembersOf(callee, calleeOut);
            ResultBoundary.project(code, ctx, callee, bridged, slot(Type.NOTHING));
        }

        /** Casts the {@code Object} on the stack to {@code type}, unboxing primitives. */
        private void stackCast(Type type) {
            if (type == Type.INT) {
                code.checkcast(CD_Long);
                code.invokevirtual(CD_Long, "longValue", MethodTypeDesc.of(ConstantDescs.CD_long));
            } else if (type == Type.BOOL) {
                code.checkcast(CD_Boolean);
                code.invokevirtual(CD_Boolean, "booleanValue", MethodTypeDesc.of(ConstantDescs.CD_boolean));
            } else if (!(type instanceof Type.Union)) {
                code.checkcast(jvmType(type));
            }
        }

        /**
         * The operator, emitted; and the comparison it was, where it was one.
         *
         * <p>A switch expression, because an enum switch statement is not held to covering its
         * type. What is wanted here is that an operator added to the language stops the compile
         * until this has decided what to emit for it, and only an expression asks javac for that.
         *
         * <p>What comes back is what a probe records, so a caller has the recognition rather than
         * the node and a lookup that answers for everything else.
         */
        private Comparison binary(Core.Binary bin) {
            return switch (bin.op()) {
                // Left to right, stopping as soon as the answer is settled. Not an optimisation:
                // `/` aborts on a zero divisor and `Int` overflows, so a left operand is how the
                // domain the right one is evaluated in gets narrowed, and `x /= 0 && 100 / x > 1`
                // is a guard or it is nothing. `iand` and `ior` are the eager conjunction of two
                // booleans, which is a different operator from the one the language has.
                case AND -> {
                    genExpr(bin.left());
                    Label settled = code.newLabel();
                    Label end = code.newLabel();
                    code.ifeq(settled);
                    genExpr(bin.right());
                    code.goto_(end);
                    code.labelBinding(settled);
                    code.iconst_0();
                    code.labelBinding(end);
                    yield null;
                }
                case OR -> {
                    genExpr(bin.left());
                    Label settled = code.newLabel();
                    Label end = code.newLabel();
                    code.ifne(settled);
                    genExpr(bin.right());
                    code.goto_(end);
                    code.labelBinding(settled);
                    code.iconst_1();
                    code.labelBinding(end);
                    yield null;
                }
                // `+ - * /` work on two Int or two Decimal operands (spec
                // §an-operator-takes-the-types-it-is-defined-for). Int aborts on overflow, and `/`
                // aborts on a zero divisor; Decimal aborts at the ends of the scale range, and its
                // `/` rounds by the default scale/mode and aborts on a zero divisor too. Case
                // handling for a zero divisor is the divide/remainder functions, not the operator.
                //
                // Both go through the runtime that owns the arithmetic — IntMath and DecimalMath —
                // rather than to a host method. What an operator means is the runtime's, and calling
                // BigDecimal here is what let a scale overflow leave a behavior as a
                // java.lang.ArithmeticException (ADR-0112, issue #976). This said "Decimal does not
                // overflow", which is not true of a sum, a difference or a product either.
                //
                // One arm each, and the runtime's method named in it. Read off the operator a
                // second time inside a single arm, the last of the four is whatever is left over,
                // and an arithmetic operator added to that arm takes the leftover's method.
                case ADD -> { arithmetic(bin, "add", "addExact"); yield null; }
                case SUB -> { arithmetic(bin, "subtract", "subtractExact"); yield null; }
                case MUL -> { arithmetic(bin, "multiply", "multiplyExact"); yield null; }
                case DIV -> { arithmetic(bin, "divide", "divideExact"); yield null; }
                case CONCAT -> {
                    Type lt = genExpr(bin.left());
                    // `++` over two strings is Elm's appendable on String; the checker guarantees both
                    // sides are String here, so emit `a.concat(b)` rather than the list join.
                    if (lt == Type.STRING) {
                        genExpr(bin.right());
                        code.invokevirtual(CD_String, "concat",
                                MethodTypeDesc.of(CD_String, CD_String));
                    } else if (bin.right() instanceof Core.ListLit lit && lit.elements().size() == 1) {
                        // `acc ++ [x]` is how every fold-derived combinator grows its list
                        // (souther.list's map/filter), so it runs once per element. Push the element
                        // itself: building a one-element list for `concat` to immediately take apart
                        // costs an ArrayList, a copyOf, and an iterator on the hot path.
                        box(code, genExpr(lit.elements().get(0)));
                        code.invokestatic(CD_Lists, "append", MTD_Lists_append);
                    } else {
                        genExpr(bin.right());
                        code.invokestatic(CD_Lists, "concat", MTD_Lists_concat);
                    }
                    yield null;
                }
                case EQ, NE, LT, LE, GT, GE -> {
                    // The recognition is here whenever this arm is, because what an operator places
                    // is a claim exactly where it compares — which is a law and not an agreement
                    // between two lists ({@code WhatAnOperatorPlacesIsOneAnswerTest}). Asked for
                    // rather than assumed all the same, so that a comparison this arm names and
                    // that law stops holding for is said rather than emitted against.
                    Comparison comparison = Comparison.of(bin).orElseThrow(
                            () -> new IllegalStateException(
                                    "an operator that compares placed nothing: " + bin.op()));
                    emitComparison(comparison);
                    yield comparison;
                }
            };
        }

        /** What the operator computes of two numbers, through the runtime that owns the arithmetic
         *  — {@code IntMath} and {@code DecimalMath} — rather than a host method. The operands are
         *  numbers here: newtype arithmetic is a construction over the values its operands wrap,
         *  and it was written as one where the tree was built (spec §newtype-arithmetic), so
         *  nothing is opened or re-wrapped at the operator. */
        private void arithmetic(Core.Binary bin, String onDecimal, String onInt) {
            Type t = genExpr(bin.left());
            genExpr(bin.right());
            if (t == Type.DECIMAL) {
                code.invokestatic(CD_DecimalMath, onDecimal, MTD_bdArith);
            } else {
                code.invokestatic(CD_IntMath, onInt, MTD_intExact);
            }
        }

        /**
         * The comparison, emitted from what it placed.
         *
         * <p>An order and a value singled out are the two things a comparison places, and which of
         * the two this is comes from the claim the recognition carries. Read off the operator here
         * instead, the same six would be divided a second time and the two divisions would agree
         * only for as long as somebody kept them so.
         *
         * <p>A single-value newtype compares by its underlying value, so each operand is opened to
         * that value right after it is pushed (金額 &lt;= 金額, 金額 &lt;= 100 — the checker allows
         * only same newtype or a bare literal).
         */
        private void emitComparison(Comparison comparison) {
            switch (comparison.claim()) {
                case ComparisonClaim.Cut cut -> ordered(comparison, cut);
                case ComparisonClaim.Singled singled -> same(comparison, singled);
            }
        }

        /**
         * An order, emitted from the order the operands open to and from nothing else.
         *
         * <p>The switch carries no {@code default}: reading the representation instead is what let
         * {@code StageN < StageN} fall past every ordering arm into an equality test, and an order
         * added to {@link Ordering} would fall the same way through an {@code instanceof} chain.
         */
        private void ordered(Comparison comparison, ComparisonClaim.Cut cut) {
            // Whether the two may be compared at all was settled by BinaryElaborator against the
            // types as written; this reads what they open to.
            Ordering how = Ordering.ofComparison(
                    comparison.left().type(), comparison.right().type(), symbols, ctx.kinds,
                    ctx.published);
            if (how == null) {
                throw new IllegalStateException("a comparison the checker admitted has no order: "
                        + comparison.left().type() + " " + cut.statedRelation() + " "
                        + comparison.right().type());
            }
            switch (how.opened()) {
                case Ordering.Longs _ -> {
                    unwrapNewtypeValue(genExpr(comparison.left()));
                    unwrapNewtypeValue(genExpr(comparison.right()));
                    comparisonMaterialize(cut.statedRelation(), true);
                }
                case Ordering.Natural _ -> {
                    // These all carry as Comparable — String, BigDecimal, LocalDate, LocalTime,
                    // LocalDateTime, Instant — so one compareTo reduces the order to its sign
                    // against 0. BigDecimal.compareTo ignores scale, which matches Decimal equality
                    // (spec §equality); the others order lexicographically / in time.
                    unwrapNewtypeValue(genExpr(comparison.left()));
                    unwrapNewtypeValue(genExpr(comparison.right()));
                    code.invokeinterface(CD_Comparable, "compareTo", MTD_compareTo_Object);
                    code.iconst_0();
                    comparisonMaterialize(cut.statedRelation(), false);
                }
                case Ordering.Places places -> {
                    // An enumeration compares by where its case stands in the declaration, which
                    // the sum answers for both operands — `stage < Won` pairs a sum with one of its
                    // cases, and `x < StageN(Qualified)` two wrappers over one sum.
                    unwrapNewtypeValue(genExpr(comparison.left()));
                    code.invokestatic(cd(places.enumeration()), ORDER_METHOD, MTD_order, true);
                    unwrapNewtypeValue(genExpr(comparison.right()));
                    code.invokestatic(cd(places.enumeration()), ORDER_METHOD, MTD_order, true);
                    comparisonMaterialize(cut.statedRelation(), false);
                }
                // `opened` answers for the value the operands are opened to, which is never one a
                // name is still worn over.
                case Ordering.Wrapped _ -> throw new IllegalStateException(
                        "an opened order is never a wrapped one: " + comparison.left().type());
            }
        }

        /**
         * A value singled out, tested for sameness.
         *
         * <p>{@link ComparisonClaim.Singled#holdsAtTheValue} says which of the two classes the
         * comparison selects, so a comparison met where the value is not the one named is this test
         * inverted.
         */
        private void same(Comparison comparison, ComparisonClaim.Singled singled) {
            Type lt = unwrapNewtypeValue(genExpr(comparison.left()));
            unwrapNewtypeValue(genExpr(comparison.right()));
            if (lt == Type.STRING) {
                code.invokevirtual(CD_String, "equals",
                        MethodTypeDesc.of(ConstantDescs.CD_boolean, CD_Object));
                selecting(singled);
                return;
            }
            if (isReference(lt)) {
                // What sameness is, is the runtime's to say: a data compares by its fields, an
                // amount ignores its scale, a collection asks that of what it holds (spec
                // §equality). A pair of Decimals takes the overload for them.
                emitValueEquals(code, lt == Type.DECIMAL);
                selecting(singled);
                return;
            }
            comparisonMaterialize(singled.statedRelation(), lt == Type.INT);
        }

        /** Turns a test of sameness into the class the comparison selects: the one it is met at is
         *  what was emitted, and the other is its denial. */
        private void selecting(ComparisonClaim.Singled singled) {
            if (!singled.holdsAtTheValue()) {
                code.iconst_1();
                code.ixor();
            }
        }

        /** The enumeration a list's elements are ordered by, or null when they are ordered otherwise
         * (an ordered primitive or a newtype over one, which carry their own {@code Comparable}). */
        private TypeSymbol elementOrdering(Core arg) {
            return arg.type() instanceof Type.ListOf lo ? sumOrdering(lo.element()) : null;
        }

        /** The sum that answers for values of {@code t}, or null where the value carries its own
         * order. Asked of the value as the runtime is handed it, so a newtype over an enumeration
         * answers null and sorts by the {@code compareTo} its own class carries — the sum's
         * {@code __order} would be handed the wrapper and not the case.
         *
         * <p>Every order is answered for rather than "everything but a {@code Places} sorts by
         * natural order", so an order added to {@link Ordering} has to say which of the two it is
         * instead of inheriting the answer that happens to be right for these three. */
        private TypeSymbol sumOrdering(Type t) {
            Ordering how = Ordering.of(t, symbols, ctx.kinds, ctx.published);
            if (how == null) {
                return null;
            }
            return switch (how.asHeld()) {
                case Ordering.Places places -> places.enumeration();
                // A long boxes to a Comparable and a newtype's own class carries a compareTo, so
                // for both of these the runtime's natural order is the order.
                case Ordering.Longs _, Ordering.Natural _ -> null;
                // `asHeld` answers for the value as its own type holds it, which is never wrapped.
                case Ordering.Wrapped _ ->
                        throw new IllegalStateException("a held order is never a wrapped one: " + t);
            };
        }

        /**
         * The relation the comparison states, brought to a boolean on the stack.
         *
         * <p>Taken as what the comparison states rather than as the operator it was written with:
         * the claim is what a reader holds below a recognition, and reaching back for the operator
         * to emit it would be the last step of an emission asking the question the recognition
         * settled.
         *
         * <p>The {@code default} is here though the six arms are the whole of {@link Rel} today. An
         * enum switch statement is not held to covering its type, so a relation added later would
         * emit no branch at all and leave the stack to the {@code iconst_0} below.
         */
        private void comparisonMaterialize(Rel rel, boolean isLong) {
            Label t = code.newLabel();
            Label end = code.newLabel();
            if (isLong) {
                code.lcmp();
                switch (rel) {
                    case LT -> code.iflt(t);
                    case LE -> code.ifle(t);
                    case GT -> code.ifgt(t);
                    case GE -> code.ifge(t);
                    case EQ -> code.ifeq(t);
                    case NE -> code.ifne(t);
                    default -> throw new IllegalStateException();
                }
            } else {
                switch (rel) {
                    case LT -> code.if_icmplt(t);
                    case LE -> code.if_icmple(t);
                    case GT -> code.if_icmpgt(t);
                    case GE -> code.if_icmpge(t);
                    case EQ -> code.if_icmpeq(t);
                    case NE -> code.if_icmpne(t);
                    default -> throw new IllegalStateException();
                }
            }
            code.iconst_0();
            code.goto_(end);
            code.labelBinding(t);
            code.iconst_1();
            code.labelBinding(end);
        }

        /** What the body may name here: the bindings this emitter holds. */
        private Scope bound() {
            Map<BindingId, Scope.Binding> held = new LinkedHashMap<>();
            locals.forEach((binding, v) -> held.put(binding, new Scope.Binding(v.name(), v.type())));
            return Scope.of(held);
        }

        /** {@link #bound} plus what a call left standing is typed against, so re-typing an
         * expression that holds one (a nested {@code foldFrom} in a fold's seed) resolves it as a
         * function.
         *
         * <p>Read from the check's own answer rather than from the methods this module emits. Which
         * names a standing call can hold follows from the declarations in reach; which methods are
         * emitted follows from what this module turned out to need. Typing against the second
         * answered that a rule reaching a fold had no fold to call, in a module whose only reach to
         * one was that rule. */
        Scope scope() {
            return bound().reaching(ctx.standingCalls);
        }

        /** Emits a function value from its elaborated node: the parameter and result types are the
         * ones the checker decided, so nothing is inferred here (issue #81). */
        private void emitFunctionValue(Core value, List<Type> paramTypes) {
            switch (value) {
                case Core.Block b -> emitLambda(b, paramTypes);
                case Core.If iff -> {
                    genExpr(iff.cond());
                    Label elseL = code.newLabel();
                    Label end = code.newLabel();
                    code.ifeq(elseL);
                    // A fork answering a function is a fork like any other: a row takes one of its
                    // arms, and the arm it took is the plan's to be told about. What the arm answers
                    // with is what differs here, and that is not something a count is about.
                    probe(iff, 0);
                    emitFunctionValue(iff.then(), paramTypes);
                    code.goto_(end);
                    code.labelBinding(elseL);
                    probe(iff, 1);
                    emitFunctionValue(iff.els(), paramTypes);
                    code.labelBinding(end);
                }
                case Core.LetIn li -> {
                    // a capture binding around the function: bind it here so the lambda captures it
                    Type vt = genExpr(li.value());
                    int s = slot(vt);
                    store(code, s, vt);
                    bind(li.binder(), s, vt);
                    emitFunctionValue(li.body(), paramTypes);
                }
                default -> genExpr(value);
            }
        }

        /** Compiles a lambda to a synthetic {@code Fn} class and emits {@code new} of it, passing the
         * captured free variables (and any injected behaviors it calls) to its constructor. Its
         * parameter and result types are the ones the checker put on the block (issue #81). */
        private void emitLambda(Core.Block block, List<Type> paramTypes) {
            emitLambda(block.params(), block.body(), paramTypes, freeVars(block));
        }

        private void emitLambda(List<Core.Binder> params, Core body, List<Type> paramTypes,
                                Reaches free) {
            List<Core.Read> captures = free.bindings();
            List<ValueName.Behavior> injectedNames = free.injected();
            GeneratedClass.Lambda lambda = new GeneratedClass.Lambda(pkg, ctx.nextLambdaId());
            ClassDesc cd = ctx.cd(lambda);
            ctx.addSynth(lambda, generateLambdaClass(cd, params, body, paramTypes,
                    captures, injectedNames, reqSuccess, reqParams));

            // the same condition generateLambdaClass interned on — it must stay the same one
            if (captures.isEmpty() && injectedNames.isEmpty()) {
                loadSharedInstance(code, cd);
                return;
            }

            code.new_(cd);
            code.dup();
            List<ClassDesc> ctorDescs = new ArrayList<>();
            for (Core.Read c : captures) {
                load(code, locals.get(c.binding()).slot(), c.type());
                ctorDescs.add(jvmType(c.type()));
            }
            for (ValueName.Behavior inj : injectedNames) {
                InjectionSlots.Slot slot = held.of(inj);
                code.aload(0);                              // the enclosing behavior instance
                code.getfield(cdName, slot.fieldName(), slot.type());   // its injected field
                ctorDescs.add(slot.type());
            }
            code.invokespecial(cd, "<init>",
                    MethodTypeDesc.of(ConstantDescs.CD_void, ctorDescs.toArray(new ClassDesc[0])));
        }

        /** Applies a first-class function value: {@code f.apply(new Object[]{args...})}, then casts
         * the {@code Object} result back to the function's result type. */
        private void applyFn(Core.Apply call, Type.FnOf fnType) {
            applyValue(locals.get(call.fn().binding()), call.args(), fnType);
        }

        private void applyValue(Var fv, List<Core> args, Type.FnOf fnType) {
            load(code, fv.slot(), fv.type());   // the Fn receiver
            pushInt(code, args.size());
            code.anewarray(CD_Object);
            for (int i = 0; i < args.size(); i++) {
                code.dup();
                pushInt(code, i);
                Type at = genExpr(args.get(i));
                box(code, at);
                code.aastore();
            }
            code.invokeinterface(CD_Fn, "apply", MTD_Fn_apply);
            stackCast(fnType.result());   // Object result -> the function's result type
        }

        /**
         * What a lambda's body reaches outside itself, and so what its class must be handed.
         *
         * <p>Two different things, kept apart rather than told apart afterwards: the bindings of the
         * enclosing body it reads, and the injected behaviors it calls. One is bound here and the
         * other is declared elsewhere, which is why one is held by binding and the other by name.
         */
        private static final class Reaches {

            private final LinkedHashMap<BindingId, Core.Read> reads = new LinkedHashMap<>();
            private final LinkedHashSet<ValueName.Behavior> behaviors = new LinkedHashSet<>();

            List<Core.Read> bindings() {
                return new ArrayList<>(reads.values());
            }

            List<ValueName.Behavior> injected() {
                return new ArrayList<>(behaviors);
            }
        }

        /** What {@code block}'s body reaches outside itself, in first-seen order. */
        private Reaches freeVars(Core.Block block) {
            Reaches free = new Reaches();
            Set<BindingId> bound = new HashSet<>();
            block.params().forEach(p -> bound.add(p.binding()));
            collectFree(block.body(), bound, free);
            return free;
        }

        private void collectFree(Core e, Set<BindingId> bound, Reaches free) {
            switch (e) {
                case Core.PreservedCall p -> throw p.unexpectedIn("the emitter");
                case Core.Read v -> reaches(v, bound, free);
                case Core.Call c -> {
                    // an injected behavior the body calls is handed over too: the lambda is a class
                    // of its own, and what it reaches has to reach it
                    ValueName.Behavior called = behaviorOf(c);
                    if (called != null && reqNames.contains(called)) {
                        free.behaviors.add(called);
                    }
                    c.args().forEach(a -> collectFree(a, bound, free));
                }
                case Core.Apply a -> {
                    reaches(a.fn(), bound, free);
                    a.args().forEach(x -> collectFree(x, bound, free));
                }
                case Core.FieldAccess fa -> collectFree(fa.target(), bound, free);
                case Core.Binary bin -> {
                    collectFree(bin.left(), bound, free);
                    collectFree(bin.right(), bound, free);
                }
                case Core.Neg neg -> collectFree(neg.operand(), bound, free);
                case Core.Construct nd ->
                        nd.values().forEach(v -> collectFree(v.value(), bound, free));
                case Core.If iff -> {
                    collectFree(iff.cond(), bound, free);
                    collectFree(iff.then(), bound, free);
                    collectFree(iff.els(), bound, free);
                }
                case Core.IfConstructed ic -> {
                    collectFree(ic.construct(), bound, free);
                    collectFree(ic.then(), with(bound, ic.binder().binding()), free);
                    ic.els().forEach(arm -> collectFree(arm.body(), bound, free));
                }
                case Core.LetIn li -> {
                    collectFree(li.value(), bound, free);
                    collectFree(li.body(), with(bound, li.binder().binding()), free);
                }
                case Core.Match m -> {
                    collectFree(m.scrutinee(), bound, free);
                    for (Core.Case c : m.cases()) {
                        collectFree(c.body(), c.binder() == null
                                ? bound : with(bound, c.binder().binding()), free);
                    }
                }
                case Core.Block b -> {
                    Set<BindingId> inner = new HashSet<>(bound);
                    b.params().forEach(p -> inner.add(p.binding()));
                    collectFree(b.body(), inner, free);
                }
                case Core.ListLit lit -> lit.elements().forEach(x -> collectFree(x, bound, free));
                case Core.OptionSome so -> collectFree(so.value(), bound, free);
                case Core.Tuple t -> t.elements().forEach(x -> collectFree(x, bound, free));
                case Core.TupleGet tg -> collectFree(tg.tuple(), bound, free);
                case Core.OptionNone _ -> { }
                case Core.Int _ -> { }
                case Core.Decimal _ -> { }
                case Core.Str _ -> { }
                case Core.Bool _ -> { }
                case Core.Temporal _ -> { }
                case Core.Unreachable _ -> { }
                // reads nothing the enclosing body binds
                case Core.UnitValue _ -> { }
            }
        }

        private static Set<BindingId> with(Set<BindingId> bound, BindingId binding) {
            Set<BindingId> inner = new HashSet<>(bound);
            inner.add(binding);
            return inner;
        }

        /** A read of something bound outside the lambda is captured; one of its own is not. */
        private void reaches(Core.Read read, Set<BindingId> bound, Reaches free) {
            if (!bound.contains(read.binding()) && locals.containsKey(read.binding())) {
                free.reads.putIfAbsent(read.binding(), read);
            }
        }

    /** Where a value lives and what it is. {@code name} is what it is called — a diagnostic quotes
     * it, and the checker's inference helpers are handed a view keyed by it; nothing is found by it. */
    private record Var(int slot, Type type, String name) {}
}
