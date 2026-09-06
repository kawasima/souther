package souther.compiler.check;

import souther.compiler.core.Core;
import souther.compiler.diag.SourcePos;
import souther.compiler.types.BinOp;
import souther.compiler.types.BindingId;
import souther.compiler.types.BindingOwner;
import souther.compiler.types.SourceConstructOrigin;
import souther.compiler.types.Type;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A binding is where the environment a clause is read in changes, and it is not a leaf.
 *
 * <p>What a clause states is answered upward and what its names mean is handed downward. Held as a
 * fold with only the first direction, there was nowhere a binding could be, so it arrived at every
 * evaluator as a shape none of them had a word for — and since a helper call expands to a binding
 * holding the argument and the helper's body written against it, that is every rule an author stated
 * by naming it.
 *
 * <p>Over the shape and the fold rather than over a compiled module, because what is fixed here is
 * that no evaluator is ever handed a binding: an evaluator that learned to read one would be a
 * second account of what a binder means, which is the environment's ({@code ADR-0106}). The
 * environment is a string so that the transitions can be read off the answer — what it is in the
 * check is {@link Denotations}, and what makes one is {@link Terms#inside}.
 */
class AClauseUnderABindingIsReadInsideItTest {

    private static final SourcePos POS = new SourcePos(1, 1);
    private static final BindingOwner OWNER = new BindingOwner.OfValue("demo", "f");

    /** What a reading is handed at each leaf: the part, whether it is stated, and where it stood. */
    private record AtALeaf(Core part, boolean positive, String at) {}

    /**
     * A reading that records what it was handed and says nothing else.
     *
     * <p>Its state is the leaves in the order they arrived, so a conjunction and a choice both
     * compose by joining the lists: what is being fixed is what reached a leaf, not what any
     * language made of it.
     */
    private static final class Recording implements ClauseReading<List<AtALeaf>, String> {

        private final List<AtALeaf> leaves = new ArrayList<>();

        @Override
        public List<AtALeaf> whole(ClauseExpr.Part part, String at) {
            leaves.add(new AtALeaf(part.of(), part.positive(), at));
            return List.of(leaves.get(leaves.size() - 1));
        }

        /** Both connectives are walked into, since what is being recorded is what reached a part. */
        @Override
        public Descent<List<AtALeaf>> at(ClauseExpr.Joined join) {
            return new Descent.Into<>(Recording::joined);
        }

        private static List<AtALeaf> joined(List<AtALeaf> one, List<AtALeaf> other) {
            List<AtALeaf> out = new ArrayList<>(one);
            out.addAll(other);
            return out;
        }
    }

    /** Each binding entered as its binder's name, so the environment a leaf is read at spells out
     *  the bindings it stands under. */
    private static final ClauseScope<String> NAMING = (li, outside) -> outside + "/" + li.name();

    private static Core.Read read(String name, int ordinal, Type type) {
        return new Core.Read(name, new BindingId(OWNER, ordinal), type, POS);
    }

    private static Core.LetIn let(String name, int ordinal, Core value, Core body) {
        return new Core.LetIn(new Core.Binder(name, new BindingId(OWNER, ordinal)), value, body,
                body.type(), POS);
    }

    private static Core.Binary binary(BinOp op, Core left, Core right) {
        return new Core.Binary(op, left, right, SourceConstructOrigin.unwritten(), Type.BOOL, POS);
    }

    /** `n >= 1`, the rule every clause below states one way or another. */
    private static Core.Binary rule(Core subject) {
        return binary(BinOp.GE, subject, new Core.Int(1, Type.INT, POS));
    }

    private static List<AtALeaf> reading(Core clause) {
        Recording recording = new Recording();
        return recording.read(clause, true, "", NAMING);
    }

    @Test
    void theBindingIsAShapeAndTheClauseUnderItIsTheLeaf() {
        Core.Read bound = read("$p", 1, Type.INT);
        Core clause = let("$p", 1, read("value", 0, Type.INT), rule(bound));

        assertTrue(ClauseExpr.of(clause, true) instanceof ClauseExpr.Scoped,
                "a binding is where the environment changes, not a part with no connective");
        assertEquals(List.of(new AtALeaf(rule(bound), true, "/$p")), reading(clause),
                "the rule under the binding is the leaf, read inside it");
    }

    @Test
    void noEvaluatorIsEverHandedABinding() {
        Core clause = let("$p", 1, read("value", 0, Type.INT), rule(read("$p", 1, Type.INT)));

        assertEquals(List.of(), reading(clause).stream()
                        .filter(one -> one.part() instanceof Core.LetIn).toList(),
                "what a binder means is the environment's answer and no evaluator's");
    }

    /**
     * And there is nowhere for one to be handed.
     *
     * <p>The same thing said of the types rather than of a walk. What a reading is given is a part,
     * and the parts are the leaf and the connective it takes whole; a binding is a shape of its own
     * and goes to {@link ClauseScope}. So a second account of what a binder means is not something
     * anybody has to be stopped from writing — it does not compile.
     */
    @Test
    void aBindingIsNotAPartAReadingCanBeHanded() {
        assertEquals(List.of(List.of(ClauseExpr.Leaf.class, ClauseExpr.Joined.class), false),
                List.of(List.of(ClauseExpr.Part.class.getPermittedSubclasses()),
                        ClauseExpr.Part.class.isAssignableFrom(ClauseExpr.Scoped.class)),
                "what a reading may be handed, and whether a binding is among it");
    }

    @Test
    void eachBindingOnTheWayDownIsEnteredInTurn() {
        Core inner = let("$q", 2, read("$p", 1, Type.INT), rule(read("$q", 2, Type.INT)));
        Core clause = let("$p", 1, read("value", 0, Type.INT), inner);

        assertEquals(List.of("/$p/$q"), reading(clause).stream().map(AtALeaf::at).toList(),
                "a helper calling a helper stands under both bindings");
    }

    @Test
    void aConnectiveUnderABindingIsReadInsideIt() {
        Core.Read bound = read("$p", 1, Type.INT);
        Core both = binary(BinOp.AND, rule(bound),
                binary(BinOp.LE, bound, new Core.Int(9, Type.INT, POS)));
        Core clause = let("$p", 1, read("value", 0, Type.INT), both);

        assertEquals(List.of("/$p", "/$p"), reading(clause).stream().map(AtALeaf::at).toList(),
                "a helper whose body joins two rules states both of them, inside the binding");
    }

    @Test
    void aBindingUnderAConnectiveIsEnteredForItsOwnSideAlone() {
        Core.Read bound = read("$p", 1, Type.INT);
        Core clause = binary(BinOp.AND,
                let("$p", 1, read("value", 0, Type.INT), rule(bound)),
                rule(read("value", 0, Type.INT)));

        assertEquals(List.of("/$p", ""), reading(clause).stream().map(AtALeaf::at).toList(),
                "a binding reaches what stands under it and nothing beside it");
    }

    /**
     * And a shape the fold does not recognise is handed on as itself.
     *
     * <p>The negative control. What a binding settles is what a name means; what a leaf means is
     * the leaf language's, and the fold widens neither — so a part no connective and no binding, of
     * whatever shape, arrives at the reading as the very node it was. Without this, a change that
     * rewrote leaves on the way down would satisfy everything above.
     */
    @Test
    void aLeafIsHandedOnAsTheNodeItWas() {
        Core.Read bare = read("$p", 1, Type.INT);
        Core clause = let("$p", 1, read("value", 0, Type.INT), bare);

        assertEquals(List.of(new AtALeaf(bare, true, "/$p")), reading(clause),
                "the fold changes the environment and nothing else");
    }

    /**
     * A binding under a closure keeps the closure's own account of its parameter.
     *
     * <p>Two environments and two questions. What a name denotes is the environment's, and the body
     * of a binding is read inside it; what a name is <em>called</em> is the naming walk's own, and a
     * closure's parameter is called where it is bound rather than which binding it is, so that two
     * lambdas written alike are one term. A binding entered in the first alone lost the second, and
     * {@code x -> let y = x in y} stopped being the term {@code x -> x} is.
     *
     * <p>Held on the naming and not on a clause, because that is where the two meet: a binding is
     * called what it was given, and what it was given is read under the names in scope where it
     * stands.
     */
    @Test
    void aBindingUnderAClosureKeepsThePlaceItsParameterIsBoundAt() {
        Terms terms = RuleReadings.termsOfNoClauseFiled(
                Symbols.none(souther.compiler.DefaultStdlib.get()),
                souther.compiler.query.ReadAs.THE_COMPILATION_DOES);
        Core.Binder x = new Core.Binder("x", new BindingId(OWNER, 7));
        Core.Binder a = new Core.Binder("a", new BindingId(OWNER, 8));

        Core plain = block(x, new Core.Read("x", x.binding(), Type.INT, POS));
        Core bound = block(x, let("y", 9, new Core.Read("x", x.binding(), Type.INT, POS),
                new Core.Read("y", new BindingId(OWNER, 9), Type.INT, POS)));
        Core renamed = block(a, let("b", 10, new Core.Read("a", a.binding(), Type.INT, POS),
                new Core.Read("b", new BindingId(OWNER, 10), Type.INT, POS)));

        assertEquals(terms.subjectOf(plain, Denotations.none()),
                terms.subjectOf(bound, Denotations.none()),
                "a name given a parameter is that parameter");
        assertEquals(terms.subjectOf(bound, Denotations.none()),
                terms.subjectOf(renamed, Denotations.none()),
                "and two closures written alike are one term, whatever the names are");
    }

    private static Core block(Core.Binder param, Core body) {
        return new Core.Block(List.of(param), body, Type.INT, POS);
    }

    @Test
    void aDenialIsCarriedThroughTheBinding() {
        Core.Read bound = read("$p", 1, Type.INT);
        Core clause = binary(BinOp.EQ,
                let("$p", 1, read("value", 0, Type.INT), rule(bound)),
                new Core.Bool(false, Type.BOOL, POS));

        assertEquals(List.of(new AtALeaf(rule(bound), false, "/$p")), reading(clause),
                "denying what a helper states denies the rule its body states, read inside it");
    }
}
