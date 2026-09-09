package souther.compiler.coverage;

import souther.compiler.core.Core;
import souther.compiler.types.ConstructOccurrence;
import souther.compiler.types.ModelOccurrence;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Where a run through each construct the model states is recorded.
 *
 * <p>The one crossing from what a model states to what a run writes down. A rule is read where the
 * language's operations stand and a run through it is recorded where they are expanded, so the two
 * are read off different trees — and what they agree about is the construct of the model
 * ({@link ModelOccurrence}), which is what this is keyed by.
 *
 * <p><b>An address and not a name.</b> What tells one construct of the model from another is the
 * key; what is answered is where the emitter numbered it. Keyed the other way round — a name handed
 * out by the walk that numbered the sites — the question "which construct is this" would be as
 * complete as "what was measured about it", and a construct nothing measures would have no name.
 *
 * <p><b>One construct of the model, several materialisations of it.</b> A library operation may
 * evaluate a closure it was handed more than once — {@code List.distinctBy} asks its key twice —
 * so a comparison the author wrote once is written into the tree that runs twice, and the model
 * states one rule all the same. What differs between them is where inside the operation's body it
 * was applied, which the model does not state; so they are materialisations of one construct, and
 * this holds them all rather than refusing the second.
 *
 * <p><b>Empty where the emitter numbered nothing.</b> A comparison behind an abort is one no run
 * reaches, so the plan numbers no site for it — and what comes back here says that and no more. It
 * does not say a run never answers through the comparison, and it does not say the arrival at its
 * line could not be projected: those are two further questions, and reading either off this absence
 * would be answering them with what stands beside them.
 */
public final class ComparisonEmissionIndex {

    /**
     * One materialisation of a construct of the model in the tree that runs.
     *
     * <p>The site is the materialisation's and not the construct's: an operation applying a closure
     * twice writes the comparison twice, and the plan may number one of them and not the other — a
     * comparison behind an abort on one path and reached on the other. Held per materialisation, a
     * reader is told which of them a run is recorded at rather than being handed one answer for
     * both.
     */
    public record EmittedComparison(ConstructOccurrence occurrence,
                                    Optional<ComparisonEmissionSite> site) {

        public EmittedComparison {
            if (occurrence == null || site == null) {
                throw new IllegalArgumentException(
                        "a materialisation is one the numbering counted, with or without a site");
            }
        }
    }

    private final Map<ModelOccurrence, List<EmittedComparison>> emitted;

    private ComparisonEmissionIndex(Map<ModelOccurrence, List<EmittedComparison>> emitted) {
        this.emitted = emitted;
    }

    /**
     * The index of one module's emitted bodies, against the plan that numbered them.
     *
     * <p>Both taken together because the answer is about the pair: the bodies say which constructs
     * of the model they hold, and the plan says which of those it numbered. Handed a plan of another
     * derivation, what came back would be addresses of somebody else's numbering, and the numbering
     * says so ({@link NumberingIdentity}) only when a site is asked about rather than when the index
     * is built.
     *
     */
    public static ComparisonEmissionIndex of(ModuleBodies of, CoverageSites.Plan plan) {
        Map<ModelOccurrence, Map<ConstructOccurrence, EmittedComparison>> emitted =
                new LinkedHashMap<>();
        for (Map.Entry<String, Core> body : of.bodies().entrySet()) {
            walk(body.getValue(), plan, emitted);
        }
        return new ComparisonEmissionIndex(copy(emitted));
    }

    /** The same over one body, for a reader that holds one rather than the module's. */
    public static ComparisonEmissionIndex ofBody(Core body, CoverageSites.Plan plan) {
        Map<ModelOccurrence, Map<ConstructOccurrence, EmittedComparison>> emitted =
                new LinkedHashMap<>();
        walk(body, plan, emitted);
        return new ComparisonEmissionIndex(copy(emitted));
    }

    private static Map<ModelOccurrence, List<EmittedComparison>> copy(
            Map<ModelOccurrence, Map<ConstructOccurrence, EmittedComparison>> of) {
        Map<ModelOccurrence, List<EmittedComparison>> out = new LinkedHashMap<>();
        of.forEach((states, made) -> out.put(states, List.copyOf(made.values())));
        return Map.copyOf(out);
    }

    private static void walk(
            Core e, CoverageSites.Plan plan,
            Map<ModelOccurrence, Map<ConstructOccurrence, EmittedComparison>> emitted) {
        // Which comparison of the emitted tree this is, asked of the catalog, which is what the
        // numbering was taken over. A node it does not hold is one no site was planned for and one
        // no rule is read off — a comparison this compiler composed, or one of another module.
        ConstructOccurrence which = e instanceof Core.Binary binary
                ? plan.comparisons().occurrenceAt(binary).orElse(null) : null;
        if (which != null) {
            // Only where the model states something. A comparison inside one of the language's own
            // operations is materialised once per call of it and the model states none of them, so
            // asking them all for one place would be one key over as many places as the body calls
            // the operation.
            //
            // Collected under the name the catalog gave it. What is being collected is the
            // materialisations of a rule, and two of them are two comparisons of the body — so a
            // place the walk reaches twice is one place, and holding them by name is what says so
            // rather than a scan of what is already there.
            ModelOccurrence.statedAt(((Core.Binary) e).occurrence()).ifPresent(states ->
                    emitted.computeIfAbsent(states, _ -> new LinkedHashMap<>())
                            .putIfAbsent(which, new EmittedComparison(which,
                                    plan.emissionSiteOf(which))));
        }
        Core.forEachChild(e, child -> walk(child, plan, emitted));
    }

    /**
     * Every materialisation of {@code occurrence} in the tree that runs, in the order the walk met
     * them.
     *
     * <p>Empty where the emitted tree holds none, which is the two readings disagreeing about the
     * body rather than a construct nothing was measured about — a caller raises rather than
     * answering around it.
     */
    public List<EmittedComparison> madeFor(ModelOccurrence occurrence) {
        return emitted.getOrDefault(occurrence, List.of());
    }
}
