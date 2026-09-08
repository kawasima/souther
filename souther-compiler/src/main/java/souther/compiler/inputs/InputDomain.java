package souther.compiler.inputs;

import souther.compiler.ast.Hir;
import souther.compiler.check.NumberAt;
import souther.compiler.check.RuleReadingSource;
import souther.compiler.check.SpecImplementation;
import souther.compiler.check.DeclarationReadings;
import souther.compiler.check.Carrier;
import souther.compiler.check.DeclaredBounds;
import souther.compiler.check.DeclaredCoordinates;
import souther.compiler.check.RuleCitation;
import souther.compiler.check.RuleKey;
import souther.compiler.check.FieldDomains;
import souther.compiler.check.NarrowedBounds;
import souther.compiler.check.NumericMeasures;
import souther.compiler.check.ReadableFields;
import souther.compiler.check.ReadingPolicy;
import souther.compiler.check.Shape;
import souther.compiler.check.Sig;
import souther.compiler.check.TypeView;
import souther.compiler.numeric.NumericDomain;
import souther.compiler.types.BindingId;
import souther.compiler.types.Type;
import souther.compiler.types.TypeSymbol;
import souther.compiler.types.ValueName;
import souther.compiler.values.AdmissibleSet;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * What can arrive at each position of one behavior's input, read once.
 *
 * <p>The single answer four measures are projections of. What cases a signature is owed, what
 * classes a position divides into, what arms a row is owed and what a body's {@code unreachable}
 * claims are held against all come from here — derived separately they disagreed, and the disagreement
 * had a direction: the reading that knew about the rules refused a case, and the reading that knew
 * only the type asked for a row at it.
 *
 * <p><b>The walk is here and not in a reader.</b> A reader with its own walk has its own set of
 * positions, and a position one of them never visited is one it can say nothing about while another
 * answers for it. So every position a behavior's input has is read, whatever any reader goes on to
 * do with it — including the ones a reader gives up in favour of what is under them, and the ones
 * dropped past a budget.
 *
 * <p>Which is a claim about the reading and not about the steps it is made of. What is readable off
 * a value is one fact and is {@link ReadableFields}'s; how far to follow what a position has under
 * it, and what is read where it lands, are this reading's. A reader wanting a step alone takes it
 * from the owner of the question it is a step of, and takes none of the meaning here with it.
 *
 * <p><b>A position may exist only under a narrowing of another.</b> What a case of a sum declares is
 * declared whether or not anything constructs one, so those fields are positions of the input and are
 * read here — at a path carrying the narrowing they stand under ({@link Refinement}), which is what
 * says a row has to be that case for them to be reached at all. Which branches are walked is settled
 * by what the position came back owing rather than by what its type has: a case the rules refuse has
 * no positions to cover, exactly as a value whose rules contradict has none.
 *
 * <p><b>How far down it goes is settled by the declarations and not by a count.</b> Every path that
 * opens no declaration twice is followed to its end, however many steps that is: how deeply an
 * author nests a record decides nothing about whether the rules written at the bottom of it are
 * measured. What ends a path is returning to a declaration already open on it ({@link
 * ExpansionTrace}) — the occurrence is read, and only unfolding it again is refused. Held to a
 * number of steps instead, a rule one step past that number drew no line, and a threshold an author
 * had written once was never asked for.
 *
 * <p><b>And a reading is closed over the finite paths the behavior's measurement names.</b> Two
 * roads reach a position and both are taken here. Enumeration asks what positions a type can have
 * and answers without reference to any body, which is why it has to stop somewhere; a demand
 * ({@link InputDemand}) names one path, adds nothing to the enumeration, and ends because the path
 * does. Both are read by the same walk and the same reading of the declarations, so what comes back
 * is one account of one input.
 *
 * <p>Once built, a reading never acquires another position, rule root or constraint. Everything
 * derived from it — what a report counts, what the rules leave a quantity, what a row is asked
 * for — is taken from it once and cannot be told about a position afterwards, so a reading that
 * grew one when somebody looked it up would answer a question by what had been asked before it. A
 * path nobody demanded is a path this has no position for, whoever asks and whenever.
 *
 * <p><b>Where a value is built is still not one of these positions.</b> The generator goes on until
 * there is a value to build, which is further down than a report is about. Those paths are written the
 * same way and are about something else, so they are never looked up here.
 *
 * <p>Nothing a body writes reaches this. A {@code guard}'s line, a {@code match} arm and an
 * {@code unreachable} are statements about the same positions and are read against this rather than
 * into it — which is what keeps a body from moving the denominator it is measured by.
 */
public final class InputDomain {

    /**
     * The declaration a position's rules are read of, and where that value stands.
     *
     * <p>One reading per rule-owning value and not one per record met on the way down: a clause on
     * the outer record relates positions at any depth it can name, and rebuilding the reading at
     * each record is how a clause stopped reaching the field it was about.
     *
     * <p><b>What starts a new one is a descent crossing a boundary of rule ownership</b>, which is
     * wherever the value below is one a declaration writes its own clauses about. Narrowing into a
     * case is one — what a {@code GlobalQuery} says about its {@code tag} is written in
     * {@code GlobalQuery} and cannot be written in the sum — and so is entering what a sequence
     * holds, since what {@code Tag} says about itself is written in {@code Tag} however the position
     * is reached. Both go through {@link #takeTheRulesOver} and nothing else does, which is what a
     * shape this compiler learns to walk later has to do to have its rules read at all (#1072).
     *
     * <p>Kept as the declarations rather than as a reading of them, for the reason the parameters
     * are: what is answered with is compared as a value by whatever decides that a compile changed
     * nothing.
     *
     * <p><b>And with the condition it was opened under.</b> A reading of one of these holds of the
     * rows the opening admits and not of every row, so a reader that met them all together would
     * have every case of every sum holding at once ({@link RootOpening}). Recorded here because it
     * is known here and nowhere else: the descent that opens the reading is what says whether it
     * crossed a narrowing or a container, and a reader working it back out of the path would be
     * answering from the steps rather than from the descent that took them.
     */
    public record RuleRoot(TermPath at, Type type, RootOpening opening) {}

    private final List<Position> positions;
    private final Map<TermPath, Position> byPath;
    private final Map<BindingId, String> read;
    /** What the behavior takes, kept as it was written. Values and nothing else: what is answered
     * with is compared as a value by whatever decides an edit changed nothing, and a reading holds a
     * way of reading the declarations rather than a statement about them. */
    private final List<Parameter> parameters;
    /** Every declaration whose rules reach a position of this input, and where it stands. One per
     * parameter, and one more wherever a descent crossed a boundary of rule ownership — see
     * {@link RuleRoot}. */
    private final List<RuleRoot> roots;
    private final ReadingPolicy policy;
    /** Where a name written at one position stands, wherever that is not the position of the same
     * name one step down. Made by the walk that made the positions, so that what a name reaches and
     * what the reading produced are one answer. */
    private final NameReach reach;
    /** Everything the rules of the values this reading opened placed, in the words each of them was
     * written in. What became of each is {@link #placements()}. */
    private final List<PlacementSeed> placed;
    /**
     * The clauses of those declarations that placed no end, once per conjunct.
     *
     * <p>Held at this level because a rule relating two positions is about the input and not about
     * either of them: hung on a position it would be one of that position's rules, which is the
     * reading a relation may not be given.
     *
     * <p>No part of what makes two readings one. These are the clauses the declarations wrote, so
     * a reading with the same parameters under the same policy has the same ones — a reading that
     * agreed about the parameters and disagreed here would be disagreeing with itself.
     */
    private final List<ClauseWithoutAnEnd> clauses;
    /**
     * Every sum this reading met and what became of each of its cases.
     *
     * <p>What says whether a sum has a value at all, which is a question about the whole list of
     * them rather than about any one case.
     *
     * <p>No part of what makes two readings one, for the reason {@link #clauses} is not: the same
     * parameters walked under the same policy meet the same cases, so a reading that agreed about
     * the positions and disagreed here would be disagreeing with itself.
     */
    private final List<CasesRead> cases;

    private InputDomain(List<Position> positions, Map<BindingId, String> read,
                        List<Parameter> parameters, List<RuleRoot> roots, ReadingPolicy policy,
                        NameReach reach, List<PlacementSeed> placed,
                        List<ClauseWithoutAnEnd> clauses, List<CasesRead> cases) {
        this.cases = List.copyOf(cases);
        this.placed = List.copyOf(placed);
        this.clauses = List.copyOf(clauses);
        this.positions = List.copyOf(positions);
        this.read = Map.copyOf(read);
        this.parameters = List.copyOf(parameters);
        this.roots = List.copyOf(roots);
        // Every one of these is what a walk that ran came back with. A reading is made by walking
        // an input and no other way, so there is no state of this in which one of them is missing —
        // and a reader asking how the names in it are read gets the policy it was read under
        // whatever the walk found.
        this.policy = Objects.requireNonNull(policy, "a reading is made under a policy");
        this.reach = Objects.requireNonNull(reach, "a reading says what its names reach");
        Map<TermPath, Position> at = new LinkedHashMap<>();
        // The first reading of a path stands. A path is where a rule and a row meet, so two
        // readings under one path would be the position answering differently depending on which
        // reader looked it up.
        positions.forEach(each -> at.putIfAbsent(each.path(), each));
        this.byPath = Map.copyOf(at);
    }

    /**
     * Two of these are one where they read one input the same way.
     *
     * <p>The question it answers is whether a reading taken again came to what the last one came
     * to. Every measure that needs a denominator reads this, and what stops the work an edit costs
     * is the answer holding it comparing equal to the one it replaces — so a reading that came back
     * saying only which run made it would put every measure of every behavior through again.
     *
     * <p>What the walk was given and what it produced, and not {@link #byPath}, which is the
     * positions under the paths they were read at. A reading that reached the same positions and
     * disagreed here would be this class disagreeing with itself.
     */
    @Override
    public boolean equals(Object other) {
        return other instanceof InputDomain that
                && positions.equals(that.positions)
                && read.equals(that.read)
                && parameters.equals(that.parameters)
                && roots.equals(that.roots)
                && java.util.Objects.equals(policy, that.policy)
                && reach.equals(that.reach)
                && placed.equals(that.placed);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(positions, read, parameters, roots, policy, reach, placed);
    }

    /**
     * One thing a behavior is applied to: what it is called, what it holds, and which binding a body
     * reads it as.
     *
     * <p>Taken together because they are one fact — three lists a caller can get out of step is how
     * a position comes to be named after one parameter and read off another.
     *
     * @param name    what a report calls the position, which is the name the <em>declaration</em>
     *                wrote: a behavior states what it takes, and an implementation may bind the same
     *                thing under another spelling
     * @param binding what a body's reads of it carry, or null where no implementation binds it. What
     *                a binding is cannot be worked out from how it was spelled ({@link BindingId}),
     *                which is why the two are both here and neither stands in for the other
     */
    public record Parameter(String name, BindingId binding, Type type) {}

    /** Every position of an input, in the order the parameters are declared and descended into. */
    public static InputDomain of(List<Parameter> parameters, RuleReadingSource source,
                                 ReadingPolicy policy) {
        return of(parameters, source, policy, InputDemand.NONE);
    }

    /**
     * The same, closed over the finite paths a behavior's measurement names as well.
     *
     * <p><b>Both ways to a position, taken here and nowhere afterwards.</b> The enumeration finds
     * what positions a type can have and stops where a path returns to a declaration already open on
     * it; a demand names one path, which ends because the path does. Both are read by the same walk
     * and the same declaration reading, so what comes back is one reading with one account of what
     * it holds — a position, its rule root, and the constraints its declarations put on it.
     *
     * <p><b>And it never grows after this.</b> A reading that resolved a path when somebody looked
     * one up would answer a question differently depending on what had been asked before it, and
     * everything derived from it — what a report counts, what a quantity is left, what a row is
     * asked for — is taken from it once. So a path nobody demanded is a path this has no position
     * for, whoever asks and whenever.
     */
    public static InputDomain of(List<Parameter> parameters, RuleReadingSource source,
                                 ReadingPolicy policy, InputDemand demand) {
        return of(parameters, source, policy, demand, DeclarationReadings.NONE);
    }

    /**
     * The same, asking {@code machines} for what somebody has already made of each declaration's
     * string rules before building any of it.
     *
     * <p>A capability and not a part of the reading: handed to every reading this walk opens,
     * and kept by nothing the walk answers with.
     */
    public static InputDomain of(List<Parameter> parameters, RuleReadingSource source,
                                 ReadingPolicy policy, InputDemand demand,
                                 DeclarationReadings machines) {
        List<Position> found = new ArrayList<>();
        List<RuleRoot> roots = new ArrayList<>();
        Map<BindingId, String> read = new LinkedHashMap<>();
        // Where a reading ended with the rules under a position still to be read, and what took
        // them over. Kept across the whole walk because the two halves happen at different steps of
        // it: the reading says it stopped as the position is read, and the descent says who took
        // the rules as it opens the reading that did.
        RuleHandoffs handoffs = new RuleHandoffs();
        // Written as the positions are made, so that a name reaches a position exactly where this
        // walk produced one.
        NameReach.Observed observed = new NameReach.Observed();
        // Everything the rules of every value this reading opened placed, gathered as each reading
        // is opened. What became of each of them is asked once the positions are all in hand.
        Gathered account = new Gathered();
        for (Parameter parameter : parameters) {
            if (parameter.binding() != null) {
                read.putIfAbsent(parameter.binding(), parameter.name());
            }
            TermPath at = TermPath.of(parameter.name());
            roots.add(new RuleRoot(at, parameter.type(), new RootOpening.Taken()));
            PlacedRules rules = PlacedRules.of(at, parameter.type(), source, policy, machines);
            account.from(rules);
            // One walk, carrying the paths the measurement named under this parameter. Walked once
            // per demand instead, two paths sharing a prefix would open that prefix's declaration
            // twice, place its rules twice, and record a second answer where the ledger of what was
            // handed on keeps one — so a reading assembled from replays would depend on how many
            // demands ran through it.
            walk(at, parameter.type(), ExpansionTrace.NONE, source, policy, rules, found, roots,
                    java.util.Set.of(), handoffs, observed, account,
                    Reach.enumerating(demand.under(at)));
        }
        // And only now, because a handoff is discharged by a descent that happens after the
        // position above it has been read. Held the other way round, every position that hands its
        // rules on would have to be read after everything under it, and the order positions are
        // reported in is the order they are declared and descended into.
        List<Position> settled = found.stream()
                .map(each -> shortOfHandedOnRules(each, handoffs.unresolvedAt(each.path())))
                .toList();
        // Whatever the walk found, including nothing. A behavior declaring no parameters has an
        // input with no positions in it, and that is a reading that was made rather than one that
        // was not: it holds the parameters it was given, the policy it was read under and the names
        // it reached. Answered with a value standing for no reading at all, an input nobody could
        // read would be the same value as an input there was nothing to read.
        return new InputDomain(settled, read, parameters, roots, policy, observed.reach(),
                account.placed(), account.clauses(), observed.cases());
    }

    /**
     * What every declaration this walk opened put forward, kept as the walk goes.
     *
     * <p>One value because they are filled together, at the one step that opens a declaration's
     * rules. Threaded as two lists, a reading that opened a declaration would have had two places to
     * remember to write to, and a walk added later writes to whichever its author noticed.
     */
    private static final class Gathered {

        private final List<PlacementSeed> placed = new ArrayList<>();
        private final List<ClauseWithoutAnEnd> clauses = new ArrayList<>();

        /** Everything {@code rules} put forward, ends and clauses alike. */
        void from(PlacedRules rules) {
            placed.addAll(rules.placed());
            clauses.addAll(rules.clausesWithoutAnEnd());
        }

        List<PlacementSeed> placed() {
            return List.copyOf(placed);
        }

        List<ClauseWithoutAnEnd> clauses() {
            return List.copyOf(clauses);
        }
    }

    /**
     * The same, of a behavior and the implementation that binds its parameters.
     *
     * <p>The one place the three are put side by side: the declaration says what the parameters are
     * called, the signature says what they hold, and the implementation says which binding a body's
     * reads of one carry. Paired here rather than by every caller that has some of them.
     *
     * @param arriving which binder each declared input arrives in, or empty where nothing
     *                 implements this behavior — an injected behavior has positions and no body to
     *                 read them in
     */
    public static InputDomain of(Hir.SpecBehavior behavior,
                                 List<SpecImplementation.ParameterBinding.AnInput> arriving, Sig sig,
                                 RuleReadingSource source, ReadingPolicy policy) {
        return of(behavior, arriving, sig, source, policy, InputDemand.NONE);
    }

    /** The same, closed over the finite paths this behavior's measurement names as well. */
    public static InputDomain of(Hir.SpecBehavior behavior,
                                 List<SpecImplementation.ParameterBinding.AnInput> arriving, Sig sig,
                                 RuleReadingSource source, ReadingPolicy policy, InputDemand demand) {
        return of(behavior, arriving, sig, source, policy, demand, DeclarationReadings.NONE);
    }

    /**
     * The same, asking {@code machines} first.
     *
     * <p>{@code arriving} says which binder each declared input arrives in, where something
     * implements the behavior. It is asked of {@link SpecImplementation} and not measured off the
     * front of the implementation's parameter list: a behavior takes the behaviors it depends on
     * beside its inputs, so the two lists are not the same length and where one stops is that
     * reading's to say. Empty where nothing implements the behavior, which has the positions all the
     * same and nothing to read them through.
     */
    public static InputDomain of(Hir.SpecBehavior behavior,
                                 List<SpecImplementation.ParameterBinding.AnInput> arriving, Sig sig,
                                 RuleReadingSource source, ReadingPolicy policy, InputDemand demand,
                                 DeclarationReadings machines) {
        Map<Integer, BindingId> bindings = new LinkedHashMap<>();
        for (SpecImplementation.ParameterBinding.AnInput input : arriving) {
            bindings.put(input.at(), input.written().binder().binding());
        }
        List<Parameter> parameters = new ArrayList<>();
        for (int i = 0; i < sig.inputTypes().size() && i < behavior.params().size(); i++) {
            parameters.add(new Parameter(behavior.params().get(i).name(), bindings.get(i),
                    sig.inputTypes().get(i)));
        }
        return of(parameters, source, policy, demand, machines);
    }

    /**
     * The same, of an input nothing reads a body against.
     *
     * <p>The positions are the same either way — what a behavior takes is what it declares — and
     * what is absent is the means to tell one of its parameters from a name a body binds under the
     * same spelling. So this is the reading for a caller with no body in hand, and a caller with one
     * that used it would find every claim and every comparison naming nothing.
     */
    public static InputDomain of(Hir.SpecBehavior behavior, Sig sig, RuleReadingSource source,
                                 ReadingPolicy policy) {
        return of(behavior, List.of(), sig, source, policy);
    }

    /** The positions, in the order they were read. */
    public List<Position> positions() {
        return positions;
    }

    /**
     * What the behavior takes, in the order it declares them.
     *
     * <p>What this reading was made from, so that a reader needing the parameters takes the ones it
     * was read at rather than assembling its own from a signature — two lists of what one behavior
     * takes are two chances to disagree about which position a row's value goes to.
     */
    public List<Parameter> parameters() {
        return parameters;
    }

    /** How the names in it are read, which is the policy this reading was made under. */
    public ReadingPolicy policy() {
        return policy;
    }

    /**
     * The position at {@code path}, or null where this reading has none there.
     *
     * <p>Null for two unlike paths: one below where this reading stops, and one that is not a
     * position of this behavior at all. Neither is a path a value is built at — those are the
     * generator's and are not looked up here, because a construction recipe puts positions under a
     * sum where the declaration has none and no reading of the declaration would ever hold them.
     */
    public Position at(TermPath path) {
        return byPath.get(path);
    }

    /** Where a name written at one position stands, as this walk observed it. */
    public NameReach reach() {
        return reach;
    }

    /**
     * The address a rule naming {@code path} would write it at, or null where this reading has no
     * value whose rules can name it.
     *
     * <p>Asked here and not worked out from how the path is spelled. Which value's rules reach which
     * positions is what {@link RuleRoot} settles, and a caller building one from the head of a path
     * would have a second answer to it — right for as long as every such path starts at a parameter,
     * and quietly wrong the first time one does not.
     *
     * <p>The value the path is under and not the innermost one it passes through. A rule written in
     * a behavior names a position from the parameter, so the parameter is what it is read of; a rule
     * of a case is written in the case and reaches this by naming nothing above it.
     *
     * <p><b>The name comes back with the value, because it is why that value was chosen.</b> A root
     * is one of these only where its rules can name the place, so the name is already worked out
     * when the answer is made. Handed back as the root alone, a caller writes the address itself
     * and asks the same question again — and gets a "no name" answer for a root chosen because it
     * had one.
     */
    public RuleAddress rootNaming(TermPath path) {
        for (RuleRoot root : roots) {
            if (root.at().equals(TermPath.of(path.head()))) {
                RuleAddress address = RuleAddress.of(root.at(), path);
                if (address != null) {
                    return address;
                }
            }
        }
        return null;
    }

    /**
     * Everything the rules of this input's values placed, and what became of each of them.
     *
     * <p>The whole of what a build has to account for on this side. Every rule that placed anything
     * is here, and every one of them ends somewhere said out loud — filed at a position, refused by
     * the reading, or left with nowhere to go and a reason for it. Counted from the filings alone, a
     * rule this compiler had nowhere to put would be a rule nobody wrote.
     */
    public List<PlacementFiling> placements() {
        List<PlacementFiling> out = new ArrayList<>();
        for (PlacementSeed each : placed) {
            out.add(file(each));
        }
        return List.copyOf(out);
    }

    /**
     * The clauses of this input's declarations that placed no end, and where each is written about.
     *
     * <p>Handed over rather than read here. What a clause states about the values of this input is
     * settled in the vocabulary a line is drawn in, which is the measure's and not this reading's —
     * so this says which clauses there are and where they are read against, and says nothing about
     * what any of them comes to.
     *
     * <p>Every one of them, whatever this reading made of it. A clause set aside here is one the
     * reading that draws lines may still read: the two name their atoms differently, and a rule over
     * a number this reading has no atom for is a rule that one names two positions in.
     */
    public List<ClauseWithoutAnEnd> clausesWithoutAnEnd() {
        return clauses;
    }

    /**
     * Every position the dotted name {@code named}, written in the rules of the value at
     * {@code root}, stands at.
     *
     * <p>One name and any number of positions. A name is written once and a value is of one case, so
     * a name naming a field the cases of a sum share stands under each of them — and where two such
     * sums are on the way, at each pairing of their cases. Which is why this is taken a step at a
     * time against what the walk observed rather than by rewriting the name and reading the types
     * again: crossing at the first sum settles which positions the second step is asked of.
     *
     * <p>Empty where nothing this walk produced answers to the name. That is the whole of what is
     * said: a case the reading left, a field below where it stops and a name of nothing at all are
     * not told apart here, and a caller that has to tell them apart asks {@link #reach} what became
     * of each case.
     */
    public List<TermPath> positionsNamed(TermPath root, RuleKey named) {
        return follow(root, named).reached();
    }

    /**
     * What becomes of {@code seed} at every place the name it was written at reaches.
     *
     * <p>The one crossing from what a rule wrote to where a row goes. Everything a rule places comes
     * through here — an end a clause put on a number, the values a clause admits, a line a body drew
     * — because what differs between them is what is said and never where, and a second reading of
     * where would be a second answer to which rules reach which positions.
     *
     * <p>Never nothing. A name that reaches no position comes back saying so, with what the reading
     * was left with at the position it stopped at, so that a reader further on never has an absence
     * to make a cause out of.
     */
    public PlacementFiling file(PlacementSeed seed) {
        Followed followed = follow(seed.address().root(), seed.address().key());
        // A rule wrote this name, and a rule names a location only where the language reads one —
        // so a place this reading has no such name under is this reading disagreeing with the
        // language. Not a limitation to tell an author about, and not something to hand on as one.
        if (!followed.unnamed().isEmpty()) {
            throw new IllegalStateException(
                    "`" + seed.by() + "` names " + seed.address() + ", and this reading puts no "
                            + "such name where it looked: " + followed.unnamed());
        }
        List<PlacementOutcome> outcomes = new ArrayList<>();
        followed.reached().forEach(each -> outcomes.add(
                new PlacementOutcome.Filed(new PositionId(each))));
        outcomes.addAll(followed.otherwise());
        return new PlacementFiling(FOLLOWED, seed, outcomes);
    }

    /**
     * That a name was followed, which is the only way a filing is made.
     *
     * <p>Its constructor is this class's own, so nothing else can say a name was followed — which is
     * what keeps a filing from being a list of outcomes a caller assembled. A caller that could
     * write one could answer for one case of three and leave the other two unsaid, and being
     * non-empty would not notice.
     */
    static final class Following {
        private Following() {}
    }

    private static final Following FOLLOWED = new Following();

    /**
     * Where following a name got to, what became of it everywhere it did not, and the places it
     * reached that this reading has no such name under.
     *
     * <p>The third is said and not judged. A name nobody wrote that names nothing is a question with
     * the answer "nowhere"; the same name written by a rule is this reading disagreeing with the
     * language about what may be written, since a rule names a location only where the language
     * reads one. Which of the two it is turns on who asked, so the walk says what it saw and the
     * caller that knows decides.
     */
    private record Followed(List<TermPath> reached, List<PlacementOutcome> otherwise,
                            List<String> unnamed) {}

    /**
     * Follow a name from the value it was written in, a step at a time.
     *
     * <p>A step at a time and not by rewriting the name: crossing at the first sum settles which
     * positions the next step is asked of, so a name naming a field two shared sums down stands at
     * every pairing of their cases. Composed from the whole name at once, the pairing would be
     * something this had to work out rather than something it walks into.
     */
    private Followed follow(TermPath root, RuleKey named) {
        List<PlacementOutcome> otherwise = new ArrayList<>();
        List<String> unnamed = new ArrayList<>();
        List<TermPath> frontier = List.of(root);
        // A name of no steps is the value itself, which is where the walk already is.
        for (String step : named.steps()) {
            List<TermPath> next = new ArrayList<>();
            for (TermPath at : frontier) {
                List<TermPath> across = reach.across(at, step);
                List<NameReach.NotStanding> stopped = reach.notStanding(at, step);
                if (!across.isEmpty() || !stopped.isEmpty()) {
                    next.addAll(across);
                    // Under this case the name stands nowhere, and the reading of the case said
                    // where it stopped. One per case, since the readings need not have stopped
                    // alike.
                    stopped.forEach(each -> otherwise.add(new PlacementOutcome.Unresolved(
                            new PlacementOutcome.Reason.TheReadingStoppedThere(
                                    new PositionId(at.refine(each.branch())), each.why()))));
                    // A name crossing into the cases reaches every case, so a case the reading left
                    // is a place this name got to and stops at. Read from what the walk wrote down
                    // about that case, never from the name having one place fewer to stand.
                    reach.branchesNotEntered().stream()
                            .filter(each -> each.at().equals(at))
                            .forEach(each -> otherwise.add(new PlacementOutcome.Refused(each)));
                    continue;
                }
                // An ordinary name, which is at the position of that name one step down — and only
                // where the walk made one.
                TermPath under = at.then(step);
                if (byPath.containsKey(under)) {
                    next.add(under);
                    continue;
                }
                PlacementOutcome.Reason why = whyNothingAt(at);
                if (why != null) {
                    otherwise.add(new PlacementOutcome.Unresolved(why));
                } else {
                    // Nothing of that name, and the reading did not stop here. Whether that is a
                    // question or a contradiction turns on whether a rule wrote the name, which
                    // this does not know — so it is said and the caller that knows decides.
                    unnamed.add(at + " has no `" + step + "` under it: " + (byPath.get(at) == null
                            ? "no position there" : byPath.get(at).structure()));
                }
            }
            frontier = next;
        }
        return new Followed(List.copyOf(frontier), List.copyOf(otherwise), List.copyOf(unnamed));
    }

    /**
     * What the reading was left with where a name stopped, which is only ever that it stopped.
     *
     * <p>The position's own answer and not a second opinion about it. A reading that stopped says so
     * where it stopped ({@link StructuralInspection.Continuation.Blocked}), and that is what an
     * author is owed.
     *
     * <p><b>There is no other answer.</b> A rule names a location only where the language reads one
     * there, and wherever the language reads one this reading has a position or a crossing for it: a
     * record's fields are its positions, a name a sum's cases share crosses into them, and a field of
     * what a sequence, an option or a map holds is not readable without opening it. So a name that
     * gets here having reached a position this reading did not stop at is this reading disagreeing
     * with the language about what may be written — which is not a limitation an author can be told
     * about, and not something to hand on as one.
     */
    private PlacementOutcome.Reason whyNothingAt(TermPath at) {
        Position position = byPath.get(at);
        if (position != null
                && position.structure() instanceof StructuralInspection.Retained retained
                && retained.continuation()
                        instanceof StructuralInspection.Continuation.Blocked blocked) {
            return new PlacementOutcome.Reason.TheReadingStoppedThere(
                    new PositionId(at), blocked.why());
        }
        return null;
    }

    /**
     * What stands at {@code path} as this reading has it, or null where it reaches nothing there.
     *
     * <p>The position first and the declarations only where there is none, which is the one
     * resolution of where a term sits. A caller that walked the declarations itself would answer
     * with what a field was declared as before anything narrowed it, and a caller that took the
     * position alone would have nothing under a path this reading stopped. Both readers of this —
     * what a term is measured on, and whether an operation may be taken of it — get the same answer
     * because there is one.
     */
    public Type typeAt(TermPath path, RuleReadingSource source) {
        Position position = at(path);
        return position != null ? position.type() : declaredAt(path, source);
    }

    /**
     * What the declarations put at {@code path}, however far down it goes, or null where they put
     * nothing this can follow.
     *
     * <p>Under a position the walk stopped at this is the only answer there is: there is no position
     * to ask and the type is what the declaration says at each step. A path is finite and each step
     * of it is followed once, so following one under a declaration that names itself ends where the
     * path does.
     *
     * <p>Step by step through {@link StructuralInspection}, which is what the walk above takes its
     * own steps from. What is under a type is one fact, and a second reading of it here would be
     * this and that walk disagreeing about what a path reaches — which is the shape of defect this
     * whole change is about, one level down.
     */
    private Type declaredAt(TermPath path, RuleReadingSource source) {
        Type here = null;
        for (Parameter parameter : parameters) {
            if (parameter.name().equals(path.head())) {
                here = parameter.type();
                break;
            }
        }
        for (TermPath.Step step : path.steps()) {
            here = here == null ? null : under(here, step, source);
        }
        return here;
    }

    /**
     * What one step of a path stands at, or null where the declarations put nothing there.
     *
     * <p><b>Exhaustive over the kinds of step, with no {@code default}.</b> A path goes into a
     * field, into what a sequence holds, or nowhere at all while narrowing which values may stand
     * where it already is ({@link Refinement}) — three, and a reading that answered one of them and
     * let the rest fall to null would lose a line the model draws for every path carrying one. It
     * did: written for fields alone, a rule comparing two fields of a list's elements was read as
     * naming nothing, and the border it draws went away. A fourth kind is a compile error here
     * rather than a fourth quiet absence.
     */
    private static Type under(Type type, TermPath.Step step, RuleReadingSource source) {
        TypeView view = TypeView.of(type, source.symbols());
        // Asked of the shape rather than through the proof a position is made with. What is under a
        // type is a question about the type, and a type nothing can be read at answers nothing here
        // rather than being refused as a position this compiler disagrees with itself about.
        if (!(view.shape() instanceof Shape.ReadablePositionShape shape)) {
            return null;
        }
        StructuralInspection under =
                StructuralInspection.of(shape, Distinctions.ofType(view, source.symbols()));
        return switch (step) {
            // A field of a record, or a name a sum's cases all spread. The second is readable on a
            // value of the sum without opening a case, so the model does put something at it, and a
            // reading that answered for the first alone would say the model puts nothing where the
            // language reads a value.
            case TermPath.Step.Field field -> under instanceof StructuralInspection.Decomposed made
                    ? made.under().get(field.name())
                    : ReadableFields.of(shape).declaredFields().get(field.name());
            case TermPath.Step.Element _ -> under instanceof StructuralInspection.Retained on
                    && on.continuation() instanceof StructuralInspection.Continuation.Elements held
                    ? held.element() : null;
            // The same position, read as the case it turned out to be. Null where the case puts
            // nothing there, which is a case that is the whole of a value.
            case TermPath.Step.Refine refine -> under instanceof StructuralInspection.Retained on
                    && on.continuation() instanceof StructuralInspection.Continuation.Branches ways
                    ? narrowed(ways, refine.refinement()) : null;
        };
    }

    /** The type the branch for this narrowing stands at, or null where the sum has no such branch. */
    private static Type narrowed(StructuralInspection.Continuation.Branches ways,
                                 Refinement refinement) {
        for (StructuralInspection.Branch branch : ways.branches()) {
            if (refinement.equals(branch.refinement())) {
                return branch.under();
            }
        }
        return null;
    }

    /**
     * This reading and what it says about its numbers, as one value.
     *
     * <p>The way a reader that uses both gets them. Which position a name stands at and what a
     * number there is measured on are two questions such a reader asks together, and asked of two
     * values it was handed separately they can be of two behaviors — a parameter spelled the same
     * way in both is all it takes. So the pairing is made here, where the second is made from the
     * first, and there is nowhere else to make one.
     */
    public InputReading reading(RuleReadingSource source) {
        return new InputReading(this, quantities(source), source);
    }

    /**
     * The same input, asked about a quantity over several of its positions.
     *
     * <p>The relational half of what a reading of an input can say. A {@link Position} answers about
     * itself, and a rule relating two of them divides neither — so a caller holding a form asks here
     * rather than composing the positions' answers, which cannot carry a relation whatever each of
     * them says.
     *
     * <p><b>A capability, and not part of what this answers with.</b> Asking the declarations a
     * further question takes a way of reading them, and what is answered with is compared as a value
     * by whatever decides that a compile changed nothing — so this is built where it is used and
     * never kept here. Which is also why it takes the symbols rather than holding them: the reading
     * of an input says what it says, and this is how something else goes on asking.
     *
     * <p><b>Once per reader.</b> What comes back reads each parameter's declarations, so a caller
     * that built one per comparison would read every parameter of every behavior once per
     * comparison written about it. Built at the top of whatever is walking, and handed down.
     */
    public Quantities quantities(RuleReadingSource source) {
        Map<TermPath, OpenedRules> byRoot = new LinkedHashMap<>();
        for (RuleRoot root : roots) {
            // The first reading under a path stands, for the same reason the first reading of a
            // position does: two roots at one path would be one place answering differently
            // depending on which reader looked it up.
            //
            // And with the condition it was opened under, which is what says whose rows its rules
            // are about. Read without it, the cases of a sum are rules about every row and refuse
            // an input between them.
            byRoot.computeIfAbsent(root.at(),
                    at -> new OpenedRules(PlacedRules.of(at, root.type(), source, policy),
                            root.opening()));
        }
        // Where a term's subject stands, handed over already answered. What comes back asks a
        // position first and the declarations under one the reading stopped above, which is the one
        // resolution of it — worked out again from the positions this hands over, a rule about a
        // name every case of a sum spreads would be read as naming nothing.
        return ReadQuantities.of(byRoot, byRoot.keySet(), byPath, cases,
                path -> typeAt(path, source), source, policy);
    }

    /**
     * What a body's read of {@code binding} names, or null where it is not one of these parameters.
     *
     * <p>Asked of the binding and never of the spelling. A body may bind a name its own behavior
     * already binds — {@code let f = defaulted(f)} — and the two are different values under one
     * word, so a reader matching the word reads the inner one as the outer.
     */
    public String parameterRead(BindingId binding) {
        return binding == null ? null : read.get(binding);
    }

    /**
     * The same, as the whole map, for a reader that walks a tree rather than asking about one
     * binding.
     *
     * <p>A behavior's parameters are bound more than once. The implementation binds them where the
     * body reads them, and the declaration binds them where its own {@code ensures} clauses do; a
     * reading is handed the bindings of the tree it is walking, and one given the others finds every
     * comparison about nothing. What is here is the implementation's, which is what this reading was
     * made from.
     */
    public Map<BindingId, String> parameterReads() {
        return read;
    }

    /**
     * {@code position} with the rules it handed on counted as unread, because nothing took them.
     *
     * <p>Made here and not asked of the position, which does not know it. A reading of a value ends
     * where no declaration stands to be read and the rules under the position become another
     * reading's; whether one was opened is settled by the descent past this position, and that
     * happens after the position has been read and reported. Answering it would be a way to write a
     * {@link Position} down, and there being no such way is what keeps one position to one answer.
     *
     * <p>Read off the ledger and not off whether a reading exists somewhere under the path: an
     * obligation discharged by whatever happens to be below it is one no descent ever had to meet
     * ({@link RuleHandoffs}).
     *
     * <p><b>This is where the two readings are joined, and the only place that may be.</b> The
     * ledger knows that no recipient was named; the structural reading knows whether the walk could
     * go into the position at all. Neither is the provenance on its own, and whoever asked later
     * would be joining whatever state each phase still happened to be holding — which for the
     * structural side is nothing, since a position a body's rule measures keeps no continuation
     * (issue #1084). So the answer is settled here and carried from here.
     *
     * <p>Added to whatever the position already found. A reading that lost a clause of its own and
     * handed rules on that nobody took is short of both, and answering with the first was how the
     * second went missing.
     */
    private static Position shortOfHandedOnRules(Position position,
                                                 RuleHandoffs.Shortfall shortfall) {
        if (shortfall == null) {
            return position;
        }
        ReadPosition read = (ReadPosition) position;
        BlockedDescent blocked = BlockedDescent.of(read.structure());
        RulesLeftUnread.HandoffUnread why = switch (shortfall) {
            case RuleHandoffs.Shortfall.NothingExpected _ ->
                    new RulesLeftUnread.HandoffUnread.FromBlockedDescent();
            case RuleHandoffs.Shortfall.NotFullyAccepted _ ->
                    new RulesLeftUnread.HandoffUnread.NotFullyAccepted();
        };
        // The whole agreement and not the half this arm happens to need. Over an owed handing over,
        // nobody was named as a recipient exactly where the walk could not go in — so a ledger that
        // named nobody at a position this entered and a ledger that named somebody at a position it
        // could not enter are both two walks over one model disagreeing about one path, joined by
        // the spelling of it. Held one way, the second would arrive as an arm nothing folds beside a
        // descent that is reported, which is #1084's own two entries, reached by a different road.
        if (why.namesABlockedDescent() != (blocked != null)) {
            throw new IllegalStateException(
                    "the ledger and the walk disagree at " + read.path() + ": " + shortfall
                            + " beside " + read.structure());
        }
        java.util.Set<RulesLeftUnread> left =
                new java.util.LinkedHashSet<>(read.rulesLeftUnread());
        left.add(new RulesLeftUnread.Handoff(why));
        return new ReadPosition(read.path(), read.view(), read.bounds(),
                read.nothingExists(), read.projection(),
                read.declared(), read.reading(), read.obligations(), read.admitted(),
                read.rulesWithoutALine(), read.unansweredQuestions(),
                left, read.structure());
    }

    /**
     * What this step of the reading is doing.
     *
     * <p>Two roads reach a position and one walk takes both, so every step has to say which of them
     * it is on. Enumerating is following what a type has; demanding is following what the
     * measurement named. A step is usually on both — the paths a measurement names mostly run
     * through positions the enumeration finds anyway — and the two part only under a position the
     * enumeration stopped at, where the demands go on alone.
     *
     * <p>Taken as one value rather than as three arguments, because the three change together and a
     * caller that moved one without the others would be describing a step that does not exist.
     *
     * @param demanded    the paths the measurement named that are at or under this position, which
     *                    is what decides where the reading goes on once the enumeration has stopped
     * @param enumerating whether every step to here is one the enumeration itself takes. Where it is
     *                    not, this position is here because the measurement named it and its
     *                    siblings are not
     * @param handedOn    whether the position above passed its rules down to here. False under a
     *                    position the reading stopped at: what is owed there is owed by the reading
     *                    that stopped, and a demand walking past it discharges none of it
     */
    private record Reach(List<TermPath> demanded, boolean enumerating, boolean handedOn) {

        static Reach enumerating(List<TermPath> demanded) {
            return new Reach(demanded, true, true);
        }

        /** Whether this position is one the reading reports, rather than one it walked through. */
        boolean reports(TermPath at) {
            return enumerating || demanded.contains(at);
        }

        /** The same at {@code child}: the demands that go on through it, and what the step is. */
        Reach into(TermPath child, boolean stopped) {
            List<TermPath> on = new ArrayList<>();
            for (TermPath each : demanded) {
                if (each.isAtOrUnder(child)) {
                    on.add(each);
                }
            }
            return new Reach(on, enumerating && !stopped, handedOn && !stopped);
        }

        /** Whether the reading goes on into the position this reaches. */
        boolean enters() {
            return enumerating || !demanded.isEmpty();
        }
    }

    /**
     * One position, read, and then what is under it.
     *
     * <p>What is under a position is walked whether or not the position itself came to anything.
     * Only a product has children, and a product states no distinction of its own and carries no
     * end, so nothing is read here that a reader would have to weigh against the position above
     * it — and a reader that gives a position up in favour of its fields finds them read either
     * way.
     */
    private static void walk(TermPath path, Type type, ExpansionTrace ancestry, RuleReadingSource source,
                             ReadingPolicy policy, PlacedRules placed, List<Position> found,
                             List<RuleRoot> roots, java.util.Set<Type> visited,
                             RuleHandoffs handoffs, NameReach.Observed observed,
                             Gathered account, Reach reach) {
        // The proof first, and before anything is read off the position. A shape a reading is not
        // made of is this compiler disagreeing with itself about what may stand at a position, and
        // it is refused here rather than arriving further down as a position nothing divides.
        ReadablePosition input = ReadablePosition.of(TypeView.of(type, source.symbols()));
        // What the position's type states, read once and handed to both readings of it. What a sum's
        // cases are decides which classes the position has and which branches stand under it, and a
        // second reading of that here would be the two disagreeing about which cases there are.
        List<Case> declared = Distinctions.ofType(input.view(), source.symbols());
        // Asked of the occurrence and answered before anything under it is opened, never before the
        // occurrence itself is read. What stands here is read whichever time round it is — the
        // classes of a sum, the ends its rules put on it — and what is refused is unfolding the
        // declaration a second time on this path.
        TypeSymbol unfolds = ExpansionTrace.unfoldedBy(input.shape());
        // And asked only where this reading is enumerating. What has to terminate is the listing of
        // what positions a type can have; a reading following one path the model named has the path
        // for a bound and needs no other, which is why the same walk answers both questions and
        // stops for only one of them.
        TermPath already = reach.enumerating() ? ancestry.openedAt(unfolds) : null;
        StructuralInspection structure = already != null
                ? StructuralInspection.stoppedAt(
                        new BlockReason.RecursiveExpansion(unfolds, already))
                : StructuralInspection.of(input.shape(), declared);
        Position here = read(input, path, source, policy, placed, structure, declared);
        // Passing through an occurrence is not finding a position. A reading following a path the
        // model named opens every declaration on the way — that is how it knows which step to take
        // and whose rules reach the end of it — and what it reports is the end. Published all the
        // way down, one threshold written five links into a chain would put four sums into the
        // measure and ask for a row at each of them, which is the listing this reading does not do.
        if (reach.reports(path)) {
            found.add(here);
        }
        // Said as the position is read and before anything under it is walked, so that a descent
        // that never happens leaves the obligation standing rather than never making one.
        if (reach.handedOn() && placed.handsTheRulesOnAt(path)) {
            handoffs.owes(placed.root(), path);
        }
        // Handed down and never back up, so that two fields declared as one type are two positions
        // and both are read. Shared between them, the second would come back as a path returning to
        // where it had been, which is a different fact entirely.
        ExpansionTrace deeper = ancestry.opening(unfolds, path);
        // What the descent may take, which is what the shape has. This reading having stopped is an
        // answer about the reading — it decides what the position reports and where the enumeration
        // goes next — and it does not change what stands under the type, so a path the measurement
        // named goes on through here while its siblings do not.
        StructuralInspection descent = already != null
                ? StructuralInspection.of(input.shape(), declared) : structure;
        switch (descent) {
            case StructuralInspection.Decomposed decomposed -> {
                for (Map.Entry<String, Type> field : decomposed.under().entrySet()) {
                    Reach on = reach.into(path.then(field.getKey()), already != null);
                    if (!on.enters()) {
                        continue;
                    }
                    walk(path.then(field.getKey()), field.getValue(), deeper, source, policy,
                            // A field is a value of its own, so a sum met under it is one this walk
                            // has not taken apart however many were taken apart above.
                            placed, found, roots, java.util.Set.of(), handoffs, observed, account,
                            on);
                }
            }
            case StructuralInspection.Retained retained ->
                    under(retained.continuation(), here, path, input, deeper, source,
                            policy, placed, found, roots, visited, handoffs, observed, account,
                            reach, already != null);
        }
    }

    /**
     * The names readable at this position that a value of one of its cases carries, which is empty
     * for every position but a sum whose cases all spread one declaration.
     *
     * <p>Asked of {@link ReadableFields}, which is what makes those names readable on a value of the
     * sum at all. Taken from what a case declares instead, a field one case has and another has not
     * would be a name this said could be written at the sum, and the reading would reach positions
     * the language refuses to name.
     *
     * <p>Asked where the branches are and nowhere else, so a shape whose readable names are its own
     * positions is not a case this has to hold an answer for: a name crosses a narrowing or it is
     * reached without one.
     */
    private static List<String> sharedAt(ReadablePosition input) {
        return List.copyOf(ReadableFields.of(input.shape()).declaredFields().keySet());
    }

    /**
     * What follows a position that stands, walked.
     *
     * <p>Beside the position and never instead of it: what a list holds is read, and the list is
     * still a position with a length of its own that this reading has already answered for; a case
     * puts positions under the sum, and the sum still divides into its cases.
     *
     * <p><b>Owed and not merely declared.</b> A case the rules refuse has no positions to cover —
     * every field of it is a row nobody can write — and the branches walked are the ones the
     * position came back owing, so that what a report counts at the position and what it measures
     * under it come from one reading of it. Which is also why the widening the obligations may have
     * applied travels with them: where the rules were set aside, they are set aside for the
     * positions under the case as much as for the case.
     *
     * <p>And it is where the rules the position handed on are passed to somebody. Which positions
     * they are passed to is settled here, from the branches this position is owed at — never worked
     * out afterwards from the readings that exist, since a reading opened under the position for
     * some other reason is not one anybody handed anything to.
     */
    private static void under(StructuralInspection.Continuation continuation, Position here,
                              TermPath path, ReadablePosition input, ExpansionTrace ancestry,
                              RuleReadingSource source, ReadingPolicy policy,
                              PlacedRules placed, List<Position> found, List<RuleRoot> roots,
                              java.util.Set<Type> visited, RuleHandoffs handoffs,
                              NameReach.Observed observed, Gathered account,
                              Reach reach, boolean stopped) {
        switch (continuation) {
            // Nothing is opened under the position, so a handoff made there stays standing. Which
            // is the answer whichever of the two this is: a shape this compiler does not enter and
            // a depth this walk stops at both leave the rules under it read by nobody.
            case StructuralInspection.Continuation.None _,
                 StructuralInspection.Continuation.Blocked _ -> { }
            case StructuralInspection.Continuation.Elements elements -> {
                TermPath at = path.element();
                Reach on = reach.into(at, stopped);
                if (!on.enters()) {
                    break;
                }
                // Not where the reading stopped here. What a demand reaches past a stop is reached
                // by the demand, and saying the rules were passed on would have the ledger record a
                // recipient for an obligation this reading never discharged.
                if (!stopped) {
                    handoffs.passesTo(placed.root(), path, List.of(at));
                }
                // Nothing crosses into what a sequence holds: what a clause of the value out here
                // says is written about the sequence, and an element is a value with a declaration
                // of its own.
                takeTheRulesOver(placed.root(), path, at, elements.element(), ancestry, source,
                        policy, found, roots, java.util.Set.of(), handoffs, observed, null,
                        new RootOpening.Inside(placed.root(), path), account, on,
                        placed.machines());
            }
            case StructuralInspection.Continuation.Branches branches -> {
                // Asked where the branches are, which is the only place a name can cross one.
                List<String> shared = sharedAt(input);
                List<StructuralInspection.Branch> standing = new ArrayList<>();
                List<TermPath> passedTo = new ArrayList<>();
                for (StructuralInspection.Branch branch : branches.branches()) {
                    // Said of every case, including the ones this walk turns back at, because what
                    // a reader of a sum asks is answered over the whole list of them: a sum has a
                    // value wherever any case does.
                    if (!reach.into(path.refine(branch.refinement()), stopped).enters()) {
                        // How far the walk goes, and not anything the model says about this case.
                        observed.became(path, branch.refinement(), new CaseOutcome.NotWalked());
                        continue;
                    }
                    // A branch that is the whole of a value puts no position anywhere, and one the
                    // rules leave nothing at has no row to be written at it. Neither is a place the
                    // rules were passed to, so neither is owed a reading.
                    //
                    // Whether the rules leave the case is asked first, because it is the question
                    // the other one presupposes: that naming a case builds it says there is nothing
                    // under it to read, and says nothing about whether a value may stand there at
                    // all. Asked the other way round, a case the rules refuse comes back standing
                    // on its own wherever it holds nothing — which is a case with no value being
                    // recorded as the plainest kind of value there is, and read as one by everything
                    // that asks whether a sum has a value.
                    if (!owed(here, branch.refinement())) {
                        observed.became(path, branch.refinement(),
                                new CaseOutcome.RefusedByTheRules());
                        continue;
                    }
                    if (branch.under() == null) {
                        // A name has nowhere to stand under a case that holds nothing, which is not
                        // a shortfall and is not the answer below. Said apart from it so that a
                        // reader of what became of this case reads which it was.
                        observed.became(path, branch.refinement(), new CaseOutcome.StandsAlone());
                        continue;
                    }
                    standing.add(branch);
                    passedTo.add(path.refine(branch.refinement()));
                }
                if (!stopped) {
                    handoffs.passesTo(placed.root(), path, passedTo);
                }
                for (StructuralInspection.Branch branch : standing) {
                    int before = found.size();
                    // What the value above calls the positions under this case, where it calls them
                    // anything: the names its cases share and nothing else.
                    SharedNames crossing = new SharedNames(path, branch.refinement(),
                            new java.util.LinkedHashSet<>(shared));
                    // Handed down as the reading of the case is opened, so a clause written above
                    // is read at the position it is about by the one reading of that position.
                    // Nothing to hand down where nothing crosses, which is not the same as the case
                    // standing under no narrowing — that is what the opening beside this says.
                    PlacedRules.Reaching reaching =
                            shared.isEmpty() ? null : new PlacedRules.Reaching(placed, crossing);
                    walkBranch(branch, placed.root(), path, ancestry, source, policy, found, roots,
                            visited, handoffs, observed, reaching,
                            new RootOpening.Refined(placed.root(), crossing), account,
                            reach.into(path.refine(branch.refinement()), stopped),
                            placed.machines());
                    crossed(observed, crossing, found, before);
                }
            }
        }
    }

    /**
     * Say where the names the cases share stand, now that this case has been walked.
     *
     * <p>Asked of the positions this branch just produced, which is what keeps a crossing from
     * naming a position nobody made: a field deeper than this reading goes and a case it turned back
     * out of both leave the name with one place fewer to stand, and neither leaves a crossing to be
     * read as one.
     *
     * @param from  where the walk's positions began before this branch was walked, so that what is
     *              looked through is what this branch put there
     */
    private static void crossed(NameReach.Observed observed, SharedNames crossing,
                                List<Position> found, int from) {
        if (crossing.names().isEmpty()) {
            return;
        }
        TermPath at = crossing.sum();
        TermPath narrowed = at.refine(crossing.branch());
        List<Position> made = found.subList(from, found.size());
        for (String field : crossing.names()) {
            // What the value above calls this position, asked of the one thing that answers it —
            // which is what the reading of the position asks when a clause of that value is read
            // here. Worked out again, this would be a second statement of one relation, and a break
            // in either would leave the other answering as though nothing had happened: the rules
            // would stop arriving and the account would go on saying they had.
            TermPath stands = made.stream()
                    .map(Position::path)
                    .filter(each -> at.then(field).equals(crossing.outerPathOf(each)))
                    .findFirst().orElse(null);
            if (stands != null) {
                observed.crosses(at, field, crossing.branch(), stands);
                continue;
            }
            // Nowhere under this case, and the reading of the case is what says why. Asked of the
            // sum instead, there would be nothing to say: a sum is read whatever the depth, so its
            // silence would be read as the model putting no such field here.
            BlockReason.AboutThePosition why = whereItStopped(made, narrowed);
            if (why != null) {
                observed.doesNotStand(at, field, crossing.branch(), why);
            }
        }
    }

    /** What the reading of the case standing at {@code narrowed} was left with, or null where it was
     *  not left with anything — which is a case that was read to the end. */
    private static BlockReason.AboutThePosition whereItStopped(List<Position> made,
                                                               TermPath narrowed) {
        for (Position each : made) {
            if (each.path().equals(narrowed)
                    && each.structure() instanceof StructuralInspection.Retained retained
                    && retained.continuation()
                            instanceof StructuralInspection.Continuation.Blocked blocked) {
                return blocked.why();
            }
        }
        return null;
    }

    /**
     * Open a reading at a position the rules were passed to, and say that it took them.
     *
     * <p>The one way a reading of a value begins under another. What a case of a sum holds and what
     * a sequence holds are values with declarations of their own, and a clause is not written across
     * either boundary: what {@code Tag} says about itself is written in {@code Tag}, whether the
     * position is reached by narrowing an optional or by being one of however many a list holds. So
     * both cross here, and a shape this compiler learns to walk later is a shape whose rules are
     * read only if it comes through this too.
     *
     * <p>Taking the rules over is said here and not worked out afterwards from the readings that
     * exist. A reading opened under a position for some other reason is not one anybody handed
     * anything to, and letting it discharge the obligation would read the absence of a failure as
     * evidence that something was read (#1072).
     */
    private static void takeTheRulesOver(TermPath by, TermPath at, TermPath opened, Type type,
                                         ExpansionTrace ancestry, RuleReadingSource source,
                                         ReadingPolicy policy,
                                         List<Position> found, List<RuleRoot> roots,
                                         java.util.Set<Type> visited, RuleHandoffs handoffs,
                                         NameReach.Observed observed,
                                         PlacedRules.Reaching crossing, RootOpening opening,
                                         Gathered account, Reach reach,
                                         DeclarationReadings machines) {
        roots.add(new RuleRoot(opened, type, opening));
        // Said where a reading is actually opened, so that a case recorded as opened is one there
        // is somewhere to ask about. Said where the branch was chosen instead, a descent that turns
        // back at a value it has already been at would leave a case pointing at a reading nobody
        // made.
        if (opening instanceof RootOpening.Refined it) {
            observed.became(it.crossing().sum(), it.crossing().branch(), new CaseOutcome.Opened());
        }
        if (reach.handedOn()) {
            handoffs.accepts(by, at, opened);
        }
        PlacedRules rules = PlacedRules.of(opened, type, source, policy, crossing, machines);
        // Said as the reading of this value is opened, so that what a build has to account for is
        // what the rules of the values it read actually placed.
        account.from(rules);
        walk(opened, type, ancestry, source, policy, rules, found, roots, visited,
                handoffs, observed, account, reach);
    }

    /**
     * One branch of a position, where the reading still owes it and something stands under it.
     *
     * <p><b>A narrowing takes no level.</b> {@code query@GlobalQuery.limit} is the same distance
     * from the parameter as {@code query.limit} is under a record parameter, because naming which
     * case a value turned out to be does not move into it. Counted as a level, the same field would
     * be measured or not according to whether an author wrote a sum around the record it is in.
     *
     * <p>And the rules under it are the case's. A clause is not written across a narrowing: what a
     * {@code GlobalQuery} says about its {@code tag} is written in {@code GlobalQuery}, so a new
     * root begins here and the reading of the sum's own value has nothing to say below it.
     */
    private static void walkBranch(StructuralInspection.Branch branch, TermPath by, TermPath path,
                                   ExpansionTrace ancestry, RuleReadingSource source, ReadingPolicy policy,
                                   List<Position> found, List<RuleRoot> roots,
                                   java.util.Set<Type> visited, RuleHandoffs handoffs,
                                   NameReach.Observed observed, PlacedRules.Reaching crossing,
                                   RootOpening opening,
                                   Gathered account, Reach reach, DeclarationReadings machines) {
        // <b>A descent that costs no level stops only where it returns to a value it has already
        // been at without a step into one.</b> That is the whole of the rule, and what it is keyed
        // on is the value reached and never the narrowing taken: a narrowing is an edge and the
        // thing that has to terminate is a state, and the two agree only where the edge decides the
        // state. A case of a sum does decide it — the case names the type — and whether an optional
        // holds anything does not, so an `Option<Option<Int>>` read on the narrowing would be one
        // `Some` deep for ever after.
        //
        // Kept per branch of the walk and dropped at every step into a value, because a value met
        // under a field is one this walk has been nowhere near.
        //
        // And no reading is opened, so nothing takes the rules of this case over and the handoff
        // above stays standing. The rules were read where the value was first met, at a position
        // this walk already reported; saying they were read here as well would be one reading
        // discharging an obligation raised somewhere it never went.
        if (visited.contains(branch.under())) {
            // Where the descent stops, and nothing under this case was read. What is known about it
            // is nothing, which is not what the rules leaving nothing at it would be.
            observed.became(path, branch.refinement(), new CaseOutcome.NotWalked());
            return;
        }
        java.util.Set<Type> deeper = new java.util.LinkedHashSet<>(visited);
        deeper.add(branch.under());
        takeTheRulesOver(by, path, path.refine(branch.refinement()), branch.under(), ancestry,
                source, policy, found, roots, deeper, handoffs, observed, crossing, opening,
                account, reach, machines);
    }

    /**
     * Whether the reading of {@code position} still owes a row at this branch.
     *
     * <p>Asked in the words the obligations are in, through the one relating of the two
     * ({@link Refinement#of}). Matched on the kind of distinction here, a branch of a kind this
     * happened not to name would be a branch nothing owes and nothing walks.
     */
    private static boolean owed(Position position, Refinement refinement) {
        for (Case each : position.obligationCases()) {
            if (refinement.equals(Refinement.of(each))) {
                return true;
            }
        }
        return false;
    }

    /**
     * The reading of one position.
     *
     * <p>Which numbers it has and what its rules leave each of them are asked together because they
     * are one reading: the numbers come off the type, and every end an author placed is placed on
     * one of them.
     */
    private static Position read(ReadablePosition input, TermPath path, RuleReadingSource source,
                                 ReadingPolicy policy,
                                 PlacedRules placed, StructuralInspection structure,
                                 List<Case> declared) {
        TypeView view = input.view();
        Type type = view.declared();
        Carrier carried = Carrier.ofValue(type, source.symbols());
        ValueName.Stdlib taken = NumericMeasures.takenOf(type, source.symbols());
        // The ends the value this sits in places on this position, which its own type says nothing
        // about. Read beside the type's own rules and not after them: a clause naming one coordinate
        // and a constant places an end wherever it is written, so where the rule was written is not
        // what decides whether there is a line here (ADR-0090).
        List<FieldDomains.Placed> stated = placed.placedAt(path);
        // Where the reading that turns this type's clauses into constraints put an end on the
        // value. Read for the check below and for nothing else: what bounds the position is the
        // reading of the clauses as they are written ({@code boundsOn}), and a second answer about
        // the same clauses is one this would have to choose between rather than intersect.
        List<FieldDomains.Placed> constrained =
                DeclaredCoordinates.placedOnItsOwnValue(type, source, policy, placed.machines());
        // And the ends the value's own conjuncts state that no comparison says: a rule about the
        // strings places no comparison, and a conjunct that placed no end can still move one.
        List<FieldDomains.Placed> movedHere = placed.ownEndsAt(path);
        RulesWithNoLine.Gathered found = new RulesWithNoLine.Gathered();
        // The numbers this position has, which its type settles and no rule votes on.
        List<NumberAt.OfWhatNumber> kinds = numbersOf(taken);
        // And every end placed here is on one of them, whichever reading found it. Nothing is filed
        // out: an end names the number it was placed on, and each of the bounds below takes the
        // ends that name its own.
        everyEndIsOnANumberOfThisPosition(kinds, constrained, path);
        everyEndIsOnANumberOfThisPosition(kinds, movedHere, path);
        everyEndIsOnANumberOfThisPosition(kinds, stated, path);
        // A value whose rules contradict has no positions to cover: every edge of every field of it
        // is a row nobody can write, which is not the same answer as a field nothing bounds.
        boolean nothingExists = placed.bounds().infeasible(placed.answers());
        // Which values the position may hold, and how much of what its rules say was read. The same
        // reading the numbers come from and a separate question of it: a rule can name the values a
        // position holds without stating where they stop, and one that states where they stop
        // without naming any of them.
        AdmissibleSet admitted = placed.admits(path);
        List<PositionBounds> bounds = new ArrayList<>();
        for (NumberAt.OfWhatNumber kind : kinds) {
            bounds.add(boundsOn(kind, path, type, taken, view, source, carried, placed,
                    movedHere, stated, nothingExists));
        }
        rulesWithoutALineAt(placed, path, type, source, found);
        // Everything left open about the rules of this position: what the accounting of the
        // declaration's clauses could not classify, and what this walk itself could not. The ones
        // nothing classified go into the gathering beside the findings, so that what the position's
        // rules came to is one value and a caller asking whether a reading stopped here has one
        // place to ask.
        List<StandingQuestion> exact = standingAt(placed, path, found);
        RulesWithNoLine noLine = found.found();
        List<RuleWithoutALine> withoutALine = noLine.reported();
        List<StandingQuestion> open = new ArrayList<>(noLine.unclassified());
        open.addAll(exact);

        // What the position's own distinctions are crossed with is what stands there, which is the
        // number those distinctions divide. A count taken of the position runs on another order,
        // and a case crossed with a range of it would be kept or refused by where a length falls.
        ReadingResult reading = crossed(declared, view,
                whatIsLeft(bounds, new NumericTerm.ValueOf(path)),
                admitted, source, noLine, nothingExists, type);
        return new ReadPosition(path, view, bounds, nothingExists,
                placed.projection(path), declared, reading,
                ObligationDomain.of(reading, declared), admitted,
                withoutALine,
                // What the rules of this position leave open. Asked of the accounting rather than
                // read off the completeness beside it: one reading being short of a position's
                // rules is that reading's business, and a rule another reading took in is not a
                // rule left unread.
                //
                // And beside those, the rules nothing worked out the questions of, from both
                // readings that can leave one.
                open,
                // And whether the rules were reached at all, asked of the gathering that knows.
                // No question is raised where nothing was seen, so an empty list beside it would
                // say every rule was accounted for. Read off the reading's own reason instead, a
                // position carrying both a rule it could not read and a subtree it never entered
                // answered with the first and lost the second.
                //
                // Only this reading's own loss is known here. Whether a handing over was taken up
                // is the descent's to say, and it is added afterwards
                // ({@link #shortOfHandedOnRules}).
                placed.everyRuleReachedAt(path) ? java.util.Set.of()
                        : java.util.Set.of(new RulesLeftUnread.ClauseOfThisReadingWasUnread()),
                structure);
    }

    /**
     * What the position's declarations leave standing.
     *
     * <p>The type's own distinctions crossed with the rules, and where the type states none, the
     * values the rules name. Asked in this order rather than merged: what a type states is what the
     * position's values are, and a rule naming some of them divides what is left rather than
     * replacing it.
     *
     * <p>A type nothing could be read off answers neither, and says so: an empty reading and a
     * reading that could not be made read alike and are not the same claim.
     */
    private static ReadingResult crossed(List<Case> declared, TypeView view,
                                         NumericDomain.Bounds admissible, AdmissibleSet admitted,
                                         RuleReadingSource source,
                                         RulesWithNoLine found,
                                         boolean nothingExists, Type type) {
        BlockReason.AboutThePosition unreadable = Distinctions.unreadableAt(view);
        if (unreadable != null) {
            return new ReadingResult.Unsupported(unreadable);
        }
        BlockReason.RuleReadingStopped here = found.aReadingThatStopped();
        if (!declared.isEmpty()) {
            return Crossing.of(declared, view, admissible, admitted, source.symbols(), here);
        }
        // The values a rule named, where the type states no division. Not crossed with anything:
        // the reading that named them is the reading of the rules, and a value the rules single out
        // is one they admit. Nothing is read for a value whose own rules contradict — there is no
        // value of it for a rule to have named.
        List<Case> named = nothingExists ? List.of()
                : Distinctions.ofValues(admitted.approximation(), type, source.symbols());
        BlockReason.ReadingStopReason why = admitted.whyPartial() != null
                ? Crossing.stopped(admitted.whyPartial()) : here;
        if (why != null) {
            return new ReadingResult.Partial(named, List.of(), why);
        }
        return admitted.alternativesNotSeparated()
                ? new ReadingResult.NotSeparated(named, List.of())
                : new ReadingResult.Complete(named, List.of());
    }

    /**
     * The numbers a position of a type counted by {@code taken} has.
     *
     * <p><b>The type's answer, and not the rules'.</b> What stands at the position is one of them
     * wherever the position is; the second is there where the type declares an operation that
     * counts its values. A rule is free to write about some further number of the place — what an
     * absolute value comes to, what two of its fields add up to — and none of those is a number the
     * position has: a measure of one would divide values this place does not hold.
     *
     * <p>Which is a qualification and never a choice. Both numbers are the position's whatever the
     * rules say about either, and a rule that placed an end on one of them has said which number it
     * is about — so there is nothing here to pick between, and a reader picking would be taking a
     * line the author can read away in favour of another.
     *
     * <p>What stands at the position comes first, so that a reader walking these meets the number
     * every position has before the one only some do.
     */
    private static List<NumberAt.OfWhatNumber> numbersOf(ValueName.Stdlib taken) {
        NumberAt.OfWhatNumber own = new NumberAt.OfWhatNumber.OfItsOwnValue();
        return taken == null ? List.of(own)
                : List.of(own, new NumberAt.OfWhatNumber.OfWhatAnOperationAnswers(taken));
    }

    /** What the rules leave one of a position's numbers, or null where the position has no such
     *  number. */
    private static NumericDomain.Bounds whatIsLeft(List<PositionBounds> bounds,
                                                   NumericTerm.FromOnePosition term) {
        for (PositionBounds each : bounds) {
            if (each.term().equals(term)) {
                return each.admissible();
            }
        }
        return null;
    }

    /**
     * What the rules leave one number of one position.
     *
     * <p>Every answer here is about {@code kind} and about no other number of the place. Three
     * sources of ends and not one: what the type's own clauses wrote, what its conjuncts state or
     * moved that no comparison says — a rule about the strings at a position leaves them running
     * between two places and orders nothing — and what the value this position sits in placed. Each
     * list holds ends of every number, and each is asked for this one's; they are intersected, and
     * every rule that put an end where it is kept.
     *
     * <p><b>One reading of the type's own clauses, and it is {@link #ofTheType}.</b> There is a
     * second — the one that turns those clauses into constraints, which {@code read} holds for its
     * own question — and it answers about more of them: it reaches the end under
     * {@code String.length(value) * 2 >= 4}, under a disequality that moves a floor and under an
     * equality that states both ends, where reading the comparison as written finds none. Handed
     * both and intersected, this would take whichever answer is tighter and there would be nothing
     * to say which of the two the position is bounded by — one clause read two ways, with the
     * difference kept out of sight by the intersection rather than settled. So the second reading
     * is not a parameter here.
     */
    private static PositionBounds boundsOn(NumberAt.OfWhatNumber kind, TermPath path, Type type,
                                           ValueName.Stdlib taken, TypeView view,
                                           RuleReadingSource source, Carrier carried,
                                           PlacedRules placed,
                                           List<FieldDomains.Placed> movedHere,
                                           List<FieldDomains.Placed> stated,
                                           boolean nothingExists) {
        NumericTerm.FromOnePosition term = switch (kind) {
            case NumberAt.OfWhatNumber.OfItsOwnValue _ -> new NumericTerm.ValueOf(path);
            case NumberAt.OfWhatNumber.OfWhatAnOperationAnswers _ ->
                    NumericTerm.TakenOf.of(taken, path, type, source.symbols());
        };
        if (term == null) {
            throw new IllegalStateException(
                    "this reading has " + path + " counted by " + taken
                            + ", which is not what its type is counted by: " + Type.show(type));
        }
        Carrier on = carrierOn(kind, carried);
        DeclaredBounds.Bounds own = on == null ? null
                : DeclaredBounds.and(ofTheType(kind, on, view, source),
                        DeclaredBounds.and(
                                DeclaredBounds.placed(movedHere, kind, on),
                                DeclaredBounds.placed(stated, kind, on)));
        // A record's rule relates the numbers its fields hold, so it reaches the term that is one of
        // them and no other: a cap on a field says nothing about how long the string beside it is.
        //
        // Exhaustive, with no `default`. Whether a clause of the record reaches a number is asked
        // per kind of number, so one added is a question put here rather than an arm that takes
        // whichever answer it was not named in.
        NarrowedBounds projected = switch (term) {
            case NumericTerm.ValueOf _ -> placed.at(path);
            case NumericTerm.TakenOf _ -> NarrowedBounds.NOTHING;
        };
        return new PositionBounds(term,
                // Two questions of one pair of readings, and they do not have one answer. What the
                // term's values can be is every rule about it intersected; where it is divided is
                // only where its own type draws a line, because a clause relating two fields is not
                // a partition of one.
                nothingExists ? null : TypeBounds.admissible(own, projected.bounds(), term),
                own, projected,
                // Where the number actually stops, which the ends as written do not say: a clause
                // placing one at 0 beside a clause that takes the 0 away leaves a number whose
                // first value is 1, and a line drawn at the 0 is drawn at no value of it.
                placed.leftAt(path, kind));
    }

    /**
     * What the ends on that number are read on.
     *
     * <p>A count is a whole number whatever it counts, so nothing about the type decides how its
     * sizes are spaced; what stands at the position is read on whatever its own values are compared
     * on, which is nothing where nothing here compares them.
     */
    private static Carrier carrierOn(NumberAt.OfWhatNumber kind, Carrier carried) {
        return switch (kind) {
            case NumberAt.OfWhatNumber.OfItsOwnValue _ -> carried;
            case NumberAt.OfWhatNumber.OfWhatAnOperationAnswers _ -> Carrier.WHOLE;
        };
    }

    /** What the names this position wears leave {@code kind}, which each of them is read for on its
     *  own: a name bounding the length of what it wraps says nothing about the order of it. */
    private static DeclaredBounds.Bounds ofTheType(NumberAt.OfWhatNumber kind, Carrier on,
                                                   TypeView view, RuleReadingSource source) {
        return switch (kind) {
            case NumberAt.OfWhatNumber.OfItsOwnValue _ -> DeclaredBounds.of(view, source, on, null);
            case NumberAt.OfWhatNumber.OfWhatAnOperationAnswers it ->
                    DeclaredBounds.of(view, source, on, it.operation());
        };
    }

    /**
     * That every end placed here is on a number this position has.
     *
     * <p>Not a fact about the model and nothing an author can act on: the two sides are one
     * question asked twice of the same type. Which numbers a position has is
     * {@link NumericMeasures#takenOf} of the type at the path, and the operation an end names is
     * that same answer, recorded where the clause was read. So an end on some other number is the
     * two readings having parted, and there is nothing to report about it because no model reaches
     * it — checked here, where both are in hand, rather than left as an end that quietly bounds
     * nothing.
     */
    private static void everyEndIsOnANumberOfThisPosition(List<NumberAt.OfWhatNumber> numbers,
                                                          List<FieldDomains.Placed> ends,
                                                          TermPath path) {
        for (FieldDomains.Placed each : ends) {
            if (!numbers.contains(each.at().of())) {
                throw new IllegalStateException(path + " has the numbers " + numbers
                        + ", and a rule placed an end on " + each.at().of()
                        + "; the reading of what a position is counted by and the reading of what a"
                        + " clause counted disagree");
            }
        }
    }

    /**
     * The number one rule of a declaration is about, as a finding names it.
     *
     * <p><b>The rule's own answer and not this reading's.</b> Which number a position is measured
     * at and which number a rule is about are two questions, and they coincide only while a
     * position has at most one number taken of it. Answered with the term this reading chose for
     * the position, a rule about one operation's number would be filed at another's the day a
     * second is taken of the same place — which is the identity this coordinate exists to keep.
     *
     * <p>The operation comes from the clause's reading, which recorded it against the count, and
     * the path from this one. Neither can name the term alone: a path is held there as a spelling,
     * and one parsed back out of a spelling is a path this compiler made up.
     *
     * <p>No falling back to the position. A rule with an operation beside it is a rule about that
     * operation's number, so a term that cannot be built from the two is the reading of the clause
     * and the reading of the position disagreeing about what stands here — which is this compiler
     * contradicting itself rather than something the model left out.
     */
    private static FilingCoordinate filedAt(TermPath path, NumberAt<RuleKey> at,
                                            Type type, RuleReadingSource source) {
        return FilingCoordinate.of(termAt(path, at, type, source));
    }

    /**
     * The number a coordinate of a declaration's own reading names, in this input's vocabulary.
     *
     * <p><b>The one crossing.</b> The operation comes from that reading, which recorded it against
     * the count, and the path from this one — and neither names the term alone: a path is held over
     * there as a key relative to the value the clauses are written on, and one parsed back out of a
     * spelling is a path this compiler made up.
     *
     * <p>Shared by the two things that cross here, a finding about a rule and a question a rule
     * raised, because it is one crossing and not a repetition. Written at each of them, the two
     * would be free to differ by a round of edits, and what they disagreed about would be which
     * number a rule is about.
     *
     * <p><b>One arm per kind of coordinate, and no falling back to the position.</b> A coordinate
     * this does not classify is not one measured by its own values; written as a test for the
     * operation with the position as the other answer, a kind added later would arrive here as a
     * term about something the model never wrote, and the reading of it would be applied to
     * whatever stood at the path.
     */
    private static NumericTerm termAt(TermPath path, NumberAt<RuleKey> at,
                                      Type type, RuleReadingSource source) {
        return switch (at.of()) {
            case NumberAt.OfWhatNumber.OfItsOwnValue _ -> new NumericTerm.ValueOf(path);
            case NumberAt.OfWhatNumber.OfWhatAnOperationAnswers answered ->
                    takenBy(answered.operation(), path, type, source);
        };
    }

    /**
     * The term for what {@code by} answers of what stands at {@code path}.
     *
     * <p>Both refusals are this compiler contradicting itself rather than something the model left
     * out, which is why neither is an answer a caller can act on.
     */
    private static NumericTerm takenBy(ValueName by, TermPath path, Type type, RuleReadingSource source) {
        // The operation a count is taken by is one the library declares, which is what the reading
        // that recorded the count went to. Anything else here is that reading and this one holding
        // different ideas of what an operation is.
        if (!(by instanceof ValueName.Stdlib operation)) {
            throw new IllegalStateException("a clause of `" + path + "` was read as a rule about `"
                    + by + "`, which is not an operation a number is taken by");
        }
        NumericTerm.TakenOf taken = NumericTerm.TakenOf.of(operation, path, type, source.symbols());
        if (taken == null) {
            throw new IllegalStateException("a clause of `" + path + "` was read as a rule about `"
                    + by + "`, and that takes no number of what stands there");
        }
        return taken;
    }

    /**
     * What the rules of this position leave open, in this input's vocabulary.
     *
     * <p>The questions crossed here, where the root the walk started at and the type standing at
     * the position both are. The reading that raised them knows its positions by a key relative to
     * the value its clauses are written on; what everything past here compares is a term path.
     * Which number of a position a question is about crosses unchanged — it is a resolved name, and
     * no capability of either side is asked about it.
     *
     * <p><b>And the rules nothing classified, which cross without any of that.</b> What such a rule
     * raises is the part that was not read, so there is no subject to translate and none is made:
     * the place crosses as where a reader is sent to look, which is not what the rule is about.
     *
     * <p>From both readings that can leave one. The accounting says what the declaration's clauses
     * left undecided; this walk says what it could not decide itself, which no accounting has —
     * which of a position's numbers it is measured at is settled here. Taken from the accounting
     * alone, a position both of whose coordinates are spoken for came back with nothing standing at
     * it and its measures closed over a rule this compiler could not use.
     *
     * <p>The questions nothing classified are put into the gathering and the rest are returned, so
     * that everything saying a reading stopped here is in one value. Kept in a list of its own
     * beside it, a caller asking what stopped had two places to ask and answered from whichever it
     * had in hand.
     */
    private static List<StandingQuestion> standingAt(PlacedRules placed, TermPath path,
                                                     RulesWithNoLine.Gathered found) {
        List<StandingQuestion> out = new ArrayList<>();
        for (PlacedRules.RuleUnclassifiedAt each : placed.unclassified(path)) {
            // One question per thing that stopped it. A published question carries one reason, and
            // a classification can have been stopped by more than one — a clause read a branch at a
            // time is stopped by whatever stopped each branch — so they are asked as the several
            // questions they are rather than one of them standing for the rest.
            each.at().why().forEach(why ->
                    found.asked(StandingQuestion.BoundaryUndetermined.of(each.cited(),
                            FilingCoordinate.at(path), why)));
        }
        for (souther.compiler.check.RuleAccounting.Unanswered each : placed.unanswered(path)) {
            out.add(StandingQuestion.Exact.of(each.cited(),
                    switch (each.owed()) {
                        case souther.compiler.check.Owed.AdmittedValues _ ->
                                new InputQuestion.AboutAPosition(path);
                        // The place and nothing else. Which number of it the rule is about is the
                        // same value on both sides, so the crossing cannot lose it and cannot fail
                        // to make it: a question about a number this compiler could not read is
                        // asked here exactly as one it could.
                        case souther.compiler.check.Owed.Boundary it ->
                                new InputQuestion.AboutANumber(it.on().at(path));
                    },
                    // What the reading that would have answered was short of, in this compiler's
                    // own terms. The crossing is where the two readings' vocabularies become one:
                    // out here a reader is owed what was missing, and which reading was asked is
                    // provenance a document has no business writing a different word for.
                    each.why().stopped()));
        }
        return List.copyOf(out);
    }

    /**
     * The rules saying where this position's values stop that nothing turned into an end.
     *
     * <p>The invariant's half of what a {@code guard}'s comparison is asked. Both draw lines
     * (ADR-0090) and both can be written in a form this does not read, and only one of them was
     * saying so — a bound it dropped left the position looking like one no rule bounds, which is
     * what the declaration above it denies.
     *
     * <p>Read off the one reading that draws lines from clauses ({@link FieldDomains#noLineAt}) and
     * not walked again here. A second walk over the position's own type answered for the clauses
     * written on it and knew nothing of the clauses written on the value it sits in, so a record's
     * rule about one of its fields was dropped in silence; and where both had something to say
     * about one clause, the report printed two causes for it.
     *
     * <p>One finding per rule, as a {@code guard}'s comparison is. Two clauses stopped by the same
     * limit at one position are two things for a reader to lift and are two findings: which clause
     * to rewrite is what an author acts on, and a position is not it. Kept per reason, the second
     * of them was dropped as a repeat of the first.
     */
    private static void rulesWithoutALineAt(PlacedRules placed, TermPath path, Type type,
                                            RuleReadingSource source,
                                            RulesWithNoLine.Gathered out) {
        for (FieldDomains.NoLine each : placed.noLineAt(path)) {
            // The rule the reading of ends was holding when it gave up, carried rather than left
            // behind. It is a clause of an invariant, so it has a name and the handle is that name.
            //
            // At the number that rule is about, which the rule itself says. Nothing is missing here
            // for the position to stand in for: a clause was read far enough to be about one number
            // or the other, and it is only the line that nothing came of.
            out.add(new RuleCitation.Named(each.part().rule()),
                    filedAt(path, each.at(), type, source),
                    each.why());
        }
    }

}
