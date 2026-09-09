package souther.compiler.partition;

import souther.compiler.sites.WrittenCondition;

/**
 * Which question a report about one condition asks for its place.
 *
 * <p>Not the place. What an answer holds is what it is about, and where a reader is sent is worked
 * out when a sentence is written — so a helper whose conditions move, saying the same thing, moves
 * where the sentences point and leaves every answer about them alone.
 *
 * <p><b>Two questions and not a first choice with a fallback.</b> A condition the source wrote is
 * placed by the module that wrote it, wherever it was met; a condition of a shape the reading has
 * no words for is any expression at all, and the module that wrote it filed nothing under a name
 * this could ask by — so it is placed by the reading that met it. Which of the two applies is
 * settled where the condition is read, and asked the other way round, a condition written in a file
 * this compilation has stopped holding would quietly be reported at wherever a reading met a copy
 * of it.
 *
 * <p>Beside {@link souther.compiler.coverage.ArmReportAnchor} and not the same shape as it. What
 * the two share is the rule rather than the arms: an answer holds no place, and an anchor says only
 * which question to put.
 */
public sealed interface ConditionReportAnchor {

    /**
     * The module that wrote it places it.
     *
     * <p>Which condition of that module, under an identity a copy cannot change: a condition inside
     * a helper spliced into three callers is one condition, and where it is written is not a
     * question any of the three can answer for itself.
     */
    record WhereItIsWritten(WrittenCondition condition) implements ConditionReportAnchor {

        public WhereItIsWritten {
            if (condition == null) {
                throw new IllegalArgumentException(
                        "a condition placed by whoever wrote it is some condition they wrote");
            }
        }
    }

    /**
     * The reading that met it places it.
     *
     * <p>For a condition the source wrote no construct of — an expression this reading has no words
     * for, or one under a fork nothing wrote. There is nothing to open where such a condition is
     * written, and what a reader can be shown is where this compilation met it, which is this
     * module's own text and moves only when this module does.
     *
     * @param module    whose reading met it, which is what makes the occurrence an address and not
     *                  half of one
     * @param condition which condition of that reading
     */
    record WhereTheReadingMetIt(String module, ConditionOccurrence condition)
            implements ConditionReportAnchor {

        public WhereTheReadingMetIt {
            if (module == null || condition == null) {
                throw new IllegalArgumentException("a condition placed by the reading that met it"
                        + " is one of some module's readings: " + module + ", " + condition);
            }
        }
    }
}
