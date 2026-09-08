package souther.compiler.ast;

import souther.compiler.diag.Region;
import souther.compiler.observe.RowIdentity;
import souther.compiler.diag.SourcePos;
import souther.compiler.types.SourceConstructOrigin;
import souther.compiler.types.SourceReferenceOrigin;
import souther.compiler.types.TypeKey;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Map;
import java.util.Optional;

/**
 * The abstract syntax: a module as the characters that spell it were read, with every name still a
 * spelling and nothing yet settled about what it denotes.
 *
 * <p>Every form here is one the frontend writes, and that is what this is — not {@link Hir} with
 * the resolution taken out. A form that comes into being further down belongs to the
 * representation whose pass makes it: a codec is derived from a data's shape, an expansion is
 * written where a call is inlined, and neither has a twin up here for nothing to build. A twin
 * would be a form no parse answers with, held to every rule the reachable frontend is held to,
 * with a reader taking it for something the language can be written in.
 *
 * <p>What the frontend writes is not the same as what an author wrote. A unit data a construction
 * implied, a binder for a name the source never spelled, the {@code Option<T>} a {@code T?} became
 * — all of them are written here, by the pass that reads the concrete syntax. What may not be
 * written here is a form that pass never answers with, and
 * {@code TheParsedTreeHoldsOnlyWhatTheFrontendWritesTest} asks of each of these forms whether it
 * is one.
 *
 * <p>The same holds of a form's parts. A component a parse cannot decide reads as a distinction the
 * tree draws, and offers a pass below an answer to take — one that says the same thing wherever it
 * is asked, since that is all a parse can put there. Where a construction came from is the one that
 * was here; {@code TheParsedTreeDoesNotSayWhereAConstructionCameFromTest} is what says it is not.
 */
public interface Ast {

    /** The source position of this node. Every record below provides it. */
    SourcePos pos();

    /**
     * A node an author wrote, which therefore has a stretch of source as well as an anchor.
     *
     * <p>Here rather than on each kind of node that has one, because it is one rule: what a report
     * about a node points at is either the characters it was written over or the point it is
     * anchored at, and a second node kind working that out for itself would be a second answer to
     * settle against this one.
     */
    interface Written extends Ast {

        /**
         * The stretch of source this node was written over, or null where no one wrote it.
         *
         * <p>Not {@link #pos()}, and not derivable from it. A position is where a report about this
         * node is anchored, and an anchor is chosen for the node it is about: a binary operation is
         * anchored at its operator and a field read at its field, because that is where a complaint
         * about either belongs. Neither is where the node begins, so a region built from an anchor
         * and a width starts inside what the author wrote however the width is arrived at.
         *
         * <p>Read off the tree the parser built and carried from there. Working it out later means
         * measuring the node — its name's length, its value's length, the token kinds it is made of
         * — and a measurement is a claim about the value rather than about the file. The two agree
         * until the source spells something the value does not keep: an escape, a decomposed
         * spelling, a leading zero, a pair of parentheses.
         *
         * <p>Null rather than a zero-width region at the anchor. A node a lowering minted was
         * written nowhere, and a report about it has nothing to underline — which is a different
         * answer from underlining no characters at a place the author's cursor could be.
         */
        Region region();

        /**
         * Where a report about this node points: the characters it was written over, or the point
         * it is anchored at where no one wrote it.
         *
         * <p>The choice between two answers already held, and not a third answer worked out from
         * either. A node a lowering minted has somewhere a complaint about it belongs and nothing to
         * underline, and that is what a point is — a place with no characters claimed at it, which
         * the renderer draws as the one caret it draws for anything it cannot measure.
         *
         * <p>Null only where the node has neither, which is a report with nowhere to point.
         */
        default Region reportedAt() {
            Region written = region();
            if (written != null) {
                return written;
            }
            return pos() == null ? null : Region.point(pos());
        }
    }

    /**
     * A name a body binds: a parameter, a {@code let}, a lambda's parameter, a {@code match} arm's
     * binding. Every node below that introduces one holds this rather than a bare spelling, so what
     * a name under it means is the binding and not the text.
     *
     * <p>The spelling and nothing more. Which binding it is, is {@code Resolve}'s to say, and a
     * binder it has answered is a {@link Hir.Binder} — so there is no absent identity here for a
     * reader to test for.
     */
    record Binder(WrittenName written, SourcePos pos) implements Ast {

        /**
         * A binder read off a name the author wrote, taken from the name itself so its spelling and
         * its place cannot come from two different things.
         *
         * <p>There is no form here that takes the two apart. A caller holding a spelling and a
         * position separately is a caller nothing can stop from pairing a name with somewhere it is
         * not written, and this branch shipped that mistake three times: the parameter {@code .v}
         * desugars to, anchored at the {@code .}; a lambda's parameters, all anchored at the
         * lambda; a {@code match} arm's binding, anchored at the {@code |}. The parser reads a
         * binder off its token; a pass that needs a name of its own says {@link #desugared}.
         */
        public static Binder of(Name written) {
            return new Binder(written.name(), written.pos());
        }

        /**
         * A binder for a name no one wrote: what a desugaring binds a value to so that the form it
         * is rewriting keeps taking plain names. {@code anchor} is the form it came from, which is
         * where a complaint about it belongs and is not where its name is.
         */
        public static Binder desugared(String name, SourcePos anchor) {
            return new Binder(WrittenName.synthetic(name, anchor), anchor);
        }

        /** What the binding is called. A diagnostic quotes {@link WrittenName#quoted()} instead,
         * which is what the author typed. */
        public String name() {
            return written.canonical();
        }

        /**
         * Where the author wrote this name, or null where the author wrote no name at all.
         *
         * <p>Not the same as {@link #pos()}, which is the form the binding came from: a {@code let}
         * statement starts at its keyword, a lambda's parameters share the lambda's start, and a
         * name a desugaring invented is written nowhere and only anchored somewhere.
         */
        public SourcePos namePos() {
            return written.authored() ? written.pos() : null;
        }

        @Override
        public String toString() {
            return name();
        }
    }

    /**
     * A name that denotes a declared type, as the source writes it — bare {@code 金額}, qualified
     * {@code billing.金額}, or through an import alias.
     *
     * <p>What it denotes is {@code Resolve}'s to say, once, before any check runs, and a name it has
     * answered is a {@link Hir.Name}. So no later pass decides for itself what a spelling means or
     * whether a qualified one is allowed here (issue #177), and this form has no slot for an answer
     * to be put in early.
     *
     * <p>{@link #written()} is the name, and is not what a report quotes: a bare and a qualified
     * spelling of one type are one name, and a decomposed and a composed spelling are one name too,
     * so neither says what the author typed. A report asks {@link WrittenName#quoted()} and
     * underlines {@link WrittenName#region()}.
     */
    record Name(WrittenName name) implements Ast {

        /** A name as the parser read it. The spelling is the source's, not one a caller
         * canonicalized on the way in — {@link WrittenName} is what canonicalizes, so the name and
         * the characters it was written with stay one value. */
        public static Name written(String spelling, SourcePos pos) {
            return new Name(WrittenName.of(spelling, pos));
        }

        /** The same, off an occurrence the parser has already read. */
        public static Name written(WrittenName name) {
            return new Name(name);
        }

        /** The bare name this reaches its declaration by, whatever the source spelled. */
        public String written() {
            return name.canonical();
        }

        /** Where the name is written. */
        @Override
        public SourcePos pos() {
            return name.pos();
        }

        @Override
        public String toString() {
            return written();
        }
    }

    /**
     * A whole source file: its public surface, imports, and definitions.
     *
     * <p>{@code exposedOutputs} maps an exposed composition behavior's name to the output signature written
     * in the {@code exposing} list ({@code exposing ( name : A | B )}, spec §declared-composition-output). An
     * exposed {@code >->} composition must have one, checked to match its inferred output (ADR-0024); other
     * exposed names carry no signature (their type is at the definition).
     *
     * <p>{@code fns} is what the source wrote, and it stays that at every stage. {@code takenOn} is
     * what the module emits as methods of its own without having written them, which is two kinds of
     * definition: a recursive helper it reaches, which cannot be inlined and has to be lowered
     * somewhere, and a definition minted for what a row writes at a position, which has no call site
     * to be inlined into. Only {@code fns} is declared here, and no rule reads a name to tell them
     * apart — {@code List.foldFrom} is reached under the library's alias and declared in
     * {@code souther.list}.
     *
     * <p>Two components rather than one list a later pass appends to. Appended, every reader asking
     * what the module declared got what it declared before {@link
     * souther.compiler.query.Shapes.Prepared} and that plus these after — the {@code exposing} check,
     * the value namespace a body is resolved against, and what the module publishes among them.
     *
     * <p>Whoever rebuilds a module carries {@code takenOn} across, and there is no constructor that
     * defaults it: a rebuild that quietly dropped it would put a module through codegen with the
     * methods its bodies call missing, which is the failure this separation is here to make
     * impossible. The arity says something has to be passed; that it is this module's own is what a
     * reader of the rebuild has to see, which is why every one of them names it.
     */
    record Module(String name,
                  List<String> exposing,
                  Map<String, RetType> exposedOutputs,
                  List<Import> imports,
                  List<Def> defs,
                  List<BehaviorDef> behaviors,
                  List<FnDef> fns,
                  List<FnDef> takenOn,
                  List<Example> examples,
                  List<Fake> fakes,
                  String exampleFileTarget,
                  SourcePos pos) implements Ast {}

    /**
     * {@code fake <injected> | (in) -> out | ...} — a test double for an injected behavior, used to
     * evaluate an example of a behavior that {@code depends on} it. The rows form an input→output
     * table matched by value equality; a {@code _ -> out} row is the default when no input matches
     * (otherwise a miss is an error). A fake produces no run-time class (it is a proxy at evaluation).
     *
     * <p>The target is a name to be resolved and not a spelling to be looked up later. What it means
     * is what the module's scope says a behavior of that spelling means, which is the same reading a
     * {@code depends on} clause gets — so a dependency declared in another module is named here the
     * way it is named there.
     */
    record Fake(Var target, List<FakeRow> rows, SourcePos pos) implements Ast {}

    /** One fake row: input argument expressions mapped to an output, or the default ({@code inputs}
     * null / {@code isDefault} true). */
    record FakeRow(List<Expr> inputs, Expr output, boolean isDefault, SourcePos pos) implements Ast {}

    /** {@code with <dep> = <value>} on an example row — a value fake for an injected dependency
     * (a zero-argument behavior whose faked result is a constant). The dependency is named as a
     * {@link Fake}'s target is. */
    record With(Var dep, Expr value, SourcePos pos) implements Ast {}

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
     * What stands where a row's answer goes.
     *
     * <p>Three states and the source says which. A row that asserts something carries the expression
     * it asserts; a row written {@code <?>} says its answer is owed and is a row all the same; and a
     * row whose answer could not be read is neither, because nothing was written there to be read.
     *
     * <p>The last two are not one state. {@link Unanswered} is what an author wrote — the row is
     * well formed, and what it says is that the answer is still to come. {@link Unwritten} is this
     * compiler's recovery: the text after the arrow was not an expression, a diagnostic says so, and
     * what the row would have asserted is not known. Held as one, a module full of syntax errors
     * would read as a module full of work an author had deliberately left.
     */
    sealed interface Expected permits Expected.Asserted, Expected.Unanswered, Expected.Unwritten {

        /** Where the answer stands, which is where a reader is pointed whatever is there. */
        SourcePos pos();

        /** The row states its answer, and this is what it states. */
        record Asserted(Expr expression) implements Expected {

            public Asserted {
                Objects.requireNonNull(expression, "an asserted answer is an expression");
            }

            @Override
            public SourcePos pos() {
                return expression.pos();
            }
        }

        /** The row is written {@code <?>}: its answer is owed and nobody has written it. */
        record Unanswered(SourcePos pos) implements Expected {}

        /** No answer was read here. The row is malformed and a parse diagnostic says how. */
        record Unwritten(SourcePos pos) implements Expected {}
    }

    /**
     * One example row: what it names itself, the input argument expressions, and what stands where
     * its answer goes. A bare {@link Var} asserted asserts only the result arm (the case); a
     * {@link NewData}, a {@link Call} (a newtype constructor), or a literal asserts the whole value.
     */
    record ExampleRow(RowIdentity identity, List<Expr> inputs, List<With> withs, Expected expected,
                      SourcePos pos) implements Ast {}

    /**
     * {@code import a.b as B ( X, Y )} — an explicit, non-wildcard import (spec §modules).
     * {@code importedNames} are the names this import brings into scope bare; {@code alias} is the
     * qualifier the module is read under here, or null. Both parts are optional: a type is reachable
     * qualified whether or not it was imported (spec §modules), so an import with neither is just the
     * dependency written down.
     */
    record Import(String module, String alias, List<ImportedName> importedNames, SourcePos pos)
            implements Ast {

        public Import {
            importedNames = List.copyOf(importedNames);
        }

        /** The same names as the text they were written with — what a reader asking only what this
         * import brings in wants. Derived on each call rather than held: a record has its components
         * and no other field, and an import list is a handful of names. */
        public List<String> names() {
            return importedNames.stream().map(ImportedName::text).toList();
        }
    }

    /**
     * One name on an import list, and where it was written.
     *
     * <p>The position belongs to the name rather than to the line, because the name is what a reader
     * is told about. An import bringing in four names of which one is unused has something to say
     * about that one, and the line the four share cannot say which.
     */
    record ImportedName(WrittenName written) implements Ast {

        /** An entry a pass wrote, standing for a name no import list spells. */
        public ImportedName(String text, SourcePos pos) {
            this(pos == null ? WrittenName.synthetic(text, null) : WrittenName.of(text, pos));
        }

        /** The name the entry claims. */
        public String text() {
            return written.canonical();
        }

        /** Where the entry is written. */
        @Override
        public SourcePos pos() {
            return written.pos();
        }
    }

    /**
     * A behavior definition — a specification, not an implementation (spec §behavior, §ast-nodes). It is either
     * a {@link SpecBehavior} (an input/output signature, with the body left to a matching
     * {@link FnDef} or to Java injection) or a {@link PipeBehavior} (a {@code >->} composition, which
     * is itself the implementation).
     */
    sealed interface BehaviorDef extends Ast permits SpecBehavior, PipeBehavior {

        /** The name this declares and the occurrence of it that declares it. Not {@link #pos()},
         * which is where the {@code behavior} keyword is. */
        WrittenName written();

        default String name() {
            return written().canonical();
        }

        @Override
        SourcePos pos();
    }

    /**
     * {@code behavior name = (p1: T1, ...) -> R constructs A, B depends on C, D} — a signature with
     * no body (spec §behavior-io). A same-named {@link FnDef} is its implementation (§fn-declaration); with none, and
     * not a pipeline, it is a Java-injected behavior (§injected-behavior).
     *
     * <p>{@code dependsOn} lists the implementation-less behaviors the {@code fn} calls; they become
     * the {@code fn}'s trailing arguments and the injected fields of the generated class (§depends-on).
     */
    record SpecBehavior(WrittenName written,
                        List<Param> params,
                        RetType ret,
                        List<Name> constructs,
                        List<Var> dependsOn,
                        List<EnsuresClause> ensures,
                        SourcePos pos) implements BehaviorDef {

        /** A behavior a pass wrote, named but written nowhere. */
        public SpecBehavior(String name, List<Param> params, RetType ret, List<Name> constructs,
                            List<Var> dependsOn, List<EnsuresClause> ensures, SourcePos pos) {
            this(WrittenName.synthetic(name, pos), params, ret, constructs, dependsOn, ensures, pos);
        }
    }

    /** One postcondition on a behavior. A single-output clause has one arm with no cases; a sum
     * clause names the output cases for which each expression is stated. */
    record EnsuresClause(Optional<String> name, List<EnsuresArm> arms, SourcePos pos, Region region)
            implements Written {}

    record EnsuresArm(List<Name> cases, Expr expr, SourcePos pos, Region region) implements Written {}

    /** A behavior parameter. Its type may be an anonymous union of cases (spec §unmarked-output). */
    record Param(WrittenName written, RetType type) implements Ast {

        /** A parameter a pass wrote. */
        public Param(String name, RetType type, SourcePos pos) {
            this(WrittenName.synthetic(name, pos), type);
        }

        /** What the parameter is called. */
        public String name() {
            return written.canonical();
        }

        /** Where the name is written. */
        @Override
        public SourcePos pos() {
            return written.pos();
        }
    }

    /**
     * {@code behavior name = f >-> g >-> ... [-> A | B]} — a composition (spec §sequential-composition).
     * {@code declaredOut} is the optional trailing output declaration (§declared-composition-output): null
     * when absent (output is inferred), else the declared cases, which must match the inferred output exactly
     * (E1604).
     */
    record PipeBehavior(WrittenName written, List<Var> stages, RetType declaredOut, SourcePos pos)
            implements BehaviorDef {

        /** A composition a pass wrote, named but written nowhere. */
        public PipeBehavior(String name, List<Var> stages, RetType declaredOut, SourcePos pos) {
            this(WrittenName.synthetic(name, pos), stages, declaredOut, pos);
        }
    }

    /**
     * What stands to the right of a definition's {@code =}: the expression the author wrote, or —
     * in the reserved {@code souther} namespace only (spec §primitives) — the name of a shipped
     * kernel, {@code intrinsic "string.trim"}. One or the other, never neither, so a reader
     * switches rather than testing two fields for null.
     */
    sealed interface FnBody {
        /** A written body. */
        record Written(Expr expr) implements FnBody {
        }

        /** A named kernel; {@code key} is what the backend emits for. */
        record Intrinsic(String key) implements FnBody {
        }
    }

    /**
     * A {@code let} definition. A behavior fn or a helper carries a {@link FnBody.Written written}
     * body and no {@code declaredReturn}. A core intrinsic (spec §primitives) instead declares its
     * return type and names a primitive: {@code let trim (s: String): String = intrinsic
     * "string.trim"} — its body is {@link FnBody.Intrinsic}, written only in the {@code souther}
     * namespace. What the modifiers say is in {@link Modifiers}.
     *
     * <p>{@code declaredIn} is the module that wrote the {@code let}, and it is written once, where
     * the source is parsed. Every pass after that copies it. It is here because a module emits the
     * recursive helpers it reaches as its own methods, under the names it reaches them by, so from
     * that point a module's fns are declarations of several modules under one set of names — and
     * which module declared one decides what may be checked of it (ADR-0098). The name cannot answer
     * that: {@code List.foldFrom} is reached under the library's alias and declared in
     * {@code souther.list}.
     *
     * <p>Null says this is not a module-level declaration at all — a block standing where a function
     * goes, which has parameters and a body so that applying it expands like a call. It does not say
     * "from somewhere else": a helper this module took on to emit was written by some module and says
     * which, and every rule here reads that rather than the absence. Anything a later pass mints that
     * <em>is</em> a declaration says which module it belongs to, so that these stay one question.
     *
     * <p>{@code role} is what the definition was made as, and it is a second question from the one
     * {@code declaredIn} answers: an attached file's value is declared in the target module and is
     * not something that module's own source wrote. See {@link DefinitionRole}.
     */
    record FnDef(WrittenName written, String declaredIn, List<FnParam> params,
                 RetType declaredReturn, FnBody body, Modifiers modifiers, DefinitionRole role,
                 SourcePos pos)
            implements Ast {

        public FnDef {
            Objects.requireNonNull(role, "a definition says what it was made as");
        }

        /** A fn read as a definition, which is every one a module's own source wrote. */
        public FnDef(WrittenName written, String declaredIn, List<FnParam> params,
                     RetType declaredReturn, FnBody body, Modifiers modifiers, SourcePos pos) {
            this(written, declaredIn, params, declaredReturn, body, modifiers,
                    DefinitionRole.Ordinary.INSTANCE, pos);
        }

        /** A fn with no modifier (the common case; totality-checked if recursive, published). */
        public FnDef(WrittenName written, String declaredIn, List<FnParam> params,
                     RetType declaredReturn, FnBody body, SourcePos pos) {
            this(written, declaredIn, params, declaredReturn, body, Modifiers.NONE, pos);
        }

        /** The same declaration read as what an attached file wrote it as: a value for the rows
         *  beside it to name, which the model neither publishes nor may reach. */
        public FnDef asAnAttachedValue() {
            return new FnDef(written, declaredIn, params, declaredReturn, body, modifiers,
                    DefinitionRole.AttachedValue.INSTANCE, pos);
        }

        /** A block standing where a function goes, which no module declares: a lambda a binding
         * holds, one handed to a function parameter. It has parameters and a body like a
         * declaration, which is what lets an application of it expand like a call, and it is
         * declared by nobody, which is what {@link #declaredIn} says of it. */
        public static FnDef lambda(String name, List<FnParam> params, RetType declaredReturn,
                                   FnBody body, SourcePos pos) {
            return new FnDef(WrittenName.synthetic(name, pos), null, params, declaredReturn, body,
                    Modifiers.NONE, pos);
        }

        /**
         * The same declaration under the name a module reaches it by.
         *
         * <p>The one way a declaration is renamed. A module emits a recursive helper it reaches as
         * one of its own methods, under the name it reaches it by ({@code List.foldFrom},
         * {@code maths.spin}), and that name is not where the declaration came from:
         * {@code List.foldFrom} is reached under the library's alias and declared in
         * {@code souther.list}, so the module it came from cannot be read back out of it. Renaming
         * here carries {@link #declaredIn} across rather than restating it, so no caller is in a
         * position to pair a name with an origin that is not its own.
         */
        public FnDef reachedAs(String name) {
            return new FnDef(WrittenName.synthetic(name, pos), declaredIn, params, declaredReturn,
                    body, modifiers, role, pos);
        }

        /** The same declaration with {@code replacement} in place of its body. */
        public FnDef withBody(FnBody replacement) {
            return new FnDef(written, declaredIn, params, declaredReturn, replacement, modifiers,
                    role, pos);
        }

        /** What the fn is called. */
        public String name() {
            return written.canonical();
        }

        /** Whether {@code module} is the module that declared this. Asked of the declaration, so a
         * helper another module published answers for the module that wrote it however the module
         * reading it happens to reach it. A lambda is declared by no module and answers no. */
        public boolean declaredBy(String module) {
            return declaredIn != null && declaredIn.equals(module);
        }

        /** Whether the definition opts out of the totality check. */
        public boolean partial() {
            return modifiers.partial();
        }

        /** Whether the definition is kept out of the module's published surface. */
        public boolean isPrivate() {
            return modifiers.isPrivate();
        }

        /** The expression the author wrote. Asked from positions an intrinsic cannot reach — a
         *  user module, or a helper already known written — which is said here rather than by a
         *  silent null. */
        public Expr writtenBody() {
            return switch (body) {
                case FnBody.Written w -> w.expr();
                case FnBody.Intrinsic _ -> throw new IllegalStateException(
                        "`" + name() + "` is an intrinsic and has no written body");
            };
        }
    }

    /**
     * The words written before a {@code let}. {@code partial} opts a recursive helper out of the
     * totality check (spec §fn-declaration). {@code isPrivate} keeps the definition out of the
     * module's published surface: it is a core privilege, written only in the reserved
     * {@code souther} namespace, so the standard library can carry an implementation helper that
     * no caller can name.
     *
     * <p>They travel together because they are both answers to "how was this {@code let} written",
     * and because two adjacent booleans in a constructor call read as neither.
     */
    record Modifiers(boolean partial, boolean isPrivate) {
        public static final Modifiers NONE = new Modifiers(false, false);
    }

    /** A {@code fn} parameter: a name, and a type only when the {@code fn} is a helper (spec §fn-declaration).
     * A helper's parameter type may be a function type {@link FnType}; a behavior fn's parameter
     * carries no type ({@code type} is null). {@code typeFromPattern} marks a type read off a
     * constructor pattern in parameter position rather than written beside the name — a behavior's
     * implementation may write the pattern, and its type still comes from the behavior. */
    record FnParam(Binder binder, RetType type, boolean typeFromPattern) implements Ast {
        /** A parameter whose type, if any, the author wrote (the common case). */
        public FnParam(Binder binder, RetType type) {
            this(binder, type, false);
        }

        public String name() {
            return binder.name();
        }

        /** The name and the occurrence of it that binds this parameter. */
        public WrittenName written() {
            return binder.written();
        }

        @Override
        public SourcePos pos() {
            return binder.pos();
        }
    }

    /**
     * One term of a written type: a reference to a named type, or a function type. A type position
     * admits either, so what a name means does not depend on where it was written; whether a
     * function may stand in a given position is decided by what that position requires of a type,
     * not by the grammar.
     */
    sealed interface TypeTerm extends Ast permits TypeRef, FnType {}

    /** A function type {@code (A, ...) -> B}. Its parameters and result are whole types, so a
     * function may take one and may return one. */
    record FnType(List<RetType> params, RetType result, SourcePos pos) implements TypeTerm {}

    /** A written type: one term, or the unmarked sum of several (spec §unmarked-output). */
    record RetType(List<TypeTerm> cases, SourcePos pos) implements Ast {

        /** The function type this stands for, or null when it is not a lone function type. A sum of
         * a function with anything else is not one, and has no case to be told apart by. */
        public FnType asFn() {
            return cases.size() == 1 && cases.get(0) instanceof FnType fn ? fn : null;
        }
    }

    /** A top-level data definition: product, sum, or unit. */
    sealed interface Def extends Ast permits Data, SumData, UnitData {

        /**
         * The name this declares, and the occurrence of it that declares it.
         *
         * <p>Where the name is written is not where the declaration starts — {@code data} comes
         * first. A reader asking what a cursor is on has only the name to compare against, and a
         * declaration that answers from its keyword answers about the keyword. It is
         * {@link WrittenName#authored() unwritten} for a declaration nobody wrote: a unit data a
         * construction implied.
         */
        WrittenName written();

        /**
         * The module that wrote this declaration, as {@link FnDef#declaredIn} is for a {@code let}.
         *
         * <p>Written once, where the source is parsed, and copied by every pass after. It is here
         * because a declaration is read apart from the module holding it — a registry answers with
         * one, a scope names one, a check is handed one — and from there the name cannot say which
         * module declares it. A reader that pairs the name with the module it happens to be
         * compiling answers for a declaration here whatever the name came from, which is what
         * issues #464, #696 and #700 each were.
         *
         * <p>Not what the declaration is reached under. A runtime-backed data is written in a
         * standard-library module and reached in the runtime namespace, because that is where its
         * implementation class is; which module declares it and which name it answers to are two
         * questions, and this is the first.
         */
        String declaredIn();

        /** What the declaration is called. */
        default String name() {
            return written().canonical();
        }

        /**
         * Which declaration this is, written down.
         *
         * <p>Answered by the declaration, so a reader holding one has the name it goes by and no
         * module of its own to pair it with. A spelling says nothing about which module declares
         * what it spells, and a reader that supplies the module it happens to be compiling answers
         * for a declaration here whatever the name came from.
         */
        default TypeKey declaredKey() {
            return new TypeKey(declaredIn(), name());
        }

        /** Whether {@code module} is the module that wrote this. Asked of the declaration, so it
         * answers for the module that wrote it however the module reading it reaches it. */
        default boolean declaredBy(String module) {
            return declaredIn() != null && declaredIn().equals(module);
        }

        /** Where the name is written, or null where nobody wrote it. */
        default SourcePos namePos() {
            return written().authored() ? written().pos() : null;
        }

        @Override
        SourcePos pos();
    }

    /**
     * A product data definition: included data (flattened) plus its own fields.
     *
     * <p>{@code newtype} marks the explicit newtype form {@code data X = Y} (spec §newtype): a single
     * implicit field named {@code value} of type {@code Y}, encoded as bare {@code Y} instead of an
     * object. Everything else (construction {@code X { value: v }}, access {@code x.value},
     * invariant on {@code value}) is the same as a one-field product; only the external
     * representation differs.
     */
    record Data(WrittenName written,
                String declaredIn,
                boolean newtype,
                List<Name> includes,
                List<Field> fields,
                List<InvariantClause> invariants,
                SourcePos pos) implements Def {}

    /**
     * One {@code invariant} clause, in the order it is written. Every clause must hold, so the
     * clauses of a declaration mean their conjunction; they are kept apart because a failure is
     * reported as one of them — the first that does not hold (spec §invariant-declaration).
     *
     * <p>{@code name} is what an attempted construction's departure arm and a boundary issue call the
     * rule. An unnamed clause is enforced exactly as before it could be named: nothing can tell it
     * apart from the type's other unnamed clauses, so it aborts inside the domain and fails the decode
     * outside it without saying which rule it was.
     */
    record InvariantClause(Optional<String> name, Expr expr, SourcePos pos, Region region)
            implements Written {

        /** A clause no name was written for. */
        public static InvariantClause unnamed(Expr expr) {
            return new InvariantClause(Optional.empty(), expr, expr.pos(), expr.region());
        }

    }

    /** A sum data definition {@code data X = A | B | ...}. Carries no boundary representation — see
     *  {@link Hir.SumData}. */
    record SumData(WrittenName written,
                   String declaredIn,
                   List<Name> cases,
                   SourcePos pos) implements Def {}

    /** A unit data definition {@code data U} with no fields. */
    record UnitData(WrittenName written, String declaredIn, SourcePos pos) implements Def {

        /** A unit data a construction implied — declared nowhere the author wrote, by the module
         * whose text implied it. */
        public UnitData(String name, String declaredIn, SourcePos pos) {
            this(WrittenName.synthetic(name, pos), declaredIn, pos);
        }
    }


    /** A field: a role name and its type. */
    record Field(WrittenName written, TypeTerm type) implements Ast {

        /** A field a pass wrote — the implicit {@code value} of a newtype. */
        public Field(String name, TypeTerm type, SourcePos pos) {
            this(WrittenName.synthetic(name, pos), type);
        }

        /** What the field is called. */
        public String name() {
            return written.canonical();
        }

        /** Where the name is written. */
        @Override
        public SourcePos pos() {
            return written.pos();
        }
    }

    /**
     * A written type reference: {@code name}, the optional single type argument {@code arg}
     * ({@code List<T>}), and {@code tupleElems}. Where {@code name} is null and {@code tupleElems}
     * is non-null the ref is a tuple type {@code (A, B, ...)} (ADR-0036), written only in a
     * helper/stdlib signature. A {@code Map<K, V>} reuses {@code tupleElems} to carry its key type
     * (a single element) while {@code name} is {@code "Map"} and {@code arg} is the value type
     * (ADR-0040).
     *
     * <p>The spelling and the shape around it. What it stands for is decided once, in
     * {@code Resolve} (issue #177), and a reference it has answered is a {@link Hir.TypeRef} — so
     * nothing here has a type slot for a later reader to find empty, and no reader has to know which
     * module the reference was written in to work one out.
     */
    record TypeRef(WrittenName written, TypeTerm arg, List<TypeTerm> tupleElems,
                   SourcePos anchor) implements TypeTerm {

        /** A reference the source wrote, read off the characters that spell it. */
        public static TypeRef written(WrittenName written, TypeTerm arg, List<TypeTerm> tupleElems) {
            return new TypeRef(written, arg, tupleElems, null);
        }

        /** A reference a pass wrote before resolution runs, named but written nowhere — {@code T?}
         * becoming {@code Option<T>}, a {@code Map} carrying its key. */
        public static TypeRef written(String name, TypeTerm arg, List<TypeTerm> tupleElems,
                                      SourcePos pos) {
            return new TypeRef(name == null ? null : WrittenName.synthetic(name, pos), arg,
                    tupleElems, pos);
        }

        /** An ordinary (non-tuple) reference a pass wrote before resolution runs. */
        public static TypeRef written(String name, TypeTerm arg, SourcePos pos) {
            return written(name, arg, null, pos);
        }

        /** The name this reference stands for, or null where it names none. */
        public String name() {
            return written == null ? null : written.canonical();
        }

        /** Where the reference starts. A named one starts where its name does; {@code anchor} is
         * for the ones that have no name to start at, which is why it is not asked otherwise: two
         * places for one reference is two places that can disagree. */
        @Override
        public SourcePos pos() {
            return written == null ? anchor : written.pos();
        }

        /** A tuple type is the nameless form; a named ref that also carries {@code tupleElems}
         *  (a {@code Map} carrying its key) is not a tuple. */
        public boolean isTuple() {
            return written == null && tupleElems != null;
        }

        @Override
        public String toString() {
            return name() == null ? "(tuple)" : name();
        }
    }

    /** {@code field: expr}, or the shorthand {@code field}, in a construction. */
    record FieldInit(WrittenName written, Expr value) implements Ast {

        /** The field this fills. */
        public String name() {
            return written.canonical();
        }

        /** Where the field name is written. */
        @Override
        public SourcePos pos() {
            return written.pos();
        }

    }

    // --- expressions ---

    sealed interface Expr extends Written
            permits IntLit, DecimalLit, StringLit, BoolLit, Var, FieldAccess, Apply, Binary, Neg,
                    NewData, Match, If, IfConstructed, ListLit, ListComp, LetIn, Block,
                    Tuple, TupleGet, Unreachable {
    }

    /**
     * {@code unreachable "reason"} — the point the model says cannot arise (spec §match).
     *
     * <p>It answers no value, so it has no type of its own to check against the position it is
     * written in: it types at {@code Never} and fits whatever is expected. The reason is a literal
     * rather than an expression so that the compiler and a reader both have it without running the
     * model; at run time it is the message the abort carries.
     */
    record Unreachable(String reason, SourcePos pos, Region region) implements Expr {}

    /**
     * {@code x -> expr}, or {@code (acc, x) -> expr} — a block (spec §blocks).
     *
     * <p>Second-class: it may only be an argument, never a value that is returned, stored in a
     * field, or bound by {@code let}. The parser only accepts one in an argument position, and
     * because it cannot escape, the backend inlines it rather than building a closure.
     *
     * <p>{@code rule} is which block of the source this is, minted where the syntax is read and
     * carried by every copy. A block handed to a function parameter is the rule the fork that
     * applies it decides by, and telling two of those apart has to survive the body being spliced —
     * which a position does not, being stamped with the call site wherever the body is one a reader
     * cannot open.
     */
    record Block(List<Binder> params, Expr body, souther.compiler.types.RuleOrigin rule,
                 SourcePos pos, Region region) implements Expr {

        /** A block whose parameters are names no one wrote — what a desugaring builds. A block the
         * author wrote is built from binders of its own, because its parameters are written one by
         * one and the block's own position is where the first of them starts at best. */
        public static Block desugared(List<String> params, Expr body,
                                      souther.compiler.types.RuleOrigin rule, SourcePos pos,
                                      Region region) {
            List<Binder> binders = new ArrayList<>();
            for (String p : params) {
                binders.add(Binder.desugared(p, pos));
            }
            return new Block(binders, body, rule, pos, region);
        }

        /** How the parameters were written, in order. */
        public List<String> paramNames() {
            List<String> names = new ArrayList<>();
            for (Binder p : params) {
                names.add(p.name());
            }
            return names;
        }
    }

    /**
     * {@code let name = value} followed by {@code body} — what a body's {@code let} desugars to
     * (spec §let). Nesting the rest of the body inside keeps {@code value} from being evaluated
     * when an enclosing {@code if} (a desugared {@code guard}) takes the other branch.
     *
     * <p>{@code declaredType} is the binding's declared type, if any, and {@code annotated} says
     * where it came from. A source annotation ({@code let x: T = e}) is pushed into {@code value}
     * as its expected type and checked against it, so a value only context can type — an empty
     * collection — is pinned at the binding. A type supplied by helper inlining (the parameter's
     * declared type, bound to the argument at the call site) is not: the argument's own type and
     * the call-site check already cover it.
     */
    record LetIn(Binder binder, Expr value, RetType declaredType, boolean annotated, Name opens,
                 Expr body, SourcePos pos, Region region) implements Expr {
        /** An ordinary {@code let x = e}: the bound name takes {@code e}'s inferred type. */
        public LetIn(Binder binder, Expr value, Expr body, SourcePos pos, Region region) {
            this(binder, value, null, false, null, body, pos, region);
        }

        /** A binding for a name no one wrote: what a desugaring holds a value in. A binding the
         * source wrote is built from a binder of its own, because a {@code let} statement starts at
         * its keyword and the name it binds is somewhere after it. */
        public LetIn(String name, Expr value, Expr body, SourcePos pos, Region region) {
            this(Binder.desugared(name, pos), value, null, false, null, body, pos, region);
        }

        /** A binding carrying an inlined helper parameter's declared type. */
        public LetIn(Binder binder, Expr value, RetType declaredType, Expr body, SourcePos pos,
                     Region region) {
            this(binder, value, declaredType, false, null, body, pos, region);
        }

        /** {@code let x: T = value} — a binding the source annotated. */
        public static LetIn annotated(Binder binder, Expr value, RetType type, Expr body,
                                      SourcePos pos, Region region) {
            return new LetIn(binder, value, type, true, null, body, pos, region);
        }

        public String name() {
            return binder.name();
        }

        /**
         * One layer of a constructor pattern: the binding holds the whole value and {@code opens}
         * names the newtype the pattern claims it is. A {@code match} arm gets that claim checked by
         * the exhaustiveness pass, which knows the scrutinee's cases; a binding has no such pass
         * behind it, so the name is carried here for the checker to hold against the value's type.
         */
        public static LetIn opening(String name, Expr value, Name opens, Expr body, SourcePos pos,
                                    Region region) {
            return new LetIn(Binder.desugared(name, pos), value, null, false, opens, body, pos,
                    region);
        }

        /** The type the source wrote on this binding, or null when it wrote none. What the source
         * wrote and what a later pass put there are both held in {@code declaredType}, and
         * {@code annotated} is what tells them apart: a carrier from inlining is not an annotation
         * and is not answered here. */
        public RetType annotation() {
            return annotated ? declaredType : null;
        }
    }

    /**
     * A collection written in brackets — {@code [a, b]}, and the empty {@code []}.
     *
     * <p>{@code origin} is which collection of this source it is. Carried rather than worked out
     * below, for the reason every construct's is: a helper holding one is expanded at each call, so
     * one pair of brackets becomes several nodes at one place, and what says those are one
     * collection is that they carry one origin.
     *
     * <p>What the brackets stand for is decided further down — a list in a body, the rows of a
     * table in a fixture, and in either case library operations no source wrote. Each of those
     * operations is a reference derived from this, which is what the identity is here for.
     */
    record ListLit(List<Expr> elements, SourceConstructOrigin origin, SourcePos pos, Region region)
            implements Expr {}

    /** A guard-only comprehension {@code [element | guard, ...]}: the element is included when
     * every guard holds, giving a 0-or-1 element list (spec §stdlib-list, conditional accumulation).
     *
     * <p>{@code origin} names the comprehension, and the fork each guard lowers to is derived from it
     * ({@link SourceConstructOrigin#lowered}) rather than minted where the lowering runs — so a comprehension
     * inside a helper answers the same whichever call site expanded it. */
    record ListComp(Expr element, List<Expr> guards, SourceConstructOrigin origin, SourcePos pos,
                    Region region) implements Expr {}

    /** A tuple {@code (e1, e2, ...)} of two or more values (ADR-0036), an expression-level value
     * that never crosses the data/behavior boundary. Opened with a {@code let (x, y) = t} destructure. */
    record Tuple(List<Expr> elements, SourcePos pos, Region region) implements Expr {}

    /** Reads the {@code index}-th element of a tuple; what a {@code let (x, y) = t} destructure lowers
     * a field read to. Not written in source — the parser produces it from a tuple pattern. {@code arity}
     * is the pattern's name count, so the checker rejects a tuple of a different size (ADR-0036). */
    record TupleGet(Expr tuple, int index, int arity, SourcePos pos, Region region) implements Expr {}

    /** {@code if cond then a else b} — both branches must have the same type (spec §if).
     *
     * <p>{@code origin} is the fork the author wrote, kept through every rewrite and every copy an
     * expansion makes, so the arms of one {@code if} are one obligation however many times a helper
     * holding it is called ({@link SourceConstructOrigin}). */
    record If(Expr cond, Expr then, Expr els, SourceConstructOrigin origin, SourcePos pos, Region region)
            implements Expr {}

    /**
     * {@code if T(v) as x then a else b} — an attempted construction, and what
     * {@code guard T(v) as x else b} desugars to. {@code construct}'s invariant decides the branch:
     * holding, the value is built and {@code binder} names it in {@code then}; failing, {@code els} is
     * taken and no value is built. The binder is scoped to {@code then} alone, which is why this is a
     * node of its own rather than a condition {@code If} would have to introspect — a plain
     * {@code If}'s condition never binds.
     *
     * <p>A construction here does not abort, so it is exempt from the possible-violation warning
     * (spec §invariant-discharge); a violation the compiler *decides* is still reported, because then no branch was
     * ever in question.
     *
     * <p>{@code construct} is an {@link Expr} rather than a {@link NewData} because the newtype
     * spelling {@code T(v)} is a {@link Call} until {@link souther.compiler.check.NewtypeDesugar}
     * rewrites it — the same reason nothing else in the AST can name a construction by type either.
     * That it is one, and that its type carries an invariant to attempt, are checked once the names
     * are resolved.
     */
    record IfConstructed(Expr construct, Binder binder, Expr then, List<ElseArm> els,
                         SourceConstructOrigin origin, SourcePos pos, Region region) implements Expr {

        /** The attempt whose failure is not told apart: one arm, naming no clause. */
        public IfConstructed(Expr construct, Binder binder, Expr then, Expr els,
                             SourceConstructOrigin origin, SourcePos pos, Region region) {
            this(construct, binder, then, List.of(ElseArm.any(els)), origin, pos, region);
        }

        /** How the binding was written. */
        public String binderName() {
            return binder.name();
        }

        /** Whether the failure is departed from per clause, rather than by one value for any of them. */
        public boolean mapsClauses() {
            return els.size() > 1 || els.get(0).clause().isPresent();
        }
    }

    /**
     * One departure of an attempted construction: the value taken when {@code clause} is the invariant
     * clause that did not hold, or when any of them did not ({@code clause} empty — the {@code else e}
     * form, and the {@code | _ -> e} that stands for the clauses carrying no name).
     *
     * <p>The arms are a lookup by clause, not a sequence: which arm is taken is decided by the failing
     * clause, so the order they are written in has no effect. Which clause fails is decided by the
     * order the clauses are declared in (spec §invariant-declaration).
     */
    record ElseArm(Optional<String> clause, Expr body, SourcePos pos) implements Ast {

        /** The arm taken for any failure — what {@code else e} and {@code | _ -> e} both mean. */
        public static ElseArm any(Expr body) {
            return new ElseArm(Optional.empty(), body, body.pos());
        }

    }

    /** {@code match scrutinee { case Case as x -> body ... }} over a sum type. {@code origin} is the
     * fork the author wrote; see {@link If}. */
    record Match(Expr scrutinee, List<Case> cases, SourceConstructOrigin origin, SourcePos pos,
                 Region region) implements Expr {}

    /**
     * One {@code match} case: {@code case A | B ... [as x] -> body} (spec §match). {@code caseTypes}
     * holds one case name, or several joined by {@code |} (an or-pattern, spec §match). With one case,
     * {@code x} binds that case's type; with several, it binds the scrutinee's sum type, since no
     * single case type fits all alternatives.
     *
     * <p>{@code unwrapAsserts} are the inner newtype names written in a constructor-destructuring
     * pattern {@code X(Y(s))} — {@code [Y]} here (the case {@code X} is in {@code caseTypes}, the
     * bound variable {@code s} is dropped). {@code null} when the pattern is not a constructor
     * destructure; an empty list is the single-layer form {@code X(v)}. The {@code TypeChecker}
     * verifies every opened layer is a newtype and that each name matches the layer it opens.
     */
    record Case(List<Name> caseTypes, Binder binding, Expr body, List<Name> unwrapAsserts,
                SourcePos pos) implements Ast {
        public Case(List<Name> caseTypes, Binder binding, Expr body, SourcePos pos) {
            this(caseTypes, binding, body, null, pos);
        }

        /** How the binding was written, or null where the arm binds nothing. */
        public String bindingName() {
            return binding == null ? null : binding.name();
        }
    }

    /**
     * {@code TypeName { ..src, field: expr, ... }} used as an expression (construction in a behavior).
     *
     * <p>A spread names a value like any other position, so it carries what that name resolves to: a
     * binding in force wins over a declaration here as everywhere else, and a reader downstream must
     * not go back to matching the spelling against the module's own definitions.
     *
     * <p>Where the construction came from is not said here. A construction stands somewhere other
     * than where it was written only after a body has been spliced into a reader, and that is
     * {@link Hir.NewData}'s to say: nothing this tree holds arrived from another body.
     */
    record NewData(Name typeName, List<FieldInit> inits, List<Var> spreads, SourcePos pos,
                   Region region) implements Expr {}

    record IntLit(long value, SourcePos pos, Region region) implements Expr {}

    record DecimalLit(java.math.BigDecimal value, SourcePos pos, Region region) implements Expr {}

    /** Unary minus {@code -operand} on an Int or Decimal (spec §an-operator-takes-the-types-it-is-defined-for). */
    record Neg(Expr operand, SourcePos pos, Region region) implements Expr {}

    record StringLit(String value, SourcePos pos, Region region) implements Expr {}

    record BoolLit(boolean value, SourcePos pos, Region region) implements Expr {}

    /**
     * A name used as a value, as the parser read it — the one representation the surface AST has for
     * one. {@code written} is how the source writes it: bare {@code price}, qualified
     * {@code billing.price}, or through an import alias. What it denotes is {@code Resolve}'s to
     * say, and a name it has answered is a {@link Hir.Var}. What {@link Name} is for a type.
     *
     * <p>It stands in two kinds of slot, which differ in what may replace it:
     *
     * <ul>
     *   <li>an expression slot, where any expression may stand — an argument, a field's value, the
     *       thing an {@code if} tests;</li>
     *   <li>a name slot, where only another name may — a construction's spread {@code T { ..base }},
     *       which copies the fields of what the name stands for and so has nothing to evaluate.</li>
     * </ul>
     */
    record Var(WrittenName written, SourceReferenceOrigin origin, Region region) implements Expr {

        // `origin` is which reference of this source it is, and it is here rather than read off the
        // name or the place: two occurrences of one name reach the same declaration and are two
        // references, a pass may respell one, and where a name is written is where a complaint
        // about it belongs. So which occurrence this is is minted where the source is read and
        // carried from there, the way the constructs a source writes already are.
        //
        // Every name in this tree has one. This tree is what reading a source produces, so a name
        // in it is a name a source wrote — the desugarings that run while it is being built write
        // names of their own, and each of those is a reference of the source that made it
        // necessary, counted with the rest. A reader here is told by the type that there is one to
        // read, and a build that forgot one is refused where the name was made rather than
        // wherever it is first asked for. What a name a pass writes downstream carries is
        // `Hir.Var`'s to say, and is not this: no source wrote one of those.

        public Var {
            if (origin == null) {
                throw new IllegalArgumentException("`" + written.canonical()
                        + "` is a name a source wrote, so it is one this source counted");
            }
            if (written.region() != null && region == null) {
                throw new IllegalArgumentException("`" + written.canonical()
                        + "` is written somewhere and the expression it is was written nowhere");
            }
            // The region is the expression's and the name's is `written`'s, and they part company
            // only where the author wrapped the name in something the tree does not keep:
            // `(price)` is one expression written over nine characters and one name written over
            // five. So the one has to hold the other.
            if (!Region.encloses(region, written.region())) {
                throw new IllegalArgumentException("`" + written.canonical()
                        + "` is written outside the expression it is");
            }
        }

        /** A name as the parser read it. */
        public static Var written(String spelling, SourcePos pos, SourceReferenceOrigin origin) {
            return written(WrittenName.of(spelling, pos), origin);
        }

        /** The same, off an occurrence the parser has already read. */
        public static Var written(WrittenName written, SourceReferenceOrigin origin) {
            return new Var(written, origin, written.region());
        }

        /** A read of a name a desugaring minted, at the form it is rewriting. Written nowhere: the
         * characters at {@code anchor} spell whatever the author put there, which is not this. */
        public static Var desugared(String name, SourcePos anchor, SourceReferenceOrigin origin) {
            return written(WrittenName.synthetic(name, anchor), origin);
        }

        /** The bare name this reaches its declaration by, whatever the source spelled. */
        public String name() {
            return written.canonical();
        }

        /** Where the name is written. */
        @Override
        public SourcePos pos() {
            return written.pos();
        }

        /** The same name, over {@code region}. The same reference, so it keeps its own origin. */
        public Var over(Region region) {
            return new Var(written, origin, region);
        }

        @Override
        public String toString() {
            return name();
        }
    }

    /**
     * {@code target.field} — a field taken off a value, or (until {@code Resolve} folds it) a member
     * taken off a namespace. {@code name} is the field's own occurrence, and {@code pos} is where
     * that occurrence is: a report about a field read is about the field, so it is anchored there
     * and not where the read begins. Where it begins is {@code region}'s to say, and the two differ
     * by however the target was written.
     */
    record FieldAccess(Expr target, WrittenName name, SourcePos pos, Region region) implements Expr {

        /** An access a pass wrote: the field is named but written nowhere, so there is nothing for a
         * report to underline. */
        public FieldAccess(Expr target, String field, SourcePos pos) {
            this(target, WrittenName.synthetic(field, pos), pos, null);
        }

        /**
         * An access standing where the author wrote something else — a copy of a body from another
         * source, stamped at the place it was carried to.
         *
         * <p>The field is named and written nowhere, the occurrence it was read from being in the
         * file this copy is no longer being read against. The characters are still somebody's, and
         * they are whatever stands at {@code region} in the file this is read in. The counterpart of
         * {@link Hir.Var#respelled}, for the same reason.
         */
        public static FieldAccess restamped(Expr target, String field, SourcePos at, Region over) {
            return new FieldAccess(target, WrittenName.synthetic(field, at), at, over);
        }

        /** The field this reads. */
        public String field() {
            return name.canonical();
        }
    }

    /**
     * A function applied to arguments. {@code function} is the thing being applied, and it is an
     * expression like any other: what is applied is what it answers, not how the application was
     * written.
     *
     * <p>Applying a name is the common case and has its own constructors and readers below. A name
     * carries what it denotes — a helper, a library function, an injected behavior, a function-typed
     * binding, or the type a newtype construction wraps — answered once during resolution.
     *
     * <p>{@code origin} is which application of this source it is, carried for the reason a
     * comparison's is: a non-recursive helper is expanded at each call, so one application the
     * author wrote becomes several in the tree that runs, and a rule read off one of them is the
     * same rule wherever it is met. What the name turns out to denote is not known here and is no
     * part of it — every application the source writes takes one, and which of them state a rule is
     * settled where names are resolved.
     */
    record Apply(Expr function, List<Expr> args, SourceConstructOrigin origin, SourcePos pos,
                 Region region) implements Expr {

        /** The same application over rewritten arguments — a pass that touches only the arguments
         *  says so here rather than listing the slots it is not changing. */
        public Apply withArgs(List<Expr> args) {
            return new Apply(function, args, origin, pos, region);
        }
    }

    /** {@code origin} is where the comparison was written, which is not always where the fork
     * testing it was: a condition can be an application of a function parameter, and the predicate
     * handed to it is the caller's. Carried so that two predicates written separately stay two lines
     * and one predicate applied twice stays one ({@link SourceConstructOrigin}). */
    record Binary(BinOp op, Expr left, Expr right, SourceConstructOrigin origin, SourcePos pos,
                  Region region) implements Expr {}


    enum BinOp { EQ, NE, LT, LE, GT, GE, AND, OR, ADD, SUB, MUL, DIV, CONCAT }

    /**
     * {@code e} written over {@code region} instead of whatever it says now — for the one caller
     * that knows a wider stretch of source than the node it is holding.
     *
     * <p>A form the parser reduces away is still characters in the file. {@code (a + 100)} leaves an
     * {@code Ast.Binary} because the parentheses say nothing the tree needs to keep, and they are
     * nine characters the author wrote as that argument all the same. A report that underlined seven
     * of them would be pointing at an expression the reader has to work out is the one it means.
     *
     * <p>The reduction is the frontend's and so is this: nowhere downstream is there anything left
     * saying the parentheses were ever there.
     */
    public static Expr withRegion(Expr e, Region region) {
        return switch (e) {
            case IntLit x -> new IntLit(x.value(), x.pos(), region);
            case DecimalLit x -> new DecimalLit(x.value(), x.pos(), region);
            case StringLit x -> new StringLit(x.value(), x.pos(), region);
            case BoolLit x -> new BoolLit(x.value(), x.pos(), region);
            case Var x -> x.over(region);
            case Unreachable x -> new Unreachable(x.reason(), x.pos(), region);
            case Neg x -> new Neg(x.operand(), x.pos(), region);
            case FieldAccess x -> new FieldAccess(x.target(), x.name(), x.pos(), region);
            case Binary x -> new Binary(x.op(), x.left(), x.right(), x.origin(), x.pos(), region);
            case Apply x -> new Apply(x.function(), x.args(), x.origin(), x.pos(), region);
            case If x -> new If(x.cond(), x.then(), x.els(), x.origin(), x.pos(), region);
            case IfConstructed x ->
                    new IfConstructed(x.construct(), x.binder(), x.then(), x.els(), x.origin(), x.pos(),
                            region);
            case LetIn x -> new LetIn(x.binder(), x.value(), x.declaredType(), x.annotated(),
                    x.opens(), x.body(), x.pos(), region);
            case Block x -> new Block(x.params(), x.body(), x.rule(), x.pos(), region);
            case ListLit x -> new ListLit(x.elements(), x.origin(), x.pos(), region);
            case ListComp x -> new ListComp(x.element(), x.guards(), x.origin(), x.pos(), region);
            case Tuple x -> new Tuple(x.elements(), x.pos(), region);
            case TupleGet x -> new TupleGet(x.tuple(), x.index(), x.arity(), x.pos(), region);
            case NewData x ->
                    new NewData(x.typeName(), x.inits(), x.spreads(), x.pos(), region);
            case Match x -> new Match(x.scrutinee(), x.cases(), x.origin(), x.pos(), region);
        };
    }

    /**
     * Applies {@code f} to each direct child of {@code e} (a leaf has none). A visiting pass (a
     * checker walk) delegates its default recursion here rather than hand-copying every node type.
     *
     * <p>A name a spread holds is a child like any other, so a pass that asks what an expression
     * names reaches one without knowing that spreads exist.
     *
     * <p>This is the one place that says which children a node has, and all it does is read them.
     * No pass below the frontend rewrites an expression of the parsed tree into another, so there
     * is no walk here that rebuilds one: what the frontend itself reduces as it reads — the
     * parentheses {@link #withRegion} takes off — it reduces where it reads it. Being exhaustive
     * over {@code Expr} with no {@code default}, a node kind added
     * later stops the build here, which is the one place it has to be accounted for — and a leaf
     * says it is one by having an arm with nothing to visit rather than by falling through.
     */
    public static void forEachChild(Expr e, java.util.function.Consumer<Expr> f) {
        switch (e) {
            case IntLit _ -> { }
            case DecimalLit _ -> { }
            case StringLit _ -> { }
            case BoolLit _ -> { }
            case Var _ -> { }
            case Unreachable _ -> { }
            case Neg n -> f.accept(n.operand());
            case FieldAccess fa -> f.accept(fa.target());
            case Binary b -> {
                f.accept(b.left());
                f.accept(b.right());
            }
            case Apply a -> {
                f.accept(a.function());
                a.args().forEach(f);
            }
            case If iff -> {
                f.accept(iff.cond());
                f.accept(iff.then());
                f.accept(iff.els());
            }
            case IfConstructed ic -> {
                f.accept(ic.construct());
                f.accept(ic.then());
                for (ElseArm arm : ic.els()) {
                    f.accept(arm.body());
                }
            }
            case LetIn li -> {
                f.accept(li.value());
                f.accept(li.body());
            }
            case Block bl -> f.accept(bl.body());
            case ListLit l -> l.elements().forEach(f);
            case ListComp comp -> {
                f.accept(comp.element());
                comp.guards().forEach(f);
            }
            case Tuple tup -> tup.elements().forEach(f);
            case TupleGet tg -> f.accept(tg.tuple());
            case NewData nd -> {
                // the spreads first: `..base` is written before the fields that replace what it
                // brought, and a walk that records the first thing it sees should see them that way
                nd.spreads().forEach(f);
                for (FieldInit i : nd.inits()) {
                    f.accept(i.value());
                }
            }
            case Match m -> {
                f.accept(m.scrutinee());
                for (Case c : m.cases()) {
                    f.accept(c.body());
                }
            }
        }
    }

}
