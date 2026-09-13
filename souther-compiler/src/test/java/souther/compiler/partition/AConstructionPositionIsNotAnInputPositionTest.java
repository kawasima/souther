package souther.compiler.partition;

import org.junit.jupiter.api.Test;

import souther.test.RepositoryLayout;

import souther.compiler.check.DeclaredBounds;
import souther.compiler.check.RuleReadingSource;
import souther.compiler.check.RuleReadings;
import souther.compiler.check.DeclaredSig;
import souther.compiler.inputs.InputDomain;
import souther.compiler.inputs.Position;
import souther.compiler.inputs.Refinement;
import souther.compiler.inputs.Requirements;
import souther.compiler.inputs.TermPath;
import souther.compiler.query.Bodies;
import souther.compiler.query.Compilation;
import souther.compiler.query.ReadAs;
import souther.compiler.types.CaseSelector;
import souther.compiler.types.Type;
import souther.compiler.types.TypeSymbol;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where a value has to be built and where a behavior's input has a position are different questions.
 *
 * <p>They are spelled with the same {@link TermPath} and they are about different things, which is
 * the defect this exists over: two walks derived the positions of one parameter, each going as far as
 * its own question took it, and a question the reading of an input was asked about a position the
 * generator was working at came back
 * refused because the reading had no such position. Read as one set, the refusal looks like the model
 * having nothing there.
 *
 * <p>How the sets part is what each of them is asked. The reading enumerates the positions a type
 * can have and stops where a path returns to a declaration already open on it, because that question
 * has no other end; the search goes on until there is a value to build.
 *
 * <p>How a position is <em>named</em> is the one thing they do not part over. A class that narrows a
 * sum to one of its cases puts positions under that narrowing, and both write the narrowing into the
 * path — so a class fixed at such a position is looked for at the path the plan builds it at.
 * Written flat here, the same location would have two names and the lookup would find nothing.
 *
 * <p>So what is checked here is that a path from one is not looked up in the other, and that the
 * disagreement is the design rather than a defect. What they share is not a step but an answer at a
 * record: the questions about a shape come to the same fields there and part at a sum whose cases
 * share a spread, which is checked as a law over the answers by
 * {@code WhatIsReadableAndWhatIsBuiltAgreeAtARecordAndPartAtASumTest}.
 */
class AConstructionPositionIsNotAnInputPositionTest {

    /** The rules counting nothing, which is what every model here leaves them saying. */
    private static final ConstructionPlan.HowManyItHolds ANY =
            (_, _) -> new DeclaredBounds.CountRange(0, Integer.MAX_VALUE);


    /** Read once: what this asks of it does not change between its checks. */
    private static final RepositoryLayout REPOSITORY = RepositoryLayout.ofWorkingDirectory();

    private record Read(DeclaredSig sig, RuleReadingSource rules) {

        /** The behavior's one parameter. */
        DeclaredSig.Input only() {
            return sig.inputs().get(0);
        }
    }

    private static Read of(String source, String behavior) {
        Compilation compilation =
                Compilation.ofSources(List.of(source), souther.compiler.meta.ModulePath.EMPTY);
        compilation.answerEverything();
        String module = compilation.modules().get(0);
        Map<String, DeclaredSig> sigs =
                compilation.db().ask(new Bodies.DeclaredSignatures(module)).value();
        RuleReadingSource rules = RuleReadings.of(compilation, module);
        return new Read(sigs.get(behavior), rules);
    }

    private static InputDomain reading(Read read) {
        return InputDomain.of(read.sig(), read.rules(), ReadAs.THE_COMPILATION_DOES);
    }

    /** The plan for the behavior's one parameter, with nothing decided and the given
     *  requirements. */
    private static ConstructionPlan plan(Read read, Requirements required) {
        ConstructionPlan.Result planned = ConstructionPlan.of(read.only().type(),
                TermPath.of(read.only().name()), read.rules().inners(), read.rules().symbols(),
                read.rules().published(), Set.of(),
                required,
                ANY);
        return assertInstanceOf(ConstructionPlan.Result.Planned.class, planned,
                "nothing here asks one position to be two things").plan();
    }

    private static List<String> slots(ConstructionPlan plan) {
        return plan.slots().stream().map(each -> each.at().toString()).toList();
    }

    private static final String NESTED = """
            module g

            data Zip = { code: String }
            data Address = { zip: Zip, forwarded: Option<Address> }
            data Order = { who: Address }
            data Ack = { at: String }

            behavior place : (o: Order) -> Ack
            """;

    /**
     * The reading stops where the path returns, and every position it does have answers for itself.
     *
     * <p>The first half of the rule. A path this reading holds is one it can be asked about, which
     * is what makes the other half a statement about something and not about a lookup that never
     * worked.
     */
    @Test
    void everyPositionTheReadingHasIsOneItAnswersFor() {
        InputDomain reading = reading(of(NESTED, "place"));
        assertEquals(List.of("o", "o.who", "o.who.zip", "o.who.zip.code", "o.who.forwarded",
                        "o.who.forwarded@Some"),
                reading.positions().stream().map(each -> each.path().toString()).toList());
        for (Position each : reading.positions()) {
            assertNotNull(reading.at(each.path()), () -> "no position at " + each.path());
        }
    }

    /**
     * And a path below where the reading stopped is not one it answers about.
     *
     * <p>Not a lookup that failed. The reading stops where the input returns to {@code Address},
     * because listing what a forwarding address can hold has no other end; the row still carries a
     * forwarded address with a {@code Zip} in it. Asking this reading about
     * {@code o.who.forwarded@Some.zip.code} and taking the answer for the model's is what the two
     * walks were doing to each other.
     *
     * <p>That the other set has positions this one does not is
     * {@link #aRecipePutsPositionsWhereTheDeclarationHasNone}'s to say, where a class chooses a
     * constructor and the recipe builds under it.
     */
    @Test
    void aPathBelowWhereTheReadingStoppedIsNotOneItAnswersAbout() {
        Read read = of(NESTED, "place");
        TermPath forwarded = reading(read).positions().stream().map(Position::path)
                .filter(each -> each.toString().equals("o.who.forwarded@Some"))
                .findFirst().orElseThrow();

        assertNull(reading(read).at(forwarded.then("zip")),
                "the reading stops at the return and does not enumerate below it");
        assertNull(reading(read).at(forwarded.then("zip").then("code")),
                "a position a value is built at is not a position of the input");
    }

    private static final String SUM = """
            module g

            data Approved = { id: Int }
            data Rejected = { why: String }
            data Decision = Approved | Rejected
            data Ack = { at: String }

            behavior decide : (d: Decision) -> Ack
            behavior probe : (a: Approved) -> Ack
            """;

    /**
     * A recipe puts positions under a sum, and no depth would have found them.
     *
     * <p>The half that says the two sets are not one set with a bound on it. {@code Decision} is a
     * sum and nothing is under it — the reading says so, and a reading that went on for ever would
     * say the same. What the row is building is an {@code Approved}, because a class of the position
     * said which case its witness takes, and {@code d.id} is a position of that and of nothing the
     * behavior declares.
     */
    @Test
    void aRecipePutsPositionsWhereTheDeclarationHasNone() {
        Read read = of(SUM, "decide");
        assertEquals(List.of("d"), slots(plan(read, Requirements.NONE)),
                "with no class choosing a constructor there is one thing to build, the sum itself");

        ConstructionPlan built = plan(read, throughApproved(read));
        assertEquals(List.of("d@Approved.id"), slots(built),
                "a narrowed position is written with the narrowing, as the reading writes it");

        InputDomain reading = reading(read);
        assertNotNull(reading.at(TermPath.of("d")), "the sum itself is a position of the input");
        assertNull(reading.at(TermPath.of("d").then("id")),
                "what is built under the sum is not at a name the declaration puts nothing at");
    }

    /**
     * And nothing that holds a plan can ask the reading about one of its positions.
     *
     * <p>The tests above say the two sets are two. What they cannot say is that nobody writes
     * {@code inputDomain.at(slot.at())} — the coordinate is one type on purpose, so that call
     * compiles, and what comes back is null for a construction position and a position of the input
     * for a path that happens to be both.
     *
     * <p>Asked of the sources at the grain the reading's own rule is asked at. Naming the file that
     * holds a plan is where such a call would be written, so the first line of it turns this red;
     * a helper in between defeats it, as it defeats every check of this kind here.
     *
     * <p>Asked of the code and not of the comments. Saying that the two are different things means
     * naming both, and a file doing that was the one thing this could not tell from a file asking
     * one about the other — answered by exempting the file that said it, which is an exemption that
     * grows by one every time somebody writes the sentence down.
     */
    @Test
    void nothingThatHoldsAPlanConsultsTheReading() throws IOException {
        List<Path> sources = mainSources();
        assertTrue(sources.size() > 20,
                () -> "the scan found only " + sources.size() + " sources, which is not the tree");

        List<String> both = new ArrayList<>();
        for (Path source : sources) {
            String text = code(Files.readString(source, StandardCharsets.UTF_8));
            if (!text.contains("ConstructionPlan") || !text.contains("InputDomain")) {
                continue;
            }
            both.add(source.getParent().getFileName() + "/" + source.getFileName());
        }
        assertEquals(List.of(), both,
                "a coordinate of the plan is not a position of the input, and these could ask");
    }

    /** {@code source} with its comments taken out, which is what this reads. */
    private static String code(String source) {
        return source.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("//[^\n]*", " ");
    }

    private static List<Path> mainSources() throws IOException {
        return REPOSITORY.mainJavaSources();
    }

    /** The requirement a class of {@code d} states by being the {@code Approved} case of it. */
    private static Requirements throughApproved(Read read) {
        return Requirements.NONE.and(TermPath.of(read.only().name()),
                toLeaf(caseNamed(SUM, "probe")));
    }

    /** The narrowing to one leaf, spelled the way the checker's resolution of an arm spells it: a
     *  leaf is a case that covers itself, so selecting it narrows to that one distinction. */
    private static Refinement toLeaf(TypeSymbol leaf) {
        return Refinement.of(souther.compiler.types.ResolvedCase.of(
                CaseSelector.direct(leaf), java.util.List.of(leaf)));
    }

    /** The name of the case to build through, taken off a behavior that is declared to take one. */
    private static TypeSymbol caseNamed(String source, String behavior) {
        Read read = of(source, behavior);
        return assertInstanceOf(ConstructionPlan.Built.class,
                plan(read, Requirements.NONE).root()).of();
    }

    /** And the declared type of the position does not move when a recipe chooses a case for it. */
    @Test
    void theRecipeRefinesWhatIsBuiltAndNotWhatIsDeclared() {
        Read read = of(SUM, "decide");
        Type declared = read.only().type();
        assertEquals(declared, reading(read).at(TermPath.of("d")).view().declared());

        ConstructionPlan built = plan(read, throughApproved(read));
        assertEquals(declared, reading(read).at(TermPath.of("d")).view().declared(),
                "the position is still declared to hold a Decision");
        ConstructionPlan.Built root = assertInstanceOf(ConstructionPlan.Built.class, built.root(),
                "an `Approved` is composed out of its fields");
        assertEquals("Approved", Type.show(root.type()),
                "and what is built there is the case the class named");
    }
}
