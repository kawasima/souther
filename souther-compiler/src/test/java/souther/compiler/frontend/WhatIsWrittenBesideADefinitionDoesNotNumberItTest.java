package souther.compiler.frontend;

import souther.compiler.WhereItSits;
import souther.compiler.ast.Ast;
import souther.compiler.diag.CompileException;
import souther.compiler.diag.SourcePos;
import souther.test.RepositoryLayout;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a definition is read as does not turn on what was written beside it.
 *
 * <p>Every number the builder hands out is an identity or a name, and both are carried from here to
 * the end: a coverage obligation is the construct it was numbered as, a rule is the block it was
 * numbered as, and a binder a lowering minted is the name it was given. Counted over the file, each
 * of those is a function of everything written before it there — so declaring a helper renumbers
 * every construct after it, an importer that cannot name the helper is handed a declaration that
 * says something new, and the work of a whole module runs again for an edit nothing about it can
 * see.
 *
 * <p>So the property is about pairs of sources rather than about any one of them. Write the same
 * model twice, once with a definition ahead of it that numbers something and once with the same
 * definition numbering one thing more, and every definition the two share must be read the same
 * way. The two probes are one line each, so nothing after them moves and no position has to be
 * passed over to compare what is left.
 *
 * <p>Asked of every model this repository carries. The counters are one shape and there are several
 * of them; a test naming the constructs it thought of would answer about those and leave the next
 * one to be found by an author.
 */
class WhatIsWrittenBesideADefinitionDoesNotNumberItTest {

    /**
     * The probes, by what the file they go into may hold.
     *
     * <p>Two pairs and not one, because what a definition may be depends on which kind of file it is
     * written in: an {@code examples for} file holds the values its rows name, which are {@code let}s
     * with no parameters, and a definition with one is refused there. A probe that a file refuses is
     * a source the property cannot be asked of, which is the thing this test says out loud rather
     * than passes over.
     *
     * <p>What each pair moves is what a {@code let} of that shape can move. In a model file the
     * pattern parameter takes a name the lowering mints, the lambda is a block and takes a rule
     * number, and the operation takes a construct number; in an attached file, where the lambda
     * would be lifted into a parameter the file refuses, the operation alone. The counters a
     * {@code match}, a tuple pattern, a field getter and a spread move are not written by either,
     * and the count a row is given belongs to an owner no {@code let} is one of —
     * {@code OneOwnerWrittenTwiceIsOneNumberingTest} is where that one is read.
     */
    private record Probes(String numbersNothing, String numbersOneMore) {

        static Probes forA(Ast.Module module) {
            return module.exampleFileTarget() == null
                    ? new Probes("let aProbeThatNumbersNothing (p) = p",
                            "let aProbeThatNumbersNothing ((a, b)) = ((x) -> x + 0)")
                    : new Probes("let aProbeThatNumbersNothing = 0",
                            "let aProbeThatNumbersNothing = 0 + 0");
        }
    }

    /** What the probe is filed under among what a module declares. */
    private static final String THE_PROBE = "let aProbeThatNumbersNothing";

    /**
     * The sources this asks nothing of, and why: they declare nothing, so there is nothing for a
     * definition written ahead of them to number.
     *
     * <p>Written down rather than passed over. A source dropped where it is met leaves the property
     * holding over whatever was left and says nothing about which sources those were, which is the
     * shape of an answer that shrinks without anybody noticing.
     */
    private static final List<String> DECLARES_NOTHING =
            List.of("souther-compiler/src/main/resources/souther/instant.sou");

    @Test
    void aDefinitionIsReadTheSameWhateverIsWrittenBeforeIt() {
        Map<String, String> corpus = everySourceTheRepositoryCarries();
        assertFalse(corpus.isEmpty(), "a property over no source would hold for any reason");

        List<String> moved = new ArrayList<>();
        List<String> refused = new ArrayList<>();
        List<String> declaringNothing = new ArrayList<>();
        int compared = 0;
        for (Map.Entry<String, String> each : corpus.entrySet()) {
            String source = each.getValue();
            Ast.Module written = readable(source);
            if (written == null) {
                // The language refusing a source as it stands is about the source, and this asks
                // nothing of it. Said rather than passed over.
                refused.add(each.getKey());
                continue;
            }
            if (whereItsDefinitionsBegin(written).isEmpty()) {
                declaringNothing.add(each.getKey());
                continue;
            }
            Probes probes = Probes.forA(written);
            // Not caught. A source the language accepts is one the probe for its kind of file can be
            // written into, so anything thrown from here is this compiler failing — and it is this
            // compiler the property is about. Swallowed, a fault put into what is being tested would
            // take the source out of the population and leave the property green.
            Ast.Module numbersNothing = probed(each.getKey(),
                    withAProbe(written, source, probes.numbersNothing()));
            Ast.Module numbersOneMore = probed(each.getKey(),
                    withAProbe(written, source, probes.numbersOneMore()));
            Map<String, Object> before = whatItDeclares(numbersNothing);
            Map<String, Object> after = whatItDeclares(numbersOneMore);
            for (Map.Entry<String, Object> declared : before.entrySet()) {
                Object beside = after.get(declared.getKey());
                // The probe is what differs between the two, and is the one definition this asks
                // nothing about.
                if (beside == null || declared.getKey().equals(THE_PROBE)) {
                    continue;
                }
                compared++;
                if (!declared.getValue().equals(beside)) {
                    moved.add(numbersNothing.name() + " " + declared.getKey());
                }
            }
        }

        assertEquals(List.of(), refused,
                "these are sources this repository carries and the property was not asked of them,"
                        + " so what it holds is about the rest. A source that cannot be read with a"
                        + " definition written ahead of it belongs here with the reason");
        assertEquals(DECLARES_NOTHING, declaringNothing,
                "a source this asks nothing of is named here with the reason. One that has left the"
                        + " list has definitions now and is asked about; one that has joined it"
                        + " stopped declaring anything, and either is a thing to know");
        assertTrue(compared > 0, "nothing was compared, so nothing was held");
        assertEquals(List.of(), moved,
                "these are read differently for a definition written before them that numbers one"
                        + " thing more, so what they were numbered as is a count over the file"
                        + " rather than within what wrote them");
    }

    /**
     * The control: the probes really do differ by numbers the builder hands out, and not merely by
     * being different definitions.
     *
     * <p>Asked of what they were numbered, not of what they are. Two definitions written differently
     * differ whatever the counters do, so a control comparing them would go on passing after the
     * counters went back to counting the file — which is the regression the property exists to
     * catch. What is asked here is that one probe took numbers and the other took none.
     */
    @Test
    void andTheProbesAreToldApartByWhatTheyWereNumbered() {
        String declaringOne = "module probe exposing ( )\n\nlet beside = 1\n";
        Ast.Module written = parsed(declaringOne);
        Probes probes = Probes.forA(written);
        Ast.FnDef numbersNothing =
                theProbeOf(parsed(withAProbe(written, declaringOne, probes.numbersNothing())));
        Ast.FnDef numbersOneMore =
                theProbeOf(parsed(withAProbe(written, declaringOne, probes.numbersOneMore())));

        assertEquals(List.of(), numbersTaken(numbersNothing),
                "the probe that numbers nothing takes no number, so writing it ahead of a"
                        + " definition moves nothing under any numbering");
        assertNotEquals(List.of(), numbersTaken(numbersOneMore),
                "and the probe beside it takes some, which is what the property is written over");
    }

    /** The probe among what a module declares. */
    private static Ast.FnDef theProbeOf(Ast.Module module) {
        for (Ast.FnDef fn : module.fns()) {
            if (("let " + fn.written().canonical()).equals(THE_PROBE)) {
                return fn;
            }
        }
        throw new IllegalStateException("the probe was not written into the source");
    }

    /** Every number the builder handed out while reading {@code definition}: the origins its forms
     *  carry, and the names its lowering minted. */
    private static List<String> numbersTaken(Ast.FnDef definition) {
        List<String> taken = new ArrayList<>();
        java.util.regex.Matcher minted =
                java.util.regex.Pattern.compile("\\$[a-z]\\d+").matcher(String.valueOf(definition));
        while (minted.find()) {
            taken.add(minted.group());
        }
        for (String said : String.valueOf(definition).split("CoverageOrigin\\[|RuleOrigin\\[")) {
            if (said.startsWith("owner=")) {
                taken.add(said.substring(0, said.indexOf(']') + 1));
            }
        }
        return taken;
    }

    /**
     * {@code source} with {@code probe} written as its first definition.
     *
     * <p>Placed where the source says its definitions begin and not at a line counted from the top:
     * a file may open with anything the grammar lets it open with — a comment, a header written over
     * several lines, imports — and a probe written into the middle of that is a source the parser
     * refuses. Where it goes is read off the parse, and one line is added either way, so nothing
     * moves between the two.
     *
     * <p>Null where the source declares nothing, there being no definition for one written ahead of
     * it to number.
     */
    private static String withAProbe(Ast.Module written, String source, String probe) {
        int firstDefinition = Integer.MAX_VALUE;
        for (SourcePos pos : whereItsDefinitionsBegin(written)) {
            firstDefinition = Math.min(firstDefinition, WhereItSits.in(source, pos).line());
        }
        if (firstDefinition == Integer.MAX_VALUE) {
            return null;
        }
        List<String> lines = new ArrayList<>(List.of(source.split("\n", -1)));
        lines.add(firstDefinition - 1, probe);
        return String.join("\n", lines);
    }

    /** Where each thing the module declares is written. */
    private static List<SourcePos> whereItsDefinitionsBegin(Ast.Module module) {
        List<SourcePos> begins = new ArrayList<>();
        module.defs().forEach(it -> begins.add(it.pos()));
        module.behaviors().forEach(it -> begins.add(it.pos()));
        module.fns().forEach(it -> begins.add(it.pos()));
        module.examples().forEach(it -> begins.add(it.pos()));
        module.fakes().forEach(it -> begins.add(it.pos()));
        return begins;
    }

    /**
     * The module, or null where the language refuses the source as it stands.
     *
     * <p>That one exception and no other. A refusal is what the language says about a source and is
     * a reason to ask this property nothing of it; anything else thrown here is this compiler
     * failing, and catching it would answer "not one of ours" about a fault in the very thing the
     * property is written over.
     */
    private static Ast.Module readable(String source) {
        try {
            return CstFrontend.parse(source, null);
        } catch (CompileException refused) {
            return null;
        }
    }

    /** The module a probe was written into, which the language accepting the source without it
     *  says it can read. */
    private static Ast.Module probed(String source, String withAProbe) {
        try {
            return CstFrontend.parse(withAProbe, null);
        } catch (RuntimeException thrown) {
            throw new AssertionError("the probe could not be written into " + source
                    + ", which the language reads without it", thrown);
        }
    }

    /** The module, for a source written here. */
    private static Ast.Module parsed(String source) {
        return CstFrontend.parse(source, null);
    }

    /** What the module declares, by what it is written as and called — everything but the probe. */
    private static Map<String, Object> whatItDeclares(Ast.Module module) {
        Map<String, Object> declared = new LinkedHashMap<>();
        for (Ast.Def def : module.defs()) {
            declared.put("data " + def.name(), def);
        }
        for (Ast.BehaviorDef behavior : module.behaviors()) {
            declared.put("behavior " + behavior.written().canonical(), behavior);
        }
        for (Ast.FnDef fn : module.fns()) {
            declared.put("let " + fn.written().canonical(), fn);
        }
        // A behavior may be exampled by more than one block and stood in for by more than one, and
        // the blocks after the first are where a numbering carried across them is read. Keyed by the
        // target alone, the later blocks would replace the earlier and the property would be blind
        // to exactly the carrying-on the reading is made per owner to do.
        for (int i = 0; i < module.examples().size(); i++) {
            Ast.Example example = module.examples().get(i);
            declared.put("example " + example.target() + " " + i, example);
        }
        for (int i = 0; i < module.fakes().size(); i++) {
            Ast.Fake fake = module.fakes().get(i);
            declared.put("fake " + fake.target().name() + " " + i, fake);
        }
        return declared;
    }

    /** Every {@code .sou} file the repository carries, by where it is written: the corpora, the
     *  models written to be worked with, and the library the language ships. */
    private static Map<String, String> everySourceTheRepositoryCarries() {
        RepositoryLayout repository = RepositoryLayout.ofWorkingDirectory();
        Path root = repository.root();
        Map<String, String> sources = new LinkedHashMap<>();
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path each : walk.filter(Files::isRegularFile)
                    .filter(it -> it.getFileName().toString().endsWith(".sou"))
                    .filter(it -> !repository.isUnderBuildOutput(it))
                    .sorted().toList()) {
                sources.put(root.relativize(each).toString(), Files.readString(each));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return sources;
    }
}
