package souther.bench;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.classfile.ClassModel;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What decides a policy reads the answer; what writes a sentence writes one payload's words; and
 * what makes an answer is the reading that made it — over every module the reactor builds.
 *
 * <p>{@code Reachability} has three arms and each carries something: why nothing arrives, what says
 * something does, why nothing settled it. A reader that took one of those apart would be deciding
 * an obligation, a diagnostic or a claim on a distinction inside the payload, and a distinction
 * added there later would silently become a change to that decision.
 *
 * <p><b>Every module, because the rule is about the repository and not about one build.</b> The
 * compiler's own copy of this check reads the classes of the module it runs in, which is quick and
 * is all it can do: at the time that module's tests run, nothing downstream has been compiled. So
 * the rule is owned here, where the reactor is finished and every module's classes exist — the same
 * reason this module already reads them all to hold a generated class's name to one place.
 *
 * <p>Read off the compiled classes rather than the sources. The arms being package-private stops an
 * {@code instanceof} and nothing else: a {@code Words} and the factories are public, so a static
 * import, a qualified call or a method reference reaches them, and a scan over text answers about
 * spellings. What the bytecode says — who calls {@code said}, who implements a {@code Words}, who
 * calls a factory, whose code runs a constructor, and what handle a reference put in a bootstrap
 * argument — is what a call is.
 *
 * <p>Both directions are asserted. A check that only counts violations passes when it reads nothing
 * at all, so every rule names what it expects to find and fails where that is missing, and the walk
 * fails where a module the reactor builds has no classes to read.
 */
class OnlyARendererTakesAProofApartTest {

    private static final String REACH = "souther.compiler.reach.";

    /**
     * Who may ask each payload what it says, and write the words it says it in.
     *
     * <p>Every payload is named, including the one nothing reads: a payload left out would be one
     * these rules say nothing about, and adding words to it later would open a reader that nothing
     * here would notice. {@code Witness} has no words today and its expected set is empty, which is
     * a claim rather than a case skipped.
     */
    private static final Map<String, List<String>> WRITES_THE_WORDS_OF = Map.of(
            REACH + "Proof",
            List.of("souther.compiler.query.Adequacy$DeadBranches$DeadBranchProofWords"),
            REACH + "WhyUnsettled",
            List.of("souther.compiler.query.ClaimAnnotations$UnsettledWords"),
            REACH + "Witness", List.of());

    /** Who may make an answer, by nest: which helper of the reading holds a given construction is
     *  the reading's own business, and what matters is that nothing outside it makes one. */
    private static final String THE_READING = "souther.compiler.check.PathReachability";

    /** The answers themselves, whose constructors are as good as a factory to a consumer. */
    private static final List<String> ANSWERS = List.of(
            REACH + "Reachability$Unreachable",
            REACH + "Reachability$Reachable",
            REACH + "Reachability$Unsettled");

    @Test
    void onlyItsOwnWordsAskAPayloadWhatItSays() throws IOException {
        Map<String, List<String>> asked = new LinkedHashMap<>();
        for (Compiled.Site use : uses()) {
            if (WRITES_THE_WORDS_OF.containsKey(use.owner()) && use.member().equals("said")) {
                asked.computeIfAbsent(use.owner(), _ -> new ArrayList<>()).add(use.from());
            }
        }
        WRITES_THE_WORDS_OF.forEach((payload, words) -> assertEquals(words,
                asked.getOrDefault(payload, List.of()).stream().distinct().toList(),
                payload + " is asked what it says by something that is not its words"));
        assertFalse(asked.isEmpty(),
                "no payload is asked what it says at all; this check is reading no calls");
    }

    @Test
    void andOnlyThoseWordsAreWritten() throws IOException {
        Map<String, List<String>> implementors = new LinkedHashMap<>();
        for (ClassModel model : Reactor.classes()) {
            for (var face : model.interfaces()) {
                String name = face.asInternalName().replace('/', '.');
                WRITES_THE_WORDS_OF.forEach((payload, words) -> {
                    if (name.equals(payload + "$Words")) {
                        implementors.computeIfAbsent(payload, _ -> new ArrayList<>())
                                .add(model.thisClass().asInternalName().replace('/', '.'));
                    }
                });
            }
        }
        WRITES_THE_WORDS_OF.forEach((payload, words) -> assertEquals(words,
                implementors.getOrDefault(payload, List.of()),
                payload + "'s words are written somewhere other than by its own renderer"));
        assertFalse(implementors.isEmpty(),
                "no payload's words are written at all; this check is reading no classes");
    }

    @Test
    void andNothingButTheReadingMakesAnAnswer() throws IOException {
        List<String> outside = new ArrayList<>();
        boolean sawAFactory = false;
        boolean sawAConstructor = false;
        for (Compiled.Site use : uses()) {
            // A factory is static; reading a component off an answer already in hand is not one,
            // and is what a policy consumer does when it passes a reason along.
            boolean isFactory = use.owner().startsWith(REACH) && use.isStatic();
            boolean isAnswer = ANSWERS.contains(use.owner()) && use.member().equals("<init>");
            if (!isFactory && !isAnswer) {
                continue;
            }
            sawAFactory |= isFactory;
            sawAConstructor |= isAnswer;
            if (!nestOf(use.from()).equals(THE_READING)) {
                outside.add(use.from() + " -> " + use.owner() + "." + use.member());
            }
        }
        assertTrue(sawAFactory, "no factory was called at all; this check is reading nothing");
        assertTrue(sawAConstructor,
                "no answer's constructor was seen; the check would miss one built directly");
        assertEquals(List.of(), outside, "these make an answer the reading did not make");
    }

    /** The nest a class belongs to: what is written inside the reading is the reading. */
    private static String nestOf(String binaryName) {
        int nested = binaryName.indexOf('$');
        return nested < 0 ? binaryName : binaryName.substring(0, nested);
    }

    /**
     * Every call and construction the built classes hold, the answers' own aside.
     *
     * <p>Read by {@link Compiled}, which is where the vocabulary lives: making a value is a
     * {@code new}, a constructor handle in a bootstrap argument, or nothing visible at the call
     * site at all, and a check that knows about one of those has a way round it. This rule found
     * that out first and the next one copied the module walk instead, so the reading is one place
     * now and both ask it.
     */
    private static List<Compiled.Site> uses() throws IOException {
        // What the answers do among themselves is their own business.
        return Compiled.sites().stream().filter(use -> !use.from().startsWith(REACH)).toList();
    }

}
