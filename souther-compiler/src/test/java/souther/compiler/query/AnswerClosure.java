package souther.compiler.query;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What is known to sit in an answer and not mean anything by {@code equals}, and who sees it.
 *
 * <p>One register for both detectors. They see different things and both are needed — one walks a
 * store and asks each object what it is, the other holds two stores side by side and asks where they
 * come apart — but what a finding <em>is</em>, and which of the two ways out it takes, is one
 * judgement and is made here. Written twice, the two lists drift into two vocabularies for one fact.
 *
 * <p><b>A place and not a class.</b> An entry names the question, where in that answer the thing
 * sits, and what it is. Keyed by the class alone, a class already written down here turning up under
 * some other answer would be waved through as known; keyed by the place, it is a new place the debt
 * has reached and says so.
 *
 * <p><b>Two ways out and no third.</b> {@link Meaning} is what a thing is, and there are two of
 * them: something that says what it is, with what is wrong with how it says it, or something that
 * does something. A word for a thing nobody has read yet would be a third, and it is what let a
 * place stand here twice with no remedy attached — so there is none, and a walk meeting something
 * unread fails until somebody says which of the two it is.
 *
 * <p><b>Where it was seen is not what it is.</b> A finding is one finding however many detectors and
 * however many scenarios meet it, so the detector and the scenario are provenance beside the entry
 * rather than part of what it names. Held inside the identity, one thing seen twice would read as
 * two things — the same mistake as keying by class, from the other side.
 */
final class AnswerClosure {

    /**
     * What a thing is, which is the whole of the reading and takes one of two forms.
     *
     * <p>Written as one thing rather than as a kind beside a fault. What says a thing is a value is
     * the same reading that says what is wrong with it as it stands, so the two were never apart:
     * held as two words, a line could say a thing is a value and a way of asking at once, and a line
     * could say the reading got as far as the kind and stopped. Neither is a state anybody can act
     * on, and the second is what let a place stand here with no remedy attached.
     *
     * <p>So there is no word for an unread one. A thing nobody has read yet is not written down, and
     * the walk that meets it fails until somebody says which of these it is.
     */
    sealed interface Meaning {

        /** Something that says what it is, and what is wrong with how it says it. */
        record Value(ValueProblem problem) implements Meaning {}

        /** Something that does something, or that carries something that does. What such a thing is
         *  worth to whoever holds it turns on where it is held. */
        record Capability() implements Meaning {}
    }

    /** What is wrong with how a value says what it is. */
    enum ValueProblem {
        /** It defines no equality over what it says. */
        MISSING_EQUALITY,
        /**
         * Its equality is an address — its own, or its members'.
         *
         * <p>Its own word beside the one above because the work is different. Something with no
         * equality wants one written; something whose equality is an address has one already and it
         * answers the wrong question, so what it wants is to be held in something that means what it
         * says. Filed under the first, the second sends whoever picks it up to add a method beside
         * one that is already there.
         */
        IDENTITY_SEMANTICS
    }

    /**
     * What a declaration leaves open, which is not a reading of a thing and does not pretend to be
     * one.
     *
     * <p>{@link Meaning} is what something <em>is</em>, and reaching it takes a declaration that
     * settles what may stand somewhere. Where the declaration settles nothing, saying the thing is a
     * value would be claiming what the declaration does not give — the same slot admits a way of
     * asking, and that everything standing there today happens to be a value is exactly the fact
     * nothing is holding.
     *
     * <p>So it is a kind of failure of its own, and the remedy is a declaration rather than a
     * method. Which is what keeps it from being the word for an unread thing: this is read, and what
     * was read is that the type is open.
     */
    enum DeclarationProblem {
        /** A member declared as something anything is, so nothing says what may stand there. */
        OPEN_VALUE_SLOT
    }

    /**
     * What was read at a place: what the thing there is, or what its declaration leaves open.
     *
     * <p>Not what to do about it. What a thing is is one reading however many places hold it, and
     * what it obliges anybody to do turns on where it stands — a way of asking is a defect under a
     * question this compiler computes and is what somebody handed in under one it is given. Held as
     * a fault, the reading would be saying the second is owed work, which the contract says it is
     * not.
     */
    private sealed interface WhatItIs {

        /** The thing was read and this is what it is. */
        record OfTheThing(Meaning meaning) implements WhatItIs {}

        /** The declaration was read and it settles nothing here, which is owed work wherever it
         *  stands. */
        record OfTheDeclaration(DeclarationProblem problem) implements WhatItIs {}
    }

    /** Something that says what it is and defines no equality over that. */
    private static final WhatItIs MISSING_EQUALITY =
            new WhatItIs.OfTheThing(new Meaning.Value(ValueProblem.MISSING_EQUALITY));

    /** A way of asking something rather than an answer, so it is one object per store whatever it
     *  is compared with. */
    private static final WhatItIs CAPABILITY = new WhatItIs.OfTheThing(new Meaning.Capability());

    /** A place the declarations leave open, so no walk of them can say which of the two it is. */
    private static final WhatItIs AN_OPEN_SLOT =
            new WhatItIs.OfTheDeclaration(DeclarationProblem.OPEN_VALUE_SLOT);

    /** What one of these is, in the words a reader of a failure gets. */
    private static String said(WhatItIs whatItIs) {
        return switch (whatItIs) {
            case WhatItIs.OfTheThing(Meaning.Value(ValueProblem problem)) -> "VALUE/" + problem;
            case WhatItIs.OfTheThing(Meaning.Capability _) -> "CAPABILITY";
            case WhatItIs.OfTheDeclaration(DeclarationProblem problem) -> problem.toString();
        };
    }

    /** Which walk met it. */
    enum Detector {
        /** One store walked, each object asked what it is. {@link AnswerWalk}. */
        ONE_ANSWER_WALKED,
        /** Two stores over one input compared. {@link Divergence}. */
        TWO_ANSWERS_COMPARED
    }

    /** What was compiled for it to be met in. */
    enum Scenario {
        /** The conformance corpora, analysed with everything measured. Not all of them hold up:
         *  one is a model on the way there, so what a compile says on the way to an answer is
         *  reached here as well as in the scenario beside it. */
        THE_CORPORA,
        /** A module the compiler has something to say about, held to one mistake. What it is for is
         *  a stimulus of its own: a difference between the two walks over one input is a difference
         *  of walk, and over one input alone there is nothing to say it is not the input's. */
        A_MODULE_SPOKEN_ABOUT
    }

    /** One detector meeting one place in one scenario. */
    record Observation(Detector detector, Scenario scenario) implements Comparable<Observation> {

        @Override
        public int compareTo(Observation other) {
            int by = detector.compareTo(other.detector);
            return by != 0 ? by : scenario.compareTo(other.scenario);
        }

        @Override
        public String toString() {
            return detector + "/" + scenario;
        }
    }

    /**
     * Where a thing stands, which is what its meaning obliges anybody to do about it.
     *
     * <p>The same reading is read two ways. A way of asking answered with by a question the compiler
     * computes is a defect and leaves the answer; the same thing handed in by whoever built the
     * compilation is what they handed in, and there is no compute to build it inside. What is left
     * of it there is that the store takes a new one for a change to the outside, which is a
     * conservative answer to a question about the outside rather than a wrong answer to a question
     * about a compile.
     *
     * <p>Read off the question and never written down beside it. Which of these a question is is
     * said by what it implements, and a register that repeated it would be a second answer to
     * something already settled.
     */
    enum Where {
        /** Under a question the compiler computes, where what is answered with is what the compile
         *  came to. */
        IN_A_DERIVED_ANSWER,
        /** Under a question the compilation is given, where what is answered with is what somebody
         *  handed in and its equality is how the store hears that the outside changed. */
        IN_A_SUPPLIED_INPUT,
        /** In the half of an answer every question has, so which question said anything is the
         *  scenario's business and not this place's. */
        IN_EVERY_ANSWER
    }

    /** The question that is every question, for a place in the half of an answer they all have. */
    private static final String EVERY_ANSWER = "*";

    /** Where the thing at {@code question} stands, asked of the question itself. */
    static Where whereItStands(String question) {
        if (EVERY_ANSWER.equals(question)) {
            return Where.IN_EVERY_ANSWER;
        }
        Class<?> asks;
        try {
            asks = Class.forName(question, false, Key.class.getClassLoader());
        } catch (ClassNotFoundException none) {
            throw new IllegalStateException(
                    "no question of this compiler is called " + question, none);
        }
        return Input.class.isAssignableFrom(asks)
                ? Where.IN_A_SUPPLIED_INPUT : Where.IN_A_DERIVED_ANSWER;
    }

    /**
     * One reading, named once and pointed at by every place it is met at.
     *
     * <p>What a thing is is one reading however many places hold it. Written per place, the four
     * ends of two ranges would be four readings of one class, and acting on it would be four lines
     * to strike with four chances to leave one saying something that is no longer so.
     *
     * <p>Whether it is owed work is not here. That is read from this and from where the place
     * stands ({@link Where}), and the two are apart because they move apart: a way of asking is a
     * defect under a question this compiler computes and is what a caller handed in under one it is
     * given, and it is the same reading either way.
     *
     * @param named what this reading is called where a failure is read
     */
    private record Reading(String named, WhatItIs whatItIs, String reason) {}

    /** One place, what is wrong with what is there, and who met it. */
    private record Known(Locus.Place place, Reading reading, Set<Observation> seenBy) {}

    private static Observation walked(Scenario scenario) {
        return new Observation(Detector.ONE_ANSWER_WALKED, scenario);
    }

    private static Observation compared(Scenario scenario) {
        return new Observation(Detector.TWO_ANSWERS_COMPARED, scenario);
    }

    /** The library this compiler ships, reached wherever a compilation holds it. */
    private static final Reading STDLIB = new Reading("STDLIB", MISSING_EQUALITY,
            "a value, and here for a reason the others are not: one is built per process and every "
                    + "answer of a compilation holds that one, so identity is the answer structural "
                    + "equality would give. Writing that equality out would walk every declaration "
                    + "the library has on every comparison, and writing \"any library equals any "
                    + "other\" would be true only while there is one of them");

    /**
     * An index onto the very objects the answer holding it is made of.
     *
     * <p>Filed by which objects were put in it, so it answers for one graph and returns nothing for
     * anybody else's. That is what it is for — a number addresses a place in a tree, and two checks
     * of one source build trees a record compares as equal, so a value-keyed index would answer for
     * either with the same confidence and be right about one of them.
     *
     * <p>Permitted where it is because its life is that graph's life. Carried into another derived
     * answer the two would be kept apart by the store — one answer recomputed while the other is
     * held — and the index would go on addressing objects nobody is emitting, which no reader of it
     * can see. So this is registered at one place and every further one is a finding.
     *
     * <p>What outlives the graph is {@link souther.compiler.coverage.NumberingIdentity}, which is a
     * value: the same places under the same numbers over the same executable, and two builds of one
     * module come to one. The equality of the answer above rests on that and never on this.
     */
    private static final Reading AN_INDEX_ONTO_THE_ANSWERS_OWN_GRAPH =
            new Reading("AN_INDEX_ONTO_THE_ANSWERS_OWN_GRAPH", MISSING_EQUALITY,
                    "a plan is filed by which Core objects were put in it, so it is worth what the "
                            + "graph the answer holds is worth and says nothing about an equal one. "
                            + "Comparing it would deny every check its own recomputation; what the "
                            + "answer is compared by is what the plan is a numbering of, which is a "
                            + "value");

    /**
     * Where a reading of a declaration borrows what has already been made of it.
     *
     * <p>Held by a reading of an input so that a later reader of the same declarations is not made
     * to read them again: what a search asks about the record a parameter is is asked long after
     * the walk that read it, and asked with nothing to borrow it pays every reading over.
     *
     * <p>Two of them built from one store answer alike and compare unlike, so the answers holding
     * one are compared without it — a reading taken again would otherwise come back as another
     * reading and put every measure through again. What it lends is dropped when the revision moves
     * and it is the store's own, so an answer kept across one hands out nothing of the world it was
     * read from.
     */
    private static final Reading A_LENDING_OF_READINGS =
            new Reading("A_LENDING_OF_READINGS", CAPABILITY,
                    "where a reading of a declaration borrows what somebody has already made of it,"
                            + " which is a way of asking the store rather than an answer, and never"
                            + " equals the same way of asking built again");

    /** How a module is found, which is something run rather than something said. */
    private static final Reading MODULE_PATH = new Reading("MODULE_PATH", CAPABILITY,
            "a module path resolves a module by running something, and a function never equals the "
                    + "same function computed again");

    /**
     * A way of asking the modules around this one, reached through a scope that was taken apart
     * before it was used.
     *
     * <p>Met at two depths and it is one thing. What the declarations say stops at the way of
     * asking, which nothing closes; a walk of what was built goes on through it to the store it
     * holds. Neither is a defect the other does not have.
     */
    private static final Reading A_STORE = new Reading("A_STORE", CAPABILITY,
            "Scoping.Scoped carries a way of asking the modules around this one a further question, "
                    + "and it holds this store to ask with. Where a scope has been taken apart "
                    + "already, that is the half of the assembly nobody has yet — it belongs inside "
                    + "the compute that asks");

    /** What a generation is asked for on behalf of, carrying what it takes to go on asking. */
    private static final Reading GENERATION_READERS = new Reading("GENERATION_READERS", CAPABILITY,
            "the subject a row would be written for carries the means to ask further questions — "
                    + "the symbols a name is read against, where a declaration's expanded clauses "
                    + "are answered from, and the reading a quantity over several positions is "
                    + "asked of. Each is built where it is used and none of them is what a plan "
                    + "says, so what belongs in the subject is what the row is about");

    /**
     * What was raised where a name resolved to nothing.
     *
     * <p><b>Two things are wrong here and the walk meets them one at a time.</b> It says nothing
     * about itself, and anything may extend what is thrown — and the second is behind the first,
     * because a walk that has been told nothing by a thing has no reason to ask what else could
     * stand there. So writing the equality moves this place from one word to the other rather than
     * striking it off, and the day it moves is the day the register says so.
     */
    private static final Reading AN_EXCEPTION = new Reading("AN_EXCEPTION", MISSING_EQUALITY,
            "a name nothing resolved is answered with what was raised where it was read. What it "
                    + "says is where the name is and what is wrong with it, and two compiles that "
                    + "found the same thing found the same thing — so that is what it wants an "
                    + "equality over, and being one object per raise is not it. An equality alone "
                    + "would not finish it: anything may extend what is thrown, so what stands "
                    + "here is settled by what a raise happens to be of");

    /** What a term holds beside its parts, declared as anything at all. */
    private static final Reading A_TERM_HOLDS_ANYTHING =
            new Reading("A_TERM_HOLDS_ANYTHING", AN_OPEN_SLOT,
                    "what a shape holds beside its parts is a location, a written value, an "
                            + "operator, the name of an operation, or the type and fields of a "
                            + "construction — six things, written as one that admits anything. Each "
                            + "of them compares by its own equality and the term above them rests "
                            + "on that, which is a sum nobody has written down: until it is, what "
                            + "may stand here is settled by whatever happens to be put there");

    /** The machine under a language, which is one way of holding the strings and not the strings. */
    private static final Reading A_MACHINE_UNDER_A_LANGUAGE =
            new Reading("A_MACHINE_UNDER_A_LANGUAGE", MISSING_EQUALITY,
                    "the states a set of strings is walked between. A value, and one nothing "
                            + "compares on its own: two machines built from patterns written "
                            + "differently accept the same strings and are the same set, so the "
                            + "language above it writes its own equality and asks the strings. "
                            + "Comparing the states instead would answer that two spellings of one "
                            + "rule are two sets, which is the answer a reading that came to it by "
                            + "another route would then disagree with");

    /** One end of what a reading leaves a position. */
    private static final Reading NARROWED_END = new Reading("NARROWED_END", MISSING_EQUALITY,
            "one end of what a reading leaves a position, with what is holding it. A value, and one "
                    + "nothing compares on its own: the range it sits in writes its own equality "
                    + "and reaches both ends through it");

    /** What a compile said on the way to an answer. */
    private static final Reading A_REPORT = new Reading("A_REPORT", MISSING_EQUALITY,
            "a report says what this compile found, and two compiles that found the same thing "
                    + "found the same thing — so what it wants is equality over what it says. "
                    + "Reached because an answer is its value and its reports together, which is "
                    + "what the store compares to stop work. A walk of a store meets it without a "
                    + "question, because the reports are the half every answer has and which one "
                    + "happened to speak is the scenario's business; a walk of the declarations "
                    + "meets it under whichever question declares reports of its own");

    private static final Set<Observation> BOTH_EVERYWHERE = Set.of(
            walked(Scenario.THE_CORPORA), compared(Scenario.THE_CORPORA),
            walked(Scenario.A_MODULE_SPOKEN_ABOUT), compared(Scenario.A_MODULE_SPOKEN_ABOUT));

    private static final Set<Observation> ONLY_WALKED = Set.of(
            walked(Scenario.THE_CORPORA), walked(Scenario.A_MODULE_SPOKEN_ABOUT));

    /** A place, written the way a walk writes one. */
    private static Locus.Place at(String question, String offender, Locus.Step... steps) {
        return new Locus(List.of(steps)).of(question, offender);
    }

    /** A member step, by what the declaring type is called rather than what it is shown as. Two
     *  types of one short name are two types, and a place is told from a place by the first. */
    private static Locus.Step m(String owner, String name) {
        return new Locus.Step.Member(owner, name);
    }

    private static final String ANSWER = "souther.compiler.query.Answer";

    private static final Locus.Step ELEMENT = new Locus.Step.Element();
    private static final Locus.Step VALUE = new Locus.Step.MapValue();

    /**
     * The end itself, on the way to what is holding it.
     *
     * <p>Under the range and never beside it: one of these exists only where there is an end, and
     * the two of them are which side of the range they sit in. So the answer to comparing two is
     * {@code NarrowedBounds}'s own, which is written out and does compare the ends and the names —
     * a reader never reaches this one, and what it is missing is never the answer anybody gets.
     *
     * <p>Reached at all because the walk asks each object what it is rather than asking whoever
     * holds it. Which is the point of asking that way: the day something takes one of these out
     * from under the range that answers for it, this place is what says so.
     */
    private static Known narrowedEnd(String question, Observation met, Locus.Step... steps) {
        return new Known(at(question, "souther.compiler.check.NarrowedEnd", steps), NARROWED_END,
                Set.of(met));
    }

    /**
     * The machine under the language a class of a measure holds, on the way down to it.
     *
     * <p>Reached wherever a body's rules divide a position into sets of its strings: the class
     * means that set, the set is the language, and the language is walked as one machine. Under the
     * names the position wears, because that is how a class of such a position is written down.
     *
     * <p>{@code steps} is the way to the measures of the question this is under, and the rest of
     * the way is the same wherever it is reached from — a class is a class, and what hangs under
     * one does not turn on which question asked for the measure it is in.
     */
    private static Known machineInAClass(String question, Observation met, Locus.Step... steps) {
        List<Locus.Step> way = new java.util.ArrayList<>(List.of(steps));
        way.addAll(List.of(
                m("souther.compiler.partition.Partitions$Partitioning", "measurements"), ELEMENT,
                m("souther.compiler.partition.PositionMeasurements", "axes"), ELEMENT,
                m("souther.compiler.partition.Axis", "classes"), ELEMENT,
                m("souther.compiler.partition.PartitionClass", "recognises"),
                m("souther.compiler.partition.Recognition$Under", "inner"),
                m("souther.compiler.partition.Recognition$OfASet", "values"),
                m("souther.compiler.values.ValueSet$Matching", "language"),
                m("souther.compiler.regex.Language", "machine")));
        return new Known(at(question, "souther.compiler.regex.Automaton",
                way.toArray(new Locus.Step[0])), A_MACHINE_UNDER_A_LANGUAGE, Set.of(met));
    }

    /**
     * The machine under a language, wherever the walk arrives at one.
     *
     * <p>{@code steps} is the way to the language; what hangs under it is the same wherever it is
     * reached from, because a language is a language and what it is walked as does not turn on
     * which answer happened to hold it.
     */
    private static Known machineUnderALanguage(String question, Observation met,
                                               Locus.Step... steps) {
        List<Locus.Step> way = new java.util.ArrayList<>(List.of(steps));
        way.addAll(List.of(
                m("souther.compiler.values.ValueSet$Matching", "language"),
                m("souther.compiler.regex.Language", "machine")));
        return new Known(at(question, "souther.compiler.regex.Automaton",
                way.toArray(new Locus.Step[0])), A_MACHINE_UNDER_A_LANGUAGE, Set.of(met));
    }

    private static final String Q = "souther.compiler.query.";

    /** What the reading of a body left of the model's own divisions, which is the half of it the
     *  measurement asks for. */
    private static final TypePath.Step THE_GEOMETRY_THE_READING_LEFT =
            part(Q + "Adequacy$BodyDivided", "geometry");

    private static final List<Known> KNOWN = List.of(
            new Known(at(Q + "Names$ModuleScope", Q + "Db",
                    m(ANSWER, "value"), m("souther.compiler.check.Scoping$Scoped", "values"), m("souther.compiler.check.Resolve$Values", "elsewhere"),
                    m("souther.compiler.check.Scoping$OfTheUniverse", "universe"), m("souther.compiler.query.CompilationUniverse", "db")),
                    A_STORE, BOTH_EVERYWHERE),
            // A function is not compared, so two of them never come apart under a walk that holds
            // one against another; only the walk that asks each object what it is meets this.
            new Known(at(Q + "Front$Path", "souther.compiler.meta.ModulePath$$Lambda",
                    m(ANSWER, "value")), MODULE_PATH, ONLY_WALKED),
            new Known(at(Q + "Front$Library", "souther.compiler.stdlib.Stdlib",
                    m(ANSWER, "value")), STDLIB, ONLY_WALKED),
            // Where a module's places are, on the answer that walked its bodies for them. Only the
            // walk that asks each object what it is meets this: what the check answers with is
            // compared without it, and two answers that hold equal bodies come out equal, so the
            // pair walk never reaches here to find two indexes into two graphs.
            new Known(at(Q + "Bodies$Checked", "souther.compiler.coverage.CoverageSites$Plan",
                    m(ANSWER, "value"), m(Q + "Bodies$Elaborated", "plan")),
                    AN_INDEX_ONTO_THE_ANSWERS_OWN_GRAPH, ONLY_WALKED),
            new Known(at(Q + "Bodies$Expanding", "souther.compiler.stdlib.Stdlib",
                    m(ANSWER, "value"), m("souther.compiler.query.Bodies$Expanding$Of", "table"), m("souther.compiler.check.HelperTable", "stdlib")),
                    STDLIB, ONLY_WALKED),
            // What the declarations a reading of an input reached have already been made into,
            // borrowed by the reading for the readers of those declarations that come after it. The
            // walk of the declarations stops at the way of asking, which nothing closes; this is
            // the object a compile put there, which is the same allowance one step further down.
            new Known(at(Q + "Adequacy$Inputs", "souther.compiler.check.LentReadings",
                    m(ANSWER, "value"), VALUE,
                    m("souther.compiler.inputs.InputDomain", "machines")),
                    A_LENDING_OF_READINGS, ONLY_WALKED),
            narrowedEnd(Q + "Adequacy$Inputs", walked(Scenario.THE_CORPORA),
                    m(ANSWER, "value"), VALUE,
                    m("souther.compiler.inputs.InputDomain", "byPath"), VALUE,
                    m("souther.compiler.inputs.ReadPosition", "bounds"), ELEMENT,
                    m("souther.compiler.inputs.PositionBounds", "narrowedEnds"),
                    m("souther.compiler.check.NarrowedBounds$Reading", "lower")),
            narrowedEnd(Q + "Adequacy$Inputs", walked(Scenario.THE_CORPORA),
                    m(ANSWER, "value"), VALUE,
                    m("souther.compiler.inputs.InputDomain", "positions"), ELEMENT,
                    m("souther.compiler.inputs.ReadPosition", "bounds"), ELEMENT,
                    m("souther.compiler.inputs.PositionBounds", "narrowedEnds"),
                    m("souther.compiler.check.NarrowedBounds$Reading", "lower")),
            // The partition's own copy. An axis carries what the reading left the position rather
            // than the names it came to, so that a border can ask whether they are about the end it
            // has — and the walk that asks each object what it is meets the end on the way.
            narrowedEnd(Q + "Adequacy$Divided", walked(Scenario.THE_CORPORA),
                    m(ANSWER, "value"),
                    m("souther.compiler.partition.Partitions$Partitioning", "measurements"),
                    ELEMENT, m("souther.compiler.partition.PositionMeasurements", "axes"), ELEMENT,
                    m("souther.compiler.partition.Axis", "narrowed"),
                    m("souther.compiler.check.NarrowedBounds$Reading", "lower")),
            // And the same end again through the reading the geometry is a projection of. Two
            // questions are asked of one reading of a body — what the model divides, and where that
            // reading met each condition it places itself — so a walk arrives at everything the
            // geometry holds by both names. One thing to fix, met twice.
            narrowedEnd(Q + "Adequacy$Dividing", walked(Scenario.THE_CORPORA),
                    m(ANSWER, "value"), m(Q + "Adequacy$BodyDivided", "geometry"),
                    m("souther.compiler.partition.Partitions$Partitioning", "measurements"),
                    ELEMENT, m("souther.compiler.partition.PositionMeasurements", "axes"), ELEMENT,
                    m("souther.compiler.partition.Axis", "narrowed"),
                    m("souther.compiler.check.NarrowedBounds$Reading", "lower")),
            // And the machines the classes of those measures hold, by both names the reading a
            // body's geometry comes off answers to.
            machineInAClass(Q + "Adequacy$Divided", walked(Scenario.THE_CORPORA),
                    m(ANSWER, "value")),
            machineInAClass(Q + "Adequacy$Dividing", walked(Scenario.THE_CORPORA),
                    m(ANSWER, "value"), m(Q + "Adequacy$BodyDivided", "geometry")),
            // What a position admits, which is a language wherever a rule about a number taken of
            // it leaves a run of its values. Held twice by the reading that found it — in the order
            // the positions were read, and under the paths they were read at — and again by the
            // account each measure carries of the location it is of.
            machineUnderALanguage(Q + "Adequacy$Inputs", walked(Scenario.THE_CORPORA),
                    m(ANSWER, "value"), VALUE,
                    m("souther.compiler.inputs.InputDomain", "positions"), ELEMENT,
                    m("souther.compiler.inputs.ReadPosition", "admitted"),
                    m("souther.compiler.values.AdmissibleSet", "approximation")),
            machineUnderALanguage(Q + "Adequacy$Inputs", walked(Scenario.THE_CORPORA),
                    m(ANSWER, "value"), VALUE,
                    m("souther.compiler.inputs.InputDomain", "byPath"), VALUE,
                    m("souther.compiler.inputs.ReadPosition", "admitted"),
                    m("souther.compiler.values.AdmissibleSet", "approximation")),
            machineUnderALanguage(Q + "Adequacy$Divided", walked(Scenario.THE_CORPORA),
                    m(ANSWER, "value"),
                    m("souther.compiler.partition.Partitions$Partitioning", "measurements"),
                    ELEMENT, m("souther.compiler.partition.PositionMeasurements", "position"),
                    m("souther.compiler.partition.PositionAccount", "admits")),
            machineUnderALanguage(Q + "Adequacy$Dividing", walked(Scenario.THE_CORPORA),
                    m(ANSWER, "value"), m(Q + "Adequacy$BodyDivided", "geometry"),
                    m("souther.compiler.partition.Partitions$Partitioning", "measurements"),
                    ELEMENT, m("souther.compiler.partition.PositionMeasurements", "position"),
                    m("souther.compiler.partition.PositionAccount", "admits")),
            new Known(at(EVERY_ANSWER, "souther.compiler.diag.Diagnostic",
                    m(ANSWER, "reports"), ELEMENT, m("souther.compiler.query.Report", "diagnostic")),
                    A_REPORT, BOTH_EVERYWHERE));

    /**
     * One place, what is wrong with what is there, and what the walk of the declarations stopped on.
     *
     * <p>The third is the walk's own word and is held to like everything else. What a thing is is a
     * judgement somebody made; where the declarations run out is what a walk saw, and the day a
     * place starts failing for another reason is the day something about the declaration moved
     * under a judgement that still reads as if it had not.
     */
    private record KnownDeclared(TypePath.Place place, Reading reading,
                                 Traversal.Why why) {}

    /** A place, written the way the walk of declarations writes one. */
    private static TypePath.Place declared(String question, String offender,
                                           TypePath.Step... steps) {
        return new TypePath(List.of(steps)).of(question, offender);
    }

    /** A member step, by what the declaring type is called. */
    private static TypePath.Step part(String owner, String name) {
        return new TypePath.Step.Member(owner, name);
    }

    /** What a container the JDK declares was written to hold. */
    private static final TypePath.Step HELD = new TypePath.Step.Argument("held");
    private static final TypePath.Step MAP_VALUE = new TypePath.Step.Argument("value");
    private static final TypePath.Step MAP_KEY = new TypePath.Step.Argument("key");

    /** One arm of a sum, which a walk of types takes all of. */
    private static TypePath.Step arm(String named) {
        return new TypePath.Step.Arm(named);
    }

    /** One way down, and then another. */
    private static TypePath.Step[] then(TypePath.Step[] first, TypePath.Step... rest) {
        List<TypePath.Step> out = new java.util.ArrayList<>(List.of(first));
        out.addAll(List.of(rest));
        return out.toArray(new TypePath.Step[0]);
    }

    /** Down to what a generation is asked for on behalf of. */
    private static final TypePath.Step[] A_SUBJECT = {
            part(Q + "Adequacy$Filling", "composed"),
            part("souther.compiler.partition.FillResult", "plan"),
            part("souther.compiler.partition.GenerationPlan", "subject")};

    /** The measurement a subject holds, down to the axes of one position. A subject is a reading
     *  and the whole of what was measured against it, so an axis is reached through the
     *  measurement rather than off a list beside it. */
    private static final TypePath.Step[] A_MEASUREMENT = {
            part("souther.compiler.partition.MeasuredInput", "divided"),
            part("souther.compiler.partition.Partitions$Partitioning", "measurements"),
            HELD,
            part("souther.compiler.partition.PositionMeasurements", "axes")};

    /** What a generation's subject carries to go on asking with, under the plan that holds it. */
    private static KnownDeclared generationReader(String offender, TypePath.Step... under) {
        return generationReader(offender, Traversal.Why.SAYS_NOTHING_OF_ITSELF, under);
    }

    /** The same, where the walk stops for another reason — a capability is closed by nothing at all,
     *  where a carrier that says nothing of itself could have said something. */
    private static KnownDeclared generationReader(String offender, Traversal.Why why,
                                                  TypePath.Step... under) {
        return new KnownDeclared(
                declared(Q + "Adequacy$Generated", offender, then(A_SUBJECT, under)),
                GENERATION_READERS, why);
    }

    /** The machine a language is held as, under the class that denotes a pattern's strings. */
    private static void theMachineUnderALanguage(List<KnownDeclared> into, String question,
                                                 TypePath.Step... under) {
        into.add(new KnownDeclared(declared(question, "souther.compiler.regex.Automaton",
                then(under, arm("souther.compiler.values.ValueSet$Matching"),
                        part("souther.compiler.values.ValueSet$Matching", "language"),
                        part("souther.compiler.regex.Language", "machine"))),
                A_MACHINE_UNDER_A_LANGUAGE, Traversal.Why.SAYS_NOTHING_OF_ITSELF));
    }

    /**
     * Both ends of one range, which are one thing to fix met twice.
     *
     * <p>A range has a lower end and an upper one and they are of a type. Written as one place, the
     * register would say which of the two the walk happens to reach first.
     */
    private static void bothEndsOfARange(List<KnownDeclared> into, String question,
                                         TypePath.Step... under) {
        for (String end : List.of("lower", "upper")) {
            into.add(new KnownDeclared(declared(question, "souther.compiler.check.NarrowedEnd",
                    then(under, arm("souther.compiler.check.NarrowedBounds$Reading"),
                            part("souther.compiler.check.NarrowedBounds$Reading", end))),
                    NARROWED_END, Traversal.Why.SAYS_NOTHING_OF_ITSELF));
        }
    }

    /**
     * What a term holds beside its parts, reached through each way a projection falls short.
     *
     * <p>Every cause a projection reports names the atom it is about, and the atom is a term. So
     * this is one member of one type, met once under each of them.
     */
    private static void whatATermHolds(List<KnownDeclared> into, TypePath.Step... under) {
        for (String cause : List.of("Lossy", "Rounded")) {
            String owner = "souther.compiler.check.ProjectionEvidence$Cause$" + cause;
            into.add(new KnownDeclared(declared(Q + "Adequacy$Inputs", "java.lang.Object",
                    then(under,
                            part("souther.compiler.inputs.ReadPosition", "projection"),
                            arm("souther.compiler.check.ProjectionEvidence$NotCertified"),
                            part("souther.compiler.check.ProjectionEvidence$NotCertified",
                                    "causes"), HELD,
                            arm(owner), part(owner, "atom"),
                            part("souther.compiler.check.FactSubject", "identity"),
                            part("souther.compiler.check.Term", "of"))),
                    A_TERM_HOLDS_ANYTHING, Traversal.Why.SAYS_NOTHING_OF_ITSELF));
        }
    }

    /** Down to a position of a reading of an input, by each way the reading holds one. */
    private static final TypePath.Step[] EVERY_POSITION = {
            MAP_VALUE, part("souther.compiler.inputs.InputDomain", "positions"), HELD,
            arm("souther.compiler.inputs.ReadPosition")};

    /** And the same positions under the paths they were read at. */
    private static final TypePath.Step[] BY_PATH = {
            MAP_VALUE, part("souther.compiler.inputs.InputDomain", "byPath"), MAP_VALUE,
            arm("souther.compiler.inputs.ReadPosition")};

    /**
     * Every place a question's own declaration puts something that cannot be compared as a value.
     *
     * <p>Read against what the declarations say and not against what a compile built, which is why
     * some of these are met by nothing that walks a store: a question no corpus puts still declares
     * what it answers with, and what a declaration allows is wider than what a run happened to
     * build.
     */
    private static final List<KnownDeclared> DECLARED = everyDeclaredPlace();

    private static List<KnownDeclared> everyDeclaredPlace() {
        List<KnownDeclared> out = new java.util.ArrayList<>(List.of(
            new KnownDeclared(declared(Q + "Front$Library", "souther.compiler.stdlib.Stdlib"),
                    STDLIB, Traversal.Why.SAYS_NOTHING_OF_ITSELF),
            new KnownDeclared(declared(Q + "Bodies$Expanding", "souther.compiler.stdlib.Stdlib",
                    part(Q + "Bodies$Expanding$Of", "table"),
                    part("souther.compiler.check.HelperTable", "stdlib")), STDLIB,
                    Traversal.Why.SAYS_NOTHING_OF_ITSELF),
            new KnownDeclared(declared(Q + "Front$Path", "souther.compiler.meta.ModulePath"),
                    MODULE_PATH, Traversal.Why.NOTHING_CLOSES_IT),
            // Where the declarations a reading of an input reached borrow what has already been
            // made of them, kept by the reading for the readers of those declarations that come
            // after the walk.
            new KnownDeclared(declared(Q + "Adequacy$Inputs",
                    "souther.compiler.check.DeclarationReadings", MAP_VALUE,
                    part("souther.compiler.inputs.InputDomain", "machines")),
                    A_LENDING_OF_READINGS, Traversal.Why.NOTHING_CLOSES_IT),
            // Where the places of a module's bodies are, held by the check that walked them. One
            // place and not the maps under it: the plan says nothing of itself, so the walk stops
            // here — which is the whole of what is being allowed, and the maps under it are what
            // it is being allowed for.
            new KnownDeclared(declared(Q + "Bodies$Checked",
                    "souther.compiler.coverage.CoverageSites$Plan",
                    part(Q + "Bodies$Elaborated", "plan")),
                    AN_INDEX_ONTO_THE_ANSWERS_OWN_GRAPH, Traversal.Why.SAYS_NOTHING_OF_ITSELF),
            // The way of asking, which is where the declarations stop. What a walk of a store goes
            // on to reach through it is the store itself, written down above.
            new KnownDeclared(declared(Q + "Names$ModuleScope",
                    "souther.compiler.check.Resolve$Elsewhere",
                    part("souther.compiler.check.Scoping$Scoped", "values"),
                    part("souther.compiler.check.Resolve$Values", "elsewhere")), A_STORE,
                    Traversal.Why.NOTHING_CLOSES_IT),
            // The way of asking is declared as the reader that does not name a stage, so the two
            // worlds under it are two places rather than one. Both for the same reason: what is
            // held is how to go on asking, not an answer that has to compare as one.
            generationReader("souther.compiler.check.ResolvedSymbols",
                    part("souther.compiler.partition.MeasuredInput", "written"),
                    part("souther.compiler.partition.BehaviorInputs", "rules"),
                    part("souther.compiler.check.RuleReadingSource", "symbols"),
                    arm("souther.compiler.check.ResolvedSymbols")),
            generationReader("souther.compiler.check.DerivedSymbols",
                    part("souther.compiler.partition.MeasuredInput", "written"),
                    part("souther.compiler.partition.BehaviorInputs", "rules"),
                    part("souther.compiler.check.RuleReadingSource", "symbols"),
                    arm("souther.compiler.check.DerivedSymbols")),
            // Where the clauses of a declaration are answered from, beside the symbols above. It is
            // a capability and not a table: which declaration is being asked about is the only input
            // there is, and holding the answers instead would be this reading's copy of what the
            // declaring module said.
            generationReader("souther.compiler.check.ExpandedClauseLookup",
                    Traversal.Why.NOTHING_CLOSES_IT,
                    part("souther.compiler.partition.MeasuredInput", "written"),
                    part("souther.compiler.partition.BehaviorInputs", "rules"),
                    part("souther.compiler.check.RuleReadingSource", "invariants")),
            // Where a clause of a declaration is written, beside the clauses above and for the same
            // reason: a capability, whose only input is which clause is being asked about. Held as
            // an answer here it would be this reading's copy of where the declaring module wrote
            // its text, which is the copy that goes on pointing at where the clause used to be.
            generationReader("souther.compiler.check.ClauseLocations",
                    Traversal.Why.NOTHING_CLOSES_IT,
                    part("souther.compiler.partition.MeasuredInput", "written"),
                    part("souther.compiler.partition.BehaviorInputs", "rules"),
                    part("souther.compiler.check.RuleReadingSource", "written")),
            // What each clause of a declaration states, beside where it is written and for the
            // same reason: a capability, whose only input is which declaration is being asked
            // about. Held as an answer here it would be this reading's copy of what the declaring
            // module made of its own clauses, which is the copy that goes on saying what the
            // clause used to state.
            generationReader("souther.compiler.check.ClauseMeanings",
                    Traversal.Why.NOTHING_CLOSES_IT,
                    part("souther.compiler.partition.MeasuredInput", "written"),
                    part("souther.compiler.partition.BehaviorInputs", "rules"),
                    part("souther.compiler.check.RuleReadingSource", "states")),
            generationReader("souther.compiler.inputs.ReadQuantities",
                    part("souther.compiler.partition.MeasuredInput", "quantities"),
                    arm("souther.compiler.inputs.ReadQuantities")),
            // Where the declarations this subject reaches borrow what has already been made of
            // them. A search chooses a value by reading those declarations with a coordinate fixed,
            // which is a reading of its own every time; what their string rules come to is not, and
            // is what the walk that read the input already worked out.
            new KnownDeclared(
                    declared(Q + "Adequacy$Generated", "souther.compiler.check.DeclarationReadings",
                            then(A_SUBJECT,
                                    part("souther.compiler.partition.MeasuredInput", "machines"))),
                    A_LENDING_OF_READINGS, Traversal.Why.NOTHING_CLOSES_IT),
            new KnownDeclared(declared(Q + "Names$Resolution",
                    "souther.compiler.diag.CompileException",
                    part("souther.compiler.check.Resolve$Resolution", "unresolved"), HELD),
                    AN_EXCEPTION, Traversal.Why.SAYS_NOTHING_OF_ITSELF),
            // Under the question that declares reports of its own, which is where a walk of the
            // declarations meets what every answer holds.
            new KnownDeclared(declared(Q + "Names$Cycles", "souther.compiler.diag.Diagnostic",
                    part(Q + "Names$Cycles$Of", "reported"), MAP_VALUE,
                    part(Q + "Report", "diagnostic")), A_REPORT,
                    Traversal.Why.SAYS_NOTHING_OF_ITSELF)));
        // A reading of an input holds its positions twice — in the order they were read, and under
        // the paths they were read at — so everything under a position is two places the answer
        // exposes it.
        for (TypePath.Step[] positions : List.of(EVERY_POSITION, BY_PATH)) {
            bothEndsOfARange(out, Q + "Adequacy$Inputs",
                    then(positions, part("souther.compiler.inputs.ReadPosition", "bounds"), HELD,
                            part("souther.compiler.inputs.PositionBounds", "narrowedEnds")));
            whatATermHolds(out, positions);
            // What the position's own rules leave it, which travels with the position because it is
            // what a behavior's rules have left to divide.
            theMachineUnderALanguage(out, Q + "Adequacy$Inputs",
                    then(positions, part("souther.compiler.inputs.ReadPosition", "admitted"),
                            part("souther.compiler.values.AdmissibleSet", "approximation")));
        }
        // The machine a class denotes where what it denotes is a pattern's strings, reached at each
        // of the two places an axis is carried from.
        theMachineUnderALanguage(out, Q + "Adequacy$Divided",
                part("souther.compiler.partition.Partitions$Partitioning", "measurements"), HELD,
                part("souther.compiler.partition.PositionMeasurements", "axes"), HELD,
                part("souther.compiler.partition.Axis", "classes"), HELD,
                part("souther.compiler.partition.PartitionClass", "denotes"));
        // And the same machines again through the reading the geometry is a projection of. Two
        // questions are asked of one reading of a body — what the model divides, and where that
        // reading met each condition it places itself — so the walk arrives at everything the
        // geometry holds by both names. One thing to fix, met twice.
        theMachineUnderALanguage(out, Q + "Adequacy$Dividing",
                THE_GEOMETRY_THE_READING_LEFT,
                part("souther.compiler.partition.Partitions$Partitioning", "measurements"), HELD,
                part("souther.compiler.partition.PositionMeasurements", "axes"), HELD,
                part("souther.compiler.partition.Axis", "classes"), HELD,
                part("souther.compiler.partition.PartitionClass", "denotes"));
        theMachineUnderALanguage(out, Q + "Adequacy$Dividing",
                THE_GEOMETRY_THE_READING_LEFT,
                part("souther.compiler.partition.Partitions$Partitioning", "measurements"), HELD,
                part("souther.compiler.partition.PositionMeasurements", "axes"), HELD,
                part("souther.compiler.partition.Axis", "classes"), HELD,
                part("souther.compiler.partition.PartitionClass", "recognises"),
                arm("souther.compiler.partition.Recognition$OfASet"),
                part("souther.compiler.partition.Recognition$OfASet", "values"));
        theMachineUnderALanguage(out, Q + "Adequacy$Dividing",
                THE_GEOMETRY_THE_READING_LEFT,
                part("souther.compiler.partition.Partitions$Partitioning", "measurements"), HELD,
                part("souther.compiler.partition.PositionMeasurements", "position"),
                part("souther.compiler.partition.PositionAccount", "admits"));
        bothEndsOfARange(out, Q + "Adequacy$Dividing",
                THE_GEOMETRY_THE_READING_LEFT,
                part("souther.compiler.partition.Partitions$Partitioning", "measurements"), HELD,
                part("souther.compiler.partition.PositionMeasurements", "axes"), HELD,
                part("souther.compiler.partition.Axis", "narrowed"));
        theMachineUnderALanguage(out, Q + "Adequacy$Generated",
                then(then(A_SUBJECT, A_MEASUREMENT), HELD,
                        part("souther.compiler.partition.Axis", "classes"), HELD,
                        part("souther.compiler.partition.PartitionClass", "denotes")));
        // The machines a declaration's rules come to, filed under what each is a fact about: the
        // plan it realized, both where the plan is a set written out and where the set is what it
        // came to; the set whose extent was taken; and the language met with a stretch.
        String facts = "souther.compiler.values.StringFacts";
        theMachineUnderALanguage(out, Q + "Machines$OfDeclaration",
                part(facts, "realized"), MAP_KEY,
                arm("souther.compiler.values.AdmittedPlan$Of"),
                part("souther.compiler.values.AdmittedPlan$Of", "set"));
        theMachineUnderALanguage(out, Q + "Machines$OfDeclaration",
                part(facts, "realized"), MAP_VALUE);
        theMachineUnderALanguage(out, Q + "Machines$OfDeclaration",
                part(facts, "extents"), MAP_KEY);
        out.add(new KnownDeclared(declared(Q + "Machines$OfDeclaration",
                "souther.compiler.regex.Automaton",
                part(facts, "inside"), MAP_KEY,
                part(facts + "$Stretch", "language"),
                part("souther.compiler.regex.Language", "machine")),
                A_MACHINE_UNDER_A_LANGUAGE, Traversal.Why.SAYS_NOTHING_OF_ITSELF));
        // And the same machine reached through what the class means rather than through what it
        // writes out. A class whose meaning is a set of values holds one, so the strings are on the
        // meaning as well as on the denotation — two routes to one machine and not a second one,
        // and both are places the walk arrives at.
        for (String question : List.of(Q + "Adequacy$Divided", Q + "Adequacy$Generated")) {
            TypePath.Step[] toTheAxes = question.endsWith("Divided")
                    ? new TypePath.Step[] {
                            part("souther.compiler.partition.Partitions$Partitioning",
                                    "measurements"), HELD,
                            part("souther.compiler.partition.PositionMeasurements", "axes")}
                    : then(A_SUBJECT, A_MEASUREMENT);
            theMachineUnderALanguage(out, question,
                    then(toTheAxes, HELD,
                            part("souther.compiler.partition.Axis", "classes"), HELD,
                            part("souther.compiler.partition.PartitionClass", "recognises"),
                            arm("souther.compiler.partition.Recognition$OfASet"),
                            part("souther.compiler.partition.Recognition$OfASet", "values")));
        }
        // And through what the position itself was left holding, which is what a behavior's rules
        // divide: an invariant restricts and a behavior divides what is left, so the values the
        // declarations leave travel with the position and are reached wherever it is.
        theMachineUnderALanguage(out, Q + "Adequacy$Divided",
                part("souther.compiler.partition.Partitions$Partitioning", "measurements"), HELD,
                part("souther.compiler.partition.PositionMeasurements", "position"),
                part("souther.compiler.partition.PositionAccount", "admits"));
        theMachineUnderALanguage(out, Q + "Adequacy$Generated",
                then(A_SUBJECT,
                        part("souther.compiler.partition.MeasuredInput", "divided"),
                        part("souther.compiler.partition.Partitions$Partitioning", "measurements"),
                        HELD,
                        part("souther.compiler.partition.PositionMeasurements", "position"),
                        part("souther.compiler.partition.PositionAccount", "admits")));
        // The same ends, reached where an axis carries what the reading left the position.
        bothEndsOfARange(out, Q + "Adequacy$Divided",
                part("souther.compiler.partition.Partitions$Partitioning", "measurements"), HELD,
                part("souther.compiler.partition.PositionMeasurements", "axes"), HELD,
                part("souther.compiler.partition.Axis", "narrowed"));
        bothEndsOfARange(out, Q + "Adequacy$Generated",
                then(then(A_SUBJECT, A_MEASUREMENT), HELD,
                        part("souther.compiler.partition.Axis", "narrowed")));
        return List.copyOf(out);
    }

    /** Every place a declaration is written down for, and what the walk stops on there. */
    static Map<TypePath.Place, Traversal.Why> declaredPlaces() {
        Map<TypePath.Place, Traversal.Why> out = new LinkedHashMap<>();
        DECLARED.forEach(each -> out.put(each.place(), each.why()));
        return out;
    }

    /** What each of them is, for a reader of a failure. */
    static Map<TypePath.Place, String> declaredReasons() {
        Map<TypePath.Place, String> out = new LinkedHashMap<>();
        DECLARED.forEach(each -> out.put(each.place(),
                said(each.reading().whatItIs()) + " " + whereItStands(each.place().question())
                        + " [" + each.reading().named() + "] " + each.reading().reason()));
        return out;
    }

    /**
     * Where a walk cannot say whose denial a denial is, and why that is known.
     *
     * <p>Beside the places rather than among them, because it is not one. A thing that wrote its own
     * equality answers alike whether that equality is its parts' or its address, for as long as a
     * part of it is denying — so where one of these stands above something already written down, it
     * is what that thing costs and it goes when that thing goes.
     *
     * <p>Held to exactly, like everything else here. One arriving that stands above nothing is a
     * walk that has stopped being able to attribute something it used to.
     */
    static Set<String> cannotBeTold() {
        return Set.of(
                // Above `Names$ModuleScope`'s store, which is the line below it. `Resolve.Values`
                // wrote its own equality and is not a record, so there is no twin of it to build
                // and no way to ask what its equality is over while the store under it denies.
                "WHOSE_DENIAL_THIS_IS_CANNOT_BE_TOLD .Answer#value.Scoped#values"
                        + " in A_MODULE_SPOKEN_ABOUT",
                "WHOSE_DENIAL_THIS_IS_CANNOT_BE_TOLD .Answer#value.Scoped#values"
                        + " in THE_CORPORA",
                // What a lookup with an answer for a name it has no entry for keeps from extending
                // what the JDK ships: two caches the language fills in and opens to nobody. The
                // walk asks, is refused, and says so — reading it as one of the JDK's own maps
                // instead would read it for its entries and never meet what it answers with.
                fieldOfAJdkParent(Adequacy.RowReadings.class, "", "keySet",
                        Scenario.THE_CORPORA),
                fieldOfAJdkParent(Adequacy.RowReadings.class, "", "keySet",
                        Scenario.A_MODULE_SPOKEN_ABOUT),
                fieldOfAJdkParent(Adequacy.RowReadings.class, "", "values",
                        Scenario.THE_CORPORA),
                fieldOfAJdkParent(Adequacy.RowReadings.class, "", "values",
                        Scenario.A_MODULE_SPOKEN_ABOUT),
                // The correspondence between a row's operand and the method it runs as, keyed on
                // the very nodes this answer hands out — so what it means it means inside the
                // answer that holds it, and what the answer says it is leaves it out on purpose.
                // Which is why it is read as a thing of its own: a map that compares by which
                // objects were put in it keeps none of what makes reading a map its entries enough.
                operandMethodsOf("AbstractMap#keySet", Scenario.THE_CORPORA),
                operandMethodsOf("AbstractMap#keySet", Scenario.A_MODULE_SPOKEN_ABOUT),
                operandMethodsOf("AbstractMap#values", Scenario.THE_CORPORA),
                operandMethodsOf("AbstractMap#values", Scenario.A_MODULE_SPOKEN_ABOUT),
                operandMethodsOf("IdentityHashMap#entrySet", Scenario.THE_CORPORA),
                operandMethodsOf("IdentityHashMap#entrySet", Scenario.A_MODULE_SPOKEN_ABOUT),
                operandMethodsOf("IdentityHashMap#table", Scenario.THE_CORPORA),
                operandMethodsOf("IdentityHashMap#table", Scenario.A_MODULE_SPOKEN_ABOUT),
                operandMethodsUnderPrepared("AbstractMap#keySet", Scenario.THE_CORPORA),
                operandMethodsUnderPrepared("AbstractMap#keySet", Scenario.A_MODULE_SPOKEN_ABOUT),
                operandMethodsUnderPrepared("AbstractMap#values", Scenario.THE_CORPORA),
                operandMethodsUnderPrepared("AbstractMap#values", Scenario.A_MODULE_SPOKEN_ABOUT),
                operandMethodsUnderPrepared("IdentityHashMap#entrySet", Scenario.THE_CORPORA),
                operandMethodsUnderPrepared("IdentityHashMap#entrySet",
                        Scenario.A_MODULE_SPOKEN_ABOUT),
                operandMethodsUnderPrepared("IdentityHashMap#table", Scenario.THE_CORPORA),
                operandMethodsUnderPrepared("IdentityHashMap#table",
                        Scenario.A_MODULE_SPOKEN_ABOUT));
    }

    /**
     * A field of what the JDK ships, under something of this compiler's own that holds it.
     *
     * <p>The question is named by its class rather than spelled, so that renaming it is a thing
     * javac says here. Spelled, this would go on standing above a question nothing asks any more.
     */
    private static String fieldOfAJdkParent(Class<?> question, String under, String named,
                                            Scenario scenario) {
        return "A_FIELD_THAT_WOULD_NOT_OPEN " + question.getName() + ".Answer#value" + under
                + ".AbstractMap#" + named + " in " + scenario;
    }

    /** And the same, under the correspondence a prepared module keeps. */
    private static String operandMethodsOf(String named, Scenario scenario) {
        return "A_FIELD_THAT_WOULD_NOT_OPEN " + Q + "Shapes$CheckSurface.Answer#value"
                + ".CheckSurface#operandMethods." + named + " in " + scenario;
    }

    /** The same table, reached through the state the assembly is half of. */
    private static String operandMethodsUnderPrepared(String named, Scenario scenario) {
        return "A_FIELD_THAT_WOULD_NOT_OPEN " + Q + "Shapes$Prepared.Answer#value"
                + ".Prepared#surface.CheckSurface#operandMethods." + named + " in " + scenario;
    }

    /** Every place written down here, whichever detector or scenario meets it. */
    static Set<Locus.Place> places() {
        Set<Locus.Place> out = new java.util.LinkedHashSet<>();
        KNOWN.forEach(each -> out.add(each.place()));
        return out;
    }

    /** And who is expected to meet each of them. */
    static Map<Locus.Place, Set<String>> observations() {
        Map<Locus.Place, Set<String>> out = new LinkedHashMap<>();
        KNOWN.forEach(each -> {
            Set<String> seen = new java.util.TreeSet<>();
            each.seenBy().forEach(one -> seen.add(one.toString()));
            out.put(each.place(), seen);
        });
        return out;
    }

    /**
     * What each place is, for a reader of a failure: what is wrong there, where that stands, and
     * the one thing to fix that every place holding it points at.
     */
    static Map<Locus.Place, String> reasons() {
        Map<Locus.Place, String> out = new LinkedHashMap<>();
        KNOWN.forEach(each -> out.put(each.place(),
                said(each.reading().whatItIs()) + " " + whereItStands(each.place().question())
                        + " [" + each.reading().named() + "] " + each.reading().reason()));
        return out;
    }

    private AnswerClosure() {
    }
}
