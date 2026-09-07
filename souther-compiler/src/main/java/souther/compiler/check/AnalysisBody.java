package souther.compiler.check;

import souther.compiler.core.Core;

/**
 * A behavior's body as the analysis reads it, which is not the body the backend emits.
 *
 * <p>Two representations of one body, and they say different things on purpose
 * ({@link souther.compiler.check.InliningPolicy}). The emitted one is an algorithm: an operation of
 * the language is expanded into what it does, because that is what a backend has to write out. This
 * one is a meaning: the operation stands as itself, because what an analysis has rules about is
 * {@code String.startsWith} and not the walk it turns into.
 *
 * <p><b>A type and not a {@code Core}, so that which representation a tree is cannot be read off
 * having one.</b> Both are a {@code Core}, so a reader handed one has no way to tell — and a reader
 * that wanted the meanings and was handed the algorithm finds every operation gone, with nothing
 * refusing it and no error to read. That happened: the rules a body writes about its inputs were
 * read off the emitted tree, where a comparison survives and a predicate over a string does not, so
 * one kind of rule was read and the other was not there to be found.
 *
 * <p><b>And a body that has none is not this one holding nothing.</b> A behavior nothing implements
 * has no body to read either way, and what a reader owed the meanings must do there is say it could
 * not read them — never fall back to the algorithm, which answers the question with a tree the
 * question is not about.
 *
 * <p><b>{@code elements} is of this tree and of no other.</b> Where the elements of what a binding
 * holds came from is written by the expansion that made the binding, and the two representations of
 * one body are two expansions with two sets of bindings — so a reading of this tree handed the other
 * expansion's answer is asking about bindings this tree does not have. The two travel together for
 * that reason, and a reader takes the pair rather than putting one beside the other.
 */
public record AnalysisBody(Core core, ElementProvenance elements) {

    public AnalysisBody {
        if (core == null || elements == null) {
            throw new IllegalArgumentException(
                    "a body the analysis reads is some tree; a behavior with none has no reading"
                            + " rather than one holding nothing");
        }
    }
}
