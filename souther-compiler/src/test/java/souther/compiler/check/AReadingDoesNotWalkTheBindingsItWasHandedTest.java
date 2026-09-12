package souther.compiler.check;

import souther.compiler.ast.Hir;
import souther.compiler.query.Bodies;
import souther.compiler.query.Compilation;
import souther.compiler.query.Names;
import souther.compiler.query.Scopes;
import souther.compiler.types.BindingId;
import souther.compiler.types.Type;

import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * What a walk is handed about the bindings in force where it starts is asked after, one binding at a
 * time, and never gone through.
 *
 * <p>What is handed over is what a module's declarations say about every parameter its behaviors
 * wrote. It is worked out once for a revision and handed to every walk made against it, so a walk
 * that went through it would cost what the module is however small the question was — which is the
 * whole of what asking it once was for. An editor asks one of these per keystroke.
 *
 * <p>Held here rather than where the table is worked out, because this is where it would be undone:
 * a walk keeps its own bindings as it enters them, and seeding that from what it was handed is one
 * line, reads as housekeeping, and puts the cost back where a check on the table being answered once
 * cannot see it. So the table arrives as something that answers a binding and refuses to be walked.
 */
class AReadingDoesNotWalkTheBindingsItWasHandedTest {

    private static final String MODEL = """
            module demo

            data Cost  = { value: Int }
            data Draft = { plannedCost: Cost }

            behavior submit : (request: Draft) -> Int

            let submit (request) = request.plannedCost.value
            """;

    /**
     * A body whose type rests on a parameter, read against a table that answers what it is asked and
     * nothing else.
     *
     * <p>The answer is the control. A walk handed no bindings states nothing about {@code request},
     * so a reading that came to {@code Int} is one that asked this table for the binding and was
     * told — and a table nothing asked would let a walk that copies it pass for one that does not.
     */
    @Test
    void aWalkAsksTheTableForABindingRatherThanGoingThroughIt() {
        Compilation compilation = Compilation.ofSource(MODEL, "Main");
        Map<BindingId, BindingEvidence> declared = compilation.db()
                .ask(new Bodies.DeclaredParameterBindings("demo")).value();
        assertFalse(declared.isEmpty(),
                "the declarations speak for a parameter of the model under test");

        Type read = reading(compilation, new OnlyAsked(declared))
                .declaredTypeOf(bodyOf(compilation, "submit"));

        assertEquals(Type.INT, read,
                "`request.plannedCost.value` is what the signature and the declarations say");
    }

    private static DeclaredTypeReading reading(Compilation compilation,
                                               Map<BindingId, BindingEvidence> bound) {
        Symbols symbols = Scopes.derived(compilation.db(), "demo").value();
        return new DeclaredTypeReading(
                new DeclarationFacts(new FieldRead(symbols, new ResolvedFieldTypes(symbols),
                        FieldRead.Unreadable.MAKES_NOTHING_READABLE)),
                compilation.db().ask(new Bodies.ModuleDefinitions("demo")).value(),
                compilation.db().ask(new Bodies.Reachable("demo")).value(),
                bound);
    }

    /** What the {@code let} under {@code name}'s signature wrote, read off the resolved module —
     *  where an implementation of a behavior is, its parameters being the ones the table speaks
     *  for. */
    private static Hir.Expr bodyOf(Compilation compilation, String name) {
        Hir.Module resolved = compilation.db().ask(new Names.Resolved("demo")).value();
        for (Hir.FnDef written : resolved.fns()) {
            if (name.equals(written.written().canonical())) {
                return assertInstanceOf(Hir.FnBody.Written.class, written.body()).expr();
            }
        }
        throw new AssertionError("the model under test no longer writes a `let " + name + "`");
    }

    /**
     * A table that answers what one binding says and refuses everything else.
     *
     * <p>Every way of reaching more than one entry is a way of costing what the table holds, so all
     * of them refuse rather than the one a walk happens to use today. Writing refuses as well: what
     * is handed over is a revision's, and a walk that put its own binding in it would be writing
     * into what the next walk is handed.
     */
    private record OnlyAsked(Map<BindingId, BindingEvidence> answering)
            implements Map<BindingId, BindingEvidence> {

        @Override
        public BindingEvidence get(Object key) {
            return answering.get(key);
        }

        @Override
        public boolean containsKey(Object key) {
            return answering.containsKey(key);
        }

        @Override
        public int size() {
            return answering.size();
        }

        @Override
        public boolean isEmpty() {
            return answering.isEmpty();
        }

        @Override
        public Set<Map.Entry<BindingId, BindingEvidence>> entrySet() {
            throw walked("entrySet");
        }

        @Override
        public Set<BindingId> keySet() {
            throw walked("keySet");
        }

        @Override
        public Collection<BindingEvidence> values() {
            throw walked("values");
        }

        @Override
        public void forEach(BiConsumer<? super BindingId, ? super BindingEvidence> each) {
            throw walked("forEach");
        }

        @Override
        public boolean containsValue(Object value) {
            throw walked("containsValue");
        }

        @Override
        public BindingEvidence put(BindingId key, BindingEvidence value) {
            throw written();
        }

        @Override
        public BindingEvidence remove(Object key) {
            throw written();
        }

        @Override
        public void putAll(Map<? extends BindingId, ? extends BindingEvidence> more) {
            throw written();
        }

        @Override
        public void clear() {
            throw written();
        }

        private static AssertionError walked(String how) {
            return new AssertionError("a walk went through the bindings it was handed, by " + how
                    + " — which costs what the module declares, at every question asked of it");
        }

        private static AssertionError written() {
            return new AssertionError("a walk wrote into the bindings it was handed, which the"
                    + " next walk against this revision is handed too");
        }
    }
}
