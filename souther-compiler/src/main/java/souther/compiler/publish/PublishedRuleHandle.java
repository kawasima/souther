package souther.compiler.publish;

import souther.compiler.check.RuleCitation;
import souther.compiler.check.RuleRef;
import souther.compiler.diag.Citation;
import souther.compiler.diag.SourcePos;

import java.util.Optional;

/**
 * How a document sends a reader to a rule, as one of the sentences a document writes.
 *
 * <p>The projection of a {@link RuleCitation} onto what varies in that sentence: the name where the
 * author gave one, and otherwise what the rule is, whether the code is here or reached from here,
 * and where it is. Everything else a citation carries is how this compiler came to be holding it.
 *
 * <p><b>The arms are the published grammar and not the internal seal.</b> Which sentence a document
 * writes does not divide the way {@link RuleRef} divides: an invariant clause the author named and
 * one they left to be counted are two sentences under one arm of that seal, and a citation and a
 * place are two seals whose product is not the grammar either. Walked for the internal division,
 * the population of forms a contract has to describe comes out short by exactly the forms a value
 * decides — which is how a schema came to promise one shape for a field that writes several. So the
 * division here is by sentence, and a form nothing spells stops the compile.
 *
 * <p><b>Made so that two handles a document writes alike are one value.</b> Choosing one of several
 * takes a comparison, and a comparison over what a citation is rather than over what is written of
 * it can come out equal for two handles a reader can tell apart — and then which is written is
 * whichever the set of them happened to iterate first, which is the thing this whole type exists to
 * have removed.
 *
 * <p>So this and never {@link RuleCitation} is what the order is over, and what it must keep is one
 * property: two of these that are equal are two a document writes the same sentence for.
 *
 * <p><b>The spelling is this layer's and is reached through a surface.</b> What the sentence reads
 * as is {@link RuleHandleSentence}, and what may ask for it is {@link RuleHandleSurface} — so every
 * place a rule handle reaches a reader is a place that named itself, and the surfaces a document has
 * are a set a check can hold against the schema rather than a habit callers keep.
 */
public sealed interface PublishedRuleHandle extends Comparable<PublishedRuleHandle> {

    /** A clause of an invariant the author gave a name. */
    record NamedInvariant(String declaredOn, String clause) implements PublishedRuleHandle {

        public NamedInvariant {
            if (declaredOn == null || declaredOn.isEmpty() || clause == null || clause.isEmpty()) {
                throw new IllegalArgumentException("a named clause of an invariant is written on a"
                        + " declaration and called something: " + declaredOn + " (" + clause + ")");
            }
        }
    }

    /**
     * A clause of an invariant the author named nothing, which a reader counts to.
     *
     * <p>Counted from one, as somebody reading the declaration counts them. Its own form and not a
     * name that happens to be a number: a reader looking for a clause called {@code #2} is looking
     * for something the author never wrote, and a contract that described only the named form left
     * this one undescribed.
     */
    record NumberedInvariant(String declaredOn, int number) implements PublishedRuleHandle {

        public NumberedInvariant {
            if (declaredOn == null || declaredOn.isEmpty() || number < 1) {
                throw new IllegalArgumentException("a clause nobody named is the nth of some"
                        + " declaration, counted from one: " + declaredOn + " #" + number);
            }
        }
    }

    /** A clause of an {@code ensures} the author gave words to — a name, or the case an arm names. */
    record NamedEnsures(String behavior, String clause) implements PublishedRuleHandle {

        public NamedEnsures {
            if (behavior == null || behavior.isEmpty() || clause == null || clause.isEmpty()) {
                throw new IllegalArgumentException("a clause of an ensures belongs to a behavior and"
                        + " is called something: " + behavior + " (" + clause + ")");
            }
        }
    }

    /** A clause stating one rule over every answer, which the behavior's own name is the whole of. */
    record WholeEnsures(String behavior) implements PublishedRuleHandle {

        public WholeEnsures {
            if (behavior == null || behavior.isEmpty()) {
                throw new IllegalArgumentException("a clause over every answer is some behavior's");
            }
        }
    }

    /** It has no name and is written where a reader can be sent, so what is said is what it is and
     *  where. */
    record Written(PublishedRuleKind kind, Place at) implements PublishedRuleHandle {

        public Written {
            if (kind == null || at == null || at instanceof Place.Nowhere) {
                throw new IllegalArgumentException("a rule with no name is said as what it is and"
                        + " where: " + kind + " at " + at);
            }
        }
    }

    /**
     * It has no name and the code is out of sight, so what is said is what it is, what reaches it,
     * and where it came from if this compile met it at a place.
     */
    record Reached(PublishedRuleKind kind, Place at, String reachedBy)
            implements PublishedRuleHandle {

        public Reached {
            if (kind == null || reachedBy == null || reachedBy.isEmpty() || at == null) {
                throw new IllegalArgumentException("code out of sight is said as what it is, what"
                        + " reaches it and where it came from: " + kind + " in " + reachedBy
                        + " at " + at);
            }
        }
    }

    /**
     * Where a report says the rule is, as it says it.
     *
     * <p>Three and not two. A place in a file this compile holds is what a reader can be sent to;
     * a position in a text it cannot name is still printed, line and column, because whoever is
     * showing the report knows which text it is; and code out of sight has no position at all.
     *
     * <p>Not {@link PublishedAt} alone, which is the shape the document's own {@code at} field
     * takes and so has nothing for the middle one. Borrowed for this, two rules the report prints
     * at different lines of an unnamed text came out as one value — and the choice between them
     * fell back to whichever the set of them iterated first.
     */
    sealed interface Place extends Comparable<Place> {

        /** In a file this compile holds, so a reader can be sent to it. */
        record InSource(PublishedAt at) implements Place {}

        /** In a text this compilation cannot name: the numbers are real and the file is the
         *  reader's to know. */
        record Unplaced(int line, int column) implements Place {}

        /** No position at all, which is what a report says of code out of sight. */
        record Nowhere() implements Place {}

        /** A place a reader can be sent to first, then one only whoever is showing the report can
         *  use, and last none — which is how much a reader is given, most first. */
        private int rank() {
            return switch (this) {
                case InSource _ -> 0;
                case Unplaced _ -> 1;
                case Nowhere _ -> 2;
            };
        }

        @Override
        default int compareTo(Place other) {
            int kind = Integer.compare(rank(), other.rank());
            if (kind != 0) {
                return kind;
            }
            return switch (this) {
                case InSource it ->
                        PublicationOrders.PLACES.compare(it.at(), ((InSource) other).at());
                case Unplaced it -> {
                    Unplaced also = (Unplaced) other;
                    int line = Integer.compare(it.line(), also.line());
                    yield line != 0 ? line : Integer.compare(it.column(), also.column());
                }
                case Nowhere _ -> 0;
            };
        }
    }

    /**
     * How a document would write {@code cited}.
     *
     * <p>The one projection, and total over both seals it reads: which sentence a rule with a name
     * takes is the clause's own answer, and which one a rule without takes is the citation's.
     */
    static PublishedRuleHandle of(RuleCitation cited) {
        return switch (cited) {
            case RuleCitation.Named it -> named(it.rule());
            case RuleCitation.WrittenAt it -> it.at() instanceof Citation.Elsewhere out
                    ? new Reached(PublishedRuleKind.of(it.rule()), placeOf(it.at()),
                            out.provenance().reachedBy())
                    : new Written(PublishedRuleKind.of(it.rule()), placeOf(it.at()));
        };
    }

    /**
     * Which sentence a rule the author named is written as.
     *
     * <p>Two kinds of named rule and two sentences each, because in both the author may have left
     * the words to be filled in: a clause with no name of its own is counted, and a clause stating
     * one rule over every answer is the behavior's name alone.
     */
    private static PublishedRuleHandle named(RuleRef.Named rule) {
        return switch (rule) {
            case RuleRef.Invariant it -> {
                String declaredOn = it.clause().id().declaredOn().name();
                yield it.clause().name()
                        .<PublishedRuleHandle>map(name ->
                                new NamedInvariant(declaredOn, name.toString()))
                        .orElseGet(() -> new NumberedInvariant(
                                declaredOn, it.clause().id().ordinal() + 1));
            }
            case RuleRef.Ensures it -> it.clause().isEmpty()
                    ? new WholeEnsures(it.rule().behavior().name())
                    : new NamedEnsures(it.rule().behavior().name(), it.clause());
        };
    }

    /**
     * Where a report says that code is: the place a reader can be sent to where there is one, and
     * otherwise the numbers it prints instead.
     *
     * <p>The numbers are asked of the citation and not of the place, because a place is what the
     * document's own field is made of and there is none for a position in a text this compilation
     * cannot name — while the sentence about the rule prints one all the same.
     */
    private static Place placeOf(Citation cited) {
        Optional<PublishedAt> held = PublishedAt.of(cited);
        if (held.isPresent()) {
            return new Place.InSource(held.get());
        }
        // Asked of what a report prints and not of what the citation holds. Code out of sight is
        // said as where it came from and nothing else, whether or not this compiler met it at a
        // position — so the numbers are no part of what a reader is shown, and a projection that
        // kept them would tell apart two handles a document writes alike.
        SourcePos where = switch (cited) {
            case Citation.Written it -> it.at();
            case Citation.Unplaced it -> it.at();
            case Citation.Reached it -> it.at();
            case Citation.UnplacedElsewhere _, Citation.OutOfSight _ -> null;
        };
        return where == null ? new Place.Nowhere()
                : new Place.Unplaced(where.line(), where.column());
    }

    /**
     * Which of two a document writes first: a name the author gave before a place they did not,
     * a place before code out of sight, and two of one kind by what they say and where they are.
     *
     * <p>A rank and not a ranking: what it is for is that a run choosing between the same two
     * chooses the same way, and a reader given a name has the word the model uses where a reader
     * given a place has what there is instead.
     *
     * <p>Over every part of each kind, so that this is zero for exactly the pairs {@code equals} is
     * true of. Two that a document writes alike are one value and either may be written; two it
     * writes apart are ordered, and which comes first does not depend on the order a set of them
     * came out in.
     */
    @Override
    default int compareTo(PublishedRuleHandle other) {
        int kind = Integer.compare(rank(this), rank(other));
        if (kind != 0) {
            return kind;
        }
        return switch (this) {
            case NamedInvariant it -> {
                NamedInvariant also = (NamedInvariant) other;
                int on = it.declaredOn().compareTo(also.declaredOn());
                yield on != 0 ? on : it.clause().compareTo(also.clause());
            }
            case NumberedInvariant it -> {
                NumberedInvariant also = (NumberedInvariant) other;
                int on = it.declaredOn().compareTo(also.declaredOn());
                yield on != 0 ? on : Integer.compare(it.number(), also.number());
            }
            case NamedEnsures it -> {
                NamedEnsures also = (NamedEnsures) other;
                int of = it.behavior().compareTo(also.behavior());
                yield of != 0 ? of : it.clause().compareTo(also.clause());
            }
            case WholeEnsures it -> it.behavior().compareTo(((WholeEnsures) other).behavior());
            case Written it -> {
                Written also = (Written) other;
                int word = it.kind().compareTo(also.kind());
                yield word != 0 ? word : it.at().compareTo(also.at());
            }
            case Reached it -> {
                Reached also = (Reached) other;
                int word = it.kind().compareTo(also.kind());
                if (word != 0) {
                    yield word;
                }
                int by = it.reachedBy().compareTo(also.reachedBy());
                yield by != 0 ? by : it.at().compareTo(also.at());
            }
        };
    }

    /** Which of the sentences comes first, written out rather than read off how the arms happen to
     *  be declared. */
    private static int rank(PublishedRuleHandle handle) {
        return switch (handle) {
            case NamedInvariant _ -> 0;
            case NumberedInvariant _ -> 1;
            case NamedEnsures _ -> 2;
            case WholeEnsures _ -> 3;
            case Written _ -> 4;
            case Reached _ -> 5;
        };
    }
}
