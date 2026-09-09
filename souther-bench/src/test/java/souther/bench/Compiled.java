package souther.bench;

import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeModel;
import java.lang.classfile.Instruction;
import java.lang.classfile.Opcode;
import java.lang.classfile.instruction.ConstantInstruction;
import java.lang.classfile.instruction.FieldInstruction;
import java.lang.classfile.instruction.InvokeDynamicInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.classfile.instruction.NewObjectInstruction;
import java.lang.classfile.instruction.TypeCheckInstruction;
import java.lang.constant.ClassDesc;
import java.lang.constant.DirectMethodHandleDesc;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * What the compiled classes do, read once and in one vocabulary.
 *
 * <p>Read off the bytecode rather than the sources, because that is what the question is. Whether a
 * constructor was invoked is not something a search of text can answer: an
 * {@code import ...Derivation.Complete} and a bare {@code new Complete(...)}, a line break after
 * {@code new}, a static import, a qualified call — all ordinary Java, and all read past a pattern
 * written for one spelling.
 *
 * <p><b>Three ways, and a check that leaves one out is a check with a way round it.</b> A reference
 * is the call it stands for and puts neither an invoke nor a {@code new} in the caller's code: it
 * arrives as a method handle in the bootstrap arguments of an {@code invokedynamic}. A check that
 * counted only {@code new} would be passed by {@code Complete::new} — which is why this is one
 * place and not a vocabulary each check writes out again. The first check here worked it out; the
 * second copied the module walk and not this, and was evadable by exactly the reference the first
 * one names.
 */
final class Compiled {

    private Compiled() {}

    /** How a class reaches something: it makes one, it calls one, it names one for later, or it
     *  reads one. */
    enum How {

        /** A {@code new}. */
        MAKES,

        /** An invocation of any kind. */
        CALLS,

        /** A method or constructor named as a reference, which runs somewhere else. */
        REFERS,

        /**
         * A field read or written, which is how a class reaches a constant.
         *
         * <p>Here because a rule about who may read an {@code enum} had nothing to look at without
         * it: {@code status == Some.CONSTANT} is a {@code getstatic} and none of the three above, so
         * a check written over calls alone met no reader at all and passed by seeing nothing. What a
         * class does is this type's to know, and a rule that needs a way of reaching something asks
         * for it here rather than being written over the nearest vocabulary to hand.
         */
        READS,

        /**
         * A type asked of a value with {@code instanceof}, which is how a class decides for itself
         * what something is.
         *
         * <p>Here because a rule that a sum is read by switching over it had nothing to look at
         * otherwise. An exhaustive {@code switch} over a sealed type is one {@code invokedynamic}
         * and stops when the sum grows; the same reader written as an {@code instanceof} compiles
         * unchanged and answers about the arms it knew — silently, and in whichever direction the
         * author's {@code else} went. The two are the same question in the source and different
         * instructions here, which is what makes the difference checkable at all.
         *
         * <p>{@code instanceof} and not a cast. A cast is something javac writes for itself wherever
         * a generic is read, so a rule counting those would be about the compiler's output rather
         * than about anything anybody wrote.
         */
        ASKS,

        /**
         * A type written as one case of a {@code switch} over what a value is.
         *
         * <p>What a reader of a sum names, which is the question a rule about reading a sum is
         * really asking. Whether such a reader stops when the sum grows is not decided by the
         * instruction it compiles to — a {@code switch} with a {@code default} is the same
         * {@code invokedynamic} as one without, and only the second stops — so a rule written over
         * the instruction is a rule about the wrong thing. What tells them apart is which cases were
         * named, which is here.
         */
        NAMES
    }

    /**
     * One thing one method of one class does.
     *
     * @param from       the class whose code holds it, nested names and all. What a rule is about
     *                   is the reader's to decide — one wants the class as it is written and one
     *                   wants the nest — so nothing is folded away here
     * @param method     the method whose code holds it, {@code <clinit>} for a static initialiser
     * @param descriptor that method's parameters and result, so that two overloads are two methods
     * @param owner      what is reached
     * @param member     which member of it, {@code <init>} for a construction
     */
    record Site(String from, String method, String descriptor, How how, String owner, String member,
                boolean isStatic) {

        /** Whether this makes a value of {@code type}, however the source spelled it. */
        boolean makesA(String type) {
            return owner.equals(type) && (how == How.MAKES
                    || (how == How.REFERS && member.equals("<init>")));
        }

        /** The method it is in, which is what a rule naming one method is about. */
        String at() {
            return from + "#" + method + descriptor;
        }
    }

    /**
     * One call, with the text this compiler could read at it.
     *
     * <p><b>What is read is the constant loaded immediately before the call and nothing more.</b>
     * Recovering what an argument was in general is running the program, and a rule written as
     * though this did that would be a rule about a guess. What it does answer is the shape a
     * document's fields are written in — {@code putArray("keptOpenBy")} — where the name is a
     * literal a line above the call, which is the whole of what a rule about a named field needs.
     *
     * <p>Empty where the call took no constant, or took one this could not see: a name built by
     * joining two strings is one nothing here reads, and a rule over these says nothing about such
     * a call rather than saying it is fine.
     *
     * @param said the string constant loaded just before, where there was one
     */
    record Invocation(Site site, List<String> said) {}

    /**
     * Every call of every compiled class, with the text read at it, worked out once.
     *
     * <p>Beside the parsed classes, which the fork shares, and not the same thing: this is what
     * <em>this</em> module makes of them. Every check here walks the whole reactor's code, and the
     * walk allocates one of these for every call there is, so sharing the files and then decoding
     * them again per check would leave the reading shared and the work not.
     */
    static List<Invocation> invocations() {
        return theReactor().invocations();
    }

    /** Everything every compiled class of every module does, beside {@link #invocations} and out of
     *  the same walk. */
    static List<Site> sites() {
        return theReactor().sites();
    }

    /**
     * What one walk over some classes came to.
     *
     * <p>Both of them, because they are two projections of one decoding and not two readings. What
     * a call is appears in each — a site saying that a call was made, and the same site with the
     * text this could read at it — so a reading that answered them separately would walk the code
     * twice, decode every method twice, and build two of the site for every call there is. Which is
     * what it did, while the count for each of them said one.
     *
     * <p>Handed out as it stands, and so held as what it is: a caller of a reading the whole fork
     * shares can empty a list everything after it reads. Made per ask, that was the caller's own
     * copy to spoil; shared, the lifetime is shared and the way to change it has to go.
     */
    record Reading(List<Site> sites, List<Invocation> invocations) {

        Reading {
            sites = List.copyOf(sites);
            invocations = List.copyOf(invocations);
        }
    }

    private static Reading EVERY;

    private static Reading theReactor() {
        if (EVERY == null) {
            EVERY = read(Reactor.classes());
            BUILDS++;
        }
        return EVERY;
    }

    /**
     * How many times the reading the fork shares has been built, for the check that says once.
     *
     * <p>Counted rather than compared: what a reading holds is a site for every call the reactor's
     * code makes, and a check asking whether it was handed the same lists twice says so by printing
     * all of them.
     *
     * <p><b>About the store and not about the walk.</b> A walk started anywhere else is not counted
     * here — {@link #read} is a walk over whatever it is handed, and a caller handing it the
     * reactor's classes is decoding them without filling this. What is true of one walk rather than
     * two is asked of the answers themselves, where the site an invocation carries is the site the
     * other answer holds.
     */
    private static int BUILDS;

    /** How many times the reading the fork shares has been built in this fork. */
    static int timesTheSharedReadingWasBuilt() {
        return BUILDS;
    }

    /** The invocations of the classes named, so that what this reads can be asked of code written
     *  to be read. */
    static List<Invocation> invocationsIn(List<ClassModel> classes) {
        return read(classes).invocations();
    }

    /** The sites of the same, beside {@link #invocationsIn} and for the same reason. */
    static List<Site> sitesIn(List<ClassModel> classes) {
        return read(classes).sites();
    }

    /**
     * One walk over {@code classes}, which is where everything this answers comes from.
     *
     * <p>The population is the argument and the reading is not: a check that wanted to know what
     * this does to a particular shape would otherwise write the walk again, and then what it
     * measured would be its own copy rather than the thing every rule here is built on. Which
     * classes are read and what is read of them are two things, and a rule that fixed both could
     * only ever be asked about the repository as it stands.
     */
    static Reading read(List<ClassModel> classes) {
        List<Site> sites = new ArrayList<>();
        List<Invocation> invocations = new ArrayList<>();
        for (ClassModel model : classes) {
            String from = named(model.thisClass().asInternalName());
            for (var method : model.methods()) {
                CodeModel code = method.code().orElse(null);
                if (code == null) {
                    continue;
                }
                String name = method.methodName().stringValue();
                String descriptor = method.methodType().stringValue();
                String loaded = null;
                for (var element : code) {
                    // Whatever was loaded is only what this call took if nothing came between, so
                    // anything that is not the constant itself clears it. Kept across the ones in
                    // between, a name put in a local and passed a line later would read as the
                    // name written at the call — and a rule about a named field would then be
                    // about a call that does not name it.
                    switch (element) {
                        case ConstantInstruction constant ->
                                loaded = constant.constantValue() instanceof String text
                                        ? text : null;
                        case InvokeInstruction call -> {
                            Site site = new Site(from, name, descriptor, How.CALLS,
                                    named(call.owner().asInternalName()),
                                    call.name().stringValue(),
                                    call.opcode() == Opcode.INVOKESTATIC);
                            sites.add(site);
                            invocations.add(new Invocation(site,
                                    loaded == null ? List.of() : List.of(loaded)));
                            loaded = null;
                        }
                        case NewObjectInstruction made -> {
                            sites.add(new Site(from, name, descriptor, How.MAKES,
                                    named(made.className().asInternalName()), "<init>", false));
                            loaded = null;
                        }
                        case TypeCheckInstruction asked
                                when asked.opcode() == Opcode.INSTANCEOF -> {
                            sites.add(new Site(from, name, descriptor, How.ASKS,
                                    named(asked.type().asInternalName()), "instanceof", false));
                            loaded = null;
                        }
                        case FieldInstruction field -> {
                            sites.add(new Site(from, name, descriptor, How.READS,
                                    named(field.owner().asInternalName()),
                                    field.name().stringValue(),
                                    field.opcode() == Opcode.GETSTATIC
                                            || field.opcode() == Opcode.PUTSTATIC));
                            loaded = null;
                        }
                        case InvokeDynamicInstruction reference -> {
                            boolean switching = isATypeSwitch(reference);
                            for (var argument : reference.bootstrapArgs()) {
                                if (argument instanceof DirectMethodHandleDesc handle) {
                                    sites.add(referred(from, name, descriptor, handle));
                                } else if (switching && argument instanceof ClassDesc labelled) {
                                    sites.add(new Site(from, name, descriptor, How.NAMES,
                                            describedBy(labelled), "case", false));
                                }
                            }
                            loaded = null;
                        }
                        case Instruction _ -> loaded = null;
                        default -> { }
                    }
                }
            }
        }
        assertFalse(sites.isEmpty(), "these classes do nothing at all, so a rule read off what they"
                + " do holds nothing");
        assertFalse(invocations.isEmpty(), "these classes make no call at all, so a rule read off"
                + " the text at a call holds nothing");
        return new Reading(sites, invocations);
    }

    /** Whether this {@code invokedynamic} is a {@code switch} over what a value is. Asked of the
     * bootstrap, because a class arrives in the bootstrap arguments of others for reasons that are
     * not a case of anything — a record's own {@code equals} is handed the record. */
    private static boolean isATypeSwitch(InvokeDynamicInstruction reference) {
        DirectMethodHandleDesc bootstrap = reference.bootstrapMethod();
        return bootstrap.owner().displayName().equals("SwitchBootstraps")
                && bootstrap.methodName().equals("typeSwitch");
    }

    private static Site referred(String from, String method, String descriptor,
                                 DirectMethodHandleDesc handle) {
        String owner = handle.owner().descriptorString();
        owner = owner.startsWith("L") && owner.endsWith(";")
                ? owner.substring(1, owner.length() - 1).replace('/', '.') : owner;
        return switch (handle.kind()) {
            case STATIC, INTERFACE_STATIC ->
                    new Site(from, method, descriptor, How.REFERS, owner, handle.methodName(), true);
            case CONSTRUCTOR ->
                    new Site(from, method, descriptor, How.REFERS, owner, "<init>", false);
            default ->
                    new Site(from, method, descriptor, How.REFERS, owner, handle.methodName(), false);
        };
    }

    private static String named(String internal) {
        return internal.replace('/', '.');
    }

    /** The name of what a descriptor describes, in the same words the rest of this answers in. */
    private static String describedBy(ClassDesc type) {
        String descriptor = type.descriptorString();
        return descriptor.startsWith("L") && descriptor.endsWith(";")
                ? named(descriptor.substring(1, descriptor.length() - 1)) : descriptor;
    }
}
