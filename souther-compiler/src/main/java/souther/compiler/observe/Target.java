package souther.compiler.observe;

import souther.compiler.source.SourceId;

import souther.compiler.diag.SourceRendering;

/**
 * What an {@link Incompleteness} is about, as the thing itself rather than as a name and a word for
 * what kind of name it is.
 *
 * <p>A scope and a string are a product, and what they stand for is a sum: each kind of subject is a
 * different thing and answers "is this behavior inside it" a different way. Held as a product, a
 * subject can be spelled for one scope and read for another, and a scope can be added that no reader
 * answers for — which is what happened to {@code POSITION}. Nothing wrote one into the list a report
 * reads, so the branch that would have answered for it was never reached, and the fallback answered
 * true for every behavior in the module.
 *
 * <p>A position knows which behavior it is in, so it says so. Reading that back out of
 * {@code submit/request.kind} would be putting the same distinction back into a string.
 *
 * <p>Deliberately not {@code AxisId}. That is the partition's identity for an axis and it lives
 * there; this is what a reason needs to say whose measurement it counts against. The two happen to
 * be spelled alike and are not the same thing, and the conversion belongs on the partition's side,
 * where the axis is.
 */
public sealed interface Target {

    /** What this is about, as an identity: what two reasons are compared on and what a build reads. */
    String subject();

    /**
     * The same, as a person is shown it, given what the caller calls its sources.
     *
     * <p>Answered by the sum rather than by the renderer, because which subjects are identities and
     * which are already names is what the kinds differ in. A behavior, a module and a position are
     * written as the author wrote them and are shown as they are; a source is named by the caller,
     * whose id for it may be a number. A renderer asking {@link #subject} and printing the answer
     * cannot tell those apart, and printed a source index as though it were a file name.
     */
    String shown(SourceRendering rendering);

    /**
     * The source this is about, where what it names is one. Empty otherwise.
     *
     * <p>Which subjects are a source's identity is what the kinds differ in, so the sum answers it
     * for the reason {@link #shown} is answered here: a source is identified by whoever handed it
     * over and the rest are named by the author. A reader working it out from {@link #scope} would be
     * spelling out a classification this has already made.
     *
     * <p>What the answer is for is not part of it. A document that has to explain the identities it
     * writes down keeps that account where it writes them, and nothing here knows one is being
     * written.
     */
    java.util.Optional<SourceId> sourceIdentity();

    /** Which kind of thing {@link #subject} identifies. */
    Incompleteness.Scope scope();

    /**
     * The behavior this is about, where the thing it names is inside one. Empty otherwise.
     *
     * <p>One answer to that question and not three. It was asked as a scope test beside a subject
     * comparison, twice, and as a containment question a third time, and a scope that answered the
     * first two differently from the third is how a position came to count against a module.
     *
     * <p>This much and no more. Whether a behavior is inside a <em>source</em> is not something a
     * source id says — it is a fact about the compilation that wrote the rows — so nothing here
     * answers it. What a reason counts against is {@link Incompleteness#countsAgainst}'s, where the
     * reading that settles it can be stated.
     */
    java.util.Optional<String> onlyBehavior();

    /** One behavior. Only that behavior's measures are affected. */
    record OfBehavior(String behavior) implements Target {

        @Override
        public String subject() {
            return behavior;
        }

        @Override
        public String shown(SourceRendering rendering) {
            return behavior;
        }

        @Override
        public java.util.Optional<SourceId> sourceIdentity() {
            return java.util.Optional.empty();
        }

        @Override
        public Incompleteness.Scope scope() {
            return Incompleteness.Scope.BEHAVIOR;
        }

        @Override
        public java.util.Optional<String> onlyBehavior() {
            return java.util.Optional.of(behavior);
        }
    }

    /**
     * One source, as the compile identifies it.
     *
     * <p>{@code sourceId} is opaque: it is whatever the caller handed its sources over as, which is
     * a position in a list for a build and a document URI for an editor. Nothing here may read it as
     * a file name — {@link #shown} asks the caller for that — and nothing may read a path out of it.
     *
     * <p>What it holds is not part of it either: which behaviors wrote rows in it is the
     * compilation's answer rather than the id's.
     */
    record OfSource(SourceId sourceId) implements Target {

        @Override
        public String subject() {
            return sourceId.value();
        }

        @Override
        public String shown(SourceRendering rendering) {
            return rendering.names().nameOf(sourceId);
        }

        @Override
        public java.util.Optional<SourceId> sourceIdentity() {
            return java.util.Optional.of(sourceId);
        }

        @Override
        public Incompleteness.Scope scope() {
            return Incompleteness.Scope.SOURCE;
        }

        @Override
        public java.util.Optional<String> onlyBehavior() {
            return java.util.Optional.empty();
        }
    }

    /** The module, and so everything in it. */
    record OfModule(String module) implements Target {

        @Override
        public String subject() {
            return module;
        }

        @Override
        public String shown(SourceRendering rendering) {
            return module;
        }

        @Override
        public java.util.Optional<SourceId> sourceIdentity() {
            return java.util.Optional.empty();
        }

        @Override
        public Incompleteness.Scope scope() {
            return Incompleteness.Scope.MODULE;
        }

        @Override
        public java.util.Optional<String> onlyBehavior() {
            return java.util.Optional.empty();
        }
    }

    /**
     * One row of one behavior, and so what that row would have decided.
     *
     * <p>The row and not the behavior it is written for. A behavior may have more than one row that
     * did not come back, and they are more than one thing to go and look at — carried as the
     * behavior, they were one identity and the second was dropped wherever these are kept one per
     * identity (issue #996).
     */
    record OfRow(RowRef rowRef) implements Target {

        @Override
        public String subject() {
            return rowRef.behavior() + "/" + rowRef.source().value() + "/"
                    + rowRef.identity().shown();
        }

        @Override
        public String shown(SourceRendering rendering) {
            // The row decides which of its parts a reader needs; the caller decides what its file
            // is called. Written the other way round, a renderer would be choosing how much of an
            // identity to show, which is the row's to say.
            return rowRef.shown(rendering.names().nameOf(rowRef.source()));
        }

        @Override
        public java.util.Optional<SourceId> sourceIdentity() {
            // The source the row is written in, which is not what this is about. A reason about a
            // source is a source that was not read at all; this one was read, and naming its file
            // here would put it among the reasons about whole files.
            return java.util.Optional.empty();
        }

        @Override
        public Incompleteness.Scope scope() {
            return Incompleteness.Scope.ROW;
        }

        @Override
        public java.util.Optional<String> onlyBehavior() {
            return java.util.Optional.of(rowRef.behavior());
        }
    }

    /** A position inside one behavior's input — a path through a value, and the behavior it is in. */
    record AtPosition(String behavior, String path) implements Target {

        @Override
        public String subject() {
            return behavior + "/" + path;
        }

        @Override
        public String shown(SourceRendering rendering) {
            return subject();
        }

        @Override
        public java.util.Optional<SourceId> sourceIdentity() {
            return java.util.Optional.empty();
        }

        @Override
        public Incompleteness.Scope scope() {
            return Incompleteness.Scope.POSITION;
        }

        @Override
        public java.util.Optional<String> onlyBehavior() {
            return java.util.Optional.of(behavior);
        }
    }

    /**
     * The target a scope and a subject stand for, where the subject is a name.
     *
     * <p>Two are not among them, and for the same reason: their subject is not a name.
     * {@code POSITION} is two names and cannot be recovered from one, so a producer uses
     * {@link Incompleteness#atPosition} and passes both. {@code SOURCE} is a {@link SourceId} — what
     * a compilation files a source under, which a module name and a display name are not — and
     * rebuilding one out of a spelling here is the way round the type that says so. A producer uses
     * {@link Incompleteness#ofSource} and passes the identity it already holds.
     */
    static Target of(Incompleteness.Scope scope, String subject) {
        return switch (scope) {
            case BEHAVIOR -> new OfBehavior(subject);
            case MODULE -> new OfModule(subject);
            case SOURCE -> throw new IllegalArgumentException(
                    "a source is identified, not spelled: " + subject);
            case POSITION -> throw new IllegalArgumentException(
                    "a position needs the behavior it is in: " + subject);
            case ROW -> throw new IllegalArgumentException(
                    "a row is identified by its behavior, its source and its own name: " + subject);
        };
    }
}
