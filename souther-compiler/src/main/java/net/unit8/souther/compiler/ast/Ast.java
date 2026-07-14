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

    /** A whole source file: data definitions, behaviors, and required-behavior declarations. */
    record Module(String name,
                  List<Def> defs,
                  List<BehaviorDef> behaviors,
                  List<RequiredBehavior> requireds,
                  SourcePos pos) implements Ast {}

    /** A behavior definition: a body form or a pipeline (`>>`) form. */
    sealed interface BehaviorDef extends Ast permits BodyBehavior, PipeBehavior {
        String name();
    }

    /** {@code behavior name(p1: T1, ...) -> R constructs A, B { stmt*; result }}. */
    record BodyBehavior(String name,
                        List<Param> params,
                        RetType ret,
                        List<String> constructs,
                        List<BStmt> stmts,
                        Expr result,
                        SourcePos pos) implements BehaviorDef {}

    /** A behavior parameter. */
    record Param(String name, TypeRef type, SourcePos pos) implements Ast {}

    /** {@code behavior name = f >> g >> ...} */
    record PipeBehavior(String name, List<String> stages, SourcePos pos) implements BehaviorDef {}

    /** {@code required behavior name(T) -> R} — implemented in Java, injected. */
    record RequiredBehavior(String name, TypeRef paramType, RetType ret, SourcePos pos)
            implements Ast {}

    /** A behavior return type: one or more success types (a union), optionally {@code Result<..., error>}. */
    record RetType(List<TypeRef> success, Optional<TypeRef> error, SourcePos pos) implements Ast {}

    /** A statement in a behavior body: a binding or a guard. */
    sealed interface BStmt extends Ast permits Let, Guard {}

    /** {@code require cond else <failure>} inside a behavior body. */
    record Guard(Expr cond, Expr failure, SourcePos pos) implements BStmt {}

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
    enum RawKind { TEXT, INT }

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
    sealed interface DecRef extends Ast permits PrimDecRef, DataDecRef, ListDecRef {}

    record PrimDecRef(PrimKind kind, SourcePos pos) implements DecRef {}

    record DataDecRef(String typeName, SourcePos pos) implements DecRef {}

    /** {@code list(<elementDecRef>)} */
    record ListDecRef(DecRef element, SourcePos pos) implements DecRef {}

    /** A primitive field decoder kind. */
    enum PrimKind { STRING, INT }

    /** A statement in a single-value decoder body. */
    sealed interface DecStmt extends Ast permits Let, Require {}

    record Let(String name, Expr value, SourcePos pos) implements DecStmt, BStmt {}

    record Require(Expr cond, String errorCode, SourcePos pos) implements DecStmt {}

    /** A typed record literal {@code TypeName { ..src, field: expr, ... }} — a construction. */
    record Construct(String typeName, List<FieldInit> inits, List<String> spreads, SourcePos pos)
            implements Ast {}

    /** One {@code field: expr} (or shorthand {@code field}) inside a record literal. */
    record FieldInit(String name, Expr value, SourcePos pos) implements Ast {}

    // --- encoders ---

    record EncoderDef(String selfName, RawExpr result, SourcePos pos) implements Ast {}

    /** A Raw-building expression. */
    sealed interface RawExpr extends Ast permits TextRaw, IntRaw, ObjectRaw, EncodeRaw {}

    record TextRaw(Expr arg, SourcePos pos) implements RawExpr {}

    record IntRaw(Expr arg, SourcePos pos) implements RawExpr {}

    record ObjectRaw(List<RawEntry> entries, SourcePos pos) implements RawExpr {}

    /** {@code TypeName.encode(expr)} — encode a nested data value to Raw. */
    record EncodeRaw(String typeName, Expr arg, SourcePos pos) implements RawExpr {}

    record RawEntry(String key, RawExpr value, SourcePos pos) implements Ast {}

    // --- expressions ---

    sealed interface Expr extends Ast
            permits IntLit, StringLit, BoolLit, Var, FieldAccess, Call, Binary, Not, NewData, Match, If {}

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

    enum BinOp { EQ, NE, LT, LE, GT, GE, AND, OR, ADD, SUB, MUL }
}
