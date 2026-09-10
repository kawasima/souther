package souther.compiler.types;

/**
 * Which construct the source wrote, said in a way that copying cannot change.
 *
 * <p>A non-recursive helper is expanded at each call, so one construct the author wrote becomes
 * several in the tree that runs. Those copies are separate occurrences — each is emitted, probed and
 * reasoned about on its own — and they are one construct: the author wrote one {@code match}, and
 * writing rows for it twice covers nothing the first set did not. This is what says they are one.
 *
 * <p><b>Named for the source and not for one of its readers.</b> A coverage obligation is derived
 * from some of these, which is what this used to be called after — and a rule read off an
 * application, an application a pass expands, and an arithmetic expression that owes nothing are all
 * one of these too. What every reader shares is the question it answers: which construct of the
 * source is this.
 *
 * <p><b>The identity of a construct the source wrote, and nothing about what is owed for it.</b>
 * Which of them a coverage obligation comes from is a later reader's answer and a narrower set than
 * this: an arithmetic binary expression has one of these and owes nothing, an application has one
 * and owes rows only where a rule is read off it, and a fork owes an arm either way. Read as "a
 * construct a row can be owed for", this value would be one nobody could hand to the constructs that
 * are not.
 *
 * <p><b>Carrying one is not being instrumented and is not being numbered.</b> What a probe copies
 * off the stack is {@link souther.compiler.coverage.SourceOutcome}'s answer, and an arithmetic
 * binary expression has an origin and no number today. A rule about the strings at a position is the
 * other way round from a fork: it owes rows because it divides the position into classes, and no run
 * through it is recorded at all. So the two questions are asked separately, and a reader that took
 * an origin for a probe site would be answering the second with the first.
 *
 * <p>Minted where such a construct is read, and carried from there on. A pass
 * that rewrites one keeps the origin it was given; a pass that turns one construct into several forks
 * derives them with {@link #lowered}, so what a comprehension's guards get is a function of the
 * comprehension rather than of when the lowering ran. Nothing else makes one — an origin minted after
 * expansion would give two copies of one fork two origins, which is the thing this exists to prevent.
 * A record's constructor is public and takes every component, so that last sentence is held against
 * the compiled classes rather than left to be read: what may settle an origin, and every compiled
 * class of this repository that names one of those makers, is written down in {@code
 * WhoMaySettleASourceConstructOriginTest}.
 *
 * <p>Not a name anything outside one compilation can be matched by. {@code ordinal} is the builder's
 * own count over what {@link #owner} names, and the builder does not take the numbers in the order
 * the constructs are written: a statement guard folds the rest of its block before numbering itself,
 * and a comprehension is numbered after its parts. What identity needs is that the numbering be a
 * function of the owner's syntax and that no two constructs of one owner share an ordinal, which it
 * is and they do not — the two together are what tell one construct from every other, and two
 * definitions' first constructs are two constructs. Matching one compilation's obligations against
 * another's is a different question and not one this answers.
 *
 * <p>Counted within the owner and not over the file. A count over the file makes the number a
 * function of everything written before it there, so editing one definition renumbers the
 * constructs of every one after it — and an identity that moves for an edit nothing about it can
 * see is not one.
 *
 * @param owner   what wrote the construct: the declaration, the stated behavior, the body, or a
 *                source's rows for a behavior or its stand-in. Null exactly for {@link #unwritten}
 * @param ordinal which construct of that owner, by the builder's own count over it
 * @param lowered which fork of that construct, where a lowering makes more than one out of it. Zero
 *                is the construct's own fork, which is every fork an author writes as one
 * @param kind    what the author wrote there. Not part of what tells one construct from another —
 *                the builder takes a fresh ordinal for every construct it reads of one owner, so no
 *                two origins of that owner share one and nothing here can disagree about a construct
 *                two values name. It is the answer a report needs and the tree that runs no longer
 *                holds
 */
public record SourceConstructOrigin(WrittenOwner owner, int ordinal, int lowered, SourceConstruct kind) {

    public SourceConstructOrigin {
        // Three spellings of one fact, held together rather than left to agree. `isWritten` is asked
        // by readers that have no use for the kind, and a value answering it one way and carrying a
        // construct the other way is one either reader can be right about. The owner goes with them:
        // a number counted within nothing addresses nothing.
        if ((ordinal < 0) != (kind == SourceConstruct.NOT_WRITTEN)) {
            throw new IllegalArgumentException(
                    "an origin says both whether a source wrote it and what was written: "
                            + ordinal + " with " + kind);
        }
        if ((owner == null) != (ordinal < 0)) {
            throw new IllegalArgumentException(
                    "an origin a source wrote says what it was counted within: "
                            + owner + " with " + ordinal);
        }
    }

    /** The construct {@code owner} wrote, counted as {@code ordinal} among the ones written there,
     *  and said as {@code kind}. */
    public static SourceConstructOrigin written(WrittenOwner owner, int ordinal, SourceConstruct kind) {
        return new SourceConstructOrigin(owner, ordinal, 0, kind);
    }

    /** The module whose source wrote the construct, or null where nobody wrote it. Asked of the
     *  owner, which is what knows. */
    public String module() {
        return owner == null ? null : owner.module();
    }

    /**
     * What a comparison rebuilt for an analysis carries, no source having written it.
     *
     * <p>The invariant-discharge reader turns a preserved call back into a comparison so it can be
     * read as one. That tree is not the tree that runs — coverage numbering refuses a preserved call
     * outright — so nothing here is ever a coverage obligation, and a value that could pass for one
     * would be worse than a value that cannot.
     */
    public static SourceConstructOrigin unwritten() {
        return UNWRITTEN;
    }

    private static final SourceConstructOrigin UNWRITTEN =
            new SourceConstructOrigin(null, -1, 0, SourceConstruct.NOT_WRITTEN);

    /** Whether a source wrote the construct this names. False only for {@link #unwritten}. */
    public boolean isWritten() {
        return ordinal >= 0;
    }

    /**
     * One of the forks a lowering makes out of this construct, numbered by the part of the construct
     * it came from — a list comprehension's guards by their place in the list.
     *
     * <p>Derived rather than minted, so the same guard of the same comprehension answers the same
     * whether the lowering runs before an expansion copies it or after.
     *
     * @throws IllegalStateException where this is already one of a construct's lowered forks. One
     *                               level is all any lowering needs, and a second would fold two
     *                               different parts into one number
     */
    public SourceConstructOrigin lowered(int part) {
        if (lowered != 0) {
            throw new IllegalStateException(
                    "a lowered fork cannot be lowered again: " + this + " part " + part);
        }
        // The kind comes along. A guard of a comprehension is a fork of that comprehension, and a
        // fork that arrived saying it was written as something else would be the construct this
        // whole value exists to keep hold of, lost one lowering in.
        return new SourceConstructOrigin(owner, ordinal, part + 1, kind);
    }
}
