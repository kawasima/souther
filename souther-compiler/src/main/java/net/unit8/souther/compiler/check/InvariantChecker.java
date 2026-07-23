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
import java.util.function.Function;

/**
 * The intraprocedural invariant-discharge check (spec §invariant-discharge). It walks a behavior's body threading a
 * {@link NumericDomain} — seeded from the input newtypes' invariants and refined along each
 * {@code require}/{@code if} guard (a {@code require} is already an {@code if} here) — and, at every
 * construction whose invariant is expressible in the domain, asks whether the guards
 * <em>discharge</em> it. A construction the domain proves must violate its invariant on a reachable
 * path is a compile error (the path-sensitive generalization of the constant check {@code 金額(-5)});
 * one it cannot prove is a warning (a possible abort — guard it, or reify the relation into a type
 * invariant). An invariant it cannot express is left opaque (no diagnostic; the run-time check stays),
 * so every flagged construction has a guard the domain can verify.
 *
 * <p>The walk mirrors {@link TotalityChecker}: a {@code switch} over {@code Ast.Expr} threading an
 * immutable environment. It is fail-open — any internal error is swallowed so an analysis bug can
 * never reject a valid program.
 */
final class InvariantChecker {

    record Findings(List<CompileException> errors, List<Diagnostic> warnings) {}

    /** A stdlib list combinator whose closure (argument {@code closureArg}) is handed each element of
     * its list argument ({@code listArg}) as closure parameter {@code elementParam} — mirrors
     * {@link TotalityChecker}'s table, so a construction inside a {@code List.map}/{@code fold} closure
     * is analyzed with the element bound to the list's element type. */
    private record Combinator(int closureArg, int elementParam, int listArg) {}

    private static final Map<String, Combinator> COMBINATORS = Map.of(
            "List.fold", new Combinator(0, 1, 2),
            "List.foldFrom", new Combinator(0, 1, 2),
            "List.map", new Combinator(0, 0, 1),
            "List.filter", new Combinator(0, 0, 1),
            "List.all", new Combinator(0, 0, 1),
            "List.any", new Combinator(0, 0, 1),
            "List.find", new Combinator(0, 0, 1),
            "List.partition", new Combinator(0, 0, 1));

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
                LinearForm vf = affineOf(li.value(), types);
                NumericDomain d2 = isNumeric(vt) && vf != null ? d.assign(li.name(), vf) : d;
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
            case Ast.Call call -> walkCall(call, d, types);
            default -> Ast.forEachChild(e, child -> walk(child, d, types));
        }
    }

    /** Walks a call, binding a combinator closure's element parameter to the list's element type (and
     * seeding its invariant) so a construction inside the closure is analyzed rather than left opaque. */
    private void walkCall(Ast.Call call, NumericDomain d, Map<String, Type> types) {
        Combinator combo = COMBINATORS.get(call.fn());
        for (int i = 0; i < call.args().size(); i++) {
            Ast.Expr arg = call.args().get(i);
            if (combo != null && i == combo.closureArg() && arg instanceof Ast.Block step
                    && combo.elementParam() < step.params().size()
                    && combo.listArg() < call.args().size()) {
                Type elem = elementType(typeExpr(call.args().get(combo.listArg()), types));
                Map<String, Type> t2 = new HashMap<>(types);
                NumericDomain d2 = d;
                if (elem != null) {
                    String p = step.params().get(combo.elementParam());
                    t2.put(p, elem);
                    d2 = seedParam(p, elem, d);   // the element carries its type's invariant
                }
                walk(step.body(), d2, t2);
            } else {
                walk(arg, d, types);
            }
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
                if (typeExpr(bin, types) instanceof Type.Ref r
                        && symbols.get(r.name()) instanceof Ast.Data type && type.newtype()) {
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
        if (inv instanceof Ast.Binary b) {
            Rel rel = relOf(b.op());
            if (rel != null) {
                LinearForm la = affine(b.left(), resolveLeaf(resolve));
                LinearForm ra = affine(b.right(), resolveLeaf(resolve));
                return la == null || ra == null ? null : List.of(new Constraint(la.minus(ra), rel));
            }
        }
        return null;
    }

    /** Refines {@code d} by asserting {@code cond} (or its negation). Non-comparison conditions and
     * non-affine operands leave the domain unchanged (sound). */
    private NumericDomain assumeCond(Ast.Expr cond, NumericDomain d, Map<String, Type> types, boolean positive) {
        if (cond instanceof Ast.Binary b && b.op() == Ast.BinOp.AND && positive) {
            return assumeCond(b.right(), assumeCond(b.left(), d, types, true), types, true);
        }
        if (cond instanceof Ast.Binary b) {
            Rel rel = relOf(b.op());
            if (rel != null) {
                LinearForm la = affineOf(b.left(), types);
                LinearForm ra = affineOf(b.right(), types);
                Rel eff = positive ? rel : negateRel(rel);
                if (la != null && ra != null && eff != null) {
                    return d.assume(la.minus(ra), eff);
                }
            }
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
        Map<String, Type> fields = TypeChecker.fieldTypes(data, symbols);
        Function<String, LinearForm> resolve = fieldName -> {
            if (data.newtype() && fieldName.equals("value")) {
                return LinearForm.atom(path);
            }
            return fields.containsKey(fieldName) ? LinearForm.atom(path + "." + fieldName) : null;
        };
        NumericDomain out = d;
        for (Ast.Expr inv : TypeChecker.effectiveInvariants(data, symbols)) {
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

    // --- affine forms --------------------------------------------------------------------------

    /** The shared affine walk: literals and {@code +}/{@code -} compose; every other node is handed to
     * {@code leaf} (which decides whether it is an atom, a resolved field, or opaque). */
    private LinearForm affine(Ast.Expr e, Function<Ast.Expr, LinearForm> leaf) {
        return switch (e) {
            case Ast.IntLit i -> LinearForm.constant(BigDecimal.valueOf(i.value()));
            case Ast.DecimalLit dd -> LinearForm.constant(dd.value());
            case Ast.Neg n -> negate(affine(n.operand(), leaf));
            case Ast.Binary b when b.op() == Ast.BinOp.ADD ->
                    add(affine(b.left(), leaf), affine(b.right(), leaf), false);
            case Ast.Binary b when b.op() == Ast.BinOp.SUB ->
                    add(affine(b.left(), leaf), affine(b.right(), leaf), true);
            // scalar multiply by a constant (金額 * 2) is linear; `/` and a variable product are not
            // (a divide truncates for Int, and a variable factor is non-linear), so leave those opaque.
            case Ast.Binary b when b.op() == Ast.BinOp.MUL ->
                    scale(affine(b.left(), leaf), affine(b.right(), leaf));
            default -> leaf.apply(e);
        };
    }

    /** A linear form scaled by a constant, when one side is a bare constant (a scalar multiply); null
     * when neither side is constant (a non-linear product). */
    private static LinearForm scale(LinearForm a, LinearForm b) {
        if (a == null || b == null) {
            return null;
        }
        if (a.coefs().isEmpty()) {
            return b.times(a.constant());
        }
        return b.coefs().isEmpty() ? a.times(b.constant()) : null;
    }

    /** The affine form of a body expression: a numeric atom, a newtype construct's wrapped value, or
     * {@code null}. */
    private LinearForm affineOf(Ast.Expr e, Map<String, Type> types) {
        return affine(e, n -> {
            if (n instanceof Ast.NewData nd && nd.spreads().isEmpty() && nd.inits().size() == 1
                    && nd.inits().get(0).name().equals("value")
                    && numericNewtype(Type.ref(nd.typeName()))) {
                return affineOf(nd.inits().get(0).value(), types);
            }
            String atom = atomOf(n, types);
            return atom == null ? null : LinearForm.atom(atom);
        });
    }

    /** The leaf rule for an invariant expression: a bare name resolves to its field/{@code value}. */
    private static Function<Ast.Expr, LinearForm> resolveLeaf(Function<String, LinearForm> resolve) {
        return n -> n instanceof Ast.Var v ? resolve.apply(v.name()) : null;
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
        return isNumeric(typeExpr(e, types)) ? pathKey(e, types) : null;
    }

    private String pathKey(Ast.Expr e, Map<String, Type> types) {
        return switch (e) {
            case Ast.Var v -> v.name();
            case Ast.FieldAccess fa -> {
                if (fa.field().equals("value") && numericNewtype(typeExpr(fa.target(), types))) {
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

    /** The result type of an arithmetic binary: the newtype for closed {@code +}/{@code -}
     * (via the checker's shared rule), else the numeric base, else {@code null}. */
    private Type arithType(Ast.Binary b, Map<String, Type> types) {
        Type lt = typeExpr(b.left(), types);
        Type rt = typeExpr(b.right(), types);
        // Closed `+`/`-` and scalar `*`/`/` both yield the newtype (the checker has already validated
        // admissibility, so a newtype operand here means the result is that newtype).
        if (isArith(b.op())) {
            Type nt = TypeChecker.closedNewtypeArithResult(lt, rt, symbols);
            if (nt != null) {
                return nt;
            }
        }
        if (lt == Type.INT || lt == Type.DECIMAL) {
            return lt;
        }
        return rt == Type.INT || rt == Type.DECIMAL ? rt : null;
    }

    private static Type elementType(Type t) {
        return t instanceof Type.ListOf list ? list.element() : null;
    }

    private boolean numericNewtype(Type t) {
        return TypeChecker.directNumericNewtypeBase(t, symbols) != null;
    }

    private boolean isNumeric(Type t) {
        return t == Type.INT || t == Type.DECIMAL || numericNewtype(t);
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
}
