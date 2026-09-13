package souther.compiler.execute.jvm;

import souther.compiler.check.Sig;
import souther.compiler.examples.Answering;
import souther.compiler.examples.Deadline;
import souther.compiler.examples.ExampleVerifier;
import souther.compiler.execute.ExampleExecution;
import souther.compiler.observe.ArmObservation;

import java.util.Map;
import java.util.Set;

/**
 * A run of one module's rows against an implementation supplied from outside Souther.
 *
 * <p>Beside {@link JvmProgramExecution} and not on it. What the language asks is whether its rows
 * hold, and the answer to that is the program this compile emitted; what this is for is a caller
 * that brings its own answerer — a Java object implementing an injected behavior — and wants the
 * module's rows run against it. That is not the same question with a parameter added. It is a
 * binding of Java to Souther, and {@link Answering} says so in its own face: what it hands back is
 * made from a manifest of generated implementations and a loader, which no other implementation of
 * an execution would have.
 *
 * <p>So it is not a second way to ask what acceptance asks. It is reached by the Java-facing
 * {@code SoutherExamples} integration and by nothing that decides whether a program is accepted; a
 * caller that wanted the compile's own answer asks the capability.
 */
public final class JvmExampleRuns {

    private JvmExampleRuns() {}

    /**
     * The module's rows, ready to be run one at a time against {@code implementation}.
     *
     * <p>Everything but the instance comes from the compile. What an implementation is held to is
     * the module as this reading of the source has it, which is the whole reason the source is read
     * at the time the run happens.
     *
     * <p>How the run is arranged is settled here and is not offered to whoever binds an instance.
     * Two things have to be of one arrangement: what keeps the wait, and where an application is
     * applied. The first runs the row on a worker and services what it hands over; the second is
     * what the row hands over through, and reaches the worker's hand-off. A caller able to name them
     * separately could name one of each of two arrangements, which is an application handed to
     * nobody — so there is nowhere to say it.
     *
     * <p>{@code answersFor} and {@code sigs} are the binding's own: which behaviors the instance was
     * supplied for, and the declarations of the module the rows are written for.
     *
     * @throws IllegalStateException where this compile emitted nothing to run the rows against
     */
    public static ExampleVerifier evaluating(JvmProgramImages images, ExampleExecution asked,
                                             Object implementation, Set<String> answersFor,
                                             Map<String, Sig> sigs) {
        Deadline deadline = JvmDeadlines.onWorkers().forThisCompile(asked.policy().compilerTimeout());
        Answering answering = Answering.bound(implementation, answersFor, sigs,
                Handoff.onTheThreadThatAsked());
        JvmProgramImage image = images.evaluating(asked.module(), ArmObservation.OMIT);
        if (image == null) {
            throw new IllegalStateException("`" + asked.module() + "` emitted nothing to run its"
                    + " rows against");
        }
        return ExampleVerifier.evaluating(asked.forExamples(), asked.symbols(), asked.published(),
                asked.kinds(), asked.fieldTypes(),
                asked.signatures(),
                image.program(), image.published(), asked.requirements(), image.around(),
                asked.definitions(), deadline, asked.policy(), answering,
                asked.contracts());
    }
}
