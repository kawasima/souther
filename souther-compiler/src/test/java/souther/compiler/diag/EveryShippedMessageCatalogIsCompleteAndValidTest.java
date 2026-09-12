package souther.compiler.diag;

import souther.compiler.DefaultStdlib;
import souther.compiler.check.DeclarationKinds;
import souther.compiler.check.PublishedDeclarations;
import souther.compiler.doc.SpecDocument;
import souther.compiler.diag.msg.MessageCodes;
import souther.compiler.diag.msg.Message;
import souther.compiler.diag.msg.MessageKeys;
import souther.compiler.diag.msg.MessageTemplate;
import souther.compiler.diag.msg.MessageValues;

import org.junit.jupiter.api.Test;

import souther.test.RepositoryLayout;

import souther.compiler.check.Ordering;
import souther.compiler.Reserved;
import souther.compiler.types.Type;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Every catalog that ships is complete on its own and valid on its own.
 *
 * <p>The checks here used to name the English and the Japanese catalog, one literal path each. A
 * third catalog therefore shipped with nothing said about it: a file of three keys quoting a
 * standard-library name that no longer exists passed every one of them, and a compile run in that
 * language answered with its message body translated and its title, its hint and every key it did
 * not define still in English. {@link ResourceBundle} falls back to the base for a key a locale is
 * missing, which is what turns an incomplete catalog into one answer written half in each language
 * rather than into a visible failure.
 *
 * <p>So the quantifier is over the catalog files that ship, discovered from the tree, and not over a
 * list of locales somebody has to remember to extend. A catalog added tomorrow is under all of this
 * the moment it is written.
 *
 * <p>The fallback stays: a key missing from both catalogs still renders as itself rather than
 * stopping a compile, and a bundle half-migrated on a branch still runs. What it no longer is, is
 * how a shipped catalog is allowed to be incomplete.
 *
 * <p>Validity is checked over every message rather than over the ones that name an argument, since
 * {@code Messages.get} renders them all as patterns. It is also checked over the file's physical
 * shape, because the checks that read a catalog line by line are only as good as the format they
 * assume {@link Properties} was given.
 */
public class EveryShippedMessageCatalogIsCompleteAndValidTest {

    /** Read once: what this asks of it does not change between its checks. */
    private static final RepositoryLayout REPOSITORY = RepositoryLayout.ofWorkingDirectory();

    /** Where the bundle {@link Messages} reads lives, as a path under a module's resources. */
    private static final String PACKAGE = "souther/compiler/diag";
    /** The bundle's own name, so the base catalog is {@code messages.properties}. */
    private static final String BUNDLE = "messages";
    private static final String SUFFIX = ".properties";

    /**
     * The same control {@link Messages} looks a bundle up with, used here to spell a locale back
     * into the file name it would be read from.
     */
    private static final ResourceBundle.Control CONTROL =
            ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES);

    /**
     * The messages that write out which values are ordered: what {@code sort} takes, and what
     * {@code <} takes. Named here rather than found by pattern, so that renaming one of them fails
     * the check that holds it to the compiler's answer instead of silently removing it.
     */
    private static final List<String> ORDERED_VALUE_KEYS =
            List.of("kind.ordered.list", "type.comparison-needs-ordered-values-of-one-type");

    /** An argument reference: {@code {0}}, and {@code {10}} too — a single digit is not the rule. */
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{(\\d+)}");

    /**
     * A line of a catalog: a key of the shape the catalog uses, {@code =}, and the message.
     *
     * <p>Narrower than what {@link Properties} would accept, which is the point. It also takes
     * {@code :} and bare whitespace as separators and joins a line ending in a backslash to the
     * next, so {@code foo=first} followed by {@code foo:second} is one key defined twice, and a
     * scan looking for {@code =} sees one definition and nothing wrong. Holding the files to one
     * spelling is what lets the duplicate check below read a line as a definition.
     */
    private static final Pattern DEFINITION = Pattern.compile("[A-Za-z0-9._-]+=.*");

    /**
     * A catalog that ships: the file, and the language its messages are written in.
     *
     * <p>The base file carries no language in its name. To {@link ResourceBundle} it is the root
     * bundle, which is a fallback rather than a language; to Souther it is the English catalog,
     * which is what Souther answers when nobody names a language and what makes "the same keys as the base"
     * a statement about a language rather than about a default.
     */
    private record Catalog(Path path, Locale locale) {

        String name() {
            return path.getFileName().toString();
        }

        boolean isBase() {
            return name().equals(BUNDLE + SUFFIX);
        }
    }

    @Test
    void exactlyOneCatalogIsTheBase() throws IOException {
        List<String> bases = new ArrayList<>();
        for (Catalog catalog : catalogs()) {
            if (catalog.isBase()) {
                bases.add(catalog.path().toString());
            }
        }
        assertEquals(1, bases.size(),
                "the base catalog is what every other one is compared against, and what a key"
                        + " missing everywhere else falls back to: " + bases);
    }

    /**
     * One catalog per language, across every module.
     *
     * <p>The modules ship onto one class path — the compiler depends on the syntax module — so two
     * files named for one language are two answers to the same lookup, and which one a reader gets
     * is decided by class-path order rather than by anything here. Each would pass every check on
     * its own, being complete and valid separately, and the other would simply never be read.
     *
     * <p>The base is under this too, its language being the English the base is written in: a
     * {@code messages_en.properties} added beside it is the same collision, and the one
     * {@link ResourceBundle} would prefer is not the one the rest of this file calls the base.
     */
    @Test
    void noTwoCatalogsAreWrittenForTheSameLanguage() throws IOException {
        Map<String, List<String>> byLanguage = new TreeMap<>();
        for (Catalog catalog : catalogs()) {
            byLanguage.computeIfAbsent(catalog.locale().toLanguageTag(), _ -> new ArrayList<>())
                    .add(catalog.path().toString());
        }
        List<String> shared = new ArrayList<>();
        byLanguage.forEach((language, files) -> {
            if (files.size() > 1) {
                shared.add(language + ": " + files);
            }
        });
        assertEquals(List.of(), shared, "two catalogs answer for one language");
    }

    /**
     * A catalog is named for the locale it will be read as. The name is the only place a shipped
     * file says which language it is in, so a name {@link ResourceBundle} resolves differently than
     * this test reads it would leave the checks below describing a locale nobody is ever answered
     * in — spelling the locale back through the same control is what closes that.
     */
    @Test
    void everyCatalogIsNamedForTheLocaleItIsReadAs() throws IOException {
        Set<String> misnamed = new TreeSet<>();
        for (Catalog catalog : catalogs()) {
            if (catalog.isBase()) {
                continue;
            }
            String reachedAs = CONTROL.toBundleName(BUNDLE, catalog.locale()) + SUFFIX;
            if (!reachedAs.equals(catalog.name())) {
                misnamed.add(catalog.name() + " is read as " + catalog.locale().toLanguageTag()
                        + ", which is looked up as " + reachedAs);
            }
        }
        assertEquals(Set.of(), misnamed, "a catalog's name and the locale it is reached by disagree");
    }

    /**
     * The invariant the fallback used to stand in for. A shipped catalog defines every key the base
     * defines, so a reader who chose that language is answered in it throughout one diagnostic —
     * title, message, labels and hint — rather than in whichever of the two languages each key
     * happened to have reached.
     */
    @Test
    void everyCatalogDefinesTheSameKeysAsTheBase() throws IOException {
        Set<String> base = keysOf(baseCatalog().path());
        Set<String> divergent = new TreeSet<>();
        for (Catalog catalog : catalogs()) {
            if (catalog.isBase()) {
                continue;
            }
            Set<String> keys = keysOf(catalog.path());
            for (String key : difference(base, keys)) {
                divergent.add(catalog.name() + " does not define " + key);
            }
            for (String key : difference(keys, base)) {
                divergent.add(catalog.name() + " defines " + key + ", which the base does not");
            }
        }
        assertEquals(Set.of(), divergent);
    }

    /**
     * A key names the same arguments everywhere it is written.
     *
     * <p>The site passes one argument list and does not know which language it will be rendered in,
     * so which arguments a key takes is part of the key and not part of a translation. A message
     * that leaves one out drops what the site had to say and reads fine doing it — the Japanese
     * type mismatch said "the type here does not match" while the English one named the argument
     * that did not: {@code argument 3 of `foo`}. One that names an argument the base does not shows
     * the reader a bare {@code {1}}, since nothing was passed for it.
     *
     * <p>Neither is visible to the checks around this one. Each catalog is well-formed on its own,
     * and each substitutes the arguments it names.
     *
     * <p>Order is a translation's business — a language that puts the expected type first is
     * still saying the same thing — so it is the set that has to agree. Read through
     * {@link MessageFormat} rather than off the text, so a brace quoted as a literal
     * ({@code '{0}'}) is not counted as an argument that has to appear elsewhere.
     */
    @Test
    void everyCatalogUsesTheSameArgumentsAsTheBase() throws IOException {
        Properties base = load(baseCatalog().path());
        Set<String> divergent = new TreeSet<>();
        for (Catalog catalog : catalogs()) {
            if (catalog.isBase()) {
                continue;
            }
            Properties messages = load(catalog.path());
            for (String key : messages.stringPropertyNames()) {
                String there = base.getProperty(key);
                if (there == null) {
                    continue;   // a key the base does not define is reported next door
                }
                Set<Integer> mine = argumentsOf(messages.getProperty(key), catalog.locale());
                Set<Integer> theirs = argumentsOf(there, Locale.ENGLISH);
                if (!mine.equals(theirs)) {
                    divergent.add(catalog.name() + ": " + key + " takes " + mine
                            + ", the base takes " + theirs);
                }
            }
        }
        assertEquals(Set.of(), divergent,
                "the site passes one argument list for every language");
    }

    /**
     * The argument indexes {@code template} references, as the formatter reads them: rendered with
     * a mark for each argument the pattern could name, and the marks that come out are the ones it
     * does name.
     *
     * <p>{@code getFormatsByArgumentIndex()} gives the bound and not the set — it is as long as the
     * highest index mentioned, so a message using {@code {0}} and {@code {2}} reports three.
     */
    private static Set<Integer> argumentsOf(String template, Locale locale) {
        MessageFormat pattern;
        try {
            pattern = new MessageFormat(template, locale);
        } catch (IllegalArgumentException _) {
            return Set.of();   // refused, and reported as that rather than as an argument list
        }
        int bound = pattern.getFormatsByArgumentIndex().length;
        Object[] marks = new Object[bound];
        for (int i = 0; i < bound; i++) {
            marks[i] = MARK + Integer.toString(i) + MARK;
        }
        String rendered = pattern.format(marks);
        Set<Integer> named = new TreeSet<>();
        for (int i = 0; i < bound; i++) {
            if (rendered.contains(MARK + Integer.toString(i) + MARK)) {
                named.add(i);
            }
        }
        return named;
    }

    /** Stands in for an argument while its pattern is rendered; no message writes one. */
    private static final char MARK = '\u0001';

    /**
     * Every message parses as a {@link MessageFormat} pattern, whether or not it names an argument.
     *
     * <p>{@code Messages.get} renders every message as one, so a template the formatter refuses is
     * a message that cannot be shown — {@code `| Some { field }`} reads as an argument called
     * {@code " field "} and throws where the reader was going to be told what to write. Checking
     * only the messages that name a {@code {n}} left that outside: the pattern is refused at
     * construction, before any argument is substituted, and a message with a stray brace and no
     * placeholder names none.
     *
     * <p>What the message says survives that: a literal brace is written <code>'{'</code> and a
     * literal apostrophe <code>''</code>, and the check below renders each pattern to see what
     * comes out.
     */
    @Test
    void everyMessageIsAPatternTheFormatterAccepts() throws IOException {
        List<String> refused = new ArrayList<>();
        for (Catalog catalog : catalogs()) {
            Properties messages = load(catalog.path());
            for (String key : messages.stringPropertyNames()) {
                if (ownedByAMessage().contains(key)) {
                    continue;   // filled by name, so a brace holds a name and not an index
                }
                try {
                    new MessageFormat(messages.getProperty(key), catalog.locale());
                } catch (IllegalArgumentException e) {
                    refused.add(catalog.name() + ": " + key + " -> " + e.getMessage());
                }
            }
        }
        refused.sort(String::compareTo);
        assertEquals(List.of(), refused,
                "the formatter cannot read these; quote a literal brace as `'{'`");
    }

    /**
     * A lone {@code '} opens a quoted run and {@code ''} is the apostrophe itself. A message that
     * writes {@code a data's} therefore quotes the whole rest of itself: the apostrophe disappears
     * and every {@code {n}} after it renders literally, as the placeholder rather than the value.
     *
     * <p>Nothing caught that, because a message is only read when its diagnostic fires and the
     * result is prose nobody diffs. This renders each one with stand-in arguments and fails on any
     * placeholder that survived.
     *
     * <p>Rendered from the catalog's own text rather than through {@link Messages#get}, which reads
     * the bundle and so can answer with the base's copy of a key. A check that a file is valid has
     * to have read that file.
     */
    @Test
    void everyCatalogSubstitutesAllOfItsArguments() throws IOException {
        List<String> broken = new ArrayList<>();
        for (Catalog catalog : catalogs()) {
            Properties messages = load(catalog.path());
            for (String key : messages.stringPropertyNames()) {
                String template = messages.getProperty(key);
                int arity = arityOf(template);
                Object[] args = new Object[arity];
                for (int i = 0; i < arity; i++) {
                    args[i] = "<arg" + i + ">";
                }
                MessageFormat pattern;
                try {
                    pattern = new MessageFormat(template, catalog.locale());
                } catch (IllegalArgumentException _) {
                    continue;   // named above, and this one has nothing to add about it
                }
                String rendered = pattern.format(args);
                if (PLACEHOLDER.matcher(rendered).find()) {
                    broken.add(catalog.name() + ": " + key + " -> " + rendered);
                }
            }
        }
        broken.sort(String::compareTo);
        assertEquals(List.of(), broken);
    }

    /**
     * Every definition is spelled {@code key=value} on one line.
     *
     * <p>The check below reads a catalog line by line to see a key defined twice, which
     * {@link Properties} cannot be asked because it keeps only the last value. That reading is only
     * as good as the file format it assumes, and the format {@code Properties} actually accepts is
     * wider than the one the catalogs use: {@code :} and bare whitespace separate a key from its
     * value too, and a line ending in a backslash continues into the next. Under that, a second
     * definition written {@code foo:second} is invisible to the duplicate check and still wins at
     * run time.
     *
     * <p>So the format is required rather than observed. The catalogs are edited by hand, and
     * one spelling is no loss.
     */
    @Test
    void everyCatalogLineIsOneKeyAndOneValue() throws IOException {
        List<String> off = new ArrayList<>();
        for (Catalog catalog : catalogs()) {
            int number = 0;
            for (String line : Files.readAllLines(catalog.path(), StandardCharsets.UTF_8)) {
                number++;
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                if (!DEFINITION.matcher(line).matches() || line.endsWith("\\")) {
                    off.add(catalog.name() + ":" + number + ": " + line);
                }
            }
        }
        assertEquals(List.of(), off,
                "a catalog line is `key=value`, on one line, or blank, or a `#` comment");
    }

    @Test
    void noCatalogDefinesAKeyTwice() throws IOException {
        List<String> duplicated = new ArrayList<>();
        for (Catalog catalog : catalogs()) {
            for (String key : duplicateKeys(catalog.path())) {
                duplicated.add(catalog.name() + ": " + key);
            }
        }
        assertEquals(List.of(), duplicated);
    }

    /**
     * The two messages that spell out which values are ordered name exactly the primitives that are.
     *
     * <p>Both sentences write the set out by hand, in every language, which makes each of them a
     * copy of the table in {@code Ordering}. A copy is wrong the day the original changes and says
     * nothing about it: an eighth ordered primitive would leave two sentences quietly listing seven,
     * and the only reader who finds out is the author who wrote a comparison the compiler accepts
     * and the message says it should not.
     *
     * <p>Held as a set of tokens and not as a substring, because {@code DateTime} contains
     * {@code Date} and {@code Time}: a sentence that had lost both of the shorter names would pass a
     * {@code contains} against either. And over every catalog that ships, because a translation is a
     * separate copy of the same table.
     */
    @Test
    void everyCatalogListsTheOrderedPrimitivesAndOnlyThose() throws IOException {
        Set<String> ordered = new TreeSet<>();
        for (Type.Prim prim : Type.Prim.values()) {
            if (Ordering.of(prim, null, DeclarationKinds.NONE, PublishedDeclarations.NONE)
                    != null) {
                ordered.add(prim.shown());
            }
        }
        Map<String, Set<String>> named = new TreeMap<>();
        for (Catalog catalog : catalogs()) {
            Properties messages = load(catalog.path());
            for (String key : ORDERED_VALUE_KEYS) {
                String sentence = messages.getProperty(key);
                assertNotNull(sentence, catalog.name() + " defines no " + key
                        + ", so the sentence this holds to the ordered set was renamed or removed");
                Set<String> found = new TreeSet<>();
                for (Type.Prim prim : Type.Prim.values()) {
                    if (Pattern.compile("\\b" + prim.shown() + "\\b").matcher(sentence).find()) {
                        found.add(prim.shown());
                    }
                }
                named.put(catalog.name() + " " + key, found);
            }
        }
        Map<String, Set<String>> expected = new TreeMap<>();
        for (String where : named.keySet()) {
            expected.put(where, ordered);
        }
        assertEquals(expected, named);
    }

    @Test
    void everyCatalogNamesOnlyStandardLibraryFunctionsThatExist() throws IOException {
        Set<String> missing = new TreeSet<>();
        for (Catalog catalog : catalogs()) {
            for (String name : missingLibraryNames(catalog.path())) {
                missing.add(catalog.name() + ": " + name);
            }
        }
        assertEquals(Set.of(), missing);
    }

    /**
     * A key the compiler names but the catalog does not define renders as the key itself, so the
     * reader gets {@code check.match.newtype.notnewtype} where the message belongs. Nothing else
     * catches it: the site compiles, and the text is only read when that diagnostic fires.
     *
     * <p>The keys are collected from the sources rather than from the call sites, because a key is
     * also passed to the parser's and the AST builder's own {@code error(...)} helpers, and those
     * sites are invisible to a scan for {@code Diagnostic.of}. A string literal counts as a key when
     * it is shaped like one and its first segment is one the catalog already uses — so the very
     * first key of a brand-new namespace is the one case this does not see.
     *
     * <p>Against the base, which every other catalog is held to define exactly.
     */
    @Test
    void everyKeyTheCompilerNamesIsInTheCatalog() throws IOException {
        Set<String> defined = keysOf(baseCatalog().path());
        Set<String> namespaces = new TreeSet<>();
        for (String key : defined) {
            namespaces.add(key.substring(0, key.indexOf('.')));
        }
        Set<String> named = new TreeSet<>();
        for (Path source : mainSources()) {
            Matcher m = KEY_LITERAL.matcher(Files.readString(source, StandardCharsets.UTF_8));
            while (m.find()) {
                String key = m.group(1);
                if (namespaces.contains(key.substring(0, key.indexOf('.')))) {
                    named.add(key);
                }
            }
        }
        assertFalse(named.isEmpty(), "found no message keys at all — the source scan missed the tree");
        named.removeAll(defined);
        assertEquals(Set.of(), named);
    }

    /**
     * The other direction, over the one namespace where it holds: an example message the catalog
     * defines and nothing shows.
     *
     * <p>Narrower than the check above on purpose. Elsewhere a key is often built from parts — a
     * title from a code, a kind from a type — and a scan for literals cannot see those. Here every
     * key is written out at the site that uses it, which is what makes a hint nobody attaches
     * findable at all. That is the mistake this catches: a hint added beside a new message and never
     * passed to the diagnostic renders nowhere, and the message still reads fine on its own.
     */
    @Test
    void noExampleMessageIsDefinedAndNeverShown() throws IOException {
        Set<String> named = new TreeSet<>();
        for (Path source : mainSources()) {
            Matcher m = KEY_LITERAL.matcher(Files.readString(source, StandardCharsets.UTF_8));
            while (m.find()) {
                named.add(m.group(1));
            }
        }
        Set<String> unshown = new TreeSet<>();
        for (String key : keysOf(baseCatalog().path())) {
            if (key.startsWith("check.example.") && !named.contains(key)) {
                unshown.add(key);
            }
        }
        assertEquals(Set.of(), unshown);
    }

    /**
     * The same question over every namespace, not just the example one: a message the catalog
     * defines and nothing names is a message no compile can show. Four of them were found by
     * reading rather than by a build while the codes were being assigned, and two of those had a
     * rule written for them in the specification off the strength of text nobody could reach.
     *
     * <p>Held to the keys a site writes out. A key built by concatenation is invisible to a scan of
     * literals and would read as unused: the hints, which are `+<key> + ".hint"+` at several sites,
     * and the two namespaces whose leaf is chosen at run time.
     */
    @Test
    void noMessageIsDefinedAndNeverShown() throws IOException {
        Set<String> named = new TreeSet<>();
        for (Path source : mainSources()) {
            Matcher m = KEY_LITERAL.matcher(Files.readString(source, StandardCharsets.UTF_8));
            while (m.find()) {
                named.add(m.group(1));
            }
        }
        Set<String> unshown = new TreeSet<>();
        for (String key : keysOf(baseCatalog().path())) {
            if (key.endsWith(".hint") || key.startsWith("check.typearg.") || key.startsWith("kind.")
                    || named.contains(key) || ownedByAMessage().contains(key)) {
                continue;
            }
            unshown.add(key);
        }
        assertEquals(Set.of(), unshown, "defined and unreachable — nothing can show these");
    }

    /**
     * Every message declared under {@link Message}, found by walking what the interface permits.
     *
     * <p>Read off the hierarchy rather than listed, so a message that is declared is a message the
     * checks below are asked about — a list here would be one more thing to keep in step.
     */
    private static final List<Class<?>> LEAVES = new ArrayList<>();

    /**
     * The walk's answer, taken once for the class.
     *
     * <p>The hierarchy is sealed and every leaf of it is loaded to be asked what it permits, so the
     * walk is the class-loading of every message this compiler declares. It answers the same each
     * time it is asked — and it was being asked from inside the loop over every key of every
     * catalog, which is that loading once per key rather than once.
     */
    private static List<Class<? extends Message>> declared;

    private static List<Class<? extends Message>> messages() {
        if (declared == null) {
            declared = walk();
        }
        return declared;
    }

    /** Down the sealed hierarchy to the leaves, filling {@link #LEAVES} with all of them. */
    private static List<Class<? extends Message>> walk() {
        List<Class<? extends Message>> found = new ArrayList<>();
        List<Class<?>> leaves = new ArrayList<>();
        Deque<Class<?>> pending = new ArrayDeque<>(List.of(Message.class));
        while (!pending.isEmpty()) {
            Class<?> next = pending.poll();
            Class<?>[] permitted = next.getPermittedSubclasses();
            if (permitted != null && permitted.length > 0) {
                pending.addAll(List.of(permitted));
                continue;
            }
            leaves.add(next);
            if (next.isRecord() && Message.class.isAssignableFrom(next)) {
                found.add(next.asSubclass(Message.class));
            }
        }
        LEAVES.clear();
        LEAVES.addAll(leaves);
        return found;
    }

    /**
     * Every message is a record that names the rule it reports.
     *
     * <p>The walk above takes the records and passes over anything else, so without this a leaf that
     * is not one — a class implementing the interface, an enum — carries no values to check and every
     * rule below is silent about it. A missing {@code @Code} is the same shape of hole: it raises
     * where the message is built rather than where it is declared, which is a use site away from the
     * mistake.
     *
     * <p>Which messages must name one is {@link souther.compiler.diag.msg.Reported}: those are what
     * a diagnostic can be about. A hint or a secondary label is
     * {@link souther.compiler.diag.msg.Supporting}, and names none — a code there is read by
     * nothing, and counted by the rule below as a rule something reports.
     *
     * <p>Every message carries one of the two roles and never both, which is what makes being a
     * {@code Reported} the same thing as being usable as one: the builder takes each where it
     * belongs, so a message written for a hint cannot reach {@code say} and one written as a subject
     * cannot reach {@code hint}. Held here as well because a leaf could carry neither and fall
     * through both. The roles sit outside the {@link Message} hierarchy, so this walk never meets
     * them and what may be a message stays what {@code Message} permits.
     */
    @Test
    void everyMessageIsARecordThatNamesItsRule() {
        messages();   // fills LEAVES
        List<String> wrong = new ArrayList<>();
        for (Class<?> leaf : LEAVES) {
            if (!leaf.isRecord()) {
                wrong.add(leaf.getName() + " is not a record");
                continue;
            }
            boolean reports = souther.compiler.diag.msg.Reported.class.isAssignableFrom(leaf);
            boolean supports = souther.compiler.diag.msg.Supporting.class.isAssignableFrom(leaf);
            if (reports == supports) {
                wrong.add(leaf.getName() + (reports
                        ? " is both what a diagnostic is about and what is said beside one"
                        : " is neither what a diagnostic is about nor what is said beside one"));
            }
            boolean named = leaf.getAnnotation(souther.compiler.diag.msg.Code.class) != null;
            if (reports && !named) {
                wrong.add(leaf.getName() + " reports a rule and names no code");
            }
            // A hint's code is read by nothing — a diagnostic's comes from its subject — so one
            // written here is a number that looks reported and is not.
            if (supports && named) {
                wrong.add(leaf.getName() + " is a hint or a label and names a code");
            }
        }
        wrong.sort(String::compareTo);
        assertEquals(List.of(), wrong,
                "a message is a record whose components are its values, and it reports a rule");
    }

    /** A message written at a site: {@code new AreaMessage.TheThing(}, however it is wrapped. */
    private static final Pattern BUILT =
            Pattern.compile("new\\s+(\\w+Message)\\s*\\.\\s*(\\w+)\\s*\\(");

    /**
     * Every message the hierarchy declares is one some site builds.
     *
     * <p>A declared message nothing builds is a sentence in every catalog that no compile can
     * produce, and it is what a code left behind looks like: the sites that reported a rule move to
     * another number and the record they used stays, still naming the old one. Without this, the
     * rule below reads that record and answers that the code is reported.
     *
     * <p>A tripwire and not a proof, like the surface test next door: what it reads is the source
     * text, so a message built through a variable would read as unbuilt. That is the direction that
     * costs a green build rather than a wrong one.
     */
    @Test
    void everyMessageIsBuiltSomewhere() throws IOException {
        Set<String> built = built();
        List<String> unbuilt = new ArrayList<>();
        for (Class<? extends Message> message : messages()) {
            if (!built.contains(message.getEnclosingClass().getSimpleName() + "."
                    + message.getSimpleName())) {
                unbuilt.add(message.getName());
            }
        }
        unbuilt.sort(String::compareTo);
        assertEquals(List.of(), unbuilt, "declared and never raised — no compile can show these");
    }

    /**
     * Every rule a reader can be told to look up is reported by a message some site builds.
     *
     * <p>A code nothing reports is a chapter of the manual nothing sends anyone to. It is how a rule
     * gets documented and then quietly stops being reported: the sites move to another number, and
     * the number they left says nothing about having been abandoned.
     *
     * <p>Read off the messages that are built rather than the ones that are declared, which is the
     * difference between this holding and it appearing to. A record kept beside the code it used to
     * report answers the declared question and not this one.
     *
     * <p>And read off the {@link souther.compiler.diag.msg.Reported} ones, which is what makes
     * "built" mean "built as a diagnostic's subject". Every message renders, so a scan of the source
     * cannot tell the subject from the hint written under it — three quarters of the subjects reach
     * {@code say} through a helper and would read as unbuilt, and a scan that classified them by
     * hand put thirteen hints on the wrong side. The types say it instead, and they are disjoint: a
     * {@code Message & Reported} is what {@code say} takes and nothing else takes it, so a message
     * that carries the role is built as a subject or is not built at all.
     */
    @Test
    void everyCodeIsReportedByAMessage() throws IOException {
        Set<String> built = built();
        Set<DiagnosticCode> reported = new java.util.HashSet<>();
        for (Class<? extends Message> message : messages()) {
            if (souther.compiler.diag.msg.Reported.class.isAssignableFrom(message)
                    && built.contains(message.getEnclosingClass().getSimpleName() + "."
                            + message.getSimpleName())) {
                reported.add(MessageCodes.of(
                        message.asSubclass(souther.compiler.diag.msg.Reported.class)));
            }
        }
        List<String> unreported = new ArrayList<>();
        for (DiagnosticCode code : DiagnosticCode.values()) {
            if (!reported.contains(code)) {
                unreported.add(code.name());
            }
        }
        unreported.sort(String::compareTo);
        assertEquals(List.of(), unreported,
                "a code names a rule a reader looks up, and nothing raises these");
    }

    /** Every {@code Area.Record} a main source builds. */
    private static Set<String> built() throws IOException {
        Set<String> found = new TreeSet<>();
        for (Path source : mainSources()) {
            Matcher m = BUILT.matcher(Files.readString(source, StandardCharsets.UTF_8));
            while (m.find()) {
                found.add(m.group(1) + "." + m.group(2));
            }
        }
        return found;
    }

    /** What a message cites: {@code (spec <anchor>)}, in any language.
     *
     * <p>Whatever stands there, not what an anchor looks like. Written to match an anchor's shape,
     * this reads a citation that is not one — the section numbers these replaced — as no citation at
     * all, and the rule holds over the ones already correct. */
    private static final Pattern CITES = Pattern.compile("\\(spec ([^)]+)\\)");

    /**
     * Every section a message sends a reader to is a section they can read.
     *
     * <p>These were section numbers — {@code (spec 19.5)} — which nothing accepts as a name: the
     * lookup takes anchors and diagnostic codes, and offered {@code e1905} and {@code e1915} as
     * near-misses for {@code 19.5}, which is worse than answering nothing. Numbers also move. The
     * chapter those four cited had become the twenty-first by the time this was written, so they
     * were wrong twice over: unlookuppable, and pointing at a number that no longer meant what the
     * sentence said it did.
     *
     * <p>An anchor is neither. It is what the reader types, and it moves with the section it names.
     *
     * <p>Asked of the resolver the reader's own lookup goes through, not of the headings it lists.
     * A name is more than a heading's: a rule written mid-section carries its own anchor so a
     * diagnostic can point at the rule rather than the chapter around it, a section answers to the
     * codes in its {@code also-named}, and all of them are matched canonically. Held against the
     * list of headings instead, this refuses `+no-two-declarations-become-one-class+` — which is
     * exactly the kind of thing worth citing, and which {@code souther doc} reads out.
     */
    @Test
    void everySectionAMessageCitesIsOneAReaderCanRead() throws IOException {
        SpecDocument spec = SpecDocument.bundled();
        assertFalse(spec.sections().isEmpty(), "found no sections at all — the spec did not load");
        List<String> dangling = new ArrayList<>();
        for (Catalog catalog : catalogs()) {
            Properties entries = load(catalog.path());
            for (String key : entries.stringPropertyNames()) {
                Matcher cited = CITES.matcher(entries.getProperty(key));
                while (cited.find()) {
                    if (spec.section(cited.group(1)) == null) {
                        dangling.add(catalog.path().getFileName() + ": " + key + " cites `"
                                + cited.group(1) + "`");
                    }
                }
            }
        }
        dangling.sort(String::compareTo);
        assertEquals(List.of(), dangling, "a message sends a reader to a section that is not there");
    }

    private static Set<String> owned;

    /** The entries a message fills by name, taken once: two checks ask this of every key they read. */
    private static Set<String> ownedByAMessage() {
        if (owned == null) {
            Set<String> keys = new TreeSet<>();
            for (Class<? extends Message> message : messages()) {
                keys.add(MessageKeys.of(message));
            }
            owned = keys;
        }
        return owned;
    }

    /**
     * One entry belongs to one message.
     *
     * <p>The key is derived, which is what keeps a site from choosing one — it is not what keeps two
     * messages from arriving at the same one. The derivation lowercases and drops a suffix, so it is
     * not injective, and where two messages did collide every check below would read one entry twice
     * and say nothing: the map they are gathered into would keep the last. Injectivity is held here
     * rather than assumed of the function, which also survives the naming rule being changed.
     */
    @Test
    void oneEntryBelongsToOneMessage() {
        Map<String, List<String>> owners = new TreeMap<>();
        for (Class<? extends Message> message : messages()) {
            owners.computeIfAbsent(MessageKeys.of(message), _ -> new ArrayList<>())
                    .add(message.getName());
        }
        List<String> shared = new ArrayList<>();
        owners.forEach((key, byThese) -> {
            if (byThese.size() > 1) {
                shared.add(key + " is owned by " + String.join(" and ", byThese));
            }
        });
        assertEquals(List.of(), shared, "two messages cannot render through one entry");
    }

    /** Every message renders through an entry, in every catalog that ships. */
    @Test
    void everyMessageHasAnEntryInEveryCatalog() throws IOException {
        List<String> missing = new ArrayList<>();
        for (Catalog catalog : catalogs()) {
            Properties entries = load(catalog.path());
            for (Class<? extends Message> message : messages()) {
                String key = MessageKeys.of(message);
                if (entries.getProperty(key) == null) {
                    missing.add(catalog.name() + ": " + key);
                }
            }
        }
        missing.sort(String::compareTo);
        assertEquals(List.of(), missing, "a message with no entry renders as its own key");
    }

    /**
     * A message says every value it carries.
     *
     * <p>This is the rule the messages are records for. A diagnostic that holds the clause it
     * judged, or the group that supplied the field, and prints neither, is what a reader cannot act
     * on — and it was writable for as long as the values were an untyped array beside a key. Here
     * the entry is held to naming each one, in every language, so carrying a value and not showing
     * it is not something a catalog can be edited into.
     */
    @Test
    void everyMessageSaysEachValueItCarries() throws IOException {
        List<String> silent = new ArrayList<>();
        for (Catalog catalog : catalogs()) {
            Properties entries = load(catalog.path());
            for (Class<? extends Message> message : messages()) {
                String key = MessageKeys.of(message);
                String written = entries.getProperty(key);
                if (written == null) {
                    continue;   // everyMessageHasAnEntryInEveryCatalog reports this one
                }
                for (String value : MessageValues.namesOf(message)) {
                    if (!written.contains("{" + value + "}")) {
                        silent.add(catalog.name() + ": " + key + " does not say `" + value + "`");
                    }
                }
            }
        }
        silent.sort(String::compareTo);
        assertEquals(List.of(), silent,
                "these carry a value the reader is never shown; write it into the entry");
    }

    /** The other direction: a name in an entry is a value the message carries. */
    @Test
    void everyNamedPlaceholderIsAValueTheMessageCarries() throws IOException {
        List<String> unknown = new ArrayList<>();
        Map<String, Class<? extends Message>> byKey = new TreeMap<>();
        for (Class<? extends Message> message : messages()) {
            byKey.put(MessageKeys.of(message), message);
        }
        for (Catalog catalog : catalogs()) {
            Properties entries = load(catalog.path());
            for (Map.Entry<String, Class<? extends Message>> owned : byKey.entrySet()) {
                String written = entries.getProperty(owned.getKey());
                if (written == null) {
                    continue;
                }
                Set<String> carried = new TreeSet<>(MessageValues.namesOf(owned.getValue()));
                MessageTemplate parsed = MessageTemplate.parse(written);
                for (String malformed : parsed.malformations()) {
                    unknown.add(catalog.name() + ": " + owned.getKey() + " has " + malformed);
                }
                for (String named : parsed.names()) {
                    if (!carried.contains(named)) {
                        unknown.add(catalog.name() + ": " + owned.getKey() + " names `" + named
                                + "`, which it does not carry");
                    }
                }
            }
        }
        unknown.sort(String::compareTo);
        assertEquals(List.of(), unknown, "an entry names a value nothing fills");
    }

    private static final Pattern KEY_LITERAL =
            Pattern.compile("\"([a-z][a-z0-9]*(?:\\.[a-z0-9]+)+)\"");

    /**
     * The catalogs that ship, found in the tree rather than listed here. Read off the repository
     * because that is what "shipped" is decided by: a file under a module's resources, in the
     * package the bundle is read from, is in the jar and is reachable by whoever names its language.
     */
    private static List<Catalog> catalogs() throws IOException {
        List<Catalog> found = new ArrayList<>();
        for (Path resources : moduleDirectories("resources")) {
            Path directory = resources.resolve(PACKAGE);
            if (!Files.isDirectory(directory)) {
                continue;
            }
            try (Stream<Path> files = Files.list(directory)) {
                for (Path file : files.sorted().toList()) {
                    String name = file.getFileName().toString();
                    if (name.startsWith(BUNDLE) && name.endsWith(SUFFIX)) {
                        found.add(new Catalog(file, localeOf(name)));
                    }
                }
            }
        }
        return List.copyOf(found);
    }

    private static Catalog baseCatalog() throws IOException {
        for (Catalog catalog : catalogs()) {
            if (catalog.isBase()) {
                return catalog;
            }
        }
        throw new IllegalStateException("no base catalog was found; the scan missed the tree");
    }

    /**
     * The language a catalog file's name says it is in. The base names none and is English.
     *
     * <p>English as a fact about the file, not as the language Souther falls back to. The two agree
     * and are different things: one is the language the base catalog is written in, the other is
     * which language is chosen when nobody names one. Reading the second here would make the base's
     * text get checked against whatever the default becomes.
     */
    private static Locale localeOf(String name) {
        String stem = name.substring(0, name.length() - SUFFIX.length());
        String tag = stem.substring(BUNDLE.length());
        return tag.isEmpty()
                ? Locale.ENGLISH
                : Locale.forLanguageTag(tag.substring(1).replace('_', '-'));
    }

    /** Every module's main sources. Any module may name a message key. */
    public static List<Path> mainSources() throws IOException {
        List<Path> sources = new ArrayList<>();
        for (Path root : moduleDirectories("java")) {
            try (Stream<Path> walk = Files.walk(root)) {
                walk.filter(p -> p.toString().endsWith(".java")).forEach(sources::add);
            }
        }
        return sources;
    }

    /** Every module's {@code src/main/<kind>}, for the modules that have one. The test runs in its
     *  own module directory, so the repository root is that directory's parent. */
    private static List<Path> moduleDirectories(String kind) throws IOException {
        return REPOSITORY.mainTrees(kind);
    }

    private static Properties load(Path catalog) throws IOException {
        Properties messages = new Properties();
        try (BufferedReader text = Files.newBufferedReader(catalog, StandardCharsets.UTF_8)) {
            messages.load(text);
        }
        return messages;
    }

    private static Set<String> keysOf(Path catalog) throws IOException {
        return new TreeSet<>(load(catalog).stringPropertyNames());
    }

    private static Set<String> difference(Set<String> from, Set<String> without) {
        Set<String> left = new TreeSet<>(from);
        left.removeAll(without);
        return left;
    }

    /** One past the highest argument index the pattern names. */
    private static int arityOf(String template) {
        Matcher m = PLACEHOLDER.matcher(template);
        int highest = -1;
        while (m.find()) {
            highest = Math.max(highest, Integer.parseInt(m.group(1)));
        }
        return highest + 1;
    }

    /**
     * The qualified standard-library names this catalog quotes that the library does not publish.
     *
     * <p>A hint that tells an author to reach for {@code List.flatMap} after it was renamed sends
     * them to a compile error, in the one text whose whole job is to get them out of one. Nothing
     * else notices: the catalog is prose, so a rename sweeps the code and the tests and leaves the
     * advice behind — which is exactly what happened to two hints here, found in review rather than
     * by a build.
     *
     * <p>Only a name under a library qualifier is checked, and only where the catalog wrote it as
     * code. A qualifier the library does not have is somebody's module and not this test's business.
     */
    private static Set<String> missingLibraryNames(Path catalog) throws IOException {
        Pattern quoted = Pattern.compile("`([A-Z][A-Za-z]*)\\.([a-zA-Z_][A-Za-z0-9_]*)`");
        Set<String> missing = new TreeSet<>();
        for (String line : Files.readAllLines(catalog, StandardCharsets.UTF_8)) {
            Matcher m = quoted.matcher(line);
            while (m.find()) {
                if (!Reserved.isQualifier(m.group(1))) {
                    continue;
                }
                String qualified = m.group(1) + "." + m.group(2);
                if (!DefaultStdlib.get().published().contains(qualified)) {
                    missing.add(qualified);
                }
            }
        }
        return Set.copyOf(missing);
    }

    /**
     * The keys this catalog defines more than once. A second definition silently wins, so the first
     * one is dead and whichever diagnostics meant it render someone else's text — the same kind of
     * defect as the one above, and just as invisible for the same reason.
     *
     * <p>Read line by line rather than through {@link Properties}, which keeps only the last value
     * and so cannot see the collision at all. What a line means is settled by the check above,
     * which holds every catalog to one definition per line spelled with {@code =} — without it a
     * second definition written another way would be read here as prose.
     */
    private static List<String> duplicateKeys(Path catalog) throws IOException {
        Set<String> seen = new LinkedHashSet<>();
        Set<String> duplicated = new TreeSet<>();
        try (BufferedReader lines = Files.newBufferedReader(catalog, StandardCharsets.UTF_8)) {
            String line;
            while ((line = lines.readLine()) != null) {
                String trimmed = line.strip();
                int eq = trimmed.indexOf('=');
                if (trimmed.isEmpty() || trimmed.startsWith("#") || eq < 0) {
                    continue;
                }
                String key = trimmed.substring(0, eq).strip();
                if (!seen.add(key)) {
                    duplicated.add(key);
                }
            }
        }
        return List.copyOf(duplicated);
    }
}
