package souther.compiler.check;

import souther.compiler.ast.Hir;
import souther.compiler.query.Answer;
import souther.compiler.query.Db;
import souther.compiler.query.Names;
import souther.compiler.query.Scopes;
import souther.compiler.types.TypeSymbol;
import souther.compiler.types.TypeSymbols;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What one module's reading of a declaration's clauses states, clause by clause.
 *
 * <p>Built for holding two readings of one declaration against each other. The comparison is over
 * {@link TermMeaning} and not over the term, because what is being asked is whether the two say the
 * same thing — a term carries where it was written, and two readings of one declaration made in two
 * modules are made over trees the reader never edited.
 *
 * <p>Clause by clause, keyed by {@link Clause.Ref}. Two readings of a declaration reach its clauses
 * in the order the declaration writes them, but an include brings a clause in under a second name
 * and a comparison by position would pair the wrong two the first time a declaration spreads
 * another. The reference is what the declaration answers by, so it pairs what the declaration would
 * call one clause.
 *
 * <p>What stopped is carried beside what stated rather than dropped. A reading in which every clause
 * stopped agrees with another in which every clause stopped, so a comparison that saw only the
 * stated ones would come back green over two readings that read nothing.
 */
final class ClauseReadings {

    private ClauseReadings() {}

    /**
     * One reading of one declaration's clauses.
     *
     * @param stated what each clause that typed says
     * @param stopped the clauses this reading has no form for, in the order it reached them
     */
    record Read(Map<Clause.Ref, TermMeaning> stated, List<Clause.Ref> stopped) {

        /** How many clauses the reading reached at all — what a control counts. */
        int reached() {
            return stated.size() + stopped.size();
        }
    }

    /**
     * {@code named}'s clauses as {@code module} reads them.
     *
     * <p>The scope is {@code module}'s and the clauses are the declaring module's: that pairing is
     * what {@link RuleReadingSource} is, and it is what makes this a question about the reader at
     * all. Handed one module's scope together with that same module's clauses, every reading would
     * be of a declaration the reader wrote.
     */
    static Read readBy(Db db, String module, TypeSymbol.AtModule named) {
        Symbols symbols = Scopes.derived(db, module).value();
        Clauses clauses = new Clauses(symbols, RuleReadings.declaredBy(db, module),
                ClauseLocations.NONE, DeclarationReadings.NONE, ClauseMeanings.NONE);
        Map<Clause.Ref, TermMeaning> stated = new LinkedHashMap<>();
        List<Clause.Ref> stopped = new ArrayList<>();
        for (TypeOps.Declared each : clauses.of(named).reached()) {
            Clause.Ref clause = Clause.Ref.of(each);
            switch (clauses.typed(each.asExpanded(), named)) {
                case TypedClause.Typed typed ->
                        stated.put(clause, TermMeaning.of(typed.value()));
                case TypedClause.Stopped _ -> stopped.add(clause);
            }
        }
        return new Read(Map.copyOf(stated), List.copyOf(stopped));
    }

    /**
     * {@code named} as {@code module} publishes it.
     *
     * <p>The reading is made the same way {@link #readBy} makes one, so what a test comparing two
     * meanings is comparing is what a store keyed by the declaration would hold.
     */
    static DeclarationMeaning meaningOf(Db db, String module, TypeSymbol.AtModule named) {
        Symbols symbols = Scopes.derived(db, module).value();
        return DeclarationMeaning.of(symbols.declaredNode(named),
                new Clauses(symbols, RuleReadings.declaredBy(db, module),
                        ClauseLocations.NONE, DeclarationReadings.NONE, ClauseMeanings.NONE));
    }

    /** One declaration, the module that wrote it, and a module that reads it without having. */
    record Edge(String asking, String declaring, TypeSymbol.AtModule named) {

        @Override
        public String toString() {
            return asking + " reads " + named;
        }
    }

    /**
     * Every declaration a module of {@code modules} reaches and did not write.
     *
     * <p>Read off what each module's scope reaches rather than off its import lines, so a
     * declaration reached through another module is here as well. The population is the
     * compilation's own, so a declaration added to the sources under it is swept without anybody
     * adding it.
     *
     * <p>Whether the declaration writes a clause is not asked. A declaration that spreads another
     * reaches that one's clauses and writes none of its own, and it is exactly the shape a
     * comparison by position would pair wrongly — selected on what it writes, it would be the case
     * left out.
     */
    static List<Edge> importsOf(Db db, List<String> modules) {
        Set<String> compiled = new LinkedHashSet<>(modules);
        List<Edge> edges = new ArrayList<>();
        for (String asking : modules) {
            Answer<Map<String, Hir.Def>> reachable = db.ask(new Names.Reachable(asking));
            if (!reachable.present()) {
                continue;
            }
            Set<TypeSymbol.AtModule> seen = new LinkedHashSet<>();
            for (Hir.Def def : reachable.value().values()) {
                if (!(def instanceof Hir.Data data) || data.declaredBy(asking)
                        || !compiled.contains(data.declaredIn())) {
                    continue;
                }
                TypeSymbol.AtModule named = TypeSymbols.declared(data.declaredKey());
                if (seen.add(named)) {
                    edges.add(new Edge(asking, data.declaredIn(), named));
                }
            }
        }
        return edges;
    }
}
