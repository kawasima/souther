package souther.architecture;

import org.junit.jupiter.api.Test;

import java.lang.classfile.ClassModel;
import java.lang.classfile.constantpool.MemberRefEntry;
import java.lang.classfile.constantpool.PoolEntry;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Who may make a place in a text, which is whoever laid that text out.
 *
 * <p>A place says which of the things written in a text it is — the {@code t}-th meaningful token
 * of the {@code c}-th top-level construct — and what counts those is what parsed the text. A caller
 * spelling the numbers out has counted them a second time, against nothing, and the two counts go
 * on separately: the place is type-correct, reaches every reader, and names whatever token happens
 * to sit that far along. That is not a hypothetical. A cursor was turned into a place by handing
 * the editor's line and column to a constructor that took a line and a column until this, and
 * go-to-definition answered nothing for every position in every file.
 *
 * <p>So the constructor is the package's, and the way in from outside it says which text the caller
 * means: {@code Placement} is made by whoever holds a text, and a place is made from one. This is
 * who does both. A row that is new here is a second counter of some text's tokens.
 *
 * <p>What a test writes is outside this, and deliberately: a test spelling a place is stating a
 * coordinate to compare rather than reading one off a text, and nothing it makes reaches a reader.
 * The walk is over what this repository publishes, which is what the compiler is.
 *
 * <p>Read off the compiled classes rather than the source, so that a maker reached through a
 * constant or a method reference is a row here whatever it is spelled as.
 */
class WhoMayMakeAPlaceInATextTest {

    private static final String PLACEMENT = "souther/compiler/diag/Placement";

    private static final String POSITION = "souther/compiler/diag/SourcePos";

    private static final CompiledOutputs COMPILED = CompiledOutputs.ofWhatThisRepositoryPublishes();

    /**
     * Who says which text a place is in.
     *
     * <p>Two, and both hold the text they are answering about. {@code CstFrontend} is where a text
     * arrives to be parsed and so where what it is — a file this compile holds, a text nobody named,
     * one put back together out of what a module published — is known; {@code SourceLayout} is where
     * a text becomes places, and is handed the same answer or works it out from a source id it was
     * given.
     *
     * <p>{@code SourcePos} is here for the one place that is in no text at all, which every pass
     * minting a position to mean nowhere reaches through.
     */
    private static final List<String> SAYING_WHICH_TEXT = List.of(
            "souther/compiler/cst/SourceLayout",
            "souther/compiler/diag/SourcePos",
            "souther/compiler/frontend/CstFrontend");

    /**
     * And who turns one into a place, which is where the tokens of that text are counted.
     *
     * <p>One. Everything else is handed the place that count made, or is handed a token and asks
     * the layout where it is. A second row is a pass that worked out which token of some text it
     * was at without the text in front of it.
     */
    private static final List<String> MAKING_A_PLACE = List.of("souther/compiler/cst/SourceLayout");

    /**
     * And who mints a place in no text at all, which is the one spelling left open.
     *
     * <p>Three passes, each with the same reason: what they build is not written anywhere. A
     * checker's own reads, a fixture this compiler generated, a branch nothing in the source
     * reaches — none of them is quoted back at a reader, and what makes such a position useful is
     * that two of them are equal. It names no text, so nothing can resolve it and no reader can be
     * sent to it, which is why it is the spelling that stays open.
     *
     * <p>Written down all the same. A row that is new is a pass that has decided some code is
     * nowhere, and whether the code it is about really is written nowhere is the question to ask
     * of it.
     */
    private static final List<String> MINTING_A_PLACE_IN_NO_TEXT = List.of(
            "souther/compiler/check/InvariantChecker",
            "souther/compiler/partition/FixtureTemplate",
            "souther/compiler/query/Adequacy$DeadBranches");

    @Test
    void everyPlaceThatSaysWhichTextAPlaceIsInIsWrittenDownHere() {
        assertEquals(SAYING_WHICH_TEXT, sayingWhichText(),
                "which text a place is in is known where the text is, so it is said there: a"
                        + " placement made anywhere else is a caller telling a place what file it"
                        + " is in from whatever it had to hand");
    }

    @Test
    void andEveryPlaceThatMakesOne() {
        assertEquals(MAKING_A_PLACE, makingAPlace(),
                "a place is which of a text's tokens it is at, so it is made where they are"
                        + " counted: one made elsewhere is a second count of the same text");
    }

    /**
     * And the constructor that says which text is not a way round either of them.
     *
     * <p>The rows above are over the doors. A caller reaching the constructor itself would name
     * neither, so what it is closed to is asked separately — and the answer is the package it is
     * in, which is where the doors are.
     */
    @Test
    void andNobodyOutsideThePackageWritesAPlacedPositionOut() {
        List<String> writing = writingAPlacedPosition();

        assertTrue(writing.contains(POSITION),
                () -> "a position writes itself out, so a walk that cannot find that is finding"
                        + " nothing at all: " + writing);
        assertEquals(List.of(POSITION), writing,
                "the constructor that says which text is the package's: a class outside it that"
                        + " writes one is one javac would have refused and a reader here should"
                        + " look at");
    }

    /** And the one spelling left open is spelled by the passes that have somewhere to put it. */
    @Test
    void everyPassThatMintsAPlaceInNoTextIsWrittenDownHere() {
        assertEquals(MINTING_A_PLACE_IN_NO_TEXT, mintingAPlaceInNoText(),
                "a position in no text is one nothing can resolve and nobody can be sent to, which"
                        + " is what makes it right for code written nowhere and wrong for code"
                        + " written somewhere this pass did not look");
    }

    /**
     * And the walk sees the makers that are there.
     *
     * <p>The control the three above need. Matched on a name nothing has, every answer would be
     * empty and equal to an empty expectation.
     */
    @Test
    void andTheWalkSeesAMakerThatIsThere() {
        assertTrue(makingAPlace().contains("souther/compiler/cst/SourceLayout"),
                "a text becomes places in the layout, so a walk that cannot find that finds"
                        + " nothing");
        assertTrue(sayingWhichText().contains("souther/compiler/frontend/CstFrontend"),
                "and what a text is is said where it is parsed");
    }

    /** Every class that says which text a place is in. */
    private static List<String> sayingWhichText() {
        return found(entry -> entry instanceof MemberRefEntry member
                && member.owner().name().stringValue().equals(PLACEMENT)
                && (member.name().stringValue().equals("aFileOfThisCompile")
                        || member.name().stringValue().equals("aTextWithNoIdentity")
                        || member.name().stringValue().equals("whatAModulePublished")));
    }

    /** Every class that makes a place out of one. */
    private static List<String> makingAPlace() {
        return found(entry -> entry instanceof MemberRefEntry member
                && member.owner().name().stringValue().equals(PLACEMENT)
                && member.name().stringValue().equals("at"));
    }

    /**
     * Every class that writes out a position saying which text it is in.
     *
     * <p>Told from the other by what it takes, which is the only thing that tells them apart in a
     * constant pool: both are written {@code <init>}, and one of them is the spelling every pass
     * minting a position to mean nowhere uses.
     */
    private static List<String> writingAPlacedPosition() {
        return found(entry -> entry instanceof MemberRefEntry member
                && member.owner().name().stringValue().equals(POSITION)
                && member.name().stringValue().equals("<init>")
                && member.type().stringValue().contains(PLACEMENT));
    }

    /** And every class that mints one in no text. */
    private static List<String> mintingAPlaceInNoText() {
        return found(entry -> entry instanceof MemberRefEntry member
                && member.owner().name().stringValue().equals(POSITION)
                && member.name().stringValue().equals("<init>")
                && !member.type().stringValue().contains(PLACEMENT));
    }

    /**
     * Every class this repository publishes whose constant pool reaches something {@code reaching}
     * admits, leaving out the two types the doors are on.
     *
     * <p>What a class names of itself is not a reader of anything: a placement makes the places it
     * offers, and a position writes itself out, so rows for those would be this walk reporting each
     * owner as its own caller.
     */
    private static List<String> found(Predicate<PoolEntry> reaching) {
        Set<String> out = new TreeSet<>();
        for (Path module : COMPILED.modules()) {
            for (ClassModel each : COMPILED.classesOf(module)) {
                String reader = each.thisClass().asInternalName();
                if (reader.equals(PLACEMENT) || reader.startsWith(PLACEMENT + "$")) {
                    continue;
                }
                for (PoolEntry entry : each.constantPool()) {
                    if (reaching.test(entry)) {
                        out.add(reader);
                    }
                }
            }
        }
        return new ArrayList<>(out);
    }
}
