package souther.compiler.inputs;

import souther.compiler.check.NumberAt;
import souther.compiler.check.RuleReadingContext;
import souther.compiler.check.ConstraintState;
import souther.compiler.check.Emptiness;
import souther.compiler.check.FieldDomains;
import souther.compiler.check.RuleKey;
import souther.compiler.numeric.Count;
import souther.compiler.numeric.CountDomain;
import souther.compiler.numeric.Endpoint;
import souther.compiler.numeric.LinearForm;
import souther.compiler.numeric.NumericDomain;
import souther.compiler.numeric.Rational;
import souther.compiler.numeric.Rel;
import souther.compiler.numeric.RationalCut;
import souther.compiler.semantics.TakenAs;
import souther.compiler.types.Type;
import souther.compiler.types.ValueName;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The one reading of a behavior's input, asked about a quantity over several of its positions.
 *
 * <p>What {@link Quantities} says, made. The declarations reaching this input are read under what a
 * question assumes stands ({@link StructuralContext}) — which values turned out to be which cases,
 * and which containers hold something — because a reading opened at a case says what it says of the
 * rows that are that case and of no others. A question with several positions in it, and the same
 * question with some of them settled, are answered from the same reading of the same context.
 *
 * <p>Read once per context and kept, which is a memo and not a second answer: what the rules leave
 * under a context is a function of this value and that context, so the table may be dropped without
 * changing anything this says.
 */
final class ReadQuantities implements Quantities {

    /** Every value whose rules reach this input, with the condition each reading holds under. */
    private final Map<TermPath, OpenedRules> byRoot;
    /** Every sum this input holds, and what became of each of its cases. What the choice between
     *  alternatives is folded over. */
    private final List<CasesRead> cases;
    /**
     * The world a declaration reached from here is read in: where the rules come from, how far one
     * is read, and what has already been made of it.
     *
     * <p>Held rather than asked for per question, and held as the three together. The reading of an
     * input is what a caller has; a question asked of the declarations afterwards under another
     * budget is a second reading of them, and one asked without what the reading of this input was
     * handed reads again what that reading already made ({@link RuleReadingContext}).
     */
    private final RuleReadingContext ruleReading;
    /** What the behavior takes, which is what a path of this input starts at. */
    private final Set<TermPath> roots;
    /** Every position that was read, by where it sits. What one of them was read to hold is what
     *  its own term runs between, and the reading that relates positions has a name for some of
     *  those terms and not for others. */
    private final Map<TermPath, Position> byPath;
    /**
     * What stands where a path names, as the reading that made this has it.
     *
     * <p>Beside {@link #byPath} and not the same question. That one answers whether this reading
     * measured a position, which is what a term's own bounds are read off; this one answers what a
     * path stands at whether or not the reading stopped above it, which is what an order follows
     * from. Answered here off the positions alone, a rule naming a field every case of a sum
     * spreads would be told nothing stands where the language reads a value.
     *
     * <p>The resolution and not the walk. What is handed over is one question already answered, so
     * nothing here can reach the rest of the reading or ask it something else.
     */
    private final java.util.function.Function<TermPath, Type> typeAt;
    /**
     * What each term has been fixed at, as the least and the greatest of the values fixed there.
     *
     * <p>A pair and not a value, because what a caller settles has to accumulate the same way
     * whichever order it arrives in. Fixing one position twice is fixing it at nothing, and holding
     * the last value to arrive — or the first — would make which of the two a proof names depend on
     * the order the question was asked in. Where a term has been fixed once the two are the same.
     */
    private final Map<NumericTerm, Fixed> fixed;
    /**
     * What a caller has taken in about this input beyond what the declarations say.
     *
     * <p>Reached only through {@link SearchRegion}, which is what keeps a reading of the
     * declarations from acquiring one. Kept as what was said and not as what it came to: taking in
     * accumulates, and what the whole of it leaves is worked out where everything else is.
     */
    private final List<Assumed> assumed;
    /**
     * What has already been worked out, by the context it was worked out under.
     *
     * <p>A memo of {@link #constraints} and of nothing else. What the rules leave under a context is
     * a function of this value and that context — the same question asked twice has the same answer,
     * and nothing here is consulted to decide what the answer is. So this may be dropped without
     * changing what this reading says, and is here because a search asks the same context a great
     * many times: where a form runs, whether anything is left, and where the next form runs are
     * three questions under one context, and reading every declaration again for each of them is
     * what a row costs three times over.
     *
     * <p>Per value, since the readings are conditioned on what this refinement fixed. A table shared
     * between refinements would answer one refinement's question out of another's.
     */
    private final Map<StructuralContext, Map<TermPath, FieldDomains.Carried<InputAtom>>> read =
            new ConcurrentHashMap<>();
    /** The same, of what those readings come to said together. Held beside them because both are
     *  asked for on their own: a proof of emptiness names a place out of the first and shows what
     *  it shows out of the second. */
    private final Map<StructuralContext, ConstraintState<InputAtom>> answered =
            new ConcurrentHashMap<>();

    /** One thing taken in about a form of this input's terms: {@code form rel 0}. */
    private record Assumed(LinearForm<NumericTerm> form, Rel rel) {}

    /** The values fixed at one term, kept as their least and greatest so that what was fixed does
     *  not depend on the order it arrived in. */
    private record Fixed(Count least, Count most) {

        Fixed and(Count also) {
            return new Fixed(least.compareTo(also) <= 0 ? least : also,
                    most.compareTo(also) >= 0 ? most : also);
        }

        boolean isOne() {
            return least.equals(most);
        }
    }

    private ReadQuantities(Map<TermPath, OpenedRules> byRoot, Set<TermPath> roots,
                           Map<TermPath, Position> byPath, List<CasesRead> cases,
                           java.util.function.Function<TermPath, Type> typeAt,
                           Map<NumericTerm, Fixed> fixed,
                           RuleReadingContext ruleReading, List<Assumed> assumed) {
        this.cases = List.copyOf(cases);
        this.ruleReading = ruleReading;
        this.typeAt = typeAt;
        this.assumed = List.copyOf(assumed);
        // In the order the behavior declares its parameters. A proof of emptiness names one of them
        // and a report is a document compared against the one written last time, so an order read
        // off a hash would move which parameter is named between runs.
        this.byRoot = Collections.unmodifiableMap(new LinkedHashMap<>(byRoot));
        this.roots = Set.copyOf(roots);
        this.byPath = Map.copyOf(byPath);
        // Kept in the order the fixings arrived, so that the order they are answered in is chosen
        // here rather than inherited. An immutable copy iterates in an order salted once per JVM
        // run, which is a fine order and not one anybody chose — read off it, which of two
        // contradictions a proof names would move between runs of the same compiler.
        this.fixed = Collections.unmodifiableMap(new LinkedHashMap<>(fixed));
    }

    /** Before anything is fixed. */
    static ReadQuantities of(Map<TermPath, OpenedRules> byRoot, Set<TermPath> roots,
                             Map<TermPath, Position> byPath, List<CasesRead> cases,
                             java.util.function.Function<TermPath, Type> typeAt,
                             RuleReadingContext ruleReading) {
        return new ReadQuantities(byRoot, roots, byPath, cases, typeAt, Map.of(), ruleReading,
                List.of());
    }

    /**
     * Both orders of {@code term}, from where the reading has its subject standing.
     *
     * <p>Carried into every refinement of this reading rather than answered against what is fixed:
     * what a term is measured on is a fact about where it sits, and fixing a position at a value
     * says nothing about the order that value is counted on.
     */
    @Override
    public TermOrders ordersOf(NumericTerm term) {
        // Refused for a term under nothing this behavior takes, the same as every other question
        // here. What an operation answers with follows from the operation alone where the type is
        // absent, so a term of another input comes back with an order on one end and nothing on the
        // other — an answer about no reading, wearing this one's name.
        held(term);
        return TermOrdering.of(term, typeAt.apply(term.subjectPath()),
                ruleReading.source().symbols());
    }

    @Override
    public int mostHeldAt(PositionId at) {
        Position position = byPath.get(at.at());
        // Refused where this reading has no such position, the way a term under nothing this
        // behavior takes is refused. A coordinate a value is built at is spelled the same way and is
        // not one of these, and answered with "no rule bounds it" the reading would be saying
        // something about a place it has never been.
        if (position == null) {
            throw new IllegalArgumentException(
                    "`" + at.at().discriminated() + "` is no position of this input, so there is"
                            + " nothing here to say how many it holds");
        }
        NumericTerm counted = howManyItHolds(at.at());
        if (counted == null) {
            return Integer.MAX_VALUE;
        }
        // Asked of the rules when the question arrives rather than solved for every container as
        // the reading is made: what they leave a count moves with whatever else has been settled.
        NumericDomain.Bounds runs = runsBetween(counted);
        return runs == null ? Integer.MAX_VALUE : CountDomain.mostFrom(runs.max());
    }

    /**
     * The number that is how many the value at {@code at} holds, or null where nothing takes one
     * there.
     *
     * <p>Asked of the rules of the value it sits in, which is what knows. Which number a position is
     * <em>measured</em> at is a reading's choice about that position, and it is not this question: a
     * clause can hold the length of a list against a field beside it while the position itself is
     * read by its own value, and a caller taking the measured number for the count is told there is
     * no count wherever that is so.
     *
     * <p>One owner, because it is asked twice — how many a container may hold, and whether it may
     * hold none — and the second reader written would be the one that answered from whichever of
     * the two facts was nearer to hand.
     *
     * <p>Counts of containers and nothing else. What this feeds bounds how many elements stand
     * somewhere, so an operation whose number is not how many the value holds has no business here:
     * {@code Time.hour(t)} is a number the rules take at a place and is not a count of it.
     */
    private NumericTerm howManyItHolds(TermPath at) {
        UnderARoot root = rootOf(at);
        if (root == null) {
            return null;
        }
        NumberAt<RuleKey> counted =
                byRoot.get(root.root()).rules().bounds().countedAt(root.named());
        if (counted == null
                || !(counted.of()
                        instanceof NumberAt.OfWhatNumber.OfWhatAnOperationAnswers taken)
                || !(taken.operation() instanceof ValueName.Stdlib operation)) {
            return null;
        }
        NumericTerm.TakenOf term =
                NumericTerm.TakenOf.of(operation, at, typeAt.apply(at),
                        ruleReading.source().symbols());
        return term != null && term.takenAs() instanceof TakenAs.HowManyItHolds ? term : null;
    }

    /**
     * The value {@code path} is a position of and what that value's rules call it, or null where no
     * value this reading holds can name it.
     *
     * <p>The nearest, which is the one whose clauses can name it: a field of a case is under the
     * parameter as well, and what the parameter's own rules say stops at the narrowing. Read as the
     * outermost, a clause of the sum would be asked about a position inside one of its cases.
     *
     * <p><b>The name comes back with the root, because it is why that root was chosen.</b> Handed
     * back alone, every caller works the name out again and has to answer for a root it cannot
     * name — and the answers do not agree: one drops the term, one refuses. A root a caller is
     * given is one whose rules name the place, so there is no such case to answer for.
     */
    private UnderARoot rootOf(TermPath path) {
        UnderARoot nearest = null;
        for (TermPath root : roots) {
            RuleKey named = path.ruleKeyUnder(root);
            if (named != null
                    && (nearest == null || root.isAtOrUnder(nearest.root()))) {
                nearest = new UnderARoot(root, named);
            }
        }
        return nearest;
    }

    /** A value whose rules reach a place, and what those rules call it. Made only by
     *  {@link #rootOf}, so holding one is holding a root that can name what was asked about. */
    private record UnderARoot(TermPath root, RuleKey named) {}

    /**
     * The rules of every value this context says stands, read with what is fixed under each and
     * said in this input's names.
     *
     * <p>Under the context and not all of them. A reading opened at a case holds of the rows whose
     * value turned out to be that case, so putting one into a space asked about rows that are some
     * other case would be stating a rule of nobody's — and putting every case in at once is a sum
     * refusing an input between its alternatives.
     */
    private Map<TermPath, FieldDomains.Carried<InputAtom>> conditioned(StructuralContext under) {
        Map<TermPath, FieldDomains.Carried<InputAtom>> had = read.get(under);
        if (had != null) {
            return had;
        }
        Map<TermPath, FieldDomains.Carried<InputAtom>> made = new LinkedHashMap<>();
        byRoot.forEach((root, opened) -> {
            if (under.holds(opened.opening())) {
                made.put(root, opened.rules().given(under(root)).constraintsOver(
                        at -> called(root, at, under),
                        subject -> new InputAtom.Anonymous(root.toString(), subject)));
            }
        });
        Map<TermPath, FieldDomains.Carried<InputAtom>> answer = Collections.unmodifiableMap(made);
        read.put(under, answer);
        return answer;
    }

    @Override
    public SearchRegion region() {
        return new ReadRegion(this);
    }

    /**
     * The same rules, with {@code form rel 0} taken in as well.
     *
     * <p>Reached only through {@link ReadRegion}, so what comes back is a region and never a reading
     * of the declarations. What is kept is the assertion and not what it came to: two of them said
     * in either order are the same two, and one said twice is one.
     *
     * <p>This value back where the arithmetic cannot take the assertion in — a form over a position
     * whose values it has no spacing for. Kept anyway, it would sit in the state as a rule about a
     * number nothing knows how to space, which {@link souther.compiler.numeric.NumericDomain#assume}
     * refuses outright; declined here, the region still holds everything that reaches the border,
     * which is the direction every reader of it depends on.
     */
    ReadQuantities assuming(LinearForm<NumericTerm> form, Rel rel) {
        if (form == null || form.coefs().isEmpty()) {
            return this;
        }
        form.coefs().keySet().forEach(this::held);
        // Refused where the form is over positions no one value has, the same as a question about
        // where it runs: what would be taken in is a condition on a row nobody can write.
        StructuralContext under = asked(form.coefs().keySet());
        for (NumericTerm term : form.coefs().keySet()) {
            if (spacingOf(constraints(under).numbers(), term, called(term, under)) == null) {
                return this;
            }
        }
        Assumed taking = new Assumed(form, rel);
        if (assumed.contains(taking)) {
            return this;
        }
        List<Assumed> both = new ArrayList<>(assumed);
        both.add(taking);
        return new ReadQuantities(byRoot, roots, byPath, cases, typeAt, fixed, ruleReading,
                both);
    }

    /**
     * The rules of every parameter, renamed into this input's vocabulary and said together.
     *
     * <p>Renamed and not read again ({@link FieldDomains.Settled#constraintsOver}). What a rule says
     * is a relation between subjects, and it says the same thing whatever they are called — so what
     * reaches here is each parameter's reading, under names this input can spell, and a subject it
     * cannot spell under a name of its own so that the rules through it are not lost.
     *
     * <p>Said together, which is what makes this a constraint space rather than a product of them.
     * Two parameters are related by nothing the declarations say, so meeting their rules leaves
     * every answer where it was; what it does is leave somewhere for a rule that relates them to be
     * said at all.
     *
     * <p>And what each number is on its own goes in here rather than being met on afterwards.
     * Projecting does not distribute over meeting: a rule holding two numbers at one apiece says
     * nothing about a form that also names a third the rules leave unbounded, and met afterwards
     * against a floor this reading did have, the rule is gone.
     */
    private ConstraintState<InputAtom> constraints(StructuralContext under) {
        ConstraintState<InputAtom> had = answered.get(under);
        if (had != null) {
            return had;
        }
        ConstraintState<InputAtom> made = ConstraintState.top();
        // What the values of this space cost to work out. One for the space and not one per
        // parameter: what each parameter was read under is the allowance of its own declaration,
        // and the set a position finally admits here is met out of all of them — so this is the
        // answer being built and this is where building it is charged. Handed to each meet, since
        // that is where a set neither reading holds comes to be.
        souther.compiler.values.Allowance<InputAtom> sets =
                ruleReading.policy().allowanceForAdmittedValues();
        for (FieldDomains.Carried<InputAtom> each : conditioned(under).values()) {
            made = made.meet(each.constraints(), sets);
        }
        // And what the context itself says about the values, which is as much a part of what the
        // question is asked against as any clause. A row this question is about is one the
        // prerequisites hold of, so where the rules have a word for one of them it is said here
        // rather than left as a fact about whose rules to read.
        for (StructuralContext.Assumption each : under.assumptions()) {
            made = alsoStating(made, each, under);
        }
        // And what the caller took in, onto the same rules rather than met against the answer
        // afterwards. A condition relating two positions says nothing about either of them alone,
        // so met afterwards it would be gone.
        //
        // Read under this context like everything else. A condition is about the positions it
        // names, so one taken in about a case says nothing where the value is another — left in, it
        // would be a rule about a row that is not the row being asked about.
        for (Assumed each : assumed) {
            if (!stands(each.form().coefs().keySet(), under)) {
                continue;
            }
            Map<InputAtom, souther.compiler.numeric.Granularity> spacing = new LinkedHashMap<>();
            for (NumericTerm term : each.form().coefs().keySet()) {
                souther.compiler.numeric.Granularity spaced =
                        spacingOf(made.numbers(), term, called(term, under));
                if (spaced == null) {
                    spacing = null;
                    break;
                }
                spacing.put(called(term, under), spaced);
            }
            if (spacing != null) {
                made = made.taking(over(each.form(), under), each.rel(), spacing);
            }
        }
        answered.put(under, made);
        return made;
    }

    /**
     * The same rules, with what one prerequisite of the context says about the values taken in.
     *
     * <p>Exhaustive over {@link StructuralContext.Assumption}, with no {@code default}, and this is
     * the one place a context is turned into what it means. A prerequisite of a kind added later
     * stops this compiling rather than joining the ones the rules were never told about — which is
     * how a container came to say whose rules were read without saying that it holds something.
     *
     * <p>That a value turned out to be a case says nothing here, and that is not it going unread:
     * the rules of that case are in this state and the names its value shares are spelled as the
     * numbers they stand at, which is the whole of what the narrowing means. There is no number in
     * it left over to state.
     *
     * <p>That a sequence holds something is a number, and one no clause writes: it is what the
     * question assumed by naming a position inside it. Said only where this reading measures the
     * container by how many it holds — where it does not, there is no subject to say it of, and what
     * the rules leave is wider rather than wrong.
     */
    private ConstraintState<InputAtom> alsoStating(ConstraintState<InputAtom> rules,
                                                   StructuralContext.Assumption said,
                                                   StructuralContext under) {
        switch (said) {
            case StructuralContext.Assumption.TheCaseAt _ -> {
                return rules;
            }
            case StructuralContext.Assumption.HoldingSomething it -> {
                NumericTerm counted = howManyItHolds(it.sequence());
                if (counted == null) {
                    return rules;
                }
                InputAtom.Named atom = called(counted, under);
                souther.compiler.numeric.Granularity spaced =
                        spacingOf(rules.numbers(), counted, atom);
                return spaced == null ? rules : rules.taking(
                        LinearForm.<InputAtom>atom(atom)
                                .minus(LinearForm.constant(BigDecimal.ONE)),
                        Rel.GE, Map.of(atom, spaced));
            }
        }
    }

    /**
     * Whether every one of these terms stands in the values {@code under} describes.
     *
     * <p>Asked as whether the context already says what each of them needs, and not as whether it
     * could. A condition about a case holds of the rows that are that case; a context that has not
     * settled which case the value is is one such a condition says nothing in, and taking it in
     * there would state it of every row. The two questions agree wherever a context is built from
     * everything taken in — which is where these are asked from today, and not a reason to ask the
     * weaker one.
     */
    private static boolean stands(Collection<NumericTerm> terms, StructuralContext under) {
        for (NumericTerm term : terms) {
            if (!under.covers(StructuralContext.of(term.subjectPath()))) {
                return false;
            }
        }
        return true;
    }

    /**
     * What this reading has been told stands, from everything taken in so far.
     *
     * <p>Assembled here and nowhere else, out of the three things that can say a value is a case:
     * where a fixing put a number, what a condition was taken in about, and — where a question is
     * being answered — what the question names. A reader working it out from the paths it happens to
     * hold would answer the same question two ways as soon as two of them were taken in.
     *
     * <p>In the order the terms are written down, which is a settled order and not the order they
     * arrived in. Two things said under two cases contradict whichever was said first, and a
     * disagreement carrying the order the questions were asked in would name the two narrowings one
     * way round for one caller and the other way round for the next.
     */
    private StructuralContext.Merge accumulated() {
        StructuralContext.Merge merged = new StructuralContext.Merge.Together(
                StructuralContext.NONE);
        List<NumericTerm> said = new ArrayList<>(fixed.keySet());
        assumed.forEach(each -> said.addAll(each.form().coefs().keySet()));
        said.sort(java.util.Comparator.comparing(NumericTerm::toString));
        for (NumericTerm term : said) {
            if (!(merged instanceof StructuralContext.Merge.Together it)) {
                return merged;
            }
            merged = it.context().merge(StructuralContext.of(term.subjectPath()));
        }
        return merged;
    }

    /**
     * The context a question over {@code terms} is answered in, or the disagreement that says no
     * value is being asked about.
     *
     * <p>The question's own and what has already been taken in, together. Built from the question
     * alone, a region that has fixed a number under one case would answer about another as though
     * nothing had been said.
     */
    private StructuralContext.Merge asking(Collection<NumericTerm> terms) {
        StructuralContext.Merge merged = accumulated();
        for (NumericTerm term : terms) {
            if (!(merged instanceof StructuralContext.Merge.Together it)) {
                return merged;
            }
            merged = it.context().merge(StructuralContext.of(term.subjectPath()));
        }
        return merged;
    }

    /**
     * The same, where the question is one no value of this input stands for.
     *
     * <p>Refused rather than answered wide. What was asked is where a quantity runs, and a quantity
     * over positions no one value has is one nothing runs between — an answer of "nothing bounds it"
     * would be read as the rules leaving it open. That two fixings cannot both hold is the other
     * question and is an emptiness ({@link #emptiness}), because there the caller said something
     * about the input rather than asking for a number.
     */
    private StructuralContext asked(Collection<NumericTerm> terms) {
        return switch (asking(terms)) {
            case StructuralContext.Merge.Together it -> it.context();
            case StructuralContext.Merge.Disagreeing it -> throw new IllegalArgumentException(
                    "`" + it.why().at().discriminated() + "` is asked to be "
                            + it.why().one().discriminated() + " and "
                            + it.why().other().discriminated() + ", which no value of this input"
                            + " is, so there is nothing here to answer about " + terms);
        };
    }

    /**
     * Where each position of this input sits, in the order they are declared.
     *
     * <p>What a proof of emptiness names a place out of, so the order is the model's and not a
     * traversal's: the parameters in the order the behavior takes them, and the positions of each in
     * the order its value declares them. Read off a map salted once per run, which position a
     * refusal names would move between runs of the same compiler.
     *
     * <p>The parameter and the declaration's own name joined, because the two spellings are the same
     * place or the report names one nobody wrote. A field of the record a clause was written on is
     * {@code x} there and {@code p.x} here, and this is where it becomes the second.
     *
     * <p>Everything here is somewhere in the input. The value a proof would call itself is the
     * behavior's whole input, which no reading names and no parameter is — so a parameter carrying
     * nothing of its own comes out as the parameter and not as the value the proof is about.
     */
    private java.util.SequencedMap<InputAtom, Emptiness.AtAField.Where>
            positions(StructuralContext under) {
        java.util.SequencedMap<InputAtom, Emptiness.AtAField.Where> made =
                new LinkedHashMap<>();
        conditioned(under).forEach((root, carried) -> carried.named().forEach(
                // Off the subject and not off the reading it arrived from. A field the cases of a
                // sum share is one place named by the sum's rules and by the case's, and the two
                // arrive here as one subject — so the place is the one that subject stands at,
                // which is what the subject itself says.
                // What a newtype wraps is at no name of its own, so the place is the value.
                (atom, path) -> made.put(atom, placeOf(atom, root, path))));
        return Collections.unmodifiableSequencedMap(made);
    }

    /**
     * Where a subject of this input sits, for a proof that names a place.
     *
     * <p>Asked of the subject, which is the one thing that knows. A subject the reading it came from
     * has a name for arrives here under a name of this input's, and that name carries the place — so
     * the place a proof names and the place a report renders are one spelling and cannot part.
     *
     * <p>A subject with a name and no place is this reading disagreeing with itself: what the
     * reading handed over as sitting somewhere would have arrived as something to be equal to and
     * nothing more, and every rule about it would have stopped meeting the rules the value above
     * wrote about the same place.
     */
    private static Emptiness.AtAField.Where placeOf(InputAtom atom, TermPath root, String path) {
        if (!(atom instanceof InputAtom.Named named)) {
            throw new IllegalStateException(
                    "`" + root.discriminated() + "` names a subject at `" + path + "` that arrived"
                            + " here with no place, so nothing can say where a proof about it is");
        }
        return new Emptiness.AtAField.Where.In(named.place());
    }

    /**
     * What this input calls the number at one of a value's coordinates.
     *
     * <p>The name and not a place, because a name is what the coordinate is about: what the cases
     * of a sum share is named at the sum and stands at a position under each of them, so a subject
     * turned into one of those positions would be one of however many the name reaches, chosen by
     * nothing.
     */
    private InputAtom called(TermPath root, NumberAt<RuleKey> at, StructuralContext under) {
        return atomAt(standingUnder(pathOf(root, at.position()), under), at.of());
    }

    /** The same, of a term this input holds. One number under one name whichever side it arrives
     *  from — the reading of a declaration, or a form a caller wrote. */
    private InputAtom.Named called(NumericTerm term, StructuralContext under) {
        UnderARoot at = rootOf(term.subjectPath());
        NumberAt<RuleKey> where = coordinateOf(at, term);
        return atomAt(standingUnder(pathOf(at.root(), where.position()), under), where.of());
    }

    /** Where a value's own rules put a coordinate, as a place of this input. */
    private static TermPath pathOf(TermPath root, RuleKey named) {
        TermPath at = root;
        for (String field : named.steps()) {
            at = at.then(field);
        }
        return at;
    }

    /**
     * The subject at a place: the nearest value whose rules can name it, and what those rules call
     * it.
     *
     * <p>The nearest, so that a place reached from two readings is one subject. A field the cases of
     * a sum share is named by the sum's rules and by the case's, and named by the sum it would be a
     * number standing under however many cases there are — chosen by nothing.
     */
    private InputAtom.Named atomAt(TermPath place,
                                   NumberAt.OfWhatNumber kind) {
        UnderARoot at = rootOf(place);
        return new InputAtom.Named(at.root().toString(), at.named(), kind);
    }

    /**
     * Where a place stands once every narrowing this context selects has been taken.
     *
     * <p><b>All of them at once and not one crossing at a time.</b> What the rules of a value above
     * say about a name its cases share is what they say about the number standing under the case,
     * and a question can select narrowings under several sums — so a clause relating two of those
     * names is about two numbers under two cases. Renamed a crossing at a time, either copy of the
     * clause still spells the other name the way the value above wrote it, and the two copies
     * relate nothing when they are met.
     *
     * <p>Nothing moves under a narrowing this context has not selected. A shared name whose case is
     * undecided is one number all the same, standing at the sum's own name — and the rules about it
     * stay one relation until a context says which case the value turned out to be.
     *
     * <p>Run to a fixed point rather than swept once, because a name two narrowings down is moved by
     * the outer one before the inner one can see it. Today the readings are held in the order the
     * walk opened them, outermost first, so one sweep settles every name — which is a fact about a
     * map's order and not about what a name means.
     */
    private TermPath standingUnder(TermPath place, StructuralContext under) {
        boolean moved = true;
        while (moved) {
            moved = false;
            for (OpenedRules opened : byRoot.values()) {
                if (!(opened.opening() instanceof RootOpening.Refined it)
                        || !under.holds(it)) {
                    continue;
                }
                TermPath deeper = it.crossing().standingUnderTheCase(place);
                if (deeper != null) {
                    place = deeper;
                    moved = true;
                }
            }
        }
        return place;
    }

    /**
     * The rules with what one term is on its own taken in.
     *
     * <p>Three things, and none of them is a clause: a value the caller fixed it at, what the
     * position measured at it was read to hold, and what the term guarantees of itself. All three
     * are true whether or not any clause ever named the coordinate, and they are put onto the rules
     * rather than met against the answer so that they are solved together with the relations.
     *
     * <p>Ends that are not numbers are left out, because what is being added to is the arithmetic. A
     * position ordered by its own values has ends that are values — a string stops at {@code "A"} —
     * and that end survives where a form is one term taken as itself ({@link #runsBetween}), which
     * is the only shape such a position is ever asked in.
     */
    private souther.compiler.numeric.NumericDomain<InputAtom> holding(
            souther.compiler.numeric.NumericDomain<InputAtom> rules, NumericTerm term,
            StructuralContext under) {
        NumericDomain.Bounds runs = whereOneTermRuns(term);
        if (runs == null || (asCut(runs.min()) == null && asCut(runs.max()) == null)) {
            return rules;
        }
        InputAtom atom = called(term, under);
        souther.compiler.numeric.Granularity spaced = spacingOf(rules, term, atom);
        if (spaced == null) {
            return rules;
        }
        return rules.assuming(atom, numbersOf(runs), Map.of(atom, spaced));
    }

    /** A range with only the ends the arithmetic has a number for. */
    private static NumericDomain.Bounds numbersOf(NumericDomain.Bounds runs) {
        return new NumericDomain.Bounds(
                asCut(runs.min()) == null ? null : runs.min(),
                asCut(runs.max()) == null ? null : runs.max());
    }

    /**
     * How the values of one term are spaced.
     *
     * <p>What the rules already record about it where they record anything, and what its type says
     * where they do not. Asked of the rules first because that is where the two could disagree, and
     * one number spaced two ways is the naming and the typing disagreeing rather than something to
     * pick the safer of.
     *
     * <p>Null where nothing says. A bound may not be taken in on a number whose spacing is guessed —
     * a strict bound is either wrongly sharpened on it or silently left blunt — so what is not
     * known is left out, and what the rules leave is then wider rather than wrong.
     */
    private souther.compiler.numeric.Granularity spacingOf(
            souther.compiler.numeric.NumericDomain<InputAtom> rules, NumericTerm term,
            InputAtom atom) {
        souther.compiler.numeric.Granularity had = rules.spacingOf(atom);
        if (had != null) {
            return had;
        }
        if (ruleReading == null) {
            return null;
        }
        // The order this reading measures the term on, which is the same answer every other reader
        // of it gets. Worked out here from the positions alone, a term whose subject the reading
        // stopped above — a field every case of a sum spreads is one — was spaced by nothing while
        // the order was there to be had, and a count under more steps than the enumeration goes
        // down would lose the floor every count has.
        souther.compiler.check.Carrier carrier = ordersOf(term).answered();
        return carrier == null ? null : carrier.spacing();
    }

    @Override
    public NumericDomain.Bounds runsBetween(LinearForm<NumericTerm> form) {
        if (form.coefs().isEmpty()) {
            return null;
        }
        form.coefs().keySet().forEach(this::held);
        // Projected out of the rules, which is one question with one answer. The rules of every
        // parameter are said together and what each number is on its own is said onto them, so what
        // a form runs between is read off that space rather than assembled out of per-parameter
        // answers — assembling is what a rule spanning two parameters cannot survive.
        //
        // Under the context this question is asked in, which is what says which of those rules are
        // about the row being asked about at all.
        return runsIn(asked(form.coefs().keySet()), form);
    }

    /**
     * The same, in a context a caller already holds.
     *
     * <p>For a reader that is walking contexts rather than answering a question in the one this
     * value accumulated — which is the fold that asks how many a container holds under a case, where
     * the context is the one the fold has reached and not the one anybody fixed.
     */
    private NumericDomain.Bounds runsIn(StructuralContext under,
                                        LinearForm<NumericTerm> form) {
        souther.compiler.numeric.NumericDomain<InputAtom> rules = constraints(under).numbers();
        for (NumericTerm term : form.coefs().keySet()) {
            rules = holding(rules, term, under);
        }
        NumericDomain.Bounds projected = rules.boundsOf(over(form, under));
        // One term taken as itself, which is the arithmetic being the identity rather than a second
        // answer to the same question. It is also the only shape a position the arithmetic cannot
        // count is ever asked in — a form adds its terms together and two strings have no sum — so
        // this is where a floor written as a value rather than as a number survives at all.
        NumericTerm only = onlyTermOf(form);
        return only == null ? projected : meeting(projected, whereOneTermRuns(only));
    }

    /**
     * A form of this input's terms, as one over the numbers the rules are about.
     *
     * <p><b>Added and not overwritten, because this is a fold and not a renaming.</b> A term carries
     * which measure was written and a number does not, so two terms can be one number — and where a
     * form weighs both of them, what that number is weighed by is the two coefficients together.
     * Written as a renaming, {@code List.length(p.xs) + Set.size(p.xs)} would come back weighing
     * that count once, which is a form the caller did not write and a range that is not the one they
     * asked about.
     *
     * <p>The other way round from {@link souther.compiler.numeric.NumericDomain#over}, and the two
     * are not the same act. Carrying a rule across may not put two positions under one name — that
     * would say they are one number, which nobody said. Reading a caller's form may, because the
     * caller wrote two spellings of a number this input has one of.
     */
    private LinearForm<InputAtom> over(
            LinearForm<NumericTerm> form, StructuralContext under) {
        Map<InputAtom, BigDecimal> coefs = new LinkedHashMap<>();
        form.coefs().forEach((term, coef) ->
                coefs.merge(called(term, under), coef, BigDecimal::add));
        return new LinearForm<>(form.constant(), coefs);
    }

    @Override
    public Quantities given(Map<NumericTerm, Count> more) {
        return fixing(more);
    }

    /**
     * The same, answered as this reading rather than as one of the faces it wears.
     *
     * <p>What refining hands back is the state, and {@link Quantities} and {@link SearchRegion} are
     * two ways of asking it. Typed by either of them, the other has to put back what it knows —
     * which is a cast, and a cast is a check the compiler is not doing. The two faces stay apart
     * because they answer different questions; what they refine is one thing and is typed as one.
     */
    ReadQuantities fixing(Map<NumericTerm, Count> more) {
        if (more.isEmpty()) {
            return this;
        }
        Map<NumericTerm, Fixed> both = new LinkedHashMap<>(fixed);
        for (Map.Entry<NumericTerm, Count> each : more.entrySet()) {
            NumericTerm term = held(each.getKey());
            both.merge(term, new Fixed(each.getValue(), each.getValue()),
                    (had, one) -> had.and(one.least()));
        }
        return new ReadQuantities(byRoot, roots, byPath, cases, typeAt, both, ruleReading,
                assumed);
    }

    /**
     * Why nothing is left, or empty where nothing proved it.
     *
     * <p><b>Worked out from what is fixed, and not from how it came to be fixed.</b> Two positions
     * fixed at values neither can take are two contradictions, and which of them a caller hears
     * about is not something the model says — kept as the first one a fixing happened to meet, the
     * answer would carry the order the questions were asked in. So nothing is remembered along the
     * way: the same accumulation answers the same thing, whichever way round it was reached and
     * whether it arrived in one call or four.
     *
     * <p>Looked for in one order, which is a settled order and not a preference. A position fixed at
     * two values contradicts without anything being read; a value the term itself cannot take
     * contradicts against what the term guarantees; two things fixed under cases no value is both
     * of contradict against where they were fixed and against nothing else; and what the
     * declarations refuse is theirs to refuse. Everything a caller said comes before everything the
     * declarations say, because what a caller said is what the caller can go and change. The terms
     * are taken in the order they are written down, so two contradictions of one kind are told
     * apart by where they sit rather than by when they were found.
     */
    @Override
    public Optional<EmptyInput> emptiness() {
        for (Map.Entry<NumericTerm, Fixed> each : inOrder()) {
            if (!each.getValue().isOne()) {
                return Optional.of(new EmptyInput.TwoValuesAtOnePosition(each.getKey(),
                        each.getValue().least(), each.getValue().most()));
            }
        }
        for (Map.Entry<NumericTerm, Fixed> each : inOrder()) {
            NumericDomain.Bounds own = each.getKey().intrinsicBounds();
            if (!own.admits(each.getValue().least())) {
                return Optional.of(new EmptyInput.OutsideWhereThePositionRuns(each.getKey(),
                        each.getValue().least()));
            }
        }
        // Two things fixed under cases no one value is both of. Said here rather than by the rules,
        // for the reason a position fixed at two values is: what contradicts is the pair of
        // assignments, and the declarations were never asked.
        if (accumulated() instanceof StructuralContext.Merge.Disagreeing it) {
            return Optional.of(new EmptyInput.TwoRefinementsAtOnePosition(
                    it.why().at(), it.why().one(), it.why().other()));
        }
        // And what the rules leave once they are all said together, which is the one thing that
        // answers it. Every parameter's reading is in here, renamed, so there is nothing a
        // per-parameter reading could add — and a contradiction between two parameters, or between a
        // declaration and something a caller took in, can be seen nowhere else.
        return switch (viability(asked(List.of()), null)) {
            case Viability.ProvedImpossible it ->
                    Optional.of(new EmptyInput.ProvedByTheRules(it.why()));
            // Neither of these is a proof. One says nothing showed the input empty and the other
            // says this reading did not look, and a caller that read either as "there is a value"
            // would be reading the absence of a proof as one.
            case Viability.MayStand _, Viability.NotRead _ -> Optional.empty();
        };
    }

    /**
     * Whether anything stands under {@code under}, and what showed it where nothing does.
     *
     * <p><b>The alternatives a value has are quantified over, not met.</b> What the rules of the
     * values a context names leave is one part of the answer; the other is that the context has not
     * said which case every sum turned out to be, and a sum has a value wherever any of its cases
     * does. So a case its own rules refuse takes nothing with it while a sibling stands, and the
     * input is empty only where every alternative of some sum is impossible.
     *
     * <p>Depth first, and it stops at the first alternative that may stand: what is being asked is
     * whether any assignment of cases leaves anything, so one that does is the whole answer. Each
     * sum is walked where it stands and its alternatives are asked about what is under them, so
     * what this walks is the choices one structure offers and never the product of two — see
     * {@link #inside}, which says what that gives up.
     *
     * <p><b>The structures a value has at once are a conjunction, and the cases of one of them are a
     * choice.</b> So what is folded here is {@link Viability#with} and what is folded across the
     * cases of a sum is {@link Viability#oneOf}, and the difference is which of three answers wins.
     * A structure this reading never finished with may not hide what the structure beside it
     * proved — read as a choice, whichever of the two the fields happen to declare first would
     * settle whether the input is refused at all.
     */
    private Viability viability(StructuralContext under, TermPath below) {
        Optional<Emptiness> here =
                constraints(under).holdsNothing(positions(under));
        if (here.isPresent()) {
            return new Viability.ProvedImpossible(here.get());
        }
        Viability standing = new Viability.MayStand();
        for (CasesRead sum : cases) {
            // A sum inside a case is a place to ask about only once the value is that case, and one
            // this context has already settled is not a choice any more.
            if (!under.covers(StructuralContext.of(sum.sum()))
                    || under.refinements().at(sum.sum()) != null
                    || !inside(sum.sum(), below)) {
                continue;
            }
            standing = standing.with(across(sum, under));
            // Everything else it may say is already said. Kept going only for the one answer that
            // is not yet in hand, which is a proof — what a caller is told is the first the model
            // writes down, and the rest of the walk cannot change it.
            if (standing instanceof Viability.ProvedImpossible) {
                return standing;
            }
        }
        for (OpenedRules opened : byRoot.values()) {
            if (!(opened.opening() instanceof RootOpening.Inside it)
                    || under.nonEmptySequences().contains(it.sequence())
                    || !under.covers(StructuralContext.of(it.sequence()))
                    || !inside(it.sequence(), below)) {
                continue;
            }
            standing = standing.with(holds(it.sequence(), under));
            if (standing instanceof Viability.ProvedImpossible) {
                return standing;
            }
        }
        return standing;
    }

    /**
     * Whether a structure is one this step of the fold answers for.
     *
     * <p><b>Each structure is folded where it stands and nowhere else.</b> A sum beside the one
     * being walked is not part of what its alternatives come to: it has the same cases whichever way
     * that one turned out, so answering for it again under each alternative would refuse the
     * alternative for what stands beside it — and the place a proof names would be whichever of the
     * two the fields happen to declare first.
     *
     * <p>What that gives up is a pair: two structures the declarations relate can be impossible
     * together while each of them stands alone, and folded apart that is a proof nobody makes. It is
     * the direction this may be wrong in — no proof rather than a proof of the wrong thing — and it
     * is what keeps a walk of the alternatives from being the product of every choice in the input.
     */
    private static boolean inside(TermPath structure, TermPath below) {
        return below == null || structure.isAtOrUnder(below);
    }

    /**
     * What a sequence and the values it holds come to together.
     *
     * <p><b>An element nothing can build refuses the sequence only where the sequence cannot be
     * empty.</b> A container that may hold none is a value whatever is true of what it would hold,
     * so what its element's rules refuse is a row nobody has to write rather than an input nobody
     * can. The two are one question asked in the order the model settles it: how many the rules
     * leave it, and then — only where they leave it no way to be empty — whether anything can stand
     * inside it.
     *
     * <p>Where nothing says how many it holds, nothing is proved. A reading that took an unmeasured
     * container for one that must hold something would refuse a model for a rule nobody wrote.
     */
    private Viability holds(TermPath sequence, StructuralContext under) {
        NumericTerm counted = howManyItHolds(sequence);
        if (counted == null) {
            return new Viability.MayStand();
        }
        NumericDomain.Bounds many = runsIn(under, LinearForm.atom(counted));
        if (many == null || CountDomain.leastFrom(many.min()) < 1) {
            return new Viability.MayStand();
        }
        Viability inside = viability(under.holding(sequence), sequence);
        return inside instanceof Viability.ProvedImpossible it
                ? new Viability.ProvedImpossible(new Emptiness.AtAField(
                        new Emptiness.AtAField.Where.In(sequence.toString()),
                        new Emptiness.NonEmptyCollectionWithNoElement(it.why())))
                : inside;
    }

    /**
     * What the cases of one sum come to together.
     *
     * <p>All of them, because that is what makes the proof a proof: picking one to speak for the
     * rest would answer a question about which case is at fault that nothing asked. And a case this
     * walk never entered stops the proof rather than joining it — nothing was shown about it, which
     * is not the same as its having been shown to hold nothing.
     */
    private Viability across(CasesRead sum, StructuralContext under) {
        List<Viability> alternatives = new ArrayList<>();
        for (Map.Entry<Refinement, CaseOutcome> each : sum.outcomes().entrySet()) {
            alternatives.add(switch (each.getValue()) {
                // Naming the case builds it, so there is a value here whatever the rules do to the
                // others.
                case CaseOutcome.StandsAlone _ -> new Viability.MayStand();
                // Impossible because the rules leave the case nothing, which is what the reading of
                // the position settled when it said the case was owed no row
                // ({@link Position#obligationCases}) — a refusal that holds whether or not every
                // rule was read. Not because the walk did not go down it: how far a walk goes is
                // the other arm, and reading this one as that would be a proof made out of where
                // this compiler stopped.
                case CaseOutcome.RefusedByTheRules _ ->
                        new Viability.ProvedImpossible(new Emptiness.ConflictingRules());
                case CaseOutcome.NotWalked _ -> new Viability.NotRead();
                case CaseOutcome.Opened _ -> viability(under.and(sum.sum(), each.getKey()),
                        sum.sum().refine(each.getKey()));
            });
        }
        // Where every case is refused, the sum is refused by all of them together and the place is
        // where it stands. Which of the three answers wins is the choice's to say and not this
        // caller's.
        return Viability.oneOf(alternatives, refused -> new Emptiness.AtAField(
                new Emptiness.AtAField.Where.In(sum.sum().toString()),
                new Emptiness.AcrossEveryCase(refused)));
    }

    /**
     * What is fixed, in the order the terms are written down.
     *
     * <p>Any order settled by the terms themselves would do; what may not decide it is the order the
     * fixings arrived in, which is the caller's business and not the model's.
     */
    private List<Map.Entry<NumericTerm, Fixed>> inOrder() {
        List<Map.Entry<NumericTerm, Fixed>> out = new ArrayList<>(fixed.entrySet());
        out.sort(java.util.Comparator.comparing(each -> each.getKey().toString()));
        return out;
    }

    /**
     * The term, where what it sits under is something this behavior takes.
     *
     * <p><b>Owned is not the same as known about.</b> The reading holds the positions the
     * enumeration found and the ones the measurement named, and nothing stops a rule from naming a
     * path outside both. A term at such a path is this input's and
     * is answered for — with whatever the term guarantees of itself and nothing the declarations
     * relate it to, because the reading has no position there for a relation to be about. Refused
     * instead, an ordinary rule naming a field of a field of a field stopped a measurement rather
     * than being measured.
     *
     * <p>What is refused is a term under something this behavior does not take, which no reading of
     * this input could ever have an answer for. Answered as an emptiness it would be a bug wearing
     * the words of a contradiction in the model; answered as unbounded it would be one wearing the
     * words of a model that says nothing.
     *
     * <p>Whether the path names a field the type actually has is settled where the term is made.
     * What arrives here is a term some reading of an expression produced, and this neither checks
     * nor could check it: a path is a location and the declarations are what say what is at one.
     */
    private NumericTerm held(NumericTerm term) {
        if (rootOf(term.subjectPath()) == null) {
            throw new IllegalArgumentException(
                    "`" + term.subjectPath().discriminated() + "` is under no value whose rules this"
                            + " reading holds and can name it by, so there is nothing here to answer"
                            + " about " + term);
        }
        return term;
    }

    /** What is fixed under one value, named the way that value's own rules name it. */
    private Map<NumberAt<RuleKey>, Count> under(TermPath root) {
        Map<NumberAt<RuleKey>, Count> out = new LinkedHashMap<>();
        fixed.forEach((term, fixedAt) -> {
            UnderARoot at = rootOf(term.subjectPath());
            // Which number of the place was settled, and not only which place. A count taken of one
            // is a coordinate of its own, and a fixing that named only the value left a rule over
            // two counts unconditioned while the same rule was read whole when the counts were
            // asked about.
            // Only where one value was fixed there. A place fixed at two settles nothing the
            // declarations could be told, and what it contradicts is said here rather than by them.
            if (at != null && root.equals(at.root()) && fixedAt.isOne()) {
                out.put(coordinateOf(at, term), fixedAt.least());
            }
        });
        return out;
    }

    /**
     * The coordinate of one term, in the words the rules of the value it is a position of are read
     * in.
     *
     * <p>Total, because the name arrives with the root that answers by it ({@link #rootOf}). Which
     * steps of a place are names a rule writes is settled once, where the root is chosen, so
     * nothing here decides it again and there is no place a term could arrive at that this cannot
     * name.
     *
     * <p>Named by the operation and not by "something was taken here". A count of a string and the
     * magnitude of a number at the same place are two quantities, and a flag brought them to one
     * name — so a guard bounding one would have been read against the clauses written about the
     * other (#1027).
     */
    private static NumberAt<RuleKey> coordinateOf(UnderARoot at, NumericTerm term) {
        return switch (term) {
            case NumericTerm.ValueOf _ -> NumberAt.valueOf(at.named());
            case NumericTerm.TakenOf taken ->
                    NumberAt.takenOf(at.named(), taken.operation());
            case NumericTerm.TakenOver over ->
                    NumberAt.takenOf(at.named(), over.operation());
        };
    }

    /** The term a form is, where it is one term taken as itself, or null where it is arithmetic. */
    private static NumericTerm onlyTermOf(LinearForm<NumericTerm> form) {
        if (form.coefs().size() != 1 || form.constant().signum() != 0) {
            return null;
        }
        Map.Entry<NumericTerm, BigDecimal> one = form.coefs().entrySet().iterator().next();
        return one.getValue().compareTo(BigDecimal.ONE) == 0 ? one.getKey() : null;
    }

    /**
     * Where one term runs, as bounds rather than as something to add up.
     *
     * <p>Three things and they are not one thing. A value the caller fixed it at is where it stands
     * whether or not any clause ever named that coordinate; where the values it is answered from
     * leave it is where its values stop, on whatever order it is measured ({@link
     * #whereItsValuesAre}); and what the term guarantees of itself is true of every term of its
     * kind, which is how a count is never negative without a clause saying so.
     *
     * <p>What this does not do is turn them into numbers: a position ordered by its own values has
     * ends that are values — a string stops at {@code "A"} — and the arithmetic that adds terms
     * together has no word for one.
     */
    private NumericDomain.Bounds whereOneTermRuns(NumericTerm term) {
        NumericDomain.Bounds runs = meeting(whereItsValuesAre(term), term.intrinsicBounds());
        Fixed fixedAt = fixed.get(term);
        // Where two values were fixed there, between them: the rules leave nothing at all, which
        // {@link #emptiness} says, and a range that crossed itself is not something to hand a
        // caller that has not asked.
        return fixedAt == null ? runs
                : meeting(runs, new NumericDomain.Bounds(Endpoint.inclusive(fixedAt.least()),
                        Endpoint.inclusive(fixedAt.most())));
    }

    /** The tighter end on each side, where an absent bound is no bound and never the tighter. */
    private static NumericDomain.Bounds meeting(NumericDomain.Bounds one,
                                                NumericDomain.Bounds other) {
        if (one == null) {
            return other;
        }
        if (other == null) {
            return one;
        }
        return new NumericDomain.Bounds(Endpoint.lower(one.min(), other.min()),
                Endpoint.upper(one.max(), other.max()));
    }

    /**
     * What the rules were read to leave this term, or null where no position has such a number.
     *
     * <p>Asked of the number and answered by the position that has it. A count taken of a place and
     * what stands at that place are different quantities, and the position holds an answer about
     * each — so a body measuring the length of a string is told where the length stops rather than
     * where the string does.
     */
    private NumericDomain.Bounds ownOf(NumericTerm.FromOnePosition term) {
        Position at = byPath.get(term.position());
        PositionBounds bounds = at == null ? null : at.boundsFor(term);
        return bounds == null ? null : bounds.admissible();
    }

    /**
     * Where the values this term is answered from leave it, or null where nothing here bounds them.
     *
     * <p>Exhaustive over the terms there are, with no {@code default}. Where a number's values come
     * from is what says who bounds them, and the two answers are not one reader's: a number one
     * position answers is bounded by what that position was read to hold, and a number taken over a
     * run has no position and is bounded by what the values it walks guarantee. A kind of term added
     * stops here until somebody says which — answered by falling through instead, it would come back
     * unbounded, and a border on it would be owed a row at a value the model admits nothing at.
     */
    private NumericDomain.Bounds whereItsValuesAre(NumericTerm term) {
        return switch (term) {
            case NumericTerm.FromOnePosition one -> ownOf(one);
            case NumericTerm.TakenOver over ->
                    RunReach.of(over, ordersOf(over), typeAt, ruleReading);
        };
    }

    /** An end as a number the arithmetic can cut at, or null where it stops at a value there is no
     *  number for — a text position has a floor and nothing for the arithmetic to relate. */
    private static RationalCut asCut(Endpoint end) {
        return end == null || !(end.at() instanceof Count at) ? null
                : new RationalCut(Rational.of(at.at()), end.inclusive());
    }

}
