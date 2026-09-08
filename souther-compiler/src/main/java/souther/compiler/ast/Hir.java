package souther.compiler.ast;

import souther.compiler.types.BinOp;
import souther.compiler.diag.Region;
import souther.compiler.observe.RowIdentity;
import souther.compiler.diag.SourcePos;
import souther.compiler.types.BindingId;
import souther.compiler.types.BindingOwner;
import souther.compiler.types.ExpansionSite;
import souther.compiler.types.MapKeyRepresentation;
import souther.compiler.types.LeafScalar;
import souther.compiler.types.SourceConstructOrigin;
import souther.compiler.types.ReachName;
import souther.compiler.types.ApplicationOrigin;
import souther.compiler.types.ReferenceOrigin;
import souther.compiler.types.Type;
import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbol;
import souther.compiler.types.TypeReachName;
import souther.compiler.types.ValueName;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * The representation {@code Resolve} answers with: a module in which every reference occurrence has
 * been read. {@link Ast} is what the source said and what the frontend rewrote it into; this is what
 * resolution worked out about it, and the two are separate representations rather than two states of
 * one, so a pass below {@code Resolve} cannot be handed a name nothing has looked at.
 *
 * <p>Answered is not the same as denoting. A name nothing declares is {@link Name.Unanswered} here —
 * read, reported where it is written, and carrying no declaration — and the checks around it go on
 * running. That is a distinction about meaning and it belongs in this representation; whether an
 * occurrence has been read at all is a distinction about representation, and it is the boundary
 * between {@link Ast} and this rather than a variant inside either.
 *
 * <p>{@code Resolve} is the only thing that builds one of these out of an {@link Ast}. Every other
 * producer already knows what its names mean and says so.
 */
public interface Hir {

    /** The source position of this node. Every record below provides it. */
    SourcePos pos();

    /**
     * A node an author wrote, which therefore has a stretch of source as well as an anchor.
     *
     * <p>Here rather than on each kind of node that has one, because it is one rule: what a report
     * about a node points at is either the characters it was written over or the point it is
     * anchored at, and a second node kind working that out for itself would be a second answer to
     * settle against this one.
     *
     * <p>What this does not answer is whether the region may be quoted. A node read back out of a
     * published module was written somewhere the compile reading it has no source for, and its
     * region says a line and a column of a text no reader holds. That is a question about the
     * compile and not about the node, and it is asked of the region rather than here.
     */
    interface Written extends Hir {

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
     * binding. Every node below that introduces one holds this rather than a bare spelling, so what a
     * name under it means is the binding and not the text.
     *
     * <p>One form, carrying the {@link BindingId} every name that resolves to it also carries. A
     * binder with no identity is {@link Ast.Binder}, which is the other representation and not a
     * state of this one, so nothing here tests for a missing identity and no reader has to ask
     * whether the binding has been answered.
     */
    record Binder(WrittenName written, BindingId binding, SourcePos pos) implements Hir {

        public Binder {
            if (binding == null) {
                throw new IllegalArgumentException("a bound binder is a binding: " + written);
            }
        }

        /** What the binding is called. A generated local is named after it; a diagnostic quotes
         * {@link WrittenName#quoted()} instead, which is what the author typed. */
        public String name() {
            return written.canonical();
        }

        /**
         * Where the author wrote this name, or null where the author wrote no name at all.
         *
         * <p>Not the same as {@link #pos()}, which is the form the binding came from: a {@code let}
         * statement starts at its keyword, a lambda's parameters share the lambda's start, and a
         * name a desugaring invented — the parameter {@code .field} becomes, the value a
         * {@code match} is held in — is written nowhere and only anchored somewhere.
         *
         * <p>A reader asking what is under a cursor has this and nothing else to compare against.
         * Anchoring one of those invented names at a form the author did write would put it under a
         * cursor on that form, and rename would then rewrite the source with a name no one can see.
         */
        public SourcePos namePos() {
            return written.authored() ? written.pos() : null;
        }

        /** The binding, for a reader that is asking which one this is. */
        public BindingId id() {
            return binding;
        }

        @Override
        public String toString() {
            return name();
        }
    }

    /**
     * Mints the binders a pass writes into a body that has already been resolved.
     *
     * <p>Nothing runs after such a pass to answer a binder it left unanswered, and this
     * representation has no form for one. So it says which binding each is, and the bindings it
     * makes belong to it — to {@code owner} — rather than to the definition whose text it is
     * writing into, which is what keeps two of them apart when one body is written into twice.
     */
    final class Binders {

        private final BindingOwner owner;
        private int next;

        public Binders(BindingOwner owner) {
            this.owner = owner;
        }

        /** What the bindings this mints belong to. */
        public BindingOwner owner() {
            return owner;
        }

        /** A binding nothing else has, under this pass's owner. A pass writes its own names, so
         * none of them is a name the author wrote. */
        public Binder binder(String name, SourcePos pos) {
            return new Binder(WrittenName.synthetic(name, pos),
                    new BindingId(owner, next++), pos);
        }
    }

    /**
     * A name that denotes a declared type, in both forms it has: {@code name} as the source writes
     * it — bare {@code 金額}, qualified {@code billing.金額}, or through an import alias — and
     * {@code denotes} as it resolves. Resolution happens once, in {@code Resolve}, before any check
     * runs; every name-bearing position in this tree carries one of these, so no later pass decides
     * for itself what a spelling means or whether a qualified one is allowed here (issue #177).
     *
     * <p>A check reads {@link Denoting#type()}, which every name the pass answered carries. A name
     * nothing declares is reported where it is written and is {@link Unanswered} from there on,
     * which the check over its declaration is what settles ({@code Names.Unbuilt}): the pass does
     * not stop, so an author is told about every unknown name at once and the definitions beside it
     * are still checked. {@link #name()} is the name, and is not what a report quotes: a bare and
     * a qualified spelling of one type are one name, and a decomposed and a composed spelling are
     * one name too, so neither says what the author typed. A report asks {@link WrittenName#quoted()}
     * and underlines {@link WrittenName#region()}.
     *
     * <p>The two forms are the two answers resolution has, and neither of them is "nobody has
     * looked". That one is {@link Ast.Name}, which is the other representation.
     */
    sealed interface Name extends Hir permits Name.Denoting, Name.Unanswered {

        /** The name, and the occurrence of it that was read. */
        WrittenName name();

        /** A name a pass synthesized, already knowing what it denotes. It is written nowhere;
         * {@code pos} is what a complaint about it points at. The spelling is the declaration's own,
         * which is what a reference internal to a module reaches it by. */
        static Name resolved(TypeSymbol denotes, SourcePos pos) {
            return new Name.Denoting(WrittenName.synthetic(denotes.name(), pos), denotes);
        }

        /**
         * The same, spelled as the module this is being written into reaches it.
         *
         * <p>For a pass whose output a person reads back: the declaration's own name is not what
         * every module writes, so a name synthesized with it quotes a spelling the reader cannot
         * write. What it denotes is the same either way.
         */
        static Name reached(TypeReachName.Written type, SourcePos pos) {
            return new Name.Denoting(WrittenName.synthetic(type.rendered(), pos), type.denotes());
        }

        /** The bare name this reaches its declaration by, whatever the source spelled. */
        default String written() {
            return name().canonical();
        }

        /** Where the name is written, or where a synthesized one is anchored. */
        @Override
        default SourcePos pos() {
            return name().pos();
        }

        /**
         * This name where it names a declaration, and null where resolution read it and found none.
         *
         * <p>The one way from here to the declaration. What names it — {@link Denoting#type()} — is
         * the answered form's, so a reader arrives at it through this projection or through
         * narrowing to the two forms, and says what it does with an {@link Unanswered} name where it
         * makes that choice.
         */
        default Name.Denoting answered() {
            return this instanceof Name.Denoting denoting ? denoting : null;
        }

        /** The same name, read and found to name nothing. */
        default Name unanswered() {
            return new Name.Unanswered(name());
        }

        /**
         * A name resolution answered, and the declaration it names.
         *
         * <p>The declaration is one that is there. A name nothing declares is {@link Unanswered},
         * which is a different type — so "this has been resolved" and "this names something" are
         * not two readings of one value, which is what a stand-in identity made them.
         */
        record Denoting(WrittenName name, TypeSymbol type) implements Name {

            public Denoting {
                if (type == null) {
                    throw new IllegalArgumentException("a name that denotes names a declaration: "
                            + name);
                }
            }

            @Override
            public String toString() {
                return written();
            }
        }

        /**
         * A name resolution read and found no declaration for.
         *
         * <p>Reported where it is written, and the declaration it is in has no meaning to work out
         * — which {@code Names.Unbuilt} is what settles, so the rest of the module goes on being
         * answered. This is an answer. A name nothing has looked at is {@link Ast.Name}, and the
         * two were one value for as long as a missing identity stood for both.
         *
         * <p>What a reader does on meeting one is the reader's to say, and the readers here say one
         * of two things. A walk that recovers — reads no declaration, states no type, builds
         * nothing — passes it over: what is wrong is reported already. A reader whose input is
         * built from answered names only says so with {@link #unexpectedHere()}, naming the
         * construction that makes it so. The order the passes run in makes nothing so: a
         * compilation goes on answering after an error, so only a producer that leaves them out can
         * be named.
         */
        record Unanswered(WrittenName name) implements Name {

            /**
             * The claim that this name is somewhere it could not have reached.
             *
             * <p>Not a reading: there is nothing here to read, and what a reader arriving here is
             * saying is that its input was built from answered names. The caller throws it, and
             * names beside the throw the construction it is standing on.
             */
            public IllegalStateException unexpectedHere() {
                return new IllegalStateException("`" + written() + "` at " + pos()
                        + " denotes nothing and was read as though it did");
            }

            @Override
            public String toString() {
                return written();
            }
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
     * definition: a recursion an expansion of this module could not remove, which has to be lowered
     * somewhere because expanding it would not terminate, and a definition minted for what a row
     * writes at a position, which has no call site to be inlined into. The first may be declared by
     * the standard library or by a module that published it; the second is this module's own and is
     * no {@code let}. Only {@code fns} is declared here, and no rule reads a name to tell any of
     * them apart — {@code List.foldFrom} is reached under the library's alias and declared in
     * {@code souther.list}, and a row's method is spelled in no source at all ({@link FnDef#role}).
     *
     * <p>{@code takenOn} is written where the module is lowered and nowhere else, and is empty at
     * every stage before that. What a module emits follows from expanding it, so it is not knowable
     * earlier: it once carried a prediction, worked out by walking the places a module writes
     * expressions before any of them had been expanded, and a tree that walk did not know to look at
     * contributed nothing — which is how a rule reaching a quantifier came to be compiled without
     * the fold it became.
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
                  SourcePos pos) implements Hir {

        /**
         * This module with {@code replacement} standing where its declarations were.
         *
         * <p>Written here rather than at each rewrite. {@code fns} and {@code takenOn} are both
         * {@code List<FnDef>}, so a rebuild that lists the components out has two slots the compiler
         * cannot tell apart — and the mistake is a module that emits the helpers it wrote and writes
         * the ones it took on.
         */
        public Module withDefs(List<Def> replacement) {
            return new Module(name, exposing, exposedOutputs, imports, replacement, behaviors, fns,
                    takenOn, examples, fakes, exampleFileTarget, pos);
        }

        /** This module with its behavior declarations replaced. */
        public Module withBehaviors(List<BehaviorDef> replacement) {
            return new Module(name, exposing, exposedOutputs, imports, defs, replacement, fns,
                    takenOn, examples, fakes, exampleFileTarget, pos);
        }

        /** This module with {@code replacement} standing where its definitions were. */
        public Module withFns(List<FnDef> replacement) {
            return new Module(name, exposing, exposedOutputs, imports, defs, behaviors, replacement,
                    takenOn, examples, fakes, exampleFileTarget, pos);
        }

        /** This module with {@code replacement} standing where what it took on was. */
        public Module withTakenOn(List<FnDef> replacement) {
            return new Module(name, exposing, exposedOutputs, imports, defs, behaviors, fns,
                    replacement, examples, fakes, exampleFileTarget, pos);
        }

        /** This module with {@code replacement} standing where its example blocks were. */
        public Module withExamples(List<Example> replacement) {
            return new Module(name, exposing, exposedOutputs, imports, defs, behaviors, fns,
                    takenOn, replacement, fakes, exampleFileTarget, pos);
        }

        /** This module with {@code replacement} standing where its fake tables were. */
        public Module withFakes(List<Fake> replacement) {
            return new Module(name, exposing, exposedOutputs, imports, defs, behaviors, fns,
                    takenOn, examples, replacement, exampleFileTarget, pos);
        }
    }

    /**
     * {@code fake <injected> | (in) -> out | ...} — a test double for an injected behavior, used to
     * evaluate an example of a behavior that {@code depends on} it. The rows form an input→output
     * table matched by value equality; a {@code _ -> out} row is the default when no input matches
     * (otherwise a miss is an error). A fake produces no run-time class (it is a proxy at evaluation).
     *
     * <p>The target is the behavior resolution answered, and every reader below asks it which
     * behavior this stands in for rather than reading the spelling again. A dependency another
     * module declares is one of the behaviors that can be named here, and the module it came from is
     * carried rather than being worked out from where the fake happens to be written.
     */
    record Fake(Var target, List<FakeRow> rows, SourcePos pos) implements Hir {

        /** Which behavior this stands in for, or null where the name denoted none. */
        public ValueName.Behavior standsInFor() {
            return behaviorOf(target);
        }
    }

    /** One fake row: what it answers for, and what it answers with. */
    record FakeRow(Matched matched, Expr output, SourcePos pos) implements Hir {}

    /**
     * What a fake's row answers for.
     *
     * <p>Two states and the source says which. A row written {@code (a, b) -> out} answers for those
     * arguments and for no others; a row written {@code _ -> out} answers for anything, and there are
     * no arguments written in it to read.
     *
     * <p>Not a list of arguments that may be absent. A walk over what the source writes has to be
     * told which of the two a row is before it can ask for arguments, and an absence it is free to
     * walk into is one a walk reads as a row that names none — which is a different row, and one no
     * author can write.
     */
    sealed interface Matched permits Matched.Arguments, Matched.Anything {

        /**
         * The same, with the arguments rewritten where there are any to rewrite.
         *
         * <p>For a pass that rewrites what a row answers for without changing whether it names
         * anything. A caller taking the list out and building a {@code Matched} back around it would
         * be deciding a second time which of the two this is.
         */
        default Matched map(UnaryOperator<Expr> rewrite) {
            return this instanceof Arguments(List<Expr> written)
                    ? new Arguments(written.stream().map(rewrite).toList()) : this;
        }

        /** The row names the arguments it answers for, and these are they. */
        record Arguments(List<Expr> inputs) implements Matched {

            public Arguments {
                inputs = List.copyOf(inputs);
            }
        }

        /** The row answers for anything: {@code _ -> out}. */
        record Anything() implements Matched {}
    }

    /** {@code with <dep> = <value>} on an example row — a value fake for an injected dependency
     * (a zero-argument behavior whose faked result is a constant). The dependency is named as a
     * {@link Fake}'s target is. */
    record With(Var dep, Expr value, SourcePos pos) implements Hir {

        /** Which behavior this stands in for, or null where the name denoted none. */
        public ValueName.Behavior standsInFor() {
            return behaviorOf(dep);
        }
    }

    /** The behavior a resolved name denotes, or null where it denoted something else or nothing. */
    private static ValueName.Behavior behaviorOf(Var named) {
        return named instanceof Var.Denoting denoting
                && denoting.denotes() instanceof ValueName.Behavior behavior ? behavior : null;
    }

    /**
     * {@code example <target> | row ...} — compile-time-checked examples for a behavior or a pure
     * helper. Whether written inline in the module or in an attached {@code examples for}
     * file, examples end up on {@link Module#examples()} (the compiler merges an attached file into
     * its target module). {@code exampleFileTarget} on a {@link Module} is non-null exactly when the
     * module was parsed from an {@code examples for <module>} file: it names the target and marks the
     * module as an example-only contribution, not a module of its own.
     */
    record Example(String target, List<ExampleRow> rows, SourcePos pos) implements Hir {}

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

        /**
         * The same, with the answer rewritten where there is one to rewrite.
         *
         * <p>For a pass that rewrites what a row says without changing whether it says anything. A
         * caller taking the expression out and building an {@code Expected} back around it would be
         * deciding a second time which of the three this is, and a row whose answer is owed would
         * come back from such a pass as one whose answer nothing could read.
         */
        default Expected map(UnaryOperator<Expr> rewrite) {
            return this instanceof Asserted(Expr e) ? new Asserted(rewrite.apply(e)) : this;
        }

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
                      SourcePos pos) implements Hir {}

    /**
     * {@code import a.b as B ( X, Y )} — an explicit, non-wildcard import (spec §modules).
     * {@code importedNames} are the names this import brings into scope bare; {@code alias} is the
     * qualifier the module is read under here, or null. Both parts are optional: a type is reachable
     * qualified whether or not it was imported (spec §modules), so an import with neither is just the
     * dependency written down.
     */
    record Import(String module, String alias, List<ImportedName> importedNames, SourcePos pos)
            implements Hir {

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
    record ImportedName(WrittenName written) implements Hir {

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
    sealed interface BehaviorDef extends Hir permits SpecBehavior, PipeBehavior {

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

    /** A behavior postcondition. Positions belong to the clause/arm, not merely the expression, so
     * diagnostics and rewrites keep the name and the output cases the author addressed. */
    record EnsuresClause(Optional<String> name, List<EnsuresArm> arms, SourcePos pos, Region region)
            implements Written {}

    record EnsuresArm(List<Name> cases, Expr expr, SourcePos pos, Region region) implements Written {
        public EnsuresArm with(Expr rewritten) {
            return new EnsuresArm(cases, rewritten, pos, region);
        }
    }

    /** A behavior parameter. Its type may be an anonymous union of cases (spec §unmarked-output). */
    record Param(WrittenName written, RetType type) implements Hir {

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
     * {@code declaredIn} answers: a wrapper minted for a row's operand is declared by this module
     * and is not a {@code let} anybody wrote. Both are here because both are things a rule asks and
     * neither can be read off a name — {@link #reachedAs} mints a name for a definition another
     * module wrote, so a rule that went by whether a name was authored answered the same for the
     * two. See {@link DefinitionRole}.
     */
    record FnDef(WrittenName written, String declaredIn, List<FnParam> params,
                 RetType declaredReturn, FnBody body, Modifiers modifiers, DefinitionRole role,
                 SourcePos pos)
            implements Hir {

        public FnDef {
            // Said rather than defaulted. What this definition is cannot be worked out from
            // anything else here — a wrapper minted for a row's position and a definition another
            // module wrote are both unspelled and both declared somewhere — so a rewrite that
            // dropped it would be answering the question by losing it, which is the shape this
            // component exists to remove. The forms below are where `Ordinary` is written.
            Objects.requireNonNull(role, "a definition says what it was made as");
        }

        /** A fn read as a definition, which is every one a source wrote. */
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
         * The same declaration as a module that reaches it by {@code reference} holds it.
         *
         * <p>The one way a declaration is renamed. A module emits a recursive helper it reaches as
         * one of its own methods, and the address it holds it under is what the reference renders
         * as ({@code List.foldFrom}, {@code maths.spin}). That address is not where the declaration
         * came from — {@code List.foldFrom} is reached under the library's alias and declared in
         * {@code souther.list} — so the reference is carried on the role rather than left to be
         * read back out of the rendering. Renaming carries {@link #declaredIn} across as well, so
         * no caller is in a position to pair an address with an origin that is not its own.
         *
         * <p>What comes back is a definition this module took on, and it says so. A row's value is
         * its module's own and is never reached from anywhere, so nothing renamed here was one.
         */
        public FnDef reachedAs(ReachName.Declaration reference) {
            return new FnDef(WrittenName.synthetic(reference.rendered(), pos), declaredIn, params,
                    declaredReturn, body, modifiers, new DefinitionRole.TakenOn(reference), pos);
        }

        /** The reference this module reaches what it took on by, or null where the definition is
         *  its module's own. Which declaration a taken-on definition is a copy of is this and not
         *  {@link #address}, which is only where the module holds it. */
        public ReachName.Declaration takenOnAs() {
            return role instanceof DefinitionRole.TakenOn(ReachName.Declaration reference)
                    ? reference : null;
        }

        /** The same declaration with {@code replacement} in place of its body. */
        public FnDef withBody(FnBody replacement) {
            return new FnDef(written, declaredIn, params, declaredReturn, replacement, modifiers,
                    role, pos);
        }

        /**
         * The position this definition stands at, or null where it stands at none.
         *
         * <p>The question every rule written for a row's operand is really asking. It reads the
         * role rather than the shape of a name, which answered the same for a wrapper minted here
         * and for a definition another module wrote.
         */
        public RowPosition standsAt() {
            return role instanceof DefinitionRole.RowValue(RowPosition at) ? at : null;
        }

        /** What the fn is called — the text of {@link #address}, for a report to quote and for a
         *  method name to be built from. Never a key: what a definition is filed under is
         *  {@link #address}, and what it is a definition of is {@link #declaredIn} and its role. */
        public String name() {
            return written.canonical();
        }

        /**
         * Where this definition sits among the ones its module holds: the address its body is asked
         * for by, and the name a method is emitted under.
         *
         * <p>Not what it is a definition of. A module holds another module's declaration under the
         * name it reaches it by, so the address and the declaration come apart exactly where a
         * reader joining a call to a body goes wrong — what it is a copy of is
         * {@link DefinitionRole.TakenOn#reachedAs}.
         */
        public DefinitionName address() {
            return DefinitionName.of(this);
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
    record FnParam(Binder binder, RetType type, boolean typeFromPattern) implements Hir {
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
    sealed interface TypeTerm extends Hir permits TypeRef, FnType {}

    /** A function type {@code (A, ...) -> B}. Its parameters and result are whole types, so a
     * function may take one and may return one. */
    record FnType(List<RetType> params, RetType result, SourcePos pos) implements TypeTerm {}

    /** A written type: one term, or the unmarked sum of several (spec §unmarked-output). */
    record RetType(List<TypeTerm> cases, SourcePos pos) implements Hir {

        /** The function type this stands for, or null when it is not a lone function type. A sum of
         * a function with anything else is not one, and has no case to be told apart by. */
        public FnType asFn() {
            return cases.size() == 1 && cases.get(0) instanceof FnType fn ? fn : null;
        }
    }

    /** A top-level data definition: product, sum, or unit. */
    sealed interface Def extends Hir permits Data, SumData, UnitData {

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
        default String declaredIn() {
            return declares().module();
        }

        /**
         * Which declaration this is, as the compiler's own reasoning names it.
         *
         * <p>Carried, not worked out. The identity was minted where this representation was built
         * ({@code DeclaredIdentity}), which is the one place that has the declaration and the module
         * that wrote it together; a reader that assembled the pair again would be answering for a
         * declaration here whatever the name came from, which is what issues #464, #696 and #700
         * each were.
         */
        TypeSymbol.AtModule declares();

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
                TypeSymbol.AtModule declares,
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
     *
     * <p>{@code pos} and {@code region} are where the author wrote the clause, and they are the
     * clause's own rather than its expression's. {@code expr} covers the condition and stops there:
     * a clause written {@code invariant even = isEven(value)} has an expression over
     * {@code isEven(value)}, and the name the author gave the clause — which is what a diagnostic
     * about the clause says — is outside it. A report that named {@code even} and underlined the
     * condition would leave the reader to work out that the two are the same clause.
     *
     * <p>{@link #with} carries both across a rewrite, so what a clause is addressed by does not
     * change with the representation it is being read in.
     */
    record InvariantClause(Optional<String> name, Expr expr, SourcePos pos, Region region)
            implements Written {

        /** A clause no name was written for. */
        public static InvariantClause unnamed(Expr expr) {
            return new InvariantClause(Optional.empty(), expr, expr.pos(), expr.region());
        }

        /** The same clause over a rewritten expression — what a stage that rewrites expressions
         * produces, so a rewrite never drops the name the rest of the compiler classifies by, nor
         * where the author wrote the clause it rewrote. */
        public InvariantClause with(Expr rewritten) {
            return new InvariantClause(name, rewritten, pos, region);
        }
    }

    /**
     * A sum data definition {@code data X = A | B | ...}.
     *
     * <p>Holds no boundary representation. What the cases are called as they cross, whether they
     * cross as a bare tag or under a key, and which key that is, are derived from the declaration
     * and are answered by {@code check.Boundary} wherever they are wanted (ADR-0004). Held here they
     * were a second answer beside the one a reader works out, and a behavior's output union — which
     * has no declaration to hold a field — could not be given one at all, which is what made it a
     * separate path (#994).
     */
    record SumData(WrittenName written,
                   TypeSymbol.AtModule declares,
                   List<Name> cases,
                   SourcePos pos) implements Def {}

    /** A unit data definition {@code data U} with no fields. */
    record UnitData(WrittenName written, TypeSymbol.AtModule declares, SourcePos pos) implements Def {}


    /** A field: a role name and its type. */
    record Field(WrittenName written, TypeTerm type) implements Hir {

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
     * A named type reference, optionally with one type argument (e.g. {@code List<T>}). When
     * {@code name} is null and {@code tupleElems} is non-null the ref is a tuple type
     * {@code (A, B, ...)} (ADR-0036), written only in a helper/stdlib signature. A {@code Map<K, V>}
     * reuses {@code tupleElems} to carry its key type (a single element) while {@code name} is
     * {@code "Map"} and {@code arg} is the value type (ADR-0040).
     *
     * <p>One form, carrying the type it stands for. A reference that has not been read is
     * {@link Ast.TypeRef}, which is the other representation and not a state of this one, so nothing
     * here tests for a missing type.
     *
     * <p>The type is decided once (issue #177), which is what keeps a later reader from resolving a
     * written type a second time — and from having to know which module the reference was written
     * in, which is what a second resolution needed to get right.
     */
    record TypeRef(WrittenName written, TypeTerm arg, List<TypeTerm> tupleElems, Type type,
                   SourcePos anchor) implements TypeTerm {

        public TypeRef {
            if (type == null) {
                throw new IllegalArgumentException("a reference that denotes is a type: " + written);
            }
        }

        /**
         * A reference nobody wrote: it carries what it denotes and no surface text. A helper
         * parameter whose type its body settles is written back as one (issue #178) — the type is
         * decided, and there is no source it stands for. Every reader here asks {@link #denotes()},
         * so a reference with a decided type is as good as one read off characters.
         */
        public static TypeRef of(Type type, SourcePos pos) {
            return new TypeRef(null, null, null, type, pos);
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

        /** The type this stands for. */
        public Type denotes() {
            return type;
        }

        /** The same reference, standing for {@code type} instead. */
        public TypeRef denoting(Type type) {
            return new TypeRef(written, arg, tupleElems, type, anchor);
        }

        @Override
        public String toString() {
            return name() == null ? String.valueOf(type) : name();
        }
    }

    /** The kind of primitive Raw a single-value decoder reads / an encoder writes. */
    enum RawKind { TEXT, INT, BOOL, DECIMAL, DATE, TIME, DATETIME, INSTANT }

    // --- decoders ---

    sealed interface DecoderDef extends Hir permits PrimDecoder, ObjectDecoder, NewtypeDecoder {}

    /** {@code decoder from Text|Int as <input> { <stmts> <construct> }} (single value). */
    record PrimDecoder(RawKind from,
                       Binder input,
                       List<DecStmt> stmts,
                       Construct result,
                       SourcePos pos) implements DecoderDef {

        public String inputName() {
            return input.name();
        }
    }

    /** {@code decoder from Object { <binds> <construct> }} (multi-field, accumulating). */
    record ObjectDecoder(List<Bind> binds, Construct result, SourcePos pos) implements DecoderDef {}

    /**
     * A newtype {@code data X = Y} over a non-primitive {@code Y} (spec §newtype): the whole input is
     * decoded by {@code inner} (a reference to {@code Y}'s decoder), and the result wrapped in
     * {@code X}. {@code X}'s external representation is {@code Y}'s — an object or a discriminated
     * sum, not {@code {value: ...}}.
     */
    record NewtypeDecoder(DecRef inner, Binder input, Construct result, SourcePos pos)
            implements DecoderDef {

        public String inputName() {
            return input.name();
        }
    }

    /** One field an object decoder reads: the key it is found under, how the value there is read,
     *  and the name the construction below refers to it by. Built by {@code Deriver}; no source
     *  writes one. */
    record Bind(Binder binder, String key, DecRef ref, SourcePos pos) implements Hir {

        public String name() {
            return binder.name();
        }
    }

    /** The decoder referenced by a bind: a primitive, another data's {@code .decoder}, or a list. */
    sealed interface DecRef extends Hir permits DecRef.Bare, OptionDecRef {

        /** A reference that is not an optional. What the distinction is for is the one position that
         *  requires it: what an optional decodes. Absence has one form wherever it stands, so an
         *  optional under an optional has two forms for three values and is not written
         *  (spec {@code [#what-has-no-external-representation]}). */
        sealed interface Bare extends DecRef
                permits PrimDecRef, DataDecRef, ListDecRef, SetDecRef, MapDecRef {}
    }

    record PrimDecRef(LeafScalar kind, SourcePos pos) implements DecRef.Bare {}

    record DataDecRef(Name typeName, SourcePos pos) implements DecRef.Bare {}

    /** {@code list(<elementDecRef>)} */
    record ListDecRef(DecRef element, SourcePos pos) implements DecRef.Bare {}

    /** {@code set(<elementDecRef>)} — a list decoder deduplicated into a Set on decode (ADR-0009). */
    record SetDecRef(DecRef element, SourcePos pos) implements DecRef.Bare {}

    /** An optional decoder. Under a key, a missing key or {@code null} is {@code None}; standing as
     *  an element or a map's value, where there is no key to be missing, {@code null} is the whole
     *  of it. Either way a present value decodes through {@code element}. */
    record OptionDecRef(DecRef.Bare element, SourcePos pos) implements DecRef {}

    /** A {@code Map<K, T>} decoder: each object value is decoded with {@code value}, and each of the
     * object's string keys through {@code key}.
     *
     * <p>The key is a {@link MapKeyRepresentation} rather than a {@code DecRef}: a key position holds one of
     * the representations the boundary admits, and nothing else — not a list, not an option — so the
     * type says as much and a reader that switches on it needs no arm for what cannot be there. It is
     * also the classification the checker already made, carried here rather than worked out again. */
    record MapDecRef(DecRef value, MapKeyRepresentation key, SourcePos pos) implements DecRef.Bare {}

    /** A statement in a single-value decoder body. */
    sealed interface DecStmt extends Hir permits Let {}

    record Let(Binder binder, Expr value, SourcePos pos) implements DecStmt {

        public String name() {
            return binder.name();
        }
    }

    /**
     * The construction a decoder ends in: {@code TypeName { field: expr, ... }}, one value per field.
     *
     * <p>Not an expression, and not what a body writes — a decoder is derived or written in the codec
     * grammar, and nothing there spreads. A construction a body writes is {@link NewData}.
     */
    record Construct(Name typeName, List<FieldInit> inits, SourcePos pos) implements Hir {

        /**
         * Never: a decoder gives one value per field, so a field left out is a field with no value.
         *
         * <p>Held here rather than at the check that asks it, so that both kinds of construction
         * answer the same question and neither reader decides for the node in front of it. This one
         * needs nothing kept to answer it — what a decoder writes is what this node is.
         */
        public boolean mayOmitOptionalFields() {
            return false;
        }
    }

    /** {@code field: expr}, or the shorthand {@code field}, in a construction. */
    record FieldInit(WrittenName written, Expr value) implements Hir {

        /** An initialiser a pass wrote — a derived encoder's, a newtype's implicit {@code value}. */
        public FieldInit(String name, Expr value, SourcePos pos) {
            this(WrittenName.synthetic(name, pos), value);
        }

        /** The field this fills. */
        public String name() {
            return written.canonical();
        }

        /** Where the field name is written. */
        @Override
        public SourcePos pos() {
            return written.pos();
        }

        /**
         * The same initialiser over a rewritten value.
         *
         * <p>The field's occurrence is the author's and survives a rewrite of what fills it. Naming
         * the field again — which every rewrite that took {@link #name()} and a position did — puts a
         * spelling where an occurrence was, and what is lost is the only record of where the author
         * wrote it: a report about the field then underlines as many characters as the name has,
         * starting where it starts, which is the same thing until it is not.
         */
        public FieldInit withValue(Expr rewritten) {
            return rewritten == value ? this : new FieldInit(written, rewritten);
        }
    }

    // --- encoders ---

    record EncoderDef(Binder self, RawExpr result, SourcePos pos) implements Hir {

        public String selfName() {
            return self.name();
        }
    }

    /** A Raw-building expression. */
    sealed interface RawExpr extends Hir
            permits TextRaw, IntRaw, BoolRaw, DecimalRaw, IsoTextRaw, ObjectRaw, EncodeRaw, ListEnc,
                    SetEnc, OptionRaw, MapEnc {}

    /** Encodes a {@code Map<K, T>} to a {@code Raw.Object}, each value via {@code elem} and each key
     * to its bare string through {@code key} — the representation the checker admitted it as, closed
     * for the reason {@link MapDecRef}'s is. */
    record MapEnc(Expr source, EncElem elem, MapKeyRepresentation key, SourcePos pos) implements RawExpr {}

    /** Encodes an optional field: {@code None} becomes {@code Raw.Null}, {@code Some(v)} encodes
     * {@code v} via {@code inner}, which reads the unwrapped value bound to {@code elemVar}. */
    record OptionRaw(Expr access, RawExpr inner, Binder elem, SourcePos pos) implements RawExpr {

        public String elemVar() {
            return elem.name();
        }
    }

    record TextRaw(Expr arg, SourcePos pos) implements RawExpr {}

    record IntRaw(Expr arg, SourcePos pos) implements RawExpr {}

    record BoolRaw(Expr arg, SourcePos pos) implements RawExpr {}

    /** Encodes a {@code Decimal} field to a {@code Raw.Decimal}. */
    record DecimalRaw(Expr arg, SourcePos pos) implements RawExpr {}

    /** Encodes a {@code Date}/{@code DateTime} field to a {@code Raw.Text} via its ISO8601 form. */
    record IsoTextRaw(Expr arg, SourcePos pos) implements RawExpr {}

    record ObjectRaw(List<RawEntry> entries, SourcePos pos) implements RawExpr {}

    /** {@code TypeName.encode(expr)} — encode a nested data value to Raw. */
    record EncodeRaw(Name typeName, Expr arg, SourcePos pos) implements RawExpr {}

    /** {@code list(expr, <elemEnc>)} — encode a {@code List<T>} to a Raw.List. */
    record ListEnc(Expr source, EncElem elem, SourcePos pos) implements RawExpr {}

    /** Encodes a {@code Set} as a JSON array: the set is listed, then each element encoded. */
    record SetEnc(Expr source, EncElem elem, SourcePos pos) implements RawExpr {}

    /** How to encode a list/set/map element: a primitive, another data's {@code .encode}, or another
     * collection — a collection may hold a collection ({@code Map<String, List<商品ID>>}), so the
     * element encoder nests as deeply as the type does. */
    sealed interface EncElem extends Hir permits EncElem.Bare, OptionElemEnc {

        /** An element encoder that is not an optional, for the reason {@link DecRef.Bare} carries. */
        sealed interface Bare extends EncElem
                permits PrimEnc, DataEnc, ListElemEnc, SetElemEnc, MapElemEnc {}
    }

    record PrimEnc(LeafScalar kind, SourcePos pos) implements EncElem.Bare {}

    record DataEnc(Name typeName, SourcePos pos) implements EncElem.Bare {}

    /** A {@code List<T>} element, each {@code T} encoded by {@code elem}. */
    record ListElemEnc(EncElem elem, SourcePos pos) implements EncElem.Bare {}

    /** A {@code Set<T>} element: listed, then each element encoded by {@code elem}, as {@link SetEnc}
     * does for a field. */
    record SetElemEnc(EncElem elem, SourcePos pos) implements EncElem.Bare {}

    /** A {@code Map<K, V>} element, each value encoded by {@code value} and each key by {@code key},
     * as {@link MapEnc} does for a field. */
    record MapElemEnc(EncElem value, MapKeyRepresentation key, SourcePos pos) implements EncElem.Bare {}

    /** An optional standing where there is no key to omit — a collection's element, a map's value.
     * {@code None} is written {@code null} and {@code Some(v)} through {@code elem}
     * (spec {@code [#absence-is-written-as-null]}). A field's optional is {@link OptionRaw}, which
     * omits its key instead. */
    record OptionElemEnc(EncElem.Bare elem, SourcePos pos) implements EncElem {}

    record RawEntry(String key, RawExpr value, SourcePos pos) implements Hir {}

    // --- expressions ---

    sealed interface Expr extends Written
            permits IntLit, DecimalLit, StringLit, BoolLit, Var, FieldAccess, Apply, Binary, Neg,
                    NewData, Match, If, IfConstructed, ListLit, RowCollection, ListComp, LetIn,
                    Expansion, Block, Tuple, TupleGet, Unreachable {
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

        /** A binding carrying an inlined helper parameter's declared type. */
        public LetIn(Binder binder, Expr value, RetType declaredType, Expr body, SourcePos pos,
                     Region region) {
            this(binder, value, declaredType, false, null, body, pos, region);
        }

        public String name() {
            return binder.name();
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
     * One application of a non-recursive helper, with the callee's body in place of the call.
     *
     * <p>It is one node rather than the bindings it becomes because a signature is one statement.
     * {@code emptyLike (xs: List<'a>) : List<'a>} says the result holds what the argument held, and
     * that is said by the two occurrences of {@code 'a} being one variable. Written out as a binding
     * per argument, each carrying its own declared type, there is nothing left saying the two
     * occurrences were ever the same — and a signature position that leaves no binding at all, which
     * the declared return and an unapplied function parameter both do, has nowhere to say anything.
     * Holding the whole instantiation here is what makes the rule hold however the callee was
     * written.
     *
     * <p>{@code application} names this one expansion, and the variables of {@code declaredReturn},
     * {@code bound} and {@code given} are that application's ({@link Type.MetaVar}). Two calls of one
     * helper are two applications and two sets of variables; a call inside an expansion is another.
     *
     * <p>{@code body} is the callee's, with each parameter read as the binding or the function it was
     * given. It is the only slot holding code that runs: what {@code given} holds is already inside
     * it wherever the callee applies it, and is kept here so the signature can be read against it.
     *
     * <p>{@code at} is where this copy is, and it is here rather than read off {@code application}.
     * What a binding belongs to is a chain the passes wrote and count within; where a copy is is the
     * call the source settles, and a reader that took the second from the first would be reading an
     * identity out of a value that says how the compiler ran. The two answer about one call and are
     * not the same answer to give.
     */
    record Expansion(ValueName callee, BindingOwner application, ExpansionSite at,
                     List<Bound> bound, List<Given> given, RetType declaredReturn, Expr body,
                     SourcePos pos, Region region) implements Expr {

        /**
         * The same expansion as the nested bindings it writes — for a reader whose question is only
         * about what flows where.
         *
         * <p>Not every reader is asking about the signature. What an invariant may be discharged
         * from is a question about which value reached which position, and to that reader an
         * expansion is a name bound to an argument and a body reading it, exactly as a {@code let}
         * the author wrote is. Reading it as one keeps those readers saying about a call what they
         * say about the code it stands for.
         */
        public Expr asBindings() {
            Expr out = body;
            for (int i = bound.size() - 1; i >= 0; i--) {
                Bound b = bound.get(i);
                out = new LetIn(b.binder(), b.value(), b.declaredType(), out, pos, region);
            }
            return out;
        }
    }

    /** {@code e} with every expansion in it read as the bindings it writes ({@link
     * Expansion#asBindings}), at every depth. */
    public static Expr asBindings(Expr e) {
        Expr through = e instanceof Expansion ex ? ex.asBindings() : e;
        return mapChildren(through, Hir::asBindings, name -> name);
    }

    /** A value argument. It becomes a binding, so the body reads a name rather than the argument's
     * text, and the callee's declared type for it comes along. */
    record Bound(Binder binder, RetType declaredType, Expr value) {}

    /**
     * A function argument. It leaves no binding, so what the signature said about it reaches a
     * reader only from here.
     *
     * <p>{@code arrivesAs} is what the argument is declared as where it comes from, where this
     * expansion can see that. The two declarations are what a boundary holds — what this callee
     * wants of the position, and what the function handed to it is — and reading them against each
     * other is what the boundary is for.
     *
     * <p>{@code applied} is whether the callee's body still reaches it. Where it does, the body is
     * where this argument is typed and what the signature said is checked by that application, as it
     * is for a function written in place. Where it does not — the callee named a function parameter
     * and never used it — the body says nothing about it at all, and this is the only place it can
     * be held to the type the callee declared for it.
     */
    record Given(RetType declaredType, Expr value, boolean applied, RetType arrivesAs) {}

    /** A list literal {@code [e1, e2, ...]} (one or more elements of the same type).
     *
     * <p>{@code origin} is the collection the author wrote, carried from where the source was read.
     * The operations a body ends up holding for it are references derived from that construct, and
     * a helper holding one is expanded at each of its calls — so the identity is the source's and
     * not the place's. */
    record ListLit(List<Expr> elements, SourceConstructOrigin origin, SourcePos pos, Region region)
            implements Expr {}

    /**
     * {@code [ … ]} written in an {@code example} or {@code fake} row, where the brackets are the
     * notation for whichever collection the position declares — a list, a set, or a map of the
     * entry pairs written in it (spec §example-evaluable).
     *
     * <p>Its own node because which collection it is has not been decided. A row's brackets and a
     * body's brackets are different questions with different answers, and the same node for both is
     * a reading that answers the row's question with the body's: a list, whatever the position says.
     * Elaboration is what decides it, from the type the position contributes, and every reader that
     * would have to know is a reader the compiler names rather than one that quietly reads a list.
     *
     * <p>The position contributes a type here and constrains nothing. Which collection the brackets
     * are is the notation's question; whether the value that comes out belongs at the position is a
     * separate one, asked of an input and not of an expectation ({@link RowPosition}).
     */
    record RowCollection(List<Expr> elements, SourceConstructOrigin origin, SourcePos pos,
                         Region region) implements Expr {}

    /** A guard-only comprehension {@code [element | guard, ...]}: the element is included when
     * every guard holds, giving a 0-or-1 element list (spec §stdlib-list, conditional accumulation).
     *
     * <p>{@code origin} names the comprehension, and the fork each guard lowers to is derived from it
     * ({@link #forkOfGuard}) rather than minted where the lowering runs — so a comprehension
     * inside a helper answers the same whichever call site expanded it. */
    record ListComp(Expr element, List<Expr> guards, SourceConstructOrigin origin, SourcePos pos,
                    Region region) implements Expr {

        /**
         * The fork the guard at {@code at} lowers to — asked of the comprehension, which is what
         * holds the construct they are forks of.
         *
         * <p>Two readers want it and they see the comprehension at different times: the lowering
         * builds the forks, and the reading that decides what a coverage obligation is about runs
         * before the lowering, where a rule is still a parameter. Written once here so that the two
         * cannot come to number the guards differently — the numbering is what makes an obligation
         * name a fork that exists, and it held only while both spelled the same arithmetic.
         *
         * @throws IndexOutOfBoundsException where {@code at} is not a guard of this comprehension.
         *                                   A fork is one of the guards and nothing else is one:
         *                                   the fork before the first would be the comprehension's
         *                                   own origin, which is a different obligation and would
         *                                   pass for this one, and the fork after the last is a
         *                                   branch the lowering does not build
         */
        public SourceConstructOrigin forkOfGuard(int at) {
            if (at < 0 || at >= guards.size()) {
                throw new IndexOutOfBoundsException(
                        "a fork is one of the guards written: " + at + " of " + guards.size());
            }
            return origin.lowered(at);
        }
    }

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
    record ElseArm(Optional<String> clause, Expr body, SourcePos pos) implements Hir {

        /** The arm taken for any failure — what {@code else e} and {@code | _ -> e} both mean. */
        public static ElseArm any(Expr body) {
            return new ElseArm(Optional.empty(), body, body.pos());
        }

        /** The same arm over a rewritten body, so a rewriting stage keeps the clause it answers. */
        public ElseArm with(Expr rewritten) {
            return new ElseArm(clause, rewritten, pos);
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
                SourcePos pos) implements Hir {
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
     * <p>{@code origin} says where the construction came from: written here, or carried in by a
     * published body or by a value this body named. {@code fields} says whether every field of it
     * had to be written, which is what the reading it was read under settled ({@link Reading}).
     *
     * <p>Both are answered when the source is read, and no later place answers them again. Expansion
     * is why: a construction spliced in from another body is the same node the reader's own would be,
     * and a construction a row spreads in was read as the model's own however it stands now — so the
     * position a node is found at says neither, and asking the node is what is left.
     *
     * <p>So a pass has no way to make one that says what it was not read as: the components are of
     * types only this package can name, and every place in this package that settles one is written
     * out and held against the compiled classes. A construction is either
     * read from the source that spells it ({@link #read}), translated from the form that already
     * answered for it ({@link #fromApply}), rebuilt from one that has the answers ({@link #with}),
     * moved across a crossing that changes where it came from ({@link #publishedBy},
     * {@link #carriedByValue}), or built by a pass out of no source at all
     * ({@link #syntheticWithEveryFieldWritten}) — which is the one of these that answers for itself,
     * and is named for the answer it gives.
     */
    record NewData(Name typeName, List<FieldInit> inits, List<Var> spreads,
                   ConstructionOrigin origin, Fields fields, SourcePos pos, Region region)
            implements Expr {

        /**
         * The construction {@code surface} spells, read under {@code reading} — the one way a
         * construction is made from a source that writes one.
         *
         * <p>{@code surface} is the run of characters this is the reading of, and what it answers is
         * where the construction stands. A pass rewriting a body holds no surface node, so nothing
         * it has is a reading — but that is what the passes are like and not something the language
         * refuses, a parsed node being a record anyone can build. What holds the answer down is that
         * every place one is settled is written out and checked against the compiled classes.
         */
        public static NewData read(Ast.NewData surface, Name typeName, List<FieldInit> inits,
                                   List<Var> spreads, Reading reading) {
            return new NewData(typeName, inits, spreads, Origins.Own.IT_IS,
                    switch (reading) {
                        case A_FIXTURE -> Fields.OPTIONALS_MAY_BE_OMITTED;
                        case THE_MODELS_OWN -> Fields.EVERY_ONE_WRITTEN;
                    },
                    surface.pos(), surface.region());
        }

        /**
         * The construction {@code application} means — {@code T(v)} at a newtype, which the author
         * wrote as an application and a desugaring reads as what it constructs.
         *
         * <p>The application is the argument because it is what already answered: where the
         * construction came from is the application's answer and is carried, not asked again of the
         * pass rewriting it. A newtype construction writes the one field the newtype declares, so
         * every field of it is written — which this says, rather than a caller.
         */
        public static NewData fromApply(Apply application, Name typeName, List<FieldInit> inits) {
            return new NewData(typeName, inits, List.of(), application.origin(),
                    Fields.EVERY_ONE_WRITTEN, application.pos(), application.region());
        }

        /**
         * A construction a pass composed, which no source spells and which gives every field of the
         * type a value — written out or spread in, as {@link Fields#EVERY_ONE_WRITTEN} is.
         *
         * <p>The one construction that is nobody's reading. It says what it is in its name because
         * there is no source to have said it: a pass building one is stating that it left no field
         * to be filled in for it, and a pass that cannot state that has no construction to build.
         */
        public static NewData syntheticWithEveryFieldWritten(Name typeName, List<FieldInit> inits,
                                                             List<Var> spreads, SourcePos pos,
                                                             Region region) {
            return new NewData(typeName, inits, spreads, Origins.Own.IT_IS,
                    Fields.EVERY_ONE_WRITTEN, pos, region);
        }

        /**
         * Whether a field this construction leaves out is the absent value its declaration holds —
         * asked of the node, which is what was there when the source was read.
         */
        public boolean mayOmitOptionalFields() {
            return fields == Fields.OPTIONALS_MAY_BE_OMITTED;
        }

        /** The same construction, carried into a reader by {@code module}'s published body. */
        public NewData publishedBy(String module) {
            return new NewData(typeName, inits, spreads, Origins.publishedIn(origin, module),
                    fields, pos, region);
        }

        /** The same construction, carried into a body by a value that body named. */
        public NewData carriedByValue() {
            return new NewData(typeName, inits, spreads, Origins.carriedByValue(origin), fields, pos,
                    region);
        }

        /** The same construction over rewritten fields and spreads, which is a rebuild and carries
         *  where the construction came from and which fields it writes out. */
        public NewData with(List<FieldInit> inits, List<Var> spreads) {
            return with(inits, spreads, pos, region);
        }

        /** The same construction rewritten and stamped where the copy of it stands — what a pass
         *  that copies a body into another one writes. */
        public NewData with(List<FieldInit> inits, List<Var> spreads, SourcePos pos, Region region) {
            return new NewData(typeName, inits, spreads, origin, fields, pos, region);
        }

        /**
         * Whether the body holding this was handed the construction rather than making it, and so
         * answers for none of it — asked of the node, which is what holds the answer.
         */
        public boolean wasCarried(TypeSymbol.AtModule built) {
            return Origins.carried(origin, built);
        }
    }

    /**
     * Whether a construction has to write out every field it has.
     *
     * <p>One rule turns on this, and it is the one that reports a field with no value. A fixture
     * writes a value the way it is read back rather than the way a body builds one, and there an
     * unwritten optional field is the absent value it would otherwise spell out (spec
     * §example-evaluable); everywhere else a construction says what each of its fields is, which is
     * the rule a body is held to. Named for what it permits rather than for where it came from, so
     * nothing else can come to rest on "this was written in a fixture".
     *
     * <p>Nothing outside this package names it. The rule that turns on it asks the construction
     * ({@link NewData#mayOmitOptionalFields}, {@link Construct#mayOmitOptionalFields}), so a reader
     * gets the answer from the node that holds it rather than from whichever value it had in hand,
     * and a pass has nothing to spell that would make a construction say what it was not read as.
     * What {@link Reading} settles is which of these a construction gets, and it settles it once.
     */
    enum Fields {
        /** Every field of the construction is written or spread — what a body writes. */
        EVERY_ONE_WRITTEN,
        /** A field the construction does not write is {@code None} where it declares an optional. */
        OPTIONALS_MAY_BE_OMITTED
    }

    record IntLit(long value, SourcePos pos, Region region) implements Expr {}

    record DecimalLit(java.math.BigDecimal value, SourcePos pos, Region region) implements Expr {}

    /** Unary minus {@code -operand} on an Int or Decimal (spec §an-operator-takes-the-types-it-is-defined-for). */
    record Neg(Expr operand, SourcePos pos, Region region) implements Expr {}

    record StringLit(String value, SourcePos pos, Region region) implements Expr {}

    record BoolLit(boolean value, SourcePos pos, Region region) implements Expr {}

    /**
     * A name used as a value — the one representation the surface AST has for one. {@code written}
     * is how the source writes it (bare {@code price}, qualified {@code billing.price}, or through
     * an import alias) and {@code denotes} is what it names, answered once during resolution; a
     * reader asks that rather than deciding for itself whether the spelling is a local, a unit data
     * or something the language provides. What {@link Name} is for a type.
     *
     * <p>It stands in two kinds of slot, which differ in what may replace it:
     *
     * <ul>
     *   <li>an expression slot, where any expression may stand — an argument, a field's value, the
     *       thing an {@code if} tests;</li>
     *   <li>a name slot, where only another name may — a construction's spread {@code T { ..base }},
     *       which copies the fields of what the name stands for and so has nothing to evaluate.</li>
     * </ul>
     *
     * <p>Both are children: {@link #forEachChild} reaches them, so a pass that asks about names sees
     * a spread without knowing spreads exist. {@link #mapChildren} takes the two slots as separate
     * operators, so a rewrite cannot put an expression where only a name may stand. A pass that does
     * substitute an expression for a name — the inliner — binds it ahead of the construction and
     * spreads the binding.
     *
     * <p>The two forms are the two answers resolution has, as {@link Name}'s are: answered with what
     * it names, or read and found to name nothing — which is an answer too, and was reported where
     * the name is written. A name nobody has looked at is {@link Ast.Var}.
     */
    sealed interface Var extends Expr permits Var.Denoting, Var.Unanswered {

        /** The name, and the occurrence of it that was read. */
        WrittenName written();

        /**
         * Which reference this is, or null where the name reaches no declaration and there is none
         * to be.
         *
         * <p>Not the name and not the place. A pass may respell a reference — a helper written bare
         * becomes qualified in a body carried out of its module — so what the name is spelled as
         * says nothing about which occurrence it is; two occurrences of one name reach the same
         * declaration, so what it reaches says nothing either; and where the characters are is
         * where a complaint belongs. So a reader that has to tell one occurrence from another reads
         * this ({@link ReferenceOrigin}).
         *
         * <p>Whoever wrote it. An author's is one this source counted; a pass writing a name of its
         * own — the operation an empty collection stands for — wrote a reference too, and it is
         * said by what made the pass write it. A pass's given the number of one an author wrote
         * would be this compiler's work passing for the model's, so the two are told apart by which
         * they are rather than by one of them being absent.
         *
         * <p>Null only where the name reaches no declaration: a read of a binding, a namespace, a
         * name that denotes nothing. What such a name is, is what it reaches — a binding is already
         * a thing this compiler tells from every other — so there is no occurrence to number and
         * none is wanted. A name that does reach a declaration carries one, which
         * {@link Denoting} refuses to be built without.
         */
        ReferenceOrigin origin();

        /** The stretch of source the expression was written over. */
        @Override
        Region region();

        /**
         * The region is the expression's and the name's is {@code written}'s, and they part company
         * only where the author wrapped the name in something the tree does not keep:
         * {@code (price)} is one expression written over nine characters and one name written over
         * five. So the one has to hold the other. A region that did not would be a claim that the
         * name is written somewhere this expression is not, which no source can produce.
         */
        private static void heldBy(WrittenName written, Region region) {
            if (written.region() != null && region == null) {
                throw new IllegalArgumentException("`" + written.canonical()
                        + "` is written somewhere and the expression it is was written nowhere");
            }
            if (!Region.encloses(region, written.region())) {
                throw new IllegalArgumentException("`" + written.canonical()
                        + "` is written outside the expression it is");
            }
        }

        /**
         * A name a pass already knows the meaning of, reaching something that is not a declaration:
         * a case of a sum, a value the language names, a binding.
         *
         * <p>The reach name is given rather than worked out here. A pass writing a name into a body
         * either has one in hand — it is rewriting a name that already carried it — or knows which
         * module's body it is writing into, and neither is something this factory can see. Worked
         * out from the spelling it would be the very derivation the carried value exists to remove.
         *
         * <p>Not for a name that reaches a declaration. Such a name is some reference of it, and
         * which one is the writer's to say ({@link #respelled}); nothing here could work it out, so
         * this hands over none and {@link Denoting} refuses the pairing.
         */
        static Var denoting(String spelling, ReachName reachedAs, SourcePos pos) {
            return denoting(WrittenName.of(spelling, pos), reachedAs);
        }

        /** The same, off an occurrence already read: a name standing as an expression over exactly
         * the characters that spell it — every one but a name the author parenthesized.
         *
         * <p>What such a name is, is what it reaches — a binding is already a thing this compiler
         * tells from every other — so there is no occurrence of it to number and none is handed
         * over. A name reaching a declaration is the other case and is not written here. */
        static Var denoting(WrittenName written, ReachName reachedAs) {
            return new Var.Denoting(written, reachedAs, null, written.region());
        }

        /**
         * A name a pass wrote in place of one the author wrote, standing where that one stood.
         *
         * <p>{@code spelling} is the pass's — a helper qualified by the module that declares it, so
         * that a body carried out of its module goes on reaching the same declaration. The
         * characters at {@code pos} spell what the author put there, which is not this, so the name
         * is written nowhere and only the expression has a place: the region is the one the name it
         * replaced was read over.
         *
         * <p><b>The reference is the one it replaced.</b> Respelling changes how a name is written
         * and what route it takes to what it names; which occurrence of the source it is has not
         * moved, and a reader telling two occurrences apart would be told they are two different
         * ones. So {@code origin} is carried in from the name being replaced — see
         * {@link #respelledAs}, which is the way in for a caller that has that name.
         */
        static Var respelled(String spelling, ReachName reachedAs, ReferenceOrigin origin,
                             SourcePos pos, Region region) {
            return new Var.Denoting(WrittenName.synthetic(spelling, pos), reachedAs, origin, region);
        }

        /**
         * This name written and reached another way, which is the same reference of the source.
         *
         * <p>To a declaration, which is what respelling a name is for: a helper qualified by the
         * module that declares it goes on reaching that declaration from a body carried out of it.
         * A name that reaches a binding is read where the binding is and is not respelled to reach
         * it another way, so the route this takes is narrower than a route in general.
         */
        default Var respelledAs(String spelling, ReachName.Declaration reachedAs) {
            return respelled(spelling, reachedAs, origin(), pos(), region());
        }

        /**
         * A read of something bound in the body, as a pass that put the binding there writes it.
         *
         * <p>A pass that runs after resolution says what it means rather than leaving a spelling for
         * a reader to work out, so it is given the binder it is reading and answers with that
         * binding. There is no way to write one of these without having the binding in hand.
         */
        static Var local(Binder binder, SourcePos pos) {
            ValueName.Local local = new ValueName.Local(binder.name(), binder.id());
            WrittenName written = WrittenName.synthetic(binder.name(), pos);
            // No source wrote it: what a pass reads here is a binding that pass put there.
            return new Var.Denoting(written, new ReachName.InScope(local), null, written.region());
        }

        /** The bare name this reaches its declaration by, whatever the source spelled. */
        default String name() {
            return written().canonical();
        }

        /** Where the name is written. */
        @Override
        default SourcePos pos() {
            return written().pos();
        }

        /**
         * This reference where it names a declaration, and null where resolution read it and found
         * none.
         *
         * <p>What a walk over a body asks. An edge, a substitution, a rewrite is about what a name
         * stands for; a name nothing declares stands for nothing, so there is no edge to add and
         * nothing to rewrite, and the mistake was reported where the name is written.
         *
         * <p>The one way from here to what a name names. What a declaration-reading observation
         * needs — {@link Denoting#denotes()}, {@link Denoting#reachedAs()} — is the answered form's,
         * so a reader arrives at it through this projection or through narrowing to the two forms,
         * and says what it does with an {@link Unanswered} name where it makes that choice.
         */
        default Var.Denoting answered() {
            return this instanceof Var.Denoting denoting ? denoting : null;
        }

        /** Whether this name denotes nothing — read by resolution, and reported where it was
         * written. Asked of {@link #answered()} rather than of the form, so that what a name
         * answers and whether it answered cannot come apart. */
        default boolean unresolved() {
            return answered() == null;
        }

        /**
         * The same name, as {@code reachedAs} reaches it.
         *
         * <p>One answer and not two. Which declaration this reaches and under what name it reaches
         * it from here are the two halves of one question, and resolution answers them together;
         * handed over separately, a caller could pair one name's denotation with another's route
         * and nothing would say so. There is no state between: a name is answered or it is
         * {@link Unanswered}.
         */
        default Var denoting(ReachName reachedAs) {
            return new Var.Denoting(written(), reachedAs, origin(), region());
        }

        /** The same name, over {@code region} — whichever of the two it is. */
        default Var over(Region region) {
            return switch (this) {
                case Var.Denoting d ->
                        new Var.Denoting(d.written(), d.reachedAs(), d.origin(), region);
                case Var.Unanswered u -> new Var.Unanswered(u.written(), u.origin(), region);
            };
        }

        /** The same name, read and found to name nothing. */
        default Var unanswered() {
            return new Var.Unanswered(written(), origin(), region());
        }

        /**
         * A name resolution answered: what the source wrote, and the reference resolution settled
         * for it.
         *
         * <p>Which declaration it names is {@code reachedAs.denotes()} and is not held beside it.
         * Held as two, a pass could put one name's denotation next to another's route and nothing
         * would say so — and three passes did, replacing what a name meant and leaving the route it
         * was reached by. A denotation is changed by replacing the reference, which is
         * {@link #denoting(WrittenName, ReachName)}.
         */
        record Denoting(WrittenName written, ReachName reachedAs, ReferenceOrigin origin,
                        Region region) implements Var {

            public Denoting {
                if (reachedAs == null) {
                    throw new IllegalArgumentException("`" + written.canonical()
                            + "` is answered by what it reaches and how it reaches it;"
                            + " nothing here says either");
                }
                // A name that reaches a declaration says which reference of it this is. Two
                // occurrences of one name reach one declaration and are two references, and a
                // reader telling them apart — the block each of them expands to is its own — has
                // nothing else to do it by: the spelling is a pass's to change and the place is
                // shared by every copy of a helper that was expanded. Whoever wrote the name owes
                // one, so a pass writing its own says what made it write one rather than leaving
                // the slot empty for a reader to find.
                //
                // Asked of what it reaches and not of who spelled it. A pass respelling an
                // author's name carries the author's reference through, and a name reaching a
                // binding or a namespace has nothing to number — what such a name is, is what it
                // reaches.
                if (reachedAs instanceof ReachName.Declaration && origin == null) {
                    throw new IllegalArgumentException("`" + written.canonical()
                            + "` reaches a declaration, so it is some reference of it");
                }
                heldBy(written, region);
            }

            /** The declaration this names, which the reference carries. */
            public ValueName denotes() {
                return reachedAs.denotes();
            }

            /**
             * The reference this was answered with where it reaches a declaration, and null where
             * it reaches none.
             *
             * <p>The one place the question is put. A body names declarations and things that are
             * not — a binding, a type written where a value goes, the library's namespace — and
             * every reader that looks a name up among declarations has to tell them apart. Asked
             * once here, a reader holds the answer as a type; asked at each of them, each reader
             * writes down which kinds are not one, and the reader that forgets a kind looks it up
             * anyway.
             */
            public ReachName.Declaration reachesADeclaration() {
                return reachedAs instanceof ReachName.Declaration reached ? reached : null;
            }

            /**
             * As {@link #reachedAs()}, rendered — what a table keyed by a declaration's name is
             * looked up with.
             *
             * <p>Never the spelling. An import lets a name be written without its qualifier, so the
             * spelling misses in the very table this is asked for, and a table answers a key it has
             * not got with silence. Every pass that writes a reference of its own says what it
             * means (ADR-0067), so there is no tree downstream of resolution for a fallback to have
             * been for.
             */
            public String reaches() {
                return reachedAs().rendered();
            }

            @Override
            public String toString() {
                return name();
            }
        }

        /**
         * A name resolution read and found nothing for.
         *
         * <p>Reported where it is written, or on the import line or the module that could not be
         * read — whichever could say what is wrong. It carries neither answer, so a reader below has
         * no spelling to match and no report to repeat.
         *
         * <p>What a reader does on meeting one is the reader's to say, and the readers here say one
         * of two things. A walk that recovers — adds no edge, states no type, settles no call —
         * passes it over: what is wrong is reported already. A reader whose input is built from
         * answered names only says so with {@link #unexpectedHere()}, naming the construction that
         * makes it so. The order the passes run in makes nothing so: a compilation goes on
         * answering after an error, so only a producer that leaves them out can be named.
         */
        record Unanswered(WrittenName written, ReferenceOrigin origin, Region region)
                implements Var {

            public Unanswered {
                heldBy(written, region);
            }

            /**
             * The claim that this name is somewhere it could not have reached.
             *
             * <p>Not a reading: there is nothing here to read, and what a reader arriving here is
             * saying is that its input was built from answered names. The caller throws it, and
             * names beside the throw the construction it is standing on.
             */
            public IllegalStateException unexpectedHere() {
                return new IllegalStateException("`" + name() + "` at " + pos()
                        + " denotes nothing and was read as though it did");
            }

            @Override
            public String toString() {
                return name();
            }
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
         * {@link Var#respelled}, for the same reason.
         */
        public static FieldAccess restamped(Expr target, String field, SourcePos at, Region over) {
            return new FieldAccess(target, WrittenName.synthetic(field, at), at, over);
        }

        /** The same access over a rewritten target. The field's occurrence and the stretch of source
         * the read was written over are the author's and survive a rewrite of what it reads from —
         * naming the field again here would put a spelling where an occurrence was. */
        public FieldAccess withTarget(Expr rewritten) {
            return rewritten == target ? this : new FieldAccess(rewritten, name, pos, region);
        }

        /** The field this reads. */
        public String field() {
            return name.canonical();
        }
    }

    /**
     * What an application applies, as a report says it: the name the author applied, and the
     * characters they applied it over.
     *
     * <p>Settled where the application is read and carried from there. A lowering replaces what is
     * in the callee position — a field read is bound to a name of its own before it is applied, a
     * sugared library name becomes the operation it stands for, a helper of another module is
     * written qualified — and a reader that asked the callee what the author applied would be told
     * whatever the last pass put there. So no reader asks it: the answer is read off the source
     * once, and every rewrite of the application hands it on.
     *
     * <p>Both halves, because they are two answers and the second cannot be worked out from the
     * first. A name the author parenthesized is written over five characters and applied over
     * seven, and a report about what is applied underlines the seven. The pair is what the reading
     * already holds, so keeping it is keeping what was read rather than working anything out.
     *
     * <p>{@code name} is null where what the author applied is not a name: the result of a call, a
     * lambda, a conditional. That is one of the three states this has, the others being a name the
     * author wrote ({@link WrittenName#authored()}) and one a pass reached for, and none of them is
     * an empty spelling.
     *
     * <p>{@code at} is never null. An application stands somewhere — where a source wrote it, or
     * where the pass composing it put it — and a report about what it applies points there. There
     * is no application with nowhere to point at what it applies.
     */
    record AppliedCallee(WrittenName name, Region at) {

        public AppliedCallee {
            Objects.requireNonNull(at, "an application is applied somewhere, and a report about"
                    + " what it applies points there");
        }

        /** Whether what the author applied is a name — which is not whether a name is what stands
         *  in the callee position now, a lowering having been free to put a binding of its own
         *  there. */
        public boolean isAName() {
            return name != null;
        }

        /** The name as a report quotes it, or the empty spelling where what is applied is not a
         *  name. */
        public String quoted() {
            return name == null ? "" : name.canonical();
        }

        /**
         * Where a report about the name this applies points: the occurrence of the name, or the
         * characters applied where the author applied no name.
         *
         * <p>The choice between two answers already held. A report about what is applied has
         * somewhere to point either way, and where there is no name the place is the expression
         * that stood in the callee position — which is what the author applied, being not a name.
         */
        public Region reportedAt() {
            return name == null ? at : name.reportedAt();
        }

        /**
         * The same applied callee in a file this copy is not read against — the name it was, written
         * nowhere here, applied over {@code over} and complained about at {@code at}.
         *
         * <p>What the author applied travels with the copy; where they wrote it does not. A body
         * spliced in from a source this compile cannot show carries no coordinate of that source, so
         * the occurrence goes and the places are the ones the copy stands at — the same answer a
         * field read taken off a copied value gives, and for the same reason.
         */
        public AppliedCallee restamped(SourcePos at, Region over) {
            return new AppliedCallee(name == null ? null
                    : WrittenName.synthetic(name.canonical(), at),
                    over != null ? over : Region.point(at));
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
     */
    record Apply(Expr function, List<Expr> args, ConstructionOrigin origin, AppliedCallee applied,
                 ApplicationOrigin application, SourcePos pos, Region region) implements Expr {

        // `application` and not `origin`, because the slot beside it is already an answer to a
        // different question: that one says how the construction this application stands for
        // reached the body, and this one says why this application is here at all. A pass
        // rewriting the first has nothing to say about the second, and one name for both would be
        // two facts a reader could take for one.
        //
        // Said by whoever writes the application and never read back off its shape. An application
        // no source wrote looks the same whether a name was expanded into it, a library operation
        // was reached for, or a row was composed — so a reader working it out from the shape gets
        // the common case right and answers the rest with what the common case says.

        public Apply {
            // Every application in this tree answers it, which is what lets a reader below take the
            // answer rather than work one out. Held by the factories alone, the rule would be one
            // the record itself did not keep, and a pass reaching for the constructor could leave a
            // reader nothing — which is the state this exists to remove.
            if (application == null) {
                throw new IllegalArgumentException(
                        "an application is here for some reason: " + function);
            }
        }

        /**
         * The application {@code surface} spells, of whatever {@code function} is — the one way an
         * application is made from a source that writes one.
         *
         * <p>Where it came from is the body's own, because a source spells an application in the
         * body it is written in. What the author applied is {@code applied}, read off the surface
         * callee by the reading that has it: which name a chain of field reads spells is a question
         * about the source, and the answer is settled once here rather than worked out again from
         * whatever the passes leave in the callee position.
         */
        public static Apply read(Ast.Apply surface, AppliedCallee applied, Expr function,
                                 List<Expr> args) {
            return new Apply(function, args, Origins.Own.IT_IS, applied,
                    new ApplicationOrigin.Written(surface.origin()), surface.pos(),
                    surface.region());
        }

        /**
         * An application a pass composed, standing where it puts it — a call that stands for a name
         * used as a value, a library operation a fixture reached for.
         *
         * <p>Nobody's reading, so it says what it is in its name. Where it came from is the body it
         * is written into: a pass composing an application is writing one there, and a pass
         * rewriting one that was already written carries what it was handed instead
         * ({@link #replacedBy}, {@link #with}, {@link #withArgs}).
         *
         * <p>What the author applied is whatever the callee already answers, and no more than that.
         * A synthetic application may apply a name occurrence somebody wrote — {@code etaExpand}
         * composes the block a bare {@code List.map} stands for, and the application is this pass's
         * while the occurrence is the author's — so the two are not one question and this settles
         * only the first. Where the callee is a name, its own is taken; where it is not, there is no
         * name to answer with and nothing is invented. A chain of field reads is not read back into
         * a name here either: which of those spells one is the source reading's answer, and a pass
         * holding a resolved expression is not reading a source.
         *
         * <p>{@code application} is why this one is here, which the composer says because only the
         * composer knows. An application no source wrote looks the same whichever pass wrote it and
         * for whatever reason, so there is nothing here to work it out from — and giving it the
         * number of a construct somebody wrote would be this pass's work passing for the model's.
         */
        public static Apply synthetic(Expr function, List<Expr> args,
                                      ApplicationOrigin application, SourcePos pos, Region region) {
            return new Apply(function, args, Origins.Own.IT_IS, appliedCallee(function, pos),
                    application, pos, region);
        }

        /** What {@code function} answers as the applied callee, anchored at {@code where} it stands
         *  when the callee is written nowhere at all. */
        private static AppliedCallee appliedCallee(Expr function, SourcePos where) {
            Region at = function.reportedAt();
            return new AppliedCallee(function instanceof Var v ? v.written() : null,
                    at != null ? at : Region.point(where));
        }

        /**
         * Applying a name a pass chose, at the form it stands in, saying what that name means and
         * how the body being written into reaches it.
         *
         * <p>{@code fn} is a spelling and not an occurrence — a library name a rewrite reached for,
         * a fixture's constructor, a value rendered back out of what was computed — so the applied
         * name is written nowhere and the characters at {@code pos} spell whatever the author put
         * there. A name the author applied is passed as the {@link Var} it is, which is where its
         * occurrence is; building one from a spelling and a position measures the spelling at the
         * anchor, and the two are the same number only until they are not.
         *
         * <p>The denotation and the reach name are taken and not optional. A pass writing an
         * application either has them or is writing a name it has not resolved, and the second is
         * what ADR-0067 rules out: the spelling would be read back downstream by whatever the
         * reading module happens to mean by it. Only the parser leaves a callee unanswered, and it
         * builds the {@link Var} for itself.
         *
         * <p>{@code region} is the application's and is not handed to the callee. What the callee
         * covers is its own — a rewrite that puts another name in a call leaves the arguments where
         * they are, so a report about what is applied would otherwise underline them too. A caller
         * that has the callee's extent builds the {@link Var} itself and passes it.
         *
         * <p>{@code origin} is which reference the name is, which the caller says because only the
         * caller knows: a name reaching a declaration is some reference of it, and what made this
         * pass write one is the caller's business and not a thing to be worked out from the
         * spelling. Null where the name reaches no declaration — a namespace, a case of a sum —
         * and {@link Var.Denoting} refuses it where it does.
         */
        public static Apply synthetic(String fn, ReachName reachedAs, ReferenceOrigin origin,
                                      ApplicationOrigin application, List<Expr> args, SourcePos pos,
                                      Region region) {
            return synthetic(
                    Var.respelled(fn, Objects.requireNonNull(reachedAs, unanswered(fn)), origin, pos,
                            null),
                    args, application, pos, region);
        }

        /** Why a pass may not apply a name it has not answered for. */
        private static String unanswered(String fn) {
            return "a pass applying `" + fn + "` says what it means: nothing here says what it"
                    + " reaches, and the spelling would be resolved again wherever this is read";
        }

        /**
         * Whether a name is what stands in the callee position now.
         *
         * <p>Not whether the author applied one, which is {@link AppliedCallee#isAName()}. The two
         * differ wherever a lowering has been: {@code deps.count(x)} binds the field read to a name
         * of its own and applies the binding, so a name is what is applied here and a field read is
         * what the author applied. A reader wanting the name itself matches on {@link #function()},
         * which is where it is.
         */
        public boolean calleeIsAName() {
            return function instanceof Var;
        }

        /**
         * The name this applies as the source writes it, or the empty spelling where what it
         * applies is not a name. What a report quotes.
         *
         * <p><b>Never a lookup key.</b> An import lets a library name be written without its
         * qualifier, so a table keyed by a declaration's name misses on the spelling — silently,
         * because a miss is what a table keyed by names does with one it has not got. Every
         * question of the form "which declaration is this" asks {@link #answered()}.
         */
        public String written() {
            return applied.quoted();
        }

        /**
         * Where a report about what this applies points: the characters the author applied it over.
         *
         * <p>Read off {@link #applied} and not off the callee. A lowering is free to replace what is
         * in the callee position — with a binding it introduced, with the operation a sugared name
         * stands for, with a helper's qualified spelling — and the characters those stand for are
         * still whatever the author wrote. Asking the callee gets the answer only until a pass has
         * been through.
         */
        public Region appliedAt() {
            return applied.at();
        }

        /**
         * As {@link Var#answered()}, of the name this applies — and what a reader asking which
         * declaration this reaches goes through.
         *
         * <p>Null the two ways there is no declaration to look up: what is applied is not a name,
         * or it is one nothing declares. A callee nothing has read yet is refused there, as it is
         * of a name standing on its own.
         *
         * <p>What the answered form says — {@link Var.Denoting#denotes()},
         * {@link Var.Denoting#reachedAs()}, {@link Var.Denoting#reaches()} — is read off it here
         * rather than carried across by this one. Carried, it would answer for a callee that is not
         * a name and for one that names nothing in the same value, and a reader would have neither
         * to hand.
         */
        public Var.Denoting answered() {
            return function instanceof Var v ? v.answered() : null;
        }


        /** The same application, carried into a body by a value that body named. A recursive helper
         * is lowered to a method rather than expanded, so a value reaching one leaves an application
         * where its constructions would otherwise stand, and it is what has to say where it came
         * from. */
        public Apply carriedByValue() {
            return new Apply(function, args, Origins.carriedByValue(origin), applied, application,
                    pos, region);
        }

        /** The same application over rewritten arguments — a pass that touches only the arguments
         *  says so here rather than listing the slots it is not changing, which is how what the
         *  author applied would be dropped by a rewrite that has no opinion about it. */
        public Apply withArgs(List<Expr> args) {
            return new Apply(function, args, origin, applied, application, pos, region);
        }

        /**
         * The same application, of something else — the one rewrite that puts another thing in the
         * callee position of an application a source already wrote.
         *
         * <p>What is applied is the pass's; where the application stands, where it came from and
         * what the author applied there are not. A sugared library name becomes the operation it
         * stands for, a field read applied is bound to a name of its own and the binding applied,
         * a helper of another module is written qualified — each replaces what is applied and none
         * of them replaces what was written over those characters.
         *
         * <p>Taking no place to stand is what makes it that rewrite rather than {@link #with}. A
         * replacement stands where the thing it replaces stood, so a caller choosing somewhere for
         * it would be choosing whether this is a replacement at all.
         */
        public Apply replacedBy(Expr function) {
            return replacedBy(function, args);
        }

        /** The same application, of something else and over rewritten arguments — the rewrite above,
         *  where what is supplied to the new callee is not what was supplied to the old one. */
        public Apply replacedBy(Expr function, List<Expr> args) {
            return new Apply(function, args, origin, applied, application, pos, region);
        }

        /**
         * The same application rewritten and stamped where the copy of it stands — what a pass that
         * copies a body into another one writes. Where the construction came from it carries.
         *
         * <p>What the author applied it takes, because a copy is where that answer stops being one
         * place. The name travels: a call the copy holds applies what it applied wherever the copy
         * is read. Where they wrote it does not, and a copy read against another file carrying a
         * coordinate of the first is a report pointing at a line its reader is not looking at.
         * Which of the two this copy is, the pass making it knows and this does not
         * ({@link AppliedCallee#restamped}).
         */
        public Apply with(AppliedCallee applied, Expr function, List<Expr> args, SourcePos pos,
                          Region region) {
            return new Apply(function, args, origin, applied, application, pos, region);
        }

        /** Whether a value this body named is what carried the construction this stands for in —
         *  asked of the node, which is what holds the answer. */
        public boolean wasCarriedByValue() {
            return Origins.viaValueReference(origin);
        }
    }

    /** {@code origin} is where the comparison was written, which is not always where the fork
     * testing it was: a condition can be an application of a function parameter, and the predicate
     * handed to it is the caller's. Carried so that two predicates written separately stay two lines
     * and one predicate applied twice stays one ({@link SourceConstructOrigin}). */
    record Binary(BinOp op, Expr left, Expr right, SourceConstructOrigin origin, SourcePos pos,
                  Region region) implements Expr {}


    /**
     * {@code e} written over {@code region} instead of whatever it says now — for the one caller
     * that knows a wider stretch of source than the node it is holding.
     *
     * <p>A form the parser reduces away is still characters in the file. {@code (a + 100)} leaves an
     * {@code Hir.Binary} because the parentheses say nothing the tree needs to keep, and they are
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
            case Apply x -> new Apply(x.function(), x.args(), x.origin(), x.applied(),
                    x.application(), x.pos(), region);
            case If x -> new If(x.cond(), x.then(), x.els(), x.origin(), x.pos(), region);
            case IfConstructed x ->
                    new IfConstructed(x.construct(), x.binder(), x.then(), x.els(), x.origin(), x.pos(),
                            region);
            case LetIn x -> new LetIn(x.binder(), x.value(), x.declaredType(), x.annotated(),
                    x.opens(), x.body(), x.pos(), region);
            case Expansion x -> new Expansion(x.callee(), x.application(), x.at(), x.bound(),
                    x.given(),
                    x.declaredReturn(), x.body(), x.pos(), region);
            case Block x -> new Block(x.params(), x.body(), x.rule(), x.pos(), region);
            case ListLit x -> new ListLit(x.elements(), x.origin(), x.pos(), region);
            case RowCollection x -> new RowCollection(x.elements(), x.origin(), x.pos(), region);
            case ListComp x -> new ListComp(x.element(), x.guards(), x.origin(), x.pos(), region);
            case Tuple x -> new Tuple(x.elements(), x.pos(), region);
            case TupleGet x -> new TupleGet(x.tuple(), x.index(), x.arity(), x.pos(), region);
            case NewData x -> new NewData(x.typeName(), x.inits(), x.spreads(), x.origin(),
                    x.fields(), x.pos(), region);
            case Match x -> new Match(x.scrutinee(), x.cases(), x.origin(), x.pos(), region);
        };
    }

    /**
     * {@code e} with each of its slots replaced by what the operator for that slot answers, its own
     * kind and position kept — or {@code e} itself where every slot answered what it was given, so a
     * walk that only reads allocates nothing.
     *
     * <p>The children of an expression occupy two kinds of slot, and they differ in what may stand
     * there. An expression slot takes any expression — an argument, a field's value, the thing an
     * {@code if} tests. A name slot takes only a name: a construction's spread {@code T { ..base }}
     * copies the fields of what the name stands for, so there is nothing there to evaluate and
     * nothing else that could be written.
     *
     * <p>This is the one place that says which slots a node has. Both {@link #mapChildren} and
     * {@link #forEachChild} are derived from it, so a slot a node gains later is written once and
     * neither walk can be left behind. Being exhaustive over {@code Expr}, a node kind added later
     * stops the build here, which is the one place it has to be accounted for.
     */
    private static Expr atSlots(Expr e, UnaryOperator<Expr> atExpr, UnaryOperator<Var> atName) {
        return switch (e) {
            case IntLit x -> x;
            case DecimalLit x -> x;
            case StringLit x -> x;
            case BoolLit x -> x;
            case Var x -> x;
            case Unreachable x -> x;
            case Neg n -> {
                Expr operand = atExpr.apply(n.operand());
                yield operand == n.operand() ? n : new Neg(operand, n.pos(), n.region());
            }
            case FieldAccess fa -> {
                Expr target = atExpr.apply(fa.target());
                yield fa.withTarget(target);
            }
            case Binary b -> {
                Expr left = atExpr.apply(b.left());
                Expr right = atExpr.apply(b.right());
                yield left == b.left() && right == b.right() ? b
                        : new Binary(b.op(), left, right, b.origin(), b.pos(), b.region());
            }
            case Apply a -> {
                Expr function = atExpr.apply(a.function());
                List<Expr> args = each(a.args(), atExpr);
                yield function == a.function() && args == a.args() ? a
                        : new Apply(function, args, a.origin(), a.applied(), a.application(), a.pos(),
                                a.region());
            }
            case If iff -> {
                Expr cond = atExpr.apply(iff.cond());
                Expr then = atExpr.apply(iff.then());
                Expr els = atExpr.apply(iff.els());
                yield cond == iff.cond() && then == iff.then() && els == iff.els() ? iff
                        : new If(cond, then, els, iff.origin(), iff.pos(), iff.region());
            }
            case IfConstructed ic -> {
                Expr construct = atExpr.apply(ic.construct());
                Expr then = atExpr.apply(ic.then());
                List<ElseArm> els = each(ic.els(), arm -> {
                    Expr body = atExpr.apply(arm.body());
                    return body == arm.body() ? arm : arm.with(body);
                });
                yield construct == ic.construct() && then == ic.then() && els == ic.els() ? ic
                        : new IfConstructed(construct, ic.binder(), then, els, ic.origin(), ic.pos(),
                                ic.region());
            }
            case LetIn li -> {
                Expr value = atExpr.apply(li.value());
                Expr body = atExpr.apply(li.body());
                yield value == li.value() && body == li.body() ? li
                        : new LetIn(li.binder(), value, li.declaredType(), li.annotated(),
                                li.opens(), body, li.pos(), li.region());
            }
            // `given` is not a slot. What stands there is caller code that is also inside `body`,
            // wherever the callee applies it, so a walk that took both would read one lambda twice —
            // and rewrite it into two different things. It is read only by whoever asks what the
            // signature said about it.
            case Expansion ex -> {
                List<Bound> bound = each(ex.bound(), b -> {
                    Expr value = atExpr.apply(b.value());
                    return value == b.value() ? b
                            : new Bound(b.binder(), b.declaredType(), value);
                });
                Expr body = atExpr.apply(ex.body());
                yield bound == ex.bound() && body == ex.body() ? ex
                        : new Expansion(ex.callee(), ex.application(), ex.at(), bound, ex.given(),
                                ex.declaredReturn(), body, ex.pos(), ex.region());
            }
            case Block bl -> {
                Expr body = atExpr.apply(bl.body());
                yield body == bl.body() ? bl
                        : new Block(bl.params(), body, bl.rule(), bl.pos(), bl.region());
            }
            case ListLit l -> {
                List<Expr> elements = each(l.elements(), atExpr);
                yield elements == l.elements() ? l
                        : new ListLit(elements, l.origin(), l.pos(), l.region());
            }
            case RowCollection l -> {
                List<Expr> elements = each(l.elements(), atExpr);
                yield elements == l.elements() ? l
                        : new RowCollection(elements, l.origin(), l.pos(), l.region());
            }
            case ListComp comp -> {
                Expr element = atExpr.apply(comp.element());
                List<Expr> guards = each(comp.guards(), atExpr);
                yield element == comp.element() && guards == comp.guards() ? comp
                        : new ListComp(element, guards, comp.origin(), comp.pos(), comp.region());
            }
            case Tuple tup -> {
                List<Expr> elements = each(tup.elements(), atExpr);
                yield elements == tup.elements() ? tup : new Tuple(elements, tup.pos(), tup.region());
            }
            case TupleGet tg -> {
                Expr tuple = atExpr.apply(tg.tuple());
                yield tuple == tg.tuple() ? tg
                        : new TupleGet(tuple, tg.index(), tg.arity(), tg.pos(), tg.region());
            }
            case NewData nd -> {
                // the spreads first: `..base` is written before the fields that replace what it
                // brought, and a walk that records the first thing it sees should see them that way
                List<Var> spreads = each(nd.spreads(), atName);
                List<FieldInit> inits = each(nd.inits(), i -> {
                    Expr value = atExpr.apply(i.value());
                    return i.withValue(value);
                });
                yield spreads == nd.spreads() && inits == nd.inits() ? nd
                        : nd.with(inits, spreads);
            }
            case Match m -> {
                Expr scrutinee = atExpr.apply(m.scrutinee());
                List<Case> cases = each(m.cases(), c -> {
                    Expr body = atExpr.apply(c.body());
                    return body == c.body() ? c
                            : new Case(c.caseTypes(), c.binding(), body, c.unwrapAsserts(), c.pos());
                });
                yield scrutinee == m.scrutinee() && cases == m.cases() ? m
                        : new Match(scrutinee, cases, m.origin(), m.pos(), m.region());
            }
        };
    }

    /** {@code xs} with {@code f} applied to each, or {@code xs} itself where none of them changed. */
    private static <T> List<T> each(List<T> xs, UnaryOperator<T> f) {
        List<T> out = null;
        for (int i = 0; i < xs.size(); i++) {
            T before = xs.get(i);
            T after = f.apply(before);
            if (out == null && after != before) {
                out = new ArrayList<>(xs.subList(0, i));
            }
            if (out != null) {
                out.add(after);
            }
        }
        return out == null ? xs : out;
    }

    /**
     * Rebuilds {@code e} with each of its slots replaced by what the operator for that slot answers;
     * a leaf (a literal, a name) is returned unchanged. The single authoritative rewrite over the
     * expression tree, so an AST-to-AST pass (a Lower desugar, an optimization) writes only the cases
     * it rewrites and delegates the rest here, instead of hand-copying every node type.
     *
     * <p>The two operators are the two kinds of slot. {@code onNameSlot} answers a {@link Var}
     * because that is all a spread may hold, so a rewrite cannot put an expression where the language
     * admits only a name; a pass that does have an expression to put there — the inliner — binds it
     * ahead of the construction and spreads the binding. There is no one-operator form: what a
     * rewrite does to a name is a decision, and it is made where the rewrite is written.
     */
    static Expr mapChildren(Expr e, UnaryOperator<Expr> onExprSlot, UnaryOperator<Var> onNameSlot) {
        return atSlots(e, onExprSlot, onNameSlot);
    }

    /**
     * Applies {@code f} to each direct child of {@code e} (a leaf has none) — the read-only
     * counterpart of {@link #mapChildren}. A visiting pass (a checker walk) delegates its default
     * recursion here rather than hand-copying every node type.
     *
     * <p>A name a spread holds is a child like any other, so a pass that asks what an expression
     * names reaches one without knowing that spreads exist.
     */
    public static void forEachChild(Expr e, java.util.function.Consumer<Expr> f) {
        atSlots(e, child -> {
            f.accept(child);
            return child;
        }, child -> {
            f.accept(child);
            return child;
        });
    }

    /** Rewrites each clause's expression, keeping its name. Every stage that rewrites a declaration's
     * invariant goes through here, so no rewrite can drop the name the failure is classified by. */
    public static List<InvariantClause> mapClauses(List<InvariantClause> clauses, UnaryOperator<Expr> f) {
        List<InvariantClause> out = new ArrayList<>();
        for (InvariantClause clause : clauses) {
            out.add(clause.with(f.apply(clause.expr())));
        }
        return out;
    }

    /** Rewrites each departure's body, keeping the clause it answers. */
    public static List<ElseArm> mapArms(List<ElseArm> arms, UnaryOperator<Expr> f) {
        List<ElseArm> out = new ArrayList<>();
        for (ElseArm arm : arms) {
            out.add(arm.with(f.apply(arm.body())));
        }
        return out;
    }
}
