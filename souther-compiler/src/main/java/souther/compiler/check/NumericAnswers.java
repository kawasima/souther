package souther.compiler.check;

import souther.compiler.semantics.NumericResult;
import souther.compiler.stdlib.Stdlib;
import souther.compiler.types.Type;
import souther.compiler.types.ValueName;

/**
 * The type of the number one of the language's operations answers.
 *
 * <p><b>The one entry, so that a caller never assembles this itself.</b> Two things have a say — the
 * library's signature and the shape of what it answers — and a caller that took the result type as
 * it stands would be right for every operation until the first that answers its number inside a
 * union, and wrong there without saying so.
 *
 * <p>Read by three kinds of reader and owned by none of them: what puts an operation in range of a
 * question ({@link Question}), what holds a declared fact to the signature it is about
 * ({@link DischargeRules}), and what lists the representations reading a number
 * ({@link NumericReadings}). Answered inside any one of them, the other two borrow a consumer's
 * answer, and a reader that would rather not borrow writes a second answer — which agrees with the
 * first everywhere except a union, where nothing brings the two together to disagree.
 *
 * <p>What this is for is the carrier of a term standing for such an answer. What a number is
 * measured by follows from what the number <em>is</em>, and what it is is declared by the operation:
 * {@code String.length} and {@code Time.hour} both answer an {@code Int} and step by one, at
 * positions that step by a character and by a second. Taken from the position the operation was
 * applied at instead, the step of the answer was the step of the argument — a line at the twelfth
 * hour drawn at the twelfth second — which is a boundary sharpened onto a value the term never
 * takes (#1027).
 *
 * <p>Here rather than beside the declarations because reading a signature is the frontend's. A fact
 * is a proposition about an operation and is declared where those are; what the library was written
 * to declare is read where the library is read.
 */
public final class NumericAnswers {

    /**
     * The type of the number {@code operation} answers, or null where it answers none this can
     * name.
     *
     * <p>Null and not a refusal. Whether an operation answers a number at all is a question askable
     * of any name, and the callers that ask it are asking whether there is a term here — an answer
     * they have somewhere to put.
     */
    public static Type typeOf(ValueName operation, Stdlib library) {
        // A namespace is not an operation and has no entry, which the arm says rather than a
        // question asked of a wider one.
        if (!(operation instanceof ValueName.Stdlib.Operation named)) {
            return null;
        }
        Stdlib.Entry entry = library.entry(named);
        if (entry == null || entry.signature() == null) {
            return null;
        }
        return in(entry.signature().result());
    }

    /**
     * The number a result of {@code t} answers — {@code t} itself where it is one, and the number a
     * union carries where exactly one of its cases is a number.
     *
     * <p>Exactly one, and not the first found. A union carrying two numbers would answer its number
     * at two cases, and which of them a statement about the operation was written for is a question
     * nothing here has a column for — so it answers none, and goes on answering none until something
     * says which.
     *
     * <p>Read off the result and not off what is declared of the operation. What arithmetic an
     * operation computes says where <em>that</em> arithmetic is answered
     * ({@link NumericResult.Answered.InTheCaseCarrying}), which is a different sentence: an
     * operation answering {@code Int | Decimal | SomeError} carries two numbers whatever its
     * arithmetic names, and taking the arithmetic's case for the number the operation answers would
     * make a proposition about one representation into the answer for all of them. The two are held
     * to each other where the arithmetic is declared, so a case naming a number this does not find
     * is refused rather than believed.
     */
    static Type in(Type t) {
        if (isANumber(t)) {
            return t;
        }
        if (!(t instanceof Type.Union union)) {
            return null;
        }
        Type found = null;
        for (souther.compiler.types.TypeSymbol member : union.members()) {
            Type.Prim prim = member.primitiveKind();
            if (prim != null && isANumber(prim)) {
                if (found != null) {
                    return null;
                }
                found = prim;
            }
        }
        return found;
    }

    /** Whether {@code t} is one of the kinds of number the domain relates arithmetically. Read where
     * the numeric rules are bound as well: what such a rule may be written about is the same question
     * as what puts an operation in range of one. */
    static boolean isANumber(Type t) {
        return t == Type.Prim.INT || t == Type.Prim.DECIMAL;
    }

    /** The same, for a reader already holding the symbols its module was compiled against. */
    public static Type typeOf(ValueName operation, Symbols symbols) {
        return typeOf(operation, symbols.library());
    }

    /**
     * The type of the number {@code operation} answers of a value of {@code source}, or null where
     * it answers none this can name.
     *
     * <p>Two stages, because for one operation the signature settles the answer and for another it
     * only says where the answer comes from. {@code String.length} answers an {@code Int} whatever
     * it is given; {@code List.sum} answers what its container holds, and the library states no
     * numeric constraint on that — Souther has no type classes, so which elements the language
     * admits is a check where a call is typed, and the answer is an {@code Int} at one call and a
     * {@code Decimal} at the next (ADR-0082).
     *
     * <p>So a caller with a value in hand gets a concrete answer, and a caller holding only a
     * declaration asks {@link #mayAnswerANumber} instead. Answered from the signature alone, a
     * polymorphic result reads as no number at all, and every rule written on a sum came back as a
     * rule about nothing.
     *
     * <p>Where the answer comes from is not a fact of its own. An operation that walks a container
     * carries what it has so far in the type it answers, which is what its accumulation already
     * says and what the binding of that fact already holds — read again here as a third statement,
     * the day one of them moved would be the day they disagreed.
     */
    public static Type typeOf(ValueName operation, Type source, NewtypeInners inners,
                              Symbols symbols) {
        if (DefaultBoundOperationFacts.get().accumulation(operation) == null) {
            return typeOf(operation, symbols.library());
        }
        Type element = source == null ? null
                : Type.elementOfAContainer(TypeOps.base(source, inners));
        // Through the names the element is written under, as everywhere a number is looked for: a
        // name wrapped round a whole number is a whole number, and a total of them is one too.
        return element == null ? null : in(TypeOps.base(element, inners));
    }

    /**
     * Whether an operation declaring {@code result} leaves to the call what number, if any, it
     * answers.
     *
     * <p>{@code List.sum} is declared {@code (List<'a>) -> 'a} and answers an {@code Int} at one
     * call and a {@code Decimal} at the next; {@code String.concat} is declared to answer a
     * {@code String} and answers one at every call there is. The first is a question the
     * declaration does not settle and the second is one it does, and the difference is whether the
     * answer's type is still open.
     *
     * <p>Both halves of the two-stage reading turn on it, so it is read once. Left out, an
     * operation whose answer is a container or a string is asked which representation reads the
     * number it answers — and the only answer it can be given is that nothing does, which is a
     * denial about a number that is not there.
     */
    static boolean answerIsLeftToTheCall(Type result) {
        return result instanceof Type.Open;
    }

    /**
     * Whether {@code operation} answers a number for some value it could be given.
     *
     * <p>The question a reader of declarations can ask, where {@link #typeOf} is the one a reader of
     * a call can. What it may not do is decide the second: a sum over a list of text answers no
     * number and is the same operation as a sum over a list of whole numbers, so an operation
     * refused here would be refused for every call including the ones that do answer one.
     *
     * <p>Accumulating is not on its own enough. A join of strings walks a container from an
     * identity through a step exactly as a sum does, and what it answers is declared: no call of it
     * answers a number, and the walk says nothing about that either way.
     */
    static boolean mayAnswerANumber(BoundOperationFacts facts, ValueName.Stdlib.Operation named,
                                    Stdlib.Signature signature) {
        if (in(signature.result()) != null) {
            return true;
        }
        return facts.accumulation(named) != null && answerIsLeftToTheCall(signature.result());
    }

    private NumericAnswers() {}
}
