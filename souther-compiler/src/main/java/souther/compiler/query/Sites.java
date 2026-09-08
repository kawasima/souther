package souther.compiler.query;

import souther.compiler.ast.Hir;
import souther.compiler.core.Core;
import souther.compiler.coverage.ArmLocations;
import souther.compiler.coverage.ArmReportAnchor;
import souther.compiler.coverage.ControlPointId;
import souther.compiler.diag.Citation;
import souther.compiler.diag.SourcePos;
import souther.compiler.sites.AuthoredSites;
import souther.compiler.sites.WrittenConstructs;
import souther.compiler.types.SourceConstructOrigin;

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
            Answer<Hir.Module> resolved = db.ask(new Names.Resolved(name));
            if (!resolved.present()) {
                return Answer.absent();
            }
            return AuthoredSites.of(resolved.value()) instanceof
                    AuthoredSites.Census.Identified(AuthoredSites sites)
                    ? Answer.of(sites) : Answer.absent();
        }
    }

    /**
     * Where each construct {@code name}'s source wrote stands.
     *
     * <p>Beside {@link Authored} and answered from the same walk, because a module wrote what it
     * wrote once. Told apart by what a reader holds: an editor holds a place and asks what is
     * there, and this is asked by a reader that holds a construct and no place at all.
     */
    record WrittenIn(String name) implements Key<WrittenConstructs> {

        @Override
        public String module() {
            return name;
        }

        @Override
        public Answer<WrittenConstructs> compute(Db db) {
            Answer<Hir.Module> resolved = db.ask(new Names.Resolved(name));
            return resolved.present()
                    ? Answer.of(AuthoredSites.constructsOf(resolved.value())) : Answer.absent();
        }
    }

    /**
     * Where one construct is written.
     *
     * <p>Beside what a reading of it came to, and not inside it. A fork the source wrote and an arm
     * of it that no row goes through are two facts about one construct, and a reader uses one of
     * them: what a warning says is read off the reading, and where to put the caret is read off the
     * module that wrote the fork. Answered together, an edit that moves a helper and changes
     * nothing it does is an edit to every reading of every module that calls it.
     *
     * <p>One construct and not a module's. A report is about the fork it is about, and that is the
     * whole of what it reads here; answered a module at a time, a report pointing at one fork would
     * depend on where every other one in that module is.
     *
     * <p>The module that wrote it answers, whichever module the reading was made in. A helper
     * expanded into three callers is one construct written once, so where it is written is not a
     * question any of the three can answer for itself — and a caller that answered it would say
     * where its own copy came to stand.
     *
     * <p>Absent where nothing this compilation holds wrote the construct. What the language itself
     * ships is the case that matters: its forks stand in every module that calls into it, and no
     * source of this compilation is where they are written.
     */
    public record WhereAConstructIsWritten(SourceConstructOrigin origin) implements Key<Citation> {

        @Override
        public String module() {
            return origin.module();
        }

        @Override
        public Answer<Citation> compute(Db db) {
            if (origin.module() == null) {
                return Answer.absent();
            }
            Answer<WrittenConstructs> written = db.ask(new WrittenIn(origin.module()));
            if (!written.present()) {
                return Answer.absent();
            }
            SourcePos at = written.value().at(origin);
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
     * together with whose plan it is; one module has one plan here, which is what makes the pair an
     * address and not half of one.
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
            Answer<Bodies.Elaborated> checked = db.ask(new Bodies.Checked(module));
            if (!checked.present() || checked.value() == null) {
                return Answer.absent();
            }
            for (Map.Entry<Core, ControlPointId.ArmOccurrence[]> forked
                    : checked.value().plan().armsByNode().entrySet()) {
                for (ControlPointId.ArmOccurrence arm : forked.getValue()) {
                    if (arm.controlId() == controlId) {
                        return Answer.of(Citation.of(forked.getKey().pos()));
                    }
                }
            }
            return Answer.absent();
        }
    }

    /**
     * Where a module writes one of its behaviors.
     *
     * <p>What a report about a behavior points at when what it is about is the behavior itself —
     * a case no row expects, a position nothing divides. The definition and not the name it
     * declares: a reader is being shown the behavior, which is what the sentence is about.
     *
     * <p>One behavior at a time, so a report about this one does not move when the one above it
     * gains a line.
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
            Answer<souther.compiler.check.Prepared> prepared = db.ask(new Shapes.Prepared(module));
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
     * Where a report about {@code anchor}'s arm points.
     *
     * <p>The one place the two questions come back together, and a switch rather than a fallback:
     * which of them to ask is what the anchor says, so an arm whose place cannot be worked out is
     * an answer missing rather than a reason to ask the other one. Asked the other way round, an
     * arm written in a file this compilation has stopped holding would quietly be reported at a
     * call instead, and the sentence would go on reading as though it were the fork.
     *
     * @throws NothingIsWrittenThere where the question the anchor names has no answer
     */
    public static Citation placeOf(Db db, ArmReportAnchor anchor) {
        Answer<Citation> at = switch (anchor) {
            case ArmReportAnchor.WhereItIsWritten(SourceConstructOrigin origin) ->
                    db.ask(new WhereAConstructIsWritten(origin));
            case ArmReportAnchor.WhereItWasReached(String module, int controlId) ->
                    db.ask(new WhereAPlanReached(module, controlId));
        };
        if (!at.present()) {
            throw new NothingIsWrittenThere(anchor);
        }
        return at.value();
    }

    /**
     * Where any arm is reported, for a surface that is about to write sentences about several.
     *
     * <p>One of these for the whole compilation, for the reason {@link #placeOf} gives: which arm
     * is being asked about is the only input there is.
     */
    public static ArmLocations armLocations(Db db) {
        return anchor -> placeOf(db, anchor);
    }

    /**
     * Raised where a report asks where an arm is and the question its anchor names has no answer.
     *
     * <p>Two of this compiler's answers disagreeing. An arm is an arm a reading of some body
     * reached, and the anchor says which of the two questions places it; a question that comes back
     * with nothing means the body the arm was read in and the module said to hold it are not of one
     * compilation. Answered with a place that points nowhere, the report would send a reader to a
     * fork nobody wrote.
     */
    public static final class NothingIsWrittenThere extends IllegalStateException {

        private static final long serialVersionUID = 1L;

        NothingIsWrittenThere(ArmReportAnchor anchor) {
            super("nothing this compilation holds places an arm reported at " + anchor);
        }
    }
}
