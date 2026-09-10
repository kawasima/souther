package souther.compiler.query;

import souther.compiler.ast.Hir;
import souther.compiler.check.Prepared;
import souther.compiler.check.RuleRef;
import souther.compiler.coverage.ArmReportAnchor;
import souther.compiler.diag.Citation;
import souther.compiler.diag.SourcePos;
import souther.compiler.partition.ConditionOccurrence;
import souther.compiler.partition.ConditionReportAnchor;
import souther.compiler.sites.AuthoredSites;
import souther.compiler.sites.WrittenApplications;
import souther.compiler.sites.WrittenCondition;
import souther.compiler.sites.WrittenConditions;
import souther.compiler.sites.WrittenForks;
import souther.compiler.types.SourceConstructOrigin;
import souther.compiler.types.TypeSymbol;

import java.util.Map;

/**
 * Where a module's source was written, occurrence by occurrence.
 *
 * <p>Asked of the resolved module and of nothing below it, which is what makes the answer about what
 * the author wrote rather than about what a pass made of it (ADR-0102).
 */
public final class Sites {

    private Sites() {}

    /**
     * Every expression occurrence {@code name}'s source was written with.
     *
     * <p>Absent where two of them could not be told apart. The reason is not carried into the graph
     * because there is no reader for it: an occurrence that cannot be named is a fact about this
     * compiler and not about the module, so what a consumer does is answer nothing — an editor that
     * asks what is at a position is told nothing is, and everything it can answer from the syntax
     * alone it still answers. {@link AuthoredSites#of} says which of the two refusals it was, for
     * whoever is looking into it.
     *
     * <p>Absent, too, where the module does not resolve. A source that will not resolve has no
     * settled reading of its names, and an occurrence found in one that has not is an occurrence
     * whose meaning is about to change.
     */
    public record Authored(String name) implements Key<AuthoredSites> {

        @Override
        public String module() {
            return name;
        }

        @Override
        public Answer<AuthoredSites> compute(Db db) {
            Answer<AuthoredSites.Walked> walked = db.ask(new Walk(name));
            return walked.present() && walked.value().census()
                    instanceof AuthoredSites.Census.Identified(AuthoredSites sites)
                    ? Answer.of(sites) : Answer.absent();
        }
    }

    /**
     * One walk of {@code name}'s source, which both questions about it are projections of.
     *
     * <p>Here rather than at each of them, because a module wrote what it wrote once: answered
     * apart, the two would be two walks of one source, agreeing until the day one of them was
     * taught something the other was not.
     *
     * <p>Kept as its own question so that what each of the two says stops where its own meaning
     * stops. Both are worked out again whenever this comes out different, which an edit to the
     * source makes it; what an editor is told about a place then comes back the same where nothing
     * about the places moved, and goes no further.
     */
    record Walk(String name) implements Key<AuthoredSites.Walked> {

        @Override
        public String module() {
            return name;
        }

        @Override
        public Answer<AuthoredSites.Walked> compute(Db db) {
            Answer<Hir.Module> resolved = db.ask(new Names.Resolved(name));
            return resolved.present()
                    ? Answer.of(AuthoredSites.walk(resolved.value())) : Answer.absent();
        }
    }

    /**
     * Where each fork {@code name}'s source wrote stands.
     *
     * <p>Beside {@link Authored} and read off the same walk. Told apart by what a reader holds: an
     * editor holds a place and asks what is there, and this is asked by a reader that holds a fork
     * and no place at all.
     */
    record ForksWrittenIn(String name) implements Key<WrittenForks> {

        @Override
        public String module() {
            return name;
        }

        @Override
        public Answer<WrittenForks> compute(Db db) {
            Answer<AuthoredSites.Walked> walked = db.ask(new Walk(name));
            return walked.present() ? Answer.of(walked.value().forks()) : Answer.absent();
        }
    }

    /**
     * Where one fork is written.
     *
     * <p>Beside what a reading of it came to, and not inside it. A fork the source wrote and an arm
     * of it that no row goes through are two facts about one fork, and a reader uses one of them:
     * what a warning says is read off the reading, and where to put the caret is read off the
     * module that wrote the fork. Answered together, an edit that moves a helper and changes
     * nothing it does is an edit to every reading of every module that calls it.
     *
     * <p>One fork and not a module's. A report is about the fork it is about, and that is the
     * whole of what it reads here; answered a module at a time, a report pointing at one fork would
     * depend on where every other one in that module is.
     *
     * <p>The module that wrote it answers, whichever module the reading was made in. A helper
     * expanded into three callers is one fork written once, so where it is written is not a
     * question any of the three can answer for itself — and a caller that answered it would say
     * where its own copy came to stand.
     *
     * <p><b>A fork and not any construct an origin can name.</b> An origin names an application or
     * a comparison as readily as a fork, and what is filed is what a body takes arms of
     * ({@link WrittenForks}). Asked about one of the others, this answers that nothing wrote it,
     * which would be a false answer rather than a missing one — so the question is about a fork,
     * and the type it takes is wider than the question only because an origin is the identity every
     * one of them is named by.
     *
     * <p>Absent where nothing this compilation holds wrote the fork. What the language itself
     * ships is the case that matters: its forks stand in every module that calls into it, and no
     * source of this compilation is where they are written.
     */
    public record WhereAForkIsWritten(SourceConstructOrigin fork) implements Key<Citation> {

        @Override
        public String module() {
            return fork.module();
        }

        @Override
        public Answer<Citation> compute(Db db) {
            if (fork.module() == null) {
                return Answer.absent();
            }
            Answer<WrittenForks> written = db.ask(new ForksWrittenIn(fork.module()));
            if (!written.present()) {
                return Answer.absent();
            }
            SourcePos at = written.value().at(fork);
            return at == null ? Answer.absent() : Answer.of(Citation.of(at));
        }
    }

    /**
     * Where one module's plan reached a place, for a report about code nobody here wrote.
     *
     * <p>The other of the two questions a report about an arm asks, and asked of the module that
     * reached it rather than of the one that wrote it. A fork the language ships stands in every
     * body that calls into it, and there is nothing for a reader to open where it is written — so
     * what a report can show is the call this compilation came in through, which is the caller's
     * own text and moves only when the caller does.
     *
     * <p>Addressed by the number the plan handed out, and by the module it was handed out in. A
     * plan numbers its places in the order its walk makes them, so the number means a place only
     * together with whose plan it is; a module's check answers with one plan, which is what makes
     * the pair an address and not half of one.
     *
     * <p>Absent where that module's bodies did not come out, or where its plan numbered no such
     * place. Neither is a report waiting to be written: nothing reached anything.
     */
    public record WhereAPlanReached(String module, int controlId) implements Key<Citation> {

        @Override
        public String module() {
            return module;
        }

        @Override
        public Answer<Citation> compute(Db db) {
            Answer<Map<Integer, Citation>> reached = db.ask(new ForksReachedIn(module));
            if (!reached.present()) {
                return Answer.absent();
            }
            Citation at = reached.value().get(controlId);
            return at == null ? Answer.absent() : Answer.of(at);
        }
    }

    /**
     * Where each place one module's plan reached is, by the number the plan handed out.
     *
     * <p>Under {@link WhereAPlanReached} for the reason {@link ForksWrittenIn} is under
     * {@link WhereAForkIsWritten}: what a report means is one place, and the plan answers for every
     * place at once. Asked place by place, the answer would be built again for each arm a sentence
     * is written about.
     */
    record ForksReachedIn(String module) implements Key<Map<Integer, Citation>> {

        @Override
        public String module() {
            return module;
        }

        @Override
        public Answer<Map<Integer, Citation>> compute(Db db) {
            Answer<Bodies.Elaborated> checked = db.ask(new Bodies.Checked(module));
            if (!checked.present() || checked.value() == null) {
                return Answer.absent();
            }
            return Answer.of(Ordered.map(checked.value().plan().whereEachArmsForkIsWritten()));
        }
    }

    /**
     * Where a module writes one of its declarations.
     *
     * <p>What a report about a line the declarations owe points at. The declaration and not the
     * clause that drew the line: which rule of it a finding is about is the finding's own, and what
     * a reader is shown is the declaration either way.
     *
     * <p>One declaration at a time, and asked of the module that wrote it. A line is owed wherever
     * the model carries the type, so the module keeping the account and the module that wrote the
     * declaration are not always one — and the second is the one that knows where it is.
     *
     * <p>Absent where nothing this compilation holds declares it. What the language itself declares
     * is that case: its declarations are in no source of this compilation.
     */
    public record WhereADeclarationIsWritten(TypeSymbol.AtModule declared) implements Key<Citation> {

        @Override
        public String module() {
            return declared.module();
        }

        @Override
        public Answer<Citation> compute(Db db) {
            Answer<Hir.Def> declaration = db.ask(new Names.ResolvedDeclaration(declared.key()));
            return declaration.present()
                    ? Answer.of(Citation.of(declaration.value().pos())) : Answer.absent();
        }
    }

    /**
     * Where a module writes one of its behaviors.
     *
     * <p>What a report about a behavior points at when what it is about is the behavior itself —
     * a case no row expects, a position nothing divides. The definition and not the name it
     * declares: a reader is being shown the behavior, which is what the sentence is about.
     *
     * <p>One behavior at a time. A module's behaviors are read together, and what is answered here
     * is where this one is — so a report about it is worked out again when the module is read again
     * and comes back where it was, unless this behavior itself has moved.
     *
     * <p>Absent where the module's behaviors did not come out, or where it declares no such one.
     */
    public record WhereABehaviorIsDeclared(String module, String behavior) implements Key<Citation> {

        @Override
        public String module() {
            return module;
        }

        @Override
        public Answer<Citation> compute(Db db) {
            Answer<Prepared> prepared = db.ask(new Shapes.Prepared(module));
            if (!prepared.present()) {
                return Answer.absent();
            }
            for (Hir.BehaviorDef each : prepared.value().behaviors()) {
                if (each.name().equals(behavior)) {
                    return Answer.of(Citation.of(each.pos()));
                }
            }
            return Answer.absent();
        }
    }

    /**
     * Where one condition of a module's source is written.
     *
     * <p>The sibling of {@link WhereAForkIsWritten}, and asked by the same kind of reader: one that
     * holds a condition it met somewhere else and no place at all. A condition inside a helper
     * expanded into three callers is one condition written once, so where it is is not a question
     * any of the three can answer for itself.
     *
     * <p>Absent where nothing this compilation holds wrote it. What the language itself ships is
     * the case that matters: its conditions stand in every module that calls into it, and no source
     * of this compilation is where they are written.
     */
    public record WhereAConditionIsWritten(WrittenCondition condition) implements Key<Citation> {

        @Override
        public String module() {
            return condition.construct().module();
        }

        @Override
        public Answer<Citation> compute(Db db) {
            if (condition.construct().module() == null) {
                return Answer.absent();
            }
            Answer<WrittenConditions> written =
                    db.ask(new ConditionsWrittenIn(condition.construct().module()));
            if (!written.present()) {
                return Answer.absent();
            }
            SourcePos at = written.value().at(condition);
            return at == null ? Answer.absent() : Answer.of(Citation.of(at));
        }
    }

    /**
     * Where each condition one module's source wrote stands.
     *
     * <p>Under {@link WhereAConditionIsWritten} for the reason {@link ForksWrittenIn} is under
     * {@link WhereAForkIsWritten}: what a report means is one place, and one walk answers for every
     * place at once. Asked condition by condition, the answer would be built again for each
     * sentence written about one.
     */
    record ConditionsWrittenIn(String name) implements Key<WrittenConditions> {

        @Override
        public String module() {
            return name;
        }

        @Override
        public Answer<WrittenConditions> compute(Db db) {
            Answer<AuthoredSites.Walked> walked = db.ask(new Walk(name));
            return walked.present() ? Answer.of(walked.value().conditions()) : Answer.absent();
        }
    }

    /**
     * Where each application one module's source wrote stands.
     *
     * <p>Under {@link WhereARuleIsWritten} for the reason {@link ForksWrittenIn} is under
     * {@link WhereAForkIsWritten}: what a report means is one place, and one walk answers for every
     * place at once.
     */
    record ApplicationsWrittenIn(String name) implements Key<WrittenApplications> {

        @Override
        public String module() {
            return name;
        }

        @Override
        public Answer<WrittenApplications> compute(Db db) {
            Answer<AuthoredSites.Walked> walked = db.ask(new Walk(name));
            return walked.present() ? Answer.of(walked.value().applications()) : Answer.absent();
        }
    }

    /**
     * Where one rule the author wrote rather than named is written.
     *
     * <p>Asked of the module that wrote it, whichever module read it. A helper expanded into three
     * callers holds one rule written once, so where it is written is not a question any of the three
     * can answer for itself — and a caller that answered it would say where its own copy came to
     * stand.
     *
     * <p><b>Which table by what the rule is, and no {@code default}.</b> A rule the author wrote is
     * a fork, a comparison or a predicate, and the source wrote a different construct for each: what
     * a body takes an arm of, what a row had to satisfy, and a call. One table asked about all three
     * would answer that nobody wrote two of them, which is a false answer rather than a missing one
     * ({@link WhereAForkIsWritten}). A kind added to the seal is a compile error here.
     *
     * <p><b>Only where the rule has a name and the seal does not.</b> A rule the author named is
     * found by that name from anywhere, so there is no place to ask for and no question to put; that
     * is why this takes {@link RuleRef.Written} rather than a rule.
     *
     * <p>Absent where nothing this compilation holds wrote the rule. What the language itself ships
     * is the case that matters: its bodies are read from every module that calls into them, and no
     * source of this compilation is where they are written — which is the reading that places such a
     * rule instead ({@code RuleReportAnchor}).
     */
    public record WhereARuleIsWritten(RuleRef.Written rule) implements Key<Citation> {

        @Override
        public String module() {
            return rule.origin().module();
        }

        @Override
        public Answer<Citation> compute(Db db) {
            String module = rule.origin().module();
            if (module == null) {
                return Answer.absent();
            }
            SourcePos at = switch (rule) {
                case RuleRef.Fork it -> {
                    Answer<WrittenForks> written = db.ask(new ForksWrittenIn(module));
                    yield written.present() ? written.value().at(it.origin()) : null;
                }
                // A comparison is a binary, which is what the writing module files every one of
                // under the identity a condition is asked by. Which of them a reading calls a rule
                // is that reading's answer and is not asked again here.
                case RuleRef.Comparison it -> {
                    Answer<WrittenConditions> written = db.ask(new ConditionsWrittenIn(module));
                    yield written.present()
                            ? written.value().at(new WrittenCondition.Construct(it.origin()))
                            : null;
                }
                case RuleRef.Predicate it -> {
                    Answer<WrittenApplications> written =
                            db.ask(new ApplicationsWrittenIn(module));
                    yield written.present() ? written.value().at(it.origin()) : null;
                }
            };
            return at == null ? Answer.absent() : Answer.of(Citation.of(at));
        }
    }

    /**
     * Where a report about {@code anchor}'s condition points.
     *
     * <p>The one place the two questions come back together, and a switch rather than a fallback,
     * for the reason {@link #placeOf(Db, ArmReportAnchor)} is one. Asked the other way round, a
     * condition written in a file this compilation has stopped holding would quietly be reported
     * wherever a reading met a copy of it.
     *
     * @throws NothingPlacesIt where the question the anchor names has no answer
     */
    public static Citation placeOf(Db db, ConditionReportAnchor anchor) {
        Answer<Citation> at = switch (anchor) {
            case ConditionReportAnchor.WhereItIsWritten(WrittenCondition condition) ->
                    db.ask(new WhereAConditionIsWritten(condition));
            case ConditionReportAnchor.WhereTheReadingMetIt(String module,
                    ConditionOccurrence condition) ->
                    metIn(db, module, condition);
        };
        if (!at.present()) {
            throw new NothingPlacesIt("a condition reported at " + anchor);
        }
        return at.value();
    }

    /** Where the reading of {@code condition}'s body met it, as an answer that may be missing. */
    private static Answer<Citation> metIn(Db db, String module, ConditionOccurrence condition) {
        Answer<Map<ConditionOccurrence, Citation>> met =
                db.ask(new Adequacy.ConditionsMet(module, condition.behavior()));
        if (!met.present()) {
            return Answer.absent();
        }
        Citation at = met.value().get(condition);
        return at == null ? Answer.absent() : Answer.of(at);
    }

    /**
     * Where a report about {@code anchor}'s arm points.
     *
     * <p>The one place the two questions come back together, and a switch rather than a fallback:
     * which of them to ask is what the anchor says, so an arm whose place cannot be worked out is
     * an answer missing rather than a reason to ask the other one. Asked the other way round, an
     * arm written in a file this compilation has stopped holding would quietly be reported at a
     * call instead, and the sentence would go on reading as though it were the fork.
     *
     * @throws NothingPlacesIt where the question the anchor names has no answer
     */
    public static Citation placeOf(Db db, ArmReportAnchor anchor) {
        Answer<Citation> at = switch (anchor) {
            case ArmReportAnchor.WhereItIsWritten(SourceConstructOrigin origin) ->
                    db.ask(new WhereAForkIsWritten(origin));
            case ArmReportAnchor.WhereItWasReached(String module, int controlId) ->
                    db.ask(new WhereAPlanReached(module, controlId));
        };
        if (!at.present()) {
            throw new NothingPlacesIt("an arm reported at " + anchor);
        }
        return at.value();
    }

    /**
     * Raised where a report asks where the thing it is about is, and nothing this compilation holds
     * places it.
     *
     * <p>Two of this compiler's answers disagreeing, and one word for it however the report was
     * going to be written. A finding is about something some reading of this compilation reached,
     * and what places it is the module said to have written it; a question that comes back with
     * nothing means those two are not of one compilation. Answered with a place that points
     * nowhere, the report would send a reader to code nobody wrote — so it is raised here, and a
     * second word for it at each kind of subject would be the same fault told three ways.
     */
    public static final class NothingPlacesIt extends IllegalStateException {

        private static final long serialVersionUID = 1L;

        public NothingPlacesIt(Object subject) {
            super("nothing this compilation holds places " + subject);
        }
    }
}
