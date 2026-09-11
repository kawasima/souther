package souther.compiler.publish;

/**
 * What a document calls one weakening.
 *
 * <p>An enum for the same reason every other enumerated field of the report has one: the shipped
 * schema names these words in its own file and is held against this, so a word added here has to be
 * taught to the schema before it can be written.
 *
 * <p>Beside the orders and not beside the writer. What a document calls something is part of what
 * this compiler publishes, which is what this package holds; a vocabulary kept where the writer is
 * would have the order over it reaching up into the writer to say what it is over.
 *
 * <p>Not one constant per {@link souther.compiler.query.Weakening} arm. A weakening that is an
 * observation gone missing writes the {@code Incompleteness} code's own word, because that
 * vocabulary already exists and a second spelling of it would be a second thing to keep in step.
 * What is here is everything else — the facts no observation code covers.
 *
 * <p>{@code ROW_DID_NOT_FINISH} was here and is not. A row that stopped is an observation gone
 * missing like any other, and saying so in this vocabulary as well was the second spelling this
 * class exists to avoid — it also named the row without saying which source it is in, so two rows
 * were one. It writes {@code row_undecided} now, which is the code the row's own reason carries.
 * The word stays in the schema: a version says what its documents may carry (issue #996).
 */
public enum WeakeningWord {

    /** A row came back and what the behavior answered with could not be read as a case. */
    OUTPUT_CASES_UNREADABLE,

    /** The same at one of the inputs. */
    INPUT_CASES_UNREADABLE,

    /** A row's value at one border was observed and the observation did not come back whole. */
    BORDER_VALUE_UNREADABLE,

    /**
     * The walk arrived at no value at one border, so there was none to observe.
     *
     * <p>Its own word beside the one above. Both leave a point undecided and they are not the same
     * news: a value an observation stopped is one a wider budget keeps, and a place this compiler
     * could not get a value out of is not. Said with one word, a reader acting on the report cannot
     * tell which of the two they are looking at, and the difference is the whole of what the reading
     * beneath it was built to carry.
     */
    BORDER_VALUE_ABSENT,

    /**
     * A row's value at one border was never looked at: the walk to the position could not be taken,
     * or no row came back to walk.
     *
     * <p>One word for the two, because what a reader does about them is the same: both are this
     * compiler unable to look rather than the model putting a value elsewhere. Which of the two it
     * was is said by the reading's own reason underneath, and the sentence the document writes is
     * chosen from that; a second word here would be that taxonomy kept in two places, free to drift
     * apart. They part when what a reader does about them parts.
     *
     * <p>Which is the grouping this vocabulary is for, and not a finer claim about the reasons
     * under a word. {@link #BORDER_VALUE_UNREADABLE} covers an observation a wider run would have
     * kept and one it would meet again, and covers them as one word for the same reason: the code
     * travels underneath. A word here says what happened at the coarseness a reader acts on.
     */
    BORDER_OBSERVATION_UNAVAILABLE,

    /** A rule of the model that a reader set aside. */
    RULE_UNREAD,

    /** A position the reading did not get into, so there is no rule to name. */
    POSITION_NOT_READ,

    /** A question the rules raised that nothing answered. */
    QUESTION_UNANSWERED,

    /** A position whose rules nothing enumerated. */
    RULES_NOT_REACHED,

    /** The bodies of the module were not elaborated, so what is inside them was not read. */
    BODIES_NOT_ELABORATED,

    /** The boundary of the behavior could not be worked out, so no measure that reads one could be
     *  finished. Every one of them was asked for and started, which is what tells this from a
     *  measure nobody asked for. */
    BEHAVIOR_BOUNDARY_NOT_DERIVED,

    /**
     * The input of the behavior was not read, so no measure that reads a position of it could be
     * finished.
     *
     * <p>Its own word beside the one above. Both leave every measure that reads the boundary
     * unfinished and they send a reader to different places: the first is a name in this
     * behavior's own declaration that resolved to nothing, and this is a module holding a type
     * nobody could name, which refuses the reading of what any of its behaviors take.
     */
    BEHAVIOR_INPUT_NOT_READ,


    /** The space of combinations was too large to walk to the end of. */
    PAIR_SPACE_TRUNCATED,

    /** A row went through an arm this compiler had proven nothing arrives at. */
    PROOF_CONTRADICTED,

    /** Two decisions of one body could not be told apart. */
    ARMS_UNSETTLED,

    /**
     * A row ran and which rule of the body's decision it took could not be told.
     *
     * <p>What it takes away is the claim that a rule nothing was seen taking is a rule no row
     * takes. One word for the shortfalls the reading can meet, since a consumer acts on all of them
     * the same way; which of them it was is the reason beside it.
     */
    DECISION_OF_ROW_UNREADABLE,

    /**
     * The ways through a body could not all be written down, so what rules its decision has is not
     * known.
     *
     * <p>Apart from the word above, and a consumer acts on them differently: that one leaves a rule
     * undecided, and this one leaves the account without the rules to be undecided about.
     */
    DECISION_NOT_FULLY_READ
}
