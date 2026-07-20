package net.unit8.souther.compiler.ast;

import net.unit8.souther.compiler.diag.SourcePos;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * The slice-2 abstract syntax: a module of product {@code data} definitions with one or
 * more fields, each with an optional {@code invariant}, {@code decoder} (a single-value
 * {@code from Text|Int} form or a multi-field {@code from Object} form), and
 * {@code encoder} (a single Raw value or a {@code Object { ... }} form).
 */
public interface Ast {

    /** The source position of this node. Every record below provides it. */
    SourcePos pos();

    /**
     * A whole source file: its public surface, imports, and definitions.
     *
     * <p>{@code exposedOutputs} maps an exposed composition behavior's name to the output signature
     * written in the {@code exposing} list ({@code exposing ( name : A | B )}, spec 14.5). An exposed
     * {@code >->} composition must have one, checked to match its inferred output (ADR-0024); other
     * exposed names carry no signature (their type is at the definition).
     */
    record Module(String name,
                  List<String> exposing,
                  Map<String, RetType> exposedOutputs,
                  List<Import> imports,
                  List<Def> defs,
                  List<BehaviorDef> behaviors,
                  List<FnDef> fns,
                  List<Example> examples,
                  String exampleFileTarget,
                  SourcePos pos) implements Ast {}

    /**
     * {@code example <target> | row ...} — compile-time-checked examples for a behavior or a pure
     * helper. Whether written inline in the module or in an attached {@code examples for}
     * file, examples end up on {@link Module#examples()} (the compiler merges an attached file into
     * its target module). {@code exampleFileTarget} on a {@link Module} is non-null exactly when the
     * module was parsed from an {@code examples for <module>} file: it names the target and marks the
     * module as an example-only contribution, not a module of its own.
     */
    record Example(String target, List<ExampleRow> rows, SourcePos pos) implements Ast {}

    /**
     * One example row: an optional business description, the input argument expressions, and the
     * expected result. A bare {@link Var} expected asserts only the result arm (the case); a
     * {@link NewData}, a {@link Call} (a newtype constructor), or a literal asserts the whole value.
     */
    record ExampleRow(String description, List<Expr> inputs, Expr expected, SourcePos pos) implements Ast {}

    /** {@code import <module> ( name, ... )} — an explicit, non-wildcard import (spec 4). */
    record Import(String module, List<String> names, SourcePos pos) implements Ast {}

    /**
     * A behavior definition — a specification, not an implementation (spec 12, 21.1). It is either
     * a {@link SpecBehavior} (an input/output signature, with the body left to a matching
     * {@link FnDef} or to Java injection) or a {@link PipeBehavior} (a {@code >->} composition, which
     * is itself the implementation).
     */
    sealed interface BehaviorDef extends Ast permits SpecBehavior, PipeBehavior {
        String name();
    }

    /**
     * {@code behavior name = (p1: T1, ...) -> R constructs A, B requires C, D} — a signature with
     * no body (spec 12.1). A same-named {@link FnDef} is its implementation (13.1); with none, and
     * not a pipeline, it is a Java-injected behavior (13.2).
     *
     * <p>{@code requires} lists the implementation-less behaviors the {@code fn} calls; they become
     * the {@code fn}'s trailing arguments and the injected fields of the generated class (12.6).
     */
    record SpecBehavior(String name,
                        List<Param> params,
                        RetType ret,
                        List<String> constructs,
                        List<String> requires,
                        SourcePos pos) implements BehaviorDef {}

    /** A behavior parameter. Its type may be an anonymous union of cases (spec 12.2). */
    record Param(String name, RetType type, SourcePos pos) implements Ast {}

    /**
     * {@code behavior name = f >-> g >-> ... [-> A | B]} — a composition (spec 14.1). {@code declaredOut}
     * is the optional trailing output declaration (14.5): null when absent (output is inferred), else
     * the declared cases, which must match the inferred output exactly (E1604).
     */
    record PipeBehavior(String name, List<String> stages, RetType declaredOut, SourcePos pos)
            implements BehaviorDef {}

    /**
     * {@code fn name (a1, ...) = body} — a behavior's implementation (spec 13.1). If a same-named
     * {@link SpecBehavior} exists, the parameter types come from it and {@code params} carry only
     * names ({@link FnParam#type()} is null); otherwise it is a helper {@code fn} that writes its
     * own parameter types.
     *
     * <p>{@code body} is a single expression. The surface forms {@code let} and {@code require} are
     * desugared by the parser into {@link LetIn} and {@link If} (spec 16.4), so every later stage
     * sees one expression tree and has exactly one place where a value can be constructed.
     */
    /**
     * A {@code let} definition. A behavior fn or a helper carries a {@code body} and no
     * {@code declaredReturn}/{@code intrinsicKey}. A core intrinsic (spec §primitives) instead
     * declares its return type and names a primitive: {@code let trim (s: String): String =
     * intrinsic "string.trim"} — its {@code body} is null, {@code declaredReturn} its result type,
     * and {@code intrinsicKey} the backend key. Intrinsics are written only in the {@code souther}
     * namespace.
     */
    record FnDef(String name, List<FnParam> params, RetType declaredReturn, String intrinsicKey,
                 Expr body, SourcePos pos) implements Ast {
        public boolean isIntrinsic() {
            return intrinsicKey != null;
        }
    }

    /** A {@code fn} parameter: a name, and a type only when the {@code fn} is a helper (spec 13.1).
     * A helper's parameter type may be a function type {@link FnType}; a behavior fn's parameter
     * carries no type ({@code type} is null). */
    record FnParam(String name, ParamType type, SourcePos pos) implements Ast {}

    /** The written type of a helper {@code fn} parameter: an ordinary type ({@link RetType}) or a
     * function type {@link FnType}. Function types appear only in {@code fn} parameter position
     * (spec §fn-declaration); nowhere else in the grammar accepts one. */
    sealed interface ParamType extends Ast permits RetType, FnType {}

    /** A function type {@code (A, ...) -> B} written on a helper {@code fn} parameter. The
     * parameters and result are ordinary types; a nested function type is not written. */
    record FnType(List<RetType> params, RetType result, SourcePos pos) implements ParamType {}

    /** A behavior return type: the output sum — one or more unmarked domain cases (spec 12.2). */
    record RetType(List<TypeRef> cases, SourcePos pos) implements ParamType {}

    /** A top-level data definition: product, sum, or unit. */
    sealed interface Def extends Ast permits Data, SumData, UnitData {
        String name();
    }

    /**
     * A product data definition: included data (flattened) plus its own fields.
     *
     * <p>{@code newtype} marks the explicit newtype form {@code data X = Y} (spec 8.7): a single
     * implicit field named {@code value} of type {@code Y}, encoded as bare {@code Y} instead of an
     * object. Everything else (construction {@code X { value: v }}, access {@code x.value},
     * invariant on {@code value}) is the same as a one-field product; only the external
     * representation differs.
     */
    record Data(String name,
                boolean newtype,
                List<String> includes,
                List<Field> fields,
                Optional<Expr> invariant,
                Optional<DecoderDef> decoder,
                Optional<EncoderDef> encoder,
                SourcePos pos) implements Def {}

    /** A sum data definition {@code data X = A | B | ...} with optional discriminate decoder/encoder. */
    record SumData(String name,
                   List<String> cases,
                   Optional<Discriminate> decoder,
                   Optional<SumEncoder> encoder,
                   SourcePos pos) implements Def {}

    /** {@code encoder discriminate on "key" { Case -> "tag" ... }} — the inverse of discriminate. */
    record SumEncoder(String key, List<EncVariant> variants, SourcePos pos) implements Ast {}

    record EncVariant(String caseType, String tag, SourcePos pos) implements Ast {}

    /** A unit data definition {@code data U} with no fields. */
    record UnitData(String name, SourcePos pos) implements Def {}

    /** {@code decoder from Object discriminate on "key" { "tag" -> Case.decoder ... }} */
    record Discriminate(String key, List<Variant> variants, SourcePos pos) implements Ast {}

    record Variant(String tag, String caseType, SourcePos pos) implements Ast {}

    /** A field: a role name and its type. */
    record Field(String name, TypeRef type, SourcePos pos) implements Ast {}

    /**
     * A named type reference, optionally with one type argument (e.g. {@code List<T>}). When
     * {@code name} is null and {@code tupleElems} is non-null the ref is a tuple type
     * {@code (A, B, ...)} (ADR-0036), written only in a helper/stdlib signature. A {@code Map<K, V>}
     * reuses {@code tupleElems} to carry its key type (a single element) while {@code name} is
     * {@code "Map"} and {@code arg} is the value type (ADR-0040).
     */
    record TypeRef(String name, TypeRef arg, List<TypeRef> tupleElems, SourcePos pos) implements Ast {
        /** An ordinary (non-tuple) reference. */
        public TypeRef(String name, TypeRef arg, SourcePos pos) {
            this(name, arg, null, pos);
        }

        /** A tuple type is the nameless form; a named ref that also carries {@code tupleElems}
         *  (a {@code Map} carrying its key) is not a tuple. */
        public boolean isTuple() {
            return name == null && tupleElems != null;
        }
    }

    /** The kind of primitive Raw a single-value decoder reads / an encoder writes. */
    enum RawKind { TEXT, INT, BOOL, DECIMAL, DATE, DATETIME }

    // --- decoders ---

    sealed interface DecoderDef extends Ast permits PrimDecoder, ObjectDecoder, NewtypeDecoder {}

    /** {@code decoder from Text|Int as <input> { <stmts> <construct> }} (single value). */
    record PrimDecoder(RawKind from,
                       String inputName,
                       List<DecStmt> stmts,
                       Construct result,
                       SourcePos pos) implements DecoderDef {}

    /** {@code decoder from Object { <binds> <construct> }} (multi-field, accumulating). */
    record ObjectDecoder(List<Bind> binds, Construct result, SourcePos pos) implements DecoderDef {}

    /**
     * A newtype {@code data X = Y} over a non-primitive {@code Y} (spec 8.7): the whole input is
     * decoded by {@code inner} (a reference to {@code Y}'s decoder), and the result wrapped in
     * {@code X}. {@code X}'s external representation is {@code Y}'s — an object or a discriminated
     * sum, not {@code {value: ...}}.
     */
    record NewtypeDecoder(DecRef inner, String inputName, Construct result, SourcePos pos)
            implements DecoderDef {}

    /** {@code name <- field("key", <decRef>)} inside an object decoder. */
    record Bind(String name, String key, DecRef ref, SourcePos pos) implements Ast {}

    /** The decoder referenced by a bind: a primitive, another data's {@code .decoder}, or a list. */
    sealed interface DecRef extends Ast
            permits PrimDecRef, DataDecRef, ListDecRef, SetDecRef, OptionDecRef, MapDecRef {}

    record PrimDecRef(PrimKind kind, SourcePos pos) implements DecRef {}

    record DataDecRef(String typeName, SourcePos pos) implements DecRef {}

    /** {@code list(<elementDecRef>)} */
    record ListDecRef(DecRef element, SourcePos pos) implements DecRef {}

    /** {@code set(<elementDecRef>)} — a list decoder deduplicated into a Set on decode (ADR-0009). */
    record SetDecRef(DecRef element, SourcePos pos) implements DecRef {}

    /** An optional field decoder: absent/null becomes {@code None}, present decodes {@code element}. */
    record OptionDecRef(DecRef element, SourcePos pos) implements DecRef {}

    /** A {@code Map<K, T>} decoder: each object value is decoded with {@code value}. {@code keyType}
     * is {@code null} for a plain {@code String} key, or the String-backed newtype the keys are
     * constructed into at the boundary. */
    record MapDecRef(DecRef value, String keyType, SourcePos pos) implements DecRef {}

    /** A primitive field decoder kind. */
    enum PrimKind { STRING, INT, BOOL, DECIMAL, DATE, DATETIME }

    /** A statement in a single-value decoder body. */
    sealed interface DecStmt extends Ast permits Let, Require {}

    record Let(String name, Expr value, SourcePos pos) implements DecStmt {}

    record Require(Expr cond, String errorCode, SourcePos pos) implements DecStmt {}

    /** A typed record literal {@code TypeName { ..src, field: expr, ... }} — a construction. */
    record Construct(String typeName, List<FieldInit> inits, List<String> spreads, SourcePos pos)
            implements Ast {}

    /** One {@code field: expr} (or shorthand {@code field}) inside a record literal. */
    record FieldInit(String name, Expr value, SourcePos pos) implements Ast {}

    // --- encoders ---

    record EncoderDef(String selfName, RawExpr result, SourcePos pos) implements Ast {}

    /** A Raw-building expression. */
    sealed interface RawExpr extends Ast
            permits TextRaw, IntRaw, BoolRaw, DecimalRaw, IsoTextRaw, ObjectRaw, EncodeRaw, ListEnc,
                    SetEnc, OptionRaw, MapEnc {}

    /** Encodes a {@code Map<K, T>} to a {@code Raw.Object}, each value via {@code elem}. {@code keyType}
     * is {@code null} for a plain {@code String} key, or the String-backed newtype whose keys are
     * rendered bare at the boundary. */
    record MapEnc(Expr source, EncElem elem, String keyType, SourcePos pos) implements RawExpr {}

    /** Encodes an optional field: {@code None} becomes {@code Raw.Null}, {@code Some(v)} encodes
     * {@code v} via {@code inner}, which reads the unwrapped value bound to {@code elemVar}. */
    record OptionRaw(Expr access, RawExpr inner, String elemVar, SourcePos pos) implements RawExpr {}

    record TextRaw(Expr arg, SourcePos pos) implements RawExpr {}

    record IntRaw(Expr arg, SourcePos pos) implements RawExpr {}

    record BoolRaw(Expr arg, SourcePos pos) implements RawExpr {}

    /** Encodes a {@code Decimal} field to a {@code Raw.Decimal}. */
    record DecimalRaw(Expr arg, SourcePos pos) implements RawExpr {}

    /** Encodes a {@code Date}/{@code DateTime} field to a {@code Raw.Text} via its ISO8601 form. */
    record IsoTextRaw(Expr arg, SourcePos pos) implements RawExpr {}

    record ObjectRaw(List<RawEntry> entries, SourcePos pos) implements RawExpr {}

    /** {@code TypeName.encode(expr)} — encode a nested data value to Raw. */
    record EncodeRaw(String typeName, Expr arg, SourcePos pos) implements RawExpr {}

    /** {@code list(expr, <elemEnc>)} — encode a {@code List<T>} to a Raw.List. */
    record ListEnc(Expr source, EncElem elem, SourcePos pos) implements RawExpr {}

    /** Encodes a {@code Set} as a JSON array: the set is listed, then each element encoded. */
    record SetEnc(Expr source, EncElem elem, SourcePos pos) implements RawExpr {}

    /** How to encode a list element: a primitive or another data's {@code .encode}. */
    sealed interface EncElem extends Ast permits PrimEnc, DataEnc {}

    record PrimEnc(PrimKind kind, SourcePos pos) implements EncElem {}

    record DataEnc(String typeName, SourcePos pos) implements EncElem {}

    record RawEntry(String key, RawExpr value, SourcePos pos) implements Ast {}

    // --- expressions ---

    sealed interface Expr extends Ast
            permits IntLit, DecimalLit, StringLit, BoolLit, Var, FieldAccess, Call, Binary, Neg,
                    NewData, Match, If, ListLit, ListComp, LetIn, Block, Tuple, TupleGet {}

    /**
     * {@code x -> expr}, or {@code (acc, x) -> expr} — a block (spec 12.5).
     *
     * <p>Second-class: it may only be an argument, never a value that is returned, stored in a
     * field, or bound by {@code let}. The parser only accepts one in an argument position, and
     * because it cannot escape, the backend inlines it rather than building a closure.
     */
    record Block(List<String> params, Expr body, SourcePos pos) implements Expr {}

    /**
     * {@code let name = value} followed by {@code body} — what a body's {@code let} desugars to
     * (spec 16.1). Nesting the rest of the body inside keeps {@code value} from being evaluated
     * when an enclosing {@code if} (a desugared {@code require}) takes the other branch.
     */
    record LetIn(String name, Expr value, Expr body, SourcePos pos) implements Expr {}

    /** A list literal {@code [e1, e2, ...]} (one or more elements of the same type). */
    record ListLit(List<Expr> elements, SourcePos pos) implements Expr {}

    /** A guard-only comprehension {@code [element | guard, ...]}: the element is included when
     * every guard holds, giving a 0-or-1 element list (spec 18.4, conditional accumulation). */
    record ListComp(Expr element, List<Expr> guards, SourcePos pos) implements Expr {}

    /** A tuple {@code (e1, e2, ...)} of two or more values (ADR-0036), an expression-level value
     * that never crosses the data/behavior boundary. Opened with a {@code let (x, y) = t} destructure. */
    record Tuple(List<Expr> elements, SourcePos pos) implements Expr {}

    /** Reads the {@code index}-th element of a tuple; what a {@code let (x, y) = t} destructure lowers
     * a field read to. Not written in source — the parser produces it from a tuple pattern. {@code arity}
     * is the pattern's name count, so the checker rejects a tuple of a different size (ADR-0036). */
    record TupleGet(Expr tuple, int index, int arity, SourcePos pos) implements Expr {}

    /** {@code if cond then a else b} — both branches must have the same type (spec 16.2). */
    record If(Expr cond, Expr then, Expr els, SourcePos pos) implements Expr {}

    /** {@code match scrutinee { case Case as x -> body ... }} over a sum type. */
    record Match(Expr scrutinee, List<Case> cases, SourcePos pos) implements Expr {}

    /**
     * One {@code match} case: {@code case A | B ... [as x] -> body} (spec 16.3). {@code caseTypes}
     * holds one case name, or several joined by {@code |} (an or-pattern, spec 16.3). With one case,
     * {@code x} binds that case's type; with several, it binds the scrutinee's sum type, since no
     * single case type fits all alternatives.
     */
    record Case(List<String> caseTypes, String binding, Expr body, SourcePos pos) implements Ast {}

    /** {@code TypeName { ..src, field: expr, ... }} used as an expression (construction in a behavior). */
    record NewData(String typeName, List<FieldInit> inits, List<String> spreads, SourcePos pos)
            implements Expr {}

    record IntLit(long value, SourcePos pos) implements Expr {}

    record DecimalLit(java.math.BigDecimal value, SourcePos pos) implements Expr {}

    /** Unary minus {@code -operand} on an Int or Decimal (spec 18.1). */
    record Neg(Expr operand, SourcePos pos) implements Expr {}

    record StringLit(String value, SourcePos pos) implements Expr {}

    record BoolLit(boolean value, SourcePos pos) implements Expr {}

    record Var(String name, SourcePos pos) implements Expr {}

    record FieldAccess(Expr target, String field, SourcePos pos) implements Expr {}

    record Call(String fn, List<Expr> args, SourcePos pos) implements Expr {}

    record Binary(BinOp op, Expr left, Expr right, SourcePos pos) implements Expr {}


    enum BinOp { EQ, NE, LT, LE, GT, GE, AND, OR, ADD, SUB, MUL, DIV, CONCAT }

    /**
     * Rebuilds {@code e} with each direct child expression replaced by {@code f} applied to it; a
     * leaf (a literal or a variable) is returned unchanged. The single authoritative walk over the
     * expression tree, so an AST-to-AST pass (a Lower desugar, an optimization) writes only the
     * cases it rewrites and delegates the rest here, instead of hand-copying every node type.
     */
    static Expr mapChildren(Expr e, UnaryOperator<Expr> f) {
        return switch (e) {
            case IntLit x -> x;
            case DecimalLit x -> x;
            case StringLit x -> x;
            case BoolLit x -> x;
            case Var x -> x;
            case Neg n -> new Neg(f.apply(n.operand()), n.pos());
            case FieldAccess fa -> new FieldAccess(f.apply(fa.target()), fa.field(), fa.pos());
            case Binary b -> new Binary(b.op(), f.apply(b.left()), f.apply(b.right()), b.pos());
            case Call c -> new Call(c.fn(), mapExprs(c.args(), f), c.pos());
            case If iff -> new If(f.apply(iff.cond()), f.apply(iff.then()), f.apply(iff.els()), iff.pos());
            case LetIn li -> new LetIn(li.name(), f.apply(li.value()), f.apply(li.body()), li.pos());
            case Block bl -> new Block(bl.params(), f.apply(bl.body()), bl.pos());
            case ListLit l -> new ListLit(mapExprs(l.elements(), f), l.pos());
            case ListComp comp -> new ListComp(f.apply(comp.element()), mapExprs(comp.guards(), f), comp.pos());
            case Tuple tup -> new Tuple(mapExprs(tup.elements(), f), tup.pos());
            case TupleGet tg -> new TupleGet(f.apply(tg.tuple()), tg.index(), tg.arity(), tg.pos());
            case NewData nd -> {
                List<FieldInit> inits = new ArrayList<>();
                for (FieldInit i : nd.inits()) {
                    inits.add(new FieldInit(i.name(), f.apply(i.value()), i.pos()));
                }
                yield new NewData(nd.typeName(), inits, nd.spreads(), nd.pos());
            }
            case Match m -> {
                List<Case> cases = new ArrayList<>();
                for (Case c : m.cases()) {
                    cases.add(new Case(c.caseTypes(), c.binding(), f.apply(c.body()), c.pos()));
                }
                yield new Match(f.apply(m.scrutinee()), cases, m.pos());
            }
        };
    }

    private static List<Expr> mapExprs(List<Expr> es, UnaryOperator<Expr> f) {
        List<Expr> out = new ArrayList<>();
        for (Expr e : es) {
            out.add(f.apply(e));
        }
        return out;
    }
}
