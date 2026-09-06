package souther.compiler.publish;

import tools.jackson.databind.node.ArrayNode;

/**
 * A repeated part of the document being written, together with what the schema says it is.
 *
 * <p>A node on its own says nothing about where it sits, so a field written into one is a field
 * nothing can be held to: the writer knows which part of the contract it is filling in and the node
 * does not, and a check that could only ask the node was reduced to trusting whatever the writer
 * meant. That is how the fields carrying a rule handle came to be a list of the ones somebody
 * registered rather than the ones a document has.
 *
 * <p>Made by {@link DocumentPart} and nowhere else, which is what keeps the name and the place one
 * answer. Handed both as arguments, a caller could put an array under one name and claim the place
 * of another, and the comparison a field is written by would hold while the field it wrote sat
 * somewhere the contract does not describe.
 */
public final class DocumentArray {

    private final ArrayNode node;

    private final String schemaPath;

    private DocumentArray(ArrayNode node, String schemaPath) {
        this.node = node;
        this.schemaPath = schemaPath;
    }

    /** One of these, for the part of the document that knows which part it is. */
    static DocumentArray of(ArrayNode node, String schemaPath) {
        if (node == null || schemaPath == null || !schemaPath.startsWith("/")) {
            throw new IllegalArgumentException("a repeated part of the document is a node and what"
                    + " the schema calls it: " + schemaPath);
        }
        return new DocumentArray(node, schemaPath);
    }

    /** Where the schema declares this part. */
    public String schemaPath() {
        return schemaPath;
    }

    /** A row of it, which the schema declares under {@code items}. */
    public DocumentItem addObject() {
        return DocumentItem.at(node.addObject(), schemaPath + "/items");
    }
}
