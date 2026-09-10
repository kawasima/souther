package souther.compiler.partition;


import souther.compiler.check.ComparisonClaim;
import souther.compiler.check.DeclaredBorders;
import souther.compiler.check.DeclaredLine;
import souther.compiler.check.PartId;
import souther.compiler.check.RuleCitation;
import souther.compiler.check.RuleRef;
import souther.compiler.check.RuleReportAnchor;
import souther.compiler.coverage.ComparisonEmissionSite;
import souther.compiler.numeric.Endpoint;
import souther.compiler.publish.PublishedRuleHandle;
import souther.compiler.publish.PublishedSentence;
import souther.compiler.types.ModelOccurrence;
import souther.compiler.types.TypeSymbol;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A rule that drew a line, as a boundary reader met it.
 *
 * <p><b>A line, and that is what the name says.</b> Everything below the identity is about one: what
 * the rule placed on the values either side of what it wrote, which of its lines this is, which
 * point a row against it stands at, what a declaration is owed for it. So a rule that divides a
 * position without drawing a line is not one of these — it is the other kind of reading a piece of
 * rule evidence carries ({@link PredicateOrigin}), and what the two share is identity alone
 * ({@link RuleEvidenceOrigin}). Named for neither, this type was one a predicate looked as
 * though it belonged in, and putting one here would have made the line's answers total over a
 * reading that has no line.
 *
 * <p>{@link RuleRef} and what is true of it here. Which rule it is is the same value however many
 * times the rule is read, and everything beside it on these records is an answer about this reading
 * of it: where the comparison's own value is recorded, which arms witness it, which side of the
 * line the cut value falls on, which declarations took an end in. None of those tell one rule from
 * another, and a question keyed on them is a question the reading raised rather than the model.
 *
 * <p>Four identities and not one, because they are four equivalences. {@link RuleRef} answers whose
 * rule it is; {@link AuthoredLine} answers which of that rule's lines was drawn; this answers which
 * reading of that line a boundary was drawn off; and {@link BoundaryLine} answers which of them a
 * partition folds into one. A guard inside a helper is read once per call: those are several of
 * these, one {@code RuleRef}, one authored line, and one line.
 *
 * <p>Kept per cut rather than per axis. Several rules can put a cut at the same value — a type's
 * invariant and a {@code guard} that repeats it, or two guards written in different behaviors — and
 * they merge into one partition while staying separate obligations. Reaching the boundary through one
 * guard says nothing about the other.
 */
public sealed interface LineOrigin extends RuleEvidenceOrigin {

    /**
     * A clause of a {@code data}'s invariant, as the clause it is.
     *
     * <p>The clause that placed the end, and not the declaration it is written on together with the
     * word {@code min} or {@code max}. Two clauses of one declaration bounding a position at one
     * value are two rules, and a cut keeps every rule that drew it; named by the declaration and the
     * word, they are one.
     *
     * @param part            which part of which clause drew this end. What tells one line of a
     *                        clause from another where the clause drew several: {@code
     *                        String.length(name) >= 1 && String.length(code) >= 1} is one clause and
     *                        two lines at one value, and a row at either says nothing about the
     *                        other. The clause's own text and not the number it was written about,
     *                        which is spelled differently by every reading that reaches it
     *                        ({@link souther.compiler.check.DeclaredBounds.Drawn})
     * @param keeps           which of the two ends the bound placed. Read where the end is read,
     *                        and carried for the same reason the inclusivity beside it is — a bound
     *                        orders nothing across its line, so there is no side to read off the
     *                        rule further down, and what is left to work it back out of is the range
     *                        the rules leave. That derivation has a case with no answer, and it
     *                        answers a rule leaving one value the same way for both of its ends.
     *                        The end and not a direction along the order: a minimum is where the
     *                        values start, and that it keeps what is above it is the same fact read
     *                        the other way round. Said as the direction, this was the fifth question
     *                        {@link souther.compiler.numeric.Towards} answered, and which end a
     *                        bound placed is the one it is about
     * @param holdsAtTheValue whether the cut value is one the bound admits, which is the end's own
     *                        inclusivity and is what says whether a row at the cut is the border's
     *                        {@code ON} point or its {@code OFF} point. Carried for the same reason
     *                        a guard's origin carries it: nothing downstream can work it back out.
     *                        A discrete carrier steps a strict bound onto the value it leaves, so
     *                        {@code value > 5} on an {@code Int} arrives as an inclusive 6; a
     *                        continuous one has no step, so {@code value > 5.0m} on a
     *                        {@code Decimal} arrives here as an exclusive 5. Both are built. No
     *                        report shows the second today, because a cut on a continuous carrier
     *                        goes no further than this — which is a fact about how far the
     *                        derivation gets and not one about the end, and reading the end is what
     *                        keeps the two from being confused if it ever does get further
     */
    record InvariantOrigin(DeclaredLine drawnBy,
                           souther.compiler.numeric.EndSide keeps, boolean holdsAtTheValue)
            implements LineOrigin {

        public InvariantOrigin {
            if (drawnBy == null) {
                throw new IllegalArgumentException("a bound drawn by no clause");
            }
            if (keeps == null) {
                throw new IllegalArgumentException(
                        "a bound places one of a range's two ends: " + drawnBy);
            }
        }

        /** Which conjunct of the clause drew it, which is what a rule is named by. */
        public PartId<RuleRef.Invariant> part() {
            return drawnBy.part();
        }

        /** Which clause of which declaration drew it. */
        @Override
        public RuleRef.Invariant rule() {
            return part().rule();
        }
    }

    /**
     * A comparison written in a behavior's body.
     *
     * <p>The comparison and not what tests it. Meeting this boundary takes more than writing the
     * value — the comparison has to have been evaluated, and a row can hand the behavior the exact
     * threshold and never get there — and where that is recorded is the comparison's own place.
     * Nothing about a fork is here at all. One comparison given a name can be consumed by two forks
     * and by none, so a reading that took anything from a fork would be answering which of them the
     * rule really belongs to — a question with no answer. What a use of the truth proves about the
     * comparison is not modelled, because no measure asks it: a row meets the line by lighting the
     * comparison's own probe.
     *
     * @param read  which comparison this is, which reading of it, and where it was met
     * @param facts what the rule placed on the values ({@link souther.compiler.check.ComparisonClaim
     *              ComparisonClaim}), which decides which neighbour is the other class's edge:
     *              {@code <= 3000} leaves 3001 over there, {@code < 3000} leaves 2999
     */
    record ComparisonOrigin(Read read, LineFacts facts) implements LineOrigin {

        public ComparisonOrigin {
            if (read == null || facts == null) {
                throw new IllegalArgumentException("a line is what some comparison placed");
            }
        }

        /**
         * Which comparison of the model this reads.
         *
         * <p>Through the handle the reading holds and not beside it. A rule and how a reader is sent
         * to it are one answer, and kept as two they could be built about two comparisons — which
         * would put an identity and a sentence about different rules in one entry of a document
         * ({@link souther.compiler.check.RuleCitation}).
         */
        @Override
        public RuleRef.Comparison rule() {
            return read.rule();
        }

        /**
         * Which comparison this reads, which reading of it this is, and where that reading was.
         *
         * <p>Only the handle tells one rule from another. A comparison inside a non-recursive helper
         * is read once per call of that helper, so one comparison the author wrote arrives as
         * several of these — each a real occurrence, each measured on its own, and all of them the
         * same rule, which is the one {@code written} names.
         *
         * <p>No fork. What a row met the line by is getting the comparison to answer, and the
         * comparison is where that is recorded — so the arms of the {@code if} standing round it
         * were a second place the same reading could be taken from, and one that has nothing to say
         * about a comparison written where no fork stands round it.
         *
         * <p>What meeting the line is measured against is getting the comparison to answer, which
         * is not what any arm records. A condition stops as soon as it is settled, so under
         * {@code A && B} the arm where the condition failed holds rows that made {@code B} false and
         * rows that never reached {@code B}.
         *
         * @param rule which comparison of the model this is, which is the same value however many
         *              times the comparison is read
         * @param states which construct of the model that rule is stated at, which is what every
         *              fact about the rule is filed under. Beside {@link #rule} and not instead of
         *              it: a rule is what a report names and what a document cites, and one
         *              construct of the model states one rule while a helper called twice states
         *              that rule at two constructs
         * @param anchor which question says where a reader finds it, settled here because this is
         *              where what the position was in could still be seen. About the comparison and
         *              not the fork — a condition holding three comparisons is three rules, and a
         *              reader sent to the {@code if} is given one handle for all of them
         * @param recordedAt every place a run through the rule is written down. Only places: which
         *              materialisation of the construct each of them is is the tree that runs
         *              saying something about itself, and nothing a reader of the rule asks turns
         *              on it — a run that got an answer out of any of them got one out of the rule.
         *              <p>Several because one construct of the model may be written into the tree
         *              that runs more than once: a library operation evaluating a closure it was
         *              handed twice writes the comparison twice, and the model states one rule at
         *              one construct all the same
         */
        public record Read(RuleRef.Comparison rule, ModelOccurrence states,
                           RuleReportAnchor anchor,
                           List<ComparisonEmissionSite> recordedAt) {

            public Read {
                if (rule == null || states == null || anchor == null) {
                    throw new IllegalArgumentException(
                            "a rule read off a comparison names one, states it somewhere and cites"
                                    + " it");
                }
                recordedAt = List.copyOf(recordedAt);
                if (recordedAt.isEmpty()) {
                    throw new IllegalArgumentException(
                            "a rule a row is held to is one a run through is written down"
                                    + " somewhere");
                }
            }

            /**
             * How a reader finds the rule, which is by where it is written.
             *
             * <p>Made here rather than kept, so that the handle is of {@link #rule} and can be of no
             * other. Kept beside the rule, the two could be built about different comparisons — and
             * a document writing both would file an entry under one rule with a sentence about
             * another.
             */
            public RuleCitation.Written written() {
                return new RuleCitation.Written(rule, anchor);
            }
        }

    }

    /**
     * A comparison written in a behavior's {@code ensures}.
     *
     * <p>The third rule that draws a line, and neither of the other two. Like an invariant it is met
     * by writing the value: what a clause states is a relation the behavior is held to, so the line
     * is covered by the input the relation changes at and there is no site to look for it at —
     * whether some run of the clause reached that comparison is another question and not the one a
     * boundary measures. Like a guard the line has values on both sides — {@code id.value > 0} under
     * a {@code NotFound} arm says the behavior may not answer that case at or below zero and may
     * above it, so a row is owed either side — which is what tells it from a bound, where nothing
     * outside can be constructed at all.
     *
     * <p>Only a comparison on an input is here. One reading {@code value} is a line on the answer,
     * and a row cannot be written at it: what a row chooses is what the behavior is applied to, not
     * what it answers with. Nothing turns such a comparison away; a term over the answer names no
     * position of the input, so it draws nothing.
     *
     * <p>Both shapes of line wear this. A line at a count of one position and a line between two
     * are drawn by the same rules and are met the same way, so what tells them apart is the target
     * and not the origin — and a reader asking which rule is owed this row does not have to know
     * which shape the line has.
     *
     * @param which             which line of the model this drew, as the readings that decomposed
     *                          the clause named it: which part the author wrote, and which of the
     *                          things that part states. Both, because they are not each other's —
     *                          {@code r.a >= 5 && r.b >= 5} is one clause the author wrote in two
     *                          parts, and a row whose {@code a} is 5 says nothing about {@code b}.
     *                          Carried rather than assembled from a rule and a number here, so that
     *                          what this says about the line is what named it and not what this
     *                          reading counted
     * @param facts             what the rule placed on the values
     *                          ({@link souther.compiler.check.ComparisonClaim ComparisonClaim}),
     *                          which decides which neighbour is the other class's edge and what
     *                          tells one line of a rule from another written at the same value:
     *                          {@code id.value <= 5} and {@code id.value > 5} agree about the class
     *                          the value is in and are two things a row on the line shows apart. On
     *                          a line between two positions that is the whole of what the row shows,
     *                          since there is no class either side to read instead
     */
    record EnsuresOrigin(WhichLine.OfAComparisonOfAPart which, LineFacts facts)
            implements LineOrigin {

        public EnsuresOrigin {
            if (which == null || facts == null) {
                throw new IllegalArgumentException("a line is what some comparison of some part"
                        + " placed: " + which + " " + facts);
            }
        }

        /** Which clause of which behavior — the rule and the whole of it. */
        @Override
        public RuleRef.Ensures rule() {
            return which.statement().rule();
        }
    }

    /**
     * A bound one rule put there and another took in.
     *
     * <p>One obligation and not two. The rules do not each want a row: {@code MinuteOfDay}'s maximum
     * is why this position has an upper edge at all, and {@code WorkInterval}'s clause is why that
     * edge is 1439 rather than 1440. Kept as two origins side by side they would be counted as two
     * boundaries at one value, which is the accounting for rules that each drew a line of their own
     * — an invariant and a guard naming the same number — and not for one line two rules settled
     * together.
     *
     * <p>The rule that put an edge here is a clause of a {@code data} and can be nothing else. A
     * narrowing moves an end a type already has, and the two rules that draw a line in a body — a
     * comparison and an {@code ensures} clause — say something about that body at that position
     * rather than placing an end anything can take in. Written wide, whoever asked such a reading
     * whose line it was had to know what builds one to answer.
     *
     * <p>What it stopped within is the declarations whose own clauses decided that, and not the
     * value the position sits in: the same relation can be written on the record, on a record inside
     * it, or on a name wrapped round either, and only the one that wrote it has anything to answer
     * for. Several where taking any one of them away leaves the end where it is, since each is then
     * as much the answer as the others and choosing would invent the one that is not known. Several
     * written in two modules where an inner record's clause and an outer record's reach one
     * coordinate at one value, so this is not a set with a module of its own.
     */
    final class NarrowedOrigin implements LineOrigin {

        private final InvariantOrigin bound;
        private final List<TypeSymbol.AtModule> within;

        private NarrowedOrigin(InvariantOrigin bound, List<TypeSymbol.AtModule> within) {
            this.bound = bound;
            this.within = List.copyOf(within);
            if (this.within.isEmpty()) {
                throw new IllegalArgumentException("a bound narrowed by nothing is not narrowed");
            }
        }

        /**
         * A bound at {@code at}, said to have been taken in by what {@code took} names.
         *
         * <p>The one way one of these is made, and it is held to the end it claims to be about. A
         * reading's answer says the names are about one end of one side, and this is where that
         * stops being a fact about a reading and becomes what a report writes beside a line — so the
         * end and the side are asked here rather than taken on trust. Neither is a restatement of
         * the caller's own work: {@link souther.compiler.check.MatchedEndAttribution} says the
         * transport was allowed and says nothing about which line it was allowed onto, so a caller
         * holding one could otherwise write it beside any bound it had.
         *
         * <p>What it does not ask is whether the names should be written at all. That is the
         * reader's own rule about what a cut is owed to, answered before this is reached; a
         * {@code null} here is that answer, or a reading with nothing to say about this end.
         *
         * @param at where the cut this bound drew falls, which is the end the names have to be about
         */
        static LineOrigin of(InvariantOrigin bound, Endpoint at,
                            souther.compiler.check.MatchedEndAttribution took) {
            if (took == null) {
                return bound;
            }
            if (took.side() != bound.keeps()) {
                throw new IllegalArgumentException("a bound placing the " + bound.keeps()
                        + " end, taken in by what holds the " + took.side() + " one");
            }
            if (!took.endpoint().sameAs(at)) {
                throw new IllegalArgumentException("a cut at " + at
                        + ", taken in by what holds " + took.endpoint());
            }
            return took.names().isEmpty() ? bound : new NarrowedOrigin(bound, took.names());
        }

        /** The rule that put an edge here. */
        public InvariantOrigin bound() {
            return bound;
        }

        /** The declarations whose own clauses decided where it stopped. Never empty. */
        public List<TypeSymbol.AtModule> within() {
            return within;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof NarrowedOrigin it && bound.equals(it.bound)
                    && within.equals(it.within);
        }

        @Override
        public int hashCode() {
            return Objects.hash(bound, within);
        }

        @Override
        public String toString() {
            return "NarrowedOrigin[bound=" + bound + ", within=" + within + "]";
        }
    }

    /**
     * Which rule of the model this is a reading of, through however many narrowings.
     *
     * <p>The same value for every reading of one rule, which is what makes it a key. What a
     * narrowing adds is about the end and not about the rule: {@code MinuteOfDay}'s maximum is the
     * rule whether or not {@code WorkInterval} moved where it lands, so it comes back the same here
     * and is kept beside it by whoever is measuring the line.
     */
    @Override
    default RuleRef rule() {
        return switch (this) {
            case InvariantOrigin i -> i.rule();
            case ComparisonOrigin g -> g.rule();
            case EnsuresOrigin e -> e.rule();
            case NarrowedOrigin n -> n.bound().rule();
        };
    }

    /**
     * The handle a report sends a reader to the rule by.
     *
     * <p>Beside {@link #rule()} and answering the other half of the same question: which rule it is,
     * and how a reader finds it. A rule the author named is found by that name wherever it is read;
     * a comparison has none and is found by where it is written, which is a place this cannot invent
     * ({@link souther.compiler.check.RuleCitation}).
     *
     * <p>Here because it is one answer per origin. Worked out by whoever is building a finding, the
     * same rule would be cited one way by a reader that had the place to hand and another by one
     * that did not.
     */
    @Override
    default souther.compiler.check.RuleCitation cited() {
        return switch (this) {
            case InvariantOrigin i -> new souther.compiler.check.RuleCitation.Named(i.rule());
            case ComparisonOrigin g -> g.read().written();
            case EnsuresOrigin e -> new souther.compiler.check.RuleCitation.Named(e.rule());
            case NarrowedOrigin n -> n.bound().cited();
        };
    }

    /**
     * Where this came from, as what a report writes rather than as the words themselves.
     *
     * <p>The handle of the rule this line came from, and no words of this reading's own about it. How
     * a reader is sent to a rule is one question with one answer
     * ({@link souther.compiler.publish.PublishedRuleHandle}), and a line saying it a second way here
     * is a second spelling that can come apart from the one a question about the same rule writes.
     *
     * <p>What this adds is what is true of the line rather than of the rule: the declarations that
     * took an end of it in. A bound narrowed by another declaration is not the line that bound would
     * have drawn alone, and the rule is the same rule either way — so those words go around the
     * handle rather than into it.
     */
    default PublishedSentence describe(PublishedRuleHandle.WhereARuleIs places) {
        PublishedRuleHandle handle = PublishedRuleHandle.of(cited(), places);
        return switch (this) {
            case InvariantOrigin _, EnsuresOrigin _, ComparisonOrigin _ ->
                    PublishedSentence.AroundAHandle.alone(handle);
            // The declarations that took the end in, said the way the line itself says them. The
            // handle underneath is the bound's, which is what this origin cites.
            case NarrowedOrigin n -> new PublishedSentence.AroundAHandle(
                    "", handle, n.authoredLine().narrowing());
        };
    }

    /**
     * What this reading recorded about the line it drew.
     *
     * <p>Asked of the reading and never assembled by whoever wants it. Written as a switch at each
     * consumer, each of the three is a slot a consumer can fill in for a rule that leaves it empty,
     * and two consumers filling one slot are free to fill it differently. One projection, one place
     * a rule answers.
     */
    default LineFacts lineFacts() {
        return switch (this) {
            case ComparisonOrigin g -> g.facts();
            case EnsuresOrigin e -> e.facts();
            // Which side the value a bound stops at is on, from the end it placed and whether it
            // admits that value: a minimum keeps what is above, so its value is below the line
            // exactly when the bound does not admit it. A bound singles nothing out — it keeps a run
            // of the order — and that the far side holds no value at all is a different answer,
            // given where a border reads what a line has sides.
            case InvariantOrigin i -> new LineFacts(ComparisonClaim.Cut.satisfiedOn(
                    i.keeps().inward(), i.holdsAtTheValue()));
            case NarrowedOrigin n -> n.bound().lineFacts();
        };
    }

    /**
     * Which line of the model this reading drew.
     *
     * <p>This reading with everything only this reading knows taken out: no position, no behavior,
     * no occurrence of a comparison a body reached. What is left is what several readings of one
     * line share, and it is what a debt is ({@link BorderObligationId}) and what a partition folds
     * readings under ({@link BoundaryLine}).
     *
     * <p>Not a fold made here. Which readings are one line is still a question about a partition,
     * and it is asked where a line is: this only says which line of the model each reading is a
     * reading of, which is the reading's own answer and nobody else's.
     */
    default AuthoredLine authoredLine() {
        return switch (this) {
            // The part that drew it, which is what named the line where a declaration wrote it.
            case InvariantOrigin i ->
                    new AuthoredLine(new WhichLine.OfAPart(i.drawnBy()), lineFacts(), List.of());
            // The rule and nothing under it. A comparison is a rule apiece — a condition holding
            // three comparisons is three rules — so there is no second line of it to tell this one
            // from, and a number here would be one this reading made up to fill a field.
            case ComparisonOrigin g ->
                    new AuthoredLine(new WhichLine.OfAComparison(g.rule()), lineFacts(), List.of());
            // The line this reading was drawn for, handed back. Taken apart into a rule and a
            // number and put together again here, the two halves would be free to be joined
            // differently from the way they were named.
            case EnsuresOrigin e -> new AuthoredLine(e.which(), lineFacts(), List.of());
            // The bound's line, said to have been taken in. What the narrowing adds is about the
            // end and not about the rule, so the rule comes back the same and this is kept beside
            // it.
            case NarrowedOrigin n -> {
                AuthoredLine bound = n.bound().authoredLine();
                yield new AuthoredLine(bound.which(), bound.facts(), n.within());
            }
        };
    }

    /**
     * The same rule, said without a place.
     *
     * <p>What a diagnostic's own sentence says. A diagnostic is built where no reader is — nothing
     * there knows what to call a source — so a place written into its text would be a line and a
     * column with no file, read against whichever file the report happens to be about. Where the rule
     * has no name, what a reader is pointed at is worked out when a sentence is written, from the
     * question the handle names ({@link souther.compiler.check.RuleReportAnchor}).
     */
    default String saidWithoutAPlace() {
        return authoredLine().saidWithoutAPlace();
    }

    /**
     * Whether this rule is found by where it is written rather than by what it is called.
     *
     * <p>What every caller wants of it, and what it used to ask instead was which construct of the
     * language drew the line. Three constructs put a line on a condition and one of them is spelled
     * {@code guard}, so a predicate reading as the keyword answered {@code true} about an
     * {@code if} — and a comparison given a name a line above the fork that tests it stands under
     * no fork at all while being the same rule.
     *
     * <p>Asked rather than matched on the text: what a rule is called is a rendering, and two of
     * them read the same word.
     *
     * <p>And asked of the rule, which is what says so ({@link RuleRef.Named},
     * {@link RuleRef.Written}). Read off which reading this is, the answer would be a second one
     * beside the rule's own — agreeing while the arms line up, and coming apart the day a reading
     * is added for a rule with no name, where this would answer {@code false} about a rule
     * {@link #cited} sends a reader to by its place.
     */
    default boolean isWrittenRatherThanNamed() {
        return rule() instanceof RuleRef.Written;
    }

    /**
     * The declaration this line is owed to, where it is a declaration's line rather than a body's.
     *
     * <p>Whose debt a row at the line is. A clause of a {@code data} says something about the type
     * wherever the type is carried, so a row standing at the line is evidence about the type and the
     * behaviors carrying it have nothing to add — one line, owed once, at the declaration that wrote
     * it. A comparison and an {@code ensures} clause are written in a body and say something about
     * that body at that position, so they are owed per behavior, as they were.
     *
     * <p>Asked of the rule rather than matched on which kind it is. Read by a caller as "is this an
     * invariant", the question would be asked again wherever a report, a build's refusal or an
     * editor wanted it, and a rule added later would be whatever the arm it was written next to
     * happened to say (issue #1062).
     *
     * <p>A narrowed end is the bound's declaration. The declarations that took it in are what
     * {@link #describe} says beside the rule, and each of them is one where taking any away leaves
     * the end where it is — so there is no one of them to send a reader to, and the rule that placed
     * the end is where the line came from.
     */
    default Optional<TypeSymbol> owedToTheDeclaration() {
        return authoredLine().owedToTheDeclaration();
    }

    /**
     * Which authored line of a declaration this is, where it is a declaration's line.
     *
     * <p>The clause and the conjunct that drew the end, which together name one line the author
     * wrote — a clause places as many as it has conjuncts with an end in them, and they are not each
     * other's ({@link souther.compiler.check.DeclaredBorders}).
     *
     * <p>Here rather than at the reader that needs it, for the reason {@link #owedToTheDeclaration}
     * is: a caller taking the clause and the conjunct apart has to know which arms have them, and a
     * rule added later is then answered by whichever arm it was written beside. Both questions are
     * the rule's, so both are asked of it.
     */
    default Optional<DeclaredBorders.Key> declaredLine() {
        return authoredLine().declaredLine();
    }

    /**
     * Which comparison a row has to get an answer out of, for a rule that meeting takes more than
     * writing the value.
     *
     * <p>Asked of the rule rather than matched on which kind it is, because the two are not the same
     * question and reading one for the other is what puts a new rule on whichever arm the code was
     * written next to. A guard's line is about control flow: the comparison is a place in a body, a
     * value can arrive at the behavior's input without arriving there, and a row met the line by
     * getting it to answer — the site is where that is recorded. Every other rule states something
     * about the values themselves. An invariant refuses everything outside its bound, so nothing
     * exists that could have missed it; a clause states a relation, and what covers where the
     * relation changes is the input written at it. For those, writing the value is the whole of what
     * there is to reach and there is no comparison to look at.
     */
    default Optional<ModelOccurrence> comparisonAt() {
        return switch (this) {
            case ComparisonOrigin g -> Optional.of(g.read().states());
            case NarrowedOrigin n -> n.bound().comparisonAt();
            case InvariantOrigin _, EnsuresOrigin _ -> Optional.empty();
        };
    }

    /**
     * Where a run through that comparison is recorded, for a rule that meeting takes reaching one.
     *
     * <p>Beside {@link #comparisonAt} and asked by whoever is holding a recording. Which comparison
     * the rule is about is what a report says and what a reading joins on; whether some run got
     * there is answered against what the run wrote down, and what a run writes down is a number.
     * One projection for both would hand a reading the emitter's number and let it stand for the
     * comparison, which is how the two came to be one value.
     */
    default List<ComparisonEmissionSite> recordedAt() {
        return switch (this) {
            case ComparisonOrigin g -> g.read().recordedAt();
            case NarrowedOrigin n -> n.bound().recordedAt();
            case InvariantOrigin _, EnsuresOrigin _ -> List.of();
        };
    }

}
