package net.unit8.souther.compiler.check;

import net.unit8.souther.compiler.ast.Ast;
import net.unit8.souther.compiler.check.NumericDomain.LinearForm;
import net.unit8.souther.compiler.check.NumericDomain.Rel;
import net.unit8.souther.compiler.diag.CompileException;
import net.unit8.souther.compiler.diag.Diagnostic;
import net.unit8.souther.compiler.diag.SourcePos;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The intraprocedural invariant-discharge check (spec §invariant-discharge). It walks a behavior's
 * body threading a {@link NumericDomain} — seeded from the input newtypes' invariants and refined
 * along each {@code require}/{@code if} guard (a {@code require} is already an {@code if} here) — and,
 * at every construction whose invariant is expressible in the domain, asks whether the guards
 * <em>discharge</em> it. A construction the domain proves must violate its invariant on a reachable
 * path is a compile error (the path-sensitive generalization of the constant check {@code 金額(-5)});
 * one it cannot prove is a warning (a possible abort — guard it, reify the relation into a type
 * invariant, or {@code unchecked} it). An invariant it cannot express is left opaque (no diagnostic;
 * the run-time check stays), so every flagged construction has a guard the domain can verify.
 *
 * <p>The walk mirrors {@link TotalityChecker}: a {@code switch} over {@code Ast.Expr} threading an
 * immutable environment. It is fail-open — any internal error is swallowed so an analysis bug can
 * never reject a valid program.
 */
final class InvariantChecker {

    record Findings(List<CompileException> errors, List<Diagnostic> warnings) {}

    private final Map<String, Ast.Def> symbols;
    private final List<CompileException> errors = new ArrayList<>();
    private final List<Diagnostic> warnings = new ArrayList<>();

    private InvariantChecker(Map<String, Ast.Def> symbols) {
        this.symbols = symbols;
    }

    /** Analyzes one behavior body against its input types. Never throws. */
    static Findings analyze(Ast.Expr body, Map<String, Type> params, Map<String, Ast.Def> symbols) {
        InvariantChecker c = new InvariantChecker(symbols);
        try {
            NumericDomain d = NumericDomain.top();
            for (Map.Entry<String, Type> p : params.entrySet()) {
                d = c.seedParam(p.getKey(), p.getValue(), d);
            }
            c.walk(body, d, new HashMap<>(params));
        } catch (RuntimeException swallowed) {
            // fail-open: the run-time invariant check remains the backstop
        }
        return new Findings(c.errors, c.warnings);
    }

    // --- the walk ------------------------------------------------------------------------------

    private void walk(Ast.Expr e, NumericDomain d, Map<String, Type> types) {
        checkIfConstruction(e, d, types);
        switch (e) {
            case Ast.If iff -> {
                walk(iff.cond(), d, types);
                walk(iff.then(), assumeCond(iff.cond(), d, types, true), types);
                walk(iff.els(), assumeCond(iff.cond(), d, types, false), types);
            }
            case Ast.LetIn li -> {
                walk(li.value(), d, types);
                Map<String, Type> t2 = new HashMap<>(types);
                Type vt = typeExpr(li.value(), types);
                if (vt != null) {
                    t2.put(li.name(), vt);
                }
                NumericDomain d2 = d;
                LinearForm vf = affineOf(li.value(), types);
                if (isNumeric(vt) && vf != null) {
                    d2 = d.assign(li.name(), vf);
                }
                walk(li.body(), d2, t2);
            }
            case Ast.Match m -> {
                walk(m.scrutinee(), d, types);
                for (Ast.Case c : m.cases()) {
                    Map<String, Type> t2 = new HashMap<>(types);
                    if (c.binding() != null && c.caseTypes().size() == 1) {
                        t2.put(c.binding(), Type.ref(c.caseTypes().get(0)));
                    }
                    walk(c.body(), d, t2);
                }
            }
            case Ast.Block b -> walk(b.body(), d, types);
            default -> forEachChild(e, child -> walk(child, d, types));
        }
    }

    // --- construction detection & discharge check ----------------------------------------------

    private void checkIfConstruction(Ast.Expr e, NumericDomain d, Map<String, Type> types) {
        switch (e) {
            case Ast.NewData nd when nd.spreads().isEmpty() -> {
                if (symbols.get(nd.typeName()) instanceof Ast.Data type) {
                    Map<String, LinearForm> fields = new HashMap<>();
                    for (Ast.FieldInit fi : nd.inits()) {
                        LinearForm f = affineOf(fi.value(), types);
                        if (f != null) {
                            fields.put(fi.name(), f);
                        }
                    }
                    check(type, fields::get, d, nd.pos());
                }
            }
            case Ast.Binary bin when isArith(bin.op()) -> {
                Type rt = arithType(bin, types);
                if (rt instanceof Type.Ref r && symbols.get(r.name()) instanceof Ast.Data type
                        && type.newtype()) {
                    LinearForm value = affineOf(bin, types);
                    if (value != null) {
                        check(type, name -> "value".equals(name) ? value : null, d, bin.pos());
                    }
                }
            }
            default -> { }
        }
    }

    /** Runs the discharge check for a construction of {@code type} whose field values resolve through
     * {@code resolve}. A definite violation is an error; an unproven one a warning; a fully-discharged
     * or non-expressible invariant is silent. */
    private void check(Ast.Data type, Function<String, LinearForm> resolve, NumericDomain d, SourcePos pos) {
        List<Ast.Expr> invs = TypeChecker.effectiveInvariants(type, symbols);
        if (invs.isEmpty()) {
            return;
        }
        List<Constraint> constraints = new ArrayList<>();
        for (Ast.Expr inv : invs) {
            List<Constraint> cs = invConstraints(inv, resolve);
            if (cs == null) {
                return;   // some part is not expressible in the domain — leave the whole opaque
            }
            constraints.addAll(cs);
        }
        boolean possible = false;
        for (Constraint c : constraints) {
            if (d.refutes(c.form(), c.rel())) {
                errors.add(CompileException.of(
                        Diagnostic.of("E2010", "check.invariant.violation").title("check.invariant.title")
                                .at(pos).args(type.name()).build(),
                        "constructing `" + type.name() + "` here violates its invariant on a reachable path"));
                return;
            }
            if (!d.entails(c.form(), c.rel())) {
                possible = true;
            }
        }
        if (possible) {
            warnings.add(
                    Diagnostic.of("E2011", "check.invariant.unproven").title("check.invariant.title")
                            .at(pos).args(type.name()).hint("check.invariant.reify", type.name())
                            .warning().build());
        }
    }

    // --- invariant / condition -> constraints --------------------------------------------------

    private record Constraint(LinearForm form, Rel rel) {}

    /** The constraints an invariant expression contributes under {@code resolve} (field/{@code value}
     * name -> its affine form), or {@code null} if any part is not expressible. */
    private List<Constraint> invConstraints(Ast.Expr inv, Function<String, LinearForm> resolve) {
        if (inv instanceof Ast.Binary b && b.op() == Ast.BinOp.AND) {
            List<Constraint> l = invConstraints(b.left(), resolve);
            List<Constraint> r = invConstraints(b.right(), resolve);
            if (l == null || r == null) {
                return null;
            }
            List<Constraint> both = new ArrayList<>(l);
            both.addAll(r);
            return both;
        }
        if (inv instanceof Ast.Binary b && relOf(b.op()) != null) {
            LinearForm la = affineResolved(b.left(), resolve);
            LinearForm ra = affineResolved(b.right(), resolve);
            if (la == null || ra == null) {
                return null;
            }
            return List.of(new Constraint(la.minus(ra), relOf(b.op())));
        }
        return null;
    }

    /** The affine form of an invariant sub-expression, resolving a bare name through {@code resolve}
     * (its field/{@code value}). Literals and {@code +}/{@code -} compose; anything else is {@code null}. */
    private LinearForm affineResolved(Ast.Expr e, Function<String, LinearForm> resolve) {
        return switch (e) {
            case Ast.Var v -> resolve.apply(v.name());
            case Ast.IntLit i -> LinearForm.constant(BigDecimal.valueOf(i.value()));
            case Ast.DecimalLit dd -> LinearForm.constant(dd.value());
            case Ast.Neg n -> negate(affineResolved(n.operand(), resolve));
            case Ast.Binary b when b.op() == Ast.BinOp.ADD -> add(
                    affineResolved(b.left(), resolve), affineResolved(b.right(), resolve), false);
            case Ast.Binary b when b.op() == Ast.BinOp.SUB -> add(
                    affineResolved(b.left(), resolve), affineResolved(b.right(), resolve), true);
            default -> null;
        };
    }

    /** Refines {@code d} by asserting {@code cond} (or its negation). Non-comparison conditions and
     * non-affine operands leave the domain unchanged (sound). */
    private NumericDomain assumeCond(Ast.Expr cond, NumericDomain d, Map<String, Type> types, boolean positive) {
        if (cond instanceof Ast.Binary b && b.op() == Ast.BinOp.AND && positive) {
            return assumeCond(b.right(), assumeCond(b.left(), d, types, true), types, true);
        }
        if (cond instanceof Ast.Binary b && relOf(b.op()) != null) {
            LinearForm la = affineOf(b.left(), types);
            LinearForm ra = affineOf(b.right(), types);
            if (la == null || ra == null) {
                return d;
            }
            Rel rel = positive ? relOf(b.op()) : negateRel(relOf(b.op()));
            if (rel == null) {
                return d;
            }
            return d.assume(la.minus(ra), rel);
        }
        return d;
    }

    // --- seeding -------------------------------------------------------------------------------

    /** Seeds the domain with what a parameter's type guarantees: a numeric newtype's own invariant on
     * its value, or a product data's invariant over its fields (and one level of numeric-newtype
     * fields), each substituted onto the parameter's atom(s). Sound by closed construction — an input
     * of type T was built through T's checked constructor. */
    private NumericDomain seedParam(String name, Type t, NumericDomain d) {
        return seedAt(name, t, d, 0);
    }

    private NumericDomain seedAt(String path, Type t, NumericDomain d, int depth) {
        if (depth > 2 || !(t instanceof Type.Ref ref) || !(symbols.get(ref.name()) instanceof Ast.Data data)) {
            return d;
        }
        List<Ast.Expr> invs = TypeChecker.effectiveInvariants(data, symbols);
        Map<String, Type> fields = TypeChecker.fieldTypes(data, symbols);
        Function<String, LinearForm> resolve = fieldName -> {
            if (data.newtype() && fieldName.equals("value")) {
                return LinearForm.atom(path);
            }
            return fields.containsKey(fieldName) ? LinearForm.atom(path + "." + fieldName) : null;
        };
        NumericDomain out = d;
        for (Ast.Expr inv : invs) {
            List<Constraint> cs = invConstraints(inv, resolve);
            if (cs != null) {
                for (Constraint c : cs) {
                    out = out.assume(c.form(), c.rel());
                }
            }
        }
        if (!data.newtype()) {
            for (Map.Entry<String, Type> f : fields.entrySet()) {
                out = seedAt(path + "." + f.getKey(), f.getValue(), out, depth + 1);
            }
        }
        return out;
    }

    // --- affine of a body expression -----------------------------------------------------------

    private LinearForm affineOf(Ast.Expr e, Map<String, Type> types) {
        return switch (e) {
            case Ast.IntLit i -> LinearForm.constant(BigDecimal.valueOf(i.value()));
            case Ast.DecimalLit dd -> LinearForm.constant(dd.value());
            case Ast.Neg n -> negate(affineOf(n.operand(), types));
            case Ast.Binary b when b.op() == Ast.BinOp.ADD -> add(
                    affineOf(b.left(), types), affineOf(b.right(), types), false);
            case Ast.Binary b when b.op() == Ast.BinOp.SUB -> add(
                    affineOf(b.left(), types), affineOf(b.right(), types), true);
            case Ast.NewData nd when nd.spreads().isEmpty() && nd.inits().size() == 1
                    && nd.inits().get(0).name().equals("value")
                    && isNumericNewtype(Type.ref(nd.typeName())) ->
                    affineOf(nd.inits().get(0).value(), types);
            default -> {
                String atom = atomOf(e, types);
                yield atom == null ? null : LinearForm.atom(atom);
            }
        };
    }

    private static LinearForm negate(LinearForm f) {
        return f == null ? null : f.negate();
    }

    private static LinearForm add(LinearForm a, LinearForm b, boolean subtract) {
        if (a == null || b == null) {
            return null;
        }
        return subtract ? a.minus(b) : a.plus(b);
    }

    /** The canonical atom key of a numeric location ({@code x}, {@code p.a}, a newtype's value), or
     * {@code null} if {@code e} is not one. */
    private String atomOf(Ast.Expr e, Map<String, Type> types) {
        Type t = typeExpr(e, types);
        if (!isNumeric(t)) {
            return null;
        }
        return pathKey(e, types);
    }

    private String pathKey(Ast.Expr e, Map<String, Type> types) {
        return switch (e) {
            case Ast.Var v -> v.name();
            case Ast.FieldAccess fa -> {
                if (fa.field().equals("value") && isNumericNewtype(typeExpr(fa.target(), types))) {
                    yield pathKey(fa.target(), types);   // a newtype's .value is the same atom
                }
                String base = pathKey(fa.target(), types);
                yield base == null ? null : base + "." + fa.field();
            }
            default -> null;
        };
    }

    // --- a minimal local typer (enough for atom/affine detection) ------------------------------

    private Type typeExpr(Ast.Expr e, Map<String, Type> types) {
        return switch (e) {
            case Ast.IntLit ignored -> Type.INT;
            case Ast.DecimalLit ignored -> Type.DECIMAL;
            case Ast.Var v -> types.get(v.name());
            case Ast.FieldAccess fa -> {
                Type owner = typeExpr(fa.target(), types);
                yield owner instanceof Type.Ref r && symbols.get(r.name()) instanceof Ast.Data d
                        ? TypeChecker.fieldTypes(d, symbols).get(fa.field()) : null;
            }
            case Ast.NewData nd -> Type.ref(nd.typeName());
            case Ast.Neg n -> typeExpr(n.operand(), types);
            case Ast.Binary b when isArith(b.op()) -> arithType(b, types);
            default -> null;
        };
    }

    /** The result type of an arithmetic binary: the newtype for closed {@code +}/{@code -}, else the
     * numeric base, else {@code null}. */
    private Type arithType(Ast.Binary b, Map<String, Type> types) {
        Type lt = typeExpr(b.left(), types);
        Type rt = typeExpr(b.right(), types);
        if (b.op() == Ast.BinOp.ADD || b.op() == Ast.BinOp.SUB) {
            if (isNumericNewtype(lt)) {
                return lt;
            }
            if (isNumericNewtype(rt)) {
                return rt;
            }
        }
        if (lt == Type.INT || lt == Type.DECIMAL) {
            return lt;
        }
        if (rt == Type.INT || rt == Type.DECIMAL) {
            return rt;
        }
        return null;
    }

    private Type newtypeValueType(Type t) {
        if (t instanceof Type.Ref r && symbols.get(r.name()) instanceof Ast.Data d && d.newtype()) {
            return TypeChecker.fieldTypes(d, symbols).get("value");
        }
        return null;
    }

    private boolean isNumericNewtype(Type t) {
        Type v = newtypeValueType(t);
        return v == Type.INT || v == Type.DECIMAL;
    }

    private boolean isNumeric(Type t) {
        return t == Type.INT || t == Type.DECIMAL || isNumericNewtype(t);
    }

    // --- helpers -------------------------------------------------------------------------------

    private static boolean isArith(Ast.BinOp op) {
        return op == Ast.BinOp.ADD || op == Ast.BinOp.SUB || op == Ast.BinOp.MUL || op == Ast.BinOp.DIV;
    }

    private static Rel relOf(Ast.BinOp op) {
        return switch (op) {
            case GE -> Rel.GE;
            case GT -> Rel.GT;
            case LE -> Rel.LE;
            case LT -> Rel.LT;
            case EQ -> Rel.EQ;
            default -> null;
        };
    }

    private static Rel negateRel(Rel rel) {
        return switch (rel) {
            case GE -> Rel.LT;
            case GT -> Rel.LE;
            case LE -> Rel.GT;
            case LT -> Rel.GE;
            case EQ -> null;
        };
    }

    /** The direct-child visitor, mirroring {@link TotalityChecker}'s. */
    private static void forEachChild(Ast.Expr e, Consumer<Ast.Expr> f) {
        switch (e) {
            case Ast.NewData nd -> nd.inits().forEach(i -> f.accept(i.value()));
            case Ast.FieldAccess fa -> f.accept(fa.target());
            case Ast.Call call -> call.args().forEach(f);
            case Ast.Binary bin -> {
                f.accept(bin.left());
                f.accept(bin.right());
            }
            case Ast.Neg neg -> f.accept(neg.operand());
            case Ast.Match m -> {
                f.accept(m.scrutinee());
                m.cases().forEach(c -> f.accept(c.body()));
            }
            case Ast.If iff -> {
                f.accept(iff.cond());
                f.accept(iff.then());
                f.accept(iff.els());
            }
            case Ast.ListLit lit -> lit.elements().forEach(f);
            case Ast.Tuple tup -> tup.elements().forEach(f);
            case Ast.TupleGet tg -> f.accept(tg.tuple());
            case Ast.ListComp comp -> {
                f.accept(comp.element());
                comp.guards().forEach(f);
            }
            case Ast.LetIn li -> {
                f.accept(li.value());
                f.accept(li.body());
            }
            case Ast.Block block -> f.accept(block.body());
            default -> { }
        }
    }
}
