package souther.compiler.partition;

import java.math.BigDecimal;

/**
 * What a dependency was asked about, as what tells two askings apart.
 *
 * <p>Beside {@link DecisionSubject} and not the same question. A subject is what a row controls —
 * what it writes a value at, or stands a dependency in for — and that is what makes a distinction
 * one an author can write a row against. An argument does not have to be controlled to be known: a
 * body asking one dependency about a number it wrote asks one question, however many times it
 * writes it, and the answer is the same answer.
 *
 * <p>Held as one type, a call about anything a row does not control had no identity at all, so two
 * askings about one written number were two columns of a table that tells them apart nowhere — the
 * occurrence doing identity's work, which is what a decision column exists to stop.
 *
 * <p>What is not here is an argument this reading cannot say: an expression built out of things it
 * has no words for. Such a call leaves the answer unnamed, which is the honest answer — two askings
 * this reading cannot tell apart may be two questions, and running them together would say a body
 * decides less than it does.
 */
public sealed interface DecisionArgument {

    /** What this argument is, as an identity spells it. */
    String spelled();

    /** Something a row controls: a position it writes at, or an answer it stands in for. */
    record OfASubject(DecisionSubject subject) implements DecisionArgument {

        public OfASubject {
            if (subject == null) {
                throw new IllegalArgumentException("an argument of a subject is some subject");
            }
        }

        @Override
        public String spelled() {
            return subject.spelled();
        }

        @Override
        public String toString() {
            return spelled();
        }
    }

    /**
     * A number the model settles, whoever runs it.
     *
     * <p>The value and not what was written: {@code 42} and an expression this compiler folds to
     * {@code 42} ask one question, and a column apiece for them would be the spelling telling them
     * apart.
     */
    record OfANumber(BigDecimal value) implements DecisionArgument {

        public OfANumber {
            if (value == null) {
                throw new IllegalArgumentException("a number argument is some number");
            }
            // The value as the arithmetic compares it, so that `42` and `42.0` are one argument
            // for the reason they are one number.
            value = value.stripTrailingZeros();
        }

        @Override
        public String spelled() {
            return value.toPlainString();
        }

        @Override
        public String toString() {
            return spelled();
        }
    }
}
