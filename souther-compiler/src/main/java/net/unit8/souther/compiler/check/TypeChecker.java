package net.unit8.souther.compiler.check;

import net.unit8.souther.compiler.CompileException;
import net.unit8.souther.compiler.SourcePos;
import net.unit8.souther.compiler.ast.Ast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The slice-3 type checker. Adds a module symbol table so fields, decoders, and encoders
 * can reference other data types (e.g. {@code id: MemberId}, {@code field("id",
 * MemberId.decoder)}, {@code MemberId.encode(self.id)}). Exposes {@link #symbols},
 * {@link #fieldTypes}, {@link #resolveType} and {@link #typeOf} for the backend.
 */
public final class TypeChecker {

    private TypeChecker() {}

    public static void check(Ast.Module module) {
        Map<String, Ast.Def> symbols = symbols(module);
        for (Ast.Def def : module.defs()) {
            switch (def) {
                case Ast.Data data -> checkData(data, symbols);
                case Ast.SumData sum -> checkSum(sum, symbols);
                case Ast.UnitData ignored -> { }
            }
        }
        Set<String> allBehaviors = new HashSet<>();
        for (Ast.BehaviorDef b : module.behaviors()) {
            allBehaviors.add(b.name());
        }
        Map<String, ReqSig> reqSigs = new HashMap<>();
        for (Ast.RequiredBehavior r : module.requireds()) {
            allBehaviors.add(r.name());
            reqSigs.put(r.name(), new ReqSig(resolveType(r.paramType(), symbols),
                    resolveType(r.ret().success(), symbols)));
        }
        Set<String> bodiesWithDeps = new HashSet<>();
        for (Ast.BehaviorDef b : module.behaviors()) {
            if (b instanceof Ast.BodyBehavior body) {
                checkBodyBehavior(body, symbols, allBehaviors, reqSigs);
                if (!requiredCalls(body, reqSigs).isEmpty()) {
                    bodiesWithDeps.add(body.name());
                }
            }
        }
        checkPipelines(module, symbols, bodiesWithDeps);
    }

    /** A required behavior's input and success types (for typing calls). */
    private record ReqSig(Type param, Type success) {}

    /** The distinct required behaviors a body calls, in first-seen order. */
    public static List<String> requiredCalls(Ast.BodyBehavior body, java.util.Set<String> requiredNames) {
        List<String> calls = new java.util.ArrayList<>();
        for (Ast.BStmt stmt : body.stmts()) {
            if (stmt instanceof Ast.Let let && let.value() instanceof Ast.Call call
                    && requiredNames.contains(call.fn()) && !calls.contains(call.fn())) {
                calls.add(call.fn());
            }
        }
        return calls;
    }

    private static List<String> requiredCalls(Ast.BodyBehavior body, Map<String, ReqSig> reqSigs) {
        return requiredCalls(body, reqSigs.keySet());
    }

    /** A behavior's input and output types. */
    public record Sig(Type in, Type out) {}

    /** Builds the input/output signature of every behavior, checking pipeline composition. */
    public static Map<String, Sig> signatures(Ast.Module module, Map<String, Ast.Def> symbols) {
        Map<String, Sig> sigs = new HashMap<>();
        for (Ast.BehaviorDef b : module.behaviors()) {
            if (b instanceof Ast.BodyBehavior body && body.params().size() == 1) {
                sigs.put(body.name(), new Sig(resolveType(body.params().get(0).type(), symbols),
                        resolveType(body.ret().success(), symbols)));
            }
        }
        for (Ast.RequiredBehavior r : module.requireds()) {
            sigs.put(r.name(), new Sig(resolveType(r.paramType(), symbols),
                    resolveType(r.ret().success(), symbols)));
        }
        for (Ast.BehaviorDef b : module.behaviors()) {
            if (b instanceof Ast.PipeBehavior pipe) {
                sigs.put(pipe.name(), pipeSig(pipe, sigs));
            }
        }
        return sigs;
    }

    private static void checkPipelines(Ast.Module module, Map<String, Ast.Def> symbols,
                                       Set<String> bodiesWithDeps) {
        Map<String, Sig> sigs = signatures(module, symbols);
        for (Ast.BehaviorDef b : module.behaviors()) {
            if (b instanceof Ast.PipeBehavior pipe) {
                for (String stage : pipe.stages()) {
                    if (bodiesWithDeps.contains(stage)) {
                        throw new CompileException(pipe.pos(),
                                "pipeline stage `" + stage + "` has its own dependencies, which is not "
                                        + "supported; inline it or make it a required behavior");
                    }
                }
            }
        }
        // side effect: signatures() already validated composition types
        if (sigs.isEmpty()) {
            return;
        }
    }

    private static Sig pipeSig(Ast.PipeBehavior pipe, Map<String, Sig> sigs) {
        List<String> stages = pipe.stages();
        for (String s : stages) {
            if (!sigs.containsKey(s)) {
                throw new CompileException(pipe.pos(), "unknown behavior `" + s + "` in pipeline");
            }
        }
        for (int i = 0; i < stages.size() - 1; i++) {
            Type out = sigs.get(stages.get(i)).out();
            Type in = sigs.get(stages.get(i + 1)).in();
            if (!out.equals(in)) {
                throw new CompileException(pipe.pos(), "E1701",
                        "Cannot compose behaviors. Left output: " + out + ", right input: " + in);
            }
        }
        return new Sig(sigs.get(stages.get(0)).in(), sigs.get(stages.get(stages.size() - 1)).out());
    }

    private static void checkBodyBehavior(Ast.BodyBehavior b, Map<String, Ast.Def> symbols,
                                          Set<String> allBehaviors, Map<String, ReqSig> reqSigs) {
        Type successType = resolveType(b.ret().success(), symbols);
        boolean isResult = b.ret().error().isPresent();
        Type errorType = isResult ? resolveType(b.ret().error().get(), symbols) : null;

        Map<String, Type> env = new HashMap<>();
        for (Ast.Param p : b.params()) {
            env.put(p.name(), resolveType(p.type(), symbols));
        }
        for (Ast.BStmt stmt : b.stmts()) {
            switch (stmt) {
                case Ast.Let let -> {
                    if (let.value() instanceof Ast.Call call && allBehaviors.contains(call.fn())) {
                        ReqSig callee = reqSigs.get(call.fn());
                        if (callee == null) {
                            throw new CompileException(call.pos(),
                                    "only required behaviors can be called from a body; compose others with `>>`");
                        }
                        if (!isResult) {
                            throw new CompileException(let.pos(), "calling `" + call.fn()
                                    + "` needs a Result return type on behavior `" + b.name() + "`");
                        }
                        if (call.args().size() != 1) {
                            throw new CompileException(call.pos(), call.fn() + " takes one argument");
                        }
                        requireType(call.args().get(0), callee.param(), env, null, symbols,
                                "argument of " + call.fn());
                        env.put(let.name(), callee.success());
                    } else {
                        env.put(let.name(), typeOf(let.value(), env, null, symbols));
                    }
                }
                case Ast.Guard guard -> {
                    requireType(guard.cond(), Type.BOOL, env, null, symbols, "require condition");
                    if (!isResult) {
                        throw new CompileException(guard.pos(),
                                "`require` needs a Result return type on behavior `" + b.name() + "`");
                    }
                    Type ft = typeOf(guard.failure(), env, null, symbols);
                    if (!ft.equals(errorType)) {
                        throw new CompileException(guard.failure().pos(),
                                "failure must be " + errorType + " but is " + ft);
                    }
                }
            }
        }
        Type rt = typeOf(b.result(), env, null, symbols);
        if (!rt.equals(successType)) {
            throw new CompileException(b.result().pos(),
                    "behavior `" + b.name() + "` returns " + successType + " but its body is " + rt);
        }

        Set<String> constructed = new HashSet<>();
        collectConstructs(b.result(), constructed);
        for (Ast.BStmt stmt : b.stmts()) {
            if (stmt instanceof Ast.Let let) {
                collectConstructs(let.value(), constructed);
            }
        }
        for (String c : constructed) {
            if (!b.constructs().contains(c)) {
                throw new CompileException(b.pos(), "E1002",
                        "Behavior `" + b.name() + "` constructs `" + c
                                + "` but does not declare `constructs " + c + "`.");
            }
            if (symbols.get(c) instanceof Ast.Data d && !effectiveInvariants(d, symbols).isEmpty()) {
                throw new CompileException(b.pos(), "E1003",
                        "Behavior `" + b.name() + "` constructs `" + c
                                + "`, which has an invariant; construct it through a decoder instead.");
            }
        }
    }

    private static void collectConstructs(Ast.Expr e, Set<String> out) {
        switch (e) {
            case Ast.NewData nd -> {
                out.add(nd.typeName());
                for (Ast.FieldInit init : nd.inits()) {
                    collectConstructs(init.value(), out);
                }
            }
            case Ast.FieldAccess fa -> collectConstructs(fa.target(), out);
            case Ast.Call call -> call.args().forEach(a -> collectConstructs(a, out));
            case Ast.Binary bin -> {
                collectConstructs(bin.left(), out);
                collectConstructs(bin.right(), out);
            }
            case Ast.Not not -> collectConstructs(not.operand(), out);
            case Ast.Match m -> {
                collectConstructs(m.scrutinee(), out);
                for (Ast.Case c : m.cases()) {
                    collectConstructs(c.body(), out);
                }
            }
            case Ast.IntLit ignored -> { }
            case Ast.StringLit ignored -> { }
            case Ast.BoolLit ignored -> { }
            case Ast.Var ignored -> { }
        }
    }

    /** Builds the name → definition table for a module. */
    public static Map<String, Ast.Def> symbols(Ast.Module module) {
        Map<String, Ast.Def> symbols = new HashMap<>();
        for (Ast.Def def : module.defs()) {
            if (symbols.put(def.name(), def) != null) {
                throw new CompileException(def.pos(), "duplicate data `" + def.name() + "`");
            }
        }
        return symbols;
    }

    private static void checkSum(Ast.SumData sum, Map<String, Ast.Def> symbols) {
        for (String arm : sum.arms()) {
            if (!symbols.containsKey(arm)) {
                throw new CompileException(sum.pos(),
                        "unknown arm `" + arm + "` in sum `" + sum.name() + "`");
            }
        }
        sum.decoder().ifPresent(disc -> {
            for (Ast.Variant v : disc.variants()) {
                Ast.Def armDef = symbols.get(v.armType());
                if (armDef == null || !sum.arms().contains(v.armType())) {
                    throw new CompileException(v.pos(),
                            "variant `" + v.armType() + "` is not an arm of `" + sum.name() + "`");
                }
                if (!(armDef instanceof Ast.Data d) || d.decoder().isEmpty()) {
                    throw new CompileException(v.pos(),
                            "variant `" + v.armType() + "` needs a decoder");
                }
            }
        });
    }

    /** Effective field name → type (included data flattened first, then own fields). */
    public static Map<String, Type> fieldTypes(Ast.Data data, Map<String, Ast.Def> symbols) {
        Map<String, Type> types = new LinkedHashMap<>();
        for (String inc : data.includes()) {
            if (!(symbols.get(inc) instanceof Ast.Data id)) {
                throw new CompileException(data.pos(),
                        "cannot `include " + inc + "` (not a product data)");
            }
            for (Map.Entry<String, Type> e : fieldTypes(id, symbols).entrySet()) {
                if (types.put(e.getKey(), e.getValue()) != null) {
                    throw new CompileException(data.pos(), "E1004", "Field `" + e.getKey()
                            + "` from `include " + inc + "` conflicts with a field of `" + data.name() + "`.");
                }
            }
        }
        for (Ast.Field f : data.fields()) {
            if (types.put(f.name(), resolveType(f.type(), symbols)) != null) {
                throw new CompileException(f.pos(), "E1004",
                        "duplicate field `" + f.name() + "` in `" + data.name() + "`");
            }
        }
        return types;
    }

    /** All invariants that apply to a data: included data's invariants first, then its own. */
    public static List<Ast.Expr> effectiveInvariants(Ast.Data data, Map<String, Ast.Def> symbols) {
        List<Ast.Expr> invs = new ArrayList<>();
        for (String inc : data.includes()) {
            if (symbols.get(inc) instanceof Ast.Data id) {
                invs.addAll(effectiveInvariants(id, symbols));
            }
        }
        data.invariant().ifPresent(invs::add);
        return invs;
    }

    private static void checkData(Ast.Data data, Map<String, Ast.Def> symbols) {
        Map<String, Type> fields = fieldTypes(data, symbols);

        data.invariant().ifPresent(expr -> {
            Type t = typeOf(expr, fields, data, symbols);
            if (t != Type.BOOL) {
                throw new CompileException(expr.pos(), "E1101",
                        "Invariant expression must have type Bool. Found: " + t);
            }
        });

        data.decoder().ifPresent(dec -> checkDecoder(dec, data, fields, symbols));
        data.encoder().ifPresent(enc -> checkEncoder(enc, data, symbols));
    }

    private static void checkDecoder(Ast.DecoderDef dec, Ast.Data data, Map<String, Type> fields,
                                     Map<String, Ast.Def> symbols) {
        switch (dec) {
            case Ast.PrimDecoder prim -> {
                Type inputType = prim.from() == Ast.RawKind.TEXT ? Type.STRING : Type.INT;
                Map<String, Type> env = new HashMap<>();
                env.put(prim.inputName(), inputType);
                for (Ast.DecStmt stmt : prim.stmts()) {
                    switch (stmt) {
                        case Ast.Let let -> env.put(let.name(), typeOf(let.value(), env, data, symbols));
                        case Ast.Require req -> requireBool(req.cond(), env, data, symbols);
                    }
                }
                checkConstruct(prim.result(), data, fields, env, symbols);
            }
            case Ast.ObjectDecoder obj -> {
                Map<String, Type> env = new HashMap<>();
                for (Ast.Bind bind : obj.binds()) {
                    env.put(bind.name(), decRefType(bind.ref(), symbols));
                }
                checkConstruct(obj.result(), data, fields, env, symbols);
            }
        }
    }

    private static Type decRefType(Ast.DecRef ref, Map<String, Ast.Def> symbols) {
        return switch (ref) {
            case Ast.PrimDecRef p -> p.kind() == Ast.PrimKind.STRING ? Type.STRING : Type.INT;
            case Ast.DataDecRef d -> {
                Ast.Def def = symbols.get(d.typeName());
                boolean hasDecoder = (def instanceof Ast.Data dd && dd.decoder().isPresent())
                        || (def instanceof Ast.SumData s && s.decoder().isPresent());
                if (!hasDecoder) {
                    throw new CompileException(d.pos(),
                            "`" + d.typeName() + "` has no decoder to call `" + d.typeName() + ".decoder`");
                }
                yield Type.ref(d.typeName());
            }
            case Ast.ListDecRef l -> Type.list(decRefType(l.element(), symbols));
        };
    }

    private static void checkConstruct(Ast.Construct c, Ast.Data data, Map<String, Type> fields,
                                       Map<String, Type> env, Map<String, Ast.Def> symbols) {
        if (!c.typeName().equals(data.name())) {
            throw new CompileException(c.pos(),
                    "decoder for `" + data.name() + "` must construct `" + data.name()
                            + "`, but constructs `" + c.typeName() + "`");
        }
        checkConstruction(c.typeName(), c.inits(), c.spreads(), c.pos(), fields, env, data, symbols);
    }

    private static void checkConstruction(String typeName, List<Ast.FieldInit> inits, List<String> spreads,
                                          SourcePos pos, Map<String, Type> fields, Map<String, Type> env,
                                          Ast.Data data, Map<String, Ast.Def> symbols) {
        Map<String, Ast.FieldInit> byName = new HashMap<>();
        for (Ast.FieldInit init : inits) {
            if (byName.put(init.name(), init) != null) {
                throw new CompileException(init.pos(), "duplicate field `" + init.name() + "`");
            }
            Type ft = fields.get(init.name());
            if (ft == null) {
                throw new CompileException(init.pos(),
                        "`" + init.name() + "` is not a field of `" + typeName + "`");
            }
            Type vt = typeOf(init.value(), env, data, symbols);
            if (!vt.equals(ft)) {
                throw new CompileException(init.pos(),
                        "field `" + init.name() + "` expects " + ft + " but got " + vt);
            }
        }
        Map<String, Type> provided = new HashMap<>();
        for (String sp : spreads) {
            if (!(env.get(sp) instanceof Type.Ref ref)
                    || !(symbols.get(ref.name()) instanceof Ast.Data sd)) {
                throw new CompileException(pos, "spread `.." + sp + "` must be a data value");
            }
            provided.putAll(fieldTypes(sd, symbols));
        }
        for (Map.Entry<String, Type> f : fields.entrySet()) {
            if (byName.containsKey(f.getKey())) {
                continue;
            }
            Type pv = provided.get(f.getKey());
            if (pv == null) {
                throw new CompileException(pos, "E1005",
                        "construction of `" + typeName + "` is missing field `" + f.getKey() + "`");
            }
            if (!pv.equals(f.getValue())) {
                throw new CompileException(pos, "spread provides `" + f.getKey() + "` as " + pv
                        + " but `" + typeName + "` needs " + f.getValue());
            }
        }
    }

    private static void checkEncoder(Ast.EncoderDef enc, Ast.Data data, Map<String, Ast.Def> symbols) {
        Map<String, Type> env = Map.of(enc.selfName(), Type.ref(data.name()));
        checkRawExpr(enc.result(), env, data, symbols);
    }

    private static void checkRawExpr(Ast.RawExpr raw, Map<String, Type> env, Ast.Data data,
                                     Map<String, Ast.Def> symbols) {
        switch (raw) {
            case Ast.TextRaw t -> requireType(t.arg(), Type.STRING, env, data, symbols, "argument of Text");
            case Ast.IntRaw i -> requireType(i.arg(), Type.INT, env, data, symbols, "argument of Int");
            case Ast.ObjectRaw o -> {
                for (Ast.RawEntry entry : o.entries()) {
                    checkRawExpr(entry.value(), env, data, symbols);
                }
            }
            case Ast.EncodeRaw e -> {
                if (!(symbols.get(e.typeName()) instanceof Ast.Data enc) || enc.encoder().isEmpty()) {
                    throw new CompileException(e.pos(),
                            "`" + e.typeName() + "` has no encoder to call `" + e.typeName() + ".encode`");
                }
                requireType(e.arg(), Type.ref(e.typeName()), env, data, symbols,
                        "argument of " + e.typeName() + ".encode");
            }
        }
    }

    // --- expression typing (shared with the backend) ---

    public static Type typeOf(Ast.Expr e, Map<String, Type> env, Ast.Data data,
                              Map<String, Ast.Def> symbols) {
        return switch (e) {
            case Ast.IntLit ignored -> Type.INT;
            case Ast.StringLit ignored -> Type.STRING;
            case Ast.BoolLit ignored -> Type.BOOL;
            case Ast.Var v -> {
                Type t = env.get(v.name());
                if (t == null) {
                    throw new CompileException(v.pos(), "unknown identifier `" + v.name() + "`");
                }
                yield t;
            }
            case Ast.FieldAccess fa -> typeOfFieldAccess(fa, env, data, symbols);
            case Ast.Call call -> typeOfCall(call, env, data, symbols);
            case Ast.Not not -> {
                requireType(not.operand(), Type.BOOL, env, data, symbols, "operand of `!`");
                yield Type.BOOL;
            }
            case Ast.Binary bin -> typeOfBinary(bin, env, data, symbols);
            case Ast.NewData nd -> {
                if (!(symbols.get(nd.typeName()) instanceof Ast.Data owner)) {
                    throw new CompileException(nd.pos(), "cannot construct `" + nd.typeName() + "`");
                }
                checkConstruction(nd.typeName(), nd.inits(), nd.spreads(), nd.pos(),
                        fieldTypes(owner, symbols), env, data, symbols);
                yield Type.ref(nd.typeName());
            }
            case Ast.Match m -> typeOfMatch(m, env, data, symbols);
        };
    }

    private static Type typeOfMatch(Ast.Match m, Map<String, Type> env, Ast.Data data,
                                    Map<String, Ast.Def> symbols) {
        Type st = typeOf(m.scrutinee(), env, data, symbols);
        if (!(st instanceof Type.Ref ref) || !(symbols.get(ref.name()) instanceof Ast.SumData sum)) {
            throw new CompileException(m.pos(), "match requires a sum-typed value, got " + st);
        }
        Set<String> covered = new HashSet<>();
        Type branchType = null;
        for (Ast.Case c : m.cases()) {
            if (!sum.arms().contains(c.armType())) {
                throw new CompileException(c.pos(),
                        "`" + c.armType() + "` is not an arm of `" + sum.name() + "`");
            }
            covered.add(c.armType());
            Map<String, Type> benv = new HashMap<>(env);
            benv.put(c.binding(), Type.ref(c.armType()));
            Type bt = typeOf(c.body(), benv, data, symbols);
            if (branchType == null) {
                branchType = bt;
            } else if (!branchType.equals(bt)) {
                throw new CompileException(c.pos(),
                        "match branches disagree: " + branchType + " vs " + bt);
            }
        }
        for (String arm : sum.arms()) {
            if (!covered.contains(arm)) {
                throw new CompileException(m.pos(), "E1201",
                        "Non-exhaustive match for data `" + sum.name() + "`. Missing case: " + arm);
            }
        }
        if (branchType == null) {
            throw new CompileException(m.pos(), "match has no cases");
        }
        return branchType;
    }


    private static Type typeOfFieldAccess(Ast.FieldAccess fa, Map<String, Type> env, Ast.Data data,
                                          Map<String, Ast.Def> symbols) {
        Type target = typeOf(fa.target(), env, data, symbols);
        if (target instanceof Type.Ref ref && symbols.get(ref.name()) instanceof Ast.Data owner) {
            Type ft = fieldTypes(owner, symbols).get(fa.field());
            if (ft != null) {
                return ft;
            }
        }
        throw new CompileException(fa.pos(), "cannot access field `" + fa.field() + "` on this value");
    }

    private static Type typeOfCall(Ast.Call call, Map<String, Type> env, Ast.Data data,
                                   Map<String, Ast.Def> symbols) {
        List<Ast.Expr> args = call.args();
        return switch (call.fn()) {
            case "length" -> {
                arity(call, 1);
                requireType(args.get(0), Type.STRING, env, data, symbols, "argument of length");
                yield Type.INT;
            }
            case "contains" -> {
                arity(call, 2);
                requireType(args.get(0), Type.STRING, env, data, symbols, "argument 1 of contains");
                requireType(args.get(1), Type.STRING, env, data, symbols, "argument 2 of contains");
                yield Type.BOOL;
            }
            case "trim" -> {
                arity(call, 1);
                requireType(args.get(0), Type.STRING, env, data, symbols, "argument of trim");
                yield Type.STRING;
            }
            case "lowercase" -> {
                arity(call, 1);
                requireType(args.get(0), Type.STRING, env, data, symbols, "argument of lowercase");
                yield Type.STRING;
            }
            case "size" -> {
                arity(call, 1);
                if (!(typeOf(args.get(0), env, data, symbols) instanceof Type.ListOf)) {
                    throw new CompileException(call.pos(), "size expects a List");
                }
                yield Type.INT;
            }
            default -> throw new CompileException(call.pos(), "unknown function `" + call.fn() + "`");
        };
    }

    private static Type typeOfBinary(Ast.Binary bin, Map<String, Type> env, Ast.Data data,
                                     Map<String, Ast.Def> symbols) {
        return switch (bin.op()) {
            case AND, OR -> {
                requireType(bin.left(), Type.BOOL, env, data, symbols, "operand of logical operator");
                requireType(bin.right(), Type.BOOL, env, data, symbols, "operand of logical operator");
                yield Type.BOOL;
            }
            case LT, LE, GT, GE -> {
                requireType(bin.left(), Type.INT, env, data, symbols, "operand of comparison");
                requireType(bin.right(), Type.INT, env, data, symbols, "operand of comparison");
                yield Type.BOOL;
            }
            case ADD, SUB, MUL -> {
                requireType(bin.left(), Type.INT, env, data, symbols, "operand of arithmetic");
                requireType(bin.right(), Type.INT, env, data, symbols, "operand of arithmetic");
                yield Type.INT;
            }
            case EQ, NE -> {
                Type lt = typeOf(bin.left(), env, data, symbols);
                Type rt = typeOf(bin.right(), env, data, symbols);
                if (!lt.equals(rt) || lt instanceof Type.Ref) {
                    throw new CompileException(bin.pos(), "cannot compare " + lt + " with " + rt);
                }
                yield Type.BOOL;
            }
        };
    }

    private static void requireBool(Ast.Expr cond, Map<String, Type> env, Ast.Data data,
                                    Map<String, Ast.Def> symbols) {
        Type t = typeOf(cond, env, data, symbols);
        if (t != Type.BOOL) {
            throw new CompileException(cond.pos(), "require condition must have type Bool. Found: " + t);
        }
    }

    private static void arity(Ast.Call call, int n) {
        if (call.args().size() != n) {
            throw new CompileException(call.pos(),
                    call.fn() + " expects " + n + " argument(s), got " + call.args().size());
        }
    }

    private static void requireType(Ast.Expr e, Type expected, Map<String, Type> env, Ast.Data data,
                                    Map<String, Ast.Def> symbols, String what) {
        Type actual = typeOf(e, env, data, symbols);
        if (!actual.equals(expected)) {
            throw new CompileException(e.pos(), what + " must be " + expected + " but is " + actual);
        }
    }

    public static Type resolveType(Ast.TypeRef ref, Map<String, Ast.Def> symbols) {
        return switch (ref.name()) {
            case "Int" -> Type.INT;
            case "String" -> Type.STRING;
            case "List" -> {
                if (ref.arg() == null) {
                    throw new CompileException(ref.pos(), "List needs a type argument, e.g. List<Int>");
                }
                yield Type.list(resolveType(ref.arg(), symbols));
            }
            default -> {
                if (symbols.containsKey(ref.name())) {
                    yield Type.ref(ref.name());
                }
                throw new CompileException(ref.pos(), "unknown type `" + ref.name() + "`");
            }
        };
    }
}
