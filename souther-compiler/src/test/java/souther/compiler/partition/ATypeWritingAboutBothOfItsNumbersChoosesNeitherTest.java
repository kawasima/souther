package souther.compiler.partition;

import org.junit.jupiter.api.Test;

import souther.compiler.diag.SourceNameResolver;
import souther.compiler.query.Adequacy;
import souther.compiler.query.Compilation;
import souther.compiler.report.AdequacyReport;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * A position is measured at one of its numbers, and a type writing about both of them chooses
 * neither.
 *
 * <p>A {@code String} is the one value with two numbers — its own order, and the length of it — and
 * the rules reaching a position from the value it sits in were already asked whether they leave the
 * choice open. The type's own rules were not: the reader took whichever of the two it looked at
 * first, and the rule about the other went out with nothing saying it had. An author was told their
 * model draws one line, of a model that draws two.
 */
class ATypeWritingAboutBothOfItsNumbersChoosesNeitherTest {

    /**
     * One type writing about both of its numbers, and a record bounding one of them beside it.
     *
     * <p>Both in one module because the second is only right if the first is: what a rule reaching
     * `Code` from `Holder` may do is settled by what `Code`'s own rules already left open.
     */
    private static final String BOTH = """
            module owned

            data Code = String
                invariant value >= "m"
                invariant String.length(value) >= 3

            data Holder = { c: Code } invariant String.length(c.value) >= 5

            data Ok = { size: Int }

            behavior onCode : (v: Code) -> Ok
                constructs Ok
            let onCode (v) = Ok { size = String.length(v.value) }

            behavior onHolder : (v: Holder) -> Ok
                constructs Ok
            let onHolder (v) = Ok { size = String.length(v.c.value) }

            example onCode   | (Code("zzz")) -> Ok { size = 3 }
            example onHolder | (Holder { c = Code("zzzzz") }) -> Ok { size = 5 }
            """;

    /**
     * Both of the type's own rules are named, each at the number it is about.
     *
     * <p>Both are read to the end and both place an end, and a position has one axis to place one
     * of them on. Taking whichever was looked at first put a line the author can read beside one
     * they cannot see: the length edge came in, the line at `m` went out, and the report named
     * neither the rule it dropped nor the fact that it had dropped one.
     */
    @Test
    void aTypeWritingAboutBothOfItsNumbersNamesBothRules() {
        assertEquals(List.of("v: COMPETING_COORDINATES",
                        "String.length(v): COMPETING_COORDINATES"),
                notReadIn(BOTH, "onCode"),
                "both of the type's own rules are named, each at the coordinate it is about");
    }

    /**
     * And neither of them divides the position.
     *
     * <p>The other half of the same answer, and the half an author sees first. Taking the length
     * because it was looked at first left a report showing the classes of a model that draws one
     * line, where the model draws two and this compiler can follow neither.
     */
    @Test
    void andNeitherOfThemDividesThePosition() {
        assertEquals(List.of(), axesOf(BOTH, "onCode"),
                "neither number is an axis of this position");
    }

    /**
     * A rule arriving from the value the position sits in is no tie breaker, and does not vanish.
     *
     * <p>What such a rule states is where one number stops; which number the position is measured
     * at is what its own type says, and here its own type said two things. Read as a third vote,
     * `Holder`'s clause would settle a question the standing above it left open — and the model
     * `Code` describes would depend on which records happen to hold one.
     */
    @Test
    void aRuleFromOutsideNeitherChoosesNorGoesQuiet() {
        assertEquals(List.of("v.c: COMPETING_COORDINATES",
                        "String.length(v.c): COMPETING_COORDINATES",
                        "String.length(v.c): COMPETING_COORDINATES"),
                notReadIn(BOTH, "onHolder"),
                "the record's clause is named beside the type's own, and none of them chose");
    }

    /**
     * A format beside a length is one of these, and the shape an author is most likely to write.
     *
     * <p>A rule about the strings a position holds is a rule about the position's own value: which
     * strings it admits is where they stop on the string's own order, whatever run comes of it. So
     * this type states two things — where its length stops, and which strings stand here — and they
     * are about its two numbers.
     *
     * <p><b>The model is not refused and nothing is unbuildable.</b> A value of it is composed
     * against both rules and the decoder settles which candidate stands
     * ({@code ACandidateIsProposedFromTheRuleAndNotTheCarrierAloneTest}). What this position does
     * not have is a single axis to divide along, so the line at the length is not drawn — and that
     * is the limit of one measurement coordinate per position rather than anything about this
     * model.
     *
     * <p>The word beside the format is its own reading's and not this one's: the strings
     * {@code [0-9]+} admits are not a set this compiler works out, which it would answer whether or
     * not a length rule stood beside it.
     */
    @Test
    void aFormatBesideALengthIsWrittenAboutBothNumbers() {
        String source = """
                module formatted

                data C = String
                    invariant String.length(value) >= 2 && String.matches("[0-9]+", value)

                data Ok = { size: Int }

                behavior onC : (v: C) -> Ok
                    constructs Ok
                let onC (v) = Ok { size = String.length(v) }

                example onC | (C("123")) -> Ok { size = 3 }
                """;

        assertEquals(List.of("String.length(v): COMPETING_COORDINATES",
                        "v: EXACT_VALUES_TOO_COSTLY"),
                notReadIn(source, "onC"),
                "the length rule is named as one nothing could choose between");
        assertFalse(report(source).contains("String.length(v) = 2"),
                "and no line is drawn at the length");
    }

    /** The numbers {@code behavior} divides some position along, as a document names them. */
    private static List<String> axesOf(String source, String behavior) {
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        return AdequacyReport.of(compilation).modules().get(0).behaviors().stream()
                .filter(each -> each.name().equals(behavior))
                .flatMap(each -> each.partition().axes().stream())
                .map(each -> each.name()).toList();
    }

    private static String report(String source) {
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        return AdequacyReport.of(compilation).human(SourceNameResolver.identity());
    }

    /** What {@code behavior} left unread, as the coordinate it is about and the reason. */
    private static List<String> notReadIn(String source, String behavior) {
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        return AdequacyReport.of(compilation).modules().get(0).behaviors().stream()
                .filter(each -> each.name().equals(behavior))
                .flatMap(each -> each.partition().notRead().stream())
                .map(each -> each.at() + ": " + each.reason()).toList();
    }
}
