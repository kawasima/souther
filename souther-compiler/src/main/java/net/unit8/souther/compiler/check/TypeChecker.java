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
        check(module, symbols(module));
    }

    /** Type-checks a module against {@code symbols} (own definitions plus any imported ones). */
    public static void check(Ast.Module module, Map<String, Ast.Def> symbols) {
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
                    successType(r.ret(), symbols)));
        }
        for (Ast.BehaviorDef b : module.behaviors()) {
            if (b instanceof Ast.BodyBehavior body) {
                checkBodyBehavior(body, symbols, allBehaviors, reqSigs);
            }
        }
        // validates composition types; a stage's own requirements propagate to the pipeline (14.3)
        signatures(module, symbols);
    }

    /** A required behavior's input and success types (for typing calls). */
    private record ReqSig(Type param, Type success) {}

    /** The distinct required behaviors a body calls, in first-seen order. Calls may appear
     * anywhere in an expression (e.g. inline in a record literal), not only bound to a let. */
    public static List<String> requiredCalls(Ast.BodyBehavior body, java.util.Set<String> requiredNames) {
        List<String> calls = new java.util.ArrayList<>();
        for (Ast.BStmt stmt : body.stmts()) {
            switch (stmt) {
                case Ast.Let let -> collectRequiredCalls(let.value(), requiredNames, calls);
                case Ast.Guard guard -> {
                    collectRequiredCalls(guard.cond(), requiredNames, calls);
                    collectRequiredCalls(guard.failure(), requiredNames, calls);
                }
            }
        }
        collectRequiredCalls(body.result(), requiredNames, calls);
        return calls;
    }

    private static void collectRequiredCalls(Ast.Expr e, Set<String> requiredNames, List<String> out) {
        switch (e) {
            case Ast.Call call -> {
                if (requiredNames.contains(call.fn()) && !out.contains(call.fn())) {
                    out.add(call.fn());
                }
                call.args().forEach(a -> collectRequiredCalls(a, requiredNames, out));
            }
            case Ast.NewData nd -> nd.inits().forEach(i -> collectRequiredCalls(i.value(), requiredNames, out));
            case Ast.FieldAccess fa -> collectRequiredCalls(fa.target(), requiredNames, out);
            case Ast.Binary bin -> {
                collectRequiredCalls(bin.left(), requiredNames, out);
                collectRequiredCalls(bin.right(), requiredNames, out);
            }
            case Ast.Not not -> collectRequiredCalls(not.operand(), requiredNames, out);
            case Ast.Match m -> {
                collectRequiredCalls(m.scrutinee(), requiredNames, out);
                m.cases().forEach(c -> collectRequiredCalls(c.body(), requiredNames, out));
            }
            case Ast.If iff -> {
                collectRequiredCalls(iff.cond(), requiredNames, out);
                collectRequiredCalls(iff.then(), requiredNames, out);
                collectRequiredCalls(iff.els(), requiredNames, out);
            }
            default -> { }
        }
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
                sigs.put(body.name(), new Sig(successType(body.params().get(0).type(), symbols),
                        successType(body.ret(), symbols)));
            }
        }
        for (Ast.RequiredBehavior r : module.requireds()) {
            sigs.put(r.name(), new Sig(resolveType(r.paramType(), symbols),
                    successType(r.ret(), symbols)));
        }
        for (Ast.BehaviorDef b : module.behaviors()) {
            if (b instanceof Ast.PipeBehavior pipe) {
                sigs.put(pipe.name(), pipeSig(pipe, sigs, symbols));
            }
        }
        return sigs;
    }

    /** The signature of a pipeline stage: a behavior, or a {@code Type.decoder}/{@code Type.encoder}
     * boundary codec (spec 14.1). A decoder is {@code Raw -> T | 復号失敗}; an encoder is {@code T -> Raw}. */
    public static Sig stageSig(String stage, Map<String, Sig> sigs, Map<String, Ast.Def> symbols,
                               SourcePos pos) {
        if (stage.endsWith(".decoder")) {
            String base = codecBase(stage, symbols, pos);
            return new Sig(Type.RAW, Type.union(new java.util.LinkedHashSet<>(List.of(base, "復号失敗"))));
        }
        if (stage.endsWith(".encoder")) {
            return new Sig(Type.ref(codecBase(stage, symbols, pos)), Type.RAW);
        }
        Sig s = sigs.get(stage);
        if (s == null) {
            throw new CompileException(pos, "unknown behavior `" + stage + "` in pipeline");
        }
        return s;
    }

    private static String codecBase(String stage, Map<String, Ast.Def> symbols, SourcePos pos) {
        String base = stage.substring(0, stage.lastIndexOf('.'));
        if (!symbols.containsKey(base)) {
            throw new CompileException(pos, "unknown data `" + base + "` in pipeline stage `" + stage + "`");
        }
        return base;
    }

    private static Sig pipeSig(Ast.PipeBehavior pipe, Map<String, Sig> sigs, Map<String, Ast.Def> symbols) {
        List<String> stages = pipe.stages();
        Sig first = stageSig(stages.get(0), sigs, symbols, pipe.pos());
        Type running = first.out();
        for (int i = 1; i < stages.size(); i++) {
            running = route(running, stageSig(stages.get(i), sigs, symbols, pipe.pos()), pipe.pos());
        }
        return new Sig(first.in(), running);
    }

    /**
     * Type-routed composition (spec 14.2): the arms of {@code out} that the next stage's input
     * accepts feed it; the rest propagate. The composed output is {@code g.out} unioned with the
     * propagated arms. Returns which arms were consumed via {@code consumed} (may be null).
     */
    public static Type route(Type out, Sig g, SourcePos pos) {
        Type in = g.in();
        if (isDataLike(out)) {
            Set<String> consumed = new HashSet<>();
            Set<String> rest = new HashSet<>();
            for (String arm : namesOf(out)) {
                if (subtypeOf(Type.ref(arm), in)) {
                    consumed.add(arm);
                } else {
                    rest.add(arm);
                }
            }
            if (consumed.isEmpty()) {
                throw new CompileException(pos, "E1701",
                        "Cannot compose behaviors: no output arm of the left behavior is accepted by "
                                + "the right behavior's input. Left output: " + out + ", right input: " + in);
            }
            Set<String> gArms = armNamesOf(g.out());
            if (gArms.isEmpty()) {
                if (!rest.isEmpty()) {
                    throw new CompileException(pos, "E1701",
                            "cannot merge non-data stage output " + g.out() + " with propagated arms " + rest);
                }
                return g.out();
            }
            Set<String> result = new HashSet<>(rest);
            result.addAll(gArms);
            return armSetType(result);
        }
        if (!out.equals(in)) {
            throw new CompileException(pos, "E1701",
                    "Cannot compose behaviors. Left output: " + out + ", right input: " + in);
        }
        return g.out();
    }

    private static void checkBodyBehavior(Ast.BodyBehavior b, Map<String, Ast.Def> symbols,
                                          Set<String> allBehaviors, Map<String, ReqSig> reqSigs) {
        Type output = successType(b.ret(), symbols);
        Set<String> outputArms = namesOf(output);

        Map<String, Type> env = new HashMap<>();
        for (Ast.Param p : b.params()) {
            env.put(p.name(), successType(p.type(), symbols));
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
                        if (call.args().size() != 1) {
                            throw new CompileException(call.pos(), call.fn() + " takes one argument");
                        }
                        requireType(call.args().get(0), callee.param(), env, null, symbols, reqSigs,
                                "argument of " + call.fn());
                        env.put(let.name(), callee.success());
                    } else {
                        env.put(let.name(), typeOf(let.value(), env, null, symbols, reqSigs));
                    }
                }
                case Ast.Guard guard -> {
                    requireType(guard.cond(), Type.BOOL, env, null, symbols, reqSigs, "require condition");
                    Type ft = typeOf(guard.failure(), env, null, symbols, reqSigs);
                    if (!subtypeOf(ft, output)) {
                        throw new CompileException(guard.failure().pos(),
                                "`require ... else` value must be one of the output arms " + output
                                        + " but is " + ft);
                    }
                }
            }
        }
        Type rt = typeOf(b.result(), env, null, symbols, reqSigs);
        if (!subtypeOf(rt, output)) {
            throw new CompileException(b.result().pos(),
                    "behavior `" + b.name() + "` returns " + output + " but its body is " + rt);
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
            // an invariant violation needs an output arm to go to (spec 9.4)
            if (isInvariantBearing(c, symbols) && !outputArms.contains("制約違反")) {
                throw new CompileException(b.pos(), "E1003",
                        "Behavior `" + b.name() + "` constructs `" + c
                                + "`, which has an invariant, but its output has no place for a "
                                + "violation. Add an arm `制約違反`.");
            }
        }
        // invariant-bearing construction is railway-bound, so it may only be the behavior's result
        for (Ast.BStmt stmt : b.stmts()) {
            if (stmt instanceof Ast.Let let) {
                forbidInvariantConstruct(let.value(), symbols);
            }
        }
        if (b.result() instanceof Ast.NewData nd && isInvariantBearing(nd.typeName(), symbols)) {
            for (Ast.FieldInit init : nd.inits()) {
                forbidInvariantConstruct(init.value(), symbols);
            }
        } else {
            forbidInvariantConstruct(b.result(), symbols);
        }
    }

    public static boolean isInvariantBearing(String typeName, Map<String, Ast.Def> symbols) {
        return symbols.get(typeName) instanceof Ast.Data d && !effectiveInvariants(d, symbols).isEmpty();
    }

    private static void forbidInvariantConstruct(Ast.Expr e, Map<String, Ast.Def> symbols) {
        if (e instanceof Ast.NewData nd && isInvariantBearing(nd.typeName(), symbols)) {
            throw new CompileException(nd.pos(), "invariant-bearing `" + nd.typeName()
                    + "` can only be constructed as the behavior's result expression");
        }
        switch (e) {
            case Ast.NewData nd -> nd.inits().forEach(i -> forbidInvariantConstruct(i.value(), symbols));
            case Ast.FieldAccess fa -> forbidInvariantConstruct(fa.target(), symbols);
            case Ast.Call call -> call.args().forEach(a -> forbidInvariantConstruct(a, symbols));
            case Ast.Binary bin -> {
                forbidInvariantConstruct(bin.left(), symbols);
                forbidInvariantConstruct(bin.right(), symbols);
            }
            case Ast.Not not -> forbidInvariantConstruct(not.operand(), symbols);
            case Ast.Match m -> {
                forbidInvariantConstruct(m.scrutinee(), symbols);
                m.cases().forEach(c -> forbidInvariantConstruct(c.body(), symbols));
            }
            case Ast.If iff -> {
                forbidInvariantConstruct(iff.cond(), symbols);
                forbidInvariantConstruct(iff.then(), symbols);
                forbidInvariantConstruct(iff.els(), symbols);
            }
            default -> { }
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
            case Ast.If iff -> {
                collectConstructs(iff.cond(), out);
                collectConstructs(iff.then(), out);
                collectConstructs(iff.els(), out);
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
        sum.encoder().ifPresent(enc -> {
            Set<String> covered = new HashSet<>();
            for (Ast.EncVariant v : enc.variants()) {
                if (!sum.arms().contains(v.armType())) {
                    throw new CompileException(v.pos(),
                            "`" + v.armType() + "` is not an arm of `" + sum.name() + "`");
                }
                if (!(symbols.get(v.armType()) instanceof Ast.Data d) || d.encoder().isEmpty()) {
                    throw new CompileException(v.pos(), "arm `" + v.armType() + "` needs an encoder");
                }
                covered.add(v.armType());
            }
            for (String arm : sum.arms()) {
                if (!covered.contains(arm)) {
                    throw new CompileException(enc.pos(),
                            "encoder for `" + sum.name() + "` is missing arm `" + arm + "`");
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
                Type inputType = primType(prim.from());
                Map<String, Type> env = new HashMap<>();
                env.put(prim.inputName(), inputType);
                for (Ast.DecStmt stmt : prim.stmts()) {
                    switch (stmt) {
                        case Ast.Let let -> env.put(let.name(), typeOf(let.value(), env, data, symbols));
                        case Ast.Require req -> requireType(req.cond(), Type.BOOL, env, data, symbols,
                                NO_REQS, "require condition");
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

    public static Type primType(Ast.RawKind kind) {
        return switch (kind) {
            case TEXT -> Type.STRING;
            case INT -> Type.INT;
            case BOOL -> Type.BOOL;
            case DECIMAL -> Type.DECIMAL;
            case DATE -> Type.DATE;
            case DATETIME -> Type.DATETIME;
        };
    }

    public static Type primType(Ast.PrimKind kind) {
        return switch (kind) {
            case STRING -> Type.STRING;
            case INT -> Type.INT;
            case BOOL -> Type.BOOL;
            case DECIMAL -> Type.DECIMAL;
            case DATE -> Type.DATE;
            case DATETIME -> Type.DATETIME;
        };
    }

    private static Type decRefType(Ast.DecRef ref, Map<String, Ast.Def> symbols) {
        return switch (ref) {
            case Ast.PrimDecRef p -> primType(p.kind());
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
            case Ast.OptionDecRef o -> Type.option(decRefType(o.element(), symbols));
            case Ast.MapDecRef mp -> Type.map(decRefType(mp.value(), symbols));
        };
    }

    private static void checkConstruct(Ast.Construct c, Ast.Data data, Map<String, Type> fields,
                                       Map<String, Type> env, Map<String, Ast.Def> symbols) {
        if (!c.typeName().equals(data.name())) {
            throw new CompileException(c.pos(),
                    "decoder for `" + data.name() + "` must construct `" + data.name()
                            + "`, but constructs `" + c.typeName() + "`");
        }
        checkConstruction(c.typeName(), c.inits(), c.spreads(), c.pos(), fields, env, data, symbols, NO_REQS);
    }

    private static void checkConstruction(String typeName, List<Ast.FieldInit> inits, List<String> spreads,
                                          SourcePos pos, Map<String, Type> fields, Map<String, Type> env,
                                          Ast.Data data, Map<String, Ast.Def> symbols,
                                          Map<String, ReqSig> reqs) {
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
            Type vt = typeOf(init.value(), env, data, symbols, reqs);
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
            case Ast.TextRaw t -> requireType(t.arg(), Type.STRING, env, data, symbols, NO_REQS,
                    "argument of Text");
            case Ast.IntRaw i -> requireType(i.arg(), Type.INT, env, data, symbols, NO_REQS,
                    "argument of Int");
            case Ast.BoolRaw b -> requireType(b.arg(), Type.BOOL, env, data, symbols, NO_REQS,
                    "argument of Bool");
            case Ast.DecimalRaw d -> requireType(d.arg(), Type.DECIMAL, env, data, symbols, NO_REQS,
                    "argument of Decimal");
            case Ast.IsoTextRaw t -> {
                Type at = typeOf(t.arg(), env, data, symbols);
                if (at != Type.DATE && at != Type.DATETIME) {
                    throw new CompileException(t.pos(), "ISO text encoder expects Date or DateTime, got " + at);
                }
            }
            case Ast.OptionRaw o -> {
                Type at = typeOf(o.access(), env, data, symbols);
                if (!(at instanceof Type.OptionOf oo)) {
                    throw new CompileException(o.pos(), "optional encoder expects an Option, got " + at);
                }
                Map<String, Type> inner = new HashMap<>(env);
                inner.put(o.elemVar(), oo.element());
                checkRawExpr(o.inner(), inner, data, symbols);
            }
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
                requireType(e.arg(), Type.ref(e.typeName()), env, data, symbols, NO_REQS,
                        "argument of " + e.typeName() + ".encode");
            }
            case Ast.ListEnc le -> {
                Type st = typeOf(le.source(), env, data, symbols);
                if (!(st instanceof Type.ListOf lo)) {
                    throw new CompileException(le.pos(), "list(...) source must be a List, got " + st);
                }
                checkEncElem(le.elem(), lo.element(), le.pos(), symbols);
            }
            case Ast.MapEnc me -> {
                Type st = typeOf(me.source(), env, data, symbols);
                if (!(st instanceof Type.MapOf mo)) {
                    throw new CompileException(me.pos(), "map encoder source must be a Map, got " + st);
                }
                checkEncElem(me.elem(), mo.value(), me.pos(), symbols);
            }
        }
    }

    private static void checkEncElem(Ast.EncElem elem, Type elemType, SourcePos pos,
                                     Map<String, Ast.Def> symbols) {
        switch (elem) {
            case Ast.PrimEnc p -> {
                Type expected = p.kind() == Ast.PrimKind.STRING ? Type.STRING : Type.INT;
                if (!elemType.equals(expected)) {
                    throw new CompileException(pos,
                            "element encoder " + p.kind() + " does not match " + elemType);
                }
            }
            case Ast.DataEnc d -> {
                if (!elemType.equals(Type.ref(d.typeName()))
                        || !(symbols.get(d.typeName()) instanceof Ast.Data dd)
                        || dd.encoder().isEmpty()) {
                    throw new CompileException(pos,
                            "element encoder `" + d.typeName() + "` does not match " + elemType);
                }
            }
        }
    }

    // --- expression typing (shared with the backend) ---

    /** No required behaviors are in scope (decoders, encoders, invariants — spec 9.3, 17). */
    private static final Map<String, ReqSig> NO_REQS = Map.of();

    public static Type typeOf(Ast.Expr e, Map<String, Type> env, Ast.Data data,
                              Map<String, Ast.Def> symbols) {
        return typeOf(e, env, data, symbols, NO_REQS);
    }

    public static Type typeOf(Ast.Expr e, Map<String, Type> env, Ast.Data data,
                              Map<String, Ast.Def> symbols, Map<String, ReqSig> reqs) {
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
            case Ast.FieldAccess fa -> typeOfFieldAccess(fa, env, data, symbols, reqs);
            case Ast.Call call -> typeOfCall(call, env, data, symbols, reqs);
            case Ast.Not not -> {
                requireType(not.operand(), Type.BOOL, env, data, symbols, reqs, "operand of `!`");
                yield Type.BOOL;
            }
            case Ast.Binary bin -> typeOfBinary(bin, env, data, symbols, reqs);
            case Ast.NewData nd -> {
                if (!(symbols.get(nd.typeName()) instanceof Ast.Data owner)) {
                    throw new CompileException(nd.pos(), "cannot construct `" + nd.typeName() + "`");
                }
                checkConstruction(nd.typeName(), nd.inits(), nd.spreads(), nd.pos(),
                        fieldTypes(owner, symbols), env, data, symbols, reqs);
                yield Type.ref(nd.typeName());
            }
            case Ast.Match m -> typeOfMatch(m, env, data, symbols, reqs);
            case Ast.If iff -> {
                requireType(iff.cond(), Type.BOOL, env, data, symbols, reqs, "if condition");
                Type tt = typeOf(iff.then(), env, data, symbols, reqs);
                Type et = typeOf(iff.els(), env, data, symbols, reqs);
                if (tt.equals(et)) {
                    yield tt;
                }
                if (isDataLike(tt) && isDataLike(et)) {
                    Set<String> names = new HashSet<>(namesOf(tt));
                    names.addAll(namesOf(et));
                    yield Type.union(names);
                }
                throw new CompileException(iff.pos(), "if branches disagree: " + tt + " vs " + et);
            }
        };
    }

    private static Type typeOfMatch(Ast.Match m, Map<String, Type> env, Ast.Data data,
                                    Map<String, Ast.Def> symbols, Map<String, ReqSig> reqs) {
        Type st = typeOf(m.scrutinee(), env, data, symbols, reqs);
        if (st instanceof Type.OptionOf oo) {
            return typeOfOptionMatch(m, oo.element(), env, data, symbols, reqs);
        }
        if (st instanceof Type.Union union) {
            return typeOfArmsMatch(m, union.members(), "union " + union, env, data, symbols, reqs);
        }
        if (!(st instanceof Type.Ref ref) || !(symbols.get(ref.name()) instanceof Ast.SumData sum)) {
            throw new CompileException(m.pos(), "match requires a sum-typed value, got " + st);
        }
        return typeOfArmsMatch(m, new HashSet<>(sum.arms()), "data `" + sum.name() + "`",
                env, data, symbols, reqs);
    }

    /** Match over a fixed set of data arms (a named sum's arms, or an anonymous union's members).
     * Each arm binds its own data type; every arm must be covered (E1201). */
    private static Type typeOfArmsMatch(Ast.Match m, Set<String> arms, String what, Map<String, Type> env,
                                        Ast.Data data, Map<String, Ast.Def> symbols, Map<String, ReqSig> reqs) {
        Set<String> covered = new HashSet<>();
        Type branchType = null;
        for (Ast.Case c : m.cases()) {
            if (!arms.contains(c.armType())) {
                throw new CompileException(c.pos(), "`" + c.armType() + "` is not an arm of " + what);
            }
            covered.add(c.armType());
            branchType = mergeBranch(m, branchType,
                    typeOf(c.body(), bound(env, c.binding(), Type.ref(c.armType())), data, symbols, reqs), c);
        }
        for (String arm : arms) {
            if (!covered.contains(arm)) {
                throw new CompileException(m.pos(), "E1201",
                        "Non-exhaustive match for " + what + ". Missing case: " + arm);
            }
        }
        if (branchType == null) {
            throw new CompileException(m.pos(), "match has no cases");
        }
        return branchType;
    }

    /** Match over {@code Option<element>}: arms are {@code Some} (binds the element) and
     * {@code None}; both must be present (spec 16.3). */
    private static Type typeOfOptionMatch(Ast.Match m, Type element, Map<String, Type> env, Ast.Data data,
                                          Map<String, Ast.Def> symbols, Map<String, ReqSig> reqs) {
        Set<String> covered = new HashSet<>();
        Type branchType = null;
        for (Ast.Case c : m.cases()) {
            Type bind = switch (c.armType()) {
                case "Some" -> element;
                case "None" -> null;
                default -> throw new CompileException(c.pos(),
                        "`" + c.armType() + "` is not an arm of Option; use Some or None");
            };
            covered.add(c.armType());
            branchType = mergeBranch(m, branchType,
                    typeOf(c.body(), bound(env, c.binding(), bind), data, symbols, reqs), c);
        }
        for (String arm : List.of("Some", "None")) {
            if (!covered.contains(arm)) {
                throw new CompileException(m.pos(), "E1201",
                        "Non-exhaustive match for Option. Missing case: " + arm);
            }
        }
        return branchType;
    }

    /** Extends {@code env} with {@code name -> type} when both are present; otherwise returns it as is. */
    private static Map<String, Type> bound(Map<String, Type> env, String name, Type type) {
        if (name == null || type == null) {
            return env;
        }
        Map<String, Type> benv = new HashMap<>(env);
        benv.put(name, type);
        return benv;
    }

    private static Type mergeBranch(Ast.Match m, Type branchType, Type bt, Ast.Case c) {
        if (branchType == null) {
            return bt;
        }
        if (!branchType.equals(bt)) {
            throw new CompileException(c.pos(),
                    "match branches disagree: " + branchType + " vs " + bt);
        }
        return branchType;
    }


    private static Type typeOfFieldAccess(Ast.FieldAccess fa, Map<String, Type> env, Ast.Data data,
                                          Map<String, Ast.Def> symbols, Map<String, ReqSig> reqs) {
        Type target = typeOf(fa.target(), env, data, symbols, reqs);
        if (target instanceof Type.Ref ref && symbols.get(ref.name()) instanceof Ast.Data owner) {
            Type ft = fieldTypes(owner, symbols).get(fa.field());
            if (ft != null) {
                return ft;
            }
        }
        throw new CompileException(fa.pos(), "cannot access field `" + fa.field() + "` on this value");
    }

    private static Type typeOfCall(Ast.Call call, Map<String, Type> env, Ast.Data data,
                                   Map<String, Ast.Def> symbols, Map<String, ReqSig> reqs) {
        List<Ast.Expr> args = call.args();
        return switch (call.fn()) {
            case "length" -> {
                arity(call, 1);
                requireType(args.get(0), Type.STRING, env, data, symbols, reqs, "argument of length");
                yield Type.INT;
            }
            case "contains" -> {
                arity(call, 2);
                requireType(args.get(0), Type.STRING, env, data, symbols, reqs, "argument 1 of contains");
                requireType(args.get(1), Type.STRING, env, data, symbols, reqs, "argument 2 of contains");
                yield Type.BOOL;
            }
            case "trim" -> {
                arity(call, 1);
                requireType(args.get(0), Type.STRING, env, data, symbols, reqs, "argument of trim");
                yield Type.STRING;
            }
            case "lowercase", "uppercase" -> {
                arity(call, 1);
                requireType(args.get(0), Type.STRING, env, data, symbols, reqs, "argument of " + call.fn());
                yield Type.STRING;
            }
            case "concat" -> {
                arity(call, 2);
                requireType(args.get(0), Type.STRING, env, data, symbols, reqs, "argument 1 of concat");
                requireType(args.get(1), Type.STRING, env, data, symbols, reqs, "argument 2 of concat");
                yield Type.STRING;
            }
            case "startsWith", "endsWith" -> {
                arity(call, 2);
                requireType(args.get(0), Type.STRING, env, data, symbols, reqs, "argument 1 of " + call.fn());
                requireType(args.get(1), Type.STRING, env, data, symbols, reqs, "argument 2 of " + call.fn());
                yield Type.BOOL;
            }
            case "substring" -> {
                arity(call, 3);
                requireType(args.get(0), Type.STRING, env, data, symbols, reqs, "argument 1 of substring");
                requireType(args.get(1), Type.INT, env, data, symbols, reqs, "argument 2 of substring");
                requireType(args.get(2), Type.INT, env, data, symbols, reqs, "argument 3 of substring");
                yield Type.STRING;
            }
            case "size" -> {
                arity(call, 1);
                if (!(typeOf(args.get(0), env, data, symbols, reqs) instanceof Type.ListOf)) {
                    throw new CompileException(call.pos(), "size expects a List");
                }
                yield Type.INT;
            }
            case "get" -> {
                arity(call, 2);
                Type first = typeOf(args.get(0), env, data, symbols, reqs);
                if (first instanceof Type.ListOf lo) {
                    requireType(args.get(1), Type.INT, env, data, symbols, reqs, "index of get");
                    yield Type.option(lo.element());
                }
                if (first instanceof Type.MapOf mo) {
                    requireType(args.get(1), Type.STRING, env, data, symbols, reqs, "key of get");
                    yield Type.option(mo.value());
                }
                throw new CompileException(call.pos(), "get expects a List or Map, got " + first);
            }
            case "containsKey" -> {
                arity(call, 2);
                if (!(typeOf(args.get(0), env, data, symbols, reqs) instanceof Type.MapOf)) {
                    throw new CompileException(call.pos(), "containsKey expects a Map");
                }
                requireType(args.get(1), Type.STRING, env, data, symbols, reqs, "key of containsKey");
                yield Type.BOOL;
            }
            case "keys" -> {
                arity(call, 1);
                if (!(typeOf(args.get(0), env, data, symbols, reqs) instanceof Type.MapOf)) {
                    throw new CompileException(call.pos(), "keys expects a Map");
                }
                yield Type.list(Type.STRING);
            }
            case "values" -> {
                arity(call, 1);
                if (!(typeOf(args.get(0), env, data, symbols, reqs) instanceof Type.MapOf mo)) {
                    throw new CompileException(call.pos(), "values expects a Map");
                }
                yield Type.list(mo.value());
            }
            case "add", "subtract", "multiply" -> numericOp(call, env, data, symbols, reqs, false);
            case "compare" -> numericOp(call, env, data, symbols, reqs, true);
            default -> {
                // a required behavior called inline (spec 12.2, 13): type it as its success arm
                ReqSig callee = reqs.get(call.fn());
                if (callee == null) {
                    throw new CompileException(call.pos(), "unknown function `" + call.fn() + "`");
                }
                arity(call, 1);
                requireType(args.get(0), callee.param(), env, data, symbols, reqs,
                        "argument of " + call.fn());
                yield callee.success();
            }
        };
    }

    private static Type typeOfBinary(Ast.Binary bin, Map<String, Type> env, Ast.Data data,
                                     Map<String, Ast.Def> symbols, Map<String, ReqSig> reqs) {
        return switch (bin.op()) {
            case AND, OR -> {
                requireType(bin.left(), Type.BOOL, env, data, symbols, reqs, "operand of logical operator");
                requireType(bin.right(), Type.BOOL, env, data, symbols, reqs, "operand of logical operator");
                yield Type.BOOL;
            }
            case LT, LE, GT, GE -> {
                requireType(bin.left(), Type.INT, env, data, symbols, reqs, "operand of comparison");
                requireType(bin.right(), Type.INT, env, data, symbols, reqs, "operand of comparison");
                yield Type.BOOL;
            }
            case ADD, SUB, MUL -> {
                requireType(bin.left(), Type.INT, env, data, symbols, reqs, "operand of arithmetic");
                requireType(bin.right(), Type.INT, env, data, symbols, reqs, "operand of arithmetic");
                yield Type.INT;
            }
            case EQ, NE -> {
                Type lt = typeOf(bin.left(), env, data, symbols, reqs);
                Type rt = typeOf(bin.right(), env, data, symbols, reqs);
                if (!lt.equals(rt) || lt instanceof Type.Ref) {
                    throw new CompileException(bin.pos(), "cannot compare " + lt + " with " + rt);
                }
                yield Type.BOOL;
            }
        };
    }

    /** A binary numeric op over two Int or two Decimal operands (spec 18.2, 18.3). {@code compare}
     * yields Int (-1/0/1); the arithmetic ops yield the operand type. */
    private static Type numericOp(Ast.Call call, Map<String, Type> env, Ast.Data data,
                                  Map<String, Ast.Def> symbols, Map<String, ReqSig> reqs, boolean compare) {
        arity(call, 2);
        Type lt = typeOf(call.args().get(0), env, data, symbols, reqs);
        if (lt != Type.INT && lt != Type.DECIMAL) {
            throw new CompileException(call.pos(), call.fn() + " expects Int or Decimal, got " + lt);
        }
        requireType(call.args().get(1), lt, env, data, symbols, reqs, "argument 2 of " + call.fn());
        return compare ? Type.INT : lt;
    }

    private static void arity(Ast.Call call, int n) {
        if (call.args().size() != n) {
            throw new CompileException(call.pos(),
                    call.fn() + " expects " + n + " argument(s), got " + call.args().size());
        }
    }

    private static void requireType(Ast.Expr e, Type expected, Map<String, Type> env, Ast.Data data,
                                    Map<String, Ast.Def> symbols, Map<String, ReqSig> reqs, String what) {
        Type actual = typeOf(e, env, data, symbols, reqs);
        if (!actual.equals(expected)) {
            throw new CompileException(e.pos(), what + " must be " + expected + " but is " + actual);
        }
    }

    /** The output type of a behavior return: a single arm, or a union of two or more arms. */
    public static Type successType(Ast.RetType ret, Map<String, Ast.Def> symbols) {
        List<Type> members = new ArrayList<>();
        for (Ast.TypeRef t : ret.arms()) {
            members.add(resolveType(t, symbols));
        }
        if (members.size() == 1) {
            return members.get(0);
        }
        Set<String> names = new HashSet<>();
        for (Type m : members) {
            if (!(m instanceof Type.Ref r)) {
                throw new CompileException(ret.pos(), "union members must be data types");
            }
            names.add(r.name());
        }
        return Type.union(names);
    }

    /** Builds a Ref (one name) or Union (two or more) from a set of arm names. */
    static Type armSetType(Set<String> names) {
        if (names.size() == 1) {
            return Type.ref(names.iterator().next());
        }
        return Type.union(names);
    }

    public static boolean isDataLike(Type t) {
        return t instanceof Type.Ref || t instanceof Type.Union;
    }

    public static Set<String> namesOf(Type t) {
        if (t instanceof Type.Ref r) {
            return Set.of(r.name());
        }
        if (t instanceof Type.Union u) {
            return u.members();
        }
        return Set.of();
    }

    /** Arm names of a stage output, treating a {@code Raw} encoder output as the arm {@code "Raw"}
     * so it can be unioned with propagated error arms (spec 14.1, 24). */
    private static Set<String> armNamesOf(Type t) {
        if (t == Type.RAW) {
            return Set.of("Raw");
        }
        return namesOf(t);
    }

    /** True when a value of {@code sub} is acceptable where {@code sup} is expected. */
    public static boolean subtypeOf(Type sub, Type sup) {
        if (sub.equals(sup)) {
            return true;
        }
        return sup instanceof Type.Union u && u.members().containsAll(namesOf(sub));
    }

    public static Type resolveType(Ast.TypeRef ref, Map<String, Ast.Def> symbols) {
        return switch (ref.name()) {
            case "Int" -> Type.INT;
            case "String" -> Type.STRING;
            case "Bool" -> Type.BOOL;
            case "Decimal" -> Type.DECIMAL;
            case "Date" -> Type.DATE;
            case "DateTime" -> Type.DATETIME;
            case "制約違反" -> Type.ref("制約違反");   // built-in constraint-violation arm (spec 9.4)
            case "復号失敗" -> Type.ref("復号失敗");   // built-in decode-failure arm (spec 10.5)
            case "List" -> {
                if (ref.arg() == null) {
                    throw new CompileException(ref.pos(), "List needs a type argument, e.g. List<Int>");
                }
                yield Type.list(resolveType(ref.arg(), symbols));
            }
            case "Option" -> {
                if (ref.arg() == null) {
                    throw new CompileException(ref.pos(), "Option needs a type argument");
                }
                yield Type.option(resolveType(ref.arg(), symbols));
            }
            case "Map" -> {
                if (ref.arg() == null) {
                    throw new CompileException(ref.pos(), "Map needs a value type, e.g. Map<String, Int>");
                }
                yield Type.map(resolveType(ref.arg(), symbols));
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
