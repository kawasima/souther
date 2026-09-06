package souther.compiler.diag;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/**
 * The public identity of a compiler diagnostic.
 *
 * <p>A code names one normative rule of the language — not one message template and not one title
 * category. The rule is identified by {@link #ruleAnchor()}, an anchor in the specification, and no
 * two codes share one: two diagnostics carry the same code exactly when they report the same rule
 * broken in different ways, and different codes when they enforce different rules, however alike
 * their titles or their sections read.
 *
 * <p>The three names a diagnostic carries are separate on purpose:
 *
 * <ul>
 *   <li>{@link #titleKey()} is the category a reader sees in the header — {@code BOUNDARY TYPE},
 *       {@code TYPE MISMATCH}. It is shared, and says nothing about identity;</li>
 *   <li>this code is the identity a reader looks up and a tool keys on;</li>
 *   <li>the message key a site passes to {@link Diagnostic#say} is the wording for one way the rule
 *       was broken, and several of them belong to one code.</li>
 * </ul>
 *
 * <p>A retired code is not here — it is in {@link RetiredDiagnosticCode}, so that what a compile can
 * emit and what a number once meant are two lists and a number is never reused.
 */
public enum DiagnosticCode {

    // --- declarations: construction authority, fields, invariants, matching ---
    E1002("constructs-authority-complete", "e1002.title"),
    E1004("spread-fields-do-not-collide", "e1004.title"),
    E1005("construction-supplies-every-field", "e1005.title"),
    E1006("constructs-no-overdeclaration", "e1006.title"),
    E1007("newtype-wraps-an-external-representation", "parse.title"),
    E1008("unit-data-is-written-without-a-body", "check.data.invalid.title"),
    E1009("newtype-wraps-a-value-that-is-always-there", "check.boundary.title"),
    E1010("a-case-does-not-declare-the-discriminator-field", "check.boundary.title"),
    E1011("a-declaration-is-made-once", "check.duplicate.title"),
    E1012("a-spelling-answers-one-thing-where-a-value-is-written", "check.duplicate.title"),
    E1013("a-data-can-be-constructed", "check.construct.title"),
    E1014("a-construction-names-fields-of-its-type", "check.construct.title"),
    E1015("a-spread-copies-a-product", "check.construct.title"),
    E1016("a-spread-supplies-what-the-fields-need", "check.type.mismatch.title"),
    E1017("a-construction-is-written-where-one-may-be", "check.construct.position.title"),
    E1018("a-construction-names-a-data", "check.construct.title"),
    E1019("a-binding-does-not-shadow-a-builtin", "check.reserved.title"),
    E1023("a-name-is-in-scope", "check.unknown.title"),
    E1024("a-name-held-as-a-value-is-a-value", "check.unknown.title"),
    E1025("a-standard-library-function-is-called-qualified", "check.unknown.title"),
    E1026("constructs-excludes-unit-data", "e1026.title"),
    E1101("invariant-expression-is-bool", "e1101.title"),
    E1102("invariant-needs-a-value-to-constrain", "check.invariant.invalid.title"),
    E1103("invariant-clause-names-are-distinct", "check.invariant.invalid.title"),
    E1104("invariant-clause-name-is-not-underscore", "check.invariant.invalid.title"),
    E1020("a-sum-case-is-a-declared-data", "check.sum.title"),
    E1021("a-sum-does-not-contain-itself", "check.sum.title"),
    E1022("a-value-does-not-reach-itself", "check.value.cycle.title"),
    E1105("an-invariant-observes-and-does-not-build", "check.invariant.invalid.title"),
    E1106("an-invariant-answers-on-every-path", "check.invariant.invalid.title"),
    E1107("an-invariant-is-declared-where-a-construction-owes-it",
            "check.invariant.invalid.title"),
    E1201("match-covers-every-case", "e1201.title"),
    E1202("a-match-subject-is-a-sum", "check.match.title"),
    E1203("a-match-arm-names-a-case-of-the-subject", "check.match.title"),
    E1204("no-two-arms-match-one-case", "check.match.title"),
    E1205("a-match-has-arms", "check.match.title"),
    E1206("a-pattern-opens-what-the-value-is", "check.open.title"),
    E1207("an-or-pattern-binds-the-sum-and-opens-nothing", "check.match.title"),
    E1208("branches-agree-on-a-type", "check.type.mismatch.title"),
    E1209("an-arms-alternatives-each-add-what-it-answers-for", "check.match.title"),

    // --- what the language does not have ---
    E1301("no-null", "e1301.title"),
    E1303("option-cases-are-not-written", "e1303.title"),
    E1305("injected-constructs-are-creatable", "e1305.title"),
    E1307("unreachable-stands-where-a-type-is-stated", "check.type.mismatch.title"),
    E1308("optional-marks-one-type", "parse.title"),
    E1310("unreachable-states-its-reason", "parse.title"),
    E1311("what-crosses-the-boundary-has-an-external-representation", "check.boundary.title"),
    E1312("a-parameter-names-one-type", "check.boundary.title"),
    E1313("an-optional-does-not-stand-in-a-boundary", "check.boundary.title"),
    E1314("a-map-that-crosses-is-keyed-by-a-text-key", "check.boundary.title"),
    E1315("a-collection-member-supports-equality", "check.boundary.title"),
    E1316("a-generic-type-is-given-its-arguments", "check.typearg.title"),
    E1317("a-value-has-the-type-its-position-requires", "check.type.mismatch.title"),
    E1318("a-lists-elements-have-one-type", "check.list.title"),
    E1319("an-operator-takes-the-types-it-is-defined-for", "check.type.mismatch.title"),
    E1320("a-tuple-pattern-matches-its-tuple", "check.type.mismatch.title"),
    E1321("a-field-read-is-declared-by-the-value", "check.type.mismatch.title"),
    E1322("a-temporal-value-is-written-as-a-literal", "check.type.mismatch.title"),
    E1323("a-matches-pattern-is-a-literal-regular-expression", "check.type.mismatch.title"),
    E1324("newtype-arithmetic-follows-the-units", "check.type.mismatch.title"),
    E1325("a-boundary-carries-the-models-own-vocabulary", "check.boundary.title"),
    E1326("unreachable-is-for-what-the-model-rules-out", "check.unreachable.title"),
    E1327("a-branch-the-rules-make-dead-is-said-so", "check.dead.branch.title"),
    E1402("core-privileges-stay-in-the-core", "parse.title"),

    // --- modules, requirements, composition ---
    E1501("module-dependencies-are-acyclic", "e1501.title"),
    E1502("a-core-name-is-not-taken", "check.module.title"),
    E1503("a-module-is-declared-in-one-place", "check.module.title"),
    E1504("every-module-reached-is-on-the-path", "check.module.title"),
    E1506("a-reached-name-is-declared-by-its-module", "check.module.title"),
    E1507("a-reached-name-is-exposed-by-its-module", "check.module.title"),
    E1508("an-imported-name-denotes-one-thing", "check.module.title"),
    E1509("a-published-module-can-be-read-back", "check.module.title"),
    E1602("depends-on-names-every-requirement", "e1602.title"),
    E1603("depends-on-names-no-more", "e1603.title"),
    E1604("composition-output-agrees-with-inference", "e1604.title"),
    E1605("exposed-composition-declares-its-output", "e1605.title"),
    E1606("sum-cases-are-declared-with-the-sum", "check.sum.title"),
    E1607("depends-on-names-a-dependency", "e1607.title"),
    E1608("behavior-does-not-reach-itself", "e1608.title"),
    E1609("exposing-lists-this-modules-own-definitions", "check.module.title"),
    E1610("exposing-is-type-granular", "check.module.title"),
    E1611("an-exposed-signature-names-only-exposed-types", "check.module.title"),
    E1613("a-union-member-is-nameable-in-an-arm", "check.boundary.title"),
    E1614("a-composition-has-no-let", "check.impl.title"),
    E1615("an-implementing-let-takes-its-shape-from-the-behavior", "check.impl.title"),
    E1616("an-ensures-clause-names-the-answer", "check.boundary.title"),
    E1617("an-ensures-clause-names-a-parameter", "check.boundary.title"),
    E1618("a-behavior-with-a-clause-has-no-parameter-named-value", "check.boundary.title"),
    E1619("an-ensures-arm-is-an-output-case", "check.boundary.title"),
    E1620("an-ensures-clause-over-a-sum-names-arms", "check.boundary.title"),
    E1621("an-ensures-clause-over-a-single-type-names-no-arm", "check.boundary.title"),
    E1622("ensures-clause-names-are-distinct", "check.boundary.title"),
    E1623("an-ensures-clause-name-is-not-underscore", "check.boundary.title"),
    E1624("a-composition-carries-no-ensures", "check.pipe.title"),
    E1626("an-attached-files-values-are-for-its-rows", "check.example.title"),
    E1627("nothing-built-rests-on-an-unwritten-behavior", "check.module.title"),
    E1701("composition-stages-type-route", "e1701.title"),
    E1702("a-stage-after-the-first-takes-one-input", "check.pipe.title"),
    E1703("a-pipeline-composes-behaviors", "check.pipe.title"),

    // --- functions, helpers, and applying them ---
    E1802("an-application-gives-the-arguments-it-takes", "check.arity.title"),
    E1803("what-is-applied-is-a-function", "check.apply.notfunction.title"),
    E1804("a-function-goes-where-a-function-is-taken", "check.fn.title"),
    E1805("a-block-answers-the-type-its-position-takes", "check.fn.title"),
    E1806("an-argument-has-the-type-its-parameter-takes", "check.fn.title"),
    E1807("a-function-binding-has-one-type", "check.fn.title"),
    E1808("a-function-bindings-type-is-known", "check.fn.title"),
    E1809("a-block-is-not-a-value", "check.block.title"),
    E1810("an-annotation-on-a-function-binding-is-a-function-type", "check.fn.title"),
    E1811("a-helper-parameter-states-its-type", "check.helper.title"),
    E1812("a-helper-answers-what-it-declares", "check.helper.title"),
    E1813("a-recursive-helper-declares-its-return-type", "check.helper.title"),
    E1814("a-recursive-helper-does-not-reach-an-injected-behavior", "check.helper.title"),
    E1815("an-empty-collections-element-type-is-determined", "check.fold.seed.title"),
    E1816("what-is-ordered-by-is-an-ordered-value", "check.constraint.title"),
    E1817("what-is-summed-is-a-number", "check.constraint.title"),
    E1818("a-helper-does-not-call-a-behavior", "check.notcallable.title"),

    // --- examples ---
    E1901("example-target-is-a-behavior", "check.example.title"),
    E1902("example-target-is-evaluable", "check.example.title"),
    E1903("example-fixtures-are-buildable", "check.example.title"),
    E1904("example-expected-arm-is-an-output-case", "check.example.title"),
    E1905("example-holds", "check.example.title"),
    E1906("examples-file-holds-only-examples", "check.example.title"),
    E1907("examples-file-names-a-compiled-module", "check.example.title"),
    E1908("example-dependency-has-a-usable-fake", "check.example.title"),
    E1909("fake-answers-the-inputs-it-is-asked", "check.example.title"),
    E1910("example-evaluation-stays-within-budget", "check.example.title"),
    E1911("example-does-not-reach-unreachable", "check.example.title"),
    E1913("every-output-case-is-expected-by-a-row", "check.example.title"),
    E1915("every-input-case-is-used-by-a-row", "check.example.title"),
    E1916("every-obligation-against-its-line-has-a-row", "check.example.title"),
    E1917("every-obligation-away-from-its-line-has-a-row", "check.example.title"),
    E1918("every-arm-has-a-row", "check.example.title"),
    E1919("stand-in-and-recorded-row-agree", "check.example.title"),
    E1920("stand-in-comparison-completes", "check.example.title"),
    E1921("fake-table-builds", "check.example.title"),
    E1922("imports-are-used", "check.import.title"),
    E1923("example-evaluation-answers", "check.example.title"),
    E1924("example-evaluation-stays-within-stack", "check.example.title"),
    E1925("a-row-name-is-unique-within-its-behavior", "check.example.title"),
    E1926("every-row-of-a-fake-table-can-answer", "check.example.title"),
    E1927("an-answer-is-of-the-module-being-evaluated", "check.example.title"),
    E1928("a-row-keeps-what-the-behavior-states", "check.example.title"),
    E1929("a-fake-row-keeps-what-the-dependency-states", "check.example.title"),
    E1930("an-implementation-keeps-what-the-behavior-states", "check.example.title"),
    E1931("every-axis-class-has-a-row", "check.example.title"),
    E1932("a-stand-in-names-a-behavior", "check.example.title"),
    E1933("a-behavior-has-at-most-one-stand-in-table", "check.example.title"),

    // --- totality, invariant discharge, attempted construction ---
    E2001("helper-carries-its-termination-guarantee", "check.totality.title"),
    E2010("construction-satisfies-its-invariant", "check.invariant.title"),
    E2011("construction-invariant-is-discharged", "check.invariant.title"),
    E2012("attempt-subject-is-a-construction", "check.attempt.title"),
    E2013("attempt-subject-declares-an-invariant", "check.attempt.title"),
    E2014("attempt-arm-names-a-declared-clause", "check.attempt.title"),
    E2015("attempt-answers-every-failing-clause", "check.attempt.title"),
    E2016("attempt-answers-unnamed-clauses", "check.attempt.title"),
    E2017("attempt-catch-all-has-clauses-to-answer", "check.attempt.title"),
    E2018("attempt-arms-answer-an-attempt", "check.attempt.title"),
    E2019("attempt-answers-each-clause-once", "check.attempt.title"),

    // --- what the JVM will hold, and what the compiler will walk ---
    E2101("generated-methods-fit-jvm-parameter-slots", "e2101.title"),
    E2102("generated-methods-fit-jvm-code-size", "e2102.title"),
    E2103("generated-classes-fit-jvm-constant-pool", "e2103.title"),
    E2104("source-nesting-is-bounded", "e2104.title"),
    E2105("no-two-declarations-become-one-class", "check.duplicate.title"),
    E2106("a-field-does-not-collide-with-an-object-method", "check.reserved.title"),
    E2107("source-structural-complexity-is-bounded", "e2107.title"),
    E2108("running-out-of-room-is-reported", "e2108.title"),

    // --- the external representation ---
    E2201("a-custom-codec-agrees-with-its-type", "check.codec.title"),

    // --- the text as written ---
    E2301("declaration-syntax", "parse.title"),
    E2302("expression-syntax", "parse.title"),
    E2303("pattern-syntax", "parse.title"),
    E2304("examples-syntax", "parse.title"),
    E2305("literal-syntax", "parse.title"),
    E2306("the-source-is-made-of-tokens", "parse.title"),
    E2307("an-anonymous-union-is-not-written-in-a-narrow-type-position", "parse.title"),
    E2308("an-optional-is-not-written-inside-another-type", "parse.title");

    /**
     * The codes for a text the compiler could not read, one for each place the reading can stop.
     *
     * <p>Written out rather than derived from what the codes happen to point at. The specification
     * lists the same places on its own side, and a comparison whose two sides come from one of them
     * agrees with itself: a code that wandered off to some other rule would drop out of the
     * comparison instead of failing it, which is exactly the drift worth catching.
     */
    public static Set<DiagnosticCode> whereAReadingStops() {
        return WHERE_A_READING_STOPS;
    }

    private static final Set<DiagnosticCode> WHERE_A_READING_STOPS = Collections.unmodifiableSet(
            EnumSet.of(E2301, E2302, E2303, E2304, E2305, E2306, E2307, E2308));

    private final String ruleAnchor;
    private final String titleKey;

    DiagnosticCode(String ruleAnchor, String titleKey) {
        this.ruleAnchor = ruleAnchor;
        this.titleKey = titleKey;
    }

    /** The specification anchor of the rule this code reports broken. Unique across the codes. */
    public String ruleAnchor() {
        return ruleAnchor;
    }

    /**
     * Whether this rule is reported as an error or a warning.
     *
     * <p>A property of the rule and not of the site that raises it. A code raised as an error in one
     * place and a warning in another is two rules wearing one number, which is what the reader looks
     * up; and a site free to choose is a site that can be written to say what the author of that one
     * site wanted rather than what the rule is.
     */
    public Severity severity() {
        return WARNINGS.contains(this) ? Severity.WARNING : Severity.ERROR;
    }

    /** The rules that are reported without failing the build. */
    private static final java.util.Set<DiagnosticCode> WARNINGS =
            java.util.EnumSet.of(E1327, E1913, E1915, E1916, E1917, E1918, E1919, E1920, E1921,
                    E1922, E1931, E2011);

    /** The catalog key of the header category this code is shown under. Shared across codes. */
    public String titleKey() {
        return titleKey;
    }

    /** The anchor of this code's own section, which {@code souther doc} answers from. */
    public String docAnchor() {
        return name().toLowerCase(Locale.ROOT);
    }
}
