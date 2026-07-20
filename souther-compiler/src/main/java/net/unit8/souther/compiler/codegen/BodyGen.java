package net.unit8.souther.compiler.codegen;

import net.unit8.souther.compiler.diag.CompileException;
import net.unit8.souther.compiler.Prelude;
import net.unit8.souther.compiler.ast.Ast;
import net.unit8.souther.compiler.check.Type;
import net.unit8.souther.compiler.check.TypeChecker;
import net.unit8.souther.compiler.core.Core;

import java.lang.classfile.ClassFile;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.Label;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static net.unit8.souther.compiler.codegen.Descriptors.*;
import static net.unit8.souther.compiler.codegen.JvmTypes.*;

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
    private final Map<String, Ast.Def> symbols;

    private ClassDesc cd(String typeName) {
        return ctx.cd(typeName);
    }

    private ClassDesc matchCaseClass(String caseName) {
        return ctx.matchCaseClass(caseName);
    }

    private Map<String, Type> fieldTypes(Ast.Data data) {
        return ctx.fieldTypes(data);
    }

    private Type successType(Ast.RetType ret) {
        return ctx.successType(ret);
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
        private final Ast.Data data;
        private final ClassDesc cdName;
        private final Map<String, Var> env = new HashMap<>();
        private int nextSlot;
        private Set<String> reqNames = Set.of();
        private Map<String, Type> reqSuccess = Map.of();
        private Map<String, Type> reqParam = Map.of();

        BodyGen(CodegenContext ctx, CodeBuilder code, Ast.Data data, ClassDesc cdName, int firstSlot) {
            this.ctx = ctx;
            this.pkg = ctx.pkg;
            this.symbols = ctx.symbols;
            this.code = code;
            this.data = data;
            this.cdName = cdName;
            this.nextSlot = firstSlot;
        }

        /** Makes injected required behaviors callable inline from this body (spec 12.2, 13). */
        void requireds(Set<String> names, Map<String, Type> success, Map<String, Type> param) {
            this.reqNames = names;
            this.reqSuccess = success;
            this.reqParam = param;
        }

        /** A {@code ReqSig} view of the injected behaviors in scope, for re-typing a closure body. */
        private Map<String, TypeChecker.ReqSig> reqSigs() {
            Map<String, TypeChecker.ReqSig> sigs = new HashMap<>();
            for (String n : reqNames) {
                sigs.put(n, new TypeChecker.ReqSig(reqParam.get(n), reqSuccess.get(n)));
            }
            return sigs;
        }

        void bind(String name, int slot, Type type) {
            env.put(name, new Var(slot, type));
            nextSlot = Math.max(nextSlot, slot + width(type));
        }

        /** Restores a name to the binding it had before a block shadowed it (or removes it if it had
         * none), so a block's parameters do not leak past the block (see {@link #fold}). */
        private void restore(String name, Var previous) {
            if (previous == null) {
                env.remove(name);
            } else {
                env.put(name, previous);
            }
        }

        int slot(Type type) {
            int s = nextSlot;
            nextSlot += width(type);
            return s;
        }

        /**
         * Emits an AST expression by lowering it to Core and emitting that (ADR-0021). The behavior
         * body is already Core by the time it reaches the backend; this adapter remains for the codec
         * paths (encoder/decoder), which still hold AST expressions.
         */
        Type expr(Ast.Expr e) {
            return genExpr(Core.of(e));
        }

        private void emitFieldRead(CodeBuilder code, String ownerName, String field, Type ft) {
            ClassDesc ownerCd = cd(ownerName);
            if (ctx.isImported(ownerName)) {
                code.invokevirtual(ownerCd, field, MethodTypeDesc.of(jvmType(ft)));
            } else {
                code.getfield(ownerCd, field, jvmType(ft));
            }
        }

        private static String captureField(int i) {
            return "c" + i;
        }

        /** Generates a synthetic {@code Fn} class for an escaping lambda: captured free variables become
         * {@code final} fields set by the constructor, and the body compiles into {@code apply}, which
         * unboxes its arguments from the {@code Object[]} and boxes its result (spec §blocks). */
        private byte[] generateLambdaClass(ClassDesc cd, Ast.Block block, List<Type> paramTypes,
                                           Type resultType, List<String> valueNames, List<Type> valueTypes,
                                           List<String> injectedNames, Map<String, Type> reqSuccess,
                                           Map<String, Type> reqParam) {
            return build(cd, cb -> {
                cb.withFlags(ClassFile.ACC_FINAL | ClassFile.ACC_SUPER);
                cb.withInterfaceSymbols(CD_Fn);
                for (int i = 0; i < valueNames.size(); i++) {
                    cb.withField(captureField(i), jvmType(valueTypes.get(i)),
                            ClassFile.ACC_PRIVATE | ClassFile.ACC_FINAL);
                }
                for (String inj : injectedNames) {   // named after the behavior so requiredCall reads it
                    cb.withField(inj, CD_Behavior, ClassFile.ACC_PRIVATE | ClassFile.ACC_FINAL);
                }
                List<ClassDesc> ctor = new ArrayList<>();
                for (Type t : valueTypes) {
                    ctor.add(jvmType(t));
                }
                for (String _ : injectedNames) {
                    ctor.add(CD_Behavior);
                }
                cb.withMethodBody("<init>", MethodTypeDesc.of(ConstantDescs.CD_void, ctor.toArray(new ClassDesc[0])),
                        ClassFile.ACC_PUBLIC, code -> {
                    code.aload(0);
                    code.invokespecial(CD_Object, "<init>", MTD_void);
                    int slot = 1;
                    for (int i = 0; i < valueNames.size(); i++) {
                        code.aload(0);
                        load(code, slot, valueTypes.get(i));
                        code.putfield(cd, captureField(i), jvmType(valueTypes.get(i)));
                        slot += width(valueTypes.get(i));
                    }
                    for (String inj : injectedNames) {
                        code.aload(0);
                        code.aload(slot);
                        code.putfield(cd, inj, CD_Behavior);
                        slot += 1;
                    }
                    code.return_();
                });
                cb.withMethodBody("apply", MTD_Fn_apply, ClassFile.ACC_PUBLIC, code -> {
                    BodyGen g = new BodyGen(ctx, code, null, cd, 2);   // slot 0 = this, slot 1 = the Object[] args
                    if (!injectedNames.isEmpty()) {
                        // the captured behaviors live in this closure's own fields; requiredCall reads
                        // `this.<name>`, so route them the same way the enclosing behavior does
                        Map<String, Type> succ = new HashMap<>();
                        Map<String, Type> parm = new HashMap<>();
                        for (String inj : injectedNames) {
                            succ.put(inj, reqSuccess.get(inj));
                            parm.put(inj, reqParam.get(inj));
                        }
                        g.requireds(new HashSet<>(injectedNames), succ, parm);
                    }
                    for (int i = 0; i < paramTypes.size(); i++) {
                        Type pt = paramTypes.get(i);
                        int s = g.slot(pt);
                        code.aload(1);
                        pushInt(code, i);
                        code.aaload();
                        unbox(code, pt, s);
                        g.bind(block.params().get(i), s, pt);
                    }
                    for (int i = 0; i < valueNames.size(); i++) {
                        Type ct = valueTypes.get(i);
                        int s = g.slot(ct);
                        code.aload(0);
                        code.getfield(cd, captureField(i), jvmType(ct));
                        store(code, s, ct);
                        g.bind(valueNames.get(i), s, ct);
                    }
                    Type rt = g.expr(block.body());
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
         * aborts rather than riding an output case (spec 7.3, 9.4).
         * Because a desugared {@code require} (spec 16.4) is an {@code if} whose branches are tail,
         * this is reached for constructions on both sides of a guard — there is no second, unchecked
         * construction path.
         */
        void emitTail(Core e, ClassDesc cdB, Set<String> requiredNames, Map<String, Type> requiredSuccess) {
            switch (e) {
                case Core.LetIn li -> {
                    if (li.value() instanceof Core.Call call && requiredNames.contains(call.fn())) {
                        // call an injected required behavior; its apply returns the value directly
                        code.aload(0);
                        code.getfield(cdB, call.fn(), CD_Behavior);
                        if (call.args().isEmpty()) {
                            code.aconst_null();    // `() -> R` (spec 13.1)
                        } else {
                            Type at = genExpr(call.args().get(0));
                            box(code, at);
                        }
                        code.invokeinterface(CD_Behavior, "apply", MTD_apply);
                        Type letType = requiredSuccess.get(call.fn());
                        int vSlot = slot(letType);
                        unbox(code, letType, vSlot);
                        bind(li.name(), vSlot, letType);
                    } else {
                        // Type inference for a closure lives in the checker (AST); Core is untyped, so
                        // the backend reaches it through toAst rather than re-deriving types.
                        Ast.Expr valueAst = li.value().toAst();
                        Type vt;
                        if (TypeChecker.isFunctionSelection(valueAst)) {
                            // a lambda chosen at runtime (e.g. by an `if`) — a first-class Fn (spec §blocks)
                            List<Type> paramTypes = TypeChecker.inferFnParamTypes(
                                    li.name(), li.body().toAst(), typesEnv(), data, symbols);
                            vt = emitFunctionValue(valueAst, paramTypes);
                        } else {
                            vt = genExpr(li.value());
                        }
                        int slot = slot(vt);
                        store(code, slot, vt);
                        bind(li.name(), slot, vt);
                    }
                    emitTail(li.body(), cdB, requiredNames, requiredSuccess);
                }
                case Core.If iff -> {
                    genExpr(iff.cond());
                    Label elseL = code.newLabel();
                    code.ifeq(elseL);
                    emitTail(iff.then(), cdB, requiredNames, requiredSuccess);
                    code.labelBinding(elseL);
                    emitTail(iff.els(), cdB, requiredNames, requiredSuccess);
                }
                case Core.NewData nd when TypeChecker.isInvariantBearing(nd.typeName(), symbols) -> {
                    ClassDesc cdType = cd(nd.typeName());
                    Map<String, Type> flds = fieldTypes((Ast.Data) symbols.get(nd.typeName()));
                    emitFieldValues(flds, nd.inits(), nd.spreads());
                    code.invokestatic(cdType, "__construct", MethodTypeDesc.of(CD_Result, fieldDescs(flds)));
                    code.invokestatic(CD_ConstraintViolation, "orThrow", MTD_orThrow);
                    code.areturn();
                }
                default -> {
                    Type rt = genExpr(e);
                    box(code, rt);
                    code.areturn();
                }
            }
        }

        /** Pushes each field's value in declaration order: an explicit initializer, else the field
         * carried over from a spread source (ADR-0021). */
        void emitFieldValues(Map<String, Type> fields, List<Core.FieldInit> inits, List<String> spreads) {
            Map<String, Core.FieldInit> byName = new HashMap<>();
            for (Core.FieldInit init : inits) {
                byName.put(init.name(), init);
            }
            for (String field : fields.keySet()) {
                Core.FieldInit init = byName.get(field);
                if (init != null) {
                    genExpr(init.value());
                    continue;
                }
                for (String sp : spreads) {
                    Ast.Data src = (Ast.Data) symbols.get(((Type.Ref) varType(sp)).name());
                    if (fieldTypes(src).containsKey(field)) {
                        spreadField(sp, field);
                        break;
                    }
                }
            }
        }

        /** Builds an ArrayList of the literal's elements and returns it immutably. */
        private Type listLit(Core.ListLit lit) {
            code.new_(CD_ArrayList);
            code.dup();
            code.invokespecial(CD_ArrayList, "<init>", MTD_void);
            Type elem = null;
            for (Core el : lit.elements()) {
                code.dup();
                Type t = genExpr(el);
                box(code, t);
                code.invokevirtual(CD_ArrayList, "add", MTD_ArrayList_add);
                code.pop();
                elem = t;
            }
            code.invokestatic(CD_List, "copyOf", MTD_List_copyOf, true);
            return elem == null ? Type.EMPTY_LIST : Type.list(elem);   // `[]` is the empty list (ADR-0028)
        }

        /** Builds a tuple {@code (e1, e2, ...)} as an {@code Object[]}, boxing each element (ADR-0036). */
        private Type tuple(Core.Tuple t) {
            List<Type> elems = new ArrayList<>();
            pushInt(code, t.elements().size());
            code.anewarray(CD_Object);
            for (int i = 0; i < t.elements().size(); i++) {
                code.dup();
                pushInt(code, i);
                Type et = genExpr(t.elements().get(i));
                box(code, et);
                code.aastore();
                elems.add(et);
            }
            return Type.tuple(elems);
        }

        /** Reads a tuple element by index: {@code arr[i]}, cast back to the element's type. */
        private Type tupleGet(Core.TupleGet tg) {
            Type tt = genExpr(tg.tuple());
            Type et = ((Type.TupleOf) tt).elements().get(tg.index());
            pushInt(code, tg.index());
            code.aaload();
            castFromObject(code, et);
            return et;
        }

        /**
         * Emits {@code fold} (spec 18.4) — the one privileged list loop — inlining the block's body
         * into an index loop that threads the accumulator. Every other combinator (map/filter/all/any)
         * is a prelude helper written in terms of this (ADR-0028, souther.list), so it arrives here
         * already expanded into a fold.
         *
         * <p>No closure is built: a block is second-class (spec 12.5), so it cannot outlive the call
         * and there is nothing to capture it into. A required behavior called from the body reads the
         * enclosing behavior's injected field, which is why the requirement belongs to that behavior.
         */
        private Type fold(Core.Fold f) {
            Type srcType = genExpr(f.source());
            Type elemType = ((Type.ListOf) srcType).element();
            int srcSlot = slot(Type.STRING);
            code.astore(srcSlot);

            // The accumulator lives in a slot typed to the seed (a long for Int, an int for Bool,
            // a reference otherwise), so it stays unboxed across iterations — no Long.valueOf /
            // longValue round-trip per element, which keeps the loop friendly to the JIT.
            Type seedType = genExpr(f.seed());   // emits the seed value
            Type accType = resolveAccType(f, seedType, elemType);
            int accSlot = slot(accType);
            store(code, accSlot, accType);

            // When the source is the empty-list literal, its element type is a Nothing bottom
            // (ADR-0028): there is no element to fetch, and the loop is dead code — `fold f z []` is
            // `z`. Emit no loop at all and load the accumulator, still the seed, below. Faithfully
            // emitting the body would unbox the Nothing element (as `acc + x` does with `x`), which
            // has no JVM form and would crash the backend.
            if (!mentionsNothing(elemType)) {
                // Walk the source with an Iterator rather than get(i): a List value is a persistent
                // vector, whose iterator yields each element in O(1) amortized, whereas get(i)
                // descends the trie (O(log n)) on every step. The one privileged list loop stays a
                // single pass over the source.
                int itSlot = slot(Type.STRING);
                code.aload(srcSlot);
                code.invokeinterface(CD_List, "iterator", MTD_iterator);
                code.astore(itSlot);

                Label test = code.newLabel();
                Label done = code.newLabel();
                code.labelBinding(test);
                code.aload(itSlot);
                code.invokeinterface(CD_Iterator, "hasNext", MTD_hasNext);
                code.ifeq(done);

                code.aload(itSlot);
                code.invokeinterface(CD_Iterator, "next", MTD_Object);
                int elemSlot = slot(elemType);
                unbox(code, elemType, elemSlot);
                // bind the block's parameters — the accumulator and this element — for this iteration.
                // The accumulator param reads straight from its typed slot; the block never writes the
                // slot, so binding the param to it directly is safe (the new value is stored below).
                // The block's parameter names are scoped to the block: a nested fold reuses the same
                // names (the derived combinators all bind `acc`/`x`), so save any outer binding these
                // shadow and restore it after the body, or the outer `acc` would resolve to the inner
                // fold's slot.
                Var prevAcc = env.get(f.params().get(0));
                Var prevElem = env.get(f.params().get(1));
                bind(f.params().get(0), accSlot, accType);
                bind(f.params().get(1), elemSlot, elemType);
                genExpr(f.body());
                restore(f.params().get(0), prevAcc);
                restore(f.params().get(1), prevElem);
                store(code, accSlot, accType);

                code.goto_(test);
                code.labelBinding(done);
            }

            load(code, accSlot, accType);
            return accType;
        }

        /**
         * The accumulator's type — the type of the slot the fold carries across iterations. Usually
         * it is the seed's type. But an empty-collection seed ({@code []}, {@code Map.empty}, or a
         * tuple of them) carries no element/key/value type of its own (ADR-0028); that type is on the
         * block that grows the accumulator, not on the seed. When the block only <em>writes</em> the
         * accumulator ({@code acc ++ [f(x)]}) the untyped slot survives, but when it <em>reads</em> it
         * through a nested combinator ({@code any(e -> e == x, acc)}) the backend would unbox a
         * {@code Nothing} element and crash. So for such a seed we ask the checker for the block's
         * result type — the type the accumulator resolves to — exactly as the checker types this fold.
         */
        private Type resolveAccType(Core.Fold f, Type seedType, Type elemType) {
            if (!mentionsNothing(seedType)) {
                return seedType;
            }
            Map<String, Type> inner = typesEnv();
            inner.put(f.params().get(0), seedType);
            inner.put(f.params().get(1), elemType);
            Type resolved = TypeChecker.typeOf(f.body().toAst(), inner, data, symbols, reqSigs());
            return mentionsNothing(resolved) ? seedType : resolved;
        }

        /** Whether {@code t} still carries the empty-collection bottom {@link Type#NOTHING} — an
         * unresolved element/key/value type that must not reach codegen (it has no JVM form). */
        private static boolean mentionsNothing(Type t) {
            return Type.mentions(t, x -> x instanceof Type.Nothing);
        }

        /**
         * Emits a Core expression — the single expression emitter (ADR-0021); every node kind is
         * handled here. A {@code let} whose value is a runtime-selected function still asks the
         * type checker (which works on the AST) whether the value is such a function and for its
         * parameter types, so those calls go through {@link Core#toAst()}: Core is untyped and type
         * inference lives in the checker, so the backend reuses it rather than re-deriving types.
         */
        Type genExpr(Core e) {
            return switch (e) {
                case Core.Int x -> {
                    code.loadConstant(x.value());
                    yield Type.INT;
                }
                case Core.Decimal x -> {
                    code.new_(CD_BigDecimal);
                    code.dup();
                    code.loadConstant(x.value().toString());
                    code.invokespecial(CD_BigDecimal, "<init>",
                            MethodTypeDesc.of(ConstantDescs.CD_void, CD_String));
                    yield Type.DECIMAL;
                }
                case Core.Str x -> {
                    code.loadConstant(x.value());
                    yield Type.STRING;
                }
                case Core.Bool x -> {
                    if (x.value()) code.iconst_1(); else code.iconst_0();
                    yield Type.BOOL;
                }
                case Core.Var v -> {
                    Var var = env.get(v.name());
                    if (var != null) {
                        load(code, var.slot(), var.type());
                        yield var.type();
                    }
                    if (symbols.get(v.name()) instanceof Ast.UnitData) {
                        ClassDesc cdU = cd(v.name());
                        code.new_(cdU);
                        code.dup();
                        code.invokespecial(cdU, "<init>", MTD_void);
                        yield Type.ref(v.name());
                    }
                    throw new CompileException(v.pos(), "unbound identifier `" + v.name() + "`");
                }
                case Core.Neg n -> {
                    Type t = genExpr(n.operand());
                    if (t == Type.DECIMAL) {
                        code.invokevirtual(CD_BigDecimal, "negate", MethodTypeDesc.of(CD_BigDecimal));
                    } else {
                        code.lneg();               // Int is carried as a long
                    }
                    yield t;
                }
                case Core.FieldAccess fa -> {
                    Type targetType = genExpr(fa.target());
                    Ast.Data owner = (Ast.Data) symbols.get(((Type.Ref) targetType).name());
                    Type ft = fieldTypes(owner).get(fa.field());
                    emitFieldRead(code, owner.name(), fa.field(), ft);
                    yield ft;
                }
                case Core.If iff -> {
                    genExpr(iff.cond());
                    Label elseL = code.newLabel();
                    Label end = code.newLabel();
                    code.ifeq(elseL);
                    Type tt = genExpr(iff.then());
                    code.goto_(end);
                    code.labelBinding(elseL);
                    genExpr(iff.els());
                    code.labelBinding(end);
                    yield tt;
                }
                case Core.Fold f -> fold(f);
                case Core.ListLit lit -> listLit(lit);
                case Core.Tuple t -> tuple(t);
                case Core.TupleGet tg -> tupleGet(tg);
                case Core.Binary bin -> binary(bin);
                case Core.NewData nd -> newData(nd);
                case Core.Match m -> match(m);
                case Core.Call c -> call(c);
                case Core.LetIn li -> {
                    // a `let` outside tail position: bind, then value the body
                    Type vt;
                    // Type inference for a closure lives in the checker (AST); Core is untyped, so
                    // the backend reaches it through toAst rather than re-deriving types.
                    Ast.Expr valueAst = li.value().toAst();
                    if (TypeChecker.isFunctionSelection(valueAst)) {
                        // a lambda chosen at runtime (e.g. by an `if`): a first-class Fn (spec §blocks)
                        List<Type> paramTypes = TypeChecker.inferFnParamTypes(
                                li.name(), li.body().toAst(), typesEnv(), data, symbols);
                        vt = emitFunctionValue(valueAst, paramTypes);
                    } else {
                        vt = genExpr(li.value());
                    }
                    int s = slot(vt);
                    store(code, s, vt);
                    bind(li.name(), s, vt);
                    yield genExpr(li.body());
                }
                // a block has no value of its own; it is inlined by the call it is passed to
                case Core.Block b -> throw new CompileException(b.pos(), "a block is not a value");
            };
        }

        private Type match(Core.Match m) {
            Type st = genExpr(m.scrutinee());
            int sSlot = slot(st);
            store(code, sSlot, st);
            Type element = st instanceof Type.OptionOf oo ? oo.element() : null;
            Label end = code.newLabel();
            Type branchType = null;
            for (Core.Case c : m.cases()) {
                Label nextCase = code.newLabel();
                List<String> cases = c.caseTypes();
                if (element != null) {
                    // Option match: a single Some/None case (or-patterns are rejected by the checker)
                    String caseName = cases.get(0);
                    code.aload(sSlot);
                    code.instanceOf(caseName.equals("Some") ? CD_OptionSome : CD_OptionNone);
                    code.ifeq(nextCase);
                    if (caseName.equals("Some")) {
                        // unwrap Some(v) -> v, bound to the element type
                        code.aload(sSlot);
                        code.checkcast(CD_OptionSome);
                        code.invokevirtual(CD_OptionSome, "value", MTD_Object);
                        int bslot = slot(element);
                        unbox(code, element, bslot);
                        if (c.binding() != null) {
                            bind(c.binding(), bslot, element);
                        }
                    }
                } else if (cases.size() == 1) {
                    code.aload(sSlot);
                    code.instanceOf(matchCaseClass(cases.get(0)));
                    code.ifeq(nextCase);
                    if (c.binding() != null) {
                        // a data case binds the instance; a primitive case (e.g. Int) unboxes the value
                        Type bt = TypeChecker.caseBindType(cases.get(0));
                        code.aload(sSlot);
                        int bslot = slot(bt);
                        unbox(code, bt, bslot);
                        bind(c.binding(), bslot, bt);
                    }
                } else {
                    // or-pattern: run the body if the value is any of the cases; the binding (if any)
                    // is the scrutinee's sum type, which every alternative already is
                    Label body = code.newLabel();
                    for (String caseName : cases) {
                        code.aload(sSlot);
                        code.instanceOf(matchCaseClass(caseName));
                        code.ifne(body);
                    }
                    code.goto_(nextCase);
                    code.labelBinding(body);
                    if (c.binding() != null) {
                        bind(c.binding(), sSlot, st);
                    }
                }
                branchType = genExpr(c.body());
                code.goto_(end);
                code.labelBinding(nextCase);
            }
            // Exhaustive by construction; this fallback is unreachable.
            code.new_(CD_IllegalStateException);
            code.dup();
            code.invokespecial(CD_IllegalStateException, "<init>", MTD_void);
            code.athrow();
            code.labelBinding(end);
            return branchType;
        }

        private Type newData(Core.NewData nd) {
            Ast.Data owner = (Ast.Data) symbols.get(nd.typeName());
            Map<String, Type> flds = fieldTypes(owner);
            ClassDesc cdType = cd(nd.typeName());
            code.new_(cdType);
            code.dup();
            emitFieldValues(flds, nd.inits(), nd.spreads());
            code.invokespecial(cdType, "<init>",
                    MethodTypeDesc.of(ConstantDescs.CD_void, fieldDescs(flds)));
            return Type.ref(nd.typeName());
        }

        Type varType(String name) {
            return env.get(name).type();
        }

        void spreadField(String spreadVar, String field) {
            Var v = env.get(spreadVar);
            String srcName = ((Type.Ref) v.type()).name();
            Ast.Data src = (Ast.Data) symbols.get(srcName);
            load(code, v.slot(), v.type());
            emitFieldRead(code, srcName, field, fieldTypes(src).get(field));
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

        /** Narrows an {@code Int} (a {@code long}) to a JVM {@code int}, for a JDK method taking an
         * {@code int} index. */
        void emitL2i() {
            code.l2i();
        }

        private Type call(Core.Call call) {
            Prelude.IntrinsicSig intrinsic = Prelude.intrinsics().get(call.fn());
            if (intrinsic != null) {
                return Intrinsics.emit(this, intrinsic.key(), call);
            }
            // A fold is a Core.Fold, never a Core.Call, so it does not appear here (see genExpr).
            switch (call.fn()) {
                case "String.length" -> {
                    genExpr(call.args().get(0));
                    code.invokevirtual(CD_String, "length", MethodTypeDesc.of(ConstantDescs.CD_int));
                    code.i2l();
                    return Type.INT;
                }
                case "List.length" -> {
                    genExpr(call.args().get(0));
                    code.invokeinterface(CD_List, "size", MTD_size);
                    code.i2l();
                    return Type.INT;
                }
                case "List.get" -> {
                    Type ct = genExpr(call.args().get(1));      // get(index, xs): list then index (Lists.get)
                    genExpr(call.args().get(0));                // long index
                    code.invokestatic(CD_Lists, "get",
                            MethodTypeDesc.of(CD_Option, CD_List, ConstantDescs.CD_long));
                    return Type.option(((Type.ListOf) ct).element());
                }
                case "List.max", "List.min" -> {
                    Type ct = genExpr(call.args().get(0));
                    code.invokestatic(CD_Lists, bareOp(call.fn()),   // "max" / "min"
                            MethodTypeDesc.of(CD_Option, CD_List));
                    return Type.option(((Type.ListOf) ct).element());
                }
                case "List.find", "List.sortBy" -> {
                    // find(p, xs) / sortBy(key, xs): the function is a value here (not inlined into a
                    // fold), so materialise it as an Fn, then pass the list. The list's element type
                    // gives the function's one parameter type.
                    Type lt = TypeChecker.typeOf(call.args().get(1).toAst(), typesEnv(), data, symbols, reqSigs());
                    Type elem = ((Type.ListOf) lt).element();
                    emitFunctionValue(call.args().get(0).toAst(), List.of(elem));   // Fn on the stack
                    genExpr(call.args().get(1));                                    // then the List
                    if (call.fn().equals("List.find")) {
                        code.invokestatic(CD_Lists, "find", MethodTypeDesc.of(CD_Option, CD_Fn, CD_List));
                        return Type.option(elem);
                    }
                    code.invokestatic(CD_Lists, "sortBy", MethodTypeDesc.of(CD_List, CD_Fn, CD_List));
                    return Type.list(elem);
                }
                case "Map.get" -> {
                    Type ct = genExpr(call.args().get(1));      // get(key, m): map then key (Maps.get)
                    genExpr(call.args().get(0));                // key (a reference)
                    code.invokestatic(CD_Maps, "get",
                            MethodTypeDesc.of(CD_Option, CD_Map, ConstantDescs.CD_Object));
                    return Type.option(((Type.MapOf) ct).value());
                }
                case "Map.empty" -> {
                    code.invokestatic(CD_Maps, "empty", MethodTypeDesc.of(CD_Map));
                    return Type.map(Type.NOTHING, Type.NOTHING);   // key/value fixed by context, like `[]`
                }
                case "Set.empty" -> {
                    code.invokestatic(CD_Sets, "empty", MethodTypeDesc.of(CD_Set));
                    return Type.set(Type.NOTHING);   // element type fixed by context, like `[]`
                }
                case "Int.add", "Int.subtract", "Int.multiply",
                     "Decimal.add", "Decimal.subtract", "Decimal.multiply" -> {
                    String op = bareOp(call.fn());
                    Type t = genExpr(call.args().get(0));
                    genExpr(call.args().get(1));
                    if (t == Type.DECIMAL) {
                        code.invokevirtual(CD_BigDecimal, op,
                                MethodTypeDesc.of(CD_BigDecimal, CD_BigDecimal));
                    } else {
                        // Int arithmetic aborts on overflow rather than wrapping (spec 18.2)
                        String exact = switch (op) {
                            case "add" -> "addExact";
                            case "subtract" -> "subtractExact";
                            default -> "multiplyExact";
                        };
                        code.invokestatic(CD_IntMath, exact, MTD_intExact);
                    }
                    return t;
                }
                case "Int.compare", "Decimal.compare" -> {
                    Type t = genExpr(call.args().get(0));
                    genExpr(call.args().get(1));
                    if (t == Type.DECIMAL) {
                        code.invokevirtual(CD_BigDecimal, "compareTo",
                                MethodTypeDesc.of(ConstantDescs.CD_int, CD_BigDecimal));
                    } else {
                        code.invokestatic(CD_Long, "compare",
                                MethodTypeDesc.of(ConstantDescs.CD_int, ConstantDescs.CD_long,
                                        ConstantDescs.CD_long));
                    }
                    code.i2l();
                    return Type.INT;
                }
                case "Int.divide", "Decimal.divide" -> {
                    if (call.args().size() == 4) {
                        return decimalDivide(call);
                    }
                    return intDivide(call, true);
                }
                case "Int.remainder" -> {
                    return intDivide(call, false);
                }
                default -> {
                    Var fv = env.get(call.fn());
                    if (fv != null && fv.type() instanceof Type.FnOf fnType) {
                        return applyFn(call, fv, fnType);
                    }
                    Ast.FnDef rec = ctx.recursiveHelpers.get(call.fn());
                    if (rec != null) {
                        return recursiveHelperCall(call, rec);
                    }
                    if (reqNames.contains(call.fn())) {
                        return requiredCall(call);
                    }
                    throw new CompileException(call.pos(), "unknown function `" + call.fn() + "`");
                }
            }
        }

        /** Calls a recursive helper as a static method on {@code $Fns} (spec 13.1): each argument is
         * evaluated and boxed, the {@code invokestatic} returns {@code Object}, and the result is cast
         * back to the helper's declared return type. A self- or mutual call reaches here the same way. */
        private Type recursiveHelperCall(Core.Call call, Ast.FnDef h) {
            for (Core arg : call.args()) {
                Type at = genExpr(arg);
                box(code, at);
            }
            ClassDesc[] params = new ClassDesc[call.args().size()];
            java.util.Arrays.fill(params, CD_Object);
            code.invokestatic(ClassDesc.of(pkg + ".$Fns"), call.fn(), MethodTypeDesc.of(CD_Object, params));
            Type rt = successType(h.declaredReturn());
            castFromObject(code, rt);
            return rt;
        }

        /** The operation name from a qualified builtin call ({@code "Decimal.add"} → {@code "add"}). */
        private static String bareOp(String fn) {
            int dot = fn.indexOf('.');
            return dot < 0 ? fn : fn.substring(dot + 1);
        }

        /** {@code divide}/{@code remainder} on Int: a zero divisor takes the DivisionByZero case,
         * otherwise the quotient/remainder is boxed (spec 18.2). */
        private Type intDivide(Core.Call call, boolean divide) {
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
                code.ldiv();
            } else {
                code.lrem();
            }
            code.invokestatic(CD_Long, "valueOf", MTD_Long_valueOf);   // box the quotient
            code.goto_(end);
            code.labelBinding(zero);
            code.getstatic(CD_DivisionByZero, "INSTANCE", CD_DivisionByZero);
            code.labelBinding(end);
            return Type.union(new java.util.LinkedHashSet<>(java.util.List.of("Int", "DivisionByZero")));
        }

        /** {@code divide(a, b, scale, mode)} on Decimal: a zero divisor takes the DivisionByZero
         * case, otherwise {@code a.divide(b, scale, RoundingMode.mode)} (spec 18.3). */
        private Type decimalDivide(Core.Call call) {
            genExpr(call.args().get(0));
            int aSlot = slot(Type.DECIMAL);
            code.astore(aSlot);
            genExpr(call.args().get(1));
            int bSlot = slot(Type.DECIMAL);
            code.astore(bSlot);
            code.aload(bSlot);
            code.invokevirtual(CD_BigDecimal, "signum", MethodTypeDesc.of(ConstantDescs.CD_int));
            Label zero = code.newLabel();
            Label end = code.newLabel();
            code.ifeq(zero);                       // signum == 0 -> DivisionByZero case
            code.aload(aSlot);
            code.aload(bSlot);
            genExpr(call.args().get(2));              // scale (Int, a long)
            code.l2i();
            String mode = ((Core.Var) call.args().get(3)).name();
            code.getstatic(CD_RoundingMode, mode, CD_RoundingMode);
            code.invokevirtual(CD_BigDecimal, "divide", MTD_bdDivide);
            code.goto_(end);
            code.labelBinding(zero);
            code.getstatic(CD_DivisionByZero, "INSTANCE", CD_DivisionByZero);
            code.labelBinding(end);
            return Type.union(new java.util.LinkedHashSet<>(java.util.List.of("Decimal", "DivisionByZero")));
        }

        /** Emits an inline call to an injected required behavior, leaving its success value on
         * the stack cast to the success type (spec 12.2, 13). */
        private Type requiredCall(Core.Call call) {
            code.aload(0);
            code.getfield(cdName, call.fn(), CD_Behavior);
            if (call.args().isEmpty()) {
                code.aconst_null();        // `() -> R`: the implementation ignores the input
            } else {
                Type at = genExpr(call.args().get(0));
                box(code, at);
            }
            code.invokeinterface(CD_Behavior, "apply", MTD_apply);
            Type success = reqSuccess.get(call.fn());
            stackCast(success);
            return success;
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

        private Type binary(Core.Binary bin) {
            switch (bin.op()) {
                case AND -> {
                    genExpr(bin.left());
                    genExpr(bin.right());
                    code.iand();
                    return Type.BOOL;
                }
                case OR -> {
                    genExpr(bin.left());
                    genExpr(bin.right());
                    code.ior();
                    return Type.BOOL;
                }
                // `+ - * /` work on two Int or two Decimal operands (spec 18.1). Int aborts on
                // overflow, and `/` aborts on a zero divisor; Decimal does not overflow, and its `/`
                // rounds by the default scale/mode. Case handling for a zero divisor is the
                // divide/remainder functions, not the operator.
                case ADD, SUB, MUL, DIV -> {
                    Type t = genExpr(bin.left());
                    genExpr(bin.right());
                    if (t == Type.DECIMAL) {
                        switch (bin.op()) {
                            case ADD -> code.invokevirtual(CD_BigDecimal, "add", MTD_bdArith);
                            case SUB -> code.invokevirtual(CD_BigDecimal, "subtract", MTD_bdArith);
                            case MUL -> code.invokevirtual(CD_BigDecimal, "multiply", MTD_bdArith);
                            default  -> code.invokestatic(CD_DecimalMath, "divide", MTD_bdDivideOp);
                        }
                    } else {
                        String m = switch (bin.op()) {
                            case ADD -> "addExact";
                            case SUB -> "subtractExact";
                            case MUL -> "multiplyExact";
                            default  -> "divideExact";
                        };
                        code.invokestatic(CD_IntMath, m, MTD_intExact);
                    }
                    return t;
                }
                case CONCAT -> {
                    Type lt = genExpr(bin.left());
                    Type rt = genExpr(bin.right());
                    // `++` over two strings is Elm's appendable on String; the checker guarantees both
                    // sides are String here, so emit `a.concat(b)` rather than the list join.
                    if (lt == Type.STRING) {
                        code.invokevirtual(CD_String, "concat",
                                MethodTypeDesc.of(CD_String, CD_String));
                        return Type.STRING;
                    }
                    code.invokestatic(CD_Lists, "concat", MTD_Lists_concat);
                    // the empty list contributes no element type; take the result's from the other
                    // side, so a `[] ++ [x]` chain does not leave the element as `Nothing` (ADR-0028)
                    if (lt.equals(Type.EMPTY_LIST)) {
                        return rt;
                    }
                    return lt;
                }
                default -> {
                    Type lt = genExpr(bin.left());
                    genExpr(bin.right());
                    boolean ordering = switch (bin.op()) {
                        case LT, LE, GT, GE -> true;
                        default -> false;
                    };
                    if (ordering && (lt == Type.STRING || lt == Type.DECIMAL
                            || lt == Type.DATE || lt == Type.DATETIME)) {
                        // String, Decimal, Date, DateTime all carry as Comparable — String,
                        // BigDecimal, LocalDate, LocalDateTime — so one compareTo reduces the order
                        // to its sign against 0. BigDecimal.compareTo ignores scale, which matches
                        // Decimal equality (spec 7.1); the others order lexicographically / in time.
                        code.invokeinterface(CD_Comparable, "compareTo", MTD_compareTo_Object);
                        code.iconst_0();
                        comparisonMaterialize(bin.op(), false);
                        return Type.BOOL;
                    }
                    if (lt == Type.STRING) {
                        code.invokevirtual(CD_String, "equals",
                                MethodTypeDesc.of(ConstantDescs.CD_boolean, CD_Object));
                        if (bin.op() == Ast.BinOp.NE) {
                            code.iconst_1();
                            code.ixor();
                        }
                        return Type.BOOL;
                    }
                    if (lt == Type.DECIMAL) {
                        emitDecimalEquals(code);          // by value, ignoring scale (spec 7.1)
                        if (bin.op() == Ast.BinOp.NE) {
                            code.iconst_1();
                            code.ixor();
                        }
                        return Type.BOOL;
                    }
                    if (isReference(lt)) {
                        // a data (or any boxed value) compares by its fields — the generated
                        // equals (spec 7.1). Objects.equals keeps it null-tolerant.
                        code.invokestatic(CD_Objects, "equals", MTD_Objects_equals);
                        if (bin.op() == Ast.BinOp.NE) {
                            code.iconst_1();
                            code.ixor();
                        }
                        return Type.BOOL;
                    }
                    comparisonMaterialize(bin.op(), lt == Type.INT);
                    return Type.BOOL;
                }
            }
        }

        private void comparisonMaterialize(Ast.BinOp op, boolean isLong) {
            Label t = code.newLabel();
            Label end = code.newLabel();
            if (isLong) {
                code.lcmp();
                switch (op) {
                    case LT -> code.iflt(t);
                    case LE -> code.ifle(t);
                    case GT -> code.ifgt(t);
                    case GE -> code.ifge(t);
                    case EQ -> code.ifeq(t);
                    case NE -> code.ifne(t);
                    default -> throw new IllegalStateException();
                }
            } else {
                switch (op) {
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

        /** A name-to-type view of this scope, for the checker's inference helpers. */
        private Map<String, Type> typesEnv() {
            Map<String, Type> t = new HashMap<>();
            env.forEach((k, v) -> t.put(k, v.type()));
            return t;
        }

        /** Emits a function value — a lambda, or an {@code if} that selects one — leaving an
         * {@link net.unit8.souther.runtime.Fn} on the stack (spec §blocks). */
        private Type emitFunctionValue(Ast.Expr value, List<Type> paramTypes) {
            return switch (value) {
                case Ast.Block b -> emitLambda(b, paramTypes);
                case Ast.If iff -> {
                    expr(iff.cond());
                    Label elseL = code.newLabel();
                    Label end = code.newLabel();
                    code.ifeq(elseL);
                    Type t = emitFunctionValue(iff.then(), paramTypes);
                    code.goto_(end);
                    code.labelBinding(elseL);
                    emitFunctionValue(iff.els(), paramTypes);
                    code.labelBinding(end);
                    yield t;
                }
                case Ast.LetIn li -> {
                    // a capture binding around the function: bind it here so the lambda captures it
                    Type vt = expr(li.value());
                    int s = slot(vt);
                    store(code, s, vt);
                    bind(li.name(), s, vt);
                    yield emitFunctionValue(li.body(), paramTypes);
                }
                default -> expr(value);
            };
        }

        /** Compiles a lambda to a synthetic {@code Fn} class and emits {@code new} of it, passing the
         * captured free variables (and any injected behaviors it calls) to its constructor. */
        private Type emitLambda(Ast.Block block, List<Type> paramTypes) {
            Map<String, Type> inner = typesEnv();
            for (int i = 0; i < paramTypes.size(); i++) {
                inner.put(block.params().get(i), paramTypes.get(i));
            }
            Type resultType = TypeChecker.typeOf(block.body(), inner, data, symbols, reqSigs());

            List<String> valueNames = new ArrayList<>();
            List<Type> valueTypes = new ArrayList<>();
            List<String> injectedNames = new ArrayList<>();
            for (String c : freeVars(block)) {
                if (env.containsKey(c)) {
                    valueNames.add(c);
                    valueTypes.add(env.get(c).type());
                } else {
                    injectedNames.add(c);   // an injected behavior the closure calls (spec 13.2)
                }
            }
            String className = pkg + ".$Fn" + ctx.nextLambdaId();
            ClassDesc cd = ClassDesc.of(className);
            ctx.addSynth(className, generateLambdaClass(cd, block, paramTypes, resultType,
                    valueNames, valueTypes, injectedNames, reqSuccess, reqParam));

            code.new_(cd);
            code.dup();
            List<ClassDesc> ctorDescs = new ArrayList<>();
            for (int i = 0; i < valueNames.size(); i++) {
                load(code, env.get(valueNames.get(i)).slot(), valueTypes.get(i));
                ctorDescs.add(jvmType(valueTypes.get(i)));
            }
            for (String inj : injectedNames) {
                code.aload(0);                              // the enclosing behavior instance
                code.getfield(cdName, inj, CD_Behavior);    // its injected field
                ctorDescs.add(CD_Behavior);
            }
            code.invokespecial(cd, "<init>",
                    MethodTypeDesc.of(ConstantDescs.CD_void, ctorDescs.toArray(new ClassDesc[0])));
            return Type.fn(paramTypes, resultType);
        }

        /** Applies a first-class function value: {@code f.apply(new Object[]{args...})}, then casts
         * the {@code Object} result back to the function's result type. */
        private Type applyFn(Core.Call call, Var fv, Type.FnOf fnType) {
            load(code, fv.slot(), fv.type());   // the Fn receiver
            pushInt(code, call.args().size());
            code.anewarray(CD_Object);
            for (int i = 0; i < call.args().size(); i++) {
                code.dup();
                pushInt(code, i);
                Type at = genExpr(call.args().get(i));
                box(code, at);
                code.aastore();
            }
            code.invokeinterface(CD_Fn, "apply", MTD_Fn_apply);
            stackCast(fnType.result());   // Object result -> the function's result type
            return fnType.result();
        }

        /** The free variables of a lambda: names its body reads that are bound in the enclosing
         * scope (so must be captured), in first-seen order. */
        private List<String> freeVars(Ast.Block block) {
            LinkedHashSet<String> free = new LinkedHashSet<>();
            collectFree(block.body(), new HashSet<>(block.params()), free);
            return new ArrayList<>(free);
        }

        private void collectFree(Ast.Expr e, Set<String> bound, LinkedHashSet<String> free) {
            switch (e) {
                case Ast.Var v -> maybeFree(v.name(), bound, free);
                case Ast.Call c -> {
                    maybeFree(c.fn(), bound, free);   // an applied function value is captured too
                    c.args().forEach(a -> collectFree(a, bound, free));
                }
                case Ast.FieldAccess fa -> collectFree(fa.target(), bound, free);
                case Ast.Binary bin -> {
                    collectFree(bin.left(), bound, free);
                    collectFree(bin.right(), bound, free);
                }
                case Ast.Neg neg -> collectFree(neg.operand(), bound, free);
                case Ast.NewData nd -> {
                    nd.inits().forEach(i -> collectFree(i.value(), bound, free));
                    nd.spreads().forEach(s -> maybeFree(s, bound, free));
                }
                case Ast.If iff -> {
                    collectFree(iff.cond(), bound, free);
                    collectFree(iff.then(), bound, free);
                    collectFree(iff.els(), bound, free);
                }
                case Ast.LetIn li -> {
                    collectFree(li.value(), bound, free);
                    Set<String> inner = new HashSet<>(bound);
                    inner.add(li.name());
                    collectFree(li.body(), inner, free);
                }
                case Ast.Match m -> {
                    collectFree(m.scrutinee(), bound, free);
                    for (Ast.Case c : m.cases()) {
                        Set<String> inner = bound;
                        if (c.binding() != null) {
                            inner = new HashSet<>(bound);
                            inner.add(c.binding());
                        }
                        collectFree(c.body(), inner, free);
                    }
                }
                case Ast.Block b -> {
                    Set<String> inner = new HashSet<>(bound);
                    inner.addAll(b.params());
                    collectFree(b.body(), inner, free);
                }
                case Ast.ListLit lit -> lit.elements().forEach(x -> collectFree(x, bound, free));
                case Ast.Tuple tup -> tup.elements().forEach(x -> collectFree(x, bound, free));
                case Ast.TupleGet tg -> collectFree(tg.tuple(), bound, free);
                case Ast.ListComp comp -> {
                    collectFree(comp.element(), bound, free);
                    comp.guards().forEach(g -> collectFree(g, bound, free));
                }
                case Ast.IntLit _ -> { }
                case Ast.DecimalLit _ -> { }
                case Ast.StringLit _ -> { }
                case Ast.BoolLit _ -> { }
            }
        }

        private void maybeFree(String name, Set<String> bound, LinkedHashSet<String> free) {
            if (!bound.contains(name) && (env.containsKey(name) || reqNames.contains(name))) {
                free.add(name);
            }
        }

    private record Var(int slot, Type type) {}
}
