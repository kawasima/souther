package souther.compiler.publish;

import tools.jackson.databind.node.ObjectNode;

/**
 * A repeated part of the adequacy document that carries a rule handle, as the schema declares it.
 *
 * <p>What a part is called and where the schema declares it are one fact, so they are one value.
 * Written as two — a key handed to {@code putArray} and a path handed beside it — a writer could
 * fill an array under one name while claiming the place of another, and every check would agree:
 * the surface's place would match what the writer claimed, the claim would match nothing, and the
 * handle would be published under a field the contract says nothing about. That is the shape this
 * whole issue is about, arrived at from the naming side, and it is why nothing here takes a key.
 *
 * <p>The path is the definition the rows conform to and not the property the array hangs from. Two
 * sections publish obligations and two publish findings, each through a {@code $ref}, and what a row
 * of either must look like is written once where the definition is. Which places may hold such an
 * array is the schema's own business and is checked against it
 * ({@code EveryFormOfARuleHandleIsOneTheContractDescribes}).
 *
 * <p>Only the parts that carry a handle are here. The rest of the document is written as it always
 * was: this exists so the one field kind whose place the contract names can be held to it.
 */
public enum DocumentPart {

    /** The questions a behavior's rules raise that nothing answered. */
    UNANSWERED("unanswered", "/$defs/partition/properties/unanswered"),

    /** What a reading could not read. */
    NOT_READ("notRead", "/$defs/partition/properties/notRead"),

    /** The lines a behavior's rules drew. */
    BOUNDARIES("boundaries", "/$defs/partition/properties/boundaries"),

    /** What a set of lines is owed a row for, published by two sections through one definition. */
    OBLIGATIONS("obligations", "/$defs/obligations"),

    /** What a measure found, published by two sections through one definition. */
    FINDINGS("findings", "/$defs/findings"),

    /** What gave the offer no value, where a search of a rule of a decision composed nothing. */
    SYNTHESIS_SHORTFALL_CAUSES("synthesisShortfallCauses",
            "/$defs/decision/properties/obligations/items/properties/synthesisShortfallCauses");

    private final String key;
    private final String schemaPath;

    DocumentPart(String key, String schemaPath) {
        this.key = key;
        this.schemaPath = schemaPath;
    }

    /** What the part is called wherever it is written. */
    public String key() {
        return key;
    }

    /** Where the schema declares what a row of it looks like. */
    public String schemaPath() {
        return schemaPath;
    }

    /**
     * This part of {@code parent}, made and named together.
     *
     * <p>The one way to get one. A caller cannot put the array under a name of its own and hand the
     * place of this one along with it, because it never says either.
     */
    public DocumentArray putArray(ObjectNode parent) {
        return DocumentArray.of(parent.putArray(key), schemaPath);
    }
}
