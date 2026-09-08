package souther.compiler.inputs;

import souther.compiler.ast.Hir;
import souther.compiler.check.NumberAt;
import souther.compiler.check.PartId;
import souther.compiler.check.RuleRef;
import souther.compiler.check.RuleReadingSource;
import souther.compiler.check.DeclarationReadings;
import souther.compiler.check.FieldDomains;
import souther.compiler.check.NarrowedBounds;
import souther.compiler.check.RuleKey;
import souther.compiler.check.Owed;
import souther.compiler.check.Requirement;
import souther.compiler.check.RuleAccounting;
import souther.compiler.check.RuleCitation;
import souther.compiler.check.ProjectionEvidence;
import souther.compiler.check.Rules;
import souther.compiler.check.Shape;
import souther.compiler.check.Symbols;
import souther.compiler.check.TypeView;
import souther.compiler.numeric.NumericDomain;
import souther.compiler.check.ReadingPolicy;
import souther.compiler.types.Type;
import souther.compiler.types.TypeSymbol;
import souther.compiler.values.AdmissibleSet;
import souther.compiler.values.StringMachineAnswers;

import java.util.ArrayList;
import java.util.List;

/**
 * The value a position is inside: what it is called, and what its rules leave each position of it
 * able to hold.
 *
 * <p>One reading per parameter, not one per record met on the way down. A clause on the outer
 * record relates positions at any depth it can name, and rebuilding the reading at each record is
 * how {@code interval.startsAt < cap} stopped reaching {@code interval.startsAt}.
 */
record PlacedRules(TermPath root, TypeSymbol value, Rules rules, Reaching alsoReaching,
                   souther.compiler.values.Allowance<TermPath> sets,
                   DeclarationReadings machines) {

    /**
     * The value this case was narrowed out of, whose rules name some of the same positions.
     *
     * <p>The one way a rule of one value reaches a position of another, and it exists because the
     * language has one: a field every case of a sum spreads is readable on a value of the sum, so a
     * clause written up there is about the field a row writes down here. Nothing else crosses — what
     * a case declares of its own is not readable above it, and a clause of the sum has no name for
     * it.
     *
     * <p><b>Not a search up the roots.</b> What crosses is settled by the shared spread and by
     * nothing else, so a name that is not one of those is not looked for above and a value that is
     * not the one this was narrowed out of is never asked. Answered by walking outwards until
     * something has a rule of that name, a clause of a case would answer for the same field of every
     * other.
     *
     * @param outer    the rules of the value the narrowing was taken from
     * @param crossing the narrowing, and which of the value above's names reach across it. Held and
     *                 not spelled out again here: where a name written above stands is one fact,
     *                 and this reading is one of the two that ask it
     */
    record Reaching(PlacedRules outer, SharedNames crossing) {

        /** What the value above calls the position at {@code here}, or null where it calls it
         *  nothing. */
        TermPath outerPathOf(TermPath here) {
            return crossing.outerPathOf(here);
        }
    }

    /**
     * What the rules reaching a value of {@code type} leave and place, read under the name the
     * signature wrote.
     *
     * <p>The written name and not the record under it. A name wrapped round a record is a place the
     * same rule can be written — {@code data NonEmptyBag = Bag invariant List.length(value.xs) >= 1}
     * states what {@code Bag} could have stated about its own field — and reading the record alone
     * drops every clause of every name round it. What those clauses leave is read at the paths the
     * record's own positions have, since a name wrapped round a value is not a step of the path.
     *
     * <p>One reading and not two. A wrapper's clauses place ends, project ranges onto the record's
     * fields, and can be ones this could not read, and all three are answers about the same value:
     * lifted as ends alone, a wrapper relating two of the record's fields narrowed nothing and a
     * wrapper clause nothing could read left every edge under it looking certain.
     */
    static PlacedRules of(TermPath root, Type type, RuleReadingSource source,
                          ReadingPolicy policy) {
        return of(root, type, source, policy, null, DeclarationReadings.NONE);
    }

    /** The same, asking {@code machines} for what somebody has already made of the value's string
     *  rules before building any of it. */
    static PlacedRules of(TermPath root, Type type, RuleReadingSource source,
                          ReadingPolicy policy, DeclarationReadings machines) {
        return of(root, type, source, policy, null, machines);
    }

    /** The same, of a value narrowed out of another whose rules name some of the same positions. */
    static PlacedRules of(TermPath root, Type type, RuleReadingSource source, ReadingPolicy policy,
                          Reaching alsoReaching, DeclarationReadings machines) {
        TypeSymbol read = readAs(type, source.symbols());
        // One composer for this reading, made where the reading is. What {@link #admits} builds is
        // the set a position of this value finally admits, met out of the rules here and the rules
        // of the value this was narrowed from — one answer, however many paths are asked about it.
        // Made per call instead, every ask would get its own allowance and the whole of what a
        // reading costs would be bounded by nothing.
        return new PlacedRules(root, read, Rules.of(read, source, policy, machines), alsoReaching,
                policy.allowanceForAdmittedValues(answersFor(read, machines).lending()),
                machines);
    }

    /**
     * The answers about the string machines of the declaration {@code read} names, or none where it
     * names no declaration of a module.
     *
     * <p>What is lent to an allowance and to the questions {@link #answers()} serves, and not a
     * reading's own answers — a reading makes those where it is made, and this is asked again each
     * time because one of them is not a value and this is inside an answer. So the empty case is
     * {@link StringMachineAnswers#NONE} and not a reading's: there is nothing here for anything to
     * keep what it builds for.
     */
    private static StringMachineAnswers answersFor(TypeSymbol read, DeclarationReadings machines) {
        return read instanceof TypeSymbol.AtModule at
                ? machines.of(at.key()) : StringMachineAnswers.NONE;
    }

    /** The answers about the string machines of the value these rules are read of — the ones a
     *  question put to {@link #bounds()} is answered from, since that is the declaration whose
     *  reading made them. */
    StringMachineAnswers answers() {
        return answersFor(value, machines);
    }

    /**
     * The path the value above calls {@code path} by, or null where nothing above names it.
     *
     * <p>Every question below asks this before it asks itself, so a rule written above is read at
     * the position it is about wherever a reader of this one is asked about that position — and a
     * reader that forgot to ask would be the one place a clause of the value above went unread.
     */
    private TermPath alsoAt(TermPath path) {
        return alsoReaching == null ? null : alsoReaching.outerPathOf(path);
    }

    /**
     * What the clauses of this value call the position at {@code path}, or null where none of them
     * can name it.
     *
     * <p>The one translation between where a position is and what the rules of one value call it.
     * A {@code GlobalQuery} says {@code tag} and the position is {@code query@GlobalQuery.tag}: the
     * rules are read of a declaration, and which declaration that is is what {@link #root} says. Put
     * in the path instead, a location would have to know which value's rules were being read of it,
     * which is not a fact about where it is.
     *
     * <p>Null for a position under no root of this one as readily as for one no clause can name:
     * a reading of one value has nothing to say about a position in another, and answering with a
     * name would be this value's rules read at somebody else's position.
     */
    private RuleKey keyOf(TermPath path) {
        return path.ruleKeyUnder(root);
    }

    /** What the rules leave the numbers, ends and narrowings of this value. */
    FieldDomains bounds() {
        return rules.bounds();
    }

    /**
     * Everything the rules of this value placed, each as an address in this value's own words.
     *
     * <p>This value's own and never what reaches it from above: a rule written in the value a
     * narrowing was taken from is placed under that value and counted there, and counting it here as
     * well would have one rule owing an answer twice.
     *
     * <p>What was placed and not what a position could be asked. A position no rule was written
     * about is not something anybody has to account for, and a reading that took every name it can
     * answer at would be counting this compiler's questions rather than the model's statements.
     */
    List<PlacementSeed> placed() {
        List<PlacementSeed> out = new ArrayList<>();
        // Rule by rule, and every question each of them raised. What a reading holds afterwards is
        // what the rules came to together — a field two clauses narrow is one narrowed field — so an
        // account taken from there is one either clause can go missing from with nothing to see.
        bounds().accounting().values().forEach(accounting ->
                accounting.answers().keySet().forEach(owed ->
                        out.add(PlacementSeed.of(root, owed, accounting.cited()))));
        return List.copyOf(out);
    }

    /**
     * The same rules with some of this value's coordinates settled.
     *
     * <p>The value's own paths and not this input's: what a caller out here calls {@code p.x} is
     * {@code x} to the rules of the value {@code p} holds, and the translation is the caller's.
     */
    FieldDomains.Settled given(java.util.Map<NumberAt<RuleKey>,
            souther.compiler.numeric.Count> settled) {
        return bounds().given(settled);
    }

    /**
     * What is left for the position at {@code path}, which is read from the value this is of.
     *
     * <p>Nothing at a position inside a sequence, and nothing at the value's own path. The clauses
     * read here relate the fields of a record ({@link TermPath#ruleKeyUnder}, since the value this
     * is of need not be the parameter), and neither of those is one of them.
     */
    NarrowedBounds at(TermPath path) {
        RuleKey where = keyOf(path);
        NarrowedBounds here = where == null || where.isTheValueItself() ? NarrowedBounds.NOTHING
                : bounds().at(where);
        TermPath above = alsoAt(path);
        if (above == null) {
            return here;
        }
        // Both values state something about the one position, so what is left is what both leave —
        // and which declarations are holding it follows the end that survived, which is why the two
        // meet as one value. Met apart, the reading whose end lies further out kept its names, and
        // an author was sent to a clause that moved this end nowhere.
        return here.meet(alsoReaching.outer().at(above));
    }

    /**
     * Where the position at {@code path} stops once every rule reaching this value has been taken
     * in, which is not the same as what {@link #at} projects onto it.
     */
    NumericDomain.Bounds leftAt(TermPath path,
                                NumberAt.OfWhatNumber kind) {
        RuleKey where = keyOf(path);
        NumericDomain.Bounds here = where == null ? null : bounds().leftAt(where, kind);
        TermPath above = alsoAt(path);
        if (above == null) {
            return here;
        }
        NumericDomain.Bounds outer = alsoReaching.outer().leftAt(above, kind);
        return here == null ? outer : outer == null ? here : here.meet(outer);
    }

    /**
     * Which values the position at {@code path} may hold, and how much of its rules was read.
     *
     * <p>Asked at every path, the value's own included: what a name wraps is at no path of its own
     * and is the position a reader of a newtype asks about, which is why this is not the empty
     * answer where {@link #at} is.
     */
    AdmissibleSet admits(TermPath path) {
        AdmissibleSet here = rules.admits(under(path));
        TermPath above = alsoAt(path);
        if (above == null) {
            return here;
        }
        // What both leave, and short of what either was short of. A value the case admits and the
        // value above refuses stands nowhere, and a rule either of them could not read leaves the
        // set wider than the rules are however completely the other was read.
        AdmissibleSet outer = alsoReaching.outer().admits(above);
        // Spent from this reading's own allowance, which is what every position of it is met out
        // of. The two sides were read from two declarations and each was read in full where it was
        // written; what is being built here is a third set, the one this position finally admits.
        souther.compiler.values.Allowance.Composed made = sets.meet(
                souther.compiler.values.Sameness.Block.of(path),
                here.approximation(), outer.approximation());
        AdmissibleSet.Completeness read = bothRead(here.completeness(), outer.completeness());
        // And where it was not built, that is not a rule going unread. Both rules were read; what
        // was not worked out is what they leave between them, and a reader told a rule went unread
        // would go looking for one to change.
        return new AdmissibleSet(made.set(), made.gaveUp()
                ? alsoWidened(read, new AdmissibleSet.Widening.ExactValuesTooCostly())
                : read);
    }

    /** The same completeness, and one more thing standing between the set and the rules. */
    private static AdmissibleSet.Completeness alsoWidened(AdmissibleSet.Completeness read,
                                                          AdmissibleSet.Widening also) {
        java.util.Set<AdmissibleSet.Widening> why = new java.util.LinkedHashSet<>();
        if (read instanceof AdmissibleSet.Completeness.Wider it) {
            why.addAll(it.why());
        }
        why.add(also);
        return new AdmissibleSet.Completeness.Wider(why);
    }

    /** What two readings of one position come to: read in full only where both were. */
    private static AdmissibleSet.Completeness bothRead(AdmissibleSet.Completeness here,
                                                       AdmissibleSet.Completeness outer) {
        if (here instanceof AdmissibleSet.Completeness.Complete
                && outer instanceof AdmissibleSet.Completeness.Complete) {
            return AdmissibleSet.READ_IN_FULL;
        }
        java.util.Set<AdmissibleSet.Widening> why = new java.util.LinkedHashSet<>();
        if (here instanceof AdmissibleSet.Completeness.Wider it) {
            why.addAll(it.why());
        }
        if (outer instanceof AdmissibleSet.Completeness.Wider it) {
            why.addAll(it.why());
        }
        return new AdmissibleSet.Completeness.Wider(why);
    }

    /**
     * Whether this reading ended at {@code path} with a declaration still to be read under it.
     *
     * <p>Not a shortfall. Nothing is declared at the position for these rules to state anything
     * about every value of — what stands there is a container, an optional, or a choice between
     * declarations — so what is written under it is written about a value one position down, and
     * this reading is done here rather than short here.
     *
     * <p>What a caller does with it is show that something took the rules over
     * ({@link RuleHandoffs}). Answered instead by asking whether the type graph has a rule
     * somewhere below, the position above was short of a rule no row could reach: the walk had gone
     * to it, one position down (#1072).
     */
    boolean handsTheRulesOnAt(TermPath path) {
        RuleKey where = keyOf(path);
        return where != null && bounds().handedOn().contains(where);
    }

    /**
     * The questions the rules reaching this value raise about the position at {@code path} that
     * nothing answered, each with the rule that raised it.
     *
     * <p>Asked of the questions and not of the readings. A reading being short of a position's
     * rules is a fact about that reading; whether a rule went unaccounted for is a fact about the
     * model, and the two come apart wherever one reading answers what another could not — which is
     * every bound on a number, since the reading that turns clauses into sets of values has no word
     * for a range.
     */
    List<RuleAccounting.Unanswered> unanswered(TermPath path) {
        RuleKey where = keyOf(path);
        if (where == null) {
            return List.of();
        }
        List<RuleAccounting.Unanswered> out = new ArrayList<>();
        bounds().accounting().values().forEach(accounting ->
                accounting.unansweredQuestions().stream()
                        .filter(each -> switch (each.owed()) {
                            case Owed.AdmittedValues it -> it.path().equals(where);
                            case Owed.Boundary it -> it.on().position().equals(where);
                        })
                        .forEach(out::add));
        TermPath above = alsoAt(path);
        if (above != null) {
            out.addAll(alsoReaching.outer().unanswered(above));
        }
        return List.copyOf(out);
    }

    /**
     * The rules reaching this value that nothing worked out what they raise at {@code path}, each
     * with the rule and what stopped there.
     *
     * <p>Beside the questions and not among them, for the reason they are told apart at all: a
     * question names what has to be answered and this names a place where nothing knows what the
     * question is. Both hold the measures open, and which of the two a reader is being told is
     * what says whether there is anything to go and answer.
     */
    List<RuleUnclassifiedAt> unclassified(TermPath path) {
        RuleKey where = keyOf(path);
        if (where == null) {
            return List.of();
        }
        List<RuleUnclassifiedAt> out = new ArrayList<>();
        bounds().accounting().values().forEach(accounting ->
                accounting.undetermined().stream()
                        .filter(each -> each.at().equals(where))
                        .forEach(each ->
                                out.add(new RuleUnclassifiedAt(accounting.cited(), each))));
        TermPath above = alsoAt(path);
        if (above != null) {
            out.addAll(alsoReaching.outer().unclassified(above));
        }
        return List.copyOf(out);
    }

    /** How a reader finds the rule, which says which rule it is, and one place its classification
     *  did not come out. */
    record RuleUnclassifiedAt(RuleCitation cited, Requirement.BoundaryUndetermined at) {}

    /**
     * How much of what the rules say the bounds at {@code path} are able to state.
     *
     * <p>What it licenses is a whole value: a row at an edge is a value with that edge in it, so a
     * rule the bounds cannot express is a way that value can be refused however plainly the numbers
     * beside it were read. Which is why it is one answer for every position of a value — and why it
     * is asked at a position all the same, because a position can be of two values.
     *
     * <p><b>A shared field belongs to the case and to the value the sum sits in.</b> A clause
     * written up there is about the field a row writes down here, so the rules reaching this
     * position are two systems. A certificate is a theorem about one of them — that its relations
     * carry nothing its box does not already describe — and two systems that each hold it separately
     * need not hold it together, since a relation from one can carry a bound of the other's further.
     * So neither certificate is one for the pair, and what comes back says so
     * ({@link ProjectionEvidence.Cause.TwoValuesStateRulesAboutIt}) rather than handing over
     * whichever was to hand.
     *
     * <p>Asked of the position and not of the value, because which values reach it is what differs
     * between one position and the next. Answered for the value alone, a field the rules above bound
     * came back proved exactly representable by a reading that never saw them.
     */
    ProjectionEvidence projection(TermPath path) {
        return proofAt(path).evidence();
    }

    /**
     * The evidence about {@code path}, and how many values actually stated rules that reach it.
     *
     * <p>The second is carried and not read off the first. Whether a value states anything and
     * whether what it states is exactly representable are two questions, and a value that states
     * nothing answers the second with a certificate — it has lost nothing, having nothing to lose.
     * Taken for the first, a shared field of a sum nobody wrote a clause about would be a position
     * two systems reach, and the case's own certificate would be given up to a pair that is really
     * one.
     *
     * <p>Nor is it read off the narrowing. That a name reaches here from the value above says the
     * value above can name it, which is not the same as its having said anything — and the two come
     * apart at the plainest model there is, a sum with no clause of its own.
     *
     * <p>Counted through the whole way up, because a sum that states nothing may sit in a value that
     * states plenty: what reaches this position is every value on the way that wrote rules, and
     * stopping at the first empty one would lose the ones above it.
     */
    private Proof proofAt(TermPath path) {
        ProjectionEvidence here = rules.projection();
        int mine = rules.anythingWasWritten() ? 1 : 0;
        TermPath above = alsoAt(path);
        if (above == null) {
            return new Proof(here, mine);
        }
        Proof outer = alsoReaching.outer().proofAt(above);
        int stating = mine + outer.stating();
        List<ProjectionEvidence.Cause> causes = new ArrayList<>();
        causesOf(here, causes);
        causesOf(outer.evidence(), causes);
        if (!causes.isEmpty()) {
            return new Proof(new ProjectionEvidence.NotCertified(causes), stating);
        }
        // At most one value said anything, so what reaches this position is that one system and the
        // certificate for it is a certificate for what is here.
        if (stating < 2) {
            return new Proof(mine == 1 ? here : outer.evidence(), stating);
        }
        return new Proof(new ProjectionEvidence.NotCertified(
                List.of(new ProjectionEvidence.Cause.TwoValuesStateRulesAboutIt())), stating);
    }

    /** What is known about a position, and how many values stated rules that reach it. */
    private record Proof(ProjectionEvidence evidence, int stating) {}

    /** The causes this evidence gives, and none where it certifies. */
    private static void causesOf(ProjectionEvidence evidence,
                                 List<ProjectionEvidence.Cause> into) {
        if (evidence instanceof ProjectionEvidence.NotCertified it) {
            it.causes().stream().filter(each -> !into.contains(each)).forEach(into::add);
        }
    }

    /**
     * Whether the gathering reached every rule written about the position at {@code path}.
     *
     * <p>Asked of the gathering, which is what knows. A position can carry both a rule that arrived
     * and could not be read and a subtree the walk never entered, and what a reading came back
     * short of has one slot to answer in — so reach read off {@link #admits} is lost wherever
     * another reason won it.
     */
    boolean everyRuleReachedAt(TermPath path) {
        TermPath above = alsoAt(path);
        return rules.everyRuleReachedAt(under(path))
                && (above == null || alsoReaching.outer().everyRuleReachedAt(above));
    }

    /**
     * What the rules of this value call {@code path}, for the questions every position answers.
     *
     * <p>Every position under this reading's root has a name here, the root's own included. What a
     * sequence holds is not one of them: an element is a value with a declaration of its own and a
     * reading is opened at it, so a path under this root never steps into one — and a position this
     * reading is not of is not a position it may be asked about.
     */
    private RuleKey under(TermPath path) {
        RuleKey where = keyOf(path);
        if (where == null) {
            throw new IllegalArgumentException(
                    path + " is not a position of the value read at " + root);
        }
        return where;
    }

    /** The ends the clauses reaching this value place on the coordinates at {@code path}, which is
     *  a different question from what {@link #at} leaves them. */
    List<FieldDomains.Placed> placedAt(TermPath path) {
        RuleKey where = keyOf(path);
        List<FieldDomains.Placed> here =
                where == null || where.isTheValueItself() ? List.of() : bounds().placedAt(where);
        TermPath above = alsoAt(path);
        if (above == null) {
            return here;
        }
        // Both, and kept apart. Each end carries the rule that drew it, so an author reading a
        // report of this position is sent to the clause that wrote the end and not to the value it
        // happened to be read through.
        List<FieldDomains.Placed> out = new ArrayList<>(here);
        out.addAll(alsoReaching.outer().placedAt(above));
        return List.copyOf(out);
    }

    /**
     * The ends a conjunct that placed none moved at the value this reading is opened at.
     *
     * <p>What {@link #placedAt} leaves out. The ends of a value's own coordinates are read off the
     * clauses as they are written, which is a reading that sees no end where no comparison places
     * one — so an end such a conjunct moved is invisible there, and is here.
     *
     * <p>Only the value's own. Everything under it is a position of its own and is answered at that
     * position, by {@link #placedAt}, which does not leave these out.
     */
    List<FieldDomains.Placed> movedAtTheValue() {
        return bounds().movedEnds().stream()
                .filter(each -> each.path().isTheValueItself())
                .toList();
    }

    /**
     * The ends the value's own conjuncts state on its own coordinates.
     *
     * <p>What {@link #placedAt} leaves out for the same reason it leaves the moved ones out: the
     * ends of a value's own coordinates are read off the clauses as they are written, and this is
     * the rest of them. A rule about the strings at a position leaves them running from one place
     * to another and states no comparison, so that reading sees nothing of it.
     *
     * <p>The comparisons are here too and are not left out. An end two readings both saw is one
     * end drawn by one conjunct of one rule, and putting the two together is what
     * {@link souther.compiler.check.DeclaredBounds.End#tighter} does with them — a second copy adds
     * no name and moves nothing.
     */
    List<FieldDomains.Placed> statedAtTheValue() {
        return bounds().stated().stream()
                .filter(each -> each.path().isTheValueItself())
                .toList();
    }

    /**
     * The same, and the moved ones beside them, for a reader asking about one position.
     *
     * <p><b>Whose ends these are is this reading's answer and not the asker's.</b> They are the
     * ends of the value the reading is opened at, so at any other position they are ends of another
     * value — and a reader that took them anyway would be holding the root's ends against the
     * numbers of whatever it happened to be reading. That was a silent nothing while the only thing
     * done with them was to keep the ones on the position's own number, and stops being one the
     * moment somebody asks what became of each of them.
     */
    List<FieldDomains.Placed> ownEndsAt(TermPath path) {
        RuleKey where = keyOf(path);
        if (where == null || !where.isTheValueItself()) {
            return List.of();
        }
        List<FieldDomains.Placed> out = new ArrayList<>(statedAtTheValue());
        out.addAll(movedAtTheValue());
        return List.copyOf(out);
    }

    /**
     * The rules saying where the coordinate at {@code path} stops that no end came out of.
     *
     * <p>At every path the value has, its own included — unlike {@link #placedAt}, whose empty
     * answer at the root is what the type's own reading already gives. A rule nothing could read is
     * not given twice by anybody, and a newtype's own clause is where the question started.
     */
    List<FieldDomains.NoLine> noLineAt(TermPath path) {
        RuleKey where = keyOf(path);
        List<FieldDomains.NoLine> here = where == null ? List.of() : bounds().noLineAt(where);
        TermPath above = alsoAt(path);
        if (above == null) {
            return here;
        }
        List<FieldDomains.NoLine> out = new ArrayList<>(here);
        out.addAll(alsoReaching.outer().noLineAt(above));
        return List.copyOf(out);
    }

    /**
     * The clauses of this value's declarations that no end came out of, once each.
     *
     * <p>For the reading that draws lines rather than places ends. A rule relating two coordinates
     * places no end at either of them, and it is still a rule about where this behavior's values
     * part — so it is handed over as a clause, with the path the value it is written about stands
     * at, and read there in the vocabulary a line is drawn in.
     *
     * <p>Once per conjunct, not once per coordinate. The same conjunct is filed at each coordinate
     * it names, which is what a reader after a position wants and what a reader after a rule must
     * not have: taken as they are filed, {@code lo <= hi} would draw its line twice and owe two
     * rows where the model states one thing.
     *
     * <p>Not what any of them came to. Which of these is a line is the drawing reading's answer,
     * and this reading's word for why it drew none is no part of the question — the two read the
     * same clause with different atoms, and a clause set aside here is one the other may read.
     *
     * <p><b>What was handed on, and not what was reported.</b> Read off the findings, this carried
     * whatever the reading of ends owed an author a sentence about — so a rule that names a value,
     * which places no end and is nothing anyone has to lift, could not be passed along at all.
     */
    List<ClauseWithoutAnEnd> clausesWithoutAnEnd() {
        java.util.Map<Key, ClauseWithoutAnEnd> once = new java.util.LinkedHashMap<>();
        for (FieldDomains.WithoutAnEnd each : bounds().withoutAnEnd()) {
            once.putIfAbsent(new Key(each.part()),
                    new ClauseWithoutAnEnd(each.part(), each.read(), root, bounds().named()));
        }
        return List.copyOf(once.values());
    }

    /** What makes two of them one: which part of which rule it is. */
    private record Key(PartId<RuleRef.Invariant> part) {}

    /**
     * The declaration a value of {@code type} is read under: the name the signature wrote where it
     * names one, and the record beneath the names where it does not.
     *
     * <p>One name for both questions. Which declaration's rules reach the positions, and which
     * declaration is said to have taken an edge in, are answers about the same value — read apart,
     * an edge a wrapper narrowed was reported as narrowed by the record under it, which is a
     * declaration that may have no clause about the pair at all.
     */
    private static TypeSymbol readAs(Type type, Symbols symbols) {
        TypeSymbol written = nameOf(type);
        return written != null
                && symbols.declaredNode(written) instanceof Hir.Data
                ? written : heldIn(type, symbols);
    }

    /**
     * The declaration whose rules reach the position: the record under the names where there is
     * one, and the declaration as written where there is not.
     *
     * <p>A position that is not a record has no fields for a clause to relate, and its own rules
     * still say what a reading of them could not turn into a range — which is what keeps an edge it
     * refuses from being called writable. So the answer falls back to the name the signature wrote
     * rather than to nothing.
     */
    private static TypeSymbol heldIn(Type type, Symbols symbols) {
        TypeSymbol record = recordIn(type, symbols);
        return record != null ? record : nameOf(type);
    }

    /** The record a position holds, through the names it is written under: a value of
     *  {@code data SlotN = Slot} is a {@code Slot}, and the clauses relating its fields are
     *  {@code Slot}'s. */
    private static TypeSymbol recordIn(Type type, Symbols symbols) {
        return TypeView.of(type, symbols).shape() instanceof Shape.Product product
                ? product.name() : null;
    }

    private static TypeSymbol nameOf(Type type) {
        return type instanceof Type.Ref ref ? ref.name() : null;
    }
}
