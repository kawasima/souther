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
 * <p>So the schema's own name for the part is carried beside the node, and a field that has a
 * declared place ({@link RuleHandleSurface}) is written by comparing the two. The array's name and
 * not the item's: items are what a schema declares under {@code items}, and a writer that named each
 * one would be writing the same answer once per row.
 *
 * <p>The name is the definition being filled rather than the place it is used from. A part reached
 * through {@code $ref} is one shape however many fields refer to it, and what a row of it must look
 * like is written where the definition is.
 */
public record DocumentArray(ArrayNode node, String schemaPath) {

    public DocumentArray {
        if (node == null || schemaPath == null || !schemaPath.startsWith("/")) {
            throw new IllegalArgumentException("a repeated part of the document is a node and what"
                    + " the schema calls it: " + schemaPath);
        }
    }

    /** A row of it, which the schema declares under {@code items}. */
    public DocumentItem addObject() {
        return new DocumentItem(node.addObject(), schemaPath + "/items");
    }
}
