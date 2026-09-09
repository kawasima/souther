package souther.compiler.inputs;

import org.junit.jupiter.api.Test;

import souther.compiler.check.DeclaredSig;
import souther.compiler.check.RuleReadingSource;
import souther.compiler.check.RuleReadings;
import souther.compiler.numeric.Count;
import souther.compiler.numeric.Endpoint;
import souther.compiler.numeric.NumericDomain;
import souther.compiler.query.Bodies;
import souther.compiler.query.Compilation;
import souther.compiler.query.ReadAs;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Where two positions of an input stand against each other is settled by the clauses relating them,
 * and by nothing that is read underneath either one.
 *
 * <p>Which reading owns the rules of a value and which readings a quantity is asked over are
 * different questions, and the second is the one this holds. A reading begun under a position — at
 * the narrowing that says an optional holds something, at the element a sequence holds — is there to
 * read what that value's own declaration states. It is not a party to a relation written above it:
 * a clause relating two fields of a record is written in that record, so the reading of the record
 * is what carries it, and a reading opened below one of the fields carries nothing of it either way.
 *
 * <p>So the answers below are the same whether or not such a reading exists. Held against sources
 * and over the quantities themselves, because the thing that could go wrong is not a wrong number
 * but a right number arrived at from a different set of readings: a relation that survived only
 * because the readings it was collected from happened to be the roots of the walk would move the day
 * the walk opens a root somewhere else.
 *
 * <p><b>What this refuses is a coincidence, not a value.</b> {@link InputDomain#quantities} takes the
 * roots of the rule reading and uses them twice — as where the clauses come from, and as the
 * positions a quantity may be asked about. Nothing says those two are one set. This is what says
 * that adding to the first does not move what the second answers.
 */
class WhatRelatesTwoPositionsIsNotMovedByWhatIsReadUnderOneOfThemTest {

    /**
     * Two fields the record holds together, with a sequence of a rule-carrying type beside them.
     *
     * <p>The sequence is what makes the model say anything here. Its element is a value with a
     * declaration of its own, which is a reading waiting to be opened under a position — and the two
     * numbers the record relates are nowhere near it.
     */
    private static final String RELATED_BESIDE_A_SEQUENCE = """
            module example.beside

            data Tag = String
                invariant nonEmpty = String.length(value) >= 1

            data N = Int
                invariant atLeastNone = value >= 0
                invariant atMostFive  = value <= 5

            data P = { x: N, y: N, tags: List<Tag> }
                invariant together = x.value + y.value <= 5

            data Taken

            behavior take : (p: P) -> Taken
            """;

    /**
     * A relation the record writes about the sequence itself.
     *
     * <p>The sharper of the two. What the record says here is about how many the sequence holds,
     * which is a number at the sequence's own position — so a reading opened at the element stands
     * between the clause and the position it is about, and a quantity collected from the readings
     * rather than from the clauses would lose it.
     */
    private static final String COUNTED_AGAINST_A_FIELD = """
            module example.counted

            data Tag = String
                invariant nonEmpty = String.length(value) >= 1

            data N = Int
                invariant atLeastNone = value >= 0
                invariant atMostFive  = value <= 5

            data P = { cap: N, tags: List<Tag> }
                invariant capped = List.length(tags) <= cap.value

            data Taken

            behavior take : (p: P) -> Taken
            """;

    private static final NumericTerm X = new NumericTerm.ValueOf(TermPath.of("p").then("x"));
    private static final NumericTerm Y = new NumericTerm.ValueOf(TermPath.of("p").then("y"));
    private static final NumericTerm CAP = new NumericTerm.ValueOf(TermPath.of("p").then("cap"));

    /** A relation between two fields is what it is, whatever stands in the field beside them. */
    @Test
    void aRelationBetweenTwoFieldsStandsBesideASequenceOfADeclaredType() {
        Quantities read = read(RELATED_BESIDE_A_SEQUENCE).quantities();

        assertEquals(bounds(0, 5), read.runsBetween(Y),
                "read a position at a time, y runs the whole of what its own type allows");
        assertEquals(bounds(0, 1), read.given(X, count(4)).runsBetween(Y),
                "and the record's clause is what takes the rest of it away");
    }

    /** And a relation about how many a sequence holds is asked at the sequence's own position. */
    @Test
    void aRelationAboutHowManyASequenceHoldsIsAskedAtTheSequence() {
        Read read = read(COUNTED_AGAINST_A_FIELD);
        NumericTerm tags = size(read, "tags");

        assertEquals(Endpoint.inclusive(count(0)), read.quantities().runsBetween(tags).min(),
                "a sequence holds none or more");
        assertEquals(bounds(0, 2), read.quantities().given(CAP, count(2)).runsBetween(tags),
                "and the record's clause is what puts a ceiling on it");
    }

    /** The term for the number that counts what stands at {@code field}, built the way the compiler
     *  builds one: through the factory that holds the operation to what is there. */
    private static NumericTerm size(Read read, String field) {
        TermPath at = TermPath.of("p").then(field);
        souther.compiler.types.Type type = read.inputs().at(at).type();
        NumericTerm.TakenOf made = NumericTerm.TakenOf.of(
                souther.compiler.check.NumericMeasures.takenOf(type, read.rules().symbols()),
                at, type, read.rules().symbols());
        assertNotNull(made, at + " is counted by what its type is counted by");
        return made;
    }

    private static Count count(int at) {
        return new Count(BigDecimal.valueOf(at));
    }

    private static NumericDomain.Bounds bounds(int least, int most) {
        return new NumericDomain.Bounds(Endpoint.inclusive(count(least)),
                Endpoint.inclusive(count(most)));
    }

    private record Read(InputDomain inputs, RuleReadingSource rules) {
        Quantities quantities() {
            return inputs.quantities(rules);
        }
    }

    private static Read read(String source) {
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.answerEverything();
        String module = compilation.modules().get(0);
        Map<String, DeclaredSig> sigs =
                compilation.db().ask(new Bodies.DeclaredSignatures(module)).value();
        RuleReadingSource rules = RuleReadings.of(compilation, module);
        return new Read(InputDomain.of(sigs.get("take"), rules,
                ReadAs.THE_COMPILATION_DOES), rules);
    }
}
