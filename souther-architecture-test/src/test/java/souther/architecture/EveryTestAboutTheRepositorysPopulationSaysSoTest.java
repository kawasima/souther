package souther.architecture;

import souther.test.RepositoryLayout;

import org.junit.jupiter.api.Test;

import java.lang.classfile.Annotation;
import java.lang.classfile.AnnotationValue;
import java.lang.classfile.Attributes;
import java.lang.classfile.ClassModel;
import java.lang.classfile.constantpool.ClassEntry;
import java.lang.classfile.constantpool.PoolEntry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every test whose subjects come from this repository says that it is one.
 *
 * <p>A test that sweeps the models this repository carries is asking about the language rather than
 * about a source somebody wrote to ask one question. Answering such a subject is most of what the
 * suite costs, so a plain run leaves them out and the merge into develop asks them. What decides
 * which run a test lands in is the tag it carries.
 *
 * <p><b>So the tag cannot be a thing to remember.</b> A test reaching a corpus without one is left in
 * the run everybody waits on, and nothing about writing it would say so — the class compiles, passes,
 * and is slow somewhere else. The population is read off the compiled tests here instead: every test
 * class that reaches a corpus, by its own constant pool or through another test class that does.
 *
 * <p>The corpora are named below rather than recognised. A corpus is a thing somebody wrote to be
 * swept, and there are few of them; a rule that guessed which classes were corpora would be a second
 * account of the same short list, and the two would disagree the first time one moved.
 *
 * <p><b>What this sees is a test that reaches one of the corpora named below.</b> A test that walks
 * this repository's own sources or its specification some other way — through
 * {@link RepositoryLayout} and a suffix, say — sweeps a population too and is not one of these, so
 * it is asked in every run and nothing here says whether that is right. Naming the layout as a
 * corpus would not close it either: reading class files to answer a question about this compiler
 * goes through the same door, and every check in this package would come back as a population test.
 */
class EveryTestAboutTheRepositorysPopulationSaysSoTest {

    private static final RepositoryLayout REPOSITORY = RepositoryLayout.ofWorkingDirectory();

    private static final CompiledOutputs TESTS = CompiledOutputs.ofEverythingCompiledHere();

    /** What this tag is spelled as where a test carries it. */
    private static final String TAG = "population";

    /**
     * What hands out a population, as binary names.
     *
     * <p>Each reads something the repository carries and hands it out to be swept: the conformance
     * corpora and the models compiled beside them, and the sources the formatter is held to. A test
     * that reaches one of these is asking about all of what it hands out.
     */
    private static final Set<String> CORPORA = Set.of(
            "souther/compiler/conformance/ConformanceCorpus",
            "souther/compiler/conformance/RepositoryModels",
            "souther/compiler/fmt/FormatterCorpus",
            "souther/compiler/fmt/WhatGoesBetweenTwoTokensOnALineTest",
            "souther/bench/Corpus");

    /**
     * The tests that reach a corpus and are asked anyway, with what each of them is.
     *
     * <p>Reaching a corpus is not the whole of what the tag is about. What the tag defers is a
     * question whose subjects are the population: its work grows with what the corpora hold, because
     * it is the same question asked of each of them. These reach a corpus to answer one bounded
     * question instead — a model added to a corpus gives them nothing more to do — and that question
     * belongs in the run a change waits on. Deferred, each would pass on the change that breaks it
     * and fail after the merge, on a branch nobody is waiting to fix.
     *
     * <p>So this is not a list of the cheap ones. What decides it is whether the corpus is being
     * quantified over or drawn from: a check that compiles one model of its own accord is here, and
     * one that reads every model already analysed would not be if it read them all to answer about
     * them all.
     */
    private static final Map<String, String> ASKED_ANYWAY = Map.of(
            "souther/compiler/conformance/TheAnswersAboutEachConformanceCorpusAreTheOnesCheckedInTest",
            "nothing else fails when an answer changes, and what fixes it is regenerating what is"
                    + " checked in",
            "souther/compiler/conformance/AConformanceCorpusReachesEveryConstructTheLanguageDeclaresTest",
            "a construct added to the language is one the corpus does not reach yet, and the change"
                    + " that added it is the one to say so",
            "souther/compiler/report/AMeasurementIsNeverStrongerThanWhatItIsAssembledFromTest",
            "the human report is held to one model the repository carries rather than to what the"
                    + " corpora hold, and a change to what that report says is one the editing run"
                    + " is the place to catch");

    @Test
    void everyTestReachingACorpusCarriesTheTag() {
        Map<String, Set<String>> references = referencesByClass();
        assertFalse(references.isEmpty(),
                "no compiled test was read, so this walked nothing and would hold either way");

        Set<String> reaching = reachingACorpus(references);
        TreeSet<String> untagged = new TreeSet<>();
        for (String each : reaching) {
            if (!each.endsWith("Test")) {
                // A helper a test reaches through. It runs nothing of its own, so no tag decides
                // anything about it.
                continue;
            }
            if (ASKED_ANYWAY.containsKey(each) || tags(each).contains(TAG)) {
                continue;
            }
            untagged.add(each.replace('/', '.'));
        }

        TreeSet<String> tagged = new TreeSet<>();
        for (String each : ASKED_ANYWAY.keySet()) {
            if (tags(each).contains(TAG)) {
                tagged.add(each.replace('/', '.'));
            }
        }
        assertEquals(List.of(), List.copyOf(tagged),
                "a test named above as one asked anyway, carrying the tag that defers it: it is in"
                        + " one list or the other and the tag is what surefire reads");

        assertEquals(List.of(), List.copyOf(untagged),
                "a test whose subjects come from this repository, in the run somebody waits on while"
                        + " editing: it carries @Tag(\"" + TAG + "\") or it stops reaching a corpus");
    }

    /**
     * And something reaches one, which is what says the walk read the pools rather than empty files.
     *
     * <p>Without it a walk that parsed nothing, or one whose corpora had all been renamed, would pass
     * the check above by finding no test to hold to it.
     */
    @Test
    void andSomeTestDoesReachACorpus() {
        Set<String> reaching = reachingACorpus(referencesByClass());
        assertTrue(reaching.stream().anyMatch(each -> each.endsWith("Test")),
                "no test reaches any of " + CORPORA + ", so the names above are stale and this holds"
                        + " for a reason other than the one it states");
    }

    /**
     * The compiled tests this walk is over, by binary name.
     *
     * <p><b>Only the ones a source still writes.</b> Nothing here removes a class file, and the
     * build is not run with {@code clean}, so a test renamed or deleted leaves its old one behind.
     * Read as a test, it would be held to a tag its author cannot add to a source that no longer
     * exists — and if the rename is what added the tag, the check would name a class nobody can
     * find. A class file with no source is what a previous build left, and is passed over.
     *
     * <p><b>And a name can be more than one class.</b> Packages are written under more than one
     * module here — {@code souther.compiler.inputs} holds a fixtures class in two of them — so a
     * name does not settle which class file it is. Kept as one, the second would replace the first
     * and whichever lost would be neither held to the tag nor read for one. All of them are kept
     * instead, and what a name reaches or carries is what any of them does: which one a reference
     * meant is what this cannot say, and asking for the tag where either would want it is the side
     * of that to be wrong on.
     */
    private static Map<String, List<ClassModel>> compiled;

    private static Map<String, List<ClassModel>> compiledTests() {
        if (compiled != null) {
            return compiled;
        }
        Map<String, List<ClassModel>> out = new LinkedHashMap<>();
        for (Path module : REPOSITORY.modules()) {
            for (ClassModel each : TESTS.testClassesOf(module)) {
                String name = each.thisClass().asInternalName();
                if (!Files.isRegularFile(sourceOf(module, name))) {
                    continue;
                }
                out.computeIfAbsent(name, one -> new ArrayList<>()).add(each);
            }
        }
        compiled = out;
        return out;
    }

    /** Where the source of one compiled test would be, its nesting read off the name. */
    private static Path sourceOf(Path module, String internalName) {
        String outer = internalName.contains("$")
                ? internalName.substring(0, internalName.indexOf('$')) : internalName;
        return module.resolve("src").resolve("test").resolve("java").resolve(outer + ".java");
    }

    /** Every compiled test, with the classes each names in its constant pool. */
    private static Map<String, Set<String>> referencesByClass() {
        Map<String, Set<String>> out = new LinkedHashMap<>();
        compiledTests().forEach((name, every) -> {
            Set<String> named = new LinkedHashSet<>();
            for (ClassModel each : every) {
                for (PoolEntry entry : each.constantPool()) {
                    if (entry instanceof ClassEntry it) {
                        named.add(it.asInternalName());
                    }
                }
            }
            out.put(name, named);
        });
        return out;
    }

    /** The classes that name a corpus, and the classes that reach one through them. */
    private static Set<String> reachingACorpus(Map<String, Set<String>> references) {
        Set<String> reaching = new LinkedHashSet<>();
        Deque<String> pending = new ArrayDeque<>(CORPORA);
        while (!pending.isEmpty()) {
            String target = pending.removeFirst();
            for (Map.Entry<String, Set<String>> each : references.entrySet()) {
                if (each.getValue().contains(target) && reaching.add(each.getKey())) {
                    pending.addLast(each.getKey());
                }
            }
        }
        // A corpus that is itself a test class reaches itself and is one of these.
        CORPORA.stream().filter(references::containsKey).forEach(reaching::add);
        return reaching;
    }

    /**
     * The tags one compiled test runs under, which are its own and the ones it is written inside.
     *
     * <p>A tag on a class covers the {@code @Nested} classes in it — that is what decides which run
     * they land in, and it is not written at them. Read off the class alone, a nested test inside a
     * tagged one comes back untagged here while surefire leaves it out, and the author is told to
     * add a tag that is already deciding its run.
     */
    private static Set<String> tags(String internalName) {
        Map<String, List<ClassModel>> tests = compiledTests();
        Set<String> out = new LinkedHashSet<>();
        for (String each = internalName; each != null; each = enclosing(each)) {
            for (ClassModel where : tests.getOrDefault(each, List.of())) {
                out.addAll(tagsOf(where));
            }
        }
        return out;
    }

    /** The class one is written inside, or null where it is written at the top of its file. */
    private static String enclosing(String internalName) {
        int nested = internalName.lastIndexOf('$');
        return nested < 0 ? null : internalName.substring(0, nested);
    }

    private static Set<String> tagsOf(ClassModel model) {
        Set<String> out = new LinkedHashSet<>();
        model.findAttribute(Attributes.runtimeVisibleAnnotations()).ifPresent(annotations -> {
            for (Annotation each : annotations.annotations()) {
                String type = each.className().stringValue();
                if (!"Lorg/junit/jupiter/api/Tag;".equals(type)) {
                    continue;
                }
                each.elements().stream()
                        .filter(element -> "value".equals(element.name().stringValue()))
                        .forEach(element -> {
                            if (element.value() instanceof AnnotationValue.OfString it) {
                                out.add(it.stringValue());
                            }
                        });
            }
        });
        return out;
    }






}
