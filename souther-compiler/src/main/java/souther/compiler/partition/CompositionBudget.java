package souther.compiler.partition;

/**
 * A figure this compiler holds a piece of its own work to.
 *
 * <p>What reaching one does is the member's to say and not this sentence's — one of them changes
 * how the work is done and gives nothing up at all. So what they have in common is only that
 * somebody wrote a bound down and that raising it is a thing a person could decide to do.
 *
 * <p>One member per place that reaches a figure, and the figure is here rather than at the place.
 * What a reader wants to know is which of these was reached, and a name written beside a number
 * somewhere else is a second declaration of the same policy — the two part the first time one of
 * them is raised.
 *
 * <p><b>A cause and not an outcome.</b> Reaching one of these says what this compiler declined to
 * do and says nothing about the model: the value it did not build may be the easiest one in the
 * file to write by hand. What the reaching did is a separate question, and it is not one question:
 *
 * <ul>
 * <li>a search goes no further, and what it had reached is carried out with the figure;
 * <li>another thing a search runs against goes no further — a plan short of the value's positions
 *     leaves a search that ran to the end of less than the point had;
 * <li>nothing is given up at all, and the work carries on by a wider way that omits none of it.
 * </ul>
 *
 * <p>So a reader may not take a member for any of the three. The first two are carried and the
 * third is not, and which a member is is written where the member is — including, for the third,
 * why there is nothing to carry. A figure named here with no carrier and no such sentence is one
 * somebody has not finished.
 *
 * <p>Nor does the first divide once and for all. A search that had produced nothing when it stopped
 * leaves the point unestablished, and one that had produced something leaves an offer that is some
 * of what there was — so the same member appears on both sides and is neither of them.
 *
 * <p><b>Every figure is written down here, and none is the size of the walk it bounds.</b> A figure
 * set to how many pieces a walk has is reached on every run and on none: it says what this compiler
 * is made of rather than what one search declined, and a reader handed it is told to raise something
 * that stopped nothing. What such a number was saying belongs to
 * {@link CompositionRepertoire}, which is a population this compiler offers some of and is not a
 * number anybody raises.
 */
public enum CompositionBudget {

    /** How many elements a proposed collection is worth building. A row is offered for somebody to
     *  read and complete, and a minimum past this asks for one nobody would. */
    ELEMENTS_A_PROPOSAL_HOLDS(64),

    /** How many characters a proposed string is worth building. Its own figure, because a string of
     *  sixty-five is one literal where a collection of sixty-five is sixty-five values each built in
     *  turn — holding the two to one number bounds a string by something about collections. */
    CHARACTERS_A_PROPOSAL_HOLDS(4096),

    /** How many pairings of what a map's key and value propose are built at once. Every pair is
     *  built before any of them is tried, so this bounds what is allocated rather than what is
     *  walked. */
    PAIRINGS_BUILT_AT_ONCE(64),

    /** How many elements a container built to reach a total is worth carrying. Its own figure and
     *  not {@link #ELEMENTS_A_PROPOSAL_HOLDS}, though they agree: held as one, a change made for one
     *  of them would move the other for no reason anybody could state. */
    ELEMENTS_A_TOTAL_IS_SPREAD_OVER(64),

    /** How many containers are offered for one total. They are alike to the total, so what a fifth
     *  buys is another row reading like the last. */
    SHAPES_OF_A_TOTAL_OFFERED(4),

    /** How many ways down to the number a total is read from are tried. Its own figure and not
     *  {@link #SHAPES_OF_A_TOTAL_OFFERED}: a way that plans is not a container that was offered, and
     *  charging one to the other leaves a case never tried because an earlier one planned and built
     *  nothing. What multiplies here is the cases of every sum the way down crosses. */
    WAYS_DOWN_TO_A_TOTAL_TRIED(8),

    /** How many places along a line a pair is tried at. What a range cannot say is that one of its
     *  values is missing, so what stepping past this walks over is holes, and there are as many of
     *  those as the rules state. */
    PLACES_A_PAIR_IS_TRIED_AT(64),

    /** How many steps a walk over the positions of a form may take. A run without an end is not
     *  walked to the end at any length. */
    STEPS_A_SEARCH_MAY_TAKE(200_000),

    /** How many assignments of one parameter's positions a search composes. A bound on the search
     *  and not on any one position. */
    ASSIGNMENTS_A_SEARCH_COMPOSES(256),

    /** How many values of a progression nothing bounds are tried. */
    VALUES_OF_AN_UNBOUNDED_PROGRESSION_TRIED(16),

    /** How many levels past the one a side starts from are asked for. */
    LEVELS_A_SIDE_IS_ASKED_AT(8),

    /**
     * How often a walk re-reads the rules with the positions it has fixed.
     *
     * <p><b>Reaching it omits no work, which is why nothing carries it.</b> Past this the walk goes
     * on against what the rules left before anything was fixed, which is a wider box and is sound:
     * it offers assignments the narrowing would have skipped and skips none the narrowing would
     * have kept. So a search that met this has still tried everything it would otherwise have
     * tried, and the last step is deliberately outside the figure.
     *
     * <p>Which makes an empty channel the right answer here and not a hole. Handed to an account,
     * it would say a point is open for a figure somebody could raise — of a search that gave up
     * nothing. The others are named beside their carriers; this is named beside the reason it has
     * none.
     */
    TIMES_THE_RULES_ARE_ASKED_AGAIN(2_000),

    /** How many values of one position on the way to a border are tried. Its outcome is not a
     *  composing that stopped: the row is composed, and what was not composed against is one
     *  condition on the way ({@link ReachabilityGap}). */
    VALUES_A_POSITION_ON_THE_WAY_IS_TRIED_AT(8),

    /** How many paths through one body a reading of its decision takes. What it had read is carried
     *  out with the figure ({@link DecisionReading.Enumeration.StoppedAtAFigure}), so the rules it
     *  did not reach are neither covered nor gaps. */
    PATHS_OF_A_DECISION_READ(4096),

    /**
     * How deep a construction plan descends.
     *
     * <p><b>Reaching it stops no search: what it shortens is the plan.</b> The composing runs
     * against a plan that has no positions below the figure, so the answer it comes to is its own —
     * every candidate refused, or nothing composed — and what this adds is that the answer is about
     * fewer positions than the value has. The two are carried side by side
     * ({@code Attempt.Limited}), because neither follows from the other.
     *
     * <p>So this has no word of its own. A search that stopped comes back saying so, and a reader
     * may read which budget from that; nothing stopped here, and asking these for a word is what
     * refuses to answer.
     *
     * <p>Where the plan is short of a position the caller asked something at, there is no search at
     * all: a row composed against such a plan is one the caller's own value is missing from, and
     * the plan says so instead of handing one back.
     */
    DEPTH_A_CONSTRUCTION_PLAN_DESCENDS(8);

    private final int maximum;

    CompositionBudget(int maximum) {
        this.maximum = maximum;
    }

    /** The figure itself. What is done on reaching it is the member's to say. */
    public int maximum() {
        return maximum;
    }
}
