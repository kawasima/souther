package souther.compiler.check;

import souther.compiler.ast.Hir;
import souther.compiler.types.Type;
import souther.compiler.types.ValueName;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * What a module's declarations settle about one parameter a {@code let} wrote.
 *
 * <p>Told apart by what a reader may do with it rather than by which kind of position it fills. Two
 * of these are types a name in a body has, and one of the two is also a type an author may write
 * where the name is — which is the difference between what is put in a hint and what is answered
 * when the name is asked about.
 */
public sealed interface ParameterFact {

    /** An input, as the signature says it arrives. */
    record TypedInput(Hir.FnParam written, Type arrives) implements ParameterFact {}

    /** An injected behavior, as what it takes and answers. */
    record TypedInjection(Hir.FnParam written, Type takes) implements ParameterFact {}

    /** A parameter this revision settles nothing about. */
    record Untyped(Hir.FnParam written) implements ParameterFact {}

    /**
     * What the declarations say about each parameter every {@code let} of {@code resolved} wrote.
     *
     * <p>One classification, read by everything that is about a parameter. What a hint is drawn for
     * and what a name in a body is typed by are two uses of one answer, and worked out apart they
     * were two: the reading that drew hints and the reading that typed bodies each decided for
     * itself which parameters a signature speaks for, and both decided it by comparing the length of
     * the {@code let}'s parameter list with the length of the signature. A behavior with a
     * {@code depends on} clause takes the behaviors it is injected with beside its inputs, so those
     * two lengths differ for every one of them and both readings left the whole definition out.
     *
     * <p>Which parameters the signature speaks for is {@link SpecImplementation}'s to say, and is
     * asked here rather than worked out from the lengths. What is added is the types: the signature
     * says what arrives at an input, and an injected parameter is a behavior this module names,
     * whose signature says what it takes and answers.
     *
     * <p>Answered whether or not the definition is one the checker will accept. A parameter the
     * declaration accounts for nothing for is {@link Untyped}, which is the same answer a reader
     * gets for a parameter of a behavior whose signature this revision could not work out — nothing
     * here says a type, and nothing here says the definition is wrong either.
     *
     * <p>{@code reachable} is empty where this revision cannot say what the module reaches. An
     * injected parameter is then {@link Untyped}, which is what a name the map does not hold comes
     * to as well: this reading asks that map for one behavior it already has in hand, so a map that
     * could not be worked out and a map without that behavior in it are one question to it.
     */
    static List<ParameterFact> of(Hir.Module resolved, Map<String, DeclaredSig> signatures,
                                  Map<ValueName.Behavior, Sig> reachable) {
        List<ParameterFact> facts = new ArrayList<>();
        SpecImplementation.implementationsOf(resolved)
                .forEach((behavior, implemented) -> {
                    DeclaredSig declared = signatures.get(behavior);
                    for (SpecImplementation.ParameterBinding binding : implemented.bindings()) {
                        facts.add(factOf(binding, declared, reachable));
                    }
                });
        return List.copyOf(facts);
    }

    /** What one parameter is, given what the module's declarations say. */
    private static ParameterFact factOf(SpecImplementation.ParameterBinding binding,
                                        DeclaredSig declared,
                                        Map<ValueName.Behavior, Sig> reachable) {
        return switch (binding) {
            // At the position the declaration holds it at, and not tested against the declaration's
            // length first. The division gave the position by walking the same parameters, so a
            // test would be an answer checked against itself, and answering `Untyped` where it
            // failed would put back the silence this reading exists to remove.
            case SpecImplementation.ParameterBinding.AnInput input -> declared == null
                    ? new Untyped(input.written())
                    : new TypedInput(input.written(), declared.inputs().get(input.at()).type());
            case SpecImplementation.ParameterBinding.AnInjection injected ->
                    injectionFact(injected, reachable);
            // A clause that reaches no declaration names no signature to read, and a parameter the
            // declaration asks for no position for is spoken for by nothing.
            case SpecImplementation.ParameterBinding.Unanswered unanswered ->
                    new Untyped(unanswered.written());
            case SpecImplementation.ParameterBinding.Extraneous extraneous ->
                    new Untyped(extraneous.written());
        };
    }

    /**
     * An injected parameter, typed by the signature of the behavior it is handed.
     *
     * <p>What arrives there is that behavior with what it depends on already supplied, so what a
     * body may do with the name is call it with the inputs the declaration names. Untyped where this
     * revision has no signature for it, which is the module it is declared in still being read.
     */
    private static ParameterFact injectionFact(
            SpecImplementation.ParameterBinding.AnInjection injected,
            Map<ValueName.Behavior, Sig> reachable) {
        Sig injects = reachable.get(injected.behavior());
        return injects == null
                ? new Untyped(injected.written())
                : new TypedInjection(injected.written(),
                        Type.fn(injects.inputTypes(), injects.outputType()));
    }
}
