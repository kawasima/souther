package souther.compiler.partition;

import souther.compiler.check.ComparisonClaim;
import souther.compiler.check.Location;
import souther.compiler.check.Symbols;
import souther.compiler.check.TypeOps;
import souther.compiler.types.Type;
import souther.compiler.diag.Citation;
import souther.compiler.inputs.ClauseWithoutAnEnd;
import souther.compiler.inputs.InputReading;
import souther.compiler.inputs.InputReads;
import souther.compiler.inputs.TermPath;
import souther.compiler.numeric.EndSide;
import souther.compiler.types.BindingId;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The lines a declaration's clauses draw between two of a behavior's positions.
 *
 * <p>A rule relating two positions divides neither of them, and it is still a line: {@code lo <= hi}
 * says where the pair parts, and a row on it holds the two at one value. A body writing that rule
 * has it read as a line by {@link GuardThresholds}; a declaration writing it has an end on one
 * coordinate read out of it, which such a rule places none of, and this is where its line comes
 * from instead.
 *
 * <p><b>Read here in the vocabulary a line is drawn in, and not carried from the reading of ends.</b>
 * What a clause cuts is a question about numbers, and the two readings name numbers differently on
 * purpose: the discharge procedure names what it can carry a fact about, and this names the numbers
 * a behavior's positions hold. A rule over a number the first has no atom for — the days between two
 * dates — is one this reads to the end, so a clause set aside there is asked here rather than taken
 * as settled.
 *
 * <p>Only the lines that are on no position. A clause bounding one coordinate leaves an end, the end
 * becomes a cut of that position's axis, and the axis is where its line already is; drawn again here
 * it would be two lines where the author wrote one, and a row would be owed twice for one rule.
 *
 * <p>What such a line owes is not what a body's line owes, and that difference is why an author
 * writes the rule on the data. Nothing outside a declaration's rule can be constructed, so the far
 * side of its line holds no value and no row is asked for there. That answer is read off the rule
 * that drew the line ({@link LineOrigin.InvariantOrigin}) and is already right — what was missing was
 * the line.
 */
public final class DeclaredThresholds {

    /**
     * The lines one behavior's declarations draw between its positions.
     *
     * <p>Already obligations rather than thresholds, for the reason an {@code ensures}'s are: a line
     * between two positions divides neither, so there is no class for a partition to be told about.
     */
    public static List<LineDrawn> between(String behavior,
                                   InputReading read) {
        List<LineDrawn> out = new ArrayList<>();
        for (ClauseWithoutAnEnd clause : read.domain().clausesWithoutAnEnd()) {
            drawn(behavior, clause, read, out);
        }
        return List.copyOf(out);
    }

    /** What one conjunct draws, or nothing where it draws no line on a quantity of its own. */
    private static void drawn(String behavior, ClauseWithoutAnEnd clause,
                              InputReading read, List<LineDrawn> out) {
        Symbols symbols = read.symbols();
        Map<BindingId, TermPath> roots = rootsOf(clause, symbols);
        if (roots.isEmpty()) {
            return;
        }
        // No answer to be read, because a declaration's clause is about the values a type admits and
        // there is nothing a behavior answered for it to be about.
        // And no arrival: a declaration's clause stands in no body for anything to be on the way
        // to, which reads as an arrival that restricts nothing.
        ComparisonAssessment assessed = ComparisonAssessment.of(behavior, clause.states(),
                Citation.of(clause.wrote()), read,
                InputReads.ofADeclaredClause(roots), null, true);
        // Only the quantity that is on no position. Why this drew no line where it drew none is not
        // said here: the reading of ends already answered for this clause at each position it names,
        // and a second sentence about one rule is two answers to one question.
        //
        // Which is what keeps a conjunct from being drawn twice. One handed over with no end is
        // read there as an end where its quantity is over one coordinate
        // ({@code FieldDomains.AboutOneCoordinate}), and here as a line where its quantity is over
        // none of them or several — so the two are disjoint exactly as far as the two readings
        // agree about how many coordinates a quantity is over. They are two readings of one
        // question and neither is asked of the other.
        if (assessed instanceof ComparisonAssessment.AcrossPositions over && over.drawsABorder()) {
            out.add(new LineDrawn(over.cutting(), originOf(clause, over.cutting())));
        }
    }

    /**
     * How a row meets a line this clause drew, which is the clause's own answer and no other rule's.
     *
     * <p>Which end the rule keeps is read off the quantity it cut and not off a position. A relation
     * bounds the number two positions stand apart — {@code lo <= hi} keeps {@code hi - lo} at or
     * above zero — so the end is an end of that quantity's range, which is what an end of a bound
     * has always been. Read as an end of a position it would name a position the rule does not
     * divide.
     */
    private static LineOrigin.InvariantOrigin originOf(ClauseWithoutAnEnd clause, Cutting cutting) {
        // Which end a rule keeps is an order's answer. A clause naming a value of the quantity two
        // positions stand apart keeps neither end of it — what it parts is that number from every
        // other one — so there is no end for this to have placed, and a caller reaching here with
        // one is holding a line whose shape it has not established.
        ComparisonClaim.Cut order = cutting.ordering();
        if (order == null) {
            throw new IllegalStateException("which end a clause keeps, asked of one that names a"
                    + " value: " + clause.rule());
        }
        return new LineOrigin.InvariantOrigin(clause.part(), endKept(order),
                order.holdsAtTheValue());
    }

    /**
     * Which end a rule placed, from what its line says about its own value.
     *
     * <p><b>The inverse of what a bound's line facts are read back as.</b> A bound records the end
     * it placed; which side the threshold's own value falls on is derived from that end together
     * with whether the bound admits it. So this has to be that derivation run backwards, and the
     * two are held against each other: a rule stated as one end and read back as the other is a
     * line whose sides are the wrong way round, and it asks for two rows that prove nothing.
     *
     * <p>Read off the side the rule's own value belongs to alone, the answer is right wherever a
     * rule admits its threshold and the other one wherever it does not — so {@code a <= b} lands
     * correctly and {@code a < b} lands inverted, which is a whole half of the rules a model can
     * write. What the end is read off is the side the rule is satisfied on, which is the claim's
     * answer and puts the two facts together once.
     */
    static EndSide endKept(ComparisonClaim.Cut order) {
        return EndSide.facing(order.satisfyingSide());
    }

    /**
     * What the names in the clause stand for, in this behavior's input.
     *
     * <p>A clause binds each field of the declaration that wrote it, so the names it reads are those
     * bindings and the positions they stand at are the fields of the value it is written about. A
     * field an include brought in keeps the binding of the declaration it was written in, which is
     * the declaration the clause was written in too — so one map answers for an included field as
     * readily as for an own one.
     *
     * <p>Empty where the declaration is not one this can read. Nothing is concluded from that: the
     * clause is a rule of the model whether or not this could say what its names stand for, and what
     * the model states at those positions is said by the reading that filed the rule there.
     */
    private static Map<BindingId, TermPath> rootsOf(ClauseWithoutAnEnd clause, Symbols symbols) {
        Map<BindingId, TermPath> roots = new LinkedHashMap<>();
        // Both the declaration that wrote the clause and the one it was read under, because a name
        // wrapped round a record is a governing declaration of its own: the record's clauses are
        // read under the name, and what their reads are bound to is the name's. Either alone
        // answers for one of the two spellings and finds nothing in the other, over one record and
        // one rule.
        //
        // And each name is a step of the path only where the language says it is. A name wrapped
        // round a value is not one, so what a newtype calls its value stands where the newtype
        // stands — stepped into all the same, every read under the name would be one position
        // further down than the position it is at.
        for (souther.compiler.types.TypeSymbol.AtModule declaration
                : List.of(clause.rule().clause().id().declaredOn(), clause.readUnder())) {
            Type of = Type.ref(declaration);
            TypeOps.fieldBindings(declaration, symbols).forEach((field, binding) ->
                    roots.putIfAbsent(binding, Location.isStep(of, field, symbols)
                            ? clause.at().then(field) : clause.at()));
        }
        return roots;
    }

    private DeclaredThresholds() {}
}
