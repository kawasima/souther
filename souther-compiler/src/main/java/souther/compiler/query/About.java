package souther.compiler.query;

import souther.compiler.check.RuleCitation;
import souther.compiler.coverage.CoverageSites;
import souther.compiler.diag.SourcePos;
import souther.compiler.observe.RowIdentity;
import souther.compiler.partition.ClassOfAPosition;
import souther.compiler.partition.DecisionReading;
import souther.compiler.partition.ObligationIdentity;
import souther.compiler.types.TypeSymbol;

import java.util.Objects;
import java.util.Set;

/**
 * What one {@link Adequacy.Finding} is about.
 *
 * <p>The value a measure established, handed on as itself. Not the arguments of a sentence: a
 * finding used to carry {@code List<Object>} in the order a message key took them, so what each
 * element was followed from the kind and from nothing written down, and every reader indexed into it
 * and cast. Two defects came out of that in one change — a record's {@code toString} published as a
 * document's subject, and a cast that would have survived compiling with an element inserted ahead
 * of it — and nothing but a reader caught either.
 *
 * <p>Nothing here is a projection made for a reader. Where a measure already holds the item — a
 * point of a border, a question nothing answered, an arm — that value arrives whole, because a value
 * taken apart at a seam loses a part at every one it crosses and a value that is not taken apart
 * cannot lose one. Words about these belong to whoever writes a sentence: a report can name a file
 * and a diagnostic cannot, and the two are readings of one item rather than one of them being handed
 * the other's answer.
 *
 * <p>Which {@link Adequacy.Kind} a finding has is derived from this and is not held beside it, so a
 * kind and what it is about cannot disagree. This does not know its own kind: the classification and
 * the word a document publishes for it are downstream of what the finding is about, and a subject
 * that answered which public category it lands in would be the leak this type exists to close.
 *
 * <p>Every one of these holds what it names. A finding whose subject is absent is a finding about
 * nothing, and the reader that would find out is whichever surface first asks the subject a
 * question — a different one per surface, and none of them where the finding was made. The list
 * this replaced refused a null element as a side effect of being copied, and a shape that says so
 * itself keeps what the copy was doing by accident.
 */
public sealed interface About {

    /** A case of the output no row expects. */
    record ACaseNoRowExpects(TypeSymbol missing) implements About {
        public ACaseNoRowExpects {
            java.util.Objects.requireNonNull(missing, "a finding is about something");
        }
    }

    /** A case some row expects and nothing was seen to produce. */
    record ACaseNothingWasSeenToProduce(TypeSymbol missing) implements About {
        public ACaseNothingWasSeenToProduce {
            java.util.Objects.requireNonNull(missing, "a finding is about something");
        }
    }

    /**
     * A case of an input no row applies the behavior to.
     *
     * <p>The evidence names which input, so that a case and the position it is a case of arrive
     * together.
     *
     * <p><b>One obligation with the class of that position, and the same one.</b> A case of a sum
     * an input ranges over and the class that sum makes of the position are one thing a row is
     * owed for, reached by two derivations: the signature counts the cases a row applies the
     * behavior to, and the partition counts the classes a row sits in. The identity is what says
     * they are one — carried here, where the position is known, rather than worked out again by
     * whoever needs it, which is how a second identity for one obligation came to exist at the one
     * consumer that needed one.
     *
     * @param owed the class of the position this case is, which is the account's own key for it
     */
    record ACaseNoRowAppliesItTo(InputCaseEvidence input, TypeSymbol missing,
                                 ClassOfAPosition owed) implements OfAnObligation {
        public ACaseNoRowAppliesItTo {
            java.util.Objects.requireNonNull(input, "a finding is about something");
            java.util.Objects.requireNonNull(missing, "a finding is about something");
            java.util.Objects.requireNonNull(owed, "a case of an input is a class of a position");
        }

        @Override
        public ObligationIdentity obligationIdentity() {
            return new ObligationIdentity.OfAClass(owed);
        }
    }

    /**
     * A class of a derived position no row is in, which knows the position it is a class of.
     *
     * <p>One entry of the domain account, and it says so by being an {@link OfAnObligation}. What
     * tells it from every other is the axis and which class of it — the words a report writes for
     * the class do not, since two positions of one behavior can divide into classes that read
     * alike.
     */
    record AClassNoRowIsIn(PartitionEvidence.AxisClass axisClass) implements OfAnObligation {
        public AClassNoRowIsIn {
            java.util.Objects.requireNonNull(axisClass, "a finding is about something");
        }

        @Override
        public ObligationIdentity obligationIdentity() {
            return new ObligationIdentity.OfAClass(
                    new ClassOfAPosition(axisClass.axis().at(), axisClass.name()));
        }
    }

    /**
     * A finding about one thing a row is owed for, which is what carries its identity.
     *
     * <p>Which findings these are is answered here and nowhere else, for the reason {@link OfARule}
     * is answered here: read off a list of kinds, a writer has to be told again every time one is
     * added, and a kind added and not told writes no identity — which is two findings about two
     * things coming out identical in every field with nothing to join them by. A shape that is
     * about something a row is owed for says so by being one of these.
     *
     * <p>Two questions and not one. Whether a subject names an obligation is this one, and it is
     * the subject's own; which account's shape that identity has is {@link ObligationIdentity}'s,
     * and it is closed. A writer that asked both at once — matching the kinds that carry an
     * identity and rendering each — would own the classification twice over, which is how an arm
     * came to be named in a document by its label and its place after the account had been given an
     * identity of its own.
     */
    sealed interface OfAnObligation extends About {

        /** What tells this obligation from every other, in the shape its account keeps. */
        ObligationIdentity obligationIdentity();
    }

    /**
     * A point of a line no row stands at, wherever the line is read.
     *
     * <p>The two arms below are one grain. A point is owed once — a line a body's rule drew is read
     * under each case of a sum the position ranges over, and a line a declaration drew is read at
     * every position carrying the type — and what a finding is about, what a verdict counts and what
     * a generation answers is that one thing. Held at the reading instead, one arm marked as many
     * gaps as the line had readings while the offering composed one row for the point and refused
     * the rest as already answered: a build a person could not make pass.
     *
     * <p>What tells the arms apart is whose account the point is in, and what words there are for
     * what it is on. The rest — the role, what the readings came to, which rule — is asked here of
     * both, so that a reader sorting findings or writing a code asks once.
     */
    sealed interface ABorderObligation extends OfAnObligation {

        /** What every reading of the point came to, which is what the finding stands on. */
        BorderObligationPointAssessment obligation();

        @Override
        default ObligationIdentity obligationIdentity() {
            return new ObligationIdentity.OfALine(obligation().point());
        }

        /** Which of a border's four points this is about, which the point itself says. */
        default souther.compiler.partition.PointRole role() {
            return obligation().role();
        }

        /** What became of it, which is what the finding is about. */
        default ObligationAssessment item() {
            return obligation().item();
        }
    }

    /**
     * A point of a line a body's own rule drew, that no row stands at.
     *
     * <p>One entry of this behavior's account
     * ({@link BorderObligationPointAssessment#belongsToBehaviorAccount}), which is what the count,
     * the document and this are three readings of. The obligation and not one reading of it: a
     * guard on a name every case of a sum spreads is read once under each case, and the readings are
     * where a row can be written, not how many rows are owed.
     *
     * <p>So this holds no word for what the line is on. A body's comparison has no authored
     * spelling of its quantity — each reading names the position it met the line at, and none of
     * them can stand for the rest — and a finding that took one would be choosing a representative
     * by the order the walk took. A report says the readings under the point, each in its own words.
     *
     * <p>That this behavior is owed a row here at all is settled where the account is made, so
     * nothing is checked again: a point owed to the declarations that drew the line is answered once
     * for the module and never reaches this.
     */
    record APointOfABorder(BorderObligationPointAssessment obligation)
            implements ABorderObligation {
        public APointOfABorder {
            java.util.Objects.requireNonNull(obligation, "a finding is about something");
        }
    }

    /**
     * A point of a line a declaration drew, that no row anywhere in the module stands at.
     *
     * <p>Beside {@link APointOfABorder} rather than among its findings, and the difference is whose
     * it is. That one is a body's to write and is in that behavior's account. This one is about the
     * line itself: {@code UserId} says a user id is a string of one character or more, whether the
     * compiler believes a row standing at length 1 is a question about {@code UserId}, and the
     * answer cannot differ between the behaviors carrying it. One row anywhere settles it, and it is
     * kept under the declaration.
     *
     * <p>The declaration's debt and not the point alone, so that what a report writes it on is the
     * quantity the author wrote — {@code String.length(value)} — which is not a representative but
     * the author's own word, and is what the body's arm has none of.
     */
    record APointOfADeclaredBorder(Adequacy.DeclaredDebt owed) implements ABorderObligation {
        public APointOfADeclaredBorder {
            java.util.Objects.requireNonNull(owed, "a finding is about something");
        }

        @Override
        public BorderObligationPointAssessment obligation() {
            return owed.debt();
        }

        /** The same, under the name the declarations' readers know it by. */
        public BorderObligationPointAssessment debt() {
            return owed.debt();
        }
    }

    /**
     * A finding about one rule of the model, which is what carries its identity.
     *
     * <p>Which findings these are is answered here and nowhere else. Read off a list of kinds, a
     * writer had to be told again every time one was added â and a kind added and not told wrote no
     * identity, which is one rule's findings coming out identical in every field with nothing to
     * join them by. A shape that is about a rule says so by being one of these.
     */
    sealed interface OfARule extends About {

        /** Which rule, as everything that names a rule names it. */
        souther.compiler.check.RuleRef rule();

        /**
         * Every handle a reader was offered for that rule.
         *
         * <p>On the seal so that whoever has to ask where these rules are shown is asking one
         * question of every kind of them. Read off the arms that happen to have one, a kind added
         * later is a kind whose rules a report names with nowhere to point — which is the same
         * fault {@link #rule()} is here to keep out, one question over.
         */
        Set<RuleCitation> cited();
    }

    /**
     * A finding the document writes as {@code partition_not_read}, whichever authority answered.
     *
     * <p>Both shapes carry a limit, and a reader acts on it: which one is in the way is the thing
     * either finding was added to say. Asked here rather than matched against the kinds that have
     * one, for the reason {@link OfARule} is: a shape added and not listed writes no limit, and one
     * rule's findings at one position come out identical wherever it stopped for two of them.
     *
     * <p>Named after the kind these are written under, and it is wider than its own word — a rule
     * read from end to end that draws no line is one of them. Which is a decision about the
     * schema's vocabulary rather than about this grouping, and it is written down beside
     * {@link PartitionEvidence.NotRead}, where the document's shape is.
     */
    sealed interface OfSomethingNotRead extends About {

        /** The finding itself, whose reason is what a document promises its reader. */
        PartitionEvidence.NotRead finding();
    }

    /** A position the model draws no line through. */
    record APositionNoLineDivides(
            souther.compiler.partition.UndividedPosition position) implements About {
        public APositionNoLineDivides {
            java.util.Objects.requireNonNull(position, "a finding is about something");
        }
    }

    /**
     * A rule of the model this read and could not turn into a line, and what stopped it.
     *
     * <p>Named by the rule, because a position is not what an author edits. Carrying only the
     * position, the sentence written from this told an author that a rule about somewhere went
     * unread and left them to work out which rule — which the accounting was made to say and this
     * measure beside it was not.
     */
    record ARuleWithoutALine(PartitionEvidence.NotRead.ARule finding)
            implements OfARule, OfSomethingNotRead {
        public ARuleWithoutALine {
            java.util.Objects.requireNonNull(finding, "a finding is about something");
        }

        @Override
        public souther.compiler.check.RuleRef rule() {
            return finding.rule();
        }

        @Override
        public Set<RuleCitation> cited() {
            return finding.cited();
        }
    }

    /**
     * A rule of the model this did not read far enough to say what it raises, and what stopped it.
     *
     * <p>Beside {@link ARuleWithoutALine} and not among it. That one is the model stating that a
     * rule draws no line here, which is a fact an author can read; this is this compiler saying it
     * does not know what the rule states, which is a different sentence and sends a reader
     * somewhere else.
     */
    record ARuleNothingClassified(PartitionEvidence.NotRead.AnUnclassifiedRule finding)
            implements OfARule, OfSomethingNotRead {
        public ARuleNothingClassified {
            java.util.Objects.requireNonNull(finding, "a finding is about something");
        }

        @Override
        public souther.compiler.check.RuleRef rule() {
            return finding.rule();
        }

        @Override
        public Set<RuleCitation> cited() {
            return finding.cited();
        }
    }

    /**
     * A position whose rules this reading never arrived at, with what stopped it.
     *
     * <p>Its own shape beside the rule above, and not that one with the rule left out. There is no
     * rule to name here and a reader is owed the position and the limit; a consumer told to read an
     * absent field to know which of the two it holds is reconstructing the authority from the
     * payload.
     */
    record APositionThisCouldNotRead(PartitionEvidence.NotRead.APosition finding)
            implements OfSomethingNotRead {
        public APositionThisCouldNotRead {
            java.util.Objects.requireNonNull(finding, "a finding is about something");
        }
    }

    /**
     * A position whose values are read from a product this reading cannot show the rules admit.
     *
     * <p>Beside the two above and not among them. Every rule about the position arrived and every
     * one was taken in, so there is no rule to name and nothing was left unreached — what a reader
     * is owed is that the classes may hold one no value can be in, and that what would lift it is a
     * reading that keeps the alternatives apart.
     */
    record APositionReadWiderThanItsRules(
            souther.compiler.inputs.PositionValuesNotSeparated finding) implements About {
        public APositionReadWiderThanItsRules {
            java.util.Objects.requireNonNull(finding, "a finding is about something");
        }
    }

    /**
     * A position the axes measure whose rules the walk never reached.
     *
     * <p>The gap the reading of the model already recorded, and not a measure that was weakened by
     * it. A location is measured at as many numbers as the rules name of it, and every one of those
     * measures is weakened by one stop under the location — so read off the measures, one stop is
     * one finding per number, and two behaviors happening to be measured alike are told apart by
     * nothing.
     *
     * <p>Which measures it weakened is beside this and is each measure's own
     * ({@link PartitionEvidence.AxisCoverage.Reading}). What went wrong is here, once.
     */
    record APositionWhoseRulesWereNotReached(
            souther.compiler.partition.ClosureGap.RulesNotReached gap) implements About {
        public APositionWhoseRulesWereNotReached {
            java.util.Objects.requireNonNull(gap, "a finding is about something");
        }
    }

    /**
     * A question a rule raised that nothing answered.
     *
     * <p>The accounting's own value, whose own contract is that it is handed on whole rather than
     * taken apart. It was taken apart into six elements one seam later, which is the thing that
     * contract was written against.
     */
    record AQuestionNothingAnswered(PartitionEvidence.Unanswered asked) implements OfARule {

        @Override
        public souther.compiler.check.RuleRef rule() {
            return asked.rule();
        }

        @Override
        public Set<RuleCitation> cited() {
            return asked.cited();
        }

        public AQuestionNothingAnswered {
            java.util.Objects.requireNonNull(asked, "a finding is about something");
        }
    }

    /**
     * An arm of the body no row goes through.
     *
     * <p>The occurrence a reader is sent to, and the arm it is one of. Those are two things: a
     * behavior with two {@code guard}s writes two arms called {@code else}, and a fork whose caller
     * supplies the rule is one arm per rule handed in — so two of them can be the same word at the
     * same place and be two things to cover. What a reader is shown comes off the site; what tells
     * one from another is the obligation, and this says so by being an {@link OfAnObligation}.
     */
    record AnArmNoRowGoesThrough(
            CoverageSites.ArmSite arm) implements OfAnObligation {

        public AnArmNoRowGoesThrough {
            java.util.Objects.requireNonNull(arm, "a finding is about something");
        }

        @Override
        public ObligationIdentity obligationIdentity() {
            return new ObligationIdentity.OfAnArm(arm.obligation());
        }
    }

    /**
     * A rule of the decision a body states that no row takes.
     *
     * <p>One entry of the decision account. The rule is the whole of what tells it from every other
     * — the distinctions the path consulted and what each came out as — and where those are written
     * is not part of it, so a body stating one rule at two places states one rule.
     *
     * <p>Said only of the rules something was seen standing in. Whether a rule is owed a row at all
     * is settled before this and is not about the rows: a rule the model's own rules leave no value
     * for is owed nothing however the rows are written, and one this compiler looked for and did
     * not find is neither covered nor a gap.
     *
     * @param behavior whose decision it is a rule of, which the rule itself does not say
     * @param ruled    the rule and what a run down its path would be seen doing, which is what a
     *                 report sends a reader to
     */
    record ARuleNoRowTakes(String behavior, DecisionReading.Ruled ruled)
            implements OfAnObligation {

        public ARuleNoRowTakes {
            Objects.requireNonNull(behavior, "a rule of a decision is some body's");
            Objects.requireNonNull(ruled, "a finding is about something");
        }

        @Override
        public ObligationIdentity obligationIdentity() {
            return new ObligationIdentity.OfADecisionRule(behavior, ruled.rule());
        }
    }

    /**
     * A row whose answer is owed: it is written {@code <?>} and nobody has written what the system
     * answers.
     *
     * <p>About the row and about nothing else. What is owed here is owed whether or not the
     * behavior has arms, whether or not another row covers the arm this one goes through, and
     * whether or not anything could be measured about the rows at all — so it is read off the
     * source, where the fact is settled, rather than off an arm that happens to carry it. Held on
     * an arm, this went missing for a behavior with no branches, for an arm a second row covered,
     * and for an arm whose measurement some unrelated unread row had weakened.
     *
     * <p>Beside {@link ARowAtAnArmAwaitsItsAnswer} and not instead of it. That one is about an arm
     * — what to tell an author about it, and that nothing should compose a second row for it —
     * and this one is the work itself.
     */
    record AnUnansweredRow(String behavior, RowIdentity identity, SourcePos at) implements About {

        public AnUnansweredRow {
            Objects.requireNonNull(behavior, "a row is a row of a behavior");
            Objects.requireNonNull(identity, "a row says what it calls itself");
            Objects.requireNonNull(at, "a row is written somewhere");
        }
    }

    /**
     * An arm a row goes through with its answer owed, and nothing covers.
     *
     * <p>Different news from {@link AnArmNoRowGoesThrough}, and different work. There is a row at
     * this arm; what it is short of is the answer, which is written where the row is and by whoever
     * knows what the system does. Told as an arm no row goes through, an author would be sent to
     * write a row that is already in front of them — and whatever they wrote would be a second row
     * for the same arm.
     *
     * <p>Still a gap. Nothing here asserts what the behavior answers, so a build is entitled to
     * refuse over it exactly as it is over an arm with no row; what differs is the sentence and not
     * the standing.
     */
    record ARowAtAnArmAwaitsItsAnswer(
            CoverageSites.ArmSite arm) implements OfAnObligation {

        public ARowAtAnArmAwaitsItsAnswer {
            java.util.Objects.requireNonNull(arm, "a finding is about something");
        }

        @Override
        public ObligationIdentity obligationIdentity() {
            return new ObligationIdentity.OfAnArm(arm.obligation());
        }
    }
}
