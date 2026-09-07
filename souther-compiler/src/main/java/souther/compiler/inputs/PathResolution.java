package souther.compiler.inputs;

import java.util.List;
import java.util.Objects;
import java.util.function.UnaryOperator;

/**
 * What a reading of where an expression stands came to.
 *
 * <p>A position reached is one. An expression that names none — arithmetic over a value, a branch
 * between two, something built — is the model saying there is nothing there, and a reader is told
 * so.
 *
 * <p><b>And a place this compiler could not choose between, which is neither of those.</b> A block
 * handed to two walks reads its element at one position on one run and another on the next, so the
 * expression names positions of the input and which of them is not settled. Held as no position, a
 * rule an author wrote about their input leaves the measurement without a word; held as one of them,
 * it is filed where the model may say nothing.
 *
 * <p>None of the three stands for a shape the reading declined. That was an answer here once,
 * because a walk refused shapes for being shapes and a reader could not tell "this expression names
 * no position" from "this walk did not follow that". The walk now refuses a step for what a binding
 * is, and a binding that is nothing this can place is a fact about the model as much as arithmetic
 * over one is — so the ways such an answer could come back are shut where it would come from rather
 * than named here. Every shape a walk after a position meets is answered by an arm of its own, and a
 * shape added to {@link souther.compiler.core.Core} is one that walk does not compile without saying
 * what it names.
 *
 * <p><b>Read out arm by arm by a reader that needs a place, and never by one taking a step.</b>
 * Which position a line is drawn on, which position a claim is about, which position a run is over:
 * each of those needs one place and says so where it asks, and an answer added here does not compile
 * until it has. A reader whose question is where a value stands rather than which place it is has
 * nothing to decide, and asking it to decide is what quietly stops it deciding: such a walk is
 * written as one arm and a catch-all, and a catch-all is exhaustive whatever this gains. That step
 * is taken here ({@link #deeper}) so there is nothing for a catch-all to swallow.
 */
public sealed interface PathResolution {

    /**
     * Where the expression stands.
     *
     * <p>Somewhere, and refusing to be made without it. A position that is no position is what the
     * answer beside this one says, so a reader given one would be told a place was reached and find
     * the absence it is held apart from.
     */
    record At(TermPath path) implements PathResolution {

        public At {
            Objects.requireNonNull(path, "a position reached is somewhere");
        }
    }

    /**
     * The expression names no position of the input, which is a fact about the model.
     *
     * <p>What a rule about such a value comes to is a question elsewhere — it may have come from a
     * position, and where it did that is said ({@link InputReads#cameFrom}) — and this is the answer
     * that it does not stand at one.
     */
    record NotAPosition() implements PathResolution {}

    /**
     * The expression stands at one of {@code among} and which is not settled.
     *
     * <p>A fact about this compiler and not about the model, which is why it is not the answer
     * beside it. A block handed to two walks has one parameter and two containers, so a name inside
     * it stands at one position of the input on one run and another on the next — the model is
     * perfectly clear and what cannot be worked out is which of them a reader should be sent to.
     *
     * <p>Held apart because everything a reader does with the two differs. A rule about no position
     * is one the model states nowhere and owes nothing; a rule about one of these is a rule the
     * model states, whose obligation stands at whichever of them it turns out to be — so a measure
     * over any of them is open until something says which. Answered alike, a rule an author wrote
     * about their input left the measurement without a word, and every position it might have
     * divided came back as one the model says nothing about.
     */
    record AtOneOfSeveral(List<TermPath> among) implements PathResolution {

        public AtOneOfSeveral {
            among = List.copyOf(among);
            if (among.size() < 2) {
                throw new IllegalArgumentException(
                        "one of several is one of more than one: " + among);
            }
        }
    }

    /** Whichever of the two {@code places} is: one place, or a choice between them. */
    static PathResolution oneOf(List<TermPath> places) {
        return places.size() == 1 ? new At(places.get(0)) : new AtOneOfSeveral(places);
    }

    /**
     * The same answer with {@code step} taken at every position it names.
     *
     * <p>For a reader whose question is about where a value stands rather than about which place it
     * is: a field of what stands here stands at the field of it, and an element of what stands here
     * at an element of it. Such a reader has nothing to decide between the answers — the step is
     * the same step wherever the value came from — so it says the step and this says where.
     *
     * <p><b>Written here because a reader that decides for itself stops deciding.</b> A walk over
     * the arms is exhaustive only while there is an arm for each, and a reader that took a step at
     * one answer and handed every other back untouched is exhaustive whatever this gains: an answer
     * naming several places came back naming those places without the step, and a rule written
     * about a field was filed at the value the field is in. So the step is taken once, here, and a
     * reader that needs one place asks for one place instead ({@link At}).
     */
    default PathResolution deeper(UnaryOperator<TermPath> step) {
        return switch (this) {
            case At(var at) -> new At(step.apply(at));
            case NotAPosition _ -> this;
            // And where the step takes two of them to one place, there is one place. Kept as
            // several, an answer would say which of them is not settled where nothing is left to
            // settle.
            case AtOneOfSeveral(var among) -> oneOf(among.stream().map(step).distinct().toList());
        };
    }
}
