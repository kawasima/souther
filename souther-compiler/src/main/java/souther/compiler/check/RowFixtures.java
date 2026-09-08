package souther.compiler.check;

import souther.compiler.ast.DefinitionRole;
import souther.compiler.ast.Hir;
import souther.compiler.ast.RowPosition;
import souther.compiler.ast.WrittenName;
import souther.compiler.types.Type;
import souther.compiler.types.ValueName;

import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The definitions a module emits so that what its {@code example} and {@code fake} rows write can be
 * run.
 *
 * <p>One per operand, taking nothing and answering with the value that operand is. It is emitted
 * beside the module's own methods, into the same generated program, so the value a row supplies and
 * the value the behavior is applied to are the one value: nothing is carried between them and
 * nothing converts one representation into another. That is the whole of what makes this a reading
 * of the language rather than a second one — a row runs its own operand the way the module runs its
 * own body.
 *
 * <p>Which operand a definition belongs to is constructed once, where the definitions are made:
 * {@link Emitted#methods()} pairs each operand with the name its method went under, and the reader
 * that invokes one reads that rather than counting the rows again. A second count would be a second
 * order, and a row would run the operand beside the one it wrote.
 */
public final class RowFixtures {

    private RowFixtures() {
    }

    /** The name the {@code n}th operand's method is emitted under. Written nowhere a source could
     *  spell, so what is emitted under it is reached from a row and from nothing else. */
    public static String methodFor(int n) {
        return "$row." + n;
    }

    /** An operand and the position it stands at, in the order the operands are read. */
    public record Placed(Hir.Expr operand, RowPosition position) {
    }

    /**
     * Whether nothing is computed for an operand: an expectation written as a bare type name states
     * which arm the behavior answered with and nothing under it, so there is no value to compute
     * and no method to emit.
     *
     * <p>An expectation and nowhere else. Everywhere a row supplies a value, a bare name is a value
     * like any other and is compiled as one: a unit data is constructed by being named, and a name
     * that stands for no value — a record's, a sum's — is refused where it is written, which is
     * what the language says of it in a body. Read at every position instead, a supplied bare name
     * was the one operand left to a reading of its own.
     */
    static boolean computesNothing(Placed placed) {
        return placed.position() instanceof RowPosition.Asserts
                && assertsAnArm(placed.operand());
    }

    /**
     * Every operand a module's rows write, with the position each stands at.
     *
     * <p>{@code signatures} is what each behavior takes and answers with, as the one place that
     * settles it ({@code PipelineSigs}) worked it out: a composition has no parameter list to read
     * and a dependency another module declares has no declaration here, so a walk over this
     * module's own {@code behavior} forms answers for neither. Reading the shapes back off the
     * syntax is the derivation this change exists to remove, one rung further out.
     *
     * <p>The walk, and the only one. What is emitted and what a row runs both come from here, and
     * they come from here together: {@link #emitted} pairs each operand with its method as it goes,
     * so nothing downstream counts the rows a second time. Two counts would be two orders, and a row
     * would run the value beside the one it wrote.
     */
    public static List<Placed> placed(CheckSurface surface,
                                      Map<ValueName.Behavior, Sig> signatures) {
        Hir.Module module = surface.module();
        List<Placed> out = new java.util.ArrayList<>();
        for (Hir.Example ex : module.examples()) {
            // A row names a behavior of the module it is written in, and a stand-in names the
            // behavior it stands in for — which may be another module's. So the target is lifted to
            // a declaration of this module and the stand-in's is read off what it resolved to.
            Sig sig = signatures.get(new ValueName.Behavior(module.name(), ex.target()));
            for (Hir.ExampleRow row : ex.rows()) {
                for (int i = 0; i < row.inputs().size(); i++) {
                    out.add(new Placed(row.inputs().get(i), supplies(sig, i)));
                }
                for (Hir.With w : row.withs()) {
                    out.add(new Placed(w.value(),
                            new RowPosition.Supplies(answersWith(sigOf(signatures,
                                    w.standsInFor())))));
                }
                switch (row.expected()) {
                    case Hir.Expected.Asserted(Hir.Expr answer) ->
                            out.add(new Placed(answer, new RowPosition.Asserts(answersWith(sig))));
                    // A row whose answer is owed writes no operand at the position, and neither
                    // does one whose answer did not parse. An operand made up here would be a value
                    // nobody wrote, emitted under a name and run.
                    case Hir.Expected.Unanswered _, Hir.Expected.Unwritten _ -> { }
                }
            }
        }
        // Every block written, whether or not its target reached a behavior: an operand stands
        // where it is written, and a block nothing resolved still writes values. What it stands in
        // for is what the classification answered, never read off the block again.
        for (FakeTables.Occurrence occurrence : surface.fakes().written()) {
            Sig sig = occurrence instanceof FakeTables.Occurrence.Resolved resolved
                    ? sigOf(signatures, resolved.behavior()) : null;
            for (Hir.FakeRow row : occurrence.read().rows()) {
                if (row.inputs() != null) {
                    for (int i = 0; i < row.inputs().size(); i++) {
                        out.add(new Placed(row.inputs().get(i), supplies(sig, i)));
                    }
                }
                out.add(new Placed(row.output(), new RowPosition.Supplies(answersWith(sig))));
            }
        }
        return out;
    }

    /** The shape of the behavior a stand-in names, or null where the name denoted none. Asked here
     *  so that a target nothing resolved is one position with nothing to say rather than a lookup
     *  under a key of the wrong kind, which answers null for every stand-in there is. */
    private static Sig sigOf(Map<ValueName.Behavior, Sig> signatures, ValueName.Behavior named) {
        return named == null ? null : signatures.get(named);
    }

    /** What a behavior takes at its {@code i}th input, or null where nothing says. */
    private static RowPosition supplies(Sig sig, int i) {
        return new RowPosition.Supplies(sig == null || i >= sig.inputTypes().size() ? null
                : sig.inputTypes().get(i));
    }

    /** What a behavior answers with, as a type, or null where nothing says. */
    private static Type answersWith(Sig sig) {
        return sig == null ? null : sig.outputType();
    }

    /**
     * What a module emits for its rows, and which operand each emitted definition is for.
     *
     * <p>Both from the one walk that built them, so they cannot disagree. The map is the
     * correspondence itself, keyed on operand identity: a reader that invokes a method looks its
     * operand up here rather than walking the rows again, because a second walk is a second order —
     * a run reads one file's rows out of a module that holds every file's, and a count over the
     * subset numbers them from zero while the methods were emitted over the whole.
     */
    public record Emitted(Map<String, Hir.FnDef> defs, Map<Hir.Expr, String> methods) {
    }

    /**
     * The definitions to emit for {@code module}'s rows, by the name each is emitted under, and the
     * operand each is for.
     *
     * <p>An expectation written as a bare case name has none: it asserts which arm the behavior
     * answered with and nothing under it, so there is no value to compute and nothing to emit.
     */
    public static Emitted emitted(CheckSurface surface, Symbols symbols,
                                  Map<ValueName.Behavior, Sig> signatures) {
        Map<String, Hir.FnDef> out = new LinkedHashMap<>();
        Map<Hir.Expr, String> methods = new IdentityHashMap<>();
        List<Placed> placed = placed(surface, signatures);
        for (int i = 0; i < placed.size(); i++) {
            Hir.Expr operand = placed.get(i).operand();
            RowPosition position = placed.get(i).position();
            if (computesNothing(placed.get(i))) {
                continue;
            }
            String name = methodFor(i);
            methods.put(operand, name);
            // Wrapped and then handed to the stage every definition of this module went through.
            // Which passes that is is the stage's to know: once a row's operand is a function, where
            // it came from stops mattering to the pipeline that reads functions, and a list of
            // passes here would be that pipeline written a second time — the shape this change
            // exists to remove.
            // Carrying no termination guarantee, because a row's operand does not claim one: what
            // stops it is the budget the row is evaluated under, which is why a row may apply a
            // `partial` helper at all. Claiming one here would hold the operand to a rule about
            // declarations — and report it as a declaration, under a name the author never wrote and
            // cannot mark.
            // What the position contributes, written as what this function answers with: it is
            // the channel the pipeline already pushes into a body, so a row's brackets take their
            // collection from it as an empty collection in any body takes its element type. For a
            // value the model is given the same type is also required, and the pipeline's own check
            // of a declared return holds it. An expectation contributes and requires nothing — a
            // row may state what the behavior does not answer with — so the check is not made of it.
            //
            // The position travels on the definition rather than beside it. Which of the two this
            // is, and every other question a rule has about the position, is then asked of the
            // definition the rule is holding; a set of names kept alongside is a second thing to
            // keep in step, and the rule that forgot to consult one asked whether the name was
            // authored instead — which is true of any definition another module wrote.
            Type given = position.contextual();
            Hir.RetType answers = given == null ? null : retTypeOf(given, operand.pos());
            Hir.FnDef wrapped = new Hir.FnDef(WrittenName.synthetic(name, operand.pos()),
                    surface.name(), List.of(), answers, new Hir.FnBody.Written(operand),
                    new Hir.Modifiers(true, true), new DefinitionRole.RowValue(position),
                    operand.pos());
            out.put(name, Desugared.Fn.desugar(wrapped, symbols).read());
        }
        return new Emitted(out, methods);
    }

    private static Hir.RetType retTypeOf(Type type, souther.compiler.diag.SourcePos pos) {
        return new Hir.RetType(List.of(Hir.TypeRef.of(type, pos)), pos);
    }

    /** Whether an expression is a bare name standing for a declared type — the form an expectation
     *  takes when it asserts the arm and nothing under it. */
    static boolean assertsAnArm(Hir.Expr e) {
        return e instanceof Hir.Var.Denoting d
                && d.denotes() instanceof souther.compiler.types.ValueName.OfType;
    }
}
