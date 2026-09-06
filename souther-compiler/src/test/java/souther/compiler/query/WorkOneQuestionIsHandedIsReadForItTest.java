package souther.compiler.query;

import souther.compiler.check.StoreWork;
import souther.compiler.meta.ModulePath;
import souther.compiler.source.SourceId;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the store watches while a piece of work is done is read for whoever is answered out of it.
 *
 * <p>A question is kept by what it read, so work one question does and another is handed cannot be
 * handed as a result alone: the second read nothing to have it, and would be kept over an edit to
 * what doing it read. {@link Db#watching} is what keeps the two together — it answers with what the
 * work made and with the reads it was made by, and whoever is handed the work reads the same.
 *
 * <p>Asked of the store rather than of what shares readings, because this is the store's part of
 * it. What shares readings holds that it hands the reads on ({@code
 * AReadingIsHandedOnWithTheReadsItWasMadeByTest}); what is held here is that the reads handed on
 * are the ones the work made, and that reading them again is a dependency of the question that did
 * so.
 */
class WorkOneQuestionIsHandedIsReadForItTest {

    private static final String MODULE = """
            module demo exposing ( twice )

            behavior twice : (n: Int) -> Int
            let twice (n) = n * 2
            """;

    /** The one question the work below asks, so that what a question read is a set with one thing
     *  in it and there is no reading anything else could stand for. */
    private static final Front.Parsed THE_SOURCE = new Front.Parsed(new SourceId("demo.sou"));

    /** A question that does a piece of work the store watches, and answers with nothing of its
     *  own: what it is for is the reads the work leaves behind. */
    private record WhoeverDoesTheWork(String name) implements Key<Boolean> {

        @Override
        public String module() {
            return name;
        }

        @Override
        public Answer<Boolean> compute(Db db) {
            return Answer.of(db.watching(() -> db.ask(THE_SOURCE).present()).value());
        }
    }

    /** A question handed the work, which reads nothing else at all. */
    private record WhoeverIsHandedIt(String name, StoreWork.Reads reads) implements Key<Boolean> {

        @Override
        public String module() {
            return name;
        }

        @Override
        public Answer<Boolean> compute(Db db) {
            reads.here();
            return Answer.of(true);
        }
    }

    @Test
    void theQuestionThatDidTheWorkReadWhatItRead() {
        Db db = compiled().db();
        db.ask(new WhoeverDoesTheWork("demo"));

        assertTrue(db.dependenciesOf(new WhoeverDoesTheWork("demo")).contains(THE_SOURCE),
                "the question that did the work read what the work read");
    }

    @Test
    void andSoDoesWhoeverIsHandedIt() {
        Db db = compiled().db();
        StoreWork.Made<Boolean> made = db.watching(() -> db.ask(THE_SOURCE).present());
        assertEquals(true, made.value(), "the work was done and answered");

        db.ask(new WhoeverIsHandedIt("demo", made.reads()));

        assertEquals(List.of(THE_SOURCE),
                List.copyOf(db.dependenciesOf(new WhoeverIsHandedIt("demo", made.reads()))),
                "a question handed the work read what making it read, and it read nothing else");
    }

    private static Compilation compiled() {
        return Compilation.ofDocuments(Map.of("demo.sou", MODULE), Set.of(), ModulePath.EMPTY);
    }
}
