package souther.compiler.diag;

import java.util.Objects;

/**
 * Where code this compile has no file for came from, and the name a reader here reaches it by.
 *
 * <p>Two facts about one body of code that are not the same fact, and neither is read off the
 * other. {@code module} is where the code lives — the module somebody published, the library module
 * the compiler ships. {@link #reachedBy()} is what a report writes: {@code Int.abs},
 * {@code lib.text.atLeast}, and {@code lib.text} itself where what is out of sight is a whole
 * module rather than something in one.
 *
 * <p>They are two components because a refinement of one must not destroy the other. A parse of a
 * published module knows the module and not which of its declarations a caller will land on, so the
 * name is replaced later, when a splice learns it ({@link #reachedBy(String)}). Held as one string
 * that replacement overwrites the module: {@code pricing} becomes {@code pricing.atLeast}, and the
 * day a published module carries where its source is, finding it again would mean splitting a
 * spelling — which is provenance inferred from how a name is written, the thing this exists to
 * stop. An alias, a re-export or a naming rule added later breaks that split without breaking
 * anything that would notice.
 *
 * <p>Which arm it is says what kind of thing this compile is without: a module somebody published,
 * or the library the compiler ships. Nothing reads the arm yet. It is here because the two have
 * different futures — a published module may one day be linked to and the library never will.
 *
 * <p>What this does <em>not</em> say is which source a line and a column are read in. That is a
 * separate question with a separate answer ({@link SourcePos#quotedFrom()}), and reading one off the
 * other is what this exists to stop: a body copied from out of sight is read against the caller's
 * file, so it is out of sight and in a source of this compile at once.
 */
public sealed interface SourceProvenance {

    /**
     * Where this stands among these, for a reader putting some of them in a steady order.
     *
     * <p>Beside the members, so that whoever adds one places it. What the numbers mean is nothing
     * beyond which comes first: they are read only against each other, and only by a reader that
     * has to write the same thing twice over one source and has nothing an author wrote to go on.
     */
    static java.util.Comparator<SourceProvenance> inASteadyOrder() {
        return java.util.Comparator.<SourceProvenance>comparingInt(SourceProvenance::rank)
                .thenComparing(SourceProvenance::module, java.util.Comparator.naturalOrder())
                .thenComparing(SourceProvenance::reachedBy, java.util.Comparator.naturalOrder());
    }

    private static int rank(SourceProvenance provenance) {
        return switch (provenance) {
            case APublishedModule _ -> 0;
            case TheStandardLibrary _ -> 1;
        };
    }

    /** The module the code is written in. */
    String module();

    /** The name a reader here reaches the code by. Where what is out of sight is a whole module
     *  rather than something in one, the module's name is how it is reached, and the two coincide. */
    String reachedBy();

    /** Code in a module somebody else built and published, read back off the module path. */
    record APublishedModule(String module, String reachedBy) implements SourceProvenance {

        public APublishedModule {
            Objects.requireNonNull(module, "code out of sight is written in a module");
            Objects.requireNonNull(reachedBy, "code out of sight is reached by a name");
        }

        /** As a parse of that module stamps it: the module, reached by its own name, there being no
         *  declaration yet for a caller to have landed on. */
        public APublishedModule(String module) {
            this(module, module);
        }
    }

    /** Code in the standard library, which ships with the compiler and is in no source of any
     *  compile that calls it. */
    record TheStandardLibrary(String module, String reachedBy) implements SourceProvenance {

        public TheStandardLibrary {
            Objects.requireNonNull(module, "code out of sight is written in a module");
            Objects.requireNonNull(reachedBy, "code out of sight is reached by a name");
        }

        /** As a load of that module stamps it. */
        public TheStandardLibrary(String module) {
            this(module, module);
        }
    }

    /**
     * The same provenance as the declaration knows it, with no reader's route in it.
     *
     * <p>What a value keyed on the declaration holds. {@link #reachedBy()} is a fact about a reading
     * — two readings of one clause reach it by two names as easily as one — so a value that is meant
     * to be the same for the same declaration cannot carry it and be the same.
     */
    default SourceProvenance asDeclared() {
        return reachedBy(module());
    }

    /**
     * The same provenance, reached by {@code name} instead — what a splice writes when it learns
     * the name the call reaches, the parse having known only the module.
     *
     * <p>Only the name. Where the code is written does not change with how a caller spells its way
     * in, and neither does what kind of thing this compile is without.
     */
    default SourceProvenance reachedBy(String name) {
        return switch (this) {
            case APublishedModule published -> new APublishedModule(published.module(), name);
            case TheStandardLibrary library -> new TheStandardLibrary(library.module(), name);
        };
    }
}
