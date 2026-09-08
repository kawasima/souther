package souther.cli;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A body compares a field every case of a sum spreads, and a row writes that field under one case.
 *
 * <p>So the line the comparison draws falls under each case. One rule, one line per case, on the
 * same number — and the report says so where before it said the comparison was against values no
 * line can be drawn on, which is a cause nothing established: the same comparison through a record
 * draws its line and always did.
 */
class ALineDrawnOnASharedNameFallsUnderEachCaseTest {

    /** A guard on a shared field of a HELD parameter. */
    private static String guarded(String held) {
        return """
            module example.line

            data Paging = { limit: Int }
            data A = { ...Paging, x: Int }
            data B = { ...Paging, y: Int }
            data Q = A | B

            data Ok
            data No

            behavior read : (q: HELD) -> Ok | No

            let read (q) = {
                guard q.limit <= 10 else No
                Ok
            }
            """.replace("HELD", held);
    }

    /**
     * The line is drawn, once per case, and no cause is invented for it not being.
     *
     * <p>Two borders where the same model through one of its cases has one, because a value of the
     * sum is of one case and a row at that number is written under the case it picked.
     */
    @Test
    void theLineIsDrawnUnderEachCase() throws Exception {
        String throughTheSum = report(guarded("Q"));
        String throughACase = report(guarded("A"));

        assertTrue(throughACase.contains("borders 1"),
                () -> "the comparison draws its line where no sum is in the way:\n" + throughACase);
        assertTrue(throughTheSum.contains("borders 2"),
                () -> "and one under each case where one is:\n" + throughTheSum);
        assertFalse(throughTheSum.contains("no line can be drawn on here"),
                () -> "and nothing says the values could not be compared:\n" + throughTheSum);
    }

    /**
     * The rows offered are under the cases, at the values the line falls between.
     *
     * <p>Which is the whole of what the fan-out is for: a row is a value somebody writes, and
     * {@code q.limit} is not somewhere a value is written.
     */
    @Test
    void theRowsOfferedAreUnderTheCases() throws Exception {
        String generated = report(guarded("Q"), "--generate");

        assertTrue(generated.contains("A { limit = 10"), () -> generated);
        assertTrue(generated.contains("B { limit = 10"), () -> generated);
        assertFalse(generated.contains("q.limit ="),
                () -> "and none of them is offered at a name no row is written at:\n" + generated);
    }

    /**
     * A number taken of a shared field falls the same way, with the operation kept.
     *
     * <p>What moves is where the number is taken. An account of what an operation takes is written
     * for a shape, so a term moved to a location of another shape would be measured by an account
     * written for something else — which is why the move is put to the same question the term was
     * built under.
     */
    @Test
    void aNumberTakenOfASharedNameFallsTheSameWay() throws Exception {
        String report = report("""
                module example.line

                data Named = { name: String }
                data A = { ...Named, x: Int }
                data B = { ...Named, y: Int }
                data Q = A | B

                data Ok
                data No

                behavior read : (q: Q) -> Ok | No

                let read (q) = {
                    guard String.length(q.name) <= 10 else No
                    Ok
                }
                """);

        assertTrue(report.contains("borders 2"), () -> report);
        assertFalse(report.contains("about a value made from this one"),
                () -> "and nothing says the derived value could not be worked out:\n" + report);
    }

    /** A name that crosses two sums falls at every pairing of their cases. */
    @Test
    void aNameCrossingTwoSumsFallsAtEveryPairing() throws Exception {
        String report = report("""
                module example.line

                data Inner = { deep: Int }
                data IA = { ...Inner, p: Int }
                data IB = { ...Inner, r: Int }
                data IS = IA | IB

                data Outer = { s: IS }
                data OA = { ...Outer, m: Int }
                data OB = { ...Outer, n: Int }
                data OS = OA | OB

                data Ok
                data No

                behavior read : (q: OS) -> Ok | No

                let read (q) = {
                    guard q.s.deep <= 10 else No
                    Ok
                }
                """);

        assertTrue(report.contains("borders 4"),
                () -> "two cases times two cases:\n" + report);
    }

    /**
     * A line between two positions falls under each case too, where one of the two names crosses.
     *
     * <p>The comparison is read once and stays one comparison. What moves is where its quantity is
     * taken — under each case a value of the sum can turn out to be — so a point a row is asked for
     * is at a position a row can be written at.
     */
    @Test
    void aLineBetweenTwoPositionsFallsUnderEachCase() throws Exception {
        String report = report("""
                module example.line

                data Paging = { limit: Int }
                data A = { ...Paging, x: Int }
                data B = { ...Paging, y: Int }
                data Q = A | B

                data Ok
                data No

                behavior read : (q: Q, cap: Int) -> Ok | No

                let read (q, cap) = {
                    guard q.limit < cap else No
                    Ok
                }
                """);

        assertTrue(report.contains("borders 2"), () -> "one under each case:\n" + report);
        assertFalse(report.contains("read/q.limit"),
                () -> "and no point is asked for at a name no row is written at:\n" + report);
    }

    /**
     * Where both names of such a line reach positions under the cases, the line is not placed, and
     * the report says that is what happened.
     *
     * <p>Which of the positions go together is a question about the model: two names narrowed by one
     * value are narrowed together, and two names under separate choices are not. Paired anyway, one
     * line would come back as a line between every case of one and every case of the other — a count
     * of borders nothing in the model asks for.
     *
     * <p>And it is not handed on either. A line at a name no row is written at reaches the generator,
     * which answers that it could not build a value there — a reason nobody established about a
     * place nobody meant. What an author is owed is the pairing.
     */
    @Test
    void aLineBothOfWhoseNamesCrossIsNotPlacedAndSaysSo() throws Exception {
        String report = report("""
                module example.line

                data Paging = { limit: Int }
                data A = { ...Paging, x: Int }
                data B = { ...Paging, y: Int }
                data Q = A | B

                data Ok
                data No

                behavior read : (one: Q, two: Q) -> Ok | No

                let read (one, two) = {
                    guard one.limit < two.limit else No
                    Ok
                }
                """);

        assertTrue(report.contains("how those positions pair up is not worked out"),
                () -> "the pairing is what an author is owed:\n" + report);
        assertFalse(report.contains("one.limit = two.limit"),
                () -> "and no point is asked for at a name no row is written at:\n" + report);
        assertTrue(report.contains("measurement: partial"),
                () -> "a line this had nowhere to put is a measurement short of something:\n"
                        + report);
    }

    /**
     * A clause naming a shared field that comes to no line names the positions it is about.
     *
     * <p>Not only the ones on its own side of the comparison. {@code q.limit <= cap} relates two
     * positions and divides neither, and what an author is owed is both of them — read at the sum
     * alone, the finding named the number beside it and said nothing about the field the clause is
     * as much about.
     */
    @Test
    void aClauseThatComesToNoLineNamesTheSharedFieldUnderEachCase() throws Exception {
        String model = """
                module example.line

                data Paging = { limit: Int }
                data A = { ...Paging, x: Int }
                data B = { ...Paging, y: Int }
                data Q = A | B

                data Holder = { q: HELD, cap: Int }
                    invariant fits = q.limit <= cap

                data Ok

                behavior read : (h: Holder) -> Ok

                let read (h) = Ok
                """;
        String throughTheSum = report(model.replace("HELD", "Q"));

        assertTrue(throughTheSum.contains("about `h.q@A.limit`"), () -> throughTheSum);
        assertTrue(throughTheSum.contains("about `h.q@B.limit`"), () -> throughTheSum);
        assertTrue(report(model.replace("HELD", "A")).contains("about `h.q.limit`"),
                "which is what the same clause says where no sum is in the way");
    }

    /**
     * And a clause whose end a choice in it left open names the shared field under each case.
     *
     * <p>The same question asked of the other reading. Where the values stop under a choice is what
     * the alternatives leave together, and a branch nothing reads leaves it as far out as that
     * branch allows — so the field is owed the sentence wherever it is read, and reading it at the
     * sum alone would say it of a name no row is written at.
     */
    @Test
    void aClauseWhoseEndAChoiceLeftOpenNamesTheSharedFieldUnderEachCase() throws Exception {
        String model = """
                module example.line

                data Paging = { limit: Int }
                data A = { ...Paging, x: Int }
                data B = { ...Paging, y: Int }
                data Q = A | B

                data Holder = { q: HELD }
                    invariant fits = q.limit >= 2 || Int.abs(q.limit) >= 5

                data Ok

                behavior read : (h: Holder) -> Ok

                let read (h) = Ok
                """;
        String throughTheSum = report(model.replace("HELD", "Q"));

        assertTrue(throughTheSum.contains("left open by a choice in it whose other alternative"
                        + " this compiler does not read, about `h.q@A.limit`"),
                () -> throughTheSum);
        assertTrue(throughTheSum.contains("left open by a choice in it whose other alternative"
                        + " this compiler does not read, about `h.q@B.limit`"),
                () -> throughTheSum);
        assertTrue(report(model.replace("HELD", "A"))
                        .contains("left open by a choice in it whose other alternative this"
                                + " compiler does not read, about `h.q.limit`"),
                "which is what the same clause says where no sum is in the way");
    }

    /**
     * A line neither of whose names was filed stays where the model wrote it.
     *
     * <p>Nothing to move it to, and the two ways of that are one answer about the line: a name
     * already at a position of this reading, and one the reading stopped before reaching. So the
     * line is drawn once, at the name as written — and where that is a name no row is written at,
     * the generator is what says it could not build a value there. Which of the two it was is what
     * the reading says where it stopped, and is not something the number of lines answers.
     */
    @Test
    void aLineNeitherOfWhoseNamesWasFiledStaysWhereItWasWritten() throws Exception {
        String report = report("""
                module example.line

                data Paging = { limit: Int }
                data A = { ...Paging, x: Int }
                data B = { ...Paging, q: Q }
                data Q = A | B
                data Outer = { q: Q }

                data Ok
                data No

                behavior read : (o: Outer, cap: Int) -> Ok | No

                let read (o, cap) =
                    match o.q with
                        | A -> Ok
                        | B as b -> {
                            guard b.q.limit < cap else No
                            Ok
                          }
                """);

        assertTrue(report.contains("borders 1"),
                () -> "one line, not one per case of a sum nothing was filed under:\n" + report);
        assertTrue(report.contains("read as read/o.q@B.q.limit:"),
                () -> "and it is at the name the model wrote, which is where it was measured:\n"
                        + report);
        assertFalse(report.contains("read/o.q@B.q@A.limit"),
                () -> "the reading stopped before the cases, so nothing was filed under them:\n"
                        + report);
        assertTrue(report.contains("nothing here could build a representative for o.q@B.q.limit"),
                () -> "and the generator is what says nothing can be written there:\n" + report);
        assertFalse(report.contains("how those positions pair up is not worked out"),
                () -> "nothing was filed, so no pairing was ever in question:\n" + report);
    }

    /**
     * What a model with no sum in the way answers, which is what the fan-out has to come to.
     *
     * <p>Named by the case rather than by the {@code @} it is written with. A rule this report
     * cites by where it was written is spelled {@code comparison@14:19}, so a report holding no
     * narrowing at all holds that character — and the check that read it as one was passing on a
     * model whose points nothing printed.
     */
    @Test
    void nothingChangesWhereNoNameCrosses() throws Exception {
        assertEquals(report(guarded("A")), report(guarded("A")));
        String report = report(guarded("A"));
        assertFalse(report.contains("@A") || report.contains("@B"),
                () -> "no narrowing is named anywhere in this one:\n" + report);
    }

    private static String report(String model, String... extra) throws Exception {
        Path file = Files.createTempDirectory("souther-line").resolve("model.sou");
        Files.writeString(file, model);
        List<String> args = new ArrayList<>(List.of("examples", file.toString()));
        args.addAll(List.of(extra));
        PrintStream was = System.out;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
        try {
            Main.dispatch(args.toArray(String[]::new));
        } finally {
            System.setOut(was);
        }
        return out.toString(StandardCharsets.UTF_8);
    }
}
