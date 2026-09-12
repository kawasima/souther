package souther.compiler.sites;

import souther.compiler.check.BindingEvidence;
import souther.compiler.check.ParameterFact;
import souther.compiler.cst.SourceLayout;
import souther.compiler.diag.SourcePos;
import souther.compiler.meta.ModulePath;
import souther.compiler.query.Answer;
import souther.compiler.query.Bodies;
import souther.compiler.query.Compilation;
import souther.compiler.query.Db;
import souther.compiler.source.SourceId;
import souther.compiler.types.BindingId;
import souther.compiler.types.Type;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What every behavior of a module declares about its parameters is worked out once for a revision,
 * however many editor requests are asked against it.
 *
 * <p>A snapshot is built where it is used and dropped there, so a reader that worked the table out
 * while answering would work it out again for the next keystroke — and what it works out is the
 * whole module, whichever behavior the cursor is in. The table is a question of its own for that
 * reason, and this holds to the two things that makes true: the request is what causes it to be
 * answered, and a second request against the same revision is handed the first one's answer.
 *
 * <p>Both keys, and not the one the declarations are read into. What a body's names are typed by is
 * a map under the binding, which is a walk of the same length over the same module — left to the
 * caller it is the reconstruction this removes, wearing a different name. So a reading that asked
 * for the list and built the map where it stood would pass a check that only watched the list.
 */
class WhatEveryBehaviorDeclaresIsWorkedOutOncePerRevisionTest {

    private static final String MODULE = """
            module m

            data Cost = { value: Int }

            data Draft = { plannedCost: Cost }

            behavior price : (draft: Draft) -> Cost

            behavior submit : (request: Draft) -> Int
                depends on price

            let submit (request, price) = request.plannedCost.value
            """;

    private static final Bodies.DeclaredParameters FACTS = new Bodies.DeclaredParameters("m");
    private static final Bodies.DeclaredParameterBindings BINDINGS =
            new Bodies.DeclaredParameterBindings("m");

    @Test
    void oneRevisionWorksOutWhatItsParametersAreOnce() {
        Compilation compilation = Compilation.ofDocuments(
                Map.of("m.sou", MODULE), Set.of(), ModulePath.EMPTY);
        Db db = compilation.db();
        assertFalse(db.isComputed(FACTS), "nothing has asked what the parameters are yet");
        assertFalse(db.isComputed(BINDINGS), "nor what a body's names among them are typed by");

        // One request, answered by a snapshot that is made for it and dropped after it.
        askWhatIsLeftOfTheDot(compilation);

        assertTrue(db.isComputed(FACTS),
                "the request is answered out of what the declarations say about every parameter");
        assertTrue(db.isComputed(BINDINGS),
                "and the table its reading walks is that answer projected, not a map built where "
                        + "the reading was asked for");

        Answer<List<ParameterFact>> facts = db.ask(FACTS);
        Answer<Map<BindingId, BindingEvidence>> bindings = db.ask(BINDINGS);

        // And a second request, from a second snapshot, which is what an editor does at the next
        // keystroke that changes nothing.
        askWhatIsLeftOfTheDot(compilation);

        assertSame(facts, db.ask(FACTS), "the revision said what it says once");
        assertSame(bindings, db.ask(BINDINGS), "and so did the projection the reading walks");
    }

    /**
     * What a request comes to, so that a store that answered nothing cannot pass for one that
     * answered once.
     *
     * <p>Asked of both readers of the table: what is left of a {@code .} goes through the bindings,
     * and the hints go through the list. A module whose names stopped resolving would leave both
     * answers absent, which is kept once as readily as an answer with anything in it.
     */
    @Test
    void bothReadersOfTheTableAreAnsweredFromIt() {
        Compilation compilation = Compilation.ofDocuments(
                Map.of("m.sou", MODULE), Set.of(), ModulePath.EMPTY);

        assertEquals("Cost", Type.show(askWhatIsLeftOfTheDot(compilation).type().type()),
                "`request.plannedCost` is what the declarations say it is");
        assertEquals(List.of("Draft"),
                snapshot(compilation).parametersIn(sourceId(compilation)).stream()
                        .map(each -> Type.show(each.type().type())).toList(),
                "and the hints say what arrives at the input the signature wrote; the parameter "
                        + "the clause hands over has no spelling to stand where a hint does");
    }

    /** What the snapshot says is left of the {@code .} in {@code request.plannedCost.value}. */
    private static MemberReceiver.Value askWhatIsLeftOfTheDot(Compilation compilation) {
        return assertInstanceOf(MemberReceiver.Value.class,
                snapshot(compilation).memberReceiverAround(over(compilation, ".value"))
                        .orElseThrow(() -> new AssertionError("nothing was written at the cursor")));
    }

    private static SemanticSnapshot snapshot(Compilation compilation) {
        return SemanticSnapshot.of(compilation.db(), "m")
                .orElseThrow(() -> new AssertionError("the module under test can be asked about"));
    }

    /** The place {@code word} is written at, read off the text as it is laid out. */
    private static SourcePos over(Compilation compilation, String word) {
        int at = MODULE.indexOf(word);
        if (at < 0) {
            throw new AssertionError("the source under test no longer writes " + word);
        }
        return SourceLayout.of(MODULE, sourceId(compilation)).placeAt(at);
    }

    private static SourceId sourceId(Compilation compilation) {
        return compilation.sourceIds().get(0);
    }
}
