package souther.architecture;

import souther.test.RepositoryLayout;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.classfile.Attributes;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.FieldModel;
import java.lang.classfile.Signature;
import java.lang.classfile.attribute.RecordAttribute;
import java.lang.classfile.attribute.RecordComponentInfo;
import java.lang.classfile.attribute.SignatureAttribute;
import java.lang.reflect.AccessFlag;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Nothing this compiler holds has two ways to the rule it is about.
 *
 * <p>Which rule of the model a piece of evidence is about, and how a reader is sent to that rule,
 * are two questions about one rule. Held as two things built apart, they can be built about two
 * rules — and nothing is wrong with such a value until a document writes both, where the identity it
 * files an entry under and the sentence it writes beside it name different rules. That is the shape
 * a rule with no name being called {@code comparison} whatever kind of rule it was turned out to
 * have, and the word was not the whole of it: the word was recoverable and the arrangement that lost
 * it was not.
 *
 * <p>So a handle carries the rule it is a handle for, and this is the rule that says nothing carries
 * a rule beside one. What a state may hold is the rule, or a handle, or a rule and the place a
 * reader reached it at — never a rule and a handle that were not made from each other.
 *
 * <p><b>The whole component graph and not the fields spelled on one class.</b> A reading of a
 * comparison held the rule beside a {@code Read} whose own field was the handle, which no rule about
 * one class's own components would see. So the walk goes through everything a state carries, and a
 * handle is one unit: reaching a rule <em>through</em> a handle is the one way there is, and the
 * handle's own component is not a second one.
 *
 * <p>What this does not hold is that two such values cannot be made and passed as arguments. A
 * method taking a rule and a handle beside it can still be written; what is closed is keeping them,
 * which is where such a pair survives long enough for two readers to disagree about it.
 */
class OneWayFromAStateToTheRuleItIsAboutTest {

    private static final RepositoryLayout REPOSITORY = RepositoryLayout.ofWorkingDirectory();

    private static final String RULE = "souther/compiler/check/RuleRef";

    private static final String HANDLE = "souther/compiler/check/RuleCitation";

    /**
     * A state carrying a rule and a handle beside it, which is what this is about.
     *
     * <p>Written here so that the walk is read for what it finds and not only for what it fails to
     * find. A rule over a population everything satisfies passes on the day the population is empty
     * and on the day the walk stops working, and neither is the day the model changed.
     */
    record TwoWaysToOneRule(souther.compiler.check.RuleRef rule,
                            souther.compiler.check.RuleCitation cited) { }

    /** One way, which is the shape everything is held to. */
    record OneWayToOneRule(souther.compiler.check.RuleCitation cited) { }

    /**
     * The same pair, with the rule reached through a type variable.
     *
     * <p>What a variable is instantiated with is settled where the state is built, and this walk is
     * not asking that. It is asking whether the state can hold a rule of its own, and a variable
     * bounded by one says statically that it can — so a walk that passed over it would let the pair
     * through under the one spelling nobody would think to look at.
     */
    record TwoWaysThroughAVariable<R extends souther.compiler.check.RuleRef>(
            R rule, souther.compiler.check.RuleCitation cited) { }

    /** A state that holds a rule, for the pair below to inherit. */
    static class HoldsARule {
        souther.compiler.check.RuleRef rule;
    }

    /**
     * And the same pair, with one half inherited.
     *
     * <p>What a state carries is what an instance of it holds, which is its own fields and the ones
     * above it. Read off the class's own declarations, a pair split over a hierarchy is two states
     * with one way each and one instance with two.
     */
    static class TwoWaysThroughASuperclass extends HoldsARule {
        souther.compiler.check.RuleCitation cited;
    }

    @Test
    void nothingThisRepositoryPublishesHoldsARuleAndAHandleBesideIt() {
        Carried carried = Carried.ofWhatThisRepositoryPublishes();
        Set<String> holdingTwo = new TreeSet<>();
        for (String each : carried.everyClass()) {
            if (carried.isAboutOneRule(each) && carried.waysToARuleFrom(each) > 1) {
                holdingTwo.add(each.replace('/', '.').replace('$', '.'));
            }
        }

        assertEquals(Set.of(), holdingTwo,
                "a state with two ways to the rule it is about can be built about two rules, and"
                        + " what is written from it then files an entry under one and describes"
                        + " the other");
    }

    /**
     * And the walk finds one that is there.
     *
     * <p>Both halves, because a walk that counted nothing would satisfy the rule above: the pair
     * this refuses is reported, and the one shape that is allowed is not.
     */
    @Test
    void theWalkFindsAStateThatHoldsTwoAndPassesOneThatHoldsOne() {
        Carried carried = Carried.ofEverythingCompiledHere();

        assertEquals(2, carried.waysToARuleFrom(fixture("TwoWaysToOneRule")),
                "a rule and a handle beside it are two ways to one rule");
        assertEquals(2, carried.waysToARuleFrom(fixture("TwoWaysThroughAVariable")),
                "a variable bounded by a rule is a way to one, whatever it is instantiated with");
        assertEquals(2, carried.waysToARuleFrom(fixture("TwoWaysThroughASuperclass")),
                "and what a state carries is what an instance of it holds, the fields above it"
                        + " among them");
        assertEquals(1, carried.waysToARuleFrom(fixture("OneWayToOneRule")),
                "and a handle alone is one, since the rule it carries is not a second way");
        assertEquals(1, carried.waysToARuleFrom(fixture("HoldsARule")),
                "and the half that holds only the rule is one way, so the pair above is the"
                        + " hierarchy's and not either half's");
    }

    /**
     * And the population is not empty: the states this is about are in it.
     *
     * <p>Named rather than counted, because what makes this rule worth running is that the readings
     * of a rule are among what it walks. A count would be satisfied by any classes at all.
     */
    @Test
    void theReadingsOfARuleAreAmongWhatWasWalked() {
        Carried carried = Carried.ofWhatThisRepositoryPublishes();

        for (String each : List.of("souther/compiler/partition/PredicateOrigin",
                "souther/compiler/partition/LineOrigin$ComparisonOrigin",
                "souther/compiler/inputs/RuleWithoutALine",
                "souther/compiler/inputs/PlacementSeed")) {
            assertTrue(carried.everyClass().contains(each),
                    () -> "a reading of a rule this is about was not walked: " + each);
            assertEquals(1, carried.waysToARuleFrom(each),
                    () -> "and it reaches the rule it is about, by one way: " + each);
        }
    }

    /** The name the compiler gives a record declared in this test. */
    private static String fixture(String name) {
        return "souther/architecture/OneWayFromAStateToTheRuleItIsAboutTest$" + name;
    }

    /**
     * What each class this repository built carries, and what those reach.
     *
     * <p>Read off the class files rather than off the sources, because what a state holds is what
     * was compiled: a record's components and the instance fields of everything else. What a class
     * mentions in a method body is not among them — a pair made and handed on is not a pair anybody
     * keeps.
     */
    private static final class Carried {

        private final Map<String, ClassModel> classes;

        /** What each class reaches, per how much of what it carries the walk was allowed
         *  through. */
        private final Map<String, Boolean> reaches = new HashMap<>();

        /** The classes this walk is inside of, so that a type holding itself is finite. A class part
         *  way through its own reading reaches nothing new by being asked again. */
        private final Set<String> underway = new LinkedHashSet<>();

        private Carried(Map<String, ClassModel> classes) {
            this.classes = classes;
        }

        static Carried ofWhatThisRepositoryPublishes() {
            return new Carried(read(List.of("classes")));
        }

        static Carried ofEverythingCompiledHere() {
            return new Carried(read(List.of("classes", "test-classes")));
        }

        Set<String> everyClass() {
            return classes.keySet();
        }

        /**
         * How many of {@code owner}'s components reach the rule it is about.
         *
         * <p>Counted over the components and not over the paths below them, because a class holding
         * two ways further down is a class this reports on its own. What is asked here is whether
         * this one holds a second answer beside the one it already has.
         */
        int waysToARuleFrom(String owner) {
            int ways = 0;
            for (Carries each : carriedBy(modelOf(owner))) {
                if (reachesARule(each, Through.ANYTHING)) {
                    ways++;
                }
            }
            return ways;
        }

        /**
         * Whether {@code owner} is a state about one rule of the model.
         *
         * <p>Which is what makes the count above mean anything. A reading of a comparison is about
         * the comparison it read; a table of what each of a declaration's clauses raised is about
         * as many rules as the declaration has clauses, and two entries in it that name one rule
         * are one rule twice by design. Held to the same count, every table of rules would be
         * refused for being a table.
         *
         * <p>Told apart by whether the rule is reached without going through a collection, which is
         * what "one" and "many" are spelled as here. A state that keeps a rule keeps it; a state
         * that keeps rules keeps a collection of them, and what is inside carries its own handle.
         */
        boolean isAboutOneRule(String owner) {
            for (Carries each : carriedBy(modelOf(owner))) {
                if (reachesARule(each, Through.ONE_VALUE_AT_A_TIME)) {
                    return true;
                }
            }
            return false;
        }

        private ClassModel modelOf(String owner) {
            ClassModel model = classes.get(owner);
            if (model == null) {
                throw new AssertionError("this repository built no " + owner
                        + ", so what it carries is a question this cannot answer");
            }
            return model;
        }

        /**
         * What one class keeps: a record's components, or the instance fields of anything else,
         * with the state it inherits among them.
         *
         * <p>What a state carries is what an instance of it holds. Read off the class's own
         * declarations alone, a pair split over a hierarchy is two classes with one way each while
         * every instance of the lower one has both.
         *
         * <p>Each carried thing keeps the class that declared it beside it, because a signature is
         * read in the frame of whoever wrote it: a variable named in a superclass's field is that
         * superclass's, and looked up in the wrong frame it is a name with no bound.
         */
        private List<Carries> carriedBy(ClassModel model) {
            List<Carries> out = new ArrayList<>();
            for (ClassModel each = model; each != null; each = superclassOf(each)) {
                Optional<RecordAttribute> record = each.findAttribute(Attributes.record());
                if (record.isPresent()) {
                    for (RecordComponentInfo component : record.get().components()) {
                        out.add(new Carries(WhatASignatureReaches.componentSignature(component),
                                each));
                    }
                    continue;
                }
                for (FieldModel field : each.fields()) {
                    if (field.flags().has(AccessFlag.STATIC)) {
                        continue;
                    }
                    ClassModel owner = each;
                    out.add(new Carries(field.findAttribute(Attributes.signature())
                            .map(SignatureAttribute::asTypeSignature)
                            .orElseGet(() -> Signature.of(field.fieldTypeSymbol())), owner));
                }
            }
            return out;
        }

        /** The class above this one, where this repository built it. A class whose parent it did
         *  not build carries nothing this walk can read, which is where the walk stops. */
        private ClassModel superclassOf(ClassModel model) {
            return model.superclass()
                    .map(each -> classes.get(each.name().stringValue()))
                    .orElse(null);
        }

        /** One thing a state carries, and the class whose type parameters its signature is read
         *  against. */
        private record Carries(Signature type, ClassModel declaredBy) { }

        /** How much of what a state carries a walk is allowed through. */
        private enum Through {

            /** Everything, which is what counting the ways to one rule asks. */
            ANYTHING,

            /** Everything but a collection, which is what tells a state about one rule from a table
             *  of many. */
            ONE_VALUE_AT_A_TIME
        }

        /** Whether one thing a class carries reaches the rule it is about. */
        private boolean reachesARule(Carries carried, Through through) {
            for (String named : namesIn(carried.type(), through, carried.declaredBy())) {
                if (reachesARule(named, through)) {
                    return true;
                }
            }
            return false;
        }

        private boolean reachesARule(String named, Through through) {
            // A handle is a rule and the way to it in one value, so reaching one is reaching the
            // rule. Its own component is not a second way, which is why the walk stops here.
            if (isA(named, HANDLE) || isA(named, RULE)) {
                return true;
            }
            String key = named + "/" + through;
            Boolean had = reaches.get(key);
            if (had != null) {
                return had;
            }
            ClassModel model = classes.get(named);
            if (model == null || !underway.add(key)) {
                return false;
            }
            boolean found = false;
            for (Carries each : carriedBy(model)) {
                if (reachesARule(each, through)) {
                    found = true;
                    break;
                }
            }
            underway.remove(key);
            reaches.put(key, found);
            return found;
        }

        /** Whether {@code named} is {@code wanted} or is declared under it, which is how a seal's
         *  arms answer for the seal. */
        private boolean isA(String named, String wanted) {
            if (named.equals(wanted)) {
                return true;
            }
            ClassModel model = classes.get(named);
            if (model == null) {
                return false;
            }
            for (var each : model.interfaces()) {
                if (isA(each.name().stringValue(), wanted)) {
                    return true;
                }
            }
            return model.superclass()
                    .map(it -> isA(it.name().stringValue(), wanted))
                    .orElse(false);
        }

        /**
         * Whether what {@code owner} names holds many of what is inside it.
         *
         * <p>Asked of the platform rather than matched against a list of names written here. A list
         * is a list of the collections somebody thought of, and the day a state keeps its rules in
         * one that is not on it, this walk calls a table of many rules a state about one and refuses
         * it for holding two.
         *
         * <p>{@code Optional} is not one of these, and the question answers that on its own: what is
         * inside one is one value.
         */
        private static boolean holdsManyAtATime(String owner) {
            if (!owner.startsWith("java/")) {
                return false;
            }
            try {
                Class<?> named = Class.forName(owner.replace('/', '.'));
                return java.util.Collection.class.isAssignableFrom(named)
                        || Map.class.isAssignableFrom(named)
                        || Stream.class.isAssignableFrom(named);
            } catch (ClassNotFoundException e) {
                return false;
            }
        }

        /**
         * Every class a signature names, its type arguments among them: a set of handles reaches
         * what a handle does, except where the walk is asked for one value at a time.
         *
         * <p>{@code declaredBy} is the class whose type parameters a variable in the signature is
         * looked up in, which is whoever wrote the field.
         */
        private static Set<String> namesIn(Signature type, Through through,
                                           ClassModel declaredBy) {
            Set<String> out = new LinkedHashSet<>();
            collect(type, through, declaredBy, out);
            return out;
        }

        private static void collect(Signature type, Through through, ClassModel declaredBy,
                                    Set<String> into) {
            switch (type) {
                case Signature.BaseTypeSig _ -> { }
                case Signature.ArrayTypeSig array -> {
                    if (through == Through.ANYTHING) {
                        collect(array.componentSignature(), through, declaredBy, into);
                    }
                }
                // Through the bound its declaration gives it. What a variable is instantiated with
                // is settled where the state is built and is not what is being asked: the question
                // is whether the state can hold a rule of its own, and a variable bounded by one
                // says so wherever it is instantiated.
                case Signature.TypeVarSig variable -> {
                    for (Signature.TypeParam declared
                            : WhatASignatureReaches.typeParametersOf(declaredBy)) {
                        if (!declared.identifier().equals(variable.identifier())) {
                            continue;
                        }
                        declared.classBound()
                                .ifPresent(each -> collect(each, through, declaredBy, into));
                        for (Signature.RefTypeSig each : declared.interfaceBounds()) {
                            collect(each, through, declaredBy, into);
                        }
                    }
                }
                case Signature.ClassTypeSig named -> {
                    String owner = named.classDesc().descriptorString().replaceAll("^L|;$", "");
                    into.add(owner);
                    if (through == Through.ONE_VALUE_AT_A_TIME && holdsManyAtATime(owner)) {
                        return;
                    }
                    for (Signature.TypeArg argument : named.typeArgs()) {
                        if (argument instanceof Signature.TypeArg.Bounded bounded) {
                            collect(bounded.boundType(), through, declaredBy, into);
                        }
                    }
                }
            }
        }

        private static Map<String, ClassModel> read(List<String> outputs) {
            Map<String, ClassModel> out = new HashMap<>();
            ClassFile parser = ClassFile.of();
            for (Path module : REPOSITORY.modules()) {
                for (String output : outputs) {
                    Path where = module.resolve("target").resolve(output);
                    if (!Files.isDirectory(where)) {
                        continue;
                    }
                    for (Path each : classFilesUnder(where)) {
                        String name = where.relativize(each).toString()
                                .replace(java.io.File.separatorChar, '/')
                                .replaceAll("\\.class$", "");
                        if (name.startsWith("souther/")) {
                            out.putIfAbsent(name, parse(parser, each));
                        }
                    }
                }
            }
            return out;
        }

        private static ClassModel parse(ClassFile parser, Path file) {
            try {
                return parser.parse(Files.readAllBytes(file));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        private static List<Path> classFilesUnder(Path where) {
            try (Stream<Path> found = Files.walk(where)) {
                return found.filter(each -> each.toString().endsWith(".class")).toList();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
