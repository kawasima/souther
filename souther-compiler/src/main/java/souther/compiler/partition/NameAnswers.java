package souther.compiler.partition;

import souther.compiler.check.AffineForms;
import souther.compiler.check.DeclarationNewtypes;
import souther.compiler.check.Symbols;
import souther.compiler.core.Core;
import souther.compiler.inputs.Denotation;
import souther.compiler.inputs.InputReads;
import souther.compiler.inputs.ReadMeaning;

import java.util.ArrayList;
import java.util.List;

/**
 * What the reading of a body's names comes to, in the shape the walks over a body ask for.
 *
 * <p>Two walks meet every name of a body — the arithmetic that finds the line a rule draws, and the
 * one that says which positions a rule is about — and each asks its own interface for the same two
 * facts. What a name means is {@link InputReads#meaningOf}'s single answer; what is here is the
 * projection of that answer onto the pair those interfaces are written in.
 *
 * <p>Written once because the two walks disagreeing about a name is not a difference of opinion but
 * a defect with a shape: one of them reads a rule and draws its line while the other cannot name the
 * position the line divides, after which the position is reported as one the model divides no way.
 * Kept as two copies, that is one edit away at all times.
 */
final class NameAnswers {

    /** The one value {@code read}'s name denotes, or null where it denotes none. */
    static AffineForms.ReadThrough<InputReads> denoting(Core.Read read, InputReads at,
                                                        Symbols symbols,
                                                        DeclarationNewtypes newtypes) {
        return at.meaningOf(read, symbols, newtypes) instanceof ReadMeaning.Through through
                ? asked(through.denotes()) : null;
    }

    /**
     * Every value {@code read}'s name can stand for, or null where the reading has not got them all.
     *
     * <p>A container an operation built answers nothing here, which is what it answered before there
     * was anything to write out.
     */
    static List<AffineForms.ReadThrough<InputReads>> alternativesOf(Core.Read read, InputReads at,
                                                                    Symbols symbols,
                                                                    DeclarationNewtypes newtypes) {
        if (!(at.meaningOf(read, symbols, newtypes) instanceof ReadMeaning.OneOf one)) {
            return null;
        }
        List<AffineForms.ReadThrough<InputReads>> each = new ArrayList<>();
        one.alternatives().forEach(stands -> each.add(asked(stands)));
        return each;
    }

    /** A value and what to read it in, as the walks spell that pair. */
    private static AffineForms.ReadThrough<InputReads> asked(Denotation stands) {
        return new AffineForms.ReadThrough<>(stands.value(), stands.at());
    }

    private NameAnswers() {}
}
