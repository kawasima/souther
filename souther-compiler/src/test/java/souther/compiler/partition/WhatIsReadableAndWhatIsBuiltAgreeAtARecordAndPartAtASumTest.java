package souther.compiler.partition;

import org.junit.jupiter.api.Test;

import souther.compiler.ast.Hir;
import souther.compiler.check.ConstructionDescent;
import souther.compiler.check.NewtypeInners;
import souther.compiler.check.PublishedDeclarations;
import souther.compiler.check.ReadableFields;
import souther.compiler.check.ResolvedFieldTypes;
import souther.compiler.check.Shape;
import souther.compiler.check.Sig;
import souther.compiler.check.Symbols;
import souther.compiler.check.TypeView;
import souther.compiler.inputs.Case;
import souther.compiler.inputs.Distinctions;
import souther.compiler.inputs.Refinement;
import souther.compiler.inputs.StructuralInspection;
import souther.compiler.inputs.TermPath;
import souther.compiler.observe.FieldTypes;
import souther.compiler.query.Bodies;
import souther.compiler.query.Compilation;
import souther.compiler.query.Scopes;
import souther.compiler.query.Shapes;
import souther.compiler.types.Type;
import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbol;
import souther.compiler.types.TypeSymbols;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Four questions are asked of one shape, and they are four.
 *
 * <p>What is readable off a value standing here, what a value here is composed out of, which
 * positions a reading has under it, and what a written value has under a step. At a record all four
 * come to the same fields, which is close enough to take one of them for all four; at a sum whose
 * cases share a spread only the first has anything to say, because those names are readable at every
 * value of the sum and a value there is one of the cases.
 *
 * <p><b>The agreement at a record is what is checked, not what is implemented.</b> Each of the four
 * reads {@code Shape.Product}'s fields where it needs them, and the four answers are held together
 * here rather than by one of them being the others' answer. Written the other way round, a question
 * that learned a new case would take the three it does not own along with it.
 *
 * <p><b>Four and not five.</b> How an observed value is read down a path is the relation the written
 * one is most often mistaken for, and it is not one of these: it consumes a value, answers with as
 * many standings as it finds, and can find none, so there is no equality to hold between it and a
 * map of fields. What it owes is stated where it lives
 * ({@link AnObservedValueIsReadByWhatIsReadableAndNotByWhatItCarriesTest}) — that a field is
 * admitted by what is readable at the position and never by what the value in hand carries.
 */
class WhatIsReadableAndWhatIsBuiltAgreeAtARecordAndPartAtASumTest {

    private static final String SPREAD = """
            module example.four

            data Base = { deadline: Int }
            data P = { ...Base, x: Int }
            data T = { ...Base, y: Int }
            data Req = P | T

            data Ok

            behavior check : (r: Req, p: P) -> Ok

            let check (r, p) = Ok
            """;

    /**
     * At a record the four answers are one map.
     *
     * <p>Field for field and in the order the declaration writes them: a reader taking any of the
     * four gets what a value there has, whichever of the questions it meant.
     */
    @Test
    void atARecordTheFourAnswersAreOneMap() {
        Type record = typeOf("p");
        Map<String, Type> readable = ReadableFields.of(shapeOf(record)).declaredFields();

        assertEquals(Map.of("deadline", Type.INT, "x", Type.INT), readable,
                "the model under test declares a record of two fields");
        assertEquals(inOrder(readable), inOrder(ConstructionDescent.toBuild(shapeOf(record))
                        .fields()),
                "a record is composed out of what is readable on it");
        assertEquals(inOrder(readable), inOrder(decomposedUnder(record)),
                "and the reading has those same positions under it");
        assertEquals(inOrder(readable), inOrder(stepped(record, readable)),
                "and a written value has one of them under each step");
    }

    /**
     * At a sum whose cases share a spread only what is readable has anything.
     *
     * <p>The other three answer nothing, and each of them says it in the way its own reader has to
     * hear: no composition, a position that stands rather than one given up for what is under it,
     * and no place a written value put anything.
     */
    @Test
    void atASumSharingASpreadOnlyWhatIsReadableHasFields() {
        Type sum = typeOf("r");
        Map<String, Type> readable = ReadableFields.of(shapeOf(sum)).declaredFields();

        assertEquals(Map.of("deadline", Type.INT), readable,
                "the name every case spreads is readable at every value of the sum");
        assertNull(ConstructionDescent.toBuild(shapeOf(sum)),
                "and a value there is one of the cases, so nothing is composed out of it");
        assertInstanceOf(StructuralInspection.Retained.class, inspected(sum),
                "the sum stands as a position rather than being given up for its shared names");
        assertNull(BehaviorInputs.stepWrittenValue(new TermPath.Step.Field("deadline"), sum,
                        wraps(), symbols(), said()),
                "and a row writes one of the cases, so nothing is written at the shared name");
    }

    /**
     * Where the steps that are not a field land for a value being written, said apart from the walk.
     *
     * <p>An element of a sequence and a narrowing to a case are one answer under this relation and
     * under the reading of a row, and the two walks work them out separately so that they may one
     * day stop being one. What holds them together is that each is pinned — here for the written
     * relation, and in {@code AnObservedValueIsReadByWhatIsReadableAndNotByWhatItCarriesTest} for
     * the reading — rather than one being compared against the other, which is one answer wearing
     * two names.
     */
    @Test
    void whereTheStepsBesideAFieldLandForAWrittenValue() {
        assertEquals(Type.INT, BehaviorInputs.stepWrittenValue(new TermPath.Step.Element(),
                        new Type.ListOf(Type.INT), wraps(), symbols(), said()),
                "a sequence puts what it holds under an element");
        assertEquals(typeOf("p"), BehaviorInputs.stepWrittenValue(
                        new TermPath.Step.Refine(caseOf("P")), typeOf("r"), wraps(), symbols(),
                        said()),
                "and a narrowing to a case is that case at the same position");
        assertNull(BehaviorInputs.stepWrittenValue(new TermPath.Step.Element(), typeOf("r"),
                        wraps(), symbols(), said()),
                "and a sum holds nothing under an element");
    }

    /** The narrowing to {@code leaf}, taken from what the sum's type divides into. */
    private static Refinement caseOf(String leaf) {
        TypeSymbol wanted = TypeSymbols.declared(new TypeKey(module(), leaf));
        for (Case one : Distinctions.ofType(TypeView.asWritten(typeOf("r"), symbols(), said()),
                symbols(), said())) {
            if (one instanceof Case.SumCase found && found.leaf().equals(wanted)) {
                return Refinement.of(one);
            }
        }
        throw new AssertionError("the model under test declares `" + leaf + "` as a case");
    }

    /**
     * The two ways of asking what is readable give the one answer.
     *
     * <p>Every name at a position and one name of it, which a reader after a single step asks
     * because it has one name and no use for the rest. Two entries into a question is two chances
     * to answer it differently, so what the narrow one says is held to what the wide one holds —
     * including where it holds nothing, since a name nothing declares and a name declared to be
     * something are what a step turns on.
     */
    @Test
    void oneNameIsReadTheWayEveryNameIs() {
        for (Type type : List.of(typeOf("p"), typeOf("r"))) {
            Shape shape = shapeOf(type);
            Map<String, Type> readable = ReadableFields.of(shape).declaredFields();

            assertFalse(readable.isEmpty(),
                    () -> "the model under test has names readable at " + Type.show(type));
            for (Map.Entry<String, Type> name : readable.entrySet()) {
                assertEquals(name.getValue(), ReadableFields.at(shape, name.getKey()),
                        () -> "one name is what every name says it is, at " + name.getKey());
            }
            assertNull(ReadableFields.at(shape, "nothingDeclaresThis"),
                    () -> "and a name nothing declares is readable nowhere, at "
                            + Type.show(type));
        }
    }

    /**
     * And the same, of the two ways of asking it in a world.
     *
     * <p>What a name holds is the world's answer wherever the reader is reading, and that question
     * has the same two widths — a surface to list, and one name to look up. So it has the same two
     * chances to be answered differently, and the narrow one is held to the wide one here rather
     * than being trusted to fold the declarations in the same order.
     *
     * <p>Driven from the names this reading makes readable and not from what the world came back
     * with. Read off the wide answer, a world that had widened or narrowed the surface would be
     * compared against itself and the two widths would agree while both were wrong.
     */
    @Test
    void oneNameIsReadInAWorldTheWayEveryNameIs() {
        FieldTypes world = new ResolvedFieldTypes(symbols(), wraps());
        for (Type type : List.of(typeOf("p"), typeOf("r"))) {
            Shape shape = shapeOf(type);
            ReadableFields readable = ReadableFields.of(shape);

            assertFalse(readable.declaredFields().isEmpty(),
                    () -> "the model under test has names readable at " + Type.show(type));
            for (String name : readable.declaredFields().keySet()) {
                assertEquals(readable.in(world).get(name),
                        ReadableFields.at(shape, name, world),
                        () -> "one name is what every name says it is, at " + name);
            }
            assertNull(ReadableFields.at(shape, "nothingDeclaresThis", world),
                    () -> "and a name nothing declares is readable nowhere, at "
                            + Type.show(type));
        }
    }

    /**
     * A world says what a name holds and never which names there are.
     *
     * <p>The two halves of a reading are told apart by what each may decide, and a world that could
     * add or drop a name would be deciding the nominal half from the other side. So a world with a
     * name of its own does not make it readable, and one that says nothing about a name this reading
     * has leaves it unreadable rather than putting a hole in the surface at both widths.
     *
     * <p>Held of both widths, because the whole point of having two is that neither is the other's
     * implementation.
     */
    @Test
    void aWorldSaysWhatANameHoldsAndNotWhichNamesThereAre() {
        Shape shape = shapeOf(typeOf("r"));
        ReadableFields readable = ReadableFields.of(shape);
        assertTrue(readable.declaredFields().containsKey("deadline"),
                "the model under test makes one name readable at the sum");

        FieldTypes inventsOne = _ -> Map.of("nothingDeclaresThis", Type.STRING);
        assertEquals(Map.of(), readable.in(inventsOne),
                "a name only the world has is readable nowhere");
        assertNull(ReadableFields.at(shape, "nothingDeclaresThis", inventsOne),
                "and is not looked up either");

        FieldTypes saysNothing = _ -> Map.of();
        assertEquals(Map.of(), readable.in(saysNothing),
                "and a name the world says nothing about is not readable in it");
        assertNull(ReadableFields.at(shape, "deadline", saysNothing),
                "at either width");
    }

    /**
     * An answer as a list, so that the order it is in is compared too.
     *
     * <p>The order the declaration writes the fields in is part of each of these answers — what a
     * report names first and which field a row is composed for first are read off it — and two maps
     * holding the same entries are equal whichever order they hold them in.
     */
    private static List<Map.Entry<String, Type>> inOrder(Map<String, Type> answer) {
        return List.copyOf(answer.entrySet());
    }

    /** What the reading has under a position of {@code type}, which a record is given up for. */
    private static Map<String, Type> decomposedUnder(Type type) {
        return assertInstanceOf(StructuralInspection.Decomposed.class, inspected(type),
                "a record is a position made of positions").under();
    }

    private static StructuralInspection inspected(Type type) {
        TypeView view = TypeView.asWritten(type, symbols(), said());
        Shape.ReadablePositionShape shape = assertInstanceOf(
                Shape.ReadablePositionShape.class, view.shape(),
                "the model under test declares a shape a position can have");
        return StructuralInspection.of(shape, Distinctions.ofType(view, symbols(), said()));
    }

    /** Where a step into a written value lands, for each name in hand. */
    private static Map<String, Type> stepped(Type type, Map<String, Type> names) {
        Map<String, Type> out = new LinkedHashMap<>();
        for (String name : names.keySet()) {
            Type there =
                    BehaviorInputs.stepWrittenValue(new TermPath.Step.Field(name), type,
                            wraps(), symbols(), said());
            assertNotNull(there, () -> "a value written here puts one at " + name);
            out.put(name, there);
        }
        return out;
    }

    private static Shape shapeOf(Type type) {
        return TypeView.asWritten(type, symbols(), said()).shape();
    }

    /** What the behavior's parameter {@code named} is declared to be. */
    private static Type typeOf(String named) {
        Hir.SpecBehavior spec = spec();
        int at = -1;
        for (int i = 0; i < spec.params().size(); i++) {
            if (spec.params().get(i).name().equals(named)) {
                at = i;
            }
        }
        assertTrue(at >= 0, "the model under test takes a parameter called " + named);
        Map<String, Sig> sigs = COMPILATION.db().ask(new Bodies.Signatures(module())).value();
        return sigs.get(spec.name()).inputTypes().get(at);
    }

    private static final Compilation COMPILATION = compiled();

    private static Compilation compiled() {
        Compilation made = Compilation.ofSource(SPREAD, "Main");
        made.answerEverything();
        return made;
    }

    private static String module() {
        return COMPILATION.modules().get(0);
    }

    private static Symbols symbols() {
        return Scopes.derived(COMPILATION.db(), module()).value();
    }

    private static PublishedDeclarations said() {
        return Shapes.publishedDeclarations(COMPILATION.db());
    }

    /** What the declarations this reads wear one of, as the compilation answers it. */
    private static NewtypeInners wraps() {
        return Shapes.newtypeInners(COMPILATION.db());
    }

    private static Hir.SpecBehavior spec() {
        return (Hir.SpecBehavior) COMPILATION.db().ask(new Shapes.Prepared(module())).value()
                .behaviors().stream().filter(each -> each.name().equals("check"))
                .findFirst().orElseThrow();
    }
}
