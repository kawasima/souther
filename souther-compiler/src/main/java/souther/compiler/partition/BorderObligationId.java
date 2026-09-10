package souther.compiler.partition;

/**
 * What a row is owed for: one line the author wrote, wherever it is read.
 *
 * <p>Two readings are one debt when they are readings of that same line — whatever position each was
 * met at and whatever occurrence of it a body reached, as far as the rule itself is one rule
 * ({@link AuthoredLine}). A row standing at one then establishes what a row standing at the other
 * would, which is what follows from their being one debt and not the test for it:
 * {@code value >= 5 && value <= 5} places two lines at one value, one row stands at both, and they
 * are two.
 *
 * <p>So {@code UserId}'s clause saying a user id is a string of one character or more is one row to
 * write, whether the position it was read at is {@code draft.owner} or
 * {@code activities[*]@CallTask.owner}, and whether the behavior carrying it is
 * {@code scheduleMeeting} or {@code touchCount}. Neither behavior says anything about the length of
 * a user id, so neither can disagree with the other about a row at length 1. A comparison written in
 * a body is the other way about — it says something about that body — so a helper's comparison
 * called from two behaviors is two rules, and two rows to write.
 *
 * <p><b>Not the rule.</b> One clause places two ends: {@code invariant within = value >= 1 && value
 * <= 10} is one {@link souther.compiler.check.RuleRef.Invariant} and two lines, and a row at the
 * bottom of the range is no evidence about the top. Keyed on the rule, the author writes one row and
 * the other end goes quiet. Which line of the rule this is is {@link AuthoredLine}'s.
 *
 * <p><b>Not the level either.</b> Two rules can cut at one value on one carrier — a record stopping
 * a {@code Minute} at 1000 and another record stopping it at the same 1000 — and each is a rule the
 * author could change without touching the other. That is the accounting {@link LineOrigin} keeps for
 * cuts, and it is kept here for the same reason.
 *
 * <p><b>And not where it was read.</b> The quantity a line was met on — which position of which
 * behavior — is the occurrence, one per position of every behavior carrying the type. It is what
 * {@link Border#cut()} holds, and holding it here asks for one clause's row once per position.
 *
 * <p><b>Nor which reading of the rule reached it.</b> A comparison inside a non-recursive helper is
 * read once per call of that helper: the calls carry different comparison sites, each is measured on
 * its own, and the author wrote one guard and owes one row for it. Keyed on the reading, the two are
 * two debts that only the merge collecting what each reading saw brings back to one — and which call
 * site the surviving debt names then comes down to the order a walk took. What an occurrence is for
 * is measurement: a row meets a guard's line by getting that comparison to answer, and which one it
 * lit is read where the reading is, before anything is folded.
 *
 * <p>Four identities and not one, the way {@link souther.compiler.coverage.CoverageSites.Obligation}
 * and {@link souther.compiler.coverage.ControlPlace} are on the arm side.
 * {@link LineOrigin#rule()} answers which rule of the model this came from and is provenance; this
 * answers which debt it is; {@link BoundaryLine} answers which readings are one line; {@link Border}
 * answers where one of them was read. A narrowing moves the debt without moving the rule:
 * {@code MinuteOfDay}'s maximum is the same rule whether or not {@code WorkInterval} moved where it
 * lands, and the line the two settled together is not the line the bound would have drawn alone — so
 * it comes back the same from {@code rule()} and different from here.
 *
 * @param line which line of the model a row here is owed for
 * @param at   where on the quantity it cut. A level and not a place: what a line is drawn on is not
 *             always a position, and two of the three shapes count something no position holds
 */
public record BorderObligationId(AuthoredLine line, Level at) {

    public BorderObligationId {
        if (line == null || at == null) {
            throw new IllegalArgumentException(
                    "a debt is some rule's line somewhere: " + line + " " + at);
        }
        // Written the one way, because this is compared as a value and is what a map of debts is
        // keyed on. A level keeps the spelling its rule was written in, so one line read at two
        // positions can arrive here as 0 and as 0.00 — one line, and two keys.
        at = at.canonical();
    }

    /**
     * Which rule of the model this came from, through however many narrowings.
     *
     * <p>Provenance, and never a key. Offered here so that a reader wanting to say what drew the
     * line does not reach past this into the line and come back holding something that groups.
     */
    public souther.compiler.check.RuleRef provenance() {
        return line.which().rule();
    }

    /** What a report calls this line — the author's word for the rule where they wrote one and what
     *  the rule is where they did not, and never a place a body reached it at. */
    public String saidWithoutAPlace() {
        return line.saidWithoutAPlace();
    }

    /** The declaration this line is owed to, where a declaration's clause drew it. */
    public java.util.Optional<souther.compiler.types.TypeSymbol> owedToTheDeclaration() {
        return line.owedToTheDeclaration();
    }

    /** Which authored line of that declaration it is, for a reader that wants the words the
     *  declaration wrote it in. */
    public java.util.Optional<souther.compiler.check.DeclaredBorders.Key> declaredLine() {
        return line.declaredLine();
    }
}
