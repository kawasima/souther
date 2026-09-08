package souther.compiler.types;

/**
 * Why an application is in the body, said by whoever put it there.
 *
 * <p>A body holds applications from more than one hand. An author writes one. A pass writes the
 * block a name used as a value stands for, and the application inside it is that expansion's. A pass
 * writes one where the language has no syntax for what a body means — the library operation an
 * empty collection stands for. And the generator composes one for a row it offers.
 *
 * <p><b>Said where the application is made, and never worked out from its shape afterwards.</b> The
 * producer knows why it is writing one; a reader meeting it later has only the shape, and a shape is
 * shared: an application no source wrote looks the same whether a name was expanded into it, a
 * library operation was reached for, or a row was composed. A reader that guessed from the shape got
 * the common case right and answered the rest with whatever the common case says.
 *
 * <p><b>Not the same question as which construction reached the body.</b> That is
 * {@link ConstructionOrigin}'s, and a pass rewriting one has nothing to say about this. Nor is it
 * what was applied, which the callee answers.
 *
 * <p>Four arms, which is what the producers of an application in this compiler are. A fifth producer
 * is a question about what its applications are occurrences of, and the way it gets asked is that
 * the readers below stop compiling until it is given an arm. A name meaning "some pass composed it"
 * is how the question would go unasked.
 */
public sealed interface ApplicationOrigin {

    /**
     * An application that can be told from every other of its kind.
     *
     * <p>Not a list of the applications something expands. It is the ones that already carry enough
     * to be told apart — the construct an author wrote, the name a block was expanded from, a
     * construct and the count of what was derived from it — so a reader that has to number one
     * application among others may hold this and nothing more.
     *
     * <p>What is outside it is {@link ComposedFixture}, which says why it is here and no more. A
     * reader wanting to tell two of those apart is a reader asking a question nothing asks yet, and
     * the way it gets asked is that this type will not hold one: the answer is designed then rather
     * than invented now, and until then no two of them can quietly become one.
     */
    sealed interface Identified extends ApplicationOrigin permits Written, Eta, Derived {
    }

    /**
     * What an application composed out of {@code from} says about where it came from, given how
     * this composer would name a cause.
     *
     * <p>The one place the question is put. A pass that composes an application out of another —
     * writing a call back out of a checked value, reading a comparison as the size it means — has
     * the same three cases to answer, and answering them apiece is how one reader comes to answer
     * differently from the next: one told the three apart and one asked only whether it had an
     * identity, so a composed fixture arrived as a term with nothing behind it.
     *
     * <p>The cases, and why each is what it is. An application that can be told from every other of
     * its kind is what a derivation is derived from, so it is one. One composed for a fixture is not
     * a derivation of anything — two of them carry the same answer, and a derivation of one would
     * equal a derivation of the other while claiming to be its own occurrence — so what comes out of
     * a composed thing is another composed thing.
     *
     * <p>Which is separate from what the composed thing <em>means</em>. Every case comes out with
     * the same meaning; they differ only in how much can be said about where it came from.
     *
     * <p><b>Which of the three it is in, is this type's to say; which of what it composed it is, is
     * the composer's.</b> One cause may make a pass write more than one thing, and only that pass
     * knows how many — so {@code ordinal} is taken rather than assumed. Assumed, this would hand
     * the same number to every composer, and a pass that composed twice from one cause would come
     * out with two values that are one.
     *
     * @param ordinal which of the things this composer derives from {@code from} is being made, by
     *                the composer's own count over them
     */
    static ApplicationOrigin composedOutOf(
            ApplicationOrigin from, int ordinal,
            java.util.function.Function<Identified, ApplicationDerivationCause> cause) {
        return switch (from) {
            case Identified identified -> new Derived(cause.apply(identified), ordinal);
            case ComposedFixture _ -> new ComposedFixture();
        };
    }

    /**
     * An application the author wrote.
     *
     * <p>The construct, which every application takes when it is read
     * ({@link SourceConstructOrigin}) and carries through every copy of it. A helper holding a call,
     * expanded at two of its own call sites, has that one call at both.
     */
    record Written(SourceConstructOrigin application) implements Identified {

        public Written {
            // And written as an application. The construct says what the author put there, so one
            // saying they wrote a comparison or a collection is a construct standing for something
            // this is not — and a name that answered for either would be a name saying a fact the
            // value beside it does not.
            if (application == null || !application.isWritten()
                    || application.kind() != SourceConstruct.CALL) {
                throw new IllegalArgumentException(
                        "an application the source wrote is one this source counted as an"
                                + " application: " + application);
            }
        }
    }

    /**
     * The application inside the block a name used as a value was expanded into.
     *
     * <p>No source wrote the application: the author wrote a name, and the block applying it is what
     * a body holds where a function value goes. So it is named by what made it necessary
     * ({@link EtaOrigin}) rather than by the application, there being none to name.
     */
    record Eta(EtaOrigin cause) implements Identified {

        public Eta {
            if (cause == null) {
                throw new IllegalArgumentException(
                        "a block a name was expanded into was expanded from some name");
            }
        }
    }

    /**
     * An application a pass wrote because of something it can name.
     *
     * <p>The library operation a collection written in brackets stands for, the operation a checked
     * value is written back as, the call a name read where a value goes is built as. What names this
     * is that cause, and the producer's own count over what it derived from it, because one cause
     * may make a pass write more than one thing.
     *
     * <p>The cause is not always a construct the author wrote. What it has to be is something that
     * can be told from every other of its kind — a construct, an application, a reference a run
     * composed — because that is what this is derived from.
     */
    record Derived(ApplicationDerivationCause cause, int ordinal) implements Identified {

        public Derived {
            if (cause == null) {
                throw new IllegalArgumentException(
                        "an application a pass wrote was written because of something it can name");
            }
            if (ordinal < 0) {
                throw new IllegalArgumentException(
                        "what a cause derived is counted from zero: " + ordinal);
            }
        }
    }

    /**
     * An application composed for a fixture: a value this compiler writes to stand at a position,
     * rather than one quoted from a body.
     *
     * <p>No source wrote it and no construct a source wrote is behind it. What it says is why it is
     * here and no more — which of the composed applications it is, it does not say, and nothing asks:
     * a fixture is a value shown for a position, and no reader of one tells two of them apart by
     * their applications.
     *
     * <p><b>And the question is not asked here in advance of a reader.</b> Several places compose
     * fixtures — the representatives a primitive's classes are shown with, the values a rule's
     * witnesses are built from, the rows a generation offers — and they are separate recipes rather
     * than one run. Numbering them would mean deciding what they are numbered within, which is a
     * question about a synthesis nothing yet performs. The day a reader has to tell two composed
     * applications apart, that reader says what it is asking and the answer is designed then.
     *
     * <p>Which is not the same answer a composed <em>reference</em> gives. That one is numbered
     * ({@link FixtureReferenceOrigin}), because a name reaching a declaration is some reference of
     * it and the generator's rows do have to say which. The two questions came out differently, so
     * they are answered differently rather than made to look alike.
     */
    record ComposedFixture() implements ApplicationOrigin {
    }
}
