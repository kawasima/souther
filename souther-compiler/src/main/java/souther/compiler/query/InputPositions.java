package souther.compiler.query;

import souther.compiler.ast.Hir;
import souther.compiler.inputs.InputDomain;

import java.util.List;

/**
 * What positions of its own a behavior has, which is what says where a case of one of its inputs is
 * owed.
 *
 * <p>A case of a sum an input ranges over and the class that sum makes of the position are one
 * thing a row is owed for — while both derivations are about one behavior's own position. A
 * behavior whose input is read at its stages has the first and not the second: what the stages
 * divide are their own positions, under their own names, and each is owed to rows of that stage.
 *
 * <p><b>Three answers and not a list that may be empty.</b> A behavior that declares no parameters
 * and one nothing could read a boundary for are different facts, and a list stands for neither: it
 * is a thing to index into, which is how every case of a composition came to be looked up at a
 * position nothing declared.
 *
 * <p>Read off {@link InputForMeasurement}, which is the one place a behavior is put in that state.
 * Worked out again from the kind of the declaration, this would be a second answer to a question
 * that has one — and the two would part the day a shape is added that writes no parameters and has
 * an input all the same.
 */
public sealed interface InputPositions {

    /** The behavior declares its own inputs, and calls them these. */
    record Declared(List<String> names) implements InputPositions {

        public Declared {
            names = List.copyOf(names);
        }
    }

    /** It declares none: its stages are where what it takes is read. */
    record AtStages() implements InputPositions {}

    /**
     * This compilation could not work out what it takes, so neither answer is available.
     *
     * <p>Beside the two rather than folded into either. A composition is a behavior whose positions
     * are its stages'; this is a compilation that did not get far enough to say which of the two
     * shapes it is looking at, and a measure that read it as the second would owe cases at a
     * behavior that may declare its own.
     */
    record NotRead() implements InputPositions {}

    /** Which of them {@code input} says the behavior is. */
    static InputPositions of(InputForMeasurement input) {
        return switch (input) {
            case InputForMeasurement.Local(Hir.SpecBehavior spec, InputDomain _) ->
                    new Declared(spec.params().stream().map(Hir.Param::name).toList());
            case InputForMeasurement.AtStages _ -> new AtStages();
        };
    }
}
