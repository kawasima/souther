package souther.compiler;

import souther.compiler.diag.Primary;

import souther.compiler.codegen.Backend;
import souther.compiler.diag.Located;
import souther.compiler.meta.ModulePath;
import souther.compiler.meta.ModuleReadback;
import souther.compiler.meta.PublishedClasses;
import souther.compiler.meta.ReadableModule;
import souther.compiler.meta.Readback;
import souther.compiler.query.Compilation;
import souther.compiler.jvm.ClassFileImage;
import souther.compiler.source.SourceId;

import org.junit.jupiter.api.Test;

import java.lang.classfile.Annotation;
import java.lang.classfile.Attributes;
import java.lang.classfile.ClassFile;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A module the class path carries and this compiler will not read is one thing the author is told,
 * said at the {@code import} line that reaches it, and the rest of the compilation still answers.
 *
 * <p>Every way of failing used to be a raise, out of a walk over the whole path, from inside a text
 * this compiler assembled and nobody holds. What the author saw was a line and a column of a file
 * they do not have, for a module they did not write — and every other file's diagnostics went with
 * it, because a raise leaves the question that asked for the module rather than answering it.
 *
 * <p>The artifacts here are written by hand. A jar this compiler agrees with could not carry any of
 * these: the declaring project's own build would have refused the line. That is the case the
 * boundary revision exists for, and the case the compiler should be at its clearest.
 */
class AnArtifactThisCompilerCannotReadIsSaidWhereItWasReachedTest {

    /** A path whose declarations are written here rather than read off class files. */
    private record Fabricated(Map<String, PublishedClasses.Declarations> published)
            implements ModulePath {
        @Override
        public byte[] bytes(String binaryName) {
            return null;
        }

        @Override
        public PublishedClasses declarations() {
            return binaryName -> PublishedClasses.carrying(published.get(binaryName));
        }
    }

    private static PublishedClasses.Declarations moduleClass(int compat, String module,
                                                             List<String> imports,
                                                             List<String> types,
                                                             List<String> helpers) {
        return new PublishedClasses.Declarations(new PublishedClasses.SoutherModuleView(
                compat, "0.0.1-other",
                "module " + module + " exposing ( " + String.join(", ", types) + " )",
                imports, types, List.of(), helpers), null, null, null);
    }

    /** Bytes that begin like a class file and end before one does: the parse itself refuses them. */
    private static final byte[] NOT_A_CLASS_FILE =
            {(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE, 0, 0};

    /**
     * {@code original} with one byte changed so that the class-file reader accepts the bytes and
     * then refuses one of the values read off them.
     *
     * <p>Parsing a class file does not read it. The model is lazy, so a constant an annotation names
     * is checked when the annotation is asked for its class or its members, and a reader that caught
     * only around the parse answers the first kind of malformed file and lets the second escape.
     *
     * <p>Searched for rather than written down, because which byte does it is a fact about the
     * bytes this compiler emits today. Failing to find one is this test having nothing to say, and
     * it says so.
     */
    private static byte[] readableUntilAsked(byte[] original) {
        for (int i = 8; i < original.length; i++) {
            byte[] bytes = original.clone();
            bytes[i] = (byte) (bytes[i] ^ 0xFF);
            boolean parsed = false;
            try {
                List<Annotation> read = ClassFile.of().parse(bytes)
                        .findAttribute(Attributes.runtimeInvisibleAnnotations())
                        .map(a -> List.copyOf(a.annotations())).orElse(List.of());
                parsed = true;
                for (Annotation a : read) {
                    a.className().stringValue();
                    for (java.lang.classfile.AnnotationElement e : a.elements()) {
                        e.name().stringValue();
                        // The values too, and not only the names. What escaped the guard before
                        // this was a read that happens later than the one a shallower sweep makes,
                        // so a sweep that stops at the names measures the half that was never the
                        // problem.
                        drain(e.value());
                    }
                }
            } catch (IllegalArgumentException _) {
                if (parsed) {
                    return bytes;
                }
            }
        }
        throw new IllegalStateException(
                "no corruption of these bytes parses and then refuses an accessor");
    }

    /** Every value under {@code value}, so that a corruption anywhere the reader looks is found. */
    private static void drain(java.lang.classfile.AnnotationValue value) {
        switch (value) {
            case java.lang.classfile.AnnotationValue.OfString s -> s.stringValue();
            case java.lang.classfile.AnnotationValue.OfArray a -> a.values().forEach(
                    AnArtifactThisCompilerCannotReadIsSaidWhereItWasReachedTest::drain);
            case java.lang.classfile.AnnotationValue.OfAnnotation a ->
                    a.annotation().elements().forEach(e -> drain(e.value()));
            default -> value.tag();
        }
    }

    private static PublishedClasses.Declarations dataClass(String declaration) {
        return new PublishedClasses.Declarations(null, declaration, null, null);
    }

    /** The importing project. Its second mistake is its own, and is how we see whether the rest of
     *  the file was still read. */
    private static final String APP = """
            module app.uses
            import lib.pub ( Held )

            data Page = { held: Held }
            data Bad = { x: NoSuchTypeAnywhere }
            """;

    private static Set<String> saidAboutTheSource(ModulePath path) {
        Compilation compilation = Compilation.ofSources(List.of(APP), path);
        Set<String> codes = new LinkedHashSet<>();
        for (Located said : compilation.diagnostics().get(new SourceId("0"))) {
            codes.add(said.diagnostic().code());
        }
        return codes;
    }

    /** Where the report about the artifact is said, in the file this compile does hold. */
    private static void bothAreSaidOnTheSource(ModulePath path) {
        Set<String> codes = saidAboutTheSource(path);
        assertTrue(codes.contains("E1509"), "the artifact is refused: " + codes);
        assertTrue(codes.contains("E1023"),
                "and the mistake in the author's own file is still said: " + codes);
    }

    /**
     * The caret is the `import` line that reaches the module, and not a place inside the artifact.
     *
     * <p>Which file the report is filed under is what the rest of these measure, and it is the
     * weaker half: a report anchored nowhere still lands somewhere once a compile decides where to
     * put it. This is the other half — the line under the caret is the one the author wrote, in the
     * source they hold.
     */
    @Test
    void itIsSaidAtTheImportThatReachesTheModule() {
        Compilation compilation = Compilation.ofSources(List.of("""
                module app.uses




                import lib.pub ( Held )

                data Page = { held: Held }
                """), new Fabricated(Map.of(
                        "lib.pub.$Module", moduleClass(Backend.BOUNDARY_VERSION + 1, "lib.pub",
                                List.of(), List.of("Held"), List.of()),
                        "lib.pub.Held", dataClass("data Held = String"))));

        Located said = compilation.diagnostics().get(new SourceId("0")).stream()
                .filter(d -> d.diagnostic().code().equals("E1509")).findFirst().orElseThrow();

        assertEquals(6, WhereItSits.in(APP, ((Primary.InSource) said.diagnostic().primary()).place().region()).start().line(), "the import line naming the module");
        assertEquals(1, WhereItSits.in(APP, ((Primary.InSource) said.diagnostic().primary()).place().region()).start().column());
    }

    /** The boundary revision does not agree. */
    @Test
    void oneBuiltByACompilerThatDisagrees() {
        bothAreSaidOnTheSource(new Fabricated(Map.of(
                "lib.pub.$Module", moduleClass(Backend.BOUNDARY_VERSION + 1, "lib.pub",
                        List.of(), List.of("Held"), List.of()),
                "lib.pub.Held", dataClass("data Held = String"))));
    }

    /** It names a declaration whose class the path does not carry. */
    @Test
    void oneWhoseDeclarationClassIsMissing() {
        bothAreSaidOnTheSource(new Fabricated(Map.of(
                "lib.pub.$Module", moduleClass(Backend.BOUNDARY_VERSION, "lib.pub",
                        List.of(), List.of("Held"), List.of()))));
    }

    /** What it published is not source this compiler parses. */
    @Test
    void oneWhosePublishedTextDoesNotParse() {
        bothAreSaidOnTheSource(new Fabricated(Map.of(
                "lib.pub.$Module", moduleClass(Backend.BOUNDARY_VERSION, "lib.pub",
                        List.of(), List.of("Held"), List.of()),
                "lib.pub.Held", dataClass("data Held = { ???"))));
    }

    /** Its import line names something this compiler's library does not publish. The helper is what
     *  makes the import needed, so it is not dropped before the check reads it. */
    @Test
    void oneWhoseImportLineCannotBeRead() {
        bothAreSaidOnTheSource(new Fabricated(Map.of(
                "lib.pub.$Module", moduleClass(Backend.BOUNDARY_VERSION, "lib.pub",
                        List.of("import List ( noSuchOperation )"), List.of("Held"),
                        List.of("let helper (xs) = noSuchOperation(xs)")),
                "lib.pub.Held", dataClass("data Held = String"))));
    }

    /**
     * An import line the published surface does not need is not one this compiler reads, so it is
     * never one it cannot read.
     *
     * <p>A module's bodies do not cross, and neither do the imports only they used — otherwise every
     * importing project would have to put a module on its path to read declarations that never
     * mention it. So the lines are dropped before the check reads them, and an artifact carrying a
     * mistaken one that nothing published refers to is read like any other.
     *
     * <p>Written down because the rule is easy to lose. Reading the import lines before dropping the
     * unneeded ones would refuse this artifact, and would be the same change that puts the dropped
     * import back on every importer's path.
     */
    @Test
    void oneWhoseUnreadableImportNoDeclarationNeedsIsReadLikeAnyOther() {
        Set<String> codes = saidAboutTheSource(new Fabricated(Map.of(
                "lib.pub.$Module", moduleClass(Backend.BOUNDARY_VERSION, "lib.pub",
                        List.of("import List ( noSuchOperation )"), List.of("Held"), List.of()),
                "lib.pub.Held", dataClass("data Held = String"))));

        assertEquals(Set.of("E1023"), codes,
                "the author's own mistake, and nothing about a line nothing published names");
    }

    /**
     * What it declares is not a set of declarations one module may have.
     *
     * <p>The declarations came back as source, so they were indexed like source — and a name a
     * published module declared twice was reported to the author of the project importing it. What
     * they saw was {@code E1011}, "data `Held` is already defined", under the caret of their own
     * {@code import} line: a rule about a file they do not have, quoted at a line of theirs that
     * does not break it. Reading an artifact is one question with one answer, and this is one of the
     * ways the answer is no.
     */
    @Test
    void oneThatDeclaresANameTwice() {
        Fabricated path = new Fabricated(Map.of(
                "lib.pub.$Module", new PublishedClasses.Declarations(
                        new PublishedClasses.SoutherModuleView(Backend.BOUNDARY_VERSION,
                                "0.0.1-other", "module lib.pub exposing ( Held )",
                                List.of(), List.of("Held", "Twice"), List.of(), List.of()),
                        null, null, null),
                "lib.pub.Held", dataClass("data Held = String"),
                "lib.pub.Twice", dataClass("data Held = Int")));

        bothAreSaidOnTheSource(path);
        assertFalse(saidAboutTheSource(path).contains("E1011"),
                "and not as a rule about declarations, said to somebody who declared none of them");
    }

    /**
     * And a declaration named after a built-in {@code Option} case, which is the other rule the
     * indexing has.
     *
     * <p>Here because the two rules are two arms of what crosses, and an arm nothing reaches is an
     * arm nothing holds to anything. A module that declares {@code Some} was published under rules
     * that allowed it (ADR-0035 is this compiler's), which is exactly the case an artifact carries
     * and a source of this compile cannot.
     */
    @Test
    void oneThatDeclaresABuiltInOptionCase() {
        bothAreSaidOnTheSource(new Fabricated(Map.of(
                "lib.pub.$Module", new PublishedClasses.Declarations(
                        new PublishedClasses.SoutherModuleView(Backend.BOUNDARY_VERSION,
                                "0.0.1-other", "module lib.pub exposing ( Held )",
                                List.of(), List.of("Held", "Some"), List.of(), List.of()),
                        null, null, null),
                "lib.pub.Held", dataClass("data Held = String"),
                "lib.pub.Some", dataClass("data Some = String"))));
    }

    /**
     * One of its classes carries metadata this compiler cannot read.
     *
     * <p>The one failure found before anything about Souther has been read, and the one that used to
     * escape from further out than any of the others: reading a class raises, and reading a module
     * begins by asking for the declarations class. Told as an absence instead, the author would be
     * shown "no such module" for a dependency their build file does name.
     */
    @Test
    void oneWhoseMetadataCannotBeReadAtAll() {
        bothAreSaidOnTheSource(
                binaryName -> binaryName.equals("lib.pub.$Module") ? NOT_A_CLASS_FILE : null);
    }

    /**
     * And a declaration's class, not only the one the reading starts at.
     *
     * <p>The reading asks for several classes: the one the declarations are stamped on, and one per
     * type and behavior it says it publishes. Answering the first as a value and leaving the rest to
     * raise moves the defect one class along rather than closing it — the jar whose {@code $Module}
     * reads fine and whose {@code Held} does not is the same artifact, and the author has the same
     * thing to do about it.
     */
    @Test
    void oneWhoseDeclarationsClassCarriesMetadataThatCannotBeRead() {
        Map<String, ClassFileImage> built = Compiler.compile("""
                module lib.pub exposing ( Held )
                data Held = String
                """);

        ModulePath path = ModulePath.of(built);
        bothAreSaidOnTheSource(binaryName -> binaryName.equals("lib.pub.Held")
                ? NOT_A_CLASS_FILE : path.bytes(binaryName));
    }

    /**
     * The class file parses, and reading a value off it does not.
     *
     * <p>The lazy half of the class-file model, and the half a catch around the parse alone lets
     * through. Of 470 single-byte corruptions of a real module's declarations class, 202 refuse the
     * parse and 97 more parse and then refuse an accessor — so answering only the first kind leaves
     * a third of the malformed artifacts ending the compilation.
     */
    @Test
    void oneWhoseMetadataIsRefusedAfterItsClassFileParses() {
        Map<String, ClassFileImage> built = Compiler.compile("""
                module lib.pub exposing ( Held )
                data Held = String
                """);
        byte[] lazily = readableUntilAsked(built.get("lib.pub.$Module").bytes());

        ModulePath path = ModulePath.of(built);
        bothAreSaidOnTheSource(binaryName -> binaryName.equals("lib.pub.$Module")
                ? lazily : path.bytes(binaryName));
    }

    /**
     * A module compiled here is shadowed by one on the path this compiler cannot read.
     *
     * <p>Whether the path has the name does not depend on whether what it has can be read — two
     * modules under one name are two answers to what that name means however either was built. The
     * presence query folded "cannot read it" into "there is none", which is the collapse this whole
     * reading is written to keep apart, at the one question that was meant not to depend on the
     * reading at all.
     */
    @Test
    void aModuleCompiledHereIsShadowedByOneThisCompilerCannotRead() {
        Compilation compilation = Compilation.ofSources(List.of("""
                module app.uses

                data Page = { n: Int }
                """), binaryName -> binaryName.equals("app.uses.$Module") ? NOT_A_CLASS_FILE : null);
        Set<String> codes = new LinkedHashSet<>();
        for (Located said : compilation.diagnostics().get(new SourceId("0"))) {
            codes.add(said.diagnostic().code());
        }

        assertTrue(codes.contains("E1503"),
                "the path has the name, whether or not what it has can be read: " + codes);
    }

    /**
     * A reading answers about the module it was asked for.
     *
     * <p>The class is found by the name the caller asked about and the module is named by the header
     * that class carries. Nothing held the two together, so an artifact whose header names something
     * else was filed under a name that is not its own, with every question about it answered from
     * the wrong declarations and no report anywhere saying so.
     */
    @Test
    void oneWhoseHeaderNamesAnotherModule() {
        bothAreSaidOnTheSource(new Fabricated(Map.of(
                "lib.pub.$Module", new PublishedClasses.Declarations(
                        new PublishedClasses.SoutherModuleView(Backend.BOUNDARY_VERSION,
                                "0.0.1-other", "module lib.other exposing ( Held )",
                                List.of(), List.of("Held"), List.of(), List.of()),
                        null, null, null),
                "lib.pub.Held", dataClass("data Held = String"))));
    }

    /**
     * The name a module took and what its artifact carries are two questions, and an artifact can be
     * wrong about both.
     *
     * <p>Asked in sequence, whichever failed first decided what the author heard, and fixing that one
     * brought the other out. They have different things to do about them — rebuild the dependency,
     * and rename the module — so telling one at a time is telling half of it.
     */
    @Test
    void oneThatTookAReservedNameAndCannotBeReadEitherIsBothThings() {
        Set<String> codes = new LinkedHashSet<>();
        Compilation compilation = Compilation.ofSources(List.of("""
                module app.uses
                import souther.taken ( Held )

                data Page = { held: Held }
                """), new Fabricated(Map.of(
                        "souther.taken.$Module", moduleClass(Backend.BOUNDARY_VERSION + 1,
                                "souther.taken", List.of(), List.of("Held"), List.of()),
                        "souther.taken.Held", dataClass("data Held = String"))));
        for (Located said : compilation.diagnostics().get(new SourceId("0"))) {
            codes.add(said.diagnostic().code());
        }

        assertTrue(codes.contains("E1502"), "the name is not one a module may take: " + codes);
        assertTrue(codes.contains("E1509"), "and what it carries cannot be read: " + codes);
    }

    /**
     * The negative control, and the whole of what {@code Readback.Failure} being a closed type is
     * for: a failure this compiler does not know the shape of stays a fault.
     *
     * <p>Converted, a defect in the compiler would reach the author as a diagnostic about their
     * dependency — an artifact reported as unreadable on the strength of a bug in the code reading
     * it. Which is what a catch around the reading, wide enough to hold every way of failing, does.
     */
    @Test
    void aFaultInTheReadingIsNotAnArtifactThisCompilerCannotRead() {
        PublishedClasses broken = _ -> {
            throw new IllegalStateException("a defect in the reader");
        };

        assertThrows(IllegalStateException.class, () -> ModuleReadback.read("lib.pub", broken, DefaultStdlib.get().names()),
                "a fault is not a statement about the artifact");
    }

    /**
     * A module compiled here that also sits on the path is two answers to one name, whether or not
     * this compiler can read the one on the path.
     *
     * <p>The question is whether the path has the name. It was asked by reading the module and
     * looking at what came out, which put every way an artifact can fail into the failure domain of
     * a question whose whole answer is yes or no — so a stale jar of a module this project also
     * compiles ended the compilation instead of being the shadowing it is.
     */
    @Test
    void aModuleCompiledHereIsShadowedByAnUnreadableArtifactOfTheSameName() {
        Set<String> codes = new LinkedHashSet<>();
        Compilation compilation = Compilation.ofSources(List.of("""
                module app.uses

                data Page = { n: Int }
                """), new Fabricated(Map.of(
                        "app.uses.$Module", moduleClass(Backend.BOUNDARY_VERSION + 1, "app.uses",
                                List.of(), List.of("Page"), List.of()),
                        "app.uses.Page", dataClass("data Page = String"))));
        for (Located said : compilation.diagnostics().get(new SourceId("0"))) {
            codes.add(said.diagnostic().code());
        }

        assertTrue(codes.contains("E1503"),
                "one name with two answers, said as the shadowing it is: " + codes);
    }

    /**
     * And it is answered without reading the artifact at all.
     *
     * <p>Measured by what is asked rather than by what comes out, because both a reading and a
     * presence check answer this one the same way — the difference is the range of failures each
     * carries. These classes answer for the declarations class and throw for anything past it, so a
     * question that reads reaches the throw and a question that asks about presence does not.
     */
    @Test
    void whetherThePathHasTheNameIsAnsweredWithoutReadingWhatItCarries() {
        PublishedClasses.Declarations onlyTheModuleClass = moduleClass(
                Backend.BOUNDARY_VERSION, "app.uses", List.of(), List.of("Page"), List.of());
        ModulePath path = new ModulePath() {
            @Override
            public byte[] bytes(String binaryName) {
                return null;
            }

            @Override
            public PublishedClasses declarations() {
                return binaryName -> {
                    if (binaryName.equals("app.uses.$Module")) {
                        return PublishedClasses.carrying(onlyTheModuleClass);
                    }
                    throw new IllegalStateException("read past the name: " + binaryName);
                };
            }
        };

        Compilation compilation = Compilation.ofSources(List.of("""
                module app.uses

                data Page = { n: Int }
                """), path);
        Set<String> codes = new LinkedHashSet<>();
        for (Located said : compilation.diagnostics().get(new SourceId("0"))) {
            codes.add(said.diagnostic().code());
        }

        assertTrue(codes.contains("E1503"), codes::toString);
    }

    /** Reading one that is fine still answers a module, with its import lines read. */
    @Test
    void oneThisCompilerCanReadComesBackReady() {
        Map<String, ClassFileImage> built = Compiler.compile("""
                module lib.ok exposing ( Held )
                data Held = String
                """);
        var readback = ModuleReadback.read("lib.ok",
                ModulePath.of(built).declarations(), DefaultStdlib.get().names());

        assertEquals("lib.ok",
                assertInstanceOf(ReadableModule.class,
                        assertInstanceOf(Readback.Ready.class, readback).value())
                        .module().name());
    }
}
