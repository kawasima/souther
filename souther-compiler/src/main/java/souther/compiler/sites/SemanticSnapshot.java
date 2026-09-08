package souther.compiler.sites;

import souther.compiler.Reserved;
import souther.compiler.ast.Hir;
import souther.compiler.ast.WrittenName;
import souther.compiler.check.BindingEvidence;
import souther.compiler.check.DeclaredTypeEvidence;
import souther.compiler.check.FieldRead;
import souther.compiler.check.ResolvedFieldTypes;
import souther.compiler.check.Sig;
import souther.compiler.check.SpecImplementation;
import souther.compiler.check.DerivedSymbols;
import souther.compiler.check.Symbols;
import souther.compiler.diag.Region;
import souther.compiler.diag.SourcePos;
import souther.compiler.observe.FieldTypes;
import souther.compiler.query.Answer;
import souther.compiler.query.Bodies;
import souther.compiler.query.Db;
import souther.compiler.query.Names;
import souther.compiler.query.Sites;
import souther.compiler.source.SourceId;
import souther.compiler.types.BindingId;
import souther.compiler.types.ValueName;
import souther.compiler.types.Type;
import souther.compiler.types.TypeSpelling;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * What is known about one revision of one module's source, as an editor asks it.
 *
 * <p>The boundary, and the only thing outside the compiler reads. Everything it answers is about an
 * occurrence the source wrote, found by the characters it was written over, and every answer comes
 * from this one revision — nothing here is carried over from a revision that compiled earlier, so an
 * answer is either about what the buffer says now or is absent.
 *
 * <p>Built where it is used and dropped there. It holds a way to reach answers rather than a set of
 * them, which is what {@link Names#derivedSymbols} is and for the same reason: it has no equality to
 * be memoised by, and a caller that kept one would be keeping the compilation it was made from.
 *
 * <p>Absent where the occurrences of the module could not be told apart. What an editor does then is
 * everything it can do from the syntax alone, and nothing that rests on knowing which occurrence a
 * position is. Half of a snapshot is not published. What an individual answer rests on is a separate
 * question: a fact this cannot reach is that fact absent, and not the snapshot going away.
 */
public final class SemanticSnapshot {

    /**
     * What this module's declarations settle about one parameter a {@code let} wrote.
     *
     * <p>Told apart by what a reader may do with it rather than by which kind of position it fills.
     * Two of these are types a name in a body has, and one of the two is also a type an author may
     * write where the name is — which is the difference between what is put in a hint and what is
     * answered when the name is asked about.
     */
    private sealed interface ParameterFact {

        /** An input, as the signature says it arrives. */
        record TypedInput(Hir.FnParam written, Type arrives) implements ParameterFact {}

        /** An injected behavior, as what it takes and answers. */
        record TypedInjection(Hir.FnParam written, Type takes) implements ParameterFact {}

        /** A parameter this revision settles nothing about. */
        record Untyped(Hir.FnParam written) implements ParameterFact {}
    }

    private final Db db;
    private final String module;
    private final AuthoredSites sites;
    private final Symbols symbols;

    private SemanticSnapshot(Db db, String module, AuthoredSites sites, Symbols symbols) {
        this.db = db;
        this.module = module;
        this.sites = sites;
        this.symbols = symbols;
    }

    /**
     * What {@code db} can be asked about {@code module}, or empty where it cannot be asked.
     *
     * <p>Empty rather than partial. A module whose source does not resolve has no settled reading of
     * its names, and one whose occurrences collide has no way to say which occurrence a position is;
     * neither is a snapshot with less in it.
     */
    public static Optional<SemanticSnapshot> of(Db db, String module) {
        Answer<AuthoredSites> occurrences = db.ask(new Sites.Authored(module));
        Answer<DerivedSymbols> scope = Names.derivedSymbols(db, module);
        return occurrences.present() && scope.present()
                ? Optional.of(new SemanticSnapshot(db, module, occurrences.value(), scope.value()))
                : Optional.empty();
    }

    /**
     * What the left of the {@code .} is, for the narrowest thing written over {@code cursor}.
     *
     * <p>Empty where no access was written there. That a receiver is a value whose type nothing here
     * states is a different answer and is {@link MemberReceiver.UntypedValue} — a reader that offers
     * nothing for both still knows which of them it was told.
     */
    public Optional<MemberReceiver> memberReceiverAround(SourcePos cursor) {
        return receiverOf(sites.innermostContaining(cursor));
    }

    /**
     * The same, for a caller that has the access's extent rather than a place inside it.
     *
     * <p>Two entry points and not one method that guesses which it was handed. What an editor has is
     * a cursor; what a reader that already walked the tree has is an extent, and an extent that is
     * an occurrence is one occurrence while a place is inside several.
     */
    public Optional<MemberReceiver> memberReceiverOf(Region extent) {
        return receiverOf(sites.site(extent));
    }

    private Optional<MemberReceiver> receiverOf(SourceSiteId site) {
        return switch (sites.written(site)) {
            // A field taken off a value. That it is one is already settled: the parser reads
            // `m.name` and `x.field` alike, and this is a field read because a binding was in force
            // where the chain is rooted.
            case Hir.FieldAccess access -> Optional.of(valueReceiver(access.target()));
            case Hir.Var written -> namespaceOf(written.written());
            case null, default -> Optional.empty();
        };
    }

    /**
     * How this module writes {@code type}, or empty where it has no name for it.
     *
     * <p>Asked of the module rather than taken off the type. What a declaration is and what a module
     * calls it are two things: a type reached through an import is written the way the import
     * brought it in, and one this module neither declares nor imports has no spelling here at all.
     * Empty is that second answer, and a reader shown a name it cannot write would be shown
     * something to type that does not compile.
     */
    public Optional<String> spellingOf(Type type) {
        return TypeSpelling.of(type, symbols.scope()::reach)
                instanceof TypeSpelling.Spelled(String rendered)
                ? Optional.of(rendered) : Optional.empty();
    }

    /**
     * Whether a value of {@code type} is held to a rule its declaration wrote.
     *
     * <p>A fact about the type and not about any value of it. What it is for is a reader looking at
     * a name whose type is not written beside it: that the type has an invariant is the difference
     * between a `Draft` and any other record of the same fields, and it is not in the signature the
     * hint came from.
     */
    public boolean heldToARule(Type type) {
        return declarations().heldToARule(type);
    }

    /**
     * What the declarations say about a type, asked of the one reading of them.
     *
     * <p>Every question of that shape goes through here: what fields a value has, what rules it is
     * held to, what a name it was given comes to. Working one out from the declaration instead —
     * reading a data's own clauses, naming a newtype's one field — is that reading written a second
     * time, and the copy is right until the language adds a step to the original. It has twice been
     * exactly that: a spread brings in fields and the rules that came with them, and both were being
     * missed by a reading that looked at what the declaration wrote rather than at what applies.
     *
     * <p>What this reading is not asked is the shape of the module itself — what it declares, what
     * it exposes, what its behaviors are called. Nothing else answers those, and they are read off
     * the resolved module here.
     */
    private DeclaredTypeEvidence declarations() {
        return new DeclaredTypeEvidence(fieldRead(), Map.of());
    }

    /**
     * What every parameter written in {@code source} is declared to arrive as.
     *
     * <p>A signature is written once, on the {@code behavior} line, and the {@code let} under it
     * repeats none of it — so a reader working in the body has the names and not the types, and the
     * line that says them may be far up the file or in another one. This is not inference shown to
     * an author: it is the declaration they already wrote, carried to where they are working.
     *
     * <p>Answered whether or not the body compiles. What arrives is the signature's to say, and a
     * body that will not check does not stop it saying so.
     */
    public List<DeclaredParameter> parametersIn(SourceId source) {
        List<DeclaredParameter> out = new ArrayList<>();
        for (ParameterFact fact : parameterFacts()) {
            // The inputs alone. What the signature says arrives is a type an author may write where
            // the hint is drawn; a parameter a `depends on` clause fills is a behavior handed to the
            // implementation, and it has no spelling that goes in that place.
            if (!(fact instanceof ParameterFact.TypedInput(Hir.FnParam written, Type arrives))) {
                continue;
            }
            Hir.Binder binder = written.binder();
            // A parameter written nowhere is one a pass introduced; there is no name in the
            // source for a hint to stand after.
            if (binder.written().authored() && binder.pos().isIn(source)) {
                out.add(new DeclaredParameter(binder.written().region(),
                        new TypeFact(arrives, new Evidence.Declared()), heldToARule(arrives)));
            }
        }
        return List.copyOf(out);
    }

    /**
     * What the declarations say about each parameter every {@code let} of this module wrote.
     *
     * <p>One classification, read by everything here that is about a parameter. What a hint is drawn
     * for and what a name in a body is typed by are two uses of one answer, and worked out apart they
     * were two: the reading that drew hints and the reading that typed bodies each decided for
     * itself which parameters a signature speaks for, and both decided it by comparing the length of
     * the {@code let}'s parameter list with the length of the signature. A behavior with a
     * {@code depends on} clause takes the behaviors it is injected with beside its inputs, so those
     * two lengths differ for every one of them and both readings left the whole definition out.
     *
     * <p>Which parameters the signature speaks for is {@link SpecImplementation}'s to say, and is
     * asked here rather than worked out from the lengths. What is added is the types: the signature
     * says what arrives at an input, and an injected parameter is a behavior this module names, whose
     * signature says what it takes and answers.
     *
     * <p>Answered whether or not the definition is one the checker will accept. A parameter the
     * declaration accounts for nothing for is {@link ParameterFact.Untyped}, which is the same
     * answer a reader gets for a parameter of a behavior whose signature this revision could not
     * work out — nothing here says a type, and nothing here says the definition is wrong either.
     */
    private List<ParameterFact> parameterFacts() {
        Answer<Hir.Module> resolved = db.ask(new Names.Resolved(module));
        Answer<Map<String, Sig>> signatures = db.ask(new Bodies.Signatures(module));
        if (!resolved.present() || !signatures.present()) {
            return List.of();
        }
        Answer<Map<ValueName.Behavior, Sig>> reachable = db.ask(new Bodies.Reachable(module));
        List<ParameterFact> facts = new ArrayList<>();
        SpecImplementation.implementationsOf(resolved.value())
                .forEach((behavior, implemented) -> {
                    Sig sig = signatures.value().get(behavior);
                    for (SpecImplementation.ParameterBinding binding : implemented.bindings()) {
                        facts.add(factOf(binding, sig, reachable));
                    }
                });
        return List.copyOf(facts);
    }

    /** What one parameter is, given what this module's signatures say. */
    private static ParameterFact factOf(SpecImplementation.ParameterBinding binding, Sig sig,
                                        Answer<Map<ValueName.Behavior, Sig>> reachable) {
        return switch (binding) {
            case SpecImplementation.ParameterBinding.AnInput input ->
                    sig == null || input.at() >= sig.inputTypes().size()
                            ? new ParameterFact.Untyped(input.written())
                            : new ParameterFact.TypedInput(input.written(),
                                    sig.inputTypes().get(input.at()));
            case SpecImplementation.ParameterBinding.AnInjection injected ->
                    injectionFact(injected, reachable);
            // A clause that reaches no declaration names no signature to read, and a parameter the
            // declaration asks for no position for is spoken for by nothing.
            case SpecImplementation.ParameterBinding.Unanswered unanswered ->
                    new ParameterFact.Untyped(unanswered.written());
            case SpecImplementation.ParameterBinding.Extraneous extraneous ->
                    new ParameterFact.Untyped(extraneous.written());
        };
    }

    /**
     * An injected parameter, typed by the signature of the behavior it is handed.
     *
     * <p>What arrives there is that behavior with what it depends on already supplied, so what a
     * body may do with the name is call it with the inputs the declaration names. Untyped where this
     * revision has no signature for it, which is the module it is declared in still being read.
     */
    private static ParameterFact injectionFact(
            SpecImplementation.ParameterBinding.AnInjection injected,
            Answer<Map<ValueName.Behavior, Sig>> reachable) {
        Sig injects = reachable.present() ? reachable.value().get(injected.behavior()) : null;
        return injects == null
                ? new ParameterFact.Untyped(injected.written())
                : new ParameterFact.TypedInjection(injected.written(),
                        Type.fn(injects.inputTypes(), injects.outputType()));
    }

    /**
     * The behavior the name written over {@code cursor} reaches, and what it declares it takes.
     *
     * <p>Asked of the callee and not of the call. A call is written over everything in it, brackets
     * included, and the last of those may not be typed yet — the name is written before any of that
     * and says which declaration this is about.
     *
     * <p>Given a place in that name rather than its extent. How far a name is written is a question
     * this already answers, and a caller measuring it from the tree would be measuring it a second
     * way: a syntax node runs over the trivia in front of it, and a name reached through a module is
     * one occurrence over the whole run. Both are answered by asking which occurrence covers a
     * place.
     *
     * <p>Empty where the name reaches no behavior: a helper, a local holding a function, a name that
     * resolves to nothing. What those take is not written on a {@code behavior} line, and there is no
     * declaration here to show.
     */
    public Optional<CalledBehavior> calledAt(SourcePos cursor) {
        SourceSiteId site = sites.innermostContaining(cursor);
        if (site == null || !(sites.written(site) instanceof Hir.Var.Denoting called)
                || !(called.reachedAs().denotes() instanceof ValueName.Behavior reached)) {
            return Optional.empty();
        }
        Answer<Map<ValueName.Behavior, Sig>> reachable = db.ask(new Bodies.Reachable(module));
        Answer<Hir.Module> declaring = db.ask(new Names.Resolved(reached.module()));
        if (!reachable.present() || !declaring.present()) {
            return Optional.empty();
        }
        Sig sig = reachable.value().get(reached);
        List<Hir.Param> written = parametersOf(declaring.value(), reached.name());
        if (sig == null || written == null || sig.inputTypes().size() != written.size()) {
            // A signature and a declaration that disagree about how many things arrive is a mistake
            // in that module, reported where it is written. Pairing them off anyway would name an
            // argument after a parameter that is not the one arriving there.
            return Optional.empty();
        }
        List<CalledBehavior.Takes> takes = new ArrayList<>();
        for (int at = 0; at < written.size(); at++) {
            takes.add(new CalledBehavior.Takes(written.get(at).name(),
                    new TypeFact(sig.inputTypes().get(at), new Evidence.Declared())));
        }
        return Optional.of(new CalledBehavior(reached.name(), List.copyOf(takes), site.extent()));
    }

    /** The parameters {@code behavior} is declared with, or null where the module declares no such
     *  behavior or declares it as a composition, which writes none. */
    private static List<Hir.Param> parametersOf(Hir.Module declaring, String behavior) {
        for (Hir.BehaviorDef each : declaring.behaviors()) {
            if (each.written().canonical().equals(behavior)) {
                return each instanceof Hir.SpecBehavior spec ? spec.params() : null;
            }
        }
        return null;
    }

    /**
     * The names a namespace offers, in the order it declares them.
     *
     * <p>What a module publishes and not what it holds: an author writing {@code m.} may write what
     * {@code m} exposes, and offering the rest would be offering names that do not resolve. The
     * module's own {@code exposing} line is that answer, made by the module rather than worked out
     * here from what it happens to declare.
     *
     * <p>A namespace the language reserves offers its published surface, which is the same answer
     * asked of the library rather than of a module: the set of qualified names a module outside the
     * reserved namespace may write.
     */
    public List<Published> namesIn(MemberReceiver.Namespace namespace) {
        // Every namespace there is, and no arm reached by not being the others. A kind narrowed by
        // what it is not is a default the compiler cannot see, and the one nobody wrote an answer
        // for comes back as the same empty list a namespace with nothing in it would.
        return switch (namespace) {
            case MemberReceiver.Namespace.OfModule(String named, Region _) -> offeredBy(named);
            case MemberReceiver.Namespace.OfLibrary(String qualifier, Region _) ->
                    offeredUnder(qualifier);
        };
    }

    /** What a module of this compilation publishes, in the order its {@code exposing} line writes
     *  them. */
    private List<Published> offeredBy(String named) {
        Answer<Hir.Module> offering = db.ask(new Names.Resolved(named));
        if (!offering.present()) {
            return List.of();
        }
        Hir.Module offered = offering.value();
        List<Published> out = new ArrayList<>();
        for (String name : offered.exposing()) {
            published(offered, name).ifPresent(out::add);
        }
        return List.copyOf(out);
    }

    /**
     * What the language's own library publishes under {@code qualifier}.
     *
     * <p>Its published surface is every qualified name a module outside the reserved namespace may
     * write, which is what an author writing {@code List.} may write — the library carries no bare
     * names, so the qualifier is how all of it is reached and the list under one is a slice of that
     * surface.
     *
     * <p>Each is a definition. What the library publishes is operations, and the reserved namespace
     * declares no data an importer names this way.
     */
    private List<Published> offeredUnder(String qualifier) {
        List<Published> out = new ArrayList<>();
        String under = qualifier + ".";
        for (String published : symbols.library().published()) {
            if (published.startsWith(under)) {
                String member = published.substring(under.length());
                // One segment. A name published deeper is reached through the qualifier it is
                // published under, and offering it here would offer something to write that does
                // not parse where it was offered.
                if (member.indexOf('.') < 0) {
                    out.add(new Published.ADefinition(member));
                }
            }
        }
        out.sort(java.util.Comparator.comparing(Published::name));
        return List.copyOf(out);
    }

    /**
     * Which of the things a module declares {@code name} is, or empty where it declares none of
     * them.
     *
     * <p>Empty rather than a fourth kind. A name on an {@code exposing} line that the module does
     * not declare is a mistake in that module, reported where it is written, and offering it here
     * would be offering a name that resolves to nothing.
     */
    private static Optional<Published> published(Hir.Module offered, String name) {
        for (Hir.Def def : offered.defs()) {
            if (def.name().equals(name)) {
                return Optional.of(new Published.AType(name));
            }
        }
        for (Hir.BehaviorDef behavior : offered.behaviors()) {
            if (behavior.written().canonical().equals(name)) {
                return Optional.of(new Published.ABehavior(name));
            }
        }
        for (Hir.FnDef fn : offered.fns()) {
            // A behavior's implementation is declared under the behavior's name and was answered
            // above; what is left here is a definition of the module's own.
            if (fn.written().canonical().equals(name)) {
                return Optional.of(new Published.ADefinition(name));
            }
        }
        return Optional.empty();
    }

    /**
     * The names a {@code .} written on a value of {@code held} may take, each with what it holds.
     *
     * <p>The same reading a field access is typed by, so what an author is offered here is what the
     * compiler will accept: a record's own fields, a name every case of a sum spreads, a newtype's
     * {@code value} and nothing of what it wraps. Answered from the layout of the declaration the
     * type names instead, a sum would offer nothing while the language reads its shared names on
     * every value of it.
     *
     * <p>Asked of what a declaration holds at this revision, which is the reading an editor is owed:
     * a module still being typed has no checked answer about a value of it, and a reader looking at
     * a name in it is owed what its declarations denote now.
     */
    public Map<String, Type> fieldsOf(TypeFact held) {
        return fieldRead().at(held.type());
    }

    /**
     * What a {@code .} may name, as the text stands.
     *
     * <p>The same reading the compiler types a field access by, in the world an editor reads: a
     * declaration that does not read yet — one name written twice, a spread of something that is no
     * product — makes nothing readable rather than being refused here. What is wrong with it is
     * reported where the module is checked, and an author who is being told that already is not
     * also told that the buffer cannot answer what may follow a {@code .}.
     */
    private FieldRead fieldRead() {
        return new FieldRead(symbols, fields(), FieldRead.Unreadable.MAKES_NOTHING_READABLE);
    }

    /** What a declaration holds, as the text has resolved it so far. */
    private FieldTypes fields() {
        return new ResolvedFieldTypes(symbols);
    }

    /**
     * A value receiver, with what the declarations say it is where they say anything.
     *
     * <p>Asked of the one walk that answers what a declaration says about an expression's type, so
     * what an editor is told about {@code request.plannedCost} and what a row's reading is told
     * about it are the same answer. What is added here is the one thing that walk has no way to
     * know on its own: a behavior's parameter is bound by nothing and its type is in the signature
     * above it.
     */
    private MemberReceiver valueReceiver(Hir.Expr receiver) {
        Type declared = declaredTypeOf(receiver);
        return declared == null
                ? new MemberReceiver.UntypedValue(receiver.region())
                : new MemberReceiver.Value(new TypeFact(declared, new Evidence.Declared()),
                        receiver.region());
    }

    /** What the declarations say {@code e} is, or null where they say nothing — including where the
     *  module's signatures or definitions are not answerable, which is one fact being absent rather
     *  than this reading being. */
    private Type declaredTypeOf(Hir.Expr e) {
        Answer<Map<String, Hir.FnDef>> values = db.ask(new Bodies.ModuleDefinitions(module));
        if (!values.present()) {
            return null;
        }
        Map<BindingId, BindingEvidence> parameters = parametersOfEveryBehavior();
        return new DeclaredTypeEvidence(fieldRead(), values.value(), parameters)
                .declaredTypeOf(e, new HashSet<>(), new HashMap<>(parameters));
    }

    /**
     * What every parameter written in this module is declared to arrive as.
     *
     * <p>All of them at once and not the ones the cursor is inside. A binding tells itself from
     * every other, so a parameter of one behavior cannot be reached by a name in another, and
     * working out which body a position is in would be a scope this does not have to keep.
     *
     * <p>The injected parameters as well as the inputs. A name a body reads is a name whatever it
     * stands for, and one standing for a behavior the module was handed has a type as much as one
     * standing for an input does — so a reader asking what {@code dep(x).field} is gets the same
     * answer here as it would for a call written any other way.
     *
     * <p>A parameter nothing here types is left out, which is a fact being absent rather than the
     * parameter being. What is wrong with a definition the declaration does not account for is
     * reported where it is written.
     */
    private Map<BindingId, BindingEvidence> parametersOfEveryBehavior() {
        Map<BindingId, BindingEvidence> declared = new LinkedHashMap<>();
        for (ParameterFact fact : parameterFacts()) {
            switch (fact) {
                case ParameterFact.TypedInput(Hir.FnParam written, Type arrives) ->
                        declared.put(written.binder().id(),
                                new BindingEvidence.DeclaredAs(arrives));
                case ParameterFact.TypedInjection(Hir.FnParam written, Type takes) ->
                        declared.put(written.binder().id(), new BindingEvidence.DeclaredAs(takes));
                case ParameterFact.Untyped _ -> { }
            }
        }
        return declared;
    }

    /**
     * The namespace a qualified name is reached through, or empty where the name is not qualified or
     * its qualifier names none.
     *
     * <p>Answered to the module rather than to the spelling. An import may bring a module in under
     * any name it likes, so what {@code m} is is a question about this module's imports, and a
     * reader handed the spelling would have to ask it again.
     */
    private Optional<MemberReceiver> namespaceOf(WrittenName name) {
        String written = name.canonical();
        int lastDot = written.lastIndexOf('.');
        if (lastDot < 0 || name.segments().size() < 2) {
            return Optional.empty();
        }
        String qualifier = written.substring(0, lastDot);
        // Every segment but the last, which is what the qualifier is written over. Taken from the
        // occurrences the name carries rather than measured in the spelling: the two differ by
        // whatever the grammar lets stand between a qualifier and the name it qualifies.
        Region at = new Region(name.segments().getFirst().start(),
                name.segments().get(name.segments().size() - 2).end());
        String namespace = symbols.scope().moduleOfQualifier(qualifier);
        if (namespace != null) {
            return Optional.of(new MemberReceiver.Namespace.OfModule(namespace, at));
        }
        return Reserved.isQualifier(qualifier)
                ? Optional.of(new MemberReceiver.Namespace.OfLibrary(qualifier, at))
                : Optional.empty();
    }
}
