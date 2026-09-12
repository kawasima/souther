package souther.compiler.check;

import souther.compiler.ast.Hir;
import souther.compiler.core.Core;
import souther.compiler.types.BindingId;
import souther.compiler.types.Type;
import souther.compiler.types.TypeSymbol;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * The invariants of the declarations the discharge check reads: each clause typed once, over the
 * fields it is written against, and read at a value by putting what each field is being given where
 * that field is read.
 *
 * <p>That reading is what lets the check hold one representation. A clause belongs to a declaration
 * and the values belong to a body, and a clause read over its own tree and a body read over another
 * would be two term grammars that have to be kept naming the same value the same way. A field is a
 * binding, so putting a value there is a substitution, and what comes out is an expression of the
 * body's own kind.
 *
 * <p>Everything here is remembered, because both the seeding and every construction ask the same
 * declaration the same questions.
 */
final class Clauses {

    private final RuleReadingSource source;
    private final Symbols symbols;
    private final ExpandedClauseLookup expandedClauses;
    private final ClauseLocations written;
    /** Where what a declaration says is answered from — the declaration's own reading of it, and
     *  not one this reader makes out of the tree it was handed. Which clauses it has, what each of
     *  them states, and what it spreads all come from here. */
    private final PublishedDeclarations published;
    /** Which form each of those declarations was written in. */
    private final DeclarationKinds kinds;
    private final Map<TypeSymbol.AtModule, Map<String, Type>> fields = new HashMap<>();
    private final Map<TypeSymbol.AtModule, Map<String, BindingId>> bindings =
            new HashMap<>();
    /** Remembered per declaration, not per clause: a clause an include brings in is one expression
     * reached under two names, and what it types to is read against the fields of the one asking. */
    private final Map<TypeSymbol, Map<Hir.Expr, TypedClause>> typed = new HashMap<>();
    /** What governs each declaration this reading has been asked about, and each one the walk
     * reached under it: a type spread by two of them is walked once. */
    private final Map<TypeSymbol.AtModule, PublishedRules> effective = new HashMap<>();
    /** Which of a declaration's own fields each typed clause reads — what a construction has to have
     * filled for the clause to be read at all. */
    private final Map<Core, Set<String>> readsFields = new IdentityHashMap<>();

    /**
     * @param source where this reads: the scope its names mean something in, where a declaration's
     *        clauses are answered from in the representation the discharge rules are written at
     *        ({@link InliningPolicy#DISCHARGE}), where what one states is answered from, where one
     *        is written, and which source that is. Taken whole rather than in parts, so that what
     *        is read here and what a reading made here is filed under are the one source
     *        ({@link RuleReadingSource#origin}); handed the parts, a reader below could be given a
     *        scope from one and an origin from another.
     */
    Clauses(RuleReadingSource source) {
        this.source = source;
        this.symbols = source.symbols();
        this.expandedClauses = source.invariants();
        this.written = source.written();
        this.published = source.published();
        this.kinds = source.kinds();
    }


    /** Where this reads, for a reader that has to hand it on rather than ask for one of its own. */
    RuleReadingSource source() {
        return source;
    }

    /** Where a clause of a declaration is written, for the same reader — asked where a sentence
     *  points and read by nothing here. */
    ClauseLocations written() {
        return written;
    }

    /** Every rule that applies to {@code named}, as the declarations that wrote them publish them,
     * with whether every one of them was reached. */
    PublishedRules of(TypeSymbol.AtModule named) {
        return PublishedRules.governing(named, symbols, published, effective);
    }

    /**
     * The clauses {@code named} itself writes, in the representation an expansion left them in.
     *
     * <p>The one way a tree of a declaration is reached here, and it is the producing side's: what
     * a declaration states is worked out from the clauses its own module expanded, and published
     * so that every reader elsewhere takes it from {@link #of} instead. Its own and not the ones it
     * spreads in, because what this is for is a declaration answering for what it wrote.
     */
    List<TypeOps.Declared> declaredHere(TypeSymbol.AtModule named) {
        return TypeOps.writtenOn(named, expandedClauses);
    }

    /** What {@code named}'s fields are, read from this reading's own world for the reason
     *  {@link #declarationOf} gives. */
    Map<String, Type> fieldsOf(TypeSymbol.AtModule named) {
        return fields.computeIfAbsent(named, name -> TypeOps.fieldTypes(declarationOf(name), symbols));
    }

    /**
     * Which binding each of {@code named}'s fields is — what a clause reads, and what a construction
     * fills.
     *
     * <p>Keyed by the name and not by the declaration, because that is what the binding is a function
     * of: two modules may declare one spelling, and a reader with both in sight would otherwise have
     * them answer alike.
     */
    Map<String, BindingId> bindingsOf(TypeSymbol.AtModule named) {
        return bindings.computeIfAbsent(named, name -> TypeOps.fieldBindings(name, symbols));
    }

    /**
     * The declaration {@code named} is, read from the world this reading was made against.
     *
     * <p>Read here and not taken from a caller. What a clause states is read in a representation,
     * and so is what the declaration it is written on holds; handed a node, this would type a clause
     * of one reading against the fields of another, and nothing it held would say so.
     */
    private Hir.Data declarationOf(TypeSymbol.AtModule named) {
        if (symbols.declaredNode(named) instanceof Hir.Data data) {
            return data;
        }
        throw new IllegalArgumentException(
                "`" + named.name() + "` is not a product this reading's world declares");
    }

    /**
     * {@code clause} as the checker types it: over {@code named}'s own fields, each a binding, in
     * the representation this check reads. Asked once per clause, because typing one walks it.
     *
     * <p>{@link TypedClause.Stopped} where typing it did not finish. This used to answer null for
     * that, saying it was the same answer as a clause naming something outside the fragment; it is
     * not, and it never was — the elaborator does not answer null of its own accord, so every one of
     * those was an exception caught here and dropped.
     */
    TypedClause typed(ClauseAsExpanded clause, TypeSymbol.AtModule named) {
        return typed.computeIfAbsent(named, _ -> new IdentityHashMap<>())
                .computeIfAbsent(clause.read(), _ -> SecondaryClauseReading.of(clause, over(named),
                        "typing a clause of " + named));
    }

    /** What a clause of {@code named} is read over, worked out inside the reading for the reason
     *  {@link SecondaryClauseReading.Over} gives. */
    private Supplier<SecondaryClauseReading.Over> over(TypeSymbol.AtModule named) {
        return () -> {
            Hir.Data data = declarationOf(named);
            return new SecondaryClauseReading.Over(
                    DataChecker.fieldScope(named, data, symbols),
                    CheckContext.of(symbols, published, kinds).forData(data).forDischarge());
        };
    }

    /**
     * What {@code clause} states where each field is given what {@code given} says, or {@code null}
     * where it states nothing this check can read.
     *
     * <p>Taken from what the declaration that wrote the clause publishes, and not worked out from
     * the tree that declaration was written as. What a clause states is that declaration's answer,
     * and a reader typing the authored tree again is a second answer to it — one that is made
     * afresh for every reader and that moves whenever anything above the declaration is edited.
     *
     * <p>The term goes no further than this reading. What comes back to the caller is a clause read
     * at the fields it was given, which is this reading's own tree from here on; where the clause is
     * written is asked of {@link ClauseLocations} by whoever puts a caret under it.
     *
     * <p>A field nothing was given — one a construction leaves out — leaves the clause naming a value
     * that is not there, and the clause is left to the run-time check rather than read against
     * nothing.
     */
    private AsStated statedAt(ClauseMeaning clause, TypeSymbol.AtModule named,
                              Map<BindingId, Core> given) {
        // Fail-open: a clause with no form leaves its run-time check standing, whichever way the
        // form went missing. Which of the two it was matters to a reader that publishes a sentence
        // about the clause, and this is not one.
        if (!(clause instanceof ClauseMeaning.Stated it)
                || !everyFieldRead(given, named, it.fieldsRead())) {
            return null;
        }
        return new AsStated(substituted(it.states().termForClauseReading(), given), it.parts());
    }

    /**
     * What a clause states as this reading's own tree, and the rules its author wrote it as.
     *
     * <p>The two together because they are one answer about one clause and a reader takes both: the
     * parts are found again in the very tree beside them, so a reader handed them apart can be
     * handed them from two lookups that need not have agreed.
     *
     * @param states what it states, with nothing put in for the fields
     * @param parts the rules its author wrote it as
     */
    record AsStated(Core states, ClauseMeaning.Parts parts) {

        AsStated {
            if (states == null || parts == null) {
                throw new IllegalArgumentException(
                        "a clause that states something states it as some rules");
            }
        }
    }

    /**
     * The parts of a stated clause, each as a subtree of the one reading of the whole.
     *
     * <p>The clause read into its shape, which is done here for every reader of a declaration's
     * rules. Which parts there are was settled where the clause was split and nothing here decides
     * it; what this adds is that they are subtrees of one reading, so what a reader says about an
     * occurrence is said in the numbering the whole clause hands out rather than in one that starts
     * wherever a part does.
     *
     * <p>One place, because reading a clause into its shape is answering what its author wrote. Two
     * readers doing it for themselves are two answers to that, arrived at under whatever polarity
     * each of them wrote down.
     */
    List<StatedPart> partsOf(AsStated stated) {
        return stated.parts().onto(ClauseExpr.of(stated.states(), true));
    }

    /**
     * What {@code clause} states, or {@code null} where its declaration has no form for it.
     *
     * <p>For the reader that seeds a declaration's own fields, where each field stands for itself
     * and there is nothing to substitute. The same statement the reading below puts a
     * construction's values into, taken the same way and from the same place.
     */
    AsStated stated(ClauseMeaning clause) {
        return clause instanceof ClauseMeaning.Stated it
                ? new AsStated(it.states().termForClauseReading(), it.parts()) : null;
    }

    /** Whether {@code given} holds a value for every one of {@code fields}, which are named as the
     *  declaration writes them and are reached here through this reading's own bindings. */
    private boolean everyFieldRead(Map<BindingId, Core> given, TypeSymbol.AtModule named,
                                   Set<String> fields) {
        Map<String, BindingId> bindings = bindingsOf(named);
        for (String each : fields) {
            BindingId binding = bindings.get(each);
            if (binding == null || !given.containsKey(binding)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Every clause of {@code named}, each stated where its fields are given what {@code given} says,
     * and the ones that state nothing this check can read left out.
     *
     * <p>Both directions ask this. What a clause guarantees where the value already exists and what
     * it owes where one is being built are the same clauses read the same way, and they differ only
     * in what the fields are given — a read of each field, or the value each is being handed.
     */
    StatedClauses statedAt(TypeSymbol.AtModule named, Map<BindingId, Core> given) {
        List<Stated> stated = new ArrayList<>();
        List<RuleRef.Invariant> lost = new ArrayList<>();
        for (ClauseMeaning inv : declared(named)) {
            Clause.Ref clause = inv.ref();
            AsStated one = statedAt(inv, named, given);
            if (one != null) {
                // The clause as one reading, and the parts its author wrote as subtrees of that
                // very reading. Read apart instead, a conjunct would be read without the conjunct
                // beside it, and a branch one of them rules out would stand.
                stated.add(new Stated(clause, one.states(), partsOf(one)));
            } else {
                lost.add(new RuleRef.Invariant(clause));
            }
        }
        return new StatedClauses(List.copyOf(stated), List.copyOf(lost));
    }


    /**
     * The clauses of one declaration as they read here, and whether they are all of them.
     *
     * <p>The second because leaving one out is not visible in the first. A clause that states
     * nothing this can read is dropped, and a caller handed the rest has no way to tell a
     * declaration whose every clause was read from one whose clauses it is holding some of — the
     * two are the same list with different things missing from it. Said here, where the dropping
     * happens, rather than counted again by whoever needs to know.
     *
     * @param lost which clauses the declaration writes are not in {@code clauses}, named rather
     *             than counted. A caller told only that something was lost has to find out what it
     *             was about by reading the declaration again, and a reader that answers for the
     *             rules it was handed would otherwise answer for a rule it never saw
     */
    record StatedClauses(List<Stated> clauses, List<RuleRef.Invariant> lost) {

        public StatedClauses {
            clauses = List.copyOf(clauses);
            lost = List.copyOf(lost);
        }

        /** What a reading told to leave a declaration's clauses out gets: none of them, and nothing
         * lost. */
        static final StatedClauses NONE_ASKED_FOR = new StatedClauses(List.of(), List.of());

        /** Whether every clause the declaration writes is in {@link #clauses}. */
        boolean everyClauseStated() {
            return lost.isEmpty();
        }
    }

    /**
     * One clause as it reads at a construction, beside the clause it is a reading of, and the parts
     * its author wrote it in.
     *
     * <p>A check that judges the clauses one at a time has something to say about the one it could
     * not settle, and what it says it by is what {@link Clause.Ref} holds — which the clauses were
     * flattened out of before reaching here, leaving every unproven clause reported as "the
     * invariant". Where the clause is written is not among it and is looked up where a sentence
     * points ({@link ClauseLocations}).
     *
     * <p>The parts are two views of one reading and not two readings. What a clause states is read
     * as one thing — its conjuncts meet there, and a branch one of them rules out is ruled out
     * there — and what an author is answerable for is a part; each part is a subtree of
     * {@code expr} and not a tree read beside it.
     */
    record Stated(Clause.Ref clause, Core expr, List<StatedPart> parts) {

        public Stated {
            parts = List.copyOf(parts);
        }
    }

    /**
     * One part of a clause as it reads here, with what it is called as a part of the rule.
     *
     * <p>The identity comes from the split that wrote the parts down and is carried rather than
     * worked out here: which part of a clause a tree is is not something a reader of the tree can
     * answer, and a reader that counted them would be a second walk deciding which parts there are.
     */
    record StatedPart(PartId<RuleRef.Invariant> id, ClauseExpr of) {

        public StatedPart {
            if (id == null || of == null) {
                throw new IllegalArgumentException("a part read here is some rule's part and a form");
            }
        }

        /** The part as the tree holds it, which is the outermost node its shape was spelled as. */
        Core expr() {
            return of.written();
        }
    }

    /** Every clause of {@code named}, as the declaration that wrote it publishes it. */
    List<ClauseMeaning> declared(TypeSymbol.AtModule named) {
        return of(named).reached();
    }

    /**
     * Which of {@code named}'s fields {@code clause} reads, remembered: a clause is read at every
     * construction of its type, and what it reads does not change between them.
     *
     * <p>Every field a value of {@code named} has, which is the fields it writes together with the
     * ones its spreads bring in ({@link #bindingsOf}). What a construction has to have filled is
     * the question, and a field brought in is filled like any other.
     *
     * <p>By the name a field is reached under and not by the binding it is read through. A binding
     * is one reading's way of reaching a field, so two readings of one declaration name the same
     * fields through two bindings. Said as bindings, the answer could only be used by the reading
     * that produced it, which is the reading that already had the tree.
     */
    Set<String> fieldsRead(Core clause, TypeSymbol.AtModule named) {
        return readsFields.computeIfAbsent(clause, read -> {
            Map<BindingId, String> declared = new HashMap<>();
            bindingsOf(named).forEach((name, binding) -> declared.put(binding, name));
            Set<String> found = new HashSet<>();
            readsOf(read, binding -> {
                String name = declared.get(binding);
                if (name != null) {
                    found.add(name);
                }
            });
            return Set.copyOf(found);
        });
    }

    /** {@code e} with each binding {@code given} names replaced by the value it was given. */
    static Core substituted(Core e, Map<BindingId, Core> given) {
        if (e instanceof Core.Read r) {
            Core value = given.get(r.binding());
            return value != null ? value : r;
        }
        return Core.mapAll(e, child -> substituted(child, given),
                // A name slot holds a binding and nothing else, so a value put there would be
                // something the reader of that slot cannot load. Only another name may stand there.
                name -> substituted(name, given) instanceof Core.Read r ? r : name);
    }

    /** Every binding {@code e} reads, at any depth. */
    private static void readsOf(Core e, java.util.function.Consumer<BindingId> f) {
        if (e instanceof Core.Read r) {
            f.accept(r.binding());
        }
        Core.forEachChild(e, child -> readsOf(child, f));
    }
}
