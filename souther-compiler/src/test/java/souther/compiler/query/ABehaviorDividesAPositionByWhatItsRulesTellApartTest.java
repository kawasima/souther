package souther.compiler.query;

import org.junit.jupiter.api.Test;

import souther.compiler.inputs.BlockReason;
import souther.compiler.inputs.RuleWithoutALine;
import souther.compiler.partition.Axis;
import souther.compiler.partition.PartitionClass;
import souther.compiler.partition.Partitions;
import souther.compiler.partition.Recognition;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A behavior that treats some of the strings at a position differently divides that position.
 *
 * <p>The whole of what this issue is about, asked of the compiler rather than of any stage in it. An
 * invariant restricts a position — every value outside it is refused at construction — and a
 * behavior divides one: both sides of what a {@code guard} asks are values the model may hold, and a
 * run is on one side or the other. A position an author wrote such a rule for was coming back
 * undivided, so nothing owed a row on either side.
 *
 * <p>End to end because every stage between was already right about its own part. The rule was read,
 * what it admits was worked out, and the classes were built — from the tree where the operation had
 * been expanded into what it does, so there was no rule there to find. What this holds is the answer,
 * which is the only place all of it shows.
 */
class ABehaviorDividesAPositionByWhatItsRulesTellApartTest {

    @Test
    void aGuardOnWhatTheStringsAtAPositionAreDividesIt() {
        Axis axis = dividing("""
                behavior route : (code: String) -> Where
                let route (code) = {
                    guard String.startsWith("JP", code) else Abroad
                    Home
                }
                """);

        assertEquals(List.of(
                        "String.startsWith(\"JP\", x)",
                        "not String.startsWith(\"JP\", x)"),
                axis.classes().stream().map(PartitionClass::label).toList(),
                "the position is divided into what the rule admits and what it leaves");
    }

    /** And the classes are sets of the position's values, which is what such a rule states. */
    @Test
    void andEachClassIsTheValuesARunOfTheModelFallsIn() {
        Axis axis = dividing("""
                behavior route : (code: String) -> Where
                let route (code) = {
                    guard String.startsWith("JP", code) else Abroad
                    Home
                }
                """);

        for (PartitionClass each : axis.classes()) {
            Recognition.OfASet is = assertInstanceOf(Recognition.OfASet.class, each.recognises(),
                    "a class such a rule makes is the values in it");
            assertTrue(!is.values().isEmpty(), "and it holds some of them");
        }
    }

    /**
     * A rule a behavior states about itself divides the position, and is answered for once.
     *
     * <p>The two halves are one question. A clause stating such a rule is read by the reader that
     * turns it into classes and passed over by the reader of comparisons, and if either half is
     * wrong the document is wrong about a rule the author wrote: with no classes the position comes
     * back undivided, and with a finding beside them the same statement is both a rule that was read
     * and a comparison whose form could not be read.
     *
     * <p>Held end to end rather than at the reader, because neither reader can see the other's
     * answer. Each is right on its own — one made the classes, the other met a form it does not
     * read — and what they come to together is only visible where both have been published.
     */
    @Test
    void aRuleAClauseStatesDividesThePositionAndIsAnsweredForOnce() {
        Partitions.Partitioning divided = partitioningOf("""
                behavior route : (code: String) -> Where
                    ensures Home -> String.startsWith("JP", code)
                let route (code) = Home
                """);

        assertEquals(2, divided.axes().stream()
                        .flatMap(each -> each.classes().stream()).toList().size(),
                "the clause divides the position into what it admits and what it leaves: "
                        + divided.axes());
        assertEquals(List.of(), divided.unanswered(),
                "and no question about that rule is held open: a statement read as a rule about"
                        + " the strings is not also one whose questions nothing worked out");
    }

    /**
     * Two rules about one position leave it divided into what they come to between them.
     *
     * <p>Not two divisions of it. A run satisfies each or does not, so the rows are owed one class
     * at each of the four — taken a rule at a time, the denominator would be one partition of the
     * position stated twice.
     */
    @Test
    void twoRulesLeaveThePositionDividedIntoWhatTheyComeToBetweenThem() {
        Axis axis = dividing("""
                behavior route : (code: String) -> Where
                let route (code) = {
                    guard String.startsWith("JP", code) else Abroad
                    guard String.endsWith("-X", code) else Abroad
                    Home
                }
                """);

        assertEquals(4, axis.classes().size(),
                "the two rules come to four classes: " + axis.classes().stream()
                        .map(PartitionClass::label).toList());
    }

    /**
     * And a position both kinds of rule reach has no classes, rather than the ones the sets left.
     *
     * <p>A line on the order the values are counted on and a set of them are two vocabularies, and a
     * class in one cannot be written in the other. Divided by the half that happened to be
     * expressible, a run would be counted at a class the model tells apart from the one beside it.
     */
    @Test
    void aPositionBothKindsOfRuleReachHasNoClasses() {
        Axis axis = dividing("""
                behavior route : (code: String) -> Where
                let route (code) = {
                    guard String.startsWith("JP", code) else Abroad
                    guard code < "M" else Abroad
                    Home
                }
                """);

        assertEquals(List.of(), axis.classes().stream().map(PartitionClass::label).toList(),
                "the rules are not all sayable as one list of classes");
        // And what the ordering rule cut is untouched: the cuts are an observation of their own and
        // not a projection of the classes, so a border still knows where the values part.
        assertTrue(!axis.cuts().isEmpty(),
                "while where the rules cut the position is still what it was");
    }

    /**
     * A value singled out and a set told from the rest are one vocabulary, not two.
     *
     * <p>{@code code == "JP"} names a set of the position's values as surely as
     * {@code String.startsWith("J", code)} does — one of them holds a value and the other holds
     * every string beginning with a letter — so the two compose, and what they leave is the classes
     * they come to between them.
     *
     * <p>Told apart by which reader produced them, a position both reach would lose its classes to
     * a vocabulary clash that is not there. Which is a distinction being dropped for the shape of
     * this compiler's evidence rather than for anything the model says.
     */
    @Test
    void aValueSingledOutAndASetToldFromTheRestComposeIntoOneListOfClasses() {
        Axis axis = dividing("""
                behavior route : (code: String) -> Where
                let route (code) = {
                    guard String.startsWith("J", code) else Abroad
                    guard code == "JP" else Abroad
                    Home
                }
                """);

        assertTrue(!axis.classes().isEmpty(),
                "the two rules are sayable together, so the position has classes");
        assertTrue(axis.classes().stream().allMatch(
                        each -> isOfASet(each.recognises())),
                "and they are the values on either side of what the two rules come to: "
                        + axis.classes().stream().map(PartitionClass::label).toList());
    }

    /**
     * And a rule about a field every case of a sum spreads is measured where the field stands.
     *
     * <p>The rule is written inside an arm, about the name the arm binds — which is the position
     * narrowed to that case. Where the rule stands and where the position is are two places, and
     * what a position is divided into has to arrive at the one the rule is about.
     */
    @Test
    void aRuleInsideAnArmDividesThePositionTheArmBinds() {
        Partitions.Partitioning divided = partitioningOf("""
                data Small = { code: String }
                data Large = { code: String }
                data Parcel = Small | Large

                behavior route : (parcel: Parcel) -> Where
                let route (parcel) = match parcel with
                    | Small as s -> if String.startsWith("JP", s.code) then Home else Abroad
                    | Large      -> Abroad
                """);

        assertTrue(divided.axes().stream().anyMatch(each -> !each.classes().isEmpty()),
                "the position the arm binds is divided by the rule written about it: "
                        + divided.axes());
    }

    /**
     * And a rule that divided nothing is shown to somebody.
     *
     * <p>Every string begins with the empty one, so this rule puts every value the position holds on
     * one side of itself and the model draws no line. What must not happen is silence: a rule an
     * author wrote reached the measure, came to nothing, and a reader has to be told which rule and
     * where — otherwise the position comes back as one the model says nothing about, and the rule
     * sitting in the body says otherwise.
     */
    @Test
    void aRuleThatDividedNothingIsSaidOfTheRule() {
        Partitions.Partitioning divided = partitioningOf("""
                behavior route : (code: String) -> Where
                let route (code) = {
                    guard String.startsWith("", code) else Abroad
                    Home
                }
                """);

        assertEquals(List.of(), divided.axes(), "the position is divided by nothing");
        assertEquals(List.of(new BlockReason.PredicateTellingNothingApart()),
                divided.rulesWithoutALine().stream()
                        .map(RuleWithoutALine::why).toList(),
                "and the rule is named as one that tells nothing apart");
    }

    /**
     * A distinction this compiler did not get closes the position's classes, even where the
     * declarations had already divided it.
     *
     * <p>The whole of what a blocker is for, and the case it is most needed in: no rule of the body
     * came out, so there is no evidence at the position at all. Measured off the evidence, the
     * position is not among the numbers this stage looks at — and the classes the declarations left
     * stand, beside a rule nobody could read that was supposed to close them.
     */
    @Test
    void aDistinctionThisCompilerDidNotGetClosesTheClassesTheDeclarationsLeft() {
        Partitions.Partitioning divided = partitioningOf("""
                data Code = String
                    invariant value == "JP" || value == "US"

                behavior route : (code: Code, p: String) -> Where
                let route (code, p) = {
                    guard String.startsWith(p, code.value) else Abroad
                    Home
                }
                """);

        assertEquals(List.of(), divided.axes().stream()
                        .flatMap(each -> each.classes().stream()).map(PartitionClass::label)
                        .toList(),
                "the position has no classes while a rule about it went unread");
        assertTrue(divided.rulesWithoutALine().stream()
                        .anyMatch(each -> each.why() instanceof BlockReason.UnreadValueRule),
                "and the rule that could not be read is named: "
                        + divided.rulesWithoutALine());
    }

    /**
     * And a rule proved to tell nothing apart leaves them standing.
     *
     * <p>The other side of the pair, and what keeps the first from being "any rule this compiler
     * did not turn into a division". This rule was read to the end and what it states is that the
     * position is undivided by it — so the classes the declarations left are untouched, and the rule
     * is reported on its own account.
     */
    @Test
    void andARuleProvedToTellNothingApartLeavesThemStanding() {
        Partitions.Partitioning divided = partitioningOf("""
                data Code = String
                    invariant value == "JP" || value == "US"

                behavior route : (code: Code) -> Where
                let route (code) = {
                    guard String.startsWith("", code.value) else Abroad
                    Home
                }
                """);

        assertTrue(divided.axes().stream().anyMatch(each -> !each.classes().isEmpty()),
                "what the declarations divide the position into is still there: " + divided.axes());
        assertTrue(divided.rulesWithoutALine().stream()
                        .anyMatch(each -> each.why()
                                instanceof BlockReason.PredicateTellingNothingApart),
                "and the rule that tells nothing apart is reported on its own: "
                        + divided.rulesWithoutALine());
    }

    /**
     * A position the declarations already cut, divided by a rule about its strings, carries no cut.
     *
     * <p>A string is counted on an order and a literal is a place on it, so a bounded string beside
     * a rule about its prefixes is a real pair and not a shape nobody writes. A cut is a place on
     * that order and a class that is a set has no answer to where it lies — so an axis carrying
     * both is one every line of which falls in no class, which its own constructor refuses.
     *
     * <p>Held here rather than at the composing answer because that answer says it already: what is
     * being asked is whether the measure assembled from it kept the answer's word.
     */
    @Test
    void aPositionWhoseClassesAreSetsCarriesNoCut() {
        Partitions.Partitioning divided = partitioningOf("""
                data Code = String
                    invariant value >= "A"

                behavior route : (code: Code) -> Where
                let route (code) = {
                    guard String.startsWith("JP", code.value) else Abroad
                    Home
                }
                """);

        for (Axis each : divided.axes()) {
            if (each.classes().stream()
                    .anyMatch(one -> isOfASet(one.recognises()))) {
                assertEquals(List.of(), each.cuts(),
                        "a class that is a set has no place on the order for a cut to be at: "
                                + each);
            }
        }
        assertTrue(divided.axes().stream().anyMatch(each -> each.classes().stream()
                        .anyMatch(one -> isOfASet(one.recognises()))),
                "and the rule does divide the position into sets: " + divided.axes());
    }

    /**
     * Whether a class means a set of the position's values, under whatever names a row writes one
     * of them under.
     *
     * <p>Asked through the wearing rather than of the arm in hand. A class of a position that
     * wears a name is written down under it, so the outermost arm is the wearing and the meaning
     * is one inside — and a model whose position wears one would read here as a position divided
     * nowhere.
     */
    private static boolean isOfASet(Recognition recognition) {
        return switch (recognition) {
            case Recognition.OfASet _ -> true;
            case Recognition.Under under -> isOfASet(under.inner());
            default -> false;
        };
    }

    /** The one measure the model under test makes of its position. */
    private static Axis dividing(String behavior) {
        Partitions.Partitioning divided = partitioningOf(behavior);
        assertEquals(1, divided.axes().size(),
                "the model under test measures one number: " + divided.axes());
        return divided.axes().get(0);
    }

    /** What the model under test divides its behavior into. */
    private static Partitions.Partitioning partitioningOf(String behavior) {
        String source = """
                module demo

                data Home
                data Abroad
                data Where = Home | Abroad

                """ + behavior;
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.answerEverything();
        assertEquals(List.of(), compilation.errors().stream()
                        .map(each -> each.diagnostic().code() + " " + each.diagnostic().primary())
                        .toList(),
                "the model under test compiles");
        Partitions.Partitioning divided = compilation.db()
                .ask(new Adequacy.Divided(compilation.modules().get(0), "route")).value();
        assertNotNull(divided, "the behavior was divided");
        return divided;
    }
}
