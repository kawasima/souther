package souther.compiler.meta;

import souther.compiler.ast.Ast;
import souther.compiler.check.BehaviorImplementation;
import souther.compiler.check.Scoping;
import souther.compiler.diag.LaidOutText;
import java.util.List;

import java.util.Map;
import java.util.Set;

/**
 * A module this compiler restored from an artifact and then checked: everything an importer needs to
 * build against it, and nothing that was still being worked out.
 *
 * <p>Holding one is holding a module whose import lines have been read. There is no value for a
 * module that was decoded and not checked, and none for a module that was never read at all — the
 * only implementation is {@link ModuleReadback}'s, and only a reading that answered
 * {@link Readback.Ready} builds one. So the reader that took the module and left the table behind
 * cannot be written, which is what it did, leaving an invariant's bare names resolving against
 * nothing in every project but the one that wrote it.
 *
 * <p>Sealed rather than a record, because a record is a constructor. Read anywhere, built in one
 * place: outside {@code souther.compiler.meta} nothing can make one at all, and inside it the one
 * implementation is the readback's own.
 *
 * <p>The three are one fact and travel together. The module no longer says what its bare names mean
 * — the lines that said so are dropped once read — so {@link #libraryClaims()} is the only thing that
 * does. Where a behavior's body comes from is not written in any declaration and does not survive as
 * source, so it cannot be worked out again from the module either.
 *
 * <p>Told apart from {@link PublishedUniverse.Read}, which is a stage further on: that one holds a
 * module every name of which has been answered, against a universe of other modules. This is what
 * one artifact gives before any of that is asked.
 */
public sealed interface ReadableModule permits ModuleReadback.AsRead {

    /** The module as the front end read it, with its library import lines dropped. */
    Ast.Module module();

    /**
     * What it declares, by the name written there — indexed once, where it was read.
     *
     * <p>Here rather than worked out by whoever needs it, because indexing is a step of the reading
     * and can refuse: a set of declarations one module may not have is an artifact this compiler
     * will not read ({@link Readback.Failure.InvalidDeclarations}). A reader that indexed for itself
     * would be asking, after the fact, a question the reading has already answered — and would have
     * to decide what to do when the answer is no, which is how a known failure came to travel as a
     * raise out of a lookup.
     */
    Map<String, Ast.Def> declarations();

    /** Where each behavior's body comes from, as the module that declared it decided.
     *
     * <p>Carried rather than derived. A module here published no {@code let}, so a reader working it
     * out again has two states to sort three declarations into — and the one it would get wrong is
     * the behavior Souther is to implement and nobody has, which would arrive as Java's to supply
     * (issue #936). */
    Map<String, BehaviorImplementation> behaviorImplementations();

    /** The behaviors of it Java supplies, read off the states above. */
    default Set<String> injectedBehaviors() {
        Set<String> injected = new java.util.LinkedHashSet<>();
        behaviorImplementations().forEach((name, implementation) -> {
            if (implementation.isInjectionTarget()) {
                injected.add(name);
            }
        });
        return injected;
    }

    /** The behaviors of it Souther is to implement and nobody has. */
    default Set<String> unwrittenBehaviors() {
        Set<String> unwritten = new java.util.LinkedHashSet<>();
        behaviorImplementations().forEach((name, implementation) -> {
            if (implementation == BehaviorImplementation.UNIMPLEMENTED) {
                unwritten.add(name);
            }
        });
        return unwritten;
    }

    /** What its library import lines brought in, which the module itself no longer says. */
    List<Scoping.Claim> libraryClaims();

    /**
     * How the text this was read back from is laid out.
     *
     * <p>Part of the reading and not an index somebody could build afterwards. The places in
     * {@link #module()} say which of the things written in that text they are, and the text is one
     * this compiler put back together out of what the module carries — so there is no file to go and
     * read it from later, and rebuilding it would be rebuilding a second text and hoping it came out
     * the same. What the reading parsed is what answers here.
     *
     * <p>What it is for is the code this module's bodies are spliced into elsewhere: the line a debug
     * table records for such an instruction is a line of this text.
     */
    LaidOutText laidOutText();
}
