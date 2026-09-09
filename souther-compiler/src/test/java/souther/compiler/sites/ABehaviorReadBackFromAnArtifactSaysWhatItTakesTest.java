package souther.compiler.sites;

import souther.compiler.Compiler;
import souther.compiler.diag.SourcePos;
import souther.compiler.meta.ModulePath;
import souther.compiler.query.Compilation;
import souther.compiler.types.Type;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * What a behavior takes is answered for one read back from an artifact as it is for one written
 * here.
 *
 * <p>A jar carries the declaration and not the witness: the module travels as the source its author
 * wrote, and the importing compilation admits it with the same walk. So the reading that says what a
 * name takes asks the module that declares it and never learns which of the two it is — the answer
 * exists because this compilation built it, whichever tree it was built from.
 *
 * <p>This is the boundary that would fail quietly. A signature serialised into the artifact would be
 * a second representation of what the declaration already says, and a reading that special-cased a
 * borrowed behavior would answer with less about one — nothing at all, most likely, which reads as a
 * name that reaches nothing rather than as a gap.
 *
 * <p>Asked over a stage of a composition, because a stage is a name that reaches a behavior. A body
 * calls what its {@code depends on} clause handed it, which is a parameter holding a function and
 * not the declaration, and the reading says so by answering nothing there.
 */
class ABehaviorReadBackFromAnArtifactSaysWhatItTakesTest {

    private static final String LIBRARY = """
            module shared.money exposing ( Amount, tally )

            data Amount = Int

            behavior tally : (amount: Amount) -> Amount
            """;

    private static final String CONSUMER = """
            module app.order
            import shared.money ( tally )

            behavior twice = tally >-> tally
            """;

    @Test
    void aBorrowedBehaviorNamesItsParametersAndTheirTypes() {
        ModulePath published = ModulePath.of(Compiler.compile(LIBRARY));
        Compilation compilation = Compilation.ofDocuments(
                Map.of("a.sou", CONSUMER), Set.of(), published);
        compilation.answerEverything();

        assertEquals(List.of(), compilation.db().allReports().stream()
                .map(each -> each.report().diagnostic().said().getClass().getSimpleName()).toList(),
                "the model under test compiles");
        CalledBehavior called = SemanticSnapshot.of(compilation.db(), "app.order").orElseThrow()
                .calledAt(over(compilation, "behavior twice =", "tally")).orElseThrow();

        assertEquals("tally", called.name());
        assertEquals(List.of("amount"), namesOf(called));
        assertEquals(List.of("Amount"), typesOf(called));
    }

    /** Where {@code word} is written on the line the consumer writes {@code line} on, in the file
     *  this compilation read it from — a place is in some text and says which. */
    private static SourcePos over(Compilation compilation, String line, String word) {
        List<String> lines = CONSUMER.lines().toList();
        for (int at = 0; at < lines.size(); at++) {
            if (lines.get(at).contains(line)) {
                return new SourcePos(at + 1, lines.get(at).indexOf(word) + 1,
                        compilation.sourceIds().get(0));
            }
        }
        throw new AssertionError("the source under test no longer writes " + line);
    }

    private static List<String> namesOf(CalledBehavior called) {
        List<String> names = new ArrayList<>();
        called.takes().forEach(each -> names.add(each.name()));
        return names;
    }

    private static List<String> typesOf(CalledBehavior called) {
        List<String> types = new ArrayList<>();
        called.takes().forEach(each -> types.add(Type.show(each.type().type())));
        return types;
    }
}
