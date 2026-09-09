package souther.compiler.check;

import souther.compiler.ast.Hir;
import souther.compiler.ast.WrittenName;
import souther.compiler.diag.Region;
import souther.compiler.diag.SourcePos;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * What a published declaration says is everything the declaration says, and none of where it stands.
 *
 * <p>Two things {@link DeclarationMeaning} has to be, and only one of them is the one that is easy
 * to see.
 * A meaning that read where a declaration stands makes a module that imports it depend on an edit it
 * cannot see. A meaning that left out something the declaration says makes two declarations that say
 * different things one dependency, and the store then stops the work of everything that read the one
 * it kept — which is the worse of the two, and the one a check written only about what a meaning
 * ignores would never fail on.
 *
 * <p><b>The population is the components, as the records declare them.</b> The switch being over a
 * sealed type says a kind added later arrives as a compile error; it says nothing about a component
 * added to a kind that is already there. So each component is asked which of the two it is, and a
 * component that is neither — or that is new and nobody classified — fails here rather than being
 * quietly left out of what a module elsewhere is told.
 *
 * <p><b>A component whose type is a written name is neither.</b> {@link WrittenName} holds what a
 * name is (its canonical form) beside how and where it was spelled, so a rule that admitted the type
 * would publish the spelling and one that refused it would drop the name. Those are read through
 * {@link #SAYS_THROUGH}, and what the producer may call on a {@code WrittenName} is held separately.
 *
 * <p>Read off the compiled producer rather than off its text: which accessors it calls is what the
 * class file says, and reading the source would be answering the same question a second way.
 */
class APublishedDeclarationSaysEverythingAndPlacesNothingTest {

    /** What a component of this type says is where the declaration stands, and not what it says. */
    private static final Set<Class<?>> WHERE_IT_STANDS = Set.of(SourcePos.class, Region.class);

    /** What a component of this type says is both, and is read through one accessor. */
    private static final Set<Class<?>> BOTH = Set.of(WrittenName.class);

    /**
     * The one thing a producer may ask a written name.
     *
     * <p>A name and its casing are one name, which is what canonical means. Everything else a
     * written name holds — the spelling the author used, the stretches it was written over, the
     * place a synthesized one is anchored at — says where the declaration stands.
     */
    private static final Set<String> WHAT_A_NAME_MAY_BE_ASKED = Set.of("canonical");

    /**
     * A component read by an accessor of its own rather than by the one the record generates.
     *
     * <p>Each is here because the component is a written name and only its canonical half is
     * published: {@code Hir.Field.name()} is that field's {@code written().canonical()}, and asking
     * it is asking the name without asking where it was written.
     */
    private static final Map<String, String> SAYS_THROUGH = Map.of("Field.written", "Field.name");

    /**
     * A component this producer does not read, and what says the same thing instead.
     *
     * <p>Written down rather than left out, so that a component going unread for any other reason is
     * a failure here and not a habit. Two reasons appear, and they are different reasons. One is
     * that something else publishes it: the clauses go out as {@link ClauseMeaning}, a field's type
     * as the type it denotes. The other is that a second component of the same record already says
     * it — a declaration's identity carries the name it was declared under, and a reference's
     * identity carries which declaration it reaches, so the written name beside either adds only how
     * that name was spelled and where.
     */
    private static final Map<String, String> READ_ELSEWHERE = Map.of(
            "Data.invariants", "the clause reading, published as ClauseMeaning and audited by"
                    + " AReadingReadsEverythingATermSaysAndNothingOfWhereItStandsTest",
            "Field.type", "TypeOps.fieldType, which answers the type the written type denotes",
            "Data.written", "declares, the identity minted where this representation was built,"
                    + " which carries the name the declaration was written under",
            "SumData.written", "declares, as above",
            "UnitData.written", "declares, as above",
            "Denoting.name", "type, which says which declaration the reference reaches. Two modules"
                    + " reaching one declaration under two spellings name one thing");

    /** The records a published declaration is read off: every kind of one, and the two it holds. */
    private static List<Class<?>> walked() {
        List<Class<?>> out = new ArrayList<>(List.of(Hir.Def.class.getPermittedSubclasses()));
        out.add(Hir.Field.class);
        out.add(Hir.Name.Denoting.class);
        out.add(Hir.Name.Unanswered.class);
        return out;
    }

    @Test
    void everythingADeclarationSaysIsRead() throws IOException {
        Set<String> called = componentsReadByTheProducer();
        Set<String> unread = new TreeSet<>();
        for (Class<?> kind : walked()) {
            for (RecordComponent component : kind.getRecordComponents()) {
                String named = name(kind, component);
                if (WHERE_IT_STANDS.contains(component.getType()) || READ_ELSEWHERE.containsKey(named)) {
                    continue;
                }
                if (!called.contains(named) && !called.contains(SAYS_THROUGH.get(named))) {
                    unread.add(named);
                }
            }
        }

        assertEquals(Set.of(), unread,
                "a declaration saying this differently is a declaration published the same, and"
                        + " every module that imported the one the store kept goes on holding it");
    }

    @Test
    void andNothingOfWhereItStandsIs() throws IOException {
        Set<String> called = componentsReadByTheProducer();
        Set<String> read = new TreeSet<>();
        for (Class<?> kind : walked()) {
            for (RecordComponent component : kind.getRecordComponents()) {
                if (WHERE_IT_STANDS.contains(component.getType())
                        && called.contains(name(kind, component))) {
                    read.add(name(kind, component));
                }
            }
        }

        assertEquals(Set.of(), read,
                "a module importing a declaration published from this is one a blank line written"
                        + " above the declaration reaches");
    }

    /**
     * And of a name that says two things, only what it says is asked.
     *
     * <p>The rule above is by component type and this one cannot be: a written name is read, so what
     * holds the spelling out of the answer is which of its own components the producer asks for.
     */
    @Test
    void andOfANameOnlyWhatItIsCalledIsAsked() throws IOException {
        Set<String> asked = new TreeSet<>(methodsCalledOn("souther/compiler/ast/WrittenName"));
        asked.removeAll(WHAT_A_NAME_MAY_BE_ASKED);

        assertEquals(Set.of(), asked,
                "what a name was spelled as, and where, is where the declaration stands: a meaning"
                        + " that asked for it moves when the author retypes the same name");
    }

    /** And the population holds both kinds of component, so neither question is asked of nothing. */
    @Test
    void bothKindsOfComponentAreThere() {
        int says = 0;
        int stands = 0;
        for (Class<?> kind : walked()) {
            for (RecordComponent component : kind.getRecordComponents()) {
                if (WHERE_IT_STANDS.contains(component.getType())) {
                    stands++;
                } else if (!BOTH.contains(component.getType())) {
                    says++;
                }
            }
        }

        assertFalse(says == 0 || stands == 0,
                "one of the two kinds of component was not found at all, so one of the questions"
                        + " above was asked of nothing");
    }

    private static String name(Class<?> kind, RecordComponent component) {
        return kind.getSimpleName() + "." + component.getName();
    }

    /**
     * Every component of a declaration the producer reads, by the record it is declared on.
     *
     * <p>A call made on {@link Hir.Def} is a call on every kind of one: the sealed parent declares
     * what all of them hold, and which arm it lands in is settled after the call.
     */
    private static Set<String> componentsReadByTheProducer() throws IOException {
        Set<String> read = new LinkedHashSet<>();
        for (Call call : callsMadeByTheProducer()) {
            if (!call.owner().startsWith("souther/compiler/ast/Hir")) {
                continue;
            }
            int nested = call.owner().lastIndexOf('$');
            String on = nested < 0 ? "Hir" : call.owner().substring(nested + 1);
            if (on.equals("Def")) {
                for (Class<?> kind : walked()) {
                    read.add(kind.getSimpleName() + "." + call.name());
                }
            } else {
                read.add(on + "." + call.name());
            }
        }
        return read;
    }

    /** What the producer asks of {@code owner}, by method name. */
    private static Set<String> methodsCalledOn(String owner) throws IOException {
        Set<String> asked = new LinkedHashSet<>();
        for (Call call : callsMadeByTheProducer()) {
            if (call.owner().equals(owner)) {
                asked.add(call.name());
            }
        }
        return asked;
    }

    /** One call the producer makes. */
    private record Call(String owner, String name) {}

    /**
     * Every call the producer makes, following the ones it makes of its own class.
     *
     * <p>From {@code of} and no further out: what is being audited is what this producer publishes,
     * and a call it makes into another class is answered by whatever that class was written to do.
     * The two it delegates to are named in {@link #READ_ELSEWHERE}.
     */
    private static List<Call> callsMadeByTheProducer() throws IOException {
        ClassModel model = ClassFile.of().parse(Files.readAllBytes(compiledProducer()));
        List<Call> calls = new ArrayList<>();
        Set<String> walked = new LinkedHashSet<>();
        Deque<MethodModel> pending = new ArrayDeque<>();
        // Every way in, and not the one that reads the most. A second entry reading a component the
        // first does not would be a component published without ever being audited, which is the
        // hole this walk exists to have none of.
        for (MethodModel method : model.methods()) {
            if (method.methodName().stringValue().startsWith("of")) {
                pending.add(method);
            }
        }
        String self = model.thisClass().asInternalName();
        while (!pending.isEmpty()) {
            MethodModel method = pending.removeFirst();
            if (!walked.add(method.methodName().stringValue()
                    + method.methodType().stringValue())) {
                continue;
            }
            method.code().ifPresent(code -> code.forEach(element -> {
                if (element instanceof InvokeInstruction call) {
                    calls.add(new Call(call.owner().asInternalName(), call.name().stringValue()));
                    if (call.owner().asInternalName().equals(self)) {
                        for (MethodModel candidate : model.methods()) {
                            if (candidate.methodName().stringValue().equals(call.name().stringValue())
                                    && candidate.methodType().stringValue()
                                            .equals(call.type().stringValue())) {
                                pending.add(candidate);
                            }
                        }
                    }
                }
            }));
        }
        return calls;
    }

    private static Path compiledProducer() {
        return Path.of("target", "classes", "souther", "compiler", "check",
                "DeclarationMeaning.class").toAbsolutePath();
    }
}
