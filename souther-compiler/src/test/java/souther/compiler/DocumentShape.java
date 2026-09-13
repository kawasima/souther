package souther.compiler;

import souther.compiler.report.AdequacyReport;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * The schema shipped beside this compiler, and a document read against it.
 *
 * <p>Its own thing rather than a detail of the test that first needed it, because more than one
 * test has a document to hold to the schema and only one of them can make it. What a measure comes
 * to where it runs out of what it may spend is written in shapes no model of this repository
 * reaches, and saying what a measure may spend is only possible from where the measures live — so
 * the walk goes to the document rather than the document to the walk.
 *
 * <p>It is not a validator, and it refuses to look like one. What it understands is the set of
 * keywords listed below; meeting any other is a failure rather than something skipped, because a
 * walk that steps over what it does not know is a check that stops saying anything the moment the
 * schema grows.
 */
public final class DocumentShape {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private DocumentShape() {
    }

    /**
     * The keywords this knows what to do with.
     *
     * <p>Two kinds. Some decide which keys an object may have and must have, and are what this is
     * about; the rest constrain a value and are none of its business — but they are listed all the
     * same, so that a keyword the schema gains is a decision somebody makes here rather than a
     * silence.
     */
    private static final Set<String> UNDERSTOOD = Set.of(
            // the shape of an object, which is the whole of what this checks
            "properties", "required", "additionalProperties", "items", "$ref",
            // compositions: their branches are read for the keys they allow, never for what they
            // require, since which branch a value took is not this walk's question
            "oneOf", "anyOf", "allOf", "if", "then", "else", "not", "dependentRequired",
            // constraints on a value, which say nothing about keys
            "type", "enum", "const", "minimum", "pattern", "minItems", "maxItems",
            "uniqueItems", "contains", "minContains", "maxContains",
            // prose and plumbing
            "description", "title", "$schema", "$id", "$defs", "examples",
            // and one annotation of this compiler's, which says that a string has a handle inside
            // it rather than being one. No part of what a document must satisfy — a reader that
            // does not know the word ignores it, which is what makes it safe to write here — and
            // what holds it to the writer is the check that reads it
            // (`EveryFormOfARuleHandleIsOneTheContractDescribes`)
            "x-souther-contains");

    /**
     * What a walk of one document came to: how much of it was reached, how many of the schema's
     * conditions were put to it, and what the schema refuses.
     *
     * <p>The conditions are counted because a condition that never applies is a check that passes
     * for saying nothing. A document holding none of the shapes they are about is a document this
     * walk agrees with the way it agrees with an empty file.
     */
    public record Read(int objects, int conditions, List<String> wrong) {}

    /** {@code document} read against the schema this compiler ships. */
    public static Read of(JsonNode document) {
        Walk walk = new Walk(schema());
        walk.of(document, "");
        return new Read(walk.objects, walk.conditions, walk.wrong);
    }

    /** The same, for a caller that wants the document held to the schema and nothing else. */
    public static void assertShapedLikeTheSchema(JsonNode document) {
        assertEquals(List.of(), of(document).wrong(),
                "what the schema shipped beside this refuses");
    }

    /** The schema shipped beside the compiler, which is what a reader validates against. */
    public static JsonNode schema() {
        try (InputStream in =
                     AdequacyReport.class.getResourceAsStream(AdequacyReport.SCHEMA_RESOURCE)) {
            assertNotNull(in, AdequacyReport.SCHEMA_RESOURCE + " ships beside the compiler");
            return JSON.readTree(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException cannotRead) {
            throw new UncheckedIOException(cannotRead);
        }
    }

    /** The document and the schema, walked together. */
    private static final class Walk {

        private final JsonNode schema;
        private final List<String> wrong = new ArrayList<>();
        private int objects;
        private int conditions;

        Walk(JsonNode schema) {
            this.schema = schema;
        }

        void of(JsonNode node, String at) {
            of(node, schema, at);
        }

        private void of(JsonNode node, JsonNode declared, String at) {
            JsonNode said = resolved(declared);
            understand(said, at);
            if (node.isObject()) {
                objects++;
                // Each thing that refuses an undeclared key refuses on its own, so a key has to be
                // named by all of them. Gathered into one set, an object closed over keys it does
                // not name is lent the keys of whatever stands under it, and the closure it wrote
                // says nothing.
                for (Set<String> may : declaredIn(said)) {
                    node.propertyNames().forEach(key -> {
                        if (!may.contains(key)) {
                            wrong.add(at + ": the schema declares no `" + key + "` here");
                        }
                    });
                }
                // Only what the object itself requires. Which branch of a composition a value took
                // is a question about the value, and answering it here would report a document as
                // short of a key another branch asks for.
                if (said.has("required")) {
                    said.get("required").forEach(key -> {
                        if (!node.has(key.asString())) {
                            wrong.add(at + ": the schema requires a `" + key.asString() + "` here");
                        }
                    });
                }
                conditions(said, node, at);
                node.properties().forEach(entry -> {
                    JsonNode under = declares(said, entry.getKey());
                    if (under != null) {
                        of(entry.getValue(), under, at + "/" + entry.getKey());
                    }
                });
            } else if (node.isArray()) {
                howMany(node, said, at);
                JsonNode items = itemsOf(said);
                if (items != null) {
                    for (int i = 0; i < node.size(); i++) {
                        of(node.get(i), items, at + "[" + i + "]");
                    }
                }
            }
        }

        /**
         * How many entries an array may have, and how many of them a shape may match.
         *
         * <p>Held rather than stepped over. A keyword this walk names as understood and does not
         * evaluate is a claim the schema makes and nothing checks — and the ones about how many are
         * exactly where a writer and a contract come apart without either changing: a border that
         * grows a point writes an array one longer, and every key in it is still declared.
         */
        private void howMany(JsonNode node, JsonNode said, String at) {
            if (said.has("minItems") && node.size() < said.get("minItems").asInt()) {
                wrong.add(at + ": the schema asks for at least " + said.get("minItems").asInt()
                        + " here and this has " + node.size());
            }
            if (said.has("maxItems") && node.size() > said.get("maxItems").asInt()) {
                wrong.add(at + ": the schema allows at most " + said.get("maxItems").asInt()
                        + " here and this has " + node.size());
            }
            if (said.has("uniqueItems") && said.get("uniqueItems").booleanValue()) {
                Set<JsonNode> once = new LinkedHashSet<>();
                node.forEach(once::add);
                if (once.size() != node.size()) {
                    wrong.add(at + ": the schema asks for one of each here and this repeats one");
                }
            }
            for (JsonNode each : said.has("allOf") ? said.get("allOf") : List.<JsonNode>of()) {
                if (each.has("contains")) {
                    matching(node, each, at);
                }
            }
            if (said.has("contains")) {
                matching(node, said, at);
            }
        }

        /** How many entries of an array match a `contains`, held to what is asked of that count. */
        private void matching(JsonNode node, JsonNode said, String at) {
            JsonNode shape = said.get("contains");
            int found = 0;
            for (JsonNode each : node) {
                if (matches(each, shape)) {
                    found++;
                }
            }
            int least = said.has("minContains") ? said.get("minContains").asInt() : 1;
            if (found < least) {
                wrong.add(at + ": the schema asks for at least " + least + " entry matching "
                        + shape + " and this has " + found);
            }
            if (said.has("maxContains") && found > said.get("maxContains").asInt()) {
                wrong.add(at + ": the schema allows at most " + said.get("maxContains").asInt()
                        + " entry matching " + shape + " and this has " + found);
            }
        }

        /** Whether one entry is what a `contains` names, which here is a required key at a
         *  constant. */
        private boolean matches(JsonNode node, JsonNode shape) {
            if (!shape.has("properties")) {
                return true;
            }
            for (var each : shape.get("properties").properties()) {
                JsonNode held = node.get(each.getKey());
                JsonNode want = each.getValue().get("const");
                if (held == null || (want != null && !held.equals(want))) {
                    return false;
                }
            }
            return true;
        }

        /**
         * Which keys an object must have and must not have where that turns on what it holds.
         *
         * <p>Evaluated, unlike the branches of an {@code oneOf}. Which branch of those a value took
         * is a question about the value, and answering it here would report a document as short of
         * a key another branch asks for — but an {@code if} names what it turns on and a
         * {@code dependentRequired} names the key that brings another with it, so there is nothing
         * to guess at. Stepped over, these were the part of the contract a consumer is held to and
         * this compiler was not: a relation the schema states and nothing applies is prose with
         * punctuation.
         *
         * <p>Through an {@code allOf}, where a relation several objects share is written once and
         * referred to, and through the {@code if} of a branch that was taken, where one condition
         * is written as the next question after another.
         */
        private void conditions(JsonNode said, JsonNode node, String at) {
            if (said.has("if")) {
                Boolean answered = guarded(said.get("if"), node, at);
                JsonNode taken = answered == null ? null : said.get(answered ? "then" : "else");
                if (taken != null) {
                    conditions++;
                    for (String key : required(taken)) {
                        if (!node.has(key)) {
                            wrong.add(at + ": the schema requires a `" + key + "` here");
                        }
                    }
                    for (String key : refused(taken, at)) {
                        if (node.has(key)) {
                            wrong.add(at + ": the schema writes no `" + key + "` here");
                        }
                    }
                    conditions(resolved(taken), node, at);
                }
            }
            for (JsonNode each : said.has("allOf") ? said.get("allOf") : List.<JsonNode>of()) {
                conditions(resolved(each), node, at);
            }
            if (said.has("dependentRequired")) {
                JsonNode with = said.get("dependentRequired");
                with.propertyNames().forEach(key -> {
                    if (node.has(key)) {
                        with.get(key).forEach(also -> {
                            if (!node.has(also.asString())) {
                                wrong.add(at + ": the schema has `" + key + "` bring `"
                                        + also.asString() + "` with it");
                            }
                        });
                    }
                });
            }
        }

        /**
         * Whether the question an {@code if} asks is answered yes by what was written, or null
         * where the question is spelled a way this walk does not read.
         *
         * <p>A word at a key, or one of several, the keys the question is put of at all, and a
         * question that is any of a list of those. Null rather than no for the rest: read as a no,
         * the other branch would be applied to every document and the condition would be reported
         * as broken wherever it holds — and read as a yes, it would say nothing anywhere. So it is
         * reported as a question nobody here can answer, which is what it is.
         */
        private Boolean guarded(JsonNode asked, JsonNode node, String at) {
            boolean answered = true;
            for (String keyword : names(asked)) {
                if (!List.of("properties", "required", "anyOf", "description").contains(keyword)) {
                    wrong.add(at + ": a condition spelled a way this walk does not read: `"
                            + keyword + "`");
                    return null;
                }
            }
            if (asked.has("properties")) {
                for (var each : asked.get("properties").properties()) {
                    JsonNode said = each.getValue();
                    JsonNode held = node.get(each.getKey());
                    // A question about what stands under a key, which is how a condition on
                    // something written inside another object is put. Asked of the object there,
                    // and answered no where nothing is.
                    if (!said.has("const") && !said.has("enum")) {
                        if (!said.has("properties") && !said.has("required")) {
                            wrong.add(at + ": a condition spelled a way this walk does not read: "
                                    + said);
                            return null;
                        }
                        if (held == null || !held.isObject()) {
                            answered = false;
                            continue;
                        }
                        Boolean under = guarded(said, held, at + "/" + each.getKey());
                        if (under == null) {
                            return null;
                        }
                        answered &= under;
                        continue;
                    }
                    answered &= held != null && (said.has("const")
                            ? said.get("const").asString().equals(held.asString())
                            : anyIs(said.get("enum"), held.asString()));
                }
            }
            for (String key : required(asked)) {
                answered &= node.has(key);
            }
            // And a question that is any of several, which is how a condition over more than one
            // word is written where the words sit at different keys.
            if (asked.has("anyOf")) {
                boolean some = false;
                for (JsonNode each : asked.get("anyOf")) {
                    Boolean branch = guarded(each, node, at);
                    if (branch == null) {
                        return null;
                    }
                    some |= branch;
                }
                answered &= some;
            }
            return answered;
        }

        private List<String> names(JsonNode of) {
            List<String> out = new ArrayList<>();
            of.propertyNames().forEach(out::add);
            return out;
        }

        private boolean anyIs(JsonNode words, String word) {
            for (JsonNode each : words) {
                if (word.equals(each.asString())) {
                    return true;
                }
            }
            return false;
        }

        private List<String> required(JsonNode of) {
            List<String> out = new ArrayList<>();
            if (of.has("required")) {
                of.get("required").forEach(each -> out.add(each.asString()));
            }
            return out;
        }

        /**
         * The keys a branch writes out of the document, however it spells the refusal.
         *
         * <p>Two spellings, because the schema needed two: one key is {@code not: {required: [k]}}
         * and several are {@code not: {anyOf: [{required: [k]}, ...]}}. Anything else is reported
         * rather than passed over, for the reason the keywords above are.
         */
        private List<String> refused(JsonNode of, String at) {
            if (!of.has("not")) {
                return List.of();
            }
            JsonNode not = of.get("not");
            if (not.has("required")) {
                return required(not);
            }
            if (!not.has("anyOf")) {
                wrong.add(at + ": a refusal spelled a way this walk does not read: " + not);
                return List.of();
            }
            List<String> out = new ArrayList<>();
            for (JsonNode each : not.get("anyOf")) {
                out.addAll(required(each));
            }
            return out;
        }

        /** Every keyword of this schema object, held to what this walk was taught. */
        private void understand(JsonNode said, String at) {
            said.propertyNames().forEach(keyword -> {
                if (!UNDERSTOOD.contains(keyword)) {
                    wrong.add(at + ": `" + keyword + "` is a keyword this walk was never taught,"
                            + " and stepping over one is how a check stops saying anything");
                }
            });
        }

        private JsonNode resolved(JsonNode said) {
            JsonNode at = said;
            while (at.has("$ref")) {
                String name = at.get("$ref").asString();
                at = schema.get("$defs").get(name.substring(name.lastIndexOf('/') + 1));
                assertNotNull(at, () -> "the schema refers to " + name + " and does not define it");
            }
            return at;
        }

        /** Whether an undeclared key is a refusal here, which is what makes this worth asking. */
        private boolean closed(JsonNode said) {
            JsonNode more = said.get("additionalProperties");
            return more != null && more.isBoolean() && !more.booleanValue();
        }

        /**
         * Every key this object may have, read the way {@code additionalProperties} is defined.
         *
         * <p><b>The keys of the object that closed itself, and no others.</b> An
         * {@code additionalProperties} is about the {@code properties} written beside it and about
         * nothing written under a composition, so a key a branch declares is an additional property
         * of the object above it. Read as though a branch's keys were the parent's, this said a
         * document was well shaped where a reader of the schema refuses it — which is a schema
         * closed over keys it declares nowhere, admitting nothing at all.
         *
         * <p><b>And what every branch of a composition allows, where every one of them is
         * closed.</b> A value satisfies a composition by satisfying a branch, so a key no branch
         * declares is a key each of them refuses. That is why this looks at the branches at all:
         * not to lend their keys to the object above, but because the object below has already
         * refused what none of them has.
         */
        private List<Set<String>> declaredIn(JsonNode said) {
            List<Set<String>> refusals = new ArrayList<>();
            if (closed(said)) {
                Set<String> own = new LinkedHashSet<>();
                if (said.has("properties")) {
                    said.get("properties").propertyNames().forEach(own::add);
                }
                refusals.add(own);
            }
            for (String branch : List.of("oneOf", "anyOf")) {
                if (said.has(branch) && everyBranchIsClosed(said.get(branch))) {
                    Set<String> anyBranch = new LinkedHashSet<>();
                    said.get(branch).forEach(each ->
                            declaredIn(resolved(each)).forEach(anyBranch::addAll));
                    refusals.add(anyBranch);
                }
            }
            // An `allOf` is every branch at once, so each branch that refuses refuses on its own.
            if (said.has("allOf")) {
                said.get("allOf").forEach(each -> refusals.addAll(declaredIn(resolved(each))));
            }
            return refusals;
        }

        /** Whether a composition refuses what none of its branches declares. */
        private boolean everyBranchIsClosed(JsonNode branches) {
            for (JsonNode each : branches) {
                if (!closed(resolved(each))) {
                    return false;
                }
            }
            return true;
        }

        /** Where the schema says what is under one key, or null where it says nothing. */
        private JsonNode declares(JsonNode said, String key) {
            if (said.has("properties") && said.get("properties").has(key)) {
                return said.get("properties").get(key);
            }
            for (String branch : List.of("oneOf", "anyOf", "allOf")) {
                if (said.has(branch)) {
                    for (JsonNode each : said.get(branch)) {
                        JsonNode under = declares(resolved(each), key);
                        if (under != null) {
                            return under;
                        }
                    }
                }
            }
            for (String arm : List.of("then", "else")) {
                if (said.has(arm)) {
                    JsonNode under = declares(resolved(said.get(arm)), key);
                    if (under != null) {
                        return under;
                    }
                }
            }
            return null;
        }

        private JsonNode itemsOf(JsonNode said) {
            return said.has("items") ? said.get("items") : null;
        }
    }
}
