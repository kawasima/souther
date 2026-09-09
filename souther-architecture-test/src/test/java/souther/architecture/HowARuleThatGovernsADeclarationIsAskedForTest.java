package souther.architecture;

import souther.architecture.WhatASignatureReaches.Scope;
import souther.compiler.ast.Hir;
import souther.compiler.check.DerivedSymbols;
import souther.compiler.check.ExpandedClauseLookup;
import souther.compiler.check.Symbols;
import souther.test.Signatures;

import org.junit.jupiter.api.Test;

import java.lang.classfile.Attributes;
import java.lang.classfile.ClassModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.MethodSignature;
import java.lang.classfile.attribute.SignatureAttribute;
import java.lang.reflect.AccessFlag;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How a caller asks for the rules that govern a declaration.
 *
 * <p>Which declarations govern one — its own and every one a spread reaches — is a walk over the
 * declarations, and it is {@code TypeOps}' walk. Two things are held of its surface, and they are
 * two because what they are about is two.
 *
 * <p><b>The walk is given a name and reads the declarations itself.</b> A caller that passed a
 * declaration node beside the world would be deciding the representation of the declaration asked
 * about while the world decided the ones under it, and nothing either of them held would say the
 * two were the same reading.
 *
 * <p><b>What a clause states is read from whatever owns that representation.</b> The settled form is
 * the derived world's, so a walk that answers with one takes {@link DerivedSymbols} and not the
 * reader that does not name a stage. The expanded form is
 * {@code ExpandedClauseLookup}'s — one question, one input, no node to fall back on — and that
 * interface holds its own answer; a rule here that demanded the derived world of it as well would
 * be reporting a walk that is already closed, by a stronger arrangement than this one.
 *
 * <p>Both are read through {@link WhatASignatureReaches}, so that what a walk is handed is read for
 * what the declaration guarantees rather than for how it was written. A world wrapped in a record,
 * named through a type variable's bound, or written as the argument of a container is the world the
 * caller has to have, and a rule that read only the types spelled beside the parameters would be
 * answered by any of those rewrites.
 *
 * <p>What this does not hold is that no walk of the kind can be written anywhere else. A class
 * reaching the declarations itself could compose its own, and no reading of a signature would say
 * so. The first line against that is which methods are reachable at all, and this is the second.
 */
class HowARuleThatGovernsADeclarationIsAskedForTest {

    private static final String TYPE_OPS = "souther/compiler/check/TypeOps";

    private static final String A_CLAUSE = internal(Hir.InvariantClause.class);
    private static final String A_DECLARATION = internal(Hir.Def.class);
    private static final String THE_DERIVED_WORLD = internal(DerivedSymbols.class);
    private static final String THE_LOOKUP = internal(ExpandedClauseLookup.class);

    /** The declaration worlds, read off the sealed interface rather than listed: a world added to it
     *  is one a walk can be declared to take, and these rules have to see it arrive. */
    private static final Set<String> THE_WORLDS = worlds();

    private static final CompiledOutputs COMPILED =
            CompiledOutputs.ofWhatThisRepositoryPublishes();

    private static final WhatASignatureReaches READING = new WhatASignatureReaches(COMPILED);

    private static Set<String> worlds() {
        Set<String> out = new LinkedHashSet<>();
        out.add(internal(Symbols.class));
        for (Class<?> each : Symbols.class.getPermittedSubclasses()) {
            out.add(internal(each));
        }
        return out;
    }

    /** Every declaration node, read off the sealed interface for the same reason. */
    private static Set<String> declarationNodes() {
        Set<String> out = new LinkedHashSet<>();
        out.add(A_DECLARATION);
        for (Class<?> each : Hir.Def.class.getPermittedSubclasses()) {
            out.add(internal(each));
        }
        return out;
    }

    private static String internal(Class<?> type) {
        return type.getName().replace('.', '/');
    }

    /**
     * One method as these rules read it.
     *
     * <p>The arguments and the answer apart, because which half a type is named in is what is being
     * asked: a walk is handed a world and answers with clauses. The type parameters are neither
     * half. They are what the variables the two halves write stand for, and a rule that started a
     * reading at them would find a bound the method never uses.
     */
    private record Read(MethodSignature signature, Scope scope) {

        boolean takesOneOf(Set<String> wanted) {
            return READING.anyOf(signature.arguments(), scope, wanted);
        }

        boolean answersWithOneOf(Set<String> wanted) {
            return READING.reaches(signature.result(), scope, wanted);
        }

        /**
         * As a failure names it.
         *
         * <p>Every frame the method stands in, and not only its own: a report about {@code S} has
         * to say what {@code S} was declared to be, and a method writing a letter its class
         * declared is answered for by the class. Shown and not read as roots — which of the two a
         * type parameter is is settled by the reading, not by whether a reader can see it.
         */
        String shown() {
            String takes = signature.arguments().stream().map(Signatures::shown)
                    .collect(Collectors.joining(", "));
            return scope.shown() + "(" + takes + "): " + Signatures.shown(signature.result());
        }
    }

    /**
     * The methods another class can ask this one for.
     *
     * <p>Not the public ones. What these rules are about is what a caller elsewhere may reach, and a
     * package-private method is reachable by every class beside it — the walk that answers with the
     * settled form is one of those, so a reading of the public surface alone would be a rule about a
     * set its own subject is not in. A private method is the class's own business and is held by
     * what its neighbours here do with it, which is a thing to read rather than to check.
     */
    private static List<Read> reachableMethods() {
        ClassModel typeOps = COMPILED.read(TYPE_OPS);
        Scope ofTheClass = Scope.of(TYPE_OPS, WhatASignatureReaches.typeParametersOf(typeOps));
        List<Read> out = new ArrayList<>();
        for (MethodModel method : typeOps.methods()) {
            if (method.flags().has(AccessFlag.PRIVATE) || method.flags().has(AccessFlag.SYNTHETIC)
                    || method.methodName().stringValue().startsWith("<")) {
                continue;
            }
            // The generic signature where there is one, and the descriptor read as one where there
            // is not. A clause reached through a list, an `Optional`, a map value or a record is a
            // clause a caller gets, and the containers erase to something naming no clause at all —
            // so a rule read off the descriptor alone would hold of exactly the shape nobody writes.
            MethodSignature signature = method.findAttribute(Attributes.signature())
                    .map(SignatureAttribute::asMethodSignature)
                    .orElseGet(() -> MethodSignature.of(method.methodTypeSymbol()));
            // The method's own frame is named for it, which is where a report gets the name from.
            out.add(new Read(signature, ofTheClass.and(method.methodName().stringValue(),
                    signature.typeParameters())));
        }
        return out;
    }

    /** The walks: what answers with a rule of a declaration, in either representation. */
    private static List<Read> walksOverWhatGoverns() {
        List<Read> found = new ArrayList<>();
        for (Read method : reachableMethods()) {
            if (method.answersWithOneOf(Set.of(A_CLAUSE)) && method.takesOneOf(THE_WORLDS)) {
                found.add(method);
            }
        }
        return found;
    }

    @Test
    void aWalkOverWhatGovernsIsGivenANameAndNotADeclaration() {
        List<String> handedANode = new ArrayList<>();
        for (Read walk : walksOverWhatGoverns()) {
            if (walk.takesOneOf(declarationNodes())) {
                handedANode.add(walk.shown());
            }
        }

        assertEquals(List.of(), handedANode,
                "a walk over what governs a declaration reads the declarations from the world it"
                        + " was handed, so that the one asked about and the ones a spread reaches"
                        + " are one reading");
    }

    @Test
    void aWalkThatAnswersWithTheSettledFormTakesTheDerivedWorld() {
        List<String> loose = new ArrayList<>();
        for (Read walk : walksOverWhatGoverns()) {
            // What the expanded form states is the lookup's to answer, and it holds that itself.
            if (walk.takesOneOf(Set.of(THE_LOOKUP))) {
                continue;
            }
            if (!walk.takesOneOf(Set.of(THE_DERIVED_WORLD))) {
                loose.add(walk.shown());
            }
        }

        assertEquals(List.of(), loose,
                "the settled form of a clause is the derived world's, so a walk that answers with"
                        + " one says which world it read");
    }

    /**
     * And each rule above is about something.
     *
     * <p>Held for the two representations apart, because the rules are. The settled walk is the one
     * the derived world's rule is about and the expanded walks are the ones it passes over, so a
     * count of both together is a count that stays right while either goes to nothing — which is how
     * a rule comes to hold of an empty set and say so to nobody.
     */
    @Test
    void thereAreWalksOfEachKind() {
        List<String> settled = new ArrayList<>();
        List<String> expanded = new ArrayList<>();
        for (Read walk : walksOverWhatGoverns()) {
            (walk.takesOneOf(Set.of(THE_LOOKUP)) ? expanded : settled).add(walk.shown());
        }

        assertFalse(settled.isEmpty(),
                "a walk here answers with the settled form, which is what the derived world's rule"
                        + " is about");
        assertFalse(expanded.isEmpty(),
                "a walk here answers with the expanded form, which that rule passes over because"
                        + " the lookup holds it");
    }

    /**
     * And a walk is found through what its answer is made of, not through what its answer is called.
     *
     * <p>The walks here answer with a value that names no clause: what a caller gets the clauses out
     * of is a record the answer holds. A reading that took the types written in the signature would
     * find none of them, and {@link #thereAreWalksOfEachKind} would be failing for that reason
     * rather than saying there is no walk — so this is held on its own.
     */
    @Test
    void aClauseInsideWhatAWalkAnswersWithIsOneThisReads() {
        List<String> namingNoClause = new ArrayList<>();
        for (Read walk : walksOverWhatGoverns()) {
            if (!walk.signature().result().signatureString().contains(A_CLAUSE)) {
                namingNoClause.add(walk.shown());
            }
        }

        assertFalse(namingNoClause.isEmpty(),
                "a walk here answers with something that names no clause and holds one, which is"
                        + " what makes following a record the thing to do");
    }

    /** Held so the readings above are over the members that class writes and not over a class file
     *  that was not found: a scan of nothing reports nothing loose. */
    @Test
    void theClassTheseRulesAreAboutWasRead() {
        assertTrue(reachableMethods().size() > 1,
                "the class these rules are about was read and has a public surface");
    }
}
