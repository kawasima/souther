package souther.compiler.publish;

import souther.compiler.check.RuleCitation;
import souther.compiler.diag.Citation;
import souther.compiler.diag.SourcePos;

import java.util.Optional;

/**
 * How a document sends a reader to a rule, as the things a document's words for it are made of.
 *
 * <p>The projection of a {@link RuleCitation} onto what varies in the sentence a report writes: the
 * name where the author gave one, and otherwise what the rule is, whether the code is here or
 * reached from here, where it is, and what it is reached by. Everything else a citation carries is
 * how this compiler came to be holding it.
 *
 * <p><b>Made so that two handles a document writes alike are one value.</b> Choosing one of several
 * takes a comparison, and a comparison over what a citation is rather than over what is written of
 * it can come out equal for two handles a reader can tell apart — and then which is written is
 * whichever the set of them happened to iterate first, which is the thing this whole type exists to
 * have removed.
 *
 * <p>So this and never {@link RuleCitation} is what the order is over, and what it must keep is one
 * property: two of these that are equal are two a document writes the same sentence for. A rule's
 * kind is in the sentence, so it is here — left out, a comparison and a predicate written at one
 * place would come to one value and be printed two ways.
 *
 * <p>Three and not one with a field that is sometimes absent, because they are three kinds of
 * evidence. A name is what the author called the rule; a place is where this document's own source
 * has it; and a reach is a place in code out of sight together with what reaches it, which is what
 * makes the place mean something. Read back out of an absent field, the third would be the second
 * with something extra, and every consumer would rebuild the distinction from the absence.
 */
public sealed interface PublishedRuleHandle extends Comparable<PublishedRuleHandle> {

    /** The author gave it a name, and that is what a reader is told. */
    record Named(String name) implements PublishedRuleHandle {

        public Named {
            if (name == null || name.isEmpty()) {
                throw new IllegalArgumentException("a rule called nothing is reached by where it is");
            }
        }
    }

    /** It has no name and is written where a reader can be sent, so what is said is what it is and
     *  where. */
    record Written(String kind, Place at) implements PublishedRuleHandle {

        public Written {
            if (kind == null || kind.isEmpty() || at == null) {
                throw new IllegalArgumentException("a rule with no name is said as what it is and"
                        + " where: " + kind + " at " + at);
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

    /** How a document would write {@code cited}. */
    static PublishedRuleHandle of(RuleCitation cited) {
        return switch (cited) {
            case RuleCitation.Named it -> new Named(it.rule().citedName());
            // A rule with no name is written where the reader can be sent, and there is no third
            // sentence for one that is not. There was: a comparison inside a library operation
            // written in this language was spliced into whoever called it, so a caller's model held
            // rules whose code the reader does not have — said as what they are, where they came
            // from, and what reached them. A caller answers for the rules a caller wrote, so the
            // rules of a model are written in sources the compile holds and this is total over
            // them.
            case RuleCitation.WrittenAt it ->
                    new Written(it.rule().whatItIs(), placeOf(it.at()));
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
            case Named it -> it.name().compareTo(((Named) other).name());
            case Written it -> {
                Written also = (Written) other;
                int word = it.kind().compareTo(also.kind());
                yield word != 0 ? word : it.at().compareTo(also.at());
            }
        };
    }

    /** Which of the two kinds of sentence comes first, written out rather than read off how the
     *  arms happen to be declared. */
    private static int rank(PublishedRuleHandle handle) {
        return switch (handle) {
            case Named _ -> 0;
            case Written _ -> 1;
        };
    }
}
