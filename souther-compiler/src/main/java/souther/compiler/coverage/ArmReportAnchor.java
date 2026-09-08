package souther.compiler.coverage;

import souther.compiler.types.SourceConstructOrigin;

/**
 * What a report about an arm points at, said without saying where that is.
 *
 * <p>Two things a reader can be shown, and they are not one. A fork written in a source this
 * compilation holds is code the author can go and read: the report points at the fork, in the file
 * it is written in, whichever module the arm was met in. A fork the language itself writes is not
 * code anybody here can open, and what the report points at is where this compilation reached it —
 * the call in the caller's own file. Neither is the other's answer, and a reader handed a place had
 * no way to tell which of the two it had been given.
 *
 * <p><b>Which of them, and not where.</b> Moving the source moves the place and leaves this alone:
 * a fork written in a file this compilation holds goes on being one however far down the file it
 * slides. That is what lets an arm cross a module boundary as a value about the model, with the
 * place asked for by whoever is about to write a sentence — the same division {@code Clause.Ref}
 * and the question that answers where a clause is written are two halves of.
 *
 * <p>Settled where the arm is made, because that is where what a position is in can still be seen.
 * Worked out later from a place the arm carried, this would be a reading of the thing it exists to
 * replace.
 */
public sealed interface ArmReportAnchor {

    /**
     * The fork is written in a source this compilation holds, and the report points at it there.
     *
     * @param origin which construct that is — the same one the arm's obligation names, so that an
     *               arm cannot say it is written somewhere and be an arm of something else
     */
    record WhereItIsWritten(SourceConstructOrigin origin) implements ArmReportAnchor {

        public WhereItIsWritten {
            if (origin == null || !origin.isWritten()) {
                throw new IllegalArgumentException(
                        "an arm reported where its fork is written is an arm of a fork some source"
                                + " wrote: " + origin);
            }
        }
    }

    /**
     * The fork is out of sight, and the report points at where this compilation reached it.
     *
     * <p>An address in one module's plan and not an identity of the source: what is being named is
     * this compilation's own way in to code nobody here wrote, and there is one such way in per
     * place the plan numbered. The module is carried with the number because a number counted
     * within one plan addresses nothing on its own.
     *
     * @param module    whose plan reached it
     * @param controlId which place of that plan, which is what the walk that made it handed out
     */
    record WhereItWasReached(String module, int controlId) implements ArmReportAnchor {

        public WhereItWasReached {
            if (module == null) {
                throw new IllegalArgumentException(
                        "a place a compilation reached is some module's: " + controlId);
            }
        }
    }
}
