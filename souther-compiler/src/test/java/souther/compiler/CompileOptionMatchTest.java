package souther.compiler;

import souther.compiler.cst.SourceLayout;
import souther.compiler.diag.CompileException;
import souther.compiler.diag.HumanRenderer;
import souther.compiler.diag.SourceContext;

import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A behavior can consume an optional field by matching {@code Some}/{@code None} (spec §match):
 * {@code Some v} binds the unwrapped element positionally (F#/Elm form); {@code None} has no binding.
 */
class CompileOptionMatchTest {

    private static final String MODULE = """
            module demo

            data Id = String
            data Trip = { id: Id, approver: Id? }
            data Label = String

            behavior approverLabel : (t: Trip) -> Label constructs Label

            let approverLabel (t) =
                match t.approver with
                    | Some a -> Label { value = a.value }
                    | None -> Label { value = "none" }
            """;

    private String run(Map<String, Object> tripObject) throws Exception {
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile(MODULE), getClass().getClassLoader());
        Object trip = Codecs.decoded(loader, "demo.Trip", tripObject);

        Object behavior = Emitted.behavior(loader, "demo", "approverLabel").getConstructor().newInstance();
        Object label = Codecs.apply(behavior, trip);

        // Label is a single-field newtype, so its encoder yields the bare String.
        return (String) Codecs.encode(loader, "demo.Label", label);
    }

    @Test
    void someBindsTheUnwrappedValue() throws Exception {
        assertEquals("e-9", run(Map.of("id", "t-1", "approver", "e-9")));
    }

    @Test
    void noneTakesTheNoneCase() throws Exception {
        assertEquals("none", run(Map.of("id", "t-1")));
    }

    @Test
    void someNestedDestructureBindsTheBaseValue() throws Exception {
        // `Some(Id(v))` opens the wrapped newtype in the pattern, so `v` is the base String — the
        // `.value` on the positional binding disappears, consistent with `X(Y(s))` on user cases.
        String src = MODULE.replace("| Some a -> Label { value = a.value }",
                "| Some(Id(v)) -> Label { value = v }");
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile(src), getClass().getClassLoader());
        Object trip = Codecs.decoded(loader, "demo.Trip", Map.of("id", "t-1", "approver", "e-9"));
        Object behavior = Emitted.behavior(loader, "demo", "approverLabel").getConstructor().newInstance();
        Object label = Codecs.apply(behavior, trip);
        assertEquals("e-9", (String) Codecs.encode(loader, "demo.Label", label));
    }

    @Test
    void someSingleNameParenFormIsRejected() {
        // `Some(a)` opens nothing (unlike `X(a)` on a user case) — the whole-element spelling is
        // `Some a`, so the single-name paren form is rejected to keep one spelling.
        String src = MODULE.replace("| Some a -> Label { value = a.value }",
                "| Some(a) -> Label { value = a.value }");
        CompileException e = assertThrows(CompileException.class, () -> Compiler.compile(src));
        assertTrue(e.getMessage().contains("Some"), e.getMessage());
    }

    @Test
    void someWithAWrongInnerNameIsRejected() {
        // the Option element is Id, not Label — the written newtype must name the element (Elm/F# parity)
        String src = MODULE.replace("| Some a -> Label { value = a.value }",
                "| Some(Label(v)) -> Label { value = v }");
        CompileException e = assertThrows(CompileException.class, () -> Compiler.compile(src));
        // pins the mismatch diagnostic specifically: it names both the element (Id) and the wrong name (Label)
        assertTrue(e.getMessage().contains("Id") && e.getMessage().contains("Label"), e.getMessage());
    }

    @Test
    void noneWithAConstructorFormIsRejected() {
        // None has no payload, so `None(x)` cannot open anything
        String src = MODULE.replace("| None -> Label { value = \"none\" }",
                "| None(x) -> Label { value = x }");
        CompileException e = assertThrows(CompileException.class, () -> Compiler.compile(src));
        assertTrue(e.getMessage().contains("None"), e.getMessage());
    }

    @Test
    void duplicateSomeCasesAreRejected() {
        // two Some arms overlap; the second is dead — an Option match rejects duplicates like a sum match
        String src = MODULE.replace("| Some a -> Label { value = a.value }",
                "| Some a -> Label { value = a.value }\n            | Some(Id(v)) -> Label { value = v }");
        CompileException e = assertThrows(CompileException.class, () -> Compiler.compile(src));
        assertTrue(e.getMessage().contains("Some"), e.getMessage());
    }

    @Test
    void someOpeningANonNewtypeLayerIsRejected() {
        // Id wraps String; String is not a newtype, so `Some(Id(String(v)))` cannot open it
        String src = MODULE.replace("| Some a -> Label { value = a.value }",
                "| Some(Id(String(v))) -> Label { value = v }");
        CompileException e = assertThrows(CompileException.class, () -> Compiler.compile(src));
        assertTrue(e.getMessage().contains("String"), e.getMessage());
    }

    private static final String NESTED = """
            module demo

            data Raw = String
            data Verified = Raw
            data Box = { token: Verified? }
            data Out = String

            behavior open : (b: Box) -> Out constructs Out

            let open (b) =
                match b.token with
                    | Some(Verified(Raw(s))) -> Out { value = s }
                    | None -> Out { value = "none" }
            """;

    @Test
    void someReachesThroughANewtypeOverNewtypeElement() throws Exception {
        // `Some(Verified(Raw(s)))` opens both layers of the element in one pattern; s is the base String
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile(NESTED), getClass().getClassLoader());
        Object box = Codecs.decoded(loader, "demo.Box", Map.of("token", "tok-7"));
        Object behavior = Emitted.behavior(loader, "demo", "open").getConstructor().newInstance();
        assertEquals("tok-7", (String) Codecs.encode(loader, "demo.Out",
                Codecs.apply(behavior, box)));
    }

    @Test
    void someWithAsIsRejectedInFavourOfThePositionalForm() {
        // `as` binds the whole matched value everywhere; the wrapped value is reached positionally.
        String src = MODULE.replace("| Some a ->", "| Some as a ->");
        CompileException e = assertThrows(CompileException.class, () -> Compiler.compile(src));
        assertTrue(e.getMessage().contains("Some"), e.getMessage());
    }

    private static final String RECORD_ELEMENT = """
            module demo

            data Booking = { member: String }
            data Queue = { head: Booking? }
            data Next = { member: String }
            data Nobody

            behavior peek : (q: Queue) -> Next | Nobody constructs Next

            let peek (q) =
                match q.head with
                    | Some { member } -> Next { member = member }
                    | None -> Nobody
            """;

    @Test
    void someDestructuresTheWrappedRecordsFields() throws Exception {
        // A record element is opened by the same `{ field }` pattern a user case takes; the fields are
        // read off the wrapped value, so no `.field` on a positional binding is needed.
        BytesClassLoader loader =
                new BytesClassLoader(Compiler.compile(RECORD_ELEMENT), getClass().getClassLoader());
        Object queue = Codecs.decoded(loader, "demo.Queue", Map.of("head", Map.of("member", "m-1")));
        Object behavior = Emitted.behavior(loader, "demo", "peek").getConstructor().newInstance();
        Map<?, ?> next = (Map<?, ?>) Codecs.encode(loader, "demo.Next", Codecs.apply(behavior, queue));
        assertEquals("m-1", next.get("member"));
    }

    @Test
    void aRecordNamedInsideSomeParensPointsAtTheFieldPattern() {
        // `Some(Booking { member })` is not a second spelling: the parens open a newtype, a record is
        // destructured directly. The error says which form to write.
        String src = RECORD_ELEMENT.replace("| Some { member }", "| Some(Booking { member })");
        CompileException e = assertThrows(CompileException.class, () -> Compiler.compile(src));
        assertTrue(e.getMessage().contains("{ member }") || e.getMessage().contains("{ field }"),
                e.getMessage());
    }

    @Test
    void someAndNoneCannotBeDeclaredAsUserData() {
        // Some/None are the built-in Option cases; declaring one as a data type is rejected, so a
        // `| Some v` pattern is unambiguously about Option, not a user case (ADR-0011, ADR-0035).
        String someSrc = """
                module demo
                data Some = { x: Int }
                """;
        CompileException a = assertThrows(CompileException.class, () -> Compiler.compile(someSrc));
        assertTrue(a.getMessage().contains("Some"), a.getMessage());

        String noneSrc = """
                module demo
                data None = { x: Int }
                """;
        CompileException b = assertThrows(CompileException.class, () -> Compiler.compile(noneSrc));
        assertTrue(b.getMessage().contains("None"), b.getMessage());
    }

    @Test
    void someCannotBeBuilt() {
        // An optional is read, never built: `Some(x)` is not a call the model can write. It used to
        // land on E1401 and advise a Java binding, which is the wrong way out (issue #166).
        String src = """
                module demo
                data Id = String
                data Trip = { id: Id }
                behavior f : (t: Trip) -> Trip
                let f (t) = Some(t.id)
                """;
        CompileException e = assertThrows(CompileException.class, () -> Compiler.compile(src));
        assertEquals("E1303", e.code(), e.getMessage());
        String out = new HumanRenderer(false).render(e.diagnostic(),
                new SourceContext("demo.sou", src, SourceLayout.of(src)), Locale.ENGLISH);
        assertTrue(out.contains("Some"), out);
        assertFalse(out.contains("Java"), "a Java binding does not build an optional either: " + out);
    }

    @Test
    void noneCannotBeWritten() {
        String src = """
                module demo
                data Id = String
                data Trip = { id: Id }
                behavior f : (t: Trip) -> Trip
                let f (t) = None
                """;
        CompileException e = assertThrows(CompileException.class, () -> Compiler.compile(src));
        assertEquals("E1303", e.code(), e.getMessage());
        String out = new HumanRenderer(false).render(e.diagnostic(),
                new SourceContext("demo.sou", src, SourceLayout.of(src)), Locale.ENGLISH);
        assertTrue(out.contains("None"), out);
    }

    @Test
    void theHintPointsAtASumOfTheModelsOwn() {
        // Where the absence is the model's own, the way out is a case of its own sum or a 0-or-1
        // list, not a way to name `Option`.
        String src = """
                module demo
                data Id = String
                data Trip = { id: Id }
                behavior f : (t: Trip) -> Trip
                let f (t) = None
                """;
        CompileException e = assertThrows(CompileException.class, () -> Compiler.compile(src));
        String out = new HumanRenderer(false).render(e.diagnostic(),
                new SourceContext("demo.sou", src, SourceLayout.of(src)), Locale.ENGLISH);
        assertTrue(out.contains("flatMap"), out);
    }
}
