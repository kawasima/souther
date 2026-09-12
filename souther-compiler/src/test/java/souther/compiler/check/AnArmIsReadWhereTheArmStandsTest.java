package souther.compiler.check;

import org.junit.jupiter.api.Test;

import souther.compiler.ast.Hir;
import souther.compiler.core.Core;
import souther.compiler.diag.SourcePos;
import souther.compiler.types.BindingId;
import souther.compiler.types.BindingOwner;
import souther.compiler.types.CaseSelector;
import souther.compiler.types.ConstructOccurrence;
import souther.compiler.types.ResolvedCase;
import souther.compiler.types.SourceConstruct;
import souther.compiler.types.SourceConstructOrigin;
import souther.compiler.types.Type;
import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbol;
import souther.compiler.types.TypeSymbols;
import souther.compiler.types.WrittenOwner;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * Every value a fork may be is read where that value is written, and an arm that comes to none is
 * not one of them.
 *
 * <p>Asked of the walk rather than of a model, because an attempted construction reaches this from
 * a {@code guard} inside a block and the readings a body's rules are filed from do not go there.
 * What the walk does with a fork is the same whichever fork it is, and this is where that is
 * settled.
 *
 * <p>What a name inside an arm stands for is the reading's, so the reading here answers with the
 * name of the reading it was asked in — which is how an arm read in the wrong one shows up as a
 * position of the wrong name rather than as nothing at all.
 */
class AnArmIsReadWhereTheArmStandsTest {

    private static final SourcePos POS = new SourcePos(1, 1);
    private static final BindingOwner OWNER = new BindingOwner.OfValue("demo", "go");
    private static final Hir.Binders BINDERS = new Hir.Binders(OWNER);
    private static final TypeSymbol.AtModule PERSON =
            TypeSymbols.declared(new TypeKey("demo", "Person"));
    private static final SourceConstructOrigin ORIGIN = SourceConstructOrigin.written(
            new WrittenOwner.Body("demo", "go"), 0, SourceConstruct.IF);

    /** A reading whose positions say which reading they were found in. */
    private static final ValueOrigin.Reading<String, String> WHERE_IT_WAS_READ =
            new ValueOrigin.Reading<>() {

        @Override
        public String positionOf(Core e, String at) {
            return e instanceof Core.Read read ? at + ":" + read.name() : null;
        }

        @Override
        public String madeFrom(Core e, String at) {
            return null;
        }

        @Override
        public AffineForms.ReadThrough<String> readThrough(Core.Read read, String at) {
            return null;
        }

        @Override
        public String inside(Core.LetIn li, String at) {
            return at;
        }

        @Override
        public ValueOrigin.Opened<String> choosing(Choice.Decides decidedBy, String at) {
            return new ValueOrigin.Opened.Entered<>(switch (decidedBy) {
                case Choice.Decides.ACondition _ -> at;
                case Choice.Decides.ACase _ -> "arm";
                case Choice.Decides.ItWasBuilt _ -> "built";
                case Choice.Decides.ItDeparted _ -> at;
                case Choice.Decides.ByArgumentRelations _ -> at;
            });
        }
    };

    /** A reading that goes inside no arm, as the reading of a clause of a {@code data} does not. */
    private static final ValueOrigin.Reading<String, String> WHICH_GOES_INSIDE_NO_ARM =
            new ValueOrigin.Reading<>() {

        @Override
        public String positionOf(Core e, String at) {
            return WHERE_IT_WAS_READ.positionOf(e, at);
        }

        @Override
        public String madeFrom(Core e, String at) {
            return null;
        }

        @Override
        public AffineForms.ReadThrough<String> readThrough(Core.Read read, String at) {
            return null;
        }

        @Override
        public String inside(Core.LetIn li, String at) {
            return at;
        }

        @Override
        public ValueOrigin.Opened<String> choosing(Choice.Decides decidedBy, String at) {
            return new ValueOrigin.Opened.NotEntered<>();
        }
    };

    private static Core.Read read(String name, int ordinal) {
        return new Core.Read(name, new BindingId(OWNER, ordinal), Type.STRING, POS);
    }

    private static Core.Construct construction(Core given) {
        return new Core.Construct(PERSON, List.of(new Core.FieldValue("name", given, POS)),
                Type.ref(PERSON), POS);
    }

    private static Core.IfConstructed attempt(Core given, Core then, List<Core> departures) {
        return new Core.IfConstructed(construction(given),
                CoreBinders.of(BINDERS.binder("built", POS)), then,
                departures.stream().map(each -> new Core.ElseArm(Optional.empty(), each)).toList(),
                Core.ForkPlace.asWritten(ConstructOccurrence.asWritten(ORIGIN)), Type.STRING, POS);
    }

    private static ValueOrigin<String> originOf(Core e) {
        return ValueOrigin.of(e, "outside", WHERE_IT_WAS_READ);
    }

    /** What {@code e} is made of, where that is a choice. */
    private static ValueOrigin.OneOf<String> choiceIn(Core e) {
        ValueOrigin<String> origin = originOf(e);
        if (origin instanceof ValueOrigin.OneOf<String> choice) {
            return choice;
        }
        throw new AssertionError("a fork is a value that is one of several: " + origin);
    }

    /**
     * An attempt's arms are the value it built and every departure, and what it tried to build is
     * what decided which.
     */
    @Test
    void anAttemptsArmsAreTheValueItBuiltAndEveryDeparture() {
        ValueOrigin.OneOf<String> choice =
                choiceIn(attempt(read("a", 0), read("held", 1), List.of(read("b", 2))));

        assertEquals(2, choice.alternatives().size(),
                () -> "the value it built and the one departure: " + choice.alternatives());
        assertEquals(List.of("outside:a"), List.copyOf(choice.decidedBy().getFirst().positions()),
                "what it tried to build is what its invariant was tested on");
    }

    /**
     * And the success arm is read in the reading choosing it opens, where the name the attempt
     * writes stands for what was built.
     */
    @Test
    void theArmAnAttemptOpensIsReadInTheReadingItOpens() {
        ValueOrigin.OneOf<String> choice =
                choiceIn(attempt(read("a", 0), read("held", 1), List.of(read("b", 2))));

        assertEquals(List.of("built:held"),
                List.copyOf(choice.alternatives().getFirst().positions()),
                "read outside the arm, the name the attempt binds stands for nothing");
        assertEquals(List.of("outside:b"),
                List.copyOf(choice.alternatives().getLast().positions()),
                "and a departure was taken where nothing was built, so it opens nothing");
    }

    /** And the same for a match, whose arm binds the value that was matched. */
    @Test
    void theArmAMatchOpensIsReadInTheReadingItOpens() {
        Core.Case arm = new Core.Case(
                new Core.ResolvedPattern.Single(ResolvedCase.of(CaseSelector.direct(PERSON),
                        List.of(PERSON))),
                CoreBinders.of(BINDERS.binder("it", POS)), read("it", 3), POS);
        ValueOrigin.OneOf<String> choice = choiceIn(new Core.Match(read("subject", 4), List.of(arm),
                Core.ForkPlace.asWritten(ConstructOccurrence.asWritten(ORIGIN)), Type.STRING, POS));

        assertEquals(List.of("arm:it"), List.copyOf(choice.alternatives().getFirst().positions()));
        assertEquals(List.of("outside:subject"),
                List.copyOf(choice.decidedBy().getFirst().positions()));
    }

    /**
     * A departure that comes to no value is not one of the values the attempt may be.
     *
     * <p>Read as one, every reader asking something of all of them answers about a path the value
     * never came down: what the whole was made from, and whether every value it may be was made by
     * an operation.
     */
    @Test
    void anArmThatComesToNoValueIsNotOneOfTheValues() {
        ValueOrigin.OneOf<String> choice = choiceIn(attempt(read("a", 0), read("held", 1),
                List.of(new Core.Unreachable("never", Type.NEVER, POS))));

        assertEquals(1, choice.alternatives().size(),
                () -> "the value it built, and not the departure: " + choice.alternatives());
    }

    /**
     * And an arm a reading does not go inside is a value it can say nothing about, rather than one
     * read where the fork stands.
     *
     * <p>Still one of the values the expression may be: the arm answers a value, and what this
     * reading is short of is the name it answers with. Read outside the arm instead, a name that
     * means something else out there would come back as the arm's own answer.
     */
    @Test
    void anArmAReadingDoesNotGoInsideIsAValueItCannotName() {
        ValueOrigin<String> origin = ValueOrigin.of(
                attempt(read("a", 0), read("held", 1), List.of(read("b", 2))),
                "outside", WHICH_GOES_INSIDE_NO_ARM);

        if (!(origin instanceof ValueOrigin.OneOf<String> choice)) {
            throw new AssertionError("a fork is a value that is one of several: " + origin);
        }
        assertEquals(2, choice.alternatives().size(),
                () -> "both arms answer a value: " + choice.alternatives());
        assertInstanceOf(ValueOrigin.Unnameable.class, choice.alternatives().getFirst());
        assertInstanceOf(ValueOrigin.Unnameable.class, choice.alternatives().getLast());
    }

    /** And a fork every arm of which comes to no value comes to none itself. */
    @Test
    void aForkEveryArmOfWhichComesToNoValueComesToNone() {
        assertInstanceOf(ValueOrigin.NoValue.class,
                originOf(attempt(read("a", 0), new Core.Unreachable("never", Type.NEVER, POS),
                        List.of(new Core.Unreachable("nor this", Type.NEVER, POS)))));
    }
}
