package souther.compiler.check;

import souther.compiler.DefaultStdlib;
import souther.compiler.types.ConstructOccurrence;
import souther.compiler.types.BinOp;
import souther.compiler.ast.Hir;
import souther.compiler.core.Core;
import souther.compiler.diag.SourcePos;
import souther.compiler.types.BindingId;
import souther.compiler.types.BindingOwner;
import souther.compiler.types.CaseSelector;
import souther.compiler.types.ReachName;
import souther.compiler.types.ResolvedCase;
import souther.compiler.types.Type;
import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbol;
import souther.compiler.types.TypeSymbols;
import souther.compiler.types.ValueName;
import souther.compiler.core.Contract.Guard;
import souther.compiler.check.BehaviorContract.RuleId;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Opening a value says which case it is. It does not produce a second value.
 *
 * <p>An arm naming one case of a declared sum, or several of them, binds what it was given — the
 * case's class is tested and the value read is that instance — so the arm's name and the scrutinee
 * are one value. Entered as a place of its own, that value had two subjects: everything the answer
 * guaranteed was filed under one and everything the arm knew under the other, with nothing relating
 * them. They agreed only because no program could reach a fact under one and read it under the other
 * (#824).
 *
 * <p>An optional's present carrier is the other case: what it binds stands under the optional and is
 * not the optional, so it is a value of its own and is introduced as one. The two are told apart by
 * what the pattern binds and not by what the arm looks like.
 */
class AnArmSaysWhichCaseAValueIsAndDoesNotMakeASecondOneTest {

    private static final SourcePos POS = new SourcePos(1, 1);
    private static final BindingOwner OWNER = new BindingOwner.OfValue("demo", "f");
    private static final ValueName.Behavior FIND = new ValueName.Behavior("demo", "findIt");
    private static final TypeSymbol.AtModule FOUND = TypeSymbols.declared(new TypeKey("demo", "Found"));
    private static final TypeSymbol.AtModule MISSING = TypeSymbols.declared(new TypeKey("demo", "Missing"));

    private static final TypeSymbol AN_INT = TypeSymbol.primitive("Int");

    private final Hir.Binders binders = new Hir.Binders(OWNER);
    private final PathEngine engine =
            new PathEngine(Symbols.none(DefaultStdlib.get()),
                RuleReadings.noClauseFiled(), ClauseLocations.NONE, DeclarationReadings.NONE,
                Terms.Of.THE_DISCHARGE_TREE,
                souther.compiler.query.ReadAs.THE_COMPILATION_DOES);

    @Test
    void anArmOverOneCaseIsAboutTheValueItOpened() {
        Core answer = answer();
        Core.Binder x = CoreBinders.of(binders.binder("x", POS));

        PathEngine.Entered in = engine.enteringArm(
                arm(new Core.ResolvedPattern.Single(aLeaf(FOUND)), x),
                answer, Known.top(), Denotations.none());

        FactSubject opened = in.at().subject(x.binding());
        assertNotNull(opened, "an arm binds something to be known about");
        assertEquals(engine.terms().subjectOf(answer, Denotations.none()), opened,
                "the value the arm opened is the value it was given");
    }

    @Test
    void anArmOverSeveralCasesIsAboutTheValueItOpened() {
        Core answer = answer();
        Core.Binder x = CoreBinders.of(binders.binder("x", POS));

        PathEngine.Entered in = engine.enteringArm(
                arm(new Core.ResolvedPattern.AnyOf(
                        List.of(aLeaf(FOUND), aLeaf(MISSING)),
                        Type.ref(FOUND)), x),
                answer, Known.top(), Denotations.none());

        assertEquals(engine.terms().subjectOf(answer, Denotations.none()), in.at().subject(x.binding()),
                "an arm naming several says the value is one of them, and it is still that value");
    }

    /** Two matches over one answer open one value, so a fact taken under either is about the same
     * subject. That is what a second subject per arm cost. */
    @Test
    void twoArmsOverOneAnswerOpenOneValue() {
        Core answer = answer();
        Core.Binder first = CoreBinders.of(binders.binder("a", POS));
        Core.Binder second = CoreBinders.of(binders.binder("b", POS));

        FactSubject one = engine.enteringArm(
                arm(new Core.ResolvedPattern.Single(aLeaf(FOUND)), first),
                answer, Known.top(), Denotations.none()).at().subject(first.binding());
        FactSubject other = engine.enteringArm(
                arm(new Core.ResolvedPattern.Single(aLeaf(FOUND)), second),
                answer, Known.top(), Denotations.none()).at().subject(second.binding());

        assertEquals(one, other);
    }

    /** What stands under a present optional is not the optional, and it is what that optional
     * holds — not a value the arm made up. */
    @Test
    void whatAnOptionalHoldsIsWhatThatOptionalHolds() {
        Core answer = optionalAnswer();
        Core.Binder x = CoreBinders.of(binders.binder("x", POS));

        PathEngine.Entered in = engine.enteringArm(
                arm(new Core.ResolvedPattern.Single(aCarrier(CaseSelector.optionPresent(Type.INT), TypeSymbol.SOME)), x),
                answer, Known.top(), Denotations.none());

        FactSubject optional = engine.terms().subjectOf(answer, Denotations.none());
        FactSubject held = in.at().subject(x.binding());
        assertNotNull(held, "what it binds is something to be known about");
        assertNotEquals(optional, held, "an optional is not what it holds");
        assertEquals(engine.terms().heldBy(optional), held);
    }

    /** Two arms opening one optional open one value, which is the same rule {@code Direct} obeys one
     * level up. Left alone, an optional would have kept the defect its own answer was given. */
    @Test
    void twoArmsOverOneOptionalOpenOneValue() {
        Core answer = optionalAnswer();
        Core.Binder first = CoreBinders.of(binders.binder("a", POS));
        Core.Binder second = CoreBinders.of(binders.binder("b", POS));

        FactSubject one = engine.enteringArm(
                arm(new Core.ResolvedPattern.Single(aCarrier(CaseSelector.optionPresent(Type.INT), TypeSymbol.SOME)), first),
                answer, Known.top(), Denotations.none()).at().subject(first.binding());
        FactSubject other = engine.enteringArm(
                arm(new Core.ResolvedPattern.Single(aCarrier(CaseSelector.optionPresent(Type.INT), TypeSymbol.SOME)), second),
                answer, Known.top(), Denotations.none()).at().subject(second.binding());

        assertEquals(one, other);
    }

    /** An optional written out holds what it was written with, so opening it names that value and
     * not a term standing over it. A constructor and what undoes it are one fact. */
    @Test
    void openingAWrittenOptionalNamesWhatItWasWrittenWith() {
        Core three = new Core.Int(3, Type.INT, POS);
        Core written = new Core.OptionSome(three, Type.option(Type.INT), POS);
        Core.Binder x = CoreBinders.of(binders.binder("x", POS));

        PathEngine.Entered in = engine.enteringArm(
                arm(new Core.ResolvedPattern.Single(aCarrier(CaseSelector.optionPresent(Type.INT), TypeSymbol.SOME)), x),
                written, Known.top(), Denotations.none());

        assertEquals(engine.terms().subjectOf(three, Denotations.none()),
                in.at().subject(x.binding()));
    }

    /** The arm's name stands for the value it opened, and not for nothing. A reader following what a
     * name was given has to reach whatever produced it. */
    @Test
    void anArmsNameStandsForTheValueItOpened() {
        Core answer = answer();
        Core.Binder x = CoreBinders.of(binders.binder("x", POS));

        PathEngine.Entered in = engine.enteringArm(
                arm(new Core.ResolvedPattern.Single(aLeaf(FOUND)), x),
                answer, Known.top(), Denotations.none());

        assertEquals(answer, in.at().valueOf(x.binding()),
                "what the arm opened is what its name was given");
    }

    /**
     * A {@code match} over what an outer arm bound finds the call underneath it.
     *
     * <p>The whole of why the name has to stand for the value and not only be about it: an arm
     * naming several cases says only that the answer is one of them, so what a rule states of one
     * case is taken in at the arm that names that case — which is a second {@code match}, over a
     * name, and the rule is found by following what that name was given.
     */
    @Test
    void aRuleAboutACaseReachesAMatchOverWhatAnOuterArmBound() {
        Core answer = numericAnswer();
        Core.Binder x = CoreBinders.of(binders.binder("x", POS));
        Core.Binder y = CoreBinders.of(binders.binder("y", POS));
        PathEngine reading = new PathEngine(Symbols.none(DefaultStdlib.get()),
                RuleReadings.noClauseFiled(), ClauseLocations.NONE, DeclarationReadings.NONE,
                Map.of(FIND, statesThatTheIntIsPositive()), Terms.Of.THE_DISCHARGE_TREE,
                souther.compiler.query.ReadAs.THE_COMPILATION_DOES);

        Denotations outer = reading.enteringArm(
                arm(new Core.ResolvedPattern.AnyOf(
                        List.of(aLeaf(AN_INT), aLeaf(MISSING)),
                        answer.type()), x),
                answer, Known.top(), Denotations.none()).at();
        Core.Case inner = arm(new Core.ResolvedPattern.Single(aLeaf(AN_INT)), y);
        PathEngine.Entered in = reading.enteringArm(inner,
                new Core.Read("x", x.binding(), answer.type(), POS), Known.top(), outer);

        FactSubject opened = in.at().subject(y.binding());
        assertTrue(in.known().speaksOf(opened),
                "the rule the behavior stated about its Int case was taken in here");
    }

    /** {@code ensures | Int v -> v > 0}, as a caller of the behavior has it — read from the
     *  declaration's own holding of it, which is what answers one anywhere else. */
    private static AssumedContract statesThatTheIntIsPositive() {
        BindingId value = new Hir.Binders(new BindingOwner.OfValue("demo", "findIt"))
                .binder("v", POS).binding();
        Core states = new Core.Binary(BinOp.GT,
                new Core.Read("v", value, Type.INT, POS), new Core.Int(0, Type.INT, POS),
                ConstructOccurrence.unwritten(), Type.BOOL, POS);
        RuleRef.Ensures ref = new RuleRef.Ensures(new RuleId(FIND, 0, 0, AN_INT), AN_INT.name());
        return new StatedContract(FIND, List.of(), Type.INT,
                List.of(new StatedContract.StatedRule(
                        new Guard.Case(CaseSpace.resolve(CaseSelector.direct(AN_INT),
                                Symbols.none(DefaultStdlib.get()))), value,
                        Optional.empty(),
                        List.of(new StatedContract.Conjunct(new PartId<>(ref, 0), POS,
                                new souther.compiler.check.TypedClause.Typed(states))))))
                .assumptions();
    }

    private Core.Case arm(Core.ResolvedPattern pattern, Core.Binder binder) {
        return new Core.Case(pattern, binder, new Core.Read(binder.name(), binder.binding(),
                pattern.bindType(), POS), POS);
    }

    private static Core answer() {
        return new Core.Call(new Core.Reached.OfDeclaration(
                new ReachName.Own(FIND)), List.of(),
                Type.ref(FOUND), POS);
    }

    /** The same call, answering an optional — what an arm naming a present carrier is written over. */
    private static Core optionalAnswer() {
        return new Core.Call(new Core.Reached.OfDeclaration(
                new ReachName.Own(FIND)), List.of(),
                Type.option(Type.INT), POS);
    }

    /** A call answering {@code Int | Missing}, which an arm may name either case of. */
    private static Core numericAnswer() {
        return new Core.Call(new Core.Reached.OfDeclaration(
                new ReachName.Own(FIND)), List.of(),
                Type.union(new java.util.LinkedHashSet<>(List.of(AN_INT, MISSING))), POS);
    }

    // --- which rules an arm takes in, when a case has cases of its own ---------------------------

    /** A sum two deep, so an arm and a rule can answer for different amounts of one subject. */
    private static final String NESTED = """
            module demo

            data Station  = { at: String }
            data Hospital = { at: String }
            data Renkei   = { at: String }
            data OnceKind  = Station | Hospital
            data VisitKind = OnceKind | Renkei
            """;

    /**
     * A rule holds of an arm when every value that could have taken the arm is one the rule is
     * about, and not otherwise.
     *
     * <p>The inclusion has a direction and both ways round type-check. An arm naming a case under
     * the rule's case answers for fewer values than the rule, so the rule holds of all of them; the
     * other way round the arm has values the rule says nothing about, and carrying the rule in would
     * be assuming of a hospital what was only stated of a station.
     *
     * <p>Asked of the operation directly. Reversing the inclusion answers the same on every program
     * where an arm and a rule name one case — which is every program written before #966 — so a
     * test that went through a compile would agree with the reversed reading and say nothing.
     */
    @Test
    void aRuleHoldsOfAnArmWhoseValuesAreAllOnesItIsAbout() {
        Symbols symbols = symbolsOf(NESTED);
        PathEngine reading = new PathEngine(symbols, RuleReadings.noClauseFiled(),
                ClauseLocations.NONE, DeclarationReadings.NONE, Terms.Of.THE_DISCHARGE_TREE,
                souther.compiler.query.ReadAs.THE_COMPILATION_DOES);
        TypeSymbol once = named(symbols, "OnceKind");
        TypeSymbol station = named(symbols, "Station");
        Guard aboutOnceKind = new Guard.Case(
                CaseSpace.resolve(CaseSelector.direct(once), symbols));
        Guard aboutStation = new Guard.Case(
                CaseSpace.resolve(CaseSelector.direct(station), symbols));

        assertTrue(reading.impliedBy(aboutOnceKind, single(station)),
                "a station is one of the values the rule about OnceKind is stated of");
        assertFalse(reading.impliedBy(aboutStation, single(once)),
                "an arm over OnceKind takes hospitals too, which the rule says nothing of");
    }

    /** And an arm naming several answers for their union, which is what the rule is held against. */
    @Test
    void anArmNamingSeveralTakesARuleThatIsAboutAllOfThem() {
        Symbols symbols = symbolsOf(NESTED);
        PathEngine reading = new PathEngine(symbols, RuleReadings.noClauseFiled(),
                ClauseLocations.NONE, DeclarationReadings.NONE, Terms.Of.THE_DISCHARGE_TREE,
                souther.compiler.query.ReadAs.THE_COMPILATION_DOES);
        TypeSymbol once = named(symbols, "OnceKind");
        TypeSymbol station = named(symbols, "Station");
        TypeSymbol hospital = named(symbols, "Hospital");
        Type visitKind = Type.ref(named(symbols, "VisitKind"));
        // Resolved the way the checker resolves an arm, so what the alternatives cover is this
        // compile's answer rather than one written here.
        Core.ResolvedPattern both = new Core.ResolvedPattern.AnyOf(
                List.of(CaseSpace.resolve(CaseSelector.direct(station), symbols),
                        CaseSpace.resolve(CaseSelector.direct(hospital), symbols)), visitKind);

        assertTrue(reading.impliedBy(
                        new Guard.Case(CaseSpace.resolve(CaseSelector.direct(once), symbols)), both),
                "both alternatives are values the rule about OnceKind is stated of");
        assertFalse(reading.impliedBy(
                        new Guard.Case(CaseSpace.resolve(CaseSelector.direct(station), symbols)), both),
                "one of the alternatives is a value the rule says nothing of");
    }

    private static Core.ResolvedPattern single(TypeSymbol name) {
        return new Core.ResolvedPattern.Single(aLeaf(name));
    }

    /** A leaf as this compile would resolve it: a case that covers itself. */
    private static ResolvedCase aLeaf(TypeSymbol leaf) {
        return ResolvedCase.of(CaseSelector.direct(leaf), List.of(leaf));
    }

    /** One of an optional's carriers, which covers itself and no leaf of anything. */
    private static ResolvedCase aCarrier(CaseSelector carrier, TypeSymbol covers) {
        return ResolvedCase.of(carrier, List.of(covers));
    }

    private static TypeSymbol named(Symbols symbols, String type) {
        for (String declared : symbols.declaredNamesIn("demo")) {
            Hir.Def d = symbols.declaredNode(new TypeKey("demo", declared));
            if (d.name().equals(type)) {
                return d.declares();
            }
        }
        throw new AssertionError("the module does not declare " + type);
    }

    private static Symbols symbolsOf(String source) {
        java.util.Map<String, String> byId = new java.util.LinkedHashMap<>();
        byId.put("m.sou", source);
        return TypeChecker.symbols(
                souther.compiler.query.Compilation.ofDocuments(byId, java.util.Set.of(),
                                souther.compiler.meta.ModulePath.EMPTY)
                        .db().ask(new souther.compiler.query.Names.Resolved("demo")).value(), DefaultStdlib.get());
    }

}
