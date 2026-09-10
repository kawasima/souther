package souther.compiler.check;

/**
 * Which question a report about one rule asks for its place.
 *
 * <p>Not the place. What an answer holds is what it is about, and where a reader is sent is worked
 * out when a sentence is written — so a helper whose rules move, saying the same thing, moves where
 * the sentences point and leaves every answer about them alone.
 *
 * <p><b>Two questions and not a first choice with a fallback.</b> A rule written in a source this
 * compilation holds is placed by the module that wrote it, wherever it was read; a rule whose source
 * this compilation holds no file for is placed by the reading that met it, which is that reading's
 * own text. Which of the two applies is settled where the rule is read, and asked the other way
 * round, a rule written in a file this compilation has stopped holding would quietly be reported at
 * a call instead.
 *
 * <p>That a source wrote the construct and that this compilation can ask that source where are two
 * facts, which is why the first arm is not the whole of it. What the language itself ships is the
 * case that matters: its bodies are read from every module that calls into them, and no source of
 * this compilation is where they are written.
 *
 * <p>Beside {@link souther.compiler.partition.ConditionReportAnchor} and
 * {@link souther.compiler.coverage.ArmReportAnchor} and the same shape as neither. What the three
 * share is the rule rather than the arms: an answer holds no place, and an anchor says only which
 * question to put.
 */
public sealed interface RuleReportAnchor {

    /**
     * The module that wrote the rule places it.
     *
     * <p>Nothing here, because the rule is beside it. This is only ever held by
     * {@link RuleCitation.Written}, which names the rule, and that rule is the identity the question
     * is put by ({@code Sites.WhereARuleIsWritten}) — so a component naming it again would be a
     * second answer to which rule this is about, free to be of another one, and the pairing this
     * whole type sits inside exists to make that unbuildable.
     */
    record ByTheModuleThatWroteIt() implements RuleReportAnchor {}

    /**
     * The reading that met the rule places it.
     *
     * <p>For a rule written in no source this compilation holds. There is nothing for a reader to
     * open where such a rule is written, and what a report can show is the call this compilation
     * came in through, which is the reading module's own text and moves only when that module does.
     *
     * <p><b>An address of one reading's table of places and not an identity of anything.</b> Two
     * readings that met a rule at one place are one entry, because they are one sentence; two that
     * met it at two calls are two. So what the number is handed out against is the place itself,
     * and a reading that reads a rule again is answered with what the first reading came to rather
     * than with the next number.
     *
     * <p><b>Counted within the reading of one body, not over the module.</b> A count over the
     * module makes the number a function of everything read before it there, so an edit to one
     * behavior renames the reaches of every one after it — and a value that moves for an edit
     * nothing about it can see is what this change exists to stop.
     *
     * <p>Which is why the body is here and not taken from the rule beside it. A number counted
     * within one reading addresses nothing until something says which reading was counting, so an
     * address that named only the module would be the same address in two bodies — and a reader
     * holding one would have to go to whatever stands next to it to find out which. What the arm
     * one over leaves out is a question with no components of its own; this one is an address, and
     * an address says where it is.
     *
     * @param module   whose reading met it. The reading module and not the writing one, which is
     *                 the whole of what this arm is for
     * @param behavior which body of that module was being read
     * @param reach    which of that reading's ways in, in the order it met them
     */
    record ByTheReadingThatMetIt(String module, String behavior, int reach)
            implements RuleReportAnchor {

        public ByTheReadingThatMetIt {
            if (module == null || behavior == null) {
                throw new IllegalArgumentException("a rule placed by the reading that met it is"
                        + " one of some module's readings: " + module + ", " + behavior);
            }
            if (reach < 0) {
                throw new IllegalArgumentException(
                        "a place a reading met a rule at is one it wrote down: " + reach);
            }
        }
    }
}
