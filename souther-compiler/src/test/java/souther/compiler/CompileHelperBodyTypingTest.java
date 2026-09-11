package souther.compiler;

import souther.compiler.source.SourceId;

import souther.compiler.diag.msg.MessageKeys;
import souther.compiler.diag.msg.HelperMessage;
import souther.compiler.diag.msg.BehaviorMessage;
import souther.compiler.diag.msg.ArithmeticMessage;
import souther.compiler.diag.Located;
import souther.compiler.diag.CompileException;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A helper's parameter takes its type from the helper's own body, never from its callers (spec §fn-declaration,
 * issue #176). The body determines it where it reaches a position that names a type — an operator
 * against a value of known type, an argument of a call whose parameter is declared, a field of a
 * construction, an {@code if} condition. Where it does not, the parameter is annotated; the report
 * names the use that left it open.
 */
class CompileHelperBodyTypingTest {

    /** Compiles a module whose only helper is {@code helper}, uncalled — so only its body can type it. */
    private static boolean bodyTypes(String helper) {
        String src = """
                module demo
                data X = Int
                behavior f : (x: X) -> X
                %s
                let f (x) = x
                """.formatted(helper);
        return Compiler.compile(src).containsKey("demo.F");
    }

    /** Applies {@code demo.F} to {@code in} decoded as {@code dataType}, and encodes the result back. */
    private long applyToInt(String src, String dataType, long in) throws Exception {
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile(src), getClass().getClassLoader());
        Object behavior = Emitted.behavior(loader, "demo", "f").getConstructor().newInstance();
        Object out = Codecs.apply(behavior, Codecs.decoded(loader, dataType, in));
        return (long) Codecs.encode(loader, dataType, out);
    }

    @Test
    void arithmeticInTheBodyTypesTheParameterWithNoCallSiteAtAll() {
        // `x * 2` fixes `x` to Int on its own. Nothing calls `double`, and nothing needs to.
        assertTrue(bodyTypes("let double (x) = x * 2"),
                "the body types `x`, so an uncalled helper compiles");
    }

    @Test
    void anIfConditionTypesTheParameter() {
        assertTrue(bodyTypes("let pick (b) = if b then 1 else 0"), "the `if` condition types `b` as Bool");
    }

    @Test
    void aLogicalOperatorTypesTheParameter() {
        assertTrue(bodyTypes("let both (b) = b && true"), "`&&` types `b` as Bool");
    }

    @Test
    void anIntrinsicsDeclaredParameterTypesIt() {
        // `String.trim` is a shipped primitive rather than a self-hosted helper, so it is not inlined
        // away; its declared parameter is what types `s`.
        assertTrue(bodyTypes("let clean (s) = String.trim(s)"), "`String.trim` types `s` as String");
    }

    @Test
    void anAnnotatedCalleeTypesTheArgumentEvenWhereItsOwnBodyDoesNot() {
        // `s` is passed where `describe` declares `note: String`. That declared type is what types
        // `s` — the callee's body never puts `note` in a position that names a type.
        assertTrue(bodyTypes("""
                let describe (n: Int, note: String) = if n > 0 then note else "none"
                let go (s) = describe(1, s)"""),
                "the callee's declared parameter types the argument");
    }

    @Test
    void aBindingOfTheSameNameShadowsTheParameter() {
        // The inner `n` is a String; it is a different `n`, so it says nothing about the parameter.
        // The parameter is typed by `n * 2`, which is the `n` the helper declares.
        assertTrue(bodyTypes("""
                let g (n) = {
                    let y = {
                        let n = "  s  "
                        String.length(String.trim(n))
                    }
                    n * 2 + y
                }"""),
                "a shadowing binding does not type the parameter");
    }

    @Test
    void aShadowingLambdaApplicationDoesNotMakeTheParameterAFunction() {
        // The applied `h` is the inner lambda binding, not the parameter, so the parameter is not
        // read as function-typed; `h * 2` types it as Int.
        assertTrue(bodyTypes("""
                let g (h) = {
                    let y = {
                        let h = (s) -> String.length(String.trim(s))
                        h("ab")
                    }
                    h * 2 + y
                }"""),
                "an application of a shadowing binding is not an application of the parameter");
    }

    @Test
    void aBodyTypedParameterStillInlinesAtItsCallSite() throws Exception {
        String src = """
                module demo
                data X = Int
                behavior f : (x: X) -> X constructs X
                let double (x) = x * 2
                let f (x) = X(double(x.value))
                """;
        assertEquals(6L, applyToInt(src, "demo.X", 3L));
    }

    @Test
    void anAnnotatedHelperItCallsTypesTheParameter() throws Exception {
        // `x` is passed to `twice`, whose parameter is declared Int, so `x` is Int.
        String src = """
                module demo
                data X = Int
                behavior f : (x: X) -> X constructs X
                let twice (n: Int) = n * 2
                let quad (x) = twice(twice(x))
                let f (x) = X(quad(x.value))
                """;
        assertEquals(12L, applyToInt(src, "demo.X", 3L));
    }

    @Test
    void aRecursiveHelpersDeclaredParameterTypesIt() throws Exception {
        // A recursive helper writes its parameter types, so a call to one types the argument passed.
        String src = """
                module demo
                data N = Int
                behavior f : (n: N) -> N constructs N
                partial let depth (n: Int): Int = if n == 0 then 0 else depth(n - 1) + 1
                partial let start (m) = depth(m)
                let f (n) = N(start(n.value))
                """;
        assertEquals(3L, applyToInt(src, "demo.N", 3L));
    }

    @Test
    void aNewtypeConstructorTypesTheParameter() throws Exception {
        // `Money(v)` is a construction of the newtype's `value` field, so `v` takes the type it wraps.
        String src = """
                module demo
                data Money = Int
                behavior f : (m: Money) -> Money constructs Money
                let wrap (v) = Money(v)
                let f (m) = wrap(m.value * 2)
                """;
        assertEquals(6L, applyToInt(src, "demo.Money", 3L));
    }

    @Test
    void aConstructionFieldTypesTheParameter() throws Exception {
        // `n` is the value of `amount`, whose field type is Int.
        String src = """
                module demo
                data Money = { amount: Int }
                behavior f : (m: Money) -> Money constructs Money
                let wrap (n) = Money { amount = n }
                let f (m) = wrap(m.amount * 2)
                """;
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile(src), getClass().getClassLoader());
        Object m = Codecs.decoded(loader, "demo.Money", java.util.Map.of("amount", 4L));

        Object behavior = Emitted.behavior(loader, "demo", "f").getConstructor().newInstance();

        java.util.Map<?, ?> out = (java.util.Map<?, ?>) Codecs.encode(
                loader, "demo.Money", Codecs.apply(behavior, m));
        assertEquals(8L, out.get("amount"));
    }

    @Test
    void anInjectedBehaviorsSignatureTypesTheParameter() {
        // The helper calls an injected behavior, whose declared input types the argument it is given.
        String src = """
                module demo
                data CustomerId = String
                data Customer = { name: String }
                data Out = { name: String }
                behavior findCustomer : (id: CustomerId) -> Customer
                behavior show : (id: CustomerId) -> Out depends on findCustomer constructs Out
                let nameOf (id) = findCustomer(id).name
                let show (id, findCustomer) = Out { name = nameOf(id) }
                """;
        assertTrue(Compiler.compile(src).containsKey("demo.Show"),
                "`findCustomer`'s declared input types `id` as CustomerId");
    }

    @Test
    void aClosureIsReadAgainstTheResultItsSignatureDeclares() {
        // `List.find`'s step answers a Bool, so the closure's `v` is a Bool, so `xs` is a
        // List<Bool>. Nothing else in the call says what the list holds.
        assertTrue(bodyTypes("let firstTrue (xs) = List.find((v) -> v, xs)"),
                "the step's declared result types the element the closure walks");
    }

    @Test
    void anArgumentBesideAClosureIsSolvedBeforeTheClosureIsRead() {
        // The seed says what the fold accumulates, and the step is read knowing it: `acc` is an Int
        // because `0` is, and `x` is one because `acc + x` puts it beside `acc`. Reading the step
        // first would leave both open and ask for an annotation on `xs`.
        assertTrue(bodyTypes("let total (xs) = List.fold((acc, x) -> acc + x, 0, xs)"),
                "the seed binds the accumulator before the step is read");
    }

    @Test
    void anArmThatAnswersNoValueDeterminesNothing() {
        // `unreachable` answers no value, so the arm beside it says nothing about what `v` is —
        // an arm determines its sibling only where it answers one (ADR-0066). Taking `Never` for
        // an answer would write it onto the parameter and let every call through.
        String src = """
                module demo
                data X = Int
                behavior f : (x: X) -> X constructs X
                let choose (b: Bool, v) = if b then v else unreachable "impossible"
                let f (x) = X(choose(true, x.value))
                """;
        CompileException e = assertThrows(CompileException.class, () -> Compiler.compile(src));
        assertInstanceOf(HelperMessage.AParameterIsNotDeterminedByTheBody.class, e.diagnostic().said(), e.getMessage());
        assertTrue(e.getMessage().contains("choose") && e.getMessage().contains("v"), e.getMessage());
    }

    @Test
    void aClosureParameterSpelledLikeTheHelpersDoesNotTypeIt() {
        // The `v` the lambda binds is another binding that happens to be spelled the same. What
        // types it — `v * 2` — says nothing about the helper's own `v`, which nothing determines.
        String src = """
                module demo
                data X = Int
                behavior f : (x: X) -> X constructs X
                let ignored (v, xs: List<Int>) = List.map((v) -> v * 2, xs)
                let f (x) = X(List.length(ignored(x, [ 1, 2 ])))
                """;
        CompileException e = assertThrows(CompileException.class, () -> Compiler.compile(src));
        assertInstanceOf(HelperMessage.AParameterIsNotDeterminedByTheBody.class, e.diagnostic().said(), e.getMessage());
        assertTrue(e.getMessage().contains("ignored") && e.getMessage().contains("v"), e.getMessage());
    }

    @Test
    void aCallSiteNoLongerTypesAParameter() {
        // `id`'s body says nothing about `v`. That the only call passes an Int is not consulted:
        // a helper is typed by its body, not by its callers.
        String src = """
                module demo
                data X = Int
                behavior f : (x: X) -> X constructs X
                let id (v) = v
                let f (x) = X(id(x.value))
                """;
        CompileException e = assertThrows(CompileException.class, () -> Compiler.compile(src));
        assertInstanceOf(HelperMessage.AParameterIsNotDeterminedByTheBody.class, e.diagnostic().said(), e.getMessage());
        assertTrue(e.getMessage().contains("id") && e.getMessage().contains("v"), e.getMessage());
    }

    @Test
    void aFieldAccessDoesNotTypeTheParameterAndTheReportNamesTheUse() {
        // `line.qty` reads a field off `line` without naming a type — no type is determined by it
        // (F# reports the same shape). The report points at the use that left the parameter open,
        // and says it was a field: reaching a type from one is a question a nominal model does not
        // ask, so no amount of writing more body will settle it.
        String src = """
                module demo
                data Line = { qty: Int, price: Int }
                data X = Int
                behavior f : (l: Line) -> X constructs X
                let total (line) = line.qty * line.price
                let f (l) = X(total(l))
                """;
        CompileException e = assertThrows(CompileException.class, () -> Compiler.compile(src));
        assertInstanceOf(HelperMessage.AParameterIsOnlyReadThroughAField.class, e.diagnostic().said(), e.getMessage());
        assertFalse(e.diagnostic().secondary().isEmpty(), "the open use is labelled");
        assertInstanceOf(HelperMessage.AFieldIsReadOffItAndThatNamesNoType.class,
                e.diagnostic().secondary().get(0).said());
        assertEquals(5, WhereItSits.in(src, ((souther.compiler.diag.DiagnosticPlace.InSource) e.diagnostic().secondary().get(0).place()).region()).start().line(),
                "the label sits on `line.qty`, the use that names no type");
    }

    @Test
    void aFunctionParameterIsStillAnnotated() {
        // `g` is applied in the body, so it is a function; a function type is not determined by the
        // application, and the inliner needs the annotation to expand the call.
        String src = """
                module demo
                data X = Int
                behavior f : (x: X) -> X constructs X
                let apply (g, v: Int) = g(v)
                let f (x) = X(apply((n) -> n * 2, x.value))
                """;
        CompileException e = assertThrows(CompileException.class, () -> Compiler.compile(src));
        assertInstanceOf(HelperMessage.AFunctionTypedParameterNeedsItsType.class, e.diagnostic().said(), e.getMessage());
    }

    @Test
    void aParameterHandedToACombinatorIsReportedAsTheFunctionItIs() {
        // `g` is not applied where it is written — `List.map` applies it, after expansion. It is a
        // function all the same, and the report says so rather than that nothing determined it.
        String src = """
                module demo
                data X = Int
                behavior f : (x: X) -> X
                let applyAll (g) = List.map(g, [1, 2])
                let f (x) = x
                """;
        CompileException e = assertThrows(CompileException.class, () -> Compiler.compile(src));
        assertInstanceOf(HelperMessage.AFunctionTypedParameterNeedsItsType.class, e.diagnostic().said(), e.getMessage());
    }

    @Test
    void theOtherArmOfAnIfTypesTheParameter() {
        // `v` is one arm of the `if`; the other answers an Int, and the two arms have one type.
        assertTrue(bodyTypes("let pick (b: Bool, v) = if b then v else 0"),
                "the other arm of the `if` types `v` as Int");
    }

    @Test
    void aSiblingArmOfAMatchTypesTheParameter() {
        // `v` is the `None` arm; the `Some` arm answers the Int the optional carries.
        assertTrue(bodyTypes("""
                let orElse (v, opt: Option<Int>) = match opt with
                    | Some got -> got
                    | None -> v"""),
                "the sibling arm types `v` as Int");
    }

    @Test
    void aSiblingElementOfACollectionLiteralTypesTheParameter() {
        // A list literal's elements have one type, and the sibling names it.
        assertTrue(bodyTypes("let pair (v) = List.length([v, 1])"),
                "the sibling element types `v` as Int");
    }

    @Test
    void theDeclaredReturnTypeTypesTheParameterItAnswers() {
        // The body answers the parameter itself, and the return type beside it says what that is.
        assertTrue(bodyTypes("let same (v): Int = v"),
                "the declared return type types `v` as Int");
    }

    @Test
    void aCaseBindingOfTheSameNameShadowsTheParameter() {
        // The `v` in the `Some` arm is the arm's own binding, so `v * 2` says nothing about the
        // parameter — the same rule a `let` of the same name follows.
        String src = """
                module demo
                data X = Int
                behavior f : (x: X) -> X
                let g (v, opt: Option<Int>) = match opt with
                    | Some v -> v * 2
                    | None -> 0
                let f (x) = x
                """;
        CompileException e = assertThrows(CompileException.class, () -> Compiler.compile(src));
        assertInstanceOf(HelperMessage.AParameterIsNotDeterminedByTheBody.class, e.diagnostic().said(), e.getMessage());
    }

    @Test
    void aSiblingArgumentSettlesTheSignaturesTypeVariable() {
        // `contains` is declared `(value: 'a, s: Set<'a>) -> Bool`. The set says what `'a` is, and
        // `'a` is what the first argument's type is.
        assertTrue(bodyTypes("let has (v, s: Set<Int>) = Set.contains(v, s)"),
                "the set argument settles `'a`, which types `v` as Int");
    }

    @Test
    void aMapsValueTypeSettlesTheArgumentItIsInsertedAs() {
        assertTrue(bodyTypes("let put (v, m: Map<String, Int>) = Map.insert(\"k\", v, m)"),
                "the map argument settles the value type, which types `v` as Int");
    }

    @Test
    void theResultPositionSettlesTheSignaturesTypeVariable() {
        // `sum` is declared `(List<'a>) -> 'a`, and `+ 1` says the answer is an Int, so the list is
        // a List<Int>.
        assertTrue(bodyTypes("let total (xs) = List.sum(xs) + 1"),
                "the result position settles `'a`, which types `xs` as List<Int>");
    }

    @Test
    void aLambdaParameterTypesTheOperandBesideIt() {
        // The lambda walks a List<Int>, so its own parameter is an Int, and `x + k` types `k`.
        assertTrue(bodyTypes("let bumpAll (k, xs: List<Int>) = List.map((x) -> x + k, xs)"),
                "the lambda parameter types `k` as Int");
    }

    @Test
    void aFoldStepsParameterTypesTheOperandBesideIt() {
        assertTrue(bodyTypes("""
                let total (k, xs: List<Int>) = List.fold((acc, x) -> acc + x + k, 0, xs)"""),
                "the step's element parameter types `k` as Int");
    }

    @Test
    void aFieldOfALambdaParameterTypesWhatItIsComparedTo() {
        String src = """
                module demo
                data Line = { qty: Int }
                data X = Bool
                behavior f : (l: Line) -> X constructs X
                let anyOver (k, ls: List<Line>) = List.any((l) -> l.qty > k, ls)
                let f (l) = X(anyOver(3, [l]))
                """;
        assertTrue(Compiler.compile(src).containsKey("demo.F"),
                "the lambda parameter is a Line, so `l.qty > k` types `k` as Int");
    }

    @Test
    void aLambdaBodySettlesTheElementOfTheCollectionItWalks() {
        // Nothing but the lambda says what the list holds: `v * 2` types the closure's parameter, and
        // that is the element type the combinator's `List<'a>` asks for.
        assertTrue(bodyTypes("let doubled (xs) = List.map((v) -> v * 2, xs)"),
                "the lambda body settles `'a`, which types `xs` as List<Int>");
    }

    @Test
    void aHelperCallingABehaviorIsToldThatBeforeItIsAskedForAnAnnotation() {
        // A helper does not reach a behavior at all, so asking for the parameter's type first sends
        // the author to write an annotation that changes nothing.
        String src = """
                module demo
                data X = Int
                data Y = Int
                behavior g : (x: X) -> Y constructs Y
                let g (x) = Y(x.value)
                behavior f : (x: X) -> Y constructs Y
                let call (x) = g(x)
                let f (x) = call(x)
                """;
        CompileException e = assertThrows(CompileException.class, () -> Compiler.compile(src));
        // Which of the errors leads is where in the source it is, so what this test is about is that
        // the call is what the author is told about and the annotation is never asked for.
        assertTrue(e.diagnostics().stream()
                        .anyMatch(d -> d.said() instanceof BehaviorMessage.ABehaviorCannotBeCalledFromHere),
                e.getMessage());
        assertTrue(e.diagnostics().stream()
                        .noneMatch(d -> d.said() instanceof HelperMessage.AParameterNeedsItsType),
                e.getMessage());
    }

    @Test
    void aFunctionParametersDeclaredInputTypesItsArgument() {
        // `f` writes its type, so applying it says what the argument is — the same thing a declared
        // callee's parameter says anywhere else.
        assertTrue(bodyTypes("let apply (f: (Int) -> String, v) = f(v)"),
                "`f`'s declared input types `v` as Int");
    }

    @Test
    void aBindingHoldingAFunctionTypesWhatItIsAppliedTo() {
        // The binding writes the function type, and applying it asks its argument for the input —
        // a written type is a declaration wherever it stands.
        assertTrue(bodyTypes("""
                let show (v) = {
                    let render: (Int) -> String = (x) -> String.fromInt(x)
                    render(v)
                }"""),
                "`render`'s written input types `v` as Int");
    }

    @Test
    void aComprehensionGuardTypesTheParameter() {
        // A guard decides whether the one element is there, so it answers a Bool.
        assertTrue(bodyTypes("let guarded (b) = [1 | b]"),
                "the comprehension guard types `b` as Bool");
    }

    @Test
    void aDeclaredTupleTypeReachesTheElementItNames() {
        assertTrue(bodyTypes("let pair (v): (String, Int) = (\"key\", v)"),
                "the declared tuple type types `v` as Int");
    }

    @Test
    void anArmOfAnAttemptedConstructionTypesTheParameter() {
        // The arms of `if X(v) as n then … else …` answer one type, and the success arm reads the
        // value that was built.
        String src = """
                module demo
                data Amount = Int
                    invariant value >= 0
                data X = Int
                behavior f : (x: X) -> X
                let wrap (n, fallback) = if Amount(n) as a then a.value else fallback
                let f (x) = x
                """;
        assertTrue(Compiler.compile(src).containsKey("demo.F"),
                "the sibling arm types `fallback` as Int");
    }

    @Test
    void aLocalBindingIsNotTypedByADeclarationOfTheSameName() {
        // The applied `fetch` is the binding, not the injected behavior that shares its spelling.
        // Nothing in the body says what the binding holds, so the parameter is annotated.
        String src = """
                module demo
                data X = Int
                data Y = Int
                behavior fetch : (x: X) -> Y
                behavior work : (x: X) -> Y depends on fetch
                let work (x, fetch) = fetch(x)
                let choose (b: Bool, v) = {
                    let fetch = if b then (x) -> 1 else (x) -> 2
                    fetch(v)
                }
                """;
        CompileException e = assertThrows(CompileException.class, () -> Compiler.compile(src));
        assertInstanceOf(HelperMessage.AParameterIsNotDeterminedByTheBody.class, e.diagnostic().said(), e.getMessage());
    }

    @Test
    void anElementThatAnswersNoTypeDoesNotHideALaterOne() {
        // The elements share one type. `[]` answers none of it, so what says what the list holds is
        // `[1]`, further along.
        assertTrue(bodyTypes("let nested (v) = List.length([v, [], [1]])"),
                "the later element types `v` as List<Int>");
    }

    @Test
    void anArmThatAnswersNoValueDoesNotHideALaterOne() {
        // `unreachable` answers no value, so the arm beside it that does is what types the parameter.
        String src = """
                module demo
                data A
                data B
                data C
                data S = A | B | C
                data X = Int
                behavior f : (x: X) -> X
                let choose (v, s: S) = match s with
                    | A -> v
                    | B -> unreachable "not here"
                    | C -> 0
                let f (x) = x
                """;
        assertTrue(Compiler.compile(src).containsKey("demo.F"),
                "the third arm types `v` as Int");
    }

    /** Compiles a module declaring a numeric newtype whose only helper is {@code helper}. */
    private static boolean scales(String helper) {
        String src = """
                module demo
                data N = Int
                data X = Int
                behavior f : (x: X) -> X
                %s
                let f (x) = x
                """.formatted(helper);
        return Compiler.compile(src).containsKey("demo.F");
    }

    @Test
    void aNumericNewtypeAsksItsScalarForTheBaseType() {
        // `N * Int` stays in `N` and `N * N` is a dimension change the model does not have, so what
        // stands beside the newtype is the base it wraps — not the newtype.
        assertTrue(scales("let scale (factor, n: N) = n * factor"),
                "the scalar beside the newtype is an Int");
    }

    @Test
    void aScalarOnTheLeftOfAMultiplicationTakesTheBaseType() {
        assertTrue(scales("let scale (factor, n: N) = factor * n"),
                "a scalar multiplies from either side");
    }

    @Test
    void aDivisorOfANumericNewtypeTakesTheBaseType() {
        assertTrue(scales("let split (factor, n: N) = n / factor"),
                "dividing a newtype by a scalar stays in the newtype");
    }

    @Test
    void aScalarDividedByANewtypeDeterminesNothing() {
        // `s / N` is an inverse — a dimension change — so nothing the operator admits stands there and
        // the parameter is annotated rather than settled at a type the body would then refuse.
        String src = """
                module demo
                data N = Int
                data X = Int
                behavior f : (x: X) -> X
                let invert (factor, n: N) = factor / n
                let f (x) = x
                """;
        CompileException e = assertThrows(CompileException.class, () -> Compiler.compile(src));
        assertInstanceOf(HelperMessage.AParameterIsNotDeterminedByTheBody.class, e.diagnostic().said(), e.getMessage());
    }

    @Test
    void aRecursiveHelperStillAnnotatesItsParameters() {
        // A recursive helper is lowered to a method and typed on its declaration, so it writes its
        // parameter types even where a body use would determine them.
        String src = """
                module demo
                data N = Int
                behavior f : (n: N) -> N constructs N
                let count (n): Int = if n == 0 then 0 else count(n - 1) + 1
                let f (n) = N(count(n.value))
                """;
        CompileException e = assertThrows(CompileException.class, () -> Compiler.compile(src));
        assertInstanceOf(HelperMessage.AParameterNeedsItsType.class, e.diagnostic().said(), e.getMessage());
    }

    @Test
    void aLyingReturnTypeIsRejectedOnABodyTypedParameter() {
        // The declared return type is checked against the body on the completed environment, whether
        // the parameter was written or determined by the body.
        // `f` passes its input through, so its `constructs` is left off: declaring one it does not
        // build is an error of its own (E1006), and a fixture carrying two errors says nothing about
        // which of them this test is for.
        String src = """
                module demo
                data X = Int
                behavior f : (x: X) -> X
                let g (n): String = n * 2
                let f (x) = x
                """;
        CompileException e = assertThrows(CompileException.class, () -> Compiler.compile(src));
        assertInstanceOf(HelperMessage.TheBodyIsNotWhatTheHelperDeclares.class, e.diagnostic().said(), e.getMessage());
    }

    @Test
    void aBodyDeterminedTypeReachesTheExpansion() throws Exception {
        // `v` is the value of `W`'s `u` field, so the body determines it as the sum `U` — and that is
        // the type the binding the call expands to carries, rather than the case the caller passed.
        String src = """
                module demo
                data A = { x: Int }
                data B = { y: Int }
                data U = A | B
                data W = { u: U }
                data X = Int
                behavior f : (a: A) -> X constructs X, W
                let describe (v) = {
                    let w = W { u = v }
                    match v with
                        | A as a -> a.x
                        | B as b -> b.y
                }
                let f (a) = X(describe(a))
                """;
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile(src), getClass().getClassLoader());
        Object behavior = Emitted.behavior(loader, "demo", "f").getConstructor().newInstance();
        Object in = Codecs.decoded(loader, "demo.A", java.util.Map.of("x", 7L));
        assertEquals(7L, Codecs.encode(loader, "demo.X", Codecs.apply(behavior, in)));
    }

    @Test
    void aBodyDeterminedTypeTypesTheNextHelpersParameter() {
        // `hold`'s parameter is determined by its body, and `describe`'s only comes from passing it to
        // `hold` — so one helper's determined type has to be settled before the next one is read.
        String src = """
                module demo
                data A = { x: Int }
                data B = { y: Int }
                data U = A | B
                data W = { u: U }
                data X = Int
                behavior f : (a: A) -> X constructs X, W
                let hold (v) = W { u = v }
                let describe (w) = {
                    let kept = hold(w)
                    match w with
                        | A as a -> a.x
                        | B as b -> b.y
                }
                let f (a) = X(describe(a))
                """;
        assertTrue(Compiler.compile(src).containsKey("demo.F"),
                "`hold`'s determined parameter type types `describe`'s");
    }

    @Test
    void aBodyDeterminedTypeReachesAnInvariantsExpansionToo() {
        // An invariant is expanded from a helper as a body is, and earlier in the pipeline — an
        // importer reads an included data's invariant already expanded. `w` is determined as `U` by
        // the call to `size`, and that is what the binding in the invariant's expansion carries.
        String src = """
                module demo
                data A = { x: Int }
                data B = { y: Int }
                data U = A | B
                data X = Int
                let size (v: U): Int = match v with
                        | A as a -> a.x
                        | B as b -> b.y
                let describe (w) = {
                    let n = size(w)
                    match w with
                        | A as a -> a.x + n
                        | B as b -> b.y
                }
                data V = { a: A }
                    invariant describe(a) > 0
                behavior f : (v: V) -> X constructs X
                let f (v) = X(v.a.x)
                """;
        assertTrue(Compiler.compile(src).containsKey("demo.F"),
                "the determined type reaches the expansion inside the invariant");
    }

    @Test
    void aRecursiveHelperMissingItsTypesIsTheOnlyThingReported() {
        // Settling reads the recursive helpers' signatures, and one that does not declare its types
        // costs it all of them — so `describe`, which is determined only by a call to the recursive
        // helper that IS declared, is left unsettled. That is not observable: the check builds the
        // same map outside its recovery and abandons the module on the same error. Holding it here
        // means making that map recoverable cannot quietly add a second, derived report about
        // `describe` on top of the real one.
        String src = """
                module demo
                data A = { x: Int }
                data B = { y: Int }
                data U = A | B
                data X = Int
                partial let validRecursive (v: U): Int = match v with
                        | A as a -> a.x
                        | B as b -> b.y
                partial let brokenRecursive (x) = brokenRecursive(x)
                let describe (v) = {
                    let n = validRecursive(v)
                    match v with
                        | A as a -> a.x + n
                        | B as b -> b.y
                }
                behavior f : (a: A) -> X constructs X
                let f (a) = X(describe(a))
                """;
        assertEquals(List.of("name.a-recursive-helper-must-declare-its-return-type"),
                Located.diagnosticsOf(Compiler.diagnoseModules(java.util.Map.of("demo.sou", src)))
                        .get(new SourceId("demo.sou")).stream()
                        .map(d -> MessageKeys.of(d.said())).toList(),
                "the undeclared recursive helper is reported, and nothing else is");
    }

    // --- what the body leaves open ---

    @Test
    void aContainerWhoseElementNothingSettlesCarriesTheVariable() {
        // The body says what each parameter is — a List, a Set, a Map — and nothing says what it
        // holds. The outer type constructor is determined, so the parameter takes it with the
        // element still open, monomorphized at each expansion as a core helper is.
        assertTrue(bodyTypes("let count (xs) = List.length(xs)"), "`xs` is a List of something");
        assertTrue(bodyTypes("let firstOf (xs) = List.get(0, xs)"), "so is `firstOf`'s");
        assertTrue(bodyTypes("let sizeOf (s) = Set.size(s)"), "`s` is a Set of something");
        assertTrue(bodyTypes("let keysOf (m) = Map.keys(m)"), "`m` is a Map of something");
    }

    @Test
    void aContainerWithTwoVariablesCarriesBoth() {
        // `Map.keys` names the Map and neither its key nor its value, so both stay open together.
        assertTrue(bodyTypes("let keyCount (m) = List.length(Map.keys(m))"),
                "`m` is a Map of something to something");
    }

    @Test
    void aBareVariableIsNotADeterminedType() {
        // Nothing about `v` is settled — not even what shape it is — so this is annotated as it was.
        String src = """
                module demo
                data X = Int
                behavior f : (x: X) -> X constructs X
                let id (v) = v
                let f (x) = X(id(x.value))
                """;
        CompileException e = assertThrows(CompileException.class, () -> Compiler.compile(src));
        assertInstanceOf(HelperMessage.AParameterIsNotDeterminedByTheBody.class, e.diagnostic().said(), e.getMessage());
    }

    @Test
    void aConcreteAnswerAnywhereInTheBodyStillWins() {
        // Each of these puts the parameter where a signature writes `List<'a>` and somewhere else
        // says what the element is. The concrete answer is the one taken, whichever comes first,
        // and the helper walks Ints rather than anything.
        assertTrue(walksInts("total", "let total (xs) = List.sum(xs) + 1"),
                "`+ 1` says the element is an Int");
        assertTrue(walksInts("doubled", "let doubled (xs) = List.map((v) -> v * 2, xs)"),
                "the closure's body says the element is an Int");
    }

    @Test
    void whatEachUseSaysDoesNotDependOnWhichWasWrittenFirst() {
        // Both uses hand `xs` to a signature that names the container and not the element, and the
        // two answers are the same whichever is written first. Neither says the element is an Int:
        // `List.sum`'s result is the element, and reading it as one asks what the element is.
        assertEquals(compiles("let a (xs) = List.length(xs) + List.sum(xs)"),
                compiles("let b (xs) = List.sum(xs) + List.length(xs)"),
                "written order decides nothing here");
    }

    /** Whether {@code helper} settled its element to Int: it takes a List of Ints and not of Strings. */
    private static boolean walksInts(String name, String helper) {
        return compiles(helper + "\nlet takesInts (n: Int) = if n > 0 then " + name + "([ 1, 2 ]) else "
                        + name + "([ 3 ])")
                && !compiles(helper + "\nlet takesStrings (n: Int) = if n > 0 then " + name
                        + "([ \"a\" ]) else " + name + "([ \"b\" ])");
    }

    @Test
    void anErrorBesideAnOpenElementIsReportedAsTheErrorItIs() {
        // `xs` is a List of something and that is what it is. What the body gets wrong is the `+`,
        // and that is what is reported, at the position of the disagreement — the parameter is not
        // blamed for an error that has nothing to do with what it holds.
        String src = """
                module demo
                data X = Int
                behavior f : (x: X) -> X constructs X
                let bad (xs) = List.length(xs) + "s"
                let f (x) = X(bad([ 1 ]))
                """;
        CompileException e = assertThrows(CompileException.class, () -> Compiler.compile(src));
        assertInstanceOf(ArithmeticMessage.AnOperandIsNotANumber.class, e.diagnostic().said(), e.getMessage());
    }

    @Test
    void anErrorUnrelatedToTheOpenElementIsNotBlamedOnTheParameter() {
        // The mistake is `s * 2` and it says nothing about `xs`. Annotating `xs` would change nothing.
        String src = """
                module demo
                data X = Int
                behavior f : (x: X) -> X constructs X
                let bad (xs, s: String) = List.length(xs) + (s * 2)
                let f (x) = X(bad([ 1 ], "a"))
                """;
        CompileException e = assertThrows(CompileException.class, () -> Compiler.compile(src));
        assertTrue(e.getMessage().contains("arithmetic"), e.getMessage());
        assertTrue(!e.getMessage().contains("not determined"), e.getMessage());
    }

    /** {@link #bodyTypes} asked as a question rather than as an assertion. */
    private static boolean compiles(String defs) {
        try {
            return bodyTypes(defs);
        } catch (CompileException _) {
            return false;
        }
    }

    @Test
    void anAnnotatedHelperIsUnchanged() {
        String src = """
                module demo
                data X = Int
                behavior f : (x: X) -> X constructs X
                let positive (v: Int) = v >= 0
                let f (x) = if positive(x.value) then x else X(0)
                """;
        assertTrue(Compiler.compile(src).containsKey("demo.F"), "the annotated-helper form still compiles");
    }
}
