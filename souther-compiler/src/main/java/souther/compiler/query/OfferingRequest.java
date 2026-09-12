package souther.compiler.query;

/**
 * What one run was asked to offer rows for.
 *
 * <p>Which readings of a module a run walks, and nothing else. What a run offers beyond that is the
 * account's: every obligation nothing covers is one a row is offered against, and the points of a
 * border are obligations like the rest. A request that also said whether the lines were asked about
 * was a caller holding part of the account back from the generation, and a block written that way
 * could be pasted whole and still leave the report naming gaps — which is the fixed point the
 * account is for.
 *
 * <p>Said by the caller before anything is asked. What a run offers follows from what was asked for,
 * and a request that read its scope back off what some earlier caller happened to have paid for
 * would answer differently depending on the order the requests arrived in.
 *
 * @param module the module the rows are about
 * @param scope  which readings of a declaration's line this request searches
 */
public record OfferingRequest(String module, GenerationScope scope) {

    public OfferingRequest {
        if (module == null || scope == null) {
            throw new IllegalArgumentException("a request for rows is about a module, over some"
                    + " readings of it: " + module + " " + scope);
        }
    }

    /** What a caller printing a block for the whole module asks: every reading of it. */
    public static OfferingRequest overTheModule(String module) {
        return new OfferingRequest(module, new GenerationScope.Module());
    }

    /** Whether {@code behavior}'s own rows are part of what this asks for. */
    public boolean admits(String behavior) {
        return scope.admits(behavior);
    }
}
