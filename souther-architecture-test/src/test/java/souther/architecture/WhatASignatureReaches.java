package souther.architecture;

import souther.test.Signatures;

import java.lang.classfile.Attributes;
import java.lang.classfile.ClassModel;
import java.lang.classfile.ClassSignature;
import java.lang.classfile.Signature;
import java.lang.classfile.attribute.RecordAttribute;
import java.lang.classfile.attribute.RecordComponentInfo;
import java.lang.classfile.attribute.SignatureAttribute;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * What the types a signature is written in reach.
 *
 * <p>The closure read from the roots it is given: the types the signature names, the declared upper
 * bounds of the type variables it refers to, and the components of the records it arrives at. A rule
 * about a surface is written against this so that it holds of what a caller can be handed rather
 * than of how the handing was spelled — a clause inside a record is a clause the caller gets, and a
 * world named through a variable is the world the declaration guarantees.
 *
 * <p><b>A bound is read because it is what the declaration guarantees, not because it says what the
 * argument will be.</b> {@code <S extends Symbols>} may be given a derived world and this reading
 * does not say so; what it says is that {@code Symbols} is what every use of {@code S} is warranted
 * to be. That is the same answer to both of the questions a rule asks — whether the declaration
 * guarantees a world arrives here at all, and whether the one it guarantees is the derived one, a
 * question about what is written and never about what a run could pass — so one reading serves both,
 * and
 * a walk bounded only by {@code Symbols} stays loose under the rule that wants the derived world.
 *
 * <p><b>A type parameter is an environment and not a root.</b> A bound is followed when the variable
 * it belongs to is referred to from a root, and not before: a method declaring
 * {@code <S extends DerivedSymbols>} and never writing {@code S} reaches nothing through it.
 *
 * <p><b>What this cannot resolve, it does not call unreachable.</b> A type variable with no binding
 * in scope and a class of this repository with no class file are both questions this reading cannot
 * answer, and both fail it. Answering {@code false} there is the shape of the defect this reading
 * replaced: a spelling nobody had thought of went unread and was reported as reaching nothing.
 *
 * <p>What it does not do is recover what a signature threw away. A raw type, or a parameter written
 * as {@code Object}, names nothing this can follow, and no reading of the signature will say what
 * flows through it. Nor does it substitute the arguments at a use into the components of the record
 * they parameterize: an argument is written where the record is used and is already read there, and
 * a bound is the only thing a signature refers to without spelling it. A question about which
 * component becomes which type would need that substitution and is not one this answers.
 */
final class WhatASignatureReaches {

    private static final String OF_THIS_REPOSITORY = "souther/";

    private final CompiledOutputs compiled;

    WhatASignatureReaches(CompiledOutputs compiled) {
        this.compiled = compiled;
    }

    /** Whether any of {@code roots}, read in {@code scope}, reaches one of {@code wanted}. */
    boolean anyOf(List<Signature> roots, Scope scope, Set<String> wanted) {
        Walk walk = new Walk(wanted);
        for (Signature root : roots) {
            if (walk.reaches(root, scope)) {
                return true;
            }
        }
        return false;
    }

    /** Whether {@code root}, read in {@code scope}, reaches one of {@code wanted}. */
    boolean reaches(Signature root, Scope scope, Set<String> wanted) {
        return anyOf(List.of(root), scope, wanted);
    }

    /** The signature of one record component, generic or not, as this reading takes it. */
    static Signature componentSignature(RecordComponentInfo component) {
        return component.findAttribute(Attributes.signature())
                .map(SignatureAttribute::asTypeSignature)
                .orElseGet(() -> Signature.of(component.descriptorSymbol()));
    }

    /** What a type parameter declares its variable to be: the class bound where there is one, and
     *  the interface bounds, which is where a variable bounded only by interfaces says so. */
    private static List<Signature.RefTypeSig> boundsOf(Signature.TypeParam declared) {
        List<Signature.RefTypeSig> out =
                new ArrayList<>(declared.classBound().map(List::of).orElse(List.of()));
        out.addAll(declared.interfaceBounds());
        return out;
    }

    /** The type parameters a class declares, none where it declares no signature of its own. */
    static List<Signature.TypeParam> typeParametersOf(ClassModel owner) {
        return owner.findAttribute(Attributes.signature())
                .map(SignatureAttribute::asClassSignature)
                .map(ClassSignature::typeParameters)
                .orElse(List.of());
    }

    /** One reading, holding what it has already been through. */
    private final class Walk {

        private final Set<String> wanted;

        /** The records already opened. Their components were read under their own scope, which is
         *  the same one however this arrived at them, so a second visit answers nothing new. */
        private final Set<String> opened = new LinkedHashSet<>();

        /** The bindings already followed, which is a different loop from the one above: a variable
         *  bounded by its own type is ordinary Java, and the closure of a bound is finite. The
         *  binding alone is the key because a binding settles the frame its bound is read in, so
         *  there is no second reading of one to miss. */
        private final Set<Scope.Binding> followed = new LinkedHashSet<>();

        private Walk(Set<String> wanted) {
            this.wanted = wanted;
        }

        private boolean reaches(Signature type, Scope scope) {
            return switch (type) {
                case Signature.BaseTypeSig _ -> false;
                case Signature.ArrayTypeSig array -> reaches(array.componentSignature(), scope);
                case Signature.TypeVarSig variable -> reachesThroughBounds(variable, scope);
                case Signature.ClassTypeSig named -> reachesThroughClass(named, scope);
            };
        }

        private boolean reachesThroughArgument(Signature.TypeArg argument, Scope scope) {
            return switch (argument) {
                case Signature.TypeArg.Unbounded _ -> false;
                case Signature.TypeArg.Bounded bounded -> reaches(bounded.boundType(), scope);
            };
        }

        private boolean reachesThroughBounds(Signature.TypeVarSig variable, Scope scope) {
            Scope.Resolved resolved = scope.bindingOf(variable.identifier());
            if (!followed.add(resolved.binding())) {
                return false;
            }
            // In the frame that declared it, never the one it was written in: what a bound names is
            // read where the bound was written.
            for (Signature.RefTypeSig bound : resolved.binding().bounds()) {
                if (reaches(bound, resolved.declaredIn())) {
                    return true;
                }
            }
            return false;
        }

        private boolean reachesThroughClass(Signature.ClassTypeSig named, Scope scope) {
            String name = Signatures.named(named);
            if (wanted.contains(name)) {
                return true;
            }
            for (Signature.TypeArg argument : named.typeArgs()) {
                if (reachesThroughArgument(argument, scope)) {
                    return true;
                }
            }
            Optional<Signature.ClassTypeSig> outer = named.outerType();
            if (outer.isPresent() && reachesThroughClass(outer.get(), scope)) {
                return true;
            }
            return reachesThroughComponents(name);
        }

        private boolean reachesThroughComponents(String named) {
            // A class of another repository holds nothing of this one, so there is no record of
            // theirs to open. Which is a definition and not an absence: what a name of theirs
            // reaches was answered by the arguments written beside it.
            if (!named.startsWith(OF_THIS_REPOSITORY) || !opened.add(named)) {
                return false;
            }
            ClassModel owner = compiled.read(named);
            Optional<RecordAttribute> components = owner.findAttribute(Attributes.record());
            if (components.isEmpty()) {
                return false;
            }
            // Replaced and not layered: a record is static wherever it is written, so the variables
            // a component names are its own and never the ones in scope where it was reached.
            Scope inside = Scope.of(named, typeParametersOf(owner));
            for (RecordComponentInfo component : components.get().components()) {
                if (reaches(componentSignature(component), inside)) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * What the type variables in scope stand for.
     *
     * <p>A frame for each declaration that binds any — the class's, and the method's standing on
     * it — kept as a chain and not as one map. A binding is two things at once: which parameter a
     * name is, and the world that parameter's own bound is written in. A method naming a letter its
     * class also names binds it for what the method writes, while what the class declared under
     * that letter goes on standing for what its own frame says; one map keyed by the name holds the
     * first of those and loses the second, and a bound read in the wrong frame answers with another
     * variable's declaration.
     *
     * <p>Built by whoever holds a signature, because which bindings are in scope is a fact about
     * where the signature was written and not one the signature carries.
     */
    record Scope(String owner, Map<String, Signature.TypeParam> declared, Optional<Scope> under) {

        static Scope of(String owner, List<Signature.TypeParam> declared) {
            return new Scope(owner, byName(declared), Optional.empty());
        }

        /** This scope with a frame over it, which is where a repeated name is bound from here. */
        Scope and(String owner, List<Signature.TypeParam> declared) {
            return new Scope(owner, byName(declared), Optional.of(this));
        }

        private static Map<String, Signature.TypeParam> byName(List<Signature.TypeParam> declared) {
            Map<String, Signature.TypeParam> out = new LinkedHashMap<>();
            for (Signature.TypeParam each : declared) {
                out.put(each.identifier(), each);
            }
            return out;
        }

        /** The frames this stands on, outermost first, this one last. */
        List<Scope> frames() {
            List<Scope> out = new ArrayList<>(under.map(Scope::frames).orElse(List.of()));
            out.add(this);
            return out;
        }

        /** The binding {@code identifier} names here, and the frame that declared it. */
        Resolved bindingOf(String identifier) {
            Signature.TypeParam found = declared.get(identifier);
            if (found != null) {
                return new Resolved(new Binding(owner, identifier, found), this);
            }
            return under.map(frame -> frame.bindingOf(identifier))
                    .orElseThrow(() -> new AssertionError("the type variable " + identifier
                            + " is bound nowhere this reading was given, so what it stands for is a"
                            + " question this cannot answer; the scope holds " + names()));
        }

        private List<String> names() {
            List<String> out = new ArrayList<>();
            for (Scope frame : frames()) {
                out.addAll(frame.declared().keySet());
            }
            return out;
        }

        /**
         * These frames as a report names them: what declared each, and what each declared.
         *
         * <p>Here because the frames are. A report about {@code S} says what {@code S} was declared
         * to be, and where two frames declare that letter it says which of them is being spoken of;
         * a reader that held only the innermost would name one of the two and call it the answer.
         */
        String shown() {
            List<String> named = new ArrayList<>();
            for (Scope frame : frames()) {
                named.add(frame.owner() + frame.shownDeclared());
            }
            return String.join(".", named);
        }

        /** What this frame alone declared, or nothing where it declares no variable. */
        String shownDeclared() {
            if (declared.isEmpty()) {
                return "";
            }
            List<String> each = new ArrayList<>();
            for (Signature.TypeParam parameter : declared.values()) {
                List<Signature.RefTypeSig> bounds = boundsOf(parameter);
                each.add(bounds.isEmpty() ? parameter.identifier()
                        : parameter.identifier() + " extends " + String.join(" & ",
                                bounds.stream().map(Signatures::shown).toList()));
            }
            return "<" + String.join(", ", each) + ">";
        }

        /** A variable's binding beside the frame it was declared in, which is where its bound is
         *  read: that frame's own names, and the ones it stands on. */
        record Resolved(Binding binding, Scope declaredIn) {
        }

        /** One type variable, told apart by what declared it: a method may name a variable its own
         *  class also names, and the two are two. */
        record Binding(String owner, String identifier, Signature.TypeParam declared) {

            List<Signature.RefTypeSig> bounds() {
                return boundsOf(declared);
            }
        }
    }
}
