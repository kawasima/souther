package souther.compiler.coverage;

import souther.compiler.core.Core;
import souther.compiler.types.ConstructOccurrence;
import souther.compiler.types.ModelOccurrence;

import java.util.LinkedHashMap;
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
 * <p><b>Empty where the emitter numbered nothing.</b> A comparison behind an abort is one no run
 * reaches, so the plan numbers no site for it — and what comes back here says that and no more. It
 * does not say a run never answers through the comparison, and it does not say the arrival at its
 * line could not be projected: those are two further questions, and reading either off this absence
 * would be answering them with what stands beside them.
 */
public final class ComparisonEmissionIndex {

    private final Map<ModelOccurrence, ComparisonEmissionSite> sites;

    private final Map<ConstructOccurrence, ComparisonOccurrence> emitted;

    private ComparisonEmissionIndex(Map<ModelOccurrence, ComparisonEmissionSite> sites,
                                    Map<ConstructOccurrence, ComparisonOccurrence> emitted) {
        this.sites = sites;
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
     * @throws IllegalStateException where two comparisons of one body are one construct of the
     *                               model. They would be one key and two places a run is recorded,
     *                               and a reader holding the key could not say which of them a row
     *                               was owed for
     */
    public static ComparisonEmissionIndex of(ModuleBodies of, CoverageSites.Plan plan) {
        Map<ModelOccurrence, ComparisonEmissionSite> sites = new LinkedHashMap<>();
        Map<ConstructOccurrence, ComparisonOccurrence> emitted = new LinkedHashMap<>();
        Map<ModelOccurrence, ComparisonOccurrence> stated = new LinkedHashMap<>();
        for (Map.Entry<String, Core> body : of.bodies().entrySet()) {
            walk(body.getValue(), body.getKey(), plan, sites, emitted, stated);
        }
        return new ComparisonEmissionIndex(Map.copyOf(sites), Map.copyOf(emitted));
    }

    /** The same over one body, for a reader that holds one rather than the module's. */
    public static ComparisonEmissionIndex ofBody(String behavior, Core body,
                                                 CoverageSites.Plan plan) {
        Map<ModelOccurrence, ComparisonEmissionSite> sites = new LinkedHashMap<>();
        Map<ConstructOccurrence, ComparisonOccurrence> emitted = new LinkedHashMap<>();
        Map<ModelOccurrence, ComparisonOccurrence> stated = new LinkedHashMap<>();
        walk(body, behavior, plan, sites, emitted, stated);
        return new ComparisonEmissionIndex(Map.copyOf(sites), Map.copyOf(emitted));
    }

    private static void walk(Core e, String behavior, CoverageSites.Plan plan,
                             Map<ModelOccurrence, ComparisonEmissionSite> sites,
                             Map<ConstructOccurrence, ComparisonOccurrence> emitted,
                             Map<ModelOccurrence, ComparisonOccurrence> stated) {
        // Which comparison of the emitted tree this is, asked of the catalog, which is what the
        // numbering was taken over. A node it does not hold is one no site was planned for and one
        // no rule is read off — a comparison this compiler composed, or one of another module.
        ComparisonOccurrence which = e instanceof Core.Binary binary
                ? plan.comparisons().occurrenceAt(binary).orElse(null) : null;
        if (which != null) {
            ConstructOccurrence stands = ((Core.Binary) e).occurrence();
            emitted.put(stands, which);
            // Only where the model states something. A comparison inside one of the language's own
            // operations is materialised once per call of it and the model states none of them, so
            // asking them all for one place would be one key over as many places as the body calls
            // the operation.
            ModelOccurrence.statedAt(stands).ifPresent(states -> {
                ComparisonOccurrence already = stated.put(states, which);
                if (already != null && !already.equals(which)) {
                    throw new IllegalStateException("two comparisons of `" + behavior
                            + "` are one construct of the model: " + already + " and " + which
                            + " are both " + states);
                }
                plan.emissionSiteOf(which).ifPresent(site -> sites.put(states, site));
            });
        }
        Core.forEachChild(e, child -> walk(child, behavior, plan, sites, emitted, stated));
    }

    /** Where a run through {@code occurrence} is recorded, or empty where the emitter numbered
     *  none. */
    public Optional<ComparisonEmissionSite> siteOf(ModelOccurrence occurrence) {
        return Optional.ofNullable(sites.get(occurrence));
    }

    /** What the walk that numbered the sites called each of them, for the one reader that still
     *  names a comparison that way ({@link LegacyComparisonAddresses}). */
    Map<ConstructOccurrence, ComparisonOccurrence> emittedNames() {
        return emitted;
    }
}
