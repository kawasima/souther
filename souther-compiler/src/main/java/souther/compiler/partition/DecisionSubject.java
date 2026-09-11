package souther.compiler.partition;

import souther.compiler.inputs.TermPath;

import java.util.List;

/**
 * What a body draws a distinction on, as a row can control it.
 *
 * <p>The operand side of a decision. What the body does with one — reads it as a truth, compares
 * it, forks on it — is the other side, and the two are apart because they vary independently: a
 * truth and a comparison are two things to do with one subject, and a position and an answer are
 * two subjects to do either with. Written together, each source would carry its own set of
 * operations and a shape added to one would have to be taught to the others.
 *
 * <p><b>A row controls every one of these, and that is what makes it a subject.</b> A position is
 * written at; an answer of a dependency is stood in for. What a row cannot control is not a subject
 * and the condition over it is one this reading has no words for — which is the line between a
 * distinction an author can write a row against and one nothing could.
 */
public sealed interface DecisionSubject {

    /**
     * What this subject is, as an identity spells it.
     *
     * <p>Whole, and not what a reader is shown. Nothing shows a column — a sentence about a rule
     * points at the construct the author wrote — so what this has to do is tell two subjects apart
     * wherever they are two: a dependency carries the module that declares it, since two modules
     * may declare behaviors of one name.
     */
    String spelled();

    /**
     * A position of the behavior's input, where a row writes a value.
     *
     * <p>The position before any arm narrows it, the way {@link DecisionCondition.APosition} says:
     * what the arms of one fork answer is one question about one position, and a subject carrying
     * the narrowing would be a subject apiece for each answer.
     */
    record AnInput(TermPath at) implements DecisionSubject {

        public AnInput {
            if (at == null) {
                throw new IllegalArgumentException("an input subject is some position");
            }
        }

        @Override
        public String spelled() {
            return at.toString();
        }

        @Override
        public String toString() {
            return spelled();
        }
    }

    /**
     * What a dependency answered, or a place inside it.
     *
     * <p>The steps are the fields read off the answer, the way a position's are: a body deciding on
     * two fields of one answer draws two distinctions, and a subject stopping at the answer would
     * run them together.
     *
     * <p>Not a position of the input, and deliberately spelled apart from one. What a row does
     * about a position is write a value at it; what it does about an answer is stand the dependency
     * in. Held as one type, what a search may assume about the input and what a row may pin a
     * dependency to would be the same vocabulary, and widening either would widen both.
     *
     * @param answered which answer of which dependency
     * @param steps    the fields read off it, in the order they are written
     */
    record AnAnswer(InjectedAnswer answered, List<TermPath.Step> steps) implements DecisionSubject {

        public AnAnswer {
            if (answered == null || steps == null) {
                throw new IllegalArgumentException("an answered subject is some answer");
            }
            steps = List.copyOf(steps);
        }

        @Override
        public String spelled() {
            StringBuilder out = new StringBuilder(answered.spelled());
            steps.forEach(step -> out.append('.').append(step));
            return out.toString();
        }

        @Override
        public String toString() {
            return spelled();
        }
    }
}
