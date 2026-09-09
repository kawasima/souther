package souther.compiler.check;

import souther.compiler.core.Core;
import souther.compiler.diag.SourcePos;
import souther.compiler.types.BinOp;
import souther.compiler.types.BindingId;
import souther.compiler.types.BindingOwner;
import souther.compiler.types.ConstructOccurrence;
import souther.compiler.types.Type;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * What tells one occurrence of a clause's structure from another.
 *
 * <p>A reader that has something to say about part of a clause files it under the occurrence it is
 * about, and a reader that walks the same clause afterwards asks for it under the same one. So an
 * occurrence has to be an answer about the clause and about nothing else: not about which trees
 * happened to be built to say it, and not about how the clause stands where it was read, since one
 * reader takes a clause as stated and another takes the same clause denied and the two are talking
 * about one thing.
 *
 * <p>Held over the whole shape and not at its root, so a clause whose occurrences agreed at the top
 * and disagreed underneath is caught here rather than by whichever reader looked deep enough.
 */
class AnOccurrenceOfAClauseIsTheSameHoweverTheClauseStandsTest {

    private static final SourcePos POS = new SourcePos(1, 1);
    private static final BindingOwner OWNER = new BindingOwner.OfValue("demo", "f");

    /**
     * {@code (if value >= 1 then false else true && value >= 2) && let $p = value in $p >= 3}.
     *
     * <p>A denial, a conjunction and a binding, so the three shapes a clause is written out of are
     * all under the one clause. The denial is written as the {@code if} the analysis reads as one,
     * which is a shape said another way and not a shape of its own.
     */
    private static Core clause() {
        return both(both(denying(rule(read("value", 0), 1)), rule(read("value", 0), 2)),
                let("$p", 1, read("value", 0), rule(read("$p", 1), 3)));
    }

    @Test
    void everyOccurrenceOfOneClauseIsItsOwn() {
        List<Integer> found = occurrencesUnder(ClauseExpr.of(clause(), true));
        assertEquals(found.size(), new HashSet<>(found).size(),
                "two shapes of one clause were issued one occurrence, so a reader filing an answer"
                        + " about either overwrites the other: " + found);
    }

    /**
     * The same shape of one clause is the same occurrence whether the clause is taken as stated or
     * as denied.
     *
     * <p>Said as where each shape stands against the occurrence it was issued, because the
     * occurrences alone are the numbers a walk hands out in the order it walks and would agree
     * between any two clauses of a size. What is being held is that the shapes standing in one
     * place are one occurrence.
     *
     * <p>What keeps this from being met by a numbering that says nothing is the test above: every
     * shape answering nought agrees with itself under either polarity, and is not a numbering any
     * reader can file an answer under.
     */
    @Test
    void aClauseDeniedNumbersItsOccurrencesAsTheSameClauseStatedDoes() {
        assertEquals(whereEachOccurrenceStands(ClauseExpr.of(clause(), true), ""),
                whereEachOccurrenceStands(ClauseExpr.of(clause(), false), ""),
                "how a clause stands changed which occurrence a shape is, so two readers taking one"
                        + " clause differently file their answers apart");
    }

    /** Which occurrence each shape under {@code shape} was issued, outermost first. */
    private static List<Integer> occurrencesUnder(ClauseExpr shape) {
        List<Integer> out = new ArrayList<>();
        whereEachOccurrenceStands(shape, "").forEach(each ->
                out.add(Integer.valueOf(each.substring(each.indexOf('=') + 1))));
        return out;
    }

    /**
     * Where each shape under {@code shape} stands, against the occurrence it was issued.
     *
     * <p>A step per side of a connective and per body of a binding, which is what "the same shape
     * of the clause" means: two readings put a shape in the same place or they do not, and the
     * occurrence is what has to agree wherever they do.
     */
    private static List<String> whereEachOccurrenceStands(ClauseExpr shape, String stands) {
        List<String> out = new ArrayList<>();
        out.add(stands + "=" + shape.at().ordinal());
        switch (shape) {
            case ClauseExpr.Joined it -> {
                out.addAll(whereEachOccurrenceStands(it.left(), stands + "L"));
                out.addAll(whereEachOccurrenceStands(it.right(), stands + "R"));
            }
            case ClauseExpr.Scoped it ->
                    out.addAll(whereEachOccurrenceStands(it.body(), stands + "B"));
            case ClauseExpr.Leaf _ -> {
                // A leaf holds no shape under it, so there is nothing more to name here.
            }
        }
        return out;
    }

    private static Core.Read read(String name, int ordinal) {
        return new Core.Read(name, new BindingId(OWNER, ordinal), Type.INT, POS);
    }

    private static Core let(String name, int ordinal, Core value, Core body) {
        return new Core.LetIn(new Core.Binder(name, new BindingId(OWNER, ordinal)), value, body,
                body.type(), POS);
    }

    private static Core both(Core left, Core right) {
        return new Core.Binary(BinOp.AND, left, right, ConstructOccurrence.unwritten(), Type.BOOL,
                POS);
    }

    /** {@code c} denied, written as the {@code if} the analysis reads as a denial. */
    private static Core denying(Core c) {
        return new Core.If(c, new Core.Bool(false, Type.BOOL, POS),
                new Core.Bool(true, Type.BOOL, POS),
                Core.ForkPlace.asWritten(ConstructOccurrence.unwritten()), Type.BOOL, POS);
    }

    private static Core rule(Core subject, int least) {
        return new Core.Binary(BinOp.GE, subject, new Core.Int(least, Type.INT, POS),
                ConstructOccurrence.unwritten(), Type.BOOL, POS);
    }
}
