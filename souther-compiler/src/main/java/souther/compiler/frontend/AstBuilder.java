package souther.compiler.frontend;

import souther.compiler.diag.msg.Reported;
import souther.compiler.diag.msg.Supporting;
import souther.compiler.ast.Ast;
import souther.compiler.types.SourceConstruct;
import souther.compiler.types.SourceConstructOrigin;
import souther.compiler.types.RuleOrigin;
import souther.compiler.types.SourceReferenceOrigin;
import souther.compiler.types.TypeKey;
import souther.compiler.types.WrittenOwner;
import souther.compiler.diag.QuotedFrom;
import souther.compiler.ast.StructuralCost;
import souther.compiler.ast.WrittenName;
import souther.compiler.cst.CstLexer;
import souther.compiler.cst.LineIndex;
import souther.compiler.cst.SyntaxElement;
import souther.compiler.cst.SyntaxKind;
import souther.compiler.cst.SyntaxNode;
import souther.compiler.observe.RowIdentity;
import souther.compiler.cst.SyntaxToken;
import souther.compiler.diag.CompileException;
import souther.compiler.diag.Diagnostic;
import souther.compiler.diag.msg.Message;
import souther.compiler.diag.msg.ParseMessage;
import souther.compiler.diag.msg.InvariantMessage;
import souther.compiler.diag.msg.DataMessage;
import souther.compiler.diag.msg.AttemptMessage;
import souther.compiler.diag.msg.DeclarationMessage;
import souther.compiler.diag.msg.ExampleMessage;
import souther.compiler.diag.msg.BehaviorMessage;
import souther.compiler.diag.Region;
import souther.compiler.diag.SourcePos;
import souther.compiler.diag.Placement;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Builds the compiler's {@link Ast} from a concrete syntax tree. This is where the surface forms the
 * parser deliberately kept lossless are lowered to the desugared AST every later stage expects:
 * {@code |>} folds into a call's last argument, {@code guard} becomes an {@code if}, {@code let}
 * becomes a {@code LetIn}, {@code T?} becomes {@code Option<T>}, and the {@code let}/{@code match}
 * destructurings expand into positional reads. The CST feeds the formatter and the LSP untouched;
 * only the compiler pipeline runs through here.
 */
public final class AstBuilder {

    private final LineIndex lines;
    /**
     * Which text this is reading — part of the owner of anything several texts may write for one
     * behavior, and nothing else's.
     *
     * <p>Not a source identity. A module read back off the module path is a text this compile
     * cannot show and a buffer handed in on its own is one it cannot name, and both are read here
     * like any other.
     */
    private final QuotedFrom text;
    private String moduleName = "";
    /**
     * The reading of each owner this source has written something for.
     *
     * <p>Held rather than made per item, because an owner may be written more than once: a
     * behavior's rows may come in several {@code example} blocks and its stand-in in several
     * {@code fake} blocks, and the second block of an owner carries on the numbering of the first.
     * Made per item, the two blocks would each start from zero and give two constructs one number —
     * and the way out of that is a number for which block, which is the count over the file this
     * whole shape is here to be rid of.
     *
     * <p>Nothing else makes one. There is no numbering state out here for a reading to be handed,
     * and no way to reach a reading without saying what it is reading for.
     */
    private final Map<WrittenOwner, Reading> readings = new LinkedHashMap<>();

    private AstBuilder(String source, Placement read) {
        this.lines = new LineIndex(source, read);
        // Asked of a position, which is the one way there is to ask which text something is in.
        // The placement holds the answer and does not publish it: a caller reading it off the
        // placement would be a second way to reach a classification that is made once.
        this.text = lines.posOf(0).quotedFrom();
    }

    /** What reads {@code owner}'s syntax, made once and handed back after that. */
    private Reading reading(WrittenOwner owner) {
        return readings.computeIfAbsent(owner, Reading::new);
    }

    /**
     * Builds a module from a parsed source file. A header-less source is named
     * {@code defaultModuleName}; a {@code null} default makes the header required. Every position
     * the module carries says what {@code read} says: which source of this compile it is in, and
     * whether that is where the code is. A module read back off the module path is in no source of
     * the compile reading it and is not where its code is, and both halves come from here.
     *
     * <p>Requires a CST the parser accepted: every node it reads has the children the grammar gives
     * it, and a missing one is dereferenced rather than reported. {@link CstFrontend#parse} is the
     * one caller and raises the parser's first error before building — which is why this is
     * package-private, and what a second caller would have to guarantee.
     */
    static Ast.Module build(SyntaxNode sourceFile, String source, String defaultModuleName,
                            Placement read) {
        return new AstBuilder(source, read).module(sourceFile, defaultModuleName);
    }

    // --- module ---

    private Ast.Module module(SyntaxNode file, String defaultModuleNameSpelling) {
        // A header-less source is named by its caller — the CLI's file stem, an
        // annotation processor, a test — which is a name arriving from outside.
        String defaultModuleName = souther.compiler.Reserved.name(defaultModuleNameSpelling);
        Optional<SyntaxNode> exampleFile = file.child(SyntaxKind.EXAMPLES_FILE_HEADER);
        if (exampleFile.isPresent()) {
            return exampleFileModule(file, exampleFile.get());
        }
        Optional<SyntaxNode> header = file.child(SyntaxKind.MODULE_HEADER);
        String name;
        SourcePos pos;
        Map<String, Ast.RetType> exposedOutputs = new HashMap<>();
        List<String> exposing = new ArrayList<>();
        if (header.isPresent()) {
            SyntaxNode h = header.get();
            name = qualifiedNameText(h.child(SyntaxKind.QUALIFIED_NAME).orElseThrow());
            pos = pos(h);
        } else if (defaultModuleName != null) {
            // The name written in a header was read by the scan and is a name by that. This one was
            // read by nothing — a file's stem, or what an embedding handed in — so it is held to
            // the alphabet here, where it becomes the module's name.
            for (String part : defaultModuleName.split("\\.", -1)) {
                if (!souther.compiler.cst.IdentifierAlphabet.isName(part)) {
                    throw error(pos(file),
                            new ParseMessage.ASourceIsNamedAfterSomethingThatIsNotAName(
                                    defaultModuleName));
                }
            }
            name = defaultModuleName;
            pos = pos(file);
        } else {
            throw error(pos(file), new ParseMessage.ASourceFileStartsWithAModuleDeclaration());
        }
        moduleName = name;   // set before any type is read, so type-variable gating knows the namespace
        header.flatMap(h -> h.child(SyntaxKind.EXPOSING_CLAUSE))
                .ifPresent(c -> readExposing(c, exposing, exposedOutputs));

        List<Ast.Import> imports = new ArrayList<>();
        List<Ast.Def> defs = new ArrayList<>();
        List<Ast.BehaviorDef> behaviors = new ArrayList<>();
        List<Ast.FnDef> fns = new ArrayList<>();
        List<Ast.Example> examples = new ArrayList<>();
        List<Ast.Fake> fakes = new ArrayList<>();
        for (SyntaxNode n : file.childNodes()) {
            switch (n.kind()) {
                case IMPORT_DECL -> imports.add(importDecl(n));
                case DATA_DEF -> {
                    WrittenName declared = nameOf(firstIdentToken(n));
                    defs.add(reading(new WrittenOwner.Declaration(
                            new TypeKey(moduleName, declared.canonical()))).dataDef(n, declared));
                }
                case BEHAVIOR_DEF -> {
                    WrittenName declared = nameOf(firstIdentToken(n));
                    behaviors.add(reading(new WrittenOwner.Stated(moduleName, declared.canonical()))
                            .behaviorDef(n, declared));
                }
                case FN_DEF -> {
                    WrittenName declared = nameOf(firstIdentToken(n));
                    fns.add(reading(new WrittenOwner.Body(moduleName, declared.canonical()))
                            .fnDef(n, declared));
                }
                case EXAMPLE_DEF -> {
                    SyntaxToken target = exampleTarget(n);
                    String named = target == null ? "" : ident(target);
                    examples.add(reading(new WrittenOwner.Examples(text, moduleName, named))
                            .example(n, target, named));
                }
                case FAKE_DEF -> {
                    fakes.add(reading(new WrittenOwner.Fake(text, moduleName, nameAfter(n, 1)))
                            .fake(n));
                }
                default -> { /* MODULE_HEADER handled above; ERROR nodes are reported already */ }
            }
        }
        // A source file declares; what the module takes on to emit follows from what its bodies reach
        // and is worked out where that is known, which is not here.
        return new Ast.Module(name, exposing, exposedOutputs, imports, defs, behaviors, fns,
                List.of(), examples, fakes, null, pos);
    }

    /**
     * An {@code examples for <module>} file: what its target module's rows are written in. It holds the
     * rows, the fakes they run against, and the values those rows name; anything else in it is E1906. The
     * returned {@link Ast.Module} carries those and its {@code exampleFileTarget}; the compiler merges it
     * into the target.
     *
     * <p>A value is a {@code let} with no parameter list, and it is here because a row that names one had
     * nowhere in this file to declare it — so the fixture went back to the model file, which is the file
     * the rows exercise rather than the one they are written in (issue #210). A {@code let} with
     * parameters is a helper, which is a method the target module emits, and a companion file does not
     * add to what the model compiles to.
     */
    private Ast.Module exampleFileModule(SyntaxNode file, SyntaxNode header) {
        String target = qualifiedNameText(header.child(SyntaxKind.QUALIFIED_NAME).orElseThrow());
        moduleName = target;
        SourcePos pos = pos(header);
        List<Ast.Example> examples = new ArrayList<>();
        List<Ast.Fake> fakes = new ArrayList<>();
        List<Ast.FnDef> values = new ArrayList<>();
        for (SyntaxNode n : file.childNodes()) {
            switch (n.kind()) {
                case EXAMPLE_DEF -> {
                    SyntaxToken exampled = exampleTarget(n);
                    String named = exampled == null ? "" : ident(exampled);
                    examples.add(reading(new WrittenOwner.Examples(text, moduleName, named))
                            .example(n, exampled, named));
                }
                case FAKE_DEF -> {
                    fakes.add(reading(new WrittenOwner.Fake(text, moduleName, nameAfter(n, 1)))
                            .fake(n));
                }
                case EXAMPLES_FILE_HEADER -> { /* the header itself */ }
                case FN_DEF -> {
                    WrittenName declared = nameOf(firstIdentToken(n));
                    Ast.FnDef fn = reading(new WrittenOwner.Body(moduleName, declared.canonical()))
                            .fnDef(n, declared);
                    if (!fn.params().isEmpty()) {
                        throw onlyExamples(n);
                    }
                    // Read as what this file wrote it as. Its values join the module its rows join,
                    // so from here on it sits among the module's own definitions under one set of
                    // names, and nothing about the definition would say which of the two files
                    // wrote it. Everything that turns on that reads this.
                    values.add(fn.asAnAttachedValue());
                }
                case IMPORT_DECL, DATA_DEF, BEHAVIOR_DEF -> throw onlyExamples(n);
                default -> { /* ERROR nodes already reported */ }
            }
        }
        return new Ast.Module(target, List.of(), new HashMap<>(), List.of(), List.of(), List.of(),
                values, List.of(), examples, fakes, target, pos);
    }

    private CompileException onlyExamples(SyntaxNode n) {
        return CompileException.of(Diagnostic.at(pos(n)).say(new ExampleMessage.AnExamplesFileHoldsOnlyExamples()).build());
    }

    private void readExposing(SyntaxNode clause, List<String> names, Map<String, Ast.RetType> outputs) {
        for (SyntaxNode entry : childNodes(clause, SyntaxKind.EXPOSED_ENTRY)) {
            String name = qualifiedNameText(entry.child(SyntaxKind.QUALIFIED_NAME).orElseThrow());
            names.add(name);
            entry.child(SyntaxKind.RET_TYPE).ifPresent(rt -> outputs.put(name, retType(rt)));
        }
    }

    private Ast.Import importDecl(SyntaxNode n) {
        String module = qualifiedNameText(n.child(SyntaxKind.QUALIFIED_NAME).orElseThrow());
        String alias = n.child(SyntaxKind.IMPORT_ALIAS)
                .map(a -> ident(identTokens(a).get(0))).orElse(null);
        List<Ast.ImportedName> names = new ArrayList<>();
        n.child(SyntaxKind.NAME_LIST).ifPresent(list -> {
            for (SyntaxToken t : identTokens(list)) {
                names.add(new Ast.ImportedName(nameOf(t)));
            }
        });
        return new Ast.Import(module, alias, names, pos(n));
    }

    /**
     * The dotted name written at {@code from} in {@code n}'s meaningful children.
     *
     * <p>The text and not a reference: this is what says which owner wrote the block, and the owner
     * is what a reference of it is counted within. Empty where the parser recovered from a form with
     * no name at that position — what is wrong with the text was said where it was read.
     */
    private String nameAfter(SyntaxNode n, int from) {
        List<SyntaxElement> es = meaningful(n);
        if (from >= es.size() || !isToken(es.get(from), SyntaxKind.IDENT)) {
            return "";
        }
        int[] at = {from};
        return dottedName(es, at).name().canonical();
    }

    /** A name as the source wrote it — bare, or qualified through a module or an import alias — read
     * from a run of tokens the parser did not wrap in a node. Advances {@code at} past the name, and
     * positions the name at its first identifier so a diagnostic points at the name, not the clause. */
    private Ast.Name dottedName(List<SyntaxElement> es, int[] at) {
        List<SyntaxToken> parts = new ArrayList<>();
        parts.add((SyntaxToken) es.get(at[0]++));
        while (at[0] + 1 < es.size() && isToken(es.get(at[0]), SyntaxKind.DOT)
                && isToken(es.get(at[0] + 1), SyntaxKind.IDENT)) {
            at[0]++;                              // .
            parts.add((SyntaxToken) es.get(at[0]++));
        }
        return Ast.Name.written(joined(parts));
    }

    /** The comma-separated names of a {@code constructs}/{@code depends on} clause, each possibly
     * qualified by its module. {@code skipIdents} drops the identifiers that belong to the keyword
     * rather than to the list — the {@code on} of {@code depends on} lexes as one. */
    private List<Ast.Name> dottedNames(SyntaxNode clause, int skipIdents) {
        List<Ast.Name> out = new ArrayList<>();
        List<SyntaxElement> es = meaningful(clause);
        int[] at = {1 + skipIdents};              // past the clause keyword
        while (at[0] < es.size()) {
            if (isToken(es.get(at[0]), SyntaxKind.COMMA)) {
                at[0]++;
                continue;
            }
            out.add(dottedName(es, at));
        }
        return out;
    }

    /** Which token names the behavior an {@code example} block writes rows for. The contextual
     *  {@code example} lexes as an identifier, so the target is the second identifier token; where
     *  the parser recovered from a block with no target there is none. */
    private SyntaxToken exampleTarget(SyntaxNode n) {
        List<SyntaxToken> idents = identTokens(n);
        return idents.size() >= 2 ? idents.get(1) : null;
    }

    // --- who a top-level item is written by ---
    //
    // Read once, here, and handed to the reading and to what it builds. What numbers an item's
    // constructs is settled by which item it is, so the two are one reading of one token: read
    // again below, the owner counting the numbers and the declaration carrying them could be told
    // apart by nothing.
    //
    // The address is the one the syntax gives, which is what a parse has. Whether a `let`
    // implements a behavior is a question about the module around it.

    // --- types ---
    //
    // Written types hold no expression, so nothing here is numbered and none of it belongs to an
    // owner. It is read for the exposing clause as well as for a declaration, which is why it sits
    // out here rather than inside a reading.

    private Ast.FnType fnType(SyntaxNode n) {
        List<Ast.RetType> params = new ArrayList<>();
        Ast.RetType result = null;
        boolean afterArrow = false;
        for (SyntaxElement e : meaningful(n)) {
            if (e instanceof SyntaxToken t && t.kind() == SyntaxKind.ARROW) {
                afterArrow = true;
            } else if (e instanceof SyntaxNode c && c.kind() == SyntaxKind.RET_TYPE) {
                if (afterArrow) {
                    result = retType(c);
                } else {
                    params.add(retType(c));
                }
            }
        }
        return new Ast.FnType(params, result, pos(n));
    }

    private Ast.RetType retType(SyntaxNode n) {
        List<Ast.TypeTerm> cases = new ArrayList<>();
        for (SyntaxNode c : n.childNodes()) {
            if (isTypeTermKind(c.kind())) {
                cases.add(typeTerm(c));
            }
        }
        if (n.token(SyntaxKind.QUESTION).isPresent()) {
            cases = List.of(optional(cases, pos(n)));
        }
        return new Ast.RetType(cases, cases.get(0).pos());
    }

    private static boolean isTypeTermKind(SyntaxKind k) {
        return k == SyntaxKind.TYPE_REF || k == SyntaxKind.TUPLE_TYPE || k == SyntaxKind.FN_TYPE;
    }

    /** One term of a written type: a function type, or a reference. A parenthesised single term is
     * grouping, so {@code ((Int) -> Bool)} is the function type it wraps rather than a one-tuple. */
    private Ast.TypeTerm typeTerm(SyntaxNode n) {
        if (n.kind() == SyntaxKind.FN_TYPE) {
            return fnType(n);
        }
        if (n.kind() == SyntaxKind.TUPLE_TYPE) {
            List<SyntaxNode> elems = new ArrayList<>();
            for (SyntaxNode c : n.childNodes()) {
                if (isTypeTermKind(c.kind())) {
                    elems.add(c);
                }
            }
            if (elems.size() == 1) {
                return typeTerm(elems.get(0));
            }
        }
        return typeRef(n);
    }

    /**
     * {@code T?} in a signature — a stdlib combinator that consumes an optional says so in its own
     * type ({@code List.filterMap(f: ('a) -> 'b?, …)}). Like a type variable it is written only in the
     * shipped core: a user still writes an optional on a field and never names one anywhere else
     * (ADR-0011), so the type stays out of the surface language. A sum cannot take the mark — an
     * absent {@code A | B} has no case to be absent as.
     */
    private Ast.TypeRef optional(List<Ast.TypeTerm> cases, SourcePos pos) {
        if (!isReservedNamespace(moduleName)) {
            throw errorWithHint(pos, new ParseMessage.AnOptionalIsOnlyWrittenOnAFieldOrInTheCore(),
                    new ParseMessage.LeaveTheTypeOffAndTheOptionalIsInferred());
        }
        if (cases.size() > 1) {
            throw error(pos, new ParseMessage.AQuestionMarkFollowsASumOfCases(
                    String.valueOf(cases.size())));
        }
        // `T?` is `Option<T>` for whatever T is: the two spellings are one type, and what may
        // stand in a position is decided by what the position requires of that type, not here
        return Ast.TypeRef.written("Option", cases.get(0), cases.get(0).pos());
    }

    private Ast.TypeRef typeRef(SyntaxNode n) {
        if (n.kind() == SyntaxKind.TUPLE_TYPE) {
            List<Ast.TypeTerm> elems = new ArrayList<>();
            for (SyntaxNode c : n.childNodes()) {
                if (isTypeTermKind(c.kind())) {
                    elems.add(typeTerm(c));
                }
            }
            if (elems.size() == 1 && elems.get(0) instanceof Ast.TypeRef only) {
                return only;   // `(T)` reads as grouping
            }
            return Ast.TypeRef.written(null, null, elems, pos(n));
        }
        Optional<SyntaxToken> typevar = n.token(SyntaxKind.TYPEVAR);
        if (typevar.isPresent()) {
            String v = souther.compiler.Reserved.name(typevar.get().text());
            if (!isReservedNamespace(moduleName)) {
                throw error(pos(n), new ParseMessage.ATypeVariableIsOnlyAllowedInTheCore(v));
            }
            return Ast.TypeRef.written(v, null, pos(n));   // name begins with `'` → Type.Var
        }
        WrittenName written = qualifiedNameOf(n);   // `Amount`, `example.billing.Amount`, `B.Amount`
        String name = written.canonical();
        Optional<SyntaxNode> args = n.child(SyntaxKind.TYPE_ARGS);
        if (args.isEmpty()) {
            return Ast.TypeRef.written(written, null, null);
        }
        List<Ast.TypeTerm> typeArgs = new ArrayList<>();
        for (SyntaxNode c : args.get().childNodes()) {
            if (isTypeTermKind(c.kind())) {
                typeArgs.add(typeTerm(c));
            }
        }
        if (typeArgs.isEmpty()) {
            return Ast.TypeRef.written(written, null, null);   // the missing argument is reported by name
        }
        if (name.equals("Map")) {
            // carry the value in `arg` and the key in `tupleElems` (ADR-0040)
            Ast.TypeTerm key = typeArgs.get(0);
            Ast.TypeTerm value = typeArgs.get(typeArgs.size() - 1);
            return Ast.TypeRef.written(written, value, List.of(key));
        }
        // List<T> / Set<T> / Option<T>
        return Ast.TypeRef.written(written, typeArgs.get(0), null);
    }

    /**
     * One {@link WrittenOwner}'s syntax, and every number handed out while reading it.
     *
     * <p>What a number means is settled by what it was counted within, so the two are one object: a
     * count kept beside an owner is a count that can be taken under the wrong one, and a count kept
     * over the file is one that moves when anything else in the file does. There is no way to reach
     * a number here without an owner, because there is no way to make one of these without an owner
     * and nothing outside makes them.
     *
     * <p>Read by owner and not by top-level item. A behavior's rows may be written in more than one
     * {@code example} block, and its stand-in in more than one {@code fake} block, so the second
     * block of an owner goes on where the first left off — the alternative is a number to say which
     * block, which is the count over the file this exists to be rid of. The builder holds one of
     * these per owner and hands the same one back.
     *
     * <p>Everything an author writes inside a top-level item is read here, which is what leaves the
     * builder around it with nothing to number: a comprehension, a fork, a pattern and a block are
     * forms this reads, and a counter added out there would have nothing to count.
     */
    private final class Reading {

        private final WrittenOwner owner;

        /**
         * How many references this owner has been read to hold, which is what numbers the next one
         * ({@link SourceReferenceOrigin}).
         *
         * <p>Its own counter, for the reason the block's is: a reference is not a construct, and one
         * counter for both would make each one's numbers turn on how many of the other stood before
         * it. Within the owner, for the reason every other number here is: what is written beside a
         * definition does not number what the definition wrote.
         */
        private int referenceCounter;

        /**
         * Which reference of this owner the next one is.
         *
         * <p>Taken here for the reason a construct's number is taken where the construct is read:
         * this is the one place reading the syntax, and everything below reads a tree where a name
         * may have been respelled, copied into another module, or resolved to what it reaches. None
         * of those tells one occurrence from another, and a reader that wanted to would have
         * nothing left but where the characters are — which is where a complaint belongs and is not
         * what a reference is.
         *
         * <p>A name a desugaring writes takes one too. It stands where the author wrote a form that
         * holds it, so it is a reference of this source however it is spelled — and leaving it
         * without one would make the field a question every reader has to ask before it may use it.
         */
        private SourceReferenceOrigin reference() {
            return new SourceReferenceOrigin(owner, referenceCounter++);
        }

        /**
         * The dotted name written at {@code from} in {@code n}'s meaningful children, as a behavior
         * reference.
         *
         * <p>Empty where the parser recovered from a form with no name at that position. A name is
         * what the following passes ask about, so there has to be one to ask about; what is wrong
         * with the text was said where it was read.
         */
        private Ast.Var behaviorNameAfter(SyntaxNode n, int from) {
            List<SyntaxElement> es = meaningful(n);
            if (from >= es.size() || !isToken(es.get(from), SyntaxKind.IDENT)) {
                return Ast.Var.desugared("", pos(n), reference());
            }
            int[] at = {from};
            return Ast.Var.written(dottedName(es, at).name(), reference());
        }

        /**
         * How many coverage-bearing constructs this owner has been read to hold, which is what
         * numbers the next one ({@link SourceConstructOrigin}).
         *
         * <p>One number per construct the author wrote, not per fork object built. An {@code if}
         * that is written as an attempted construction is one construct however many of the three
         * shapes below it takes, and a comprehension is one construct whose guards derive their
         * forks from it — so the number is taken once, where the construct is recognised.
         */
        private int constructCounter;
        /** How many blocks this owner has been read to hold, which is what numbers the next one.
         *  Counted apart from the constructs: a block is not a fork, and one counter for both would
         *  make each one's numbers turn on how many of the other stood before it. */
        private int ruleCounter;
        /**
         * How many rows this owner has been read to write, which is what numbers the next one
         * ({@link RowIdentity.Unnamed}).
         *
         * <p>The owner is one behavior's rows in one source, so this is per behavior and per source
         * without anything here having to say so: a behavior exampled in two blocks of one file has
         * one run of numbers, and one exampled here and in an attached file has a first row in each.
         */
        private int rowCounter;

        /**
         * The names a lowering mints for the binders it needs, kept apart from each other within
         * the owner being read.
         *
         * <p>Within the owner and not within the file, and nothing turns on the difference. What a
         * spelling has to do is not be captured by, or capture, a name written beside it in one
         * scope — and two owners' forms are never in one scope by being written in one file. What
         * does put one definition's code inside another's scope is a helper spliced into a call,
         * and the inliner α-renames what it splices, because a helper written in another module was
         * already numbered from zero in its own file. So file-wide uniqueness was never what kept
         * these apart, and narrowing it to the owner takes nothing away.
         */
        private int matchWholeCounter;
        private int tupleCounter;
        private int getterCounter;
        private int patternCounter;
        private int spreadCounter;

        private Reading(WrittenOwner owner) {
            this.owner = owner;
        }

        /**
         * The next construct of this owner, said as what the author wrote it as.
         *
         * <p>Called once where a construct is recognised, never once per node it is built as.
         *
         * <p>The kind is taken here and nowhere else. This is the one place that has the syntax in
         * front of it — every stage below reads a tree that has already been lowered, where a
         * {@code guard}, an {@code if} and a comprehension's condition are the same node.
         *
         * <p><b>Taking a number is not placing a probe.</b> What a run is recorded at is settled by
         * the coverage plan out of what the catalog holds; this hands out the identity a source
         * construct keeps through every copy of it. A construct numbered here and instrumented
         * nowhere is the arithmetic among the binary expressions, and it is a rule about the
         * strings at a position — which owes rows for the classes it tells a position into and has
         * no run to record.
         */
        private SourceConstructOrigin construct(SourceConstruct kind) {
            return SourceConstructOrigin.written(owner, constructCounter++, kind);
        }

        /** Which block of this owner the next one is. Taken here for the same reason the construct's
         *  number is: this is the one place reading the syntax, and a copy has to carry it. */
        private RuleOrigin rule() {
            return RuleOrigin.written(owner, ruleCounter++);
        }

    // --- data ---

    private Ast.Def dataDef(SyntaxNode n, WrittenName declared) {
        String name = declared.canonical();
        SourcePos pos = pos(n);
        List<Ast.InvariantClause> clauses = invariants(n, name);

        Optional<SyntaxNode> product = n.child(SyntaxKind.PRODUCT_BODY);
        if (product.isPresent()) {
            List<Ast.Name> includes = new ArrayList<>();
            List<Ast.Field> fields = new ArrayList<>();
            for (SyntaxNode member : product.get().childNodes()) {
                if (member.kind() == SyntaxKind.SPREAD_MEMBER) {
                    SyntaxToken included = identTokens(member).get(0);
                    includes.add(Ast.Name.written(nameOf(included)));
                } else if (member.kind() == SyntaxKind.FIELD) {
                    fields.add(field(member));
                }
            }
            // `data T = { }` names a type with one value, which is what a unit data is — but it is
            // built as `T {}` where a unit is built by name, so the two spellings mean the same
            // thing and reject each other's construction. One way to write it (spec §unit-data).
            if (includes.isEmpty() && fields.isEmpty()) {
                throw CompileException.of(Diagnostic
                                .at(bodyRegion(product.get()))
                                .hint(new DataMessage.WriteItAsAUnitDataOrGiveItFields(name)).say(new DataMessage.ADataWithAnEmptyBody(name)).build());
            }
            return new Ast.Data(declared, moduleName, false, includes, fields, clauses, pos);
        }
        Optional<SyntaxNode> sum = n.child(SyntaxKind.SUM_BODY);
        if (sum.isPresent()) {
            // Nothing constructs a sum — a value of one is written as one of its cases — so a clause
            // here would be owed by no construction
            // (spec §an-invariant-is-declared-where-a-construction-owes-it). Refused where the
            // clause is still in hand: `Ast.SumData` has no slot for one, and past this point what
            // was written and what was dropped read alike.
            for (SyntaxNode clause : childNodes(n, SyntaxKind.INVARIANT_CLAUSE)) {
                throw CompileException.of(Diagnostic
                        .at(pos(clause))
                        .hint(new InvariantMessage.WriteItOnACaseOrOnANewtypeOverTheSum(name))
                        .say(new InvariantMessage.ASumIsNeverConstructed(name)).build());
            }
            List<Ast.Name> cases = new ArrayList<>();
            for (SyntaxToken t : identTokens(sum.get())) {
                cases.add(Ast.Name.written(nameOf(t)));
            }
            return new Ast.SumData(declared, moduleName, cases, pos);
        }
        Optional<SyntaxNode> newtype = n.child(SyntaxKind.NEWTYPE_BODY);
        if (newtype.isPresent()) {
            SyntaxNode inner = typeChild(newtype.get());
            Ast.TypeRef innerType = typeRef(inner);
            if (newtype.get().token(SyntaxKind.QUESTION).isPresent()) {
                innerType = Ast.TypeRef.written("Option", innerType, innerType.pos());   // `Y?` → Option<Y>
            }
            List<Ast.Field> fields = List.of(new Ast.Field("value", innerType, pos(inner)));
            return new Ast.Data(declared, moduleName, true, List.of(), fields, clauses, pos);
        }
        // No body of any kind: a unit data, which has no fields for an invariant to observe (spec
        // §unit-data). The parser takes an `invariant` clause after any data, so this is where a
        // clause that has nothing to constrain is refused — reaching `Ast.UnitData`, which has no
        // slot for one, would silently drop it and with it any error inside it.
        for (SyntaxNode clause : childNodes(n, SyntaxKind.INVARIANT_CLAUSE)) {
            throw CompileException.of(Diagnostic
                            .at(pos(clause)).say(new InvariantMessage.AUnitDataHasNothingToObserve(name)).build());
        }
        return new Ast.UnitData(declared, moduleName, pos);
    }

    /**
     * Every {@code invariant} clause in the order it is written; each must hold. A clause keeps the
     * name written for it, which is what an attempt's departure arm and a boundary issue read.
     *
     * <p>The name is the clause's own identifier, so two clauses of one declaration cannot share one:
     * an arm naming it would answer neither rule in particular.
     */
    private List<Ast.InvariantClause> invariants(SyntaxNode dataDef, String typeName) {
        List<Ast.InvariantClause> out = new ArrayList<>();
        Set<String> named = new HashSet<>();
        for (SyntaxNode clause : childNodes(dataDef, SyntaxKind.INVARIANT_CLAUSE)) {
            Ast.Expr expr = expr(onlyExpr(clause));
            Optional<String> name = Optional.empty();
            if (clause.token(SyntaxKind.ASSIGN).isPresent()) {
                SyntaxToken label = identTokens(clause).get(0);
                // `_` is what an attempt writes for the clauses that carry no name, so a clause named
                // `_` could not be answered by name at all: the arm reading it would be that wildcard.
                // Refused here rather than left to be discovered at the attempt.
                if (ident(label).equals("_")) {
                    throw CompileException.of(Diagnostic
                                    .at(posOf(label))
                                    .hint(new InvariantMessage.NameTheClauseOrLeaveItUnnamed()).say(new InvariantMessage.UnderscoreCannotNameAClause(typeName)).build());
                }
                if (!named.add(ident(label))) {
                    throw CompileException.of(Diagnostic
                                    .at(posOf(label)).say(new InvariantMessage.TwoClausesShareOneName(ident(label), typeName)).build());
                }
                name = Optional.of(ident(label));
            }
            out.add(new Ast.InvariantClause(name, expr, pos(clause), region(clause)));
        }
        return out;
    }

    private Ast.Field field(SyntaxNode n) {
        Ast.TypeTerm type = typeTerm(typeChild(n));
        if (n.token(SyntaxKind.QUESTION).isPresent()) {
            type = Ast.TypeRef.written("Option", type, type.pos());   // `T?` → Option<T>
        }
        return new Ast.Field(nameOf(firstIdentToken(n)), type);
    }

    // --- behavior ---

    private Ast.BehaviorDef behaviorDef(SyntaxNode n, WrittenName declared) {
        SourcePos pos = pos(n);
        Optional<SyntaxNode> sig = n.child(SyntaxKind.BEHAVIOR_SIG);
        if (sig.isPresent()) {
            SyntaxNode s = sig.get();
            List<Ast.Param> params = new ArrayList<>();
            s.child(SyntaxKind.PARAM_LIST).ifPresent(pl -> {
                for (SyntaxNode p : childNodes(pl, SyntaxKind.PARAM)) {
                    params.add(new Ast.Param(nameOf(firstIdentToken(p)),
                            retType(p.child(SyntaxKind.RET_TYPE).orElseThrow())));
                }
            });
            Ast.RetType ret = retType(s.child(SyntaxKind.RET_TYPE).orElseThrow());
            List<Ast.Name> constructs = new ArrayList<>();
            List<Ast.Var> dependsOn = new ArrayList<>();
            List<Ast.EnsuresClause> ensures = new ArrayList<>();
            Set<String> namedEnsures = new HashSet<>();
            for (SyntaxNode clause : s.childNodes()) {
                // either clause may name through a module, so the idents of one name are joined and
                // a comma starts the next
                if (clause.kind() == SyntaxKind.CONSTRUCTS_CLAUSE) {
                    constructs.addAll(dottedNames(clause, 0));
                } else if (clause.kind() == SyntaxKind.DEPENDS_CLAUSE) {
                    // one ident past the keyword is the `on` of `depends on`, which lexes as an
                    // ordinary identifier and is no part of the list
                    for (Ast.Name dep : dottedNames(clause, 1)) {
                        dependsOn.add(Ast.Var.written(dep.name(), reference()));
                    }
                } else if (clause.kind() == SyntaxKind.ENSURES_CLAUSE) {
                    // Reported at the name, which is what the rule is about — as a data's clause
                    // name is (see `invariants`). The clause's own position is the `ensures`, and
                    // underlining that would leave a reader to find which word was meant.
                    if (clause.token(SyntaxKind.ASSIGN).isPresent()) {
                        SyntaxToken label = identTokens(clause).get(0);
                        if (ident(label).equals("_")) {
                            throw CompileException.of(Diagnostic.at(posOf(label))
                                    .say(new BehaviorMessage.UnderscoreCannotNameAnEnsuresClause(
                                            declared.canonical())).build());
                        }
                        if (!namedEnsures.add(ident(label))) {
                            throw CompileException.of(Diagnostic.at(posOf(label))
                                    .say(new BehaviorMessage.TwoEnsuresClausesShareOneName(
                                            ident(label), declared.canonical())).build());
                        }
                    }
                    ensures.add(ensuresClause(clause));
                }
            }
            return new Ast.SpecBehavior(declared, params, ret, constructs, dependsOn, ensures, pos);
        }
        SyntaxNode pipe = n.child(SyntaxKind.PIPE_BEHAVIOR).orElseThrow();
        if (n.child(SyntaxKind.ENSURES_CLAUSE).isPresent()) {
            throw CompileException.of(Diagnostic.at(pos(n.child(SyntaxKind.ENSURES_CLAUSE).orElseThrow()))
                    .say(new BehaviorMessage.ACompositionCarriesAnEnsures(declared.canonical())).build());
        }
        List<Ast.Var> stages = new ArrayList<>();
        for (SyntaxNode st : childNodes(pipe, SyntaxKind.STAGE)) {
            stages.add(Ast.Var.written(qualifiedNameOf(st), reference()));
        }
        Ast.RetType declaredOut = pipe.child(SyntaxKind.RET_TYPE).map(AstBuilder.this::retType).orElse(null);
        return new Ast.PipeBehavior(declared, stages, declaredOut, pos);
    }

    private Ast.EnsuresClause ensuresClause(SyntaxNode clause) {
        Optional<String> name = Optional.empty();
        if (clause.token(SyntaxKind.ASSIGN).isPresent()) {
            name = Optional.of(ident(identTokens(clause).get(0)));
        }
        List<Ast.EnsuresArm> arms = new ArrayList<>();
        for (SyntaxNode arm : childNodes(clause, SyntaxKind.ENSURES_ARM)) {
            List<Ast.Name> cases = new ArrayList<>();
            for (SyntaxNode qn : childNodes(arm, SyntaxKind.QUALIFIED_NAME)) {
                cases.add(Ast.Name.written(qualifiedNameOf(qn)));
            }
            arms.add(new Ast.EnsuresArm(cases, expr(onlyExpr(arm)), pos(arm), region(arm)));
        }
        if (arms.isEmpty()) {
            Ast.Expr condition = expr(onlyExpr(clause));
            arms.add(new Ast.EnsuresArm(List.of(), condition, pos(clause), region(clause)));
        }
        return new Ast.EnsuresClause(name, List.copyOf(arms), pos(clause), region(clause));
    }

    // --- fn ---

    private Ast.FnDef fnDef(SyntaxNode n, WrittenName declared) {
        SourcePos pos = pos(n);
        List<Ast.FnParam> params = new ArrayList<>();
        // parallel to params: the pattern a parameter was written as, or null where it was a name
        List<SyntaxNode> paramPatterns = new ArrayList<>();
        n.child(SyntaxKind.FN_PARAM_LIST).ifPresent(pl -> {
            for (SyntaxNode p : childNodes(pl, SyntaxKind.FN_PARAM)) {
                SyntaxNode pat = optionalPatternChild(p);
                paramPatterns.add(pat);
                params.add(fnParam(p, pat));
            }
        });
        Ast.RetType declaredReturn = n.child(SyntaxKind.RET_TYPE).map(AstBuilder.this::retType).orElse(null);
        boolean partial = n.child(SyntaxKind.PARTIAL_MODIFIER).isPresent();
        Optional<SyntaxNode> privateModifier = n.child(SyntaxKind.PRIVATE_MODIFIER);
        if (privateModifier.isPresent() && !isReservedNamespace(moduleName)) {
            // Like `intrinsic`, and for the same reason: what the standard library keeps to itself
            // is the library's own business, and a user module has no surface to hide anything from
            // — everything it declares is published (ADR-0075).
            throw error(pos(privateModifier.get()), new ParseMessage.PrivateIsACorePrivilege());
        }
        Ast.Modifiers modifiers = new Ast.Modifiers(partial, privateModifier.isPresent());

        Optional<SyntaxNode> intrinsic = n.child(SyntaxKind.INTRINSIC_BODY);
        if (intrinsic.isPresent()) {
            if (!isReservedNamespace(moduleName)) {
                throw error(pos, new ParseMessage.IntrinsicIsACorePrivilege());
            }
            String key = stringValue(intrinsic.get().token(SyntaxKind.STRING_LIT).orElseThrow().text());
            return new Ast.FnDef(declared, moduleName, params, declaredReturn,
                    new Ast.FnBody.Intrinsic(key), modifiers, pos);
        }
        SyntaxNode bodyNode = onlyExpr(n);
        Ast.Expr body = expr(bodyNode);
        // `let f = (x) -> e` is the parameter-list form written the other way round: the parameters
        // move to the left of `=` and the two spellings settle to one definition. A definition that
        // already wrote parameters keeps a lambda body as its result. Only a lambda the source wrote
        // moves — a `.field` getter is a block too, but its parameter is synthesized, and lifting it
        // would name a definition's parameter something the author never wrote.
        //
        // A written function type moves nothing either. It says what the definition is, and what it
        // says is a function — so the definition is a value of that type, and lifting its parameters
        // out would leave the type describing something the definition no longer is.
        if (params.isEmpty() && bodyNode.kind() == SyntaxKind.LAMBDA_EXPR
                && (declaredReturn == null || declaredReturn.asFn() == null)
                && body instanceof Ast.Block lambda) {
            for (Ast.Binder p : lambda.params()) {
                params.add(new Ast.FnParam(p, null, false));
            }
            body = lambda.body();
        }
        // a pattern parameter took a fresh name above; it opens itself at the top of the body, so
        // the helper still takes plain names and nothing downstream sees a pattern
        for (int i = paramPatterns.size() - 1; i >= 0; i--) {
            SyntaxNode pat = paramPatterns.get(i);
            if (pat != null) {
                // positioned on the pattern: what a complaint about it has to name is the parameter
                // the author wrote, not the definition it sits in
                SourcePos at = pos(pat);
                // What the pattern lowers to holds the body, so it covers what the body covers.
                body = bindPattern(pat, Ast.Var.desugared(params.get(i).name(), at, reference()), body, at,
                        body.region());
            }
        }
        // What the definition says, measured on what was built for it. Folding a block writes a
        // level per structural step and folding a pattern writes one per binding and one to take
        // them out of the value, which is what those cost (spec
        // [#source-structural-complexity-is-bounded]) — so this is the source's number, arrived at
        // the only place it is ever arrived at.
        int costs = StructuralCost.of(body);
        if (costs > StructuralCost.MAX) {
            throw errorWithHint(pos,
                    new DeclarationMessage.ADefinitionIsMoreStructureThanIsHeld(
                            declared.spelling(), costs, StructuralCost.MAX),
                    new DeclarationMessage.WriteItAsABehaviorOfItsOwn());
        }
        return new Ast.FnDef(declared, moduleName, params, declaredReturn,
                new Ast.FnBody.Written(body), modifiers, pos);
    }

    private Ast.FnParam fnParam(SyntaxNode p, SyntaxNode pat) {
        // A parameter the author named binds that name where it is written. One that is a pattern
        // takes a carrier the author never wrote; the pattern opens itself at the top of the body,
        // and the names it binds are written there.
        Ast.Binder bound = pat == null
                ? binderOf(p)
                : Ast.Binder.desugared("$p" + (patternCounter++), pos(p));
        Ast.RetType type = null;
        Optional<SyntaxNode> rt = p.child(SyntaxKind.RET_TYPE);
        if (rt.isPresent()) {
            type = retType(rt.get());
        }
        if (type == null && pat != null && pat.kind() == SyntaxKind.PATTERN_CTOR) {
            // `let count (Tags(xs))` says the parameter is a Tags; writing `: Tags` beside it would
            // only repeat what the pattern already named
            SourcePos at = pos(pat);
            type = new Ast.RetType(
                    List.of(Ast.TypeRef.written(qualifiedNameOf(pat), null, null)), at);
            return new Ast.FnParam(bound, type, true);
        }
        return new Ast.FnParam(bound, type, false);
    }

    private SyntaxNode optionalPatternChild(SyntaxNode n) {
        for (SyntaxNode c : n.childNodes()) {
            if (isPatternKind(c.kind())) {
                return c;
            }
        }
        return null;
    }

    // --- expressions ---

    private Ast.Expr expr(SyntaxNode n) {
        return switch (n.kind()) {
            case LITERAL_EXPR -> literal(n);
            case VAR_EXPR -> Ast.Var.written(nameOf(firstIdentToken(n)), reference());
            case FIELD_ACCESS -> fieldAccess(n);
            case APPLY_EXPR -> apply(n);
            case BINARY_EXPR -> binary(n);
            case UNARY_EXPR -> new Ast.Neg(expr(onlyExpr(n)), pos(n), region(n));
            case PIPE_EXPR -> pipe(n);
            // The parentheses are dropped from the tree and not from the file: what stands here is
            // the expression inside them, written over the whole of what the author bracketed.
            case PAREN_EXPR -> Ast.withRegion(expr(onlyExpr(n)), region(n));
            case TUPLE_EXPR -> new Ast.Tuple(exprList(n), pos(n), region(n));
            case LIST_EXPR -> new Ast.ListLit(exprList(n), construct(SourceConstruct.COLLECTION_LITERAL),
                    pos(n), region(n));
            case LIST_COMP -> listComp(n);
            case IF_EXPR -> ifExpr(n);
            case MATCH_EXPR -> matchExpr(n);
            case LAMBDA_EXPR -> lambda(n);
            case FIELD_GETTER -> fieldGetter(n);
            case NEW_DATA_EXPR -> newData(n);
            case BLOCK_EXPR -> block(n);
            case UNREACHABLE_EXPR -> unreachable(n);
            default -> throw error(pos(n), new ParseMessage.AnExpressionWasExpected());
        };
    }

    private Ast.Expr literal(SyntaxNode n) {
        SyntaxToken t = firstMeaningfulToken(n);
        SourcePos pos = posOf(t);
        // The token's own slice, never the decoded value's length. A literal is written with the
        // escapes the author typed and read as the characters they stand for, and it is canonicalized
        // as it is read, so the value is the wrong ruler for the file in two ways at once.
        Region region = regionOf(t);
        return switch (t.kind()) {
            case INT_LIT -> new Ast.IntLit(Long.parseLong(t.text()), pos, region);
            case DECIMAL_LIT ->
                    new Ast.DecimalLit(new BigDecimal(stripDecimalSuffix(t.text())), pos, region);
            case STRING_LIT -> new Ast.StringLit(stringValue(t.text()), pos, region);
            case TRUE_KW -> new Ast.BoolLit(true, pos, region);
            case FALSE_KW -> new Ast.BoolLit(false, pos, region);
            default -> throw error(pos, new ParseMessage.ALiteralWasExpected());
        };
    }

    /** {@code unreachable "reason"} — the reason is the literal the parser required beside it. */
    private Ast.Expr unreachable(SyntaxNode n) {
        Ast.Expr reason = expr(firstExprChild(n));
        if (!(reason instanceof Ast.StringLit lit)) {
            throw error(pos(n), new ParseMessage.UnreachableStatesItsReasonAsAString());
        }
        return new Ast.Unreachable(lit.value(), pos(n), region(n));
    }

    private Ast.Expr fieldAccess(SyntaxNode n) {
        Ast.Expr target = expr(firstExprChild(n));
        SyntaxToken field = lastIdentToken(n);
        return new Ast.FieldAccess(target, nameOf(field), posOf(field), region(n));
    }

    /**
     * An argument list written after any expression — every application, whatever is applied.
     *
     * <p>Positioned where the callee starts, not where the callee node says it is: a field read
     * positions itself at the field, so an application of {@code Map.empty} taken from
     * {@code function.pos()} would report at {@code empty} and underline {@code Map.empty}'s
     * length from there, running past the end of what was written.
     */
    private Ast.Expr apply(SyntaxNode n) {
        SyntaxNode callee = exprChildren(n).get(0);
        List<Ast.Expr> args = new ArrayList<>();
        n.child(SyntaxKind.ARG_LIST).ifPresent(list -> {
            for (SyntaxNode arg : exprChildren(list)) {
                args.add(expr(arg));
            }
        });
        return new Ast.Apply(expr(callee), args, construct(SourceConstruct.CALL), pos(callee),
                region(n));
    }

    private Ast.Expr binary(SyntaxNode n) {
        List<SyntaxNode> operands = exprChildren(n);
        SyntaxToken op = operatorToken(n);
        // Anchored at the operator, which is what a report about the operation is about, and written
        // over both operands, which is what the operation is.
        return new Ast.Binary(binOp(op.kind()), expr(operands.get(0)), expr(operands.get(1)),
                construct(SourceConstruct.BINARY), posOf(op), region(n));
    }

    private static Ast.BinOp binOp(SyntaxKind k) {
        return switch (k) {
            case EQ -> Ast.BinOp.EQ;
            case NE -> Ast.BinOp.NE;
            case LT -> Ast.BinOp.LT;
            case LE -> Ast.BinOp.LE;
            case GT -> Ast.BinOp.GT;
            case GE -> Ast.BinOp.GE;
            case AND -> Ast.BinOp.AND;
            case OR -> Ast.BinOp.OR;
            case PLUS -> Ast.BinOp.ADD;
            case MINUS -> Ast.BinOp.SUB;
            case STAR -> Ast.BinOp.MUL;
            case SLASH -> Ast.BinOp.DIV;
            case PLUSPLUS -> Ast.BinOp.CONCAT;
            default -> throw new IllegalStateException("not a binary operator: " + k);
        };
    }

    /** {@code e |> f(a)} → {@code f(a, e)}; {@code e |> f} → {@code f(e)}; {@code e |> Mod.name}
     * → {@code Mod.name(e)} (spec §pipe). */
    private Ast.Expr pipe(SyntaxNode n) {
        List<SyntaxNode> operands = exprChildren(n);
        Ast.Expr left = expr(operands.get(0));
        Ast.Expr right = expr(operands.get(1));
        // The application this becomes is anchored where the callee is written, which is where a
        // report about what is being applied belongs, and is written over the whole pipe — the value
        // on the left is an argument of it however far from the callee the author put it.
        Region written = region(n);
        if (right instanceof Ast.Apply c) {
            List<Ast.Expr> args = new ArrayList<>(c.args());
            args.add(left);
            // The application the author already wrote, with the piped value among its arguments.
            // Its own identity and not a fresh one: `f(a)` is the application here, and `e |>` says
            // where one of its arguments came from.
            return new Ast.Apply(c.function(), args, c.origin(), c.pos(), written);
        }
        if (right instanceof Ast.Var v) {
            return new Ast.Apply(v, List.of(left), construct(SourceConstruct.CALL), v.pos(),
                    written);
        }
        // `e |> Mod.name`: the read is handed over as the callee it is, rather than reassembled
        // into a name here. Whether it is a namespace member or a field taken off a binding is
        // resolution's to say, and it says it once, for this and for `Mod.name(e)` alike.
        if (right instanceof Ast.FieldAccess fa) {
            return new Ast.Apply(fa, List.of(left), construct(SourceConstruct.CALL),
                    pos(operands.get(1)), written);
        }
        throw CompileException.of(Diagnostic.at(right.pos())
                .say(new DeclarationMessage.TheRightSideOfAValuePipeIsACall()).build());
    }

    private Ast.Expr listComp(SyntaxNode n) {
        List<SyntaxNode> exprs = exprChildren(n);
        Ast.Expr element = expr(exprs.get(0));
        List<Ast.Expr> guards = new ArrayList<>();
        for (int i = 1; i < exprs.size(); i++) {
            guards.add(expr(exprs.get(i)));
        }
        return new Ast.ListComp(element, guards, construct(SourceConstruct.COMPREHENSION),
                pos(n), region(n));
    }

    private Ast.Expr ifExpr(SyntaxNode n) {
        List<SyntaxNode> exprs = exprChildren(n);
        SyntaxToken as = attemptBinder(n);
        String binder = as == null ? null : ident(as);
        List<Ast.ElseArm> arms = elseArms(n, binder);
        // One construct, so one origin whichever of the three shapes it is written as.
        SourceConstructOrigin origin = construct(SourceConstruct.IF);
        if (arms != null) {
            return new Ast.IfConstructed(expr(exprs.get(0)),
                    binderOf(as), expr(exprs.get(1)), arms, origin, pos(n), region(n));
        }
        return binder == null
                ? new Ast.If(expr(exprs.get(0)), expr(exprs.get(1)), expr(exprs.get(2)), origin,
                        pos(n), region(n))
                : new Ast.IfConstructed(expr(exprs.get(0)),
                        binderOf(as), expr(exprs.get(1)),
                        List.of(Ast.ElseArm.any(expr(exprs.get(2)))), origin, pos(n), region(n));
    }

    /**
     * The per-clause departures of an attempted construction, or null where the {@code else} took one
     * expression. Only an attempt has clauses to answer, so arms without a binder are refused here
     * rather than reaching a plain {@code If} that has no room for them.
     */
    private List<Ast.ElseArm> elseArms(SyntaxNode form, String binder) {
        Optional<SyntaxNode> node = form.child(SyntaxKind.ELSE_ARMS);
        if (node.isEmpty()) {
            return null;
        }
        if (binder == null) {
            throw CompileException.of(Diagnostic.at(pos(node.get()))
                    .say(new AttemptMessage.NothingHereIsAttempted())
                    .hint(new AttemptMessage.AttemptTheConstructionOrGiveTheElseOneValue())
                    .build());
        }
        List<Ast.ElseArm> arms = new ArrayList<>();
        Set<String> answered = new HashSet<>();
        for (SyntaxNode arm : childNodes(node.get(), SyntaxKind.ELSE_ARM)) {
            SyntaxToken label = identTokens(arm).get(0);
            if (!answered.add(ident(label))) {
                throw CompileException.of(Diagnostic.at(posOf(label))
                        .say(new AttemptMessage.TheClauseIsAnsweredTwice(ident(label)))
                        .build());
            }
            Ast.Expr body = expr(onlyExpr(arm));
            arms.add(ident(label).equals("_")
                    ? Ast.ElseArm.any(body)
                    : new Ast.ElseArm(Optional.of(ident(label)), body, posOf(label)));
        }
        return arms;
    }

    /** The {@code x} of an attempted construction's {@code as x}, or {@code null} where none was
     * written. The binder is the identifier following {@code as} among the form's own tokens; the
     * condition is a child node, so its identifiers are not among them. */
    private static SyntaxToken attemptBinder(SyntaxNode n) {
        boolean afterAs = false;
        for (SyntaxElement e : n.children()) {
            if (!(e instanceof SyntaxToken t)) continue;
            if (t.kind() == SyntaxKind.AS_KW) {
                afterAs = true;
            } else if (afterAs && t.kind() == SyntaxKind.IDENT) {
                return t;
            }
        }
        return null;
    }

    /** {@code (p, ...) -> body}. A parameter that is more than a name takes a fresh one and opens
     * itself at the top of the body, so the block keeps taking plain names and nothing downstream
     * has to know a pattern was written. */
    private Ast.Expr lambda(SyntaxNode n) {
        SourcePos pos = pos(n);
        List<SyntaxNode> pats = patternChildren(n);
        List<Ast.Binder> params = new ArrayList<>();
        for (SyntaxNode p : pats) {
            // A parameter the author named is a name written where it is written; one that is a
            // pattern takes a fresh name nobody wrote, anchored on the pattern it stands for.
            params.add(p.kind() == SyntaxKind.PATTERN_NAME
                    ? binderOf(p)
                    : Ast.Binder.desugared("$p" + (patternCounter++), pos(p)));
        }
        SyntaxNode bodyNode = lastExprChild(n);
        Ast.Expr body = expr(bodyNode);
        Region bodyRegion = region(bodyNode);
        for (int i = pats.size() - 1; i >= 0; i--) {
            if (pats.get(i).kind() != SyntaxKind.PATTERN_NAME) {
                SourcePos at = pos(pats.get(i));
                body = bindPattern(pats.get(i), Ast.Var.desugared(params.get(i).name(), at, reference()), body,
                        at, bodyRegion);
            }
        }
        return new Ast.Block(List.copyOf(params), body, rule(), pos, region(n));
    }

    private Ast.Expr fieldGetter(SyntaxNode n) {
        // `.field` desugars to the getter (x) -> x.field: an ordinary single-param block. The param
        // is bound and read only inside this block, so it shadows any same-named outer name; the
        // inliner alpha-renames it on inline. The `$g` prefix + counter only keep synthesized names
        // apart from each other (the same scheme as `$m`/`$t`); `$` is a legal identifier character.
        SyntaxToken field = lastIdentToken(n);
        SourcePos pos = pos(n);
        String param = "$g" + (getterCounter++);
        // Both the getter and the read inside it are written over the `.field` that stands for
        // them: the parameter is a name nobody typed, and the characters here are the field's.
        Ast.Expr body = new Ast.FieldAccess(Ast.Var.desugared(param, pos, reference()), nameOf(field),
                posOf(field), region(n));
        return Ast.Block.desugared(List.of(param), body, rule(), pos, region(n));
    }

    private Ast.Expr newData(SyntaxNode n) {
        SyntaxToken head = identTokens(n).get(0);
        Ast.Name typeName = Ast.Name.written(nameOf(head));
        List<Ast.FieldInit> inits = new ArrayList<>();
        List<Ast.Var> spreads = new ArrayList<>();
        // a spread naming a field path (`...c.address`) binds that path first, so the construction
        // itself still spreads a plain local: `let $s0 = c.address in Address { ...$s0, ... }`
        List<String> pathNames = new ArrayList<>();
        List<Ast.Expr> pathValues = new ArrayList<>();
        for (SyntaxNode c : n.childNodes()) {
            if (c.kind() == SyntaxKind.SPREAD_MEMBER) {
                List<SyntaxToken> path = identTokens(c);
                if (path.size() == 1) {
                    spreads.add(Ast.Var.written(nameOf(path.get(0)), reference()));
                } else {
                    String bound = "$s" + (spreadCounter++);
                    Ast.Expr value = Ast.Var.written(nameOf(path.get(0)), reference());
                    for (int i = 1; i < path.size(); i++) {
                        value = new Ast.FieldAccess(value, nameOf(path.get(i)),
                                posOf(path.get(i)),
                                new Region(posOf(path.get(0)), lines.posOf(path.get(i).end())));
                    }
                    pathNames.add(bound);
                    pathValues.add(value);
                    // the path is bound just outside the construction, so this name is answered
                    // against that binding like any other the source wrote
                    spreads.add(Ast.Var.desugared(bound, posOf(path.get(0)), reference()));
                }
            } else if (c.kind() == SyntaxKind.FIELD_INIT) {
                WrittenName field = nameOf(firstIdentToken(c));
                Optional<SyntaxNode> value = firstExprChildOpt(c);
                // shorthand `field` means `field = field`, and the one name is both
                Ast.Expr v = value.isPresent() ? expr(value.get()) : Ast.Var.written(field, reference());
                inits.add(new Ast.FieldInit(field, v));
            }
        }
        Ast.Expr built = new Ast.NewData(typeName, inits, spreads, pos(n), region(n));
        // The bindings the spread paths become are the construction as it was written: they stand
        // where it stands and there is nothing else at those characters.
        for (int i = pathNames.size() - 1; i >= 0; i--) {
            built = new Ast.LetIn(pathNames.get(i), pathValues.get(i), built, pos(n), region(n));
        }
        return built;
    }

    private Ast.Expr matchExpr(SyntaxNode n) {
        List<SyntaxNode> exprs = exprChildren(n);
        Ast.Expr scrutinee = expr(exprs.get(0));
        List<Ast.Case> cases = new ArrayList<>();
        for (SyntaxNode c : childNodes(n, SyntaxKind.MATCH_CASE)) {
            cases.add(matchCase(c));
        }
        return new Ast.Match(scrutinee, cases, construct(SourceConstruct.MATCH), pos(n),
                region(n));
    }

    private Ast.Case matchCase(SyntaxNode n) {
        List<SyntaxElement> es = meaningful(n);
        SourcePos casePos = pos(n);
        int[] at = {0};
        List<Ast.Name> caseTypes = new ArrayList<>();
        caseTypes.add(dottedName(es, at));
        while (at[0] + 1 < es.size() && isToken(es.get(at[0]), SyntaxKind.PIPE)
                && isToken(es.get(at[0] + 1), SyntaxKind.IDENT)) {
            at[0]++;                              // |
            caseTypes.add(dottedName(es, at));
        }
        int i = at[0];
        // newtype constructor destructuring `X(inner)` / nested `X(Y(s))`: the ident chain inside the
        // parens. The last ident is the bound variable; each earlier ident names a newtype layer peeled.
        List<Ast.Name> unwrapNames = new ArrayList<>();
        if (i < es.size() && isToken(es.get(i), SyntaxKind.LPAREN)) {
            int depth = 0;
            while (at[0] < es.size()) {
                SyntaxElement e = es.get(at[0]);
                if (isToken(e, SyntaxKind.LPAREN)) {
                    depth++;
                    at[0]++;
                } else if (isToken(e, SyntaxKind.RPAREN)) {
                    depth--;
                    at[0]++;
                    if (depth == 0) {
                        break;
                    }
                } else if (isToken(e, SyntaxKind.IDENT)) {
                    unwrapNames.add(dottedName(es, at));   // a layer is named like any other type
                } else {
                    break;
                }
            }
            i = at[0];
        }
        boolean isSome = caseTypes.size() == 1 && caseTypes.get(0).written().equals("Some");
        // Option's positional binding `Some v`: a bare identifier before `{` / `as` / `->`. It never
        // follows a constructor destructure, so a trailing ident after the parens is not consumed here.
        // Option is the one case with a payload to reach, so every other case binds its value with
        // `as` and a bare identifier beside its name binds nothing.
        String someBinding = null;
        SyntaxToken bindingToken = null;   // the name the author wrote for the arm, if they did
        if (unwrapNames.isEmpty() && i < es.size() && isToken(es.get(i), SyntaxKind.IDENT)) {
            String ident = tokenText(es.get(i));
            // A qualified name is not a case this advice fits: `Some` is the one case with a payload
            // to bind and it has no qualified spelling, so what is wrong with `Q.C v` is `Q.C`, and
            // saying so is resolution's to do. The parser has no answer for a name yet.
            boolean qualified = caseTypes.get(caseTypes.size() - 1).written().indexOf('.') >= 0;
            if (!isSome && !qualified) {
                throw error(posOf((SyntaxToken) es.get(i)), new ParseMessage.ACaseValueIsBoundWithAs(
                        caseTypes.get(caseTypes.size() - 1).written(), ident));
            }
            someBinding = ident;
            bindingToken = (SyntaxToken) es.get(i);
            i++;
        }
        // field destructuring `{ field [= var], ... }`
        List<String> fieldNames = new ArrayList<>();
        List<Ast.Binder> fieldVars = new ArrayList<>();
        if (i < es.size() && isToken(es.get(i), SyntaxKind.LBRACE)) {
            i++;   // {
            while (i < es.size() && !isToken(es.get(i), SyntaxKind.RBRACE)) {
                SyntaxToken fieldToken = (SyntaxToken) es.get(i++);
                // `{ v }` binds the field's own name; `{ v = x }` binds the name after the `=`.
                // Either way the binding is written where its token is.
                SyntaxToken varToken = fieldToken;
                if (i < es.size() && isToken(es.get(i), SyntaxKind.ASSIGN)) {
                    i++;
                    varToken = (SyntaxToken) es.get(i++);
                }
                fieldNames.add(ident(fieldToken));
                fieldVars.add(binderOf(varToken));
                if (i < es.size() && isToken(es.get(i), SyntaxKind.COMMA)) {
                    i++;
                }
            }
            if (i < es.size()) {
                i++;   // }
            }
        }
        // whole-value binding `as x`
        String asBinding = null;
        if (i < es.size() && isToken(es.get(i), SyntaxKind.AS_KW)) {
            i++;
            asBinding = tokenText(es.get(i));
            bindingToken = (SyntaxToken) es.get(i++);
        }
        if (isSome && asBinding != null) {
            throw error(casePos, new ParseMessage.OptionsWrappedValueIsBoundPositionally());
        }
        // `Some(a)` opens nothing (unlike `X(a)` on a user case); the whole-element spelling is `Some v`.
        // Only `Some(X(...))`, which opens a wrapped newtype, uses the paren form.
        if (isSome && unwrapNames.size() == 1) {
            throw error(casePos, new ParseMessage.SomeParensOpenAWrappedNewtype(
                    unwrapNames.get(0).written()));
        }
        // skip the arrow, then the body is the trailing expression node
        SyntaxNode bodyNode = lastExprChild(n);
        Ast.Expr body = expr(bodyNode);
        // What the pattern lowers to holds the arm's body, so that is what those bindings are
        // written over. The reads they bind are the lowering's own and were written nowhere.
        Region bodyRegion = region(bodyNode);

        String binding = someBinding != null ? someBinding : asBinding;
        if (!fieldNames.isEmpty()) {
            String whole = binding != null ? binding : "$m" + (matchWholeCounter++);
            if (binding == null) {
                bindingToken = null;   // the arm holds the value in a name nobody wrote
            }
            for (int k = fieldNames.size() - 1; k >= 0; k--) {
                body = new Ast.LetIn(fieldVars.get(k),
                        new Ast.FieldAccess(Ast.Var.desugared(whole, casePos, reference()), fieldNames.get(k),
                                casePos),
                        body, casePos, bodyRegion);
            }
            binding = whole;
        } else if (!unwrapNames.isEmpty()) {
            String whole = binding != null ? binding : "$m" + (matchWholeCounter++);
            if (binding == null) {
                bindingToken = null;   // the arm holds the value in a name nobody wrote
            }
            // open the case's newtype (whole.value), then peel one layer per earlier name; the last
            // name binds the value reached — `アクティベート済み(メールアドレス(s))` binds s to whole.value.value.
            // Option's `Some` binds the unwrapped element already (codegen strips the wrapper), so its
            // first named layer opens that element directly — `Some(従業員ID(v))` binds v to whole.value.
            Ast.Expr target = isSome
                    ? Ast.Var.desugared(whole, casePos, reference())
                    : new Ast.FieldAccess(Ast.Var.desugared(whole, casePos, reference()), "value", casePos);
            for (int k = 0; k < unwrapNames.size() - 1; k++) {
                target = new Ast.FieldAccess(target, "value", casePos);
            }
            Ast.Name innermost = unwrapNames.get(unwrapNames.size() - 1);
            body = new Ast.LetIn(Ast.Binder.of(innermost), target, body, casePos, bodyRegion);
            binding = whole;
        }
        // null = no constructor destructure; otherwise the inner names (before the bound variable),
        // which the type checker prepends the case type to when verifying the opened layers.
        List<Ast.Name> unwrapAsserts = unwrapNames.isEmpty()
                ? null
                : new ArrayList<>(unwrapNames.subList(0, unwrapNames.size() - 1));
        Ast.Binder bound = binding == null ? null
                : bindingToken != null ? binderOf(bindingToken)
                : Ast.Binder.desugared(binding, casePos);
        return new Ast.Case(caseTypes, bound, body, unwrapAsserts, casePos);
    }

    /** A brace block: its {@code let}/{@code guard} statements folded into the result expression. */
    private Ast.Expr block(SyntaxNode n) {
        List<SyntaxNode> stmts = new ArrayList<>();
        SyntaxNode result = null;
        for (SyntaxNode c : n.childNodes()) {
            switch (c.kind()) {
                case LET_STMT, LET_DESTRUCTURE, GUARD_STMT -> stmts.add(c);
                default -> result = c;   // the trailing result expression
            }
        }
        return foldStatements(stmts, 0, result);
    }

    /**
     * How many steps a block takes, blocks written inside it counted along with it, read from the
     * source before any of it is folded.
     *
     * <p>Before, because folding descends once per step and a block whose steps are the ones inside
     * it too has as many to descend. Each block on its own can be well under the bound while what
     * they come to together is thousands, and the fold would be that far down before anything could
     * say so — the answer would come from running out rather than from counting.
     *
     * <p>What is counted is the way down: the steps before a statement, the step it is, and then
     * whatever block it holds. A block held at the first statement of one that takes three hundred
     * costs what it costs and not three hundred more, which is what makes this never refuse
     * something the whole count would keep. Anything that is not a block counts as one here — it is
     * bounded as the source is read, and this is only about what the fold will descend.
     */
    private int stepsTakenBy(List<SyntaxNode> stmts, SyntaxNode result) {
        int stepsBefore = 0;
        int most = 0;
        for (SyntaxNode stmt : stmts) {
            for (SyntaxNode held : exprChildren(stmt)) {
                most = Math.max(most, stepsBefore + 1 + stepsHeldBy(held));
            }
            stepsBefore += stepsTakenBy(stmt);
        }
        return Math.max(most, stepsBefore + stepsHeldBy(result));
    }

    /**
     * What the deepest block written anywhere in {@code held} would take, or one where there is no
     * block in it.
     *
     * <p>Down through whatever is written around a block and not only where one is written
     * directly. A block handed to a call or put in a tuple is a block the fold still descends, and
     * a preflight that only looked at what a statement's value is would be walked past by writing
     * {@code f({ … })}. What is written around it is not counted — a construct is a level and the
     * source's nesting is bounded as it is read — so what this comes to is the steps the fold will
     * take and nothing else.
     */
    private int stepsHeldBy(SyntaxNode held) {
        if (held == null) {
            return 1;
        }
        if (held.kind() == SyntaxKind.BLOCK_EXPR) {
            List<SyntaxNode> stmts = new ArrayList<>();
            SyntaxNode result = null;
            for (SyntaxNode c : held.childNodes()) {
                switch (c.kind()) {
                    case LET_STMT, LET_DESTRUCTURE, GUARD_STMT -> stmts.add(c);
                    default -> result = c;
                }
            }
            return stepsTakenBy(stmts, result);
        }
        int most = 1;
        for (SyntaxNode child : held.childNodes()) {
            most = Math.max(most, stepsHeldBy(child));
        }
        return most;
    }

    /** How many steps one statement takes: a `let` written with a pattern is what the pattern
     *  binds, and anything else — an ordinary `let`, a `guard`, which binds nothing — is one. */
    private int stepsTakenBy(SyntaxNode stmt) {
        return stmt.kind() == SyntaxKind.LET_DESTRUCTURE
                ? bindingsIntroducedBy(patternChild(stmt))
                : 1;
    }

    private Ast.Expr foldStatements(List<SyntaxNode> stmts, int index, SyntaxNode result) {
        // Before the fold and not after it. Everything a statement is followed by is written inside
        // what that statement introduced, so folding them descends once per step — a block long
        // enough to be refused is a block long enough to run this out on the way to saying so.
        if (index == 0) {
            int steps = stepsTakenBy(stmts, result);
            if (steps > StructuralCost.MAX) {
                // A block holding nothing but another block has no statement to be reported at, and
                // what it comes to is what that one comes to; the result is where it is written.
                throw errorWithHint(pos(stmts.isEmpty() ? result : stmts.get(stmts.size() - 1)),
                        new DeclarationMessage.ABlockTakesMoreStructuralStepsThanADefinitionHolds(
                                steps, StructuralCost.MAX),
                        new DeclarationMessage.WriteItAsABehaviorOfItsOwn());
            }
        }
        if (index == stmts.size()) {
            return expr(result);   // a block always ends in a result expression
        }
        SyntaxNode s = stmts.get(index);
        SourcePos pos = pos(s);
        // A statement folded into what follows it is written over both: the expression this becomes
        // begins at the statement and ends where the block's result does.
        Region held = spanning(s, result);
        return switch (s.kind()) {
            case LET_STMT -> {
                Ast.RetType annotation = s.child(SyntaxKind.RET_TYPE).map(AstBuilder.this::retType).orElse(null);
                Ast.Expr value = expr(onlyExpr(s));
                Ast.Expr rest = foldStatements(stmts, index + 1, result);
                // The statement starts at its keyword and the binding is written after it. A reader
                // asking what a cursor is on has only the name to compare against.
                Ast.Binder bound = binderOf(s);
                yield annotation == null
                        ? new Ast.LetIn(bound, value, rest, pos, held)
                        : Ast.LetIn.annotated(bound, value, annotation, rest, pos, held);
            }
            case GUARD_STMT -> {
                List<SyntaxNode> exprs = exprChildren(s);
                SyntaxToken as = attemptBinder(s);
                String binder = as == null ? null : ident(as);
                Ast.Expr rest = foldStatements(stmts, index + 1, result);
                List<Ast.ElseArm> arms = elseArms(s, binder);
                // One construct, so one origin whichever of the three shapes it is written as.
                SourceConstructOrigin origin = construct(SourceConstruct.GUARD);
                if (arms != null) {
                    yield new Ast.IfConstructed(expr(exprs.get(0)), binderOf(as), rest, arms, origin,
                            pos, held);
                }
                Ast.Expr settles = expr(exprs.get(0));
                Ast.Expr otherwise = expr(exprs.get(1));
                yield binder == null
                        ? new Ast.If(settles, rest, otherwise, origin, pos, held)
                        : new Ast.IfConstructed(settles, binderOf(as), rest,
                                List.of(Ast.ElseArm.any(otherwise)), origin, pos, held);
            }
            case LET_DESTRUCTURE -> {
                SyntaxNode pat = patternChild(s);
                yield bindPattern(pat, expr(onlyExpr(s)),
                        foldStatements(stmts, index + 1, result), pos(pat), held);
            }
            default -> throw error(pos, new ParseMessage.AStatementWasExpected());
        };
    }

    /**
     * How many bindings one pattern introduces.
     *
     * <p>The value itself is one, and what is taken out of it is one each: an element of a tuple,
     * a field a record pattern names, what a case pattern opens. A pattern written inside one of
     * those counts its own the same way. Nothing here reads the tree the pattern becomes — this is
     * what the author wrote, and it is what the bound is over.
     */
    private int bindingsIntroducedBy(SyntaxNode pat) {
        return switch (pat.kind()) {
            case PATTERN_NAME -> 1;
            case PATTERN_TUPLE -> {
                List<SyntaxNode> elems = patternChildren(pat);
                if (elems.size() == 1) {
                    yield bindingsIntroducedBy(elems.get(0));   // `(p)` is grouping
                }
                int bindings = 1;
                for (SyntaxNode elem : elems) {
                    bindings += bindingsIntroducedBy(elem);
                }
                yield bindings;
            }
            case PATTERN_CTOR -> 1 + bindingsIntroducedBy(patternChild(pat));
            case PATTERN_RECORD -> 1 + childNodes(pat, SyntaxKind.PATTERN_FIELD).size();
            default -> 1;
        };
    }

    /**
     * A pattern binding {@code value}, with {@code rest} in its scope. Every shape lowers to the
     * ordinary reads it stands for — a tuple to indexed gets, a newtype to {@code .value}, a record
     * to field reads — so nothing past this point has to know a pattern was written. It is the same
     * lowering a {@code match} arm does; the difference is only that here there is one arm.
     */
    private Ast.Expr bindPattern(SyntaxNode pat, Ast.Expr value, Ast.Expr rest, SourcePos pos,
                                 Region held) {
        return switch (pat.kind()) {
            case PATTERN_NAME -> new Ast.LetIn(binderOf(pat), value, rest, pos, held);
            case PATTERN_TUPLE -> {
                List<SyntaxNode> elems = patternChildren(pat);
                if (elems.size() == 1) {
                    yield bindPattern(elems.get(0), value, rest, pos, held);   // `(p)` is grouping
                }
                String whole = "$t" + (tupleCounter++);
                Ast.Expr body = rest;
                for (int i = elems.size() - 1; i >= 0; i--) {
                    body = bindPattern(elems.get(i),
                            new Ast.TupleGet(Ast.Var.desugared(whole, pos, reference()), i, elems.size(), pos,
                                    null),
                            body, pos, held);
                }
                yield new Ast.LetIn(whole, value, body, pos, held);
            }
            case PATTERN_CTOR -> {
                String whole = "$p" + (patternCounter++);
                Ast.Expr inner = new Ast.FieldAccess(Ast.Var.desugared(whole, pos, reference()), "value", pos);
                Ast.Expr body = bindPattern(patternChild(pat), inner, rest, pos, held);
                yield Ast.LetIn.opening(whole, value,
                        Ast.Name.written(qualifiedNameOf(pat)), body, pos, held);
            }
            case PATTERN_RECORD -> {
                String whole = "$r" + (patternCounter++);
                Ast.Expr body = rest;
                List<SyntaxNode> fields = childNodes(pat, SyntaxKind.PATTERN_FIELD);
                for (int k = fields.size() - 1; k >= 0; k--) {
                    List<SyntaxToken> names = identTokens(fields.get(k));
                    String field = ident(names.get(0));
                    SyntaxToken var = names.size() > 1 ? names.get(1) : names.get(0);
                    body = new Ast.LetIn(binderOf(var),
                            new Ast.FieldAccess(Ast.Var.desugared(whole, pos, reference()), field, pos), body,
                            pos, held);
                }
                yield new Ast.LetIn(whole, value, body, pos, held);
            }
            default -> throw error(pos, new ParseMessage.APatternWasExpected());
        };
    }

    private List<Ast.Expr> exprList(SyntaxNode n) {
        List<Ast.Expr> out = new ArrayList<>();
        for (SyntaxNode c : exprChildren(n)) {
            out.add(expr(c));
        }
        return out;
    }

    // --- example rows and stand-ins ---

    /** {@code example <target> | rows...}, read for the owner the target names. Which token that is
     *  was settled where the owner was, so the name and the place a report about the block is sent
     *  are one reading of one token. */
    private Ast.Example example(SyntaxNode n, SyntaxToken target, String named) {
        SourcePos pos = target == null ? pos(n) : posOf(target);
        List<Ast.ExampleRow> rows = new ArrayList<>();
        for (SyntaxNode row : childNodes(n, SyntaxKind.EXAMPLE_ROW)) {
            rows.add(exampleRow(row));
        }
        return new Ast.Example(named, rows, pos);
    }

    /** {@code [ "name" : ] ( inputs ) -> expected}. The name is a leading string token; the inputs are
     * the {@code ARG_LIST}'s expressions; the expected is the remaining expression. A name that names
     * nothing is not one, and is refused where it is written (E2304), so a row written with one is
     * read here as the row without a name it turned out to be. */
    private Ast.ExampleRow exampleRow(SyntaxNode n) {
        String written = n.token(SyntaxKind.STRING_LIT).map(t -> stringValue(t.text())).orElse(null);
        RowIdentity identity = RowIdentity.of(written, ++rowCounter);
        List<Ast.Expr> inputs = new ArrayList<>();
        n.child(SyntaxKind.ARG_LIST).ifPresent(list -> {
            for (SyntaxNode a : exprChildren(list)) {
                inputs.add(expr(a));
            }
        });
        List<Ast.With> withs = new ArrayList<>();
        n.child(SyntaxKind.WITH_CLAUSE).ifPresent(clause -> {
            for (SyntaxNode b : childNodes(clause, SyntaxKind.WITH_BINDING)) {
                withs.add(new Ast.With(behaviorNameAfter(b, 0), expr(firstExprChild(b)), pos(b)));
            }
        });
        return new Ast.ExampleRow(identity, inputs, withs, expected(n), pos(n));
    }

    /**
     * What the row put where its answer goes.
     *
     * <p>{@code <?>} is a token of the row and an asserted answer is its own expr child (ARG_LIST
     * holds the inputs and WITH_CLAUSE the fakes), so the two cannot both be there and the reading
     * takes them in that order. A row with neither is one whose answer did not parse, which is said
     * as that rather than left for a reader to find out by looking.
     */
    private Ast.Expected expected(SyntaxNode n) {
        Optional<SyntaxToken> owed = n.token(SyntaxKind.UNANSWERED);
        if (owed.isPresent()) {
            return new Ast.Expected.Unanswered(posOf(owed.get()));
        }
        List<SyntaxNode> written = exprChildren(n);
        return written.isEmpty() ? new Ast.Expected.Unwritten(pos(n))
                : new Ast.Expected.Asserted(expr(written.get(0)));
    }

    /** {@code fake <target> | rows}, read for the owner it names. */
    private Ast.Fake fake(SyntaxNode n) {
        Ast.Var target = behaviorNameAfter(n, 1);
        List<Ast.FakeRow> rows = new ArrayList<>();
        for (SyntaxNode row : childNodes(n, SyntaxKind.FAKE_ROW)) {
            rows.add(fakeRow(row));
        }
        return new Ast.Fake(target, rows, target.pos());
    }

    /** {@code ( args ) -> out} or {@code _ -> out}. A row with no {@code ARG_LIST} is the default. */
    private Ast.FakeRow fakeRow(SyntaxNode n) {
        Optional<SyntaxNode> args = n.child(SyntaxKind.ARG_LIST);
        List<Ast.Expr> inputs = new ArrayList<>();
        args.ifPresent(list -> {
            for (SyntaxNode a : exprChildren(list)) {
                inputs.add(expr(a));
            }
        });
        boolean isDefault = args.isEmpty();
        List<SyntaxNode> exprs = exprChildren(n);   // the output (not inside ARG_LIST)
        Ast.Expr output = exprs.isEmpty() ? null : expr(exprs.get(0));
        return new Ast.FakeRow(isDefault ? null : inputs, output, isDefault, pos(n));
    }

    }

    private static boolean isPatternKind(SyntaxKind k) {
        return k == SyntaxKind.PATTERN_NAME || k == SyntaxKind.PATTERN_TUPLE
                || k == SyntaxKind.PATTERN_CTOR || k == SyntaxKind.PATTERN_RECORD;
    }

    private SyntaxNode patternChild(SyntaxNode n) {
        for (SyntaxNode c : n.childNodes()) {
            if (isPatternKind(c.kind())) {
                return c;
            }
        }
        throw new IllegalStateException("no pattern in " + n.kind());
    }

    private List<SyntaxNode> patternChildren(SyntaxNode n) {
        List<SyntaxNode> out = new ArrayList<>();
        for (SyntaxNode c : n.childNodes()) {
            if (isPatternKind(c.kind())) {
                out.add(c);
            }
        }
        return out;
    }

    // --- CST navigation helpers ---

    /** The direct child nodes that are expressions, in order. */
    private List<SyntaxNode> exprChildren(SyntaxNode n) {
        List<SyntaxNode> out = new ArrayList<>();
        for (SyntaxNode c : n.childNodes()) {
            if (isExprKind(c.kind())) {
                out.add(c);
            }
        }
        return out;
    }

    private SyntaxNode firstExprChild(SyntaxNode n) {
        return exprChildren(n).get(0);
    }

    private Optional<SyntaxNode> firstExprChildOpt(SyntaxNode n) {
        List<SyntaxNode> c = exprChildren(n);
        return c.isEmpty() ? Optional.empty() : Optional.of(c.get(0));
    }

    private SyntaxNode lastExprChild(SyntaxNode n) {
        List<SyntaxNode> c = exprChildren(n);
        return c.get(c.size() - 1);
    }

    /** The single expression child of a node that wraps exactly one (a block statement, an invariant,
     * a unary/fn body). */
    private SyntaxNode onlyExpr(SyntaxNode n) {
        return firstExprChild(n);
    }

    private static boolean isExprKind(SyntaxKind k) {
        return switch (k) {
            case LITERAL_EXPR, VAR_EXPR, FIELD_ACCESS, APPLY_EXPR, BINARY_EXPR,
                 UNARY_EXPR, PIPE_EXPR,
                 PAREN_EXPR, TUPLE_EXPR, LIST_EXPR, LIST_COMP, IF_EXPR, MATCH_EXPR, LAMBDA_EXPR,
                 FIELD_GETTER, NEW_DATA_EXPR, BLOCK_EXPR, UNREACHABLE_EXPR -> true;
            default -> false;
        };
    }

    private List<SyntaxNode> childNodes(SyntaxNode n, SyntaxKind kind) {
        List<SyntaxNode> out = new ArrayList<>();
        for (SyntaxNode c : n.childNodes()) {
            if (c.kind() == kind) {
                out.add(c);
            }
        }
        return out;
    }

    /** The direct IDENT tokens of a node, in order (skipping keywords, punctuation, and trivia). */
    private List<SyntaxToken> identTokens(SyntaxNode n) {
        List<SyntaxToken> out = new ArrayList<>();
        for (SyntaxElement e : n.children()) {
            if (e instanceof SyntaxToken t && t.kind().standsWhereANameStands()) {
                out.add(t);
            }
        }
        return out;
    }


    /**
     * The name an identifier token spells, canonicalized to NFC.
     *
     * <p>A source file is bytes from an editor, and two spellings that Unicode calls canonically
     * equivalent are the same text — so they have to be the same name. Without this the compiler
     * holds two names that no reader can tell apart: two fields, two cases, two helpers. It also has
     * to happen here rather than to the source: normalizing before lexing would shorten a line and
     * move every position after it, so a diagnostic's caret would point at the wrong column of the
     * file the author is looking at. A token keeps its own width; only the name it spells is settled.
     *
     * <p>A string literal is canonicalized by {@link #stringValue}, for the same reason. A numeric
     * literal is not: it is parsed, not compared.
     */
    private static String ident(SyntaxToken t) {
        return souther.compiler.Reserved.name(t.text());
    }

    /** The name a top-level declaration declares: the first identifier in it. This is how the
     * builder names a {@code data}, a {@code behavior} and a {@code let}, and how the declaration's
     * source is filed under the same name when a module publishes what it declares. */
    static String firstIdentText(SyntaxNode n) {
        return ident(firstIdentToken(n));
    }

    /** The token that spells what a top-level declaration declares. */
    static SyntaxToken firstIdentToken(SyntaxNode n) {
        for (SyntaxElement e : n.children()) {
            if (e instanceof SyntaxToken t && t.kind().standsWhereANameStands()) {
                return t;
            }
        }
        throw new IllegalStateException("no identifier in " + n.kind());
    }

    private SyntaxToken lastIdentToken(SyntaxNode n) {
        SyntaxToken last = null;
        for (SyntaxElement e : n.children()) {
            if (e instanceof SyntaxToken t && t.kind().standsWhereANameStands()) {
                last = t;
            }
        }
        return last;
    }

    private SyntaxToken operatorToken(SyntaxNode n) {
        for (SyntaxElement e : n.children()) {
            if (e instanceof SyntaxToken t && !t.isTrivia() && isBinaryOperator(t.kind())) {
                return t;
            }
        }
        throw new IllegalStateException("no operator in " + n.kind());
    }

    private static boolean isBinaryOperator(SyntaxKind k) {
        return switch (k) {
            case EQ, NE, LT, LE, GT, GE, AND, OR, PLUS, MINUS, STAR, SLASH, PLUSPLUS -> true;
            default -> false;
        };
    }

    private SyntaxNode typeChild(SyntaxNode n) {
        for (SyntaxNode c : n.childNodes()) {
            if (isTypeTermKind(c.kind())) {
                return c;
            }
        }
        throw new IllegalStateException("no type in " + n.kind());
    }

    /**
     * The name a run of dotted identifiers spells, and where each part of it is written.
     *
     * <p>Read off the tokens rather than off the joined spelling. A qualified name is read over
     * meaningful tokens, so the parts may be separated by whitespace, a comment or a line break, and
     * counting a dot's worth of characters between them puts the last segment — which is the part a
     * rename rewrites — in the wrong column.
     */
    private WrittenName qualifiedNameOf(SyntaxNode n) {
        return joined(identTokens(n));
    }

    /** The one name a run of identifier tokens spells, occupying everything from the first to the
     *  last of them. */
    private WrittenName joined(List<SyntaxToken> parts) {
        WrittenName name = nameOf(parts.get(0));
        for (int i = 1; i < parts.size(); i++) {
            name = name.then(nameOf(parts.get(i)));
        }
        return name;
    }

    private String qualifiedNameText(SyntaxNode n) {
        StringBuilder sb = new StringBuilder();
        for (SyntaxToken t : identTokens(n)) {
            if (sb.length() > 0) {
                sb.append('.');
            }
            sb.append(ident(t));
        }
        return sb.toString();
    }

    private List<SyntaxElement> meaningful(SyntaxNode n) {
        List<SyntaxElement> out = new ArrayList<>();
        for (SyntaxElement e : n.children()) {
            if (e instanceof SyntaxNode || (e instanceof SyntaxToken t && !t.isTrivia())) {
                out.add(e);
            }
        }
        return out;
    }

    private static boolean isToken(SyntaxElement e, SyntaxKind kind) {
        return e instanceof SyntaxToken t && t.kind() == kind;
    }

    private static String tokenText(SyntaxElement e) {
        return ident((SyntaxToken) e);
    }

    /** The name a token spells, kept with the characters that spell it. Every name the source
     *  wrote enters the tree through here; canonicalizing is {@link WrittenName}'s, so a caller
     *  cannot hand over a name and a place that are not each other's. */
    private WrittenName nameOf(SyntaxToken t) {
        return WrittenName.of(t.text(), posOf(t));
    }

    private SyntaxToken firstMeaningfulToken(SyntaxNode n) {
        for (SyntaxElement e : n.children()) {
            if (e instanceof SyntaxToken t && !t.isTrivia()) {
                return t;
            }
            if (e instanceof SyntaxNode c) {
                SyntaxToken inner = firstMeaningfulTokenOrNull(c);
                if (inner != null) {
                    return inner;
                }
            }
        }
        throw new IllegalStateException("no token under " + n.kind());
    }

    private SyntaxToken firstMeaningfulTokenOrNull(SyntaxNode n) {
        for (SyntaxElement e : n.children()) {
            if (e instanceof SyntaxToken t && !t.isTrivia()) {
                return t;
            }
            if (e instanceof SyntaxNode c) {
                SyntaxToken inner = firstMeaningfulTokenOrNull(c);
                if (inner != null) {
                    return inner;
                }
            }
        }
        return null;
    }

    private SourcePos pos(SyntaxNode n) {
        return lines.posOf(firstMeaningfulToken(n).start());
    }

    private SyntaxToken lastMeaningfulTokenOrNull(SyntaxNode n) {
        List<SyntaxElement> children = n.children();
        for (int i = children.size() - 1; i >= 0; i--) {
            SyntaxElement e = children.get(i);
            if (e instanceof SyntaxToken t && !t.isTrivia()) {
                return t;
            }
            if (e instanceof SyntaxNode c) {
                SyntaxToken inner = lastMeaningfulTokenOrNull(c);
                if (inner != null) {
                    return inner;
                }
            }
        }
        return null;
    }

    /**
     * The stretch of source {@code n} was written over: the first character of its first meaningful
     * token to the last of its last.
     *
     * <p>Both ends off the tokens rather than off the node. A node's own extent takes in the trivia
     * the parser flushed into it, and what stands between two forms belongs to neither: a comment
     * after the last operand of a condition is not part of the condition, and an underline that ran
     * to the end of it would say it was.
     *
     * <p>Null for a node holding no meaningful token, which is what a recovery node can be. A form
     * the parser could not read is not one the author wrote a definite stretch of.
     */
    private Region region(SyntaxNode n) {
        SyntaxToken first = firstMeaningfulTokenOrNull(n);
        SyntaxToken last = lastMeaningfulTokenOrNull(n);
        return first == null || last == null ? null
                : new Region(lines.posOf(first.start()), lines.posOf(last.end()));
    }

    private SourcePos posOf(SyntaxToken t) {
        return lines.posOf(t.start());
    }

    /** The characters {@code t} is written with — its own slice of the file, escapes and all. */
    private Region regionOf(SyntaxToken t) {
        return new Region(lines.posOf(t.start()), lines.posOf(t.end()));
    }

    /**
     * From where {@code from} begins to where {@code to} ends.
     *
     * <p>What a statement folded into the rest of its block is written over. {@code let x = e} is one
     * expression holding everything after it, so the characters it covers are its own and the ones
     * it nests, and neither node has both ends of that on its own.
     */
    private Region spanning(SyntaxNode from, SyntaxNode to) {
        Region begins = region(from);
        Region ends = region(to);
        return begins == null || ends == null ? null : new Region(begins.start(), ends.end());
    }

    /** The name {@code n} binds, written where the source writes it. */
    private Ast.Binder binderOf(SyntaxNode n) {
        return binderOf(firstIdentToken(n));
    }

    /** The name {@code token} spells, bound where it is written. The one way this builder makes a
     * binding out of something the author wrote: the token carries both halves, so they cannot be
     * paired wrongly. */
    private Ast.Binder binderOf(SyntaxToken token) {
        return Ast.Binder.of(Ast.Name.written(nameOf(token)));
    }


    /** The whole `{ ... }` of a body, so an error about the body underlines both braces. */
    private Region bodyRegion(SyntaxNode body) {
        SourcePos open = pos(body);
        return body.token(SyntaxKind.RBRACE)
                .map(close -> new Region(open, lines.posOf(close.end())))
                .orElseGet(() -> Region.point(open));
    }

    // --- literal decoding ---

    private static String stripDecimalSuffix(String raw) {
        return raw.endsWith("m") ? raw.substring(0, raw.length() - 1) : raw;
    }

    /**
     * Decodes a raw string-literal slice (quotes and escapes included) to its value, canonicalized
     * to NFC.
     *
     * <p>A source file is bytes from an editor, which is the other place text arrives from outside —
     * the first being a decoder, which canonicalizes for the same reason. Two forms that are
     * canonically equivalent are the same text by Unicode's definition and different values to a
     * comparison by code units, so a literal left as written would compare unequal to the same text
     * that came in through a boundary, and a pattern written in one form would not match a value in
     * the other. Which form an editor writes is not something the author chose.
     *
     * <p>NFC and not NFKC: compatibility folding turns ① into 1 and a half-width kana into a
     * full-width one, which is a different claim about the text than "these are the same characters".
     */
    private static String stringValue(String raw) {
        return java.text.Normalizer.normalize(CstLexer.textOf(raw), java.text.Normalizer.Form.NFC);
    }

    private <M extends Message & Reported> CompileException error(SourcePos pos, M said) {
        return CompileException.of(Diagnostic.say(said).at(pos).build());
    }

    /** As {@link #error}, with a hint under it naming the way out. */
    private <M extends Message & Reported, H extends Message & Supporting>
            CompileException errorWithHint(SourcePos pos, M said, H hint) {
        return CompileException.of(Diagnostic.say(said).at(pos).hint(hint).build());
    }

    /** Whether {@code name} sits in the compiler-shipped {@code souther} namespace (ADR-0028); only
     * those modules may write type variables, {@code intrinsic} bodies, and {@code private}. */
    private static boolean isReservedNamespace(String name) {
        return souther.compiler.Reserved.isNamespace(name);
    }
}
