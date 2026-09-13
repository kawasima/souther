package souther.compiler.execute.jvm;

import souther.compiler.codegen.Backend;
import souther.compiler.execute.ConstantConstruction;
import souther.compiler.execute.ConstantOutcome;
import souther.compiler.execute.ProgramExecution;
import souther.compiler.execute.WrittenValue;
import souther.compiler.examples.Answering;
import souther.compiler.examples.ExampleStatements;
import souther.compiler.examples.FixtureReader;
import souther.compiler.examples.ExampleVerifier;
import souther.compiler.examples.RowTrial;
import souther.compiler.execute.BoundaryValues;
import souther.compiler.execute.ExampleExecution;
import souther.compiler.execute.RowTrials;
import souther.compiler.jvm.GeneratedClass;
import souther.compiler.jvm.SoutherJvmAbi;
import souther.compiler.observe.ArmObservation;
import souther.compiler.observe.Observations;
import souther.compiler.observe.RowRun;
import souther.compiler.observe.StatementReading;
import souther.compiler.observe.TableBuild;
import souther.compiler.source.SourceId;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The implementation ADR-0032 names: the questions are answered by running the program that will
 * ship.
 *
 * <p>Everything the machine needs is here and nowhere above it — the generated class name, the
 * loader it is resolved in, the reflection that calls the check, and which primitive a written
 * constant is passed as. A compiler that reflected over a generated class in the middle of deciding
 * whether it accepts a program was writing one implementation's address into the language's own
 * verdict.
 */
public final class JvmProgramExecution implements ProgramExecution {

    private final JvmProgramImages images;
    private final JvmExampleDeadlines deadlines;

    public JvmProgramExecution(JvmProgramImages images, JvmExampleDeadlines deadlines) {
        if (images == null) {
            throw new IllegalArgumentException("a run of the JVM program needs its classes");
        }
        if (deadlines == null) {
            throw new IllegalArgumentException(
                    "a run of the JVM program needs the deadline it runs under");
        }
        this.images = images;
        this.deadlines = deadlines;
    }

    /**
     * The arrangement this run keeps the wait it was told with.
     *
     * <p>Told and not looked up. The wait is one of the terms in {@code asked}, so this is where it
     * is turned into something that keeps it, and there is no other way for a run here to learn it —
     * which is what stops the boundary saying one wait while the run is given up on at another.
     */
    private souther.compiler.examples.Deadline keeping(ExampleExecution asked) {
        return deadlines.forThisCompile(asked.policy().compilerTimeout());
    }

    @Override
    public RowRun run(ExampleExecution asked, SourceId source, ArmObservation arms) {
        JvmProgramImage image = images.evaluating(asked.module(), arms);
        if (image == null) {
            return new RowRun.NotRunHere();
        }
        Observations observed = ExampleVerifier.check(asked.forExamplesWrittenIn(source),
                asked.symbols(),
                asked.published(),
                asked.kinds(),
                asked.fieldTypes(),
                asked.signatures(), image.program(), image.published(), asked.requirements(),
                image.around(), asked.definitions(), keeping(asked), asked.policy(),
                // What applies a behavior here is what this compile emitted. A compile has nothing
                // else to run a row against; something supplied from outside one arrives through
                // the same seam and brings its own classes.
                Answering.generatedHere(), asked.contracts());
        return new RowRun.Ran(observed);
    }

    @Override
    public TableBuild fakeTables(ExampleExecution asked, SourceId source) {
        if (ExampleStatements.tablesBuiltIn(asked.forExamples(), asked.signatures(), source).isEmpty()) {
            // Nothing this source states is a table this source builds, so there is nothing here
            // that went unbuilt. Asked the other way round — is there a program to build against —
            // a file that wrote no fake at all would answer that its tables could not be built,
            // and a caller reading that as "this source was not answered for" would lose whatever
            // its rows had to say.
            return new TableBuild.Built(List.of());
        }
        JvmProgramImage image = images.evaluating(asked.module(), ArmObservation.OMIT);
        if (image == null) {
            return new TableBuild.NotBuiltHere();
        }
        // The classes alone. Nothing here applies a behavior, so what the compile implemented is not
        // a question this asks.
        return new TableBuild.Built(ExampleStatements.fakeTables(asked.forExamples(), asked.symbols(),
                asked.published(),
                asked.kinds(),
                asked.fieldTypes(),
                asked.signatures(), image.program().classes(), image.around(), asked.definitions(),
                source, keeping(asked), asked.policy(), asked.contracts()));
    }

    @Override
    public StatementReading statements(ExampleExecution asked) {
        JvmProgramImage image = images.evaluating(asked.module(), ArmObservation.OMIT);
        if (image == null) {
            return new StatementReading.NotReadHere();
        }
        // The rows of the modules this one stands in for a behavior of, as those modules write
        // them. Their executions are not taken: the values are built on this image's loader, which
        // carries every module this one reaches, so the two sides of a comparison are of one
        // execution and the equality that decides it is the language's own.
        Map<String, ExampleStatements.Declaring> declaring = new LinkedHashMap<>();
        asked.declaring().forEach((name, reading) -> declaring.put(name,
                new ExampleStatements.Declaring(reading.forExamples(), reading.symbols(),
                        reading.published(), reading.kinds(), reading.fieldTypes(),
                        reading.definitions())));
        return new StatementReading.Read(ExampleStatements.disagreements(asked.forExamples(),
                asked.symbols(), asked.published(), asked.kinds(), asked.fieldTypes(),
                asked.signatures(), image.program().classes(), image.around(),
                asked.definitions(), keeping(asked), asked.policy(), asked.contracts(),
                declaring));
    }

    @Override
    public BoundaryValues values(ExampleExecution asked) {
        // The classes alone: building a value applies no behavior, so what the compile implemented
        // is not a question this asks.
        //
        // And uncounted, said outright rather than taken from what the build happens to be
        // measuring. Which arms a row goes through is recorded by instrumenting the bodies, and a
        // value is built by the decoders without one of them running — so asking for the recorded
        // classes here would make composing a value fail wherever the plan and the bodies do not
        // line up, which is a fault in a measurement nothing here is making.
        JvmProgramImage image = images.evaluating(asked.module(), ArmObservation.OMIT);
        return image == null ? null
                : FixtureReader.constructing(asked.forExamples(), asked.symbols(),
                        asked.published(), asked.kinds(), asked.fieldTypes(),
                        image.program().classes(), image.around(), asked.definitions());
    }

    @Override
    public RowTrials trials(ExampleExecution asked, ArmObservation arms) {
        JvmProgramImage image = images.evaluating(asked.module(), arms);
        if (image == null || image.program().implementations() == null) {
            return null;
        }
        return RowTrial.over(asked.forExamples(), asked.symbols(), asked.published(), asked.kinds(),
                asked.fieldTypes(),
                image.program().classes(),
                image.around(), asked.definitions(), image.program().implementations(),
                asked.signatures(), asked.contracts(),
                image.program().probes(), asked.policy());
    }

    @Override
    public ConstantOutcome check(ConstantConstruction written) {
        ClassLoader loader = images.compileTimeLoader(written.writtenIn());
        if (loader == null) {
            return new ConstantOutcome.NotEvaluatedHere();
        }
        Class<?> ctfe;
        try {
            ctfe = Class.forName(SoutherJvmAbi.nameOf(new GeneratedClass.Ctfe(
                    new GeneratedClass.Value(written.type()))).binaryName(), true, loader);
            if ((boolean) ctfe.getMethod("check", parameterOf(written.value()))
                    .invoke(null, argumentOf(written.value()))) {
                return new ConstantOutcome.Holds();
            }
        } catch (ReflectiveOperationException | LinkageError _) {
            // Not this compiler failing to say something. Where the check cannot be loaded or run
            // here the language leaves the construction to the check that runs when the program
            // does, which is ADR-0032's own degradation and one of the three answers.
            return new ConstantOutcome.NotEvaluatedHere();
        }
        return new ConstantOutcome.Violates(failingClause(ctfe, written));
    }

    /**
     * The clause the value breaks, or none where the clause carries no name of its own or the check
     * for it cannot be run.
     *
     * <p>The clauses are asked one at a time in the order they are declared, so the one named is the
     * one a construction at run time would report: that construction refines the same checks in the
     * same order and stops at the first that does not hold.
     */
    private static Optional<String> failingClause(Class<?> ctfe, ConstantConstruction written) {
        List<ConstantConstruction.Clause> clauses = written.clauses();
        for (int i = 0; i < clauses.size(); i++) {
            try {
                if (!(boolean) ctfe.getMethod(Backend.clauseCheck(i), parameterOf(written.value()))
                        .invoke(null, argumentOf(written.value()))) {
                    return clauses.get(i).name();
                }
            } catch (ReflectiveOperationException | LinkageError _) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    /** What the generated check declares the constant as. */
    private static Class<?> parameterOf(WrittenValue value) {
        return switch (value) {
            case WrittenValue.Whole _ -> long.class;
            case WrittenValue.Truth _ -> boolean.class;
            case WrittenValue.Text _ -> String.class;
            case WrittenValue.Decimal _ -> BigDecimal.class;
        };
    }

    /** The same constant as the object a reflective call is handed. */
    private static Object argumentOf(WrittenValue value) {
        return switch (value) {
            case WrittenValue.Whole(long whole) -> whole;
            case WrittenValue.Truth(boolean truth) -> truth;
            case WrittenValue.Text(String text) -> text;
            case WrittenValue.Decimal(BigDecimal decimal) -> decimal;
        };
    }
}
