package souther.compiler.publish;

import tools.jackson.databind.node.ObjectNode;

/**
 * One object of the document being written, together with what the schema says it is.
 *
 * <p>What {@link DocumentArray} hands out, and what a field with a declared place is written into.
 * Everything else about writing a document stays as it was: a node takes whatever keys the writer
 * puts on it, and this is not a second way to do that. It exists so that the one field kind whose
 * place the contract names can be checked against the place it was actually written.
 *
 * <p>Made where a row of a part is made, for the reason {@link DocumentArray} gives.
 */
public final class DocumentItem {

    private final ObjectNode node;

    private final String schemaPath;

    private DocumentItem(ObjectNode node, String schemaPath) {
        this.node = node;
        this.schemaPath = schemaPath;
    }

    /** One of these, for a row of a part that knows which part it is. */
    public static DocumentItem at(ObjectNode node, String schemaPath) {
        if (node == null || schemaPath == null || !schemaPath.startsWith("/")) {
            throw new IllegalArgumentException("an object of the document is a node and what the"
                    + " schema calls it: " + schemaPath);
        }
        return new DocumentItem(node, schemaPath);
    }

    /** The object being written, for the fields that have no declared place of their own. */
    public ObjectNode node() {
        return node;
    }

    /** Where the schema declares a field this object writes under {@code key}. */
    String fieldPath(String key) {
        return schemaPath + "/properties/" + key;
    }
}
