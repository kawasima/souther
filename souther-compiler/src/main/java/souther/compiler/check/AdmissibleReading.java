package souther.compiler.check;

import souther.compiler.core.Core;
import souther.compiler.regex.PatternPlan;
import souther.compiler.inputs.BlockReason;
import souther.compiler.regex.PatternRead;
import souther.compiler.types.Type;
import souther.compiler.values.AdmittedPlan;
import souther.compiler.values.Allowance;
import souther.compiler.values.PlannedValues;
import souther.compiler.values.UnreadReason;
import souther.compiler.values.Value;
import souther.compiler.values.ValueSet;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The clauses reaching a value, read for which values each of its positions may hold.
 *
 * <p>Beside the reading that turns those same clauses into bounds, over the same list and at the
 * same moment. Which clauses reach a value is settled once, by the walk that gathers them, so the
 * two readings cannot come to disagree about what they were given; what each of them makes of a
 * clause is its own.
 *
 * <p><b>Leaves, and not the connectives over them.</b> What a conjunction or a choice of these
 * comes to is asked where both languages are held together and where the choices of a declaration
 * are decided ({@code StatedByClauses}), because a branch nobody can be in is settled by things
 * neither language holds alone. Answered here as well, that would be a second place deciding what
 * a choice does to the values, and the two would agree until one of them learned something.
 *
 * <p>Apart from what a construction owes ({@link Predicates}), which is a different question and
 * has a different answer for the same clause. A clause stated as one of two alternatives owes a
 * construction nothing that a guard could discharge, and it says something here — so reading it
 * through the obligations would turn "some reading took this in" into "a construction may be held
 * to it", and a row would be offered at an edge promised by nothing.
 *
 * <p>Everything this cannot read is {@link AdmissibleValues#unreadable}, which widens and never
 * narrows. That is the whole discipline: a reading that answered "no value" from a clause it did
 * not read would refuse a model somebody can write. A denial is turned into what it leaves where
 * the values can be written out ({@link ValueUniverse}) and is kept as a denial where they cannot,
 * so nothing reaches emptiness except through values this had in hand.
 */
final class AdmissibleReading {

    private final Terms terms;
    /** What each position of the value is called, and what type stands there. A position is here
     * whether or not it is a number: which values a boolean has is as much an answer as which
     * values an integer has, and the reading that asked the carrier had no word for the first. */
    private final Map<FactSubject, Type> byName;
    private final Symbols symbols;
    /**
     * What puts two sets together, and what it is allowed to build doing it.
     *
     * <p>One of these for the whole of what is read into one answer, because that is what the
     * allowance is about: every rule reaching a position pays into the machine that position finally
     * admits. It is the reading's and not a leaf's, so a clause read here and a rule met with it
     * afterwards ({@code InvariantChecker}) spend from the same purse.
     */
    private final Allowance<FactSubject> allowed;
    /**
     * What each node was read as, so that reading it is done once.
     *
     * <p>By the node itself and not by what a node is equal to: two clauses written the same way
     * are two places in a declaration, and what each of them is about is asked of the one in hand.
     */
    private final Map<Core, StringPredicates.Stated> asStated = new IdentityHashMap<>();
    /**
     * What this reading was short of at each clause it gave up on, against the clause.
     *
     * <p>By the node itself and by the same rule as the table above: two clauses written the same
     * way are two places somebody wrote, and a rule is answerable for the one it wrote. Kept as the
     * decisions are made, so that what a rule is answerable for is never worked out afterwards from
     * what a position was left holding.
     */
    private final Map<Core, List<RuleShortfall>> shortfalls = new IdentityHashMap<>();
    /** What each clause asked a machine for, against the clause, and by the same rule again. */
    private final Map<Core, Set<AskedAt>> asked = new IdentityHashMap<>();
    /**
     * The clauses this reading gave up on, whatever they were about.
     *
     * <p>Beside {@link #shortfalls} and not read off it. A shortfall is one fact per position, so a
     * clause about no position of this value leaves none — and it is still a clause nothing read,
     * which is what an alternative holding it turns on.
     */
    private final Set<Core> gaveUp = Collections.newSetFromMap(new IdentityHashMap<>());
    private AdmissibleReading(Terms terms, Map<FactSubject, Type> byName,
                              Symbols symbols, Allowance<FactSubject> allowed) {
        this.terms = terms;
        this.byName = byName;
        this.symbols = symbols;
        this.allowed = allowed;
    }

    /** The reading of one value's positions, for {@link StatedByClauses} to take the leaves of.
     *
     *  <p>No environment is held. A leaf is read at where it stands, which the fold hands down; a
     *  reading holding the environment the clause began in would read a rule under a binding at
     *  names that mean nothing there, and every such rule came out as a form nothing reads. */
    static AdmissibleReading of(Terms terms, Map<FactSubject, Type> byName,
                                Symbols symbols, Allowance<FactSubject> allowed) {
        return new AdmissibleReading(terms, byName, symbols, allowed);
    }

    /** What this reading is spending, for whoever meets its answer with the next rule's. */
    Allowance<FactSubject> allowed() {
        return allowed;
    }

    /**
     * An equality names a value, a denial leaves the rest, and a pattern names the strings it
     * accepts; nothing else here is read.
     *
     * <p>A pattern is the third because a format is a third kind of answer and not because it is a
     * call. What {@code String.matches} says about a position is which strings stand there, which
     * is the question this reading asks — read as a call nobody follows, the rule said nothing and
     * a position an author had written a format for came out admitting every string there is.
     *
     * <p>And a position of two values is read as itself. Naming one is stating it, which is the
     * one value it then holds, and the polarity above says which — the reading of the clause has
     * already turned {@code p == false} and {@code p /= true} into this leaf denied
     * ({@link Conditions#restated}), so nothing here asks how the author spelled it.
     */
    PlannedValues<FactSubject> leaf(Core e, boolean positive, Denotations at) {
        if (e instanceof Core.Binary b
                && Comparison.of(b).map(Comparison::claim).orElse(null)
                        instanceof ComparisonClaim.Singled singled) {
            // Which of the two it states, once the denials above have been counted: what the
            // comparison holds at the value it names, turned over by each denial it stands under.
            return comparison(b, singled.holdsAtTheValue() == positive, at);
        }
        PlannedValues<FactSubject> truth = truthValued(e, positive, at);
        if (truth != null) {
            return truth;
        }
        PlannedValues<FactSubject> matched = pattern(e, positive, at);
        return matched != null ? matched : unreadable(e, at);
    }

    /** What naming a position of two values says about it, or null where {@code e} is not one. */
    private PlannedValues<FactSubject> truthValued(Core e, boolean states, Denotations at) {
        FactSubject position = positionIn(e, at);
        Type type = position == null ? null : byName.get(position);
        return type == Type.BOOL
                ? PlannedValues.at(position,
                        AdmittedPlan.of(admits(Value.truth(true), states, type)))
                : null;
    }

    /**
     * What a predicate over strings says about the position it is asked of, or null where the leaf
     * is not one.
     *
     * <p>Read whichever way it is stated. Denied, what stands is every string the predicate does
     * not accept, which is a set the same way — and reading only the stated form would leave a
     * denial as a form this cannot take apart, which of this very form would not be true.
     *
     * <p>Which calls these are, and what each says, is {@link StringPredicates}'. What is left here
     * is reaching the position and the written text and turning the answer into a plan — so a
     * predicate this reading learns is a row in that table and not an arm added here.
     *
     * <p>Null and not {@link AdmissibleValues#unreadable} wherever the leaf's own account is the
     * right one, so that the one place a reading gives up stays where it is: what a rule this could
     * not read costs is worked out there, and a second answer to it here would be a second account
     * of the same thing. That is every leaf that is not one of these, and every one of these whose
     * reading stopped at something other than this reading's own limit.
     */
    private PlannedValues<FactSubject> pattern(Core e, boolean states, Denotations at) {
        StringPredicates.Stated stated = statedIn(e, at);
        FactSubject position = stated == null ? null : positionIn(stated.subject(), at);
        if (position == null) {
            return null;
        }
        return switch (stated.reading()) {
            // Named and not built. What the position finally admits is met out of every rule that
            // reached it, and a pattern met with three written strings is a question about three
            // strings — built here, it would be a machine nobody needed and the position would have
            // that much less for the meet it does need. So what is said is which machine would
            // answer this rule, and whether one is ever made of it is settled where the position's
            // plan is worked out under its allowance.
            case StringPredicates.Reading.Accepting it -> asking(e, position,
                    new AdmittedPlan.Pattern(states ? PatternPlan.of(it.accepts())
                            : PatternPlan.notMatching(it.accepts())));
            case StringPredicates.Reading.PatternNotRead it -> stoppedBy(e, it.why(), position);
            // A rule whose text this could not work out is a rule this did not read, and what that
            // costs is the leaf's to say — over every position the clause names, which is more than
            // this one wherever the text is written out of another.
            case StringPredicates.Reading.WrittenArgumentNotKnown _ -> null;
        };
    }

    /**
     * What a pattern this reading stopped short of costs, or nothing where the leaf says it.
     *
     * <p>Only the one this reading is itself the limit of. A pattern written more deeply than this
     * reads is a limit of the reading and not a shape it has no word for, so it is said as itself —
     * left to fall through, it would go out as a form nothing here takes apart, and an author would
     * go looking for the construct that was the trouble when every construct in it is one this
     * reads. Every other construct is one the subset does not hold, which is a rule this could not
     * read like any other, and what that costs is worked out once at the leaf over every position
     * the clause names.
     *
     * <p>No {@code default}: a construct the subset learns to stop at is one somebody decides about
     * here, rather than one that quietly takes the answer its neighbours were given.
     */
    private PlannedValues<FactSubject> stoppedBy(Core e, PatternRead.Unsupported why,
                                                 FactSubject position) {
        return switch (why) {
            // The clause as well as the position. What a rule is answerable for is about the
            // pattern somebody wrote, and the position is where the reading was left short — read
            // back off the second, this would be every rule that named the place.
            case NESTED_TOO_DEEPLY -> shortOf(e, Set.of(position),
                    UnreadReason.PATTERN_TOO_DEEPLY_NESTED);
            case A_GROUP_ABOUT_THE_MATCH,
                 A_BACK_REFERENCE,
                 A_CHARACTER_PROPERTY,
                 A_BOUNDARY,
                 A_QUOTATION,
                 A_CLASS_OF_CLASSES,
                 A_COUNT_THIS_CANNOT_READ,
                 SOMETHING_UNCLOSED,
                 AN_ESCAPE_THIS_DOES_NOT_READ,
                 AN_ANCHOR_THIS_CANNOT_PLACE,
                 A_POSSESSIVE_REPETITION -> null;
        };
    }

    /**
     * What one comparison says, which is a value named at a position or two positions being one.
     *
     * <p>The second is not a narrowing of either of them and is read all the same. What
     * {@code p == r} says is that the two are one side of the product, so a rule stated of one of
     * them afterwards is a rule about the other — and the reading that answers what a position
     * admits is where that has to be said, because it is that reading's product it is about.
     * Given up on instead, the two stayed two answers with a rule between them that reached
     * neither, and a declaration whose positions cannot hold one value together was admitted.
     */
    private PlannedValues<FactSubject> comparison(Core.Binary b, boolean states, Denotations at) {
        PlannedValues<FactSubject> read = sided(b.left(), b.right(), states, at);
        if (read == null) {
            // `"A" == value` says what `value == "A"` says.
            read = sided(b.right(), b.left(), states, at);
        }
        if (read == null) {
            read = relating(b, states, at);
        }
        return read != null ? read : unreadable(b, at);
    }

    /**
     * What a comparison between two positions states about the pair, or null where it is not one.
     *
     * <p>Two rules and one reading of them. An equality says the two are one side of the product;
     * a denial says no one value stands at both, which makes no side and is held beside the product
     * ({@code Apartness}). Which of the two it is has been settled already — {@code states} is what
     * the comparison claims once the denials it stands under have been counted — so {@code p /= r}
     * and {@code !(p == r)} arrive here alike and are read as the same rule.
     *
     * <p>Read here and not given up on. Left as a rule that reached no reading, what the rules
     * stated about {@code p} and what they stated about {@code r} stayed two facts about two
     * places, and a declaration whose positions cannot differ was admitted.
     *
     * <p>Only between two positions. A comparison of a position with itself is a rule about one
     * position and is left where every rule this does not read is left, and a comparison naming one
     * position and something this could not read is not a rule about a pair either.
     */
    private PlannedValues<FactSubject> relating(Core.Binary b, boolean states, Denotations at) {
        FactSubject here = positionIn(b.left(), at);
        FactSubject there = positionIn(b.right(), at);
        if (here == null || there == null || here.equals(there)) {
            return null;
        }
        return states ? PlannedValues.holdingAsOne(here, there)
                : PlannedValues.heldApart(here, there);
    }

    /**
     * A rule this reading could not turn into a set of values, and why.
     *
     * <p>How far this got and not how many positions the rule mentions. A comparison of one
     * position with another is a rule about a pair — {@code startsAt < endsAt}, {@code a /= b} —
     * and a set of one position's values is not what it says; nothing about it was beyond this
     * reading, which is what makes it its own answer rather than a shape nobody could read.
     * Anything else is a form this reading does not take apart, whatever it mentions.
     *
     * <p>Counting the positions instead reads {@code validPair(left, right)} as a relation, which
     * it may not be: what is known there is that two positions appear in an expression this could
     * not interpret, and the word it would be projected to says the rule relates them. The two
     * cases are told apart by what was recognised, at the one place a reading gives up, so an
     * ordering comparison and an equality answer alike — written per shape, {@code <} fell through
     * one path and {@code ==} another, and a relation came out as a form nobody could read.
     */
    private PlannedValues<FactSubject> unreadable(Core e, Denotations at) {
        return shortOf(e, names(e, at), relatesTwoPositions(e, at)
                ? UnreadReason.RELATES_TWO_POSITIONS : UnreadReason.FORM_NOT_READ);
    }

    /**
     * What {@code e} leaves the positions it names, said twice: to the places, and about the clause.
     *
     * <p>Both from the one decision and neither from the other. What a place is left holding is the
     * reading's answer about the place; what a rule is answerable for is about the thing somebody
     * wrote, which is the node in hand here and is nowhere in what the places come back with. Read
     * off the places afterwards, the second is a list of reasons and no clause — every rule reaching
     * a position pays into its answer, so the place has as many claimants as it has rules.
     *
     * <p>Written down against the node rather than returned beside the values, because the fold
     * carries one answer upward and this is not part of it. What is recorded here is asked for at
     * the leaf that was read ({@link #shortfallsAt}), which is a handing over and not a second
     * reading: nothing looks at what the clause came to in order to work out what was decided.
     */
    private PlannedValues<FactSubject> shortOf(Core e, Set<FactSubject> named, UnreadReason why) {
        // That this reading gave up here, which is not the same as what it gave up on. A clause
        // about no position of this value is one nothing read all the same, and a choice needs to
        // know: had it been read, the alternative holding it might have turned out one nobody can
        // be in, and then the choice would have been the other branch.
        gaveUp.add(e);
        List<RuleShortfall> mine = shortfalls.computeIfAbsent(e, _ -> new ArrayList<>());
        RuleShortfall.Site site = new RuleShortfall.Site.AtALeaf(e);
        named.forEach(each -> {
            RuleShortfall one = new RuleShortfall(each, why, site);
            if (!mine.contains(one)) {
                mine.add(one);
            }
        });
        return PlannedValues.unreadable(named, why);
    }

    /**
     * The position held to {@code plan}, with the clause that asked for it written down.
     *
     * <p>A machine is made far from here, under the position's allowance, and refused there. What
     * asked for it is this clause, and it is known here and nowhere after — every rule reaching the
     * position pays into that allowance, so the place cannot say which of them asked, and the
     * pattern alone is the same pattern wherever somebody wrote it.
     */
    private PlannedValues<FactSubject> asking(Core e, FactSubject position,
                                              AdmittedPlan.Pattern plan) {
        asked.computeIfAbsent(e, _ -> new LinkedHashSet<>())
                .add(new AskedAt(position, plan.plan(), new RuleShortfall.Site.AtALeaf(e)));
        return PlannedValues.at(position, plan);
    }

    /**
     * What was asked for at {@code e}, as it was asked and in the order it was.
     *
     * <p>A set of facts and no order of anybody's, kept in the order they were met all the same:
     * what is made out of these is published through a projection that reads the order it is given,
     * so a copy free to reorder would have one compiler publish two documents of one source.
     */
    Set<AskedAt> askedAt(Core e) {
        return Collections.unmodifiableSet(
                new LinkedHashSet<>(asked.getOrDefault(e, Set.of())));
    }

    /**
     * One machine a clause asked for: the position it is being built for, the pattern it is, and
     * the clause that asked.
     *
     * <p>The first two are what a refusal is about ({@link souther.compiler.values.Unbuilt
     * .RuleShortfall}) and are what routes it back here; the third is the written place it is then
     * answerable at. Two clauses writing one pattern about one position ask for one machine and are
     * two of these, which is two rules answerable for one refusal.
     *
     * <p>The place is held as the site it will be filed under, so that what makes two of these one
     * is what makes two facts one. Held as the node instead, the record would compare it as a
     * value — two clauses of one shape at one source position are equal nodes — and the pair of
     * them would arrive here as one, while the site they file under tells them apart.
     */
    record AskedAt(FactSubject position, PatternPlan plan, RuleShortfall.Site site) {

        AskedAt {
            if (position == null || plan == null || site == null) {
                throw new IllegalArgumentException(
                        "a machine is asked for by a clause, for a position");
            }
        }
    }

    /**
     * What the reading decided it was short of at {@code e}, of the rules somebody wrote there.
     *
     * <p>Asked at the leaf it was decided at, and answered out of what was written down when the
     * decision was made. Empty where the reading took the clause in, which is what a leaf nothing
     * was short of says.
     */
    List<RuleShortfall> shortfallsAt(Core e) {
        return List.copyOf(shortfalls.getOrDefault(e, List.of()));
    }

    /** Whether this reading gave up on {@code e}, which is what an alternative holding it is. */
    boolean gaveUpAt(Core e) {
        return gaveUp.contains(e);
    }

    /**
     * Whether {@code e} is a comparison this reading recognised, of one position against another.
     *
     * <p>Which shapes those are is {@link Relates}'s and not this reading's, because the check that
     * classifies a clause for what it raises asks the same thing — and a shape counted here and not
     * there tells an author two different things about one clause. What stays here is how this
     * reading looks a position up.
     */
    private boolean relatesTwoPositions(Core e, Denotations at) {
        return Relates.twoPositions(e, part -> positionIn(part, at));
    }

    /** The same, with {@code where} read as the position and {@code what} as the value, or null
     * where they are not those. */
    private PlannedValues<FactSubject> sided(Core where, Core what, boolean states, Denotations at) {
        FactSubject position = positionIn(where, at);
        Type type = position == null ? null : byName.get(position);
        Value value = type == null ? null : valueOf(what, at);
        return value == null ? null
                : PlannedValues.at(position, AdmittedPlan.of(admits(value, states, type)));
    }

    /**
     * The positions the clause {@code e} states a rule about the strings of.
     *
     * <p><b>Whatever became of the rule.</b> Which position a predicate over strings is about is
     * settled by the call, and it is settled whether or not this could work out the text written in
     * it: {@code String.matches("001" ++ tail, value)} is a rule about {@code value} in a module
     * that cannot reach {@code tail} exactly as it is in the one that can. Answered off what the
     * reading came to, the same declaration would be a rule about a position for one reader and
     * about nothing for another.
     *
     * <p>Which is why this is beside the reading and not a projection of it. What the position
     * finally admits is what this reading leaves; that it is a position the model wrote a string
     * rule about is a fact about the clause, and both are wanted by the readers that decide which
     * of a position's numbers it is measured at and where its values stop.
     *
     * <p>Over the whole of {@code e} and not its leaves as this folds them. A clause is walked
     * elsewhere by a reader holding a conjunct, and what that reader is asking is which positions
     * the conjunct states such a rule about — a denial and a choice inside it are still the
     * conjunct's.
     */
    Map<FactSubject, StringRestriction> stringRuleIn(Core e, PlannedValues<FactSubject> said,
                                                    Denotations at) {
        StringPredicates.Stated stated = statedIn(e, at);
        FactSubject position = stated == null ? null : positionIn(stated.subject(), at);
        if (position == null) {
            return Map.of();
        }
        // What the rule admits where it was worked out, and what stopped the reading where it was
        // not. The reading is the one recognition's and is projected here once: a reader working out
        // where the values stop and a reader saying what a rule left unread want the same fact, and
        // asking the table twice is one question with two derivations.
        //
        // Exhaustive with no `default`: an outcome added to the recognition is one somebody decides
        // about here, rather than one that quietly takes the answer its neighbours were given.
        return Map.of(position, switch (stated.reading()) {
            case StringPredicates.Reading.Accepting _ ->
                    new StringRestriction.Admitting(said.at(position));
            case StringPredicates.Reading.PatternNotRead it ->
                    new StringRestriction.NotKnown(BlockReason.forAPatternNotRead(it.why()));
            case StringPredicates.Reading.WrittenArgumentNotKnown _ ->
                    new StringRestriction.NotKnown(new BlockReason.UnreadValueRule());
        });
    }

    /**
     * What {@code e} states as a predicate over strings, worked out once for the node.
     *
     * <p>One derivation and two readers of it: what the rule admits, and which position it is
     * about. Asked twice of the table, the two would be one question with two derivations — which
     * is the arrangement {@link StringPredicates} exists to stop, one file further down.
     */
    private StringPredicates.Stated statedIn(Core e, Denotations at) {
        if (asStated.containsKey(e)) {
            return asStated.get(e);
        }
        StringPredicates.Stated said = StringPredicates.statedByChecked(e, symbols, terms, at);
        asStated.put(e, said);
        return said;
    }

    /** The position {@code e} is, or null where it is not one of the positions being read for. */
    private FactSubject positionIn(Core e, Denotations at) {
        FactSubject named = terms.subjectOf(e, at);
        return named != null && byName.containsKey(named) ? named : null;
    }

    /**
     * The values a position of {@code type} is left by naming {@code value}, or by denying it.
     *
     * <p>A denial over a type whose values can be written out is the rest of them, which is how
     * {@code /= true} beside {@code /= false} comes to leave nothing. Over a type whose values
     * cannot be, it stays a denial and leaves no end of values, which is how {@code /= "A"} beside
     * {@code /= "B"} comes to leave almost everything.
     */
    private ValueSet admits(Value value, boolean states, Type type) {
        if (states) {
            return ValueSet.just(value);
        }
        List<Value> every = ValueUniverse.of(type, symbols);
        if (every == null) {
            return ValueSet.allBut(value);
        }
        Set<Value> left = new LinkedHashSet<>(every);
        left.remove(value);
        return ValueSet.oneOf(left);
    }

    /**
     * The value {@code e} is, or null where it is not one written out.
     *
     * <p>Read through the one fold there is, so a value written as an expression of values — two
     * strings joined — is the value it comes to. A case of an enumeration holds nothing and is not
     * folded to anything; what it is is which declaration it is.
     */
    private Value valueOf(Core e, Denotations at) {
        if (e instanceof Core.UnitValue unit) {
            return Value.of(unit.data());
        }
        Object folded = Terms.folded(e, symbols, at);
        if (folded instanceof String text) {
            return Value.text(text);
        }
        if (folded instanceof Boolean truth) {
            return Value.truth(truth);
        }
        if (folded instanceof Long whole) {
            return Value.number(whole);
        }
        return folded instanceof BigDecimal number ? Value.number(number) : null;
    }

    /**
     * The positions {@code e} names.
     *
     * <p>What a clause this could not read costs. It cannot cost a position it does not name:
     * nothing here relates one position to another, so a rule narrowing a position names it — and a
     * rule relating two of them names both and is itself one of these.
     */
    private Set<FactSubject> names(Core e, Denotations at) {
        Set<FactSubject> found = new LinkedHashSet<>();
        gather(e, found, at);
        return found;
    }

    private void gather(Core e, Set<FactSubject> found, Denotations at) {
        // A binding is crossed as a binding: its body is what the clause states, read inside it,
        // and what it was given is not a part of the clause on its own. Walked as an ordinary node,
        // an argument a helper never reads was counted among the positions the rule names.
        if (e instanceof Core.LetIn li) {
            gather(li.body(), found, terms.inside(li, at));
            return;
        }
        FactSubject here = terms.subjectOf(e, at);
        if (here != null && byName.containsKey(here)) {
            found.add(here);
            return;   // a position names itself, and nothing under it is a position of its own
        }
        Core.forEachChild(e, child -> gather(child, found, at));
    }
}
