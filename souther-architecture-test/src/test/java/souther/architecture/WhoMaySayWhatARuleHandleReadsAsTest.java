package souther.architecture;


import org.junit.jupiter.api.Test;

import java.lang.classfile.MethodModel;
import java.lang.classfile.ClassModel;
import java.lang.classfile.constantpool.FieldRefEntry;
import java.lang.classfile.constantpool.LoadableConstantEntry;
import java.lang.classfile.constantpool.MethodHandleEntry;
import java.lang.classfile.constantpool.PoolEntry;
import java.lang.classfile.instruction.InvokeDynamicInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Who may say what a rule handle reads as, and which fields of the document one is written into.
 *
 * <p>The document says how a reader is sent to a rule in five fields, and the schema says of each
 * that it carries such a handle. What holds those two lists together is a comparison between the
 * schema and {@code RuleHandleSurface} — and that comparison is worth what the surface is worth: if
 * a handle can reach a document field without going through one, the enum is a list of the places
 * somebody remembered, which is the thing this whole issue is about one layer up.
 *
 * <p>So it is read off the compiled classes rather than trusted. Two questions, and neither is
 * answerable by looking at the enum: who renders a handle at all, and which fields are written
 * through it. A renderer reached by a method reference renders as surely as one called here, which
 * is the other reason to read the classes rather than the source.
 *
 * <p>What the rows leave open is deliberate: a class may put whatever string it likes under whatever
 * key. What it cannot do is get a rule handle's words without appearing here.
 */
class WhoMaySayWhatARuleHandleReadsAsTest {

    private static final String PROSE = "souther/compiler/publish/RuleHandleProse";

    private static final String SURFACE = "souther/compiler/publish/RuleHandleSurface";

    private static final CompiledOutputs COMPILED = CompiledOutputs.ofWhatThisRepositoryPublishes();

    /**
     * Every class that asks what a handle reads as outside a document, and why it may.
     *
     * <p>Two, and both write the report a person reads. {@code AdequacyReport} says a rule in the
     * lines under a behavior; {@code GeneratedRows} says the same rule beside a row it composed, and
     * says it that way so that a reader meeting the finding in both places meets one sentence.
     *
     * <p>A row added here is a place that turns a handle into words with no field of the schema
     * behind it. That is what a document field written past the surface would look like from here,
     * and it is why this list is short on purpose.
     */
    private static final List<String> SAYING_IT_IN_PROSE = List.of(
            "souther/compiler/report/AdequacyReport"
                    + "#cited(Set, SourceRendering, SourceId, PublishedRuleHandle$WhereARuleIs)",
            "souther/compiler/report/AdequacyReport"
                    + "#declared(StringBuilder, AdequacyReport$ModuleReport, SourceRendering,"
                    + " PublishedRuleHandle$WhereARuleIs)",
            "souther/compiler/report/AdequacyReport#partition lambda taking (StringBuilder,"
                    + " BorderAssessment, PublishedRuleHandle$WhereARuleIs, SourceRendering,"
                    + " SourceId, PointRole, RoleAnswer)",
            "souther/compiler/report/AdequacyReport#partition(StringBuilder,"
                    + " AdequacyReport$BehaviorReport, SourceId, SourceRendering,"
                    + " PublishedRuleHandle$WhereARuleIs)",
            "souther/compiler/report/AdequacyReport"
                    + "#said(Subject, SourceRendering, PublishedRuleHandle$WhereARuleIs)",
            "souther/compiler/report/GeneratedRows"
                    + "#about(Adequacy$Finding, SourceRendering, PublishedRuleHandle$WhereARuleIs)",
            // And the same block saying which rule gave the offer no value, which is the same
            // reader meeting the same rule: told that a search was short of what the rules leave
            // and not told which rule, they have every rule of the position to look at.
            "souther/compiler/report/GeneratedRows"
                    + "#gaveNothing(StringOfferShortfall$NotOffered, TermPath, SourceRendering,"
                    + " PublishedRuleHandle$WhereARuleIs)");

    /**
     * And every class that writes one into the document, which is one.
     *
     * <p>The document is written in one place, so a second row here is a second writer — and two
     * writers of one document are two vocabularies for a consumer to learn.
     */
    private static final List<String> WRITING_IT_INTO_THE_DOCUMENT = List.of(
            "souther/compiler/report/AdequacyReport"
                    + "#about(ObjectNode, PublishedSubject, DocumentSources)",
            "souther/compiler/report/AdequacyReport#findings(DocumentArray, List, DocumentSources,"
                    + " PublishedRuleHandle$WhereARuleIs)",
            "souther/compiler/report/AdequacyReport"
                    + "#obligations(DocumentArray, List, Map, DocumentSources,"
                    + " PublishedRuleHandle$WhereARuleIs)",
            "souther/compiler/report/AdequacyReport#partition lambda taking (DocumentArray,"
                    + " PublishedRuleHandle$WhereARuleIs, DocumentSources, WhereAPartIs,"
                    + " PartitionEvidence$NotRead)",
            "souther/compiler/report/AdequacyReport#partition(ObjectNode, PartitionEvidence,"
                    + " Measure, List, ClaimAnnotations, DocumentSources,"
                    + " PublishedRuleHandle$WhereARuleIs, WhereAPartIs)");

    @Test
    void everyClassThatTurnsARuleHandleIntoWordsIsWrittenDown() {
        assertEquals(SAYING_IT_IN_PROSE, new ArrayList<>(naming(PROSE, "said")),
                "a class here turns a handle into words with nothing in the schema behind it, which"
                        + " is what a document field written past the surface looks like");
    }

    @Test
    void andEveryClassThatWritesOneIntoTheDocumentIsWrittenDown() {
        assertEquals(WRITING_IT_INTO_THE_DOCUMENT, new ArrayList<>(naming(SURFACE, "put")),
                "the document is written in one place, and a handle reaches a consumer through the"
                        + " fields that place names");
    }

    /**
     * And every field the surface declares is one the writer writes.
     *
     * <p>The other direction. A constant nobody names is a field the schema is held to carry a
     * handle in while nothing puts one there — the comparison against the schema would pass, and
     * what it would be comparing is two lists of intentions.
     */
    @Test
    void andEveryFieldTheSurfaceDeclaresIsOneSomethingWrites() {
        assertEquals(surfaceConstants(), used(SURFACE),
                "the fields the surface declares and the fields something writes through");
    }

    /**
     * The walk reads every module's classes.
     *
     * <p>Asked of the modules the repository has and not of what a build happened to leave: a module
     * whose classes are missing is one whose calls this cannot see, and the rows from the rest would
     * match and this would pass while answering about fewer modules than it names.
     */
    @Test
    void andEveryModuleTheRepositoryHoldsWasRead() {
        assertTrue(modulesRead() > 1,
                "the classes this reads are in more than the one module that declares the sentence");
    }

    /**
     * Every method calling {@code member} on {@code owner}, as the class and the method.
     *
     * <p>The method and not the class, because the class is not the boundary. One class writes the
     * report a person reads and the document a consumer keys on, so a class allowed to say a handle
     * in prose is a class that may put those words under any field of the document and add no row
     * here. Which method it happened in is what tells those two apart.
     */
    private static Set<String> naming(String owner, String member) {
        Set<String> found = new TreeSet<>();
        for (Path module : COMPILED.modules()) {
            for (ClassModel each : COMPILED.classesOf(module)) {
                for (MethodModel method : each.methods()) {
                    if (calls(method, owner, member)) {
                        found.add(each.thisClass().asInternalName() + "#" + said(method));
                    }
                }
            }
        }
        return found;
    }

    /**
     * The method somebody wrote, as a name and what it takes.
     *
     * <p>What it takes, because a name is not a method: this writer says a partition in the report a
     * person reads and in the document, and both are called {@code partition}. Told apart by name
     * alone, a handle rendered in prose inside the one that writes the document would arrive here as
     * a row that was already allowed — which is the whole distinction these rows exist to draw.
     *
     * <p>And the method somebody wrote, because a lambda is compiled to a method of its own, named
     * after the one it was written in and numbered within the class. The number moves when a lambda
     * is added anywhere above it, so a row carrying one would go red for edits that have nothing to
     * do with handles.
     */
    private static String said(MethodModel method) {
        String compiled = method.methodName().stringValue();
        String taking = method.methodTypeSymbol().parameterList().stream()
                .map(each -> each.displayName())
                .collect(Collectors.joining(", ", "(", ")"));
        if (compiled.startsWith("lambda$")) {
            // What a lambda takes is what it captured and what it is applied to, which is not what
            // the method around it takes — and is what tells one lambda of that method from
            // another now that the number is gone.
            return compiled.substring("lambda$".length(), compiled.lastIndexOf('$'))
                    + " lambda taking " + taking;
        }
        return compiled + taking;
    }

    /**
     * Whether {@code method}'s own code names {@code member} on {@code owner}.
     *
     * <p>Called, or handed to something that will call it. A method reference compiles to an
     * {@code invokedynamic} whose bootstrap carries the target as a method handle, and nothing in
     * the method's instructions names the target at all — so a walk over calls alone answers no for
     * {@code RuleHandleProse::said}, which renders a handle as surely as calling it. The claim to
     * read both is what makes this a walk over compiled classes rather than over the source.
     */
    private static boolean calls(MethodModel method, String owner, String member) {
        return method.code().map(code -> code.elementStream().anyMatch(element ->
                (element instanceof InvokeInstruction called
                        && owner.equals(called.owner().name().stringValue())
                        && member.equals(called.name().stringValue()))
                || (element instanceof InvokeDynamicInstruction dynamic
                        && handedOver(dynamic, owner, member)))).orElse(false);
    }

    /** Whether the bootstrap of {@code dynamic} hands over {@code member} on {@code owner}. */
    private static boolean handedOver(InvokeDynamicInstruction dynamic, String owner,
                                      String member) {
        for (LoadableConstantEntry argument
                : dynamic.invokedynamic().bootstrap().arguments()) {
            if (argument instanceof MethodHandleEntry handle
                    && owner.equals(handle.reference().owner().name().stringValue())
                    && member.equals(handle.reference().name().stringValue())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Every constant of {@code owner} that some other class reads.
     *
     * <p>The owner itself is passed over. An enum's own class file names every constant it declares,
     * because that is where they are made — counted, this would answer that every field the surface
     * declares is written and go on answering it with nothing writing any of them.
     */
    private static Set<String> used(String owner) {
        Set<String> found = new TreeSet<>();
        for (Path module : COMPILED.modules()) {
            for (ClassModel each : COMPILED.classesOf(module)) {
                if (each.thisClass().asInternalName().equals(owner)) {
                    continue;
                }
                for (PoolEntry entry : each.constantPool()) {
                    if (entry instanceof FieldRefEntry read
                            && owner.equals(read.owner().name().stringValue())
                            && surfaceConstants().contains(read.name().stringValue())) {
                        found.add(read.name().stringValue());
                    }
                }
            }
        }
        return found;
    }

    /**
     * The fields the surface declares, read off the enum rather than written here.
     *
     * <p>Loaded by name, because this module compiles against the compiler and a list of constants
     * copied into a test is one more list to keep — which is the shape being checked.
     */
    private static Set<String> surfaceConstants() {
        Set<String> out = new TreeSet<>();
        try {
            for (Object each : Class.forName(SURFACE.replace('/', '.')).getEnumConstants()) {
                out.add(((Enum<?>) each).name());
            }
        } catch (ClassNotFoundException e) {
            throw new AssertionError("the surface this is about is on the classpath", e);
        }
        return out;
    }

    private static int modulesRead() {
        int read = 0;
        for (Path module : COMPILED.modules()) {
            if (!COMPILED.classesOf(module).isEmpty()) {
                read++;
            }
        }
        return read;
    }












}
