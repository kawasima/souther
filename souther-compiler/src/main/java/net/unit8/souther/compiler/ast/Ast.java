package net.unit8.souther.compiler.ast;

import net.unit8.souther.compiler.SourcePos;

import java.util.List;
import java.util.Optional;

/**
 * The slice-2 abstract syntax: a module of product {@code data} definitions with one or
 * more fields, each with an optional {@code invariant}, {@code decoder} (a single-value
 * {@code from Text|Int} form or a multi-field {@code from Object} form), and
 * {@code encoder} (a single Raw value or a {@code Object { ... }} form).
 */
public interface Ast {

    /** The source position of this node. Every record below provides it. */
    SourcePos pos();

    /** A whole source file: its public surface, imports, and definitions. */
    record Module(String name,
                  List<String> exposing,
                  List<Import> imports,
                  List<Def> defs,
                  List<BehaviorDef> behaviors,
                  List<RequiredBehavior> requireds,
                  SourcePos pos) implements Ast {}

    /** {@code import <module> { name, ... }} — an explicit, non-wildcard import (spec 4). */
    record Import(String module, List<String> names, SourcePos pos) implements Ast {}

    /** A behavior definition: a body form or a pipeline (`>>`) form. */
    sealed interface BehaviorDef extends Ast permits BodyBehavior, PipeBehavior {
        String name();
    }

    /**
     * {@code behavior name = (p1: T1, ...) -> R constructs A, B { body }}.
     *
     * <p>{@code body} is a single expression. The surface forms {@code let} and {@code require}
     * are desugared by the parser into {@link LetIn} and {@link If} (spec 16.4), so every later
     * stage sees one expression tree and has exactly one place where a value can be constructed.
     */
    record BodyBehavior(String name,
                        List<Param> params,
                        RetType ret,
                        List<String> constructs,
                        Expr body,
                        SourcePos pos) implements BehaviorDef {}

    /** A behavior parameter. Its type may be an anonymous union of arms (spec 12.2). */
    record Param(String name, RetType type, SourcePos pos) implements Ast {}

    /** {@code behavior name = f >> g >> ...} */
    record PipeBehavior(String name, List<String> stages, SourcePos pos) implements BehaviorDef {}

    /** {@code required behavior name = (T) -> R} — implemented in Java, injected. */
    record RequiredBehavior(String name, TypeRef paramType, RetType ret, SourcePos pos)
            implements Ast {}

    /** A behavior return type: the output sum — one or more unmarked domain arms (spec 12.2). */
    record RetType(List<TypeRef> arms, SourcePos pos) implements Ast {}

    /** A top-level data definition: product, sum, or unit. */
    sealed interface Def extends Ast permits Data, SumData, UnitData {
        String name();
    }

    /** A product data definition: included data (flattened) plus its own fields. */
    record Data(String name,
                List<String> includes,
                List<Field> fields,
                Optional<Expr> invariant,
                Optional<DecoderDef> decoder,
                Optional<EncoderDef> encoder,
                SourcePos pos) implements Def {}

    /** A sum data definition {@code data X = A | B | ...} with optional discriminate decoder/encoder. */
    record SumData(String name,
                   List<String> arms,
                   Optional<Discriminate> decoder,
                   Optional<SumEncoder> encoder,
                   SourcePos pos) implements Def {}

    /** {@code encoder discriminate on "key" { Arm => "tag" ... }} — the inverse of discriminate. */
    record SumEncoder(String key, List<EncVariant> variants, SourcePos pos) implements Ast {}

    record EncVariant(String armType, String tag, SourcePos pos) implements Ast {}

    /** A unit data definition {@code data U} with no fields. */
    record UnitData(String name, SourcePos pos) implements Def {}

    /** {@code decoder from Object discriminate on "key" { "tag" => Arm.decoder ... }} */
    record Discriminate(String key, List<Variant> variants, SourcePos pos) implements Ast {}

    record Variant(String tag, String armType, SourcePos pos) implements Ast {}

    /** A field: a role name and its type. */
    record Field(String name, TypeRef type, SourcePos pos) implements Ast {}

    /** A named type reference, optionally with one type argument (e.g. {@code List<T>}). */
    record TypeRef(String name, TypeRef arg, SourcePos pos) implements Ast {}

    /** The kind of primitive Raw a single-value decoder reads / an encoder writes. */
    enum RawKind { TEXT, INT, BOOL, DECIMAL, DATE, DATETIME }

    // --- decoders ---

    sealed interface DecoderDef extends Ast permits PrimDecoder, ObjectDecoder {}

    /** {@code decoder from Text|Int as <input> { <stmts> <construct> }} (single value). */
    record PrimDecoder(RawKind from,
                       String inputName,
                       List<DecStmt> stmts,
                       Construct result,
                       SourcePos pos) implements DecoderDef {}

    /** {@code decoder from Object { <binds> <construct> }} (multi-field, accumulating). */
    record ObjectDecoder(List<Bind> binds, Construct result, SourcePos pos) implements DecoderDef {}

    /** {@code name <- field("key", <decRef>)} inside an object decoder. */
    record Bind(String name, String key, DecRef ref, SourcePos pos) implements Ast {}

    /** The decoder referenced by a bind: a primitive, another data's {@code .decoder}, or a list. */
    sealed interface DecRef extends Ast permits PrimDecRef, DataDecRef, ListDecRef, OptionDecRef, MapDecRef {}

    record PrimDecRef(PrimKind kind, SourcePos pos) implements DecRef {}

    record DataDecRef(String typeName, SourcePos pos) implements DecRef {}

    /** {@code list(<elementDecRef>)} */
    record ListDecRef(DecRef element, SourcePos pos) implements DecRef {}

    /** An optional field decoder: absent/null becomes {@code None}, present decodes {@code element}. */
    record OptionDecRef(DecRef element, SourcePos pos) implements DecRef {}

    /** A {@code Map<String, T>} decoder: each object value is decoded with {@code value}. */
    record MapDecRef(DecRef value, SourcePos pos) implements DecRef {}

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
                    OptionRaw, MapEnc {}

    /** Encodes a {@code Map<String, T>} to a {@code Raw.Object}, each value via {@code elem}. */
    record MapEnc(Expr source, EncElem elem, SourcePos pos) implements RawExpr {}

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

    /** How to encode a list element: a primitive or another data's {@code .encode}. */
    sealed interface EncElem extends Ast permits PrimEnc, DataEnc {}

    record PrimEnc(PrimKind kind, SourcePos pos) implements EncElem {}

    record DataEnc(String typeName, SourcePos pos) implements EncElem {}

    record RawEntry(String key, RawExpr value, SourcePos pos) implements Ast {}

    // --- expressions ---

    sealed interface Expr extends Ast
            permits IntLit, StringLit, BoolLit, Var, FieldAccess, Call, Binary, Not, NewData, Match, If,
                    ListLit, ListComp, LetIn {}

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

    /** {@code if cond then a else b} — both branches must have the same type (spec 16.2). */
    record If(Expr cond, Expr then, Expr els, SourcePos pos) implements Expr {}

    /** {@code match scrutinee { case Arm as x => body ... }} over a sum type. */
    record Match(Expr scrutinee, List<Case> cases, SourcePos pos) implements Expr {}

    record Case(String armType, String binding, Expr body, SourcePos pos) implements Ast {}

    /** {@code TypeName { ..src, field: expr, ... }} used as an expression (construction in a behavior). */
    record NewData(String typeName, List<FieldInit> inits, List<String> spreads, SourcePos pos)
            implements Expr {}

    record IntLit(long value, SourcePos pos) implements Expr {}

    record StringLit(String value, SourcePos pos) implements Expr {}

    record BoolLit(boolean value, SourcePos pos) implements Expr {}

    record Var(String name, SourcePos pos) implements Expr {}

    record FieldAccess(Expr target, String field, SourcePos pos) implements Expr {}

    record Call(String fn, List<Expr> args, SourcePos pos) implements Expr {}

    record Binary(BinOp op, Expr left, Expr right, SourcePos pos) implements Expr {}

    record Not(Expr operand, SourcePos pos) implements Expr {}

    enum BinOp { EQ, NE, LT, LE, GT, GE, AND, OR, ADD, SUB, MUL, CONCAT }
}
