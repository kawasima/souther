package souther.compiler.examples;

import souther.compiler.execute.EvaluationPolicy;
import souther.compiler.execute.RowTrials;
import souther.compiler.ast.Hir;
import souther.compiler.check.BoundaryInput;
import souther.compiler.check.Sig;
import souther.compiler.check.Symbols;
import souther.compiler.coverage.NumberingIdentity;
import souther.compiler.coverage.Observation;
import souther.compiler.coverage.Probe;
import souther.compiler.evaluate.EvaluationContext;
import souther.compiler.generated.GeneratedImplementations;
import souther.compiler.generated.MemoryClassLoader;
import souther.compiler.generated.ProbeImage;
import souther.compiler.jvm.ClassFileImage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Running a row nobody has written yet, to see where it goes.
 *
 * <p>What a generator cannot do for itself and cannot be told. Which combination of a body's
 * decisions a composed row sits in is settled by running it: everything up to that point is a
 * reading of the body, and a reading is what may be wrong.
 *
 * <p>The same classes an evaluation runs against, reached the same way a written row reaches them —
 * the values are built through this module's own decoders and handed to the answerer this compile
 * emitted. What differs is that there is no row: no expectation to hold the answer to, and no
 * module's table behind what its dependencies answer. What a candidate stands them in with travels
 * with the candidate, because which answer a rule takes is part of what the candidate is; a
 * behavior whose implementation is out of reach still cannot be applied, which is said outright by
 * the layer that would have applied it and comes back as nothing having run — asked of that layer
 * rather than worked out here from what a behavior declares, there being one question and no reason
 * for two answers to it.
 *
 * <p>A run that aborts still went where it went. An invariant refusing the answer, a budget running
 * out, an {@code unreachable} being reached — each of them happens after the row has passed whatever
 * it passed, and what was recorded up to that point is what the row did. So the recording is read on
 * every way out and the failure itself is dropped: nothing here is judging the row, and a generator
 * has no expectation for it to have failed against.
 */
public final class RowTrial {


    /**
     * A way to run rows against one module's generated classes.
     *
     * <p>One loader for the module, defined once here. The values a row hands over have to be
     * instances of the classes the answerer applies, so building them and applying them are two ends
     * of one loader rather than two loaders that agree — and the classes are defined once however
     * many behaviors are searched.
     *
     * <p>The classes have to be the measuring ones. A run of classes emitted without the calls that
     * record where it went is a run nothing was recorded of, which reads exactly like a run that
     * went nowhere — so a caller with unmeasured classes must not build one of these, and every
     * combination would otherwise come back missed.
     *
     * @param steps how many counted points a row may pass, and how deep a recursive helper may go.
     *              A composed row is not a row anyone wrote, so a model that loops on it is this
     *              search's problem to stop rather than an author's to be told about
     */
    public static RowTrials over(souther.compiler.check.Prepared.ForExamples module,
                              Symbols symbols, souther.compiler.observe.FieldTypes fields,
                              Map<String, ClassFileImage> classes,
                              ClassLoader parent,
                              Map<String, Hir.FnDef> values, GeneratedImplementations generated,
                              ProbeImage probes,
                              EvaluationPolicy steps) {
        MemoryClassLoader loader = new MemoryClassLoader(classes, parent);
        Answerer answerer = Answering.generatedHere().over(generated, loader);
        return (behavior, sig) -> (inputs, answers) -> {
            if (!(answerer.of(behavior) instanceof Answerer.Answer.Something applies)) {
                return Optional.empty();   // nothing applies this behavior, so nothing ran
            }
            // One reader per row, the way a written row has one: what a reading builds up while it
            // expands a value is that row's, and a reader kept between them would be a session
            // spanning every candidate of every combination.
            return went(new FixtureReader(module, symbols, fields, values, loader), applies, behavior, sig,
                    inputs, answers, probes, steps);
        };
    }

    /**
     * One row, built and applied, and what the probe saw while it was.
     *
     * <p>The recording is begun and read on this thread because the run is on this thread, and it
     * is begun only where there is something to record: classes generated without the calls that
     * write a run down leave no account, which is not the same as an account of a run that reached
     * nothing.
     */
    private static Optional<Observation> went(FixtureReader fixtures,
                                              Answerer.Answer.Something applies, String behavior,
                                              Sig sig, List<Hir.Expr> inputs,
                                              List<RowTrials.AnsweredWith> answers,
                                              ProbeImage probes, EvaluationPolicy steps) {
        List<BoundaryInput> ins = sig.ins();
        if (inputs.size() != ins.size()) {
            return Optional.empty();   // not a row of this behavior, so this is not the thing to run
        }
        Answerer.Applying applying;
        List<Handed> over;
        try {
            // Built and gathered before anything is recorded. A value that could not be made is a
            // row that never ran, and recording the building would put whatever a fixture's own
            // helpers passed through into what the row is said to have done.
            over = new ArrayList<>(ins.size());
            for (int i = 0; i < ins.size(); i++) {
                Object built = fixtures.built(inputs.get(i), ins.get(i));
                BoundaryInput at = ins.get(i);
                String what = "input " + (i + 1) + " of `" + behavior + "`";
                over.add(new Handed(built, () -> fixtures.neutral(built, at, what)));
            }
            applying = applies.applying(standingIn(fixtures, answers));
        } catch (StandinNotBuilt | LinkageError e) {
            // Nothing was applied, and these are the two ways that happens before an application:
            // a stand-in that could not be made, and this compiler's own output not linking. Named
            // rather than taken by category — anything else out of here is this compiler failing at
            // something it does not have a word for, and turning that into a fact about where a row
            // went is how a defect in the runner comes back as a defect in the model.
            return Optional.empty();
        }
        // Under the numbering the classes about to be run were emitted with, or not at all where
        // they record nothing. Asked once and answered by the shape: the recording is begun, read
        // and let go inside one arm, so there is no way to end one that was never begun — and what
        // comes back where nothing was watching is no account of a run rather than an account of
        // one that went nowhere.
        return switch (probes) {
            case ProbeImage.Uninstrumented _ -> {
                applied(applying, over, steps);
                yield Optional.empty();
            }
            case ProbeImage.Instrumented(NumberingIdentity numbering) -> {
                Probe.begin(numbering);
                try {
                    yield applied(applying, over, steps)
                            ? Optional.of(Probe.snapshot()) : Optional.empty();
                } finally {
                    // On every way out, including one nothing here catches. A recording left
                    // installed is where the next reader on this thread would start.
                    Probe.end();
                }
            }
        };
    }

    /**
     * What the candidate stands its target's dependencies in with, built.
     *
     * <p>The values go through the reader this row is being built by, so a dependency's answer is
     * one of this module's own values exactly as an input of the row is. Built here rather than
     * where the candidate was composed, for the reason a row's inputs are: what a search chose is
     * text and a tree, and what an implementation is constructed with is of the loader it came
     * from.
     *
     * <p>Which entry answers a call is the rule a written table dispatches by
     * ({@link ExampleStatements.Standins#answering}), asked of what this built rather than decided
     * again here. The entry naming no arguments is the fallback, so a call none of the others state
     * is answered rather than refused.
     */
    private static List<DependencyStandin> standingIn(FixtureReader fixtures,
                                                      List<RowTrials.AnsweredWith> answers) {
        List<DependencyStandin> out = new ArrayList<>(answers.size());
        for (RowTrials.AnsweredWith each : answers) {
            List<BoundaryInput> takes = each.signature().ins();
            List<Entry> stated = new ArrayList<>();
            Entry otherwise = null;
            for (RowTrials.AnsweredWith.Answer answer : each.answers()) {
                Object value = fixtures.buildFixture(answer.answers(), each.signature().out())
                        .value();
                if (answer.forEveryCall()) {
                    otherwise = new Entry(null, value);
                    continue;
                }
                Object[] key = new Object[takes.size()];
                for (int i = 0; i < key.length; i++) {
                    key[i] = fixtures.built(answer.whenAppliedTo().get(i), takes.get(i));
                }
                stated.add(new Entry(key, value));
            }
            Entry fallback = otherwise;
            out.add(StandingIn.by(each.dependency(), takes.size(), applied ->
                    ExampleStatements.Standins.answering(stated, fallback, Entry::arguments,
                            Arrays.copyOf(applied, takes.size())).value()));
        }
        return List.copyOf(out);
    }

    /** One entry of what a candidate stands a dependency in with, built. */
    private record Entry(Object[] arguments, Object value) {}

    /**
     * Applies the behavior, and says whether it was applied at all.
     *
     * <p>A run that aborts still went where it went, so what stopped it is dropped: nothing here is
     * judging the row, and a generator has no expectation for it to have failed against. What is
     * not a run at all is the implementation being out of reach, and that is the false answer.
     *
     * <p>The budget is let go on every way out, a worker being something the next row would
     * otherwise start inside of.
     */
    private static boolean applied(Answerer.Applying applying, List<Handed> over,
                                   EvaluationPolicy steps) {
        EvaluationContext.begin(steps.stepLimit(), steps.recursionDepthLimit());
        try {
            applying.to(over);
        } catch (ImplementationNotReached e) {
            // Not a run at all: the implementation could not be reached to apply. Saying the row
            // did nothing would be saying it went nowhere, and those are different facts.
            return false;
        } catch (InvocationFailure e) {
            // It ran and stopped. Where it had got to is what is being asked for, and what stopped
            // it is not.
            //
            // This one and no wider. What the applied code ends with arrives as this, so a
            // throwable that is not one is this compiler failing to reach or drive its own output —
            // and swallowed here it would come back as a candidate that ran and missed, which is a
            // statement about the model. The seam says which failures it has
            // ({@link Answerer.Applying#to}) and those are the ones read.
        } finally {
            EvaluationContext.end();
        }
        return true;
    }

    private RowTrial() {}
}
