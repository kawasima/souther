package souther.compiler.partition;

import souther.compiler.check.DeclaredBounds;
import souther.compiler.check.Symbols;
import souther.compiler.check.TypeView;
import souther.compiler.inputs.Case;
import souther.compiler.inputs.Distinctions;
import souther.compiler.inputs.Refinement;
import souther.compiler.inputs.Requirements;
import souther.compiler.check.ConstructionDescent;
import souther.compiler.inputs.TermPath;
import souther.compiler.types.Type;
import souther.compiler.types.TypeSymbol;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;
import java.util.Set;

/**
 * Where a value has to be built under one parameter, and out of what.
 *
 * <p><b>Not the positions of an input.</b> What a behavior declares it takes is
 * {@link souther.compiler.inputs.InputDomain}'s, and what a row has to compose is this. The two are
 * written with the same {@link TermPath} and are about different things: this goes on until there is
 * a value to build, which is further than a report is about. So a path from here is never looked up
 * over there.
 *
 * <p>Where they do meet is how a position is named. A requirement narrowing a position is written
 * into the path here exactly as the reading writes it, so a class fixed at a position under a
 * refinement is a position this builds at under the same name. Written flat, the two would name one
 * location two ways, and a class the caller fixed would be looked for at a path this plan does not
 * have.
 *
 * <p><b>Worked out once and consumed three ways.</b> What each position may take, the search that
 * chooses one position at a time, and the composing of the chosen values back into a record were
 * three recursions over the same declarations, and the third said so — a value came back null "only
 * where the walk that collected the choices and this one disagree". Holding the positions and the
 * shape they compose back into as one value is what makes that disagreement unsayable rather than
 * something a test has to go looking for.
 *
 * <p><b>Made only by {@link #of}, which is where the requirements are put together.</b> A path the
 * caller fixed a value at states what has to hold for that position to exist, and a caller that also
 * hands over requirements of its own is handing over the second half of one fact rather than a
 * second fact (ADR-0114: coverage and construction use one merge, and neither keeps an account of
 * its own). A constructor a caller could reach is that merge being optional, and one caller took the
 * option: a line at {@code query.tag@Tag} was planned against no requirement at all, and the row
 * offered for it put a {@code NoTag} there.
 */
final class ConstructionPlan {

    private final Node root;

    private ConstructionPlan(Node root) {
        this.root = root;
    }

    /** The position the parameter's own value is built at. */
    Node root() {
        return root;
    }

    /**
     * One position of the value being built.
     *
     * <p><b>Where it is, and no more.</b> What is built at a position is a question three of these
     * answer and the fourth does not have: a requirement that settles the value itself leaves
     * nothing to build there, and a type answered for it would be a type nothing is built at. Asked
     * of all four, the one that has no answer has to invent one — the declared type, or the absence
     * dressed as a type — which is the shortfall this vocabulary exists to state going back in under
     * another name. So {@code type()} is each builder's own, and every reader of one already knows
     * which it is holding.
     */
    sealed interface Node permits Slot, Built, Held, Exact {

        /** Where it is. */
        TermPath at();

        /**
         * The newtype names still to put on what this node produces, outermost first.
         *
         * <p>One question of every node and three answers, because what produces the value differs.
         * A record and a collection are composed bare, so every name the position wears is still to
         * go on; a value chosen at a slot arrives already wearing whatever names its own type wears,
         * so what is left is the names the position wore before a requirement narrowed it — and
         * nothing at all where none did.
         *
         * <p>A row at a {@code data SlotN = Slot} carries {@code SlotN(Slot { ... })}, and a
         * {@code data DecisionN = Decision} narrowed to a case that wraps a number carries
         * {@code DecisionN(Special(5))}. A value composed without them is of a type the parameter
         * does not declare.
         */
        List<TypeSymbol> worn();
    }

    /**
     * A position the search chooses a value for.
     *
     * @param worn {@link Node#worn}: the names the position wore before a requirement narrowed it,
     *             since what is chosen here already wears whatever names {@link #type} wears
     * @param leaf why the search chooses a whole value here rather than composing one out of
     *             positions under it
     */
    record Slot(TermPath at, Type type, List<TypeSymbol> worn, Leaf leaf) implements Node {

        Slot {
            worn = List.copyOf(worn);
        }
    }

    /**
     * Why a position is one the search chooses a whole value at.
     *
     * <p><b>Only one of these is this compiler.</b> A position nothing composes is the
     * declarations' answer and a position the caller settled is the demand's; a position this
     * stopped short of looking inside is a figure being reached, and a row that comes to nothing
     * under it is a different thing to tell a reader. Written in the first's word, a value this
     * declined to plan for was reported as one the rules leave nothing at.
     *
     * <p>{@link Beneath} is evidence and not yet an answer. What follows from a plan being short
     * depends on what the composing then did: a row composed against it is a row, and nothing here
     * is owed to anybody. It is where the composing comes to nothing that this becomes the
     * difference between a fact about the model and a fact about this compiler.
     */
    sealed interface Leaf {

        /** Nothing is composed here: the declarations put no positions under it. */
        record Open() implements Leaf {}

        /** The caller fixed a value here, which is what says a class is being placed under whatever
         *  holds it. */
        record Fixed() implements Leaf {}

        /**
         * This compiler stopped short of reading what is under it.
         *
         * <p>Not that a figure was reached at this position. A sequence is planned by asking what
         * stands at its element, and a plan that gave that up further down is a plan whose answer
         * here — a whole value, chosen like any other — was not arrived at by looking. So what this
         * says is that the judgement was made without the reading, and the figures are those the
         * reading gave up at.
         *
         * @param cutBy every figure that was reached under here, and not the first of them: two
         *              positions given up at two figures are two things this compiler declined to
         *              do, and a reader asking what would let the plan go further is owed both
         */
        record Beneath(Set<CompositionBudget> cutBy) implements Leaf {

            public Beneath {
                cutBy = Set.copyOf(cutBy);
                if (cutBy.isEmpty()) {
                    throw new IllegalArgumentException(
                            "a position this stopped short at says which figure stopped it");
                }
            }
        }
    }

    /**
     * A sequence composed out of what stands at its element.
     *
     * <p>Only where a class is to be put there. A list nothing is being placed inside is a value
     * like any other and is chosen whole, which is what keeps the rules about how many it holds
     * with the one reader that has them — so this is the shape of a list a row is being built
     * <em>into</em>, and not the shape of every list.
     *
     * <p>One element and not however many. What a class at an element asks for is a list holding a
     * value in it; what the other elements are is a separate question, and answering it here would
     * decide it for every rule at once. So what is built here holds the element in the class and
     * whatever else the rules ask the list for, which is the least a row can be and still meet both
     * ({@link #neededToHold}).
     *
     * @param worn  {@link Node#worn}: every name the position wears, since what is composed here
     *              is bare
     * @param under  the element's own position
     * @param needed how many this has to hold for the value to be placed in it: the fewest the
     *               rules allow, and never fewer than one. The element being placed is one of them
     *               and the rest are values of the element's type — a class at an element asks for
     *               a list holding a value in it, and a list that met that and broke the rule about
     *               how many it holds is not a row.
     *               <p>Named for what it is and not for the end it is read off. The floor is the
     *               rules'; this is what the composing has to make, and the two part wherever the
     *               rules ask for none.
     */
    record Held(TermPath at, Type type, List<TypeSymbol> worn, Node under, int needed)
            implements Node {

        Held {
            worn = List.copyOf(worn);
            if (needed < 1) {
                throw new IllegalArgumentException(
                        "a list built around an element holds it: " + needed);
            }
        }
    }

    /**
     * A position composed out of the ones under it.
     *
     * @param of    what the record is called, which is what the composed value is written as
     * @param worn  {@link Node#worn}: every name the position wears, since what is composed here is
     *              bare. Where a requirement narrowed the position, the names it wore before the
     *              narrowing come first and the narrowed value's own after them — one list, because
     *              putting them back on is one thing done once
     * @param under the positions of its fields, in the order the declaration writes them
     */
    record Built(TermPath at, Type type, TypeSymbol of, List<TypeSymbol> worn,
                 SequencedMap<String, Node> under) implements Node {

        Built {
            worn = List.copyOf(worn);
            under = java.util.Collections.unmodifiableSequencedMap(new LinkedHashMap<>(under));
        }
    }

    /**
     * A position the requirement itself settles the value of.
     *
     * <p>The absence of an optional is the one of these there is. {@code None} is not a narrowed
     * type with something to be chosen inside it — it is a branch that "puts no position anywhere"
     * (ADR-0114) — so there is nothing here for the search to look at and nothing for it to be
     * refused at.
     *
     * <p><b>Beside the value being fixed by the caller and not the same thing.</b> A caller that
     * fixed a value has one to hand over; here nobody handed anything over and the requirement
     * decides, so writing it as a fixed {@link Slot} would mean putting the value into the caller's
     * table as well — a class stating a narrowing and fixing a value at the same position, which is
     * the two accounts ADR-0114 keeps apart.
     *
     * @param exact the value, which is what the requirement came to
     * @param worn  {@link Node#worn}: every name the position wears, since the value arrives bare.
     *              A {@code data MaybeTagN = Tag?} carries {@code MaybeTagN(None)}
     */
    record Exact(TermPath at, FixtureTemplate exact, List<TypeSymbol> worn) implements Node {

        Exact {
            if (exact == null) {
                throw new IllegalArgumentException("a position settled at no value is not settled");
            }
            worn = List.copyOf(worn);
        }
    }

    /**
     * A plan, or why there is none.
     *
     * <p>The ways of having none are whose fact each is. A caller asking for a class under one case
     * of a sum beside a class under another has asked for a row no value is, which the model
     * settles and this reports; nothing here fell short. A caller asking for a position deeper than
     * this plans has asked for something ordinary that this compiler declined to work out, and what
     * would change it is somebody raising a figure. A caller asking for something under a position
     * that holds nothing until a narrowing says what stands there has not said which, and what
     * would change that is the caller stating one.
     *
     * <p>Told apart because a reader acts on them differently, and reported rather than answered
     * because none of them is a plan. Handed back as one, a limit of this compiler's would arrive
     * in the words of a thing the model settles.
     */
    sealed interface Result {

        /** The plan, which is what a caller that got one goes on with. */
        record Planned(ConstructionPlan plan) implements Result {}

        /**
         * The model settles that there is no value to plan for, and this is what settles it.
         *
         * <p>One arm for every way of that, so a reader takes them together or not at all. What a
         * reader does with one of these is the same whichever it is — there is no row and no figure
         * to raise — and what parts them is only what to say. Beside {@link Beyond} as separate
         * arms, each new way the model can settle it would be a fourth arm somebody had to notice
         * belonged with the first rather than the second.
         */
        record Refused(ModelRefusal why) implements Result {}

        /**
         * What the caller asked for stands under a position this compiler stopped short of reading.
         *
         * <p>Not a plan that fell short: there is no plan. A path fixed under the figure is a
         * position the walk never reached, so nothing here would build the value there — and a plan
         * handed back all the same is one the composing would satisfy while leaving the caller's
         * value out of the row. That is the one shape of this a reader could not tell from an
         * ordinary row.
         *
         * <p>Told apart from {@link Refused} by whose fact it is. That is the model settling that
         * there is no value; this is a figure of this compiler's, and what would change it is
         * somebody raising the figure.
         *
         * <p>Carries the figures and no word for them. What a search that gets this comes back with
         * is the caller's to say — the plan knows the demand was out of reach and not what the
         * composing would have been reported as.
         */
        record Beyond(Set<CompositionBudget> by) implements Result {

            public Beyond {
                by = Set.copyOf(by);
                if (by.isEmpty()) {
                    throw new IllegalArgumentException(
                            "a demand out of this compiler's reach says which figure put it there");
                }
            }
        }

        /**
         * What the caller asked for stands under a position that holds nothing until a narrowing
         * says what stands there, and nothing said which.
         *
         * <p>A sum is the one of these a model writes. Its cases are what put positions under it,
         * so a path reaching a field of every case names a position of the sum and no case of it —
         * which is how a rule about the shared part is read (spec §sum-data) and is not enough to
         * write a value with. Reading and writing part here, and the reading is right: what has to
         * be added is which case the value is, and the caller is the one with a reason to prefer
         * one.
         *
         * <p>Handed back rather than chosen here. Which case a value is is a fact about the value
         * and not about the plan, and a plan that took the first would answer a question nobody
         * asked it — the narrowing the caller then states is what makes the position, and every
         * path under it, the one the value is written at.
         *
         * @param narrowings what the position stands at, in the order the declarations write them.
         *                   Empty where the position divides no way at all, which is a demand under
         *                   a position nothing puts anything under
         */
        record Unnarrowed(TermPath at, List<Refinement> narrowings) implements Result {

            public Unnarrowed {
                narrowings = List.copyOf(narrowings);
            }
        }
    }

    /**
     * How many a collection has to hold for a value to be placed in it, given what the rules leave
     * it.
     *
     * <p><b>The one place the rules' floor becomes a count to build.</b> They are two numbers: the
     * floor is how many the whole collection has to hold and says nothing about anything being
     * placed, and this is what the composing has to make. They agree wherever the rules ask for
     * more than none, which is why a second copy of this reads plausibly and why the two names
     * collapsed back into one every time there was a second copy.
     *
     * <p>So everything that needs the count takes it from here — what the plan records at a
     * collection, what a refusal compares the cap against, and what the composing builds — and a
     * caller holding one of these numbers can say which it is by where it came from.
     */
    static int neededToHold(DeclaredBounds.CountRange holds) {
        return Math.max(1, holds.least());
    }

    /**
     * What the model settles, where what it settles is that there is nothing to build.
     *
     * <p>Each of these is a fact about the declarations, established before anything was searched
     * for and standing however far this compiler went on to look. So one of these is what a reader
     * is told, and a figure this compiler reached at the same position is not — an author sent to
     * raise it would raise it and be told the same thing.
     *
     * <p>Which is why they are one type. A reader of a refusal acts on it the same way whichever it
     * is, and the ordering against a figure is written once, over this, rather than once per way
     * the model can settle it.
     */
    sealed interface ModelRefusal {

        /** Where the model settles it, which is what a report about it is written at. */
        TermPath at();

        /** No value is at both, and this is the position and the two it would have to be. */
        record Conflict(TermPath at, Refinement one, Refinement other) implements ModelRefusal {}

        /**
         * A value is to be placed inside a collection the rules leave no room in.
         *
         * <p><b>One reading, and {@link #needed} is read off it here rather than handed in.</b> The
         * two numbers are ends of one answer, and what this refusal says is that they do not meet:
         * the collection has to hold at least as many as the floor and at least the one being
         * placed, and the cap is under that. A maker that brought its own count could bring one
         * read where the row had settled something this one had not — which is the reading in two
         * places that this refusal exists because of.
         *
         * @param holds from how few to how many the rules let it hold, against the positions the
         *              caller has settled
         */
        record NoRoom(TermPath at, DeclaredBounds.CountRange holds) implements ModelRefusal {

            public NoRoom {
                if (holds.most() >= neededToHold(holds)) {
                    throw new IllegalArgumentException("a collection with room for what is placed"
                            + " in it refuses nothing: " + at + " holds " + holds);
                }
            }

            /**
             * How many it would have to hold for the value to be placed in it.
             *
             * <p>The floor is how many the rules ask the whole collection to hold, and the value
             * being placed is one of them rather than another beside them — so where the rules ask
             * for none, one is what a collection holding it comes to.
             */
            int needed() {
                return neededToHold(holds);
            }
        }
    }

    /**
     * The plan for one parameter, against everything that has to hold of it.
     *
     * <p><b>The requirements are put together here and nowhere else.</b> A path states what has to
     * hold for the position it names to exist ({@link TermPath#requirements}), and a caller may have
     * requirements besides that — a class selects a refinement at its own position, which no path
     * says. Both are read here, so a caller cannot hand over a fixed path under a case and no
     * requirement that the case was taken: the two would be one location decided twice, and the
     * plan would build the position as the sum rather than as the case.
     *
     * @param decided    the paths the caller has already fixed a value at, which are positions this
     *                   search does not choose at and does not look under. Not a depth and not a
     *                   fact about the type: the caller has what goes there, so what the field of a
     *                   record three levels inside it would have offered is nothing this row asks
     * @param additional what has to hold besides whatever {@code decided} already states. Named for
     *                   that, because a caller handing over the whole of it is the arrangement this
     *                   exists to stop
     */
    static Result of(Type declared, TermPath at, Symbols symbols, Set<TermPath> decided,
                     Requirements additional, HowManyItHolds howMany) {
        Requirements required = additional;
        for (TermPath fixed : decided) {
            switch (required.merge(fixed.requirements())) {
                case Requirements.Merge.Merged both -> required = both.requirements();
                case Requirements.Merge.Conflict against -> {
                    return new Result.Refused(new ModelRefusal.Conflict(
                            against.at(), against.one(), against.other()));
                }
            }
        }
        // A position said twice. A caller that fixed a value at a position and also requires a
        // narrowing there has stated the narrowing and fixed a value at it, which ADR-0114 keeps
        // apart -- the value stands at the narrowed position and is chosen there, so one location is
        // never decided twice under two names. Nothing a model writes reaches this: the classes that
        // narrow offer no value to fix, and the ones that offer a value narrow nothing. It is the
        // arrangement an optional's classes had before they said which narrowing they were, and it
        // is refused rather than answered by preferring one of the two.
        for (TermPath fixed : decided) {
            if (required.at(fixed) != null) {
                throw new IllegalStateException("`" + fixed + "` is fixed at a value and required to"
                        + " be " + required.at(fixed).spelled() + "; a narrowing states the"
                        + " narrowing and does not also fix a value there, so this is the caller"
                        + " keeping two accounts of one position");
            }
        }
        return switch (node(declared, at, symbols, 0, decided, required, howMany)) {
            case NodeResult.Made(Node root) -> new Result.Planned(new ConstructionPlan(root));
            case NodeResult.Refused(ModelRefusal why) -> new Result.Refused(why);
            case NodeResult.Beyond(Set<CompositionBudget> by) -> new Result.Beyond(by);
            case NodeResult.Unnarrowed(TermPath where, List<Refinement> narrowings) ->
                    new Result.Unnarrowed(where, narrowings);
        };
    }

    /**
     * How many the rules let the value at a position hold, read against what the caller has settled.
     *
     * <p>Asked rather than read here, because which rules those are is the caller's to know: a
     * position of a parameter is read against the record it sits in and the values fixed beside it,
     * and a position of a container being built to a total is read against the element's own type.
     * Handed over as one answer with both ends, so that the floor and the cap are of one state of
     * the row.
     */
    interface HowManyItHolds {

        /** What the rules let the value built at {@code at}, of type {@code building}, hold. */
        DeclaredBounds.CountRange at(TermPath at, Type building);
    }

    /**
     * One position of the walk, or the figure that put the caller's demand out of reach.
     *
     * <p>The second travels rather than being thrown or spelled as a null node. A position under a
     * figure is found part-way down and the answer belongs to the whole plan, so every step has to
     * be able to hand it up — and a walk that could only return a node would need a side channel to
     * do it, which is the same fact kept in two places.
     */
    private sealed interface NodeResult {

        /** The position, worked out. */
        record Made(Node node) implements NodeResult {}

        /** Nothing was, because the model settles that there is no value to build here or under
         *  here. */
        record Refused(ModelRefusal why) implements NodeResult {}

        /** Nothing was, because what the caller asked for is under a figure this reached. */
        record Beyond(Set<CompositionBudget> by) implements NodeResult {}

        /** Nothing was, because what the caller asked for is under a position nothing stands at
         *  until a narrowing says what does. */
        record Unnarrowed(TermPath at, List<Refinement> narrowings) implements NodeResult {}
    }

    /**
     * Every position the caller asked something of: the ones it stated a narrowing at, and the ones
     * it fixed a value at.
     *
     * <p><b>One reading, because the two halves are one demand.</b> A value fixed at {@code x.a.b}
     * adds no requirement that a field step was taken, so a reading of the requirements alone lets
     * it through and drops it in silence — and a reading of the fixed paths alone drops a narrowing
     * the caller stated. Both places that ask what stands below a position ask it here, so a third
     * kind of demand is answered for by both of them or by neither.
     *
     * <p>The narrowings first, which is the order the answers below are given in. A path is in one
     * of the two and never both: a caller that fixed a value at a position and also required a
     * narrowing there is refused where the requirements are put together.
     */
    private static List<TermPath> whatTheCallerAsked(Set<TermPath> decided, Requirements required) {
        List<TermPath> out = new ArrayList<>(required.refinements().keySet());
        out.addAll(decided);
        return out;
    }

    /**
     * Whether anything the caller asked for stands strictly under {@code here}.
     *
     * <p>Strictly under, because the position itself is one this does plan — a whole value is
     * chosen there, and a caller that fixed a value at it was answered before this was asked.
     */
    private static boolean anythingIsAskedUnder(TermPath here, Set<TermPath> decided,
                                                Requirements required) {
        return whatTheCallerAsked(decided, required).stream()
                .anyMatch(each -> !each.equals(here) && each.isAtOrUnder(here));
    }

    /** Every position a value is chosen at, in the order they are composed. */
    List<Slot> slots() {
        List<Slot> out = new ArrayList<>();
        collect(root, out);
        return List.copyOf(out);
    }

    private static void collect(Node node, List<Slot> out) {
        switch (node) {
            case Slot slot -> out.add(slot);
            case Built built -> built.under().values().forEach(each -> collect(each, out));
            case Held held -> collect(held.under(), out);
            // Not a position a value is chosen at: the requirement settled it, so there is nothing
            // here for the search to offer and nothing for it to be refused at.
            case Exact _ -> { }
        }
    }

    private static NodeResult node(Type declared, TermPath at, Symbols symbols, int depth,
                                   Set<TermPath> decided, Requirements required,
                                   HowManyItHolds howMany) {
        // What the requirements leave standing here, worked out before anything is decided about
        // the position. Read once and in full: what is built, whether the search chooses it, and
        // where every path below it hangs all follow from it.
        Settled settled = settle(declared, at, symbols, required);
        if (settled.exact() != null) {
            refuseWhatWouldStandUnder(settled.at(), decided, required);
            return new NodeResult.Made(
                    new Exact(settled.at(), settled.exact(), settled.outer()));
        }
        // A position the caller fixed takes the value it was given whatever would have been built
        // there. Asked at the position as the narrowings leave it, which is the name the caller
        // wrote it under: a value fixed under a case is fixed at the case.
        if (decided.contains(settled.at())) {
            return new NodeResult.Made(new Slot(settled.at(), settled.building(), settled.outer(),
                    new Leaf.Fixed()));
        }
        TermPath here = settled.at();
        Type building = settled.building();
        TypeView view = TypeView.of(building, symbols);
        // The names the position wore before the narrowings, and those with the narrowed type's own
        // after them — which is what a value composed here bare needs. Both are what the position
        // declares, kept: a value written under the narrowed type's names alone is of a type the
        // parameter does not declare.
        List<TypeSymbol> worn = settled.outer().isEmpty() ? view.wrappers()
                : outside(settled.outer(), view.wrappers());
        // A sequence with something to be placed inside it. Built out of its element rather than
        // chosen whole, since what is being asked for is a list holding a value in a class and no
        // proposal of a whole list can be asked to hold one.
        // Read here and handed on, so that one place decides both that the descent stops and which
        // figure stopped it. Named again where the answer is made, the two could part.
        CompositionBudget descent = CompositionBudget.DEPTH_A_CONSTRUCTION_PLAN_DESCENDS;
        boolean asDeepAsThisGoes = depth >= descent.maximum();
        if (view.shape() instanceof souther.compiler.check.Shape.Sequence sequence) {
            // Asked once, here, and handed to everything below that turns on it. What is built at
            // this position, whether the descent has anything to reach, and whether the rules leave
            // room for it are one question — is the caller asking for something inside this list —
            // and read twice they are free to come apart over one list.
            boolean demanded = anythingIsAskedUnder(here, decided, required);
            // How many it would have to hold, and how many it may, off one reading. The floor is
            // how many the rules ask the whole collection to hold and the value being placed is one
            // of them, so what a collection holding it comes to is the floor, or one where the
            // rules ask for none.
            DeclaredBounds.CountRange holds = demanded ? howMany.at(here, building) : null;
            int needed = demanded ? neededToHold(holds) : 0;
            // Asked before the figure and before the descent, because this is the model's answer
            // and those are this compiler's. A list the rules leave no room in holds nothing
            // however far this had read, so a figure named beside it sends an author to raise one
            // that changes nothing, and a figure named instead of it says this compiler did not
            // look, of a position it has the answer for.
            if (demanded && holds.most() < needed) {
                return new NodeResult.Refused(new ModelRefusal.NoRoom(here, holds));
            }
            // A sequence is planned by asking what stands at its element, so the figure is met here
            // rather than below: read as a shape nothing composes — which is what a sequence is to
            // the descent under this — a list this stopped short of looking into would be a
            // position the declarations put nothing under.
            if (asDeepAsThisGoes) {
                return givenUpAt(descent, here, building, settled.outer(), demanded);
            }
            NodeResult inside = node(sequence.element(), here.element(), symbols, depth + 1,
                    decided, required, howMany);
            if (!(inside instanceof NodeResult.Made(Node element))) {
                return inside;
            }
            if (demanded) {
                return new NodeResult.Made(new Held(here, building, worn, element, needed));
            }
            // Nothing was asked for inside it, so the list is chosen whole — but where the walk
            // that would have found something gave up part-way, that is not the same answer. Read
            // as "no class is placed in here", a plan that never reached the class says there is
            // none.
            Set<CompositionBudget> cutBy = cutBy(element);
            if (!cutBy.isEmpty()) {
                return new NodeResult.Made(new Slot(here, building, settled.outer(),
                        new Leaf.Beneath(cutBy)));
            }
        }
        ConstructionDescent.ProductBuild composed = ConstructionDescent.toBuild(view.shape());
        // A record with no fields composes nothing out of anything, so it is a value to be chosen
        // like any other and not a position made of positions. Asked before the figure, because
        // this is the declarations saying so — a position nothing composes is the same position
        // however deep it is, and answering the figure first would file the model's own answer as
        // something this compiler declined to do.
        if (composed == null || composed.fields().isEmpty()) {
            // A whole value is chosen here, so anything the caller asked for below it has nowhere
            // to be written. Said rather than dropped, for the reason a demand under the figure is:
            // a plan handed back all the same is one the composing satisfies while leaving the
            // caller's value out of what it built. What the two answers differ in is what would
            // change them — a figure is raised, and this is a narrowing the caller has yet to
            // state.
            if (anythingIsAskedUnder(here, decided, required)) {
                return new NodeResult.Unnarrowed(here, narrowingsAt(settled, symbols));
            }
            return new NodeResult.Made(
                    new Slot(here, building, settled.outer(), new Leaf.Open()));
        }
        if (asDeepAsThisGoes) {
            return givenUpAt(descent, here, building, settled.outer(),
                    anythingIsAskedUnder(here, decided, required));
        }
        SequencedMap<String, Node> under = new LinkedHashMap<>();
        Set<CompositionBudget> beyond = new LinkedHashSet<>();
        NodeResult.Unnarrowed owed = null;
        for (Map.Entry<String, Type> field : composed.fields().entrySet()) {
            switch (node(field.getValue(), here.then(field.getKey()), symbols, depth + 1, decided,
                    required, howMany)) {
                case NodeResult.Made(Node built) -> under.put(field.getKey(), built);
                // The model settling that there is no value ends the walk. Nothing a field further
                // along could say outranks it, and one proof is the whole of what a reader gets — a
                // second would name another position to look at where there is no row to look for.
                case NodeResult.Refused refused -> { return refused; }
                // Every field's, and not the first one's. Two fields whose demands are out of reach
                // are two figures a reader could raise, and a reader owed one of them is owed both;
                // taking whichever the walk met first would make what they are told turn on the
                // order the fields are declared in.
                case NodeResult.Beyond(Set<CompositionBudget> by) -> beyond.addAll(by);
                // One position, and the first of them. A narrowing is stated at a position rather
                // than gathered up, and the caller states one and asks again — so what it is owed
                // is somewhere to begin, and the field a second one is under may not even be
                // reached under the narrowing it settles on here.
                //
                // Kept rather than returned, because these two are not alike: a refusal is an
                // answer about the value, and this is a question put to whoever asked for it. A
                // caller sent to state a narrowing where a field further along leaves no row at all
                // has been sent to do work that cannot help, so the walk goes on to find out
                // whether there is one. Which narrowing is owed does not change — it is still the
                // first, in the order the declarations write them.
                case NodeResult.Unnarrowed unnarrowed -> {
                    if (owed == null) {
                        owed = unnarrowed;
                    }
                }
            }
        }
        if (owed != null) {
            return owed;
        }
        if (!beyond.isEmpty()) {
            return new NodeResult.Beyond(beyond);
        }
        return new NodeResult.Made(
                new Built(here, building, composed.constructor(), worn, under));
    }

    /**
     * What a position this stopped short of reading comes to.
     *
     * <p>Two answers and the demand is what parts them. Where nothing was asked for below, a whole
     * value chosen here is a value like any other and the plan goes on carrying that it was not
     * arrived at by looking; where something was, there is no position in the plan for it to be
     * written at, and a plan handed back would compose a row the caller's value is missing from.
     */
    private static NodeResult givenUpAt(CompositionBudget figure, TermPath here, Type building,
                                        List<TypeSymbol> outer, boolean demanded) {
        Set<CompositionBudget> by = Set.of(figure);
        return demanded ? new NodeResult.Beyond(by)
                : new NodeResult.Made(new Slot(here, building, outer, new Leaf.Beneath(by)));
    }

    /**
     * The narrowings that would put a position under {@code settled}, in the order its declarations
     * write them.
     *
     * <p>What a position divides into is asked of {@link Distinctions}, which is where that is
     * answered. A second reading here would be this deciding what a case is beside the reading that
     * decides what a class is, and the two would be free to differ about a case that is itself a
     * sum.
     *
     * <p><b>Those that leave something to be built, which is not every narrowing.</b> The absence of
     * an optional settles the value rather than narrowing to something with positions under it, so
     * stating it is not a way to reach what the caller asked for — it is the answer that there is
     * nothing there at all, and a caller that stated it would be asking for a value under a position
     * holding none. Asked by applying it, so this and {@link #applying} cannot come to differ about
     * which narrowings those are.
     *
     * <p>Empty where nothing here would put a position under this at all. A {@code Bool} divides
     * into two values and holds nothing under either, which says, correctly, that stating something
     * here is not what would make the demand reachable.
     */
    private static List<Refinement> narrowingsAt(Settled settled, Symbols symbols) {
        List<Refinement> out = new ArrayList<>();
        for (Case one : Distinctions.ofType(TypeView.of(settled.building(), symbols), symbols)) {
            Refinement narrowing = Refinement.of(one);
            if (narrowing != null
                    && applying(settled, narrowing, symbols).exact() == null) {
                out.add(narrowing);
            }
        }
        return List.copyOf(out);
    }

    /**
     * A position with every requirement standing at it applied.
     *
     * <p><b>Every one, because a narrowing takes no level.</b> A refinement does not move to another
     * position (ADR-0114), so a second may stand at the position the first left — an optional
     * holding a sum is narrowed to what it holds and then to the case that turned out to be there,
     * and `query.tag@Some@Tag` is one position with two narrowings at it. Read one at a time, the
     * plan built the sum and the case went missing, which is a value chosen from the wrong type
     * however carefully the first narrowing was applied.
     *
     * @param at       the position as the narrowings leave it, which is the name every path below it
     *                 hangs from and the name a caller that fixed a value here wrote it under
     * @param building what is to be built there, or null where {@code exact} settled it instead
     * @param exact    the value the narrowings settled, or null where they left something to build.
     *                 Exactly one of the two is null: a narrowing either says which values may stand
     *                 here or says which value does
     * @param outer    the names the position wore before the narrowings, which are what a value
     *                 arriving under the narrowed type is still missing
     */
    private record Settled(TermPath at, Type building, FixtureTemplate exact,
                           List<TypeSymbol> outer) {

        Settled {
            if ((building == null) == (exact == null)) {
                throw new IllegalArgumentException(
                        "a settled position either has something to build or is a value: "
                                + building + " / " + exact);
            }
            outer = List.copyOf(outer);
        }
    }

    /**
     * {@code declared} at {@code at}, with the requirements standing there applied in turn.
     *
     * <p>A fold and not a walk with a running total beside it. What one narrowing does is two
     * things at once — it changes what stands at the position, and it takes a name off that a value
     * chosen under the narrowed type is then missing — and they are one step. Kept as two locals
     * updated on their own, the second stopped after the first narrowing while the first went on:
     * a case that is a newtype over an optional narrowed to what the optional holds and the case's
     * own name was never put back, so the row carried a value of a type the parameter does not
     * declare. The state is a {@link Settled}, so a step cannot move one half and leave the other.
     */
    private static Settled settle(Type declared, TermPath at, Symbols symbols,
                                  Requirements required) {
        Settled settled = new Settled(at, declared, null, List.of());
        for (Refinement refinement = required.at(settled.at()); refinement != null;
                refinement = required.at(settled.at())) {
            settled = applying(settled, refinement, symbols);
            // Nothing narrows what is not there, so a narrowing that settled the value is the end
            // of the chain whatever else was written.
            if (settled.exact() != null) {
                return settled;
            }
        }
        return settled;
    }

    /**
     * One narrowing applied to a settled position: what stands there afterwards, and the name it
     * took off.
     *
     * <p>Both, here, because they are one step. What the position wears now is what a value chosen
     * under the narrowed type will be missing, and it is read from what stands here rather than
     * from the declaration — a narrowing may leave a position wearing a name the next one takes off
     * in turn, and the declaration wore neither.
     */
    private static Settled applying(Settled settled, Refinement refinement, Symbols symbols) {
        List<TypeSymbol> outer = outside(settled.outer(),
                TypeView.of(settled.building(), symbols).wrappers());
        TermPath here = settled.at().refine(refinement);
        if (refinement instanceof Refinement.Presence presence && !presence.present()) {
            // The absence of an optional settles the value rather than narrowing to something to be
            // built: `None` is a branch that puts no position anywhere (ADR-0114). Whether anything
            // was asked for under it is not this reading's question — what stands at a position and
            // what a caller demanded of it are two, and only the caller's side knows both halves of
            // the second.
            return new Settled(here, null, FixtureTemplate.none(), outer);
        }
        return new Settled(here, narrowed(settled.building(), refinement, symbols), null, outer);
    }

    /**
     * That nothing is asked for at or under a position an absence settled.
     *
     * <p>Asked of the whole demand and not of half of it. What a caller asks for is the paths it
     * fixed a value at and the requirements it stated, put together where the plan is made — so a
     * check that read only the requirements would pass a value fixed at {@code x@None.foo}, which a
     * field step adds no requirement for, and drop it in silence.
     *
     * <p>And at the position as well as under it. A refinement does not move to another position,
     * so a second narrowing of one an absence settled is written at that same path: leaving it out
     * as "not below" reads a rule about steps into a value as one about narrowings, which take
     * none. That is what {@link ModelRefusal.Conflict} is about at a position two narrowings disagree
     * over, and this is the other half of it — the narrowings agree, and there is nothing there for
     * the second to be about.
     *
     * <p>Nothing a model writes reaches either. The absence puts no position anywhere, so nothing
     * derives one to require or to fix. It is refused so that {@link Exact} means what it says: the
     * value is settled here, and there is nothing left below for anything to have asked of.
     */
    private static void refuseWhatWouldStandUnder(TermPath absent, Set<TermPath> decided,
                                                  Requirements required) {
        for (TermPath each : whatTheCallerAsked(decided, required)) {
            if (!each.isAtOrUnder(absent)) {
                continue;
            }
            Refinement stated = required.at(each);
            throw new IllegalStateException(stated != null
                    ? "`" + each + "` is required to be " + stated.spelled() + " at or under `"
                            + absent + "`, which holds no value; nothing stands there for a"
                            + " requirement to be about"
                    : "a value is fixed at `" + each + "`, at or under `" + absent + "`, which"
                            + " holds no value; nothing stands there for a value to be written at");
        }
    }

    /** {@code outer} and then {@code inner}, which is the order names are put back on a value. */
    private static List<TypeSymbol> outside(List<TypeSymbol> outer, List<TypeSymbol> inner) {
        List<TypeSymbol> out = new ArrayList<>(outer);
        out.addAll(inner);
        return List.copyOf(out);
    }

    /**
     * Every figure this compiler gave up reading at, in {@code inside} and everything under it.
     *
     * <p>Read off the plan that was built rather than off how the paths are written, because this
     * is about what the walk did and the paths say nothing about that. What the caller asked for is
     * a separate question, answered where the demand is
     * ({@link #anythingIsAskedUnder}) and once — a plan read back for it would be this compiler
     * working out from what it built what it had been told.
     */
    private static Set<CompositionBudget> cutBy(Node inside) {
        return switch (inside) {
            case Slot(TermPath _, Type _, List<TypeSymbol> _, Leaf leaf) -> switch (leaf) {
                case Leaf.Fixed _, Leaf.Open _ -> Set.of();
                case Leaf.Beneath(Set<CompositionBudget> cutBy) -> cutBy;
            };
            case Built built -> across(built.under().values());
            case Held held -> cutBy(held.under());
            case Exact _ -> Set.of();
        };
    }

    /** The same of several positions: given up at wherever any of them was. */
    private static Set<CompositionBudget> across(Collection<Node> nodes) {
        Set<CompositionBudget> out = new LinkedHashSet<>();
        for (Node each : nodes) {
            out.addAll(cutBy(each));
        }
        return out;
    }

    /**
     * Every figure this compiler gave up reading at, anywhere in the plan.
     *
     * <p>Derived from the positions rather than recorded beside them, so a plan whose leaves say
     * one thing and whose summary says another cannot be built. What it is for is the composing:
     * where a row was written against this plan there is nothing here anybody is owed, and where
     * one was not, this is the difference between the rules leaving nothing at the point and this
     * compiler not having looked.
     */
    Set<CompositionBudget> cutBy() {
        return cutBy(root);
    }

    /**
     * The type a value is built at once {@code refinement} has narrowed the position.
     *
     * <p>A narrowing of what stands at the position and not a rereading of the declaration. The
     * position's declared type is still the sum, and the axis still says so — a class of it saying
     * which case a witness takes is not the position becoming that case, and reading the two as one
     * would have a later reader believe the model declares something it does not. What moves is what
     * is being built.
     *
     * <p><b>Only the narrowings that leave something to build reach here.</b> The absence of an
     * optional is not one of them and is answered by {@link #settle}, where the position is settled
     * at a value instead. Answered here, it would have to come back as a type — the declared
     * optional, or what the optional holds — and either is a position built as something the
     * narrowing said is not there. So this returns a type and never null, and a narrowing that
     * settles a value is a case {@link #settle} has to name rather than one this can express.
     */
    private static Type narrowed(Type declared, Refinement refinement, Symbols symbols) {
        return switch (refinement) {
            case Refinement.SumCase one -> Type.ref(one.leaf());
            // What a `Some` leaves at the position is what the optional was declared to hold, which
            // is the same reading the branches under a position are made from
            // (`StructuralInspection.carried`).
            case Refinement.Presence presence -> presence.present() ? held(declared, symbols)
                    : illegal(declared);
        };
    }

    /** The absence reaching the one place that cannot express it, which is this compiler
     *  contradicting itself rather than anything a model can write. */
    private static Type illegal(Type declared) {
        throw new IllegalStateException("`" + Type.show(declared) + "` is asked for the type an"
                + " absence narrows it to; an absence settles the value and narrows no type, so this"
                + " is the plan reading a settled position as one still to be built");
    }

    /**
     * What the optional at this position holds.
     *
     * <p>Asked of the shape rather than of the written type, since the position may wear names: a
     * {@code data MaybeTagN = Tag?} is an optional and holds a {@code Tag}.
     */
    private static Type held(Type declared, Symbols symbols) {
        if (TypeView.of(declared, symbols).shape()
                instanceof souther.compiler.check.Shape.Optional optional) {
            return optional.element();
        }
        // The requirement and the declaration disagreeing about the position's shape, which is this
        // compiler contradicting itself rather than anything a model can write.
        throw new IllegalStateException("`" + Type.show(declared) + "` is asked for what it holds,"
                + " and it is not an optional; the reading of a position and the plan built from it"
                + " disagree about its shape");
    }
}
