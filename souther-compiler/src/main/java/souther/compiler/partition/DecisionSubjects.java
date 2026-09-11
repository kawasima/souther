package souther.compiler.partition;

import souther.compiler.check.Location;
import souther.compiler.check.Symbols;
import souther.compiler.core.Core;
import souther.compiler.inputs.InputDomain;
import souther.compiler.inputs.InputReads;
import souther.compiler.inputs.PathResolution;
import souther.compiler.inputs.ReadMeaning;
import souther.compiler.inputs.TermPath;
import souther.compiler.types.ValueName;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * What a body's expressions name that a row can control.
 *
 * <p>One reading for every question the decision asks about a subject. A truth, a comparison and a
 * fork are three things to do with one of these, and each working out for itself what it was looking
 * at would be three accounts of one identity — after which a rule's columns could name two subjects
 * for one value and the table would admit an assignment the body never reaches.
 *
 * <p><b>Only what a row can control.</b> A position of the input is written at and an answer of a
 * dependency this behavior declares is stood in for. A call to anything else is a value the model
 * computes, and a row cannot be composed to make it come out one way rather than another.
 *
 * @param dependencies the behaviors this one declares it depends on, which are the ones a row
 *                     stands in for
 */
record DecisionSubjects(InputDomain inputs, Symbols symbols,
                        Set<ValueName.Behavior> dependencies) {

    DecisionSubjects {
        dependencies = Set.copyOf(dependencies);
    }

    /**
     * What {@code e} names, or null where it is nothing a row controls.
     *
     * <p>The input is asked first, because a name that is a position is that position however it
     * was given its value. What is left is read as an answer: the fields taken off it, and under
     * them a call this behavior stands a dependency in for.
     */
    DecisionSubject of(Core e, InputReads at) {
        if (at.pathOf(e, symbols) instanceof PathResolution.At(TermPath stands)
                && inputs.at(stands) != null) {
            return new DecisionSubject.AnInput(stands);
        }
        List<TermPath.Step> steps = new ArrayList<>();
        Core under = e;
        InputReads reads = at;
        while (true) {
            // A newtype's value is the value it wraps, which is one subject and not a step inside
            // one. Read as a step, `riskScore(c).value` and `riskScore(c)` would be two columns
            // over one answer.
            if (under instanceof Core.FieldAccess field) {
                if (Location.isStep(field.target().type(), field.field(), symbols)) {
                    steps.add(new TermPath.Step.Field(field.field()));
                }
                under = field.target();
                continue;
            }
            if (under instanceof Core.Read name
                    && reads.meaningOf(name, symbols) instanceof ReadMeaning.Through through) {
                under = through.denotes().value();
                reads = through.denotes().at();
                continue;
            }
            break;
        }
        InjectedAnswer answered = answerOf(under, reads);
        if (answered == null) {
            return null;
        }
        Collections.reverse(steps);
        return new DecisionSubject.AnAnswer(answered, steps);
    }

    /**
     * The answer {@code e} is, or null where it is not a call to a dependency of this behavior.
     *
     * <p>The arguments are subjects themselves, so an argument nothing here names leaves the answer
     * unnamed: two calls whose arguments this reading cannot tell apart are two questions, and one
     * column for them would say a body that asks about two things asks about one.
     */
    private InjectedAnswer answerOf(Core e, InputReads at) {
        if (!(e instanceof Core.Call call && call.fn() instanceof Core.Reached reached
                && reached.denotes() instanceof ValueName.Behavior dependency
                && dependencies.contains(dependency))) {
            return null;
        }
        List<DecisionSubject> arguments = new ArrayList<>();
        for (Core argument : call.args()) {
            DecisionSubject stands = of(argument, at);
            if (stands == null) {
                return null;
            }
            arguments.add(stands);
        }
        return new InjectedAnswer(dependency, arguments);
    }
}
