package souther.compiler.check;

import souther.compiler.ast.Hir;
import souther.compiler.types.BindingId;
import souther.compiler.types.ValueName;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The formal parameters the {@code let} implementing a behavior is required to take (spec
 * §fn-declaration, §depends-on), and what each parameter it actually wrote stands for.
 *
 * <p>The behavior's inputs, then the behaviors it depends on in the order they were declared. The
 * order is not a matter of layout: it is what {@link SpecChecker} holds an implementation to, and
 * writing an injected parameter out of that order is E1615.
 *
 * <p>That boundary — where the inputs stop and the injected behaviors begin — is written here and
 * nowhere else. Every reader that needs it asks {@link #align}: the checker deciding whether an
 * implementation is legal, the emitter binding a local to an input, an editor saying what a name in
 * a body is. Worked out again at a reader, it is the same rule with a strictness of its own, and the
 * three that existed disagreed about what a list too short to divide meant.
 *
 * <p>Inside, the declaration and the definition are two things and are read in that order. What the
 * behavior requires is worked out from its inputs and its clause alone, and both things this offers
 * — the list a caller writing an implementation is held to, and the division of one already
 * written — read that. Read the clause twice instead, the two agree because one private method
 * answers both, which is what having one place is supposed to stop anybody resting on.
 *
 * <p>Nothing here is about how a parameter is written. Where it goes in a line, and whether the line
 * breaks, is the formatter's.
 */
public final class SpecImplementation {

    private SpecImplementation() {}

    /**
     * One position of the parameter list, told apart by what settles its name.
     *
     * <p>A reader has to do something different with each, which is why they are three and not one
     * name and a flag. What an editor may offer as a hole is exactly what nothing here settles.
     *
     * <p>What the behavior asks for, and not what an implementation wrote. {@link ParameterBinding}
     * is the second question: this list is as long as the declaration requires, and that one is as
     * long as the {@code let} the author typed.
     */
    public sealed interface Parameter {

        /**
         * An input. Its position and its type come from the behavior; its name does not — the
         * implementation may call it what it likes — so the behavior's own spelling is a suggestion.
         */
        record Input(String nameSuggestion) implements Parameter {}

        /**
         * An injected behavior. Its position and its name are both settled: it names the behavior it
         * injects, and an implementation that spells it otherwise is refused.
         */
        record Injected(String name) implements Parameter {}

        /**
         * A {@code depends on} entry that reaches no declaration.
         *
         * <p>It takes a position — an implementation still has to have a parameter there — and
         * settles no name for it, since the name it would be held to is the one that was not found.
         * That the clause names nothing is reported where the clause is written, so nothing here
         * says it again.
         */
        record Unanswered() implements Parameter {}
    }

    /**
     * One parameter a {@code let} wrote, and what the declaration above it says that parameter is.
     *
     * <p>One of these per parameter the author typed, however many the behavior asked for. A
     * definition is read while it is being written, and a reader handed only the ones that lined up
     * would be handed a list whose positions are not the positions in the source.
     *
     * <p>The two ways a parameter can stand for nothing are two arms, because a reader does
     * different things with them. {@link Unanswered} has a place in the declaration and no name to
     * hold it to — the clause that would have named it reaches nothing, said where the clause is
     * written. {@link Extraneous} has no place in the declaration at all. Read as one "unknown",
     * they would be one arm that means "do not ask", and the next reader to want the difference
     * would work it out again from the lengths.
     */
    public sealed interface ParameterBinding {

        /** The parameter as the {@code let} wrote it. */
        Hir.FnParam written();

        /**
         * An input the behavior declares, with the declaration it stands for.
         *
         * <p>The declaration itself and not only where it sits. What a reader wants of an input is
         * the type on the {@code behavior} line, and one holding the position alone would go back to
         * the signature and index into it — which is this alignment made a second time, through a
         * number. {@code at} is here because the checked signature is a list of its own and is
         * reached by position.
         */
        record AnInput(Hir.FnParam written, Hir.Param declared, int at) implements ParameterBinding {}

        /** A parameter the {@code depends on} clause names a behavior for. */
        record AnInjection(Hir.FnParam written, ValueName.Behavior behavior)
                implements ParameterBinding {}

        /** A parameter standing where a {@code depends on} entry that reaches no declaration would
         *  have named one. */
        record Unanswered(Hir.FnParam written) implements ParameterBinding {}

        /** A parameter the declaration asks for no position for. */
        record Extraneous(Hir.FnParam written) implements ParameterBinding {}
    }

    /**
     * A definition read as the implementation of a behavior: every parameter it wrote, and what the
     * declaration says each of them is.
     *
     * <p>A value rather than the list alone, so that having found a definition and having found one
     * that takes nothing stay two answers. A behavior of no inputs is implemented by a {@code let}
     * of no parameters, and a caller handed an empty list for both would have to decide which it was
     * looking at.
     *
     * <p>The division stands whether or not the definition is legal. What a reader does about an
     * implementation that is not is its own: the checker reports it, the emitters refuse to run on
     * it, and an editor answers what it can about the parameters that did line up. So the facts
     * about the whole shape are here to be asked, and none of them is enforced here — a reading that
     * refused to divide would be deciding for all three.
     */
    public static final class Implemented {

        private final Hir.FnDef definition;
        private final List<ParameterBinding> bindings;
        private final Shape shape;

        private Implemented(Hir.FnDef definition, List<ParameterBinding> bindings, Shape shape) {
            this.definition = definition;
            this.bindings = List.copyOf(bindings);
            this.shape = shape;
        }

        /** The {@code let} this is the reading of. */
        public Hir.FnDef definition() {
            return definition;
        }

        /** What each parameter it wrote stands for, in the order it wrote them. */
        public List<ParameterBinding> bindings() {
            return bindings;
        }

        /** The parameters the declaration says are its inputs, in order, each with the input it
         *  stands for. */
        public List<ParameterBinding.AnInput> declaredInputs() {
            List<ParameterBinding.AnInput> inputs = new ArrayList<>();
            for (ParameterBinding binding : bindings) {
                if (binding instanceof ParameterBinding.AnInput input) {
                    inputs.add(input);
                }
            }
            return List.copyOf(inputs);
        }

        /**
         * Which behavior each injected parameter stands for, under the binding a body reads it
         * through.
         *
         * <p>A name written at a call inside the body denotes the parameter and not the behavior,
         * so what a reader typing that call needs is the binding. Read off the division and not
         * paired with the clause by name: an implementation names its own parameters, and two
         * modules may declare a behavior of one name.
         *
         * <p>Here rather than at any of the readers, for the reason {@link #inputs} is: a caller
         * that filtered the bindings itself would be deciding which arm answers this question, and
         * three of them would each have decided.
         */
        public Map<BindingId, ValueName.Behavior> injectedBindings() {
            Map<BindingId, ValueName.Behavior> injected = new LinkedHashMap<>();
            for (ParameterBinding binding : bindings) {
                if (binding instanceof ParameterBinding.AnInjection stands) {
                    injected.put(stands.written().binder().id(), stands.behavior());
                }
            }
            return Collections.unmodifiableMap(injected);
        }

        /** The same, as the parameters alone — what a caller binding a local to an input needs. */
        public List<Hir.FnParam> inputs() {
            List<Hir.FnParam> written = new ArrayList<>();
            for (ParameterBinding.AnInput input : declaredInputs()) {
                written.add(input.written());
            }
            return List.copyOf(written);
        }

        /** Whether the definition wrote a parameter for each position the declaration asks for, and
         *  no others. Where it did not, E1615 says so where the definition is written. */
        public boolean hasExactArity() {
            return definition.params().size() == shape.size();
        }

        /**
         * Whether every {@code depends on} entry reaches a declaration.
         *
         * <p>Asked of what the declaration requires rather than of the parameters. A definition
         * that wrote too few parameters has no position for the entry that reaches nothing, and a
         * reading that looked for one would call the clause answered because the parameter list ran
         * out first.
         */
        public boolean hasAnsweredDependencies() {
            return shape.everyDependencyAnswered();
        }

        /**
         * Whether the parameters line up with the declaration and every dependency reaches one.
         *
         * <p>What an implementation has to be for a reader to divide it and act on every part. It is
         * not the same as the implementation being legal: a parameter standing for an injection it
         * spells wrongly is placed and is refused (E1615), and this says nothing about it.
         */
        public boolean hasCompleteShape() {
            return hasExactArity() && hasAnsweredDependencies();
        }
    }

    /**
     * One position a behavior requires an implementation to have, as the declaration settles it.
     *
     * <p>About the declaration and nothing else. Which parameter of a {@code let} fills a position
     * is {@link ParameterBinding}'s, and is a second question — this list is as long as the
     * declaration says and that one is as long as the {@code let} the author typed.
     */
    private sealed interface RequiredParameter {

        /** A declared input, at the position the signature holds its type at. */
        record Input(Hir.Param declared, int at) implements RequiredParameter {}

        /** A behavior the clause names. */
        record Injection(ValueName.Behavior behavior) implements RequiredParameter {}

        /** A clause entry that reaches no declaration. */
        record Unanswered() implements RequiredParameter {}
    }

    /**
     * What a behavior requires of whatever implements it.
     *
     * <p>The inputs first and then the clause, which is where that order is written. Both readers of
     * the rule — the list a caller writing a declaration is offered, and the division of the
     * parameters one already wrote — are projections of this, so what the clause reaches is decided
     * once. Two walks of the clause agreed because the same private reading answered both, which is
     * the shape this component exists to stop being relied on.
     */
    private record Shape(List<RequiredParameter> parameters) {

        private Shape {
            parameters = List.copyOf(parameters);
        }

        /** How many parameters an implementation is required to write. */
        int size() {
            return parameters.size();
        }

        /** Whether every entry of the clause reaches a declaration. */
        boolean everyDependencyAnswered() {
            for (RequiredParameter required : parameters) {
                if (required instanceof RequiredParameter.Unanswered) {
                    return false;
                }
            }
            return true;
        }
    }

    /** What {@code spec} requires, which is the one reading of its inputs and its clause. */
    private static Shape shapeOf(Hir.SpecBehavior spec) {
        List<RequiredParameter> required =
                new ArrayList<>(spec.params().size() + spec.dependsOn().size());
        for (int at = 0; at < spec.params().size(); at++) {
            required.add(new RequiredParameter.Input(spec.params().get(at), at));
        }
        for (Hir.Var dependency : spec.dependsOn()) {
            ValueName.Behavior named = behaviorReached(dependency);
            required.add(named == null
                    ? new RequiredParameter.Unanswered()
                    : new RequiredParameter.Injection(named));
        }
        return new Shape(required);
    }

    /** What an implementation of {@code spec} is required to take, in order. */
    public static List<Parameter> parameters(Hir.SpecBehavior spec) {
        List<Parameter> offered = new ArrayList<>();
        for (RequiredParameter required : shapeOf(spec).parameters()) {
            offered.add(switch (required) {
                case RequiredParameter.Input(Hir.Param declared, int _) ->
                        new Parameter.Input(declared.name());
                case RequiredParameter.Injection(ValueName.Behavior behavior) ->
                        new Parameter.Injected(behavior.name());
                case RequiredParameter.Unanswered _ -> new Parameter.Unanswered();
            });
        }
        return List.copyOf(offered);
    }

    /**
     * {@code definition} read as the implementation of {@code spec}.
     *
     * <p>The one place the inputs are told from the injections. A definition writes them in one
     * list — the behavior's inputs, then what it depends on — and which is which is settled by how
     * many inputs the behavior declares and by nothing about the parameters themselves.
     *
     * <p>Total. A definition may have too few parameters to fill the positions the declaration asks
     * for, or more than it asks for at all, and both are states an editor reads a module in as it is
     * typed. What is wrong with either is reported where the definition is written; here they are a
     * shorter list of bindings and an {@link ParameterBinding.Extraneous} arm, and
     * {@link Implemented#hasExactArity} is the fact a caller that may not go on asks for.
     */
    public static Implemented align(Hir.SpecBehavior spec, Hir.FnDef definition) {
        Shape shape = shapeOf(spec);
        List<Hir.FnParam> written = definition.params();
        List<ParameterBinding> bindings = new ArrayList<>(written.size());
        for (int at = 0; at < shape.size() && at < written.size(); at++) {
            Hir.FnParam wrote = written.get(at);
            bindings.add(switch (shape.parameters().get(at)) {
                case RequiredParameter.Input(Hir.Param declared, int held) ->
                        new ParameterBinding.AnInput(wrote, declared, held);
                case RequiredParameter.Injection(ValueName.Behavior behavior) ->
                        new ParameterBinding.AnInjection(wrote, behavior);
                case RequiredParameter.Unanswered _ -> new ParameterBinding.Unanswered(wrote);
            });
        }
        for (int at = shape.size(); at < written.size(); at++) {
            bindings.add(new ParameterBinding.Extraneous(written.get(at)));
        }
        return new Implemented(definition, bindings, shape);
    }

    /**
     * Each behavior of {@code module} that a {@code let} implements, under the name it is declared
     * by.
     *
     * <p>Every behavior in one walk, rather than a way to ask about one. Asked one at a time, a
     * caller looking at each behavior of a module searches its definitions once per behavior, and
     * every reader of this rule is a caller looking at each behavior of a module.
     *
     * <p>Among what the module declared, and not among what it took on to emit. What a module emits
     * without declaring is a recursion another module wrote and a method minted for a row's operand
     * ({@code Hir.Module#takenOn}); a behavior's implementation is a {@code let} of its name and is
     * always a declaration. {@link Requirements#implementationOf} decides whether a behavior has one
     * by looking in the same place, so a name found here is a name that reading called implemented.
     *
     * <p>A behavior with no definition is absent rather than present with nothing: an injected
     * behavior and an unwritten one both reach this and neither has parameters to divide.
     */
    public static Map<String, Implemented> implementationsOf(Hir.Module module) {
        Map<String, Hir.FnDef> defined = new LinkedHashMap<>();
        for (Hir.FnDef fn : module.fns()) {
            defined.put(fn.name(), fn);   // the last, as every reader keying these by name keeps
        }
        Map<String, Implemented> implementations = new LinkedHashMap<>();
        for (Hir.BehaviorDef declared : module.behaviors()) {
            if (declared instanceof Hir.SpecBehavior spec) {
                Hir.FnDef definition = defined.get(spec.name());
                if (definition != null) {
                    implementations.put(spec.name(), align(spec, definition));
                }
            }
        }
        // In the order the module declares them, so that a reader listing what it found lists it the
        // way the source reads.
        return Collections.unmodifiableMap(implementations);
    }

    /**
     * The behavior {@code named} reaches, or null where resolution found none.
     *
     * <p>Asked of the declaration and not of the name it was written under. A call to another
     * module's behavior and a call to one this module declares can be written the same, and what a
     * requirement is about is one of the two.
     */
    private static ValueName.Behavior behaviorReached(Hir.Var named) {
        return named.answered() != null
                && named.answered().denotes() instanceof ValueName.Behavior behavior
                ? behavior : null;
    }
}
