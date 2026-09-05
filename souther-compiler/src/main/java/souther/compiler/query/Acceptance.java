package souther.compiler.query;

import souther.compiler.source.SourceId;

import souther.compiler.diag.CompileException;
import souther.compiler.diag.Located;
import souther.compiler.diag.ReportContext;
import souther.compiler.diag.ExampleDiagnostics;

import java.util.ArrayList;
import java.util.List;

/**
 * Whether the language accepts a compilation, decided in one place.
 *
 * <p>A program having checked is more than every body having typed. A constant construction has to
 * satisfy the invariant of what it builds, two rows may not go by one name, and an {@code example}
 * row has to hold — in Souther a row that disagrees is a compile error and not a test failure — so
 * a program whose rows are wrong is a program the language refuses. Everything that says "this
 * program is accepted" says it by asking here.
 *
 * <p>It was written twice before this, and the two had already come apart: the entry point for one
 * source read the names its rows go by and the one for a module set did not, so a name written
 * twice was refused or not according to how many sources a caller happened to pass. A third reading
 * was about to be added for the checked-program boundary, which would have let one output ship an
 * artifact for a program another output refuses to build. Which of two answers is right is not
 * something two answers can settle.
 *
 * <p>This runs the module's classes. An example is decided by evaluating it, and evaluating it is
 * running the module — which today is running the classes the JVM backend emits. So a caller that
 * wants nothing of the JVM still goes through it, and a program the JVM cannot emit is refused here
 * whether or not the language had anything against it. That is a fact about how a row is evaluated
 * rather than about this: it moves when evaluation stops needing one backend, and until then a
 * verdict here is never more permissive than the one a JVM build gives.
 *
 * <p>Which is the decision and not the arrangement (ADR-0115): a program Souther accepts is one the
 * JVM can emit, so the refusal above reaches the author naming the machine's rule, and an output
 * that is not the JVM's may emit only what the JVM could have emitted.
 * {@code AnOutputOutsideTheCompilerReadsACheckedProgramTest} holds it at the boundary it is about.
 *
 * <p>What the JVM can run bounds nothing. A constant construction whose check will not load is
 * {@code ConstantOutcome.NotEvaluatedHere} and is left to the check that runs when the program does
 * (ADR-0032); a row whose classes will not link is recorded as {@code LINKAGE_FAILED} and observes
 * nothing. Neither refuses the program, and what does is a declaration no JVM would load, decided
 * before anything runs.
 */
public final class Acceptance {

    private Acceptance() {}

    /**
     * Drives {@code compilation} to the language's verdict and raises what it refuses over.
     *
     * <p>For a caller with nothing to do with the warnings. What a program is warned about is not
     * part of whether it is accepted, and a caller that reports them takes the overload that hands
     * them over.
     *
     * @throws CompileException where the program is refused
     */
    public static void of(Compilation compilation) {
        of(compilation, new ArrayList<>());
    }

    /**
     * The same, putting what the program is warned about into {@code warningsOut}.
     *
     * <p>Filled whether or not the program is accepted. A refused program has been warned about the
     * same things an accepted one would be, and a caller that reports both wants both — losing them
     * on the way out would make what a reader is told depend on whether something else was wrong.
     *
     * @throws CompileException where the program is refused
     */
    public static void of(Compilation compilation, List<Located> warningsOut) {
        Db db = compilation.db();

        CompileException structural = compilation.structuralFailure();
        if (structural != null) {
            throw structural;
        }

        db.ask(new Output.All());
        CompileException failed = compilation.failure();
        if (failed != null) {
            throw failed;
        }

        // Every module's classes are now present, so a constant construction and an example can
        // resolve a cross-module reference — including into a dependency, whose classes come off the
        // same path its declarations were read from.
        List<Located> refused = new ArrayList<>();
        for (String module : compilation.modules()) {
            if (!db.ask(new Output.ConstConstructions(module)).present()) {
                CompileException bad = compilation.failure();
                if (bad != null) {
                    throw bad;
                }
                continue;
            }
            // Each failing row with the file it is listed under: a row from an `examples for` file
            // is written in that file, not in the module it contributes to.
            for (SourceId id : compilation.exampleSourcesOf(module)) {
                // What the rows name themselves, before what they state: a name says which row is
                // meant, and two rows sharing one leave every later report about either of them
                // saying it of both.
                for (Report failure : Report.errorsIn(db.ask(new Front.RowNames(id)).reports())) {
                    refused.add(new Located(failure.diagnostic(), ReportContext.inFile(id)));
                }
                // And how many blocks the module writes for one behavior, before what a stand-in
                // does while a row runs: a behavior more than one names has none, so a report about
                // dispatching through one, or about holding one against a recorded row, would be
                // about a table standing in for nothing. What such a block states inside itself is
                // said all the same.
                for (Report failure
                        : Report.errorsIn(db.ask(new Names.StandInBlocks(id)).reports())) {
                    refused.add(new Located(failure.diagnostic(), ReportContext.inFile(id)));
                }
                // Only the errors: this key also carries what a clean run wants to say about how
                // well the rows cover the model, and a warning is not a reason to fail the build.
                for (Report failure
                        : Report.errorsIn(db.ask(Output.Examples.asked(db, module, id)).reports())) {
                    refused.add(new Located(failure.diagnostic(), ReportContext.inFile(id)));
                }
            }
            // Asked whether or not the rows ran: what two written statements say about each other
            // is readable when nothing is.
            db.ask(new Output.SaidDisagreements(module));
        }
        for (String module : compilation.modules()) {
            compilation.answerWarnings(module);
        }
        warningsOut.addAll(compilation.warnings());
        // Each with the file it is written in, whether there is one of them or several: the row a
        // report is about is not always in the module it contributes to. What differs is the line
        // that stands in front of them — one error says what it says, and several are summarised.
        if (refused.size() == 1) {
            throw CompileException.ofReported(refused.get(0));
        }
        if (!refused.isEmpty()) {
            throw CompileException.ofAllReported(refused,
                    ExampleDiagnostics.legacySummary(Located.diagnosticsOf(refused)));
        }
    }
}
