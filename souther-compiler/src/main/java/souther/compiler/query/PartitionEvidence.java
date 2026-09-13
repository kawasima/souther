package souther.compiler.query;

import souther.compiler.check.CoverageObligation;
import souther.compiler.check.NumberAt;
import souther.compiler.check.RuleCitation;
import souther.compiler.check.RuleCitations;
import souther.compiler.check.RuleRef;
import souther.compiler.inputs.BlockReason;
import souther.compiler.inputs.FilingCoordinate;
import souther.compiler.inputs.InputQuestion;
import souther.compiler.inputs.StandingQuestion;
import souther.compiler.inputs.WhatAQuestionStandsOn;
import souther.compiler.observe.Incompleteness;
import souther.compiler.observe.MeasureReason;
import souther.compiler.partition.AxisId;
import souther.compiler.partition.ReportedReason;
import souther.compiler.partition.RuleEvidenceOrigin;
import souther.compiler.partition.UndividedPosition;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.SequencedMap;
import java.util.Set;

/**
 * What a behavior's rows reach of the distinctions its model draws.
 *
 * <p>Values only — no classifier, no representative source. What decides a class is a function, and a
 * function held in a memoised answer never compares equal to the same function computed again, so
 * everything downstream would recompute on every ask. What a report needs is the names and the
 * numbers; the functions are used on the way here and left behind.
 *
 * <p><b>The classes, and nothing about the lines.</b> What a behavior is owed a row for at the
 * lines its rules drew is the module's one relation projected to it
 * ({@link Adequacy.BodyBorders}), and the lines themselves — every point of them, whosever they
 * are — are what a reader that describes them asks for ({@link BehaviorEvidence#boundaryReadings}).
 * Neither is here. An account kept beside the classes was read off the lines a second time, and a
 * reader holding one of its entries could walk back to the border and the roles beside it; asked
 * for by name, each is one answer with one owner.
 *
 * @param partitioned  one entry per position the model divides
 * @param notDerivable positions no class came back for, each saying whether the model divides them
 *                     no way at all or this could not read what it divides them by. Both used to be
 *                     one list of paths, and the sentence written from it claimed the first about
 *                     both
 * <p><b>Why these are still here, beside the measures that went without them.</b> #953 moved every
 * fact that costs a measure something into what weakened that measure, and the obvious next step is
 * to drop the lists and project them back out. Two of them cannot be: {@code rulesWithoutALine} holds rules
 * that were set aside and left neither measure short — a comparison relating two positions is read,
 * says what it says, and divides nothing ({@code BlockReason.RuleWithoutLineReason.leavesShort}) — so the
 * weakening is a strictly smaller set than what a reader is owed, and a projection would drop
 * exactly those rules from the report.
 *
 * <p>The other, {@code unanswered}, looks as though it would project: every unanswered question
 * goes to the measure that answers it. It is kept anyway, because that is a reading of the code and
 * not a measurement of it, and the rule this normalization follows is to drop only what something
 * shows is covered.
 *
 * @param whyUnclassified why the rows counted in {@link AxisCoverage.Reached#unclassifiedRows} could not be
 *                     placed. The count is the measurement and this is what it came out of, which
 *                     is why they are two things and not one wider count. Not a report's list of
 *                     reasons: these are what classification observed, and joining them to
 *                     everything else a module could not read happens where that list is built
 */
public record PartitionEvidence(Measure<List<AxisCoverage>> partitioned,
                                PairSpace pairs,
                                List<souther.compiler.partition.UndividedPosition> notDerivable,
                                List<souther.compiler.inputs.RuleWithoutALine> rulesWithoutALine,
                                List<souther.compiler.inputs.PositionReadingBlocked> blocked,
                                List<souther.compiler.inputs.PositionValuesNotSeparated> notSeparated,
                                List<Unanswered> unanswered,
                                List<Incompleteness> whyUnclassified) implements RuleCitations {


    /**
     * No measure of this kind here at all, which is not a measure that came back empty.
     *
     * <p>What a {@code >->} composition gets. It has no positions of its own for either of these to
     * be about, the way it has no arms, and no row anybody writes would give it one — so the two
     * answer inapplicable, and a verdict that counted them would hold every model with a composition
     * in it open for a measurement that was never anybody's to make.
     */
    public static final PartitionEvidence NONE = new PartitionEvidence(
            PartitionDerivation.noSubject(),
            PairSpace.NONE, List.of(), List.of(), List.of(), List.of(),
            List.of(), List.of());

    /**
     * What the model divides a behavior into, where its boundary could not be worked out.
     *
     * <p>Not {@link #NONE}. That says the model holds no subject for either measure and no row would
     * change it, which is a claim about the model; this is a measure that was asked for, started and
     * could not be finished, and what it went without is beside it. The two used to be one absent
     * key, which is how a behavior whose parameter named an unresolved type reached a reader with
     * nothing to read and nothing saying why.
     *
     * <p>Nothing here has a declaration to fall back on. The positions this measure is about are the
     * ones the model divides, worked out from the boundary and from the rules written about it —
     * unlike the positions of the signature measure, which a declared behavior writes down and which
     * are known there whatever the boundary did. So both measures come back short and only one of
     * them still knows how many places it was to have counted at.
     *
     * <p>The pair space is sized at nought for the same reason: a size is a product over those
     * positions. Nothing reads the number — the measurement beside it says none could be finished,
     * and a document leaves the whole section out rather than writing a size nobody worked out.
     */
    public static PartitionEvidence notMeasurable(BoundaryForMeasurement.NotDerived why,
                                                  String behavior) {
        return new PartitionEvidence(
                why.failed(behavior),
                PairSpace.notMeasurable(why, behavior), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of());
    }

    /** Whether this is what a behavior missing something its boundary is made of comes to. */
    public boolean notMeasurable() {
        return BoundaryForMeasurement.wasNotDerived(partitioned);
    }

    public PartitionEvidence {
        notDerivable = List.copyOf(notDerivable);
        rulesWithoutALine = List.copyOf(rulesWithoutALine);
        notSeparated = List.copyOf(notSeparated);
        blocked = List.copyOf(blocked);
        unanswered = List.copyOf(unanswered);
        whyUnclassified = List.copyOf(whyUnclassified);
    }

    /**
     * One question a rule of the model raised about a position that nothing answered.
     *
     * <p>Beside the measures rather than inside one of them. The questions are the model's and every
     * measure here is one reader of them, so a position an axis could not be derived at is not a
     * position whose rules went unwritten — read off the axes, {@code value <= 10 * 2} raised a
     * question about its line, left it standing, and was reported as a model with nothing to answer
     * for, because no axis survived to carry it.
     *
     * <p>The rule as words and not as whatever identifies it. Which rules there are is a question
     * with more than one answer in it — an invariant's clause, a comparison in a body — and a reader
     * downstream that took one of them apart would have to be taught the next; the words come from
     * the one place that names a rule, and everything here is a string by the time it arrives.
     *
     * @param asked   the question as the accounting that raised it holds it: which rule, how a
     *                reader finds that rule, and what is owed. Handed on whole rather than taken
     *                apart — every round of this lost a part at a seam, and what is not taken apart
     *                cannot lose one. Which rule it is tells one question from another; the handle
     *                beside it is what a document shows and is not in step with that wherever a rule
     *                has no name
     * <p><b>The question and nothing derived from it.</b> Everything a document writes about one of
     * these is a projection of what the question says it is about, so it is projected here. Held
     * beside the question as the strings a document wants, a value of this could name one position
     * and be about another, and the two would agree only for as long as whoever wrote the one
     * producer kept them agreeing — which is the arrangement this whole change exists to remove,
     * left standing one layer from the document.
     */
    public record Unanswered(StandingQuestion asked) {

        /** Which rule of the model raised it, which is what tells one question from another. */
        public RuleRef rule() {
            return asked.rule();
        }

        /** How a reader finds that rule, which is not what tells it from another. Every handle the
         *  readers offered, because one question found twice is one question a reader can be sent
         *  to either way; which of them a document writes is that document's to decide. */
        public java.util.Set<RuleCitation> cited() {
            return asked.cited();
        }

        /**
         * The question itself, as the reading that raised it produced it.
         *
         * <p>Handed on whole rather than taken apart here. What it asks, what it asks it about and
         * whether anything worked either of those out are the question's own answers, and a reader
         * that needs one of them asks the question rather than a projection of it made here.
         */
        @Override
        public StandingQuestion asked() {
            return asked;
        }

        /** Whether {@code measure} stays open while this stands, which is the question's own
         *  answer and the same one the closure reads. */
        public boolean holdsOpen(CoverageObligation.Measure measure) {
            return asked.holdsOpen(measure);
        }

        /**
         * What this compiler was short of, which is why the question stands.
         *
         * <p>Every one of them, in the order the parts of the clause were met. Handed on as this
         * compiler's own account and projected onto a published word where a document is written
         * ({@link ReportedReason}) — projected here instead, a surface
         * that says more than the document does would have nothing left to say it from.
         */
        public WhatAQuestionStandsOn stopped() {
            return asked.stopped();
        }

        /**
         * The position it is about, spelled the way a report names it.
         *
         * <p>The walk's and not the accounting's: which position of a behavior's inputs a rule was
         * filed at is what the walk that found it says, which is what the question carries once it
         * has crossed into this vocabulary.
         */
        public String at() {
            return switch (asked) {
                case StandingQuestion.Exact it ->
                        it.asks().path().toString();
                // Where the reader is sent to look, which is what such a question has instead of a
                // subject. What the rule is about is the part that was not read, and a document
                // printing this as the subject would name whichever position the walk was passing.
                case StandingQuestion.Unclassified it ->
                        it.at().path().toString();
            };
        }

        /**
         * The number a border falls on, where it falls on one taken of the position, and null
         * where the line is on what the position itself holds.
         *
         * <p>Both arms of each of the two types, and no {@code default} at either. What has no
         * number is said by the arm that has none rather than by a test that any shape added later
         * would also fail — which is how a question about a number nobody had classified would
         * reach a document as one about no number at all.
         *
         * <p><b>Written from the question and never from an answer to it.</b> What the document
         * says a line falls on is the place the rules name and the operation as it resolved, both
         * of which the question carries. Read off the term a reading made, this
         * had a word only for the numbers something had already read — and the document's own
         * vocabulary was then a projection of what this compiler can do.
         */
        public String measure() {
            return switch (asked) {
                case StandingQuestion.Exact one -> switch (one.asks()) {
                    case InputQuestion.AboutAPosition _ -> null;
                    case InputQuestion.AboutANumber it ->
                            switch (it.about().of()) {
                                // The position's own values, which the `path` beside this already
                                // says.
                                case NumberAt.OfWhatNumber
                                        .OfItsOwnValue _ -> null;
                                case NumberAt.OfWhatNumber
                                        .OfWhatAnOperationAnswers taken ->
                                        named(taken.operation()) + "(" + at() + ")";
                            };
                };
                // The number the walk had named when it stopped, where it named one. Not what the
                // rule is about — nothing worked that out — but which of a position's numbers a
                // reader is being sent to look at, which is what tells two of these apart at one
                // path.
                case StandingQuestion.Unclassified one ->
                        switch (one.at()) {
                            case FilingCoordinate.AtPosition _ -> null;
                            case FilingCoordinate.OfTerm it ->
                                    it.term().toString();
                        };
            };
        }

        /**
         * What a document calls the operation a number is taken by.
         *
         * <p>The qualified spelling, which is the only one a document has. Every claim made today
         * takes its operation from what the library declares of the shape
         * ({@code NumericMeasures.takenOf}), so an operation of another kind arriving here is a
         * producer added without the document being told what to call what it names — and a word
         * invented here for it would be that decision taken by whoever wrote the renderer.
         */
        private static String named(souther.compiler.types.ValueName operation) {
            if (!(operation instanceof souther.compiler.types.ValueName.Stdlib it)) {
                throw new IllegalStateException("a question stands about the number `" + operation
                        + "` answers, which no document has a word for");
            }
            return it.qualified();
        }
    }

    /**
     * One entry of the document's {@code notRead}, as a document writes it.
     *
     * <p>Two shapes because two authorities answer. A rule was read and could not be used, and the
     * finding names which rule; or the reading never got to the rules of a position, and there is
     * no rule to name. Written as one shape with the rule left out where there is none, a consumer
     * would have to read an absent field to know which of the two it was holding â which is the
     * reconstruction this pair exists to stop.
     *
     * <p>The reason in the vocabulary a document promises its reader, not the one this compiler
     * records for itself: which capability was missing is this compiler's own business, and which
     * kind of thing stopped the derivation is what a reader can act on.
     */
    /*
     * `NotRead` is the document's word, and it is wider than the word. A rule read from end to end
     * that draws no line arrives here, and the array it is written to is called `notRead` in every
     * document of schema 7. Renaming that is a version and a retired word, and the schema already
     * tells a consumer which entries are about a limit of this compiler and which are facts about
     * the rule — so the word stays, and the name here is the word, because this is where the
     * document's shape is written and a type named for one thing writing a field named for another
     * is a second place to be kept in step.
     *
     * Behind this projection it means what it says. Nothing in the compiler calls a rule it read to
     * the end one it could not read: what a reading fell short of is `BlockReason.ReadingStopReason`
     * and a rule that is no line at a position is `RuleWithoutALine`. The older word lives on this
     * side alone.
     */
    public sealed interface NotRead {

        /** The position it is about, spelled the way a report names it. */
        String at();

        /** What stopped it, in the words a document promises. */
        UndividedPosition.Reason reason();

        /**
         * Whether this is a reading that stopped, rather than one that ran to the end and left the
         * position divided no way.
         *
         * <p>Both are here because both leave the position with nothing from that rule, and they
         * are opposite sentences to whoever is told one: a form no reader takes apart is a limit of
         * this compiler, and a rule whose quantity is empty was read from end to end and says what
         * it says. Read off the reason word instead, a renderer would be deciding which half a word
         * belongs to — a second place for the split to be made, and the two would disagree the day
         * a word is added.
         */
        boolean readingStopped();

        /**
         * Every handle a reader was offered for what this is about, which is nothing for an entry
         * about no rule.
         *
         * <p>On the seal so that whoever asks where the rules of this array are shown asks one
         * question of every kind of entry. A position nothing divides is about the position and
         * names no rule, and says so by answering with none.
         */
        Set<RuleCitation> cited();

        /** A rule of the model this read and could not turn into a line. */
        record ARule(souther.compiler.inputs.RuleWithoutALine finding) implements NotRead {

            @Override
            public String at() {
                return finding.at().toString();
            }

            @Override
            public UndividedPosition.Reason reason() {
                return ReportedReason.of(finding.why());
            }

            @Override
            public boolean readingStopped() {
                return finding.why() instanceof BlockReason.RuleReadingStopped;
            }

            /** Which rule, which is what tells this finding from the one beside it. */
            public RuleRef rule() {
                return finding.rule();
            }

            /** And how a reader finds that rule, which is not what tells it from another. Every
             *  handle offered, for the reason a standing question gives. */
            @Override
            public java.util.Set<RuleCitation> cited() {
                return finding.cited();
            }
        }

        /**
         * A rule of the model this reading did not get far enough through to classify.
         *
         * <p>Beside {@link ARule} because a reader out here is asking what went unread, and this is
         * what that is: the reading of the rule stopped, and what it raises is the part that was
         * not read. It holds a measure open as well, and is among the questions for that — which is
         * the other question and has the other reader.
         */
        record AnUnclassifiedRule(
                StandingQuestion.NothingClassifiesIt finding)
                implements NotRead {

            @Override
            public String at() {
                return finding.at().toString();
            }

            @Override
            public UndividedPosition.Reason reason() {
                return ReportedReason.of(finding.why());
            }

            /** Always: nothing reaches this having been read to the end. */
            @Override
            public boolean readingStopped() {
                return true;
            }

            /** Which rule, which is what tells this finding from the one beside it. */
            public RuleRef rule() {
                return finding.rule();
            }

            /** And how a reader finds that rule, which is not what tells it from another. */
            @Override
            public java.util.Set<RuleCitation> cited() {
                return finding.cited();
            }
        }

        /** A position whose rules this reading never arrived at. */
        record APosition(souther.compiler.inputs.PositionReadingBlocked finding)
                implements NotRead {

            @Override
            public String at() {
                return finding.at().toString();
            }

            @Override
            public UndividedPosition.Reason reason() {
                return ReportedReason.of(finding.why());
            }

            /** A walk that never arrived at the rules of a position is a reading that stopped,
             *  whatever stopped it. */
            @Override
            public boolean readingStopped() {
                return true;
            }

            /** None: this entry is about the position and names no rule of the model. */
            @Override
            public Set<RuleCitation> cited() {
                return Set.of();
            }
        }
    }

    /**
     * Everything this reading could not read: the rules it did not turn into lines, and the
     * positions it did not reach the rules of.
     *
     * <p>Not every position whose rules this reading is short of. How far the reading of a position
     * ran is what its axis carries ({@link AxisCoverage#read()}), and a position measured on a
     * reading that did not run to the end need have no entry here — these are findings about rules
     * and that is an account of a position. Read as the whole of what was left unread, this would
     * be the very thing it was written to stop: one question answered by a list that answers
     * another.
     *
     * <p><b>Both halves are canonical and neither is recovered from a verdict.</b> This used to add
     * the positions {@link #notDerivable} could not derive, which is an account of whether anything
     * divides a position and not of what was read — and a rule left unread reached that list only
     * after being projected onto the position it was about, losing the rule on the way. So a rule
     * and the position it is at came back as two entries of one list, one of which could name
     * nothing. What is joined here are the two findings themselves, each from the
     * reader that made it.
     *
     * <p>What the model states first, what nothing classified after it, and the positions last,
     * each in the order it was read.
     */
    public List<NotRead> notRead() {
        List<NotRead> out = new java.util.ArrayList<>();
        rulesWithoutALine.forEach(each -> out.add(new NotRead.ARule(each)));
        // And the comparisons nothing worked out what they do, which nothing else says. A rule of a
        // declaration that came to no line is already above, said by the reader that gave up on it;
        // a comparison has no such finding, so the question is what a reader asking what went
        // unread is told about it.
        unanswered.forEach(each -> {
            if (each.asked() instanceof StandingQuestion
                    .NothingClassifiesIt it) {
                out.add(new NotRead.AnUnclassifiedRule(it));
            }
        });
        blocked.forEach(each -> out.add(new NotRead.APosition(each)));
        return List.copyOf(out);
    }

    /**
     * What every measure of this behavior's positions went without, all of them.
     *
     * <p>Asked here rather than assembled by whoever wants it. There are six measures under this —
     * the two derivations, each position, each thing this behavior is owed a row for, and the
     * combinations — and a reader listing them is a reader who has to be told when a seventh
     * arrives. The report listed them, so a measure added here would have been a measure the report
     * left out of every whole above it, silently.
     *
     * <p><b>This behavior's account and no more.</b> A row owed to the declarations that drew a line
     * is answered once for the module and is short or not short there ({@link Adequacy.DeclaredBorders});
     * counted here as well, a behavior would be reported as having gone without something no row
     * written for it is owed. What the two share is the reading that found the lines: an account
     * built from a reading that did not run out may be short of entries whosever they are, so that
     * weakens both.
     */
    public WeakeningSet weakening() {
        WeakeningSet out = partitioned.weakening()
                .union(pairs.counted().weakening());
        for (AxisCoverage axis : axes()) {
            out = out.union(axis.reached().weakening());
        }
        return out;
    }

    /**
     * Every handle this measure holds for a rule it read.
     *
     * <p>Three ways a rule of the model reaches this measure and all three are here. A question
     * nothing answered names the rule it stands on; a rule this reading could not turn into a line
     * names itself; and a rule that did divide a position is what the classes were composed out of,
     * which is the axis's to say ({@link AxisCoverage#divides()}). The third used to be nobody's:
     * the page that names a class no row is in had no handle for the rule that made that class.
     *
     * <p>Asked here and not of the axes. Which arrays a rule can reach this measure through is what
     * this record is made of, and a reader gathering them from outside is a reader who has to be
     * told when a fourth arrives.
     */
    @Override
    public Set<RuleCitation> ruleCitations() {
        Set<RuleCitation> out = new LinkedHashSet<>();
        unanswered().forEach(each -> out.addAll(each.cited()));
        notRead().forEach(each -> out.addAll(each.cited()));
        for (AxisCoverage axis : axes()) {
            axis.divides().forEach(each -> out.add(each.cited()));
        }
        return Collections.unmodifiableSet(out);
    }

    /** The positions, for a reader that wants them and not what the measure made of itself. Empty
     *  where the measure has none to show, which its own answer says the reason for. */
    public List<AxisCoverage> axes() {
        return PartitionDerivation.at(partitioned);
    }

    /**
     * How many two-class combinations the rows reach, taken between the two positions each is
     * between.
     *
     * <p>Counts rather than a ratio, because the denominator is not known. A combination a row
     * reaches is proven reachable — the row is the proof. A combination no row reaches has not been
     * shown impossible; nothing has tried to build one. Calling those unreachable would flatter the
     * coverage, and calling them missing would send the author after rows that may not exist.
     *
     * <p><b>The pairs, and not their sum.</b> A sum says how many combinations there are and
     * nothing about which two positions each is between, so a reader told twenty-one are unknown
     * cannot tell one relation from six. The space is therefore the pairs, and {@link #total()} is
     * read off them rather than kept beside them — kept, the two would be a second thing to hold
     * true.
     *
     * <p>{@code space} is outside the measurement because it is a fact about the model: what a row
     * can be written at is that whether or not anybody counted. What was counted is inside, and a
     * space nobody counted has no counts at all.
     *
     * <p>The other thing that used to sit out here was {@code truncated}, a boolean that had to be
     * kept in step with a status beside it (#951 added the check that did it). A space too large to
     * walk is now said once, as what weakened the measurement.
     */
    public record PairSpace(List<AxisPair> space, Measurement<CoveredBetween> counted) {

        /**
         * Both sides are over one set of pairs, in one order, and this is what says so.
         *
         * <p>Sequence equality and not two containments. What is written of a pair is written
         * against the pair beside it in the other list, so an order that differs is two readers
         * disagreeing about which pair a number is of — which a check on the members alone lets
         * through.
         */
        public PairSpace {
            space = List.copyOf(space);
            List<Between> asked = space.stream().map(AxisPair::between).toList();
            for (CoveredBetween made : counted.made().stream().toList()) {
                if (!List.copyOf(made.byPair().sequencedKeySet()).equals(asked)) {
                    throw new IllegalArgumentException(
                            "a count of the pairs is a count of these pairs, in this order: "
                                    + asked + " counted as " + made.byPair().sequencedKeySet());
                }
            }
        }

        /**
         * The two positions a combination is between, which is the whole of what names one.
         *
         * <p>How many combinations they make between them is not part of it. That is what the
         * model says of the pair and is read from the pair; taken into the name, two readings of
         * one relation that came to different sizes would be two relations.
         */
        public record Between(AxisId one, AxisId other) {

            public Between {
                if (one == null || other == null) {
                    throw new IllegalArgumentException("a combination is between two positions");
                }
            }
        }

        /** One relation of the model, and how many combinations it holds. */
        public record AxisPair(Between between, long total) {

            public AxisPair {
                if (between == null) {
                    throw new IllegalArgumentException("a size is a size of some two positions");
                }
                if (total < 0) {
                    throw new IllegalArgumentException("a relation holds no negative number of"
                            + " combinations: " + total);
                }
            }
        }

        /**
         * What the rows reached of the space, where anybody counted.
         *
         * <p>Named for what it holds and not for what the position measure beside it calls its
         * own count: one file with two {@code Reached} in it is two things a reader has to keep
         * apart by where they are written.
         *
         * <p>Per pair, because that is what was counted: a row sits in the combinations of each
         * relation it reaches, and a sum of them is an answer about no relation in particular.
         *
         * <p><b>It answers no further than what it holds.</b> How many of a relation are left is a
         * question about the size as well as the count, and the size is the model's and sits
         * outside — so {@link PairSpace#unknown()} answers that, and nothing here keeps a second
         * copy of the sizes to answer it from.
         */
        public record CoveredBetween(SequencedMap<Between, Integer> byPair) {

            public CoveredBetween {
                byPair = Collections.unmodifiableSequencedMap(new LinkedHashMap<>(byPair));
            }

            /** How many combinations the rows reach of one relation. */
            public int covered(Between between) {
                Integer said = byPair.get(between);
                if (said == null) {
                    throw new IllegalArgumentException(
                            "a relation this count is not over was read for its count: " + between);
                }
                return said;
            }

            /** And of all of them, which is the one number the whole space is spoken of by. */
            public int covered() {
                return byPair.values().stream().mapToInt(Integer::intValue).sum();
            }
        }

        /** Why the combinations have no numbers. */
        public enum NoRows implements NotMeasuredReason {
            /** No row names this behavior, so nothing sits anywhere. */
            NO_ROWS;

            @Override
            public MeasureReason.About about() {
                return MeasureReason.About.THE_BEHAVIOR;
            }
        }

        public static final PairSpace NONE = new PairSpace(List.of(),
                new Measurement.Complete<>(new CoveredBetween(new LinkedHashMap<>())));

        /** A space nobody counted. It keeps its pairs, which the model settles, and has no counts. */
        public static PairSpace noRows(List<AxisPair> space) {
            return new PairSpace(space, new Measurement.NotMeasured<>(PairSpace.NoRows.NO_ROWS));
        }

        /** The same, where nobody asked for a measurement at all. */
        public static PairSpace notAsked(List<AxisPair> space) {
            return new PairSpace(space, new Measurement.NotMeasured<>(NothingWasAsked.NOT_ASKED));
        }

        /** A space whose pairs were never worked out, because the positions they are between were
         *  not. What it is short of is what every measure of that behavior is short of. */
        public static PairSpace notMeasurable(BoundaryForMeasurement.NotDerived why,
                                              String behavior) {
            return new PairSpace(List.of(), why.failed(behavior));
        }

        /**
         * A space too large to walk to the end of.
         *
         * <p>The pairs are known and none of them was walked, so what is written of each is what
         * was reached of it: none. What it is measured in part by is the fact that stopped it, said
         * once.
         */
        public static PairSpace truncated(String behavior, List<AxisPair> space, long size,
                                          int limit) {
            SequencedMap<Between, Integer> none = new LinkedHashMap<>();
            space.forEach(pair -> none.put(pair.between(), 0));
            return new PairSpace(space, new Measurement.Partial<>(new CoveredBetween(none),
                    WeakeningSet.of(new Weakening.PairSpaceTruncated(behavior, size, limit))));
        }

        /**
         * How many combinations the model has across every relation.
         *
         * <p>Clamped, because a document writes it as one number a consumer reads. A space this
         * large is one the walk was never going to finish, and what it is short of is said as what
         * weakened the measurement rather than by a number that wrapped.
         */
        public int total() {
            long sum = space.stream().mapToLong(AxisPair::total).sum();
            return (int) Math.min(sum, Integer.MAX_VALUE);
        }

        /**
         * The counts, where a measurement was made.
         *
         * <p>Throws where none was. A measure with no number has none, and an accessor that answered
         * zero would be the thing this type was introduced to remove — a reader would get an answer
         * and no sign that nobody measured it.
         */
        public CoveredBetween counts() {
            return counted.made().orElseThrow(() -> new IllegalStateException(
                    "a pair space nobody counted was read for its counts"));
        }

        /**
         * How many combinations no row reaches, over the whole space and over one relation.
         *
         * <p>Here and nowhere else. It is the one answer that needs both halves — the sizes the
         * model settles and the counts a measurement made — and a reader that subtracted them for
         * itself would be a second mechanism for one fact, which is what the pair of numbers this
         * type used to publish already was.
         */
        public int unknown() {
            return total() - counts().covered();
        }

        /**
         * The same of one relation, where the caller is holding it.
         *
         * <p>Takes the pair rather than what names it. A reader walking the space has the size in
         * hand, and looking it up again by name is a walk of the space per relation — which is the
         * space walked once for every pair it holds.
         */
        public long unknown(AxisPair pair) {
            return pair.total() - counts().covered(pair.between());
        }

        /** The same for a caller that has only the name, which costs a look through the space. */
        public long unknown(Between between) {
            return sizeOf(between) - counts().covered(between);
        }

        /** What the model says one relation holds, which is the model's answer and not a count. */
        public long sizeOf(Between between) {
            return space.stream().filter(pair -> pair.between().equals(between))
                    .mapToLong(AxisPair::total).findFirst().orElseThrow(
                            () -> new IllegalArgumentException(
                                    "a relation this space is not over: " + between));
        }

        /** Whether a single ratio would say anything. With unknowns in the denominator it would not,
         *  and a measurement that is not complete has them whether or not they were counted. */
        public boolean decided() {
            return counted instanceof Measurement.Complete<CoveredBetween> && unknown() == 0;
        }
    }

    /**
     * How much of one position's partition the rows reach.
     *
     * @param classes the classes a row can be written at, which is what the model divides the
     *                position into: a case its rules refuse is not one of them. Outside the
     *                measurement, because it is what the model says and is so whether or not
     *                anybody counted. Nothing a body declares narrows it — what it declared is said
     *                beside these numbers ({@link ClaimAnnotations}) and never into them
     * @param reached what the rows reached of them, where anybody counted. A position nothing was
     *                measured at used to carry an empty {@code covered} and a zero count beside a
     *                status saying so, which reads exactly like a position every class of which
     *                went unreached
     */
    public record AxisCoverage(AxisId at, String path,
                               List<String> classes, List<RuleEvidenceOrigin> divides,
                               boolean cutOrParted,
                               Reading read, Measurement<Reached> reached) {

        /**
         * What a document calls this measure, which is the number it is of.
         *
         * <p>Not the location. A location is measured at as many numbers as the rules name of it —
         * which hour of a time and which minute are two — and named by the location a reader is
         * given two lines that say the same thing about different measures. The path is still here
         * and is what a fact about the location is said of.
         */
        public String name() {
            return at.term();
        }

        /**
         * What the rows reached at one position.
         *
         * @param unclassifiedRows rows whose value at this position could not be read. Above zero,
         *                         an unreached class is undecided rather than unreached — and what
         *                         made them unreadable is what the measurement is weakened by
         */
        public record Reached(Set<String> covered, int unclassifiedRows) {

            public Reached {
                covered = Set.copyOf(covered);
            }
        }

        /**
         * Which of this position's rules nothing accounted for.
         *
         * <p>It qualifies the classes and nothing else says it. A rule nothing took in may yet
         * refuse a value one of the classes holds, so the classes are the denominator the model
         * states and not one every class of which is known to be inhabited.
         *
         * <p><b>The questions the model raises, not a reading's account of itself.</b> There are
         * several readings of a clause and they are short of different things: the one that turns
         * clauses into sets of values has no word for a range, so it is short at every numeric
         * position an invariant bounds while two others have those rules whole. Written off that
         * reading, this line said a model had gone unread on the strength of a fact about this
         * compiler, two rows above a boundary drawn from the very rule it was about (issue #842).
         *
         * <p>Each entry names the rule. A position was all a reader used to be given, which left
         * them looking for a rule the sentence never named.
         */
        public record Reading(Reach reach, boolean everyQuestionAboutItsValuesWasAnswered) {

            /**
             * Whether what this measure is short of is nothing.
             *
             * <p>Its own questions and not every question the position raises. Which values may
             * stand somewhere is what classes are made of and is this measure's to be short of;
             * where the line falls is the border measure's, and a position with a line nothing
             * answered has classes that are all the model divides it into. Counted here, the two
             * measures #869 told apart would go back to one number.
             */
            public boolean answered() {
                return reach == Reach.EVERY_RULE && everyQuestionAboutItsValuesWasAnswered;
            }
        }

        /**
         * Whether the walk reached the rules written about this position.
         *
         * <p>Its own axis beside the questions, and not another arm among them. Nothing was found
         * where nothing looked, and an empty list of questions says every rule was accounted for —
         * which is the opposite of the one thing worth knowing (issue #791). The two are not
         * exclusive either: a rule can have arrived and gone unaccounted for while a subtree beside
         * it was never entered, and a type that made them arms could not say so.
         */
        public enum Reach {

            /** Every rule written about the position reached a reading. */
            EVERY_RULE,

            /** Some of them did not, so what is written there is not known. */
            SOME_OUT_OF_SIGHT
        }

        /** Nothing standing, which is what a caller holding no account of its own says. */
        public static final Reading ANSWERED = new Reading(Reach.EVERY_RULE, true);

        /** Why a position has no coverage numbers. */
        public enum NoRows implements NotMeasuredReason {
            /** No row names this behavior. An absence of evidence is not a set of gaps, so the
             *  classes nothing sits in are not classes nothing reaches. */
            NO_ROWS;

            @Override
            public MeasureReason.About about() {
                return MeasureReason.About.THE_BEHAVIOR;
            }
        }

        /** Which classes there are is a fact about the model, and no row has to exist for it to be
         *  so — which is why a position nothing was measured at still names them. */
        public static AxisCoverage noRows(AxisId at, String path, List<String> classes,
                                          List<RuleEvidenceOrigin> divides, boolean cutOrParted,
                                          Reading read) {
            return new AxisCoverage(at, path, classes, divides, cutOrParted, read,
                    new Measurement.NotMeasured<>(AxisCoverage.NoRows.NO_ROWS));
        }

        /** The same, where nobody asked for a measurement at all. */
        public static AxisCoverage notAsked(AxisId at, String path, List<String> classes,
                                            List<RuleEvidenceOrigin> divides, boolean cutOrParted,
                                            Reading read) {
            return new AxisCoverage(at, path, classes, divides, cutOrParted, read,
                    new Measurement.NotMeasured<>(NothingWasAsked.NOT_ASKED));
        }

        public AxisCoverage {
            classes = List.copyOf(classes);
            divides = List.copyOf(divides);
            if (read == null) {
                throw new IllegalArgumentException(
                        "a position with no account of what was read about its values: " + path);
            }
        }

        /**
         * The classes of this position no row is in, each knowing which position it is a class of.
         *
         * <p>A bare name used to be enough because one axis was read at a time. It is not enough to
         * be told apart by: two positions of one behavior divide into classes named after the same
         * cases, and a class name alone is the same words about two of them. A reader given the
         * name and nothing else cannot say which position to write the row at, and neither can a
         * document trying to join the two back together.
         *
         * <p>Which of {@link #at} and {@link #path} names the position to a reader is not settled
         * here. The two are for different readers and a value that chose one of them would be this
         * measure writing a report's sentence.
         */
        public List<AxisClass> uncovered() {
            // Empty where nothing was measured here. An absence of evidence is not a set of gaps:
            // the classes nothing sits in are not classes nothing reaches, and the measurement
            // beside this says which of the two a reader is looking at.
            return reached.made()
                    .map(it -> classes.stream().filter(c -> !it.covered().contains(c))
                            .map(c -> new AxisClass(this, c)).toList())
                    .orElseGet(List::of);
        }
    }

    /**
     * One class of one position.
     *
     * <p>A class is a class <em>of</em> something, and the two halves travel together for the same
     * reason {@link BorderAssessment.Point} exists: a count, a document and a finding are three
     * readings of one item, and each of them flattening it its own way is where a part goes missing.
     */
    public record AxisClass(AxisCoverage axis, String name) {

        public AxisClass {
            // A class with no position is the thing this exists to make unsayable. Held here and
            // not only where a finding takes one: the value is what says a class is a class of
            // something, and a reader that got one without a position would find out at whichever
            // sentence names the position first.
            java.util.Objects.requireNonNull(axis, "a class is a class of a position");
            java.util.Objects.requireNonNull(name, "a class of a position has a name");
        }
    }

}
