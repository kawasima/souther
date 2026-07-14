package net.unit8.souther.compiler.check;

import net.unit8.souther.compiler.CompileException;
import net.unit8.souther.compiler.SourcePos;
import net.unit8.souther.compiler.ast.Ast;

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
        for (Ast.BehaviorDef b : module.behaviors()) {
            if (b instanceof Ast.BodyBehavior body) {
                checkBodyBehavior(body, symbols);
            }
        }
        checkPipelines(module, symbols);
    }

    /** A behavior's input and output types. */
    public record Sig(Type in, Type out) {}

    /** Builds the input/output signature of every behavior, checking pipeline composition. */
    public static Map<String, Sig> signatures(Ast.Module module, Map<String, Ast.Def> symbols) {
        Map<String, Sig> sigs = new HashMap<>();
        for (Ast.BehaviorDef b : module.behaviors()) {
            if (b instanceof Ast.BodyBehavior body) {
                sigs.put(body.name(), new Sig(resolveType(body.paramType(), symbols),
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

    private static void checkPipelines(Ast.Module module, Map<String, Ast.Def> symbols) {
        signatures(module, symbols);
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

    private static void checkBodyBehavior(Ast.BodyBehavior b, Map<String, Ast.Def> symbols) {
        Type paramType = resolveType(b.paramType(), symbols);
        Type successType = resolveType(b.ret().success(), symbols);
        boolean isResult = b.ret().error().isPresent();
        Type errorType = isResult ? resolveType(b.ret().error().get(), symbols) : null;

        Map<String, Type> env = new HashMap<>();
        env.put(b.paramName(), paramType);
        for (Ast.BStmt stmt : b.stmts()) {
            switch (stmt) {
                case Ast.Let let -> env.put(let.name(), typeOf(let.value(), env, null, symbols));
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
            if (symbols.get(c) instanceof Ast.Data d && d.invariant().isPresent()) {
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

    /** Field name → type, in declaration order. */
    public static Map<String, Type> fieldTypes(Ast.Data data, Map<String, Ast.Def> symbols) {
        Map<String, Type> types = new LinkedHashMap<>();
        for (Ast.Field f : data.fields()) {
            types.put(f.name(), resolveType(f.type(), symbols));
        }
        return types;
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
                if (!symbols.containsKey(d.typeName())) {
                    throw new CompileException(d.pos(), "unknown data type `" + d.typeName() + "`");
                }
                yield Type.ref(d.typeName());
            }
        };
    }

    private static void checkConstruct(Ast.Construct c, Ast.Data data, Map<String, Type> fields,
                                       Map<String, Type> env, Map<String, Ast.Def> symbols) {
        if (!c.typeName().equals(data.name())) {
            throw new CompileException(c.pos(),
                    "decoder for `" + data.name() + "` must construct `" + data.name()
                            + "`, but constructs `" + c.typeName() + "`");
        }
        Map<String, Ast.FieldInit> byName = new HashMap<>();
        for (Ast.FieldInit init : c.inits()) {
            if (byName.put(init.name(), init) != null) {
                throw new CompileException(init.pos(), "duplicate field `" + init.name() + "`");
            }
        }
        if (byName.size() != fields.size() || !byName.keySet().equals(fields.keySet())) {
            throw new CompileException(c.pos(),
                    "construction of `" + data.name() + "` must set exactly its fields "
                            + fields.keySet());
        }
        for (Map.Entry<String, Type> field : fields.entrySet()) {
            Ast.FieldInit init = byName.get(field.getKey());
            Type valueType = typeOf(init.value(), env, data, symbols);
            if (!valueType.equals(field.getValue())) {
                throw new CompileException(init.pos(),
                        "field `" + init.name() + "` expects " + field.getValue()
                                + " but got " + valueType);
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
                if (!symbols.containsKey(e.typeName())) {
                    throw new CompileException(e.pos(), "unknown data type `" + e.typeName() + "`");
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
                checkInits(nd.typeName(), nd.inits(), nd.pos(), fieldTypes(owner, symbols), env, data, symbols);
                yield Type.ref(nd.typeName());
            }
        };
    }

    private static void checkInits(String typeName, List<Ast.FieldInit> inits, SourcePos pos,
                                   Map<String, Type> fields, Map<String, Type> env, Ast.Data data,
                                   Map<String, Ast.Def> symbols) {
        Map<String, Ast.FieldInit> byName = new HashMap<>();
        for (Ast.FieldInit init : inits) {
            byName.put(init.name(), init);
        }
        if (byName.size() != fields.size() || !byName.keySet().equals(fields.keySet())) {
            throw new CompileException(pos,
                    "construction of `" + typeName + "` must set exactly its fields " + fields.keySet());
        }
        for (Map.Entry<String, Type> field : fields.entrySet()) {
            Type valueType = typeOf(byName.get(field.getKey()).value(), env, data, symbols);
            if (!valueType.equals(field.getValue())) {
                throw new CompileException(byName.get(field.getKey()).pos(),
                        "field `" + field.getKey() + "` expects " + field.getValue() + " but got " + valueType);
            }
        }
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
            default -> {
                if (symbols.containsKey(ref.name())) {
                    yield Type.ref(ref.name());
                }
                throw new CompileException(ref.pos(), "unknown type `" + ref.name() + "`");
            }
        };
    }
}
