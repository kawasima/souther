package souther.compiler.values;

import org.junit.jupiter.api.Test;
import souther.compiler.numeric.OrderedInterval;
import souther.compiler.numeric.OrderedIntervals;
import souther.compiler.reading.StateOfAReading;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.constantpool.MethodHandleEntry;
import java.lang.classfile.instruction.InvokeDynamicInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.constant.ClassDesc;
import java.lang.constant.DirectMethodHandleDesc;
import java.lang.reflect.AccessFlag;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A reading is in one of the states its operations reach, and the parts are not a way in.
 *
 * <p>What each of these holds is several parts that state relations to each other — a whole that
 * holds nothing is not a position that holds nothing, the alternatives are never an empty union, a
 * promise is about the blocks every alternative agrees on, a refusal is one the work that built the
 * reading noted. None of those is a property of one part, so a caller handed the parts side by side
 * can write down a combination nothing read, and nothing says so. What holds the relations up is
 * that the operations are the only things that make one.
 *
 * <p>So the propositions are about the ways in. A reading answers with a reading — that is what an
 * algebra is — and what is asked of each of those is that it composes or narrows one, rather than
 * taking the parts and handing the reading back. The second shape is the one that lets a state be
 * asserted of parts nothing put together, and it is also the one that lets a fact live in an
 * operation rather than in the value: remove the operation and the way to say the fact is gone with
 * it.
 *
 * <p><b>Which types these are is read off what they say of themselves</b>
 * ({@link StateOfAReading}), and never off whether they already hold their parts the way a state
 * does. Worked out from the shape, this would find the states that are already right and pass over
 * one written as a record of its parts — which is the state these propositions exist to refuse, and
 * the only one such a reading could never report. So membership is declared and the shape is what
 * is held to.
 *
 * <p>And read off the compiled classes, so a state declaring itself in a package nothing here names
 * is found as surely as one beside these.
 */
class AReadingIsWhatItsOperationsReachTest {

    /** Every class the compiler is made of, which is what both of the walks below read. */
    private static final List<Class<?>> COMPILED = compiled();

    /** Every state of a reading the compiler declares, in the order their names sort. */
    private static final List<Class<?>> FAMILY = family();

    private static List<Class<?>> family() {
        List<Class<?>> found = new ArrayList<>();
        for (Class<?> each : COMPILED) {
            if (each.isAnnotationPresent(StateOfAReading.class)) {
                found.add(each);
            }
        }
        found.sort(Comparator.comparing(Class::getName));
        return List.copyOf(found);
    }

    /**
     * The compiler's own classes, read from where the ones this test runs against were built.
     *
     * <p>Found from a class of the compiler rather than from a path written here, so this reads the
     * classes it is running with. Its own tests are not among them: a test writes a reading to look
     * at it and ships nothing.
     *
     * <p>Loaded without being initialised. What is asked of them is what they declare, which is
     * there as soon as the class is; running their static initialisers would make this walk carry
     * whatever any of them does on the way in, and a proposition about what a type says of itself
     * would fail for a reason that is nothing to do with it.
     */
    private static List<Class<?>> compiled() {
        Path where = classesUnder(AdmissibleValues.class);
        ClassLoader by = AdmissibleValues.class.getClassLoader();
        try (Stream<Path> found = Files.walk(where)) {
            List<Class<?>> out = new ArrayList<>();
            for (Path each : found.filter(p -> p.toString().endsWith(".class")).toList()) {
                String named = where.relativize(each).toString()
                        .replace(java.io.File.separatorChar, '.');
                out.add(Class.forName(named.substring(0, named.length() - ".class".length()),
                        false, by));
            }
            return List.copyOf(out);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Path classesUnder(Class<?> one) {
        try {
            return Path.of(one.getProtectionDomain().getCodeSource().getLocation().toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * The states of a reading are these, and a type saying so is one of them.
     *
     * <p>Written down so that a state added to the family is somebody saying so. What each of them
     * is for is its own type's business; that these are the ones is read off what they declare, and
     * one added or dropped arrives here as a difference.
     */
    @Test
    void theStatesOfAReadingAreTheOnesThatSaySo() {
        assertEquals(List.of(AdmissibleValues.class, PlannedValues.Settled.class, Realized.class,
                        OrderedIntervals.class).stream()
                        .sorted(Comparator.comparing(Class::getName)).toList(),
                FAMILY,
                "the states of a reading are not the ones this says they are. One added here is"
                        + " held to the propositions below, which is what declaring it means");
        assertTrue(COMPILED.size() > FAMILY.size(),
                "the walk read the compiler's classes, or every proposition below is about nothing");
    }

    /** And each of them keeps its parts as one record of its own, which is what it declared. */
    @Test
    void eachOfThemKeepsItsPartsAsOneRecordOfItsOwn() {
        for (Class<?> state : FAMILY) {
            assertTrue(keepsItsPartsAsOne(state),
                    () -> state.getSimpleName() + " says it is a state of a reading and holds "
                            + List.of(state.getDeclaredFields()) + ", where a state holds one"
                            + " record of the parts it is made of");
        }
    }

    /**
     * And the reading that asks it can say no, which is what makes the answer above worth having.
     *
     * <p>{@link OrderedInterval} is a pair of ends: a product with nothing to hold up between its
     * parts, which writes them out as what it is. Asked the same question, it comes back with no —
     * so a state reported as keeping its parts as its own is a fact about the state.
     */
    @Test
    void andATypeThatIsARecordOfItsPartsIsSeenNotToKeepThem() {
        assertFalse(keepsItsPartsAsOne(OrderedInterval.class),
                "the question cannot answer no, so its yes above says nothing");
    }

    /** Whether it holds one thing and that thing is a record of its parts. */
    private static boolean keepsItsPartsAsOne(Class<?> type) {
        List<Field> mine = new ArrayList<>();
        for (Field each : type.getDeclaredFields()) {
            if (!Modifier.isStatic(each.getModifiers())) {
                mine.add(each);
            }
        }
        return mine.size() == 1 && mine.getFirst().getType().isRecord();
    }

    /** And none of them may be made from outside, whichever of them it is. */
    @Test
    void noneOfThemMayBeMadeFromOutside() {
        for (Class<?> state : FAMILY) {
            assertEquals(0, state.getConstructors().length,
                    () -> state.getSimpleName() + " publishes a way to write its parts down, and"
                            + " the relations they state to each other are held up by nothing");
        }
    }

    /**
     * Every way one of these is made, read off the code of every class the compiler is made of.
     *
     * <p>Read off what a method does and not off what it answers with. A method whose type says it
     * answers with a reading may be handing back one it was given — a projection of a state that
     * holds it, an answer that carries it along — and that is not a way in. What makes a way in is
     * making one, which is the constructor and whatever invokes it, and that is in the code.
     *
     * <p>Every class, whatever it is called and wherever it is written: a maker somewhere this
     * happened to be told to look at is a maker, and a walk told where to look answers about the
     * places it was told about.
     *
     * <p>Whatever its access, because a maker handed the parts is the thing being asked about and
     * being private is not what settles it. A construction reached through a method reference makes
     * one as surely as one written out, so the bootstrap arguments of an {@code invokedynamic} are
     * read too.
     */
    private static Set<String> waysInto(Class<?> state) {
        String internal = state.getName().replace('.', '/');
        Set<String> ways = new LinkedHashSet<>();
        for (Path each : classFiles()) {
            ClassModel owner = parse(each);
            boolean itself = owner.thisClass().asInternalName().equals(internal);
            for (MethodModel method : owner.methods()) {
                boolean made = "<init>".equals(method.methodName().stringValue())
                        ? itself : makes(method, internal);
                if (made) {
                    ways.add(named(owner, method));
                }
            }
        }
        return ways;
    }

    /** One way in, as the method it is: what it is called, and what it was handed. */
    private static String named(ClassModel owner, MethodModel method) {
        boolean constructor = "<init>".equals(method.methodName().stringValue());
        StringBuilder out = new StringBuilder();
        if (method.flags().has(AccessFlag.PUBLIC)) {
            out.append("public ");
        } else if (method.flags().has(AccessFlag.PRIVATE)) {
            out.append("private ");
        }
        out.append(constructor ? simpleNameOf(owner.thisClass().asInternalName())
                : method.methodName().stringValue()).append('(');
        List<ClassDesc> takes = method.methodTypeSymbol().parameterList();
        for (int at = 0; at < takes.size(); at++) {
            String takesOne = takes.get(at).displayName();
            out.append(at == 0 ? "" : ", ").append(takesOne.substring(takesOne.lastIndexOf('$') + 1));
        }
        return out.append(')').toString();
    }

    private static String simpleNameOf(String internal) {
        String named = internal.substring(internal.lastIndexOf('/') + 1);
        return named.substring(named.lastIndexOf('$') + 1);
    }

    /** Whether the method's own code makes one: written out, or handed to something that will. */
    private static boolean makes(MethodModel method, String internal) {
        return method.code().map(code -> code.elementStream().anyMatch(element -> switch (element) {
            case InvokeInstruction it -> internal.equals(it.owner().asInternalName())
                    && "<init>".equals(it.name().stringValue());
            case InvokeDynamicInstruction it -> it.invokedynamic().bootstrap().arguments().stream()
                    .anyMatch(argument -> argument instanceof MethodHandleEntry handle
                            && handle.asSymbol() instanceof DirectMethodHandleDesc said
                            && internal.equals(said.owner().descriptorString()
                                    .substring(1, said.owner().descriptorString().length() - 1))
                            && "<init>".equals(said.methodName()));
            default -> false;
        })).orElse(false);
    }

    private static ClassModel parse(Path compiled) {
        try {
            return ClassFile.of().parse(Files.readAllBytes(compiled));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static List<Path> classFiles() {
        try (Stream<Path> found = Files.walk(classesUnder(AdmissibleValues.class))) {
            return found.filter(p -> p.toString().endsWith(".class")).toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * A reading of the values is reached by reading something.
     *
     * <p>Where a reading starts, a conjunction of two or of several, the same reading with more
     * said about what a choice left open, and the same blocks under other names. A rule of the
     * values is not among them: it enters as a description and is worked out, which is the one way
     * across and does the work rather than being handed what the work would have left.
     */
    @Test
    void everyWayToAReadingOfTheValuesIsSomethingReadingIt() {
        assertEquals(Set.of(
                        "private AdmissibleValues(Parts)",
                        "public top()",
                        "public meet(AdmissibleValues, Allowance)",
                        "public renamed(Function)",
                        "public alsoOpenedAt(Set)",
                        // A conjunction of several, which writes down the order the rules were
                        // read in over what meeting them one after another left.
                        "private sayingWhatWasReadInTheOrderOf(List)",
                        "realize(Settled, Allowance)"),
                waysInto(AdmissibleValues.class),
                "a way into a reading of the values that is not one of the operations it is"
                        + " composed by. If it takes the parts and hands a reading back, the"
                        + " relations they state to each other are held up by nothing");
    }

    /**
     * And realization answers with the reading beside what could not be built while making it.
     *
     * <p>The two are settled by one piece of work, so what makes one is handed what that work came
     * to and cannot be handed the two halves — a reading whose positions an allowance ran out on,
     * beside a record of work that noted nothing, is a pair no working-out produced.
     */
    @Test
    void andADescriptionIsWorkedOutByTheReadingItComesTo() {
        assertEquals(Set.of(
                        "private Realized(Parts)",
                        "of(Outcome)",
                        "public alsoOpenedAt(Set)"),
                waysInto(Realized.class),
                "realization is the one way across, and it is the reading's own: handed the"
                        + " description and the allowance it builds the sets, decides which of"
                        + " them nobody could work out, and settles the rest against what came out");
    }

    /** And what realization came to is minted by realization and by nothing else. */
    @Test
    void andWhatAWorkingOutCameToIsMintedWhereTheWorkIsDone() {
        assertEquals(Set.of(
                        "private Outcome(AdmissibleValues, Unbuilt)",
                        "realize(Settled, Allowance)"),
                waysInto(AdmissibleValues.Outcome.class),
                "a second way to put a reading beside a record of work is a second way to say that"
                        + " this work made this reading, which is what handing them over as two"
                        + " came to");
    }

    /**
     * A description is reached the same way, over plans rather than sets.
     *
     * <p>Asked of the interface a caller holds, since every way to one of these answers with it.
     */
    @Test
    void everyWayToADescriptionIsSomethingReadingIt() {
        assertEquals(Set.of(
                        "private Settled(Parts)",
                        "public top()",
                        "public at(Object, AdmittedPlan)",
                        "public holdingAsOne(Object, Object)",
                        "public heldApart(Object, Object)",
                        "public unreadable(Set, UnreadReason)",
                        "public meet(PlannedValues)",
                        "public alsoStanding(Standing)",
                        "public bothDead(PlannedValues)",
                        // A choice, whose two ways of leaving the alternatives — merged into one
                        // product, or held apart — are one maker told which.
                        "private joinedLive(PlannedValues, boolean)"),
                waysInto(PlannedValues.Settled.class),
                "a way into a description that is not one of the operations it is composed by");
    }

    /** And so is a reading of the ordered rules. */
    @Test
    void everyWayToAReadingOfTheOrderIsSomethingReadingIt() {
        assertEquals(Set.of(
                        "private OrderedIntervals(Parts)",
                        "public top()",
                        "public at(Object, OrderedInterval)",
                        "public meet(OrderedIntervals)",
                        "public renamed(Function)",
                        "public bothDead(OrderedIntervals)",
                        "public joinLive(OrderedIntervals)"),
                waysInto(OrderedIntervals.class),
                "a way into a reading of the ordered rules that is not one of the operations it is"
                        + " composed by");
    }

    /**
     * And the walk that reads the ways in can see one that is published.
     *
     * <p>{@link OrderedInterval} publishes the way to write a pair of ends down. Read by the same
     * walk, it comes back with that way — so a state reported as having none is a fact about the
     * state and not about a walk that finds nothing wherever it looks.
     */
    @Test
    void aTypeThatDoesPublishItsPartsIsSeenToPublishThem() {
        Set<String> ways = waysInto(OrderedInterval.class);

        assertTrue(ways.contains("public OrderedInterval(Endpoint, Endpoint)"),
                () -> "the walk did not find the way in a product publishes, and would report a"
                        + " reading closed whatever the reading did: " + ways);
    }
}
